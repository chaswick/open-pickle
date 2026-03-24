package com.w3llspring.fhpb.web.controller.account;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.StringUtils;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.TrophyRepository;
import com.w3llspring.fhpb.web.db.UserCourtNameRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.db.UserStyleRepository;
import com.w3llspring.fhpb.web.db.UserTrophyRepository;
import com.w3llspring.fhpb.web.model.BadgeView;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.Trophy;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.model.UserStyle;
import com.w3llspring.fhpb.web.model.UserTrophy;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.CourtNameSimilarityService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import com.w3llspring.fhpb.web.service.user.UserIdentityService;
import com.w3llspring.fhpb.web.service.user.UserIdentityService.DisplayNameChangeResult;
import com.w3llspring.fhpb.web.service.user.UserIdentityService.DisplayNameChangeStatus;
import com.w3llspring.fhpb.web.service.trophy.TrophyBadgeSupport;
import com.w3llspring.fhpb.web.util.InputValidation;
import com.w3llspring.fhpb.web.util.PaginationWindowSupport;


@Controller
@Secured("ROLE_USER")
public class UserStyleController {

	private static final int ACCOUNT_RECENT_MATCHES_PAGE_SIZE = 8;
	private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("America/New_York");
	private static final DateTimeFormatter AWARD_DISPLAY_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH);
	private static final DateTimeFormatter SEARCH_MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d uuuu", Locale.ENGLISH);
	private static final DateTimeFormatter SEARCH_FULL_MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMMM d uuuu", Locale.ENGLISH);
	private static final DateTimeFormatter SEARCH_MONTH_YEAR = DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);
	private static final DateTimeFormatter SEARCH_FULL_MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);
	private static final DateTimeFormatter SEARCH_SLASH_DATE = DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ENGLISH);
	private static final DateTimeFormatter SEARCH_PADDED_SLASH_DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.ENGLISH);
	private static final String ACCOUNT_PATH = "/account";
	private static final String HOME_PATH = "/home";

	@Autowired
	private UserStyleRepository userStyleRepo;

	@Autowired
	private UserRepository userRepo;

	@Autowired
	private com.w3llspring.fhpb.web.db.MatchRepository matchRepo;

	@Autowired
	private UserCourtNameRepository userCourtNameRepo;

	@Autowired
	private LadderMembershipRepository ladderMembershipRepository;

	@Autowired
	private UserTrophyRepository userTrophyRepository;

	@Autowired
	private TrophyRepository trophyRepository;

	@Autowired
	private DisplayNameModerationService displayNameModerationService;

	@Autowired
	private CourtNameSimilarityService courtNameSimilarityService;

    

	@Autowired
	private com.w3llspring.fhpb.web.service.MatchRowModelBuilder matchRowModelBuilder;

	@Value("${fhpb.account.display-name-change-cooldown-seconds:86400}")
	private long displayNameChangeCooldownSeconds;

	@Autowired
	private UserIdentityService userIdentityService;

	@Autowired
	private UserAccountSettingsService userAccountSettingsService;

	public String viewUser(String memberCode, Model model) {
		return viewUser(memberCode, null, model);
	}

	@GetMapping("/account")
	public String viewUser(@RequestParam(value = "member", required = false) String memberCode,
			@RequestParam(value = "recentMatchesPage", required = false) Integer recentMatchesPage,
			Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User sessionUser = userDetails.getUserObject();
		User currentUser = refreshAuthenticatedUser(sessionUser);
		String currentNickName = currentUser != null ? currentUser.getNickName() : null;

		User styleOwner = resolveProfileUser(memberCode, currentUser);
		if (styleOwner == null && StringUtils.hasText(memberCode)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found.");
		}
		String displayName = styleOwner != null
				? styleOwner.getNickName()
				: (!StringUtils.hasText(memberCode) ? currentNickName : "");
		String resolvedMemberCode = styleOwner != null ? styleOwner.getPublicCode() : memberCode;
		boolean canEditDisplayName = currentUser != null
				&& styleOwner != null
				&& Objects.equals(currentUser.getId(), styleOwner.getId());

        model.addAttribute("userName", currentNickName);
		model.addAttribute("memberCode", resolvedMemberCode);
		model.addAttribute("queryName", displayName);
		model.addAttribute("accountPageTitle", buildAccountPageTitle(displayName));
		model.addAttribute("accountPageSubtitle", buildAccountPageSubtitle(displayName, canEditDisplayName));
		if (styleOwner == null) {
			model.addAttribute("styleList", Collections.emptyList());
			model.addAttribute("canVote", false);
			model.addAttribute("canEditDisplayName", false);
			model.addAttribute("displayNameMaxLength", User.MAX_NICKNAME_LENGTH);
			model.addAttribute("courtNames", Collections.emptyList());
			model.addAttribute("ladderOptions", Collections.emptyList());
			populateEmptyBadgeModel(model);
			model.addAttribute("courtNameMaxLength", UserCourtName.MAX_ALIAS_LENGTH);
			model.addAttribute("recentMatches", Collections.emptyList());
			return "auth/userDetails";
		}
		requireSharedActiveLadderOrSelf(currentUser, styleOwner);

		Long styleOwnerId = styleOwner.getId();

		List<UserStyle> userStyleList = userStyleRepo.findUserStyles(styleOwnerId);
		model.addAttribute("styleList", userStyleList);

		List<com.w3llspring.fhpb.web.model.Match> recentMatches;
		if (canEditDisplayName) {
			RecentMatchesPage recentMatchesPageSlice = loadRecentMatchesPage(
					styleOwner,
					recentMatchesPage,
					ACCOUNT_RECENT_MATCHES_PAGE_SIZE);
			recentMatches = recentMatchesPageSlice.matches();
			model.addAttribute("recentMatchesPage", recentMatchesPageSlice.pageNumber());
			model.addAttribute("recentMatchesPageSize", recentMatchesPageSlice.pageSize());
			model.addAttribute("recentMatchesTotalPages", recentMatchesPageSlice.totalPages());
			model.addAttribute("recentMatchesStandalonePage", false);
			applyRecentMatchesPaginationModel(model, recentMatchesPageSlice.pageNumber(), recentMatchesPageSlice.totalPages());
		} else {
			recentMatches = Collections.emptyList();
		}
		model.addAttribute("recentMatches", recentMatches);

		if (canEditDisplayName) {
			// Use MatchRowModelBuilder to compute confirmer/pending/editable attributes for these recent matches.
			var mrModel = matchRowModelBuilder.buildFor(currentUser, recentMatches);
			model.addAttribute("confirmerByMatchId", mrModel.getConfirmerByMatchId());
			model.addAttribute("casualAutoConfirmedByMatchId", mrModel.getCasualAutoConfirmedByMatchId());
			model.addAttribute("pendingByMatchId", mrModel.getPendingByMatchId());
			model.addAttribute("confirmableMatchIds", mrModel.getConfirmableMatchIds());
			model.addAttribute("editableByMatchId", mrModel.getEditableByMatchId());
			model.addAttribute("deletableByMatchId", mrModel.getDeletableByMatchId());
			model.addAttribute("nullifyRequestableByMatchId", mrModel.getNullifyRequestableByMatchId());
			model.addAttribute("nullifyApprovableByMatchId", mrModel.getNullifyApprovableByMatchId());
			model.addAttribute("nullifyWaitingOnOpponentByMatchId", mrModel.getNullifyWaitingOnOpponentByMatchId());
		}

		Long styleVoterId = currentUser.getId();

		if (userStyleRepo.findIfVoted(styleOwnerId, styleVoterId) > 0) {
			model.addAttribute("canVote", false);
		} else {
			model.addAttribute("canVote", true);
		}

		model.addAttribute("canEditDisplayName", canEditDisplayName);
		model.addAttribute("profilePublicCode", canEditDisplayName ? styleOwner.getPublicCode() : null);
		model.addAttribute("displayNameMaxLength", User.MAX_NICKNAME_LENGTH);
		model.addAttribute("courtNameMaxLength", UserCourtName.MAX_ALIAS_LENGTH);
		if (canEditDisplayName) {
			populateCourtNameModel(styleOwner, model);
			populateBadgeModel(styleOwner, model);
		} else {
			model.addAttribute("courtNames", Collections.emptyList());
			model.addAttribute("ladderOptions", Collections.emptyList());
			populateEmptyBadgeModel(model);
		}
		return "auth/userDetails";
	}

	private String buildAccountPageTitle(String displayName) {
		if (!StringUtils.hasText(displayName)) {
			return "Account";
		}
		return displayName + "'s Account";
	}

	private RecentMatchesPage loadRecentMatchesPage(User user, Integer requestedPage, int requestedSize) {
		int pageSize = Math.max(1, requestedSize);
		int safeRequestedPage = requestedPage != null ? Math.max(0, requestedPage) : 0;
		long totalElements = matchRepo.countParticipantMatchesIncludingNullified(user);
		int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);
		int pageNumber = Math.max(0, Math.min(safeRequestedPage, totalPages - 1));

		List<com.w3llspring.fhpb.web.model.Match> matches = Collections.emptyList();
		if (totalElements > 0) {
			List<Long> recentMatchIds = matchRepo.findParticipantMatchIdsOrderByPlayedAtDescIncludingNullified(
					user,
					PageRequest.of(pageNumber, pageSize));
			matches = recentMatchIds.isEmpty()
					? Collections.emptyList()
					: matchRepo.findAllByIdInWithUsers(recentMatchIds).stream()
							.sorted(Comparator.comparing(this::matchTimeline).reversed())
							.collect(Collectors.toList());
		}

		return new RecentMatchesPage(matches, pageNumber, pageSize, totalPages, totalElements);
	}

	private void applyRecentMatchesPaginationModel(Model model, int currentPage, int totalPages) {
		PaginationWindowSupport.PaginationWindow pagination = PaginationWindowSupport.buildWindow(currentPage, totalPages);
		model.addAttribute("recentMatchesPageNumbers", pagination.pageNumbers());
		model.addAttribute("recentMatchesShowFirstPage", pagination.showFirstPage());
		model.addAttribute("recentMatchesShowLastPage", pagination.showLastPage());
		model.addAttribute("recentMatchesShowLeadingEllipsis", pagination.showLeadingEllipsis());
		model.addAttribute("recentMatchesShowTrailingEllipsis", pagination.showTrailingEllipsis());
		model.addAttribute("recentMatchesJumpBackPage", pagination.jumpBackPage());
		model.addAttribute("recentMatchesJumpForwardPage", pagination.jumpForwardPage());
	}

	private String buildAccountPageSubtitle(String displayName, boolean canEditDisplayName) {
		if (!canEditDisplayName) {
			return "";
		}
		if (!StringUtils.hasText(displayName)) {
			return "Update your profile, names, and preferences.";
		}
		return "Update " + displayName + "'s profile, names, and preferences.";
	}

	private void requireSharedActiveLadderOrSelf(User currentUser, User styleOwner) {
		if (currentUser == null || currentUser.getId() == null || styleOwner == null || styleOwner.getId() == null) {
			throw new SecurityException("User details unavailable.");
		}
		if (Objects.equals(currentUser.getId(), styleOwner.getId())) {
			return;
		}
		if (!sharesActiveLadder(currentUser.getId(), styleOwner.getId())) {
			throw new SecurityException("User details unavailable.");
		}
	}

	private boolean sharesActiveLadder(Long currentUserId, Long otherUserId) {
		Set<Long> currentLadderIds = ladderMembershipRepository
				.findByUserIdAndState(currentUserId, LadderMembership.State.ACTIVE)
				.stream()
				.map(LadderMembership::getLadderConfig)
				.filter(Objects::nonNull)
				.map(LadderConfig::getId)
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(HashSet::new));
		if (currentLadderIds.isEmpty()) {
			return false;
		}
		return ladderMembershipRepository.findByUserIdAndState(otherUserId, LadderMembership.State.ACTIVE)
				.stream()
				.map(LadderMembership::getLadderConfig)
				.filter(Objects::nonNull)
				.map(LadderConfig::getId)
				.filter(Objects::nonNull)
				.anyMatch(currentLadderIds::contains);
	}

	private void populateCourtNameModel(User styleOwner, Model model) {
		List<UserCourtName> aliases = userCourtNameRepo.findByUser_Id(styleOwner.getId());
		
		// Check for phonetically similar court names
		Map<Long, CourtNameSimilarityService.SimilarityWarning> warnings = 
				courtNameSimilarityService.checkSimilarities(styleOwner, aliases);
		
		List<CourtNameRow> rows = aliases.stream()
				.map(alias -> {
					LadderConfig ladderConfig = alias.getLadderConfig();
					Long ladderId = ladderConfig != null ? ladderConfig.getId() : null;
					String ladderTitle = ladderConfig != null ? ladderConfig.getTitle() : null;
					
					// Get similarity warning if present
					String warningMessage = null;
					CourtNameSimilarityService.SimilarityWarning warning = warnings.get(alias.getId());
					if (warning != null) {
						warningMessage = warning.formatWarning();
					}
					
					return new CourtNameRow(alias.getId(), alias.getAlias(), ladderId, ladderTitle, warningMessage);
				})
				.sorted(Comparator
						.comparing(CourtNameRow::hasLadder)
						.thenComparing(CourtNameRow::getScopeLabel, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(CourtNameRow::getAlias, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());
		model.addAttribute("courtNames", rows);

		List<CourtNameOption> ladderChoices = ladderMembershipRepository
				.findByUserIdAndState(styleOwner.getId(), LadderMembership.State.ACTIVE)
				.stream()
				.map(LadderMembership::getLadderConfig)
				.filter(Objects::nonNull)
				.map(cfg -> new CourtNameOption(cfg.getId(), cfg.getTitle()))
				.sorted(Comparator.comparing(CourtNameOption::getLabel, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toCollection(ArrayList::new));
		model.addAttribute("ladderOptions", ladderChoices);
	}

	private void populateBadgeModel(User styleOwner, Model model) {
		if (styleOwner == null || styleOwner.getId() == null) {
			populateEmptyBadgeModel(model);
			return;
		}

		List<UserTrophy> ownedTrophies = userTrophyRepository
				.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(styleOwner.getId());
		List<Trophy> alwaysAvailableBadges = loadAlwaysAvailableBadges();
		List<BadgeOption> badgeOptions = new ArrayList<>();
		Set<Long> seenTrophyIds = new HashSet<>();
		ZoneId displayZone = resolveUserZone(styleOwner);
		for (UserTrophy userTrophy : ownedTrophies) {
			if (userTrophy == null || userTrophy.getTrophy() == null || userTrophy.getTrophy().getId() == null) {
				continue;
			}
			Trophy trophy = userTrophy.getTrophy();
			if (!isRenderableBadgeTrophy(trophy) || !seenTrophyIds.add(trophy.getId())) {
				continue;
			}
			BadgeOption option = buildBadgeOption(trophy, userTrophy, displayZone, buildAwardedLabel(userTrophy, displayZone));
			if (option != null) {
				badgeOptions.add(option);
			}
		}
		for (Trophy trophy : alwaysAvailableBadges) {
			if (!isRenderableBadgeTrophy(trophy) || !seenTrophyIds.add(trophy.getId())) {
				continue;
			}
			BadgeOption option = buildBadgeOption(trophy, null, displayZone, "Always available");
			if (option != null) {
				badgeOptions.add(option);
			}
		}
		model.addAttribute("badgeOptions", badgeOptions);
		model.addAttribute("selectedBadgeSlot1Id", styleOwner.getBadgeSlot1TrophyId());
		model.addAttribute("selectedBadgePreviews", resolveSelectedBadgePreviews(styleOwner, badgeOptions));
	}

	private void populateEmptyBadgeModel(Model model) {
		model.addAttribute("badgeOptions", Collections.emptyList());
		model.addAttribute("selectedBadgeSlot1Id", null);
		model.addAttribute("selectedBadgePreviews", Collections.emptyList());
	}

	private List<BadgeOption> resolveSelectedBadgePreviews(User styleOwner, List<BadgeOption> badgeOptions) {
		Map<Long, BadgeOption> optionsById = badgeOptions == null
				? Collections.emptyMap()
				: badgeOptions.stream().collect(Collectors.toMap(BadgeOption::getTrophyId, option -> option, (left, right) -> left));
		List<BadgeOption> previews = new ArrayList<>(1);
		addSelectedBadgePreview(previews, optionsById, styleOwner.getBadgeSlot1TrophyId());
		return previews;
	}

	private void addSelectedBadgePreview(List<BadgeOption> previews, Map<Long, BadgeOption> optionsById, Long trophyId) {
		if (trophyId == null) {
			return;
		}
		BadgeOption option = optionsById.get(trophyId);
		if (option != null) {
			previews.add(option);
		}
	}

	private BadgeOption buildBadgeOption(Trophy trophy, UserTrophy userTrophy, ZoneId displayZone, String awardedLabel) {
		if (!isRenderableBadgeTrophy(trophy)) {
			return null;
		}
		int awardCount = userTrophy != null ? Math.max(userTrophy.getAwardCount(), 1) : 0;
		String pickerLabel = buildBadgeLabel(trophy, awardCount);
		BadgeView badgeView = TrophyBadgeSupport.badgeView(trophy);
		String badgeLabel = badgeView != null && StringUtils.hasText(badgeView.getLabel())
				? badgeView.getLabel()
				: (StringUtils.hasText(trophy.getTitle()) ? trophy.getTitle() : "Trophy");
		String badgeTitle = badgeView != null && StringUtils.hasText(badgeView.getTitle())
				? badgeView.getTitle()
				: pickerLabel;
		String groupLabel = buildBadgeGroupLabel(trophy);
		return new BadgeOption(
				trophy.getId(),
				pickerLabel,
				badgeLabel,
				badgeTitle,
				TrophyBadgeSupport.badgeUrl(trophy.getId()),
				trophy.getUnlockCondition(),
				groupLabel,
				awardedLabel,
				buildBadgeSearchText(trophy, userTrophy, pickerLabel, badgeLabel, badgeTitle, groupLabel, awardedLabel, displayZone));
	}

	private String buildBadgeLabel(Trophy trophy, int awardCount) {
		String contextLabel = TrophyBadgeSupport.badgeContextLabel(trophy);
		String title = StringUtils.hasText(trophy.getTitle()) ? trophy.getTitle() : "Trophy";
		String baseLabel = StringUtils.hasText(contextLabel) ? contextLabel + " - " + title : title;
		if (awardCount > 1) {
			return baseLabel + " x" + awardCount;
		}
		return baseLabel;
	}

	private String buildBadgeGroupLabel(Trophy trophy) {
		if (trophy == null) {
			return null;
		}
		if (trophy.getSeason() != null
				&& trophy.getSeason().getLadderConfig() != null
				&& StringUtils.hasText(trophy.getSeason().getLadderConfig().getTitle())) {
			return trophy.getSeason().getLadderConfig().getTitle().trim();
		}
		if (trophy.isBadgeSelectableByAll()) {
			String contextLabel = TrophyBadgeSupport.badgeContextLabel(trophy);
			return StringUtils.hasText(contextLabel) ? contextLabel : "Profile badge";
		}
		return null;
	}

	private String buildAwardedLabel(UserTrophy userTrophy, ZoneId displayZone) {
		LocalDate awardDate = resolveAwardDate(userTrophy, displayZone);
		if (awardDate == null) {
			return null;
		}
		if (userTrophy != null && userTrophy.getAwardCount() > 1) {
			return "Last awarded " + AWARD_DISPLAY_DATE.format(awardDate);
		}
		return "Awarded " + AWARD_DISPLAY_DATE.format(awardDate);
	}

	private String buildBadgeSearchText(Trophy trophy,
			UserTrophy userTrophy,
			String pickerLabel,
			String badgeLabel,
			String badgeTitle,
			String groupLabel,
			String awardedLabel,
			ZoneId displayZone) {
		StringBuilder search = new StringBuilder();
		appendSearchValue(search, pickerLabel);
		appendSearchValue(search, badgeLabel);
		appendSearchValue(search, badgeTitle);
		appendSearchValue(search, groupLabel);
		appendSearchValue(search, trophy != null ? trophy.getTitle() : null);
		appendSearchValue(search, trophy != null ? trophy.getSummary() : null);
		appendSearchValue(search, trophy != null ? trophy.getUnlockCondition() : null);
		appendSearchValue(search, trophy != null && trophy.getSeason() != null ? trophy.getSeason().getName() : null);
		appendSearchValue(search, trophy != null
				&& trophy.getSeason() != null
				&& trophy.getSeason().getLadderConfig() != null
				? trophy.getSeason().getLadderConfig().getTitle()
				: null);
		appendSearchValue(search, userTrophy != null ? userTrophy.getAwardedReason() : null);
		appendSearchValue(search, userTrophy != null ? userTrophy.getMetadata() : null);
		appendSearchValue(search, awardedLabel);
		appendSearchDateTokens(search, userTrophy != null ? userTrophy.getFirstAwardedAt() : null, displayZone);
		appendSearchDateTokens(search, userTrophy != null ? userTrophy.getAwardedAt() : null, displayZone);
		appendSearchDateTokens(search, userTrophy != null ? userTrophy.getLastAwardedAt() : null, displayZone);
		return search.toString().trim();
	}

	private boolean isRenderableBadgeTrophy(Trophy trophy) {
		return trophy != null && trophy.getId() != null && trophy.hasRenderableBadgeArt();
	}

	private List<Trophy> loadAlwaysAvailableBadges() {
		if (trophyRepository == null) {
			return Collections.emptyList();
		}
		return trophyRepository.findByBadgeSelectableByAllTrueOrderByDisplayOrderAscIdAsc();
	}

	private void appendSearchDateTokens(StringBuilder search, Instant timestamp, ZoneId displayZone) {
		if (timestamp == null || search == null) {
			return;
		}
		LocalDate date = timestamp.atZone(displayZone != null ? displayZone : DEFAULT_TIME_ZONE).toLocalDate();
		appendSearchValue(search, DateTimeFormatter.ISO_LOCAL_DATE.format(date));
		appendSearchValue(search, SEARCH_SLASH_DATE.format(date));
		appendSearchValue(search, SEARCH_PADDED_SLASH_DATE.format(date));
		appendSearchValue(search, SEARCH_MONTH_DAY_YEAR.format(date));
		appendSearchValue(search, SEARCH_FULL_MONTH_DAY_YEAR.format(date));
		appendSearchValue(search, SEARCH_MONTH_YEAR.format(date));
		appendSearchValue(search, SEARCH_FULL_MONTH_YEAR.format(date));
		appendSearchValue(search, String.valueOf(date.getYear()));
	}

	private void appendSearchValue(StringBuilder search, String value) {
		if (search == null || !StringUtils.hasText(value)) {
			return;
		}
		if (!search.isEmpty()) {
			search.append(' ');
		}
		search.append(value.trim());
	}

	private LocalDate resolveAwardDate(UserTrophy userTrophy, ZoneId displayZone) {
		if (userTrophy == null) {
			return null;
		}
		Instant timestamp = userTrophy.getLastAwardedAt() != null
				? userTrophy.getLastAwardedAt()
				: (userTrophy.getAwardedAt() != null ? userTrophy.getAwardedAt() : userTrophy.getFirstAwardedAt());
		if (timestamp == null) {
			return null;
		}
		return timestamp.atZone(displayZone != null ? displayZone : DEFAULT_TIME_ZONE).toLocalDate();
	}

	private ZoneId resolveUserZone(User user) {
		if (user == null || !StringUtils.hasText(user.getTimeZone())) {
			return DEFAULT_TIME_ZONE;
		}
		try {
			return ZoneId.of(user.getTimeZone().trim());
		} catch (Exception ex) {
			return DEFAULT_TIME_ZONE;
		}
	}

	@PostMapping("/account/court-names/add")
	public String addCourtName(@RequestParam("alias") String alias,
			@RequestParam(value = "ladderConfigId", required = false) String ladderConfigIdParam,
			RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User currentUser = userDetails.getUserObject();

		final String trimmedAlias;
		try {
			trimmedAlias = InputValidation.requireCourtName(alias);
		} catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		LadderResolution ladderResolution = resolveLadderSelection(ladderConfigIdParam, currentUser);
		if (ladderResolution.hasError()) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", ladderResolution.getErrorMessage());
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		LadderConfig ladderConfig = ladderResolution.getLadderConfig();
		Optional<UserCourtName> duplicate = findDuplicateAlias(currentUser, trimmedAlias, ladderConfig);
	if (duplicate.isPresent()) {
	    redirectAttributes.addFlashAttribute("toastLevel", "info");
	    redirectAttributes.addFlashAttribute("toastMessage",
		    "You already have that court name for the selected ladder.");
			return redirectToUserDetails(currentUser, redirectAttributes);
		}
		if (hasReachedCourtNameScopeLimit(currentUser, ladderConfig)) {
			redirectAttributes.addFlashAttribute("toastLevel", "warning");
			redirectAttributes.addFlashAttribute("toastMessage", buildCourtNameLimitMessage(ladderConfig));
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		User managedUser = userRepo.findById(currentUser.getId())
				.orElseThrow(() -> new IllegalStateException("User not found."));

		UserCourtName record = new UserCourtName();
		record.setUser(managedUser);
		record.setAlias(trimmedAlias);
		record.setLadderConfig(ladderConfig);
		userCourtNameRepo.save(record);

		redirectAttributes.addFlashAttribute("toastLevel", "success");
		redirectAttributes.addFlashAttribute("toastMessage", "Court name saved.");
		return redirectToUserDetails(currentUser, redirectAttributes);
	}

	@PostMapping("/account/court-names/update")
	public String updateCourtName(@RequestParam("id") Long recordId,
			@RequestParam("alias") String alias,
			@RequestParam(value = "ladderConfigId", required = false) String ladderConfigIdParam,
			RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User currentUser = userDetails.getUserObject();

		Optional<UserCourtName> recordOpt = userCourtNameRepo.findByIdAndUser_Id(recordId, currentUser.getId());
		if (recordOpt.isEmpty()) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", "Court name not found.");
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		final String trimmedAlias;
		try {
			trimmedAlias = InputValidation.requireCourtName(alias);
		} catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		LadderResolution ladderResolution = resolveLadderSelection(ladderConfigIdParam, currentUser);
		if (ladderResolution.hasError()) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", ladderResolution.getErrorMessage());
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		LadderConfig ladderConfig = ladderResolution.getLadderConfig();
		UserCourtName record = recordOpt.get();

		Optional<UserCourtName> duplicate = findDuplicateAlias(currentUser, trimmedAlias, ladderConfig);
	if (duplicate.isPresent() && !duplicate.get().getId().equals(record.getId())) {
	    redirectAttributes.addFlashAttribute("toastLevel", "danger");
	    redirectAttributes.addFlashAttribute("toastMessage",
		    "Another court name already uses that alias for the selected ladder.");
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		boolean aliasUnchanged = trimmedAlias.equals(record.getAlias());
		boolean ladderUnchanged = (record.getLadderConfig() == null && ladderConfig == null)
				|| (record.getLadderConfig() != null && ladderConfig != null
						&& Objects.equals(record.getLadderConfig().getId(), ladderConfig.getId()));

		if (aliasUnchanged && ladderUnchanged) {
			redirectAttributes.addFlashAttribute("toastLevel", "info");
			redirectAttributes.addFlashAttribute("toastMessage", "No changes to save.");
			return redirectToUserDetails(currentUser, redirectAttributes);
		}
		if (!ladderUnchanged && hasReachedCourtNameScopeLimit(currentUser, ladderConfig)) {
			redirectAttributes.addFlashAttribute("toastLevel", "warning");
			redirectAttributes.addFlashAttribute("toastMessage", buildCourtNameLimitMessage(ladderConfig));
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		record.setAlias(trimmedAlias);
		record.setLadderConfig(ladderConfig);
		userCourtNameRepo.save(record);

		redirectAttributes.addFlashAttribute("toastLevel", "success");
		redirectAttributes.addFlashAttribute("toastMessage", "Court name updated.");
		return redirectToUserDetails(currentUser, redirectAttributes);
	}

	@PostMapping("/account/court-names/delete")
	public String deleteCourtName(@RequestParam("id") Long recordId, RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User currentUser = userDetails.getUserObject();

		Optional<UserCourtName> recordOpt = userCourtNameRepo.findByIdAndUser_Id(recordId, currentUser.getId());
		if (recordOpt.isEmpty()) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", "Court name not found.");
			return redirectToUserDetails(currentUser, redirectAttributes);
		}

		userCourtNameRepo.delete(recordOpt.get());
		redirectAttributes.addFlashAttribute("toastLevel", "success");
		redirectAttributes.addFlashAttribute("toastMessage", "Court name removed.");
		return redirectToUserDetails(currentUser, redirectAttributes);
	}

	@PostMapping("/account/badges")
	public String updateBadges(@RequestParam(value = "badgeSlot1TrophyId", required = false) String badgeSlot1TrophyId,
			RedirectAttributes redirectAttributes) {
		BadgeUpdateResult result = processBadgeUpdate(currentAuthenticatedUser(), badgeSlot1TrophyId);
		redirectAttributes.addFlashAttribute("toastLevel", result.toastLevel());
		redirectAttributes.addFlashAttribute("toastMessage", result.toastMessage());
		return "redirect:" + result.redirectPath();
	}

	@PostMapping(value = "/account/badges", headers = "X-Requested-With=XMLHttpRequest", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<BadgeUpdateResponse> updateBadgesAjax(
			@RequestParam(value = "badgeSlot1TrophyId", required = false) String badgeSlot1TrophyId) {
		BadgeUpdateResult result = processBadgeUpdate(currentAuthenticatedUser(), badgeSlot1TrophyId);
		String redirectUrl = ACCOUNT_PATH.equals(result.redirectPath()) ? null : result.redirectPath();
		return ResponseEntity.status(result.status())
				.body(new BadgeUpdateResponse(result.success(), result.toastLevel(), result.toastMessage(), redirectUrl));
	}

	private Long parseBadgeSelection(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Long.valueOf(value.trim());
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid badge selection.");
		}
	}

	private Trophy resolveSelectableBadgeSelection(Long trophyId, Map<Long, Trophy> selectableTrophiesById) {
		if (trophyId == null || selectableTrophiesById == null || selectableTrophiesById.isEmpty()) {
			return null;
		}
		return selectableTrophiesById.get(trophyId);
	}

	private Map<Long, Trophy> loadSelectableBadgeTrophies(Long userId) {
		Map<Long, Trophy> selectableTrophies = new java.util.LinkedHashMap<>();
		if (userId != null) {
			userTrophyRepository.findByUserIdWithTrophyAndSeasonOrderBySeasonStartDateDesc(userId)
					.stream()
					.map(UserTrophy::getTrophy)
					.filter(this::isRenderableBadgeTrophy)
					.forEach(trophy -> selectableTrophies.putIfAbsent(trophy.getId(), trophy));
		}
		for (Trophy trophy : loadAlwaysAvailableBadges()) {
			if (isRenderableBadgeTrophy(trophy)) {
				selectableTrophies.putIfAbsent(trophy.getId(), trophy);
			}
		}
		return selectableTrophies;
	}

	private BadgeUpdateResult processBadgeUpdate(User currentUser, String badgeSlot1TrophyId) {
		final Long selectedSlot1Id;
		try {
			selectedSlot1Id = parseBadgeSelection(badgeSlot1TrophyId);
		} catch (IllegalArgumentException ex) {
			return badgeUpdateFailure(HttpStatus.BAD_REQUEST, ex.getMessage(), ACCOUNT_PATH);
		}

		Map<Long, Trophy> selectableTrophiesById = loadSelectableBadgeTrophies(currentUser != null ? currentUser.getId() : null);
		Trophy slot1 = resolveSelectableBadgeSelection(selectedSlot1Id, selectableTrophiesById);
		if (selectedSlot1Id != null && slot1 == null) {
			return badgeUpdateFailure(HttpStatus.BAD_REQUEST,
					"You can only equip a badge you've unlocked or one that's always available.",
					ACCOUNT_PATH);
		}

		final User persistedUser;
		try {
			persistedUser = userAccountSettingsService.updateBadgeSlot1(currentUser.getId(), slot1);
		} catch (IllegalArgumentException ex) {
			return badgeUpdateFailure(HttpStatus.NOT_FOUND, "User not found.", HOME_PATH);
		}
		syncSessionUserAccountSettings(currentUser, persistedUser);
		return new BadgeUpdateResult(true, HttpStatus.OK, "success", "Name badge updated.", ACCOUNT_PATH);
	}

	private BadgeUpdateResult badgeUpdateFailure(HttpStatus status, String message, String redirectPath) {
		return new BadgeUpdateResult(false, status, "danger", message, redirectPath);
	}

	private User currentAuthenticatedUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		return userDetails.getUserObject();
	}

	private Optional<UserCourtName> findDuplicateAlias(User user, String alias, LadderConfig ladderConfig) {
		if (!StringUtils.hasText(alias)) {
			return Optional.empty();
		}
		if (ladderConfig == null) {
			return userCourtNameRepo.findByUser_IdAndAliasIgnoreCaseAndLadderConfigIsNull(user.getId(), alias);
		}
		return userCourtNameRepo.findByUser_IdAndAliasIgnoreCaseAndLadderConfig_Id(user.getId(), alias,
				ladderConfig.getId());
	}

	private boolean hasReachedCourtNameScopeLimit(User user, LadderConfig ladderConfig) {
		if (user == null || user.getId() == null) {
			return false;
		}
		long count = countCourtNamesInScope(user.getId(), ladderConfig);
		return count >= UserCourtName.MAX_ALIASES_PER_SCOPE;
	}

	private long countCourtNamesInScope(Long userId, LadderConfig ladderConfig) {
		if (ladderConfig == null || ladderConfig.getId() == null) {
			return userCourtNameRepo.countByUser_IdAndLadderConfigIsNull(userId);
		}
		return userCourtNameRepo.countByUser_IdAndLadderConfig_Id(userId, ladderConfig.getId());
	}

	private String buildCourtNameLimitMessage(LadderConfig ladderConfig) {
		String scope = (ladderConfig != null && StringUtils.hasText(ladderConfig.getTitle()))
				? "'" + ladderConfig.getTitle() + "'"
				: "all ladders";
		return "You can save up to " + UserCourtName.MAX_ALIASES_PER_SCOPE
				+ " court names for " + scope + ". Remove one before adding another.";
	}

	private LadderResolution resolveLadderSelection(String ladderConfigIdParam, User currentUser) {
		if (!StringUtils.hasText(ladderConfigIdParam)) {
			return LadderResolution.global();
		}

		final Long ladderId;
		try {
			ladderId = Long.valueOf(ladderConfigIdParam);
		} catch (NumberFormatException ex) {
			return LadderResolution.error("Invalid ladder selection.");
		}

		return ladderMembershipRepository.findByLadderConfigIdAndUserId(ladderId, currentUser.getId())
				.filter(membership -> membership.getState() == LadderMembership.State.ACTIVE)
				.map(LadderMembership::getLadderConfig)
				.map(LadderResolution::specific)
				.orElseGet(() -> LadderResolution
						.error("You can only attach a court name to ladders you're actively a member of."));
	}

	private String safeName(User u) {
		return com.w3llspring.fhpb.web.util.UserPublicName.forUserOrGuest(u);
	}

	private Instant matchTimeline(com.w3llspring.fhpb.web.model.Match match) {
		if (match == null) {
			return Instant.EPOCH;
		}
		if (match.getPlayedAt() != null) {
			return match.getPlayedAt();
		}
		if (match.getCreatedAt() != null) {
			return match.getCreatedAt();
		}
		return Instant.EPOCH;
	}

	private record RecentMatchesPage(
			List<com.w3llspring.fhpb.web.model.Match> matches,
			int pageNumber,
			int pageSize,
			int totalPages,
			long totalElements) {
	}

	private String redirectToUserDetails(User user, RedirectAttributes redirectAttributes) {
		return "redirect:/account";
	}

	private User resolveProfileUser(String memberCode, User currentUser) {
		if (currentUser == null) {
			return null;
		}
		if (!StringUtils.hasText(memberCode) || Objects.equals(memberCode, currentUser.getPublicCode())) {
			return currentUser;
		}
		return userRepo.findByPublicCode(memberCode).orElse(null);
	}

	private User refreshAuthenticatedUser(User sessionUser) {
		if (sessionUser == null || sessionUser.getId() == null) {
			return sessionUser;
		}
		return userRepo.findById(sessionUser.getId())
				.map(dbUser -> {
					sessionUser.setNickName(dbUser.getNickName());
					sessionUser.setAdmin(dbUser.isAdmin());
					sessionUser.setMaxOwnedLadders(dbUser.getMaxOwnedLadders());
					sessionUser.setLastDisplayNameChangeAt(dbUser.getLastDisplayNameChangeAt());
					sessionUser.setMeetupsEmailOptIn(dbUser.isMeetupsEmailOptIn());
					sessionUser.setTimeZone(dbUser.getTimeZone());
					sessionUser.setPublicCode(dbUser.getPublicCode());
					return dbUser;
				})
				.orElse(sessionUser);
	}

	private static class CourtNameRow {
		private final Long id;
		private final String alias;
		private final Long ladderId;
		private final String ladderTitle;
		private final String similarityWarning;

		CourtNameRow(Long id, String alias, Long ladderId, String ladderTitle, String similarityWarning) {
			this.id = id;
			this.alias = alias;
			this.ladderId = ladderId;
			this.ladderTitle = ladderTitle;
			this.similarityWarning = similarityWarning;
		}

		public Long getId() {
			return id;
		}

		public String getAlias() {
			return alias;
		}

		public Long getLadderId() {
			return ladderId;
		}

		public String getLadderTitle() {
			return ladderTitle;
		}

		public String getSimilarityWarning() {
			return similarityWarning;
		}

		public boolean hasSimilarityWarning() {
			return StringUtils.hasText(similarityWarning);
		}

		public boolean hasLadder() {
			return ladderId != null;
		}

		public String getScopeLabel() {
			return hasLadder() && StringUtils.hasText(ladderTitle) ? ladderTitle : "All ladders";
		}
	}

	private static class CourtNameOption {
		private final Long ladderId;
		private final String label;

		CourtNameOption(Long ladderId, String label) {
			this.ladderId = ladderId;
			this.label = label;
		}

		public Long getLadderId() {
			return ladderId;
		}

		public String getLabel() {
			return label;
		}
	}

	private static class BadgeOption {
		private final Long trophyId;
		private final String pickerLabel;
		private final String badgeLabel;
		private final String badgeTitle;
		private final String badgeUrl;
		private final String unlockCondition;
		private final String groupLabel;
		private final String awardedLabel;
		private final String searchText;

		BadgeOption(Long trophyId, String pickerLabel, String badgeLabel, String badgeTitle, String badgeUrl,
				String unlockCondition, String groupLabel, String awardedLabel, String searchText) {
			this.trophyId = trophyId;
			this.pickerLabel = pickerLabel;
			this.badgeLabel = badgeLabel;
			this.badgeTitle = badgeTitle;
			this.badgeUrl = badgeUrl;
			this.unlockCondition = unlockCondition;
			this.groupLabel = groupLabel;
			this.awardedLabel = awardedLabel;
			this.searchText = searchText;
		}

		public Long getTrophyId() {
			return trophyId;
		}

		public String getPickerLabel() {
			return pickerLabel;
		}

		public String getBadgeLabel() {
			return badgeLabel;
		}

		public String getBadgeTitle() {
			return badgeTitle;
		}

		public String getBadgeUrl() {
			return badgeUrl;
		}

		public String getUnlockCondition() {
			return unlockCondition;
		}

		public String getGroupLabel() {
			return groupLabel;
		}

		public String getAwardedLabel() {
			return awardedLabel;
		}

		public String getSearchText() {
			return searchText;
		}
	}

	private static class LadderResolution {
		private final LadderConfig ladderConfig;
		private final String errorMessage;

		private LadderResolution(LadderConfig ladderConfig, String errorMessage) {
			this.ladderConfig = ladderConfig;
			this.errorMessage = errorMessage;
		}

		static LadderResolution global() {
			return new LadderResolution(null, null);
		}

		static LadderResolution specific(LadderConfig ladderConfig) {
			return new LadderResolution(ladderConfig, null);
		}

		static LadderResolution error(String message) {
			return new LadderResolution(null, message);
		}

		boolean hasError() {
			return errorMessage != null;
		}

		LadderConfig getLadderConfig() {
			return ladderConfig;
		}

		String getErrorMessage() {
			return errorMessage;
		}
	}

	@PostMapping("/account/app-ui")
	public String updateAppUi(@RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
			RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
			return "redirect:/login";
		}
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User currentUser = userDetails.getUserObject();

		final User persistedUser;
		try {
			persistedUser = userAccountSettingsService.enableAppUi(currentUser.getId());
		} catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", "User not found.");
			return "redirect:/home";
		}
		syncSessionUserAccountSettings(currentUser, persistedUser);

		redirectAttributes.addFlashAttribute("toastLevel", "success");
		redirectAttributes.addFlashAttribute("toastMessage",
				(enabled ? "App-style home enabled." : "App-style home is now the default."));
		return redirectToUserDetails(currentUser, redirectAttributes);
	}

	@PostMapping("/account/time-zone")
	public String updateTimeZone(@RequestParam(value = "timeZone", required = false) String timeZone,
			RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
			return "redirect:/login";
		}
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User currentUser = userDetails.getUserObject();

		String trimmed = timeZone != null ? timeZone.trim() : "";
		if (!trimmed.isEmpty()) {
			try {
				ZoneId.of(trimmed);
			} catch (Exception ex) {
				redirectAttributes.addFlashAttribute("toastLevel", "danger");
				redirectAttributes.addFlashAttribute("toastMessage", "Invalid time zone.");
				return redirectToUserDetails(currentUser, redirectAttributes);
			}
		}

		final User persistedUser;
		try {
			persistedUser = userAccountSettingsService.updateTimeZone(currentUser.getId(), trimmed.isEmpty() ? null : trimmed);
		} catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", "User not found.");
			return "redirect:/home";
		}
		syncSessionUserAccountSettings(currentUser, persistedUser);

		redirectAttributes.addFlashAttribute("toastLevel", "success");
		redirectAttributes.addFlashAttribute("toastMessage", "Time zone updated.");
		return redirectToUserDetails(currentUser, redirectAttributes);
	}

	@PostMapping("/account/styles/vote")
	public String voteUserStyle(@RequestParam(value = "member", required = true) String memberCode,
			@RequestParam(value = "style", required = true) String style) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		User sessionUser = ((CustomUserDetails) authentication.getPrincipal()).getUserObject();
		User styleVoter = refreshAuthenticatedUser(sessionUser);
		User styleOwner = userRepo.findByPublicCode(memberCode).orElse(null);
		if (styleOwner == null) {
			return "redirect:/account";
		}
		requireSharedActiveLadderOrSelf(styleVoter, styleOwner);
		if (userStyleRepo.findIfVoted(styleOwner.getId(), styleVoter.getId()) < 1) {
			UserStyle userStyle = new UserStyle();
			userStyle.setVotedDt(new Date());
			userStyle.setStyleName(style);
			userStyle.setStyleOwner(styleOwner);
			userStyle.setStyleVoter(styleVoter);
			userStyleRepo.saveAndFlush(userStyle);
		}
		return "redirect:/account?member=" + memberCode;
	}

	@PostMapping("/account/change-display-name")
	public String changeDisplayName(@RequestParam("currentName") String currentName,
			@RequestParam("desiredDisplayName") String desiredDisplayName,
			RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User sessionUser = userDetails.getUserObject();
		User currentUser = refreshAuthenticatedUser(sessionUser);

		final String trimmedDesired;
		try {
			trimmedDesired = InputValidation.requireDisplayName(desiredDisplayName, displayNameModerationService);
		} catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
			return "redirect:/account";
		}
		Instant now = Instant.now();
		DisplayNameChangeResult result = userIdentityService.changeDisplayName(
				currentUser.getId(),
				trimmedDesired,
				currentUser.getId(),
				now,
				Duration.ofSeconds(Math.max(0L, displayNameChangeCooldownSeconds)));

		if (result.status() == DisplayNameChangeStatus.UNCHANGED) {
			redirectAttributes.addFlashAttribute("toastLevel", "info");
			redirectAttributes.addFlashAttribute("toastMessage", "That's already the current display name.");
			return "redirect:/account";
		}
		if (result.status() == DisplayNameChangeStatus.COOLDOWN) {
			redirectAttributes.addFlashAttribute("toastLevel", "warning");
			redirectAttributes.addFlashAttribute("toastMessage",
					"You can change your display name again in "
							+ formatRemainingDisplayNameCooldown(now, result.allowedAt()) + ".");
			return "redirect:/account";
		}
		if (result.status() == DisplayNameChangeStatus.TAKEN) {
			redirectAttributes.addFlashAttribute("toastLevel", "danger");
			redirectAttributes.addFlashAttribute("toastMessage", "Display name is already taken. Try another.");
			return "redirect:/account";
		}

		User persistedUser = result.user();
		sessionUser.setNickName(persistedUser.getNickName());
		sessionUser.setLastDisplayNameChangeAt(persistedUser.getLastDisplayNameChangeAt());

		redirectAttributes.addFlashAttribute("toastLevel", "success");
		redirectAttributes.addFlashAttribute("toastMessage", "Display name updated.");
		return "redirect:/account";
	}

	private String formatRemainingDisplayNameCooldown(Instant now, Instant allowedAt) {
		if (allowedAt == null) {
			return "1m";
		}
		Duration remaining = Duration.between(now, allowedAt);
		long totalMinutes = Math.max(1L, remaining.toMinutes());
		long hours = totalMinutes / 60L;
		long minutes = totalMinutes % 60L;
		return hours > 0 ? (hours + "h " + minutes + "m") : (totalMinutes + "m");
	}

	private void syncSessionUserAccountSettings(User sessionUser, User persistedUser) {
		if (sessionUser == null || persistedUser == null) {
			return;
		}
		sessionUser.setAcknowledgedTermsAt(persistedUser.getAcknowledgedTermsAt());
		sessionUser.setAppUiEnabled(persistedUser.isAppUiEnabled());
		sessionUser.setTimeZone(persistedUser.getTimeZone());
		sessionUser.setBadgeSlot1Trophy(persistedUser.getBadgeSlot1Trophy());
		sessionUser.setBadgeSlot2Trophy(persistedUser.getBadgeSlot2Trophy());
		sessionUser.setBadgeSlot3Trophy(persistedUser.getBadgeSlot3Trophy());
	}

	public record BadgeUpdateResponse(boolean success, String level, String message, String redirectUrl) {
	}

	private record BadgeUpdateResult(boolean success,
			HttpStatus status,
			String toastLevel,
			String toastMessage,
			String redirectPath) {
	}
}
