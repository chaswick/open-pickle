package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.BadgeView;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import java.util.ArrayList;
import java.util.List;

public final class TrophyBadgeSupport {

  private TrophyBadgeSupport() {}

  public static String badgeUrl(Long trophyId) {
    return trophyId == null ? null : "/trophies/badge/" + trophyId;
  }

  public static List<String> badgeUrls(User user) {
    if (user == null) {
      return List.of();
    }
    List<String> urls = new ArrayList<>(1);
    addBadgeUrl(urls, user.getBadgeSlot1TrophyId());
    return urls;
  }

  private static void addBadgeUrl(List<String> urls, Long trophyId) {
    String url = badgeUrl(trophyId);
    if (url != null) {
      urls.add(url);
    }
  }

  public static BadgeView badgeView(Trophy trophy) {
    if (trophy == null || trophy.getId() == null) {
      return null;
    }
    String label = hasText(trophy.getTitle()) ? trophy.getTitle().trim() : "Trophy";
    String contextLabel = badgeContextLabel(trophy);
    String title = hasText(contextLabel) ? contextLabel + " - " + label : label;
    String unlockCondition =
        hasText(trophy.getUnlockCondition()) ? trophy.getUnlockCondition().trim() : null;
    return new BadgeView(badgeUrl(trophy.getId()), label, title, unlockCondition);
  }

  public static String badgeContextLabel(Trophy trophy) {
    if (trophy == null) {
      return null;
    }
    if (trophy.getSeason() != null && hasText(trophy.getSeason().getName())) {
      return trophy.getSeason().getName().trim();
    }
    if (trophy.isBadgeSelectableByAll() && hasText(trophy.getSummary())) {
      return trophy.getSummary().trim();
    }
    return null;
  }

  public static List<BadgeView> badgeViewsFromLoadedUser(User user) {
    if (user == null) {
      return List.of();
    }
    List<BadgeView> views = new ArrayList<>(1);
    addBadgeView(views, user.getBadgeSlot1Trophy());
    return views;
  }

  private static void addBadgeView(List<BadgeView> views, Trophy trophy) {
    BadgeView view = badgeView(trophy);
    if (view != null) {
      views.add(view);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
