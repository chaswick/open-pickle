package com.w3llspring.fhpb.web.model;

import jakarta.persistence.*;

/**
 * Optional additional names a player uses on court. Entries can be global or scoped to a specific
 * ladder configuration.
 */
@Entity
@Table(
    name = "user_court_name",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "alias", "ladder_config_id"}))
public class UserCourtName {

  public static final int MAX_ALIAS_LENGTH = 64;
  public static final int MAX_ALIASES_PER_SCOPE = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "ladder_config_id")
  private LadderConfig ladderConfig;

  @Column(name = "alias", nullable = false, length = MAX_ALIAS_LENGTH)
  private String alias;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public LadderConfig getLadderConfig() {
    return ladderConfig;
  }

  public void setLadderConfig(LadderConfig ladderConfig) {
    this.ladderConfig = ladderConfig;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }
}
