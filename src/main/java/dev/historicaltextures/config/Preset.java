package dev.historicaltextures.config;

import java.util.List;
import java.util.Map;

public record Preset(
		String name,
		Map<String, String> textureChoices,
		Map<String, List<String>> soundChoices,
		boolean hideVillagerHood,
		boolean builtIn
) {
	public Preset {
		textureChoices = textureChoices == null ? Map.of() : Map.copyOf(textureChoices);
		soundChoices = SoundChoices.copyOf(soundChoices);
	}
}
