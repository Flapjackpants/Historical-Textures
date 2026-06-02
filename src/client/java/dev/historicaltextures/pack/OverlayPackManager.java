package dev.historicaltextures.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;

public final class OverlayPackManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PACK_FORMAT = 75;

	private OverlayPackManager() {
	}

	public static Path overlayDirectory() {
		return Path.of("config", "historical_textures", "overlay");
	}

	public static boolean packExists() {
		return Files.isRegularFile(overlayDirectory().resolve("pack.mcmeta"));
	}

	public static ApplyResult applyChoices(boolean reloadResources) {
		try {
			ApplyResult result = generateOverlayPack();
			enableOverlayPack();
			if (reloadResources && result.hadChoices()) {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft != null) {
					minecraft.reloadResourcePacks();
				}
			}
			if (result.hadChoices()) {
				if (result.texturesWritten() == 0 && result.soundsWritten() == 0) {
					HistoricalTextures.LOGGER.error(
							"Overlay pack generated but no assets were written. Rebuild the mod with './gradlew :wiki-indexer:run build'."
					);
				} else {
					HistoricalTextures.LOGGER.info(
							"Applied {} texture(s) and {} sound(s); skipped {} (missing variant or asset)",
							result.texturesWritten(),
							result.soundsWritten(),
							result.skipped()
					);
				}
			}
			return result;
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.error("Failed to apply overlay pack", exception);
			return ApplyResult.failed();
		}
	}

	private static void enableOverlayPack() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		var repository = minecraft.getResourcePackRepository();
		if (packExists()) {
			repository.reload();
			var selected = new ArrayList<>(repository.getSelectedIds());
			if (!selected.contains(OverlayResourcePackProvider.PACK_ID)) {
				repository.addPack(OverlayResourcePackProvider.PACK_ID);
				selected = new ArrayList<>(repository.getSelectedIds());
			}
			if (!selected.contains(OverlayResourcePackProvider.PACK_ID)) {
				selected.add(OverlayResourcePackProvider.PACK_ID);
				repository.setSelected(selected);
			}
		} else {
			var selected = new ArrayList<>(repository.getSelectedIds());
			if (selected.remove(OverlayResourcePackProvider.PACK_ID)) {
				repository.setSelected(selected);
			}
			repository.reload();
		}
	}

	public static ApplyResult generateOverlayPack() throws IOException {
		Path root = overlayDirectory();
		ModConfig config = ModConfig.get();
		HistoricalCatalog catalog = HistoricalCatalog.get();

		if (config.textureChoices().isEmpty() && config.soundChoices().isEmpty()) {
			if (Files.exists(root)) {
				deleteRecursive(root);
			}
			return ApplyResult.empty();
		}

		if (Files.exists(root)) {
			deleteRecursive(root);
		}
		Files.createDirectories(root.resolve("assets/minecraft/textures"));
		Files.createDirectories(root.resolve("assets/minecraft/sounds"));

		int texturesWritten = 0;
		int skipped = 0;
		for (Map.Entry<String, String> entry : config.textureChoices().entrySet()) {
			String target = entry.getKey();
			String variantId = entry.getValue();
			var optionalVariant = catalog.textureVariant(variantId);
			if (optionalVariant.isEmpty()) {
				HistoricalTextures.LOGGER.warn("Unknown texture variant id {} for target {}", variantId, target);
				skipped++;
				continue;
			}
			if (copyTextureVariant(root, optionalVariant.get(), target)) {
				texturesWritten++;
			} else {
				skipped++;
			}
		}

		int soundsWritten = 0;
		JsonObject soundsJson = buildSoundsJson(root, catalog, config);
		soundsWritten = soundsJson.size();
		if (soundsJson.size() > 0) {
			Path soundsPath = root.resolve("assets/minecraft/sounds.json");
			Files.createDirectories(soundsPath.getParent());
			Files.writeString(soundsPath, GSON.toJson(soundsJson));
		}

		JsonObject packMeta = new JsonObject();
		JsonObject pack = new JsonObject();
		pack.addProperty("description", "Historical Textures overlay");
		pack.addProperty("pack_format", PACK_FORMAT);
		packMeta.add("pack", pack);
		Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(packMeta));

		return new ApplyResult(true, texturesWritten, soundsWritten, skipped);
	}

	private static boolean copyTextureVariant(Path root, TextureVariant variant, String targetPath) throws IOException {
		String relative = targetPath;
		if (relative.startsWith("minecraft:")) {
			relative = relative.substring("minecraft:".length());
		}
		Path destination = root.resolve("assets/minecraft").resolve(relative);
		Files.createDirectories(destination.getParent());

		try (InputStream input = HistoricalCatalog.get().openAsset(variant.assetPath())) {
			if (input == null) {
				HistoricalTextures.LOGGER.warn("Missing catalog asset {} for variant {}", variant.assetPath(), variant.id());
				return false;
			}
			Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
	}

	private static JsonObject buildSoundsJson(Path root, HistoricalCatalog catalog, ModConfig config) throws IOException {
		JsonObject soundRoot = new JsonObject();
		for (Map.Entry<String, String> entry : config.soundChoices().entrySet()) {
			String event = entry.getKey();
			String variantId = entry.getValue();
			SoundVariant variant = catalog.soundVariant(variantId).orElse(null);
			if (variant == null) {
				continue;
			}
			Path soundFile = copySoundVariant(root, variant);
			if (soundFile == null) {
				continue;
			}
			String soundName = stripExtension(soundFile.getFileName().toString());
			String relativePath = "historical_textures/" + soundName;
			if (!variant.vanillaSoundPath().isEmpty()) {
				String vanilla = variant.vanillaSoundPath();
				if (vanilla.startsWith("sounds/")) {
					relativePath = vanilla.substring("sounds/".length());
				}
				relativePath = relativePath.replace(".ogg", "");
			}

			JsonObject eventObject = new JsonObject();
			com.google.gson.JsonArray sounds = new com.google.gson.JsonArray();
			sounds.add(relativePath);
			eventObject.add("sounds", sounds);
			soundRoot.add(event, eventObject);
		}
		return soundRoot;
	}

	private static Path copySoundVariant(Path root, SoundVariant variant) throws IOException {
		String fileName = variant.wikiFile();
		Path destination;
		if (!variant.vanillaSoundPath().isEmpty()) {
			String relative = variant.vanillaSoundPath();
			if (relative.startsWith("sounds/")) {
				relative = relative.substring("sounds/".length());
			}
			destination = root.resolve("assets/minecraft/sounds").resolve(relative);
		} else {
			destination = root.resolve("assets/minecraft/sounds/historical_textures").resolve(fileName);
		}
		Files.createDirectories(destination.getParent());
		try (InputStream input = HistoricalCatalog.get().openAsset(variant.assetPath())) {
			if (input == null) {
				HistoricalTextures.LOGGER.warn("Missing catalog sound asset {} for variant {}", variant.assetPath(), variant.id());
				return null;
			}
			Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		return destination;
	}

	private static String stripExtension(String name) {
		int index = name.lastIndexOf('.');
		return index >= 0 ? name.substring(0, index) : name;
	}

	private static void deleteRecursive(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (var walk = Files.walk(path)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException exception) {
					HistoricalTextures.LOGGER.warn("Could not delete {}", p, exception);
				}
			});
		}
	}

	public record ApplyResult(boolean hadChoices, int texturesWritten, int soundsWritten, int skipped) {
		public static ApplyResult empty() {
			return new ApplyResult(false, 0, 0, 0);
		}

		public static ApplyResult failed() {
			return new ApplyResult(false, 0, 0, 0);
		}
	}
}
