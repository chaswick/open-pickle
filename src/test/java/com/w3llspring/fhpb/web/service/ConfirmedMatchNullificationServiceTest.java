package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.MatchNullificationRequestRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchNullificationRequest;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfirmedMatchNullificationServiceTest {

    private MatchNullificationRequestRepository requestRepository;
    private MatchRepository matchRepository;
    private UserRepository userRepository;
    private TrackingLadderV2Service ladderV2Service;
    private ConfirmedMatchNullificationService service;

    private final Map<Long, Match> matchesById = new LinkedHashMap<>();
    private final Map<Long, User> usersById = new LinkedHashMap<>();
    private final List<MatchNullificationRequest> storedRequests = new ArrayList<>();
    private long nextRequestId = 1L;

    @BeforeEach
    void setUp() {
        requestRepository = mock(MatchNullificationRequestRepository.class);
        matchRepository = mock(MatchRepository.class);
        userRepository = mock(UserRepository.class);
        ladderV2Service = new TrackingLadderV2Service();

        configureRepositoryAnswers();

        service = new ConfirmedMatchNullificationService(
                requestRepository,
                matchRepository,
                userRepository,
                ladderV2Service);
    }

    @Test
    void first_team_request_records_pending_removal() {
        User a1 = saveUser(10L, "Alice");
        User b1 = saveUser(20L, "Bob");
        Match match = saveMatch(100L, confirmedMatch(a1, b1));

        ConfirmedMatchNullificationService.NullificationRequestResult result =
                service.requestNullification(100L, 10L, 3L);

        assertFalse(result.nullified());
        assertEquals("Removal requested.", result.message());
        assertEquals(1, storedRequests.size());
        assertEquals("A", storedRequests.get(0).getTeam());
        assertEquals(a1.getId(), storedRequests.get(0).getPlayer().getId());
        assertEquals(MatchState.CONFIRMED, match.getState());
    }

    @Test
    void opposite_team_request_nullifies_confirmed_match_and_clears_requests() {
        User a1 = saveUser(10L, "Alice");
        User b1 = saveUser(20L, "Bob");
        Match match = saveMatch(101L, confirmedMatch(a1, b1));
        addStoredRequest(match, a1, "A", Instant.now().plusSeconds(120));

        ConfirmedMatchNullificationService.NullificationRequestResult result =
                service.requestNullification(101L, 20L, 3L);

        assertTrue(result.nullified());
        assertEquals("Match nullified.", result.message());
        assertTrue(storedRequests.isEmpty());
        assertEquals(MatchState.NULLIFIED, match.getState());
        assertEquals(match.getId(), ladderV2Service.nullifiedMatchId);
    }

    @Test
    void same_team_repeat_request_is_rejected() {
        User a1 = saveUser(10L, "Alice");
        User a2 = saveUser(11L, "Amy");
        User b1 = saveUser(20L, "Bob");
        Match match = saveMatch(102L, confirmedMatch(a1, b1));
        match.setA2(a2);
        addStoredRequest(match, a1, "A", Instant.now().plusSeconds(120));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requestNullification(102L, 11L, 3L));

        assertTrue(ex.getMessage().contains("already requested"));
        assertEquals(MatchState.CONFIRMED, match.getState());
        assertEquals(1, storedRequests.size());
    }

    private void configureRepositoryAnswers() {
        when(matchRepository.findByIdWithUsersForUpdate(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(matchesById.get(invocation.getArgument(0))));
        when(matchRepository.findByIdWithUsers(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(matchesById.get(invocation.getArgument(0))));
        when(userRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
        when(requestRepository.findActiveByMatch(any(Match.class), any(Instant.class)))
                .thenAnswer(invocation -> requestsFor(invocation.getArgument(0), invocation.getArgument(1), true));
        when(requestRepository.findByMatchWithPlayer(any(Match.class)))
                .thenAnswer(invocation -> requestsFor(invocation.getArgument(0), Instant.EPOCH, false));
        when(requestRepository.save(any(MatchNullificationRequest.class)))
                .thenAnswer(invocation -> saveRequest(invocation.getArgument(0)));
        doAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            storedRequests.removeIf(request -> sameMatch(request.getMatch(), match));
            return null;
        }).when(requestRepository).deleteByMatch(any(Match.class));
        doAnswer(invocation -> {
            MatchNullificationRequest request = invocation.getArgument(0);
            storedRequests.removeIf(existing -> sameRequest(existing, request));
            return null;
        }).when(requestRepository).delete(any(MatchNullificationRequest.class));
    }

    private Match confirmedMatch(User a1, User b1) {
        Match match = new Match();
        match.setState(MatchState.CONFIRMED);
        match.setA1(a1);
        match.setB1(b1);
        ReflectionTestUtils.setField(match, "version", 3L);
        return match;
    }

    private Match saveMatch(Long id, Match match) {
        ReflectionTestUtils.setField(match, "id", id);
        matchesById.put(id, match);
        return match;
    }

    private User saveUser(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        usersById.put(id, user);
        return user;
    }

    private void addStoredRequest(Match match, User player, String team, Instant expiresAt) {
        MatchNullificationRequest request = new MatchNullificationRequest();
        ReflectionTestUtils.setField(request, "id", nextRequestId++);
        request.setMatch(match);
        request.setPlayer(player);
        request.setTeam(team);
        request.setRequestedAt(Instant.now());
        request.setExpiresAt(expiresAt);
        storedRequests.add(request);
    }

    private MatchNullificationRequest saveRequest(MatchNullificationRequest request) {
        if (request.getId() == null) {
            ReflectionTestUtils.setField(request, "id", nextRequestId++);
        }
        storedRequests.removeIf(existing -> sameRequest(existing, request));
        storedRequests.add(request);
        return request;
    }

    private List<MatchNullificationRequest> requestsFor(Match match, Instant now, boolean activeOnly) {
        return storedRequests.stream()
                .filter(request -> sameMatch(request.getMatch(), match))
                .filter(request -> !activeOnly
                        || request.getExpiresAt() == null
                        || request.getExpiresAt().isAfter(now))
                .map(this::copyRequest)
                .toList();
    }

    private MatchNullificationRequest copyRequest(MatchNullificationRequest original) {
        MatchNullificationRequest copy = new MatchNullificationRequest();
        ReflectionTestUtils.setField(copy, "id", original.getId());
        copy.setMatch(original.getMatch());
        copy.setPlayer(original.getPlayer());
        copy.setTeam(original.getTeam());
        copy.setRequestedAt(original.getRequestedAt());
        copy.setExpiresAt(original.getExpiresAt());
        return copy;
    }

    private boolean sameMatch(Match left, Match right) {
        Long leftId = left != null ? left.getId() : null;
        Long rightId = right != null ? right.getId() : null;
        return leftId != null && leftId.equals(rightId);
    }

    private boolean sameRequest(MatchNullificationRequest left, MatchNullificationRequest right) {
        Long leftId = left != null ? left.getId() : null;
        Long rightId = right != null ? right.getId() : null;
        if (leftId != null && rightId != null) {
            return leftId.equals(rightId);
        }
        return sameMatch(left != null ? left.getMatch() : null, right != null ? right.getMatch() : null)
                && java.util.Objects.equals(left != null ? left.getTeam() : null, right != null ? right.getTeam() : null);
    }

    private static final class TrackingLadderV2Service extends LadderV2Service {
        private Long nullifiedMatchId;

        private TrackingLadderV2Service() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void onMatchNullified(Match match) {
            nullifiedMatchId = match != null ? match.getId() : null;
            if (match != null) {
                match.setState(MatchState.NULLIFIED);
            }
        }
    }
}
