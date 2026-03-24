package com.w3llspring.fhpb.web.util;

import com.w3llspring.fhpb.web.model.CustomUserDetails;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AuthenticatedUserSupport {

  private static volatile AuthenticatedUserService authenticatedUserService;

  public AuthenticatedUserSupport(AuthenticatedUserService authenticatedUserService) {
    AuthenticatedUserSupport.authenticatedUserService = authenticatedUserService;
  }

  public static User currentUser() {
    return currentUser(SecurityContextHolder.getContext().getAuthentication());
  }

  public static User currentUser(Authentication authentication) {
    if (authenticatedUserService != null) {
      return authenticatedUserService.currentUser(authentication);
    }

    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof CustomUserDetails details)) {
      return null;
    }

    return details.getUserObject();
  }

  public static User refresh(User sessionUser) {
    if (authenticatedUserService != null) {
      return authenticatedUserService.refresh(sessionUser);
    }
    return sessionUser;
  }

  public static void syncSessionUser(User sessionUser, User persistedUser) {
    if (authenticatedUserService != null) {
      authenticatedUserService.syncSessionUser(sessionUser, persistedUser);
    }
  }

  public static User requireCurrentUser() {
    User currentUser = currentUser();
    if (currentUser == null || currentUser.getId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return currentUser;
  }

  public static Long requireCurrentUserId() {
    return requireCurrentUser().getId();
  }
}
