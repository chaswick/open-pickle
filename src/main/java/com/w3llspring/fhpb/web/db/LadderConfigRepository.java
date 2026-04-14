package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.LadderConfig;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LadderConfigRepository extends JpaRepository<LadderConfig, Long> {
  Optional<LadderConfig> findByInviteCode(String inviteCode);

  List<LadderConfig> findByPendingDeletionIsTrueAndPendingDeletionAtBefore(Instant cutoff);

  // LadderConfigRepository.java
  List<LadderConfig> findByPendingDeletionIsTrueAndPendingDeletionByUserId(Long userId);

  List<LadderConfig> findByOwnerUserIdAndPendingDeletionIsTrue(Long ownerUserId);

  // === New (added) ===

  /** Serialize season start/end per ladder (race safety). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from LadderConfig l where l.id = :id")
  LadderConfig lockById(Long id);

  /** For the rolling job or admin pages that filter by mode. */
  List<LadderConfig> findByMode(LadderConfig.Mode mode);

  long countByOwnerUserId(Long ownerUserId);

  List<LadderConfig> findByOwnerUserId(Long ownerUserId);

  Optional<LadderConfig> findFirstByOwnerUserIdAndTitleIgnoreCase(Long ownerUserId, String title);

  long countByOwnerUserIdAndTypeNot(Long ownerUserId, LadderConfig.Type type);

  long countByOwnerUserIdAndTypeAndStatusAndExpiresAtAfter(
      Long ownerUserId, LadderConfig.Type type, LadderConfig.Status status, Instant cutoff);

  long countByOwnerUserIdAndTypeAndCreatedAtAfter(
      Long ownerUserId, LadderConfig.Type type, Instant cutoff);

  long countByTypeAndCreatedAtGreaterThanEqual(LadderConfig.Type type, Instant cutoff);

  List<LadderConfig> findByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
      LadderConfig.Type type, Instant cutoff, Pageable pageable);

  Optional<LadderConfig> findFirstByOwnerUserIdAndTypeOrderByIdAsc(
      Long ownerUserId, LadderConfig.Type type);

  List<LadderConfig> findByTypeAndStatusAndExpiresAtBefore(
      LadderConfig.Type type, LadderConfig.Status status, Instant cutoff);

  List<LadderConfig>
      findByTypeAndStatusAndNearbyShareLocationIdAndInviteCodeIsNotNullAndExpiresAtAfterOrderByUpdatedAtDesc(
          LadderConfig.Type type,
          LadderConfig.Status status,
          Long nearbyShareLocationId,
          Instant cutoff);

  @Query(
      """
      select count(l)
      from LadderConfig l
      where l.type = :type
          and l.status = :status
          and (l.expiresAt is null or l.expiresAt > :cutoff)
      """)
  long countActiveByType(
      @Param("type") LadderConfig.Type type,
      @Param("status") LadderConfig.Status status,
      @Param("cutoff") Instant cutoff);

  @Query(
      """
      select l
      from LadderConfig l
      where l.type = :type
          and l.status = :status
          and (l.expiresAt is null or l.expiresAt > :cutoff)
      order by l.updatedAt desc, l.id desc
      """)
  List<LadderConfig> findActiveByTypeOrderByUpdatedAtDesc(
      @Param("type") LadderConfig.Type type,
      @Param("status") LadderConfig.Status status,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);

  Optional<LadderConfig> findFirstByTypeOrderByIdAsc(LadderConfig.Type type);
}
