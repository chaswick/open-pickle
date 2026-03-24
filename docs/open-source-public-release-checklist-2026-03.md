# Open Source Public Release Checklist (March 2026)

**Status:** In progress
**Purpose:** Capture the remaining repo/release hygiene work after the application-specific cleanup was completed.

Related docs:

- [`public-repo-curation-plan-2026-03.md`](./public-repo-curation-plan-2026-03.md)
- [`current-competition-and-integrity-system-2026-03.md`](./current-competition-and-integrity-system-2026-03.md)
- [`build-java25-notes.md`](./build-java25-notes.md)

## 1. Public-Facing Repo Docs

**Status:** In progress

- [x] Add a top-level [`README.md`](../README.md)
- [x] Add [`CONTRIBUTING.md`](../CONTRIBUTING.md)
- [x] Add [`SECURITY.md`](../SECURITY.md)
- [x] Add a final public [`LICENSE`](../LICENSE)
- [ ] Decide whether to add a `CODE_OF_CONDUCT.md`

Notes:

- The repo now includes the GNU Affero General Public License v3.0.
- `SECURITY.md` is currently a pre-release placeholder and should be updated with a monitored public disclosure contact before launch.

## 2. CI Baseline

**Status:** Done

- [x] Add GitHub Actions CI for the documented Java 25 build/test path

Files:

- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

## 3. Secrets And Config Hygiene

**Status:** In progress

- [x] Local secrets moved to `config/local-secrets.env`
- [x] Private production deployment config kept out of the public repo
- [ ] Final credential rotation review for anything historically committed
- [ ] Final pass to confirm no stray local secret artifacts remain tracked before public launch

## 4. Third-Party Asset And Dependency Review

**Status:** In progress

- [x] Create bundled/external asset inventory in [`THIRD_PARTY_ASSETS.md`](../THIRD_PARTY_ASSETS.md)
- [ ] Identify the original upstream source for the bundled moderation word list and add it to the asset inventory
- [ ] Generate or record a software dependency license inventory for Gradle dependencies and important transitive runtime libraries
- [ ] Decide whether CDN-loaded assets should remain external or be self-hosted for the public release

## 5. Release Metadata

**Status:** Pending

- [ ] Choose the public repository description/tagline
- [ ] Decide the first public release/versioning story
- [ ] Decide whether sample screenshots or a demo GIF belong in the repo

## 6. Optional But Useful

**Status:** Pending

- [ ] Add issue templates
- [ ] Add pull request template
- [ ] Add a simple maintainer roadmap doc after public launch

## Recommended Next Step

The biggest remaining public-release items are policy/repo hygiene details like a code of conduct decision, final security contact, and any last metadata polish.
