package dev.historicaltextures.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBabiesTest {
	private static final String BABIES = "assets/historical_textures/legacy_models/babies.json";
	private static final String INDEX = "assets/historical_textures/legacy_models/index.json";

	private static JsonObject load(String resource) throws Exception {
		try (InputStream stream = LegacyBabiesTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(stream, "missing classpath resource " + resource);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	private static JsonArray rows() throws Exception {
		try (InputStream stream = LegacyBabiesTest.class.getClassLoader().getResourceAsStream(BABIES)) {
			assertNotNull(stream, "missing classpath resource " + BABIES);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonArray();
		}
	}

	/** adult texture -> true when index.json maps it to a variant writing optifine/cem/*.jem */
	private static Set<String> jemBackedAdults() throws Exception {
		JsonObject index = load(INDEX);
		Set<String> adults = new HashSet<>();
		for (Map.Entry<String, com.google.gson.JsonElement> target : index.entrySet()) {
			for (Map.Entry<String, com.google.gson.JsonElement> variant
					: target.getValue().getAsJsonObject().entrySet()) {
				for (Map.Entry<String, com.google.gson.JsonElement> file
						: variant.getValue().getAsJsonObject().entrySet()) {
					if (file.getKey().startsWith("optifine/cem/") && file.getKey().endsWith(".jem")) {
						adults.add(target.getKey());
					}
				}
			}
		}
		return adults;
	}

	@Test
	void entriesAreLoadedAndNonEmpty() {
		assertFalse(LegacyBabies.entries().isEmpty(), "babies.json must load entries");
		assertSame(LegacyBabies.entries(), LegacyBabies.entries(), "entries must be cached");
	}

	@Test
	void cowTemperateRowHasLegacyTransform() {
		Optional<LegacyBabies.Entry> entry =
				LegacyBabies.forBabyTexture("minecraft:textures/entity/cow/cow_temperate_baby.png");
		assertTrue(entry.isPresent());
		assertEquals("cow_baby", entry.get().emfModel());
		assertEquals("minecraft:textures/entity/cow/cow_temperate.png", entry.get().adultTexture());
		LegacyBabies.BabyTransform transform = entry.get().legacyTransform();
		assertNotNull(transform);
		assertEquals("baby", transform.type());
		assertEquals(8.0f, transform.yHeadOffset(), 1e-6f);
	}

	@Test
	void unknownBabyTextureReturnsEmpty() {
		assertTrue(LegacyBabies.forBabyTexture("minecraft:textures/entity/zzz/none_baby.png").isEmpty());
		assertTrue(LegacyBabies.forBabyTexture(null).isEmpty());
	}

	@Test
	void everyLegacyTransformAdultHasLegacyJem() throws Exception {
		Set<String> jemAdults = jemBackedAdults();
		Map<String, Integer> tagged = new HashMap<>();
		for (JsonElement element : rows()) {
			JsonObject row = element.getAsJsonObject();
			if (!row.has("legacyTransform")) {
				continue;
			}
			String adult = row.get("adultTexture").getAsString();
			assertTrue(jemAdults.contains(adult),
					"legacyTransform row " + row.get("babyTexture").getAsString()
							+ " has no legacy jem for adult " + adult);
			tagged.merge(adult, 1, Integer::sum);
		}
		assertFalse(tagged.isEmpty(), "expected some rows with legacyTransform");
	}

	@Test
	void legacyTransformCoverageMatchesSpec() throws Exception {
		Map<String, JsonObject> byBaby = new HashMap<>();
		for (JsonElement element : rows()) {
			JsonObject row = element.getAsJsonObject();
			byBaby.put(row.get("babyTexture").getAsString(), row);
		}
		assertTrue(byBaby.get("minecraft:textures/entity/cow/cow_temperate_baby.png")
				.getAsJsonObject("legacyTransform").has("headParts"));
		assertEquals("uniform",
				byBaby.get("minecraft:textures/entity/rabbit/rabbit_brown_baby.png")
						.getAsJsonObject("legacyTransform").get("type").getAsString());
		assertEquals(0.5,
				byBaby.get("minecraft:textures/entity/rabbit/rabbit_brown_baby.png")
						.getAsJsonObject("legacyTransform").get("scale").getAsFloat(), 1e-6f);
		assertFalse(byBaby.get("minecraft:textures/entity/zombie_villager/baby/plains.png")
				.has("legacyTransform"), "zombie villager biome rows use type/ adults without jem");
		assertFalse(byBaby.get("minecraft:textures/entity/villager/villager_baby.png")
				.has("legacyTransform"), "villager has no legacy jem");
	}
}
