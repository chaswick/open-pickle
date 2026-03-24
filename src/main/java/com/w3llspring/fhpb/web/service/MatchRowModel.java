package com.w3llspring.fhpb.web.service;

import java.util.Map;
import java.util.Set;

/**
 * Lightweight DTO containing model data consumed by the matchRow fragments.
 */
public class MatchRowModel {
    private final Set<Long> confirmableMatchIds;
    private final Map<Long, String> confirmerByMatchId;
    private final Map<Long, Boolean> casualAutoConfirmedByMatchId;
    private final Map<Long, Boolean> pendingByMatchId;
    private final Map<Long, Boolean> waitingOnOpponentByMatchId;
    private final Map<Long, Boolean> editableByMatchId;
    private final Map<Long, Boolean> deletableByMatchId;
    private final Map<Long, Boolean> nullifyRequestableByMatchId;
    private final Map<Long, Boolean> nullifyApprovableByMatchId;
    private final Map<Long, Boolean> nullifyWaitingOnOpponentByMatchId;

    public MatchRowModel(Set<Long> confirmableMatchIds,
                         Map<Long, String> confirmerByMatchId,
                         Map<Long, Boolean> casualAutoConfirmedByMatchId,
                         Map<Long, Boolean> pendingByMatchId,
                         Map<Long, Boolean> waitingOnOpponentByMatchId,
                         Map<Long, Boolean> editableByMatchId,
                         Map<Long, Boolean> deletableByMatchId) {
        this(confirmableMatchIds,
                confirmerByMatchId,
                casualAutoConfirmedByMatchId,
                pendingByMatchId,
                waitingOnOpponentByMatchId,
                editableByMatchId,
                deletableByMatchId,
                Map.of(),
                Map.of(),
                Map.of());
    }

    public MatchRowModel(Set<Long> confirmableMatchIds,
                         Map<Long, String> confirmerByMatchId,
                         Map<Long, Boolean> casualAutoConfirmedByMatchId,
                         Map<Long, Boolean> pendingByMatchId,
                         Map<Long, Boolean> waitingOnOpponentByMatchId,
                         Map<Long, Boolean> editableByMatchId,
                         Map<Long, Boolean> deletableByMatchId,
                         Map<Long, Boolean> nullifyRequestableByMatchId,
                         Map<Long, Boolean> nullifyApprovableByMatchId,
                         Map<Long, Boolean> nullifyWaitingOnOpponentByMatchId) {
        this.confirmableMatchIds = confirmableMatchIds;
        this.confirmerByMatchId = confirmerByMatchId;
        this.casualAutoConfirmedByMatchId = casualAutoConfirmedByMatchId;
        this.pendingByMatchId = pendingByMatchId;
        this.waitingOnOpponentByMatchId = waitingOnOpponentByMatchId;
        this.editableByMatchId = editableByMatchId;
        this.deletableByMatchId = deletableByMatchId;
        this.nullifyRequestableByMatchId = nullifyRequestableByMatchId;
        this.nullifyApprovableByMatchId = nullifyApprovableByMatchId;
        this.nullifyWaitingOnOpponentByMatchId = nullifyWaitingOnOpponentByMatchId;
    }

    public Set<Long> getConfirmableMatchIds() {
        return confirmableMatchIds;
    }

    public Map<Long, String> getConfirmerByMatchId() {
        return confirmerByMatchId;
    }

    public Map<Long, Boolean> getCasualAutoConfirmedByMatchId() {
        return casualAutoConfirmedByMatchId;
    }

    public Map<Long, Boolean> getPendingByMatchId() {
        return pendingByMatchId;
    }

    public Map<Long, Boolean> getWaitingOnOpponentByMatchId() {
        return waitingOnOpponentByMatchId;
    }

    public Map<Long, Boolean> getEditableByMatchId() {
        return editableByMatchId;
    }

    public Map<Long, Boolean> getDeletableByMatchId() {
        return deletableByMatchId;
    }

    public Map<Long, Boolean> getNullifyRequestableByMatchId() {
        return nullifyRequestableByMatchId;
    }

    public Map<Long, Boolean> getNullifyApprovableByMatchId() {
        return nullifyApprovableByMatchId;
    }

    public Map<Long, Boolean> getNullifyWaitingOnOpponentByMatchId() {
        return nullifyWaitingOnOpponentByMatchId;
    }
}
