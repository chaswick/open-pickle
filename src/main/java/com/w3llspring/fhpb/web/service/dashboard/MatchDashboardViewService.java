package com.w3llspring.fhpb.web.service.dashboard;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import com.w3llspring.fhpb.web.service.MatchDashboardService;
import com.w3llspring.fhpb.web.service.MatchRowModel;

@Service
public class MatchDashboardViewService {

    public void applyToModel(Model model, MatchDashboardService.DashboardModel dashboard) {
        if (model == null || dashboard == null) {
            return;
        }
        model.addAttribute("links", dashboard.links());
        model.addAttribute("confirmableMatchIds", dashboard.matchRowModel().getConfirmableMatchIds());
        model.addAttribute("confirmerByMatchId", dashboard.matchRowModel().getConfirmerByMatchId());
        model.addAttribute("casualAutoConfirmedByMatchId", dashboard.matchRowModel().getCasualAutoConfirmedByMatchId());
        model.addAttribute("pendingByMatchId", dashboard.matchRowModel().getPendingByMatchId());
        model.addAttribute("waitingOnOpponentByMatchId", dashboard.matchRowModel().getWaitingOnOpponentByMatchId());
        model.addAttribute("editableByMatchId", dashboard.matchRowModel().getEditableByMatchId());
        model.addAttribute("deletableByMatchId", dashboard.matchRowModel().getDeletableByMatchId());
        model.addAttribute("nullifyRequestableByMatchId", dashboard.matchRowModel().getNullifyRequestableByMatchId());
        model.addAttribute("nullifyApprovableByMatchId", dashboard.matchRowModel().getNullifyApprovableByMatchId());
        model.addAttribute("nullifyWaitingOnOpponentByMatchId",
                dashboard.matchRowModel().getNullifyWaitingOnOpponentByMatchId());
    }

    public MatchDashboardService.DashboardModel emptyDashboard() {
        return new MatchDashboardService.DashboardModel(
                List.of(),
                new MatchRowModel(
                        Set.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()));
    }
}
