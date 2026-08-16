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
readonly WORKER_ENV_FIXTURE="${TEMPORARY_ROOT}/content-sync-worker.env"
readonly ASSET_ROOT_FIXTURE="${TEMPORARY_ROOT}/assets"
readonly SHA="2222222222222222222222222222222222222222"
readonly OLD_SHA="1111111111111111111111111111111111111111"
readonly NEWER_SHA="3333333333333333333333333333333333333333"

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
	mkdir -p "$SPOOL_ROOT_FIXTURE" "$SERVER_ROOT_FIXTURE" "$ASSET_ROOT_FIXTURE"
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
export HOUKAGO_WORKER_ENV_FILE="$WORKER_ENV_FIXTURE"
export HOUKAGO_REVALIDATE_URL="https://frontend.example.invalid/api/internal/revalidate/posts"
export HOUKAGO_REVALIDATE_SECRET="synthetic-worker-secret"
export HOUKAGO_PUBLIC_ASSET_ROOT="$ASSET_ROOT_FIXTURE"
export HOUKAGO_PUBLIC_ASSET_BASE_URL="https://backend.example.invalid"

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
	grep -Fq -- '-name ".*"' "$docker_arguments" \
		|| fail "readability guard did not prune hidden directories"
	grep -Fq -- '-name assets' "$docker_arguments" \
		|| fail "readability guard did not prune post asset directories"
}

