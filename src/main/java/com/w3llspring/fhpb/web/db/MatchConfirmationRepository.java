package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchConfirmationRepository extends JpaRepository<MatchConfirmation, Long> {

  interface SessionConfirmedMatchTimeline {
    Long getMatchId();

    Instant getConfirmedAt();
  }

  Optional<MatchConfirmation> findByMatchAndPlayer(Match match, User player);

  List<MatchConfirmation> findByMatch(Match match);

  List<MatchConfirmation> findByMethodAndConfirmedAtBefore(
      MatchConfirmation.ConfirmationMethod method, Instant before);

  // Find pending placeholders (no confirmedAt) for a given player and method
  List<MatchConfirmation> findByPlayerAndMethodAndConfirmedAtIsNull(
      User player, MatchConfirmation.ConfirmationMethod method);

  /** Delete all confirmation rows for a given match. */
  void deleteByMatch(Match match);

  /**
   * Prune pending (not confirmed) confirmation rows older than the provided cutoff. Returns number
   * deleted.
   */
  int deleteByCreatedAtBeforeAndConfirmedAtIsNull(Instant cutoff);

  /** Trust system: Find confirmation requests for a specific player in a season. */
  @Query(
      "select mc from MatchConfirmation mc "
          + "where mc.player.id = :playerId "
          + "and mc.match.season.id = :seasonId")
  List<MatchConfirmation> findByPlayerIdAndMatchSeasonId(
      @Param("playerId") Long playerId, @Param("seasonId") Long seasonId);

  /** Batch loader used by dashboards to gather confirmations without per-match queries. */
  @Query(
      "select distinct mc from MatchConfirmation mc "
          + "join fetch mc.match m "
          + "left join fetch mc.player "
          + "where m.id in :matchIds")
  List<MatchConfirmation> findByMatchIdIn(
      @Param("matchIds") Collection<Long> matchIds);

  /** Batch loader for player-season confirmation requests. */
  @Query(
      "select distinct mc from MatchConfirmation mc "
          + "join fetch mc.match m "
          + "left join fetch mc.player p "
          + "where p.id in :playerIds "
          + "and m.season.id = :seasonId")
  List<MatchConfirmation> findByPlayerIdInAndMatchSeasonId(
      @Param("playerIds") Collection<Long> playerIds, @Param("seasonId") Long seasonId);

  @Query(
      "select mc.match.id as matchId, max(mc.confirmedAt) as confirmedAt "
          + "from MatchConfirmation mc "
          + "where mc.match.sourceSessionConfig.id = :sessionConfigId "
          + "and mc.match.state = :confirmedState "
          + "and mc.confirmedAt is not null "
          + "group by mc.match.id "
          + "order by max(mc.confirmedAt) desc, mc.match.id desc")
  List<SessionConfirmedMatchTimeline> findRecentConfirmedSessionTimelines(
      @Param("sessionConfigId") Long sessionConfigId,
      @Param("confirmedState") MatchState confirmedState,
      Pageable pageable);
}
