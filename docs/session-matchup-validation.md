# Session matchup validation

## Seven-game Normal versus Performance comparisons

```bash
./gradlew test --tests '*SevenGameRoundRobinSimulationTest' --tests '*PerformanceRoundRobinSchedulerTest' --tests '*SessionMatchupPersistenceTest' --rerun-tasks
```

Performance uses the latest available season standings when releasing each round.
The existing season scorer owns ability updates; there is no additional Elo-style session
model. Starting points are saved as a fallback if a standing is unavailable. A registered
replacement uses their own standing and a named guest uses the unrated baseline. Once
released, pairings stay stable even if standings subsequently change. Integration tests
cover updated standings, missing standings, confirmation gating, concurrent release,
and stable released games. Standings may refresh asynchronously: the next round uses
the latest completed update without recalculating the season under the round-robin lock.

Normal favors broad rotation. Performance favors nearby individual ratings, then team
balance and partner/opponent variety. Scheduling costs preserve relative rating gaps and
ties while normalizing the roster's numerical range. Changing the common rating baseline
or multiplying all ratings by a constant does not change scheduling preferences. This
normalization never changes season points. Each nearby quartet first tries all three
team divisions before exchanging players across courts; otherwise local search can mix
distant levels just to escape an old partnership when a nearby rotation exists. Fair
bye allocation takes precedence throughout.

Performance matchup cards display each registered player's current season rating as a
small suffix after their name, using the same 1000-point base as the standings. The
starting snapshot is the fallback; a player without a standing or saved value uses
1000. Named guests have no numeric suffix. Normal and Fixed teams omit ratings. Live
refresh revisions include only the schedule's players, so unrelated league rating
changes do not refresh these cards. Mode choices use short explanatory text rather
than an info icon or tooltip.

### Reproducible sessions

The report is written to `build/reports/roundrobin-seven-games/runs.csv` (sessions) and
`build/reports/roundrobin-seven-games/players.csv` (each person's experience and
starting/ending season points). There are 1,044 reported sessions, 7,530 scheduled rounds,
and 30,924 games. Every player count from 4 through 32 is covered with seeds 7, 41, 101.
Five profiles use seven scheduled rounds: equal skill, accurately seeded bands, noisy
starting ratings, everyone unrated, and one badly underrated strong player. A second set
of accurately seeded sessions schedules enough rounds for everyone to play seven games.
The report distinguishes these time budgets rather than counting a bye as a played game.

| Players | Games each in seven rounds | Rounds for at least seven games each |
| ---: | ---: | ---: |
| 5 | 5-6 | 9 |
| 6 | 4-5 | 11 |
| 7 | 4 | 13 |
| 15 | 5-6 | 9 |
| 31 | 6-7 | 8 |

After confirmed results, the simulation applies the real default
`MarginCurveV1LadderScoringAlgorithm`, including margin, recent variety, and streak
adjustments. History is supplied newest first. Both modes use the same scorer and
outcome model. Performance reads those updated points before the next round; Normal's
whole schedule is prepared upfront. This models promptly refreshed standings.
Installations using Balanced V1 supply their actual standing points to the same
production scheduler, but their scoring dynamics are not simulated here.

Latent skill is separate from season points. Accurate bands are at 0/300/600/900 latent
skill and 0/40/80/120 initial season points. Continuous profiles span 0-800 latent skill;
noisy estimates add Gaussian error with a standard deviation of 20 season points.
Unrated players start at zero; the underrated profile gives one skill-800 player a zero
starting estimate. IDs are shuffled independently of skill. This conversion between
latent skill and initial points is a modeling assumption, not a real-world calibration.

Synthetic rallies use `P(A wins point) = 1 / (1 + 10^((mean skill B - mean skill A) / 1600))`.
Games reach eleven, win by two, with a thirty-five-point cap. Canonical teams, round,
and seed determine the random stream: identical games in either mode receive identical
outcomes. These synthetic tests cannot establish real player experiences or promise
recovery from a badly inaccurate starting rating within seven games.

