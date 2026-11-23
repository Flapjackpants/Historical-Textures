package dev.historicaltextures.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyBabyTransformTest {
	private static final String JEM_DIR = "assets/historical_textures/legacy_models/";

	private static JsonObject loadJem(String name) throws Exception {
		try (InputStream stream = LegacyBabyTransformTest.class.getClassLoader()
				.getResourceAsStream(JEM_DIR + name)) {
			assertNotNull(stream, "missing classpath resource " + name);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				.getAsJsonObject();
		}
	}

	private static JsonObject modelByPart(JsonObject doc, String part) {
		for (JsonElement element : doc.getAsJsonArray("models")) {
			JsonObject model = element.getAsJsonObject();
			if (model.has("part") && part.equals(model.get("part").getAsString())) {
				return model;
			}
		}
		throw new AssertionError("no top-level model with part " + part);
	}

	private static JsonObject freshCow() throws Exception {
		try (InputStream stream = LegacyBabyTransformTest.class.getClassLoader()
				.getResourceAsStream(JEM_DIR + "cow_legacy.jem")) {
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private static LegacyBabies.BabyTransform cowTransform() {
		return new LegacyBabies.BabyTransform("baby", false, 8.0f, 6.0f, 2.0f, 2.0f, 24.0f,
				List.of("head"), 0.0f);
	}

	@Test
	void cowHeadKeepsFullSizeAndMovesUp() throws Exception {
		JsonObject result = LegacyBabyModels.fromAdultJem(freshCow(), cowTransform());
		JsonObject head = modelByPart(result, "head");
		JsonArray translate = head.getAsJsonArray("translate");
		assertEquals(0.0, translate.get(0).getAsDouble(), 1e-4);
		assertEquals(-12.0, translate.get(1).getAsDouble(), 1e-4);
		assertEquals(2.0, translate.get(2).getAsDouble(), 1e-4);
		assertEquals(1.0f, head.get("scale").getAsFloat(), 1e-6f);

		JsonArray coords = head.getAsJsonArray("boxes").get(0).getAsJsonObject()
				.getAsJsonArray("coordinates");
		assertEquals(-4.0, coords.get(0).getAsDouble(), 1e-4);
		assertEquals(8.0, coords.get(1).getAsDouble(), 1e-4);
		assertEquals(-8.0, coords.get(2).getAsDouble(), 1e-4);
	}

	@Test
	void cowBodyAndLegsScaleByHalf() throws Exception {
		JsonObject result = LegacyBabyModels.fromAdultJem(freshCow(), cowTransform());
		JsonObject body = modelByPart(result, "body");
		JsonArray translate = body.getAsJsonArray("translate");
		assertEquals(0.0, translate.get(0).getAsDouble(), 1e-4);
		assertEquals(-9.5, translate.get(1).getAsDouble(), 1e-4);
		assertEquals(-1.0, translate.get(2).getAsDouble(), 1e-4);
		assertEquals(0.5f, body.get("scale").getAsFloat(), 1e-6f);

		JsonArray coords = body.getAsJsonArray("boxes").get(0).getAsJsonObject()
				.getAsJsonArray("coordinates");
		assertEquals(-6.0, coords.get(0).getAsDouble(), 1e-4);
		assertEquals(1.5, coords.get(1).getAsDouble(), 1e-4);
		assertEquals(-6.0, coords.get(2).getAsDouble(), 1e-4);

		JsonObject leg = modelByPart(result, "leg1");
		assertEquals(0.5f, leg.get("scale").getAsFloat(), 1e-6f);
		JsonArray legTranslate = leg.getAsJsonArray("translate");
		assertEquals(-2.0, legTranslate.get(0).getAsDouble(), 1e-4);
		assertEquals(-6.0, legTranslate.get(1).getAsDouble(), 1e-4);
		assertEquals(-3.5, legTranslate.get(2).getAsDouble(), 1e-4);
	}

	@Test
	void inputJemIsNotMutated() throws Exception {
		JsonObject input = freshCow();
		JsonObject snapshot = freshCow();
		LegacyBabyModels.fromAdultJem(input, cowTransform());
		assertEquals(snapshot, input);
	}

	@Test
	void rabbitUniformScalesRootSubmodels() throws Exception {
		JsonObject rabbit = loadJem("rabbit_legacy.jem");
		JsonObject result = LegacyBabyModels.fromAdultJem(rabbit,
				new LegacyBabies.BabyTransform("uniform", false, 0, 0, 0, 0, 0, List.of(), 0.5f));
		JsonObject root = modelByPart(result, "root");
		JsonObject legacy = null;
		for (JsonElement sub : root.getAsJsonArray("submodels")) {
			JsonObject candidate = sub.getAsJsonObject();
			if ("legacy".equals(candidate.get("id").getAsString())) {
				legacy = candidate;
			}
		}
		assertNotNull(legacy, "rabbit_legacy.jem root must contain legacy submodel");
		assertEquals(0.3f, legacy.get("scale").getAsFloat(), 1e-6f);
	}
}
