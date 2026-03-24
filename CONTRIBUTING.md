# Contributing

## Scope

This project is still in the pre-public-release cleanup phase. Contributions should preserve working behavior first and favor incremental changes over broad rewrites.

## Development Setup

1. Copy [`config/local-secrets.env.example`](./config/local-secrets.env.example) to `config/local-secrets.env`.
2. Start the local stack:

```bash
docker compose up --build -d
```

3. Run tests before sending changes:

```bash
./gradlew test
```

## Contribution Expectations

- Keep routes and templates stable unless the change is intentionally user-facing.
- Prefer additive seam extraction over behavior-changing rewrites.
- Add or update focused tests for any controller, service, or workflow change.
- Do not commit secrets, tokens, or local `.env`-style files.
- Keep production-only secrets out of Git.
- Update docs when behavior, setup, or deployment expectations change.

## Change Style

- Small, reviewable pull requests are preferred.
- If a change touches a protected workflow, include the test slice you used to validate it.
- If a refactor is guided by one of the audit docs, reference the relevant document in the PR description.

## Areas That Need Extra Care

- match logging and confirmation
- competition routing and navigation state
- group administration and season lifecycle
- round robin management

## Before Opening A Pull Request

- run `./gradlew test`
- check for accidental generated files or secrets
- make sure any new environment variables are reflected in example/config docs
