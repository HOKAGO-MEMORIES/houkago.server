#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly WORKER_SCRIPT="${SCRIPT_DIRECTORY}/houkago-backend-deploy-worker"
readonly SERVICE_UNIT="${SCRIPT_DIRECTORY}/../systemd/houkago-backend-deploy.service"
readonly TEMPORARY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/houkago-deploy-worker-test.XXXXXX")"
readonly SPOOL_ROOT_FIXTURE="${TEMPORARY_ROOT}/deploy-jobs"
readonly STATE_ROOT_FIXTURE="${TEMPORARY_ROOT}/deploy-state"
readonly SERVER_ROOT_FIXTURE="${TEMPORARY_ROOT}/server"
readonly SERVER_ENV_FIXTURE="${TEMPORARY_ROOT}/server.env"
readonly RELEASE_ENV_FIXTURE="${TEMPORARY_ROOT}/backend-release.env"
readonly PREVIOUS_RELEASE_ENV_FIXTURE="${STATE_ROOT_FIXTURE}/previous-release.env"
readonly MAINTENANCE_LOCK_FIXTURE="${TEMPORARY_ROOT}/backend-maintenance.lock"
readonly WORKER_ENV_FIXTURE="${TEMPORARY_ROOT}/backend-deploy-worker.env"
readonly IMAGE_REPOSITORY_FIXTURE="ghcr.io/hokago-memories/houkago.server"
readonly OLD_REVISION="1111111111111111111111111111111111111111"
readonly NEW_REVISION="2222222222222222222222222222222222222222"
readonly NEXT_REVISION="3333333333333333333333333333333333333333"
readonly OLD_IMAGE="${IMAGE_REPOSITORY_FIXTURE}@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
readonly NEW_IMAGE="${IMAGE_REPOSITORY_FIXTURE}@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
readonly NEXT_IMAGE="${IMAGE_REPOSITORY_FIXTURE}@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

cleanup_fixture() {
	rm -rf -- "$TEMPORARY_ROOT"
}
trap cleanup_fixture EXIT

fail() {
	printf 'FAIL: %s\n' "$*" >&2
	exit 1
}

assert_file() {
	[[ -f "$1" ]] || fail "missing file: $1"
}

assert_equals() {
	local expected="$1" actual="$2" message="$3"
	[[ "$expected" == "$actual" ]] || fail "${message}: expected=${expected} actual=${actual}"
}

write_release() {
	local path="$1" image="$2" revision="$3"
	cat > "$path" <<EOF
HOUKAGO_SERVER_IMAGE=${image}
HOUKAGO_SERVER_REVISION=${revision}
HOUKAGO_SERVER_SCHEMA_COMPATIBILITY=unchanged
EOF
	chmod 0644 "$path"
}

write_job() {
	local directory="$1" delivery_id="$2" revision="$3" image="$4"
	cat > "${directory}/${delivery_id}.json" <<EOF
{"deliveryId":"${delivery_id}","revision":"${revision}","image":"${image}","receivedAt":"2026-08-13T00:00:00Z","notBefore":"2026-08-13T00:00:05Z"}
EOF
	chmod 0640 "${directory}/${delivery_id}.json"
}

reset_spool() {
	local directory
	for directory in .tmp incoming processing succeeded failed; do
		find "${SPOOL_ROOT_FIXTURE}/${directory}" -maxdepth 1 -type f -delete
	done
	current_processing_path=""
	temporary_paths=()
	failures=0
	queue_blocked=false
}

mkdir -p "$SERVER_ROOT_FIXTURE" "$STATE_ROOT_FIXTURE"
for directory in .tmp incoming processing succeeded failed; do
	mkdir -p "${SPOOL_ROOT_FIXTURE}/${directory}"
done
: > "$SERVER_ENV_FIXTURE"
: > "$WORKER_ENV_FIXTURE"
printf 'services: {}\n' > "${SERVER_ROOT_FIXTURE}/compose.prod.yml"
write_release "$RELEASE_ENV_FIXTURE" "$OLD_IMAGE" "$OLD_REVISION"

