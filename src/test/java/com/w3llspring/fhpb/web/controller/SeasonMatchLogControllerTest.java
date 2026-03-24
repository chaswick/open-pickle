package com.w3llspring.fhpb.web.controller;

import com.w3llspring.fhpb.web.controller.match.SeasonMatchLogController;
import com.w3llspring.fhpb.web.db.MatchConfirmationRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.LadderAccessService;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import com.w3llspring.fhpb.web.service.MatchRowModel;
import com.w3llspring.fhpb.web.service.matchentry.MatchEntryContextService;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;

import com.w3llspring.fhpb.web.service.MatchConfirmationService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeasonMatchLogControllerTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchConfirmationRepository matchConfirmationRepository;

    @Mock
    private MatchConfirmationService matchConfirmationService;

    private MockMvc mockMvc;
    private LadderSeason requiredSeason;

    @BeforeEach
    void setUp() {
    LadderAccessService ladderAccessService = new LadderAccessService(null, null) {
        @Override
        public LadderSeason requireSeason(Long seasonId) {
            return requiredSeason;
        }

        @Override
        public void requireMember(Long seasonId, User user) {
        }
    };
    LadderV2Service ladderV2Service = new LadderV2Service(null, null, null, null, null, null, null, null, null,
            null, null, null, null, null);
    com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder =
            new com.w3llspring.fhpb.web.service.MatchRowModelBuilder(null, null, null) {
        @Override
        public MatchRowModel buildFor(User viewer, List<Match> matches) {
            return new MatchRowModel(Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    };
    CourtNameService courtNameService =
            new CourtNameService() {
        @Override
        public Map<Long, Set<String>> gatherCourtNamesForUsers(Collection<Long> userIds, Long ladderId) {
            return Map.of();
        }

        @Override
        public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
            return Set.of();
        }
    };
    SeasonMatchLogController controller = new SeasonMatchLogController(matchRepository, userRepository,
        ladderAccessService, ladderV2Service, matchRowModelBuilder, new MatchEntryContextService(courtNameService));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void exportCsvReturnsAttachment() throws Exception {
        Long seasonId = 7L;
        LadderSeason season = new LadderSeason();

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 12L);
        match.setPlayedAt(Instant.parse("2023-09-01T14:30:00Z"));
        match.setScoreA(11);
        match.setScoreB(7);
        match.setState(MatchState.CONFIRMED);

        User p1 = new User();
        p1.setNickName("Alice");
        p1.setPublicCode("PB-alic");
        match.setA1(p1);

        User p2 = new User();
        p2.setNickName("Bob, Jr.");
        p2.setPublicCode("PB-bobj");
        match.setA2(p2);

        User p3 = new User();
        p3.setNickName("Charlie");
        p3.setPublicCode("PB-char");
        match.setB1(p3);

        match.setA1Guest(false);
        match.setA2Guest(false);
        match.setB1Guest(false);
        match.setB2Guest(true);

        User cosigner = new User();
        cosigner.setNickName("Dana");
        cosigner.setPublicCode("PB-dana");
        match.setCosignedBy(cosigner);

        requiredSeason = season;
        when(matchRepository.findBySeasonOrderByPlayedAtDescIncludingNullified(season)).thenReturn(List.of(match));

        String responseBody = mockMvc.perform(get("/seasons/{seasonId}/matches/export", seasonId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("season-" + seasonId + "-match-log.csv")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .contains("match_id,played_at_eastern,a1,a1_code")
                .contains("12,2023-09-01 10:30 EDT,Alice,PB-alic,\"Bob, Jr.\",PB-bobj,Charlie,PB-char,Guest,,11,7,CONFIRMED,Dana,PB-dana");
    }

    @Test
    void exportCsvNeutralizesSpreadsheetFormulas() throws Exception {
        Long seasonId = 8L;
        LadderSeason season = new LadderSeason();

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 13L);
        match.setPlayedAt(Instant.parse("2023-09-01T14:30:00Z"));
        match.setScoreA(11);
        match.setScoreB(7);
        match.setState(MatchState.CONFIRMED);

        User p1 = new User();
        p1.setNickName("=cmd|' /C calc'!A0");
        p1.setPublicCode("PB-form");
        match.setA1(p1);
        match.setA1Guest(false);
        match.setA2Guest(true);
        match.setB1Guest(true);
        match.setB2Guest(true);

        requiredSeason = season;
        when(matchRepository.findBySeasonOrderByPlayedAtDescIncludingNullified(season)).thenReturn(List.of(match));

        String responseBody = mockMvc.perform(get("/seasons/{seasonId}/matches/export", seasonId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .contains("'=cmd|' /C calc'!A0")
                .contains("PB-form");
    }

    @Test
    void recentLogUsesCompetitionBackLinkAndHidesExport() {
        Long seasonId = 7L;
        LadderSeason season = new LadderSeason();
        LadderConfig ladder = new LadderConfig();
        ladder.setId(22L);
        ladder.setTitle("Global Competition");
        season.setLadderConfig(ladder);
        season.setName("Opening Season");
        season.setStartDate(LocalDate.of(2026, 3, 9));
        season.setEndDate(LocalDate.of(2026, 4, 20));
        ReflectionTestUtils.setField(season, "id", seasonId);
        requiredSeason = season;

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 12L);
        match.setSeason(season);
        match.setPlayedAt(Instant.parse("2023-09-01T14:30:00Z"));
        match.setScoreA(11);
        match.setScoreB(7);
        match.setState(MatchState.CONFIRMED);

        when(matchRepository.findBySeasonOrderByPlayedAtDescIncludingNullified(eq(season), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(match),
                        invocation.getArgument(1),
                        75));

        LadderAccessService ladderAccessService = new LadderAccessService(null, null) {
            @Override
            public LadderSeason requireSeason(Long requestedSeasonId) {
                return requiredSeason;
            }

            @Override
            public void requireMember(Long requestedSeasonId, User user) {
            }

            @Override
            public boolean isSeasonAdmin(Long requestedSeasonId, User user) {
                return false;
            }
        };
        LadderV2Service ladderV2Service = new LadderV2Service(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder =
                new com.w3llspring.fhpb.web.service.MatchRowModelBuilder(null, null, null) {
            @Override
            public MatchRowModel buildFor(User viewer, List<Match> matches) {
                return new MatchRowModel(Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
            }
        };
        CourtNameService courtNameService =
                new CourtNameService() {
            @Override
            public Map<Long, Set<String>> gatherCourtNamesForUsers(Collection<Long> userIds, Long ladderId) {
                return Map.of();
            }

            @Override
            public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
                return Set.of();
            }
        };
        SeasonMatchLogController controller = new SeasonMatchLogController(
                matchRepository,
                userRepository,
                ladderAccessService,
                ladderV2Service,
                matchRowModelBuilder,
                new MatchEntryContextService(courtNameService));

        ExtendedModelMap model = new ExtendedModelMap();
        User viewer = new User();
        viewer.setId(44L);
        viewer.setNickName("Viewer");
        String view = controller.recentLog(
                seasonId,
                null,
                model,
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        assertThat(view).isEqualTo("auth/seasonMatchLog");
        assertThat(model.get("recentOnly")).isEqualTo(true);
        assertThat(model.get("showExport")).isEqualTo(false);
        assertThat(model.get("backHref")).isEqualTo("/competition");
        assertThat(model.get("backLabel")).isEqualTo("Back to Competition");
        assertThat(model.get("pageSize")).isEqualTo(50);
        assertThat(model.get("matchLogSubtitle")).isEqualTo("Most recent 50 matches from this season.");
        assertThat(model.get("seasonName")).isEqualTo("Opening Season");
        assertThat(model.get("seasonDateRange")).isEqualTo("Mar 9, 2026 - Apr 20, 2026");
    }

    @Test
    void recentLogUsesSessionBackLinkWhenProvided() {
        Long seasonId = 8L;
        LadderSeason season = new LadderSeason();
        LadderConfig ladder = new LadderConfig();
        ladder.setId(23L);
        ladder.setTitle("Global Competition");
        season.setLadderConfig(ladder);
        season.setName("Opening Season");
        season.setStartDate(LocalDate.of(2026, 3, 9));
        season.setEndDate(LocalDate.of(2026, 4, 20));
        ReflectionTestUtils.setField(season, "id", seasonId);
        requiredSeason = season;

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 13L);
        match.setSeason(season);
        match.setPlayedAt(Instant.parse("2023-09-01T14:30:00Z"));
        match.setScoreA(11);
        match.setScoreB(7);
        match.setState(MatchState.CONFIRMED);

        when(matchRepository.findBySeasonOrderByPlayedAtDescIncludingNullified(eq(season), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(match),
                        invocation.getArgument(1),
                        75));

        LadderAccessService ladderAccessService = new LadderAccessService(null, null) {
            @Override
            public LadderSeason requireSeason(Long requestedSeasonId) {
                return requiredSeason;
            }

            @Override
            public void requireMember(Long requestedSeasonId, User user) {
            }

            @Override
            public boolean isSeasonAdmin(Long requestedSeasonId, User user) {
                return false;
            }
        };
        LadderV2Service ladderV2Service = new LadderV2Service(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder =
                new com.w3llspring.fhpb.web.service.MatchRowModelBuilder(null, null, null) {
                    @Override
                    public MatchRowModel buildFor(User viewer, List<Match> matches) {
                        return new MatchRowModel(Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
                    }
                };
        CourtNameService courtNameService =
                new CourtNameService() {
                    @Override
                    public Map<Long, Set<String>> gatherCourtNamesForUsers(Collection<Long> userIds, Long ladderId) {
                        return Map.of();
                    }

                    @Override
                    public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
                        return Set.of();
                    }
                };
        SeasonMatchLogController controller = new SeasonMatchLogController(
                matchRepository,
                userRepository,
                ladderAccessService,
                ladderV2Service,
                matchRowModelBuilder,
                new MatchEntryContextService(courtNameService));

        ExtendedModelMap model = new ExtendedModelMap();
        User viewer = new User();
        viewer.setId(45L);
        viewer.setNickName("Viewer");
        String view = controller.recentLog(
                seasonId,
                "/groups/23",
                model,
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        assertThat(view).isEqualTo("auth/seasonMatchLog");
        assertThat(model.get("backHref")).isEqualTo("/groups/23");
        assertThat(model.get("backLabel")).isEqualTo("Back to Session");
    }

    @Test
    void recentLogIncludesNullifiedMatchesInHistory() {
        Long seasonId = 9L;
        LadderSeason season = new LadderSeason();
        LadderConfig ladder = new LadderConfig();
        ladder.setId(24L);
        ladder.setTitle("Global Competition");
        season.setLadderConfig(ladder);
        season.setName("Opening Season");
        season.setStartDate(LocalDate.of(2026, 3, 9));
        season.setEndDate(LocalDate.of(2026, 4, 20));
        ReflectionTestUtils.setField(season, "id", seasonId);
        requiredSeason = season;

        Match match = new Match();
        ReflectionTestUtils.setField(match, "id", 14L);
        match.setSeason(season);
        match.setPlayedAt(Instant.parse("2026-03-10T14:30:00Z"));
        match.setScoreA(11);
        match.setScoreB(7);
        match.setState(MatchState.NULLIFIED);

        when(matchRepository.findBySeasonOrderByPlayedAtDescIncludingNullified(eq(season), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(match),
                        invocation.getArgument(1),
                        1));

        LadderAccessService ladderAccessService = new LadderAccessService(null, null) {
            @Override
            public LadderSeason requireSeason(Long requestedSeasonId) {
                return requiredSeason;
            }

            @Override
            public void requireMember(Long requestedSeasonId, User user) {
            }

            @Override
            public boolean isSeasonAdmin(Long requestedSeasonId, User user) {
                return false;
            }
        };
        LadderV2Service ladderV2Service = new LadderV2Service(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder =
                new com.w3llspring.fhpb.web.service.MatchRowModelBuilder(null, null, null) {
                    @Override
                    public MatchRowModel buildFor(User viewer, List<Match> matches) {
                        return new MatchRowModel(Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
                    }
                };
        CourtNameService courtNameService = new CourtNameService() {
            @Override
            public Map<Long, Set<String>> gatherCourtNamesForUsers(Collection<Long> userIds, Long ladderId) {
                return Map.of();
            }

            @Override
            public Set<String> gatherCourtNamesForUser(long userId, Long ladderConfigId) {
                return Set.of();
            }
        };
        SeasonMatchLogController controller = new SeasonMatchLogController(
                matchRepository,
                userRepository,
                ladderAccessService,
                ladderV2Service,
                matchRowModelBuilder,
                new MatchEntryContextService(courtNameService));

        ExtendedModelMap model = new ExtendedModelMap();
        User viewer = new User();
        viewer.setId(46L);
        viewer.setNickName("Viewer");
        controller.recentLog(
                seasonId,
                null,
                model,
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(viewer), null, List.of()));

        @SuppressWarnings("unchecked")
        List<SeasonMatchLogController.DayGroup> groups =
                (List<SeasonMatchLogController.DayGroup>) model.get("groups");
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).rows).hasSize(1);
        assertThat(groups.get(0).rows.get(0).state).isEqualTo(MatchState.NULLIFIED);
    }
}