reset_spool_files() {
	local directory
	for directory in .tmp incoming processing succeeded failed; do
		rm -f -- "${SPOOL_ROOT}/${directory}"/*
	done
}

test_process_failure_case() (
	local scenario="$1"
	local fixture_delivery_id="$2"
	local incoming_path="${INCOMING_DIRECTORY}/${fixture_delivery_id}.json"
	local sync_marker="${TEMPORARY_ROOT}/sync-${scenario}-called"
	local asset_marker="${TEMPORARY_ROOT}/asset-${scenario}-called"

	reset_spool_files
	printf '{}\n' > "$incoming_path"
	chmod 0640 "$incoming_path"

	validate_job() {
		printf '%s\n%s\n' "$fixture_delivery_id" "$SHA"
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
	run_asset_stage() {
		: > "$asset_marker"
		return 0
	}

	if process_job "$incoming_path" >/dev/null 2>&1; then
		fail "${scenario} case unexpectedly succeeded"
	fi
	assert_file "${FAILED_DIRECTORY}/${fixture_delivery_id}.json"
	[[ ! -e "$sync_marker" ]] || fail "${scenario} case invoked one-shot sync"
	[[ ! -e "$asset_marker" ]] || fail "${scenario} case invoked asset stage"
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
	grep -Fq 'EnvironmentFile=-/opt/houkago/env/content-sync-worker.env' "$SERVICE_UNIT" \
		|| fail "service must load the dedicated worker environment"
	! grep -Fq 'chmod -R' "$WORKER_SCRIPT" || fail "worker must not recursively chmod the checkout"
	grep -Eq '^ReadOnlyPaths=.* /opt/houkago/assets$' "$SERVICE_UNIT" \
		|| fail "service must expose the asset root read-only for smoke inspection"
	! grep -Eq '^ReadWritePaths=.* /opt/houkago/assets([[:space:]]|$)' "$SERVICE_UNIT" \
		|| fail "host Worker must not receive direct asset-root write access"
}

set_revalidation_sequence() {
	local sequence_file="$1"
	shift
	printf '%s\n' "$@" > "$sequence_file"
}

test_revalidation_case() (
	local scenario="$1"
	local expected_attempts="$2"
	local expected_status="$3"
	shift 3
	local sequence_file="${TEMPORARY_ROOT}/revalidation-${scenario}.sequence"
	local attempts_file="${TEMPORARY_ROOT}/revalidation-${scenario}.attempts"
	local backoff_file="${TEMPORARY_ROOT}/revalidation-${scenario}.backoff"
	local output result=0

	set_revalidation_sequence "$sequence_file" "$@"
	: > "$attempts_file"
	: > "$backoff_file"

	perform_frontend_revalidation_request() {
		local response
		response="$(sed -n '1p' "$sequence_file")"
		sed '1d' "$sequence_file" > "${sequence_file}.next"
		mv "${sequence_file}.next" "$sequence_file"
		printf 'x' >> "$attempts_file"
		case "$response" in
			network) return 7 ;;
			*) printf '%s' "$response" ;;
		esac
	}
	revalidation_backoff() {
		printf 'x' >> "$backoff_file"
	}

	output="$(revalidate_frontend)" || result=$?
	assert_equals "$expected_attempts" "$(wc -c < "$attempts_file" | tr -d ' ')" \
		"${scenario} attempt count"
	[[ "$output" == *"status=${expected_status}"* ]] \
		|| fail "${scenario} did not log ${expected_status}"
	[[ "$output" != *"${HOUKAGO_REVALIDATE_SECRET}"* ]] \
		|| fail "${scenario} leaked the revalidation secret"

	if [[ "$expected_status" == "SUCCESS" ]]; then
		assert_equals "0" "$result" "${scenario} result"
	else
		[[ "$result" -ne 0 ]] || fail "${scenario} unexpectedly succeeded"
	fi
)

test_revalidation_request_contract() (
	local arguments_file="${TEMPORARY_ROOT}/revalidation-curl-arguments"
	local headers_file="${TEMPORARY_ROOT}/revalidation-curl-headers"
	local output

	curl() {
		cat > "$headers_file"
		printf '%s\n' "$@" > "$arguments_file"
		printf '200'
	}

	output="$(perform_frontend_revalidation_request)"
	assert_equals "200" "$output" "revalidation HTTP status"
	grep -Fxq -- '--proto' "$arguments_file" || fail "revalidation must restrict protocol"
	grep -Fxq -- '=https' "$arguments_file" || fail "revalidation must require HTTPS"
	grep -Fxq -- '--connect-timeout' "$arguments_file" || fail "missing connection timeout"
	grep -Fxq -- '--max-time' "$arguments_file" || fail "missing overall timeout"
	grep -Fxq -- '--header' "$arguments_file" || fail "missing header option"
	grep -Fxq -- '@-' "$arguments_file" || fail "Bearer header must be read from stdin"
	! grep -Fq -- "$HOUKAGO_REVALIDATE_SECRET" "$arguments_file" \
		|| fail "revalidation secret must not appear in process arguments"
	grep -Fxq -- 'Authorization: Bearer synthetic-worker-secret' "$headers_file" \
		|| fail "missing Bearer authorization header on stdin"
)

test_revalidation_failure_is_non_fatal() (
	local fixture_delivery_id="66666666-6666-4666-8666-666666666666"
	local incoming_path="${INCOMING_DIRECTORY}/${fixture_delivery_id}.json"
	local output

	reset_spool_files
	printf '{}\n' > "$incoming_path"
	chmod 0640 "$incoming_path"

	validate_job() { printf '%s\n%s\n' "$fixture_delivery_id" "$SHA"; }
	git() {
		case "$*" in
			*"status --porcelain"*) return 0 ;;
			*"branch --show-current"*) printf 'main\n' ;;
			*"fetch --quiet origin main"*) return 0 ;;
			*"cat-file -e"*) return 0 ;;
			*"rev-parse refs/remotes/origin/main"*) printf '%s\n' "$SHA" ;;
			*"rev-parse HEAD"*) printf '%s\n' "$SHA" ;;
			*"merge-base --is-ancestor"*) return 0 ;;
			*"merge --ff-only --quiet"*) return 0 ;;
			*) fail "unexpected mocked git invocation: $*" ;;
		esac
	}
	verify_checkout_readable() { return 0; }
	run_asset_stage() {
		ASSET_PUBLIC_POST_COUNT=1
		ASSET_COUNT=1
		ASSET_TOTAL_BYTES=10
		ASSET_SMOKE_PATH=/assets/posts/example-post/image.png
		return 0
	}
	run_one_shot_sync() { return 0; }
	run_asset_activate() { return 0; }
	verify_public_api() { return 0; }
	verify_public_asset() { return 0; }
	revalidate_frontend() {
		log "phase=frontend_revalidation status=WARNING attempts=3 reason=http_5xx"
		return 1
	}

	output="$(process_job "$incoming_path")" || fail "non-fatal revalidation failed the job"
	assert_file "${SUCCEEDED_DIRECTORY}/${fixture_delivery_id}.json"
	[[ "$output" == *"backendSync=SUCCESS assetPublication=SUCCESS reason=non_fatal"* ]] \
		|| fail "non-fatal revalidation result was not logged"
)

test_manual_revalidation_only_calls_endpoint() (
	local marker="${TEMPORARY_ROOT}/manual-revalidation-called"

	revalidate_frontend() { : > "$marker"; }
	git() { fail "manual revalidation must not call Git"; }
	run_one_shot_sync() { fail "manual revalidation must not call one-shot sync"; }
	run_asset_stage() { fail "manual revalidation must not stage assets"; }
	run_asset_activate() { fail "manual revalidation must not activate assets"; }

	dispatch revalidate
	assert_file "$marker"
)

test_asset_commands_use_sync_contract() (
	local delivery_id="77777777-7777-4777-8777-777777777777"
	local arguments_file="${TEMPORARY_ROOT}/asset-compose-arguments"

	: > "$arguments_file"
	run_compose() {
		printf 'CALL\n' >> "$arguments_file"
		printf '%s\n' "$@" >> "$arguments_file"
		if [[ " $* " == *" HOUKAGO_ASSET_PUBLICATION_ACTION=stage "* ]]; then
			printf 'phase=ASSET_STAGE_SUCCESS status=SUCCESS generationId=%s publicPostCount=2 assetCount=1 totalBytes=10 smokeAssetPath=/assets/posts/example-post/image.png\n' "$SHA"
		else
			printf 'phase=ASSET_ACTIVATE_SUCCESS status=SUCCESS generationId=%s\n' "$SHA"
		fi
	}

	run_asset_stage "$delivery_id" "$SHA" >/dev/null
	run_asset_activate "$delivery_id" "$SHA" >/dev/null

	assert_equals "2" "$(grep -c '^CALL$' "$arguments_file")" "asset Compose call count"
	assert_equals "2" "$(grep -c '^run$' "$arguments_file")" "asset commands must use Compose run"
	assert_equals "2" "$(grep -c '^--rm$' "$arguments_file")" "asset commands must remove one-shot containers"
	grep -Fxq 'HOUKAGO_ASSET_PUBLICATION_ACTION=stage' "$arguments_file" \
		|| fail "asset stage action missing"
	grep -Fxq 'HOUKAGO_ASSET_PUBLICATION_ACTION=activate' "$arguments_file" \
		|| fail "asset activate action missing"
	grep -Fxq "HOUKAGO_ASSET_PUBLICATION_GENERATION_ID=${SHA}" "$arguments_file" \
		|| fail "asset generation must use the checked-out commit"
	! grep -Eq '^(up|app|mysql)$' "$arguments_file" \
		|| fail "asset commands must not recreate app or MySQL"
	assert_equals "2" "$ASSET_PUBLIC_POST_COUNT" "parsed public post count"
	assert_equals "1" "$ASSET_COUNT" "parsed asset count"
	assert_equals "10" "$ASSET_TOTAL_BYTES" "parsed asset bytes"
	assert_equals "/assets/posts/example-post/image.png" "$ASSET_SMOKE_PATH" "parsed smoke path"
)

prepare_asset_smoke_fixture() {
	rm -rf -- "$ASSET_ROOT_FIXTURE"
	mkdir -p "${ASSET_ROOT_FIXTURE}/releases/${SHA}/posts/example-post"
	ln -s "releases/${SHA}" "${ASSET_ROOT_FIXTURE}/current"
}

test_asset_http_smoke_contract() (
	local delivery_id="88888888-8888-4888-8888-888888888888"
	local arguments_file="${TEMPORARY_ROOT}/asset-smoke-curl-arguments"
	local output

	prepare_asset_smoke_fixture
	ASSET_SMOKE_PATH=/assets/posts/example-post/image.png
	curl() {
		local headers_file=""
		printf '%s\n' "$@" > "$arguments_file"
		while [[ $# -gt 0 ]]; do
			if [[ "$1" == "--dump-header" ]]; then
				headers_file="$2"
				shift 2
				continue
			fi
			shift
		done
		printf 'HTTP/1.1 200 OK\r\nETag: "fixture"\r\nCache-Control: max-age=300\r\n\r\n' > "$headers_file"
		printf '200'
	}

	output="$(verify_public_asset "$delivery_id" "$SHA")" \
		|| fail "asset HTTP smoke unexpectedly failed"
	[[ "$output" == *"phase=asset_smoke status=SUCCESS"* ]] \
		|| fail "asset HTTP smoke success was not logged"
	grep -Fxq -- '=https' "$arguments_file" || fail "asset smoke must require HTTPS"
	grep -Fxq -- 'https://backend.example.invalid/assets/posts/example-post/image.png' "$arguments_file" \
		|| fail "asset smoke did not use the deterministic staged path"
)

test_zero_asset_smoke_is_explicitly_skipped() (
	local delivery_id="99999999-9999-4999-8999-999999999999"
	local output

	prepare_asset_smoke_fixture
	ASSET_SMOKE_PATH=NONE
	curl() { fail "zero-asset smoke must not call HTTP"; }

	output="$(verify_public_asset "$delivery_id" "$SHA")" \
		|| fail "zero-asset smoke unexpectedly failed"
	[[ "$output" == *"status=SKIPPED"* && "$output" == *"reason=no_public_assets"* ]] \
		|| fail "zero-asset smoke skip was not explicit"
)

test_publication_pipeline_case() (
	local scenario="$1"
	local fixture_delivery_id="$2"
	local expected_state="$3"
	local expected_current="$4"
	local expected_sequence="$5"
	local incoming_path="${INCOMING_DIRECTORY}/${fixture_delivery_id}.json"
	local sequence_file="${TEMPORARY_ROOT}/pipeline-${scenario}.sequence"
	local current_file="${TEMPORARY_ROOT}/pipeline-${scenario}.current"
	local result=0 actual_state output

	reset_spool_files
	: > "$sequence_file"
	printf '%s\n' "$OLD_SHA" > "$current_file"
	printf '{}\n' > "$incoming_path"
	chmod 0640 "$incoming_path"

	validate_job() { printf '%s\n%s\n' "$fixture_delivery_id" "$SHA"; }
	git() {
		case "$*" in
			*"status --porcelain"*) return 0 ;;
			*"branch --show-current"*) printf 'main\n' ;;
			*"fetch --quiet origin main"*) return 0 ;;
			*"cat-file -e"*) return 0 ;;
			*"rev-parse refs/remotes/origin/main"*) printf '%s\n' "$SHA" ;;
			*"rev-parse HEAD"*) printf '%s\n' "$SHA" ;;
			*"merge-base --is-ancestor"*) return 0 ;;
			*"merge --ff-only --quiet"*) return 0 ;;
			*) fail "unexpected pipeline git invocation: $*" ;;
		esac
	}
	verify_checkout_readable() { printf 'readability\n' >> "$sequence_file"; return 0; }
	run_asset_stage() {
		printf 'preflight\n' >> "$sequence_file"
		[[ "$scenario" == "preflight" ]] && return 1
		printf 'stage\n' >> "$sequence_file"
		[[ "$scenario" == "asset-stage" ]] && return 1
		ASSET_PUBLIC_POST_COUNT=1
		ASSET_COUNT=1
		ASSET_TOTAL_BYTES=10
		ASSET_SMOKE_PATH=/assets/posts/example-post/image.png
		return 0
	}
	run_one_shot_sync() {
		printf 'db\n' >> "$sequence_file"
		[[ "$scenario" != "db" ]]
	}
	run_asset_activate() {
		printf 'activate\n' >> "$sequence_file"
		[[ "$scenario" == "activation" ]] && return 1
		printf '%s\n' "$SHA" > "$current_file"
		return 0
	}
	verify_public_api() {
		printf 'api\n' >> "$sequence_file"
		[[ "$scenario" != "api" ]]
	}
	verify_public_asset() {
		printf 'asset\n' >> "$sequence_file"
		[[ "$scenario" != "asset-smoke" ]]
	}
	revalidate_frontend() {
		printf 'revalidate\n' >> "$sequence_file"
		[[ "$scenario" != "revalidation" ]]
	}

	output="$(process_job "$incoming_path" 2>&1)" || result=$?
	if [[ -f "${SUCCEEDED_DIRECTORY}/${fixture_delivery_id}.json" ]]; then
		actual_state=succeeded
	elif [[ -f "${FAILED_DIRECTORY}/${fixture_delivery_id}.json" ]]; then
		actual_state=failed
	else
		actual_state=missing
	fi

	if [[ "$actual_state" != "$expected_state" ]]; then
		fail "${scenario} final job state: expected=${expected_state} actual=${actual_state} output=${output}"
	fi
	assert_equals "$expected_current" "$(cat "$current_file")" "${scenario} current snapshot"
	assert_equals "$expected_sequence" "$(paste -sd, "$sequence_file")" "${scenario} ordering"
	if [[ "$expected_state" == "succeeded" ]]; then
		assert_equals "0" "$result" "${scenario} process result"
	else
		[[ "$result" -ne 0 ]] || fail "${scenario} unexpectedly returned success"
	fi
)

test_checkout_commit_mismatch_fails_before_asset_stage() (
	local fixture_delivery_id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	local incoming_path="${INCOMING_DIRECTORY}/${fixture_delivery_id}.json"
	local head_calls_file="${TEMPORARY_ROOT}/checkout-mismatch-head-calls"
	local output

	reset_spool_files
	: > "$head_calls_file"
	printf '{}\n' > "$incoming_path"
	chmod 0640 "$incoming_path"
	validate_job() { printf '%s\n%s\n' "$fixture_delivery_id" "$SHA"; }
	git() {
		case "$*" in
			*"status --porcelain"*) return 0 ;;
			*"branch --show-current"*) printf 'main\n' ;;
			*"fetch --quiet origin main"*) return 0 ;;
			*"cat-file -e"*) return 0 ;;
			*"rev-parse refs/remotes/origin/main"*) printf '%s\n' "$NEWER_SHA" ;;
			*"rev-parse HEAD"*)
				printf 'x' >> "$head_calls_file"
				if [[ "$(wc -c < "$head_calls_file" | tr -d ' ')" -eq 1 ]]; then
					printf '%s\n' "$OLD_SHA"
				else
					printf '%s\n' "$NEWER_SHA"
				fi
				;;
			*"merge-base --is-ancestor"*) return 0 ;;
			*"merge --ff-only --quiet"*) return 0 ;;
			*) fail "unexpected mismatch git invocation: $*" ;;
		esac
	}
	verify_checkout_readable() { return 0; }
	run_asset_stage() { fail "commit mismatch must fail before asset stage"; }

	if output="$(process_job "$incoming_path" 2>&1)"; then
		fail "checkout commit mismatch unexpectedly succeeded"
	fi
	assert_file "${FAILED_DIRECTORY}/${fixture_delivery_id}.json"
	[[ "$output" == *"reason=checkout_commit_mismatch"* ]] \
		|| fail "checkout mismatch did not fail closed with its expected reason"
)

test_activation_failure_operator_retry_recovers() (
	local fixture_delivery_id="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
	local incoming_path="${INCOMING_DIRECTORY}/${fixture_delivery_id}.json"
	local activation_attempts="${TEMPORARY_ROOT}/activation-retry.attempts"
	local stage_attempts="${TEMPORARY_ROOT}/activation-retry.stages"
	local db_attempts="${TEMPORARY_ROOT}/activation-retry.db"
	local current_file="${TEMPORARY_ROOT}/activation-retry.current"

	reset_spool_files
	: > "$activation_attempts"
	: > "$stage_attempts"
	: > "$db_attempts"
	printf '%s\n' "$OLD_SHA" > "$current_file"
	printf '{}\n' > "$incoming_path"
	chmod 0640 "$incoming_path"

	validate_job() { printf '%s\n%s\n' "$fixture_delivery_id" "$SHA"; }
	git() {
		case "$*" in
			*"status --porcelain"*) return 0 ;;
			*"branch --show-current"*) printf 'main\n' ;;
			*"fetch --quiet origin main"*) return 0 ;;
			*"cat-file -e"*) return 0 ;;
			*"rev-parse refs/remotes/origin/main"*) printf '%s\n' "$SHA" ;;
			*"rev-parse HEAD"*) printf '%s\n' "$SHA" ;;
			*"merge-base --is-ancestor"*) return 0 ;;
			*"merge --ff-only --quiet"*) return 0 ;;
			*) fail "unexpected retry git invocation: $*" ;;
		esac
	}
	verify_checkout_readable() { return 0; }
	run_asset_stage() {
		printf 'x' >> "$stage_attempts"
		ASSET_PUBLIC_POST_COUNT=1
		ASSET_COUNT=1
		ASSET_TOTAL_BYTES=10
		ASSET_SMOKE_PATH=/assets/posts/example-post/image.png
		return 0
	}
	run_one_shot_sync() { printf 'x' >> "$db_attempts"; return 0; }
	run_asset_activate() {
		printf 'x' >> "$activation_attempts"
		if [[ "$(wc -c < "$activation_attempts" | tr -d ' ')" -eq 1 ]]; then
			return 1
		fi
		printf '%s\n' "$SHA" > "$current_file"
		return 0
	}
	verify_public_api() { return 0; }
	verify_public_asset() { return 0; }
	revalidate_frontend() { return 0; }

	if process_job "$incoming_path" >/dev/null 2>&1; then
		fail "first activation attempt unexpectedly succeeded"
	fi
	assert_file "${FAILED_DIRECTORY}/${fixture_delivery_id}.json"
	assert_equals "$OLD_SHA" "$(cat "$current_file")" "failed activation current snapshot"

	retry_failed_job "$fixture_delivery_id" >/dev/null
	process_job "${INCOMING_DIRECTORY}/${fixture_delivery_id}.json" >/dev/null \
		|| fail "activation retry did not recover"

	assert_file "${SUCCEEDED_DIRECTORY}/${fixture_delivery_id}.json"
	assert_equals "2" "$(wc -c < "$stage_attempts" | tr -d ' ')" "same generation stage attempts"
	assert_equals "2" "$(wc -c < "$db_attempts" | tr -d ' ')" "idempotent DB sync attempts"
	assert_equals "2" "$(wc -c < "$activation_attempts" | tr -d ' ')" "activation attempts"
	assert_equals "$SHA" "$(cat "$current_file")" "recovered current snapshot"
)

test_checkout_umask_scope
test_readability_guard_uses_sync_service
test_process_failure_case "dirty" "11111111-1111-4111-8111-111111111111"
test_process_failure_case "unknown" "22222222-2222-4222-8222-222222222222"
test_process_failure_case "nonff" "33333333-3333-4333-8333-333333333333"
test_process_failure_case "unreadable" "44444444-4444-4444-8444-444444444444"
test_operator_retry_preserves_history
test_restrictive_service_contract
test_revalidation_case "success" 1 "SUCCESS" 200
test_revalidation_case "network" 3 "WARNING" network network network
test_revalidation_case "rate-limit" 2 "SUCCESS" 429 200
test_revalidation_case "server-error" 2 "SUCCESS" 500 200
test_revalidation_case "unauthorized" 1 "WARNING" 401
test_revalidation_case "forbidden" 1 "WARNING" 403
test_revalidation_request_contract
test_revalidation_failure_is_non_fatal
test_manual_revalidation_only_calls_endpoint
test_asset_commands_use_sync_contract
test_asset_http_smoke_contract
test_zero_asset_smoke_is_explicitly_skipped
test_publication_pipeline_case "success" "cccccccc-cccc-4ccc-8ccc-cccccccccccc" \
	"succeeded" "$SHA" "readability,preflight,stage,db,activate,api,asset,revalidate"
test_publication_pipeline_case "preflight" "dddddddd-dddd-4ddd-8ddd-dddddddddddd" \
	"failed" "$OLD_SHA" "readability,preflight"
test_publication_pipeline_case "asset-stage" "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee" \
	"failed" "$OLD_SHA" "readability,preflight,stage"
test_publication_pipeline_case "db" "ffffffff-ffff-4fff-8fff-ffffffffffff" \
	"failed" "$OLD_SHA" "readability,preflight,stage,db"
test_publication_pipeline_case "activation" "12121212-1212-4212-8212-121212121212" \
	"failed" "$OLD_SHA" "readability,preflight,stage,db,activate"
test_publication_pipeline_case "api" "13131313-1313-4313-8313-131313131313" \
	"failed" "$SHA" "readability,preflight,stage,db,activate,api"
test_publication_pipeline_case "asset-smoke" "14141414-1414-4414-8414-141414141414" \
	"failed" "$SHA" "readability,preflight,stage,db,activate,api,asset"
test_publication_pipeline_case "revalidation" "15151515-1515-4515-8515-151515151515" \
	"succeeded" "$SHA" "readability,preflight,stage,db,activate,api,asset,revalidate"
test_checkout_commit_mismatch_fails_before_asset_stage
test_activation_failure_operator_retry_recovers

printf 'All content sync worker tests passed.\n'
