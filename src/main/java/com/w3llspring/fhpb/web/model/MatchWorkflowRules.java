package com.w3llspring.fhpb.web.model;

import java.util.Collections;
import java.util.Set;

/**
 * Shared workflow rules for provisional match actions.
 *
 * <p>This keeps edit-permission logic consistent across controllers, row models, and confirmation
 * rebuilds.
 */
public final class MatchWorkflowRules {

  private MatchWorkflowRules() {}

  public static boolean canEdit(Match match, User actor, boolean ladderAdmin) {
    if (match == null || actor == null || actor.getId() == null) {
      return false;
    }
    boolean adminOverride = ladderAdmin && canUseAdminOverride(match, actor);
    if (match.isConfirmationLocked()) {
      return false;
    }
    MatchState state = match.getState();
    if (state == MatchState.NULLIFIED) {
      return false;
    }
    if (state == MatchState.CONFIRMED || state == MatchState.FLAGGED) {
      return adminOverride;
    }
    if (adminOverride) {
      return true;
    }
    if (requiresOpponentConfirmation(match)) {
      return hasConfirmationPriority(match, actor)
          || canLoggerSelfCorrectVoiceSubmission(match, actor);
    }
    return isParticipant(match, actor);
  }

  public static boolean canNonAdminEdit(Match match, User actor) {
    return canEdit(match, actor, false);
  }

  public static boolean hasConfirmationPriority(Match match, User actor) {
    if (match == null
        || actor == null
        || actor.getId() == null
        || !requiresOpponentConfirmation(match)) {
      return false;
    }
    String actorTeam = teamForUser(match, actor);
    if (actorTeam == null) {
      return false;
    }
    return !representedTeamsForStandardFlow(match).contains(actorTeam);
  }

  public static Set<String> representedTeamsForStandardFlow(Match match) {
    User representedUser = representedUserForStandardFlow(match);
    String representedTeam = teamForUser(match, representedUser);
    if (representedTeam == null) {
      return Collections.emptySet();
    }
    return Set.of(representedTeam);
  }

  public static User representedUserForStandardFlow(Match match) {
    if (match == null) {
      return null;
    }
    if (isEditedMatch(match)) {
      User editor = match.getEditedBy();
      return isParticipant(match, editor) ? editor : null;
    }
    User logger = match.getLoggedBy();
    return isParticipant(match, logger) ? logger : null;
  }

  public static boolean isEditedMatch(Match match) {
    return match != null
        && (match.getEditedBy() != null || match.getEditedAt() != null || match.isUserCorrected());
  }

  public static boolean isParticipant(Match match, User actor) {
    return teamForUser(match, actor) != null;
  }

  public static String teamForUser(Match match, User actor) {
    if (match == null || actor == null || actor.getId() == null) {
      return null;
    }
    Long userId = actor.getId();
    if (isSameUser(match.getA1(), userId) || isSameUser(match.getA2(), userId)) {
      return "A";
    }
    if (isSameUser(match.getB1(), userId) || isSameUser(match.getB2(), userId)) {
      return "B";
    }
    return null;
  }

  public static boolean isSelfConfirm(Match match) {
    LadderSecurity security = securityLevel(match);
    return security != null && LadderSecurity.normalize(security).isSelfConfirm();
  }

  public static boolean requiresOpponentConfirmation(Match match) {
    LadderSecurity security = securityLevel(match);
    return security == null || LadderSecurity.normalize(security).requiresOpponentConfirmation();
  }

  private static LadderSecurity securityLevel(Match match) {
    if (match == null || match.getSeason() == null || match.getSeason().getLadderConfig() == null) {
      return null;
    }
    return match.getSeason().getLadderConfig().getSecurityLevel();
  }

  private static boolean canUseAdminOverride(Match match, User actor) {
    if (match == null || actor == null) {
      return false;
    }
    if (match.getState() == MatchState.CONFIRMED && isCompetitionMatch(match)) {
      return actor.isAdmin();
    }
    return true;
  }

  private static boolean isCompetitionMatch(Match match) {
    return match != null
        && match.getSeason() != null
        && match.getSeason().getLadderConfig() != null
        && match.getSeason().getLadderConfig().isCompetitionType();
  }

  private static boolean canLoggerSelfCorrectVoiceSubmission(Match match, User actor) {
    if (match == null || actor == null || actor.getId() == null) {
      return false;
    }
    if (match.getState() != MatchState.PROVISIONAL || isEditedMatch(match)) {
      return false;
    }
    if (!hasVoiceSubmissionContext(match)) {
      return false;
    }
    User logger = match.getLoggedBy();
    if (!isSameUser(logger, actor.getId())) {
      return false;
    }
    return isParticipant(match, actor);
  }

  private static boolean hasVoiceSubmissionContext(Match match) {
    return match != null && match.getTranscript() != null && !match.getTranscript().isBlank();
  }

  private static boolean isSameUser(User candidate, Long userId) {
    return candidate != null && candidate.getId() != null && candidate.getId().equals(userId);
  }
}
