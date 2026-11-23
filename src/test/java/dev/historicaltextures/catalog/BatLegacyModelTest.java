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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatLegacyModelTest {
	private static final String RESOURCE = "assets/historical_textures/legacy_models/bat_legacy.jem";
	private static final String INDEX_RESOURCE = "assets/historical_textures/legacy_models/index.json";
	private static final List<String> VANILLA_PARTS = List.of(
			"body", "head", "left_ear", "right_ear",
			"left_wing", "left_wing_tip", "right_wing", "right_wing_tip", "feet"
	);

	private static JsonObject load() throws Exception {
		try (InputStream stream = BatLegacyModelTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			assertNotNull(stream, "missing classpath resource " + RESOURCE);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
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
	void declaresSixtyFourPixelTexture() throws Exception {
		JsonArray textureSize = load().getAsJsonArray("textureSize");
		assertNotNull(textureSize);
		assertEquals(2, textureSize.size());
		assertEquals(64, textureSize.get(0).getAsInt());
		assertEquals(64, textureSize.get(1).getAsInt());
	}

	@Test
	void hostsLegacyModelUnderSingleRootPart() throws Exception {
		JsonArray models = load().getAsJsonArray("models");
		int rootCount = 0;
		JsonObject legacy = null;
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			if ("root".equals(model.has("part") ? model.get("part").getAsString() : null)) {
				rootCount++;
				for (JsonElement sub : model.getAsJsonArray("submodels")) {
					JsonObject candidate = sub.getAsJsonObject();
					if ("legacy".equals(candidate.get("id").getAsString())) {
						legacy = candidate;
					}
				}
			}
		}
		assertEquals(1, rootCount, "expected exactly one top-level root entry");
		assertNotNull(legacy, "root entry must contain the legacy submodel");
		assertEquals(0.35f, legacy.get("scale").getAsFloat(), 1e-6f);
	}

	@Test
	void legacyPartIdsCoverBatGeometry() throws Exception {
		JsonObject doc = load();
		Set<String> ids = new HashSet<>();
		for (JsonElement element : doc.getAsJsonArray("models")) {
			collectIds(element, ids);
		}
		for (String id : List.of(
				"legacy_head", "legacy_body",
				"legacy_right_wing", "legacy_left_wing",
				"legacy_right_wing_tip", "legacy_left_wing_tip",
				"legacy_right_ear", "legacy_left_ear")) {
			assertTrue(ids.contains(id), "missing legacy part id " + id);
		}
	}

	@Test
	void hidesAllVanillaParts() throws Exception {
		JsonArray models = load().getAsJsonArray("models");
		Set<String> seen = new HashSet<>();
		for (String part : VANILLA_PARTS) {
			JsonObject entry = null;
			for (JsonElement element : models) {
				JsonObject model = element.getAsJsonObject();
				if (part.equals(model.has("part") ? model.get("part").getAsString() : null)) {
					entry = model;
				}
			}
			assertNotNull(entry, "missing blank vanilla part " + part);
			JsonArray boxes = entry.getAsJsonArray("boxes");
			assertTrue(boxes == null || boxes.size() == 0,
					"vanilla part " + part + " must not draw boxes");
			JsonArray submodels = entry.getAsJsonArray("submodels");
			assertTrue(submodels == null || submodels.size() == 0,
					"vanilla part " + part + " must not have submodels");
			seen.add(part);
		}
		assertEquals(VANILLA_PARTS.size(), seen.size());
	}

	@Test
	void indexMapsBothBatVariantsToLegacyJem() throws Exception {
		try (InputStream stream = BatLegacyModelTest.class.getClassLoader().getResourceAsStream(INDEX_RESOURCE)) {
			assertNotNull(stream, "missing classpath resource " + INDEX_RESOURCE);
			JsonObject index = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
			JsonObject target = index.getAsJsonObject("minecraft:textures/entity/bat/bat.png");
			assertNotNull(target, "index.json must key the bat texture target");
			for (String variantId : List.of("bat_texture_je1_png", "bat_texture_je2_be2_png")) {
				JsonObject files = target.getAsJsonObject(variantId);
				assertNotNull(files, "index.json missing variant " + variantId);
				assertEquals("bat_legacy.jem", files.get("optifine/cem/bat.jem").getAsString());
			}
		}
	}

	@Test
	void legacyModelsIndexResolvesBatFiles() {
		for (String variantId : List.of("bat_texture_je1_png", "bat_texture_je2_be2_png")) {
			Map<String, String> files = LegacyModels.filesFor(
					"minecraft:textures/entity/bat/bat.png", variantId);
			assertEquals("bat_legacy.jem", files.get("optifine/cem/bat.jem"));
			try (InputStream stream = LegacyModels.open("bat_legacy.jem")) {
				assertNotNull(stream, "bat_legacy.jem must be openable for " + variantId);
			} catch (Exception exception) {
				throw new AssertionError(exception);
			}
		}
	}
}
