package com.w3llspring.fhpb.web.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class UserSessionState {
  public static final String SESSION_KEY = "fhpb.user.session";

  // Durable cross-page session state is only the selected private group.
  private Long selectedGroupId;

  public Long getSelectedGroupId() {
    return selectedGroupId;
  }

  public void setSelectedGroupId(Long selectedGroupId) {
    this.selectedGroupId = selectedGroupId;
  }

  public static Long resolveSelectedGroupId(HttpServletRequest request, Long groupIdParam) {
    if (request == null) {
      return groupIdParam;
    }
    if (groupIdParam != null) {
      return groupIdParam;
    }
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object existing = session.getAttribute(SESSION_KEY);
    if (existing instanceof UserSessionState) {
      return ((UserSessionState) existing).getSelectedGroupId();
    }
    return null;
  }

  public static void storeSelectedGroupId(HttpServletRequest request, Long groupId) {
    if (request == null || groupId == null) {
      return;
    }
    HttpSession session = request.getSession(true);
    Object existing = session.getAttribute(SESSION_KEY);
    UserSessionState state =
        (existing instanceof UserSessionState)
            ? (UserSessionState) existing
            : new UserSessionState();
    state.setSelectedGroupId(groupId);
    session.setAttribute(SESSION_KEY, state);
  }
}
