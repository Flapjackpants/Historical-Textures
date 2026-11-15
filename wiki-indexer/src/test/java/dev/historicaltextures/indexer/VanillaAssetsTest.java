package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaAssetsTest {
	private static final Set<String> TEXTURES = Set.of(
			"textures/block/grass_block_top.png",
			"textures/item/apple.png",
			"textures/entity/cow/cow_temperate.png",
			"textures/entity/cow/cow_cold.png",
			"textures/entity/cow/cow_temperate_baby.png",
			"textures/painting/aztec2.png"
	);

	private final VanillaAssets assets = new VanillaAssets(TEXTURES, Set.of());

	@Test
	void resolvesNameInContextDirectory() {
		assertEquals(
				List.of("minecraft:textures/block/grass_block_top.png"),
				assets.resolveTexture("grass_block_top", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
	}

	@Test
	void fallsBackToBasenameMatchAcrossDirectories() {
		assertEquals(
				List.of("minecraft:textures/item/apple.png"),
				assets.resolveTexture("apple", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
	}

	@Test
	void resolvesNestedEntityVariantsWithoutBabies() {
		assertEquals(
				List.of(
						"minecraft:textures/entity/cow/cow_cold.png",
						"minecraft:textures/entity/cow/cow_temperate.png"
				),
				assets.resolveTexture("cow", WikiFilenameUtil.CrawlContext.ENTITIES)
		);
	}

	@Test
	void resolvesPaintingInPreferredDirectory() {
		assertEquals(
				List.of("minecraft:textures/painting/aztec2.png"),
				assets.resolveTexture("aztec2", WikiFilenameUtil.CrawlContext.PAINTINGS)
		);
	}

	@Test
	void returnsEmptyForUnknownTexture() {
		assertTrue(assets.resolveTexture("missing", WikiFilenameUtil.CrawlContext.ITEMS).isEmpty());
	}

	@Test
	void loadsTexturesAndSoundEventsFromLoomCache(@TempDir Path cacheDir) throws Exception {
		Path versionDir = Files.createDirectories(cacheDir.resolve("26.3"));
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(versionDir.resolve("minecraft-client.jar")))) {
			zip.putNextEntry(new ZipEntry("assets/minecraft/textures/item/apple.png"));
			zip.write(new byte[]{1, 2, 3});
			zip.closeEntry();
		}
		String hash = "ab12cd34ef56";
		Path indexes = Files.createDirectories(cacheDir.resolve("assets/indexes"));
		Files.writeString(
				indexes.resolve("26.3-34.json"),
				"{\"objects\":{\"minecraft/sounds.json\":{\"hash\":\"" + hash + "\"}}}"
		);
		Path object = Files.createDirectories(cacheDir.resolve("assets/objects/ab")).resolve(hash);
		Files.writeString(object, "{\"entity.cow.ambient\":{}}");

		Optional<VanillaAssets> loaded = VanillaAssets.load(cacheDir, "26.3");
		assertTrue(loaded.isPresent());
		assertEquals(
				List.of("minecraft:textures/item/apple.png"),
				loaded.get().resolveTexture("apple", WikiFilenameUtil.CrawlContext.ITEMS)
		);
		assertEquals(Set.of("entity.cow.ambient"), loaded.get().soundEvents());
	}

	@Test
	void returnsEmptyWhenClientJarIsMissing(@TempDir Path cacheDir) throws Exception {
		Path empty = Files.createDirectories(cacheDir.resolve("empty"));
		assertTrue(VanillaAssets.load(empty, "26.3").isEmpty());
	}

	private static final Set<String> QUALIFIED_TEXTURES = Set.of(
			"textures/entity/creeper/creeper.png",
			"textures/entity/banner/creeper.png",
			"textures/entity/shield/creeper.png",
			"textures/entity/enderman/enderman.png",
			"textures/entity/enderman/enderman_eyes.png",
			"textures/block/bamboo_small_leaves.png",
			"textures/entity/boat/bamboo.png",
			"textures/item/bamboo.png",
			"textures/block/fern.png",
			"textures/painting/fern.png",
			"textures/block/bush.png",
			"textures/item/chicken.png",
			"textures/entity/chicken/chicken_temperate.png",
			"textures/entity/chicken/chicken_cold.png",
			"textures/entity/chicken/chicken_temperate_baby.png",
			"textures/entity/decorated_pot/archer_pottery_pattern.png",
			"textures/particle/spark_0.png",
			"textures/entity/shulker/spark.png",
			"textures/block/farmland_moist.png",
			"textures/painting/water.png",
			"textures/block/item_frame.png",
			"textures/item/item_frame.png"
	);

	private final VanillaAssets qualified = new VanillaAssets(QUALIFIED_TEXTURES, Set.of());

	@Test
	void prefersSameFolderMatchesAndSkipsExcludedEntityFolders() {
		assertEquals(
				List.of("minecraft:textures/entity/creeper/creeper.png"),
				qualified.resolveTextureFile("Creeper (texture) JE2.png", WikiFilenameUtil.CrawlContext.ENTITIES)
		);
		assertEquals(
				List.of("minecraft:textures/block/bamboo_small_leaves.png"),
				qualified.resolveTextureFile("Bamboo (small leaves texture) JE1.png", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
		assertEquals(
				List.of("minecraft:textures/item/bamboo.png"),
				qualified.resolveTextureFile("Bamboo (texture) JE1.png", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
		assertEquals(
				List.of("minecraft:textures/entity/enderman/enderman_eyes.png"),
				qualified.resolveTextureFile("Enderman (eyes texture) JE1.png", WikiFilenameUtil.CrawlContext.ENTITIES)
		);
	}

	@Test
	void resolvesQualifiedNamesByPageContext() {
		assertEquals(
				List.of(),
				qualified.resolveTextureFile("Fern (carried texture) BE1.png", WikiFilenameUtil.CrawlContext.ITEMS)
		);
		assertEquals(
				List.of("minecraft:textures/block/bush.png"),
				qualified.resolveTextureFile("Bush (item) JE1 BE1.png", WikiFilenameUtil.CrawlContext.ITEMS)
		);
		assertEquals(
				List.of(
						"minecraft:textures/entity/chicken/chicken_cold.png",
						"minecraft:textures/entity/chicken/chicken_temperate.png"
				),
				qualified.resolveTextureFile("Chicken (texture) JE1.png", WikiFilenameUtil.CrawlContext.ENTITIES)
		);
		assertEquals(
				List.of("minecraft:textures/entity/decorated_pot/archer_pottery_pattern.png"),
				qualified.resolveTextureFile("Archer Pottery Pattern (texture).png", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
		assertEquals(
				List.of("minecraft:textures/particle/spark_0.png"),
				qualified.resolveTextureFile("Spark (texture 0) JE1 BE1.png", WikiFilenameUtil.CrawlContext.PARTICLES)
		);
		assertEquals(
				List.of("minecraft:textures/block/farmland_moist.png"),
				qualified.resolveTextureFile("Farmland (wet texture) JE1 BE1.png", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
		assertEquals(
				List.of(),
				qualified.resolveTextureFile("Water (texture) BE3.png", WikiFilenameUtil.CrawlContext.BLOCKS)
		);
		assertEquals(
				List.of("minecraft:textures/block/item_frame.png"),
				qualified.resolveTextureFile("Item Frame (texture) JE2 BE2.png", WikiFilenameUtil.CrawlContext.ENTITIES)
		);
	}
}
