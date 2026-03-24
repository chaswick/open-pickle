package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class MatchLogRoutingService {

  public String sanitizeReturnTo(String returnTo) {
    return ReturnToSanitizer.sanitize(returnTo);
  }

  public String competitionRedirect(String toastKey) {
    return competitionRedirect(toastKey, null);
  }

  public String competitionRedirect(String toastKey, Long matchId) {
    if (!StringUtils.hasText(toastKey)) {
      return "redirect:/competition";
    }
    StringBuilder redirect = new StringBuilder("redirect:/competition?toast=").append(toastKey);
    if (matchId != null) {
      redirect.append("&matchId=").append(matchId);
    }
    return redirect.toString();
  }

  public String competitionLogRedirect(String toastKey, Long ladderId) {
    StringBuilder redirect = new StringBuilder("redirect:/competition/log-match");
    boolean first = true;
    if (ladderId != null) {
      redirect.append(first ? '?' : '&').append("ladderId=").append(ladderId);
      first = false;
    }
    if (StringUtils.hasText(toastKey)) {
      redirect.append(first ? '?' : '&').append("toast=").append(toastKey);
    }
    return redirect.toString();
  }

  public String competitionContextRedirect(Long sessionLadderId, String toastKey, Long matchId) {
    if (sessionLadderId != null) {
      UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/groups/{ladderId}");
      if (StringUtils.hasText(toastKey)) {
        builder.queryParam("toast", toastKey);
      }
      if (matchId != null) {
        builder.queryParam("matchId", matchId);
      }
      return "redirect:" + builder.buildAndExpand(sessionLadderId).toUriString();
    }

    UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/competition/log-match");
    if (StringUtils.hasText(toastKey)) {
      builder.queryParam("toast", toastKey);
    }
    if (matchId != null) {
      builder.queryParam("matchId", matchId);
    }
    return "redirect:" + builder.toUriString();
  }

  public String privateGroupRedirect(Long ladderId, Long seasonId) {
    if (ladderId != null && seasonId != null) {
      return "redirect:/private-groups/" + ladderId + "?seasonId=" + seasonId;
    }
    if (ladderId != null) {
      return "redirect:/private-groups/" + ladderId;
    }
    return "redirect:/home";
  }

  public String redirectToReturnTarget(String returnTo, String toastKey, Long matchId) {
    String sanitizedReturnTo = sanitizeReturnTo(returnTo);
    if (!StringUtils.hasText(sanitizedReturnTo)) {
      return null;
    }
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(sanitizedReturnTo);
    if (StringUtils.hasText(toastKey)) {
      builder.replaceQueryParam("toast", toastKey);
    } else {
      builder.replaceQueryParam("toast");
    }
    if (matchId != null) {
      builder.replaceQueryParam("matchId", matchId);
    } else {
      builder.replaceQueryParam("matchId");
    }
    return "redirect:" + builder.build(true).toUriString();
  }
}
