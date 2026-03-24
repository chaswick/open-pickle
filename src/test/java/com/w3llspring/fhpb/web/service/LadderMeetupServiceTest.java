package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupRsvpRepository;
import com.w3llspring.fhpb.web.db.LadderMeetupSlotRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMeetupRsvp;
import com.w3llspring.fhpb.web.model.LadderMeetupSlot;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.meetups.MeetupCreatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

public class LadderMeetupServiceTest {

  private LadderMeetupSlotRepository slotRepo;
  private LadderMeetupRsvpRepository rsvpRepo;
  private LadderMembershipRepository membershipRepo;
  private LadderConfigRepository ladderConfigRepo;
  private UserRepository userRepo;
  private ApplicationEventPublisher events;

  private LadderMeetupService service;

  @BeforeEach
  void setup() {
    slotRepo = mock(LadderMeetupSlotRepository.class);
    rsvpRepo = mock(LadderMeetupRsvpRepository.class);
    membershipRepo = mock(LadderMembershipRepository.class);
    ladderConfigRepo = mock(LadderConfigRepository.class);
    userRepo = mock(UserRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service =
        new LadderMeetupService(
            slotRepo, rsvpRepo, membershipRepo, ladderConfigRepo, userRepo, events);
  }

  @Test
  void createSlot_rejectsWhenNotActiveMember() {
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createSlot(99L, 10L, Instant.now().plusSeconds(3600)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not a ladder member");
  }

  @Test
  void createSlot_rejectsWhenUserAlreadyHasThreeActivePlansForLadder() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(slotRepo.countActiveUpcomingByLadderAndCreator(eq(10L), eq(99L), any())).thenReturn(3L);

    assertThatThrownBy(() -> service.createSlot(99L, 10L, Instant.now().plusSeconds(3600)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3")
        .hasMessageContaining("active plans");
  }

  @Test
  void createSlot_savesSlotAndAutoRsvpsCreatorAsIn() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);
    cfg.setTitle("Ladder Z");

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(slotRepo.countActiveUpcomingByLadderAndCreator(eq(10L), eq(99L), any())).thenReturn(0L);
    when(ladderConfigRepo.findById(10L)).thenReturn(Optional.of(cfg));

    ArgumentCaptor<LadderMeetupSlot> slotCaptor = ArgumentCaptor.forClass(LadderMeetupSlot.class);
    when(slotRepo.save(any(LadderMeetupSlot.class)))
        .thenAnswer(
            inv -> {
              LadderMeetupSlot s = inv.getArgument(0);
              ReflectionTestUtils.setField(s, "id", 123L);
              return s;
            });

    when(slotRepo.findById(123L))
        .thenAnswer(
            inv -> {
              LadderMeetupSlot s = new LadderMeetupSlot();
              ReflectionTestUtils.setField(s, "id", 123L);
              s.setLadderConfig(cfg);
              return Optional.of(s);
            });

    Instant startsAt = Instant.now().plusSeconds(3600);
    Long slotId = service.createSlot(99L, 10L, startsAt);

    assertThat(slotId).isEqualTo(123L);
    verify(slotRepo, times(1)).save(slotCaptor.capture());
    LadderMeetupSlot created = slotCaptor.getValue();
    assertThat(created.getCreatedByUserId()).isEqualTo(99L);
    assertThat(created.getStartsAt()).isEqualTo(startsAt);
    assertThat(created.getLadderConfig().getId()).isEqualTo(10L);

    ArgumentCaptor<LadderMeetupRsvp> rsvpCaptor = ArgumentCaptor.forClass(LadderMeetupRsvp.class);
    verify(rsvpRepo, times(1)).save(rsvpCaptor.capture());
    LadderMeetupRsvp rsvp = rsvpCaptor.getValue();
    assertThat(rsvp.getUserId()).isEqualTo(99L);
    assertThat(rsvp.getStatus()).isEqualTo(LadderMeetupRsvp.Status.IN);
    assertThat(rsvp.getSlot()).isNotNull();
    assertThat(rsvp.getSlot().getId()).isEqualTo(123L);

    verify(events, times(1)).publishEvent(any(MeetupCreatedEvent.class));
  }

  @Test
  void setRsvp_upsertsExistingRow() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 555L);
    slot.setLadderConfig(cfg);

    LadderMeetupRsvp existing = new LadderMeetupRsvp();
    ReflectionTestUtils.setField(existing, "id", 1L);
    existing.setSlot(slot);
    existing.setUserId(99L);
    existing.setStatus(LadderMeetupRsvp.Status.CANT);

    when(slotRepo.findById(555L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(rsvpRepo.findBySlotIdAndUserId(555L, 99L)).thenReturn(Optional.of(existing));

    service.setRsvp(99L, 555L, LadderMeetupRsvp.Status.MAYBE);

    ArgumentCaptor<LadderMeetupRsvp> rsvpCaptor = ArgumentCaptor.forClass(LadderMeetupRsvp.class);
    verify(rsvpRepo).save(rsvpCaptor.capture());
    LadderMeetupRsvp saved = rsvpCaptor.getValue();
    assertThat(saved.getId()).isEqualTo(1L);
    assertThat(saved.getStatus()).isEqualTo(LadderMeetupRsvp.Status.MAYBE);
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void setRsvp_clickingSameStatusAgainDeletesRsvp() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 555L);
    slot.setLadderConfig(cfg);

    LadderMeetupRsvp existing = new LadderMeetupRsvp();
    ReflectionTestUtils.setField(existing, "id", 1L);
    existing.setSlot(slot);
    existing.setUserId(99L);
    existing.setStatus(LadderMeetupRsvp.Status.IN);

    when(slotRepo.findById(555L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(rsvpRepo.findBySlotIdAndUserId(555L, 99L)).thenReturn(Optional.of(existing));

    service.setRsvp(99L, 555L, LadderMeetupRsvp.Status.IN);

    verify(rsvpRepo).delete(existing);
  }

  @Test
  void upcomingForUser_returnsRowsWithInMaybeNamesAndMyStatus() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);
    cfg.setTitle("My Ladder");

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 200L);
    slot.setLadderConfig(cfg);
    slot.setStartsAt(Instant.now().plusSeconds(3600));

