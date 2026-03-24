package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "userStyle")
public class UserStyle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Basic String styleName;

  @ManyToOne
  @JoinColumn(name = "styleOwner", referencedColumnName = "id", nullable = false)
  private User styleOwner;

  @ManyToOne
  @JoinColumn(name = "styleVoter", referencedColumnName = "id", nullable = false)
  private User styleVoter;

  @Basic
  @Temporal(TemporalType.TIMESTAMP)
  java.util.Date votedDt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getStyleName() {
    return styleName;
  }

  public void setStyleName(String styleName) {
    this.styleName = styleName;
  }

  public User getStyleOwner() {
    return styleOwner;
  }

  public void setStyleOwner(User styleOwner) {
    this.styleOwner = styleOwner;
  }

  public User getStyleVoter() {
    return styleVoter;
  }

  public void setStyleVoter(User styleVoter) {
    this.styleVoter = styleVoter;
  }

  public java.util.Date getVotedDt() {
    return votedDt;
  }

  public void setVotedDt(java.util.Date votedDt) {
    this.votedDt = votedDt;
  }
}
