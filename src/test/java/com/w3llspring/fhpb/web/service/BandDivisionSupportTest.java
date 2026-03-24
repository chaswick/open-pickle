package com.w3llspring.fhpb.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BandDivisionSupportTest {

  @Test
  void resolveBandNames_usesDedicatedNamesForEachSupportedSeasonSize() {
    assertThat(BandDivisionSupport.resolveBandNames(1))
        .containsExactlyEntriesOf(linkedMapOf(1, "Open Division"));
    assertThat(BandDivisionSupport.resolveBandNames(2))
        .containsExactlyEntriesOf(
            linkedMapOf(
                1, "Upper Division",
                2, "Lower Division"));
    assertThat(BandDivisionSupport.resolveBandNames(3))
        .containsExactlyEntriesOf(
            linkedMapOf(
                1, "Premier Division",
                2, "Select Division",
                3, "Club Division"));
    assertThat(BandDivisionSupport.resolveBandNames(4))
        .containsExactlyEntriesOf(
            linkedMapOf(
                1, "Champion Division",
                2, "Master Division",
                3, "Contender Division",
                4, "Challenger Division"));
    assertThat(BandDivisionSupport.resolveBandNames(5))
        .containsExactlyEntriesOf(
            linkedMapOf(
                1, "Diamond Division",
                2, "Platinum Division",
                3, "Gold Division",
                4, "Silver Division",
                5, "Bronze Division"));
  }

  @Test
  void resolveBandNames_doesNotReuseSupportedLabelsAcrossSeasonSizes() {
    Set<String> names = new LinkedHashSet<>();
    for (int bandCount = 1; bandCount <= 5; bandCount++) {
      names.addAll(BandDivisionSupport.resolveBandNames(bandCount).values());
    }
    assertThat(names).hasSize(15);
  }

  private Map<Integer, String> linkedMapOf(Object... entries) {
    Map<Integer, String> map = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      map.put((Integer) entries[i], (String) entries[i + 1]);
    }
    return map;
  }
}
