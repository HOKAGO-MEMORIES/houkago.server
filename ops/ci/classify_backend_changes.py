#!/usr/bin/env python3

import argparse
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path


CLASSIFICATIONS = {
    "APP_ONLY",
    "OPS_ONLY",
    "APP_AND_OPS",
    "NO_PRODUCTION_IMPACT",
    "CONTROL_PLANE",
}
ZERO_SHA = "0" * 40
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")

APP_EXACT_PATHS = {
    "Dockerfile",
    "build.gradle.kts",
    "gradlew",
    "gradlew.bat",
    "settings.gradle.kts",
}
NO_PRODUCTION_EXACT_PATHS = {
    ".env.example",
    ".env.prod.example",
    ".gitattributes",
    ".gitignore",
    "AGENTS.md",
    "compose.dev.yml",
}
OPS_SUPPORT_FILE_NAMES = {
    "backend-deploy-worker.env.example",
    "backend-release.env.example",
    "content-sync-worker.env.example",
    "test-houkago-backend-deploy-worker.sh",
    "test-houkago-content-sync-worker.sh",
}


def run_git(repository: Path, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(repository), *arguments],
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )


def classify_path(path: str) -> tuple[str, bool]:
    if path == ".dockerignore" or path.startswith(".github/workflows/") or path.startswith("ops/ci/"):
        return "control", False

    migration = path.startswith("src/main/resources/db/migration/")
    if path in APP_EXACT_PATHS or path.startswith("gradle/") or path.startswith("src/main/"):
        return "app", migration

    if path == "compose.prod.yml" or path.startswith("ops/systemd/") or path.startswith("ops/nginx/"):
        return "ops", False

    if path.startswith("ops/content-sync/") or path.startswith("ops/backend-deploy/"):
        if Path(path).name in OPS_SUPPORT_FILE_NAMES:
            return "no_production", False
        return "ops", False

    if (
        path in NO_PRODUCTION_EXACT_PATHS
        or path.startswith("README")
        or path.startswith("src/test/")
    ):
        return "no_production", False

    return "unknown", False


def derive_classification(app_changed: bool, ops_changed: bool, control_plane_changed: bool) -> str:
    if control_plane_changed:
        return "CONTROL_PLANE"
    if app_changed and ops_changed:
        return "APP_AND_OPS"
    if app_changed:
        return "APP_ONLY"
    if ops_changed:
        return "OPS_ONLY"
    return "NO_PRODUCTION_IMPACT"


def classify_paths(base_sha: str, head_sha: str, paths: list[str], blocked_reason: str = "") -> dict:
    app_changed = False
    ops_changed = False
    control_plane_changed = bool(blocked_reason)
    migration_changed = False
    unknown_paths = []

    for path in sorted(set(paths)):
        category, migration = classify_path(path)
        if category == "app":
            app_changed = True
            migration_changed = migration_changed or migration
        elif category == "ops":
            ops_changed = True
        elif category == "control":
            control_plane_changed = True
        elif category == "unknown":
            control_plane_changed = True
            unknown_paths.append(path)

    return {
        "head_sha": head_sha,
        "base_sha": base_sha,
        "classification": derive_classification(app_changed, ops_changed, control_plane_changed),
        "app_changed": app_changed,
        "ops_changed": ops_changed,
        "control_plane_changed": control_plane_changed,
        "migration_changed": migration_changed,
        "changed_paths": sorted(set(paths)),
        "unknown_paths": unknown_paths,
        "blocked_reason": blocked_reason,
    }


def classify_repository(repository: Path, base_sha: str, head_sha: str) -> dict:
    normalized_base = base_sha.lower()
    normalized_head = head_sha.lower()
    if normalized_base == ZERO_SHA:
        return classify_paths(normalized_base, normalized_head, [], "zero_base_sha")
    if not SHA_PATTERN.fullmatch(normalized_base) or not SHA_PATTERN.fullmatch(normalized_head):
        return classify_paths(normalized_base, normalized_head, [], "invalid_sha")

    for revision in (normalized_base, normalized_head):
        result = run_git(repository, "cat-file", "-e", f"{revision}^{{commit}}", check=False)
        if result.returncode != 0:
            return classify_paths(normalized_base, normalized_head, [], "missing_commit")

    ancestor = run_git(repository, "merge-base", "--is-ancestor", normalized_base, normalized_head, check=False)
    if ancestor.returncode != 0:
        return classify_paths(normalized_base, normalized_head, [], "non_fast_forward_range")

    diff = run_git(
        repository,
        "diff",
        "--name-only",
        "--no-renames",
        normalized_base,
        normalized_head,
    )
    paths = [line for line in diff.stdout.splitlines() if line]
    return classify_paths(normalized_base, normalized_head, paths)


