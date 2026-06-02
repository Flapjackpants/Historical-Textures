package dev.historicaltextures.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.historicaltextures.HistoricalTextures;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HistoricalCatalog {
	private static final Gson GSON = new GsonBuilder().create();
	private static HistoricalCatalog instance;

	private final int schemaVersion;
	private final List<TextureVariant> textureVariants;
	private final List<SoundVariant> soundVariants;
	private final Map<String, TextureVariant> textureById;
	private final Map<String, List<TextureVariant>> texturesByTarget;
	private final Map<String, SoundVariant> soundById;
	private final Map<String, List<SoundVariant>> soundsByEvent;

	private HistoricalCatalog(
			int schemaVersion,
			List<TextureVariant> textureVariants,
			List<SoundVariant> soundVariants
	) {
		this.schemaVersion = schemaVersion;
		this.textureVariants = List.copyOf(textureVariants);
		this.soundVariants = List.copyOf(soundVariants);
		this.textureById = new HashMap<>();
		this.texturesByTarget = new HashMap<>();
		this.soundById = new HashMap<>();
		this.soundsByEvent = new HashMap<>();

		for (TextureVariant variant : textureVariants) {
			textureById.put(variant.id(), variant);
			for (String target : variant.targets()) {
				texturesByTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(variant);
			}
		}

		for (SoundVariant variant : soundVariants) {
			soundById.put(variant.id(), variant);
			soundsByEvent.computeIfAbsent(variant.soundEvent(), key -> new ArrayList<>()).add(variant);
		}

		for (Map.Entry<String, List<TextureVariant>> entry : texturesByTarget.entrySet()) {
			entry.setValue(List.copyOf(entry.getValue()));
		}
		for (Map.Entry<String, List<SoundVariant>> entry : soundsByEvent.entrySet()) {
			entry.setValue(List.copyOf(entry.getValue()));
		}
	}

	public static HistoricalCatalog get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static HistoricalCatalog load() {
		Identifier catalogId = Identifier.fromNamespaceAndPath(HistoricalTextures.MOD_ID, "catalog/catalog.json");
		try (InputStream stream = HistoricalCatalog.class.getClassLoader().getResourceAsStream(
				"assets/" + catalogId.getNamespace() + "/catalog/catalog.json"
		)) {
			if (stream == null) {
				HistoricalTextures.LOGGER.error("Missing bundled catalog.json");
				return empty();
			}
			JsonObject root = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
			int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
			List<TextureVariant> textures = parseTextures(root.getAsJsonArray("variants"));
			List<SoundVariant> sounds = parseSounds(root.getAsJsonArray("soundVariants"));
			HistoricalTextures.LOGGER.info("Loaded catalog schema {} with {} texture variants and {} sound variants",
					schema, textures.size(), sounds.size());
			return new HistoricalCatalog(schema, textures, sounds);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.error("Failed to load catalog", exception);
			return empty();
		}
	}

	private static HistoricalCatalog empty() {
		return new HistoricalCatalog(1, List.of(), List.of());
	}

	private static List<TextureVariant> parseTextures(JsonArray array) {
		if (array == null) {
			return List.of();
		}
		List<TextureVariant> result = new ArrayList<>();
		for (var element : array) {
			JsonObject object = element.getAsJsonObject();
			List<CatalogEdition> editions = new ArrayList<>();
			if (object.has("editions")) {
				for (var editionElement : object.getAsJsonArray("editions")) {
					editions.add(CatalogEdition.fromTag(editionElement.getAsString()));
				}
			}
			List<String> targets = new ArrayList<>();
			if (object.has("targets")) {
				for (var targetElement : object.getAsJsonArray("targets")) {
					targets.add(targetElement.getAsString());
				}
			}
			boolean mapped = !object.has("mapped") || object.get("mapped").getAsBoolean();
			result.add(new TextureVariant(
					object.get("id").getAsString(),
					object.get("wikiFile").getAsString(),
					List.copyOf(editions),
					object.has("introducedIn") ? object.get("introducedIn").getAsString() : "",
					object.get("assetPath").getAsString(),
					List.copyOf(targets),
					object.has("label") ? object.get("label").getAsString() : object.get("wikiFile").getAsString(),
					mapped
			));
		}
		return result;
	}

	private static List<SoundVariant> parseSounds(JsonArray array) {
		if (array == null) {
			return List.of();
		}
		List<SoundVariant> result = new ArrayList<>();
		for (var element : array) {
			JsonObject object = element.getAsJsonObject();
			result.add(new SoundVariant(
					object.get("id").getAsString(),
					object.get("wikiFile").getAsString(),
					object.get("soundEvent").getAsString(),
					object.get("assetPath").getAsString(),
					object.has("vanillaSoundPath") ? object.get("vanillaSoundPath").getAsString() : "",
					object.has("label") ? object.get("label").getAsString() : object.get("wikiFile").getAsString()
			));
		}
		return result;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public List<TextureVariant> textureVariants() {
		return textureVariants;
	}

	public List<SoundVariant> soundVariants() {
		return soundVariants;
	}

	public Optional<TextureVariant> textureVariant(String id) {
		return Optional.ofNullable(textureById.get(id));
	}

	public List<TextureVariant> textureVariantsForTarget(String target) {
		return texturesByTarget.getOrDefault(target, List.of());
	}

	public Map<String, List<TextureVariant>> texturesByTarget() {
		return Collections.unmodifiableMap(texturesByTarget);
	}

	public Optional<SoundVariant> soundVariant(String id) {
		return Optional.ofNullable(soundById.get(id));
	}

	public List<SoundVariant> soundVariantsForEvent(String event) {
		return soundsByEvent.getOrDefault(event, List.of());
	}

	public Map<String, List<SoundVariant>> soundsByEvent() {
		return Collections.unmodifiableMap(soundsByEvent);
	}

	public InputStream openAsset(String assetPath) {
		String resourcePath = "assets/historical_textures/catalog/" + assetPath;
		return HistoricalCatalog.class.getClassLoader().getResourceAsStream(resourcePath);
	}
}
