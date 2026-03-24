# Current Competition And Integrity System (March 2026)

**Status:** Active reference
**Last updated:** 2026-03-24

## Purpose

Summarize the current competition, standings, confirmation, and trust model based on the code that exists now. This replaces older design notes that described earlier experiments or partially implemented plans.

## Core Ladder Model

The application uses a shared ladder/season model with three ladder types in `LadderConfig`:

- `COMPETITION`: the global competition
- `STANDARD`: private groups
- `SESSION`: session ladders

Each ladder also has:

- a season mode: `ROLLING` or `MANUAL`
- a scoring algorithm: `MARGIN_CURVE_V1` or `BALANCED_V1`
- a security mode stored as `LadderSecurity`

Canonical security behavior today is:

- `STANDARD`: opponent-team confirmation is required
- `SELF_CONFIRM`: the logger confirms their own match

Legacy `NONE` and `HIGH` values are still in the enum for backward compatibility, but business logic normalizes them to `STANDARD`.

## Standings And Rating Movement

Standings are not Elo or DUPR clones. The current system uses ladder-specific scoring algorithms under `web/service/scoring`.

### Margin Curve

`MarginCurveV1LadderScoringAlgorithm` is the default competition-style model.

Current behavior:

- everyone effectively starts from a `1000` baseline in standings presentation
- score dominance sets the base step on a curved scale from `2` to `16`
- guest-heavy matches are scaled down to `60%`, `40%`, or `25%`
- winners can earn additional upside from recent partner/opponent variety
- winners can also get capped streak bonuses for 3, 4, and 5+ wins
- opponent rating does not directly change the swing

This is intentionally a broad-sorting ladder, not a prediction engine.

### Balanced V1

`BalancedV1LadderScoringAlgorithm` is the more guarded model.

Current behavior:

- base steps are `6`, `8`, `11`, or `14` based on score difference
- guest-heavy matches use the same guest scaling as Margin Curve
- a 56-day history window drives opponent-variety scoring
- band/division context affects edge scaling and protection rules
- losses are floored at roughly `75%` of the base step
- top-band clamp and bottom-band loss protections reduce runaway movement

## Confirmation Workflow

The current confirmation engine is centered in `DefaultMatchConfirmationService` and match-state orchestration under `web/service/matchworkflow`.

Key current rules:

- opponent confirmation is the normal path for `STANDARD`
- `SELF_CONFIRM` ladders bypass opponent-team confirmation
- some guest-only matches can be allowed as personal records but excluded from standings
- pending confirmations can expire into moderation/nullification paths
- the dedicated confirm-matches flow is the active review surface; standings/group pages no longer do page-level match review

## Trust And Moderation

`PlayerTrustService` calculates season-scoped integrity metrics.

Current trust model:

- logging accuracy = confirmed logged matches / qualifying logged matches
- confirmation honesty = completed required confirmations / qualifying required confirmations
- trust weight = `min(loggingAccuracy, confirmationHonesty)`
- players with fewer than 5 qualifying matches get a grace period of `1.0`
- players with both rates at or above `80%` stay at `1.0`

Important boundary:

- this trust score is for moderation, warnings, and season eligibility
- it is **not** part of rating-delta calculation

Competition-specific moderation also uses expired-confirmation thresholds from configuration, surfaced through services like `CompetitionAutoModerationService`.

## Trophies And Story Mode

The trophy system now has a catalog/instance split:

- `TrophyCatalogEntry`: reusable available trophies
- `Trophy`: season-instantiated or awarded trophies
- art is attached separately and exposed through trophy services/controllers

Current runtime behavior:

- `AutoTrophyService` instantiates applicable catalog entries for a season
- `TrophyCatalogService` builds the user-facing catalog view
- story mode is still present, feature-gated, and tied into trackers/trophies rather than being a separate top-level product mode

## Historical Notes

Older documents explored alternative rating systems, confirmation weighting ideas, and early trust-system plans. They are useful as design history, but they should not be treated as the current implementation contract.
