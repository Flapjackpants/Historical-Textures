package dev.historicaltextures.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.historicaltextures.HistoricalTextures;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bundled legacy model files, keyed by texture target and variant id.
 */
public final class LegacyModels {
	private static final String INDEX_RESOURCE = "index.json";
	private static volatile Map<String, Map<String, Map<String, String>>> index;

	private LegacyModels() {
	}

	public static Map<String, String> filesFor(String target, String variantId) {
		if (target == null || variantId == null) {
			return Map.of();
		}
		Map<String, Map<String, String>> variants = index().get(target);
		if (variants == null) {
			return Map.of();
		}
		Map<String, String> files = variants.get(variantId);
		if (files == null) {
			return Map.of();
		}
		return files;
	}

	public static InputStream open(String source) {
		return CatalogResources.openLegacyModel(source);
	}

	public static boolean isSafeDest(String dest) {
		if (dest == null || dest.isBlank()) {
			return false;
		}
		return !dest.contains("..") && !dest.contains("\\") && !dest.startsWith("/");
	}

	private static Map<String, Map<String, Map<String, String>>> index() {
		Map<String, Map<String, Map<String, String>>> loaded = index;
		if (loaded == null) {
			synchronized (LegacyModels.class) {
				loaded = index;
				if (loaded == null) {
					loaded = load();
					index = loaded;
				}
			}
		}
		return loaded;
	}

	private static Map<String, Map<String, Map<String, String>>> load() {
		Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<>();
		try (InputStream stream = CatalogResources.openLegacyModel(INDEX_RESOURCE)) {
			if (stream == null) {
				HistoricalTextures.LOGGER.warn("Missing legacy model index at {}", INDEX_RESOURCE);
				return Map.of();
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
			for (Map.Entry<String, JsonElement> targetEntry : root.entrySet()) {
				Map<String, Map<String, String>> variants = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> variantEntry : targetEntry.getValue().getAsJsonObject().entrySet()) {
					Map<String, String> files = new LinkedHashMap<>();
					for (Map.Entry<String, JsonElement> fileEntry : variantEntry.getValue().getAsJsonObject().entrySet()) {
						files.put(fileEntry.getKey(), fileEntry.getValue().getAsString());
					}
					variants.put(variantEntry.getKey(), Collections.unmodifiableMap(files));
				}
				result.put(targetEntry.getKey(), Collections.unmodifiableMap(variants));
			}
			HistoricalTextures.LOGGER.debug("Loaded legacy model index for {} target(s)", result.size());
			return Collections.unmodifiableMap(result);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to load legacy model index", exception);
			return Map.of();
		}
	}
}
