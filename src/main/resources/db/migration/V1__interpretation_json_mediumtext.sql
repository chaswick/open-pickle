-- Make interpretation JSON and transcript columns large enough for recorded interpretations
ALTER TABLE interpretation_event
  MODIFY COLUMN interpretation_json MEDIUMTEXT,
  MODIFY COLUMN transcript MEDIUMTEXT;
