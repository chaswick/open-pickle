package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.model.BadgeView;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class TrophyBadgeViewService {

  private final TrophyRepository trophyRepository;

  public TrophyBadgeViewService(TrophyRepository trophyRepository) {
    this.trophyRepository = trophyRepository;
  }

  public Map<Long, BadgeView> buildBadgeViewMap(Collection<Long> trophyIds) {
    if (trophyRepository == null || trophyIds == null || trophyIds.isEmpty()) {
      return Map.of();
    }
    List<Long> ids = trophyIds.stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) {
      return Map.of();
    }

    Map<Long, BadgeView> viewsById = new LinkedHashMap<>();
    for (Trophy trophy : trophyRepository.findAllByIdInWithSeason(ids)) {
      BadgeView view = TrophyBadgeSupport.badgeView(trophy);
      if (view != null && trophy.getId() != null) {
        viewsById.putIfAbsent(trophy.getId(), view);
      }
    }
    return viewsById;
  }

  public List<BadgeView> badgeViews(User user) {
    return badgeViews(user, buildBadgeViewMap(selectedBadgeIds(user)));
  }

  public List<BadgeView> badgeViews(User user, Map<Long, BadgeView> badgeViewsByTrophyId) {
    if (user == null) {
      return List.of();
    }
    List<BadgeView> views = new java.util.ArrayList<>(1);
    addBadgeView(
        views, user.getBadgeSlot1Trophy(), user.getBadgeSlot1TrophyId(), badgeViewsByTrophyId);
    return views;
  }

  public List<Long> selectedBadgeIds(User user) {
    if (user == null) {
      return List.of();
    }
    return user.getBadgeSlot1TrophyId() != null ? List.of(user.getBadgeSlot1TrophyId()) : List.of();
  }

  private void addBadgeView(
      List<BadgeView> views,
      Trophy loadedTrophy,
      Long trophyId,
      Map<Long, BadgeView> badgeViewsByTrophyId) {
    BadgeView loadedView = TrophyBadgeSupport.badgeView(loadedTrophy);
    if (loadedView != null) {
      views.add(loadedView);
      return;
    }
    if (trophyId == null || badgeViewsByTrophyId == null) {
      return;
    }
    BadgeView mappedView = badgeViewsByTrophyId.get(trophyId);
    if (mappedView != null) {
      views.add(mappedView);
    }
  }
}