export HOUKAGO_DEPLOY_WORKER_ENV_FILE="$WORKER_ENV_FIXTURE"
export HOUKAGO_DEPLOY_SPOOL_ROOT="$SPOOL_ROOT_FIXTURE"
export HOUKAGO_DEPLOY_STATE_ROOT="$STATE_ROOT_FIXTURE"
export HOUKAGO_SERVER_ROOT="$SERVER_ROOT_FIXTURE"
export HOUKAGO_SERVER_ENV="$SERVER_ENV_FIXTURE"
export HOUKAGO_RELEASE_ENV="$RELEASE_ENV_FIXTURE"
export HOUKAGO_PREVIOUS_RELEASE_ENV="$PREVIOUS_RELEASE_ENV_FIXTURE"
export HOUKAGO_MAINTENANCE_LOCK_FILE="$MAINTENANCE_LOCK_FIXTURE"
export HOUKAGO_MAINTENANCE_LOCK_TIMEOUT_SECONDS=1
export HOUKAGO_DEPLOY_STARTUP_ATTEMPTS=1
export HOUKAGO_DEPLOY_STARTUP_INTERVAL_SECONDS=0
export HOUKAGO_IMAGE_REPOSITORY="$IMAGE_REPOSITORY_FIXTURE"

flock_mode=success
flock() {
	[[ "$flock_mode" != "busy" ]]
}

# shellcheck source=houkago-backend-deploy-worker
source "$WORKER_SCRIPT"

test_job_validation() {
	local delivery_id="11111111-1111-4111-8111-111111111111"
	local output
	write_job "$INCOMING_DIRECTORY" "$delivery_id" "$NEW_REVISION" "$NEW_IMAGE"
	output="$(validate_job "${INCOMING_DIRECTORY}/${delivery_id}.json")" || fail "valid job rejected"
	assert_equals "$delivery_id" "$(printf '%s\n' "$output" | sed -n '1p')" "delivery id"
	assert_equals "$NEW_REVISION" "$(printf '%s\n' "$output" | sed -n '2p')" "revision"
	assert_equals "$NEW_IMAGE" "$(printf '%s\n' "$output" | sed -n '3p')" "image"

	printf '{"deliveryId":"%s","revision":"%s","image":"%s:latest","receivedAt":"2026-08-13T00:00:00Z","notBefore":"2026-08-13T00:00:05Z"}\n' \
		"$delivery_id" "$NEW_REVISION" "$IMAGE_REPOSITORY_FIXTURE" > "${INCOMING_DIRECTORY}/${delivery_id}.json"
	if validate_job "${INCOMING_DIRECTORY}/${delivery_id}.json" >/dev/null 2>&1; then
		fail "mutable tag accepted"
	fi
	printf '{"deliveryId":"%s","revision":"%s","image":"ghcr.io/other/houkago.server@sha256:%s","receivedAt":"2026-08-13T00:00:00Z","notBefore":"2026-08-13T00:00:05Z"}\n' \
		"$delivery_id" "$NEW_REVISION" "$(printf 'b%.0s' {1..64})" > "${INCOMING_DIRECTORY}/${delivery_id}.json"
	if validate_job "${INCOMING_DIRECTORY}/${delivery_id}.json" >/dev/null 2>&1; then
		fail "wrong repository accepted"
	fi
	printf '{"deliveryId":"%s","revision":"%s","image":"%s@sha256:short","receivedAt":"2026-08-13T00:00:00Z","notBefore":"2026-08-13T00:00:05Z"}\n' \
		"$delivery_id" "$NEW_REVISION" "$IMAGE_REPOSITORY_FIXTURE" > "${INCOMING_DIRECTORY}/${delivery_id}.json"
	if validate_job "${INCOMING_DIRECTORY}/${delivery_id}.json" >/dev/null 2>&1; then
		fail "invalid digest accepted"
	fi
	reset_spool
}

