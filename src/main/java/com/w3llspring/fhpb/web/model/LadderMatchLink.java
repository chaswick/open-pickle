package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;

/**
 * Links a logged match to the season it belongs to while allowing optional highlight copy for the
 * ladder UI.
 */
@Entity
@Table(
    name = "ladder_match_link",
    uniqueConstraints = {@UniqueConstraint(columnNames = "match_id")})
public class LadderMatchLink {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "season_id")
  private LadderSeason season;

  @OneToOne(optional = false)
  @JoinColumn(name = "match_id", nullable = false, unique = true)
  private Match match;

  // Optional highlight text (cached so we can tweak copy without touching match history)
  @Column(name = "headline", length = 160)
  private String headline;

  public Long getId() {
    return id;
  }

  public LadderSeason getSeason() {
    return season;
  }

  public void setSeason(LadderSeason season) {
    this.season = season;
  }

  public Match getMatch() {
    return match;
  }

  public void setMatch(Match match) {
    this.match = match;
  }

  public String getHeadline() {
    return headline;
  }

  public void setHeadline(String headline) {
    this.headline = headline;
  }
}
