ALTER TABLE ladder_config
    ADD COLUMN allow_guest_only_personal_matches BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE matches
    ADD COLUMN exclude_from_standings BOOLEAN NOT NULL DEFAULT FALSE;
