-- Date-only sessions need time for signups and play on the selected day even
-- when the configured live-session window is shorter than a full day.
-- Preserve sessions that already started, ended, or have a longer window.
UPDATE session_join_request r
JOIN ladder_config s ON s.id = r.ladder_config_id
SET r.expires_at = DATE_ADD(s.session_scheduled_at, INTERVAL 48 HOUR),
    r.updated_at = UTC_TIMESTAMP(6),
    r.version = r.version + 1
WHERE s.type = 'SESSION'
  AND s.status = 'ACTIVE'
  AND s.session_scheduled_at IS NOT NULL
  AND s.session_started_at IS NULL
  AND s.expires_at < DATE_ADD(s.session_scheduled_at, INTERVAL 48 HOUR)
  AND r.status = 'PENDING'
  AND r.expires_at < DATE_ADD(s.session_scheduled_at, INTERVAL 48 HOUR);

UPDATE ladder_config
SET expires_at = DATE_ADD(session_scheduled_at, INTERVAL 48 HOUR)
WHERE type = 'SESSION'
  AND status = 'ACTIVE'
  AND session_scheduled_at IS NOT NULL
  AND session_started_at IS NULL
  AND expires_at < DATE_ADD(session_scheduled_at, INTERVAL 48 HOUR);
