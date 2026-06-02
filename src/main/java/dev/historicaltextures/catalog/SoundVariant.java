package dev.historicaltextures.catalog;

public record SoundVariant(
		String id,
		String wikiFile,
		String soundEvent,
		String assetPath,
		String vanillaSoundPath,
		String label
) {
}
