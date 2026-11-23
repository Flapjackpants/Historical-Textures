package dev.historicaltextures.config;

import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureVariant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInPresetsTest {
	private static TextureVariant texture(String id, String target, String javaVersion, String... jeTags) {
		return new TextureVariant(
				id,
				id + ".png",
				List.of(),
				"",
				"assets/test/" + id + ".png",
				List.of(target),
				id,
				true,
				List.of(jeTags),
				javaVersion,
				"",
				""
		);
	}

	private static SoundVariant sound(String id, String event, String javaVersion) {
		return new SoundVariant(
				id,
				id + ".ogg",
				event,
				"assets/test/" + id + ".ogg",
				"",
				id,
				List.of(),
				javaVersion,
				"",
				javaVersion
		);
	}

	private static Preset presetNamed(List<Preset> presets, String name) {
		return presets.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void createsOnePresetPerCutoffInOrder() {
		List<Preset> presets = BuiltInPresets.create(List.of(), List.of());
		assertEquals(BuiltInPresets.CUTOFFS.size(), presets.size());
		List<String> expectedNames = List.of(
				"Alpha 1.2.6", "Beta 1.7.3", "1.2.5", "1.4.7", "1.6.4", "1.8.9", "1.12.2", "1.13.2"
		);
		for (int i = 0; i < presets.size(); i++) {
			assertEquals(expectedNames.get(i), presets.get(i).name());
			assertTrue(presets.get(i).builtIn());
			assertTrue(presets.get(i).hideVillagerHood());
		}
		assertEquals(List.of(
				"Alpha v1.2.6", "Beta 1.7.3", "1.2.5", "1.4.7", "1.6.4", "1.8.9", "1.12.2", "1.13.2"
		), BuiltInPresets.CUTOFFS);
	}

	@Test
	void picksNewestDatableVariantPerTarget() {
		TextureVariant alpha = texture("t_alpha", "T", "v1.0.8", "JE1");
		TextureVariant snapshot = texture("t_snap", "T", "12w05a", "JE2");
		TextureVariant late = texture("t_late", "T", "18w43a", "JE3");
		TextureVariant undated = texture("t_undated", "T", "snap3", "JE9");
		List<Preset> presets = BuiltInPresets.create(List.of(alpha, snapshot, late, undated), List.of());

		assertEquals("t_alpha", presetNamed(presets, "Alpha 1.2.6").textureChoices().get("T"));
		assertEquals("t_alpha", presetNamed(presets, "Beta 1.7.3").textureChoices().get("T"));
		assertEquals("t_snap", presetNamed(presets, "1.2.5").textureChoices().get("T"));
		assertEquals("t_snap", presetNamed(presets, "1.13.2").textureChoices().get("T"));
		for (Preset preset : presets) {
			assertFalse(preset.textureChoices().containsValue("t_undated"), preset.name());
			assertFalse(preset.textureChoices().containsValue("t_late"), preset.name());
		}
	}

	@Test
	void targetsWithoutEligibleVariantsAreAbsent() {
		TextureVariant only = texture("u_late", "U", "18w43a", "JE3");
		List<Preset> presets = BuiltInPresets.create(List.of(only), List.of());
		assertFalse(presetNamed(presets, "Beta 1.7.3").textureChoices().containsKey("U"));
		assertFalse(presetNamed(presets, "1.12.2").textureChoices().containsKey("U"));
		assertFalse(presetNamed(presets, "1.13.2").textureChoices().containsKey("U"));
	}

	@Test
	void tiesPreferHigherJeTagThenId() {
		TextureVariant low = texture("t_je1", "T", "1.2.5", "JE1");
		TextureVariant high = texture("t_je3", "T", "1.2.5", "JE3");
		TextureVariant sameTag = texture("a_same", "T", "1.2.5", "JE3");
		List<Preset> presets = BuiltInPresets.create(List.of(low, high, sameTag), List.of());
		Map<String, String> choices = presetNamed(presets, "1.4.7").textureChoices();
		assertEquals("a_same", choices.get("T"));
	}

	@Test
	void soundsUseHistoricalVariantsBeforeOnePointNine() {
		SoundVariant historical = sound("cow_old", "entity.cow.ambient", "Historical (old)");
		SoundVariant ranged = sound("cow_range", "entity.cow.hurt", "0.0.14a – 1.8.9");
		List<Preset> presets = BuiltInPresets.create(List.of(), List.of(historical, ranged));

		assertEquals("cow_old", presetNamed(presets, "Beta 1.7.3").soundChoices().get("entity.cow.ambient"));
		assertEquals("cow_old", presetNamed(presets, "1.8.9").soundChoices().get("entity.cow.ambient"));
		assertFalse(presetNamed(presets, "1.12.2").soundChoices().containsKey("entity.cow.ambient"));
		assertFalse(presetNamed(presets, "1.13.2").soundChoices().containsKey("entity.cow.ambient"));

		assertEquals("cow_range", presetNamed(presets, "1.6.4").soundChoices().get("entity.cow.hurt"));
		assertEquals("cow_range", presetNamed(presets, "1.8.9").soundChoices().get("entity.cow.hurt"));
		assertEquals("cow_range", presetNamed(presets, "1.2.5").soundChoices().get("entity.cow.hurt"));
	}

	@Test
	void rangeMatchesBeatHistoricalOnTheSameEvent() {
		SoundVariant historical = sound("a_hist", "entity.event", "Historical");
		SoundVariant ranged = sound("b_range", "entity.event", "0.0.14a – 1.8.9");
		List<Preset> presets = BuiltInPresets.create(List.of(), List.of(historical, ranged));
		assertEquals("b_range", presetNamed(presets, "1.6.4").soundChoices().get("entity.event"));
		assertEquals("b_range", presetNamed(presets, "Alpha 1.2.6").soundChoices().get("entity.event"));
	}

	@Test
	void presetRecordCopiesMaps() {
		Map<String, String> mutable = new java.util.HashMap<>();
		mutable.put("T", "v1");
		Preset preset = new Preset("x", mutable, Map.of(), true, true);
		mutable.put("U", "v2");
		assertEquals(Map.of("T", "v1"), preset.textureChoices());
		assertEquals(Map.of(), preset.soundChoices());
	}
}
