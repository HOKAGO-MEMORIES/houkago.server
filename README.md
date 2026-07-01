# houkago.server

Houkago backend API and content sync service.

The current `main` branch is a clean start for the backend content sync/read model MVP.

Legacy AWS/CodeDeploy-based server code is preserved in the `archive/aws-legacy-server` branch.
That legacy setup is not the current MVP baseline.

## MVP Direction

- `houkago.posts` remains the content source of truth.
- The database is not a canonical content store.
- The database will be used as an index/cache/read model for serving post data.
- The MVP will start with manual full resync from a local `houkago.posts` checkout.
- Production deployment is not decided yet.

## Reference Documentation

Detailed architecture, API, content schema, deployment, and sync rules live in `houkago.docs`.
