package com.w3llspring.fhpb.web.service.meetups;

import java.time.Instant;

public record MeetupCanceledEvent(
    Long slotId, Long ladderId, Long cancelerUserId, String ladderTitle, Instant startsAt) {}
