package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.CompetitionDisplayNameReportRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CompetitionDisplayNameReport;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.user.DisplayNameNormalization;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompetitionDisplayNameModerationService {

  public enum ReportOutcome {
    REPORTED,
    AUTO_HIDDEN,
    ALREADY_REPORTED,
    ALREADY_HIDDEN
  }

  private final CompetitionDisplayNameReportRepository reportRepository;
  private final UserRepository userRepository;

  @Value("${fhpb.competition.display-name-report-threshold:3}")
  private int reportThreshold;

  public CompetitionDisplayNameModerationService(
      CompetitionDisplayNameReportRepository reportRepository, UserRepository userRepository) {
    this.reportRepository = reportRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public ReportOutcome reportDisplayName(User reporter, User target) {
    if (reporter == null || reporter.getId() == null) {
      throw new IllegalArgumentException("Reporter is required.");
    }
    if (target == null || target.getId() == null) {
      throw new IllegalArgumentException("Target is required.");
    }
    if (Objects.equals(reporter.getId(), target.getId())) {
      throw new IllegalArgumentException("You cannot report your own display name.");
    }
    if (target.isCompetitionSafeDisplayNameActive()) {
      return ReportOutcome.ALREADY_HIDDEN;
    }
    if (reportRepository.existsByReporterUserIdAndTargetUserId(reporter.getId(), target.getId())) {
      return ReportOutcome.ALREADY_REPORTED;
    }

    CompetitionDisplayNameReport report = new CompetitionDisplayNameReport();
    report.setReporterUserId(reporter.getId());
    report.setTargetUserId(target.getId());
    report.setCreatedAt(java.time.Instant.now());
    reportRepository.save(report);

    if (reportRepository.countByTargetUserId(target.getId()) >= Math.max(1, reportThreshold)) {
      activateSafeDisplayNameOverride(target);
      return ReportOutcome.AUTO_HIDDEN;
    }

    return ReportOutcome.REPORTED;
  }

  public Set<Long> findReportedTargetUserIds(Long reporterUserId) {
    if (reporterUserId == null) {
      return Set.of();
    }
    return reportRepository.findByReporterUserId(reporterUserId).stream()
        .map(CompetitionDisplayNameReport::getTargetUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public void applyCompetitionDisplayNames(
      List<LadderV2Service.LadderRow> rows, List<LadderStanding> standings) {
    if (rows == null || rows.isEmpty() || standings == null || standings.isEmpty()) {
      return;
    }
    Map<Long, User> userById =
        standings.stream()
            .map(LadderStanding::getUser)
            .filter(Objects::nonNull)
            .filter(user -> user.getId() != null)
            .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

    for (LadderV2Service.LadderRow row : rows) {
      if (row == null || row.userId == null) {
        continue;
      }
      User user = userById.get(row.userId);
      if (user == null || !user.isCompetitionSafeDisplayNameActive()) {
        continue;
      }
      String safeName = user.getCompetitionSafeDisplayName();
      if (!StringUtils.hasText(safeName)) {
        continue;
      }
      row.displayName = safeName;
      row.competitionSafeDisplayNameActive = true;
    }
  }

  @Transactional
  public void clearOverrideIfSubstantialRename(
      User user, String oldDisplayName, String newDisplayName) {
    if (user == null || user.getId() == null || !user.isCompetitionSafeDisplayNameActive()) {
      return;
    }
    String basis =
        StringUtils.hasText(user.getCompetitionSafeDisplayNameBasis())
            ? user.getCompetitionSafeDisplayNameBasis()
            : DisplayNameNormalization.normalizedCollapsed(oldDisplayName);
    String updated = DisplayNameNormalization.normalizedCollapsed(newDisplayName);
    if (!StringUtils.hasText(basis) || !StringUtils.hasText(updated)) {
      return;
    }
    if (!isSubstantialChange(basis, updated)) {
      return;
    }

    user.setCompetitionSafeDisplayName(null);
    user.setCompetitionSafeDisplayNameActive(false);
    user.setCompetitionSafeDisplayNameBasis(null);
    userRepository.save(user);
    reportRepository.deleteByTargetUserId(user.getId());
  }

  boolean isSubstantialChange(String priorBasis, String updatedName) {
    String left = DisplayNameNormalization.normalizedCollapsed(priorBasis);
    String right = DisplayNameNormalization.normalizedCollapsed(updatedName);
    if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
      return false;
    }
    if (left.equals(right)) {
      return false;
    }
    if ((left.contains(right) || right.contains(left))
        && Math.abs(left.length() - right.length()) <= 2) {
      return false;
    }
    int threshold = Math.max(2, Math.min(5, Math.max(left.length(), right.length()) / 3));
    return DisplayNameNormalization.levenshteinDistance(left, right) > threshold;
  }

  private void activateSafeDisplayNameOverride(User user) {
    user.setCompetitionSafeDisplayName(resolveSafePlaceholder(user));
    user.setCompetitionSafeDisplayNameActive(true);
    user.setCompetitionSafeDisplayNameBasis(
        DisplayNameNormalization.normalizedCollapsed(user.getNickName()));
    userRepository.save(user);
  }

  private String resolveSafePlaceholder(User user) {
    String publicCode = user.getPublicCode();
    if (StringUtils.hasText(publicCode)) {
      String cleaned = publicCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
      if (!cleaned.isEmpty()) {
        String suffix = cleaned.length() > 6 ? cleaned.substring(cleaned.length() - 6) : cleaned;
        return "Player " + suffix;
      }
    }
    return "Player " + user.getId();
  }
}
