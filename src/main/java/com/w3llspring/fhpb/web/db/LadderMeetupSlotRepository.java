package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderMeetupSlot;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderMeetupSlotRepository extends JpaRepository<LadderMeetupSlot, Long> {

  @Query(
      """
            select count(s)
            from LadderMeetupSlot s
            where s.canceledAt is null
              and s.ladderConfig.id = :ladderId
              and s.startsAt = :startsAt
            """)
  long countActiveByLadderAndStartsAt(
      @Param("ladderId") Long ladderId, @Param("startsAt") Instant startsAt);

  @Query(
      """
            select s
            from LadderMeetupSlot s
            where s.canceledAt is null
              and s.startsAt >= :from
              and s.startsAt <= :to
              and s.ladderConfig.id in :ladderIds
            order by s.startsAt asc
            """)
  List<LadderMeetupSlot> findUpcomingForLadders(
      @Param("ladderIds") Collection<Long> ladderIds,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      """
            select count(s)
            from LadderMeetupSlot s
            where s.canceledAt is null
              and s.startsAt >= :from
              and s.ladderConfig.id = :ladderId
              and s.createdByUserId = :userId
            """)
  long countActiveUpcomingByLadderAndCreator(
      @Param("ladderId") Long ladderId, @Param("userId") Long userId, @Param("from") Instant from);

  @Query(
      """
            select s
            from LadderMeetupSlot s
            where s.canceledAt is null
              and s.startsAt >= :from
              and s.ladderConfig.id in :ladderIds
            order by s.startsAt asc
            """)
  List<LadderMeetupSlot> findFutureForLadders(
      @Param("ladderIds") Collection<Long> ladderIds, @Param("from") Instant from);
}
