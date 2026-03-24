# Java 25 Notes

**Status:** Active reference
**Last updated:** 2026-03-24

## Baseline

This repo targets Java 25 for local builds, tests, CI, and Docker images.

Current expectations:

- use Java 25 for Gradle work
- `./gradlew test` is expected to be green
- the Docker image should build on Java 25

## Validation Commands

Typical validation:

```bash
./gradlew test
docker build -t openpickle-smoke .
```

## Scope

This public repo includes the application, tests, and local container support needed to run and validate the codebase on Java 25.

It does not include the maintainer's private production deployment bundle or host-specific operations config.
