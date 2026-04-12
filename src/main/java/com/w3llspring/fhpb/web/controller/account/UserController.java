package com.w3llspring.fhpb.web.controller.account;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import com.w3llspring.fhpb.web.service.GlobalLadderBootstrapService;
import com.w3llspring.fhpb.web.service.auth.PasswordPolicyService;
import com.w3llspring.fhpb.web.service.auth.RegistrationAbuseGuard;
import com.w3llspring.fhpb.web.service.auth.RegistrationFormTokenService;
import com.w3llspring.fhpb.web.service.user.DisplayNameModerationService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import com.w3llspring.fhpb.web.util.InputValidation;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class UserController {
  private static final Logger log = LoggerFactory.getLogger(UserController.class);
  private static final String[] FUN_SUFFIXES = {
    "Dinker",
    "Drop",
    "Spinner",
    "Smash",
    "Ace",
    "Rally",
    "Lob",
    "Volley",
    "Paddle",
    "Kitchen",
    "Coach",
    "Pro",
    "Champ",
    "Star",
    "Driver",
    "Backhand",
    "Stacker",
    "Lefty",
    "Paddle",
    "Out"
  };

  @Autowired private UserRepository userRepo;

  @Autowired private UserAccountSettingsService userAccountSettingsService;

  @Value("${fhpb.ladder.max-per-user:10}")
  private int defaultMaxOwnedLadders;

  @Autowired private DisplayNameModerationService displayNameModerationService;

  @Autowired private GlobalLadderBootstrapService globalLadderBootstrapService;

  @Autowired private PasswordPolicyService passwordPolicyService;

  @Autowired private RegistrationAbuseGuard registrationAbuseGuard;

  @Autowired private RegistrationFormTokenService registrationFormTokenService;

  @Autowired private AuthenticationManager authenticationManager;

  @ModelAttribute("displayNameMaxLength")
  public int displayNameMaxLength() {
    return User.MAX_NICKNAME_LENGTH;
  }

  @ModelAttribute("passwordRequirements")
  public String passwordRequirements() {
    return passwordPolicyService.getRequirementsDescription();
  }

  @ModelAttribute("passwordPattern")
  public String passwordPattern() {
    return passwordPolicyService.getHtmlPattern();
  }

  @GetMapping("/login")
  public String viewLoginPage(
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "returnTo", required = false) String returnTo,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    // custom logic before showing login page...
    if (java.util.Objects.equals(error, "true")) {
      model.addAttribute("message", "Invalid email or password");
    }

    String effectiveReturnTo = sanitizeReturnTo(returnTo);
    if (!StringUtils.hasText(effectiveReturnTo)) {
      SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
      effectiveReturnTo =
          sanitizeReturnTo(
              ReturnToSanitizer.toAppRelativePath(savedRequest, request.getContextPath()));
    }
    if (StringUtils.hasText(effectiveReturnTo)) {
      model.addAttribute("returnTo", effectiveReturnTo);
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean authenticatedUser =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    if (authenticatedUser) {
      if (StringUtils.hasText(effectiveReturnTo)) {
        return "redirect:" + effectiveReturnTo;
      }
      return "redirect:/home";
    }
    return "public/login";
  }

  @GetMapping("/accept-terms")
  public String viewAcceptTerms(
      @RequestParam(value = "returnTo", required = false) String returnTo, Model model) {
    // Present a simple acceptance page to authenticated users so they don't
    // have to re-enter their password.
    String effectiveReturnTo = sanitizeReturnTo(returnTo);
    if (StringUtils.hasText(effectiveReturnTo)) {
      model.addAttribute("returnTo", effectiveReturnTo);
    }
    String acceptTermsReturnTarget = buildAcceptTermsPath(effectiveReturnTo);
    model.addAttribute("termsHref", buildLegalDocumentPath("/terms", acceptTermsReturnTarget));
    model.addAttribute("privacyHref", buildLegalDocumentPath("/privacy", acceptTermsReturnTarget));
    return "public/acceptTerms";
  }

  @PostMapping("/accept-terms")
  public String acceptTermsPost(
      Authentication authentication,
      @RequestParam(value = "returnTo", required = false) String returnTo) {
    // Record acceptance for the currently authenticated user and continue.
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return "redirect:/login";
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof CustomUserDetails) {
      User user = ((CustomUserDetails) principal).getUserObject();
      if (user != null) {
        User persistedUser =
            userAccountSettingsService.acknowledgeTerms(user.getId(), Instant.now());
        user.setAcknowledgedTermsAt(persistedUser.getAcknowledgedTermsAt());
      }
    }
    String effectiveReturnTo = sanitizeReturnTo(returnTo);
    if (StringUtils.hasText(effectiveReturnTo)) {
      return "redirect:" + effectiveReturnTo;
    }
    return "redirect:/home";
  }

  private String sanitizeReturnTo(String returnTo) {
    return ReturnToSanitizer.sanitize(returnTo);
  }

  private String buildAcceptTermsPath(String returnTo) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/accept-terms");
    if (StringUtils.hasText(returnTo)) {
      builder.queryParam("returnTo", returnTo);
    }
    return builder.build().encode().toUriString();
  }

  private String buildLegalDocumentPath(String path, String returnTo) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
    if (StringUtils.hasText(returnTo)) {
      builder.queryParam("returnTo", returnTo);
    }
    return builder.build().encode().toUriString();
  }

  @GetMapping("/register")
  public String showRegistrationForm(Model model) {
    model.addAttribute("user", new User());
    prepareRegistrationForm(model);
    return "public/registrationForm";
  }

  @GetMapping("/registration-success")
  public String showRegistrationSuccess() {
    return "public/registrationSuccess";
  }

  @PostMapping("/register")
  public String processRegister(
      @ModelAttribute("user") User user,
      BindingResult bindingResult,
      Model model,
      @RequestParam("confirm_password") String confirmPassword,
      @RequestParam(value = "company", required = false) String company,
      @RequestParam(value = "formToken", required = false) String formToken,
      HttpServletRequest request,
      HttpServletResponse response) {
    Long formServedAt = registrationFormTokenService.resolveIssuedAt(formToken);

    String clientIp = registrationAbuseGuard.resolveClientIp(request);
    RegistrationAbuseGuard.Decision guardDecision =
        registrationAbuseGuard.evaluate(clientIp, company, formServedAt);
    if (!guardDecision.allowed()) {
      log.warn("Blocked registration attempt ip={} reason={}", clientIp, guardDecision.reason());
      // Intentionally do not reveal the reason to the client.
      return "redirect:/registration-success";
    }

    String rawPassword = user != null ? user.getPassword() : null;

    if (user == null) {
      log.info("Registration rejected: ip={} emailDomain={} reasons={}", clientIp, "-", "invalidUser");
      bindingResult.addError(new FieldError("User", "", "Invalid user submission."));
      prepareRegistrationForm(model);
      return "public/registrationForm";
    }

    try {
      user.setEmail(InputValidation.requireEmail(user.getEmail()));
    } catch (IllegalArgumentException ex) {
      bindingResult.addError(new FieldError("User", "email", ex.getMessage()));
    }

    if (!bindingResult.hasFieldErrors("email") && userRepo.findByEmail(user.getEmail()) != null) {
      bindingResult.addError(
          new FieldError("User", "email", "E-mail address is already registered."));
    }

    List<String> parsedCourtNames = List.of();
    try {
      parsedCourtNames = parseCourtNames(user.getCourtNamesInput());
    } catch (IllegalArgumentException ex) {
      bindingResult.addError(new FieldError("User", "courtNamesInput", ex.getMessage()));
    }
    if (parsedCourtNames.isEmpty()) {
      if (!bindingResult.hasFieldErrors("courtNamesInput")) {
        bindingResult.addError(
            new FieldError("User", "courtNamesInput", "Enter at least one name people call you."));
      }
    } else {
      user.setCourtNamesInput(String.join(", ", parsedCourtNames));
    }

    passwordPolicyService
        .validate(user.getPassword())
        .ifPresent(msg -> bindingResult.rejectValue("password", "password.invalid", msg));

    if (!bindingResult.hasFieldErrors("password")) {
      if (confirmPassword == null || !java.util.Objects.equals(confirmPassword, rawPassword)) {
        bindingResult.addError(new FieldError("User", "password", "Passwords must match."));
      }
    }
    if (!user.isAcceptTerms()) {
      bindingResult.addError(
          new FieldError("User", "acceptTerms", "You must agree to the terms to register."));
    }

    // Time zone is optional; blank means "default Eastern".
    // If provided, it must be a valid IANA ZoneId string (e.g. "America/Los_Angeles").
    if (user.getTimeZone() != null) {
      String tz = user.getTimeZone().trim();
      if (tz.isBlank()) {
        user.setTimeZone(null);
      } else {
        try {
          java.time.ZoneId.of(tz);
          user.setTimeZone(tz);
        } catch (Exception ex) {
          bindingResult.addError(
              new FieldError("User", "timeZone", "Please choose a valid time zone."));
        }
      }
    }

    if (bindingResult.hasErrors()) {
      log.info(
          "Registration rejected: ip={} emailDomain={} reasons={}",
          clientIp,
          emailDomain(user.getEmail()),
          summarizeBindingErrors(bindingResult));
      user.setPassword(null);
      prepareRegistrationForm(model);
      return "public/registrationForm";
    }

    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    String encodedPassword = passwordEncoder.encode(rawPassword);
    user.setPassword(encodedPassword);
    user.setAcknowledgedTermsAt(Instant.now());
    if (user.getRegisteredAt() == null) {
      user.setRegisteredAt(Instant.now());
    }

    /*
     * if (userRepo.findByEmail(user.getEmail()) != null) { throw new
     * DuplicateUserException("User already exists!"); }
     */
    String nickSeed = assignNickName(user, parsedCourtNames);
    if (nickSeed == null || nickSeed.isBlank()) {
      nickSeed = "Player";
    }
    if (user.getMaxOwnedLadders() == null) {
      user.setMaxOwnedLadders(defaultMaxOwnedLadders);
    }

    user.getCourtNames().clear();
    for (String alias : parsedCourtNames) {
      UserCourtName courtName = new UserCourtName();
      courtName.setUser(user);
      courtName.setAlias(alias);
      user.getCourtNames().add(courtName);
    }

    int nicknameRetries = 0;
    boolean saved = false;
    while (!saved) {
      try {
        userRepo.saveAndFlush(user);
        saved = true;
      } catch (DataIntegrityViolationException e) {
        String message =
            Optional.ofNullable(e.getMostSpecificCause()).map(Throwable::getMessage).orElse("");
        log.warn("Registration failed due to data integrity violation: {}", message);
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("nick")) {
          nicknameRetries++;
          if (nicknameRetries > 25) {
            bindingResult.addError(
                new FieldError(
                    "User",
                    "courtNamesInput",
                    "We couldn't generate a unique nickname. Please try different names."));
            break;
          }
          String newNick = ensureUniqueNick(nickSeed, nicknameRetries);
          log.debug("Retrying nickname generation with attempt {} -> {}", nicknameRetries, newNick);
          user.setNickName(newNick);
          continue;
        }
        if (lower.contains("email")) {
          bindingResult.addError(
              new FieldError("User", "email", "E-mail address is already registered."));
        } else {
          bindingResult.reject(
              "registration.failed", "Sorry, something went wrong while creating your account.");
        }
        break;
      } catch (Exception e) {
        log.error("Unexpected error while registering user.", e);
        bindingResult.reject(
            "registration.failed", "Sorry, something went wrong while creating your account.");
        break;
      }
    }
    if (bindingResult.hasErrors()) {
      log.info(
          "Registration rejected: ip={} emailDomain={} reasons={}",
          clientIp,
          emailDomain(user.getEmail()),
          summarizeBindingErrors(bindingResult));
      user.setPassword(null);
      prepareRegistrationForm(model);
      return "public/registrationForm";
    }

    log.info(
        "Registration created: userId={} ip={} emailDomain={} aliasCount={}",
        user.getId(),
        clientIp,
        emailDomain(user.getEmail()),
        parsedCourtNames.size());

    try {
      globalLadderBootstrapService.enrollUserIfConfigured(user);
    } catch (Exception e) {
      log.warn("Post-registration enrollment failed: {}", e.toString());
    }

    // per-user passphrase regeneration removed
    try {
      var authToken = new UsernamePasswordAuthenticationToken(user.getEmail(), rawPassword);
      Authentication authentication = authenticationManager.authenticate(authToken);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      HttpSession session = request.getSession(true);
      session.setAttribute(
          HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
          SecurityContextHolder.getContext());
      var requestCache = new HttpSessionRequestCache();
      SavedRequest savedRequest = requestCache.getRequest(request, response);
      if (savedRequest != null) {
        String redirectUrl = savedRequest.getRedirectUrl();
        requestCache.removeRequest(request, response);
        return "redirect:" + redirectUrl;
      }
      return "redirect:/home";
    } catch (Exception ex) {
      log.warn(
          "Auto-login after registration failed: userId={} ip={} reason={}",
          user.getId(),
          clientIp,
          ex.toString());
      model.addAttribute("message", "You have been registered! Log in below.");
      return "public/login";
    }
  }

  private List<String> parseCourtNames(String input) {
    return InputValidation.parseCourtNames(input);
  }

  private void prepareRegistrationForm(Model model) {
    model.addAttribute("registrationFormToken", registrationFormTokenService.issueToken());
  }

  private String summarizeBindingErrors(BindingResult bindingResult) {
    if (bindingResult == null || !bindingResult.hasErrors()) {
      return "-";
    }
    return bindingResult.getAllErrors().stream()
        .map(
            error -> {
              if (error instanceof FieldError fieldError) {
                return fieldError.getField();
              }
              return error.getObjectName();
            })
        .filter(StringUtils::hasText)
        .distinct()
        .limit(8)
        .collect(Collectors.joining(","));
  }

  private String emailDomain(String email) {
    if (!StringUtils.hasText(email)) {
      return "-";
    }
    int at = email.indexOf('@');
    if (at < 0 || at == email.length() - 1) {
      return "-";
    }
    return email.substring(at + 1).trim().toLowerCase(Locale.US);
  }

  private String assignNickName(User user, List<String> parsedCourtNames) {
    String base = parsedCourtNames.isEmpty() ? "Player" : parsedCourtNames.get(0);
    String candidate = generateCandidateNick(base);
    if (candidate == null || candidate.isBlank()) {
      candidate = "Player";
    }

    Optional<String> moderated = displayNameModerationService.explainViolation(candidate);
    if (moderated.isPresent()) {
      candidate = "Player";
    }

    String uniqueNick = ensureUniqueNick(candidate);
    user.setNickName(uniqueNick);
    return candidate;
  }

  private String generateCandidateNick(String base) {
    if (base == null || base.isBlank()) {
      return "player";
    }
    String normalized = base.trim().toLowerCase();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < normalized.length(); i++) {
      char ch = normalized.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        sb.append(ch);
      } else if (sb.length() == 0 || sb.charAt(sb.length() - 1) != '-') {
        sb.append('-');
      }
    }
    String cleaned = sb.toString().replaceAll("^-+|-+$", "");
    if (cleaned.isBlank()) {
      return "player";
    }
    String[] parts = cleaned.split("-");
    StringBuilder titleCase = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (titleCase.length() + part.length() > User.MAX_NICKNAME_LENGTH) {
        break;
      }
      if (titleCase.length() > 0) {
        // avoid overrun; leave concatenated without separator
      }
      titleCase.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        titleCase.append(part.substring(1).toLowerCase(Locale.US));
      }
    }
    String result = titleCase.toString();
    if (result.isBlank()) {
      return "Player";
    }
    if (result.length() > User.MAX_NICKNAME_LENGTH) {
      result = result.substring(0, User.MAX_NICKNAME_LENGTH);
    }
    return result;
  }

  private String ensureUniqueNick(String base) {
    return ensureUniqueNick(base, 0);
  }

  private String ensureUniqueNick(String base, int attemptOffset) {
    String sanitizedBase = base != null && !base.isBlank() ? base : "Player";
    if (sanitizedBase.length() > User.MAX_NICKNAME_LENGTH) {
      sanitizedBase = sanitizedBase.substring(0, User.MAX_NICKNAME_LENGTH);
    }
    int attempt = attemptOffset;
    if (attemptOffset == 0) {
      String upperBase = sanitizedBase.toUpperCase(Locale.US);
      if (!upperBase.contains("GUEST")
          && !displayNameModerationService.explainViolation(sanitizedBase).isPresent()
          && !isNickTaken(sanitizedBase)) {
        return sanitizedBase;
      }
      attempt++;
    }
    int limit = attemptOffset + 1_000;
    while (attempt < limit) {
      String suffixWord = FUN_SUFFIXES[attempt % FUN_SUFFIXES.length];
      int number = (attempt / FUN_SUFFIXES.length) + 1;
      String candidate = composeCandidate(sanitizedBase, suffixWord, number);
      String upper = candidate.toUpperCase(Locale.US);
      if (upper.contains("GUEST")) {
        attempt++;
        continue;
      }
      if (displayNameModerationService.explainViolation(candidate).isPresent()) {
        attempt++;
        continue;
      }
      if (!isNickTaken(candidate)) {
        return candidate;
      }
      attempt++;
    }
    // Fallback: append numeric suffix only
    int counter = Math.max(1, attemptOffset + 1);
    while (true) {
      String suffix = String.valueOf(counter++);
      int maxBaseLength = User.MAX_NICKNAME_LENGTH - suffix.length();
      String candidate = sanitizedBase;
      if (candidate.length() > maxBaseLength) {
        candidate = candidate.substring(0, maxBaseLength);
      }
      candidate = candidate + suffix;
      if (!isNickTaken(candidate)) {
        return candidate;
      }
    }
  }

  private String composeCandidate(String base, String suffixWord, int number) {
    String numberPart = String.valueOf(number);
    int totalSuffixLength = suffixWord.length() + numberPart.length();
    int maxBaseLength = User.MAX_NICKNAME_LENGTH - totalSuffixLength;
    String workingBase = base;
    if (maxBaseLength < 1) {
      maxBaseLength = 1;
    }
    if (workingBase.length() > maxBaseLength) {
      workingBase = workingBase.substring(0, maxBaseLength);
    }
    return workingBase + suffixWord + numberPart;
  }

  private boolean isNickTaken(String nick) {
    User existing = userRepo.findByNickName(nick);
    return existing != null;
  }
}
