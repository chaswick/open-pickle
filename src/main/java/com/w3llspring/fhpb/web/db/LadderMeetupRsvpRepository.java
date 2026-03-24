package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import com.w3llspring.fhpb.web.model.LadderMeetupRsvp.Status;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderMeetupRsvpRepository extends JpaRepository<LadderMeetupRsvp, Long> {

  Optional<LadderMeetupRsvp> findBySlotIdAndUserId(Long slotId, Long userId);

  List<LadderMeetupRsvp> findBySlotIdIn(Collection<Long> slotIds);

  @Query(
      """
                        select r.userId
                        from LadderMeetupRsvp r
                        where r.slot.id = :slotId
                            and r.status in :statuses
                        """)
  List<Long> findUserIdsBySlotIdAndStatusIn(
      @Param("slotId") Long slotId, @Param("statuses") Collection<Status> statuses);

  @Query(
      """
                        select r.slot.id
                        from LadderMeetupRsvp r
                        where r.userId = :userId
                            and r.slot.id in :slotIds
                        """)
  Set<Long> findRsvpedSlotIds(@Param("userId") Long userId, @Param("slotIds") List<Long> slotIds);
}
