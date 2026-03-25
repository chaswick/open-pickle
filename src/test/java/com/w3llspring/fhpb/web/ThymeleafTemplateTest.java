package com.w3llspring.fhpb.web;

import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.config.BrandingProperties;
import com.w3llspring.fhpb.web.config.OperatorProperties;
import com.w3llspring.fhpb.web.service.StoryModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ThymeleafTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    void testStandardizedButtonClassesRenderCorrectly() {
        // Test that templates with our standardized button classes can be processed
        Context context = new Context();
        String result = templateEngine.process("test-button-classes", context);

        assertThat(result).isNotNull();
        assertThat(result).contains("btn btn-action-primary");
        assertThat(result).contains("btn btn-action-danger");
        assertThat(result).contains("btn btn-nav-secondary");
        assertThat(result).contains("btn btn-nav-primary");

        // Test workflow-specific classes
        assertThat(result).contains("btn-workflow-destructive");
        assertThat(result).contains("btn-workflow-confirm");
        assertThat(result).contains("btn-workflow-happy");
        assertThat(result).contains("btn-workflow-critical");
        assertThat(result).contains("btn-workflow-safe");
    }

    @Test
    void storyModeStandingsTemplateRendersSideTaskStatusText() throws Exception {
        Context context = new Context();
        context.setVariable("storyMode", storyModeModel());
        context.setVariable("ladderDisplay", List.of());

        String result = templateEngine.process("components/storyModeStandings", context);

        assertThat(result).contains("Pat&#39;s Keys");
        assertThat(result).contains("Meet Pat the Pickler");
        assertThat(result).contains("Our Story So Far");
        assertThat(result).contains("Each unique contributor finds one key.");
        assertThat(result).contains("Earned 1 time by the group.");
        assertThat(result).contains("You have added 1 so far.");
        assertThat(result).contains("Added so far: 2 matches.");
    }

    @Test
    void createLadderConfigCardHidesCompetitionMembershipFromYourGroups() {
        String result = renderWebTemplate("components/createLadderConfigCard", Map.of(
                "myMemberships", List.of(
                        membership(99L, "Global Competition", LadderConfig.Type.COMPETITION),
                        membership(7L, "Ladder X", LadderConfig.Type.STANDARD)),
                "selectorMemberships", List.of(membership(7L, "Ladder X", LadderConfig.Type.STANDARD)),
                "restorableLadders", List.of()));

        assertThat(result).contains("Your Groups");
        assertThat(result).contains("Ladder X");
        assertThat(result).doesNotContain("Global Competition");
        assertThat(result).doesNotContain("/groups/99");
    }

    @Test
    void createLadderConfigCardShowsEmptyStateWhenOnlyCompetitionMembershipIsHidden() {
        String result = renderWebTemplate("components/createLadderConfigCard", Map.of(
                "myMemberships", List.of(membership(99L, "Global Competition", LadderConfig.Type.COMPETITION)),
                "selectorMemberships", List.of(),
                "restorableLadders", List.of()));

        assertThat(result).contains("You're not in any groups yet.");
        assertThat(result).doesNotContain("Create a new group, or view your existing groups below.");
        assertThat(result).doesNotContain("Global Competition");
        assertThat(result).doesNotContain("Your Groups");
    }

    @Test
    void sessionShowPageUsesDashboardVoiceRecorderButton() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/auth/show.html"));

        assertThat(template).contains("class=\"card-stack-col card-stack-col-desktop-fluid pb-5 text-start\"");
        assertThat(template).contains("id=\"logMatchVoiceBtn\"");
        assertThat(template).contains("window.matchLogConfig.reviewParams");
        assertThat(template).contains("/js/match-log-voice.js");
        assertThat(template).contains("competition: true");
        assertThat(template).contains("th:href=\"@{/round-robin/list(ladderId=${ladder.id})}\"");
        assertThat(template).contains("Start a Round Robin");
        assertThat(template).contains("id=\"sessionStandingContainer\"");
        assertThat(template).contains("session-standing-momentum");
        assertThat(template).doesNotContain("th:href=\"@{/competition}\"");
        assertThat(template).contains("data-bs-target=\"#sessionReportCardCollapse\"");
        assertThat(template).contains("id=\"sessionReportCardCollapse\" class=\"collapse\"");
        assertThat(template).contains("data-session-standings-pending=${sessionStandingsRecalculationPending}");
        assertThat(template).contains("onclick=\"return refreshSessionReportCard()\"");
        assertThat(template).contains("Show Recent Matches");
        assertThat(template).contains("th:href=\"@{/seasons/{seasonId}/matches/recent(seasonId=${targetSeason.id},backTo=${'/groups/' + ladder.id})}\"");
        assertThat(template).contains("th:href=\"@{/log-match(ladderId=${ladder.id}, seasonId=${targetSeason.id}, returnTo=${'/groups/' + ladder.id})}\"");
        assertThat(template).doesNotContain("th:href=\"@{/voice-match-log");
        assertThat(template).contains("th:href=\"@{/confirm-matches}\"");
        assertThat(template).doesNotContain("Disputed Match Review");
        assertThat(template).doesNotContain("/matches/{matchId}/reopen");
        assertThat(template).doesNotContain("th:href=\"@{/log-match(editMatchId=${match.id}, seasonId=${seasonId}, ladderId=${ladder.id}, returnTo=${returnToPath})}\"");
    }

    @Test
    void userDetailsBadgePickerAutoSavesWithoutSaveButton() throws Exception {
        String pageTemplate = Files.readString(Path.of("src/main/resources/templates/auth/userDetails.html"));
        String statsTemplate = Files.readString(Path.of("src/main/resources/templates/auth/stats.html"));
        String fragmentTemplate = Files.readString(Path.of("src/main/resources/templates/fragments/userDetailsFrag.html"));
        String recentMatchesTemplate = Files.readString(Path.of("src/main/resources/templates/fragments/userRecentMatches.html"));

        assertThat(pageTemplate).contains("<main layout:fragment=\"content\" class=\"app-page\">");
        assertThat(pageTemplate).contains("class=\"card-stack-col card-stack-col-desktop-fluid pb-5 text-start\"");
        assertThat(statsTemplate).contains("class=\"card-stack-col card-stack-col-desktop-fluid pb-5 text-start\"");
        int badgeSectionStart = fragmentTemplate.indexOf("<div class=\"card-header card-header-title text-center\">Name Badges</div>");
        int badgeFormEnd = fragmentTemplate.indexOf("</form>", badgeSectionStart);
        String badgeSection = fragmentTemplate.substring(badgeSectionStart, badgeFormEnd);

        assertThat(pageTemplate).contains("function submitBadgeSelection(value)");
        assertThat(pageTemplate).contains("X-Requested-With': 'XMLHttpRequest'");
        assertThat(pageTemplate).contains("fetch(picker.getAttribute('action')");
        assertThat(StringUtils.countOccurrencesOf(fragmentTemplate, "data-card-collapse-default=\"closed\"")).isEqualTo(4);
        assertThat(recentMatchesTemplate).contains("data-card-collapse-default=\"closed\"");
        assertThat(badgeSection).contains("Use Equip or Hide to save your selection immediately.");
        assertThat(badgeSection).doesNotContain(">Save</button>");
    }

    @Test
    void unlockConditionPanelsRenderAboveBadgeAndTrophyArt() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/site.css"));

        assertThat(css).contains("overflow: visible;");
        assertThat(css).contains(".badge-detail-overlay,");
        assertThat(css).contains(".trophy-room-overlay {");
        assertThat(css).contains("bottom: calc(100% + 0.65rem);");
        assertThat(css).contains("transform: translate(calc(-50% + var(--badge-detail-offset-x)), 0.35rem);");
        assertThat(css).contains(".trophy-room-trigger:hover .trophy-room-overlay,");
    }

    @Test
    void csrfProtectedClientsUseSharedHelperWithoutHardcodedHeaderNames() throws Exception {
        String appJs = Files.readString(Path.of("src/main/resources/static/js/app.js"));
        String componentsJs = Files.readString(Path.of("src/main/resources/static/js/components.js"));
        String checkInJs = Files.readString(Path.of("src/main/resources/static/js/check-in.js"));
        String pwaJs = Files.readString(Path.of("src/main/resources/static/js/pwa.js"));
        String matchLogVoiceJs = Files.readString(Path.of("src/main/resources/static/js/match-log-voice.js"));
        String meetupsCard = Files.readString(Path.of("src/main/resources/templates/components/meetupsCard.html"));
        String showTemplate = Files.readString(Path.of("src/main/resources/templates/auth/show.html"));

        assertThat(appJs).contains("FHPB.Csrf");
        assertThat(componentsJs).contains("FHPB.Csrf.headers");
        assertThat(checkInJs).contains("window.FHPB.Csrf.headers");
        assertThat(pwaJs).contains("window.FHPB.Csrf.headers");
        assertThat(matchLogVoiceJs).contains("window.FHPB.Csrf.headers");
        assertThat(meetupsCard).contains("window.FHPB.Csrf.headers");
        assertThat(showTemplate).contains("window.FHPB.Csrf.headers");

        assertThat(componentsJs).doesNotContain("X-CSRF-TOKEN");
        assertThat(checkInJs).doesNotContain("X-CSRF-TOKEN");
        assertThat(pwaJs).doesNotContain("X-CSRF-TOKEN");
        assertThat(matchLogVoiceJs).doesNotContain("X-CSRF-TOKEN");
        assertThat(meetupsCard).doesNotContain("X-CSRF-TOKEN");
        assertThat(showTemplate).doesNotContain("X-CSRF-TOKEN");
        assertThat(showTemplate).doesNotContain("data-csrf=");
        assertThat(meetupsCard).doesNotContain("meetupsCsrf");
    }

    @Test
    void climbFasterCardStartsCollapsed() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/components/climbFasterCard.html"));

        assertThat(template).contains("data-bs-target=\"#climbFasterCardCollapse\"");
        assertThat(template).contains("id=\"climbFasterCardCollapse\" class=\"collapse\"");
    }

    @Test
    void componentsJsAutoUpgradesTopLevelCardsIntoSharedCollapsePattern() throws Exception {
        String componentsJs = Files.readString(Path.of("src/main/resources/static/js/components.js"));

        assertThat(componentsJs).contains("FHPB.CardCollapse");
        assertThat(componentsJs).contains("data-bs-toggle', 'collapse'");
        assertThat(componentsJs).contains("card-collapse-toggle");
        assertThat(componentsJs).contains("cardCollapseUpgraded");
        assertThat(componentsJs).contains("card.closest('.app-page, .app-home')");
    }

    @Test
    void sessionInsightRefreshUsesOneShotUpdateInsteadOfPollingLoop() throws Exception {
        String componentsJs = Files.readString(Path.of("src/main/resources/static/js/components.js"));

        assertThat(componentsJs).contains("refreshSessionInsightsAfterConfirm: function()");
        assertThat(componentsJs).contains("FHPB.Confirmations.refreshSessionInsightsOnce({ reloadOnFailure: true })");
        assertThat(componentsJs).contains("FHPB.Confirmations.refreshSessionInsightsOnce({ reloadOnFailure: false });");
        assertThat(componentsJs).doesNotContain("refreshSessionInsightsWhilePending");
        assertThat(componentsJs).doesNotContain("primeSessionInsightsRefreshWindow");
        assertThat(componentsJs).doesNotContain("sessionInsightsLocalRefreshUntil");
        assertThat(componentsJs).doesNotContain("sessionInsightsServerPendingSeen");
        assertThat(componentsJs).doesNotContain("refreshSessionInsightsAfterConfirm(tries + 1)");
    }

    @Test
    void confirmJsSupportsDuplicateConfirmedWarningAcknowledgeFlow() throws Exception {
        String componentsJs = Files.readString(Path.of("src/main/resources/static/js/components.js"));

        assertThat(componentsJs).contains("duplicateConfirmedMatch");
        assertThat(componentsJs).contains("duplicateWarningAcceptedMatchId");
        assertThat(componentsJs).contains("showDuplicateConfirmWarning");
        assertThat(componentsJs).contains("Confirm Anyway");
        assertThat(componentsJs).contains("handleConfirmedNullifyButton");
        assertThat(componentsJs).contains("/request-nullify");
    }

    @Test
    void roundRobinStartTemplateIncludesFixedTeamModeControls() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/roundrobin/start.html"));

        assertThat(template).contains("value=\"ROTATING_PARTNERS\"");
        assertThat(template).contains("value=\"FIXED_TEAMS\"");
        assertThat(template).contains("id=\"fixedTeamsBuilder\"");
        assertThat(template).contains("name=\"fixedTeamsJson\"");
        assertThat(template).contains("id=\"fixedTeamsRandomize\"");
        assertThat(template).contains("id=\"fixedTeamsAssignments\"");
        assertThat(template).contains("id=\"participantFilter\"");
        assertThat(template).contains("id=\"participantSort\"");
        assertThat(template).contains("value=\"COURT_NAME\"");
        assertThat(template).contains("value=\"MEMBER_ID\"");
        assertThat(template).contains("value=\"APP_NAME\"");
        assertThat(template).contains("data-public-code=${u.publicCode}");
        assertThat(template).contains("data-court-name=${courtNames != null and courtNames.containsKey(u.id) ? courtNames[u.id] : ''}");
        assertThat(template).contains("data-app-name=${u.nickName != null ? u.nickName : ''}");
        assertThat(template).contains("fragments/memberIdentity :: memberIdentity(${u.publicCode})");
        assertThat(template).doesNotContain("id=\"fixedTeamsReset\"");
        assertThat(template).contains("Randomize teams and replace your current pairings?");
    }

    @Test
    void privateGroupPickerIncludesCreateTournamentShortcut() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/auth/private-group-picker.html"));

        assertThat(template).contains("Create Tournament");
        assertThat(template).contains("th:href=\"@{/groups/new(returnTo='/private-groups',tournamentMode=true)}\"");
    }

    @Test
    void loginTemplateDoesNotRenderInlineTermsAcceptancePrompt() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/public/login.html"));

        assertThat(template).doesNotContain("loginAcceptTerms");
        assertThat(template).doesNotContain("requireTermsAck");
        assertThat(template).contains("Forgot password?");
    }

    @Test
    void homeAndNavigationTemplatesUseStartPlayingUntilSessionExistsThenShowLogMatches() throws Exception {
        String homeTemplate = Files.readString(Path.of("src/main/resources/templates/auth/home.html"));
        String navigationTemplate = Files.readString(Path.of("src/main/resources/templates/components/navigation.html"));
        String sessionPickerTemplate = Files.readString(Path.of("src/main/resources/templates/auth/competition-session-picker.html"));

        String homeChooserAction = extractTemplateSection(
                homeTemplate,
                "th:if=\"${showCompetitionSessionChooser}\"",
                "</a>");
        String homeActiveSessionAction = extractTemplateSection(
                homeTemplate,
                "th:if=\"${!showCompetitionSessionChooser and activeCompetitionSessionId != null}\"",
                "</a>");
        String homeStartAction = extractTemplateSection(
                homeTemplate,
                "th:if=\"${!showCompetitionSessionChooser and activeCompetitionSessionId == null}\"",
                "</a>");
        String navChooserAction = extractTemplateSection(
                navigationTemplate,
                "th:if=\"${showCompetitionSessionChooser}\"",
                "</a>");
        String navActiveSessionAction = extractTemplateSection(
                navigationTemplate,
                "th:if=\"${!showCompetitionSessionChooser and activeCompetitionSessionId != null}\"",
                "</a>");
        String navStartAction = extractTemplateSection(
                navigationTemplate,
                "th:if=\"${!showCompetitionSessionChooser and activeCompetitionSessionId == null}\"",
                "</a>");

        assertThat(homeTemplate).contains("Choose log matches to open a session, then record results in the global competition.");
        assertThat(homeTemplate).contains("Choose start playing to start a session or join one from an invite link.");
        assertThat(homeChooserAction).contains("Log Matches");
        assertThat(homeChooserAction).contains("@{/competition/sessions}");
        assertThat(homeActiveSessionAction).contains("Log Matches");
        assertThat(homeActiveSessionAction).contains("@{/competition/sessions}");
        assertThat(homeStartAction).contains("Start Playing");
        assertThat(homeStartAction).contains("@{/competition/sessions}");
        assertThat(navChooserAction).contains("Log Matches");
        assertThat(navChooserAction).contains("@{/competition/sessions}");
        assertThat(navActiveSessionAction).contains("Log Matches");
        assertThat(navActiveSessionAction).contains("@{/competition/sessions}");
        assertThat(navStartAction).contains("Start Playing");
        assertThat(navStartAction).contains("@{/competition/sessions}");
        assertThat(sessionPickerTemplate).contains("th:text=\"${sessionMemberships != null and !#lists.isEmpty(sessionMemberships)} ? 'Log Matches' : 'Start Playing'\"");
        assertThat(sessionPickerTemplate).contains("class=\"app-action-grid\"");
        assertThat(sessionPickerTemplate).contains("Start a Session");
        assertThat(sessionPickerTemplate).contains("Join a Session");
        assertThat(sessionPickerTemplate).contains("joinSessionHelpCollapse");
        assertThat(sessionPickerTemplate).contains("use your phone camera to scan the session QR code");
        assertThat(sessionPickerTemplate).doesNotContain("No Active Sessions Yet");
    }

    @Test
    void landingTemplateSizesHeroImagesToReduceLayoutShift() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/public/landing.html"));

        assertThat(template).contains("landing-brand-inline");
        assertThat(template).contains("landing-brand-core");
        assertThat(template).contains("landing-brand-text");
        assertThat(template).contains("landing-logo-mark");
        assertThat(template).contains("landing-proof-mark-cta");
        assertThat(template).contains("op_logo.png");
        assertThat(template).contains("width=\"1024\"");
        assertThat(template).contains("height=\"1024\"");
        assertThat(template).contains("notamoneygrab.png");
        assertThat(template).contains("width=\"768\"");
        assertThat(template).contains("height=\"768\"");
        assertThat(template).contains("decoding=\"async\"");
        assertThat(template).contains("fetchpriority=\"low\"");
        assertThat(template).contains("openPickleScreenshot.png");
        assertThat(template).contains("width=\"379\"");
        assertThat(template).contains("height=\"850\"");
        assertThat(template).contains("fetchpriority=\"high\"");
        assertThat(template).doesNotContain("landing-proof-mark-inline");
        assertThat(template).doesNotContain("landing-side-rail");
    }

    @Test
    void publicLayoutPinsSharedPreviewImageToProjectLogo() throws Exception {
        String layoutTemplate = Files.readString(Path.of("src/main/resources/templates/layouts/public.html"));
        String metaTemplate = Files.readString(Path.of("src/main/resources/templates/components/head-meta.html"));

        assertThat(layoutTemplate).contains("components/head-meta :: public-social-image");
        assertThat(metaTemplate).contains("branding.socialImagePath + '?v=' + assetVersion");
        assertThat(metaTemplate).contains("property=\"og:image\"");
        assertThat(metaTemplate).contains("name=\"twitter:image\"");
        assertThat(metaTemplate).contains("rel=\"image_src\"");
        assertThat(metaTemplate).doesNotContain("notamoneygrab.png");
    }

    @Test
    void publicLandingRendersAbsoluteLogoPreviewImageFromModelBaseUrl() {
        BrandingProperties branding = new BrandingProperties();
        OperatorProperties operator = new OperatorProperties();
        String result = renderWebTemplate("public/landing", Map.of(
                "assetVersion", "test-build",
                "publicMetadataBaseUrl", "https://open-pickle.example",
            "branding", branding,
            "operator", operator,
                "_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"),
                "googleAnalyticsEnabled", false,
                "googleAnalyticsMeasurementId", "",
                "googleAdsId", ""));

        assertThat(result).contains("content=\"https://open-pickle.example/images/landing/op_logo.png?v=test-build\"");
        assertThat(result).contains("name=\"twitter:image\"");
        assertThat(result).contains("property=\"og:image\"");
    }

    @Test
    void checkInTemplateShowsOverallAndPrivateGroupCounts() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/auth/check-in.html"));

        assertThat(template).contains("checked in");
        assertThat(template).contains("in your groups");
        assertThat(template).contains("location.privateGroupActiveUserCount");
    }

    @Test
    void tournamentTemplatesHideStoryModeCheckboxes() throws Exception {
        String createTemplate = Files.readString(Path.of("src/main/resources/templates/auth/createLadderConfig.html"));
        String showTemplate = Files.readString(Path.of("src/main/resources/templates/auth/show.html"));

        assertThat(createTemplate)
                .contains("th:if=\"${storyModeFeatureEnabled and !tournamentModePreset and (selectedLadderType == null or selectedLadderType.name() != 'SESSION')}\"");
        assertThat(createTemplate)
                .contains("th:if=\"${!tournamentModePreset}\">Cancel</a>");
        assertThat(showTemplate)
                .contains("th:if=\"${storyModeFeatureEnabled and !ladder.tournamentMode}\"");
    }

    @Test
    void showTemplateLocksTournamentModeSettings() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/auth/show.html"));

        assertThat(template).contains("Tournament mode is enabled.");
        assertThat(template).contains("id=\"modeSelectLocked\"");
        assertThat(template).contains("id=\"securityLevelLocked\"");
    }

    @Test
    void matchRowTemplateIncludesDisputeActionAndDisputedBadge() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/fragments/matchRow.html"));

        assertThat(template).contains("dispute-match-form");
        assertThat(template).contains("handleDisputeButton");
        assertThat(template).contains("Match disputed and awaiting admin review");
        assertThat(template).contains("Disputed by");
        assertThat(template).contains("match-row-status-pill-disputed");
        assertThat(template).contains("disputed=${match.state.name() == 'FLAGGED'}");
        assertThat(template).doesNotContain("<span th:if=\"${match.state.name() == 'FLAGGED'}\"");
        assertThat(template).contains("Reopen");
        assertThat(template).contains("request-match-nullify-form");
        assertThat(template).contains("confirm-match-nullify-form");
        assertThat(template).contains("Approve Removal");
        assertThat(template).contains("Nullify requested");
        assertThat(template).contains("Other team asked to nullify this match.");
        assertThat(template).contains("If you do nothing, the request expires and the match stays.");
        assertThat(template).contains("Match was removed from calculation");
        assertThat(template).contains("Nullified");
        assertThat(template).contains("match-row-status-pill-nullified");
        assertThat(template).contains("nullifyRequestable=${nullifyRequestableByMatchId != null");
        assertThat(template).contains("nullified=${match.state.name() == 'NULLIFIED'}");
        assertThat(template).doesNotContain("<span th:if=\"${match.state.name() == 'NULLIFIED'}\"");
        assertThat(template).doesNotContain("<tr th:with=\"nullifyRequestable=");
    }

    @Test
    void matchRowTemplateOnlyShowsEditLinkWhenRowIsEditable() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/fragments/matchRow.html"));

        assertThat(template).contains("confirmableSafe=${confirmable != null and confirmable}");
        assertThat(template).contains("editableSafe=${editable != null and editable}");
        assertThat(template).contains("th:if=\"${confirmableSafe and editableSafe}\"");
        assertThat(template).contains("th:if=\"${confirmableSafe and !editableSafe}\"");
        assertThat(template).contains("th:if=\"${editableSafe}\" class=\"d-flex align-items-center edit-hint\"");
        assertThat(template).doesNotContain("th:if=\"${confirmableSafe or editableSafe}\" class=\"d-flex align-items-center edit-hint\"");
        assertThat(template).contains("name=\"expectedVersion\"");
    }

    @Test
    void matchRowTemplatePreservesReturnPathOnEditLinks() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/fragments/matchRow.html"));

        assertThat(template).contains("returnTo=${returnToPath}");
        assertThat(template).contains("th:with=\"editHref=@{/log-match(editMatchId=${match.id},seasonId=${seasonId},ladderId=${ladderId},returnTo=${returnToPath})}\"");
    }

    @Test
    void matchRowTemplateUsesSharedAdminControlsSoEditableRowsCanStillNullify() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/fragments/matchRow.html"));
        String readonlyFragment = extractTemplateSection(
                template,
                "th:fragment=\"matchRow_readonly(",
                "th:fragment=\"matchRow_adminControls(");
        String topLevelRowFragment = extractTemplateSection(
                template,
                "th:fragment=\"matchRow(match,",
                null);

        assertThat(template).contains("th:fragment=\"matchRow_adminControls(match, showAdminControls)\"");
        assertThat(readonlyFragment).doesNotContain("btn btn-sm btn-outline-danger\">Nullify</button>");
        assertThat(topLevelRowFragment).contains(
                "fragments/matchRow :: matchRow_adminControls(match=${match}, showAdminControls=${showAdminControls})");
    }

    @Test
    void alertsTemplateRendersCompetitionAutoModerationBanner() {
        String result = renderWebTemplate("components/alerts", Map.of(
                "competitionAutoModStatus", Map.of(
                        "visibleBanner", true,
                        "bannerVariant", "danger",
                        "bannerTitle", "Competition Match Lockout",
                        "bannerMessage", "You cannot be included in competition matches for the rest of this season.")));

        assertThat(result).contains("competitionAutoModBanner");
        assertThat(result).contains("Competition Match Lockout");
        assertThat(result).contains("rest of this season");
    }

    @Test
    void matchLogVoiceFallbackUsesClassicReviewForm() throws Exception {
        String voiceJs = Files.readString(Path.of("src/main/resources/static/js/match-log-voice.js"));
        String logMatchTemplate = Files.readString(Path.of("src/main/resources/templates/auth/logMatch.html"));
        String competitionLogTemplate = Files.readString(Path.of("src/main/resources/templates/auth/competition-log-match.html"));

        assertThat(voiceJs).contains("const VOICE_REVIEW_URL = '/log-match';");
        assertThat(voiceJs).contains("params.set('voiceReview', '1');");
        assertThat(logMatchTemplate).contains("name=\"voiceReview\"");
        assertThat(logMatchTemplate).contains("voiceReviewTranscript");
        assertThat(logMatchTemplate).contains("Review the voice result, fix anything off, then log the match.");
        assertThat(competitionLogTemplate).doesNotContain("/voice-match-log");
        assertThat(competitionLogTemplate).contains("Open Voice Logger");
        assertThat(competitionLogTemplate).contains("th:href=\"@{/log-match(ladderId=${ladderId},seasonId=${seasonId},competition=true,returnTo=${returnToPath})}\"");
    }

    @Test
    void matchLogTemplateIncludesSearchablePlayerPickerHooks() throws Exception {
        String logMatchTemplate = Files.readString(Path.of("src/main/resources/templates/auth/logMatch.html"));

        assertThat(logMatchTemplate).contains("data-player-picker-mode");
        assertThat(logMatchTemplate).contains("playerPickerModal");
        assertThat(logMatchTemplate).contains("data-picker-enabled");
        assertThat(logMatchTemplate).contains("playerOptionLabelByUser");
        assertThat(logMatchTemplate).contains("playerSearchTextByUser");
        assertThat(logMatchTemplate).contains("data-picker-recent");
        assertThat(logMatchTemplate).contains("player-picker-section-label");
        assertThat(logMatchTemplate).contains("name=\"expectedVersion\"");
    }

    @Test
    void matchLogTemplateIncludesRecentDuplicateWarningModalHooks() throws Exception {
        String logMatchTemplate = Files.readString(Path.of("src/main/resources/templates/auth/logMatch.html"));

        assertThat(logMatchTemplate).contains("name=\"duplicateWarningAcceptedMatchId\"");
        assertThat(logMatchTemplate).contains("duplicateMatchWarningModal");
        assertThat(logMatchTemplate).contains("duplicateMatchWarningContinue");
        assertThat(logMatchTemplate).contains("Possible duplicate match");
        assertThat(logMatchTemplate).contains("form.submit()");
    }

    @Test
    void trophiesTemplateUsesCollapsibleSeasonCards() throws Exception {
        String trophiesTemplate = Files.readString(Path.of("src/main/resources/templates/auth/trophies.html"));

        assertThat(trophiesTemplate).contains("card-collapse-toggle");
        assertThat(trophiesTemplate).contains("data-bs-toggle=\"collapse\"");
        assertThat(trophiesTemplate).contains("trophySeasonCollapse");
        assertThat(trophiesTemplate).contains("Season details");
    }

    private StoryModeService.StoryPageModel storyModeModel() throws Exception {
        Constructor<StoryModeService.StoryPageModel> constructor = StoryModeService.StoryPageModel.class.getDeclaredConstructor(
                boolean.class,
                String.class,
                String.class,
                String.class,
                String.class,
                double.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                String.class,
                List.class,
                List.class,
                List.class,
                List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                true,
                "Story Mode is a playful overlay.",
                "Pat is on the move",
                "The group is helping Pat get to the courts.",
                "33% to the next stop",
                33.3d,
                false,
                false,
                "Bike",
                "organized",
                "4 counted matches, 3 contributors, 88 total points.",
                List.of("Pat found the spare paddle."),
                List.of(new StoryModeService.StageNode(1, "Couch", "Pat is on the couch", "bi bi-cup-hot", true, false, false)),
                List.of(new StoryModeService.GoalView("Counted matches", 2, 4, false, 50d, "matches", "Added so far: 2 matches.")),
                List.of(new StoryModeService.SideTaskView(
                        "keys",
                        "Pat's Keys",
                        "Spare keys are still out there.",
                        "Each unique contributor finds one key.",
                        1,
                        1,
                        1,
                        1,
                        33.3d,
                        true,
                        "Earned 1 time by the group.",
                        "You have added 1 so far.")));
    }

    private LadderMembership membership(Long ladderId, String title, LadderConfig.Type type) {
        LadderConfig config = new LadderConfig();
        config.setId(ladderId);
        config.setTitle(title);
        config.setType(type);

        LadderMembership membership = new LadderMembership();
        membership.setLadderConfig(config);
        return membership;
    }

    private String renderWebTemplate(String templateName, Map<String, Object> variables) {
        return renderWebTemplate(templateName, variables, request -> { });
    }

    private String renderWebTemplate(String templateName,
                                     Map<String, Object> variables,
                                     Consumer<MockHttpServletRequest> requestCustomizer) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        requestCustomizer.accept(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, response), Locale.getDefault(), variables);
        return templateEngine.process(templateName, context);
    }

    private String extractTemplateSection(String template, String startMarker, String endMarker) {
        int start = template.indexOf(startMarker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = endMarker == null ? template.length() : template.indexOf(endMarker, start);
        assertThat(end).isGreaterThanOrEqualTo(0);
        return template.substring(start, end);
    }
}
