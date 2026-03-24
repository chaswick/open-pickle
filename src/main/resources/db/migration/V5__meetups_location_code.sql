-- Optional creator-provided meetup location code (2 characters)

ALTER TABLE ladder_meetup_slot
  ADD COLUMN location_code VARCHAR(2) NULL AFTER starts_at;
