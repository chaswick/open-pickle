package com.w3llspring.fhpb.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorEndpointSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void loggedOutErrorEndpointDoesNotRedirectToLogin() throws Exception {
    mockMvc
        .perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
        .andExpect(status().isNotFound())
        .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
  }
}
