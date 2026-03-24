package com.w3llspring.fhpb.web.service.jobs.maintenance;

import com.w3llspring.fhpb.web.logging.BackgroundJobLogContext;
import com.w3llspring.fhpb.web.service.ConfirmedMatchNullificationService;
import com.w3llspring.fhpb.web.service.DefaultMatchConfirmationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MatchConfirmationMaintenanceJob {

    private final DefaultMatchConfirmationService matchConfirmationService;
    private final ConfirmedMatchNullificationService confirmedMatchNullificationService;

    public MatchConfirmationMaintenanceJob(DefaultMatchConfirmationService matchConfirmationService,
                                          ConfirmedMatchNullificationService confirmedMatchNullificationService) {
        this.matchConfirmationService = matchConfirmationService;
        this.confirmedMatchNullificationService = confirmedMatchNullificationService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void pruneOldConfirmationRows() {
        try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("match-confirmation-prune")) {
            matchConfirmationService.pruneOldConfirmationRows();
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void autoConfirmOverdue() {
        try (BackgroundJobLogContext ignored = BackgroundJobLogContext.open("match-confirmation-overdue")) {
            if (confirmedMatchNullificationService != null) {
                confirmedMatchNullificationService.pruneExpiredRequests();
            }
            matchConfirmationService.autoConfirmOverdue();
        }
    }
}
