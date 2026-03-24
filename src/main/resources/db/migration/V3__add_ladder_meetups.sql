-- Ladder meetup slots: time-only coordination (no free-form text)

CREATE TABLE IF NOT EXISTS ladder_meetup_slot (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ladder_config_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  starts_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  canceled_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_meetup_slot_ladder_time (ladder_config_id, starts_at),
  KEY idx_meetup_slot_creator_time (created_by_user_id, starts_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ladder_meetup_rsvp (
  id BIGINT NOT NULL AUTO_INCREMENT,
  slot_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(8) NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_meetup_rsvp_slot_user (slot_id, user_id),
  KEY idx_meetup_rsvp_slot_status (slot_id, status),
  KEY idx_meetup_rsvp_user (user_id)
) ENGINE=InnoDB;
