package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.PlayLocationAliasRepository;
import com.w3llspring.fhpb.web.db.PlayLocationCheckInRepository;
import com.w3llspring.fhpb.web.db.PlayLocationRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.PlayLocation;
import com.w3llspring.fhpb.web.model.PlayLocationAlias;
import com.w3llspring.fhpb.web.model.PlayLocationCheckIn;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.DisplayNameNormalization;
import com.w3llspring.fhpb.web.util.InputValidation;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlayLocationService {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/New_York");
  private static final DateTimeFormatter ACTIVE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);
  private static final double EARTH_RADIUS_METERS = 6_371_000d;

  private final PlayLocationRepository playLocationRepository;
  private final PlayLocationAliasRepository playLocationAliasRepository;
  private final PlayLocationCheckInRepository playLocationCheckInRepository;
  private final LadderMembershipRepository ladderMembershipRepository;
  private final UserRepository userRepository;
  private final DisplayNameModerationService moderationService;
  private final DoubleMetaphone doubleMetaphone = new DoubleMetaphone();
  private final long checkInExpiryMinutes;
  private final double nearbyRadiusMeters;
  private final int suggestionCount;
  private final Duration checkInCooldown;
  private final int maxCheckInsPerHour;
  private final int maxNewLocationsPerDay;

  @Autowired
  public PlayLocationService(
      PlayLocationRepository playLocationRepository,
      PlayLocationAliasRepository playLocationAliasRepository,
      PlayLocationCheckInRepository playLocationCheckInRepository,
      LadderMembershipRepository ladderMembershipRepository,
      UserRepository userRepository,
      DisplayNameModerationService moderationService,
      @Value("${fhpb.check-in.expiry-minutes:180}") long checkInExpiryMinutes,
      @Value("${fhpb.check-in.nearby-radius-meters:120}") double nearbyRadiusMeters,
      @Value("${fhpb.check-in.suggestion-count:2}") int suggestionCount,
      @Value("${fhpb.check-in.cooldown-seconds:30}") long checkInCooldownSeconds,
      @Value("${fhpb.check-in.max-per-hour:20}") int maxCheckInsPerHour,
      @Value("${fhpb.check-in.max-new-locations-per-day:5}") int maxNewLocationsPerDay) {
    this.playLocationRepository = playLocationRepository;
    this.playLocationAliasRepository = playLocationAliasRepository;
    this.playLocationCheckInRepository = playLocationCheckInRepository;
    this.ladderMembershipRepository = ladderMembershipRepository;
    this.userRepository = userRepository;
    this.moderationService = moderationService;
    this.checkInExpiryMinutes = checkInExpiryMinutes > 0 ? checkInExpiryMinutes : 180;
    this.nearbyRadiusMeters = nearbyRadiusMeters > 0 ? nearbyRadiusMeters : 120d;
    this.suggestionCount = suggestionCount > 0 ? suggestionCount : 2;
    this.checkInCooldown = Duration.ofSeconds(Math.max(0L, checkInCooldownSeconds));
    this.maxCheckInsPerHour = Math.max(1, maxCheckInsPerHour);
    this.maxNewLocationsPerDay = Math.max(1, maxNewLocationsPerDay);
  }

  public PlayLocationService(
      PlayLocationRepository playLocationRepository,
      PlayLocationAliasRepository playLocationAliasRepository,
      PlayLocationCheckInRepository playLocationCheckInRepository,
      LadderMembershipRepository ladderMembershipRepository,
      UserRepository userRepository,
      DisplayNameModerationService moderationService,
      long checkInExpiryMinutes,
      double nearbyRadiusMeters,
      int suggestionCount) {
    this(
        playLocationRepository,
        playLocationAliasRepository,
        playLocationCheckInRepository,
        ladderMembershipRepository,
        userRepository,
        moderationService,
        checkInExpiryMinutes,
        nearbyRadiusMeters,
        suggestionCount,
        30L,
        20,
        5);
  }

  @Transactional(readOnly = true)
  public CheckInPageView buildPage(User currentUser) {
    if (currentUser == null || currentUser.getId() == null) {
      return new CheckInPageView(null, List.of());
    }

    Instant now = Instant.now();
    List<PlayLocationAlias> aliases =
        playLocationAliasRepository.findAllWithLocationByUserId(currentUser.getId());
    Map<Long, List<PlayLocationAlias>> aliasesByLocationId =
        aliases.stream()
            .filter(alias -> alias.getLocation() != null && alias.getLocation().getId() != null)
            .collect(
                Collectors.groupingBy(
                    alias -> alias.getLocation().getId(), LinkedHashMap::new, Collectors.toList()));

    PlayLocationCheckIn activeCheckIn =
        playLocationCheckInRepository
            .findActiveWithLocationByUserId(currentUser.getId(), now)
            .stream()
            .findFirst()
            .orElse(null);

    Set<Long> locationIds = new java.util.LinkedHashSet<>(aliasesByLocationId.keySet());
    if (activeCheckIn != null
        && activeCheckIn.getLocation() != null
        && activeCheckIn.getLocation().getId() != null) {
      locationIds.add(activeCheckIn.getLocation().getId());
    }
    Map<Long, Long> activeCountsByLocationId = loadActiveCounts(locationIds, now);
    Map<Long, Long> privateGroupCountsByLocationId =
        loadPrivateGroupCounts(locationIds, currentUser.getId(), now);

    ActiveCheckInView activeView =
        buildActiveView(
            activeCheckIn,
            currentUser,
            activeCountsByLocationId,
            privateGroupCountsByLocationId,
            aliasesByLocationId);

    List<UserLocationView> locations =
        aliasesByLocationId.entrySet().stream()
            .map(
                entry ->
                    buildUserLocationView(
                        entry.getKey(),
                        entry.getValue(),
                        activeCountsByLocationId,
                        privateGroupCountsByLocationId,
                        activeView))
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparing(UserLocationView::isCurrent)
                    .reversed()
                    .thenComparing(UserLocationView::getActiveUserCount, Comparator.reverseOrder())
                    .thenComparing(UserLocationView::getUsageCount, Comparator.reverseOrder())
                    .thenComparing(UserLocationView::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

    return new CheckInPageView(activeView, locations);
  }

  @Transactional
  public ResolveOutcome resolveCheckIn(long userId, double latitude, double longitude) {
    validateCoordinates(latitude, longitude);

    NearbyLocation nearby = findNearbyLocation(latitude, longitude);
    if (nearby == null) {
      return ResolveOutcome.nameRequired(
          null, "No saved location was found nearby. Name this place to add it.");
    }

    List<PlayLocationAlias> userAliases =
        playLocationAliasRepository
            .findByLocation_IdAndUser_IdOrderByUsageCountDescLastUsedAtDescIdAsc(
                nearby.location().getId(), userId);
    if (!userAliases.isEmpty()) {
      PlayLocationAlias alias = userAliases.get(0);
      Instant now = Instant.now();
      enforceCheckInLimits(userId, false, now);
      PlayLocationCheckIn priorCheckIn = findActiveCheckIn(userId, now);
      alias.setUsageCount(alias.getUsageCount() + 1);
      alias.setLastUsedAt(now);
      playLocationAliasRepository.save(alias);
      createCheckIn(userId, nearby.location(), alias.getDisplayName(), now);
      return ResolveOutcome.checkedIn(
          buildSuccessMessage(alias.getDisplayName(), priorCheckIn, nearby.location()));
    }

    List<NameSuggestion> suggestions = buildSuggestions(nearby.location().getId(), userId);
    if (suggestions.isEmpty()) {
      return ResolveOutcome.nameRequired(
          nearby.location().getId(),
          "A nearby location already exists. What do you want to call it?");
    }
    return ResolveOutcome.chooseName(
        nearby.location().getId(),
        "A nearby location already exists. Is it one of these?",
        suggestions);
  }

  @Transactional
  public CheckInOutcome completeCheckIn(long userId, CompleteCheckInCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("Missing check-in request.");
    }

    validateCoordinates(command.getLatitude(), command.getLongitude());
    String chosenName =
        validateLocationName(resolveChosenName(command.getSelectedName(), command.getCustomName()));

    NearbyLocation nearby = findNearbyLocation(command.getLatitude(), command.getLongitude());
    boolean creatingNewLocation = command.getLocationId() == null && nearby == null;
    Instant now = Instant.now();
    enforceCheckInLimits(userId, creatingNewLocation, now);
    PlayLocation location =
        resolveTargetLocation(
            userId, command.getLocationId(), nearby, command.getLatitude(), command.getLongitude());
    PlayLocationCheckIn priorCheckIn = findActiveCheckIn(userId, now);
    User userReference = userRepository.getReferenceById(userId);

    PlayLocationAlias alias = upsertAlias(location, userReference, chosenName, now);
    createCheckIn(userId, location, alias.getDisplayName(), now);

    return new CheckInOutcome(buildSuccessMessage(alias.getDisplayName(), priorCheckIn, location));
  }

  public int getLocationNameMaxLength() {
    return PlayLocationAlias.MAX_NAME_LENGTH;
  }

  public String getExpiryLabel() {
    long hours = checkInExpiryMinutes / 60;
    if (checkInExpiryMinutes % 60 == 0 && hours > 0) {
      return hours == 1 ? "1 hour" : hours + " hours";
    }
    return checkInExpiryMinutes == 1 ? "1 minute" : checkInExpiryMinutes + " minutes";
  }

  private ActiveCheckInView buildActiveView(
      PlayLocationCheckIn activeCheckIn,
      User currentUser,
      Map<Long, Long> activeCountsByLocationId,
      Map<Long, Long> privateGroupCountsByLocationId,
      Map<Long, List<PlayLocationAlias>> aliasesByLocationId) {
    if (activeCheckIn == null
        || activeCheckIn.getLocation() == null
        || activeCheckIn.getLocation().getId() == null) {
      return null;
    }

    Long locationId = activeCheckIn.getLocation().getId();
    String displayName = activeCheckIn.getDisplayName();
    if (!StringUtils.hasText(displayName)) {
      displayName =
          choosePrimaryAlias(aliasesByLocationId.get(locationId))
              .map(PlayLocationAlias::getDisplayName)
              .orElse("Checked-In Location");
    }

    return new ActiveCheckInView(
        locationId,
        displayName,
        activeCheckIn.getExpiresAt(),
        formatForUser(activeCheckIn.getExpiresAt(), currentUser),
        activeCountsByLocationId.getOrDefault(locationId, 0L),
        privateGroupCountsByLocationId.getOrDefault(locationId, 0L));
  }

  private UserLocationView buildUserLocationView(
      Long locationId,
      List<PlayLocationAlias> aliases,
      Map<Long, Long> activeCountsByLocationId,
      Map<Long, Long> privateGroupCountsByLocationId,
      ActiveCheckInView activeView) {
    PlayLocationAlias primary = choosePrimaryAlias(aliases).orElse(null);
    if (primary == null || !StringUtils.hasText(primary.getDisplayName())) {
      return null;
    }

    long usageCount =
        aliases != null
            ? aliases.stream().mapToLong(alias -> Math.max(1, alias.getUsageCount())).sum()
            : 0L;
    boolean current = activeView != null && Objects.equals(activeView.getLocationId(), locationId);
    return new UserLocationView(
        locationId,
        primary.getDisplayName(),
        usageCount,
        activeCountsByLocationId.getOrDefault(locationId, 0L),
        privateGroupCountsByLocationId.getOrDefault(locationId, 0L),
        current);
  }

  private java.util.Optional<PlayLocationAlias> choosePrimaryAlias(
      List<PlayLocationAlias> aliases) {
    if (aliases == null || aliases.isEmpty()) {
      return java.util.Optional.empty();
    }
    return aliases.stream()
        .filter(alias -> StringUtils.hasText(alias.getDisplayName()))
        .max(
            Comparator.comparingInt(PlayLocationAlias::getUsageCount)
                .thenComparing(
                    PlayLocationAlias::getLastUsedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    PlayLocationAlias::getId, Comparator.nullsLast(Comparator.naturalOrder())));
  }

  private Map<Long, Long> loadActiveCounts(Collection<Long> locationIds, Instant now) {
    if (locationIds == null || locationIds.isEmpty()) {
      return Map.of();
    }

    return toCountMap(
        playLocationCheckInRepository.countActiveUsersByLocationIds(locationIds, now));
  }

  private Map<Long, Long> loadPrivateGroupCounts(
      Collection<Long> locationIds, Long currentUserId, Instant now) {
    if (currentUserId == null || locationIds == null || locationIds.isEmpty()) {
      return Map.of();
    }

    List<Long> privateGroupPeerUserIds =
        ladderMembershipRepository.findDistinctPeerUserIdsForPrivateGroups(
            currentUserId,
            LadderMembership.State.ACTIVE,
            LadderConfig.Type.COMPETITION,
            LadderConfig.Type.SESSION);
    if (privateGroupPeerUserIds == null || privateGroupPeerUserIds.isEmpty()) {
      return Map.of();
    }

    return toCountMap(
        playLocationCheckInRepository.countActiveUsersByLocationIdsAndUserIds(
            locationIds, privateGroupPeerUserIds, now));
  }

  private Map<Long, Long> toCountMap(List<PlayLocationCheckInRepository.ActiveLocationCount> rows) {
    Map<Long, Long> counts = new LinkedHashMap<>();
    if (rows == null || rows.isEmpty()) {
      return counts;
    }
    for (PlayLocationCheckInRepository.ActiveLocationCount row : rows) {
      if (row.getLocationId() != null) {
        counts.put(row.getLocationId(), row.getUserCount());
      }
    }
    return counts;
  }

  private NearbyLocation findNearbyLocation(double latitude, double longitude) {
    double latDelta = nearbyRadiusMeters / 111_320d;
    double lonScale = Math.cos(Math.toRadians(latitude));
    double lonDelta = lonScale <= 0.000001d ? 180d : nearbyRadiusMeters / (111_320d * lonScale);

    List<PlayLocation> candidates =
        playLocationRepository.findWithinBoundingBox(
            latitude - latDelta, latitude + latDelta, longitude - lonDelta, longitude + lonDelta);

    return candidates.stream()
        .map(
            location ->
                new NearbyLocation(
                    location,
                    distanceMeters(
                        latitude, longitude, location.getLatitude(), location.getLongitude())))
        .filter(candidate -> candidate.distanceMeters() <= nearbyRadiusMeters)
        .min(
            Comparator.comparingDouble(NearbyLocation::distanceMeters)
                .thenComparing(
                    candidate -> candidate.location().getId(),
                    Comparator.nullsLast(Comparator.naturalOrder())))
        .orElse(null);
  }

  private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double originLat = Math.toRadians(lat1);
    double targetLat = Math.toRadians(lat2);
    double a =
        Math.pow(Math.sin(dLat / 2d), 2d)
            + Math.cos(originLat) * Math.cos(targetLat) * Math.pow(Math.sin(dLon / 2d), 2d);
    double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    return EARTH_RADIUS_METERS * c;
  }

  private List<NameSuggestion> buildSuggestions(Long locationId, long userId) {
    if (locationId == null) {
      return List.of();
    }

    List<PlayLocationAlias> aliases =
        playLocationAliasRepository.findOtherUsersByLocationId(locationId, userId);
    if (aliases.isEmpty()) {
      return List.of();
    }

    List<SuggestionCluster> clusters = new ArrayList<>();
    for (PlayLocationAlias alias : aliases) {
      if (alias == null || !StringUtils.hasText(alias.getDisplayName())) {
        continue;
      }
      SuggestionCluster cluster =
          clusters.stream().filter(existing -> existing.matches(alias)).findFirst().orElse(null);
      if (cluster == null) {
        clusters.add(new SuggestionCluster(alias));
      } else {
        cluster.add(alias);
      }
    }

    return clusters.stream()
        .sorted(
            Comparator.comparingLong(SuggestionCluster::totalUsage)
                .reversed()
                .thenComparing(
                    SuggestionCluster::latestUse, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(suggestionCount)
        .map(SuggestionCluster::toSuggestion)
        .collect(Collectors.toList());
  }

  private PlayLocation resolveTargetLocation(
      long userId,
      Long requestedLocationId,
      NearbyLocation nearby,
      double latitude,
      double longitude) {
    if (requestedLocationId != null) {
      if (nearby == null
          || nearby.location() == null
          || !Objects.equals(nearby.location().getId(), requestedLocationId)) {
        throw new IllegalArgumentException("That location is no longer nearby. Try again.");
      }
      return nearby.location();
    }

    if (nearby != null) {
      return nearby.location();
    }

    PlayLocation location = new PlayLocation();
    location.setLatitude(latitude);
    location.setLongitude(longitude);
    location.setCreatedBy(userRepository.getReferenceById(userId));
    return playLocationRepository.save(location);
  }

  private PlayLocationAlias upsertAlias(
      PlayLocation location, User userReference, String chosenName, Instant now) {
    String normalized = normalizeLocationName(chosenName);
    String phoneticKey = phoneticKey(chosenName);

    PlayLocationAlias alias =
        playLocationAliasRepository
            .findByLocation_IdAndUser_IdAndNormalizedName(
                location.getId(), userReference.getId(), normalized)
            .orElseGet(
                () -> {
                  PlayLocationAlias created = new PlayLocationAlias();
                  created.setLocation(location);
                  created.setUser(userReference);
                  created.setNormalizedName(normalized);
                  created.setPhoneticKey(phoneticKey);
                  created.setUsageCount(0);
                  created.setFirstUsedAt(now);
                  return created;
                });

    alias.setDisplayName(chosenName);
    alias.setNormalizedName(normalized);
    alias.setPhoneticKey(phoneticKey);
    alias.setUsageCount(alias.getUsageCount() + 1);
    alias.setLastUsedAt(now);
    return playLocationAliasRepository.save(alias);
  }

  private void createCheckIn(long userId, PlayLocation location, String displayName, Instant now) {
    playLocationCheckInRepository.expireActiveByUserId(userId, now);

    PlayLocationCheckIn checkIn = new PlayLocationCheckIn();
    checkIn.setLocation(location);
    checkIn.setUser(userRepository.getReferenceById(userId));
    checkIn.setDisplayName(displayName);
    checkIn.setCheckedInAt(now);
    checkIn.setExpiresAt(now.plusSeconds(checkInExpiryMinutes * 60));
    playLocationCheckInRepository.save(checkIn);
  }

  private PlayLocationCheckIn findActiveCheckIn(long userId, Instant now) {
    return playLocationCheckInRepository.findActiveWithLocationByUserId(userId, now).stream()
        .findFirst()
        .orElse(null);
  }

  private void enforceCheckInLimits(long userId, boolean creatingNewLocation, Instant now) {
    if (!checkInCooldown.isZero()) {
      Instant cooldownCutoff = now.minus(checkInCooldown);
      var latestCheckIn =
          playLocationCheckInRepository.findTopByUser_IdOrderByCheckedInAtDescIdDesc(userId);
      if (latestCheckIn.isPresent()
          && latestCheckIn.get().getCheckedInAt() != null
          && latestCheckIn.get().getCheckedInAt().isAfter(cooldownCutoff)) {
        long secondsRemaining =
            Math.max(
                1L,
                Duration.between(now, latestCheckIn.get().getCheckedInAt().plus(checkInCooldown))
                    .getSeconds());
        throw new CheckInRateLimitException(
            "Please wait "
                + secondsRemaining
                + " more second"
                + (secondsRemaining == 1 ? "" : "s")
                + " before checking in again.");
      }
    }

    Instant hourlyCutoff = now.minus(Duration.ofHours(1));
    long recentCheckIns =
        playLocationCheckInRepository.countByUser_IdAndCheckedInAtGreaterThanEqual(
            userId, hourlyCutoff);
    if (recentCheckIns >= maxCheckInsPerHour) {
      throw new CheckInRateLimitException(
          "Check-in limit reached (" + maxCheckInsPerHour + " per hour). Please try again later.");
    }

    if (creatingNewLocation) {
      Instant dailyCutoff = now.minus(Duration.ofDays(1));
      long createdLocations =
          playLocationRepository.countByCreatedBy_IdAndCreatedAtGreaterThanEqual(
              userId, dailyCutoff);
      if (createdLocations >= maxNewLocationsPerDay) {
        throw new CheckInRateLimitException(
            "New location limit reached ("
                + maxNewLocationsPerDay
                + " in 24 hours). Please use an existing location or try again later.");
      }
    }
  }

  private String resolveChosenName(String selectedName, String customName) {
    if (StringUtils.hasText(customName)) {
      return customName.trim();
    }
    if (StringUtils.hasText(selectedName)) {
      return selectedName.trim();
    }
    throw new IllegalArgumentException("Choose a location name or enter your own.");
  }

  private String validateLocationName(String locationName) {
    return InputValidation.requireLocationName(locationName, moderationService);
  }

  private void validateCoordinates(double latitude, double longitude) {
    if (Double.isNaN(latitude)
        || Double.isNaN(longitude)
        || latitude < -90d
        || latitude > 90d
        || longitude < -180d
        || longitude > 180d) {
      throw new IllegalArgumentException("Invalid location coordinates.");
    }
  }

  private String normalizeLocationName(String name) {
    return DisplayNameNormalization.normalize(name);
  }

  private String phoneticKey(String name) {
    String normalized = normalizeLocationName(name);
    if (!StringUtils.hasText(normalized)) {
      return "";
    }
    String[] phonetics = doubleMetaphone.doubleMetaphone(normalized);
    String primary = phonetics != null && phonetics.length > 0 ? phonetics[0] : "";
    if (!StringUtils.hasText(primary)) {
      return normalized;
    }
    return primary;
  }

  private String buildSuccessMessage(String displayName) {
    return "Checked in at " + displayName + " for the next " + getExpiryLabel() + ".";
  }

  private String buildSuccessMessage(
      String displayName, PlayLocationCheckIn priorCheckIn, PlayLocation location) {
    if (priorCheckIn == null
        || priorCheckIn.getLocation() == null
        || priorCheckIn.getLocation().getId() == null
        || location == null
        || location.getId() == null) {
      return buildSuccessMessage(displayName);
    }

    if (Objects.equals(priorCheckIn.getLocation().getId(), location.getId())) {
      return "Updated your check-in at " + displayName + " for the next " + getExpiryLabel() + ".";
    }

    return "Moved your check-in to " + displayName + " for the next " + getExpiryLabel() + ".";
  }

  private String formatForUser(Instant instant, User user) {
    if (instant == null) {
      return "";
    }
    ZoneId zone = resolveZone(user);
    return ACTIVE_TIME_FORMATTER.withZone(zone).format(instant);
  }

  private ZoneId resolveZone(User user) {
    if (user == null || !StringUtils.hasText(user.getTimeZone())) {
      return DEFAULT_ZONE;
    }
    try {
      return ZoneId.of(user.getTimeZone().trim());
    } catch (Exception ex) {
      return DEFAULT_ZONE;
    }
  }

  public static final class CheckInPageView {
    private final ActiveCheckInView activeCheckIn;
    private final List<UserLocationView> locations;

    public CheckInPageView(ActiveCheckInView activeCheckIn, List<UserLocationView> locations) {
      this.activeCheckIn = activeCheckIn;
      this.locations = locations != null ? List.copyOf(locations) : List.of();
    }

    public ActiveCheckInView getActiveCheckIn() {
      return activeCheckIn;
    }

    public List<UserLocationView> getLocations() {
      return locations;
    }
  }

  public static final class ActiveCheckInView {
    private final Long locationId;
    private final String locationName;
    private final Instant expiresAt;
    private final String expiresAtLabel;
    private final long activeUserCount;
    private final long privateGroupActiveUserCount;

    public ActiveCheckInView(
        Long locationId,
        String locationName,
        Instant expiresAt,
        String expiresAtLabel,
        long activeUserCount,
        long privateGroupActiveUserCount) {
      this.locationId = locationId;
      this.locationName = locationName;
      this.expiresAt = expiresAt;
      this.expiresAtLabel = expiresAtLabel;
      this.activeUserCount = activeUserCount;
      this.privateGroupActiveUserCount = privateGroupActiveUserCount;
    }

    public Long getLocationId() {
      return locationId;
    }

    public String getLocationName() {
      return locationName;
    }

    public Instant getExpiresAt() {
      return expiresAt;
    }

    public String getExpiresAtLabel() {
      return expiresAtLabel;
    }

    public long getActiveUserCount() {
      return activeUserCount;
    }

    public long getPrivateGroupActiveUserCount() {
      return privateGroupActiveUserCount;
    }
  }

  public static final class UserLocationView {
    private final Long locationId;
    private final String name;
    private final long usageCount;
    private final long activeUserCount;
    private final long privateGroupActiveUserCount;
    private final boolean current;

    public UserLocationView(
        Long locationId,
        String name,
        long usageCount,
        long activeUserCount,
        long privateGroupActiveUserCount,
        boolean current) {
      this.locationId = locationId;
      this.name = name;
      this.usageCount = usageCount;
      this.activeUserCount = activeUserCount;
      this.privateGroupActiveUserCount = privateGroupActiveUserCount;
      this.current = current;
    }

    public Long getLocationId() {
      return locationId;
    }

    public String getName() {
      return name;
    }

    public long getUsageCount() {
      return usageCount;
    }

    public long getActiveUserCount() {
      return activeUserCount;
    }

    public long getPrivateGroupActiveUserCount() {
      return privateGroupActiveUserCount;
    }

    public boolean isCurrent() {
      return current;
    }
  }

  public static final class ResolveOutcome {
    private final String status;
    private final Long locationId;
    private final String message;
    private final List<NameSuggestion> suggestions;

    private ResolveOutcome(
        String status, Long locationId, String message, List<NameSuggestion> suggestions) {
      this.status = status;
      this.locationId = locationId;
      this.message = message;
      this.suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
    }

    public static ResolveOutcome checkedIn(String message) {
      return new ResolveOutcome("checked_in", null, message, List.of());
    }

    public static ResolveOutcome chooseName(
        Long locationId, String message, List<NameSuggestion> suggestions) {
      return new ResolveOutcome("choose_name", locationId, message, suggestions);
    }

    public static ResolveOutcome nameRequired(Long locationId, String message) {
      return new ResolveOutcome("name_required", locationId, message, List.of());
    }

    public String getStatus() {
      return status;
    }

    public Long getLocationId() {
      return locationId;
    }

    public String getMessage() {
      return message;
    }

    public List<NameSuggestion> getSuggestions() {
      return suggestions;
    }
  }

  public static final class NameSuggestion {
    private final String name;
    private final long usageCount;

    public NameSuggestion(String name, long usageCount) {
      this.name = name;
      this.usageCount = usageCount;
    }

    public String getName() {
      return name;
    }

    public long getUsageCount() {
      return usageCount;
    }
  }

  public static final class CompleteCheckInCommand {
    private final double latitude;
    private final double longitude;
    private final Long locationId;
    private final String selectedName;
    private final String customName;

    public CompleteCheckInCommand(
        double latitude,
        double longitude,
        Long locationId,
        String selectedName,
        String customName) {
      this.latitude = latitude;
      this.longitude = longitude;
      this.locationId = locationId;
      this.selectedName = selectedName;
      this.customName = customName;
    }

    public double getLatitude() {
      return latitude;
    }

    public double getLongitude() {
      return longitude;
    }

    public Long getLocationId() {
      return locationId;
    }

    public String getSelectedName() {
      return selectedName;
    }

    public String getCustomName() {
      return customName;
    }
  }

  public static final class CheckInOutcome {
    private final String message;

    public CheckInOutcome(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }

  public static class CheckInRateLimitException extends IllegalArgumentException {
    public CheckInRateLimitException(String message) {
      super(message);
    }
  }

  private record NearbyLocation(PlayLocation location, double distanceMeters) {}

  private final class SuggestionCluster {
    private final List<PlayLocationAlias> members = new ArrayList<>();
    private String representativeNormalized;
    private String representativePhonetic;

    private SuggestionCluster(PlayLocationAlias alias) {
      add(alias);
    }

    private void add(PlayLocationAlias alias) {
      members.add(alias);
      if (representativeNormalized == null) {
        representativeNormalized = defaultString(alias.getNormalizedName());
      }
      if (representativePhonetic == null) {
        representativePhonetic = defaultString(alias.getPhoneticKey());
      }
    }

    private boolean matches(PlayLocationAlias alias) {
      String normalized = defaultString(alias.getNormalizedName());
      if (normalized.equals(representativeNormalized)) {
        return true;
      }
      String phonetic = defaultString(alias.getPhoneticKey());
      if (!StringUtils.hasText(phonetic) || !phonetic.equals(representativePhonetic)) {
        return false;
      }
      return normalized.contains(representativeNormalized)
          || representativeNormalized.contains(normalized)
          || DisplayNameNormalization.levenshteinDistance(normalized, representativeNormalized)
              <= 2;
    }

    private long totalUsage() {
      return members.stream().mapToLong(member -> Math.max(1, member.getUsageCount())).sum();
    }

    private Instant latestUse() {
      return members.stream()
          .map(PlayLocationAlias::getLastUsedAt)
          .filter(Objects::nonNull)
          .max(Comparator.naturalOrder())
          .orElse(null);
    }

    private NameSuggestion toSuggestion() {
      List<PlayLocationAlias> weighted = new ArrayList<>();
      for (PlayLocationAlias member : members) {
        int weight = Math.max(1, member.getUsageCount());
        for (int i = 0; i < weight; i++) {
          weighted.add(member);
        }
      }
      PlayLocationAlias picked = weighted.get(ThreadLocalRandom.current().nextInt(weighted.size()));
      return new NameSuggestion(picked.getDisplayName(), totalUsage());
    }
  }

  private String defaultString(String value) {
    return value != null ? value : "";
  }
}