### What the metrics mean

- Individual opponent gaps and all-four-player skill span are measured alongside team
  balance. A balanced score can hide a player sharing a court with people far outside
  their level.
- A near-opponent encounter is an individual opponent within 200 latent skill points.
- A far-level game has an average absolute gap to the two opponents above 300 latent
  points. The worst person's share and longest streak are also recorded so improved
  group averages cannot conceal one person's poor session.
- The report includes unique partners/opponents, repeat encounters, consecutive
  partnerships, games actually played, byes, and bye-count spread after every round.

### Observed seven-game experience

In accurately seeded 8/12/16/24/32-player cohorts everyone plays seven actual games.
These are equal-weight averages over fifteen runs per mode:

| Metric | Normal | Performance |
| --- | ---: | ---: |
| Mean individual opponent skill gap | 364 | 199 |
| Near-opponent encounters | 23% | 46% |
| Far-level games | 45% | 15% |
| Longest far-level streak per session | 4.0 | 1.9 |
| Unique partners per person | 7.0 | 6.9 |
| Unique opponents per person | 10.5 | 8.9 |

Performance gives a distinct short-session experience: closer individual opponents with
fewer distinct opponents. It does not depend on learning another ability model over many
rounds. Equal-skill groups offer no skill advantage to either mode. Unrated groups show
little benefit. A badly underrated player can still spend several games in the wrong
group even when the rest of the group benefits; those cases are reported rather than
called successful recovery.

Tests enforce roster validity, full court use, fair total byes, fair byes after every
Performance round, no consecutive Performance byes, no consecutive Performance partners
for groups of eight or more in these scenarios, and at least three distinct partners
when enough games are played. Aggregate seven-game checks require at least a 15%
reduction in individual opponent gap and court skill span for the accurately seeded
cohorts, plus fewer far-level games. Thirty-round scheduler tests remain as mechanical
fairness stress tests; long-horizon recovery is no longer the product target. The old
session-rating and placement-challenge experiments have been removed from the active
test suite along with the duplicate session ability calculation.

## Scheduler and workflow regression checks

Run the scheduler and fixed-team regression checks with:

```bash
./gradlew test \
  --tests '*CasualScheduleFairnessTest' \
  --tests '*FairByeRoundRobinSchedulerTest' \
  --tests '*RoundRobinServiceManageTest' \
  --tests '*SessionMatchupServiceTest' \
  --tests '*GroupAdministrationServiceTest' \
  --tests '*SessionJoinRequestControllerTest'
```

`CasualScheduleFairnessTest` checks every player count from 0 through 32, every requested length from 1 through 30, and the default cycle. It uses three seeds, sparse player IDs, reversed input, and historical opponent counts. Counts below four return no schedule. For every playable schedule it verifies:

- Every player appears exactly once per round, either playing or sitting out.
- Each court has four different players; all available courts are used.
- Total byes differ by at most one between players.
- Partnerships do not repeat before the partnership cycle wraps.
- Complete default cycles with zero or one sitter per round include every possible partnership and opponent.
- For 4, 8, 16, and 32 players, every full cycle includes each partner once and each opponent twice. Shorter schedules and repeated cycles also respect the opponent bounds.

`RoundRobinServiceManageTest` starts fixed-team schedules through the service for every even roster from 4 through 32. It verifies intact partners, one meeting per opposing team, every player accounted for in each round, the expected number of courts, and one bye per team when the team count is odd. Every odd roster from 5 through 31 is rejected for fixed partners.

`SessionMatchupServiceTest` checks automatic inclusion through 32 players in both formats and rejection of 33 players. `GroupAdministrationServiceTest` checks that new and returning players respect the session's player limit. The member API reports the configured limit and counts only playing members.

The 26- and 30-player cases need alternate constraint search orders to find balanced byes within a bounded search budget. The eight-player circle partnership template could force heavily repeated opponents despite distinct partners; the binary-field schedules for 4, 8, 16, and 32 avoid that problem. Other counts prioritize fresh opponents but do not promise exactly two meetings per opponent.

