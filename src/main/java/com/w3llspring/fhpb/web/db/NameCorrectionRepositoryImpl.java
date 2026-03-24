package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.NameCorrection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NameCorrectionRepositoryImpl implements NameCorrectionRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(NameCorrectionRepositoryImpl.class);

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public NameCorrection incrementCorrectionCount(
      Long ladderConfigId,
      String tokenNormalized,
      String phoneticKey,
      Long userId,
      Long reporterUserId,
      int delta,
      Instant lastConfirmedAt) {
    try {
      // Try to find existing correction for exact user + token + ladder and lock it for update
      String q =
          "SELECT nc FROM NameCorrection nc WHERE nc.tokenNormalized = :token AND nc.userId = :userId";
      if (ladderConfigId == null) {
        q += " AND nc.ladderConfigId IS NULL";
      } else {
        q += " AND nc.ladderConfigId = :ladderId";
      }
      TypedQuery<NameCorrection> tq = em.createQuery(q, NameCorrection.class);
      tq.setParameter("token", tokenNormalized);
      tq.setParameter("userId", userId);
      if (ladderConfigId != null) tq.setParameter("ladderId", ladderConfigId);
      tq.setLockMode(LockModeType.PESSIMISTIC_WRITE);
      NameCorrection nc = null;
      try {
        nc = tq.getSingleResult();
      } catch (NoResultException nre) {
        nc = null;
      }

      if (nc != null) {
        Integer cur = nc.getCount() == null ? 0 : nc.getCount();
        nc.setCount(cur + delta);
        nc.setLastConfirmedAt(lastConfirmedAt != null ? lastConfirmedAt : Instant.now());
        if (reporterUserId != null) nc.setReporterUserId(reporterUserId);
        if (phoneticKey != null && nc.getPhoneticKey() == null) nc.setPhoneticKey(phoneticKey);
        nc = em.merge(nc);
        return nc;
      }

      // No existing row - create one
      NameCorrection created = new NameCorrection();
      created.setTokenNormalized(tokenNormalized);
      created.setLadderConfigId(ladderConfigId);
      created.setUserId(userId == null ? -1L : userId);
      created.setReporterUserId(reporterUserId);
      created.setPhoneticKey(phoneticKey);
      created.setCount(delta);
      created.setLastConfirmedAt(lastConfirmedAt != null ? lastConfirmedAt : Instant.now());
      em.persist(created);
      // ensure flush to get id
      em.flush();
      return created;
    } catch (Exception ex) {
      log.warn("Failed to increment/create NameCorrection atomically: {}", ex.getMessage());
      // Fall back to non-atomic path: try to find without lock and persist
      try {
        List<NameCorrection> found =
            em.createQuery(
                    "SELECT nc FROM NameCorrection nc WHERE nc.tokenNormalized = :token",
                    NameCorrection.class)
                .setParameter("token", tokenNormalized)
                .getResultList();
        if (!found.isEmpty()) {
          NameCorrection nc = found.get(0);
          nc.setCount((nc.getCount() == null ? 0 : nc.getCount()) + delta);
          nc.setLastConfirmedAt(lastConfirmedAt != null ? lastConfirmedAt : Instant.now());
          if (reporterUserId != null) nc.setReporterUserId(reporterUserId);
          return em.merge(nc);
        }
      } catch (Exception e2) {
        log.warn("Fallback path also failed: {}", e2.getMessage());
      }
      return null;
    }
  }
}
