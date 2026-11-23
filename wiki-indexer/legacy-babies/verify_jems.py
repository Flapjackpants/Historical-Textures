#!/usr/bin/env python3
"""Verify exported baby .jem files round-trip through the repo's legacy-model
reader (VariantPreviewRenderer.parseLegacyModel/addLegacySubmodels/legacyCubes)
back to the original 1.21.11 vanilla PartDefinition tree.

For every <emf>.jem + <emf>.tree.json pair in the input directory this script
reconstructs the vanilla (pose, cubes) tree from the JEM and compares it with
the dumped 1.21.11 tree. Exits non-zero on any mismatch.

Notes:
- Parts whose cubes mix mirrored and unmirrored cubes are emitted as sibling
  JEM nodes with identical poses; the verifier merges consecutive siblings with
  identical poses before comparing.
- Non-uniform pose scales (llama_baby only) are emitted as a uniform
  geometric-mean approximation; the verifier accepts a uniform JEM scale that
  lies inside the dumped non-uniform range and reports it as a warning.
"""
import json
import math
import sys
from pathlib import Path

EPS = 2e-3
warnings = []


def close(a, b, eps=EPS):
    return abs(a - b) <= eps


def cube_key(c):
    return (
        round(c["origin"][0], 4), round(c["origin"][1], 4), round(c["origin"][2], 4),
        round(c["dims"][0], 4), round(c["dims"][1], 4), round(c["dims"][2], 4),
        round(c["grow"][0], 4), round(c["grow"][1], 4), round(c["grow"][2], 4),
        c["mirror"], round(c["uv"][0], 4), round(c["uv"][1], 4),
    )


def floats(arr, count):
    arr = arr or []
    return [float(arr[i]) if i < len(arr) else 0.0 for i in range(count)]


def reconstruct_node(model, offset):
    """Mirror of addLegacySubmodels/legacyCubes for one JEM node.
    offset = (offsetX, offsetY, offsetZ) passed by the parent call; it is
    (0,0,0) for every node we emit (root translate is [0,0,0] and recursion
    always passes zeros)."""
    t = floats(model.get("translate"), 3)
    r = floats(model.get("rotate"), 3)
    # pose offset = (offsetX - tx, offsetY - ty, offsetZ + tz)
    px = offset[0] - t[0]
    py = offset[1] - t[1]
    pz = offset[2] + t[2]
    # PartPose.offsetAndRotation(x,y,z, rad(-r0), rad(-r1), rad(r2))
    pose = [px, py, pz,
            math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2]),
            1.0, 1.0, 1.0]
    if "scale" in model:
        s = float(model["scale"])
        pose[6] = pose[7] = pose[8] = s
    mirror_all = model.get("mirrorTexture") == "u"
    cubes = []
    for box in model.get("boxes") or []:
        co = floats(box.get("coordinates"), 6)
        u, v = floats(box.get("textureOffset"), 2)
        cx, cy, cz, w, h, d = co
        # legacyCubes(submodel,0,0,0): x=-(cx+w), y=-(cy+h), z=cz
        ox = -(cx + w)
        oy = -(cy + h)
        oz = cz
        if "sizeAdd" in box:
            s = float(box["sizeAdd"])
            grow = [s, s, s]
        elif "sizeAddX" in box:
            grow = [float(box.get("sizeAddX", 0)), float(box.get("sizeAddY", 0)),
                    float(box.get("sizeAddZ", 0))]
        else:
            grow = [0.0, 0.0, 0.0]
        cubes.append({
            "origin": [ox, oy, oz],
            "dims": [w, h, d],
            "grow": grow,
            "mirror": mirror_all,
            "uv": [int(u), int(v)],
        })
    kids = []
    for sub in model.get("submodels") or []:
        kids.append(reconstruct_node(sub, (0.0, 0.0, 0.0)))
    return {"pose": pose, "cubes": cubes, "kids": kids, "id": model.get("id")}


def dump_node(node):
    return {
        "pose": [float(x) for x in node["pose"]],
        "cubes": [{
            "origin": [float(x) for x in c["origin"]],
            "dims": [float(x) for x in c["dims"]],
            "grow": [float(x) for x in c["grow"]],
            "mirror": bool(c["mirror"]),
            "uv": [float(x) for x in c["uv"]],
        } for c in node["cubes"]],
        "kids": [dump_node(k) for k in node["kids"].values()],
    }


def pose_key(p):
    return tuple(round(x, 5) for x in p)


def id_base(i):
    return i.rsplit("_", 1)[0] if i and i.rsplit("_", 1)[-1].isdigit() else i


