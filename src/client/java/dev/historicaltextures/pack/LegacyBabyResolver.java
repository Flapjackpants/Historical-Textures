package dev.historicaltextures.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.LegacyBabies;
import dev.historicaltextures.catalog.LegacyBabyModels;
import dev.historicaltextures.catalog.LegacyModels;
import dev.historicaltextures.catalog.LegacyTextureFixes;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the effective adult texture and baby JEM for a legacy baby target.
 * The baby follows whatever variant the user picked for the adult texture.
 */
public final class LegacyBabyResolver {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private LegacyBabyResolver() {
	}

	/**
	 * PNG bytes for the adult texture the baby should wear: the user's adult
	 * choice when it resolves, otherwise the bundled fallback or vanilla file.
	 */
	public static Optional<byte[]> adultTextureBytes(LegacyBabies.Entry entry, ModConfig config) {
		String adultRelative = relative(entry.adultTexture());
		if (adultRelative.isEmpty()) {
			return Optional.empty();
		}
		if (LegacyTextureFixes.villagerTypeTexturesToBlank(config.textureChoices()).contains(adultRelative)) {
			return transparentPng();
		}
		Optional<TextureVariant> variant = adultVariant(entry, config);
		if (variant.isPresent()) {
			String choice = config.getTextureChoice(entry.adultTexture());
			for (Map.Entry<String, String> file : LegacyModels.filesFor(entry.adultTexture(), choice).entrySet()) {
				if (adultRelative.equals(file.getKey()) && file.getKey().endsWith(".png")) {
					Optional<byte[]> converted = readBytes(LegacyModels.open(file.getValue()));
					if (converted.isPresent()) {
						return converted;
					}
				}
			}
			return readBytes(HistoricalCatalog.get().openAsset(variant.get().assetPath()));
		}
		if (entry.fallbackAdultTexture() != null) {
			Optional<byte[]> fallback = readBytes(LegacyModels.open(entry.fallbackAdultTexture()));
			if (fallback.isPresent()) {
				return fallback;
			}
		}
		return vanillaTextureBytes(entry.adultTexture());
	}

	/**
	 * The baby JEM: derived from the adult's legacy JEM when the user picked a
	 * legacy adult variant and a transform is known, else the static 1.21.11 export.
	 */
	public static Optional<JsonObject> babyJem(LegacyBabies.Entry entry, ModConfig config) {
		String choice = config.getTextureChoice(entry.adultTexture());
		if (entry.legacyTransform() != null && adultVariant(entry, config).isPresent()) {
			for (Map.Entry<String, String> file : LegacyModels.filesFor(entry.adultTexture(), choice).entrySet()) {
				if (file.getKey() == null || !file.getKey().endsWith(".jem")) {
					continue;
				}
				Optional<JsonObject> adult = parseJem(file.getValue());
				if (adult.isPresent()) {
					return Optional.of(LegacyBabyModels.fromAdultJem(adult.get(), entry.legacyTransform()));
				}
			}
		}
		return parseJem(entry.jem());
	}

	/**
	 * Writes the baby texture and EMF JEM into the overlay pack root.
	 */
	public static boolean write(Path overlayRoot, LegacyBabies.Entry entry) {
		ModConfig config = ModConfig.get();
		Optional<byte[]> texture = adultTextureBytes(entry, config);
		Optional<JsonObject> jem = babyJem(entry, config);
		if (texture.isEmpty() || jem.isEmpty()) {
			HistoricalTextures.LOGGER.warn(
					"Missing assets for legacy baby {} (texture={}, jem={})",
					entry.babyTexture(),
					texture.isPresent(),
					jem.isPresent()
			);
			return false;
		}
		try {
			Path textureDestination = overlayRoot.resolve("assets/minecraft").resolve(relative(entry.babyTexture()));
			Files.createDirectories(textureDestination.getParent());
			Files.write(textureDestination, texture.get());
			Path jemDestination = overlayRoot.resolve("assets/minecraft/optifine/cem")
					.resolve(entry.emfModel() + ".jem");
			Files.createDirectories(jemDestination.getParent());
			Files.writeString(jemDestination, GSON.toJson(jem.get()));
			HistoricalTextures.LOGGER.debug("Wrote legacy baby {}", entry.babyTexture());
			return true;
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.warn("Failed to write legacy baby {}", entry.babyTexture(), exception);
			return false;
		}
	}

	private static Optional<TextureVariant> adultVariant(LegacyBabies.Entry entry, ModConfig config) {
		String choice = config.getTextureChoice(entry.adultTexture());
		if (choice == null
				|| HistoricalCatalog.CURRENT_VARIANT_ID.equals(choice)
				|| LegacyBabies.VARIANT_ID.equals(choice)) {
			return Optional.empty();
		}
		return HistoricalCatalog.get().textureVariant(choice)
				.filter(variant -> variant.targets().contains(entry.adultTexture()));
	}

	private static Optional<byte[]> vanillaTextureBytes(String target) {
		Identifier resourceId;
		try {
			resourceId = Identifier.parse(target);
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
		var supplier = Minecraft.getInstance().getVanillaPackResources().fullResources()
				.getResource(PackType.CLIENT_RESOURCES, resourceId);
		if (supplier == null) {
			return Optional.empty();
		}
		try (InputStream input = supplier.get()) {
			return input == null ? Optional.empty() : readBytes(input);
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.warn("Failed to read vanilla adult texture {}", target, exception);
			return Optional.empty();
		}
	}

	private static Optional<JsonObject> parseJem(String source) {
		try (InputStream stream = LegacyModels.open(source)) {
			if (stream == null) {
				return Optional.empty();
			}
			return Optional.of(JsonParser.parseReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to parse legacy baby model {}", source, exception);
			return Optional.empty();
		}
	}

	private static Optional<byte[]> readBytes(InputStream stream) {
		if (stream == null) {
			return Optional.empty();
		}
		try (InputStream input = stream) {
			return Optional.of(input.readAllBytes());
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.warn("Failed to read legacy baby texture", exception);
			return Optional.empty();
		}
	}

	private static Optional<byte[]> transparentPng() {
		Path temporary = null;
		try {
			temporary = Files.createTempFile("ht-baby", ".png");
			try (NativeImage image = new NativeImage(64, 64, true)) {
				image.fillRect(0, 0, 64, 64, 0);
				image.writeToFile(temporary);
			}
			return Optional.of(Files.readAllBytes(temporary));
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.warn("Failed to encode transparent baby texture", exception);
			return Optional.empty();
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static String relative(String target) {
		if (target == null) {
			return "";
		}
		return target.startsWith("minecraft:") ? target.substring("minecraft:".length()) : target;
	}
}
