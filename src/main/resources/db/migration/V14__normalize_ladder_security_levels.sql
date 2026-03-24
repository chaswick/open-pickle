UPDATE ladder_config
SET security_level = 'STANDARD'
WHERE security_level IN ('NONE', 'HIGH');
