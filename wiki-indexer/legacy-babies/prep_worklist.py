#!/usr/bin/env python3
"""Build the ExportBabies worklist from the Stage-3 research outputs.

Inputs (produced by the 1.21.11/26.3 research harnesses, see README.md):
  babies.json        - per-texture mapping produced by build_babies.py
  parts_263.txt      - "model/layer|top,level,parts" dump of every 26.3 layer

Output: worklist.json - [{"emf","location","parts26":[...]}] consumed by
ExportBabies.java.

Location rules:
  * 1.21.11 layer: "minecraft:<emfBabyModel>/main". The only exception is
    sheep_baby_wool, whose 1.21.11 layer is "minecraft:sheep_baby/wool"
    (ModelLayers.SHEEP_BABY_WOOL... actually sheep baby wool sublayer).
  * parts26 comes from parts_263.txt. The 26.3 location is derived from the
    row's babyLayer26 field (lowercased + "/main"), except SHEEP_BABY_WOOL
    which lives at "minecraft:sheep_baby/wool" in 26.3 as well. Cold chicken
    and cold pig have no dedicated 26.3 layer; their rows point at the shared
    CHICKEN_BABY / PIG_BABY layers, so the generated .jem hides the temperate
    26.3 parts.
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# (emfBabyModel) -> overrides for non-"/main" layer paths
LAYER_OVERRIDES = {
    "sheep_baby_wool": ("minecraft:sheep_baby/wool", "minecraft:sheep_baby/wool"),
}


def layer26_to_location(field: str) -> str:
    return "minecraft:" + field.lower() + "/main"


def main() -> None:
    research = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "babies.json"
    parts_file = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "parts_263.txt"
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else HERE / "worklist.json"

    parts = {}
    for line in parts_file.read_text().splitlines():
        key, _, value = line.partition("|")
        parts[key] = value.split(",") if value else []

    rows = json.loads(research.read_text())
    groups = {}
    for row in rows:
        if not row.get("supported") or not row.get("emfBabyModel"):
            continue
        emf = row["emfBabyModel"]
        if emf in groups:
            continue
        loc263 = layer26_to_location(row["babyLayer26"])
        loc263, loc12111 = LAYER_OVERRIDES.get(emf, (loc263, "minecraft:" + emf + "/main"))
        part_names = parts.get(loc263)
        if part_names is None:
            raise SystemExit(f"{emf}: no 26.3 layer {loc263} in {parts_file}")
        # "root" is already bound by the single root entry emitted for the
        # legacy geometry, so it must not get a blank hiding entry too.
        part_names = [p for p in part_names if p != "root"]
        groups[emf] = {"emf": emf, "location": loc12111, "parts26": part_names}

    worklist = [groups[k] for k in sorted(groups)]
    out.write_text(json.dumps(worklist, indent=1) + "\n")
    print(f"wrote {len(worklist)} models to {out}")


if __name__ == "__main__":
    main()
