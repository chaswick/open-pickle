#!/usr/bin/env python3
"""Build a self-contained art-only MySQL import and review gallery.

Usage: python3 artwork/trophy-pack-v1/build_pack.py
       python3 artwork/trophy-pack-v1/build_pack.py --preview-only

Generation uses the built-in image tool; this script only packages existing files.
"""

import argparse
import base64
import hashlib
import html
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parent
VERSION = "openpickle-art-v1"


def quote(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def image_record(path, expected_size):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not a PNG: {path}")
    dimensions = struct.unpack(">II", data[16:24])
    if dimensions != (expected_size, expected_size):
        raise ValueError(f"Wrong image dimensions for {path}: {dimensions}")
    # Same bytes-only storage-key convention used by the app's art normalization.
    digest = hashlib.sha256(data).hexdigest()
    key = hashlib.sha256((digest + "|").encode()).hexdigest()
    return key, digest, data


def build_sql(entries):
    images = {}
    mapping = []
    for entry in entries:
        main_key, main_digest, main = image_record(ROOT / entry["file"], 512)
        badge_key, badge_digest, badge = image_record(ROOT / entry["badge_file"], 128)
        images[main_key] = main
        images[badge_key] = badge
        entry["sha256"] = main_digest
        entry["badge_sha256"] = badge_digest
        mapping.append((entry, main_key, badge_key))

    statements = [
        "-- Generated artwork-only import. Apply after the relevant trophy definitions exist.",
        "-- Match by exact trophy title and rarity; preserve IDs, rules and earned awards.",
        "-- Keep old artwork and original references for rollback. Safe to rerun.",
        "SET NAMES utf8mb4;",
        "CREATE TABLE IF NOT EXISTS trophy_art_pack_backup (\n"
        " pack_version VARCHAR(48) NOT NULL, entity_type VARCHAR(16) NOT NULL,\n"
        " entity_id BIGINT NOT NULL, old_art_id BIGINT NULL, old_badge_art_id BIGINT NULL,\n"
        " new_art_id BIGINT NOT NULL, new_badge_art_id BIGINT NOT NULL,\n"
        " created_at DATETIME(6) NOT NULL,\n"
        " PRIMARY KEY (pack_version, entity_type, entity_id)\n"
        ");",
        "DROP TEMPORARY TABLE IF EXISTS trophy_art_pack_map;",
        "CREATE TEMPORARY TABLE trophy_art_pack_map (\n"
        " title VARCHAR(96) NOT NULL, rarity VARCHAR(24) NOT NULL,\n"
        " art_id BIGINT NOT NULL, badge_art_id BIGINT NOT NULL,\n"
        " PRIMARY KEY (title, rarity)\n"
        ");",
        "START TRANSACTION;",
    ]
    for key, data in images.items():
        encoded = base64.b64encode(data).decode("ascii")
        statements.append(
            "INSERT INTO trophy_art (storage_key,image_url,image_bytes,created_at,updated_at) "
            f"VALUES ({quote(key)},NULL,FROM_BASE64({quote(encoded)}),NOW(6),NOW(6)) "
            "ON DUPLICATE KEY UPDATE storage_key=VALUES(storage_key);"
        )
    for entry, main, badge in mapping:
        statements.append(
            "INSERT INTO trophy_art_pack_map (title,rarity,art_id,badge_art_id) VALUES ("
            f"{quote(entry['title'])},{quote(entry['rarity'])},"
            f"(SELECT id FROM trophy_art WHERE storage_key={quote(main)}),"
            f"(SELECT id FROM trophy_art WHERE storage_key={quote(badge)}));"
        )
    for table, kind in [("trophy_catalog", "catalog"), ("trophy", "trophy")]:
        statements.append(
            "INSERT INTO trophy_art_pack_backup "
            "(pack_version,entity_type,entity_id,old_art_id,old_badge_art_id,"
            "new_art_id,new_badge_art_id,created_at) "
            f"SELECT {quote(VERSION)},{quote(kind)},t.id,t.art_id,t.badge_art_id,"
            "m.art_id,m.badge_art_id,NOW(6) "
            f"FROM {table} t JOIN trophy_art_pack_map m "
            "ON BINARY t.title=BINARY m.title AND t.rarity=m.rarity WHERE 1=1 "
            "ON DUPLICATE KEY UPDATE new_art_id=VALUES(new_art_id),"
            "new_badge_art_id=VALUES(new_badge_art_id);"
        )
        statements.append(
            f"UPDATE {table} t JOIN trophy_art_pack_map m "
            "ON BINARY t.title=BINARY m.title AND t.rarity=m.rarity "
            "SET t.art_id=m.art_id,t.badge_art_id=m.badge_art_id,t.updated_at=NOW(6);"
        )
    statements += [
        "COMMIT;",
        "SELECT entity_type,COUNT(*) AS backed_up_rows FROM trophy_art_pack_backup "
        f"WHERE pack_version={quote(VERSION)} GROUP BY entity_type;",
        "-- Definitions not installed yet are skipped. Reapply after creating them.",
        "SELECT m.title AS not_installed_title,m.rarity FROM trophy_art_pack_map m "
        "WHERE NOT EXISTS (SELECT 1 FROM trophy_catalog c "
        "WHERE BINARY c.title=BINARY m.title AND c.rarity=m.rarity) "
        "AND NOT EXISTS (SELECT 1 FROM trophy t "
        "WHERE BINARY t.title=BINARY m.title AND t.rarity=m.rarity) ORDER BY m.title;",
        "DROP TEMPORARY TABLE trophy_art_pack_map;",
    ]
    (ROOT / "apply.sql").write_text("\n\n".join(statements) + "\n")

    rollback = [
        "-- Restore artwork references captured when this pack was first applied.",
        "-- Rows edited to different artwork afterward are left alone.",
        "-- Existing and generated art rows are retained; no awards or definitions are deleted.",
        "START TRANSACTION;",
    ]
    for table, kind in [("trophy_catalog", "catalog"), ("trophy", "trophy")]:
        rollback.append(
            f"UPDATE {table} t JOIN trophy_art_pack_backup b "
            f"ON b.entity_id=t.id AND b.entity_type={quote(kind)} "
            f"AND b.pack_version={quote(VERSION)} "
            "SET t.art_id=b.old_art_id,t.badge_art_id=b.old_badge_art_id,t.updated_at=NOW(6) "
            "WHERE t.art_id <=> b.new_art_id AND t.badge_art_id <=> b.new_badge_art_id;"
        )
    rollback.append("COMMIT;")
    (ROOT / "rollback.sql").write_text("\n\n".join(rollback) + "\n")


def build_gallery(manifest):
    entries = manifest["entries"]
    ready = sum((ROOT / e["file"]).is_file() for e in entries)
    cards = []
    for e in entries:
        title = html.escape(e["title"])
        rarity = e["rarity"].lower()
        exists = (ROOT / e["file"]).is_file()
        image = (
            f'<a href="{e["file"]}" target="_blank" rel="noopener">'
            f'<img loading="lazy" src="{e["file"]}" alt="{title}"></a>'
            if exists else '<span class="pending">Generating</span>'
        )
        retained = '<p class="retained">Existing Lucky Charm artwork retained.</p>' if e.get("preserve_existing") else ""
        condition = html.escape(e["condition"] or "Custom production trophy")
        cards.append(
            f'<article class="card {rarity}" data-rarity="{rarity}" '
            f'data-group="{e["group"]}" data-title="{html.escape(e["title"].lower(), quote=True)}">'
            f'<div class="art">{image}</div><div class="copy"><span class="rarity">{rarity.title()}</span>'
            f'<h2>{title}</h2><p>{condition}</p>{retained}</div></article>'
        )
    gallery = """<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Pickleball trophy art pack</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#edf3f7;color:#193647;font:16px/1.5 system-ui,sans-serif}
main{max-width:1280px;margin:auto;padding:24px}.panel,.card{background:white;border:1px solid #d5e1e9;border-radius:16px;padding:20px}
h1,h2{line-height:1.2}h1{margin:0 0 12px}h2{font-size:19px;margin:8px 0}p{margin:8px 0}.panel{margin-bottom:20px}
a{color:#086c9b}.controls{display:flex;gap:12px;flex-wrap:wrap;align-items:end}.controls label{display:flex;flex-direction:column;gap:6px;flex:1;min-width:130px}
input,select,button{font:inherit;padding:10px;border:1px solid #a9bdca;border-radius:8px}button{cursor:pointer;background:#e9f3f8}
.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.card{padding:12px;overflow:hidden;border-top:4px solid #a97a47}
.card.uncommon{border-top-color:#199e9d}.card.rare{border-top-color:#3788d0}.card.epic{border-top-color:#8951cf}.card.legendary{border-top-color:#e0a73a}
.art{height:245px;display:flex;align-items:center;justify-content:center;background:repeating-conic-gradient(#edf1f4 0% 25%,#fff 0% 50%) 50%/20px 20px;border-radius:10px}
.art img{display:block;max-width:100%;max-height:245px;object-fit:contain}.art a{max-width:100%;max-height:100%;display:flex;justify-content:center}
.copy{padding:8px 4px 0}.copy p{font-size:14px;color:#526879}.rarity{font-size:12px;text-transform:uppercase;font-weight:700;letter-spacing:.07em}.retained{font-style:italic}
.pending{color:#526879}.status{color:#526879;font-size:14px}.dark{background:#18232e;color:#e6edf5}.dark .panel,.dark .card{background:#243544;border-color:#425769}
.dark .copy p,.dark .status{color:#c5d4de}.dark .art{background:#17232e}.dark a{color:#8bc8ff}
body.small .art,body.small .art img{height:64px;max-height:64px}body.medium .art,body.medium .art img{height:128px;max-height:128px}
article[hidden]{display:none}@media(max-width:1050px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:750px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.art,.art img{max-height:220px}.art{height:220px}}
@media(max-width:480px){main{padding:14px}.grid{grid-template-columns:1fr}.art,.art img{max-height:300px}.art{height:300px}}
</style><main><header class="panel"><h1>Pickleball trophy art pack</h1>
<p>Individual illustrations in a shared shield family. Bronze, teal, sapphire, violet and gold frames mark the five rarities.</p>
<p>Lucky Charm keeps its existing holiday artwork. All other artwork is new. Main images and profile badges use the same illustration.</p>
<p><a href="trophy-pack-v1.zip">Download the drop-in pack</a> · <a href="README.txt">Installation notes</a> · <a href="manifest.json">Complete manifest</a> · <a href="prompts.json">Generation prompts</a> · <a href="contact-sheet.png">Contact sheet</a></p>
<p class="status">READY_COUNT of TOTAL_COUNT images ready.</p></header>
<section class="panel" aria-label="Gallery controls"><div class="controls"><label>Find a trophy<input id="search" type="search" placeholder="Trophy name"></label>
<label>Rarity<select id="rarity"><option value="all">All rarities</option><option>Common</option><option>Uncommon</option><option>Rare</option><option>Epic</option><option>Legendary</option></select></label>
<label>Collection<select id="group"><option value="all">All trophies</option><option value="season">Season and special</option><option value="division">Division finishes</option><option value="round-robin">Round robin</option><option value="story">Story trackers</option></select></label>
<label>Preview size<select id="size"><option value="large">Card</option><option value="medium">128px badge</option><option value="small">64px badge</option></select></label>
<button id="background" type="button">Dark background</button></div><p id="count" class="status" aria-live="polite"></p></section>
<div class="grid">CARDS</div></main><script>
const search=document.querySelector('#search'),rarity=document.querySelector('#rarity'),group=document.querySelector('#group'),cards=[...document.querySelectorAll('article.card')];
function filter(){let visible=0;for(const c of cards){c.hidden=!(c.dataset.title.includes(search.value.toLowerCase())&&(rarity.value==='all'||c.dataset.rarity===rarity.value.toLowerCase())&&(group.value==='all'||c.dataset.group===group.value));if(!c.hidden)visible++;}document.querySelector('#count').textContent=visible+' trophies shown';}
for(const el of [search,rarity,group])el.addEventListener('input',filter);filter();
document.querySelector('#size').addEventListener('change',e=>{document.body.classList.remove('small','medium');if(e.target.value!=='large')document.body.classList.add(e.target.value)});
document.querySelector('#background').addEventListener('click',e=>{document.body.classList.toggle('dark');e.target.textContent=document.body.classList.contains('dark')?'Light background':'Dark background'});
</script></html>"""
    (ROOT / "index.html").write_text(
        gallery.replace("READY_COUNT", str(ready)).replace("TOTAL_COUNT", str(len(entries))).replace("CARDS", "".join(cards))
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-only", action="store_true")
    args = parser.parse_args()
    manifest = json.loads((ROOT / "manifest.json").read_text())
    build_gallery(manifest)
    if args.preview_only:
        return
    prompts = []
    for entry in manifest["entries"]:
        if entry.get("preserve_existing"):
            prompts.append({"slug": entry["slug"], "mode": "preserved production artwork", "prompt": None})
            continue
        record = json.loads((ROOT / "prompts" / (entry["slug"] + ".json")).read_text())
        record["references"] = [
            ref.split("/artwork/trophy-pack-v1/")[-1]
            for ref in record.get("references", [])
        ]
        prompts.append(record)
    (ROOT / "prompts.json").write_text(json.dumps(prompts, indent=2) + "\n")
    build_sql(manifest["entries"])
    (ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    files = ["apply.sql", "rollback.sql", "README.txt", "manifest.json", "prompts.json", "index.html", "build_pack.py", "asset-validation.json"]
    files += [e[key] for e in manifest["entries"] for key in ["file", "badge_file"]]
    files += [str(path.relative_to(ROOT)) for path in (ROOT / "references").rglob("*.png")]
    files += [str(path.relative_to(ROOT)) for path in (ROOT / "prompts").glob("*.json")]
    files += [name for name in ["contact-sheet.png", "import-validation.json", "browser-validation.json", "validation-summary.txt", "people-refresh-review.html", "people-refresh-validation.json", "people-refresh-import-validation.json", "people-refresh-browser-validation.json"] if (ROOT / name).is_file()]
    with zipfile.ZipFile(ROOT / "trophy-pack-v1.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        for name in dict.fromkeys(files):
            bundle.write(ROOT / name, name)
    print(f"Built {len(manifest['entries'])} artwork mappings, SQL import, rollback and ZIP.")


if __name__ == "__main__":
    main()
