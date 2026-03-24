package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "trophy",
    uniqueConstraints = {@UniqueConstraint(name = "uk_trophy_slug", columnNames = "slug")})
public class Trophy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "season_id", foreignKey = @ForeignKey(name = "fk_trophy_season"))
  private LadderSeason season;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "catalog_entry_id", foreignKey = @ForeignKey(name = "fk_trophy_catalog_entry"))
  private TrophyCatalogEntry catalogEntry;

  @Column(name = "catalog_entry_id", insertable = false, updatable = false)
  private Long catalogEntryId;

  @Column(nullable = false, length = 96)
  private String title;

  @Column(nullable = false, length = 140)
  private String summary;

  @Column(name = "unlock_condition", length = 512)
  private String unlockCondition;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private TrophyRarity rarity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private TrophyStatus status;

  @Column(nullable = false, length = 80)
  private String slug;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "art_id", foreignKey = @ForeignKey(name = "fk_trophy_art"))
  private TrophyArt art;

  @Column(name = "art_id", insertable = false, updatable = false)
  private Long artId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "badge_art_id", foreignKey = @ForeignKey(name = "fk_trophy_badge_art"))
  private TrophyArt badgeArt;

  @Column(name = "badge_art_id", insertable = false, updatable = false)
  private Long badgeArtId;

  @Column(name = "ai_provider", length = 64)
  private String aiProvider;

  @Column(name = "generation_seed", length = 128)
  private String generationSeed;

  @Column(columnDefinition = "TEXT")
  private String prompt;

  @Column(name = "unlock_expression", length = 512)
  private String unlockExpression;

  @Column(name = "is_limited")
  private boolean limited;

  @Column(name = "is_repeatable")
  private boolean repeatable;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluation_scope", nullable = false, length = 24)
  private TrophyEvaluationScope evaluationScope;

  @Column(name = "is_default_template")
  private boolean defaultTemplate;

  @Column(name = "story_mode_tracker")
  private boolean storyModeTracker;

  @Column(name = "story_mode_key", length = 64)
  private String storyModeKey;

  @Column(name = "max_claims")
  private Integer maxClaims;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "badge_selectable_by_all")
  private boolean badgeSelectableByAll;

  @Column(name = "generated_at", nullable = false)
  private Instant generatedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "regeneration_count")
  private Integer regenerationCount;

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public TrophyCatalogEntry getCatalogEntry() {
    return catalogEntry;
  }

  public void setCatalogEntry(TrophyCatalogEntry catalogEntry) {
    this.catalogEntry = catalogEntry;
    this.catalogEntryId = catalogEntry != null ? catalogEntry.getId() : null;
  }

  public Long getCatalogEntryId() {
    if (catalogEntryId != null) {
      return catalogEntryId;
    }
    return catalogEntry != null ? catalogEntry.getId() : null;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getUnlockCondition() {
    return unlockCondition;
  }

  public void setUnlockCondition(String unlockCondition) {
    this.unlockCondition = unlockCondition;
  }

  public TrophyRarity getRarity() {
    return rarity;
  }

  public void setRarity(TrophyRarity rarity) {
    this.rarity = rarity;
  }

  public TrophyStatus getStatus() {
    return status;
  }

  public void setStatus(TrophyStatus status) {
    this.status = status;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public TrophyArt getArt() {
    return art;
  }

  public void setArt(TrophyArt art) {
    this.art = art;
    this.artId = art != null ? art.getId() : null;
  }

  public Long getArtId() {
    if (artId != null) {
      return artId;
    }
    return art != null ? art.getId() : null;
  }

  public TrophyArt getBadgeArt() {
    return badgeArt;
  }

  public void setBadgeArt(TrophyArt badgeArt) {
    this.badgeArt = badgeArt;
    this.badgeArtId = badgeArt != null ? badgeArt.getId() : null;
  }

  public Long getBadgeArtId() {
    if (badgeArtId != null) {
      return badgeArtId;
    }
    if (badgeArt != null) {
      return badgeArt.getId();
    }
    return getArtId();
  }

  public String getImageUrl() {
    return art != null ? art.getImageUrl() : null;
  }

  public byte[] getImageBytes() {
    return art != null ? art.getImageBytes() : null;
  }

  public boolean hasArt() {
    return art != null || artId != null;
  }

  public boolean hasBadgeArt() {
    return badgeArt != null || badgeArtId != null;
  }

  public boolean hasRenderableBadgeArt() {
    return hasBadgeArt() || hasArt();
  }

  public String getAiProvider() {
    return aiProvider;
  }

  public void setAiProvider(String aiProvider) {
    this.aiProvider = aiProvider;
  }

  public String getGenerationSeed() {
    return generationSeed;
  }

  public void setGenerationSeed(String generationSeed) {
    this.generationSeed = generationSeed;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getUnlockExpression() {
    return unlockExpression;
  }

  public void setUnlockExpression(String unlockExpression) {
    this.unlockExpression = unlockExpression;
  }

  public boolean isLimited() {
    return limited;
  }

  public void setLimited(boolean limited) {
    this.limited = limited;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public void setRepeatable(boolean repeatable) {
    this.repeatable = repeatable;
  }

  public TrophyEvaluationScope getEvaluationScope() {
    return evaluationScope;
  }

  public void setEvaluationScope(TrophyEvaluationScope evaluationScope) {
    this.evaluationScope = evaluationScope;
  }

  public boolean isDefaultTemplate() {
    return defaultTemplate;
  }

  public void setDefaultTemplate(boolean defaultTemplate) {
    this.defaultTemplate = defaultTemplate;
  }

  public boolean isStoryModeTracker() {
    return storyModeTracker;
  }

  public void setStoryModeTracker(boolean storyModeTracker) {
    this.storyModeTracker = storyModeTracker;
  }

  public String getStoryModeKey() {
    return storyModeKey;
  }

  public void setStoryModeKey(String storyModeKey) {
    this.storyModeKey = storyModeKey;
  }

  public Integer getMaxClaims() {
    return maxClaims;
  }

  public void setMaxClaims(Integer maxClaims) {
    this.maxClaims = maxClaims;
  }

  public Integer getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(Integer displayOrder) {
    this.displayOrder = displayOrder;
  }

  public boolean isBadgeSelectableByAll() {
    return badgeSelectableByAll;
  }

  public void setBadgeSelectableByAll(boolean badgeSelectableByAll) {
    this.badgeSelectableByAll = badgeSelectableByAll;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Integer getRegenerationCount() {
    return regenerationCount;
  }

  public void setRegenerationCount(Integer regenerationCount) {
    this.regenerationCount = regenerationCount;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.generatedAt = now;
    this.updatedAt = now;
    if (this.regenerationCount == null) {
      this.regenerationCount = 0;
    }
    if (this.status == null) {
      this.status = hasArt() ? TrophyStatus.GENERATED : TrophyStatus.PENDING_IMAGE;
    }
    if (this.rarity == null) {
      this.rarity = TrophyRarity.COMMON;
    }
    if (this.evaluationScope == null) {
      this.evaluationScope = TrophyEvaluationScope.USER;
    }
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
