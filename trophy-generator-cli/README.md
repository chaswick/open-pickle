# Trophy Generator CLI

Generates trophies and outputs MySQL-friendly SQL for shared `trophy_art` rows plus season-specific `trophy` rows.

## Usage

```bash
./gradlew :trophy-generator-cli:run --args="\
  --config ../src/main/resources/application.properties \
  --season-id 123 \
  --season-name 'Spring 2026' \
  --season-start 2026-03-01 \
  --season-end 2026-05-31 \
  --fallback-image-root ../src/main/resources/static \
  --output ../build/trophies.sql"
```

### Prompt Preview (single rarity)

```bash
./gradlew :trophy-generator-cli:run --args="\
  --config ../src/main/resources/application.properties \
  --prompt-rarity legendary \
  --season-name 'Spring 2026' \
  --season-start 2026-03-01"
```

## Arguments

- `--config` (required) Path to a properties file (same keys as application.properties).
- `--season-id` (required) Numeric season ID for SQL inserts.
- `--season-name` (required) Season name used in prompts and summaries.
- `--season-start` (required) Season start date (YYYY-MM-DD).
- `--season-end` (required) Season end date (YYYY-MM-DD).
- `--desired-count` (optional) Overrides `fhpb.ai.trophies.desired-count`.
- `--fallback-image-root` (optional) Root folder for fallback images; default `src/main/resources/static`.
- `--output` (optional) Output SQL path; if omitted, prints to stdout.
- `--prompt-rarity` (optional) Prints a single randomized prompt for `common|uncommon|rare|epic|legendary` and exits.

## Output

The SQL file includes:
- `INSERT INTO trophy_art` statements for shared art records
- `INSERT INTO trophy` statements that reference `art_id`
- `FROM_BASE64(...)` only when generated art bytes are present
- A rollback section that deletes inserted trophy rows and then prunes unreferenced generated art

## Notes

- If `fhpb.ai.trophies.enabled=false`, the CLI uses the fallback template set and emits shared art URLs for the built-in assets.
- When `fhpb.ai.trophies.enabled=true`, OpenAI images are requested and stored in `trophy_art.image_bytes`.
- Paths are resolved from the `trophy-generator-cli` module working directory when using `:trophy-generator-cli:run`.
