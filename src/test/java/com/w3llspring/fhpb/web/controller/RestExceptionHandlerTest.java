package com.w3llspring.fhpb.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.w3llspring.fhpb.web.controller.advice.RestExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RestExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  void brokenPipeDuringVideoResponseReturnsEmptyResponse() throws Exception {
    mockMvc
        .perform(get("/test/video"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().doesNotExist("X-Correlation-Id"));
  }

  @Test
  void genericExceptionStillReturnsJsonErrorBody() throws Exception {
    mockMvc
        .perform(get("/test/error").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again."))
        .andExpect(jsonPath("$.exception").value("IllegalStateException"))
        .andExpect(jsonPath("$.status").value(500));
  }

  @RestController
  static class TestController {

    @GetMapping(value = "/test/video", produces = "video/mp4")
    void video(HttpServletResponse response) throws IOException {
      response.setContentType("video/mp4");
      throw new IOException("Broken pipe");
    }

    @GetMapping("/test/error")
    void error() {
      throw new IllegalStateException("boom");
    }
  }
}
