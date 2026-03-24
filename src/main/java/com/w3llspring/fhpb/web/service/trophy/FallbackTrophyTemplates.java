package com.w3llspring.fhpb.web.service.trophy;

import java.util.List;

/**
 * Exposes the fallback trophy templates defined in {@link FallbackTrophyLibrary} to callers outside
 * of the trophy service package.
 */
public final class FallbackTrophyTemplates {

  private FallbackTrophyTemplates() {}

  public static List<GeneratedTrophy> createAll(String seasonName) {
    return FallbackTrophyLibrary.create(seasonName, FallbackTrophyLibrary.templateCount());
  }

  public static int templateCount() {
    return FallbackTrophyLibrary.templateCount();
  }
}
