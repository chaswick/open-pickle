package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  @Query("SELECT u FROM User u WHERE u.email = ?1")
  public User findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.email = :email")
  Optional<User> findByEmailForUpdate(@Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") Long id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update User u set u.lastSeenAt = :lastSeenAt where u.id = :id")
  int updateLastSeenAtById(@Param("id") Long id, @Param("lastSeenAt") Instant lastSeenAt);

  public User findByResetPasswordToken(String token);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.resetPasswordToken = :token")
  Optional<User> findByResetPasswordTokenForUpdate(@Param("token") String token);

  @Query(value = "SELECT * FROM users WHERE nick_name = ?1", nativeQuery = true)
  public User findByNickName(String nickName);

  Optional<User> findByPublicCode(String publicCode);

  boolean existsByPublicCode(String publicCode);

  List<User> findByPublicCodeIsNull(Pageable pageable);

  long countByLastSeenAtGreaterThanEqual(Instant cutoff);

  long countByRegisteredAtGreaterThanEqual(Instant cutoff);

  List<User> findByLastSeenAtGreaterThanEqualOrderByLastSeenAtDesc(
      Instant cutoff, Pageable pageable);

  List<User> findByRegisteredAtGreaterThanEqualOrderByRegisteredAtDesc(
      Instant cutoff, Pageable pageable);

  @Query(
      """
                        select u
                        from User u
                        where u.meetupsEmailPending = true
                            and u.meetupsEmailOptIn = true
                        """)
  List<User> findUsersWithPendingMeetupsDigest();

  @Query(
      """
            select u.id
            from User u
            where u.meetupsEmailPending = true
                and u.meetupsEmailOptIn = true
            """)
  List<Long> findUserIdsWithPendingMeetupsDigest();

  @Query(
      """
            select u
            from User u
            where u.id > :afterId
                and (u.isAdmin is null or u.isAdmin = false)
                and coalesce(u.registeredAt, u.acknowledgedTermsAt, u.lastSeenAt) <= :cutoff
                and not exists (
                    select m.id
                    from Match m
                    where m.a1 = u
                        or m.a2 = u
                        or m.b1 = u
                        or m.b2 = u
                        or m.loggedBy = u
                        or m.editedBy = u
                        or m.cosignedBy = u
                )
                and not exists (select mc.id from MatchConfirmation mc where mc.player = u)
                and not exists (select ls.id from LadderStanding ls where ls.user = u)
                and not exists (select bp.id from BandPosition bp where bp.user = u)
                and not exists (select rr.id from RoundRobin rr where rr.createdBy = u)
                and not exists (
                    select rre.id
                    from RoundRobinEntry rre
                    where rre.a1 = u
                        or rre.a2 = u
                        or rre.b1 = u
                        or rre.b2 = u
                )
                and not exists (select ut.id from UserTrophy ut where ut.user = u)
                and not exists (select uc.id from UserCredit uc where uc.user = u)
                and not exists (
                    select us.id
                    from UserStyle us
                    where us.styleOwner = u
                        or us.styleVoter = u
                )
                and not exists (select lm.id from LadderMembership lm where lm.userId = u.id)
                and not exists (select lc.id from LadderConfig lc where lc.ownerUserId = u.id)
                and not exists (select lms.id from LadderMeetupSlot lms where lms.createdByUserId = u.id)
                and not exists (select lmr.id from LadderMeetupRsvp lmr where lmr.userId = u.id)
                and not exists (
                    select lsea.id
                    from LadderSeason lsea
                    where lsea.startedByUserId = u.id
                        or lsea.endedByUserId = u.id
                )
                and not exists (
                    select nc.id
                    from NameCorrection nc
                    where nc.userId = u.id
                        or nc.reporterUserId = u.id
                )
                and not exists (
                    select cdr.id
                    from CompetitionDisplayNameReport cdr
                    where cdr.targetUserId = u.id
                        or cdr.reporterUserId = u.id
                )
                and not exists (select ie.id from InterpretationEvent ie where ie.currentUserId = u.id)
                and not exists (select ups.id from UserPushSubscription ups where ups.userId = u.id)
            order by u.id asc
            """)
  List<User> findStaleZeroFootprintUsersAfterId(
      @Param("afterId") Long afterId, @Param("cutoff") Instant cutoff, Pageable pageable);
}