test_successful_deploy_and_response_grace_order() (
	local delivery_id="22222222-2222-4222-8222-222222222222"
	local output grace_line recreate_line
	write_release "$RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	rm -f -- "$PREVIOUS_RELEASE_ENV"
	reset_spool
	write_job "$INCOMING_DIRECTORY" "$delivery_id" "$NEW_REVISION" "$NEW_IMAGE"
	wait_until_not_before() {
		log "phase=request_grace status=COMPLETE receivedAt=$1 notBefore=$2 readyAt=2026-08-13T00:00:06Z"
	}
	verify_migration_gate() { return 0; }
	verify_artifact() { return 0; }
	verify_compose_resolution() { [[ "$2" == "$NEW_IMAGE" ]]; }
	container_id() { printf 'mysql-id\n'; }
	recreate_app() {
		log "phase=app_recreate status=STARTED at=2026-08-13T00:00:07Z image=$2"
		return 0
	}

	output="$(process_job "${INCOMING_DIRECTORY}/${delivery_id}.json")" || fail "valid deploy failed"
	assert_file "${SUCCEEDED_DIRECTORY}/${delivery_id}.json"
	assert_equals "$NEW_IMAGE" "$(validate_release_env "$RELEASE_ENV" | sed -n '1p')" "current image"
	assert_equals "$OLD_IMAGE" "$(validate_release_env "$PREVIOUS_RELEASE_ENV" | sed -n '1p')" "previous image"
	grace_line="$(printf '%s\n' "$output" | grep -n 'phase=request_grace' | cut -d: -f1)"
	recreate_line="$(printf '%s\n' "$output" | grep -n 'phase=app_recreate' | cut -d: -f1)"
	(( grace_line < recreate_line )) || fail "app recreate started before request grace completed"
)

test_same_release_is_noop() (
	local delivery_id="88888888-8888-4888-8888-888888888888"
	local output
	write_release "$RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	rm -f -- "$PREVIOUS_RELEASE_ENV"
	reset_spool
	write_job "$INCOMING_DIRECTORY" "$delivery_id" "$OLD_REVISION" "$OLD_IMAGE"
	wait_until_not_before() { return 0; }
	verify_migration_gate() { fail "same release checked migrations"; }
	verify_artifact() { fail "same release pulled an artifact"; }
	verify_compose_resolution() { fail "same release resolved Compose"; }
	recreate_app() { fail "same release recreated app"; }

	output="$(process_job "${INCOMING_DIRECTORY}/${delivery_id}.json")" || fail "same release failed"
	assert_file "${SUCCEEDED_DIRECTORY}/${delivery_id}.json"
	assert_equals "$OLD_IMAGE" "$(validate_release_env "$RELEASE_ENV" | sed -n '1p')" "same release current image"
	assert_equals "$OLD_REVISION" "$(validate_release_env "$RELEASE_ENV" | sed -n '2p')" "same release current revision"
	[[ ! -e "$PREVIOUS_RELEASE_ENV" ]] || fail "same release created previous state"
	printf '%s\n' "$output" | grep -Fq 'operation=already_current' \
		|| fail "same release no-op was not logged"
)

test_migration_gate_is_fail_closed() (
	local delivery_id="33333333-3333-4333-8333-333333333333"
	local recreate_marker="${TEMPORARY_ROOT}/migration-recreate-called"
	write_release "$RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	rm -f -- "$PREVIOUS_RELEASE_ENV"
	reset_spool
	write_job "$INCOMING_DIRECTORY" "$delivery_id" "$NEW_REVISION" "$NEW_IMAGE"
	wait_until_not_before() { return 0; }
	verify_migration_gate() { return 2; }
	verify_artifact() { fail "migration-gated job pulled an artifact"; }
	recreate_app() { : > "$recreate_marker"; }

	if process_job "${INCOMING_DIRECTORY}/${delivery_id}.json" >/dev/null; then
		fail "migration-gated job succeeded"
	fi
	assert_file "${FAILED_DIRECTORY}/${delivery_id}.json"
	[[ ! -e "$recreate_marker" ]] || fail "migration-gated job recreated app"
)

test_health_failure_rolls_back_without_changing_release() (
	local delivery_id="44444444-4444-4444-8444-444444444444"
	local call_count_file="${TEMPORARY_ROOT}/recreate-count"
	write_release "$RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	rm -f -- "$PREVIOUS_RELEASE_ENV"
	reset_spool
	write_job "$INCOMING_DIRECTORY" "$delivery_id" "$NEW_REVISION" "$NEW_IMAGE"
	printf '0\n' > "$call_count_file"
	wait_until_not_before() { return 0; }
	verify_migration_gate() { return 0; }
	verify_artifact() { return 0; }
	verify_compose_resolution() { return 0; }
	container_id() { printf 'mysql-id\n'; }
	recreate_app() {
		local count
		count="$(cat "$call_count_file")"
		count=$((count + 1))
		printf '%s\n' "$count" > "$call_count_file"
		[[ "$count" -gt 1 ]]
	}

	if process_job "${INCOMING_DIRECTORY}/${delivery_id}.json" >/dev/null; then
		fail "unhealthy candidate succeeded"
	fi
	assert_file "${FAILED_DIRECTORY}/${delivery_id}.json"
	assert_equals "2" "$(cat "$call_count_file")" "candidate plus rollback recreate count"
	assert_equals "$OLD_IMAGE" "$(validate_release_env "$RELEASE_ENV" | sed -n '1p')" "release after rollback"
)

