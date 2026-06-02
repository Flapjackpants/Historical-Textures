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
		boolean mapped
) {
}
