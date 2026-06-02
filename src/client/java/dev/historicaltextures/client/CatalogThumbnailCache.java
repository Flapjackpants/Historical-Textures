package dev.historicaltextures.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.CatalogResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CatalogThumbnailCache {
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Map<String, DynamicTexture> DYNAMIC_TEXTURES = new HashMap<>();

	private CatalogThumbnailCache() {
	}

	public static Optional<Identifier> get(String variantId, String assetPath) {
		if (variantId == null || assetPath == null || assetPath.isBlank()) {
			return Optional.empty();
		}
		Identifier cached = TEXTURES.get(variantId);
		if (cached != null) {
			return Optional.of(cached);
		}
		try (InputStream stream = openAssetStream(assetPath)) {
			if (stream == null) {
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
			TEXTURES.put(variantId, textureId);
			DYNAMIC_TEXTURES.put(variantId, dynamicTexture);
			return Optional.of(textureId);
		} catch (Exception exception) {
			HistoricalTextures.LOGGER.debug("Failed to load catalog preview for {}", variantId, exception);
			return Optional.empty();
		}
	}

	private static InputStream openAssetStream(String assetPath) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getResourceManager() != null) {
			String normalized = assetPath.replace('\\', '/');
			while (normalized.startsWith("/")) {
				normalized = normalized.substring(1);
			}
			Identifier resourceId = Identifier.fromNamespaceAndPath(
					HistoricalTextures.MOD_ID,
					"catalog/" + normalized
			);
			Resource resource = minecraft.getResourceManager().getResource(resourceId).orElse(null);
			if (resource != null) {
				try {
					return resource.open();
				} catch (Exception exception) {
					HistoricalTextures.LOGGER.debug("ResourceManager failed for {}", resourceId, exception);
				}
			}
		}
		return CatalogResources.openAsset(assetPath);
	}

	public static void clear() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Map.Entry<String, Identifier> entry : TEXTURES.entrySet()) {
			minecraft.getTextureManager().release(entry.getValue());
			DynamicTexture dynamicTexture = DYNAMIC_TEXTURES.remove(entry.getKey());
			if (dynamicTexture != null) {
				dynamicTexture.close();
			}
		}
		TEXTURES.clear();
		DYNAMIC_TEXTURES.clear();
	}

	private static String sanitizeId(String variantId) {
		return variantId.replaceAll("[^a-z0-9_./-]", "_");
	}
}
