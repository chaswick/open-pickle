package com.w3llspring.fhpb.web.service.auth;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Service
public class AuthenticatedUserService {

  private static final String REQUEST_USER_CACHE_ATTRIBUTE =
      AuthenticatedUserService.class.getName() + ".requestUserCache";

  private final UserRepository userRepository;

  public AuthenticatedUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User currentUser() {
    return currentUser(SecurityContextHolder.getContext().getAuthentication());
  }

  public User currentUser(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof CustomUserDetails details) {
      return refresh(details.getUserObject());
    }

    String username = authentication.getName();
    if (username == null || username.isBlank()) {
      return null;
    }

    User dbUser = userRepository.findByEmail(username);
    if (dbUser != null && dbUser.getId() != null) {
      cacheUser(dbUser);
    }
    return dbUser;
  }

  public User refresh(User sessionUser) {
    if (sessionUser == null || sessionUser.getId() == null) {
      return sessionUser;
    }

    User dbUser = findFreshUserById(sessionUser.getId());
    if (dbUser == null) {
      return null;
    }

    syncSessionUser(sessionUser, dbUser);
    return sessionUser;
  }

  public User requireCurrentUser() {
    User currentUser = currentUser();
    if (currentUser == null || currentUser.getId() == null) {
      return null;
    }
    return currentUser;
  }

  public Long requireCurrentUserId() {
    User currentUser = requireCurrentUser();
    return currentUser != null ? currentUser.getId() : null;
  }

  public void syncSessionUser(User sessionUser, User persistedUser) {
    if (sessionUser == null || persistedUser == null) {
      return;
    }

    sessionUser.setEmail(persistedUser.getEmail());
    sessionUser.setNickName(persistedUser.getNickName());
    sessionUser.setAdmin(persistedUser.isAdmin());
    sessionUser.setPublicCode(persistedUser.getPublicCode());
    sessionUser.setMaxOwnedLadders(persistedUser.getMaxOwnedLadders());
    sessionUser.setLastMatchLoggedAt(persistedUser.getLastMatchLoggedAt());
    sessionUser.setLastDisplayNameChangeAt(persistedUser.getLastDisplayNameChangeAt());
    sessionUser.setConsecutiveMatchLogs(persistedUser.getConsecutiveMatchLogs());
    sessionUser.setLastSeenAt(persistedUser.getLastSeenAt());
    sessionUser.setAcknowledgedTermsAt(persistedUser.getAcknowledgedTermsAt());
    sessionUser.setRegisteredAt(persistedUser.getRegisteredAt());
    sessionUser.setFailedPassphraseAttempts(persistedUser.getFailedPassphraseAttempts());
    sessionUser.setPassphraseTimeoutUntil(persistedUser.getPassphraseTimeoutUntil());
    sessionUser.setMeetupsEmailOptIn(persistedUser.isMeetupsEmailOptIn());
    sessionUser.setMeetupsEmailPending(persistedUser.isMeetupsEmailPending());
    sessionUser.setMeetupsEmailLastSentAt(persistedUser.getMeetupsEmailLastSentAt());
    sessionUser.setMeetupsEmailDailySentCount(persistedUser.getMeetupsEmailDailySentCount());
    sessionUser.setMeetupsEmailDailySentDay(persistedUser.getMeetupsEmailDailySentDay());
    sessionUser.setAppUiEnabled(persistedUser.isAppUiEnabled());
    sessionUser.setTimeZone(persistedUser.getTimeZone());
    sessionUser.setCompetitionSafeDisplayName(persistedUser.getCompetitionSafeDisplayName());
    sessionUser.setCompetitionSafeDisplayNameActive(
        persistedUser.isCompetitionSafeDisplayNameActive());
    sessionUser.setCompetitionSafeDisplayNameBasis(
        persistedUser.getCompetitionSafeDisplayNameBasis());
  }

  private User findFreshUserById(Long userId) {
    Map<Long, User> cache = requestUserCache();
    if (cache != null && cache.containsKey(userId)) {
      return cache.get(userId);
    }

    User dbUser = userRepository.findById(userId).orElse(null);
    if (cache != null) {
      cache.put(userId, dbUser);
    }
    return dbUser;
  }

  private void cacheUser(User user) {
    if (user == null || user.getId() == null) {
      return;
    }

    Map<Long, User> cache = requestUserCache();
    if (cache != null) {
      cache.put(user.getId(), user);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<Long, User> requestUserCache() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return null;
    }

    Object cached =
        attributes.getAttribute(REQUEST_USER_CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    if (cached instanceof Map<?, ?> map) {
      return (Map<Long, User>) map;
    }

    Map<Long, User> cache = new HashMap<>();
    attributes.setAttribute(REQUEST_USER_CACHE_ATTRIBUTE, cache, RequestAttributes.SCOPE_REQUEST);
    return cache;
  }
}
