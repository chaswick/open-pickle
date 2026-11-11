package com.w3llspring.fhpb.web.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class UserPublicCodeGeneratorTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PB-sky-play-007",
        "PB-sky-ace-000",
        "PB-aqua-dink-999",
        "PB-wind-ace-042",
        "PB-ruby-rush-742193",
        "PB-aqua-dink-000000",
        "PB-wind-ace-999999",
        "calm-ruby-paddle",
        "bright-maple-otter"
      })
  void acceptsTheWordLengthsUsedByTheGenerator(String code) {
    assertThat(UserPublicCodeGenerator.isCurrentFormat(code)).isTrue();
  }

  @Test
  void newCodesUseThreeDifferentShortWords() {
    for (int i = 0; i < 100; i++) {
      String code = UserPublicCodeGenerator.nextCode();
      assertThat(code).matches("[a-z]{3,6}-[a-z]{3,6}-[a-z]{3,6}");
      assertThat(List.of(code.split("-"))).doesNotHaveDuplicates();
      assertThat(code.length()).isLessThanOrEqualTo(24);
    }
  }

  @Test
  void repeatedRandomSelectionsCannotRepeatAWordWithinAnId() {
    RandomGenerator random = mock(RandomGenerator.class);
    when(random.nextInt(anyInt())).thenReturn(0, 0, 1, 0, 1, 2);
    assertThat(List.of(UserPublicCodeGenerator.nextCode(random).split("-")))
        .hasSize(3)
        .doesNotHaveDuplicates();
  }

  @Test
  void bundledWordsAreDistinctShortLabelsAndPassExistingModeration() throws Exception {
    List<String> words =
        new ClassPathResource("identity/member-code-words.txt")
            .getContentAsString(StandardCharsets.UTF_8)
            .lines()
            .filter(line -> !line.isBlank() && !line.startsWith("#"))
            .toList();
    assertThat(words).hasSize(1024).doesNotHaveDuplicates();
    DefaultDisplayNameModerationService moderation = new DefaultDisplayNameModerationService("");
    for (String word : words) {
      assertThat(word).matches("[a-z]{3,6}");
      assertThat(moderation.explainViolation(word)).as(word).isEmpty();
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "PB-sk-play-007",
        "PB-skies-play-007",
        "PB-sky-at-007",
        "PB-sky-plays-007",
        "PB-SKY-play-007",
        "PB-sky-play-07",
        "PB-sky-play-1000",
        "PB-sky-play-10000",
        "PB-sky-play-1000000",
        "PB-sky-play-007-extra",
        "calm-ruby",
        "calm-ruby-paddle-extra",
        "calm-Ruby-paddle",
        "calm-ruby-123",
        "calm-ruby-paddles",
        "calm-ruby pad",
        "calm-ruby-"
      })
  void rejectsMalformedCodes(String code) {
    assertThat(UserPublicCodeGenerator.isCurrentFormat(code)).isFalse();
  }
}
