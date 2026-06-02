# Historical Textures

Fabric mod for **Minecraft 26.1+** that lets you pick historical textures and sounds per block, item, entity, and sound event. Assets are indexed from [minecraft.wiki](https://minecraft.wiki) and bundled with the mod.

## Requirements

- Minecraft 26.1+
- Fabric Loader 0.19+
- Fabric API
- [Mod Menu](https://modrinth.com/mod/modmenu) (recommended)
- Java **25** for building (Gradle toolchains auto-download via Foojay)

## Player usage

1. Install the mod and Mod Menu.
2. Open **Mods → Historical Textures → Configure**.
3. Choose a tab (Blocks, Items, Entities, Sounds), pick a target, then select a historical variant.
4. Click **Apply & Reload** to generate the overlay resource pack and reload client resources.

Selections are stored in `config/historical_textures.json`. Generated pack files live in `config/historical_textures/overlay/`.

## Building

```bash
./gradlew build
```

### Refresh catalog from the wiki

Quick index (override list only, used for default builds):

```bash
./gradlew :wiki-indexer:indexQuick
```

Full wiki crawl (slow, downloads all images on seed pages):

```bash
./gradlew :wiki-indexer:run
```

Output is copied to `src/main/resources/assets/historical_textures/catalog/`.

## Architecture

- **wiki-indexer** — build-time MediaWiki crawler and asset downloader
- **HistoricalCatalog** — loads bundled `catalog.json` and assets from the mod JAR
- **ModConfig** — player choices
- **OverlayPackManager** — writes a dynamic resource pack and enables it via `PackRepository`
- **ModMenu** — configuration UI

## Attribution

Texture and sound files documented on minecraft.wiki. Wiki text is [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Minecraft assets remain property of Mojang/Microsoft. See the in-game **Credits** screen.

## Manual test checklist

- [ ] `./gradlew build` succeeds on JDK 25 toolchain
- [ ] Mod appears in Mod Menu with config button
- [ ] Select **Stone** → older variant → **Apply & Reload** → stone texture changes in world
- [ ] Select **entity.cow.hurt** → old sound → hear change in game
- [ ] **Clear all** restores vanilla after reload
- [ ] Game launches offline (no wiki network needed)
# Historical-Textures
