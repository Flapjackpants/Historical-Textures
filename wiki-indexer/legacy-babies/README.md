# legacy-babies — pre-26.1 baby model exporter

Generates the EMF `.jem` resources under
`src/main/resources/assets/historical_textures/legacy_models/babies/` plus the
`babies.json` row index. Each `.jem` reproduces the 1.21.11 (pre baby-redesign)
baby layer geometry — i.e. the adult mesh with the old `BabyModelTransform` /
`MeshTransformer.scaling` pose baked in — so babies keep their pre-26.1 look
under EMF while using the user's chosen adult texture.

## Inputs (not committed — see "Sources")

| File | What it is |
| --- | --- |
| `babies.json` | Research output: one record per 26.3 baby texture → adult texture, `emfBabyModel`, `babyLayer26`, supported flag. Produced by `build_babies.py` from the texture lists and EMF name dump. |
| `parts_263.txt` | `model/layer|top-level-part-names` dump of every 26.3 `LayerDefinitions.createRoots()` entry (Mojmap harness `Dump.java`). |
| `client_mappings_1.21.11.txt` | Mojang client mappings for 1.21.11, used to resolve obfuscated class/field names. |
| 1.21.11 client jar | `/Applications/ATLauncher.app/Contents/Java/libraries/net/minecraft/client/1.21.11/client-1.21.11.jar` (ATLauncher install; any 1.21.11 client jar works). |
| 26.3 jars | `~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar` and `minecraft-client-only.jar` (produced by `./gradlew` on this repo). |
| EMF jar | `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/entity-model-features/3.3.12-fabric-26.3/.../entity-model-features-3.3.12-fabric-26.3.jar`. |

Mappings download URL (already fetched once):
`https://piston-meta.mojang.com/v1/packages/a969d96978af7b52dba8df44e177f48ec4ebe0d8/1.21.11.json`
→ `downloads.client_mappings.url` → `client.txt`.

## Pipeline

```sh
# 1. worklist: one entry per distinct EMF baby model
python3 -I prep_worklist.py babies.json parts_263.txt worklist.json

# 2. compile the exporter (needs gson on the classpath; any recent gson jar works)
javac -cp <gson.jar> -d classes src/legacybabies/ExportBabies.java

# 3. run against the obfuscated 1.21.11 client
java -cp "classes:<1.21.11 client jar>:<gson.jar>:<joml.jar>" \
    legacybabies.ExportBabies worklist.json out/
# writes <emf>.jem + <emf>.tree.json + manifest.json per model

# 4. verify the emitted .jem files round-trip through the repo's
#    VariantPreviewRenderer coordinate/pose conventions
#    (each <emf>.tree.json dump sits next to its .jem in out/)
python3 -I verify_jems.py out/
```

`babies.json` (the resource index) is assembled from the research `babies.json`:
rows with `supported && emfBabyModel != null`, `jem` = `babies/<emf>.jem`,
`fallbackAdultTexture` set only for rabbit (see below).

## Conventions

The output is the exact inverse of `VariantPreviewRenderer.parseLegacyModel`:

- One top-level `{"part":"root","id":"root","invertAxis":"xy",...}` entry holds
  the whole legacy tree as nested `submodels` with `legacy_`-prefixed ids.
- Pose → `translate: [-x, -y, z]` plus `rotate: [-deg(xRot), -deg(yRot), deg(zRot)]`.
- Cube origin/dims → `coordinates: [-(ox+dx), -(oy+dy), oz, dx, dy, dz]`,
  `textureOffset: [u, v]` — matching `legacyCubes` in the renderer.
- `CubeDeformation` → `sizeAdd` when uniform on all axes, else per-axis
  `sizeAddX/Y/Z` (only the horse-family legs use non-uniform grow `0,5.5,0`;
  the preview reader only reads scalar `sizeAdd`, EMF reads all three).
- Mirrored cubes are split into a sibling node carrying `mirrorTexture: "u"`.
- `textureSize` is the 1.21.11 layer's material size.
- One empty `boxes: []` top-level entry per 26.3 baby-layer top-level part
  (`parts26`, minus `root` which the root entry already binds) hides the modern
  26.3 baby geometry.
- Animations live on the root entry in EMF expression syntax, targeting
  `legacy_*` ids: head pitch/yaw, quadruped diagonal limb swing
  (`cos(limb_swing*0.6662)[+pi]*1.4*limb_speed`), humanoid arm/leg swing
  (`*limb_speed`, legs `*1.4`). Non-zero rest `xRot`/`yRot` are appended as
  `+ <radians>` offsets. Everything else (tails, ears, wings, jelly-ish parts)
  is static — a known approximation, same as the existing `*_legacy.jem` files.

## Scale representation

Part `PartPose` scale is emitted as the `.jem` `scale` property. The format —
and the repo preview reader — only supports a **uniform** scalar. Uniform pose
scales are emitted exactly (e.g. `cow_baby` body/legs = `0.5`, the baked
`BabyModelTransform(false, 8, 6, {head})` half-scale). The only non-uniform
case is `llama_baby` (per-axis pose scales like `0.714, 0.649, 0.794`); it is
approximated with the geometric mean `cbrt(x*y*z)` and logged as a warning by
the exporter. EMF 3.3.12's own serializer also emits only scalar `scale`
(verified by decompiling `EntityModelAnimation`/jem writer classes), so there
is no more faithful representation available.

## Cold/warm variants

26.1 removed the distinct cold chicken/pig baby layers: both cold variants
share the temperate `CHICKEN_BABY`/`PIG_BABY` layers. The exporter still emits
`cold_chicken_baby.jem` / `cold_pig_baby.jem` from the 1.21.11
`cold_chicken_baby`/`cold_pig_baby` layers (which did exist and differ), but
their blank hiding entries cover the shared temperate 26.3 parts. Cold/warm
cows keep separate EMF models (`cold_cow_baby`, `warm_cow_baby`).

## Rabbit / axolotl adult fallback

Both mobs' **adult** geometry changed between 1.21.11 and 26.3, so their baby
`.jem` UV layouts may not match the 26.3 adult textures. Cube-level comparison
(`DumpUV263` vs the 1.21.11 tree dumps):

- **axolotl**: identical cube origins/dims/UVs (11 cubes, 64x64) → the 26.3
  adult texture fits the legacy UVs; **no fallback needed**.
- **rabbit**: layout changed (12 → 9 cubes, texture 64x32 → 64x64) → the eight
  1.21.11 rabbit variant textures were extracted from the 1.21.11 jar to
  `legacy_models/babies/textures/rabbit_<variant>.png` and the eight rabbit
  rows set `fallbackAdultTexture: "babies/textures/rabbit_<variant>.png"`.
  (`adultGeometryChanged` flagged exactly these two mobs in the research.)

## Known limits

- Animations are the generic quadruped/humanoid set only; mob-specific motion
  (rabbit hop was handled by a bespoke legacy jem, fox tail, etc.) is not
  reproduced.
- Non-uniform pose scales approximated (llama only, see above).
- Non-uniform cube grow is invisible in the config-screen preview (reader
  limitation) but correct in-game under EMF.
- Unsupported research rows (armadillo, strider, collars, saddles, equipment
  overlays, outer layers) are excluded — EMF has no baby model for them or the
  texture is not a standalone baby skin.
