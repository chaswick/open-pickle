package com.w3llspring.fhpb.web.service.meetups;

import java.time.Instant;

public record MeetupCreatedEvent(
    Long ladderId, Long creatorUserId, String ladderTitle, Instant startsAt) {}
