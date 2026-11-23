package dev.historicaltextures.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.CatalogResources;
import dev.historicaltextures.catalog.LegacyModels;
import dev.historicaltextures.pack.OverlayPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CatalogThumbnailCache {
	private static final Map<String, CachedPreview> CACHE = new HashMap<>();

	private CatalogThumbnailCache() {
	}

	public record CachedPreview(Identifier textureId, int width, int height) {
	}

	public static Optional<CachedPreview> get(String variantId, String assetPath) {
		return get(variantId, assetPath, false);
	}

	public static Optional<CachedPreview> get(String variantId, String assetPath, boolean stripVillagerHood) {
		if (variantId == null || assetPath == null || assetPath.isBlank()) {
			return Optional.empty();
		}
		String cacheKey = stripVillagerHood ? "nohood:" + variantId : variantId;
		CachedPreview cached = CACHE.get(cacheKey);
		if (cached != null) {
			return Optional.of(cached);
		}
		try (InputStream stream = CatalogResources.openAsset(assetPath)) {
			if (stream == null) {
				HistoricalTextures.LOGGER.warn("Missing catalog preview asset {} for variant {}", assetPath, variantId);
				return Optional.empty();
			}
			NativeImage image = NativeImage.read(stream);
			if (stripVillagerHood) {
				OverlayPackManager.clearVillagerHood(image);
			}
			String idPath = stripVillagerHood
					? "preview/nohood/" + sanitizeId(variantId)
					: "preview/" + sanitizeId(variantId);
			Identifier textureId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					idPath
			);
			DynamicTexture dynamicTexture = new DynamicTexture(textureId::toString, image);
			Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
			dynamicTexture.upload();
			CachedPreview preview = new CachedPreview(textureId, image.getWidth(), image.getHeight());
			CACHE.put(cacheKey, preview);
			return Optional.of(preview);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to load catalog preview for {}", variantId, exception);
			return Optional.empty();
		}
	}

	public static Optional<CachedPreview> getVanilla(String resourcePath) {
		if (resourcePath == null || resourcePath.isBlank()) {
			return Optional.empty();
		}
		String key = "vanilla:" + resourcePath;
		CachedPreview cached = CACHE.get(key);
		if (cached != null) {
			return Optional.of(cached);
		}
		try {
			Identifier resourceId = Identifier.parse(resourcePath);
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(resourceId);
			if (resource.isEmpty()) {
				HistoricalTextures.LOGGER.debug("Missing vanilla preview texture {}", resourcePath);
				return Optional.empty();
			}
			NativeImage image;
			try (InputStream stream = resource.get().open()) {
				image = NativeImage.read(stream);
			}
			Identifier textureId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					"preview/vanilla/" + sanitizeId(resourcePath)
			);
			DynamicTexture dynamicTexture = new DynamicTexture(textureId::toString, image);
			Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
			dynamicTexture.upload();
			CachedPreview preview = new CachedPreview(textureId, image.getWidth(), image.getHeight());
			CACHE.put(key, preview);
			return Optional.of(preview);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.debug("Failed to load vanilla preview for {}", resourcePath, exception);
			return Optional.empty();
		}
	}

	public static Optional<CachedPreview> getBuiltIn(String resourcePath) {
		if (resourcePath == null || resourcePath.isBlank()) {
			return Optional.empty();
		}
		String key = "builtin:" + resourcePath;
		CachedPreview cached = CACHE.get(key);
		if (cached != null) {
			return Optional.of(cached);
		}
		try {
			Identifier resourceId = Identifier.parse(resourcePath);
			var supplier = Minecraft.getInstance().getVanillaPackResources().fullResources()
					.getResource(PackType.CLIENT_RESOURCES, resourceId);
			if (supplier == null) {
				return Optional.empty();
			}
			NativeImage image;
			try (InputStream stream = supplier.get()) {
				if (stream == null) {
					return Optional.empty();
				}
				image = NativeImage.read(stream);
			}
			Identifier textureId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					"preview/builtin/" + sanitizeId(resourcePath)
			);
			DynamicTexture dynamicTexture = new DynamicTexture(textureId::toString, image);
			Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
			dynamicTexture.upload();
			CachedPreview preview = new CachedPreview(textureId, image.getWidth(), image.getHeight());
			CACHE.put(key, preview);
			return Optional.of(preview);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.debug("Failed to load built-in preview for {}", resourcePath, exception);
			return Optional.empty();
		}
	}

	public static boolean hasBuiltIn(String resourcePath) {
		if (resourcePath == null || resourcePath.isBlank()) {
			return false;
		}
		try {
			Identifier resourceId = Identifier.parse(resourcePath);
			return Minecraft.getInstance().getVanillaPackResources().fullResources()
					.getResource(PackType.CLIENT_RESOURCES, resourceId) != null;
		} catch (Exception exception) {
			return false;
		}
	}

	public static Optional<CachedPreview> getLegacy(String source) {
		if (source == null || source.isBlank()) {
			return Optional.empty();
		}
		String key = "legacy:" + source;
		CachedPreview cached = CACHE.get(key);
		if (cached != null) {
			return Optional.of(cached);
		}
		try (InputStream stream = LegacyModels.open(source)) {
			if (stream == null) {
				HistoricalTextures.LOGGER.debug("Missing legacy preview asset {}", source);
				return Optional.empty();
			}
			NativeImage image = NativeImage.read(stream);
			Identifier textureId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					"preview/legacy/" + sanitizeId(source)
			);
			DynamicTexture dynamicTexture = new DynamicTexture(textureId::toString, image);
			Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
			dynamicTexture.upload();
			CachedPreview preview = new CachedPreview(textureId, image.getWidth(), image.getHeight());
			CACHE.put(key, preview);
			return Optional.of(preview);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.debug("Failed to load legacy preview for {}", source, exception);
			return Optional.empty();
		}
	}

	public static Optional<CachedPreview> getBytes(String key, byte[] png) {
		if (key == null || png == null || png.length == 0) {
			return Optional.empty();
		}
		CachedPreview cached = CACHE.get(key);
		if (cached != null) {
			return Optional.of(cached);
		}
		try {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
			Identifier textureId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					"preview/bytes/" + sanitizeId(key)
			);
			DynamicTexture dynamicTexture = new DynamicTexture(textureId::toString, image);
			Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
			dynamicTexture.upload();
			CachedPreview preview = new CachedPreview(textureId, image.getWidth(), image.getHeight());
			CACHE.put(key, preview);
			return Optional.of(preview);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to load byte preview for {}", key, exception);
			return Optional.empty();
		}
	}

	public static void preload(String variantId, String assetPath) {
		get(variantId, assetPath);
	}

	public static void clear() {
		Minecraft minecraft = Minecraft.getInstance();
		for (CachedPreview preview : CACHE.values()) {
			minecraft.getTextureManager().release(preview.textureId());
		}
		CACHE.clear();
	}

	private static String sanitizeId(String variantId) {
		return variantId.replaceAll("[^a-z0-9_./-]", "_");
	}
}
