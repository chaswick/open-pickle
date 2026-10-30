# Round-robin trophies

`RoundRobinTrophyAwardService` evaluates one finished run, independently of season awards.
`is_round_robin_trophy` defaults to false on both `trophy_catalog` and concrete `trophy` rows.
`AutoTrophyService` copies the flag when creating a season's trophy instances. Regular season
evaluation, season finales and season sweeps exclude flagged definitions. Round-robin games
continue to count toward ordinary season achievements.

The initial catalog contains nine repeatable awards: Round Robin Champion, Round Robin Runner-Up,
Wooden Spoon, Mix Master, Court Captain, Court Time, Close Call, Winning Pair and Back in It.
The migration reuses existing artwork where available. First Rotation remains outside this feature.

## Processing

The worker runs on startup and then every minute. It selects at most 50 unprocessed, finished runs
whose recorded results are all confirmed or nullified. Unsettled runs do not occupy the front of
the ready queue. `fhpb.trophies.round-robin-sweep-delay-ms` controls the interval, and
`fhpb.trophies.round-robin-sweep-enabled=false` disables the worker.

A run is finished when all non-bye assignments have recorded results, it has been explicitly ended,
or its session has ended/expired. For an early ending, unplayed assignments are ignored. Nullified
games are excluded; provisional and disputed/flagged results block evaluation. Organizer-scored
games follow the same rule because scoring saves them as confirmed. A run with no qualifying games
is processed without awarding anything.

Processing locks the run, reconciles its award contributions and the players' collections, then sets
`round_robin.trophies_processed` and `trophies_processed_at` in the same transaction. Successful runs
are skipped by later sweeps. An award ledger has a unique key on run/trophy/player; ordinary
`user_trophy.award_count` displays the total across qualifying runs for that season.

Committed match changes and assignment changes reset the processed flag and withdraw only that run's
contributions. This covers score corrections, disputes, confirmations, reopening, nullification,
and deletion through JPA, including changes made outside the session controller. Rating-only
recalculations leave completed awards intact. Events run after
commit in a separate transaction, so rolled-back changes leave trophies intact. Score requests enqueue
the changes without retaining a second database connection. The trophy worker drains them before
selecting ready runs, and retries transient invalidation failures. Once results settle,
the run can be processed again without double counting. Deleting a run reconciles its collection
counts from the remaining ledger, without rescanning historical game results.

Direct SQL edits bypass these application callbacks. Administrative edits or a deliberate backfill
after adding/changing definitions must reset the affected runs explicitly. Changing old definitions
does not automatically rescan previously processed runs.

## Rules and metrics

Shared match-statistic calculations and the existing numeric expression evaluator are reused, with
only the run's confirmed games supplied as input. `matches_played`, `matches_won`, `matches_lost`,
partner/opponent variety, win/loss streaks, close wins and shutouts therefore refer to that run.
Additional metrics are:

| Metric | Meaning |
| --- | --- |
| `final_rank` | Individual place for rotating partners; team place for fixed partners |
| `final_total_competitors` | Number of people/guest places or fixed teams that actually played |
| `is_final_overall_last` | Last place, with at least two distinct result groups |
| `round_robin_players` | Actual participants, including guest places |
| `round_robin_games` | Confirmed games in the run |
| `is_round_robin_host` | The session owner, or run creator for a standalone run |
| `rotating_partners` | 1 for rotating partners, 0 for fixed teams |
| `wins_with_same_partner` | Most wins with any one registered partner in the run |
| `points_for` | Points scored in that person's actual games |

Placements use wins, then points for. Exact ties share a place using dense ranks (1, 1, 2).
Guests can affect placements but do not receive account trophies. Byes and people who never played
are absent from the standings. Fixed teams use stable roster slots: registered contributors to a
placed team share its award, while participation and partner statistics use each person's actual
games. A person who played for multiple fixed teams has no single team placement. Limited claims,
if configured on custom trophies, apply to competing people/teams within a run; default awards are
unlimited. Nonrepeatable definitions retain one collection award if any qualifying run remains.

Trophy collections label these awards **Round robin**. The session's current matchup board shows
the winner and runner-up awards in that order, with artwork and player profile links. Other awards
remain in trophy collections. The normal live refresh picks up processing and invalidation.

## Validation

`RoundRobinTrophyAwardIntegrationTest` checks transaction callbacks, concurrent workers,
independent runs in one session, score changes, pending confirmation, voids, expiry, early endings,
byes, ties, team awards, replacements, guests, hosting, collection reconciliation, and season isolation.
`RoundRobinTrophySweepJobTest` checks overlapping sweeps and recovery after a failed run.