def write_json_atomic(output: Path, result: dict) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{output.name}.", dir=output.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as file:
            json.dump(result, file, sort_keys=True, separators=(",", ":"))
            file.write("\n")
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)


def validate_artifact(artifact: Path, expected_head: str) -> dict:
    try:
        result = json.loads(artifact.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exception:
        raise ValueError("classification artifact is unreadable") from exception

    required = {
        "head_sha": str,
        "base_sha": str,
        "classification": str,
        "app_changed": bool,
        "ops_changed": bool,
        "control_plane_changed": bool,
        "migration_changed": bool,
        "changed_paths": list,
        "unknown_paths": list,
        "blocked_reason": str,
    }
    if not isinstance(result, dict) or set(result) != set(required):
        raise ValueError("classification artifact fields are invalid")
    for field, expected_type in required.items():
        if not isinstance(result[field], expected_type):
            raise ValueError(f"classification artifact field has invalid type: {field}")

    expected = expected_head.lower()
    if not SHA_PATTERN.fullmatch(expected) or result["head_sha"] != expected:
        raise ValueError("classification artifact head SHA mismatch")
    if result["base_sha"] != ZERO_SHA and not SHA_PATTERN.fullmatch(result["base_sha"]):
        raise ValueError("classification artifact base SHA is invalid")
    if result["classification"] not in CLASSIFICATIONS:
        raise ValueError("classification artifact classification is unknown")
    if result["migration_changed"] and not result["app_changed"]:
        raise ValueError("migration change must also be an application change")
    if not all(isinstance(path, str) and path for path in result["changed_paths"]):
        raise ValueError("classification artifact changed paths are invalid")
    if not all(isinstance(path, str) and path for path in result["unknown_paths"]):
        raise ValueError("classification artifact unknown paths are invalid")
    if result["unknown_paths"] and not result["control_plane_changed"]:
        raise ValueError("unknown paths must block Production automation")

    derived = derive_classification(
        result["app_changed"],
        result["ops_changed"],
        result["control_plane_changed"],
    )
    if result["classification"] != derived:
        raise ValueError("classification artifact derived classification mismatch")
    return result


def write_github_output(path: Path, result: dict) -> None:
    with path.open("a", encoding="utf-8") as output:
        for field in (
            "head_sha",
            "base_sha",
            "classification",
            "app_changed",
            "ops_changed",
            "control_plane_changed",
            "migration_changed",
        ):
            output.write(f"{field}={str(result[field]).lower() if isinstance(result[field], bool) else result[field]}\n")
        output.write(f"unknown_path_count={len(result['unknown_paths'])}\n")
        output.write(f"blocked_reason={result['blocked_reason'] or 'none'}\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    classify_parser = subparsers.add_parser("classify")
    classify_parser.add_argument("--repository", type=Path, default=Path.cwd())
    classify_parser.add_argument("--base", required=True)
    classify_parser.add_argument("--head", required=True)
    classify_parser.add_argument("--output", type=Path, required=True)
    classify_parser.add_argument("--github-output", type=Path)

    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("--artifact", type=Path, required=True)
    validate_parser.add_argument("--expected-head", required=True)
    validate_parser.add_argument("--github-output", type=Path)

    arguments = parser.parse_args()
    try:
        if arguments.command == "classify":
            result = classify_repository(arguments.repository.resolve(), arguments.base, arguments.head)
            write_json_atomic(arguments.output, result)
        else:
            result = validate_artifact(arguments.artifact, arguments.expected_head)
        if arguments.github_output:
            write_github_output(arguments.github_output, result)
    except ValueError as exception:
        parser.error(str(exception))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
