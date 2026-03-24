# Public Repo Curation Plan (March 2026)

**Status:** Active reference
**Last updated:** 2026-03-24

## Recommendation

For the initial open source release, create a **new public repository** from a curated working tree rather than publishing the history of the private repo.

That gives you:

- a clean public commit history
- less risk of old secrets or private ops notes leaking through history
- tighter control over what the public repo is actually about: the web application and its supporting modules

## Current Readiness Assessment

From an application/release perspective, the project is close.

Already in good shape:

- Java 25 build/test baseline is green
- Spring Boot baseline is modernized
- CI/docs/license-adjacent repo files are in place
- top-level docs are reduced to current public-repo references

The remaining work is mostly **repo curation**, not application architecture.

## What To Include In The Public Repo

Recommended public scope:

- `src/`
- `trophy-generator-core/`
- `trophy-generator-cli/`
- `gradle/`
- `build.gradle`
- `settings.gradle`
- `gradlew`
- `gradlew.bat`
- `Dockerfile`
- `compose.yaml`
- `.github/workflows/ci.yml`
- `config/checkstyle/`
- `config/pmd/`
- `config/local-secrets.env.example`
- `scripts/ops/wait-for-mysql.sh`
- `scripts/ops/docker-entrypoint.sh`
- `scripts/db/perf/`
- `scripts/db/seed/`
- active docs from `docs/`
- `README.md`
- `CONTRIBUTING.md`
- `SECURITY.md`
- `THIRD_PARTY_ASSETS.md`
- `TRADEMARKS.md`

## What To Exclude From The Public Repo

Recommended private-only scope:

- `prod_config/`
- most of `scripts/`
- `logs/`
- `config/local-secrets.env`
- local token/credential helper files
- `.git/`
- `.gradle/`
- `build/`
- `.jdks/`
- `.mypy_cache/`
- `bin/`
- `.settings/`
- `.vscode/`
- `.classpath`
- `.project`

Also exclude older historical docs unless you explicitly want them public.

## Why These Exclusions Make Sense

### Operations

`prod_config/` is deployment and host-operations material, not the core web app. It includes server-specific nginx/docker notes and backup notes that should stay private.

### Scripts

Most of `scripts/` is internal-only and should stay out of the public repo. The exception is a very small subset that the curated repo still needs:

- `scripts/ops/wait-for-mysql.sh`
- `scripts/ops/docker-entrypoint.sh`
- `scripts/db/perf/`
- `scripts/db/seed/`

### Sensitive Or Private Workspace Files

This workspace contained files that should not be copied into a public repo:

- `config/local-secrets.env`
- local token/credential helper files
- `logs/`

## Final Secrets/Privacy Check From This Pass

What was found in the original private workspace:

- no obvious committed PEM/private-key material in the scanned working tree
- no obvious committed real `.env` file at the repo root
- one real local secrets file existed in the workspace: `config/local-secrets.env`
- one local token/credential helper file existed in the workspace
- operational/private deployment material existed under `prod_config/`
- local/runtime data existed under `logs/`

Important nuance:

- this was a **working-tree** curation check, not a full historical Git forensic audit
- if you publish from a brand-new repo created from a curated copy, that is usually good enough for the initial public release
- any real secrets that were ever used in practice should still be rotated based on operational judgment, regardless of whether they are published

## Assets And License Review Still Worth Doing

Before publishing, make one more explicit yes/no decision on:

- bundled third-party assets already listed in [`THIRD_PARTY_ASSETS.md`](../THIRD_PARTY_ASSETS.md)
- externally loaded fonts/icons
- any bundled art whose provenance is uncertain

If provenance is unclear, do not include it in the initial public repo.

## Suggested Export Approach

1. Create a new empty public repo.
2. Copy only the approved public-scope files and directories.
3. Add the chosen `LICENSE`.
4. Re-run tests and a Docker build in the new repo.
5. Make the first public commit something like `Initial public release`.

## Recommended Next Step

Run the build/tests and do one final README/docs pass so the public repo no longer points at private-only paths.
