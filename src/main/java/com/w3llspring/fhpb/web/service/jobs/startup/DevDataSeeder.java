package com.w3llspring.fhpb.web.service.jobs.startup;

import java.text.Normalizer;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import com.w3llspring.fhpb.web.db.BandPositionRepository;
import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMatchLinkRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.TrophyCatalogEntryRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.PlayLocationAliasRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.PlayLocationRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderConfig.CadenceUnit;
import com.w3llspring.fhpb.web.model.LadderConfig.Mode;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderMembership.Role;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.LadderMembership.State;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchConfirmation;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.PlayLocation;
import com.w3llspring.fhpb.web.model.PlayLocationAlias;
import com.w3llspring.fhpb.web.model.PlayLocationCheckIn;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyRarity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.user.DisplayNameNormalization;
import com.w3llspring.fhpb.web.service.ConfirmedMatchNullificationService;
import com.w3llspring.fhpb.web.service.LadderConfigService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchConfirmationService;
import com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone;
import com.w3llspring.fhpb.web.service.trophy.AutoTrophyService;
import com.w3llspring.fhpb.web.service.trophy.FallbackTrophyTemplates;
import com.w3llspring.fhpb.web.service.trophy.GeneratedTrophy;
import com.w3llspring.fhpb.web.service.trophy.TrophyArtService;
import com.w3llspring.fhpb.web.service.user.UserPublicCodeGenerator;

/**
 * Rebuilds a representative dataset every time the dev/docker profiles boot.
 * The goal is to keep manual QA fast, so everything starts from a blank DB,
 * then repopulates using the same services/controllers the UI exercises.
 */
