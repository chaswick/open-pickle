package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderSeason;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderSeasonRepository extends JpaRepository<LadderSeason, Long> {

  // === Existing (kept) ===

  Optional<LadderSeason> findTopByOrderByStartDateDesc();

  List<LadderSeason> findByStateOrderByStartDateDesc(LadderSeason.State state);

  Optional<LadderSeason>
      findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          LocalDate start, LocalDate end);

  Optional<LadderSeason> findFirstByStartDateBeforeOrderByStartDateDesc(LocalDate startDate);

  Optional<LadderSeason> findFirstByStartDateAfterOrderByStartDateAsc(LocalDate startDate);

  Optional<LadderSeason> findByStartDate(LocalDate startDate);

  List<LadderSeason> findByLadderConfigIdOrderByStartDateDesc(Long ladderConfigId);

  Optional<LadderSeason>
      findFirstByLadderConfigIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          Long configId, LocalDate start, LocalDate end);

  Optional<LadderSeason> findTopByLadderConfigIdOrderByStartDateDesc(Long configId);

  Optional<LadderSeason> findFirstByLadderConfigIdAndStartDateBeforeOrderByStartDateDesc(
      Long configId, LocalDate startDate);

  Optional<LadderSeason> findFirstByLadderConfigIdAndStartDateAfterOrderByStartDateAsc(
      Long configId, LocalDate startDate);

  // === New (added) ===

  /** The single ACTIVE season for a ladder (if any). */
  @Query("select s from LadderSeason s where s.ladderConfig.id = :ladderId and s.state = 'ACTIVE'")
  Optional<LadderSeason> findActive(Long ladderId);

  @Query("select s from LadderSeason s join fetch s.ladderConfig where s.id = :id")
  Optional<LadderSeason> findByIdWithLadderConfig(@Param("id") Long id);

  @Query(
      "select s from LadderSeason s join fetch s.ladderConfig "
          + "where s.storyModeEnabled = true and s.state = :state "
          + "order by coalesce(s.endedAt, s.startedAt) desc")
  List<LadderSeason> findByStoryModeEnabledTrueAndStateOrderByTransitionDesc(
      @Param("state") LadderSeason.State state);

  @Query(
      "select s from LadderSeason s join fetch s.ladderConfig "
          + "where s.storyModeEnabled = true and s.state = :state "
          + "order by coalesce(s.endedAt, s.startedAt) desc")
  List<LadderSeason> findByStoryModeEnabledTrueAndStateOrderByTransitionDesc(
      @Param("state") LadderSeason.State state, Pageable pageable);

  /** Count transitions (starts or ends) since given timestamp. */
  @Query(
      "select count(s) from LadderSeason s "
          + "where s.ladderConfig.id = :ladderId and s.state <> 'SCHEDULED' and "
          + "(s.startedAt >= :since or (s.endedAt is not null and s.endedAt >= :since))")
  long countTransitionsSince(Long ladderId, Instant since);

  /** Recent seasons ordered by last transition moment. */
  @Query(
      "select s from LadderSeason s "
          + "where s.ladderConfig.id = :ladderId "
          + "order by coalesce(s.endedAt, s.startedAt) desc")
  List<LadderSeason> findRecent(Long ladderId, Pageable pageable);

  Optional<LadderSeason> findTopByLadderConfigIdAndStateOrderByStartedAtDesc(
      Long ladderId, LadderSeason.State state);

  Optional<LadderSeason> findTopByLadderConfigIdAndStateOrderByStartDateAsc(
      Long ladderId, LadderSeason.State state);

  Optional<LadderSeason>
      findFirstByStateAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          LadderSeason.State state, LocalDate start, LocalDate end);

  Optional<LadderSeason>
      findFirstByLadderConfigIdAndStateAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          Long ladderConfigId, LadderSeason.State state, LocalDate start, LocalDate end);

  /** Find the most recent season creation timestamp within the given time window. */
  @Query(
      "select max(s.startedAt) from LadderSeason s "
          + "where s.ladderConfig.id = :ladderId and s.startedAt >= :since")
  Optional<Instant> findLastSeasonCreatedAt(Long ladderId, Instant since);

  @Query("select count(s) from LadderSeason s where s.state = 'ACTIVE'")
  long countActive();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update LadderSeason s "
          + "set s.standingsRecalcInFlight = coalesce(s.standingsRecalcInFlight, 0) + 1, "
          + "s.standingsRecalcLastStartedAt = :startedAt "
          + "where s.id = :seasonId")
  int incrementStandingsRecalcInFlight(
      @Param("seasonId") Long seasonId, @Param("startedAt") Instant startedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update LadderSeason s "
          + "set s.standingsRecalcInFlight = "
          + "case when coalesce(s.standingsRecalcInFlight, 0) > 0 "
          + "then coalesce(s.standingsRecalcInFlight, 0) - 1 else 0 end, "
          + "s.standingsRecalcLastFinishedAt = :finishedAt "
          + "where s.id = :seasonId")
  int decrementStandingsRecalcInFlight(
      @Param("seasonId") Long seasonId, @Param("finishedAt") Instant finishedAt);
}
