# houkago.server

Houkago backend API and content sync service.

The current `main` branch is a clean start for the backend content sync/read model MVP.

Legacy AWS/CodeDeploy-based server code is preserved in the `archive/aws-legacy-server` branch.
That legacy setup is not the current MVP baseline.

## MVP Direction

- `houkago.posts` remains the content source of truth.
- The database is not a canonical content store.
- The database will be used as an index/cache/read model for serving post data.
- The MVP supports manual full resync from a local `houkago.posts` checkout.
- The implementation baseline is Spring Boot 3.x, Java 21, Gradle, MySQL, Flyway, and Actuator.
- Production-like runtime will use Docker Compose, Nginx, the Spring Boot app, and MySQL.
- Production deployment automation is not decided yet.
- `commit_hash` stores the `houkago.posts` Git commit used for a sync.
- `checksum` tracks metadata plus `raw_body` changes during full resync.
- `source_hash` is not part of the read model.

## Local Run

Install Java 21, then run:

```bash
./gradlew bootRun
```

The default profile is `local`. It expects a MySQL database to be available unless overridden by
environment variables.

Required or useful environment variables:

```bash
HOUKAGO_DB_URL=jdbc:mysql://localhost:3306/houkago_content
HOUKAGO_DB_USERNAME=
HOUKAGO_DB_PASSWORD=
HOUKAGO_POSTS_CHECKOUT_PATH=/path/to/houkago.posts
HOUKAGO_RESYNC_ENABLED=false
HOUKAGO_RESYNC_POSTS_ROOT=/path/to/houkago.posts
HOUKAGO_RESYNC_COMMIT_HASH=example-commit-hash
SERVER_PORT=8080
```

Do not commit real credentials, tokens, server IPs, or production environment values.

## Manual Resync Runner

Manual full resync can be triggered once during application startup. It is disabled by default.

Example with placeholders:

```bash
./gradlew bootRun --args='--houkago.resync.enabled=true --houkago.resync.posts-root=/path/to/houkago.posts --houkago.resync.commit-hash=example-commit-hash'
```

The runner loads local `houkago.posts` files, validates metadata, and upserts the backend read model.
Manual full resync also retires rows for source files missing from the current scan. Deleted rows are
soft-deleted with `syncStatus = DELETED` and `visibility = PRIVATE`; the database stores those
values as `sync_status = 'deleted'` and `visibility = 'private'`.

The runner does not expose an HTTP sync endpoint and does not calculate the Git commit hash
automatically.

## Public Read API MVP

The current public post read API exposes only rows that are published, active, and public:

```http
GET /api/posts
GET /api/posts/{slug}
```

`GET /api/posts` returns paginated post summaries without `rawBody`. `GET /api/posts/{slug}` returns
one public post detail with `rawBody`.

Deleted rows are excluded from public list/detail APIs.

## Local Docker Smoke Test

The local Docker Compose setup is for development smoke tests only. It is not a production
deployment setup and does not include Nginx, HTTPS, scheduler, or production deployment automation.

Prepare a local `.env` file:

```bash
cp .env.example .env
```

Edit `.env` and set placeholder values:

```bash
HOUKAGO_POSTS_ROOT_HOST=/path/to/houkago.posts
HOUKAGO_RESYNC_ENABLED=true
HOUKAGO_RESYNC_COMMIT_HASH=example-commit-hash
```

`HOUKAGO_POSTS_ROOT_HOST` should point to a local checkout of `houkago.posts`. The compose file mounts
that directory read-only into the app container at `HOUKAGO_POSTS_ROOT_CONTAINER`.

Build and start the smoke test stack:

```bash
docker compose --env-file .env -f compose.dev.yml up --build
```

Useful checks:

```bash
docker compose --env-file .env -f compose.dev.yml logs app
docker compose --env-file .env -f compose.dev.yml exec mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "select count(*) from post_read_models;"'
```

The app logs should show Flyway migration activity and, when `HOUKAGO_RESYNC_ENABLED=true`, the
manual resync summary. A deleted-handling smoke should show `deletedCount` when a previously synced
source file is missing from the mounted posts root. `MYSQL_HOST_PORT` exposes MySQL to the host for
local debugging only.

Stop the smoke test stack:

```bash
docker compose --env-file .env -f compose.dev.yml down
```

## Verification

```bash
./gradlew test
./gradlew bootJar
```

The current tests validate the application context and health endpoints. MySQL Testcontainers-based
integration tests validate Flyway migration and repository behavior for the post read model.

The repository integration test requires Docker because it starts a MySQL Testcontainer.

## Not Implemented Yet

- automatic Git commit hash lookup
- production Docker Compose, Nginx config, and deployment automation
- webhook, incremental sync, and frontend revalidation

## Reference Documentation

Detailed architecture, API, content schema, deployment, and sync rules live in `houkago.docs`.

Start with:

- `houkago.docs/architecture/backend-content-sync.md`
- `houkago.docs/api/backend-content-api.md`
- `houkago.docs/architecture/deployment.md`
- `houkago.docs/architecture/overview.md`
- `houkago.docs/decisions/architecture-history.md`
- `houkago.docs/guides/content/content-schema.md`
