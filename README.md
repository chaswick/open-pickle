# Open-Pickle

Open-Pickle is a Spring Boot web application for playing pickleball with friends and clubs: invite players to a session, log results, and track standings together. It also supports seasonal standings, round robins, trophies, and voice-assisted match logging.

The home page offers session creation, code-based joining, and links back to active sessions. First-use guidance stays inline, session codes and QR invites are visible immediately, and season standings focus on the opponents you have played. Community totals and private-league entry points are hidden from the main experience; existing league data and routes are retained for compatibility.

Current product surface:

- sessions with shared codes, QR invites, and invite links
- season standings for your playing circle
- classic and voice-assisted match logging
- match confirmation, dispute, nullify, and reopen flows
- round robin scheduling and management
- trophies, badges, meetups, check-in, and push notifications

## Project Values

This project is being opened up because the pickleball software space is crowded with products that eventually optimize for monetization, lock-in, or paywalls.

Open-Pickle is intended to push in a different direction:

- keep core competition and group features openly inspectable
- make community trust easier by keeping the platform transparent
- support a model where hosting and maintenance can be community-supported without making the software itself a black box
- make it easier for clubs, organizers, and contributors to build on the project without guessing at hidden incentives

Open sourcing the code does not prevent hosted services, donations, or paid support. The goal is to make the project's incentives legible and keep the platform available to the community even if the hosted service evolves over time.

## AI Usage

AI has been used extensively during development of this project with experienced web developer oversight.  This includes ChatGPT, Codex, and GitHub Copilot.

That does not mean AI output is treated as authoritative. Project changes still require human oversight and manual verification. In practice, that means:

- AI-assisted changes are expected to be reviewed critically rather than accepted blindly
- tests should cover the behavior being changed, and good focused or full-suite validation matters
- design quality, architecture, and integration with the existing codebase take priority over fast code generation
- maintainers are responsible for making sure generated code is correct, coherent, and production-appropriate before it is kept

The standard for code in this repo is not whether AI produced part of it. The standard is whether the result is well-designed, well-integrated, tested appropriately, and manually verified.

## Repository Layout

- [`src/main/java`](./src/main/java): main web application
- [`src/main/resources`](./src/main/resources): templates, static assets, config, Flyway migrations
- [`src/test/java`](./src/test/java): test suite
- [`trophy-generator-core`](./trophy-generator-core): shared trophy-generation library
- [`trophy-generator-cli`](./trophy-generator-cli): standalone CLI for trophy generation
- [`scripts`](./scripts): minimal SQL fixtures and container runtime helpers kept for tests and local Docker use
- [`docs`](./docs): architecture, release, and curation docs for the public repo

## Local Development

### Prerequisites

- Docker
- Java 25 for local build/test work
- Node.js 22 LTS (22.12 or newer) and npm for frontend tests

The app targets Java 25 and the repo is set up to build and test cleanly on Java 25. See [`build-java25-notes.md`](./docs/build-java25-notes.md).

### Quick Start

1. Copy [`config/local-secrets.env.example`](./config/local-secrets.env.example) to `config/local-secrets.env`.
2. Fill in the required local values.
3. Start the local stack:

```bash
docker compose up --build -d
```

4. Open `http://localhost:8090`.

The local Docker stack uses:

- MySQL on `localhost:3306`
- the app on `localhost:8090`
- `config/local-secrets.env` as the only local secrets file

Demo accounts and fixtures are disabled by default. For an isolated development database,
set `FHPB_DEV_SEED_DATA_ENABLED=true` to create them. Demo fixtures include accounts with
publicly known passwords; keep this disabled on hosted deployments. Data and schema resets
also default to off. A deliberate schema reset requires both
`FHPB_DEV_RESET_SCHEMA_ON_STARTUP=true` and `FHPB_FLYWAY_CLEAN_DISABLED=false`.

For a fresh deployment without demo fixtures, enable `FHPB_BOOTSTRAP_GLOBAL_LADDER_ENABLED`
and `FHPB_BOOTSTRAP_GLOBAL_LADDER_AUTO_ENROLL_NEW`, and configure
`FHPB_BOOTSTRAP_ADMIN_EMAIL` and a strong `FHPB_BOOTSTRAP_ADMIN_PASSWORD`.
This initializes the shared season used by sessions and enrolls new players.

## Running Tests

Use the same command CI runs so local results match GitHub as closely as possible:

```bash
./gradlew clean test --no-daemon
```

Focused test slices are used heavily during refactors, but the full suite is expected to be green.

## Hosting And Deployment

This public repo includes local Docker support, but it does not include the maintainer's private production deployment bundle, server notes, or host-specific operations config.

If you self-host, treat this repo as the application source and create your own deployment configuration around it.
Build release images with `docker compose build --pull` to refresh the Java base images.
Operator-specific legal text, support links, analytics IDs, and donation links have been replaced with neutral defaults or placeholders and should be customized in your own deployment.

## Live Service

A live deployment of this codebase runs at `https://www.picklebuddies.com`.

If you want to use the app rather than self-host it, start there. If you want to support the hosted service's ongoing costs, donation and support information is published on the live site rather than embedded in this repo.

## Docs

- [`docs/README.md`](./docs/README.md)
- [`build-java25-notes.md`](./docs/build-java25-notes.md)
- [`current-competition-and-integrity-system-2026-03.md`](./docs/current-competition-and-integrity-system-2026-03.md)
- [`open-source-public-release-checklist-2026-03.md`](./docs/open-source-public-release-checklist-2026-03.md)
- [`public-repo-curation-plan-2026-03.md`](./docs/public-repo-curation-plan-2026-03.md)

## Contributing

See [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Security

See [`SECURITY.md`](./SECURITY.md).

## License

This project is licensed under the GNU Affero General Public License v3.0. See [`LICENSE`](./LICENSE).

## Branding

See [`TRADEMARKS.md`](./TRADEMARKS.md).
