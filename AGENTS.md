# houkago.server

Backend API and content sync repository for Houkago.

## Responsibilities

- sync post metadata and body from `houkago.posts`
- maintain backend DB as index/cache/read model, not canonical content storage
- expose public post read APIs for `houkago.blog`
- protect internal sync/revalidation operations
- support future dynamic data that does not belong in Git

## Reference Docs

Before implementation, check `houkago.docs` active documents:

- `architecture/backend-content-sync.md`
- `api/backend-content-api.md`
- `architecture/deployment.md`
- `architecture/overview.md`
- `guides/content/content-schema.md`

Read metadata headers first and follow `related_docs` when the change crosses boundaries.

## Rules

- `houkago.posts` remains the content source of truth.
- DB rows are read model/cache/index data for posts.
- Sync must be idempotent and recoverable.
- Public APIs must not expose draft, private, archived, or deleted posts.
- Internal endpoints must be protected.
- Never commit secrets, tokens, deploy credentials, private keys, or production environment values.
