#!/usr/bin/env python3

import datetime as dt
import fcntl
import importlib.machinery
import importlib.util
import json
import os
import subprocess
import tempfile
import unittest
import uuid
from pathlib import Path


SCRIPT = Path(__file__).with_name("houkago-ops-reconcile-worker")
LOADER = importlib.machinery.SourceFileLoader("houkago_ops_reconcile_worker", str(SCRIPT))
SPEC = importlib.util.spec_from_loader(LOADER.name, LOADER)
MODULE = importlib.util.module_from_spec(SPEC)
LOADER.exec_module(MODULE)


def git(repository: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repository), *arguments],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True)
    return result.stdout.strip()


class FixtureWorker(MODULE.OpsReconcileWorker):
    def __init__(self, config):
        super().__init__(config)
        self.runtime_actions = []
        self.fail_validation = False
        self.fail_after_install = False
        self.interrupt_after_install = False

    def validate_staged(self, staged):
        if self.fail_validation:
            raise MODULE.ReconcileError("synthetic_validation_failure")
        if "compose.prod.yml" not in staged:
            raise MODULE.ReconcileError("missing_managed_compose")

    def validate_live(self, staged, changed=None):
        for item in self.config.managed_files():
            source = staged.get(item.source)
            if source is None:
                continue
            if MODULE.sha256_file(source) != MODULE.sha256_file(item.target):
                raise MODULE.ReconcileError("managed_install_mismatch")

    def apply_runtime_actions(self, changed):
        self.runtime_actions.append(tuple(item.source for item in changed))

    def install_changed(self, changed, staged):
        super().install_changed(changed, staged)
        if self.interrupt_after_install:
            raise MODULE.WorkerInterrupted("synthetic_interrupt")
        if self.fail_after_install:
            raise MODULE.ReconcileError("synthetic_install_failure")


class ValidationScopeWorker(MODULE.OpsReconcileWorker):
    def __init__(self, config):
        super().__init__(config)
        self.commands = []

    def validate_staged(self, staged):
        pass

    def run_command(self, arguments, **kwargs):
        self.commands.append(arguments)


class OpsReconcileWorkerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="houkago-ops-worker-test-")
        self.root = Path(self.temporary.name)
        self.source = self.root / "source"
        self.remote = self.root / "remote.git"
        self.server = self.root / "server"
        self.install_root = self.root / "installed"
        self.spool = self.root / "spool"
        self.state = self.root / "state"
        self.backups = self.root / "backups"
        self.locks = self.root / "locks"
        for path in (self.source, self.spool, self.state, self.backups, self.locks):
            path.mkdir(parents=True)
        for name in (".tmp", "incoming", "processing", "succeeded", "failed"):
            (self.spool / name).mkdir()
        (self.locks / "backend-maintenance.lock").touch()
        self.server_env = self.root / "server.env"
        self.release_env = self.root / "release.env"
        self.server_env.write_text("SYNTHETIC=true\n", encoding="utf-8")
        self.release_env.write_text("SYNTHETIC=true\n", encoding="utf-8")

        git(self.source, "init", "-b", "main")
        git(self.source, "config", "user.name", "Ops Worker Test")
        git(self.source, "config", "user.email", "ops-worker@example.invalid")
        self.config = MODULE.Config(
            spool_root=self.spool,
            state_root=self.state,
            backup_root=self.backups,
            server_root=self.server,
            server_git_user="",
            server_env=self.server_env,
            release_env=self.release_env,
            compose_project_name="server",
            maintenance_lock=self.locks / "backend-maintenance.lock",
            maintenance_lock_timeout=1,
            install_root=self.install_root)
        self.seed_managed_files("old")
        git(self.source, "add", ".")
        git(self.source, "commit", "-m", "old ops")
        self.old_revision = git(self.source, "rev-parse", "HEAD")
        git(self.source, "init", "--bare", str(self.remote))
        git(self.source, "remote", "add", "origin", str(self.remote))
        git(self.source, "push", "-u", "origin", "main")

        changed = self.source / "ops/systemd/houkago-ops-reconcile.service"
        changed.write_text("new ops service\n", encoding="utf-8")
        worker = self.source / "ops/ops-reconcile/houkago-ops-reconcile-worker"
        worker.write_text("#!/usr/bin/env python3\n# self update\n", encoding="utf-8")
        git(self.source, "add", ".")
        git(self.source, "commit", "-m", "new ops")
        self.new_revision = git(self.source, "rev-parse", "HEAD")
        git(self.source, "push", "origin", "main")

        git(self.root, "init", "-b", "main", str(self.server))
        git(self.server, "remote", "add", "origin", str(self.remote))
        git(self.server, "fetch", "origin", "main")
        git(self.server, "checkout", "-B", "main", self.old_revision)

        self.worker = FixtureWorker(self.config)
        self.install_revision(self.old_revision)
        with tempfile.TemporaryDirectory(dir=self.spool / ".tmp") as directory:
            staged = self.worker.stage_revision(self.old_revision, Path(directory))
            MODULE.atomic_json(
                self.worker.current_state_path,
                self.worker.build_state(self.old_revision, staged),
                0o600)

    def tearDown(self):
        self.temporary.cleanup()

    def seed_managed_files(self, marker: str):
        config = MODULE.Config(
            spool_root=Path("/tmp/unused"),
            state_root=Path("/tmp/unused"),
            backup_root=Path("/tmp/unused"),
            server_root=Path("/tmp/unused"),
            server_git_user="",
            server_env=Path("/tmp/unused"),
            release_env=Path("/tmp/unused"),
            compose_project_name="server",
            maintenance_lock=Path("/tmp/unused"),
            maintenance_lock_timeout=1,
            install_root=Path("/"))
        for item in config.managed_files():
            path = self.source / item.source
            path.parent.mkdir(parents=True, exist_ok=True)
            if item.source.endswith("ops-reconcile-worker"):
                content = "#!/usr/bin/env python3\n"
            elif item.kind == "worker":
                content = "#!/usr/bin/env bash\nset -eu\n"
            elif item.source == "compose.prod.yml":
                content = "services: {}\n"
            else:
                content = f"{marker} {item.source}\n"
            path.write_text(content, encoding="utf-8")

    def install_revision(self, revision: str):
        with tempfile.TemporaryDirectory(dir=self.spool / ".tmp") as directory:
            staged = self.worker.stage_revision(revision, Path(directory))
            for item in self.config.managed_files():
                if item.source in staged:
                    MODULE.atomic_install(staged[item.source], item.target, item.mode)

    def write_job(self, directory: Path, revision: str | None = None) -> tuple[str, Path]:
        delivery_id = str(uuid.uuid4())
        now = dt.datetime.now(dt.timezone.utc) - dt.timedelta(seconds=10)
        job = {
            "deliveryId": delivery_id,
            "revision": revision or self.new_revision,
            "receivedAt": now.isoformat().replace("+00:00", "Z"),
            "notBefore": (now + dt.timedelta(seconds=5)).isoformat().replace("+00:00", "Z"),
        }
        path = directory / f"{delivery_id}.json"
        path.write_text(json.dumps(job), encoding="utf-8")
        return delivery_id, path

    def current_revision(self) -> str:
        return self.worker.load_state(self.worker.current_state_path)["revision"]

    def test_success_installs_only_changed_allowlist_and_updates_separate_state(self):
        delivery_id, _ = self.write_job(self.worker.incoming)

        self.assertEqual(0, self.worker.run())

        self.assertTrue((self.worker.succeeded / f"{delivery_id}.json").is_file())
        self.assertEqual(self.new_revision, self.current_revision())
        self.assertEqual(
            self.old_revision,
            self.worker.load_state(self.worker.previous_state_path)["revision"])
        self.assertEqual(self.new_revision, git(self.server, "rev-parse", "HEAD"))
        self.assertIn(
            "new ops service",
            self.config.installed("/etc/systemd/system/houkago-ops-reconcile.service").read_text())
        self.assertIn(
            "self update",
            self.config.installed("/usr/local/sbin/houkago-ops-reconcile-worker").read_text())
        changed = set(self.worker.runtime_actions[-1])
        self.assertEqual({
            "ops/systemd/houkago-ops-reconcile.service",
            "ops/ops-reconcile/houkago-ops-reconcile-worker",
        }, changed)

    def test_validation_failure_changes_no_live_file_or_state(self):
        delivery_id, _ = self.write_job(self.worker.incoming)
        target = self.config.installed("/etc/systemd/system/houkago-ops-reconcile.service")
        before = target.read_bytes()
        self.worker.fail_validation = True

        self.assertEqual(1, self.worker.run())

        self.assertEqual(before, target.read_bytes())
        self.assertEqual(self.old_revision, self.current_revision())
        self.assertTrue((self.worker.failed / f"{delivery_id}.json").is_file())
        self.assertEqual(self.old_revision, git(self.server, "rev-parse", "HEAD"))

    def test_live_nginx_validation_only_runs_for_nginx_changes(self):
        worker = ValidationScopeWorker(self.config)
        with tempfile.TemporaryDirectory(dir=self.spool / ".tmp") as directory:
            staged = worker.stage_revision(self.old_revision, Path(directory))
            workers = tuple(item for item in self.config.managed_files() if item.kind == "worker")
            nginx = tuple(item for item in self.config.managed_files() if item.kind == "nginx")

            worker.validate_live(staged, workers)
            self.assertNotIn(["nginx", "-t"], worker.commands)

            worker.validate_live(staged, nginx)
            self.assertIn(["nginx", "-t"], worker.commands)

    def test_install_failure_restores_live_files_and_previous_state(self):
        delivery_id, _ = self.write_job(self.worker.incoming)
        target = self.config.installed("/etc/systemd/system/houkago-ops-reconcile.service")
        before = target.read_bytes()
        self.worker.fail_after_install = True

        self.assertEqual(1, self.worker.run())

        self.assertEqual(before, target.read_bytes())
        self.assertEqual(self.old_revision, self.current_revision())
        self.assertFalse(self.worker.previous_state_path.exists())
        self.assertTrue((self.worker.failed / f"{delivery_id}.json").is_file())
        self.assertEqual(self.new_revision, git(self.server, "rev-parse", "HEAD"))
        manifests = list(self.backups.glob("*/manifest.json"))
        self.assertEqual(1, len(manifests))
        manifest = json.loads(manifests[0].read_text())
        self.assertEqual(self.old_revision, manifest["previousOpsRevision"])

    def test_drift_blocks_reconcile_before_checkout_update(self):
        delivery_id, _ = self.write_job(self.worker.incoming)
        target = self.config.installed("/etc/systemd/system/houkago-content-sync.path")
        target.write_text("operator drift\n", encoding="utf-8")

        self.assertEqual(1, self.worker.run())

        self.assertEqual(self.old_revision, self.current_revision())
        self.assertEqual(self.old_revision, git(self.server, "rev-parse", "HEAD"))
        self.assertTrue((self.worker.failed / f"{delivery_id}.json").is_file())

    def test_domain_lock_contention_leaves_job_queued(self):
        delivery_id, path = self.write_job(self.worker.incoming)
        lock_path = self.spool / "worker.lock"
        with lock_path.open("a+") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.assertEqual(0, self.worker.run())
        self.assertTrue(path.is_file())
        self.assertFalse((self.worker.processing / f"{delivery_id}.json").exists())

    def test_interruption_restores_then_startup_requeues_orphan(self):
        delivery_id, path = self.write_job(self.worker.incoming)
        self.worker.interrupt_after_install = True

        with self.assertRaises(MODULE.WorkerInterrupted):
            self.worker.process_job(path)

        self.assertEqual(self.old_revision, self.current_revision())
        self.assertTrue((self.worker.processing / f"{delivery_id}.json").is_file())
        recovery_worker = FixtureWorker(self.config)
        self.assertEqual(0, recovery_worker.recover_orphans())
        self.assertTrue((recovery_worker.incoming / f"{delivery_id}.json").is_file())

    def test_duplicate_terminal_state_fails_without_overwrite(self):
        delivery_id, path = self.write_job(self.worker.incoming)
        terminal = self.worker.succeeded / f"{delivery_id}.json"
        terminal.write_text("terminal", encoding="utf-8")

        self.assertFalse(self.worker.process_job(path))

        self.assertEqual("terminal", terminal.read_text(encoding="utf-8"))
        self.assertTrue(any(self.worker.failed.glob(f"{delivery_id}*.json")))


if __name__ == "__main__":
    unittest.main(verbosity=2)
