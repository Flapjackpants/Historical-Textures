package dev.historicaltextures.catalog;

import java.util.List;

public record SoundVariant(
		String id,
		String wikiFile,
		String soundEvent,
		String assetPath,
		String vanillaSoundPath,
		String label,
		List<String> textureTags,
		String javaVersion,
		String bedrockVersion,
		String displayVersion
) {
	public String versionLabel() {
		if (displayVersion != null && !displayVersion.isBlank()) {
			return displayVersion;
		}
		return label;
	}
}