The current session flow accepts **4–32 players** and **1–30 requested rotating rounds**. Session invitations, approval, and member counts respect the host's configured capacity, up to 32 players. A scheduler-selected default cycle can exceed 30 rounds; for example, 32 players have a 31-round full cycle. Fixed partners use a complete team cycle. Fairness is measured over the generated schedule; an early stop or a replacement can change an individual person's actual playing time. Replacement preserves the slot's remaining assignments and recorded history.

## Capacity and host participation

Hosts choose a player limit from 1 through 32 when creating a session and can change it in
Session options. The limit cannot be lower than the number of joined players. Waitlisted
players do not count toward that minimum. Existing sessions retain the default limit of 32.

Direct invites admit a player when there is space and otherwise add them to the waitlist.
Code-based requests still need host approval; approval adds the player to the session or,
when full, to the waitlist. Departures, host removals, and capacity increases fill vacancies
in waitlist order within the same transaction. Waiting players can cancel their place.
Ending a session does not admit waiting players.

The checked-by-default Host is playing option determines whether the host uses a player
spot and appears in rosters, player pickers, and schedules created later. An unchecked host
keeps their administrative membership and can manage the session and record scores.
This choice is independent of organizer scoring. The older standalone round-robin
participant picker selects players for that schedule; the session flow uses its playing roster.
Migrations V61 and V62 persist capacity and host participation respectively.

`SessionCapacityIntegrationTest` covers FIFO admission, departures, removals, cancellations,
capacity changes, minimum validation, session closure, and concurrent joins for the last spot.
`SessionSchedulingIntegrationTest` covers a non-playing host creating matchups later with
either scoring mode. Frontend checks cover the live minimum and consistent waitlist names.

## Organizer scoring

Scheduled sessions can opt into **Organizer records scores** at creation or before the first schedule starts. The choice is fixed after that first start. Join pages and the session itself explain that results are confirmed automatically. In this mode only the session organizer (or a site administrator) can enter scores; players can view their matchups and results without confirming games.

Each result retains an `organizer_confirmed` snapshot, so its confirmation policy survives later edits. Organizers can correct the score in the session; optimistic version checks reject stale corrections. Recorded players and schedule assignments stay intact. A confirmation failure rolls back the score instead of leaving a pending game nobody can confirm.

Organizer-confirmed assignments do not consume the ordinary 100-entry weekly quota. This lets one organizer finish a complete 32-player fixed-partner cycle (120 games). Each generated assignment can be scored once; the existing session creation limits, 32-player cap, 30-round request limit, membership checks, and season eligibility checks still apply. Ordinary entries retain their weekly and burst limits.

Migration `V56__organizer_confirmed_scores.sql` adds the result snapshot with a false default. Existing scores retain their original policy. The new field has no public bean setter and cannot be enabled through match-form binding.

The MySQL release check also records a full 120-game cycle while standings replay runs. Write locks cover the assignment or match row first; related season/player data is fetched separately to avoid graph-lock deadlocks. Confirmation and editing concurrency tests retain the same row serialization guarantees.

## Session standings

Session standings default to Wins, with Points For and then season Rating breaking ties. The selector order is **W / PF / Rtg**. The server renders this same initial order and value before JavaScript loads. Selecting another statistic sorts by it first; explicit selections survive reloads and live updates. The preference key is versioned so the old automatically saved Rating default does not override the new Wins default. Season standings continue to use their rating-based presentation.

### Finishing a session

Home shows a visible note inside the “Let’s Pickle!” card only for a player hosting an active session. Each active session card has an ×: the host ends the session, and a participant leaves it. Both actions return Home. Ending a session also ends its unfinished matchups through the existing session lifecycle, removes active memberships, and keeps recorded games. The matchup page uses the same End Session action in Session options; there is no separate End matchups step when finishing for the day. The session page’s Back button returns Home.

