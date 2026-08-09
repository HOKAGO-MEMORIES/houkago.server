#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly WORKER_SCRIPT="${SCRIPT_DIRECTORY}/houkago-content-sync-worker"
readonly SERVICE_UNIT="${SCRIPT_DIRECTORY}/../systemd/houkago-content-sync.service"
readonly TEMPORARY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/houkago-worker-test.XXXXXX")"
readonly REMOTE_REPOSITORY="${TEMPORARY_ROOT}/remote.git"
readonly SEED_REPOSITORY="${TEMPORARY_ROOT}/seed"
readonly CHECKOUT_REPOSITORY="${TEMPORARY_ROOT}/checkout"
readonly SPOOL_ROOT_FIXTURE="${TEMPORARY_ROOT}/spool"
readonly SERVER_ROOT_FIXTURE="${TEMPORARY_ROOT}/server"
readonly COMPOSE_FILE_FIXTURE="${SERVER_ROOT_FIXTURE}/compose.prod.yml"
readonly SERVER_ENV_FIXTURE="${TEMPORARY_ROOT}/server.env"
readonly SHA="2222222222222222222222222222222222222222"
readonly OLD_SHA="1111111111111111111111111111111111111111"

cleanup() {
	rm -rf -- "$TEMPORARY_ROOT"
}
trap cleanup EXIT

fail() {
	printf 'FAIL: %s\n' "$*" >&2
	exit 1
}

mode_of() {
	local path="$1"
	stat -c '%a' "$path" 2>/dev/null || stat -f '%Lp' "$path"
}

assert_equals() {
	local expected="$1"
	local actual="$2"
	local message="$3"
	[[ "$actual" == "$expected" ]] || fail "${message}: expected=${expected} actual=${actual}"
}

assert_file() {
	[[ -f "$1" ]] || fail "missing file: $1"
}

initialize_git_fixture() {
	git init --bare --quiet "$REMOTE_REPOSITORY"
	git init --quiet -b main "$SEED_REPOSITORY"
	git -C "$SEED_REPOSITORY" config user.name "Houkago Worker Test"
	git -C "$SEED_REPOSITORY" config user.email "worker-test@example.invalid"
	mkdir -p "${SEED_REPOSITORY}/algorithm/boj/1"
	printf 'initial\n' > "${SEED_REPOSITORY}/algorithm/boj/1/index.md"
	git -C "$SEED_REPOSITORY" add .
	git -C "$SEED_REPOSITORY" commit --quiet -m "initial"
	git -C "$SEED_REPOSITORY" remote add origin "$REMOTE_REPOSITORY"
	git -C "$SEED_REPOSITORY" push --quiet -u origin main
	git --git-dir="$REMOTE_REPOSITORY" symbolic-ref HEAD refs/heads/main
	git clone --quiet "$REMOTE_REPOSITORY" "$CHECKOUT_REPOSITORY"

	printf 'updated\n' > "${SEED_REPOSITORY}/algorithm/boj/1/index.md"
	mkdir -p "${SEED_REPOSITORY}/algorithm/boj/2"
	printf 'new\n' > "${SEED_REPOSITORY}/algorithm/boj/2/index.md"
	git -C "$SEED_REPOSITORY" add .
	git -C "$SEED_REPOSITORY" commit --quiet -m "update"
	git -C "$SEED_REPOSITORY" push --quiet
}

initialize_spool_fixture() {
	local directory
	mkdir -p "$SPOOL_ROOT_FIXTURE" "$SERVER_ROOT_FIXTURE"
	for directory in .tmp incoming processing succeeded failed; do
		mkdir -p "${SPOOL_ROOT_FIXTURE}/${directory}"
		chmod 0770 "${SPOOL_ROOT_FIXTURE}/${directory}"
	done
	printf 'services: {}\n' > "$COMPOSE_FILE_FIXTURE"
	: > "$SERVER_ENV_FIXTURE"
}

initialize_git_fixture
initialize_spool_fixture

export HOUKAGO_SYNC_SPOOL_ROOT="$SPOOL_ROOT_FIXTURE"
export HOUKAGO_POSTS_ROOT="$CHECKOUT_REPOSITORY"
export HOUKAGO_SERVER_ROOT="$SERVER_ROOT_FIXTURE"
export HOUKAGO_SERVER_ENV="$SERVER_ENV_FIXTURE"
export HOUKAGO_COMPOSE_FILE="$COMPOSE_FILE_FIXTURE"

# shellcheck source=houkago-content-sync-worker
source "$WORKER_SCRIPT"

test_checkout_umask_scope() {
	local origin_main
	(
		umask 0007
		run_checkout_git fetch --quiet origin main
		origin_main="$(git -C "$CHECKOUT_REPOSITORY" rev-parse refs/remotes/origin/main)"
		run_checkout_git merge --ff-only --quiet "$origin_main"
		: > "${TEMPORARY_ROOT}/after-git-file"
		mkdir "${TEMPORARY_ROOT}/after-git-directory"
	)

	assert_equals "644" "$(mode_of "${CHECKOUT_REPOSITORY}/algorithm/boj/1/index.md")" \
		"updated tracked file must be readable"
	assert_equals "644" "$(mode_of "${CHECKOUT_REPOSITORY}/algorithm/boj/2/index.md")" \
		"new tracked file must be readable"
	assert_equals "755" "$(mode_of "${CHECKOUT_REPOSITORY}/algorithm/boj/2")" \
		"new checkout directory must be traversable"
	assert_equals "660" "$(mode_of "${TEMPORARY_ROOT}/after-git-file")" \
		"checkout umask must not escape its subshell"
	assert_equals "770" "$(mode_of "${TEMPORARY_ROOT}/after-git-directory")" \
		"directory umask must remain restrictive outside Git"
}