    when(membershipRepo.findByUserIdAndState(99L, LadderMembership.State.ACTIVE))
        .thenReturn(List.of(active));
    when(slotRepo.findUpcomingForLadders(any(), any(), any())).thenReturn(List.of(slot));
    when(ladderConfigRepo.findAllById(any())).thenReturn(List.of(cfg));

    LadderMeetupRsvp a = new LadderMeetupRsvp();
    a.setSlot(slot);
    a.setUserId(1L);
    a.setStatus(LadderMeetupRsvp.Status.IN);

    LadderMeetupRsvp b = new LadderMeetupRsvp();
    b.setSlot(slot);
    b.setUserId(2L);
    b.setStatus(LadderMeetupRsvp.Status.MAYBE);

    LadderMeetupRsvp me = new LadderMeetupRsvp();
    me.setSlot(slot);
    me.setUserId(99L);
    me.setStatus(LadderMeetupRsvp.Status.IN);

    when(rsvpRepo.findBySlotIdIn(eq(List.of(200L)))).thenReturn(List.of(a, b, me));

    User u1 = new User();
    u1.setId(1L);
    u1.setNickName("Zed");
    User u2 = new User();
    u2.setId(2L);
    u2.setNickName("Amy");
    User uMe = new User();
    uMe.setId(99L);
    uMe.setNickName("Me");
    when(userRepo.findAllById(any())).thenReturn(List.of(u1, u2, uMe));

    List<LadderMeetupService.MeetupRow> rows = service.upcomingForUser(99L, 5);
    assertThat(rows).hasSize(1);

    LadderMeetupService.MeetupRow row = rows.get(0);
    assertThat(row.slotId()).isEqualTo(200L);
    assertThat(row.ladderId()).isEqualTo(10L);
    assertThat(row.ladderTitle()).isEqualTo("My Ladder");
    assertThat(row.inCount()).isEqualTo(2);
    assertThat(row.maybeCount()).isEqualTo(1);
    assertThat(row.cantCount()).isEqualTo(0);

    assertThat(row.inNames()).containsExactly("Me", "Zed");
    assertThat(row.maybeNames()).containsExactly("Amy");
    assertThat(row.myStatus()).isEqualTo(LadderMeetupRsvp.Status.IN);
  }

  @Test
  void deleteSlot_requiresCreator() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 777L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(123L);

    when(slotRepo.findById(777L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.deleteSlot(99L, 777L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not allowed");
  }

  @Test
  void deleteSlot_softCancelsWhenCreator() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 777L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(99L);

    when(slotRepo.findById(777L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(slotRepo.save(any(LadderMeetupSlot.class))).thenAnswer(inv -> inv.getArgument(0));

    service.deleteSlot(99L, 777L);

    assertThat(slot.getCanceledAt()).isNotNull();
    verify(slotRepo).save(slot);
  }

  @Test
  void updateLocation_requiresCreator() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 888L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(123L);

    when(slotRepo.findById(888L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.updateLocation(99L, 888L, "AB"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not allowed");
  }

  @Test
  void updateLocation_rejectsCanceledPlan() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 888L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(99L);
    slot.setCanceledAt(Instant.now());

    when(slotRepo.findById(888L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.updateLocation(99L, 888L, "AB"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("canceled");
  }

  @Test
  void updateLocation_clearsWhenBlank() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 888L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(99L);
    slot.setLocationCode("AB");

    when(slotRepo.findById(888L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(slotRepo.save(any(LadderMeetupSlot.class))).thenAnswer(inv -> inv.getArgument(0));

    service.updateLocation(99L, 888L, "  ");

    assertThat(slot.getLocationCode()).isNull();
    verify(slotRepo).save(slot);
  }

  @Test
  void updateLocation_setsUppercaseWhenValid() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 888L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(99L);

    when(slotRepo.findById(888L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));
    when(slotRepo.save(any(LadderMeetupSlot.class))).thenAnswer(inv -> inv.getArgument(0));

    service.updateLocation(99L, 888L, "a1");

    assertThat(slot.getLocationCode()).isEqualTo("A1");
    verify(slotRepo).save(slot);
  }

  @Test
  void updateLocation_rejectsNonAlnum() {
    LadderConfig cfg = new LadderConfig();
    cfg.setId(10L);

    LadderMembership active = new LadderMembership();
    active.setUserId(99L);
    active.setLadderConfig(cfg);
    active.setState(LadderMembership.State.ACTIVE);

    LadderMeetupSlot slot = new LadderMeetupSlot();
    ReflectionTestUtils.setField(slot, "id", 888L);
    slot.setLadderConfig(cfg);
    slot.setCreatedByUserId(99L);

    when(slotRepo.findById(888L)).thenReturn(Optional.of(slot));
    when(membershipRepo.findByLadderConfigIdAndUserId(10L, 99L)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.updateLocation(99L, 888L, "A!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("letters/numbers");
  }
}
