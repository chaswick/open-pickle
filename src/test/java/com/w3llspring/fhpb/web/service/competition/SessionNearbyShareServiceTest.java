package com.w3llspring.fhpb.web.service.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.PlayLocation;
import com.w3llspring.fhpb.web.model.PlayLocationCheckIn;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionNearbyShareServiceTest {

  @Mock private LadderConfigRepository configs;
  @Mock private LadderMembershipRepository memberships;
  @Mock private PlayLocationCheckInRepository checkIns;
  @Mock private UserRepository userRepo;
  @Mock private GroupAdministrationService groupAdministration;

  private SessionNearbyShareService service;

  @BeforeEach
  void setUp() {
    service =
        new SessionNearbyShareService(configs, memberships, checkIns, userRepo, groupAdministration);
  }

  @Test
  void enableUsingActiveCheckIn_updatesSessionLocationAndEnsuresInvite() {
    LadderConfig session = session(42L, null);
    PlayLocationCheckIn activeCheckIn = activeCheckIn(11L, 7L, "Lakeside Courts");

    when(configs.lockById(42L)).thenReturn(session);
    when(checkIns.findActiveWithLocationByUserId(eq(7L), any(Instant.class)))
        .thenReturn(List.of(activeCheckIn));
    when(groupAdministration.syncInviteAvailability(session, 7L, true))
        .thenAnswer(
            invocation -> {
              session.setInviteCode("MINT-COURT-42");
              session.setLastInviteChangeAt(Instant.now());
              return session;
            });
    when(configs.save(session)).thenReturn(session);

    SessionNearbyShareService.NearbyShareStatusView status =
        service.enableUsingActiveCheckIn(42L, 7L);

    assertThat(status.sessionId()).isEqualTo(42L);
    assertThat(status.locationId()).isEqualTo(11L);
    assertThat(status.locationName()).isEqualTo("Lakeside Courts");
    assertThat(status.sharingActive()).isTrue();
    assertThat(session.getNearbyShareLocationId()).isEqualTo(11L);
    assertThat(session.getNearbyShareLocationName()).isEqualTo("Lakeside Courts");
    verify(groupAdministration).requireAdmin(session, 7L);
    verify(groupAdministration).syncInviteAvailability(session, 7L, true);
    verify(configs).save(session);
  }

  @Test
  void enableUsingActiveCheckIn_allowsUnnamedTemporaryCheckIn() {
    LadderConfig session = session(42L, null);
    PlayLocationCheckIn activeCheckIn = activeCheckIn(11L, 7L, "");

    when(configs.lockById(42L)).thenReturn(session);
    when(checkIns.findActiveWithLocationByUserId(eq(7L), any(Instant.class)))
        .thenReturn(List.of(activeCheckIn));
    when(groupAdministration.syncInviteAvailability(session, 7L, true))
        .thenAnswer(
            invocation -> {
              session.setInviteCode("MINT-COURT-42");
              session.setLastInviteChangeAt(Instant.now());
              return session;
            });
    when(configs.save(session)).thenReturn(session);

    SessionNearbyShareService.NearbyShareStatusView status =
        service.enableUsingActiveCheckIn(42L, 7L);

    assertThat(status.locationId()).isEqualTo(11L);
    assertThat(status.locationName()).isNull();
    assertThat(status.message()).isEqualTo("Nearby sharing is on for your current court.");
    assertThat(status.sharingActive()).isTrue();
    assertThat(session.getNearbyShareLocationId()).isEqualTo(11L);
    assertThat(session.getNearbyShareLocationName()).isNull();
  }

  @Test
  void listForRequesterFromActiveCheckIn_returnsActiveNearbySessionsAtLocation() {
    PlayLocationCheckIn requesterCheckIn = activeCheckIn(11L, 9L, "Lakeside Courts");
    LadderConfig activeSession = session(42L, "MINT-COURT-42");
    activeSession.setOwnerUserId(7L);
    activeSession.setTitle("Saturday Open");
    activeSession.setNearbyShareLocationId(11L);
    activeSession.setNearbyShareLocationName("Lakeside Courts");

    LadderConfig inactiveSession = session(43L, null);
    inactiveSession.setOwnerUserId(8L);
    inactiveSession.setTitle("Should Not Show");
    inactiveSession.setNearbyShareLocationId(11L);
    inactiveSession.setNearbyShareLocationName("Lakeside Courts");

    User owner = new User();
    owner.setId(7L);
    owner.setNickName("Host");

    when(checkIns.findActiveWithLocationByUserId(eq(9L), any(Instant.class)))
        .thenReturn(List.of(requesterCheckIn));
    when(
            configs
                .findByTypeAndStatusAndNearbyShareLocationIdAndInviteCodeIsNotNullAndExpiresAtAfterOrderByUpdatedAtDesc(
                    eq(LadderConfig.Type.SESSION),
                    eq(LadderConfig.Status.ACTIVE),
                    eq(11L),
                    any(Instant.class)))
        .thenReturn(List.of(activeSession));
    when(userRepo.findAllById(List.of(7L))).thenReturn(List.of(owner));
    when(memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(new LadderMembership(), new LadderMembership()));

    SessionNearbyShareService.NearbySessionsView view =
        service.listForRequesterFromActiveCheckIn(9L);

    assertThat(view.locationName()).isEqualTo("Lakeside Courts");
    assertThat(view.sessions()).hasSize(1);
    assertThat(view.sessions().get(0).sessionId()).isEqualTo(42L);
    assertThat(view.sessions().get(0).sessionTitle()).isEqualTo("Saturday Open");
    assertThat(view.sessions().get(0).ownerDisplayName()).isEqualTo("Host");
    assertThat(view.sessions().get(0).activeMemberCount()).isEqualTo(2L);
    verify(memberships, never())
        .findByLadderConfigIdAndStateOrderByJoinedAtAsc(43L, LadderMembership.State.ACTIVE);
  }

  @Test
  void listForRequesterFromActiveCheckIn_rejectsWhenUserIsNotCheckedIn() {
    when(checkIns.findActiveWithLocationByUserId(eq(9L), any(Instant.class))).thenReturn(List.of());

    assertThatThrownBy(() -> service.listForRequesterFromActiveCheckIn(9L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Check in first");
  }

  @Test
  void listForRequesterFromActiveCheckIn_allowsUnnamedTemporaryCheckIn() {
    PlayLocationCheckIn requesterCheckIn = activeCheckIn(11L, 9L, "");
    LadderConfig activeSession = session(42L, "MINT-COURT-42");
    activeSession.setOwnerUserId(7L);
    activeSession.setTitle("Saturday Open");
    activeSession.setNearbyShareLocationId(11L);
    activeSession.setNearbyShareLocationName("Lakeside Courts");

    User owner = new User();
    owner.setId(7L);
    owner.setNickName("Host");

    when(checkIns.findActiveWithLocationByUserId(eq(9L), any(Instant.class)))
        .thenReturn(List.of(requesterCheckIn));
    when(
            configs
                .findByTypeAndStatusAndNearbyShareLocationIdAndInviteCodeIsNotNullAndExpiresAtAfterOrderByUpdatedAtDesc(
                    eq(LadderConfig.Type.SESSION),
                    eq(LadderConfig.Status.ACTIVE),
                    eq(11L),
                    any(Instant.class)))
        .thenReturn(List.of(activeSession));
    when(userRepo.findAllById(List.of(7L))).thenReturn(List.of(owner));
    when(memberships.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
            42L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(new LadderMembership()));

    SessionNearbyShareService.NearbySessionsView view =
        service.listForRequesterFromActiveCheckIn(9L);

    assertThat(view.locationName()).isNull();
    assertThat(view.sessions()).hasSize(1);
    assertThat(view.sessions().get(0).locationName()).isEqualTo("Lakeside Courts");
  }

  @Test
  void requireSessionVisibleToRequester_rejectsWhenRequesterIsAtAnotherLocation() {
    PlayLocationCheckIn requesterCheckIn = activeCheckIn(11L, 9L, "Lakeside Courts");
    LadderConfig activeSession = session(42L, "MINT-COURT-42");
    activeSession.setNearbyShareLocationId(22L);
    activeSession.setNearbyShareLocationName("Riverside Courts");

    when(checkIns.findActiveWithLocationByUserId(eq(9L), any(Instant.class)))
        .thenReturn(List.of(requesterCheckIn));
    when(configs.findById(42L)).thenReturn(java.util.Optional.of(activeSession));

    assertThatThrownBy(() -> service.requireSessionVisibleToRequester(42L, 9L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("current check-in");
  }

  private LadderConfig session(Long id, String inviteCode) {
    LadderConfig session = new LadderConfig();
    session.setId(id);
    session.setType(LadderConfig.Type.SESSION);
    session.setStatus(LadderConfig.Status.ACTIVE);
    session.setCreatedAt(Instant.now().minusSeconds(60));
    session.setExpiresAt(Instant.now().plusSeconds(3_600));
    session.setInviteCode(inviteCode);
    session.setLastInviteChangeAt(Instant.now().minusSeconds(60));
    return session;
  }

  private PlayLocationCheckIn activeCheckIn(Long locationId, Long userId, String displayName) {
    PlayLocation location = new PlayLocation();
    location.setId(locationId);

    User user = new User();
    user.setId(userId);

    PlayLocationCheckIn checkIn = new PlayLocationCheckIn();
    checkIn.setLocation(location);
    checkIn.setUser(user);
    checkIn.setDisplayName(displayName);
    checkIn.setCheckedInAt(Instant.now().minusSeconds(60));
    checkIn.setExpiresAt(Instant.now().plusSeconds(3_600));
    return checkIn;
  }
}
