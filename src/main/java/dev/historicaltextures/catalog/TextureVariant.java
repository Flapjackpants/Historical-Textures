package dev.historicaltextures.catalog;

import java.util.List;

public record TextureVariant(
		String id,
		String wikiFile,
		List<CatalogEdition> editions,
		String introducedIn,
		String assetPath,
		List<String> targets,
		String label,
		boolean mapped,
		List<String> textureTags,
		String javaVersion,
		String bedrockVersion,
		String displayVersion
) {
	public String versionLabel() {
		if (displayVersion != null && !displayVersion.isBlank()) {
			return displayVersion;
		}
		if (textureTags != null && !textureTags.isEmpty()) {
			return String.join(" / ", textureTags);
		}
		return label;
	}
}
