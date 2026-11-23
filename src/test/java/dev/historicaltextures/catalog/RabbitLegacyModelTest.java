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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitLegacyModelTest {
	private static final String RESOURCE = "assets/historical_textures/legacy_models/rabbit_legacy.jem";

	private static JsonObject load() throws Exception {
		try (InputStream stream = RabbitLegacyModelTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
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
	void hostsLegacyModelUnderRootPart() throws Exception {
		JsonArray models = load().getAsJsonArray("models");
		JsonObject root = null;
		int rootCount = 0;
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			if ("root".equals(model.has("part") ? model.get("part").getAsString() : null)) {
				root = model;
				rootCount++;
			}
		}
		assertEquals(1, rootCount, "expected exactly one top-level root entry");
		assertNotNull(root);

		JsonObject legacy = null;
		for (JsonElement sub : root.getAsJsonArray("submodels")) {
			JsonObject candidate = sub.getAsJsonObject();
			if ("legacy".equals(candidate.get("id").getAsString())) {
				legacy = candidate;
			}
		}
		assertNotNull(legacy, "root entry must contain the legacy submodel");
		assertEquals(0.6f, legacy.get("scale").getAsFloat(), 1e-6f);
		JsonArray translate = legacy.getAsJsonArray("translate");
		assertEquals(3, translate.size());
		assertEquals(0.0f, translate.get(0).getAsFloat(), 1e-6f);
		assertEquals(-24.0f, translate.get(1).getAsFloat(), 1e-6f);
		assertEquals(0.0f, translate.get(2).getAsFloat(), 1e-6f);
	}

	@Test
	void hidesAllOtherVanillaParts() throws Exception {
		JsonArray models = load().getAsJsonArray("models");
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			if ("root".equals(model.has("part") ? model.get("part").getAsString() : null)) {
				continue;
			}
			JsonArray boxes = model.getAsJsonArray("boxes");
			assertTrue(boxes == null || boxes.size() == 0,
					"non-root entry " + model + " must not draw boxes");
			JsonArray submodels = model.getAsJsonArray("submodels");
			assertTrue(submodels == null || submodels.size() == 0,
					"non-root entry must not have submodels");
		}
	}

	@Test
	void rootAnimationsMatchLegacyRabbit() throws Exception {
		Map<String, String> expected = new java.util.HashMap<>();
		expected.put("var.jc", "if(var.jc >= 1, if(is_on_ground, 0, 1), if(var.jc > 0 || !is_on_ground, min(1, var.jc + frame_time * 2), 0))");
		expected.put("var.jf", "sin(var.jc * pi)");
		expected.put("legacy_head.rx", "torad(head_pitch)");
		expected.put("legacy_head.ry", "torad(head_yaw)");
		expected.put("legacy_left_thigh.rx", "torad(var.jf * 50 - 21)");
		expected.put("legacy_right_thigh.rx", "torad(var.jf * 50 - 21)");
		expected.put("legacy_left_foot.rx", "torad(var.jf * 50)");
		expected.put("legacy_right_foot.rx", "torad(var.jf * 50)");
		expected.put("legacy_left_arm.rx", "torad(var.jf * -40 - 11)");
		expected.put("legacy_right_arm.rx", "torad(var.jf * -40 - 11)");

		JsonArray models = load().getAsJsonArray("models");
		JsonObject root = null;
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			if ("root".equals(model.has("part") ? model.get("part").getAsString() : null)) {
				root = model;
			}
		}
		assertNotNull(root);
		JsonArray animations = root.getAsJsonArray("animations");
		assertNotNull(animations);
		assertEquals(1, animations.size());
		JsonObject frame = animations.get(0).getAsJsonObject();
		assertEquals(expected.size(), frame.size(), "animation frame must contain exactly the expected keys");
		for (Map.Entry<String, String> entry : expected.entrySet()) {
			assertTrue(frame.has(entry.getKey()), "missing animation key " + entry.getKey());
			assertEquals(entry.getValue(), frame.get(entry.getKey()).getAsString(),
					"wrong expression for " + entry.getKey());
		}
	}

	@Test
	void everyAnimatedPartExistsInTree() throws Exception {
		JsonObject doc = load();
		Set<String> ids = new HashSet<>();
		for (JsonElement element : doc.getAsJsonArray("models")) {
			collectIds(element, ids);
		}
		JsonArray models = doc.getAsJsonArray("models");
		for (JsonElement element : models) {
			JsonObject model = element.getAsJsonObject();
			if (!model.has("animations")) {
				continue;
			}
			for (JsonElement frameElement : model.getAsJsonArray("animations")) {
				for (String key : frameElement.getAsJsonObject().keySet()) {
					String part = key.substring(0, key.indexOf('.'));
					if (part.startsWith("legacy_")) {
						assertTrue(ids.contains(part),
								"animation references missing part " + part);
					}
				}
			}
		}
	}
}
