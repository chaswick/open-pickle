package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

  @Query(
      "select m from Match m "
          + "where m.playedAt >= :start and m.playedAt < :end "
          + "and (m.a1 in :players or m.a2 in :players or m.b1 in :players or m.b2 in :players) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findRecentPlayedMatchesForPlayers(
      @Param("players") Collection<User> players,
      @Param("start") Instant start,
      @Param("end") Instant end);

  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "and m.playedAt >= :start and m.playedAt < :end "
          + "and (m.a1 in :players or m.a2 in :players or m.b1 in :players or m.b2 in :players) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findRecentPlayedMatchesForPlayersInSeason(
      @Param("season") LadderSeason season,
      @Param("players") Collection<User> players,
      @Param("start") Instant start,
      @Param("end") Instant end);

  @Query(
      "select m from Match m "
          + "where m.sourceSessionConfig.id = :sessionConfigId "
          + "and m.playedAt >= :start and m.playedAt < :end "
          + "and (m.a1 in :players or m.a2 in :players or m.b1 in :players or m.b2 in :players) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findRecentPlayedMatchesForPlayersInSession(
      @Param("sessionConfigId") Long sessionConfigId,
      @Param("players") Collection<User> players,
      @Param("start") Instant start,
      @Param("end") Instant end);

  // Logging-time/diagnostic query: this intentionally uses createdAt, not playedAt.
  List<Match> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      Instant startInclusive, Instant endExclusive);

  @Query(
      "select m.id from Match m "
          + "where m.createdAt <= :cutoff "
          + "and (m.state is null "
          + "     or (m.state <> com.w3llspring.fhpb.web.model.MatchState.CONFIRMED "
          + "         and m.state <> com.w3llspring.fhpb.web.model.MatchState.FLAGGED "
          + "         and m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)) "
          + "order by m.createdAt asc, m.id asc")
  List<Long> findPendingConfirmationCandidateIdsCreatedBefore(@Param("cutoff") Instant cutoff);

  @Query(
      "select m from Match m "
          + "where m.createdAt >= :start and m.createdAt < :end "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.createdAt desc")
  List<Match> findByCreatedAtInRange(@Param("start") Instant start, @Param("end") Instant end);

  @Query(
      "select m from Match m "
          + "where m.createdAt >= :start and m.createdAt < :end "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.createdAt desc")
  List<Match> findByCreatedAtInRange(
      @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

  @Query(
      "select count(m) from Match m "
          + "where m.createdAt >= :start and m.createdAt < :end "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  long countByCreatedAtInRange(@Param("start") Instant start, @Param("end") Instant end);

  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findBySeasonOrderByPlayedAtDesc(@Param("season") LadderSeason season);

  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findBySeasonOrderByPlayedAtDescIncludingNullified(
      @Param("season") LadderSeason season);

  // OPTIMIZED: Eagerly fetch all users for season matches listing
  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.disputedBy "
          + "where m.season = :season "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findBySeasonOrderByPlayedAtDescWithUsers(@Param("season") LadderSeason season);

  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Match> findBySeason(@Param("season") LadderSeason season);

  long countByLoggedBy_IdAndCreatedAtGreaterThanEqual(
      Long loggedById, Instant createdAtStartInclusive);

  @Query(
      "select m from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Match> findByParticipant(@Param("user") User user);

  // OPTIMIZED: Eagerly fetch all users for participant matches
  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.disputedBy "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Match> findByParticipantWithUsers(@Param("user") User user);

  @Query(
      "select m from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findByParticipantOrderByPlayedAtDesc(@Param("user") User user);

  @Query(
      "select m from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findByParticipantOrderByPlayedAtDescIncludingNullified(@Param("user") User user);

  // Pageable variant for listing participant matches with pagination (newest first by playedAt)
  @Query(
      "select m from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  Page<Match> findByParticipantOrderByPlayedAtDesc(@Param("user") User user, Pageable pageable);

  @Query(
      "select m.id from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Long> findParticipantMatchIdsOrderByPlayedAtDesc(
      @Param("user") User user, Pageable pageable);

  @Query(
      "select m.id from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "order by m.playedAt desc, m.id desc")
  List<Long> findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(
      @Param("user") User user, Pageable pageable);

  @Query(
      "select count(m) from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  long countParticipantMatches(@Param("user") User user);

  @Query(
      "select count(m) from Match m "
          + "where (m.a1 = :user or m.a2 = :user or m.b1 = :user or m.b2 = :user)")
  long countParticipantMatchesIncludingNullified(@Param("user") User user);

  // Pageable variant for season-ordered matches (newest first by playedAt)
  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  Page<Match> findBySeasonOrderByPlayedAtDesc(
      @Param("season") LadderSeason season, Pageable pageable);

  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "order by m.playedAt desc, m.id desc")
  Page<Match> findBySeasonOrderByPlayedAtDescIncludingNullified(
      @Param("season") LadderSeason season, Pageable pageable);

  // For recalc after nullification, get all confirmed matches for a season in played order.
  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "and (m.state is null or m.state = com.w3llspring.fhpb.web.model.MatchState.CONFIRMED) "
          + "order by m.playedAt asc, m.id asc")
  List<Match> findConfirmedForSeasonChrono(@Param("season") LadderSeason season);

  // Phase D: User Correction ML - Find user's corrected matches for learning
  @Query(
      "select m from Match m "
          + "where m.userCorrected = true "
          + "and (m.a1.id = :userId or m.a2.id = :userId or m.b1.id = :userId or m.b2.id = :userId) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findUserCorrectedMatchesByParticipant(
      @Param("userId") Long userId, Pageable pageable);

  // Trust system: Find matches logged by a specific user in a season
  @Query(
      "select m from Match m " + "where m.loggedBy.id = :userId " + "and m.season.id = :seasonId")
  List<Match> findByLoggedByIdAndSeasonId(
      @Param("userId") Long userId, @Param("seasonId") Long seasonId);

  // Batch fetch matches logged by any of the given users within a season (join fetch player
  // references to avoid N+1)
  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "where m.loggedBy.id in :userIds "
          + "and m.season.id = :seasonId")
  List<Match> findByLoggedByIdInAndSeasonId(
      @Param("userIds") java.util.Collection<Long> userIds, @Param("seasonId") Long seasonId);

  // OPTIMIZED: Eagerly fetch all users when loading a match for confirmation to avoid N+1 queries
  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.sourceSessionConfig "
          + "left join fetch m.editedBy "
          + "left join fetch m.disputedBy "
          + "left join fetch m.cosignedBy "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where m.id = :id")
  java.util.Optional<Match> findByIdWithUsers(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.sourceSessionConfig "
          + "left join fetch m.editedBy "
          + "left join fetch m.disputedBy "
          + "left join fetch m.cosignedBy "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where m.id = :id")
  java.util.Optional<Match> findByIdWithUsersForUpdate(@Param("id") Long id);

  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.sourceSessionConfig "
          + "left join fetch m.editedBy "
          + "left join fetch m.disputedBy "
          + "left join fetch m.cosignedBy "
          + "left join fetch m.season s "
          + "left join fetch s.ladderConfig "
          + "where m.id in :ids")
  List<Match> findAllByIdInWithUsers(@Param("ids") java.util.Collection<Long> ids);

  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.disputedBy "
          + "where m.sourceSessionConfig.id = :sessionConfigId "
          + "and m.state = com.w3llspring.fhpb.web.model.MatchState.CONFIRMED "
          + "order by m.playedAt desc, m.id desc")
  List<Match> findConfirmedBySourceSessionConfigIdOrderByPlayedAtDescWithUsers(
      @Param("sessionConfigId") Long sessionConfigId);

  @Query(
      "select distinct m from Match m "
          + "left join fetch m.a1 "
          + "left join fetch m.a2 "
          + "left join fetch m.b1 "
          + "left join fetch m.b2 "
          + "left join fetch m.loggedBy "
          + "left join fetch m.disputedBy "
          + "where m.season = :season "
          + "and m.state = :state "
          + "order by m.disputedAt desc, m.playedAt desc, m.id desc")
  List<Match> findBySeasonAndStateOrderByDisputedAtDescWithUsers(
      @Param("season") LadderSeason season, @Param("state") MatchState state);

  @Query(
      "select distinct m.a1.id from Match m "
          + "where m.season = :season "
          + "and m.a1 is not null "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Long> findDistinctA1IdsBySeason(@Param("season") LadderSeason season);

  @Query(
      "select distinct m.a2.id from Match m "
          + "where m.season = :season "
          + "and m.a2 is not null "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Long> findDistinctA2IdsBySeason(@Param("season") LadderSeason season);

  @Query(
      "select distinct m.b1.id from Match m "
          + "where m.season = :season "
          + "and m.b1 is not null "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Long> findDistinctB1IdsBySeason(@Param("season") LadderSeason season);

  @Query(
      "select distinct m.b2.id from Match m "
          + "where m.season = :season "
          + "and m.b2 is not null "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED)")
  List<Long> findDistinctB2IdsBySeason(@Param("season") LadderSeason season);

  @Query(
      "select m from Match m "
          + "where m.season = :season "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt asc, m.id asc")
  List<Match> findBySeasonOrderByPlayedAtAsc(@Param("season") LadderSeason season);

  @Query(
      "select distinct m from Match m "
          + "left join m.sourceSessionConfig sourceSessionConfig "
          + "where (m.season = :season or sourceSessionConfig.targetSeasonId = :targetSeasonId) "
          + "and (m.state is null or m.state <> com.w3llspring.fhpb.web.model.MatchState.NULLIFIED) "
          + "order by m.playedAt asc, m.id asc")
  List<Match> findBySeasonOrSourceSessionTargetSeasonOrderByPlayedAtAsc(
      @Param("season") LadderSeason season, @Param("targetSeasonId") Long targetSeasonId);
}
