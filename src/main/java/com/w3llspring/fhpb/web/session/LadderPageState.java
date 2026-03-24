package com.w3llspring.fhpb.web.session;

public class LadderPageState {
  // Durable app-level selection is ladder/group only.
  public Long ladderId;
  public String ladderName; // scalar, safe for template
  // Season fields describe the current page snapshot, not a global remembered selection.
  public Long seasonId;
  public String seasonName; // scalar
  public String seasonDateRange; // "Jan 1, 2025 – Feb 15, 2025"

  public Long prevLadderId;
  public Long nextLadderId;
  public Long prevSeasonId;
  public Long nextSeasonId;
}