def merge_sibling_splits(kids):
    """Merge mirror-split siblings: consecutive nodes with identical poses and
    ids sharing a base (the exporter suffixes split parts _2, _3, ...)."""
    merged = []
    for k in kids:
        if (merged and pose_key(merged[-1]["pose"]) == pose_key(k["pose"])
                and id_base(merged[-1].get("id")) == id_base(k.get("id"))):
            merged[-1]["cubes"].extend(k["cubes"])
            # children live on the first sibling only
        else:
            merged.append({"pose": list(k["pose"]), "cubes": list(k["cubes"]),
                           "kids": k["kids"], "id": k.get("id")})
    return merged


def compare_pose(a, b, path, errors):
    for i in range(6):
        if not close(a[i], b[i]):
            errors.append(f"{path}: pose[{i}] {a[i]} != {b[i]}")
    # scale: uniform JEM scale vs dump scale (may be non-uniform -> approximated)
    ja, jb = a[6], b[6]
    dump_uniform = close(b[6], b[7]) and close(b[7], b[8])
    jem_uniform = close(a[6], a[7]) and close(a[7], a[8])
    if dump_uniform and jem_uniform:
        if not close(ja, jb):
            errors.append(f"{path}: scale {ja} != {jb}")
    elif not dump_uniform and jem_uniform:
        lo, hi = min(b[6], b[7], b[8]), max(b[6], b[7], b[8])
        if not (lo - EPS <= ja <= hi + EPS):
            errors.append(f"{path}: approx scale {ja} outside [{lo},{hi}]")
        else:
            warnings.append(f"{path}: non-uniform scale {b[6:9]} approximated as {ja}")
    else:
        errors.append(f"{path}: scale mismatch {a[6:9]} vs {b[6:9]}")


def compare_cubes(a, b, path, errors):
    ka = sorted(cube_key(c) for c in a)
    kb = sorted(cube_key(c) for c in b)
    if len(ka) != len(kb):
        errors.append(f"{path}: cube count {len(ka)} != {len(kb)}")
        return
    for i, (x, y) in enumerate(zip(ka, kb)):
        if x != y:
            errors.append(f"{path}: cube {i} {x} != {y}")


def compare_node(jem_node, dump_node_, path, errors):
    compare_pose(jem_node["pose"], dump_node_["pose"], path, errors)
    compare_cubes(jem_node["cubes"], dump_node_["cubes"], path, errors)
    jk = merge_sibling_splits(jem_node["kids"])
    dk = dump_node_["kids"]
    if len(jk) != len(dk):
        errors.append(f"{path}: child count {len(jk)} != {len(dk)}")
        return
    for i, (a, b) in enumerate(zip(jk, dk)):
        compare_node(a, b, f"{path}/{i}", errors)


def check_jem(jem_path):
    errors = []
    doc = json.loads(jem_path.read_text())
    ts = doc.get("textureSize")
    if not (isinstance(ts, list) and len(ts) == 2):
        errors.append("textureSize missing/not 2 ints")
    models = doc.get("models") or []
    root_count = sum(1 for m in models
                     if m.get("part") == "root" and m.get("id") == "root")
    if root_count != 1:
        errors.append(f"expected exactly one root part, found {root_count}")
    ids = []

    def collect(n):
        ids.append(n.get("id"))
        for s in n.get("submodels") or []:
            collect(s)

    for m in models:
        collect(m)
    if len(ids) != len(set(ids)):
        errors.append("duplicate ids")
    if not any(i and i.startswith("legacy_") for i in ids):
        errors.append("no legacy_ ids")
    for m in models:
        if m.get("part") == "root" and m.get("id") == "root":
            continue
        if (m.get("boxes") or []) or (m.get("submodels") or []):
            errors.append(f"non-root entry {m.get('id')} has boxes/submodels")
    root_entry = next(m for m in models if m.get("part") == "root" and m.get("id") == "root")
    jem_root = {"pose": [0, 0, 0, 0, 0, 0, 1, 1, 1], "cubes": [],
                "kids": [reconstruct_node(s, (0.0, 0.0, 0.0))
                         for s in root_entry.get("submodels") or []]}

    tree_path = jem_path.with_suffix(".tree.json")
    tree = json.loads(tree_path.read_text())
    dump_root = dump_node(tree["root"])
    jk = merge_sibling_splits(jem_root["kids"])
    dk = dump_root["kids"]
    if len(jk) != len(dk):
        errors.append(f"top-level part count {len(jk)} != {len(dk)}")
    else:
        for i, (a, b) in enumerate(zip(jk, dk)):
            compare_node(a, b, f"kid{i}", errors)
    return errors


def main():
    out_dir = Path(sys.argv[1])
    jems = sorted(out_dir.glob("*.jem"))
    if not jems:
        print("no .jem files found")
        return 1
    total = 0
    failed = 0
    for jem in jems:
        errs = check_jem(jem)
        total += 1
        if errs:
            failed += 1
            print(f"FAIL {jem.name}: {len(errs)} errors")
            for e in errs[:10]:
                print("   ", e)
        else:
            print(f"ok   {jem.name}")
    for w in warnings:
        print("WARN", w)
    print(f"{total - failed}/{total} jems verified")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
