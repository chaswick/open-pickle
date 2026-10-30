PICKLEBALL TROPHY ART PACK V1

Individual trophy illustrations with consistent rarity frames.

CONTENTS
- 66 title/rarity mappings: 35 season/special, 14 division, 9 round robin,
  and 8 story trackers.
- assets/: 512x512 PNG images for trophy cards.
- badges/: 128x128 PNG versions of the same illustrations for profile badges.
- Lucky Charm retains its existing holiday artwork. The import restores it
  as the main trophy image as well as using it for the profile badge.
- apply.sql: inserts shared artwork and updates matching trophy/catalog rows.
- rollback.sql: restores the captured artwork references.
- manifest.json: trophy names, rarity, conditions, filenames and checksums.
- prompts.json: generation prompts and reference-image roles.
- index.html: searchable preview; open directly or serve from a web server.
- contact-sheet.png: overview of all 66 images.
- validation-summary.txt and validation JSON: local database/browser checks.

The full-resolution generated originals are retained in the workspace under
originals/. The downloadable ZIP contains the production images and badges.
references/ also retains the earlier EPIC rarity-badge experiment.
Country flags and unrelated profile badges keep their existing assignments.

INSTALL
1. Apply the app release that creates any new trophy definitions you want
   covered, including the round-robin trophies.
2. Unzip this pack. Run the following from its extracted directory, replacing
   YOUR_DATABASE with the actual database name:

mysql -h 127.0.0.1 -P 3307 -u fhpb -p"$MYSQL_ROOT_PASSWORD" \
  --default-character-set=utf8mb4 --max-allowed-packet=256M \
  YOUR_DATABASE < apply.sql > trophy-art-install-results.txt

The output lists updated row totals and definitions not installed yet.
Skipped definitions can receive their artwork by rerunning the import after
those trophies are created. This also applies to newly enabled story trackers.
New season trophies inherit the updated images from existing catalog entries.

The script matches exact title and rarity across catalog entries and season
instances. It updates art_id, badge_art_id and updated_at. Trophy IDs, unlock
rules, rarity, awarded trophies, counts and notifications are preserved.
Images are stored in trophy_art.image_bytes, so no static-file deployment or
external image hosting is required. Reapplying does not duplicate image rows.

ROLLBACK
Use the same connection options with rollback.sql instead of apply.sql.
The importer retains the old artwork and records original references in
`trophy_art_pack_backup` under pack version `openpickle-art-v1`.

Rollback restores recorded rows that still use this pack's assigned images.
Later manual artwork changes are preserved. Trophy rows created after the
import are outside that original snapshot. Artwork rows are retained so a
rollback or reapplication does not remove shared image data.

DESIGN AND GENERATION
New images were produced with the built-in image_gen tool. The Common master
was based on the existing coastal illustration style. Each rarity uses a
master reference with a shared shield silhouette, inset rope, corner studs
and bottom pickleball seal. Individual scenes represent the trophy's rule.

Common: warm bronze and cream.
Uncommon: teal enamel and brass.
Rare: sapphire and silver.
Epic: indigo gunmetal and violet.
Legendary: gold, faceted magenta and laurels.

Titles are rendered by the app rather than embedded into the images.
Lucky Charm is the deliberate preserved-artwork exception to the new frames.
The scope covers implemented definitions and the round-robin draft. Archived
idea spreadsheets are not added as trophies or included in the artwork scope.

REBUILD
The Python standard-library script build_pack.py rebuilds apply.sql,
rollback.sql, index.html and the ZIP from the checked-in production PNGs,
manifest.json and prompts.json:

python3 build_pack.py