test_release_state_failure_restores_app_and_state() (
	local processing_path="${PROCESSING_DIRECTORY}/77777777-7777-4777-8777-777777777777.json"
	local candidate_env="${TEMPORARY_DIRECTORY}/candidate-release-fixture"
	local atomic_count_file="${TEMPORARY_ROOT}/atomic-count"
	local rollback_marker="${TEMPORARY_ROOT}/release-state-rollback-called"
	reset_spool
	write_release "$RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	write_release "$PREVIOUS_RELEASE_ENV" "$NEXT_IMAGE" "$NEXT_REVISION"
	write_release "$candidate_env" "$NEW_IMAGE" "$NEW_REVISION"
	write_job "$PROCESSING_DIRECTORY" "77777777-7777-4777-8777-777777777777" "$NEW_REVISION" "$NEW_IMAGE"
	printf '0\n' > "$atomic_count_file"
	atomic_install_release() {
		local count
		count="$(cat "$atomic_count_file")"
		count=$((count + 1))
		printf '%s\n' "$count" > "$atomic_count_file"
		[[ "$count" -ne 2 ]] || return 1
		cp -- "$1" "$2"
	}
	rollback_release() {
		assert_equals "$OLD_IMAGE" "$(validate_release_env "$1" | sed -n '1p')" "rollback image"
		: > "$rollback_marker"
	}

	if finalize_candidate_release \
			"$candidate_env" \
			"$processing_path" \
			"${SUCCEEDED_DIRECTORY}/$(basename "$processing_path")" \
			"mysql-id"; then
		fail "release-state failure succeeded"
	fi
	assert_file "$rollback_marker"
	assert_equals "$OLD_IMAGE" "$(validate_release_env "$RELEASE_ENV" | sed -n '1p')" "restored current image"
	assert_equals "$NEXT_IMAGE" "$(validate_release_env "$PREVIOUS_RELEASE_ENV" | sed -n '1p')" "restored previous image"
	assert_file "$processing_path"
)

test_maintenance_lock_contention_keeps_job_queued() (
	local delivery_id="55555555-5555-4555-8555-555555555555"
	write_release "$RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	rm -f -- "$PREVIOUS_RELEASE_ENV"
	reset_spool
	write_job "$INCOMING_DIRECTORY" "$delivery_id" "$NEW_REVISION" "$NEW_IMAGE"
	wait_until_not_before() { return 0; }
	flock_mode=busy
	process_job "${INCOMING_DIRECTORY}/${delivery_id}.json" >/dev/null \
		|| fail "lock contention should leave a queued job"
	assert_file "${INCOMING_DIRECTORY}/${delivery_id}.json"
	[[ ! -e "${PROCESSING_DIRECTORY}/${delivery_id}.json" ]] || fail "contended job moved to processing"
)

test_cleanup_recovers_processing_job() (
	local delivery_id="66666666-6666-4666-8666-666666666666"
	reset_spool
	write_job "$PROCESSING_DIRECTORY" "$delivery_id" "$NEW_REVISION" "$NEW_IMAGE"
	current_processing_path="${PROCESSING_DIRECTORY}/${delivery_id}.json"
	cleanup
	assert_file "${FAILED_DIRECTORY}/${delivery_id}.json"
)

test_main_drains_multiple_jobs_in_order() (
	local first_id="88888888-8888-4888-8888-888888888888"
	local second_id="99999999-9999-4999-8999-999999999999"
	local order_file="${TEMPORARY_ROOT}/queue-order"
	reset_spool
	write_job "$INCOMING_DIRECTORY" "$second_id" "$NEXT_REVISION" "$NEXT_IMAGE"
	write_job "$INCOMING_DIRECTORY" "$first_id" "$NEW_REVISION" "$NEW_IMAGE"
	process_job() {
		local path="$1"
		printf '%s\n' "$(basename "$path")" >> "$order_file"
		mv -- "$path" "${SUCCEEDED_DIRECTORY}/$(basename "$path")"
	}
	main >/dev/null || fail "queue drain failed"
	assert_equals "${first_id}.json" "$(sed -n '1p' "$order_file")" "first queued job"
	assert_equals "${second_id}.json" "$(sed -n '2p' "$order_file")" "second queued job"
)

