package com.w3llspring.fhpb.web.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MatchWorkflowRulesTest {

  @Nested
  @DisplayName("STANDARD opponent-confirm flow")
  class StandardFlow {

    @Test
    void logger_side_loses_edit_priority_after_logging() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isTrue();
      assertThat(MatchWorkflowRules.representedTeamsForStandardFlow(match)).containsExactly("A");
    }

    @Test
    void voice_submitted_match_allows_logger_one_self_correction_before_handoff() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.setTranscript("me and Dave beat Angelo and Alex 11-8");

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isTrue();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isTrue();
      assertThat(MatchWorkflowRules.representedTeamsForStandardFlow(match)).containsExactly("A");
    }

    @Test
    void voice_submitted_logger_self_correction_is_single_use() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.setTranscript("me and Dave beat Angelo and Alex 11-8");
      match.setEditedBy(logger);
      match.setEditedAt(Instant.now());
      match.setUserCorrected(true);

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isTrue();
      assertThat(MatchWorkflowRules.representedTeamsForStandardFlow(match)).containsExactly("A");
      assertThat(MatchWorkflowRules.representedUserForStandardFlow(match)).isEqualTo(logger);
    }

    @Test
    void participant_edit_flips_priority_to_the_other_team() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.setEditedBy(opponent);
      match.setEditedAt(Instant.now());
      match.setUserCorrected(true);

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isTrue();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isFalse();
      assertThat(MatchWorkflowRules.representedTeamsForStandardFlow(match)).containsExactly("B");
      assertThat(MatchWorkflowRules.representedUserForStandardFlow(match)).isEqualTo(opponent);
    }

    @Test
    void nonparticipant_editor_leaves_both_teams_unrepresented() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");
      User organizer = user(30L, "Organizer");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.setEditedBy(organizer);
      match.setEditedAt(Instant.now());
      match.setUserCorrected(true);

      assertThat(MatchWorkflowRules.representedTeamsForStandardFlow(match)).isEmpty();
      assertThat(MatchWorkflowRules.representedUserForStandardFlow(match)).isNull();
      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isTrue();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isTrue();
    }
  }

  @Nested
  @DisplayName("SELF_CONFIRM flow")
  class SelfConfirmFlow {

    @Test
    void participants_can_edit_provisional_matches() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.SELF_CONFIRM, logger, opponent, logger);

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isTrue();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isTrue();
    }

    @Test
    void nonparticipants_cannot_edit_provisional_matches() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");
      User outsider = user(30L, "Outsider");

      Match match = provisionalMatch(LadderSecurity.SELF_CONFIRM, logger, opponent, logger);

      assertThat(MatchWorkflowRules.canEdit(match, outsider, false)).isFalse();
    }
  }

  @Nested
  @DisplayName("State guards")
  class StateGuards {

    @Test
    void locked_matches_are_not_editable() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.setConfirmationLocked(true);

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isFalse();
    }

    @Test
    void confirmed_private_group_matches_allow_season_admin_override() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.getSeason().getLadderConfig().setType(LadderConfig.Type.STANDARD);
      match.setState(MatchState.CONFIRMED);

      assertThat(MatchWorkflowRules.canEdit(match, opponent, false)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, true)).isTrue();
    }

    @Test
    void confirmed_competition_matches_require_site_admin_override() {
      User logger = user(10L, "Logger");
      User sessionAdmin = user(20L, "SessionAdmin");
      User siteAdmin = user(30L, "SiteAdmin");
      siteAdmin.setAdmin(true);

      Match match = provisionalCompetitionMatch(logger, sessionAdmin, logger);
      match.setState(MatchState.CONFIRMED);

      assertThat(MatchWorkflowRules.canEdit(match, sessionAdmin, false)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, sessionAdmin, true)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, siteAdmin, true)).isTrue();
    }

    @Test
    void nullified_matches_are_not_editable_even_for_admins() {
      User logger = user(10L, "Logger");
      User opponent = user(20L, "Opponent");

      Match match = provisionalMatch(LadderSecurity.STANDARD, logger, opponent, logger);
      match.setState(MatchState.NULLIFIED);

      assertThat(MatchWorkflowRules.canEdit(match, logger, false)).isFalse();
      assertThat(MatchWorkflowRules.canEdit(match, opponent, true)).isFalse();
    }
  }

  private static Match provisionalMatch(LadderSecurity security, User a1, User b1, User loggedBy) {
    LadderConfig config = new LadderConfig();
    config.setSecurityLevel(security);
    LadderSeason season = new LadderSeason();
    season.setLadderConfig(config);

    Match match = new Match();
    match.setSeason(season);
    match.setState(MatchState.PROVISIONAL);
    match.setLoggedBy(loggedBy);
    match.setA1(a1);
    match.setB1(b1);
    match.setA1Guest(false);
    match.setA2Guest(true);
    match.setB1Guest(false);
    match.setB2Guest(true);
    match.setScoreA(11);
    match.setScoreB(9);
    return match;
  }

  private static Match provisionalCompetitionMatch(User a1, User b1, User loggedBy) {
    Match match = provisionalMatch(LadderSecurity.STANDARD, a1, b1, loggedBy);
    match.getSeason().getLadderConfig().setType(LadderConfig.Type.COMPETITION);
    return match;
  }

  private static User user(Long id, String nickName) {
    User user = new User();
    user.setId(id);
    user.setNickName(nickName);
    user.setEmail(nickName.toLowerCase() + "@test.local");
    return user;
  }
}
