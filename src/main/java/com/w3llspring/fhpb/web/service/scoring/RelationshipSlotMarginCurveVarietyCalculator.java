package com.w3llspring.fhpb.web.service.scoring;

import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RelationshipSlotMarginCurveVarietyCalculator implements MarginCurveVarietyCalculator {
  private final double fullBonusThreshold;
  private final double maxBonus;

  RelationshipSlotMarginCurveVarietyCalculator(double fullBonusThreshold, double maxBonus) {
    this.fullBonusThreshold = fullBonusThreshold;
    this.maxBonus = maxBonus;
  }

  @Override
  public MarginCurveVarietySnapshot calculate(List<Match> recentMatches, User user) {
    if (recentMatches == null || recentMatches.isEmpty() || user == null || user.getId() == null) {
      return MarginCurveVarietySnapshot.neutral();
    }

    Set<RelationshipSlot> uniqueSlots = new HashSet<>();
    int comparableMatchCount = 0;
    int repeatFloor = 0;
    int totalSeatOpportunities = 0;
    for (Match match : recentMatches) {
      int seatOpportunities = countOtherSeatOpportunities(match, user);
      if (seatOpportunities <= 0) {
        continue;
      }

      comparableMatchCount++;
      repeatFloor = Math.max(repeatFloor, seatOpportunities);
      totalSeatOpportunities += seatOpportunities;
      uniqueSlots.addAll(collectRelationshipSlots(match, user));
    }

    if (totalSeatOpportunities <= 0) {
      return MarginCurveVarietySnapshot.neutral();
    }
    if (comparableMatchCount < 2) {
      return new MarginCurveVarietySnapshot(
          recentMatches.size(),
          uniqueSlots.size(),
          repeatFloor,
          totalSeatOpportunities,
          repeatFloor,
          comparableMatchCount,
          1.0d);
    }

    double fullBonusTarget = Math.max(repeatFloor, totalSeatOpportunities * fullBonusThreshold);
    double normalized = (uniqueSlots.size() - repeatFloor) / (fullBonusTarget - repeatFloor);
    normalized = Math.max(0.0d, Math.min(1.0d, normalized));
    double multiplier = 1.0d + (normalized * maxBonus);
    return new MarginCurveVarietySnapshot(
        recentMatches.size(),
        uniqueSlots.size(),
        repeatFloor,
        totalSeatOpportunities,
        (int) Math.ceil(fullBonusTarget),
        comparableMatchCount,
        multiplier);
  }

  private Set<RelationshipSlot> collectRelationshipSlots(Match match, User user) {
    Team team = identifyTeam(match, user);
    Set<RelationshipSlot> slots = new HashSet<>();
    if (team == Team.A) {
      addRelationshipSlot(slots, match.getA1(), match.isA1Guest(), user, Relationship.PARTNER);
      addRelationshipSlot(slots, match.getA2(), match.isA2Guest(), user, Relationship.PARTNER);
      addRelationshipSlot(slots, match.getB1(), match.isB1Guest(), user, Relationship.OPPONENT);
      addRelationshipSlot(slots, match.getB2(), match.isB2Guest(), user, Relationship.OPPONENT);
    } else if (team == Team.B) {
      addRelationshipSlot(slots, match.getB1(), match.isB1Guest(), user, Relationship.PARTNER);
      addRelationshipSlot(slots, match.getB2(), match.isB2Guest(), user, Relationship.PARTNER);
      addRelationshipSlot(slots, match.getA1(), match.isA1Guest(), user, Relationship.OPPONENT);
      addRelationshipSlot(slots, match.getA2(), match.isA2Guest(), user, Relationship.OPPONENT);
    }
    return slots;
  }

  private void addRelationshipSlot(
      Set<RelationshipSlot> slots,
      User candidate,
      boolean guest,
      User self,
      Relationship relationship) {
    if (guest || candidate == null || candidate.getId() == null || sameUser(candidate, self)) {
      return;
    }
    slots.add(new RelationshipSlot(candidate.getId(), relationship));
  }

  private int countOtherSeatOpportunities(Match match, User user) {
    Team team = identifyTeam(match, user);
    if (team == null) {
      return 0;
    }

    int seats = 0;
    if (team == Team.A) {
      seats += seatOpportunityCount(match.getA1(), match.isA1Guest(), user);
      seats += seatOpportunityCount(match.getA2(), match.isA2Guest(), user);
      seats += seatOpportunityCount(match.getB1(), match.isB1Guest(), user);
      seats += seatOpportunityCount(match.getB2(), match.isB2Guest(), user);
    } else {
      seats += seatOpportunityCount(match.getB1(), match.isB1Guest(), user);
      seats += seatOpportunityCount(match.getB2(), match.isB2Guest(), user);
      seats += seatOpportunityCount(match.getA1(), match.isA1Guest(), user);
      seats += seatOpportunityCount(match.getA2(), match.isA2Guest(), user);
    }
    return seats;
  }

  private int seatOpportunityCount(User candidate, boolean guest, User self) {
    if (guest) {
      return 1;
    }
    if (candidate == null || sameUser(candidate, self)) {
      return 0;
    }
    return 1;
  }

  private Team identifyTeam(Match match, User user) {
    if (match == null || user == null || user.getId() == null) {
      return null;
    }
    if (!match.isA1Guest() && sameUser(match.getA1(), user)) {
      return Team.A;
    }
    if (!match.isA2Guest() && sameUser(match.getA2(), user)) {
      return Team.A;
    }
    if (!match.isB1Guest() && sameUser(match.getB1(), user)) {
      return Team.B;
    }
    if (!match.isB2Guest() && sameUser(match.getB2(), user)) {
      return Team.B;
    }
    return null;
  }

  private boolean sameUser(User left, User right) {
    if (left == null || right == null || left.getId() == null || right.getId() == null) {
      return false;
    }
    return left.getId().equals(right.getId());
  }

  private enum Team {
    A,
    B
  }

  private enum Relationship {
    PARTNER,
    OPPONENT
  }

  private record RelationshipSlot(Long userId, Relationship relationship) {}
}
