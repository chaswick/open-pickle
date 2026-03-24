package com.w3llspring.fhpb.web.service.meetups;

import java.time.Instant;

public record MeetupsEmailDigestRow(
    Long slotId, String ladderTitle, String createdByName, Instant startsAt) {}
