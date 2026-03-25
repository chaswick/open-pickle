package com.w3llspring.fhpb.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.MatchState;
import com.w3llspring.fhpb.web.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ControllerEndpointAccessMatrixIntegrationTest {

  private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}/]+)}");

  private static final Set<String> PUBLIC_CONTROLLER_PATHS =
      Set.of(
          "/",
          "/login",
          "/register",
          "/registration-success",
          "/forgot-password",
          "/reset-password",
          "/meetups/unsubscribe",
          "/terms",
          "/privacy",
          "/site.webmanifest",
          "/sw.js",
          "/apple-touch-icon-precomposed.png");

  private static final Set<String> LOGGED_IN_BAD_INPUT_SKIPS =
      Set.of("POST /accept-terms", "POST /groups/start-session", "POST /logout");

  private static final Set<String> OPTIONAL_PARAM_BAD_INPUT_KEYS =
      Set.of("POST /account/app-ui", "POST /account/time-zone", "POST /account/badges");

  @Autowired private MockMvc mockMvc;

  @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;

  @Autowired private UserRepository userRepository;

  @Autowired private LadderConfigRepository ladderConfigRepository;

  @Autowired private LadderMembershipRepository ladderMembershipRepository;

  @Autowired private LadderSeasonRepository ladderSeasonRepository;

  @Autowired private MatchRepository matchRepository;

  private User viewer;
  private User sharedUser;
  private User privateGroupOwner;
  private User unrelatedOpponent;
  private LadderConfig sharedLadder;
  private LadderConfig privateLadder;
  private LadderSeason privateSeason;
  private Match privateSeasonMatch;

  @BeforeEach
  void setUp() {
    viewer = saveUser("viewer@example.com", "Viewer");
    sharedUser = saveUser("shared@example.com", "Shared");
    privateGroupOwner = saveUser("owner@example.com", "Owner");
    unrelatedOpponent = saveUser("opponent@example.com", "Opponent");

    sharedLadder = saveLadder("Shared Ladder", viewer.getId(), LadderConfig.Type.STANDARD);
    addMembership(sharedLadder, viewer, LadderMembership.Role.ADMIN);
    addMembership(sharedLadder, sharedUser, LadderMembership.Role.MEMBER);

    privateLadder =
        saveLadder("Private Ladder", privateGroupOwner.getId(), LadderConfig.Type.STANDARD);
    addMembership(privateLadder, privateGroupOwner, LadderMembership.Role.ADMIN);
    addMembership(privateLadder, unrelatedOpponent, LadderMembership.Role.MEMBER);

    privateSeason = saveSeason(privateLadder, "Private Season", LadderSeason.State.ACTIVE);
    privateSeasonMatch =
        saveMatch(privateSeason, privateGroupOwner, unrelatedOpponent, privateGroupOwner);
  }

  @Test
  void loggedOutRequestsEitherStayPublicOrGetBlockedByAuth() throws Exception {
    List<String> failures = new ArrayList<>();

    for (EndpointProbe probe : endpointProbes()) {
      MvcResult result = mockMvc.perform(buildRequest(probe, false)).andReturn();
      int status = result.getResponse().getStatus();
      String redirect = result.getResponse().getRedirectedUrl();

      if (probe.publicRoute()) {
        if (isAuthBlocked(status, redirect) && !allowsPublicLoginFailureRedirect(probe, redirect)) {
          failures.add(probe.key() + " unexpectedly required auth: " + describe(result));
        }
      } else if (!isAuthBlocked(status, redirect)) {
        failures.add(probe.key() + " was reachable while logged out: " + describe(result));
      }
    }

    assertThat(failures).as(String.join(System.lineSeparator(), failures)).isEmpty();
  }

  @Test
  void loggedInBadInputRequestsDoNotReturnServerErrors() throws Exception {
    List<String> failures = new ArrayList<>();
    List<String> skipped = new ArrayList<>();

    for (EndpointProbe probe : endpointProbes()) {
      if (!probe.supportsLoggedInBadInput()) {
        skipped.add(probe.key());
        continue;
      }

      MvcResult result =
          mockMvc
              .perform(buildRequest(probe, true).with(user(new CustomUserDetails(viewer))))
              .andReturn();

      int status = result.getResponse().getStatus();
      if (status >= 500) {
        failures.add(probe.key() + " returned " + describe(result));
      }
    }

    assertThat(new LinkedHashSet<>(skipped))
        .containsExactlyInAnyOrderElementsOf(LOGGED_IN_BAD_INPUT_SKIPS);
    assertThat(failures).as(String.join(System.lineSeparator(), failures)).isEmpty();
  }

  @Test
  void unrelatedAccountPageIsHiddenButSharedAccountStillLoadsWithoutRecentMatches()
      throws Exception {
    mockMvc
        .perform(
            get("/account")
                .param("member", privateGroupOwner.getPublicCode())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

    String html =
        mockMvc
            .perform(
                get("/account")
                    .param("member", sharedUser.getPublicCode())
                    .with(user(new CustomUserDetails(viewer))))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("Shared&#39;s Account");
    assertThat(html).doesNotContain("Recent matches");
  }

  @Test
  void ownerOnlyUserMatchEndpointsRejectOtherLoggedInUsers() throws Exception {
    mockMvc
        .perform(
            get("/users/{memberCode}/matches", sharedUser.getPublicCode())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

    mockMvc
        .perform(
            get("/users/{memberCode}/matches/export", sharedUser.getPublicCode())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));
  }

  @Test
  void nonMembersCannotOpenPrivateGroupOrSeasonLog() throws Exception {
    mockMvc
        .perform(
            get("/groups/{configId}", privateLadder.getId())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

    mockMvc
        .perform(
            get("/seasons/{seasonId}/matches", privateSeason.getId())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));

    mockMvc
        .perform(
            get("/seasons/{seasonId}/matches/export", privateSeason.getId())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(404));
  }

  @Test
  void nonParticipantsCannotOpenAnotherMatchFragment() throws Exception {
    mockMvc
        .perform(
            get("/matches/{id}/fragment", privateSeasonMatch.getId())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));
  }

  @Test
  void nonAdminsCannotCallMeetupsEmailDebugSendPending() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/meetups/email/send-pending")
                .with(csrf())
                .with(user(new CustomUserDetails(viewer))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));
  }

  private List<EndpointProbe> endpointProbes() {
    Map<String, EndpointProbe> probes = new LinkedHashMap<>();

    requestMappingHandlerMapping
        .getHandlerMethods()
        .forEach(
            (mapping, handlerMethod) -> {
              if (!isApplicationController(handlerMethod)) {
                return;
              }
              for (String pattern : normalizedPatterns(mapping)) {
                for (RequestMethod method : normalizedMethods(mapping)) {
                  EndpointProbe probe =
                      new EndpointProbe(
                          method,
                          pattern,
                          buildKey(method, pattern),
                          PUBLIC_CONTROLLER_PATHS.contains(pattern),
                          hasRequestBody(handlerMethod),
                          hasPathVariable(handlerMethod),
                          requestParams(handlerMethod));
                  probes.putIfAbsent(probe.key(), probe);
                }
              }
            });

    probes.put(
        "POST /login",
        new EndpointProbe(
            RequestMethod.POST,
            "/login",
            "POST /login",
            true,
            false,
            false,
            List.of(
                new RequestParamDescriptor("user", String.class, true),
                new RequestParamDescriptor("password", String.class, true))));
    probes.put(
        "POST /logout",
        new EndpointProbe(
            RequestMethod.POST, "/logout", "POST /logout", true, false, false, List.of()));

    return probes.values().stream().sorted(Comparator.comparing(EndpointProbe::key)).toList();
  }

  private boolean isApplicationController(HandlerMethod handlerMethod) {
    String packageName = handlerMethod.getBeanType().getPackageName();
    return packageName.startsWith("com.w3llspring.fhpb.web.controller");
  }

  private List<String> normalizedPatterns(RequestMappingInfo mapping) {
    return mapping.getPatternValues().stream().map(this::normalizePath).sorted().toList();
  }

  private List<RequestMethod> normalizedMethods(RequestMappingInfo mapping) {
    Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
    if (methods == null || methods.isEmpty()) {
      return List.of(RequestMethod.GET);
    }
    return methods.stream().sorted(Comparator.comparing(Enum::name)).toList();
  }

  private String normalizePath(String path) {
    return (path == null || path.isBlank()) ? "/" : path;
  }

  private boolean hasRequestBody(HandlerMethod handlerMethod) {
    return Arrays.stream(handlerMethod.getMethodParameters())
        .anyMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class));
  }

  private boolean hasPathVariable(HandlerMethod handlerMethod) {
    return Arrays.stream(handlerMethod.getMethodParameters())
        .anyMatch(parameter -> parameter.hasParameterAnnotation(PathVariable.class));
  }

  private List<RequestParamDescriptor> requestParams(HandlerMethod handlerMethod) {
    List<RequestParamDescriptor> params = new ArrayList<>();
    for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
      RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
      if (requestParam == null) {
        continue;
      }
      String name = requestParam.name();
      if (name == null || name.isBlank()) {
        name = requestParam.value();
      }
      if ((name == null || name.isBlank()) && parameter.getParameterName() != null) {
        name = parameter.getParameterName();
      }
      boolean required =
          requestParam.required()
              && ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue());
      params.add(new RequestParamDescriptor(name, parameter.getParameterType(), required));
    }
    return params;
  }

  private MockHttpServletRequestBuilder buildRequest(
      EndpointProbe probe, boolean loggedInBadInputMode) {
    MockHttpServletRequestBuilder builder =
        switch (probe.method()) {
          case GET -> get(materializePath(probe.pathPattern()));
          case POST -> post(materializePath(probe.pathPattern()));
          case PUT -> put(materializePath(probe.pathPattern()));
          case PATCH -> patch(materializePath(probe.pathPattern()));
          case DELETE -> delete(materializePath(probe.pathPattern()));
          default -> throw new IllegalArgumentException("Unsupported method: " + probe.method());
        };

    if (probe.method() != RequestMethod.GET) {
      builder.with(csrf());
    }

    if (probe.hasRequestBody()) {
      builder.contentType(MediaType.APPLICATION_JSON);
      builder.content("{}");
    }

    if (loggedInBadInputMode) {
      applyBadRequestParameters(builder, probe);
    }

    return builder;
  }

  private void applyBadRequestParameters(
      MockHttpServletRequestBuilder builder, EndpointProbe probe) {
    if (probe.requestParams().isEmpty()) {
      return;
    }

    boolean hasRequiredParam =
        probe.requestParams().stream().anyMatch(RequestParamDescriptor::required);
    if (hasRequiredParam) {
      return;
    }

    if (!OPTIONAL_PARAM_BAD_INPUT_KEYS.contains(probe.key())) {
      return;
    }

    for (RequestParamDescriptor descriptor : probe.requestParams()) {
      String value =
          switch (descriptor.name()) {
            case "enabled" -> "not-a-boolean";
            case "timeZone" -> "Mars/OlympusMons";
            case "badgeSlot1TrophyId" -> "not-a-number";
            default -> "invalid";
          };
      builder.param(descriptor.name(), value);
    }
  }

  private String materializePath(String pathPattern) {
    Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pathPattern);
    StringBuffer resolved = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      matcher.appendReplacement(resolved, Matcher.quoteReplacement(pathVariableValue(name)));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }

  private String pathVariableValue(String variableName) {
    String lower = variableName == null ? "" : variableName.toLowerCase();
    if (lower.contains("membercode")) {
      return "missing-member";
    }
    return "999999";
  }

  private boolean isAuthBlocked(int status, String redirectUrl) {
    if (redirectUrl != null && redirectUrl.contains("/login")) {
      return true;
    }
    return status == 401 || status == 403;
  }

  private boolean allowsPublicLoginFailureRedirect(EndpointProbe probe, String redirectUrl) {
    return "POST /login".equals(probe.key())
        && redirectUrl != null
        && redirectUrl.contains("/login?error=true");
  }

  private String describe(MvcResult result) {
    String redirect = Optional.ofNullable(result.getResponse().getRedirectedUrl()).orElse("-");
    Throwable failure = result.getResolvedException();
    String exception = failure == null ? "-" : failure.getClass().getSimpleName();
    return "status="
        + result.getResponse().getStatus()
        + ", redirect="
        + redirect
        + ", exception="
        + exception;
  }

  private String buildKey(RequestMethod method, String path) {
    return method.name() + " " + path;
  }

  private User saveUser(String email, String nickName) {
    User user = new User();
    user.setEmail(email);
    user.setNickName(nickName);
    user.setPassword("pw");
    user.setRegisteredAt(Instant.now());
    user.setAcknowledgedTermsAt(Instant.now());
    user.setMaxOwnedLadders(10);
    return userRepository.saveAndFlush(user);
  }

  private LadderConfig saveLadder(String title, Long ownerUserId, LadderConfig.Type type) {
    LadderConfig ladder = new LadderConfig();
    ladder.setTitle(title);
    ladder.setOwnerUserId(ownerUserId);
    ladder.setType(type);
    ladder.setStatus(LadderConfig.Status.ACTIVE);
    ladder.setMode(LadderConfig.Mode.ROLLING);
    ladder.setRollingEveryCount(6);
    ladder.setRollingEveryUnit(LadderConfig.CadenceUnit.WEEKS);
    ladder.setCreatedAt(Instant.now());
    ladder.setUpdatedAt(Instant.now());
    return ladderConfigRepository.saveAndFlush(ladder);
  }

  private LadderMembership addMembership(
      LadderConfig ladder, User user, LadderMembership.Role role) {
    LadderMembership membership = new LadderMembership();
    membership.setLadderConfig(ladder);
    membership.setUserId(user.getId());
    membership.setRole(role);
    membership.setState(LadderMembership.State.ACTIVE);
    membership.setJoinedAt(Instant.now());
    return ladderMembershipRepository.saveAndFlush(membership);
  }

  private LadderSeason saveSeason(LadderConfig ladder, String name, LadderSeason.State state) {
    LadderSeason season = new LadderSeason();
    season.setLadderConfig(ladder);
    season.setName(name);
    season.setState(state);
    season.setStartDate(LocalDate.now().minusDays(7));
    season.setEndDate(LocalDate.now().plusDays(7));
    season.setStartedAt(Instant.now().minusSeconds(86_400));
    season.setStartedByUserId(ladder.getOwnerUserId());
    return ladderSeasonRepository.saveAndFlush(season);
  }

  private Match saveMatch(LadderSeason season, User a1, User b1, User loggedBy) {
    Match match = new Match();
    match.setSeason(season);
    match.setA1(a1);
    match.setB1(b1);
    match.setScoreA(11);
    match.setScoreB(7);
    match.setState(MatchState.PROVISIONAL);
    match.setLoggedBy(loggedBy);
    match.setPlayedAt(Instant.now().minusSeconds(1_800));
    return matchRepository.saveAndFlush(match);
  }

  private record RequestParamDescriptor(String name, Class<?> type, boolean required) {}

  private record EndpointProbe(
      RequestMethod method,
      String pathPattern,
      String key,
      boolean publicRoute,
      boolean hasRequestBody,
      boolean hasPathVariable,
      List<RequestParamDescriptor> requestParams) {

    private boolean supportsLoggedInBadInput() {
      return !LOGGED_IN_BAD_INPUT_SKIPS.contains(key);
    }
  }
}
