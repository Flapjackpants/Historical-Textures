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
		return SoundVariant.versionLabel(javaVersion, bedrockVersion, displayVersion,
				textureTags != null && !textureTags.isEmpty() ? String.join(" / ", textureTags) : label);
	}
}