test_readability_guard_uses_sync_service() {
	local fake_bin="${TEMPORARY_ROOT}/fake-bin"
	local docker_arguments="${TEMPORARY_ROOT}/docker-arguments"
	local output
	mkdir -p "$fake_bin"
	cat > "${fake_bin}/docker" <<EOF
#!/bin/sh
printf '%s\n' "\$*" > "$docker_arguments"
printf 'HOUKAGO_UNREADABLE_PATH=algorithm/boj/2461/index.md\n'
exit 1
EOF
	chmod 0755 "${fake_bin}/docker"

	if output="$(PATH="${fake_bin}:$PATH" verify_checkout_readable 2>&1)"; then
		fail "readability guard unexpectedly succeeded"
	fi
	[[ "$output" == *"HOUKAGO_UNREADABLE_PATH=algorithm/boj/2461/index.md"* ]] \
		|| fail "readability guard did not preserve the failing relative path"
	grep -Fq -- '--no-deps -T --entrypoint /bin/sh sync' "$docker_arguments" \
		|| fail "readability guard did not use the sync service contract"
}

reset_spool_files() {
	local directory
	for directory in .tmp incoming processing succeeded failed; do
		rm -f -- "${SPOOL_ROOT}/${directory}"/*
	done
}

test_process_failure_case() (
	local scenario="$1"
	local delivery_id="$2"
	local incoming_path="${INCOMING_DIRECTORY}/${delivery_id}.json"
	local sync_marker="${TEMPORARY_ROOT}/sync-${scenario}-called"

	reset_spool_files
	printf '{}\n' > "$incoming_path"
	chmod 0640 "$incoming_path"

	validate_job() {
		printf '%s\n%s\n' "$delivery_id" "$SHA"
	}
	git() {
		case "$*" in
			*"status --porcelain"*)
				[[ "$scenario" == "dirty" ]] && printf ' M index.md\n'
				return 0
				;;
			*"branch --show-current"*) printf 'main\n' ;;
			*"fetch --quiet origin main"*) return 0 ;;
			*"cat-file -e"*) [[ "$scenario" != "unknown" ]] ;;
			*"rev-parse refs/remotes/origin/main"*) printf '%s\n' "$SHA" ;;
			*"rev-parse HEAD"*)
				if [[ "$scenario" == "nonff" ]]; then printf '%s\n' "$OLD_SHA"; else printf '%s\n' "$SHA"; fi
				;;
			*"merge-base --is-ancestor ${OLD_SHA} ${SHA}"*) return 1 ;;
			*"merge-base --is-ancestor"*) return 0 ;;
			*"merge --ff-only --quiet"*) return 0 ;;
			*) fail "unexpected mocked git invocation: $*" ;;
		esac
	}
	verify_checkout_readable() {
		printf 'HOUKAGO_UNREADABLE_PATH=algorithm/boj/2461/index.md\n'
		return 1
	}
	run_one_shot_sync() {
		: > "$sync_marker"
		return 0
	}

	if process_job "$incoming_path" >/dev/null 2>&1; then
		fail "${scenario} case unexpectedly succeeded"
	fi
	assert_file "${FAILED_DIRECTORY}/${delivery_id}.json"
	[[ ! -e "$sync_marker" ]] || fail "${scenario} case invoked one-shot sync"
)

test_operator_retry_preserves_history() {
	local delivery_id="55555555-5555-4555-8555-555555555555"
	local failed_path="${FAILED_DIRECTORY}/${delivery_id}.json"
	local history_path

	reset_spool_files
	printf '{"deliveryId":"%s","commitSha":"%s","receivedAt":"2026-08-09T15:05:16.293Z"}\n' \
		"$delivery_id" "$SHA" > "$failed_path"
	chmod 0640 "$failed_path"
	retry_failed_job "$delivery_id" >/dev/null

	assert_file "${INCOMING_DIRECTORY}/${delivery_id}.json"
	history_path="$(find "$FAILED_DIRECTORY" -maxdepth 1 -type f \
		-name "${delivery_id}.operator-retry-*.json" -print -quit)"
	assert_file "$history_path"
	assert_equals "640" "$(mode_of "${INCOMING_DIRECTORY}/${delivery_id}.json")" \
		"retried job permission"
	assert_equals "640" "$(mode_of "$history_path")" "retry history permission"
}

test_restrictive_service_contract() {
	local lock_path="${SPOOL_ROOT_FIXTURE}/worker.test.lock"
	(
		umask 0007
		: > "$lock_path"
	)
	assert_equals "660" "$(mode_of "$lock_path")" "lock permission"
	grep -Eq '^UMask=0*007$' "$SERVICE_UNIT" || fail "service UMask is not restrictive"
	! grep -Fq 'chmod -R' "$WORKER_SCRIPT" || fail "worker must not recursively chmod the checkout"
}

test_checkout_umask_scope
test_readability_guard_uses_sync_service
test_process_failure_case "dirty" "11111111-1111-4111-8111-111111111111"
test_process_failure_case "unknown" "22222222-2222-4222-8222-222222222222"
test_process_failure_case "nonff" "33333333-3333-4333-8333-333333333333"
test_process_failure_case "unreadable" "44444444-4444-4444-8444-444444444444"
test_operator_retry_preserves_history
test_restrictive_service_contract

printf 'All content sync worker tests passed.\n'
