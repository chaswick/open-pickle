package com.w3llspring.fhpb.web.service.user;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultDisplayNameModerationService implements DisplayNameModerationService {

  private static final String DATASET_PATH = "moderation/naughty-words-en.txt";

  private final Set<String> bannedNormalized;

  public DefaultDisplayNameModerationService(
      @Value("${fhpb.moderation.display-name-banned:}") String additionalFragments) {
    this.bannedNormalized = new HashSet<>();
    loadDataset();
    addAdditional(additionalFragments);
  }

  @Override
  public Optional<String> explainViolation(String displayName) {
    if (!StringUtils.hasText(displayName)) {
      return Optional.empty();
    }
    String normalized = normalize(displayName);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    String collapsed = DisplayNameNormalization.collapseRepeated(normalized);
    Set<String> tokenized = DisplayNameNormalization.tokenize(displayName);
    Set<String> collapsedTokens =
        tokenized.stream()
            .map(DisplayNameNormalization::collapseRepeated)
            .collect(Collectors.toSet());

    for (String banned : bannedNormalized) {
      if (tokenized.contains(banned) || collapsedTokens.contains(banned)) {
        return Optional.of("Display name contains language that is not allowed.");
      }
      if (banned.length() > 3 && (normalized.contains(banned) || collapsed.contains(banned))) {
        return Optional.of("Display name contains language that is not allowed.");
      }
    }
    return Optional.empty();
  }

  private void loadDataset() {
    ClassPathResource resource = new ClassPathResource(DATASET_PATH);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
      reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .map(this::normalize)
          .filter(s -> !s.isEmpty())
          .forEach(bannedNormalized::add);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load profanity dataset", ex);
    }
  }

  private void addAdditional(String additionalFragments) {
    if (!StringUtils.hasText(additionalFragments)) {
      return;
    }
    Arrays.stream(additionalFragments.split("[,;]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(this::normalize)
        .filter(s -> !s.isEmpty())
        .forEach(bannedNormalized::add);
  }

  private String normalize(String input) {
    return DisplayNameNormalization.normalize(input);
  }
}
