package dev.historicaltextures.catalog;

import dev.historicaltextures.HistoricalTextures;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CatalogResources {
	private static final String CATALOG_JSON = "assets/historical_textures/catalog/catalog.json";
	private static final String CATALOG_ROOT = "assets/historical_textures/catalog/";

	private CatalogResources() {
	}

	public static InputStream openCatalogJson() {
		InputStream stream = openAtPath(CATALOG_JSON);
		if (stream != null) {
			return stream;
		}
		return HistoricalCatalog.class.getClassLoader().getResourceAsStream(CATALOG_JSON);
	}

	public static InputStream openAsset(String assetPath) {
		if (assetPath == null || assetPath.isBlank()) {
			return null;
		}
		String normalized = assetPath.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		InputStream stream = openAtPath(CATALOG_ROOT + normalized);
		if (stream != null) {
			return stream;
		}
		return HistoricalCatalog.class.getClassLoader().getResourceAsStream(CATALOG_ROOT + normalized);
	}

	private static InputStream openAtPath(String resourcePath) {
		try {
			var container = FabricLoader.getInstance().getModContainer(HistoricalTextures.MOD_ID);
			if (container.isEmpty()) {
				return null;
			}
			Path path = container.get().findPath(resourcePath).orElse(null);
			if (path != null && Files.isRegularFile(path)) {
				return Files.newInputStream(path);
			}
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.debug("Failed to open catalog resource {}", resourcePath, exception);
		}
		return null;
	}
}
