package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.PlayLocationAliasRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.PlayLocationRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.PlayLocation;
import com.w3llspring.fhpb.web.model.PlayLocationAlias;
import com.w3llspring.fhpb.web.model.PlayLocationCheckIn;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayLocationServiceTest {

  @Mock private PlayLocationRepository playLocationRepository;

  @Mock private PlayLocationAliasRepository playLocationAliasRepository;

  @Mock private PlayLocationCheckInRepository playLocationCheckInRepository;

  @Mock private LadderMembershipRepository ladderMembershipRepository;

  @Mock private UserRepository userRepository;

  @Mock private DisplayNameModerationService moderationService;

  private PlayLocationService service;

  @BeforeEach
  void setUp() {
    service =
        new PlayLocationService(
            playLocationRepository,
            playLocationAliasRepository,
            playLocationCheckInRepository,
            ladderMembershipRepository,
            userRepository,
            moderationService,
            180,
            120d,
            2);
    Mockito.lenient()
        .when(playLocationCheckInRepository.findTopByUser_IdOrderByCheckedInAtDescIdDesc(anyLong()))
        .thenReturn(Optional.empty());
    Mockito.lenient()
        .when(
            playLocationCheckInRepository.countByUser_IdAndCheckedInAtGreaterThanEqual(
                anyLong(), any(Instant.class)))
        .thenReturn(0L);
    Mockito.lenient()
        .when(
            playLocationRepository.countByCreatedBy_IdAndCreatedAtGreaterThanEqual(
                anyLong(), any(Instant.class)))
        .thenReturn(0L);
  }

  @Test
  void resolveCheckInAutoChecksInForKnownUserLocation() {
    PlayLocation location = location(11L, 27.0d, -82.0d);
    PlayLocationAlias alias = alias(location, user(1L), "Lakeside Courts", "lakesidecourts", 3);
    alias.setLastUsedAt(Instant.parse("2026-03-15T18:00:00Z"));

    when(playLocationRepository.findWithinBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(location));
    when(playLocationAliasRepository
            .findByLocation_IdAndUser_IdOrderByUsageCountDescLastUsedAtDescIdAsc(11L, 1L))
        .thenReturn(List.of(alias));
    when(playLocationCheckInRepository.findActiveWithLocationByUserId(eq(1L), any(Instant.class)))
        .thenReturn(List.of());
    when(playLocationAliasRepository.save(any(PlayLocationAlias.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.getReferenceById(1L)).thenReturn(user(1L));

    PlayLocationService.ResolveOutcome outcome = service.resolveCheckIn(1L, 27.0d, -82.0d);

    assertThat(outcome.getStatus()).isEqualTo("checked_in");
    assertThat(outcome.getMessage()).contains("Lakeside Courts");
    verify(playLocationAliasRepository).save(alias);
    verify(playLocationCheckInRepository).expireActiveByUserId(eq(1L), any(Instant.class));
    verify(playLocationCheckInRepository).save(any(PlayLocationCheckIn.class));
  }

  @Test
  void resolveCheckInReturnsTopSuggestionsForNearbyUnknownLocation() {
    PlayLocation location = location(11L, 27.0d, -82.0d);
    PlayLocationAlias first = alias(location, user(2L), "Lakeside", "lakeside", 4);
    PlayLocationAlias second = alias(location, user(3L), "LakesideCourts", "lakesidecourts", 2);
    PlayLocationAlias third = alias(location, user(4L), "Lithia Courts", "lithiacourts", 3);

    when(playLocationRepository.findWithinBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(location));
    when(playLocationAliasRepository
            .findByLocation_IdAndUser_IdOrderByUsageCountDescLastUsedAtDescIdAsc(11L, 1L))
        .thenReturn(List.of());
    when(playLocationAliasRepository.findOtherUsersByLocationId(11L, 1L))
        .thenReturn(List.of(first, second, third));

    PlayLocationService.ResolveOutcome outcome = service.resolveCheckIn(1L, 27.0d, -82.0d);

    assertThat(outcome.getStatus()).isEqualTo("choose_name");
    assertThat(outcome.getLocationId()).isEqualTo(11L);
    assertThat(outcome.getSuggestions()).hasSize(2);
    assertThat(outcome.getSuggestions())
        .extracting(PlayLocationService.NameSuggestion::getName)
        .containsAnyOf("Lakeside", "LakesideCourts");
    assertThat(outcome.getSuggestions())
        .extracting(PlayLocationService.NameSuggestion::getName)
        .contains("Lithia Courts");
    verify(playLocationCheckInRepository, never()).save(any(PlayLocationCheckIn.class));
  }

  @Test
  void completeCheckInCreatesNewLocationWhenNothingNearbyExists() {
    User userReference = user(1L);
    when(playLocationRepository.findWithinBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    when(moderationService.explainViolation("New Courts")).thenReturn(Optional.empty());
    when(playLocationCheckInRepository.findActiveWithLocationByUserId(eq(1L), any(Instant.class)))
        .thenReturn(List.of());
    when(userRepository.getReferenceById(1L)).thenReturn(userReference);
    when(playLocationRepository.save(any(PlayLocation.class)))
        .thenAnswer(
            invocation -> {
              PlayLocation saved = invocation.getArgument(0);
              saved.setId(88L);
              return saved;
            });
    when(playLocationAliasRepository.findByLocation_IdAndUser_IdAndNormalizedName(
            88L, 1L, "newcourts"))
        .thenReturn(Optional.empty());
    when(playLocationAliasRepository.save(any(PlayLocationAlias.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PlayLocationService.CheckInOutcome outcome =
        service.completeCheckIn(
            1L,
            new PlayLocationService.CompleteCheckInCommand(
                27.0d, -82.0d, null, null, "New Courts"));

    assertThat(outcome.getMessage()).contains("New Courts");

    ArgumentCaptor<PlayLocationAlias> aliasCaptor =
        ArgumentCaptor.forClass(PlayLocationAlias.class);
    verify(playLocationAliasRepository).save(aliasCaptor.capture());
    assertThat(aliasCaptor.getValue().getDisplayName()).isEqualTo("New Courts");
    assertThat(aliasCaptor.getValue().getNormalizedName()).isEqualTo("newcourts");
    verify(playLocationCheckInRepository).save(any(PlayLocationCheckIn.class));
  }

  @Test
  void completeCheckInRejectsNamesThatNormalizeToNothing() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.completeCheckIn(
                    1L,
                    new PlayLocationService.CompleteCheckInCommand(
                        27.0d, -82.0d, null, null, "---")));

    assertThat(error.getMessage()).isEqualTo("Location name must include letters or numbers.");
    verify(playLocationRepository, never()).save(any(PlayLocation.class));
  }

  @Test
  void resolveCheckInUsesUpdatedMessageWhenRefreshingSameLocation() {
    PlayLocation location = location(11L, 27.0d, -82.0d);
    PlayLocationAlias alias = alias(location, user(1L), "Lakeside Courts", "lakesidecourts", 3);
    PlayLocationCheckIn activeCheckIn = activeCheckIn(location, user(1L), "Lakeside Courts");

    when(playLocationRepository.findWithinBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(location));
    when(playLocationAliasRepository
            .findByLocation_IdAndUser_IdOrderByUsageCountDescLastUsedAtDescIdAsc(11L, 1L))
        .thenReturn(List.of(alias));
    when(playLocationCheckInRepository.findActiveWithLocationByUserId(eq(1L), any(Instant.class)))
        .thenReturn(List.of(activeCheckIn));
    when(playLocationAliasRepository.save(any(PlayLocationAlias.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.getReferenceById(1L)).thenReturn(user(1L));

    PlayLocationService.ResolveOutcome outcome = service.resolveCheckIn(1L, 27.0d, -82.0d);

    assertThat(outcome.getMessage())
        .isEqualTo("Updated your check-in at Lakeside Courts for the next 3 hours.");
    verify(playLocationCheckInRepository).expireActiveByUserId(eq(1L), any(Instant.class));
    verify(playLocationCheckInRepository).save(any(PlayLocationCheckIn.class));
  }

  @Test
  void completeCheckInUsesMovedMessageWhenReplacingDifferentLocation() {
    PlayLocation priorLocation = location(11L, 27.0d, -82.0d);
    PlayLocation nextLocation = location(88L, 27.1d, -82.1d);
    PlayLocationCheckIn activeCheckIn = activeCheckIn(priorLocation, user(1L), "Lakeside Courts");
    User userReference = user(1L);

    when(playLocationRepository.findWithinBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    when(moderationService.explainViolation("Maple Street Courts")).thenReturn(Optional.empty());
    when(playLocationCheckInRepository.findActiveWithLocationByUserId(eq(1L), any(Instant.class)))
        .thenReturn(List.of(activeCheckIn));
    when(userRepository.getReferenceById(1L)).thenReturn(userReference);
    when(playLocationRepository.save(any(PlayLocation.class)))
        .thenAnswer(
            invocation -> {
              PlayLocation saved = invocation.getArgument(0);
              saved.setId(nextLocation.getId());
              return saved;
            });
    when(playLocationAliasRepository.findByLocation_IdAndUser_IdAndNormalizedName(
            88L, 1L, "maplestreetcourts"))
        .thenReturn(Optional.empty());
    when(playLocationAliasRepository.save(any(PlayLocationAlias.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PlayLocationService.CheckInOutcome outcome =
        service.completeCheckIn(
            1L,
            new PlayLocationService.CompleteCheckInCommand(
                27.1d, -82.1d, null, null, "Maple Street Courts"));

    assertThat(outcome.getMessage())
        .isEqualTo("Moved your check-in to Maple Street Courts for the next 3 hours.");
    verify(playLocationCheckInRepository).expireActiveByUserId(eq(1L), any(Instant.class));
    verify(playLocationCheckInRepository).save(any(PlayLocationCheckIn.class));
  }

  @Test
  void completeCheckInRejectsWhenCooldownIsActive() {
    PlayLocationCheckIn latest =
        activeCheckIn(location(11L, 27.0d, -82.0d), user(1L), "Lakeside Courts");
    latest.setCheckedInAt(Instant.now().minusSeconds(5));
    when(playLocationCheckInRepository.findTopByUser_IdOrderByCheckedInAtDescIdDesc(1L))
        .thenReturn(Optional.of(latest));

    PlayLocationService.CheckInRateLimitException error =
        assertThrows(
            PlayLocationService.CheckInRateLimitException.class,
            () ->
                service.completeCheckIn(
                    1L,
                    new PlayLocationService.CompleteCheckInCommand(
                        27.0d, -82.0d, null, null, "New Courts")));

    assertThat(error.getMessage()).contains("Please wait");
    verify(playLocationRepository, never()).save(any(PlayLocation.class));
  }

  @Test
  void completeCheckInRejectsWhenNewLocationQuotaIsReached() {
    when(playLocationRepository.findWithinBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    when(playLocationRepository.countByCreatedBy_IdAndCreatedAtGreaterThanEqual(
            eq(1L), any(Instant.class)))
        .thenReturn(5L);
    when(moderationService.explainViolation("New Courts")).thenReturn(Optional.empty());

    PlayLocationService.CheckInRateLimitException error =
        assertThrows(
            PlayLocationService.CheckInRateLimitException.class,
            () ->
                service.completeCheckIn(
                    1L,
                    new PlayLocationService.CompleteCheckInCommand(
                        27.0d, -82.0d, null, null, "New Courts")));

    assertThat(error.getMessage()).contains("New location limit reached");
    verify(playLocationRepository, never()).save(any(PlayLocation.class));
  }

  @Test
  void buildPageIncludesOverallAndPrivateGroupCounts() {
    User currentUser = user(1L);
    PlayLocation location = location(11L, 27.0d, -82.0d);
    PlayLocationAlias alias = alias(location, currentUser, "Lakeside Courts", "lakesidecourts", 3);
    PlayLocationCheckIn activeCheckIn = activeCheckIn(location, currentUser, "Lakeside Courts");

    when(playLocationAliasRepository.findAllWithLocationByUserId(1L)).thenReturn(List.of(alias));
    when(playLocationCheckInRepository.findActiveWithLocationByUserId(eq(1L), any(Instant.class)))
        .thenReturn(List.of(activeCheckIn));
    when(playLocationCheckInRepository.countActiveUsersByLocationIds(
            anyCollection(), any(Instant.class)))
        .thenReturn(List.of(activeCountRow(11L, 5L)));
    when(ladderMembershipRepository.findDistinctPeerUserIdsForPrivateGroups(
            1L,
            LadderMembership.State.ACTIVE,
            LadderConfig.Type.COMPETITION,
            LadderConfig.Type.SESSION))
        .thenReturn(List.of(2L, 3L));
    when(playLocationCheckInRepository.countActiveUsersByLocationIdsAndUserIds(
            anyCollection(), anyCollection(), any(Instant.class)))
        .thenReturn(List.of(activeCountRow(11L, 2L)));

    PlayLocationService.CheckInPageView page = service.buildPage(currentUser);

    assertThat(page.getActiveCheckIn()).isNotNull();
    assertThat(page.getActiveCheckIn().getActiveUserCount()).isEqualTo(5L);
    assertThat(page.getActiveCheckIn().getPrivateGroupActiveUserCount()).isEqualTo(2L);
    assertThat(page.getLocations())
        .singleElement()
        .satisfies(
            locationView -> {
              assertThat(locationView.getActiveUserCount()).isEqualTo(5L);
              assertThat(locationView.getPrivateGroupActiveUserCount()).isEqualTo(2L);
            });
  }

  private PlayLocationCheckInRepository.ActiveLocationCount activeCountRow(
      Long locationId, long userCount) {
    return new PlayLocationCheckInRepository.ActiveLocationCount() {
      @Override
      public Long getLocationId() {
        return locationId;
      }

      @Override
      public long getUserCount() {
        return userCount;
      }
    };
  }

  private PlayLocation location(Long id, double latitude, double longitude) {
    PlayLocation location = new PlayLocation();
    location.setId(id);
    location.setLatitude(latitude);
    location.setLongitude(longitude);
    return location;
  }

  private PlayLocationAlias alias(
      PlayLocation location, User user, String displayName, String normalizedName, int usageCount) {
    PlayLocationAlias alias = new PlayLocationAlias();
    alias.setLocation(location);
    alias.setUser(user);
    alias.setDisplayName(displayName);
    alias.setNormalizedName(normalizedName);
    alias.setUsageCount(usageCount);
    alias.setLastUsedAt(Instant.parse("2026-03-15T18:00:00Z"));
    return alias;
  }

  private PlayLocationCheckIn activeCheckIn(PlayLocation location, User user, String displayName) {
    PlayLocationCheckIn checkIn = new PlayLocationCheckIn();
    checkIn.setLocation(location);
    checkIn.setUser(user);
    checkIn.setDisplayName(displayName);
    checkIn.setCheckedInAt(Instant.parse("2026-03-15T18:00:00Z"));
    checkIn.setExpiresAt(Instant.parse("2026-03-15T21:00:00Z"));
    return checkIn;
  }

  private User user(long id) {
    User user = new User();
    user.setId(id);
    user.setNickName("User" + id);
    return user;
  }
}