## Planning a session

Session creation offers **Now** or **Later**. Later requires a future date and time, including
later today, within the next 90 days. Start times use a quarter-hour dropdown; the server
also rejects times between those intervals. The organizer's account timezone (Eastern when unset)
is saved with the session. Nonexistent times during daylight-saving changes are rejected.
Changing account timezones does not move a planned session.

An optional session name (up to 40 characters) appears in the existing session title, for example
`Dave's "Amateur Bracket" session`, on Home, session pages, and invitations. Blank names keep
the default host title. Names and chosen date/time are retained after validation errors.
Reopening an existing current session does not rename it.

Friends can join upcoming sessions immediately. Scheduled open-play sessions also allow logging
before the planned time once another player joins. Recording an early game leaves the planned
date, upcoming classification, and expiry unchanged. Games use the season covering their played
date; session wins and points include confirmed games across seasons. Round Robin sessions
still require the organizer to start matchups. Matchup and scheduled open-play invitations stay
available until the session expires.
Join requests for dated sessions remain pending until that expiry, allowing organizers to approve
advance signups later; undated live sessions retain their existing short request window.
Current sessions appear before upcoming ones on Home;
upcoming sessions are ordered by their playing date. The creation link stays available while
hosting a current session so the organizer can choose Later. Choosing Now reopens an existing
current hosted session, and never reuses an upcoming one. The configured session cap applies
separately to current and upcoming sessions; the existing daily creation cap also remains.

At the chosen start time the card changes from Upcoming to Today. This does not generate
matchups or freeze the roster. Starting matchups explicitly records the actual start, including
an early start, and gives the session its configured playing window (48 hours by default).
An unstarted planned session gets at least 48 hours after its planned boundary, even if the
configured playing window is shorter. Now sessions use the configured window from creation.
Migration V60 extends shorter active, unstarted planned sessions and their pending join requests;
it leaves started, archived, undated, and longer-lived sessions alone. Upcoming pages show the
planned date, time, and timezone; the expiry appears once the session is current.
Copied invitations include the host, application name, and planned date/time or “now,” followed
by the join link. QR codes continue to encode only the direct join link.
The existing cleanup job archives expired sessions and preserves recorded matches.

`SessionSchedulingIntegrationTest` checks real persistence, independent current/upcoming caps,
creation while hosting, chosen-time classification, timezone persistence, invalid or missing
dates/times, cleanup, and season rollover for scheduled open play. `SessionScheduleSupportTest`
checks later-today scheduling and daylight-saving boundaries. `SessionInviteTextTest` checks
copied invitation details. `SessionMatchupServiceTest`
checks the explicit start and playing window. Browser checks cover signup and organizer approval
before the playing date, the Now/Later controls, dated Home cards, and existing-session reuse.

## Session voice logging

Casual session pages provide the session and target-season IDs to the shared voice logger,
so name matching is limited to that session and results retain their source session. Quick
joining automatically creates a court name from the name entered; no separate setup is needed.
Ambiguous or uncertain player matches open the existing review form. Its player choices use
the same assigned member IDs as the manual form to distinguish shared names. Round Robin
scores continue to use Enter score beside the scheduled game.

## Session creation layout

The form uses numbered sections for When, How, the optional session name, and the player limit. Now/Later and
Round Robin/open play are separate either-or choices. Play modes share one group, displayed
side by side on wider screens and stacked with an “or” on phones. Organizer scoring stays
inside the selected Round Robin option; switching to open play disables that checkbox.

## Initial rendering

Page-specific stylesheets belong in the layout’s head `styles` fragment, before body content and scripts. Use `th:block` for a fragment containing multiple head elements; a literal `div` closes the browser’s head early. Session headers retain the shared card background, and the organizer-scoring choice uses the same card treatment as the play modes. The loading regression check delays both CSS and JavaScript to verify that cards are styled at the first contentful paint and retain their appearance after scripts initialize.
