import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parents[1]
sys.path.insert(0, str(SCRIPT_DIRECTORY))

import classify_backend_changes as classifier


class BackendChangeClassifierTest(unittest.TestCase):
    def assert_paths(self, expected, *paths, **expected_flags):
        result = classifier.classify_paths("1" * 40, "2" * 40, list(paths))
        self.assertEqual(expected, result["classification"])
        for field, value in expected_flags.items():
            self.assertEqual(value, result[field], field)

    def test_path_classifications(self):
        cases = (
            ("APP_ONLY", ("src/main/java/example/App.java",), {"app_changed": True}),
            (
                "APP_ONLY",
                ("src/main/resources/db/migration/V3__sample.sql",),
                {"app_changed": True, "migration_changed": True},
            ),
            (
                "OPS_ONLY",
                (
                    "compose.prod.yml",
                    "ops/content-sync/houkago-content-sync-worker",
                    "ops/ops-reconcile/houkago-ops-reconcile-worker",
                ),
                {"ops_changed": True},
            ),
            (
                "APP_AND_OPS",
                ("src/main/java/example/App.java", "ops/systemd/example.service"),
                {"app_changed": True, "ops_changed": True},
            ),
            (
                "NO_PRODUCTION_IMPACT",
                (
                    "src/test/java/example/AppTest.java",
                    "README.md",
                    "ops/ops-reconcile/ops-reconcile-worker.env.example",
                    "ops/ops-reconcile/test_houkago_ops_reconcile_worker.py",
                ),
                {"app_changed": False, "ops_changed": False},
            ),
            (
                "CONTROL_PLANE",
                (".github/workflows/ci.yml", "ops/ci/classify_backend_changes.py"),
                {"control_plane_changed": True},
            ),
            (
                "CONTROL_PLANE",
                ("new-runtime-area/config.yml",),
                {"control_plane_changed": True, "unknown_paths": ["new-runtime-area/config.yml"]},
            ),
        )
        for expected, paths, flags in cases:
            with self.subTest(expected=expected, paths=paths):
                self.assert_paths(expected, *paths, **flags)

    def test_multi_commit_range_uses_the_whole_push(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self.git(repository, "init", "-q")
            self.git(repository, "config", "user.name", "Classifier Test")
            self.git(repository, "config", "user.email", "classifier@example.invalid")
            self.git(repository, "commit", "--allow-empty", "-qm", "base")
            base = self.git(repository, "rev-parse", "HEAD")

            source = repository / "src/main/java/example/App.java"
            source.parent.mkdir(parents=True)
            source.write_text("class App {}\n", encoding="utf-8")
            self.git(repository, "add", "src/main/java/example/App.java")
            self.git(repository, "commit", "-qm", "application")

            (repository / "README.md").write_text("docs\n", encoding="utf-8")
            self.git(repository, "add", "README.md")
            self.git(repository, "commit", "-qm", "docs")
            head = self.git(repository, "rev-parse", "HEAD")

            result = classifier.classify_repository(repository, base, head)
            self.assertEqual("APP_ONLY", result["classification"])
            self.assertIn("src/main/java/example/App.java", result["changed_paths"])
            self.assertIn("README.md", result["changed_paths"])

    def test_future_app_gate_blocks_cumulative_ops_change_after_mixed_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.initialize_repository(Path(directory))
            deployed_ops = self.git(repository, "rev-parse", "HEAD")
            mixed = self.commit_files(
                repository,
                "mixed",
                {
                    "src/main/java/example/App.java": "class App {}\n",
                    "compose.prod.yml": "services: {}\n",
                },
            )
            target_app = self.commit_files(
                repository,
                "application follow-up",
                {"src/main/java/example/App.java": "class App { int version = 2; }\n"},
            )

            push_local = classifier.classify_repository(repository, mixed, target_app)
            cumulative = classifier.classify_repository(repository, deployed_ops, target_app)

            self.assertEqual("APP_ONLY", push_local["classification"])
            self.assertEqual("APP_AND_OPS", cumulative["classification"])
            self.assertTrue(self.future_app_gate_blocks(cumulative))

    def test_future_ops_gate_blocks_cumulative_app_change_after_mixed_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.initialize_repository(Path(directory))
            deployed_app = self.git(repository, "rev-parse", "HEAD")
            mixed = self.commit_files(
                repository,
                "mixed",
                {
                    "src/main/java/example/App.java": "class App {}\n",
                    "compose.prod.yml": "services: {}\n",
                },
            )
            target_ops = self.commit_files(
                repository,
                "operations follow-up",
                {"ops/systemd/example.service": "[Service]\nType=oneshot\n"},
            )

            push_local = classifier.classify_repository(repository, mixed, target_ops)
            cumulative = classifier.classify_repository(repository, deployed_app, target_ops)

            self.assertEqual("OPS_ONLY", push_local["classification"])
            self.assertEqual("APP_AND_OPS", cumulative["classification"])
            self.assertTrue(self.future_ops_gate_blocks(cumulative))

    def test_future_gate_evaluates_the_full_multi_commit_release_range(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.initialize_repository(Path(directory))
            deployed_app = self.commit_files(
                repository,
                "deployed application",
                {"src/main/java/example/App.java": "class App { int version = 1; }\n"},
            )
            self.commit_files(repository, "docs", {"README.md": "documentation\n"})
            mixed = self.commit_files(
                repository,
                "mixed",
                {
                    "src/main/java/example/App.java": "class App { int version = 2; }\n",
                    "compose.prod.yml": "services: {}\n",
                },
            )
            target_ops = self.commit_files(
                repository,
                "operations follow-up",
                {"ops/systemd/example.service": "[Service]\nType=oneshot\n"},
            )

            push_local = classifier.classify_repository(repository, mixed, target_ops)
            cumulative = classifier.classify_repository(repository, deployed_app, target_ops)

            self.assertEqual("OPS_ONLY", push_local["classification"])
            self.assertTrue(cumulative["app_changed"])
            self.assertTrue(cumulative["ops_changed"])
            self.assertIn("README.md", cumulative["changed_paths"])
            self.assertTrue(self.future_ops_gate_blocks(cumulative))

    def test_historical_control_plane_does_not_deadlock_app_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.initialize_repository(Path(directory))
            deployed_ops = self.git(repository, "rev-parse", "HEAD")
            control_plane = self.commit_files(
                repository,
                "control plane",
                {".github/workflows/policy.yml": "name: policy\n"},
            )
            target_app = self.commit_files(
                repository,
                "application",
                {"src/main/java/example/App.java": "class App {}\n"},
            )

            control_push = classifier.classify_repository(repository, deployed_ops, control_plane)
            app_push = classifier.classify_repository(repository, control_plane, target_app)
            cumulative = classifier.classify_repository(repository, deployed_ops, target_app)

            self.assertEqual("CONTROL_PLANE", control_push["classification"])
            self.assertEqual("APP_ONLY", app_push["classification"])
            self.assertTrue(cumulative["control_plane_changed"])
            self.assertEqual([], cumulative["unknown_paths"])
            self.assertFalse(self.future_app_gate_blocks(cumulative))

    def test_historical_control_plane_does_not_deadlock_ops_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.initialize_repository(Path(directory))
            deployed_app = self.git(repository, "rev-parse", "HEAD")
            control_plane = self.commit_files(
                repository,
                "control plane",
                {"ops/ci/policy.py": "POLICY = 'current'\n"},
            )
            target_ops = self.commit_files(
                repository,
                "operations",
                {"compose.prod.yml": "services: {}\n"},
            )

            control_push = classifier.classify_repository(repository, deployed_app, control_plane)
            ops_push = classifier.classify_repository(repository, control_plane, target_ops)
            cumulative = classifier.classify_repository(repository, deployed_app, target_ops)

            self.assertEqual("CONTROL_PLANE", control_push["classification"])
            self.assertEqual("OPS_ONLY", ops_push["classification"])
            self.assertTrue(cumulative["control_plane_changed"])
            self.assertEqual([], cumulative["unknown_paths"])
            self.assertFalse(self.future_ops_gate_blocks(cumulative))

    def test_future_gates_block_paths_unknown_to_current_taxonomy(self):
        result = classifier.classify_paths(
            "1" * 40,
            "2" * 40,
            ["unclassified/runtime.conf"],
        )

        self.assertTrue(result["control_plane_changed"])
        self.assertEqual(["unclassified/runtime.conf"], result["unknown_paths"])
        self.assertTrue(self.future_app_gate_blocks(result))
        self.assertTrue(self.future_ops_gate_blocks(result))

    def test_future_gate_reclassifies_historical_paths_with_current_taxonomy(self):
        historical_artifact = {
            "app_changed": False,
            "ops_changed": False,
            "migration_changed": False,
            "control_plane_changed": True,
            "unknown_paths": ["README.md"],
            "blocked_reason": "",
        }
        current_result = classifier.classify_paths("1" * 40, "2" * 40, ["README.md"])

        self.assertTrue(self.future_app_gate_blocks(historical_artifact))
        self.assertEqual("NO_PRODUCTION_IMPACT", current_result["classification"])
        self.assertFalse(self.future_app_gate_blocks(current_result))
        self.assertFalse(self.future_ops_gate_blocks(current_result))

    def test_future_ops_gate_blocks_cumulative_migration(self):
        result = classifier.classify_paths(
            "1" * 40,
            "2" * 40,
            [
                "src/main/resources/db/migration/V3__sample.sql",
                "compose.prod.yml",
            ],
        )

        self.assertTrue(result["migration_changed"])
        self.assertTrue(self.future_ops_gate_blocks(result))

    def test_future_gates_allow_normal_independent_release_progression(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = self.initialize_repository(Path(directory))
            initial = self.git(repository, "rev-parse", "HEAD")
            current_ops = self.commit_files(
                repository,
                "operations",
                {"compose.prod.yml": "services: {}\n"},
            )
            ops_range = classifier.classify_repository(repository, initial, current_ops)
            self.assertFalse(self.future_ops_gate_blocks(ops_range))

            target_app = self.commit_files(
                repository,
                "application",
                {"src/main/java/example/App.java": "class App {}\n"},
            )
            app_range = classifier.classify_repository(repository, current_ops, target_app)
            self.assertFalse(self.future_app_gate_blocks(app_range))

    def test_zero_and_non_fast_forward_ranges_block_automation(self):
        zero = classifier.classify_repository(REPOSITORY_ROOT, classifier.ZERO_SHA, "1" * 40)
        self.assertEqual("CONTROL_PLANE", zero["classification"])
        self.assertEqual("zero_base_sha", zero["blocked_reason"])
        self.assertTrue(self.future_app_gate_blocks(zero))
        self.assertTrue(self.future_ops_gate_blocks(zero))

        invalid = classifier.classify_repository(REPOSITORY_ROOT, "invalid", "1" * 40)
        self.assertEqual("CONTROL_PLANE", invalid["classification"])
        self.assertEqual("invalid_sha", invalid["blocked_reason"])
        self.assertTrue(self.future_app_gate_blocks(invalid))
        self.assertTrue(self.future_ops_gate_blocks(invalid))

        missing = classifier.classify_repository(REPOSITORY_ROOT, "f" * 40, "1" * 40)
        self.assertEqual("CONTROL_PLANE", missing["classification"])
        self.assertEqual("missing_commit", missing["blocked_reason"])
        self.assertTrue(self.future_app_gate_blocks(missing))
        self.assertTrue(self.future_ops_gate_blocks(missing))

        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self.git(repository, "init", "-q")
            self.git(repository, "config", "user.name", "Classifier Test")
            self.git(repository, "config", "user.email", "classifier@example.invalid")
            self.git(repository, "commit", "--allow-empty", "-qm", "root")
            root = self.git(repository, "rev-parse", "HEAD")
            self.git(repository, "commit", "--allow-empty", "-qm", "base")
            base = self.git(repository, "rev-parse", "HEAD")
            self.git(repository, "checkout", "-q", "--detach", root)
            self.git(repository, "commit", "--allow-empty", "-qm", "head")
            head = self.git(repository, "rev-parse", "HEAD")

            result = classifier.classify_repository(repository, base, head)
            self.assertEqual("CONTROL_PLANE", result["classification"])
            self.assertEqual("non_fast_forward_range", result["blocked_reason"])
            self.assertTrue(self.future_app_gate_blocks(result))
            self.assertTrue(self.future_ops_gate_blocks(result))

    def test_artifact_validation_fails_on_sha_mismatch(self):
        result = classifier.classify_paths("1" * 40, "2" * 40, ["README.md"])
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "classification.json"
            artifact.write_text(json.dumps(result), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "SHA mismatch"):
                classifier.validate_artifact(artifact, "3" * 40)

    def test_current_repository_paths_are_explicitly_classified(self):
        tracked_paths = self.git(REPOSITORY_ROOT, "ls-files").splitlines()
        tracked_paths.extend(
            [
                "ops/ci/classify_backend_changes.py",
                "ops/ci/test_classify_backend_changes.py",
            ]
        )
        unknown = [path for path in tracked_paths if classifier.classify_path(path)[0] == "unknown"]
        self.assertEqual([], unknown)

    def test_historical_commit_regressions(self):
        expected = {
            "3e433f1": ("APP_ONLY", True, False, False),
            "4c00485": ("NO_PRODUCTION_IMPACT", False, False, False),
            "290685f": ("NO_PRODUCTION_IMPACT", False, False, False),
            "3330825": ("OPS_ONLY", False, True, False),
            "bd5a0e8": ("APP_AND_OPS", True, True, False),
            "e8bdd24": ("CONTROL_PLANE", False, True, True),
            "cad170f": ("OPS_ONLY", False, True, False),
        }
        for revision, values in expected.items():
            with self.subTest(revision=revision):
                head = self.git(REPOSITORY_ROOT, "rev-parse", revision)
                base = self.git(REPOSITORY_ROOT, "rev-parse", f"{revision}^")
                result = classifier.classify_repository(REPOSITORY_ROOT, base, head)
                actual = (
                    result["classification"],
                    result["app_changed"],
                    result["ops_changed"],
                    result["control_plane_changed"],
                )
                self.assertEqual(values, actual)

    def test_ops_workflow_reuses_verified_artifact_and_only_allows_ops_only(self):
        workflow = (REPOSITORY_ROOT / ".github/workflows/reconcile-ops.yml").read_text(encoding="utf-8")

        self.assertIn("actions/download-artifact@v8", workflow)
        self.assertIn("run-id: ${{ steps.source.outputs.ci_run_id }}", workflow)
        self.assertIn('--expected-head "$TARGET_SHA"', workflow)
        self.assertIn('[[ "$CLASSIFICATION" == "OPS_ONLY"', workflow)
        self.assertIn("OPS_ONLY)", workflow)
        self.assertIn("needs.evaluate.outputs.decision == 'ALLOW'", workflow)
        self.assertIn("HOUKAGO_OPS_RECONCILE_SECRET", workflow)
        self.assertIn("/internal/deployments/ops", workflow)
        self.assertIn("APP_AND_OPS)", workflow)
        self.assertIn("CONTROL_PLANE)", workflow)

    @staticmethod
    def future_app_gate_blocks(result):
        return bool(result["blocked_reason"] or result["ops_changed"] or result["unknown_paths"])

    @staticmethod
    def future_ops_gate_blocks(result):
        return bool(
            result["blocked_reason"]
            or result["app_changed"]
            or result["migration_changed"]
            or result["unknown_paths"]
        )

    def initialize_repository(self, repository: Path) -> Path:
        self.git(repository, "init", "-q")
        self.git(repository, "config", "user.name", "Classifier Test")
        self.git(repository, "config", "user.email", "classifier@example.invalid")
        self.git(repository, "commit", "--allow-empty", "-qm", "base")
        return repository

    def commit_files(self, repository: Path, message: str, files: dict[str, str]) -> str:
        for relative_path, content in files.items():
            path = repository / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        self.git(repository, "add", "--all")
        self.git(repository, "commit", "-qm", message)
        return self.git(repository, "rev-parse", "HEAD")

    @staticmethod
    def git(repository: Path, *arguments: str) -> str:
        return subprocess.run(
            ["git", "-C", str(repository), *arguments],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ).stdout.strip()


if __name__ == "__main__":
    unittest.main()
