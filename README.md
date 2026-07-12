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

## OCI Backend Smoke Test

The OCI smoke setup verifies the backend app and MySQL on the single VM. Host Nginx is managed
outside this Compose stack; HTTPS, frontend integration, scheduler, and webhook automation remain
separate phases.

Prerequisites on the OCI host:

- Docker Engine and the Docker Compose plugin are installed.
- `/opt/houkago/server` contains this repository.
- `/opt/houkago/posts` contains a read-only checkout source for `houkago.posts`.
- `/opt/houkago/env/server.env` exists only on the server and is not committed.

The production smoke compose file is:

```bash
compose.prod.yml
```

Create the server-only env file from the example and replace placeholders on the server:

```bash
cp .env.prod.example /opt/houkago/env/server.env
chmod 600 /opt/houkago/env/server.env
```

Before manual resync, check the posts checkout status and commit hash:

```bash
git -C /opt/houkago/posts status --short --branch
git -C /opt/houkago/posts rev-parse HEAD
```

Use that commit hash as `HOUKAGO_RESYNC_COMMIT_HASH`.

Run the backend smoke stack from `/opt/houkago/server`:

```bash
docker compose --env-file /opt/houkago/env/server.env -f compose.prod.yml up -d --build
```

Security boundary for this smoke stack:

- MySQL is not published to the host.
- Spring Boot is bound to `127.0.0.1:8080` only.
- `/opt/houkago/posts` is mounted read-only at `/workspace/houkago.posts`.
- Nginx is a host service outside this Compose stack, and HTTPS remains deferred.

The `127.0.0.1:8080` binding is intentional network hardening. The app should not be reachable
directly from the public internet; host Nginx is the public HTTP ingress on port 80.

Useful smoke checks:

```bash
docker compose --env-file /opt/houkago/env/server.env -f compose.prod.yml ps
docker compose --env-file /opt/houkago/env/server.env -f compose.prod.yml logs --tail=100 app
curl -sS http://127.0.0.1:8080/actuator/health
curl -sS "http://127.0.0.1:8080/api/posts?size=3"
sudo ss -tulpn
```

The app logs should show Flyway migration and the manual resync summary. The list API should not
return `rawBody`; detail responses for public posts include `rawBody`.

Latest OCI smoke result:

```text
candidateCount=265
createdCount=265
updatedCount=0
touchedCount=0
totalUpsertedCount=265
deletedCount=0

DB rows:
total=265
active/public=258
active/private=7

API:
GET /actuator/health -> UP
GET /api/posts?size=3 -> 200
GET /api/posts/{slug} -> 200
```

MySQL 8.4 emitted a Flyway compatibility warning during smoke. This is not a smoke blocker because
migration, schema creation, manual resync, and API checks succeeded. Before production traffic,
revisit whether to keep MySQL 8.4 with a Flyway upgrade or pin MySQL to the 8.0 series.

## OCI Nginx HTTPS Ingress

`api.houkago.moe` is the public backend API subdomain.

Current ingress status:

- host Nginx is installed
- `/etc/nginx/sites-available/api.houkago.moe` proxies HTTP 80 to `http://127.0.0.1:8080`
- `/etc/nginx/sites-enabled/api.houkago.moe` enables the site
- external HTTPS list/detail smoke passed with HTTP 200
- list responses do not expose `rawBody`
- HTTP redirects to HTTPS with 301
- Nginx returns 404 for Actuator variants over HTTPS and after HTTP redirect
- Spring Boot remains bound to `127.0.0.1:8080`
- MySQL remains unpublished to the host
- Docker and Nginx services are enabled and active
- app and MySQL containers use the `unless-stopped` restart policy
- Certbot Snap automatic renewal is enabled and the renewal dry-run passes
- a planned reboot verified automatic recovery of TCP 80/443, Nginx HTTP/HTTPS, Docker, MySQL,
  Spring Boot, the external HTTPS API, redirect behavior, and Actuator blocking

The DNS A record and OCI TCP 80 ingress are verified. The host TCP 80 allow rule is persisted with
`netfilter-persistent` and a minimal `/etc/iptables/rules.v4`. Docker runtime chains are excluded
from that file. A planned reboot verified automatic recovery of SSH, the firewall rule, Docker,
Nginx, MySQL, Spring Boot, the external post API, and Actuator blocking.

TCP 443 is present in both the live INPUT chain and the minimal persistent `rules.v4`. A planned
reboot verified that TCP 80/443, the Nginx HTTP/HTTPS listeners, app/MySQL containers, external HTTPS
list/detail API, HTTP redirect, certificate connection, and Certbot renewal schedule recover without
manual service restarts. Spring Boot remains loopback-only and MySQL remains unpublished to the host.

The reboot-required package was `apparmor`; the kernel remained `6.17.0-1018-oracle` before and
after reboot. HSTS, frontend/CORS integration, and remaining security updates are deferred.

On the OCI ARM server, the first Docker build can take a while because Gradle dependencies are
downloaded inside the Docker build. If that becomes too slow, consider a later Host-build plus thin
runtime image workflow instead of changing this smoke baseline immediately.

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
- deployment automation
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
