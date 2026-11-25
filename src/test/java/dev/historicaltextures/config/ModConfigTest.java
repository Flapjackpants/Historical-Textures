package dev.historicaltextures.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("historical_textures.json");
	}

	@Test
	void legacyStringSoundChoiceLoadsAsSingletonList() throws Exception {
		Files.writeString(file(),
				"{\"textureChoices\":{},\"soundChoices\":{\"entity.cow.ambient\":\"cow_old\"},\"hideVillagerHood\":false}");

		ModConfig config = ModConfig.load(file());

		assertEquals(List.of("cow_old"), config.getSoundChoices("entity.cow.ambient"));
		assertEquals(List.of(), config.getSoundChoices("entity.cow.hurt"));
	}

	@Test
	void soundChoicesRoundTripWritesArrays() throws Exception {
		ModConfig config = ModConfig.load(file());
		config.setSoundChoices("entity.cow.ambient", List.of("a", "b"));
		config.save();

		assertEquals(List.of("a", "b"), ModConfig.load(file()).getSoundChoices("entity.cow.ambient"));

		JsonObject root = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
		assertTrue(root.getAsJsonObject("soundChoices").get("entity.cow.ambient").isJsonArray());
	}

	@Test
	void setSoundChoicesKeepsDistinctIdsInGivenOrder() {
		ModConfig config = ModConfig.load(file());
		config.setSoundChoices("event", List.of("b", "a", "b", " "));

		assertEquals(List.of("b", "a"), config.getSoundChoices("event"));
	}

	@Test
	void emptyOrNullChoicesRemoveTheEvent() {
		ModConfig config = ModConfig.load(file());
		config.setSoundChoices("event", List.of("a"));
		config.setSoundChoices("event", List.of());

		assertEquals(List.of(), config.getSoundChoices("event"));
		assertTrue(config.soundChoices().isEmpty());

		config.setSoundChoices("event", null);
		assertTrue(config.soundChoices().isEmpty());
	}

	@Test
	void missingFileHasNoChoices() {
		ModConfig config = ModConfig.load(file());

		assertEquals(List.of(), config.getSoundChoices("event"));
		assertEquals(Map.of(), config.soundChoices());
	}
}
