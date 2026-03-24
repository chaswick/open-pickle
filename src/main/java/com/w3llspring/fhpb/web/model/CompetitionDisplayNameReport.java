package com.w3llspring.fhpb.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "competition_display_name_report",
    indexes = {
      @Index(name = "idx_comp_name_report_target", columnList = "target_user_id"),
      @Index(name = "idx_comp_name_report_reporter", columnList = "reporter_user_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uc_comp_name_report_reporter_target",
          columnNames = {"reporter_user_id", "target_user_id"})
    })
public class CompetitionDisplayNameReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reporter_user_id", nullable = false)
  private Long reporterUserId;

  @Column(name = "target_user_id", nullable = false)
  private Long targetUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getReporterUserId() {
    return reporterUserId;
  }

  public void setReporterUserId(Long reporterUserId) {
    this.reporterUserId = reporterUserId;
  }

  public Long getTargetUserId() {
    return targetUserId;
  }

  public void setTargetUserId(Long targetUserId) {
    this.targetUserId = targetUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
