package dev.historicaltextures.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.CatalogResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

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
		if (variantId == null || assetPath == null || assetPath.isBlank()) {
			return Optional.empty();
		}
		CachedPreview cached = CACHE.get(variantId);
		if (cached != null) {
			return Optional.of(cached);
		}
		try (InputStream stream = CatalogResources.openAsset(assetPath)) {
			if (stream == null) {
				HistoricalTextures.LOGGER.warn("Missing catalog preview asset {} for variant {}", assetPath, variantId);
				return Optional.empty();
			}
			NativeImage image = NativeImage.read(stream);
			Identifier textureId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					"preview/" + sanitizeId(variantId)
			);
			DynamicTexture dynamicTexture = new DynamicTexture(textureId::toString, image);
			Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
			dynamicTexture.upload();
			CachedPreview preview = new CachedPreview(textureId, image.getWidth(), image.getHeight());
			CACHE.put(variantId, preview);
			return Optional.of(preview);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.warn("Failed to load catalog preview for {}", variantId, exception);
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
