#!/usr/bin/env python3
"""CI : versions Modrinth des dependances (utilisees par les tests serveur et client)."""
import json, os, sys, urllib.request

OUT = sys.argv[1] if len(sys.argv) > 1 else "out/discover"
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "cobblepulse-crates-ci/1.0 (github actions)"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


lines = []
SLUGS = ["cobblemon", "fabric-api", "fabric-language-kotlin", "luckperms"]
modrinth = {}
for slug in SLUGS:
    try:
        vs = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D"))
        modrinth[slug] = vs
        lines.append("== modrinth " + slug)
        lines.extend(f'{v["version_number"]} | {v["date_published"]}' for v in vs[:5])
    except Exception as e:
        lines.append(f"== modrinth {slug} ERR {e}")

with open(os.path.join(OUT, "modrinth.json"), "w") as fh:
    json.dump({k: [{"version_number": v["version_number"], "id": v["id"], "files": v["files"],
                    "dependencies": v.get("dependencies", [])} for v in vs[:10]] for k, vs in modrinth.items()}, fh, indent=1)
with open(os.path.join(OUT, "versions.txt"), "w") as fh:
    fh.write("\n".join(lines) + "\n")
print("discover done")
