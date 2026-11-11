package com.w3llspring.fhpb.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.model.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerNamesTest {
  @Test
  void collisionsDependOnThisListAndIgnoreCaseAndOuterSpaces() {
    var duplicates = PlayerNames.duplicates(List.of(" Morgan ", "morgan", "Sam"));
    assertThat(PlayerNames.requiresId(duplicates, "MORGAN")).isTrue();
    assertThat(PlayerNames.requiresId(duplicates, "Sam")).isFalse();
    assertThat(PlayerNames.duplicates(List.of("Morgan", "Sam"))).isEmpty();
  }

  @Test
  void matchListsCountDifferentPlayersRatherThanRepeatedAppearances() {
    User first = player(1L, "Alex", "PB-sage-ace-247");
    User second = player(2L, "Alex", "PB-reef-jump-430");
    User sam = player(3L, "Sam", "PB-cove-ace-123");
    var one = new com.w3llspring.fhpb.web.model.Match();
    one.setA1(first);
    one.setB1(sam);
    var two = new com.w3llspring.fhpb.web.model.Match();
    two.setA1(second);
    two.setB1(sam);
    var names = PlayerNames.inMatches(List.of(one, two));
    assertThat(names.get(1L).publicCode()).isEqualTo(first.getPublicCode());
    assertThat(names.get(2L).publicCode()).isEqualTo(second.getPublicCode());
    assertThat(names.get(3L).publicCode()).isNull();
    assertThat(PlayerNames.inMatches(List.of(one, one)).get(1L).publicCode()).isNull();
  }

  @Test
  void enteredNameDoesNotNeedAnIdJustBecauseAnAccountNameWasSuffixed() {
    User alex = player(1L, "Alex-abcdef", "PB-sage-ace-247");
    var names = PlayerNames.resolve(List.of(alex), Map.of(1L, "Alex"), List.of());
    assertThat(names.get(1L)).isEqualTo(new PlayerNames.Name("Alex", null));
  }

  @Test
  void onlyNamesRepeatedInsideThisSessionGetPublicIds() {
    User first = player(1L, "Alex-abcdef", "PB-sage-ace-247");
    User second = player(2L, "Alex-123abc", "PB-reef-jump-430");
    User sam = player(3L, "Sam-fedcba", "PB-cove-ace-123");
    var names =
        PlayerNames.resolve(
            List.of(first, second, sam, first),
            Map.of(1L, "Alex", 2L, "alex", 3L, "Sam"),
            List.of());
    assertThat(names.get(1L).publicCode()).isEqualTo(first.getPublicCode());
    assertThat(names.get(2L).publicCode()).isEqualTo(second.getPublicCode());
    assertThat(names.get(3L)).isEqualTo(new PlayerNames.Name("Sam", null));
  }

  @Test
  void guestNameCanAlsoMakeARegisteredPlayersNameAmbiguous() {
    User alex = player(1L, "Alex", "PB-sage-ace-247");
    var names = PlayerNames.resolve(List.of(alex), Map.of(), List.of("Alex"));
    assertThat(names.get(1L).publicCode()).isEqualTo(alex.getPublicCode());
  }

  private User player(Long id, String name, String publicCode) {
    User user = new User();
    user.setId(id);
    user.setNickName(name);
    user.setPublicCode(publicCode);
    return user;
  }
}
