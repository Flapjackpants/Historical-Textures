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

	public static void applyChoices(boolean reloadResources) {
		try {
			boolean generated = generateOverlayPack();
			enableOverlayPack();
			if (reloadResources && generated) {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft != null) {
					minecraft.reloadResourcePacks();
				}
			}
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.error("Failed to apply overlay pack", exception);
		}
	}

	private static void enableOverlayPack() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		var repository = minecraft.getResourcePackRepository();
		var selected = new ArrayList<>(repository.getSelectedIds());
		if (packExists()) {
			if (!repository.isAvailable(OverlayResourcePackProvider.PACK_ID)) {
				repository.reload();
			}
			if (!selected.contains(OverlayResourcePackProvider.PACK_ID)) {
				repository.addPack(OverlayResourcePackProvider.PACK_ID);
			}
		} else {
			selected.remove(OverlayResourcePackProvider.PACK_ID);
			repository.setSelected(selected);
		}
	}

	public static boolean generateOverlayPack() throws IOException {
		Path root = overlayDirectory();
		ModConfig config = ModConfig.get();
		HistoricalCatalog catalog = HistoricalCatalog.get();

		if (config.textureChoices().isEmpty() && config.soundChoices().isEmpty()) {
			if (Files.exists(root)) {
				deleteRecursive(root);
			}
			return false;
		}

		Files.createDirectories(root.resolve("assets/minecraft/textures"));
		Files.createDirectories(root.resolve("assets/minecraft/sounds"));

		boolean wroteTextures = false;
		for (Map.Entry<String, String> entry : config.textureChoices().entrySet()) {
			String target = entry.getKey();
			String variantId = entry.getValue();
			var optionalVariant = catalog.textureVariant(variantId);
			if (optionalVariant.isPresent()) {
				copyTextureVariant(root, optionalVariant.get(), target);
				wroteTextures = true;
			}
		}

		JsonObject soundsJson = buildSoundsJson(catalog, config);
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

		return wroteTextures || !soundsJson.isEmpty();
	}

	private static void copyTextureVariant(Path root, TextureVariant variant, String targetPath) throws IOException {
		String relative = targetPath;
		if (relative.startsWith("minecraft:")) {
			relative = relative.substring("minecraft:".length());
		}
		Path destination = root.resolve("assets/minecraft").resolve(relative);
		Files.createDirectories(destination.getParent());

		try (InputStream input = HistoricalCatalog.get().openAsset(variant.assetPath())) {
			if (input == null) {
				HistoricalTextures.LOGGER.warn("Missing catalog asset {}", variant.assetPath());
				return;
			}
			Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static JsonObject buildSoundsJson(HistoricalCatalog catalog, ModConfig config) throws IOException {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, String> entry : config.soundChoices().entrySet()) {
			String event = entry.getKey();
			String variantId = entry.getValue();
			SoundVariant variant = catalog.soundVariant(variantId).orElse(null);
			if (variant == null) {
				continue;
			}
			Path soundFile = copySoundVariant(overlayDirectory(), variant);
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
			root.add(event, eventObject);
		}
		return root;
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
}
