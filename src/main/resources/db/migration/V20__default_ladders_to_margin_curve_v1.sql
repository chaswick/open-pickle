ALTER TABLE ladder_config
    ALTER COLUMN scoring_algorithm SET DEFAULT 'MARGIN_CURVE_V1';

UPDATE ladder_config
SET scoring_algorithm = 'MARGIN_CURVE_V1'
WHERE scoring_algorithm = 'BALANCED_V1';
