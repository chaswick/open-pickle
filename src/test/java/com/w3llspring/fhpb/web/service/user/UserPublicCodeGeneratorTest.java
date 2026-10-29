package com.w3llspring.fhpb.web.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserPublicCodeGeneratorTest {

  @ParameterizedTest
  @ValueSource(strings = {"PB-sky-play-007", "PB-sky-ace-000", "PB-aqua-dink-999", "PB-wind-ace-042"})
  void acceptsTheWordLengthsUsedByTheGenerator(String code) {
    assertThat(UserPublicCodeGenerator.isCurrentFormat(code)).isTrue();
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
        "PB-sky-play-007-extra"
      })
  void rejectsMalformedCodes(String code) {
    assertThat(UserPublicCodeGenerator.isCurrentFormat(code)).isFalse();
  }
}
