// LadderAccessService.java
package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LadderAccessService {

  private final LadderSeasonRepository seasonRepo;
  private final LadderMembershipRepository membershipRepo;

  public LadderAccessService(
      LadderSeasonRepository seasonRepo, LadderMembershipRepository membershipRepo) {
    this.seasonRepo = seasonRepo;
    this.membershipRepo = membershipRepo;
  }

  public LadderSeason requireSeason(Long seasonId) {
    return seasonRepo
        .findById(seasonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found"));
  }

  public boolean isSeasonMember(Long seasonId, User user) {
    if (user == null || user.getId() == null) return false;
    LadderSeason s = requireSeason(seasonId);
    Long ladderId = s.getLadderConfig().getId();
    LadderMembership lm =
        membershipRepo.findByLadderConfigIdAndUserId(ladderId, user.getId()).orElse(null);
    return lm != null && lm.getState() == LadderMembership.State.ACTIVE;
  }

  public boolean isSeasonAdmin(Long seasonId, User user) {
    if (user == null || user.getId() == null) return false;
    LadderSeason s = requireSeason(seasonId);
    if (s.getLadderConfig() == null || s.getLadderConfig().getId() == null) {
      return false;
    }
    Long userId = user.getId();
    if (s.getLadderConfig() != null
        && Objects.equals(s.getLadderConfig().getOwnerUserId(), userId)) {
      return true;
    }
    Long ladderId = s.getLadderConfig().getId();
    LadderMembership lm =
        membershipRepo.findByLadderConfigIdAndUserId(ladderId, userId).orElse(null);
    return lm != null
        && lm.getState() == LadderMembership.State.ACTIVE
        && lm.getRole() == LadderMembership.Role.ADMIN;
  }

  public void requireMember(Long seasonId, User user) {
    if (!isSeasonMember(seasonId, user)) {
      throw new SecurityException("You must be a ladder member to view this.");
    }
  }

  public void requireAdmin(Long seasonId, User user) {
    if (!isSeasonAdmin(seasonId, user)) {
      throw new SecurityException("Admin privileges required.");
    }
  }
}
