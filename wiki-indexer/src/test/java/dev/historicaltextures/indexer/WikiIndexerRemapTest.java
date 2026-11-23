package dev.historicaltextures.indexer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiIndexerRemapTest {
	@Test
	void rewritesTargetsAndDropsUnmatchedTextures(@TempDir Path tempDir) throws Exception {
		Path cacheDir = Files.createDirectories(tempDir.resolve("loom"));
		Path versionDir = Files.createDirectories(cacheDir.resolve("26.3"));
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(versionDir.resolve("minecraft-client.jar")))) {
			writeEntry(zip, "assets/minecraft/textures/entity/creeper/creeper.png");
			writeEntry(zip, "assets/minecraft/textures/entity/banner/creeper.png");
		}

		Path outputRoot = Files.createDirectories(tempDir.resolve("catalog"));
		JsonObject existing = new JsonObject();
		existing.addProperty("schemaVersion", 2);
		JsonArray variants = new JsonArray();
		variants.add(variant(
				"creeper_texture_je2_png",
				"Creeper (texture) JE2.png",
				"Java_Edition_history_of_textures/Entities",
				"minecraft:textures/entity/banner/creeper.png",
				"minecraft:textures/entity/creeper/creeper.png"
		));
		variants.add(variant(
				"water_texture_be3_png",
				"Water (texture) BE3.png",
				"Bedrock_Edition_history_of_textures/Blocks",
				"minecraft:textures/painting/water.png"
		));
		existing.add("variants", variants);
		existing.add("soundVariants", new JsonArray());
		Files.writeString(outputRoot.resolve("catalog.json"), existing.toString());

		new WikiIndexer(outputRoot, false, false, true, false, cacheDir, "26.3").run();

		JsonObject written = JsonParser
				.parseString(Files.readString(outputRoot.resolve("catalog.json")))
				.getAsJsonObject();
		JsonArray remaining = written.getAsJsonArray("variants");
		assertEquals(1, remaining.size());
		JsonObject variant = remaining.get(0).getAsJsonObject();
		assertEquals("creeper_texture_je2_png", variant.get("id").getAsString());
		List<String> targets = new ArrayList<>();
		for (var target : variant.getAsJsonArray("targets")) {
			targets.add(target.getAsString());
		}
		assertEquals(List.of("minecraft:textures/entity/creeper/creeper.png"), targets);
		assertTrue(variant.get("mapped").getAsBoolean());
		assertTrue(written.has("soundVariants"));
	}

	@Test
	void overrideTargetsWinOverVanillaResolution(@TempDir Path tempDir) throws Exception {
		Path cacheDir = Files.createDirectories(tempDir.resolve("loom"));
		Path versionDir = Files.createDirectories(cacheDir.resolve("26.3"));
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(versionDir.resolve("minecraft-client.jar")))) {
			writeEntry(zip, "assets/minecraft/textures/entity/creeper/creeper.png");
		}

		Path outputRoot = Files.createDirectories(tempDir.resolve("catalog"));
		JsonObject existing = new JsonObject();
		existing.addProperty("schemaVersion", 2);
		JsonArray variants = new JsonArray();
		variants.add(variant(
				"zombie_texture_je1_png",
				"Zombie (texture) JE1.png",
				"Java_Edition_history_of_textures/Entities",
				"minecraft:textures/block/dirt.png"
		));
		existing.add("variants", variants);
		existing.add("soundVariants", new JsonArray());
		Files.writeString(outputRoot.resolve("catalog.json"), existing.toString());

		new WikiIndexer(outputRoot, false, false, true, false, cacheDir, "26.3").run();

		JsonObject written = JsonParser
				.parseString(Files.readString(outputRoot.resolve("catalog.json")))
				.getAsJsonObject();
		JsonArray remaining = written.getAsJsonArray("variants");
		assertEquals(1, remaining.size());
		List<String> targets = new ArrayList<>();
		for (var target : remaining.get(0).getAsJsonObject().getAsJsonArray("targets")) {
			targets.add(target.getAsString());
		}
		assertEquals(List.of("minecraft:textures/entity/zombie/zombie.png"), targets);
	}

	private static JsonObject variant(String id, String wikiFile, String introducedIn, String... targets) {
		JsonObject object = new JsonObject();
		object.addProperty("id", id);
		object.addProperty("wikiFile", wikiFile);
		JsonArray editions = new JsonArray();
		editions.add("JE");
		object.add("editions", editions);
		JsonArray tags = new JsonArray();
		tags.add("JE2");
		object.add("textureTags", tags);
		object.addProperty("javaVersion", "1.0");
		object.addProperty("bedrockVersion", "");
		object.addProperty("displayVersion", "JE2");
		object.addProperty("introducedIn", introducedIn);
		object.addProperty("assetPath", "assets/" + id + ".png");
		object.addProperty("label", wikiFile);
		object.addProperty("mapped", true);
		JsonArray targetArray = new JsonArray();
		for (String target : targets) {
			targetArray.add(target);
		}
		object.add("targets", targetArray);
		return object;
	}

	private static void writeEntry(ZipOutputStream zip, String name) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(new byte[]{1, 2, 3});
		zip.closeEntry();
	}
}
