package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.TrophyCatalogEntryRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutoTrophyService {

  private static final Logger log = LoggerFactory.getLogger(AutoTrophyService.class);
  private final TrophyCatalogEntryRepository trophyCatalogEntryRepository;
  private final TrophyRepository trophyRepository;
  private final LadderStandingRepository ladderStandingRepository;
  private final com.w3llspring.fhpb.web.service.StoryModeService storyModeService;

  public AutoTrophyService(
      TrophyCatalogEntryRepository trophyCatalogEntryRepository,
      TrophyRepository trophyRepository,
      LadderStandingRepository ladderStandingRepository,
      com.w3llspring.fhpb.web.service.StoryModeService storyModeService) {
    this.trophyCatalogEntryRepository = trophyCatalogEntryRepository;
    this.trophyRepository = trophyRepository;
    this.ladderStandingRepository = ladderStandingRepository;
    this.storyModeService = storyModeService;
  }

  @Transactional
  public void generateSeasonTrophies(LadderSeason season) {
    if (season == null || season.getId() == null) {
      log.debug("Skipping trophy generation because season is null or not persisted yet.");
      return;
    }

    if (season.isStoryModeEnabled()) {
      if (storyModeService != null) {
        storyModeService.ensureTrackers(season);
      }
      return;
    }

    List<TrophyCatalogEntry> templates = loadApplicableTemplates(season);
    if (templates.isEmpty()) {
      log.warn("No default trophy templates found in DB for season {}.", season.getId());
      if (storyModeService != null) {
        storyModeService.ensureTrackers(season);
      }
      return;
    }

    List<Trophy> existing = trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season);
    Set<String> existingSeeds =
        existing.stream()
            .map(Trophy::getGenerationSeed)
            .filter(Objects::nonNull)
            .collect(HashSet::new, Set::add, Set::addAll);
    Set<String> existingFallbackKeys =
        existing.stream()
            .map(this::fallbackKey)
            .filter(Objects::nonNull)
            .collect(HashSet::new, Set::add, Set::addAll);

    int standingCount =
        season.getState() == LadderSeason.State.ENDED && ladderStandingRepository != null
            ? ladderStandingRepository.findBySeasonOrderByRankNoAsc(season).size()
            : 0;

    List<Trophy> toPersist = new ArrayList<>(templates.size());
    int order = 0;
    for (TrophyCatalogEntry template : templates) {
      if (template == null) {
        order++;
        continue;
      }
      if (!DefaultBandFinishTrophyTemplates.appliesToSeason(template, season, standingCount)) {
        order++;
        continue;
      }
      if (templateAlreadyPresent(template, existingSeeds, existingFallbackKeys)) {
        order++;
        continue;
      }

      DefaultBandFinishTrophyTemplates.TemplateSpec bandFinishSpec =
          DefaultBandFinishTrophyTemplates.matchingSpec(template);
      String generatedTitle = bandFinishSpec != null ? bandFinishSpec.title() : template.getTitle();
      String generatedSummary =
          bandFinishSpec != null
              ? bandFinishSpec.formattedSummary(
                  season.getName() != null ? season.getName() : "Season")
              : template.getSummary();
      String generatedUnlockCondition =
          bandFinishSpec != null ? bandFinishSpec.unlockCondition() : template.getUnlockCondition();
      String generatedPrompt =
          bandFinishSpec != null ? bandFinishSpec.prompt() : template.getPrompt();
      com.w3llspring.fhpb.web.model.TrophyRarity generatedRarity =
          bandFinishSpec != null ? bandFinishSpec.rarity() : template.getRarity();

      Trophy trophy = new Trophy();
      trophy.setSeason(season);
      trophy.setTitle(generatedTitle);
      trophy.setSummary(generatedSummary);
      trophy.setUnlockCondition(generatedUnlockCondition);
      trophy.setUnlockExpression(template.getUnlockExpression());
      trophy.setRarity(generatedRarity);
      trophy.setLimited(template.isLimited());
      trophy.setRepeatable(template.isRepeatable());
      trophy.setMaxClaims(template.getMaxClaims());
      trophy.setPrompt(generatedPrompt);
      trophy.setAiProvider(template.getAiProvider());
      trophy.setGenerationSeed(template.getGenerationSeed());
      int displayOrder = template.getDisplayOrder() != null ? template.getDisplayOrder() : order;
      trophy.setDisplayOrder(displayOrder);
      trophy.setSlug(buildUniqueSlug(season, generatedTitle, displayOrder));
      trophy.setCatalogEntry(template);
      trophy.setArt(template.getArt());
      trophy.setBadgeArt(template.getBadgeArt());
      trophy.setEvaluationScope(template.getEvaluationScope());

      toPersist.add(trophy);
      if (trophy.getGenerationSeed() != null) {
        existingSeeds.add(trophy.getGenerationSeed());
      } else {
        String key = fallbackKey(trophy);
        if (key != null) {
          existingFallbackKeys.add(key);
        }
      }
      order++;
    }

    if (!toPersist.isEmpty()) {
      trophyRepository.saveAll(toPersist);
      log.info(
          "Generated {} missing default trophies for season {}.", toPersist.size(), season.getId());
    }

    if (storyModeService != null) {
      storyModeService.ensureTrackers(season);
    }
  }

  private String buildSlug(LadderSeason season, String title, int order) {
    String base =
        (season.getStartDate() != null ? season.getStartDate().toString() : "season") + "-" + title;
    String normalized =
        Normalizer.normalize(base, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "")
            .replaceAll("[^a-zA-Z0-9]+", "-")
            .replaceAll("-+", "-")
            .toLowerCase(Locale.ENGLISH);
    String trimmed = normalized.replaceAll("^-", "").replaceAll("-$", "");
    return trimmed + "-" + order;
  }

  private String buildUniqueSlug(LadderSeason season, String title, int preferredOrder) {
    int candidate = Math.max(preferredOrder, 0);
    for (int i = 0; i < 10000; i++) {
      String slug = buildSlug(season, title, candidate);
      if (trophyRepository.findBySlug(slug).isEmpty()) {
        return slug;
      }
      candidate++;
    }
    return buildSlug(season, title, candidate);
  }

  private boolean templateAlreadyPresent(
      TrophyCatalogEntry template, Set<String> existingSeeds, Set<String> existingFallbackKeys) {
    if (template.getGenerationSeed() != null) {
      return existingSeeds.contains(template.getGenerationSeed());
    }
    String key = fallbackKey(template);
    return key != null && existingFallbackKeys.contains(key);
  }

  private List<TrophyCatalogEntry> loadApplicableTemplates(LadderSeason season) {
    List<TrophyCatalogEntry> templates =
        new ArrayList<>(
            trophyCatalogEntryRepository
                .findByDefaultTemplateTrueAndSeasonIsNullOrderByDisplayOrderAscIdAsc());
    if (season != null && season.getId() != null) {
      templates.addAll(
          trophyCatalogEntryRepository
              .findByDefaultTemplateTrueAndSeasonOrderByDisplayOrderAscIdAsc(season));
    }
    templates.sort(
        Comparator.comparing(
                TrophyCatalogEntry::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(TrophyCatalogEntry::getId, Comparator.nullsLast(Long::compareTo)));
    return templates;
  }

  private String fallbackKey(Trophy trophy) {
    if (trophy == null || trophy.getTitle() == null || trophy.getUnlockExpression() == null) {
      return null;
    }
    return (trophy.getTitle().trim().toLowerCase(Locale.ENGLISH)
        + "|"
        + trophy.getUnlockExpression().trim().toLowerCase(Locale.ENGLISH));
  }

  private String fallbackKey(TrophyCatalogEntry trophy) {
    if (trophy == null || trophy.getTitle() == null || trophy.getUnlockExpression() == null) {
      return null;
    }
    return (trophy.getTitle().trim().toLowerCase(Locale.ENGLISH)
        + "|"
        + trophy.getUnlockExpression().trim().toLowerCase(Locale.ENGLISH));
  }
}