@Component
@Profile({"dev", "docker"})
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");
    private static final List<String> ALWAYS_AVAILABLE_PROFILE_BADGE_SEED_SCRIPTS = List.of(
            "db/migration/V45__add_country_flag_profile_badges.sql");
    private static final int ROLLING_SEED_DEFAULT_START_DAYS_AGO = 3;
    private static final int ROLLING_SEED_MAX_MATCH_OFFSET_DAYS = 20;
    private static final int DEV_DISPUTE_SPAM_MATCH_COUNT = 5;
    private static final int DEV_LARGE_SESSION_EXTRA_PLAYERS = 8;
    private static final String[] COMPETITION_FIRST_NAMES = {
            "Avery", "Parker", "Reese", "Morgan", "Taylor", "Rowan", "Sage", "Drew", "Quinn", "Blake",
            "Jordan", "Skyler", "Cameron", "Hayden", "Emerson", "Finley", "Logan", "Micah", "Casey", "Riley",
            "Kai", "Tatum", "Noel", "Sydney", "Peyton"
    };
    private static final String[] COMPETITION_LAST_NAMES = {
            "Harper", "Monroe", "Bennett", "Sullivan", "Wilder", "Mercer", "Hayes", "Palmer", "Holland", "Parker",
            "Quincy", "Brooks", "Sawyer", "Ellis", "Sutton", "Walker", "Foster", "Bishop", "Collins", "Nolan"
    };

    private record CompetitionSeedParticipant(User user, int skill, boolean prefersDoubles) {
    }

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;
    private final LadderConfigService configService;
    private final LadderConfigRepository configRepo;
    private final LadderSeasonRepository seasonRepo;
    private final LadderMembershipRepository membershipRepo;
    private final LadderMatchLinkRepository linkRepo;
    private final LadderStandingRepository standingRepo;
    private final BandPositionRepository bandRepo;
    private final MatchRepository matchRepo;
    private final MatchConfirmationRepository matchConfirmationRepo;
    private final MatchConfirmationService matchConfirmationService;
    private final TrophyCatalogEntryRepository trophyCatalogEntryRepo;
    private final TrophyRepository trophyRepo;
    private final UserTrophyRepository userTrophyRepo;
    private final PlayLocationRepository playLocationRepo;
    private final PlayLocationAliasRepository playLocationAliasRepo;
    private final PlayLocationCheckInRepository playLocationCheckInRepo;
    private final UserCourtNameRepository courtNameRepo;
    private final LadderV2Service ladderService;
    private final ConfirmedMatchNullificationService confirmedMatchNullificationService;
    private final AutoTrophyService autoTrophyService;
    private final TrophyArtService trophyArtService;
    private final com.w3llspring.fhpb.web.service.MatchFactory matchFactory;
    private final JdbcTemplate jdbc;
    private final DoubleMetaphone playLocationDoubleMetaphone = new DoubleMetaphone();
    private final boolean seedConfirmations;
    private final int defaultMaxOwnedLadders;
    private final boolean alwaysResetData;
    private final boolean resetSchemaOnStartup;
    private final boolean seedFutureMatches;
    @Value("${fhpb.features.story-mode.enabled:true}")
    private boolean storyModeFeatureEnabled = true;
    @Value("${fhpb.bootstrap.global-ladder.title:Community Ladder}")
    private String competitionLadderTitle = "Community Ladder";
    @Value("${fhpb.bootstrap.global-ladder.season-name:Opening Season}")
    private String competitionSeasonName = "Opening Season";
    @Value("${fhpb.dev.seed-large-competition.enabled:true}")
    private boolean seedLargeCompetitionEnabled = true;
    @Value("${fhpb.dev.seed-large-competition.participants:100}")
    private int seedLargeCompetitionParticipants = 100;
    @Value("${fhpb.dev.seed-large-competition.matches:280}")
    private int seedLargeCompetitionMatches = 280;
    @Value("${fhpb.automod.competition.warning1-expired-confirmations:3}")
    private int competitionWarningOneThreshold = 3;
    @Value("${fhpb.automod.competition.warning2-expired-confirmations:5}")
    private int competitionWarningTwoThreshold = 5;
    @Value("${fhpb.automod.competition.block-expired-confirmations:7}")
    private int competitionBlockThreshold = 7;

    public DevDataSeeder(UserRepository userRepo,
            BCryptPasswordEncoder encoder,
            LadderConfigService configService,
            LadderConfigRepository configRepo,
            LadderSeasonRepository seasonRepo,
            LadderMembershipRepository membershipRepo,
            LadderMatchLinkRepository linkRepo,
            LadderStandingRepository standingRepo,
            BandPositionRepository bandRepo,
            MatchRepository matchRepo,
            MatchConfirmationRepository matchConfirmationRepo,
            MatchConfirmationService matchConfirmationService,
            TrophyCatalogEntryRepository trophyCatalogEntryRepo,
            TrophyRepository trophyRepo,
            UserTrophyRepository userTrophyRepo,
            PlayLocationRepository playLocationRepo,
            PlayLocationAliasRepository playLocationAliasRepo,
            PlayLocationCheckInRepository playLocationCheckInRepo,
            UserCourtNameRepository courtNameRepo,
            LadderV2Service ladderService,
            ConfirmedMatchNullificationService confirmedMatchNullificationService,
            AutoTrophyService autoTrophyService,
            TrophyArtService trophyArtService,
            com.w3llspring.fhpb.web.service.MatchFactory matchFactory,
            JdbcTemplate jdbc,
            @Value("${fhpb.dev.seed-confirmations:false}") boolean seedConfirmations,
            @Value("${fhpb.ladder.max-per-user:10}") int defaultMaxOwnedLadders,
            @Value("${fhpb.dev.reset-data-on-startup:true}") boolean alwaysResetData,
            @Value("${fhpb.dev.reset-schema-on-startup:false}") boolean resetSchemaOnStartup,
            @Value("${fhpb.dev.seed-future-matches:false}") boolean seedFutureMatches) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.configService = configService;
        this.configRepo = configRepo;
        this.seasonRepo = seasonRepo;
        this.membershipRepo = membershipRepo;
        this.linkRepo = linkRepo;
        this.standingRepo = standingRepo;
        this.bandRepo = bandRepo;
        this.matchRepo = matchRepo;
        this.matchConfirmationRepo = matchConfirmationRepo;
        this.matchConfirmationService = matchConfirmationService;
        this.matchFactory = matchFactory;
        this.trophyCatalogEntryRepo = trophyCatalogEntryRepo;
        this.trophyRepo = trophyRepo;
    this.userTrophyRepo = userTrophyRepo;
    this.playLocationRepo = playLocationRepo;
    this.playLocationAliasRepo = playLocationAliasRepo;
    this.playLocationCheckInRepo = playLocationCheckInRepo;
    this.courtNameRepo = courtNameRepo;
    this.ladderService = ladderService;
    this.confirmedMatchNullificationService = confirmedMatchNullificationService;
    this.autoTrophyService = autoTrophyService;
        this.trophyArtService = trophyArtService;
        this.jdbc = jdbc;
        this.seedConfirmations = seedConfirmations;
        this.defaultMaxOwnedLadders = defaultMaxOwnedLadders;
        this.alwaysResetData = alwaysResetData;
        this.resetSchemaOnStartup = resetSchemaOnStartup;
        this.seedFutureMatches = seedFutureMatches;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    @Transactional
    public void seed() {
        try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("dev-data-seed")) {
            if (!resetSchemaOnStartup) {
                normalizeLadderConfigSchema();
                normalizeMatchHistoryLookupIndexes();
                if (alwaysResetData) {
                    resetDatabaseTables();
                }
            }

            // Check if we need to seed users (only if no users exist or always resetting)
            if (userRepo.count() == 0 || alwaysResetData) {
                normalizeBandPositionSchemaForSeasonScope();

                // Users
                User admin = createUser("admin@demo.local", "playhard", true, "AdminCharlie", true);
                User member = createUser("member@demo.local", "playhard", false, "DaveM", false);
            User coach = createUser("coach@demo.local", "playhard", false, "CoachYoung", false);
            User ruleBreaker = createUser("rulebreaker@demo.local", "playhard", false, "RuleBreak", false);
            User ruleBreaker2 = createUser("rulebreaker2@demo.local", "playhard", false, "RuleBreak2", false);
            User ruleBreaker3 = createUser("rulebreaker3@demo.local", "playhard", false, "RuleBreak3", false);
            User spamLogger = createUser("spamlogger@demo.local", "playhard", false, "SpamLogger", false);
            User spectator = createUser("spectator@demo.local", "playhard", false, "EddieA", false);

            User other1 = createUser("other1@demo.local", "playhard", false, "Other1", false);
            User other2 = createUser("other2@demo.local", "playhard", false, "Other2", false);
            User sunriseRiley = createUser("riley@demo.local", "playhard", false, "RileySun", false);
            User sunriseJordan = createUser("jordan@demo.local", "playhard", false, "JordanRay", false);
            User sunriseSky = createUser("sky@demo.local", "playhard", false, "SkyServe", false);
            User sunriseDawn = createUser("dawn@demo.local", "playhard", false, "DawnDash", false);
            User pickerCaptain = createUser("pickerhost@demo.local", "playhard", false, "PickerHost", true);
            User other1Clone = createUser("other1clone@demo.local", "playhard", false, "Other1Clone", true);
            User other2Clone = createUser("other2clone@demo.local", "playhard", false, "Other2Clone", true);
            List<User> largeSessionExtras = createLargeSessionQaUsers(DEV_LARGE_SESSION_EXTRA_PLAYERS);

        // Keep one extra owner slot on the seeded admin so the dev competition ladder can coexist
        // with the three standard demo ladders even when the global per-user limit is set to 3.
        admin.setMaxOwnedLadders(Math.max(defaultMaxOwnedLadders, 4));
        admin = saveUserWithUniquePublicCode(admin);

        addCourtName(admin, "Charlie", null);
        addCourtName(member, "Dave", null);
        addCourtName(coach, "Young", null);
        addCourtName(ruleBreaker, "RuleBreak", null);
        addCourtName(ruleBreaker2, "RuleBreak2", null);
        addCourtName(ruleBreaker3, "RuleBreak3", null);
        addCourtName(spamLogger, "SpamLogger", null);
        addCourtName(spectator, "Eddie", null);
        
        addCourtName(other1, "Pam", null);
        addCourtName(other2, "Vince", null);
        addCourtName(other2, "Vinny", null);
        addCourtName(sunriseRiley, "Riley", null);
        addCourtName(sunriseJordan, "Jordan", null);
        addCourtName(sunriseSky, "Sky", null);
        addCourtName(sunriseDawn, "Dawn", null);
        addCourtName(pickerCaptain, "Host", null);
        addCourtName(other1Clone, "Pam", null);
        addCourtName(other2Clone, "Vince", null);
        addCourtName(other2Clone, "Vinny", null);

        // Ladder A: rolling, active season plus historic season.
        // Optionally keep all seeded matches in the past so a manual log becomes the latest match.
        LocalDate rollingStart = resolveRollingSeedStart();
        LadderConfig rolling = configService.createConfigAndSeason(
                admin.getId(),
                "Sunrise Smashers",
                rollingStart,
                null,
                "Summer Ladder",
                Mode.ROLLING,
                6,
                CadenceUnit.WEEKS,
                LadderSecurity.SELF_CONFIRM,
                false,
                true);
        rolling.setLastSeasonCreatedAt(rollingStart.atStartOfDay(LADDER_ZONE).toInstant());
        configRepo.saveAndFlush(rolling);
        LadderSeason rollingSeason = seasonRepo.findActive(rolling.getId()).orElseThrow();
        rollingSeason.setStartDate(rollingStart);
        rollingSeason.setEndDate(rollingStart.plusWeeks(6));
        rollingSeason.setStartedAt(rollingStart.atStartOfDay(LADDER_ZONE).toInstant());
        seasonRepo.saveAndFlush(rollingSeason);

        upsertMembership(rolling, member, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, coach, Role.ADMIN, State.ACTIVE);
        upsertMembership(rolling, ruleBreaker, Role.MEMBER, State.BANNED);
        upsertMembership(rolling, ruleBreaker2, Role.MEMBER, State.BANNED);
        upsertMembership(rolling, ruleBreaker3, Role.MEMBER, State.BANNED);
        upsertMembership(rolling, spamLogger, Role.MEMBER, State.BANNED);
        upsertMembership(rolling, spectator, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, other1, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, other2, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, other1Clone, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, other2Clone, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, sunriseRiley, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, sunriseJordan, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, sunriseSky, Role.MEMBER, State.ACTIVE);
        upsertMembership(rolling, sunriseDawn, Role.MEMBER, State.ACTIVE);

        addCourtName(member, "Marshall", rolling);
        addCourtName(coach, "Coach Young", rolling);
        addCourtName(admin, "Chas", rolling);
        addCourtName(spectator, "Eddie A", rolling);
        addCourtName(sunriseRiley, "Riley S", rolling);
        addCourtName(sunriseJordan, "Jordan R", rolling);
        addCourtName(sunriseSky, "SkyServe", rolling);
        addCourtName(sunriseDawn, "DawnDash", rolling);

    logMatch(rollingSeason, rollingStart.plusDays(1), admin, coach, member, null, 11, 7);
    logMatch(rollingSeason, rollingStart.plusDays(3), coach, null, member, null, 15, 13);
    logMatch(rollingSeason, rollingStart.plusDays(5), admin, member, coach, null, 11, 9);
        logMatch(rollingSeason, rollingStart.plusDays(6), sunriseRiley, null, sunriseJordan, null, 11, 8);
        logMatch(rollingSeason, rollingStart.plusDays(7), member, null, coach, null, 12, 10);
        logMatch(rollingSeason, rollingStart.plusDays(8), sunriseSky, sunriseDawn, spectator, other1, 15, 13);
        logMatch(rollingSeason, rollingStart.plusDays(10), coach, admin, member, null, 11, 6);
        logMatch(rollingSeason, rollingStart.plusDays(11), sunriseJordan, null, sunriseSky, null, 12, 10);
        logMatch(rollingSeason, rollingStart.plusDays(14), admin, null, coach, null, 13, 11);
        logMatch(rollingSeason, rollingStart.plusDays(16), sunriseDawn, spectator, sunriseRiley, member, 11, 7);

        // Add test matches for admin user to display all badge types
        // 1. Provisional match (needs confirmation)
        logMatch(rollingSeason, rollingStart.plusDays(17), admin, null, member, null, 11, 9, MatchState.PROVISIONAL, false, 85);
        // 2. Estimated score match (confirmed, full teams so standings deltas are meaningful)
        logMatch(rollingSeason, rollingStart.plusDays(18), admin, member, coach, spectator, 12, 10, MatchState.CONFIRMED, true, 90);
        // 3. Low confidence match (< 50, also confirmed)
        logMatch(rollingSeason, rollingStart.plusDays(19), admin, coach, spectator, other1, 11, 8, MatchState.CONFIRMED, false, 35);
        // 4. Personal record match (all guests)
        logMatch(rollingSeason, rollingStart.plusDays(20), null, null, null, null, 15, 13, MatchState.CONFIRMED, false, 95);

        LadderSeason historic = new LadderSeason();
        LocalDate historicStart = rollingStart.minusWeeks(8);
        LocalDate historicEnd = historicStart.plusWeeks(4);
        historic.setLadderConfig(rolling);
        historic.setName("Spring Sprint");
        historic.setStartDate(historicStart);
        historic.setEndDate(historicEnd);
        historic.setState(LadderSeason.State.ENDED);
        historic.setStartedAt(historicStart.atStartOfDay(LADDER_ZONE).toInstant());
        historic.setEndedAt(historicEnd.plusDays(1).atStartOfDay(LADDER_ZONE).minusHours(2).toInstant());
        historic.setEndedByUserId(admin.getId());
        seasonRepo.save(historic);

        logMatch(historic, historicStart.plusDays(1), admin, member, coach, null, 11, 5);
        logMatch(historic, historicStart.plusDays(4), member, null, admin, null, 12, 10);
        logMatch(historic, historicStart.plusDays(7), coach, admin, member, null, 9, 11);
        logMatch(historic, historicStart.plusDays(9), sunriseRiley, null, sunriseJordan, null, 11, 9);
        logMatch(historic, historicStart.plusDays(11), admin, null, coach, null, 11, 8);
        logMatch(historic, historicStart.plusDays(13), sunriseSky, sunriseDawn, member, spectator, 15, 12);
        logMatch(historic, historicStart.plusDays(15), member, coach, admin, null, 11, 7);
        logMatch(historic, historicStart.plusDays(20), coach, null, member, null, 15, 13);
        logMatch(historic, historicStart.plusDays(22), sunriseDawn, null, sunriseSky, null, 11, 6);

        // Ladder B: manual ladder with ended season containing more matches
        LadderConfig legacy = configService.createConfigAndSeason(
                admin.getId(),
                "Historic Heroes",
                LocalDate.now(LADDER_ZONE).minusWeeks(10),
                LocalDate.now(LADDER_ZONE).minusWeeks(4),
                "Spring Throwdown",
                Mode.MANUAL,
                null,
                null,
                LadderSecurity.STANDARD,
                false,
                false);
        LadderSeason legacySeason = seasonRepo.findActive(legacy.getId()).orElseThrow();
        LocalDate legacyStart = LocalDate.now(LADDER_ZONE).minusWeeks(10);
        LocalDate legacyEnd = LocalDate.now(LADDER_ZONE).minusWeeks(4);
        legacySeason.setStartDate(legacyStart);
        legacySeason.setEndDate(legacyEnd);
        seasonRepo.saveAndFlush(legacySeason);
        upsertMembership(legacy, member, Role.MEMBER, State.ACTIVE);
        upsertMembership(legacy, coach, Role.MEMBER, State.ACTIVE);
        logMatch(legacySeason, legacyStart.plusDays(2), admin, coach, member, null, 11, 3);
        logMatch(legacySeason, legacyStart.plusDays(6), member, null, admin, null, 11, 9);
        logMatch(legacySeason, legacyStart.plusDays(11), coach, member, admin, null, 12, 10);
        logMatch(legacySeason, legacyStart.plusDays(17), admin, null, coach, null, 11, 8);
        logMatch(legacySeason, legacyStart.plusDays(23), member, coach, admin, null, 15, 13);
        logMatch(legacySeason, legacyStart.plusDays(27), coach, null, member, null, 11, 7);
        closeSeason(legacySeason, admin);

        // Ladder C: future season scheduled
        LadderConfig scheduled = configService.createConfigAndSeason(
                admin.getId(),
                "Future Flyers",
                LocalDate.now(LADDER_ZONE).plusWeeks(2),
                null,
                "Fall Kickoff",
                Mode.ROLLING,
                4,
                CadenceUnit.WEEKS,
                LadderSecurity.SELF_CONFIRM,
                false,
                false);
        LadderSeason scheduledSeason = seasonRepo.findActive(scheduled.getId()).orElseThrow();
        LocalDate scheduledStart = LocalDate.now(ZoneOffset.UTC).plusWeeks(2);
        scheduledSeason.setState(LadderSeason.State.SCHEDULED);
        scheduledSeason.setStartDate(scheduledStart);
        scheduledSeason.setEndDate(scheduledStart.plusWeeks(4));
        scheduledSeason.setStartedAt(scheduledStart.atStartOfDay(ZoneOffset.UTC).toInstant());
        scheduledSeason.setStartedByUserId(null);
        seasonRepo.saveAndFlush(scheduledSeason);
        upsertMembership(scheduled, member, Role.MEMBER, State.ACTIVE);
        upsertMembership(scheduled, coach, Role.MEMBER, State.ACTIVE);

        LadderSeason storySeason = null;
        if (storyModeFeatureEnabled) {
            // Ladder D: member-owned story mode group with no matches yet.
            LocalDate storyStart = LocalDate.now(LADDER_ZONE).minusDays(2);
            LadderConfig storyMode = configService.createConfigAndSeason(
                    member.getId(),
                    "Pat's Practice Crew",
                    storyStart,
                    null,
                    "Pat's First Trip",
                    Mode.ROLLING,
                    4,
                    CadenceUnit.WEEKS,
                    LadderSecurity.SELF_CONFIRM,
                    false,
                    true);
            storySeason = seasonRepo.findActive(storyMode.getId()).orElseThrow();
            storySeason.setStartDate(storyStart);
            storySeason.setEndDate(storyStart.plusWeeks(4));
            storySeason.setStartedAt(storyStart.atStartOfDay(LADDER_ZONE).toInstant());
            seasonRepo.saveAndFlush(storySeason);
            upsertMembership(storyMode, coach, Role.MEMBER, State.ACTIVE);
            upsertMembership(storyMode, spectator, Role.MEMBER, State.ACTIVE);
            upsertMembership(storyMode, sunriseRiley, Role.MEMBER, State.ACTIVE);
            upsertMembership(storyMode, sunriseJordan, Role.MEMBER, State.ACTIVE);
        }

        // Ensure standings exist immediately after boot; do not rely only on async recalc events.
        recalcSeasonStandingsSync(rollingSeason);
        recalcSeasonStandingsSync(historic);
        recalcSeasonStandingsSync(legacySeason);

        seedTrophies(rollingSeason, historic, member, admin);
        seedAlwaysAvailableProfileBadges();
        if (storySeason != null) {
            autoTrophyService.generateSeasonTrophies(storySeason);
        }

        LadderSeason competitionSeason = ensureDevCompetitionSeason(admin);
        LadderConfig memberSession = seedMatchSession(
                member,
                "Saturday Open Session",
                competitionSeason,
                ruleBreaker,
                ruleBreaker2,
                ruleBreaker3,
                spamLogger,
                coach,
                spectator,
                other1,
                other1Clone,
                other2,
                other2Clone,
                sunriseRiley,
                sunriseJordan);
        seedSessionTickerMatches(
                memberSession,
                competitionSeason,
                member,
                coach,
                spectator,
                other1,
                sunriseRiley,
                sunriseJordan);
        List<User> largePickerSessionRoster = Stream.concat(
                        Stream.of(
                                member,
                                coach,
                                spectator,
                                other1,
                                other2,
                                other1Clone,
                                other2Clone,
                                sunriseRiley,
                                sunriseJordan,
                                sunriseSky,
                                sunriseDawn),
                        largeSessionExtras.stream())
                .collect(Collectors.toCollection(ArrayList::new));
        LadderConfig largePickerSession = seedMatchSession(
                pickerCaptain,
                "Mega Picker Session",
                competitionSeason,
                largePickerSessionRoster.toArray(User[]::new));
        upsertMembership(competitionSeason.getLadderConfig(), pickerCaptain, Role.MEMBER, State.ACTIVE);
        largePickerSessionRoster.forEach(user -> upsertMembership(competitionSeason.getLadderConfig(), user, Role.MEMBER, State.ACTIVE));
        seedSessionTickerMatches(
                largePickerSession,
                competitionSeason,
                pickerCaptain,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);
        seedCompetitionMatches(
                competitionSeason,
                member,
                coach,
                spectator,
                other1,
                sunriseRiley,
                sunriseJordan);
        seedLargeCompetitionPopulation(
                competitionSeason,
                admin,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);
        seedCompetitionAutoModerationScenario(
                competitionSeason,
                ruleBreaker,
                competitionBlockThreshold,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);
        seedCompetitionAutoModerationScenario(
                competitionSeason,
                ruleBreaker2,
                competitionWarningOneThreshold,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);
        seedCompetitionAutoModerationScenario(
                competitionSeason,
                ruleBreaker3,
                competitionWarningTwoThreshold,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);
        seedCompetitionDisputeSpamScenario(
                competitionSeason,
                spamLogger,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);
        seedConfirmedMatchRemovalScenarios(
                rollingSeason,
                admin,
                member,
                coach,
                spectator,
                sunriseRiley,
                sunriseJordan);
        recalcSeasonStandingsSync(competitionSeason);
        seedPlayLocations(admin, member, coach, spectator, other1, sunriseRiley, sunriseJordan);

        log.info("Dev database seeded: ladders={}, users={}, matches={}, scheduledSeasons={}, endedSeasons={}.",
                configRepo.count(),
                userRepo.count(),
                matchRepo.count(),
                seasonRepo.findAll().stream().filter(s -> s.getState() == LadderSeason.State.SCHEDULED).count(),
                seasonRepo.findAll().stream().filter(s -> s.getState() == LadderSeason.State.ENDED).count());
        log.info("Seeded active match session '{}' for {} with invite code {}.",
                memberSession.getTitle(),
                member.getEmail(),
                memberSession.getInviteCode());
        log.info("Seeded large picker session '{}' for {} with invite code {} (rosterSize={}).",
                largePickerSession.getTitle(),
                pickerCaptain.getEmail(),
                largePickerSession.getInviteCode(),
                membershipRepo.findByLadderConfigIdAndStateOrderByJoinedAtAsc(largePickerSession.getId(), State.ACTIVE).size());
        log.info("Seeded duplicate-name QA accounts: {} mirrors {} and {} mirrors {}.",
                other1Clone.getEmail(),
                other1.getEmail(),
                other2Clone.getEmail(),
                other2.getEmail());
        }
    }
    }

    private LocalDate resolveRollingSeedStart() {
        LocalDate today = LocalDate.now(LADDER_ZONE);
        if (seedFutureMatches) {
            return today.minusDays(ROLLING_SEED_DEFAULT_START_DAYS_AGO);
        }
        return today.minusDays(ROLLING_SEED_MAX_MATCH_OFFSET_DAYS + 1L);
    }

    private void resetDatabaseTables() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        normalizeLadderConfigSchema();
        List<String> tables = List.of(
                "group_trophy",
                "user_trophy",
                "trophy",
                "ladder_match_link",
                "ladder_rating_change",
                "ladder_standing",
                "band_positions",
        "matches",
                "ladder_season",
                "ladder_membership",
                "ladder_config",
                "play_location_check_in",
                "play_location_alias",
                "play_location",
                "user_court_name",
                "user_credit",
                "match_confirmation",
        // round-robin support tables
        "round_robin",
        "round_robin_entry",
                "userStyle",
                "users");
        try {
            tables.forEach(t -> truncateTable(jdbc, t));
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private void normalizeLadderConfigSchema() {
        try {
            Integer hasColumn = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'recurrence_type'",
                    Integer.class);
            if (hasColumn != null && hasColumn > 0) {
                jdbc.execute("ALTER TABLE ladder_config DROP COLUMN recurrence_type");
                log.info("Removed legacy column ladder_config.recurrence_type");
            }
        } catch (Exception ex) {
            log.warn("Unable to drop legacy recurrence_type column: {}", ex.getMessage());
        }
        try {
            Integer inviteNullable = jdbc.queryForObject(
                    "SELECT CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'invite_code'",
                    Integer.class);
            if (inviteNullable != null && inviteNullable == 0) {
                jdbc.execute("ALTER TABLE ladder_config MODIFY COLUMN invite_code VARCHAR(64) NULL");
                log.info("Updated ladder_config.invite_code to allow NULL values");
            }
        } catch (Exception ex) {
            log.warn("Unable to update ladder_config.invite_code nullability: {}", ex.getMessage());
        }
        try {
            Integer securityLen = jdbc.queryForObject(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'security_level'",
                    Integer.class);
            if (securityLen != null && securityLen < 16) {
                jdbc.execute("ALTER TABLE ladder_config MODIFY COLUMN security_level VARCHAR(16) NOT NULL");
                log.info("Expanded ladder_config.security_level to VARCHAR(16)");
            }
        } catch (Exception ex) {
            log.warn("Unable to update ladder_config.security_level length: {}", ex.getMessage());
        }
        try {
            Integer ladderConfigExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config'",
                    Integer.class);
            if (ladderConfigExists == null || ladderConfigExists == 0) {
                return;
            }

            Integer scoringColumnCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'scoring_algorithm'",
                    Integer.class);
            if (scoringColumnCount == null || scoringColumnCount == 0) {
                jdbc.execute(
                        "ALTER TABLE ladder_config ADD COLUMN scoring_algorithm VARCHAR(32) NOT NULL DEFAULT 'MARGIN_CURVE_V1'");
                log.info("Added ladder_config.scoring_algorithm as VARCHAR(32) defaulting to MARGIN_CURVE_V1");
                return;
            }

            String scoringType = jdbc.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'scoring_algorithm'",
                    String.class);
            Integer scoringLength = jdbc.queryForObject(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'scoring_algorithm'",
                    Integer.class);
            Integer scoringNullable = jdbc.queryForObject(
                    "SELECT CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'scoring_algorithm'",
                    Integer.class);
            String scoringDefault = jdbc.queryForObject(
                    "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ladder_config' AND COLUMN_NAME = 'scoring_algorithm'",
                    String.class);

            boolean needsNormalize = !"varchar".equalsIgnoreCase(scoringType)
                    || scoringLength == null
                    || scoringLength < 32
                    || scoringNullable == null
                    || scoringNullable != 0
                    || !"MARGIN_CURVE_V1".equals(scoringDefault);
            if (needsNormalize) {
                jdbc.execute(
                        "ALTER TABLE ladder_config MODIFY COLUMN scoring_algorithm VARCHAR(32) NOT NULL DEFAULT 'MARGIN_CURVE_V1'");
                log.info("Normalized ladder_config.scoring_algorithm to VARCHAR(32) with default MARGIN_CURVE_V1");
            }
        } catch (Exception ex) {
            log.warn("Unable to normalize ladder_config.scoring_algorithm: {}", ex.getMessage());
        }
    }

    private void normalizeMatchHistoryLookupIndexes() {
        ensureMatchHistoryIndex("idx_matches_season_played", "CREATE INDEX idx_matches_season_played ON matches (season_id, played_at)");
        ensureMatchHistoryIndex("idx_matches_a1_created_at", "CREATE INDEX idx_matches_a1_created_at ON matches (a1_id, created_at)");
        ensureMatchHistoryIndex("idx_matches_a2_created_at", "CREATE INDEX idx_matches_a2_created_at ON matches (a2_id, created_at)");
        ensureMatchHistoryIndex("idx_matches_b1_created_at", "CREATE INDEX idx_matches_b1_created_at ON matches (b1_id, created_at)");
        ensureMatchHistoryIndex("idx_matches_b2_created_at", "CREATE INDEX idx_matches_b2_created_at ON matches (b2_id, created_at)");
        ensureMatchHistoryIndex("idx_matches_a1_played_at", "CREATE INDEX idx_matches_a1_played_at ON matches (a1_id, played_at)");
        ensureMatchHistoryIndex("idx_matches_a2_played_at", "CREATE INDEX idx_matches_a2_played_at ON matches (a2_id, played_at)");
        ensureMatchHistoryIndex("idx_matches_b1_played_at", "CREATE INDEX idx_matches_b1_played_at ON matches (b1_id, played_at)");
        ensureMatchHistoryIndex("idx_matches_b2_played_at", "CREATE INDEX idx_matches_b2_played_at ON matches (b2_id, played_at)");
    }

    private void ensureMatchHistoryIndex(String indexName, String createSql) {
        try {
            Integer matchesTableExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'matches'",
                    Integer.class);
            if (matchesTableExists == null || matchesTableExists == 0) {
                return;
            }

            Integer indexExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() " +
                            "AND table_name = 'matches' " +
                            "AND index_name = ?",
                    Integer.class,
                    indexName);
            if (indexExists == null || indexExists == 0) {
                jdbc.execute(createSql);
                log.info("Added match history lookup index {}", indexName);
            }
        } catch (Exception ex) {
            log.warn("Unable to ensure match history lookup index {}: {}", indexName, ex.getMessage());
        }
    }

    private void truncateTable(org.springframework.jdbc.core.JdbcTemplate jdbc, String table) {
        try {
            jdbc.execute("TRUNCATE TABLE " + table);
            log.info("Truncated table: {}", table);
            return;
        } catch (Exception e) {
            String fallback = toSnakeCase(table);
            if (!fallback.equals(table)) {
                try {
                    jdbc.execute("TRUNCATE TABLE " + fallback);
                    log.info("Truncated table: {}", fallback);
                    return;
                } catch (Exception ignored) {
                    // fall through to warning below
                }
            }
            log.warn("Could not truncate table {}: {}", table, e.getMessage());
        }
    }

    private String toSnakeCase(String table) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < table.length(); i++) {
            char c = table.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private User createUser(String email, String rawPassword, boolean admin, String nickname, boolean termsAccepted) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(encoder.encode(rawPassword));
        user.setAdmin(admin);
        user.setNickName(nickname);
        user.setMaxOwnedLadders(defaultMaxOwnedLadders);
        user.setRegisteredAt(Instant.now().minusSeconds(172_800));
        if (termsAccepted) {
            user.setAcknowledgedTermsAt(Instant.now().minusSeconds(86_400));
        }
        return saveUserWithUniquePublicCode(user);
    }

    private List<User> createLargeSessionQaUsers(int desiredCount) {
        List<User> users = new ArrayList<>();
        int total = Math.max(desiredCount, 0);
        for (int i = 1; i <= total; i++) {
            String suffix = String.format(Locale.US, "%02d", i);
            User user = createUser("picker" + suffix + "@demo.local", "playhard", false, "Picker" + suffix, true);
            addCourtName(user, "Bench" + suffix, null);
            users.add(user);
        }
        return users;
    }

    private void upsertMembership(LadderConfig config, User user, Role role, State state) {
        LadderMembership membership = membershipRepo.findByLadderConfigIdAndUserId(config.getId(), user.getId())
                .orElseGet(() -> {
                    LadderMembership fresh = new LadderMembership();
                    fresh.setLadderConfig(config);
                    fresh.setUserId(user.getId());
                    return fresh;
                });
        membership.setRole(role);
        membership.setState(state);
        membershipRepo.save(membership);
    }

    private Match logMatch(LadderSeason season,
            LocalDate matchDate,
            User a1,
            User a2,
            User b1,
            User b2,
            int scoreA,
            int scoreB) {
        // Seed data should count in standings by default.
        return logMatch(season, matchDate, a1, a2, b1, b2, scoreA, scoreB, MatchState.CONFIRMED, false, null, null);
    }

    private Match logMatch(LadderSeason season,
            LocalDate matchDate,
            User a1,
            User a2,
            User b1,
            User b2,
            int scoreA,
            int scoreB,
            MatchState state,
            boolean scoreEstimated,
            Integer confidenceScore) {
        return logMatch(season, matchDate, a1, a2, b1, b2, scoreA, scoreB, state, scoreEstimated, confidenceScore, null);
    }

    private Match logSessionMatch(LadderConfig sourceSessionConfig,
            LadderSeason season,
            LocalDate matchDate,
            User a1,
            User a2,
            User b1,
            User b2,
            int scoreA,
            int scoreB) {
        return logMatch(season, matchDate, a1, a2, b1, b2, scoreA, scoreB, MatchState.CONFIRMED, false, null, sourceSessionConfig);
    }

    private Match logMatch(LadderSeason season,
            LocalDate matchDate,
            User a1,
            User a2,
            User b1,
            User b2,
            int scoreA,
            int scoreB,
            MatchState state,
            boolean scoreEstimated,
            Integer confidenceScore,
            LadderConfig sourceSessionConfig) {
        Match match = new Match();
        LadderSeason managedSeason = season;
        if (season != null && season.getId() != null) {
            managedSeason = seasonRepo.findByIdWithLadderConfig(season.getId()).orElse(season);
        }
        match.setSeason(managedSeason);
        match.setSourceSessionConfig(sourceSessionConfig);
        ZonedDateTime played = matchDate.atTime(18, 0).atZone(LADDER_ZONE);
        Instant occurredAt = played.toInstant();
        match.setPlayedAt(occurredAt);
        // Match chronology is playedAt. Seed createdAt alongside it so diagnostic/logging views stay aligned.
        match.setCreatedAt(occurredAt);
        match.setState(state);

        match.setA1(a1);
        match.setA2(a2);
        match.setA1Guest(false);
        match.setA2Guest(a2 == null);

        match.setB1(b1);
        match.setB2(b2);
        match.setB1Guest(b1 == null);
        match.setB2Guest(b2 == null);

        match.setScoreA(scoreA);
        match.setScoreB(scoreB);
        match.setScoreEstimated(scoreEstimated);
        match.setConfidenceScore(confidenceScore);
        match.setCosignedBy(b1 != null ? b1 : a1);

        // Deterministic logger selection keeps seeded standings stable across restarts.
        match.setLoggedBy(selectDeterministicLogger(a1, a2, b1, b2));

            Match saved = matchFactory.createMatch(match);
            ladderService.applyMatch(saved);

            // If enabled, create the expected confirmation requests for matches
            // that are NOT already confirmed. This mirrors runtime behaviour where
            // AUTO placeholder rows are created for the losing-side players.
            if (seedConfirmations) {
                if (saved.getState() != MatchState.CONFIRMED) {
                    try {
                        matchConfirmationService.createRequests(saved);
                    } catch (Exception ex) {
                        log.warn("Unable to create seeded MatchConfirmation placeholders for match {}: {}", saved.getId(), ex.getMessage());
                    }
                }
            }
            return saved;
    }

    private User selectDeterministicLogger(User a1, User a2, User b1, User b2) {
        if (a1 != null) return a1;
        if (a2 != null) return a2;
        if (b1 != null) return b1;
        return b2;
    }

    private void addCourtName(User user, String alias, LadderConfig ladder) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (alias == null || alias.isBlank()) {
            return;
        }
        UserCourtName record = new UserCourtName();
        record.setUser(user);
        record.setAlias(alias.trim());
        record.setLadderConfig(ladder);
        courtNameRepo.save(record);
    }

    private void seedPlayLocations(User admin,
            User member,
            User coach,
            User spectator,
            User other1,
            User sunriseRiley,
            User sunriseJordan) {
        // Seed a few shared physical locations for the core QA accounts only.
        // Leave some demo users without saved locations, and do not touch the large global competition population.
        // Keep the public repository seed data generic so it does not expose real places.
        Instant now = Instant.now();
        Duration activeCheckInWindow = Duration.ofMinutes(180);

        PlayLocation lakeside = createPlayLocation(member, 40.1000d, -75.1000d, now.minus(24, ChronoUnit.DAYS));
        addPlayLocationAlias(lakeside, member, "Lakeside Courts", 6, now.minus(2, ChronoUnit.DAYS));
        addPlayLocationAlias(lakeside, coach, "Lakeside Park", 4, now.minus(1, ChronoUnit.DAYS));
        addPlayLocationAlias(lakeside, admin, "Lakeside", 2, now.minus(8, ChronoUnit.HOURS));
        addPlayLocationCheckIn(lakeside, member, "Lakeside Courts", now.minus(55, ChronoUnit.MINUTES), activeCheckInWindow);
        addPlayLocationCheckIn(lakeside, coach, "Lakeside Park", now.minus(80, ChronoUnit.MINUTES), activeCheckInWindow);

        PlayLocation maple = createPlayLocation(spectator, 40.1100d, -75.1150d, now.minus(19, ChronoUnit.DAYS));
        addPlayLocationAlias(maple, spectator, "Maple Street Park", 5, now.minus(3, ChronoUnit.DAYS));
        addPlayLocationAlias(maple, other1, "Maple Street Courts", 3, now.minus(36, ChronoUnit.HOURS));
        addPlayLocationAlias(maple, sunriseRiley, "Maple Street", 1, now.minus(6, ChronoUnit.HOURS));
        addPlayLocationCheckIn(maple, spectator, "Maple Street Park", now.minus(45, ChronoUnit.MINUTES), activeCheckInWindow);
        addPlayLocationCheckIn(maple, other1, "Maple Street Courts", now.minus(25, ChronoUnit.MINUTES), activeCheckInWindow);

        PlayLocation riverfront = createPlayLocation(admin, 40.1225d, -75.1300d, now.minus(28, ChronoUnit.DAYS));
        addPlayLocationAlias(riverfront, admin, "Riverfront Rec Center", 5, now.minus(4, ChronoUnit.DAYS));
        addPlayLocationAlias(riverfront, sunriseJordan, "Riverfront Courts", 3, now.minus(18, ChronoUnit.HOURS));
        addPlayLocationCheckIn(riverfront, admin, "Riverfront Rec Center", now.minus(30, ChronoUnit.MINUTES), activeCheckInWindow);
        addPlayLocationCheckIn(riverfront, sunriseJordan, "Riverfront Courts", now.minus(65, ChronoUnit.MINUTES), activeCheckInWindow);
    }

    private PlayLocation createPlayLocation(User createdBy, double latitude, double longitude, Instant createdAt) {
        if (createdBy == null || createdBy.getId() == null) {
            throw new IllegalArgumentException("Seeded play locations require a persisted user.");
        }

        PlayLocation location = new PlayLocation();
        location.setCreatedBy(createdBy);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setCreatedAt(createdAt != null ? createdAt : Instant.now());
        return playLocationRepo.save(location);
    }

    private void addPlayLocationAlias(PlayLocation location,
            User user,
            String displayName,
            int usageCount,
            Instant lastUsedAt) {
        if (location == null || location.getId() == null || user == null || user.getId() == null) {
            return;
        }
        if (displayName == null || displayName.isBlank()) {
            return;
        }

        String normalizedName = DisplayNameNormalization.normalize(displayName);
        if (normalizedName.isBlank()) {
            return;
        }

        Instant effectiveLastUsedAt = lastUsedAt != null ? lastUsedAt : Instant.now();
        int effectiveUsageCount = Math.max(usageCount, 1);
        PlayLocationAlias alias = new PlayLocationAlias();
        alias.setLocation(location);
        alias.setUser(user);
        alias.setDisplayName(displayName.trim());
        alias.setNormalizedName(normalizedName);
        alias.setPhoneticKey(playLocationPhoneticKey(displayName));
        alias.setUsageCount(effectiveUsageCount);
        alias.setLastUsedAt(effectiveLastUsedAt);
        alias.setFirstUsedAt(effectiveLastUsedAt.minus(Math.max(effectiveUsageCount - 1, 0), ChronoUnit.DAYS));
        playLocationAliasRepo.save(alias);
    }

    private void addPlayLocationCheckIn(PlayLocation location,
            User user,
            String displayName,
            Instant checkedInAt,
            Duration activeWindow) {
        if (location == null || location.getId() == null || user == null || user.getId() == null) {
            return;
        }

        Instant effectiveCheckedInAt = checkedInAt != null ? checkedInAt : Instant.now();
        Duration effectiveWindow = activeWindow != null && !activeWindow.isNegative() && !activeWindow.isZero()
                ? activeWindow
                : Duration.ofHours(3);

        PlayLocationCheckIn checkIn = new PlayLocationCheckIn();
        checkIn.setLocation(location);
        checkIn.setUser(user);
        checkIn.setDisplayName(displayName != null && !displayName.isBlank()
                ? displayName.trim()
                : "Seeded Location");
        checkIn.setCheckedInAt(effectiveCheckedInAt);
        checkIn.setExpiresAt(effectiveCheckedInAt.plus(effectiveWindow));
        playLocationCheckInRepo.save(checkIn);
    }

    private String playLocationPhoneticKey(String displayName) {
        String normalizedName = DisplayNameNormalization.normalize(displayName);
        if (normalizedName.isBlank()) {
            return "";
        }
        String[] phonetics = playLocationDoubleMetaphone.doubleMetaphone(normalizedName);
        if (phonetics == null || phonetics.length == 0 || phonetics[0] == null || phonetics[0].isBlank()) {
            return normalizedName;
        }
        return phonetics[0];
    }

    private void closeSeason(LadderSeason season, User user) {
        season.setState(LadderSeason.State.ENDED);
        ZonedDateTime endedAt = season.getEndDate()
                .plusDays(1)
                .atStartOfDay(LADDER_ZONE)
                .minusHours(2);
        season.setEndedAt(endedAt.toInstant());
        season.setEndedByUserId(user.getId());
        seasonRepo.save(season);
    }

    private void recalcSeasonStandingsSync(LadderSeason season) {
        if (season == null || season.getId() == null) {
            return;
        }
        try {
            LadderSeason managed = seasonRepo.findById(season.getId()).orElse(null);
            if (managed != null) {
                ladderService.recalcSeasonStandings(managed);
            }
        } catch (Exception ex) {
            log.warn("Unable to precompute standings during seed for season {}: {}", season.getId(), ex.getMessage());
        }
    }

    private LadderSeason ensureDevCompetitionSeason(User admin) {
        LadderConfig competition = configRepo.findFirstByTypeOrderByIdAsc(LadderConfig.Type.COMPETITION)
                .orElseGet(() -> {
                    LadderConfig created = configService.createConfigAndSeason(
                            admin.getId(),
                            competitionLadderTitle,
                            LocalDate.now(LADDER_ZONE),
                            null,
                            competitionSeasonName,
                            Mode.ROLLING,
                            6,
                            CadenceUnit.WEEKS,
                            LadderSecurity.STANDARD,
                            false,
                            false);
                    created.setType(LadderConfig.Type.COMPETITION);
                    return configRepo.saveAndFlush(created);
                });

        if (competition.getType() != LadderConfig.Type.COMPETITION) {
            competition.setType(LadderConfig.Type.COMPETITION);
            competition = configRepo.saveAndFlush(competition);
        }

        LadderSeason activeSeason = seasonRepo.findActive(competition.getId()).orElse(null);
        if (activeSeason == null) {
            activeSeason = new LadderSeason();
            activeSeason.setLadderConfig(competition);
            activeSeason.setName(competitionSeasonName);
            activeSeason.setState(LadderSeason.State.ACTIVE);
        }

        LocalDate seededStart = LocalDate.now(LADDER_ZONE).minusDays(7);
        activeSeason.setStartDate(seededStart);
        activeSeason.setEndDate(seededStart.plusWeeks(6));
        activeSeason.setStartedAt(seededStart.atStartOfDay(LADDER_ZONE).toInstant());
        activeSeason.setStartedByUserId(admin.getId());
        activeSeason = seasonRepo.saveAndFlush(activeSeason);

        return seasonRepo.findByIdWithLadderConfig(activeSeason.getId()).orElse(activeSeason);
    }

    private LadderConfig seedMatchSession(User owner,
            String title,
            LadderSeason competitionSeason,
            User... participants) {
        LadderConfig session = configService.createSessionConfig(owner.getId(), title, competitionSeason);
        upsertMembership(session, owner, Role.ADMIN, State.ACTIVE);
        Stream.of(participants)
                .filter(Objects::nonNull)
                .filter(user -> !Objects.equals(user.getId(), owner.getId()))
                .forEach(user -> upsertMembership(session, user, Role.MEMBER, State.ACTIVE));
        return configRepo.saveAndFlush(session);
    }

    private void seedSessionTickerMatches(LadderConfig sessionConfig,
            LadderSeason competitionSeason,
            User... featuredPlayers) {
        if (sessionConfig == null || competitionSeason == null) {
            return;
        }

        List<User> pool = Stream.of(featuredPlayers)
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .distinct()
                .collect(Collectors.toList());
        if (pool.size() < 4) {
            return;
        }

        int[][] scores = {
                { 11, 5 },
                { 15, 13 },
                { 11, 7 },
                { 15, 10 }
        };
        LocalDate seedStart = LocalDate.now(LADDER_ZONE).minusDays(scores.length);
        for (int i = 0; i < scores.length; i++) {
            int offset = i % pool.size();
            User a1 = pool.get(offset);
            User a2 = pool.get((offset + 1) % pool.size());
            User b1 = pool.get((offset + 2) % pool.size());
            User b2 = pool.get((offset + 3) % pool.size());
            logSessionMatch(
                    sessionConfig,
                    competitionSeason,
                    seedStart.plusDays(i),
                    a1,
                    a2,
                    b1,
                    b2,
                    scores[i][0],
                    scores[i][1]);
        }
    }

    private void seedCompetitionMatches(LadderSeason competitionSeason,
            User member,
            User coach,
            User spectator,
            User other1,
            User sunriseRiley,
            User sunriseJordan) {
        LocalDate competitionStart = LocalDate.now(LADDER_ZONE).minusDays(3);
        logMatch(competitionSeason, competitionStart, member, coach, sunriseRiley, sunriseJordan, 11, 8);
        logMatch(competitionSeason, competitionStart.plusDays(1), other1, null, member, null, 11, 9);
        logMatch(competitionSeason, competitionStart.plusDays(2), member, spectator, coach, other1, 15, 13);
    }

    private void seedCompetitionAutoModerationScenario(LadderSeason competitionSeason,
            User ruleBreaker,
            int incidentCount,
            User... opponents) {
        if (competitionSeason == null
                || competitionSeason.getLadderConfig() == null
                || ruleBreaker == null
                || ruleBreaker.getId() == null) {
            return;
        }

        upsertMembership(competitionSeason.getLadderConfig(), ruleBreaker, Role.MEMBER, State.ACTIVE);

        List<User> opponentPool = Stream.of(opponents)
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .filter(user -> !Objects.equals(user.getId(), ruleBreaker.getId()))
                .distinct()
                .collect(Collectors.toList());
        if (opponentPool.isEmpty()) {
            return;
        }

        LocalDate seasonStart = competitionSeason.getStartDate() != null
                ? competitionSeason.getStartDate()
                : LocalDate.now(LADDER_ZONE).minusDays(7);
        LocalDate lastPlayableDate = LocalDate.now(LADDER_ZONE).minusDays(1);
        if (lastPlayableDate.isBefore(seasonStart)) {
            lastPlayableDate = seasonStart;
        }
        int availablePastDays = Math.max(1, (int) ChronoUnit.DAYS.between(seasonStart, lastPlayableDate) + 1);
        int incidentsToSeed = Math.max(1, incidentCount);

        for (int i = 0; i < incidentsToSeed; i++) {
            User logger = opponentPool.get(i % opponentPool.size());
            LocalDate matchDate = seasonStart.plusDays(i % availablePastDays);
            int losingScore = 5 + (i % 5);
            seedExpiredCompetitionConfirmationStrike(
                    competitionSeason,
                    matchDate,
                    logger,
                    ruleBreaker,
                    11,
                    losingScore);
        }

        log.info("Seeded {} expired competition confirmations for {} in season {}.",
                incidentsToSeed,
                ruleBreaker.getEmail(),
                competitionSeason.getId());
    }

    private void seedCompetitionDisputeSpamScenario(LadderSeason competitionSeason,
            User spamLogger,
            User... opponents) {
        if (competitionSeason == null
                || competitionSeason.getLadderConfig() == null
                || spamLogger == null
                || spamLogger.getId() == null) {
            return;
        }

        upsertMembership(competitionSeason.getLadderConfig(), spamLogger, Role.MEMBER, State.ACTIVE);

        List<User> opponentPool = Stream.of(opponents)
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .filter(user -> !Objects.equals(user.getId(), spamLogger.getId()))
                .distinct()
                .collect(Collectors.toList());
        if (opponentPool.isEmpty()) {
            return;
        }

        LocalDate seasonStart = competitionSeason.getStartDate() != null
                ? competitionSeason.getStartDate()
                : LocalDate.now(LADDER_ZONE).minusDays(7);
        LocalDate lastPlayableDate = LocalDate.now(LADDER_ZONE).minusDays(1);
        if (lastPlayableDate.isBefore(seasonStart)) {
            lastPlayableDate = seasonStart;
        }
        int availablePastDays = Math.max(1, (int) ChronoUnit.DAYS.between(seasonStart, lastPlayableDate) + 1);

        for (int i = 0; i < DEV_DISPUTE_SPAM_MATCH_COUNT; i++) {
            User opponent = opponentPool.get(i % opponentPool.size());
            LocalDate matchDate = seasonStart.plusDays((i + 1) % availablePastDays);
            seedDisputedCompetitionMatch(
                    competitionSeason,
                    matchDate,
                    spamLogger,
                    opponent,
                    11,
                    6 + (i % 4),
                    "Seeded dispute: this logger keeps entering matches opponents deny playing.");
        }

        log.info("Seeded {} disputed competition matches logged by {} in season {}.",
                DEV_DISPUTE_SPAM_MATCH_COUNT,
                spamLogger.getEmail(),
                competitionSeason.getId());
    }

    private void seedConfirmedMatchRemovalScenarios(LadderSeason season,
            User admin,
            User member,
            User coach,
            User spectator,
            User sunriseRiley,
            User sunriseJordan) {
        if (season == null
                || admin == null
                || member == null
                || coach == null
                || spectator == null
                || sunriseRiley == null
                || sunriseJordan == null) {
            return;
        }

        LocalDate pendingRequestDate = LocalDate.now(LADDER_ZONE).minusDays(2);
        Match pendingRequestMatch = logMatch(
                season,
                pendingRequestDate,
                member,
                coach,
                sunriseRiley,
                sunriseJordan,
                15,
                13,
                MatchState.CONFIRMED,
                false,
                92);
        pendingRequestMatch.setVerificationNotes(
                "Seeded confirmed-match removal QA: DaveM and CoachYoung requested removal; RileySun or JordanRay can approve within 48 hours.");
        pendingRequestMatch = matchRepo.saveAndFlush(pendingRequestMatch);
        confirmedMatchNullificationService.requestNullification(pendingRequestMatch.getId(), member.getId(), null);

        LocalDate resolvedRemovalDate = LocalDate.now(LADDER_ZONE).minusDays(1);
        Match resolvedRemovalMatch = logMatch(
                season,
                resolvedRemovalDate,
                admin,
                member,
                coach,
                spectator,
                11,
                9,
                MatchState.CONFIRMED,
                false,
                89);
        resolvedRemovalMatch.setVerificationNotes(
                "Seeded confirmed-match removal QA: both teams agreed to remove this confirmed result.");
        resolvedRemovalMatch = matchRepo.saveAndFlush(resolvedRemovalMatch);
        confirmedMatchNullificationService.requestNullification(resolvedRemovalMatch.getId(), admin.getId(), null);
        confirmedMatchNullificationService.requestNullification(resolvedRemovalMatch.getId(), coach.getId(), null);
        Match finalResolvedRemovalMatch = matchRepo.findByIdWithUsers(resolvedRemovalMatch.getId())
                .orElse(resolvedRemovalMatch);

        log.info(
                "Seeded confirmed-match removal QA in season {}: pendingRequestMatchId={} ({} & {} vs {} & {}), nullifiedMatchId={} ({} & {} vs {} & {}), finalResultState={}.",
                season.getId(),
                pendingRequestMatch.getId(),
                member.getNickName(),
                coach.getNickName(),
                sunriseRiley.getNickName(),
                sunriseJordan.getNickName(),
                finalResolvedRemovalMatch.getId(),
                admin.getNickName(),
                member.getNickName(),
                coach.getNickName(),
                spectator.getNickName(),
                finalResolvedRemovalMatch.getState());
    }

    private void seedLargeCompetitionPopulation(LadderSeason competitionSeason,
            User admin,
            User member,
            User coach,
            User spectator,
            User other1,
            User other2,
            User sunriseRiley,
            User sunriseJordan,
            User sunriseSky,
            User sunriseDawn) {
        if (!seedLargeCompetitionEnabled || competitionSeason == null || competitionSeason.getLadderConfig() == null) {
            return;
        }

        int desiredParticipants = Math.max(seedLargeCompetitionParticipants, 16);
        int desiredMatches = Math.max(seedLargeCompetitionMatches, desiredParticipants * 2);
        String sharedPasswordHash = encoder.encode("playhard");
        Random rng = new Random(20_260_315L);

        List<CompetitionSeedParticipant> participants = buildCompetitionParticipants(
                desiredParticipants,
                sharedPasswordHash,
                rng,
                admin,
                member,
                coach,
                spectator,
                other1,
                other2,
                sunriseRiley,
                sunriseJordan,
                sunriseSky,
                sunriseDawn);

        LadderConfig competitionConfig = competitionSeason.getLadderConfig();
        for (CompetitionSeedParticipant participant : participants) {
            Role role = Objects.equals(participant.user().getId(), admin.getId()) ? Role.ADMIN : Role.MEMBER;
            upsertMembership(competitionConfig, participant.user(), role, State.ACTIVE);
        }

        int generatedMatches = seedProceduralCompetitionMatches(competitionSeason, participants, desiredMatches, rng);
        log.info("Seeded dev global competition population: participants={}, generatedMatches={}, seasonId={}.",
                participants.size(),
                generatedMatches,
                competitionSeason.getId());
    }

    private List<CompetitionSeedParticipant> buildCompetitionParticipants(int desiredParticipants,
            String sharedPasswordHash,
            Random rng,
            User... anchorUsers) {
        List<CompetitionSeedParticipant> participants = new ArrayList<>();
        Set<String> usedDisplayNames = new LinkedHashSet<>();
        int anchorSkill = 1710;

        for (User anchorUser : anchorUsers) {
            if (anchorUser == null || anchorUser.getId() == null) {
                continue;
            }
            usedDisplayNames.add(anchorUser.getNickName());
            participants.add(new CompetitionSeedParticipant(
                    anchorUser,
                    anchorSkill + rng.nextInt(19) - 9,
                    rng.nextDouble() < 0.42));
            anchorSkill -= 38;
        }

        int syntheticIndex = 0;
        while (participants.size() < desiredParticipants) {
            syntheticIndex++;
            String displayName = buildCompetitionDisplayName(syntheticIndex, usedDisplayNames);
            String email = String.format("global%03d@demo.local", syntheticIndex);
            Instant registeredAt = Instant.now().minus(Duration.ofDays(30L + rng.nextInt(360)).plusHours(rng.nextInt(24)));
            User user = createUserWithPasswordHash(email, sharedPasswordHash, false, displayName, true, registeredAt);
            String[] nameParts = displayName.split(" ");
            if (nameParts.length > 0) {
                addCourtName(user, nameParts[0], null);
            }
            int ladderIndex = participants.size();
            double percentile = ladderIndex / (double) Math.max(desiredParticipants - 1, 1);
            int skill = (int) Math.round(1650 - (percentile * 620)) + rng.nextInt(31) - 15;
            participants.add(new CompetitionSeedParticipant(user, skill, rng.nextDouble() < 0.47));
        }

        return participants;
    }

    private String buildCompetitionDisplayName(int syntheticIndex, Set<String> usedDisplayNames) {
        int comboIndex = syntheticIndex - 1;
        String first = COMPETITION_FIRST_NAMES[comboIndex % COMPETITION_FIRST_NAMES.length];
        String last = COMPETITION_LAST_NAMES[(comboIndex / COMPETITION_FIRST_NAMES.length) % COMPETITION_LAST_NAMES.length];
        String candidate = first + " " + last;
        if (!usedDisplayNames.add(candidate)) {
            candidate = candidate + " " + (comboIndex / (COMPETITION_FIRST_NAMES.length * COMPETITION_LAST_NAMES.length) + 2);
            usedDisplayNames.add(candidate);
        }
        return candidate;
    }

    private User createUserWithPasswordHash(String email,
            String encodedPassword,
            boolean admin,
            String nickname,
            boolean termsAccepted,
            Instant registeredAt) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setAdmin(admin);
        user.setNickName(nickname);
        user.setMaxOwnedLadders(defaultMaxOwnedLadders);
        user.setRegisteredAt(registeredAt != null ? registeredAt : Instant.now().minusSeconds(172_800));
        if (termsAccepted) {
            user.setAcknowledgedTermsAt(user.getRegisteredAt().plus(Duration.ofHours(12)));
        }
        return saveUserWithUniquePublicCode(user);
    }

    private User saveUserWithUniquePublicCode(User user) {
        if (user == null) {
            return null;
        }
        if (user.getPublicCode() == null || user.getPublicCode().isBlank()) {
            user.setPublicCode(generateUniquePublicCode());
        }
        return userRepo.save(user);
    }

    private String generateUniquePublicCode() {
        for (int attempt = 0; attempt < 256; attempt++) {
            String candidate = UserPublicCodeGenerator.nextCode();
            if (!userRepo.existsByPublicCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique public code for dev seed data.");
    }

    private int seedProceduralCompetitionMatches(LadderSeason competitionSeason,
            List<CompetitionSeedParticipant> participants,
            int desiredMatches,
            Random rng) {
        if (participants == null || participants.size() < 2 || desiredMatches <= 0) {
            return 0;
        }

        List<CompetitionSeedParticipant> rankedParticipants = participants.stream()
                .sorted((left, right) -> Integer.compare(right.skill(), left.skill()))
                .collect(Collectors.toCollection(ArrayList::new));

        LocalDate seasonStart = competitionSeason.getStartDate() != null
                ? competitionSeason.getStartDate()
                : LocalDate.now(LADDER_ZONE).minusDays(7);
        LocalDate lastPlayableDate = LocalDate.now(LADDER_ZONE).minusDays(1);
        if (lastPlayableDate.isBefore(seasonStart)) {
            lastPlayableDate = seasonStart;
        }
        int availableDays = (int) ChronoUnit.DAYS.between(seasonStart, lastPlayableDate) + 1;
        int seededMatches = 0;

        for (int i = 0; i < desiredMatches; i++) {
            boolean doubles = rankedParticipants.size() >= 4 && rng.nextDouble() < 0.38;
            List<CompetitionSeedParticipant> lineup = pickCompetitionLineup(
                    rankedParticipants,
                    doubles ? 4 : 2,
                    doubles,
                    rng);
            if (lineup.size() < (doubles ? 4 : 2)) {
                continue;
            }

            CompetitionSeedParticipant a1 = lineup.get(0);
            CompetitionSeedParticipant a2 = doubles ? lineup.get(3) : null;
            CompetitionSeedParticipant b1 = lineup.get(1);
            CompetitionSeedParticipant b2 = doubles ? lineup.get(2) : null;

            int teamASkill = a1.skill() + (a2 != null ? a2.skill() : 0);
            int teamBSkill = b1.skill() + (b2 != null ? b2.skill() : 0);
            double winProbabilityA = 1.0d / (1.0d + Math.exp(-(teamASkill - teamBSkill) / 95.0d));
            boolean teamAWins = rng.nextDouble() < winProbabilityA;
            int targetScore = doubles ? 15 : 11;
            int losingScore = deriveLosingScore(targetScore, Math.abs(teamASkill - teamBSkill), rng);
            boolean scoreEstimated = rng.nextDouble() < 0.08;
            int confidence = scoreEstimated ? 56 + rng.nextInt(25) : 78 + rng.nextInt(21);
            LocalDate matchDate = seasonStart.plusDays(rng.nextInt(Math.max(availableDays, 1)));

            logMatch(
                    competitionSeason,
                    matchDate,
                    a1.user(),
                    a2 != null ? a2.user() : null,
                    b1.user(),
                    b2 != null ? b2.user() : null,
                    teamAWins ? targetScore : losingScore,
                    teamAWins ? losingScore : targetScore,
                    MatchState.CONFIRMED,
                    scoreEstimated,
                    confidence);
            seededMatches++;
        }

        return seededMatches;
    }

    private void seedExpiredCompetitionConfirmationStrike(LadderSeason season,
            LocalDate matchDate,
            User logger,
            User target,
            int scoreA,
            int scoreB) {
        if (season == null || logger == null || target == null) {
            return;
        }

        Match match = logMatch(
                season,
                matchDate,
                logger,
                null,
                target,
                null,
                scoreA,
                scoreB,
                MatchState.PROVISIONAL,
                false,
                84);
        match.setVerificationNotes("Seeded expired competition confirmation for auto-moderation QA.");
        matchRepo.save(match);
        matchConfirmationService.createRequests(match);
        ladderService.nullifyMatch(match, true);
    }

    private void seedDisputedCompetitionMatch(LadderSeason season,
            LocalDate matchDate,
            User logger,
            User opponent,
            int scoreA,
            int scoreB,
            String disputeNote) {
        if (season == null || logger == null || opponent == null) {
            return;
        }

        Match match = logMatch(
                season,
                matchDate,
                logger,
                null,
                opponent,
                null,
                scoreA,
                scoreB,
                MatchState.PROVISIONAL,
                false,
                78);
        match.setVerificationNotes("Seeded disputed competition match for logger-spam QA.");
        matchRepo.save(match);
        matchConfirmationService.createRequests(match);
        matchConfirmationService.disputeMatch(match.getId(), opponent.getId(), disputeNote);
    }

    private List<CompetitionSeedParticipant> pickCompetitionLineup(List<CompetitionSeedParticipant> rankedParticipants,
            int playersNeeded,
            boolean doubles,
            Random rng) {
        int size = rankedParticipants.size();
        if (size < playersNeeded) {
            return List.of();
        }

        int anchorIndex = Math.min(size - 1, (int) Math.floor(Math.pow(rng.nextDouble(), 1.35) * size));
        int window = doubles ? 14 : 11;
        Set<Integer> selectedIndexes = new LinkedHashSet<>();
        selectedIndexes.add(anchorIndex);
        int attempts = 0;

        while (selectedIndexes.size() < playersNeeded && attempts < 160) {
            int candidate = anchorIndex + rng.nextInt(window * 2 + 1) - window;
            if (candidate < 0 || candidate >= size) {
                attempts++;
                continue;
            }
            selectedIndexes.add(candidate);
            attempts++;
        }

        while (selectedIndexes.size() < playersNeeded) {
            selectedIndexes.add(rng.nextInt(size));
        }

        return selectedIndexes.stream()
                .limit(playersNeeded)
                .map(rankedParticipants::get)
                .sorted((left, right) -> Integer.compare(right.skill(), left.skill()))
                .collect(Collectors.toList());
    }

    private int deriveLosingScore(int targetScore, int skillGap, Random rng) {
        int baseline = targetScore - 2 - rng.nextInt(4);
        int adjustment = Math.min(skillGap / 90, 5);
        return Math.max(2, Math.min(targetScore - 1, baseline - adjustment));
    }

    private void normalizeBandPositionSchemaForSeasonScope() {
        try {
            Integer hasSeasonColumn = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'band_positions' " +
                            "AND COLUMN_NAME = 'season_id'",
                    Integer.class);
            if (hasSeasonColumn == null || hasSeasonColumn == 0) {
                jdbc.execute("ALTER TABLE band_positions ADD COLUMN season_id BIGINT NULL");
                log.info("Added band_positions.season_id column");
            }
        } catch (Exception ex) {
            log.warn("Unable to ensure band_positions.season_id column: {}", ex.getMessage());
        }

        try {
            Integer hasUserIndex = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() " +
                            "AND table_name = 'band_positions' " +
                            "AND index_name = 'idx_band_positions_user'",
                    Integer.class);
            if (hasUserIndex == null || hasUserIndex == 0) {
                jdbc.execute("CREATE INDEX idx_band_positions_user ON band_positions(user_id)");
            }
        } catch (Exception ex) {
            log.warn("Unable to ensure idx_band_positions_user index: {}", ex.getMessage());
        }

        try {
            List<String> userFkNames = jdbc.queryForList(
                    "SELECT DISTINCT CONSTRAINT_NAME " +
                            "FROM information_schema.KEY_COLUMN_USAGE " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'band_positions' " +
                            "AND COLUMN_NAME = 'user_id' " +
                            "AND REFERENCED_TABLE_NAME = 'users'",
                    String.class);
            for (String fkName : userFkNames) {
                if (fkName == null || fkName.isBlank()) {
                    continue;
                }
                String escapedFk = fkName.replace("`", "``");
                jdbc.execute("ALTER TABLE band_positions DROP FOREIGN KEY `" + escapedFk + "`");
                log.info("Dropped band_positions user FK temporarily: {}", fkName);
            }

            List<String> legacyUniqueIndexes = jdbc.queryForList(
                    "SELECT s.index_name " +
                            "FROM information_schema.statistics s " +
                            "WHERE s.table_schema = DATABASE() " +
                            "AND s.table_name = 'band_positions' " +
                            "AND s.non_unique = 0 " +
                            "AND s.index_name <> 'PRIMARY' " +
                            "GROUP BY s.index_name " +
                            "HAVING COUNT(*) = 1 " +
                            "AND SUM(CASE WHEN s.column_name = 'user_id' THEN 1 ELSE 0 END) = 1",
                    String.class);
            for (String indexName : legacyUniqueIndexes) {
                if (indexName == null || indexName.isBlank()) {
                    continue;
                }
                String escaped = indexName.replace("`", "``");
                jdbc.execute("ALTER TABLE band_positions DROP INDEX `" + escaped + "`");
                log.info("Dropped legacy unique index on band_positions.user_id: {}", indexName);
            }

            Integer hasUserFk = jdbc.queryForObject(
                    "SELECT COUNT(*) " +
                            "FROM information_schema.KEY_COLUMN_USAGE " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'band_positions' " +
                            "AND COLUMN_NAME = 'user_id' " +
                            "AND REFERENCED_TABLE_NAME = 'users'",
                    Integer.class);
            if (hasUserFk == null || hasUserFk == 0) {
                jdbc.execute(
                        "ALTER TABLE band_positions " +
                                "ADD CONSTRAINT fk_band_positions_user FOREIGN KEY (user_id) " +
                                "REFERENCES users(id) ON DELETE CASCADE");
                log.info("Added foreign key fk_band_positions_user");
            }
        } catch (Exception ex) {
            log.warn("Unable to drop legacy band_positions unique index(es): {}", ex.getMessage());
        }

        try {
            Integer hasCompositeUnique = jdbc.queryForObject(
                    "SELECT COUNT(*) " +
                            "FROM (" +
                            "  SELECT s.index_name " +
                            "  FROM information_schema.statistics s " +
                            "  WHERE s.table_schema = DATABASE() " +
                            "    AND s.table_name = 'band_positions' " +
                            "    AND s.non_unique = 0 " +
                            "    AND s.index_name <> 'PRIMARY' " +
                            "  GROUP BY s.index_name " +
                            "  HAVING COUNT(*) = 2 " +
                            "     AND SUM(CASE WHEN s.column_name = 'season_id' THEN 1 ELSE 0 END) = 1 " +
                            "     AND SUM(CASE WHEN s.column_name = 'user_id' THEN 1 ELSE 0 END) = 1" +
                            ") t",
                    Integer.class);
            if (hasCompositeUnique == null || hasCompositeUnique == 0) {
                jdbc.execute("ALTER TABLE band_positions ADD CONSTRAINT uk_band_positions_season_user UNIQUE (season_id, user_id)");
                log.info("Added unique constraint uk_band_positions_season_user");
            }
        } catch (Exception ex) {
            log.warn("Unable to ensure unique(season_id,user_id) on band_positions: {}", ex.getMessage());
        }

        try {
            Integer hasSeasonFk = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = 'band_positions' " +
                            "AND COLUMN_NAME = 'season_id' " +
                            "AND REFERENCED_TABLE_NAME = 'ladder_season'",
                    Integer.class);
            if (hasSeasonFk == null || hasSeasonFk == 0) {
                jdbc.execute(
                        "ALTER TABLE band_positions " +
                                "ADD CONSTRAINT fk_band_positions_season FOREIGN KEY (season_id) " +
                                "REFERENCES ladder_season(id) ON DELETE CASCADE");
                log.info("Added foreign key fk_band_positions_season");
            }
        } catch (Exception ex) {
            log.warn("Unable to ensure band_positions season FK: {}", ex.getMessage());
        }

        try {
            Integer seasonNullCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM band_positions WHERE season_id IS NULL",
                    Integer.class);
            if (seasonNullCount != null && seasonNullCount == 0) {
                jdbc.execute("ALTER TABLE band_positions MODIFY COLUMN season_id BIGINT NOT NULL");
            } else if (seasonNullCount != null && seasonNullCount > 0) {
                log.warn("Skipping NOT NULL on band_positions.season_id because {} rows are NULL", seasonNullCount);
            }
        } catch (Exception ex) {
            log.warn("Unable to enforce NOT NULL on band_positions.season_id: {}", ex.getMessage());
        }
    }

    private void seedTrophies(LadderSeason currentSeason,
            LadderSeason priorSeason,
            User primaryHolder,
            User secondaryHolder) {
        seedDefaultTemplates();
        autoTrophyService.generateSeasonTrophies(currentSeason);
        autoTrophyService.generateSeasonTrophies(priorSeason);

        List<Trophy> currentSeasonCollection = loadSeasonTrophies(currentSeason, 8);
        currentSeasonCollection.forEach(trophy ->
                grantTrophy(primaryHolder, trophy, "Seeded fallback trophy for current season."));

        List<Trophy> priorSeasonCollection = loadSeasonTrophies(priorSeason, 6);
        priorSeasonCollection.forEach(trophy ->
                grantTrophy(primaryHolder, trophy, "Seeded fallback trophy for prior season."));

        if (!currentSeasonCollection.isEmpty()) {
            grantTrophy(secondaryHolder, currentSeasonCollection.get(0), "Seeded comparison trophy for current season.");
        }
        if (currentSeasonCollection.size() > 1) {
            grantTrophy(secondaryHolder, currentSeasonCollection.get(1), "Seeded comparison trophy for current season.");
        }
        if (!priorSeasonCollection.isEmpty()) {
            grantTrophy(secondaryHolder, priorSeasonCollection.get(0), "Seeded comparison trophy for prior season.");
        }
        if (priorSeasonCollection.size() > 1) {
            grantTrophy(secondaryHolder, priorSeasonCollection.get(1), "Seeded comparison trophy for prior season.");
        }
    }

    private List<Trophy> loadSeasonTrophies(LadderSeason season, int limit) {
        if (season == null) {
            return List.of();
        }
        List<Trophy> trophies = trophyRepo.findBySeasonOrderByDisplayOrderAscIdAsc(season);
        if (limit <= 0 || trophies.isEmpty()) {
            return trophies;
        }
        return trophies.subList(0, Math.min(limit, trophies.size()));
    }

    private void seedDefaultTemplates() {
        if (trophyCatalogEntryRepo.existsByDefaultTemplateTrue()) {
            return;
        }

        List<GeneratedTrophy> templates = FallbackTrophyTemplates.createAll("Default Template");
        for (int i = 0; i < templates.size(); i++) {
            GeneratedTrophy template = templates.get(i);
            String imageUrl = template.getImageUrl();
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = "/images/trophy/fallback.png";
            }
            TrophyCatalogEntry trophy = new TrophyCatalogEntry();
            trophy.setTitle(template.getTitle());
            trophy.setSummary(template.getSummary());
            trophy.setUnlockCondition(template.getUnlockCondition());
            trophy.setUnlockExpression(template.getUnlockExpression());
            trophy.setRarity(template.getRarity());
            trophy.setLimited(template.isLimited());
            trophy.setRepeatable(template.isRepeatable());
            trophy.setMaxClaims(template.getMaxClaims());
            trophy.setPrompt(template.getPrompt());
            trophy.setAiProvider(template.getAiProvider());
            trophy.setGenerationSeed("default-template-" + buildSlugSuffix(template.getTitle(), i));
            trophy.setSlug("default-template-" + buildSlugSuffix(template.getTitle(), i));
            trophy.setDisplayOrder(i);
            trophy.setDefaultTemplate(true);
            trophy.setArt(trophyArtService.resolveOrCreate(imageUrl, null));
            trophyCatalogEntryRepo.save(trophy);
        }
    }

    private void seedAlwaysAvailableProfileBadges() {
        normalizeTrophySchemaForAlwaysAvailableBadges();
        if (jdbc.getDataSource() == null) {
            log.warn("Skipping always-available profile badge seed because no DataSource is available");
            return;
        }

        for (String scriptPath : ALWAYS_AVAILABLE_PROFILE_BADGE_SEED_SCRIPTS) {
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(scriptPath));
                populator.execute(jdbc.getDataSource());
                log.info("Ensured always-available profile badges from {}", scriptPath);
            } catch (Exception ex) {
                log.warn("Unable to seed always-available profile badges from {}: {}", scriptPath, ex.getMessage());
            }
        }
    }

    private void normalizeTrophySchemaForAlwaysAvailableBadges() {
        try {
            Integer trophyTableExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trophy'",
                    Integer.class);
            if (trophyTableExists == null || trophyTableExists == 0) {
                return;
            }

            Integer seasonNullable = jdbc.queryForObject(
                    "SELECT CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END " +
                            "FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trophy' AND COLUMN_NAME = 'season_id'",
                    Integer.class);
            if (seasonNullable != null && seasonNullable == 0) {
                jdbc.execute("ALTER TABLE trophy MODIFY COLUMN season_id BIGINT NULL");
                log.info("Updated trophy.season_id to allow NULL for always-available profile badges");
            }
        } catch (Exception ex) {
            log.warn("Unable to normalize trophy.season_id nullability: {}", ex.getMessage());
        }

        try {
            Integer badgeIndexExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() " +
                            "AND table_name = 'trophy' " +
                            "AND index_name = 'idx_trophy_badge_selectable_by_all'",
                    Integer.class);
            if (badgeIndexExists == null || badgeIndexExists == 0) {
                jdbc.execute("CREATE INDEX idx_trophy_badge_selectable_by_all ON trophy (badge_selectable_by_all, display_order, id)");
                log.info("Added trophy badge selection index idx_trophy_badge_selectable_by_all");
            }
        } catch (Exception ex) {
            log.warn("Unable to ensure trophy badge selection index: {}", ex.getMessage());
        }
    }

    private String buildSlugSuffix(String title, int displayOrder) {
        String base = (title == null || title.isBlank()) ? "fallback-trophy" : title;
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-", "")
                .replaceAll("-$", "");
        if (normalized.isBlank()) {
            normalized = "fallback-trophy";
        }
        return normalized + "-" + Math.max(displayOrder, 0);
    }

    private void grantTrophy(User user, Trophy trophy, String reason) {
        UserTrophy grant = new UserTrophy();
        grant.setUser(user);
        grant.setTrophy(trophy);
        grant.setAwardedReason(reason);
        userTrophyRepo.save(grant);
    }
}
