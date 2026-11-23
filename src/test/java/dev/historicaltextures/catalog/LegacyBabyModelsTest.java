package dev.historicaltextures.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBabyModelsTest {
	private static final String INDEX = "assets/historical_textures/legacy_models/babies.json";
	private static final String JEM_DIR = "assets/historical_textures/legacy_models/";

	private static JsonObject load(String resource) throws Exception {
		try (InputStream stream = LegacyBabyModelsTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(stream, "missing classpath resource " + resource);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private static JsonArray rows() throws Exception {
		return JsonParser.parseReader(new InputStreamReader(
				LegacyBabyModelsTest.class.getClassLoader().getResourceAsStream(INDEX), StandardCharsets.UTF_8))
				.getAsJsonArray();
	}

	private static void collectIds(JsonElement element, Set<String> ids) {
		if (element == null || !element.isJsonObject()) {
			return;
		}
		JsonObject object = element.getAsJsonObject();
		if (object.has("id")) {
			ids.add(object.get("id").getAsString());
		}
		if (object.has("submodels")) {
			for (JsonElement child : object.getAsJsonArray("submodels")) {
				collectIds(child, ids);
			}
		}
	}

	@Test
	void indexParsesWithEnoughRows() throws Exception {
		JsonArray rows = rows();
		assertTrue(rows.size() >= 100, "expected at least 100 baby rows, got " + rows.size());
	}

	@Test
	void babyTexturesAreUniqueAndLookLikeBabies() throws Exception {
		Set<String> seen = new HashSet<>();
		for (JsonElement element : rows()) {
			String texture = element.getAsJsonObject().get("babyTexture").getAsString();
			assertTrue(texture.contains("_baby") || texture.contains("/baby/"),
					"babyTexture must be a baby texture: " + texture);
			assertTrue(seen.add(texture), "duplicate babyTexture " + texture);
		}
	}

	@Test
	void everyReferencedJemExistsAndParses() throws Exception {
		Set<String> jems = new HashSet<>();
		for (JsonElement element : rows()) {
			String jem = element.getAsJsonObject().get("jem").getAsString();
			jems.add(jem);
		}
		for (String jem : jems) {
			JsonObject doc = load(JEM_DIR + jem);
			assertTrue(doc.has("models"), jem + " must have a models array");
		}
	}

	@Test
	void everyJemHasExactlyOneRootAndLegacyIds() throws Exception {
		Set<String> jems = new HashSet<>();
		for (JsonElement element : rows()) {
			jems.add(element.getAsJsonObject().get("jem").getAsString());
		}
		for (String jem : jems) {
			JsonObject doc = load(JEM_DIR + jem);
			JsonArray models = doc.getAsJsonArray("models");
			int rootCount = 0;
			for (JsonElement element : models) {
				JsonObject model = element.getAsJsonObject();
				if ("root".equals(model.has("part") ? model.get("part").getAsString() : null)) {
					rootCount++;
				}
			}
			assertEquals(1, rootCount, jem + " must have exactly one root entry");

			JsonArray size = doc.getAsJsonArray("textureSize");
			assertNotNull(size, jem + " must declare textureSize");
			assertEquals(2, size.size(), jem + " textureSize must have exactly 2 entries");

			Set<String> ids = new HashSet<>();
			for (JsonElement element : models) {
				collectIds(element, ids);
			}
			assertTrue(ids.stream().anyMatch(id -> id.startsWith("legacy_")),
					jem + " must contain legacy_ part ids");
		}
	}

	@Test
	void cowRowMapsTemperateBabyToAdultWithCowModel() throws Exception {
		JsonObject cow = null;
		for (JsonElement element : rows()) {
			JsonObject row = element.getAsJsonObject();
			if ("minecraft:textures/entity/cow/cow_temperate_baby.png"
					.equals(row.get("babyTexture").getAsString())) {
				cow = row;
			}
		}
		assertNotNull(cow, "missing cow_temperate_baby row");
		assertEquals("minecraft:textures/entity/cow/cow_temperate.png",
				cow.get("adultTexture").getAsString());
		assertEquals("cow_baby", cow.get("emfModel").getAsString());
		assertEquals("babies/cow_baby.jem", cow.get("jem").getAsString());
	}

	@Test
	void cowJemUsesPreBabyUpdateBodyScale() throws Exception {
		JsonObject doc = load(JEM_DIR + "babies/cow_baby.jem");
		JsonObject root = null;
		for (JsonElement element : doc.getAsJsonArray("models")) {
			JsonObject model = element.getAsJsonObject();
			if ("root".equals(model.has("part") ? model.get("part").getAsString() : null)) {
				root = model;
			}
		}
		assertNotNull(root);

		JsonObject body = null;
		for (JsonElement sub : root.getAsJsonArray("submodels")) {
			JsonObject candidate = sub.getAsJsonObject();
			if ("legacy_body".equals(candidate.get("id").getAsString())) {
				body = candidate;
			}
		}
		assertNotNull(body, "cow_baby.jem must contain legacy_body");
		assertEquals(0.5f, body.get("scale").getAsFloat(), 1e-6f,
				"legacy_body must carry the 1.21.11 baby scale of 0.5");
	}
}
