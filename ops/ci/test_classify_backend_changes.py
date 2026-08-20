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

    def test_zero_and_non_fast_forward_ranges_block_automation(self):
        zero = classifier.classify_repository(REPOSITORY_ROOT, classifier.ZERO_SHA, "1" * 40)
        self.assertEqual("CONTROL_PLANE", zero["classification"])
        self.assertEqual("zero_base_sha", zero["blocked_reason"])

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
