package com.w3llspring.fhpb.web.controller.competition;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.StoryModeService;
import com.w3llspring.fhpb.web.service.user.UserStatsService;
import com.w3llspring.fhpb.web.util.AuthenticatedUserSupport;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LadderController {

  private final UserRepository userRepository;
  private final LadderConfigRepository ladderConfigRepository;
  private final MatchRepository matchRepository;
  private final LadderSeasonRepository seasonRepository;
  private final UserStatsService userStatsService;
  private final StoryModeService storyModeService;
  private final Instant appStartInstant;
  private final DateTimeFormatter timestampFormatter;

  public LadderController(
      UserRepository userRepository,
      LadderConfigRepository ladderConfigRepository,
      MatchRepository matchRepository,
      LadderSeasonRepository seasonRepository,
      UserStatsService userStatsService,
      StoryModeService storyModeService,
      ObjectProvider<BuildProperties> buildPropertiesProvider) {
    this.userRepository = userRepository;
    this.ladderConfigRepository = ladderConfigRepository;
    this.matchRepository = matchRepository;
    this.seasonRepository = seasonRepository;
    this.userStatsService = userStatsService;
    this.storyModeService = storyModeService;
    BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
    Instant runtimeStart =
        Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    if (buildProperties != null && buildProperties.getTime() != null) {
      Instant buildTime = buildProperties.getTime();
      this.appStartInstant = buildTime.isBefore(runtimeStart) ? runtimeStart : buildTime;
    } else {
      this.appStartInstant = runtimeStart;
    }
    this.timestampFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z").withZone(ZoneId.systemDefault());
  }

  @GetMapping("/stats")
  public String showStats(Model model) {
    User currentUser = AuthenticatedUserSupport.currentUser();

    // Community stats
    Map<String, Object> stats = new HashMap<>();
    stats.put("userCount", userRepository.count());
    stats.put("ladderCount", ladderConfigRepository.count());
    stats.put("matchCount", matchRepository.count());
    stats.put("activeSeasonCount", seasonRepository.countActive());
    stats.put("generatedAt", timestampFormatter.format(Instant.now()));
    stats.put("uptime", formatDuration(Duration.between(appStartInstant, Instant.now())));
    model.addAttribute("stats", stats);
    if (storyModeService != null && storyModeService.isFeatureEnabled()) {
      model.addAttribute("storyStats", storyModeService.buildCommunityStats(6));
    }

    // User stats
    if (currentUser != null) {
      Map<String, Object> userStats = userStatsService.calculateUserStats(currentUser);
      model.addAttribute("userStats", userStats);
    }

    return "auth/stats";
  }

  private String formatDuration(Duration duration) {
    long seconds = duration.getSeconds();
    long days = seconds / 86_400;
    long hours = (seconds % 86_400) / 3_600;
    long minutes = (seconds % 3_600) / 60;
    long secs = seconds % 60;

    StringBuilder sb = new StringBuilder();
    if (days > 0) {
      sb.append(days).append(days == 1 ? " day " : " days ");
    }
    if (hours > 0 || days > 0) {
      sb.append(hours).append("h ");
    }
    if (minutes > 0 || hours > 0 || days > 0) {
      sb.append(minutes).append("m ");
    }
    sb.append(secs).append("s");
    return sb.toString().trim();
  }
}
