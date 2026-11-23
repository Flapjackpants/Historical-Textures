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
		return versionLabel(javaVersion, bedrockVersion, displayVersion, label);
	}

	static String versionLabel(String javaVersion, String bedrockVersion, String displayVersion, String fallback) {
		if (javaVersion != null && !javaVersion.isBlank()) {
			if (bedrockVersion != null && !bedrockVersion.isBlank()) {
				return javaVersion + " / " + bedrockVersion;
			}
			return javaVersion;
		}
		if (bedrockVersion != null && !bedrockVersion.isBlank()) {
			return bedrockVersion;
		}
		if (displayVersion != null && !displayVersion.isBlank()) {
			return displayVersion;
		}
		return fallback;
	}
}
