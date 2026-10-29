# Session matchup validation

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

`SessionMatchupServiceTest` checks automatic inclusion through 32 players in both formats and rejection of 33 players. `GroupAdministrationServiceTest` checks that new and returning players can fill the 32nd slot and cannot exceed it. The member API reports the same session limit.

The 26- and 30-player cases need alternate constraint search orders to find balanced byes within a bounded search budget. The eight-player circle partnership template could force heavily repeated opponents despite distinct partners; the binary-field schedules for 4, 8, 16, and 32 avoid that problem. Other counts prioritize fresh opponents but do not promise exactly two meetings per opponent.

The current session flow accepts **4–32 players** and **1–30 requested rotating rounds**. Session invitations, approval, member counts, and matchup generation share the 32-player limit. A scheduler-selected default cycle can exceed 30 rounds; for example, 32 players have a 31-round full cycle. Fixed partners use a complete team cycle. Fairness is measured over the generated schedule; an early stop or a replacement can change an individual person's actual playing time. Replacement preserves the slot's remaining assignments and recorded history.

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

Session creation offers **Now** or **Later**. Later requires a date within the next 90 days.
The organizer's account timezone (Eastern when unset) is saved with the session. Its planned
boundary is the start of that date in that timezone, including daylight-saving transitions.
Changing account timezones does not move a planned session. Existing sessions keep their current expiry.

Friends can join upcoming sessions immediately. Matchup and scheduled open-play invitations stay
available until the session expires.
Join requests for dated sessions remain pending until that expiry, allowing organizers to approve
advance signups later; undated live sessions retain their existing short request window.
Current sessions appear before upcoming ones on Home;
upcoming sessions are ordered by their playing date. The creation link stays available while
hosting a current session so the organizer can choose Later. Choosing Now reopens an existing
current hosted session, and never reuses an upcoming one. The configured session cap applies
separately to current and upcoming sessions; the existing daily creation cap also remains.

At the planned local midnight the card changes from Upcoming to Today. This does not generate
matchups or freeze the roster. Starting matchups explicitly records the actual start, including
an early start, and gives the session its normal 48-hour playing window. An unstarted planned
session expires 48 hours after its planned boundary; Now sessions expire 48 hours after creation.
The existing cleanup job archives expired sessions and preserves recorded matches.

`SessionSchedulingIntegrationTest` checks real persistence, independent current/upcoming caps,
creation while hosting, local-midnight classification, timezone persistence, daylight-saving dates,
invalid dates, cleanup, and season rollover for scheduled open play. `SessionMatchupServiceTest`
checks the explicit start and playing window. Browser checks cover signup and organizer approval
before the playing date, the Now/Later controls, dated Home cards, and existing-session reuse.

## Initial rendering

Page-specific stylesheets belong in the layout’s head `styles` fragment, before body content and scripts. Use `th:block` for a fragment containing multiple head elements; a literal `div` closes the browser’s head early. Session headers retain the shared card background, and the organizer-scoring choice uses the same card treatment as the play modes. The loading regression check delays both CSS and JavaScript to verify that cards are styled at the first contentful paint and retain their appearance after scripts initialize.