test_manual_rollback_swaps_release_state() (
	reset_spool
	write_release "$RELEASE_ENV" "$NEW_IMAGE" "$NEW_REVISION"
	write_release "$PREVIOUS_RELEASE_ENV" "$OLD_IMAGE" "$OLD_REVISION"
	container_id() { printf 'mysql-id\n'; }
	rollback_release() { return 0; }
	manual_rollback >/dev/null || fail "manual rollback failed"
	assert_equals "$OLD_IMAGE" "$(validate_release_env "$RELEASE_ENV" | sed -n '1p')" "rollback current image"
	assert_equals "$NEW_IMAGE" "$(validate_release_env "$PREVIOUS_RELEASE_ENV" | sed -n '1p')" "rollback previous image"
)

test_service_security_contract() {
	grep -Fxq 'User=root' "$SERVICE_UNIT" || fail "deploy worker service must own root release state"
	grep -Eq '^UMask=0*077$' "$SERVICE_UNIT" || fail "deploy worker UMask must be restrictive"
	grep -Fq 'ProtectSystem=strict' "$SERVICE_UNIT" || fail "missing systemd filesystem protection"
	grep -Fq '/opt/houkago/locks' "$SERVICE_UNIT" || fail "maintenance lock path is not writable"
	grep -Eq '^ReadWritePaths=.* /opt/houkago/env([[:space:]]|$)' "$SERVICE_UNIT" \
		|| fail "release env directory must be writable for atomic publication"
	grep -Eq '^ReadOnlyPaths=.* /opt/houkago/env/server\.env([[:space:]]|$)' "$SERVICE_UNIT" \
		|| fail "server secrets must remain read-only"
	grep -Eq '^ReadOnlyPaths=.* /opt/houkago/env/backend-deploy-worker\.env([[:space:]]|$)' "$SERVICE_UNIT" \
		|| fail "deploy worker secrets must remain read-only"
	grep -Eq '^ReadWritePaths=.* /opt/houkago/server/\.git([[:space:]]|$)' "$SERVICE_UNIT" \
		|| fail "server Git metadata must be writable for the migration gate fetch"
	grep -Eq '^ReadOnlyPaths=/opt/houkago/server([[:space:]]|$)' "$SERVICE_UNIT" \
		|| fail "server working tree must remain read-only"
	! grep -Fq 'docker compose down' "$WORKER_SCRIPT" || fail "worker must not stop the full stack"
	! grep -Eq '(^|[[:space:]])eval([[:space:]]|$)' "$WORKER_SCRIPT" || fail "worker must not eval payload"
}

test_compose_resolution_requires_shared_asset_sync_image() (
	local asset_image="$NEW_IMAGE"
	run_compose_with_release() {
		[[ " $* " == *" --profile sync --profile asset-sync config --format json "* ]] \
			|| fail "Compose resolution did not include both one-shot profiles"
		printf '{"services":{"app":{"image":"%s"},"sync":{"image":"%s"},"asset-sync":{"image":"%s"}}}\n' \
			"$NEW_IMAGE" "$NEW_IMAGE" "$asset_image"
	}

	verify_compose_resolution "$RELEASE_ENV" "$NEW_IMAGE" \
		|| fail "matching app/sync/asset-sync images were rejected"
	asset_image="$OLD_IMAGE"
	if verify_compose_resolution "$RELEASE_ENV" "$NEW_IMAGE"; then
		fail "mismatched asset-sync image was accepted"
	fi
)

test_job_validation
test_successful_deploy_and_response_grace_order
test_same_release_is_noop
test_migration_gate_is_fail_closed
test_health_failure_rolls_back_without_changing_release
test_release_state_failure_restores_app_and_state
test_maintenance_lock_contention_keeps_job_queued
test_cleanup_recovers_processing_job
test_main_drains_multiple_jobs_in_order
test_manual_rollback_swaps_release_state
test_service_security_contract
test_compose_resolution_requires_shared_asset_sync_image

printf 'All backend deploy worker tests passed.\n'
