CREATE TABLE play_location (
  id BIGINT NOT NULL AUTO_INCREMENT,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_play_location_coordinates (latitude, longitude),
  KEY idx_play_location_created_by (created_by_user_id),
  CONSTRAINT fk_play_location_created_by
    FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE play_location_alias (
  id BIGINT NOT NULL AUTO_INCREMENT,
  location_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  normalized_name VARCHAR(80) NOT NULL,
  phonetic_key VARCHAR(40) NULL,
  usage_count INT NOT NULL DEFAULT 1,
  first_used_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_used_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_play_location_alias_location_user_norm (location_id, user_id, normalized_name),
  KEY idx_play_location_alias_user (user_id),
  KEY idx_play_location_alias_location (location_id),
  KEY idx_play_location_alias_location_last_used (location_id, last_used_at),
  CONSTRAINT fk_play_location_alias_location
    FOREIGN KEY (location_id) REFERENCES play_location (id),
  CONSTRAINT fk_play_location_alias_user
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE play_location_check_in (
  id BIGINT NOT NULL AUTO_INCREMENT,
  location_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  checked_in_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at TIMESTAMP(6) NOT NULL,
  ended_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  KEY idx_play_location_check_in_user_expires (user_id, expires_at),
  KEY idx_play_location_check_in_location_expires (location_id, expires_at),
  KEY idx_play_location_check_in_user_checked_in (user_id, checked_in_at),
  CONSTRAINT fk_play_location_check_in_location
    FOREIGN KEY (location_id) REFERENCES play_location (id),
  CONSTRAINT fk_play_location_check_in_user
    FOREIGN KEY (user_id) REFERENCES users (id)
);
