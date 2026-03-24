DELETE FROM trophy
WHERE slug IN (
    'profile-badge-control-paddle',
    'profile-badge-power-paddle',
    'profile-badge-widebody-paddle',
    'profile-badge-usa-colors',
    'profile-badge-canada-colors',
    'profile-badge-mexico-colors',
    'profile-badge-birthday-rally'
);

DELETE FROM trophy_art
WHERE storage_key IN (
    'profile-badge-control-paddle',
    'profile-badge-power-paddle',
    'profile-badge-widebody-paddle',
    'profile-badge-usa-colors',
    'profile-badge-canada-colors',
    'profile-badge-mexico-colors',
    'profile-badge-birthday-rally'
)
AND NOT EXISTS (
    SELECT 1
    FROM trophy
    WHERE trophy.art_id = trophy_art.id
       OR trophy.badge_art_id = trophy_art.id
);
