package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.LadderStandingRepository;
import com.w3llspring.fhpb.web.db.TrophyCatalogEntryRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.LadderStanding;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.TrophyCatalogEntry;
import com.w3llspring.fhpb.web.model.TrophyRarity;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.LadderV2Service;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrophyCatalogService {

  private final LadderSeasonRepository seasonRepository;
  private final TrophyCatalogEntryRepository trophyCatalogEntryRepository;
  private final TrophyRepository trophyRepository;
  private final UserTrophyRepository userTrophyRepository;
  private final LadderMembershipRepository membershipRepository;
  private final LadderStandingRepository ladderStandingRepository;
  private final LadderV2Service ladderV2Service;

  public TrophyCatalogService(
      LadderSeasonRepository seasonRepository,
      TrophyCatalogEntryRepository trophyCatalogEntryRepository,
      TrophyRepository trophyRepository,
      UserTrophyRepository userTrophyRepository,
      LadderMembershipRepository membershipRepository,
      LadderStandingRepository ladderStandingRepository,
      LadderV2Service ladderV2Service) {
    this.seasonRepository = seasonRepository;
    this.trophyCatalogEntryRepository = trophyCatalogEntryRepository;
    this.trophyRepository = trophyRepository;
    this.userTrophyRepository = userTrophyRepository;
    this.membershipRepository = membershipRepository;
    this.ladderStandingRepository = ladderStandingRepository;
    this.ladderV2Service = ladderV2Service;
  }

  @Transactional(readOnly = true)
  public TrophyCatalog fetchCurrentSeasonCatalog(User user) {
    LadderSeason season = seasonRepository.findTopByOrderByStartDateDesc().orElse(null);
    return buildCatalog(season, user, true);
  }

  @Transactional(readOnly = true)
  public List<TrophyCatalog> fetchSeasonCatalogs(User user) {
    LinkedHashMap<Long, LadderSeason> seasons = new LinkedHashMap<>();
    Set<Long> currentSeasonIds = new LinkedHashSet<>();

    Set<Long> eligibleLadderIds = resolveActiveLadderIds(user);

    boolean restrictToMembership = user != null && user.getId() != null;

    List<LadderSeason> activeSeasons =
        seasonRepository.findByStateOrderByStartDateDesc(LadderSeason.State.ACTIVE);
    for (LadderSeason season : activeSeasons) {
      if (season != null && season.getId() != null) {
        LadderConfig ladderConfig = season.getLadderConfig();
        Long ladderId = ladderConfig != null ? ladderConfig.getId() : null;
        if (restrictToMembership) {
          if (ladderId == null || !eligibleLadderIds.contains(ladderId)) {
            continue;
          }
        }
        seasons.putIfAbsent(season.getId(), season);
        currentSeasonIds.add(season.getId());
      }
    }

    if (user != null && user.getId() != null) {
      List<LadderSeason> earnedSeasons = userTrophyRepository.findDistinctSeasonsByUser(user);
      for (LadderSeason season : earnedSeasons) {
        if (season != null && season.getId() != null) {
          seasons.putIfAbsent(season.getId(), season);
        }
      }
    }

    if (seasons.isEmpty()) {
      return List.of(
          new TrophyCatalog(null, null, null, null, null, List.of(), true, null, null, null));
    }

    return seasons.values().stream()
        .map(
            season ->
                buildCatalog(
                    season,
                    user,
                    season != null
                        && season.getId() != null
                        && currentSeasonIds.contains(season.getId())))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public Optional<TrophyCatalog> fetchSeasonCatalog(User user, Long seasonId) {
    if (seasonId == null) {
      return Optional.empty();
    }

    LadderSeason season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null) {
      return Optional.empty();
    }

    if (!canViewSeason(user, season)) {
      return Optional.empty();
    }

    boolean currentSeasonFlag = season.getState() == LadderSeason.State.ACTIVE;
    return Optional.of(buildCatalog(season, user, currentSeasonFlag));
  }

  private boolean canViewSeason(User user, LadderSeason season) {
    if (season == null) {
      return false;
    }

    if (user == null || user.getId() == null) {
      return true;
    }

    Long ladderId = season.getLadderConfig() != null ? season.getLadderConfig().getId() : null;
    Set<Long> eligibleLadderIds = resolveActiveLadderIds(user);
    if (ladderId != null && eligibleLadderIds.contains(ladderId)) {
      return true;
    }

    return userTrophyRepository.existsByUserAndTrophySeasonId(user, season.getId());
  }

  private Set<Long> resolveActiveLadderIds(User user) {
    if (user == null || user.getId() == null) {
      return Collections.emptySet();
    }

    List<LadderMembership> memberships =
        membershipRepository.findByUserIdAndState(user.getId(), LadderMembership.State.ACTIVE);

    if (memberships.isEmpty()) {
      return Collections.emptySet();
    }

    Set<Long> ladderIds = new HashSet<>();
    for (LadderMembership membership : memberships) {
      LadderConfig ladderConfig = membership.getLadderConfig();
      if (ladderConfig != null && ladderConfig.getId() != null) {
        ladderIds.add(ladderConfig.getId());
      }
    }
    return ladderIds;
  }

  private TrophyCatalog buildCatalog(LadderSeason season, User user, boolean currentSeasonFlag) {
    if (season == null) {
      return new TrophyCatalog(
          null, null, null, null, null, List.of(), currentSeasonFlag, null, null, null);
    }

    String ladderConfigTitle =
        season.getLadderConfig() != null ? season.getLadderConfig().getTitle() : null;
    String displaySeasonName =
        (ladderConfigTitle != null ? ladderConfigTitle + " - " : "") + season.getName();
    boolean storySeason = season.isStoryModeEnabled();

    List<Trophy> trophies =
        trophyRepository.findBySeasonOrderByDisplayOrderAscIdAsc(season).stream()
            .filter(trophy -> !storySeason || trophy.isStoryModeTracker())
            .collect(Collectors.toList());

    Map<Long, Long> userAwardCounts = resolveUserAwardCounts(user, trophies);
    Map<Long, Long> ownerCounts = resolveOwnerCounts(trophies);

    List<TrophyCardModel> cards = new ArrayList<>();
    for (Trophy trophy : trophies) {
      long earnedCount = userAwardCounts.getOrDefault(trophy.getId(), 0L);
      DefaultBandFinishTrophyTemplates.TemplateSpec bandFinishSpec =
          DefaultBandFinishTrophyTemplates.matchingSpec(trophy);
      String title = bandFinishSpec != null ? bandFinishSpec.title() : trophy.getTitle();
      String summary =
          bandFinishSpec != null
              ? bandFinishSpec.formattedSummary(
                  season.getName() != null ? season.getName() : "Season")
              : trophy.getSummary();
      String unlockCondition =
          bandFinishSpec != null ? bandFinishSpec.unlockCondition() : trophy.getUnlockCondition();
      TrophyRarity rarity = bandFinishSpec != null ? bandFinishSpec.rarity() : trophy.getRarity();
      cards.add(
          new TrophyCardModel(
              trophy.getId(),
              title,
              summary,
              unlockCondition,
              formatEnum(rarity),
              trophy.isLimited(),
              trophy.isRepeatable(),
              trophy.getMaxClaims(),
              resolveImageUrl(trophy),
              season.getName(),
              formatEnum(trophy.getStatus()),
              earnedCount > 0,
              earnedCount,
              ownerCounts.getOrDefault(trophy.getId(), 0L),
              hasImage(trophy),
              cssForRarity(rarity)));
    }

    if (!storySeason && (currentSeasonFlag || isFallbackCatalog(trophies))) {
      appendDefaultTemplates(season, trophies, cards);
    }

    FinalStandingSummary finalStandingSummary = resolveFinalStandingSummary(season, user);

    if (cards.isEmpty()) {
      return new TrophyCatalog(
          season.getId(),
          displaySeasonName,
          ladderConfigTitle,
          season.getStartDate(),
          season.getEndDate(),
          List.of(),
          currentSeasonFlag,
          finalStandingSummary.rank(),
          finalStandingSummary.division(),
          finalStandingSummary.rating());
    }

    return new TrophyCatalog(
        season.getId(),
        displaySeasonName,
        ladderConfigTitle,
        season.getStartDate(),
        season.getEndDate(),
        cards,
        currentSeasonFlag,
        finalStandingSummary.rank(),
        finalStandingSummary.division(),
        finalStandingSummary.rating());
  }

  private FinalStandingSummary resolveFinalStandingSummary(LadderSeason season, User user) {
    if (season == null
        || season.getState() != LadderSeason.State.ENDED
        || user == null
        || user.getId() == null) {
      return FinalStandingSummary.empty();
    }

    List<LadderStanding> standings =
        ladderStandingRepository.findBySeasonOrderByRankNoAscWithUser(season);
    if (standings.isEmpty()) {
      return FinalStandingSummary.empty();
    }

    List<LadderV2Service.LadderRow> rows = ladderV2Service.buildDisplayRows(standings);
    if (rows.isEmpty()) {
      return FinalStandingSummary.empty();
    }

    Long userId = user.getId();
    for (LadderV2Service.LadderRow row : rows) {
      if (row != null && Objects.equals(userId, row.userId)) {
        return new FinalStandingSummary(row.rank, row.bandLabel, 1000 + row.points);
      }
    }

    return FinalStandingSummary.empty();
  }

  private void appendDefaultTemplates(
      LadderSeason season, List<Trophy> trophies, List<TrophyCardModel> cards) {
    if (season == null) {
      return;
    }

    String seasonName = season.getName() != null ? season.getName() : "Season";
    int standingCount =
        season.getState() == LadderSeason.State.ENDED
            ? ladderStandingRepository.findBySeasonOrderByRankNoAsc(season).size()
            : 0;

    Set<String> existingSeeds =
        trophies.stream()
            .map(Trophy::getGenerationSeed)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));

    List<TrophyCatalogEntry> defaultTemplates = loadApplicableDefaultTemplates(season);
    if (defaultTemplates.isEmpty()) {
      return;
    }

    for (TrophyCatalogEntry template : defaultTemplates) {
      if (!DefaultBandFinishTrophyTemplates.appliesToSeason(template, season, standingCount)) {
        continue;
      }
      if (template.getGenerationSeed() != null
          && existingSeeds.contains(template.getGenerationSeed())) {
        continue;
      }
      DefaultBandFinishTrophyTemplates.TemplateSpec bandFinishSpec =
          DefaultBandFinishTrophyTemplates.matchingSpec(template);
      String title = bandFinishSpec != null ? bandFinishSpec.title() : template.getTitle();
      String summary =
          bandFinishSpec != null
              ? bandFinishSpec.formattedSummary(seasonName)
              : formatSummary(template.getSummary(), seasonName);
      String unlockCondition =
          bandFinishSpec != null ? bandFinishSpec.unlockCondition() : template.getUnlockCondition();
      TrophyRarity rarity = bandFinishSpec != null ? bandFinishSpec.rarity() : template.getRarity();
      cards.add(
          new TrophyCardModel(
              template.getId(),
              title,
              summary,
              unlockCondition,
              formatEnum(rarity),
              template.isLimited(),
              template.isRepeatable(),
              template.getMaxClaims(),
              resolveImageUrl(template),
              seasonName,
              "Available",
              false,
              0L,
              0L,
              hasImage(template),
              cssForRarity(rarity)));
      if (template.getGenerationSeed() != null) {
        existingSeeds.add(template.getGenerationSeed());
      }
    }
  }

  private boolean isFallbackCatalog(List<Trophy> trophies) {
    return !trophies.isEmpty() && trophies.stream().allMatch(this::isFallbackTrophy);
  }

  private String formatSummary(String summary, String seasonName) {
    if (summary == null) {
      return null;
    }
    if (summary.contains("%s")) {
      return String.format(Locale.ENGLISH, summary, seasonName);
    }
    return summary;
  }

  private boolean isFallbackTrophy(Trophy trophy) {
    String provider = trophy.getAiProvider();
    return provider != null && provider.equalsIgnoreCase("fallback");
  }

  private Map<Long, Long> resolveUserAwardCounts(User user, List<Trophy> trophies) {
    if (user == null || user.getId() == null || trophies.isEmpty()) {
      return Collections.emptyMap();
    }
    List<UserTrophy> owned = userTrophyRepository.findByUserAndTrophyIn(user, trophies);
    Map<Long, Long> counts = new HashMap<>();
    for (UserTrophy userTrophy : owned) {
      if (userTrophy == null
          || userTrophy.getTrophy() == null
          || userTrophy.getTrophy().getId() == null) {
        continue;
      }
      counts.merge(userTrophy.getTrophy().getId(), (long) userTrophy.getAwardCount(), Long::sum);
    }
    return counts;
  }

  private Map<Long, Long> resolveOwnerCounts(List<Trophy> trophies) {
    if (trophies.isEmpty()) {
      return Collections.emptyMap();
    }
    List<UserTrophyRepository.TrophyOwnerCount> aggregates =
        userTrophyRepository.countOwnersByTrophyIn(trophies);
    Map<Long, Long> counts = new HashMap<>();
    for (UserTrophyRepository.TrophyOwnerCount aggregate : aggregates) {
      counts.put(aggregate.getTrophyId(), aggregate.getOwners());
    }
    return counts;
  }

  private String formatEnum(Enum<?> value) {
    if (value == null) {
      return "";
    }
    String lower = value.name().toLowerCase(Locale.ENGLISH);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  private boolean hasImage(Trophy trophy) {
    return trophy != null && trophy.hasArt();
  }

  private boolean hasImage(TrophyCatalogEntry trophy) {
    return trophy != null && trophy.hasArt();
  }

  private String resolveImageUrl(Trophy trophy) {
    if (trophy == null || trophy.getId() == null || !trophy.hasArt()) {
      return null;
    }
    return "/trophies/image/" + trophy.getId();
  }

  private String resolveImageUrl(TrophyCatalogEntry trophy) {
    if (trophy == null || !trophy.hasArt()) {
      return null;
    }
    return trophy.getImageUrl();
  }

  private List<TrophyCatalogEntry> loadApplicableDefaultTemplates(LadderSeason season) {
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

  private String cssForRarity(TrophyRarity rarity) {
    if (rarity == null) {
      return "bg-secondary text-white";
    }
    switch (rarity) {
      case COMMON:
        return "bg-success text-white";
      case UNCOMMON:
        return "bg-info text-dark";
      case RARE:
        return "bg-primary text-white";
      case EPIC:
        return "bg-warning text-dark";
      case LEGENDARY:
        return "bg-danger text-white";
      default:
        return "bg-secondary text-white";
    }
  }

  private record FinalStandingSummary(Integer rank, String division, Integer rating) {
    private static FinalStandingSummary empty() {
      return new FinalStandingSummary(null, null, null);
    }
  }
}
