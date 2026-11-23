package dev.historicaltextures.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetStoreTest {
	@TempDir
	Path dir;

	private PresetStore store() {
		return new PresetStore(dir.resolve("presets.json"));
	}

	@Test
	void saveLoadRoundTrip() {
		store().save(new Preset(
				"Mine",
				Map.of("minecraft:textures/block/stone.png", "stone_je1"),
				Map.of("entity.cow.ambient", "cow_old"),
				false,
				false
		));

		List<Preset> presets = store().userPresets();
		assertEquals(1, presets.size());
		Preset preset = presets.get(0);
		assertEquals("Mine", preset.name());
		assertEquals(Map.of("minecraft:textures/block/stone.png", "stone_je1"), preset.textureChoices());
		assertEquals(Map.of("entity.cow.ambient", "cow_old"), preset.soundChoices());
		assertFalse(preset.hideVillagerHood());
		assertFalse(preset.builtIn());
	}

	@Test
	void sameNameSaveReplacesCaseInsensitive() {
		PresetStore store = store();
		store.save(new Preset("My Preset", Map.of("a", "1"), Map.of(), true, false));
		store.save(new Preset("MY PRESET", Map.of("b", "2"), Map.of("e", "s"), false, false));

		List<Preset> presets = store.userPresets();
		assertEquals(1, presets.size());
		assertEquals(Map.of("b", "2"), presets.get(0).textureChoices());
		assertEquals(Map.of("e", "s"), presets.get(0).soundChoices());
		assertFalse(presets.get(0).hideVillagerHood());
	}

	@Test
	void deleteAndExists() {
		PresetStore store = store();
		assertFalse(store.exists("X"));
		store.save(new Preset("X", Map.of(), Map.of(), true, false));
		assertTrue(store.exists("x"));
		assertTrue(store.delete("X"));
		assertFalse(store.exists("X"));
		assertFalse(store.delete("X"));
		assertTrue(store().userPresets().isEmpty());
	}

	@Test
	void userPresetsSortedByNameCaseInsensitive() {
		PresetStore store = store();
		store.save(new Preset("charlie", Map.of(), Map.of(), true, false));
		store.save(new Preset("Alpha", Map.of(), Map.of(), true, false));
		store.save(new Preset("bravo", Map.of(), Map.of(), true, false));

		assertEquals(List.of("Alpha", "bravo", "charlie"),
				store.userPresets().stream().map(Preset::name).toList());
	}

	@Test
	void corruptFileStartsEmptyKeepsFileAndBacksUp() throws Exception {
		Path file = dir.resolve("presets.json");
		Files.writeString(file, "{ this is not json !!!");

		PresetStore store = store();
		assertTrue(store.userPresets().isEmpty());
		assertEquals("{ this is not json !!!", Files.readString(file));

		store.save(new Preset("Ok", Map.of("t", "v"), Map.of(), true, false));
		assertTrue(Files.readString(file).contains("\"Ok\""));
		Path backup = dir.resolve("presets.json.bak");
		assertTrue(Files.isRegularFile(backup));
		assertEquals("{ this is not json !!!", Files.readString(backup));
		assertEquals(1, store().userPresets().size());
	}
}
