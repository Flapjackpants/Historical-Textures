package dev.historicaltextures.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.historicaltextures.GamePaths;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.LegacyModels;
import dev.historicaltextures.catalog.LegacyTextureFixes;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OverlayPackManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Resource pack format for Minecraft 26.3 (see minecraft.wiki Pack format). */
	private static final int PACK_FORMAT_MAJOR = 97;
	private static final int PACK_FORMAT_MINOR = 1;
	public static final String VILLAGER_PROFESSION_PREFIX = "textures/entity/villager/profession/";
	static final String VILLAGER_TEXTURE_PREFIX = "textures/entity/villager/";

	private OverlayPackManager() {
	}

	public static Path overlayDirectory() {
		return GamePaths.overlayDirectory();
	}

	public static boolean packExists() {
		return Files.isRegularFile(overlayDirectory().resolve("pack.mcmeta"));
	}

	public static ApplyResult applyChoices(boolean reloadResources) {
		try {
			ApplyResult result = generateOverlayPack();
			enableOverlayPack();
			if (reloadResources) {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft != null) {
					minecraft.reloadResourcePacks()
							.exceptionally(error -> {
								HistoricalTextures.LOGGER.error("Resource reload failed after applying overlay", error);
								return null;
							});
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
		var options = minecraft.options;
		repository.reload();

		List<String> enabledPacks = new ArrayList<>(options.resourcePacks);
		enabledPacks.remove(OverlayResourcePackProvider.PACK_ID);

		if (packExists()) {
			if (!repository.isAvailable(OverlayResourcePackProvider.PACK_ID)) {
				HistoricalTextures.LOGGER.error(
						"Overlay pack exists on disk at {} but is not registered. Check mixin / pack.mcmeta.",
						overlayDirectory().toAbsolutePath()
				);
				return;
			}
			var pack = repository.getPack(OverlayResourcePackProvider.PACK_ID);
			if (pack != null && !pack.getCompatibility().isCompatible()) {
				HistoricalTextures.LOGGER.error(
						"Overlay pack is incompatible ({}). Update pack.mcmeta format.",
						pack.getCompatibility()
				);
				return;
			}
			// Last in the list wins over earlier packs when Minecraft merges resources.
			enabledPacks.add(OverlayResourcePackProvider.PACK_ID);
			HistoricalTextures.LOGGER.info(
					"Overlay pack enabled at {} (compatibility={})",
					overlayDirectory().toAbsolutePath(),
					pack != null ? pack.getCompatibility() : "unknown"
			);
		}

		options.resourcePacks.clear();
		options.resourcePacks.addAll(enabledPacks);
		options.loadSelectedResourcePacks(repository);
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
			if (HistoricalCatalog.CURRENT_VARIANT_ID.equals(variantId)) {
				if (copyBuiltInTexture(root, target)) {
					texturesWritten++;
				} else {
					skipped++;
				}
				continue;
			}
			var optionalVariant = catalog.textureVariant(variantId);
			if (optionalVariant.isEmpty()) {
				HistoricalTextures.LOGGER.warn("Unknown texture variant id {} for target {}", variantId, target);
				skipped++;
				continue;
			}
			if (!optionalVariant.get().targets().contains(target)) {
				HistoricalTextures.LOGGER.warn(
						"Variant {} no longer targets {}; skipping stale choice",
						variantId,
						target
				);
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
		pack.addProperty("pack_format", PACK_FORMAT_MAJOR);
		JsonArray formatComponent = new JsonArray();
		formatComponent.add(PACK_FORMAT_MAJOR);
		formatComponent.add(PACK_FORMAT_MINOR);
		JsonArray maxFormat = new JsonArray();
		maxFormat.add(PACK_FORMAT_MAJOR);
		maxFormat.add(PACK_FORMAT_MINOR);
		pack.add("min_format", formatComponent);
		pack.add("max_format", maxFormat);
		packMeta.add("pack", pack);
		Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(packMeta));

		return new ApplyResult(true, texturesWritten, soundsWritten, skipped);
	}

	private static boolean copyTextureVariant(Path root, TextureVariant variant, String targetPath) throws IOException {
		String relative = normalizeTargetPath(targetPath);
		if (relative.isEmpty()) {
			HistoricalTextures.LOGGER.warn("Invalid texture target path: {}", targetPath);
			return false;
		}
		Path destination = root.resolve("assets/minecraft").resolve(relative);
		Files.createDirectories(destination.getParent());

		try (InputStream input = HistoricalCatalog.get().openAsset(variant.assetPath())) {
			if (input == null) {
				HistoricalTextures.LOGGER.warn("Missing catalog asset {} for variant {}", variant.assetPath(), variant.id());
				return false;
			}
			Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
			HistoricalTextures.LOGGER.debug("Wrote overlay texture {}", destination.toAbsolutePath());
			writeLegacyFiles(root, variant, targetPath);
			writeVillagerHoodMcmeta(destination, relative);
			if (ModConfig.get().hideVillagerHood() && isVillagerHoodTarget(relative)) {
				clearVillagerHoodPixels(destination);
			}
			if (LegacyTextureFixes.isCakeSideTarget(relative)) {
				shiftLegacyCakeArt(destination);
			}
			return true;
		}
	}

	private static boolean copyBuiltInTexture(Path root, String targetPath) throws IOException {
		String relative = normalizeTargetPath(targetPath);
		if (relative.isEmpty()) {
			HistoricalTextures.LOGGER.warn("Invalid texture target path: {}", targetPath);
			return false;
		}
		Identifier resourceId;
		try {
			resourceId = Identifier.parse(targetPath);
		} catch (RuntimeException exception) {
			HistoricalTextures.LOGGER.warn("Invalid built-in texture id {}", targetPath);
			return false;
		}
		var resources = Minecraft.getInstance().getVanillaPackResources().fullResources();
		var supplier = resources.getResource(PackType.CLIENT_RESOURCES, resourceId);
		if (supplier == null) {
			HistoricalTextures.LOGGER.warn("Missing built-in texture {} for target {}", resourceId, targetPath);
			return false;
		}
		Path destination = root.resolve("assets/minecraft").resolve(relative);
		Files.createDirectories(destination.getParent());
		try (InputStream input = supplier.get()) {
			if (input == null) {
				HistoricalTextures.LOGGER.warn("Missing built-in texture stream {} for target {}", resourceId, targetPath);
				return false;
			}
			Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		HistoricalTextures.LOGGER.debug("Wrote built-in overlay texture {}", destination.toAbsolutePath());
		Identifier mcmetaId = Identifier.fromNamespaceAndPath(
				resourceId.getNamespace(),
				resourceId.getPath() + ".mcmeta"
		);
		var mcmetaSupplier = resources.getResource(PackType.CLIENT_RESOURCES, mcmetaId);
		if (mcmetaSupplier != null) {
			try (InputStream input = mcmetaSupplier.get()) {
				if (input != null) {
					Path mcmeta = destination.resolveSibling(destination.getFileName() + ".mcmeta");
					Files.copy(input, mcmeta, StandardCopyOption.REPLACE_EXISTING);
					HistoricalTextures.LOGGER.debug("Wrote built-in overlay mcmeta {}", mcmeta.toAbsolutePath());
				}
			}
		}
		return true;
	}

	public static boolean isVillagerHoodTarget(String relative) {
		return relative != null
				&& (relative.startsWith(VILLAGER_PROFESSION_PREFIX)
						|| relative.equals("textures/entity/villager/villager.png"));
	}

	public static boolean clearVillagerHood(NativeImage image) {
		if (image.getWidth() != 64 || image.getHeight() != 64) {
			return false;
		}
		image.fillRect(32, 0, 32, 18, 0);
		image.fillRect(30, 47, 34, 17, 0);
		return true;
	}

	private static void clearVillagerHoodPixels(Path png) throws IOException {
		try (InputStream input = Files.newInputStream(png);
				NativeImage image = NativeImage.read(input)) {
			if (!clearVillagerHood(image)) {
				HistoricalTextures.LOGGER.debug(
						"Skipping villager hood pixel cleanup for non-64x64 texture {}",
						png.toAbsolutePath()
				);
				return;
			}
			image.writeToFile(png);
			HistoricalTextures.LOGGER.debug("Cleared villager hood pixels {}", png.toAbsolutePath());
		}
	}

	private static void shiftLegacyCakeArt(Path png) throws IOException {
		try (InputStream input = Files.newInputStream(png);
				NativeImage image = NativeImage.read(input)) {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					pixels[y * width + x] = image.getPixel(x, y);
				}
			}
			if (!LegacyTextureFixes.shiftTopHalfDown(pixels, width, height)) {
				return;
			}
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					image.setPixel(x, y, pixels[y * width + x]);
				}
			}
			image.writeToFile(png);
			HistoricalTextures.LOGGER.debug("Shifted legacy cake art {}", png.toAbsolutePath());
		}
	}

	private static void writeVillagerHoodMcmeta(Path destination, String relative) throws IOException {
		if (!ModConfig.get().hideVillagerHood() || !relative.startsWith(VILLAGER_PROFESSION_PREFIX)) {
			return;
		}
		JsonObject villager = new JsonObject();
		JsonObject hat = new JsonObject();
		hat.addProperty("hat", "full");
		villager.add("villager", hat);
		Path mcmeta = destination.resolveSibling(destination.getFileName() + ".mcmeta");
		Files.writeString(mcmeta, GSON.toJson(villager));
		HistoricalTextures.LOGGER.debug("Wrote villager hood mcmeta {}", mcmeta.toAbsolutePath());
	}

	private static void writeLegacyFiles(Path root, TextureVariant variant, String targetPath) throws IOException {
		for (Map.Entry<String, String> entry : LegacyModels.filesFor(targetPath, variant.id()).entrySet()) {
			String dest = entry.getKey();
			if (!LegacyModels.isSafeDest(dest)) {
				HistoricalTextures.LOGGER.warn("Unsafe legacy model path {} for variant {}", dest, variant.id());
				continue;
			}
			Path destination = root.resolve("assets/minecraft").resolve(dest);
			Files.createDirectories(destination.getParent());
			try (InputStream input = LegacyModels.open(entry.getValue())) {
				if (input == null) {
					HistoricalTextures.LOGGER.warn(
							"Missing legacy model {} for variant {}",
							entry.getValue(),
							variant.id()
					);
					continue;
				}
				Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
				HistoricalTextures.LOGGER.debug("Wrote legacy model {}", destination.toAbsolutePath());
			}
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

	private static String normalizeTargetPath(String targetPath) {
		if (targetPath == null || targetPath.isBlank()) {
			return "";
		}
		String relative = targetPath.trim();
		if (relative.startsWith("minecraft:")) {
			relative = relative.substring("minecraft:".length());
		}
		while (relative.startsWith("/")) {
			relative = relative.substring(1);
		}
		return relative;
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
