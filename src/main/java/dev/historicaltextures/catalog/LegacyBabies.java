package dev.historicaltextures.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.historicaltextures.HistoricalTextures;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Pre-26.1 baby texture table: which adult texture/model each baby texture reverts to.
 */
public final class LegacyBabies {
	public static final String VARIANT_ID = "legacy_baby";
	private static final String INDEX_RESOURCE = "babies.json";
	private static volatile List<Entry> entries;

	private LegacyBabies() {
	}

	/**
	 * Pre-26.1 baby transform in JEM terms. {@code type} is {@code "baby"} for the
	 * per-part BabyModelTransform or {@code "uniform"} for a whole-model scale.
	 */
	public record BabyTransform(
			String type,
			boolean scaleHead,
			float yHeadOffset,
			float zHeadOffset,
			float headScale,
			float bodyScale,
			float bodyYOffset,
			List<String> headParts,
			float scale
	) {
		static BabyTransform fromJson(JsonObject object) {
			JsonArray headParts = object.getAsJsonArray("headParts");
			List<String> parts = new ArrayList<>();
			if (headParts != null) {
				for (JsonElement element : headParts) {
					parts.add(element.getAsString());
				}
			}
			return new BabyTransform(
					string(object, "type", "baby"),
					bool(object, "scaleHead"),
					number(object, "yHeadOffset"),
					number(object, "zHeadOffset"),
					number(object, "headScale"),
					number(object, "bodyScale"),
					number(object, "bodyYOffset"),
					Collections.unmodifiableList(parts),
					number(object, "scale")
			);
		}

		private static String string(JsonObject object, String key, String fallback) {
			return object.has(key) ? object.get(key).getAsString() : fallback;
		}

		private static boolean bool(JsonObject object, String key) {
			return object.has(key) && object.get(key).getAsBoolean();
		}

		private static float number(JsonObject object, String key) {
			return object.has(key) ? object.get(key).getAsFloat() : 0.0f;
		}
	}

	public record Entry(
			String babyTexture,
			String adultTexture,
			String emfModel,
			String jem,
			String fallbackAdultTexture,
			String label,
			BabyTransform legacyTransform
	) {
	}

	public static List<Entry> entries() {
		List<Entry> loaded = entries;
		if (loaded == null) {
			synchronized (LegacyBabies.class) {
				loaded = entries;
				if (loaded == null) {
					loaded = load();
					entries = loaded;
				}
			}
		}
		return loaded;
	}

	public static Optional<Entry> forBabyTexture(String babyTexture) {
		if (babyTexture == null) {
			return Optional.empty();
		}
		for (Entry entry : entries()) {
			if (babyTexture.equals(entry.babyTexture())) {
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}

	private static List<Entry> load() {
		try (InputStream stream = CatalogResources.openLegacyModel(INDEX_RESOURCE)) {
			if (stream == null) {
				HistoricalTextures.LOGGER.warn("Missing legacy babies table at {}", INDEX_RESOURCE);
				return List.of();
			}
			JsonArray rows = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonArray();
			List<Entry> result = new ArrayList<>(rows.size());
			for (JsonElement element : rows) {
				JsonObject row = element.getAsJsonObject();
				BabyTransform transform = null;
				if (row.has("legacyTransform") && row.get("legacyTransform").isJsonObject()) {
					transform = BabyTransform.fromJson(row.getAsJsonObject("legacyTransform"));
				}
				result.add(new Entry(
						row.get("babyTexture").getAsString(),
						row.get("adultTexture").getAsString(),
						row.get("emfModel").getAsString(),
						row.get("jem").getAsString(),
						row.has("fallbackAdultTexture") ? row.get("fallbackAdultTexture").getAsString() : null,
						row.get("label").getAsString(),
						transform
				));
			}
			HistoricalTextures.LOGGER.debug("Loaded {} legacy baby entries", result.size());
			return Collections.unmodifiableList(result);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to load legacy babies table", exception);
			return List.of();
		}
	}
}
