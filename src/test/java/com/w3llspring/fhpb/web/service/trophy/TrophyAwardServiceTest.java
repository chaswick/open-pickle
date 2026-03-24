package com.w3llspring.fhpb.web.service.trophy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.BandPositionRepository;
import com.w3llspring.fhpb.web.db.GroupTrophyRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderConfig.Type;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderSecurity;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyEvaluationScope;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.StoryModeService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrophyAwardServiceTest {

  private static final ZoneId LADDER_ZONE = ZoneId.of("America/New_York");

  @Mock private LadderSeasonRepository seasonRepository;

  @Mock private TrophyRepository trophyRepository;

  @Mock private UserTrophyRepository userTrophyRepository;

  @Mock private GroupTrophyRepository groupTrophyRepository;

  @Mock private MatchRepository matchRepository;

  @Mock private LadderStandingRepository ladderStandingRepository;

  @Mock private BandPositionRepository bandPositionRepository;

  private final TrophyArtRealizer trophyArtRealizer = new TrophyArtRealizer();

  private TrophyAwardService service;

  @BeforeEach
  void setUp() {
    service =
        new TrophyAwardService(
            seasonRepository,
            trophyRepository,
            userTrophyRepository,
            groupTrophyRepository,
            matchRepository,
            ladderStandingRepository,
            bandPositionRepository,
            trophyArtRealizer);
    lenient()
        .when(userTrophyRepository.findByUserAndTrophy(any(User.class), any(Trophy.class)))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            matchRepository.findBySeasonOrSourceSessionTargetSeasonOrderByPlayedAtAsc(
                any(LadderSeason.class), any(Long.class)))
        .thenReturn(List.of());
  }

  @Test
  void evaluateMatch_awardsPlayersForSpecificCalendarDayMetric() {
    LocalDate holiday = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(44L, holiday.minusDays(10), holiday.plusDays(10), LadderSeason.State.ACTIVE);
    Match match =
        singlesMatch(101L, season, holiday.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());
    Trophy trophy = trophy(season, "St. Patrick's Day", "matches_played_on_03_17 >= 1");

    stubSeasonMetrics(season, holiday, trophy, match);

    service.evaluateMatch(match);

    ArgumentCaptor<UserTrophy> grantCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository, times(2)).save(grantCaptor.capture());
    Set<Long> awardedUserIds =
        grantCaptor.getAllValues().stream()
            .map(UserTrophy::getUser)
            .map(User::getId)
            .collect(Collectors.toSet());
    assertThat(awardedUserIds).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void evaluateMatch_doesNotAwardPlayersWhenCalendarDayMetricDoesNotMatch() {
    LocalDate holiday = LocalDate.of(2026, 3, 17);
    LocalDate playedDate = holiday.plusDays(1);
    LadderSeason season =
        season(45L, holiday.minusDays(10), holiday.plusDays(10), LadderSeason.State.ACTIVE);
    Match match =
        singlesMatch(102L, season, playedDate.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());
    Trophy trophy = trophy(season, "St. Patrick's Day", "matches_played_on_03_17 >= 1");

    stubSeasonMetrics(season, playedDate, trophy, match);

    service.evaluateMatch(match);

    verify(userTrophyRepository, never()).save(any(UserTrophy.class));
  }

  @Test
  void evaluateMatch_usesEachPlayersTimeZoneForSpecificCalendarDayMetric() {
    LocalDate holiday = LocalDate.of(2026, 3, 17);
    Instant playedAt = Instant.parse("2026-03-17T05:30:00Z");
    LadderSeason season =
        season(46L, holiday.minusDays(10), holiday.plusDays(10), LadderSeason.State.ACTIVE);
    Match match = singlesMatch(103L, season, playedAt);
    match.getA1().setTimeZone("America/New_York");
    match.getB1().setTimeZone("America/Los_Angeles");
    Trophy trophy = trophy(season, "St. Patrick's Day", "matches_played_on_03_17 >= 1");

    stubSeasonMetrics(season, holiday, trophy, match);

    service.evaluateMatch(match);

    ArgumentCaptor<UserTrophy> grantCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository).save(grantCaptor.capture());
    assertThat(grantCaptor.getValue().getUser().getId()).isEqualTo(1L);
  }

  @Test
  void evaluateMatch_skipsSelfConfirmMatches() {
    LocalDate day = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(
            60L,
            day.minusDays(10),
            day.plusDays(10),
            LadderSeason.State.ACTIVE,
            LadderSecurity.SELF_CONFIRM);
    Match match =
        singlesMatch(108L, season, day.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());

    service.evaluateMatch(match);

    verify(trophyRepository, never())
        .findBySeasonOrderByDisplayOrderAscIdAsc(any(LadderSeason.class));
    verify(userTrophyRepository, never()).save(any(UserTrophy.class));
  }

  @Test
  void evaluateMatch_skipsPersonalRecordMatchesExcludedFromStandings() {
    LocalDate day = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(61L, day.minusDays(10), day.plusDays(10), LadderSeason.State.ACTIVE);
    Match match =
        singlesMatch(109L, season, day.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());
    match.setExcludeFromStandings(true);

    service.evaluateMatch(match);

    verify(trophyRepository, never())
        .findBySeasonOrderByDisplayOrderAscIdAsc(any(LadderSeason.class));
    verify(userTrophyRepository, never()).save(any(UserTrophy.class));
  }

  @Test
  void evaluateMatch_prefersAttachedCompetitionSeasonOverCalendarLookup() {
    LocalDate playedDate = LocalDate.of(2026, 3, 17);
    LadderSeason competitionSeason =
        season(64L, playedDate.minusWeeks(4), playedDate.plusWeeks(2), LadderSeason.State.ACTIVE);
    competitionSeason.getLadderConfig().setType(Type.COMPETITION);
    LadderSeason otherSeason =
        season(65L, playedDate.minusDays(2), playedDate.plusWeeks(1), LadderSeason.State.ACTIVE);
    Match match =
        singlesMatch(
            110L,
            competitionSeason,
            playedDate.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());
    Trophy trophy = trophy(competitionSeason, "Competition Day", "matches_played_on_03_17 >= 1");

    lenient()
        .when(
            seasonRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                    playedDate, playedDate))
        .thenReturn(Optional.of(otherSeason));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(competitionSeason))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrSourceSessionTargetSeasonOrderByPlayedAtAsc(
            competitionSeason, competitionSeason.getId()))
        .thenReturn(List.of(match));
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(competitionSeason))
        .thenReturn(List.of());
    when(bandPositionRepository.findBySeason(competitionSeason)).thenReturn(List.of());
    when(userTrophyRepository.existsByUserAndTrophy(any(User.class), eq(trophy))).thenReturn(false);

    service.evaluateMatch(match);

    verify(trophyRepository).findBySeasonOrderByDisplayOrderAscIdAsc(competitionSeason);
    verify(trophyRepository, never()).findBySeasonOrderByDisplayOrderAscIdAsc(otherSeason);
    verify(userTrophyRepository, times(2)).save(any(UserTrophy.class));
  }

  @Test
  void evaluateSeasonSweep_backfillsSpecificCalendarDayTrophyFromHistory() {
    LocalDate holiday = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(47L, holiday.minusDays(10), holiday.plusDays(10), LadderSeason.State.ACTIVE);
    Match holidayMatch =
        singlesMatch(104L, season, holiday.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());
    Trophy trophy = trophy(season, "St. Patrick's Day", "matches_played_on_03_17 >= 1");

    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(List.of(holidayMatch));
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season)).thenReturn(List.of());
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());
    when(userTrophyRepository.findByTrophy(trophy)).thenReturn(List.of());

    int awarded = service.evaluateSeasonSweep(season);

    assertThat(awarded).isEqualTo(2);
    verify(userTrophyRepository, times(2)).save(any(UserTrophy.class));
  }

  @Test
  void evaluateSeasonSweep_skipsSelfConfirmSeason() {
    LocalDate day = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(
            62L,
            day.minusDays(10),
            day.plusDays(10),
            LadderSeason.State.ACTIVE,
            LadderSecurity.SELF_CONFIRM);

    int awarded = service.evaluateSeasonSweep(season);

    assertThat(awarded).isZero();
    verify(trophyRepository, never())
        .findBySeasonOrderByDisplayOrderAscIdAsc(any(LadderSeason.class));
    verify(matchRepository, never()).findBySeasonOrderByPlayedAtAsc(any(LadderSeason.class));
    verify(userTrophyRepository, never()).save(any(UserTrophy.class));
  }

  @Test
  void evaluateSeasonSweep_includesSessionMatchesLinkedThroughTargetSeason() {
    LocalDate playedDate = LocalDate.of(2026, 3, 17);
    LadderSeason competitionSeason =
        season(66L, playedDate.minusWeeks(4), playedDate.plusWeeks(2), LadderSeason.State.ACTIVE);
    competitionSeason.getLadderConfig().setType(Type.COMPETITION);
    LadderSeason sessionSeason =
        season(67L, playedDate.minusDays(1), playedDate.plusDays(1), LadderSeason.State.ACTIVE);
    Match match =
        singlesMatch(
            111L, sessionSeason, playedDate.atStartOfDay(LADDER_ZONE).plusHours(19).toInstant());
    LadderConfig sessionConfig = new LadderConfig();
    sessionConfig.setType(Type.SESSION);
    sessionConfig.setTargetSeasonId(competitionSeason.getId());
    match.setSourceSessionConfig(sessionConfig);
    Trophy trophy = trophy(competitionSeason, "Competition Day", "matches_played_on_03_17 >= 1");

    when(seasonRepository.findByIdWithLadderConfig(competitionSeason.getId()))
        .thenReturn(Optional.of(competitionSeason));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(competitionSeason))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrSourceSessionTargetSeasonOrderByPlayedAtAsc(
            competitionSeason, competitionSeason.getId()))
        .thenReturn(List.of(match));
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(competitionSeason))
        .thenReturn(List.of());
    when(bandPositionRepository.findBySeason(competitionSeason)).thenReturn(List.of());
    when(userTrophyRepository.findByTrophy(trophy)).thenReturn(List.of());

    int awarded = service.evaluateSeasonSweep(competitionSeason);

    assertThat(awarded).isEqualTo(2);
    verify(userTrophyRepository, times(2)).save(any(UserTrophy.class));
  }

  @Test
  void evaluateMatch_allowsRepeatableTrophyToBeEarnedAcrossMultipleMatches() {
    LocalDate holiday = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(48L, holiday.minusDays(10), holiday.plusDays(10), LadderSeason.State.ACTIVE);
    Match firstMatch =
        singlesMatch(105L, season, holiday.atStartOfDay(LADDER_ZONE).plusHours(18).toInstant());
    Match secondMatch =
        singlesMatch(106L, season, holiday.atStartOfDay(LADDER_ZONE).plusHours(20).toInstant());
    Trophy trophy = trophy(season, "Thursday Grinder", "matches_played_on_03_17 >= 1");
    trophy.setRepeatable(true);

    lenient()
        .when(
            seasonRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                    holiday, holiday))
        .thenReturn(Optional.of(season));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season))
        .thenReturn(List.of(firstMatch))
        .thenReturn(List.of(firstMatch, secondMatch));
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season)).thenReturn(List.of());
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());

    service.evaluateMatch(firstMatch);
    service.evaluateMatch(secondMatch);

    verify(userTrophyRepository, times(4)).save(any(UserTrophy.class));
  }

  @Test
  void evaluateMatch_countsRepeatableStoryLaundryByCompletedCycles() {
    LocalDate day = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(50L, day.minusDays(10), day.plusDays(10), LadderSeason.State.ACTIVE);
    List<Match> matches = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      matches.add(
          singlesMatch(
              200L + i, season, day.atStartOfDay(LADDER_ZONE).plusHours(10 + i).toInstant()));
    }

    Trophy trophy = trophy(season, "Pat's Laundry", "story_laundry_loads >= 1");
    trophy.setRepeatable(true);
    trophy.setEvaluationScope(TrophyEvaluationScope.GROUP);
    trophy.setStoryModeTracker(true);
    trophy.setStoryModeKey(StoryModeService.TASK_LAUNDRY);

    lenient()
        .when(
            seasonRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                    day, day))
        .thenReturn(Optional.of(season));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(matches);
    when(groupTrophyRepository.findBySeasonAndTrophy(season, trophy)).thenReturn(Optional.empty());
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season)).thenReturn(List.of());
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());
    when(userTrophyRepository.findByTrophy(trophy)).thenReturn(List.of());

    service.evaluateMatch(matches.get(matches.size() - 1));

    ArgumentCaptor<com.w3llspring.fhpb.web.model.GroupTrophy> groupCaptor =
        ArgumentCaptor.forClass(com.w3llspring.fhpb.web.model.GroupTrophy.class);
    verify(groupTrophyRepository).save(groupCaptor.capture());
    assertThat(groupCaptor.getValue().getAwardCount()).isEqualTo(2);

    ArgumentCaptor<UserTrophy> userCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository, times(2)).save(userCaptor.capture());
    assertThat(userCaptor.getAllValues()).allMatch(grant -> grant.getAwardCount() == 2);
  }

  @Test
  void evaluateMatch_skipsStoryTrackersWhenFeatureFlagIsOff() {
    LocalDate playedDate = LocalDate.of(2026, 3, 17);
    LadderSeason season =
        season(49L, playedDate.minusDays(10), playedDate.plusDays(10), LadderSeason.State.ACTIVE);
    Match match =
        singlesMatch(107L, season, playedDate.atStartOfDay(LADDER_ZONE).plusHours(18).toInstant());
    Trophy trophy = trophy(season, "Pat gets off the couch", "story_matches_played >= 1");
    trophy.setStoryModeTracker(true);
    trophy.setEvaluationScope(com.w3llspring.fhpb.web.model.TrophyEvaluationScope.GROUP);
    ReflectionTestUtils.setField(service, "storyModeFeatureEnabled", false);

    lenient()
        .when(
            seasonRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                    playedDate, playedDate))
        .thenReturn(Optional.of(season));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(List.of(match));
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season)).thenReturn(List.of());
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());

    service.evaluateMatch(match);

    verify(groupTrophyRepository, never()).save(any());
    verify(userTrophyRepository, never()).save(any(UserTrophy.class));
  }

  @Test
  void awardSeasonFinale_skipsSelfConfirmSeason() {
    LocalDate day = LocalDate.of(2026, 4, 21);
    LadderSeason season =
        season(63L, day.minusWeeks(6), day, LadderSeason.State.ENDED, LadderSecurity.SELF_CONFIRM);
    season.setName("Casual Season");

    service.awardSeasonFinale(season);

    verify(trophyRepository, never())
        .findBySeasonOrderByDisplayOrderAscIdAsc(any(LadderSeason.class));
    verify(userTrophyRepository, never()).save(any(UserTrophy.class));
  }

  @Test
  void awardSeasonFinale_awardsCourtSovereignForPlayedGroupFirstPlaceFinish() {
    LocalDate day = LocalDate.of(2026, 4, 21);
    LadderSeason season = season(51L, day.minusWeeks(6), day, LadderSeason.State.ENDED);
    season.setName("Opening Season");

    User user1 = player(1L, "alice");
    User user2 = player(2L, "bob");
    User user3 = player(3L, "cora");
    User user4 = player(4L, "drew");
    User user5 = player(5L, "erin");
    User user6 = player(6L, "finn");

    Trophy trophy =
        trophy(
            season,
            "Court Sovereign",
            "final_played_group_rank == 1 && final_played_group_size >= 6");

    List<Match> matches =
        List.of(
            singlesMatch(
                301L, season, day.atStartOfDay(LADDER_ZONE).plusHours(9).toInstant(), user1, user2),
            singlesMatch(
                302L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(10).toInstant(),
                user1,
                user3),
            singlesMatch(
                303L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(11).toInstant(),
                user1,
                user4),
            singlesMatch(
                304L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(12).toInstant(),
                user1,
                user5),
            singlesMatch(
                305L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(13).toInstant(),
                user1,
                user6));

    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(matches);
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(
            List.of(
                standing(season, user1, 1),
                standing(season, user2, 2),
                standing(season, user3, 3),
                standing(season, user4, 4),
                standing(season, user5, 5),
                standing(season, user6, 6)));
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());

    service.awardSeasonFinale(season);

    ArgumentCaptor<UserTrophy> grantCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository).save(grantCaptor.capture());
    assertThat(grantCaptor.getValue().getUser().getId()).isEqualTo(1L);
    assertThat(grantCaptor.getValue().getTrophy().getTitle()).isEqualTo("Court Sovereign");
  }

  @Test
  void awardSeasonFinale_awardsPodiumFinishForPlayedGroupSecondPlaceFinish() {
    LocalDate day = LocalDate.of(2026, 4, 21);
    LadderSeason season = season(53L, day.minusWeeks(6), day, LadderSeason.State.ENDED);
    season.setName("Opening Season");

    User user1 = player(1L, "alice");
    User user2 = player(2L, "bob");
    User user3 = player(3L, "cora");
    User user4 = player(4L, "drew");
    User user5 = player(5L, "erin");
    User user6 = player(6L, "finn");

    Trophy trophy =
        trophy(
            season,
            "Podium Finish",
            "final_played_group_rank >= 2 && final_played_group_rank <= 3 && final_played_group_size >= 6");

    List<Match> matches =
        List.of(
            singlesMatch(
                311L, season, day.atStartOfDay(LADDER_ZONE).plusHours(9).toInstant(), user2, user1),
            singlesMatch(
                312L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(10).toInstant(),
                user2,
                user3),
            singlesMatch(
                313L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(11).toInstant(),
                user2,
                user4),
            singlesMatch(
                314L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(12).toInstant(),
                user2,
                user5),
            singlesMatch(
                315L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(13).toInstant(),
                user2,
                user6));

    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(matches);
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(
            List.of(
                standing(season, user1, 1),
                standing(season, user2, 2),
                standing(season, user3, 3),
                standing(season, user4, 4),
                standing(season, user5, 5),
                standing(season, user6, 6)));
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());

    service.awardSeasonFinale(season);

    ArgumentCaptor<UserTrophy> grantCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository).save(grantCaptor.capture());
    assertThat(grantCaptor.getValue().getUser().getId()).isEqualTo(2L);
    assertThat(grantCaptor.getValue().getTrophy().getTitle()).isEqualTo("Podium Finish");
  }

  @Test
  void awardSeasonFinale_awardsYouGotPickledForPlayedGroupLastPlaceFinish() {
    LocalDate day = LocalDate.of(2026, 4, 21);
    LadderSeason season = season(54L, day.minusWeeks(6), day, LadderSeason.State.ENDED);
    season.setName("Opening Season");

    User user1 = player(1L, "alice");
    User user2 = player(2L, "bob");
    User user3 = player(3L, "cora");
    User user4 = player(4L, "drew");
    User user5 = player(5L, "erin");
    User user6 = player(6L, "finn");

    Trophy trophy =
        trophy(
            season,
            "You got Pickled",
            "final_played_group_size >= 6 && is_final_played_group_last == 1");

    List<Match> matches =
        List.of(
            singlesMatch(
                321L, season, day.atStartOfDay(LADDER_ZONE).plusHours(9).toInstant(), user6, user1),
            singlesMatch(
                322L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(10).toInstant(),
                user6,
                user2),
            singlesMatch(
                323L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(11).toInstant(),
                user6,
                user3),
            singlesMatch(
                324L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(12).toInstant(),
                user6,
                user4),
            singlesMatch(
                325L,
                season,
                day.atStartOfDay(LADDER_ZONE).plusHours(13).toInstant(),
                user6,
                user5));

    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(matches);
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(
            List.of(
                standing(season, user1, 1),
                standing(season, user2, 2),
                standing(season, user3, 3),
                standing(season, user4, 4),
                standing(season, user5, 5),
                standing(season, user6, 6)));
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());

    service.awardSeasonFinale(season);

    ArgumentCaptor<UserTrophy> grantCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository).save(grantCaptor.capture());
    assertThat(grantCaptor.getValue().getUser().getId()).isEqualTo(6L);
    assertThat(grantCaptor.getValue().getTrophy().getTitle()).isEqualTo("You got Pickled");
  }

  @Test
  void evaluateSeasonSweep_endedSeasonAwardsLastButNotLeastToLastPlaceFinisher() {
    LocalDate day = LocalDate.of(2026, 4, 21);
    LadderSeason season = season(52L, day.minusWeeks(6), day, LadderSeason.State.ENDED);

    User user1 = player(1L, "alice");
    User user2 = player(2L, "bob");
    User user3 = player(3L, "cora");
    User user4 = player(4L, "drew");
    User user5 = player(5L, "erin");
    User user6 = player(6L, "finn");

    Trophy trophy = trophy(season, "Last but not Least", "is_final_overall_last == 1");

    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(List.of());
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season))
        .thenReturn(
            List.of(
                standing(season, user1, 1),
                standing(season, user2, 2),
                standing(season, user3, 3),
                standing(season, user4, 4),
                standing(season, user5, 5),
                standing(season, user6, 6)));
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());
    when(userTrophyRepository.findByTrophy(trophy)).thenReturn(List.of());

    int awarded = service.evaluateSeasonSweep(season);

    assertThat(awarded).isEqualTo(1);
    ArgumentCaptor<UserTrophy> grantCaptor = ArgumentCaptor.forClass(UserTrophy.class);
    verify(userTrophyRepository).save(grantCaptor.capture());
    assertThat(grantCaptor.getValue().getUser().getId()).isEqualTo(6L);
    assertThat(grantCaptor.getValue().getTrophy().getTitle()).isEqualTo("Last but not Least");
  }

  private void stubSeasonMetrics(LadderSeason season, LocalDate date, Trophy trophy, Match match) {
    lenient()
        .when(
            seasonRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                    date, date))
        .thenReturn(Optional.of(season));
    when(trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season))
        .thenReturn(List.of(trophy));
    when(matchRepository.findBySeasonOrderByPlayedAtAsc(season)).thenReturn(List.of(match));
    when(ladderStandingRepository.findBySeasonOrderByRankNoAsc(season)).thenReturn(List.of());
    when(bandPositionRepository.findBySeason(season)).thenReturn(List.of());
    when(userTrophyRepository.existsByUserAndTrophy(any(User.class), eq(trophy))).thenReturn(false);
  }

  private LadderSeason season(
      Long id, LocalDate startDate, LocalDate endDate, LadderSeason.State state) {
    return season(id, startDate, endDate, state, LadderSecurity.STANDARD);
  }

  private LadderSeason season(
      Long id,
      LocalDate startDate,
      LocalDate endDate,
      LadderSeason.State state,
      LadderSecurity security) {
    LadderSeason season = new LadderSeason();
    ReflectionTestUtils.setField(season, "id", id);
    season.setStartDate(startDate);
    season.setEndDate(endDate);
    season.setState(state);
    LadderConfig ladderConfig = new LadderConfig();
    ladderConfig.setSecurityLevel(security);
    season.setLadderConfig(ladderConfig);
    return season;
  }

  private Match singlesMatch(Long id, LadderSeason season, Instant playedAt) {
    return singlesMatch(id, season, playedAt, player(1L, "alice"), player(2L, "bob"));
  }

  private Match singlesMatch(Long id, LadderSeason season, Instant playedAt, User a1, User b1) {
    Match match = new Match();
    ReflectionTestUtils.setField(match, "id", id);
    match.setSeason(season);
    match.setPlayedAt(playedAt);
    match.setState(MatchState.CONFIRMED);
    match.setA1(a1);
    match.setB1(b1);
    match.setScoreA(11);
    match.setScoreB(7);
    return match;
  }

  private Trophy trophy(LadderSeason season, String title, String unlockExpression) {
    Trophy trophy = new Trophy();
    ReflectionTestUtils.setField(trophy, "id", 900L);
    trophy.setSeason(season);
    trophy.setTitle(title);
    trophy.setSummary(title + " summary");
    trophy.setUnlockCondition(title + " condition");
    trophy.setUnlockExpression(unlockExpression);
    return trophy;
  }

  private User player(Long id, String nickName) {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", id);
    user.setNickName(nickName);
    return user;
  }

  private com.w3llspring.fhpb.web.model.LadderStanding standing(
      LadderSeason season, User user, int rank) {
    com.w3llspring.fhpb.web.model.LadderStanding standing =
        new com.w3llspring.fhpb.web.model.LadderStanding();
    standing.setSeason(season);
    standing.setUser(user);
    standing.setDisplayName(user.getNickName());
    standing.setRank(rank);
    standing.setPoints(1000 - rank);
    return standing;
  }
}
