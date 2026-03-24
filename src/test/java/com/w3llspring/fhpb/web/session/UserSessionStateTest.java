package com.w3llspring.fhpb.web.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class UserSessionStateTest {

  @Test
  void storeSelectedGroupIdPersistsGroupSelection() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    UserSessionState.storeSelectedGroupId(request, 7L);

    assertThat(UserSessionState.resolveSelectedGroupId(request, null)).isEqualTo(7L);
  }

  @Test
  void resolveSelectedGroupIdWithExplicitParamDoesNotOverwriteStoredSelection() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession session = new MockHttpSession();
    UserSessionState existing = new UserSessionState();
    existing.setSelectedGroupId(7L);
    session.setAttribute(UserSessionState.SESSION_KEY, existing);
    request.setSession(session);

    assertThat(UserSessionState.resolveSelectedGroupId(request, 42L)).isEqualTo(42L);
    assertThat(UserSessionState.resolveSelectedGroupId(request, null)).isEqualTo(7L);
  }
}
