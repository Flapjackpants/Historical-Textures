package dev.historicaltextures.catalog;

import dev.historicaltextures.HistoricalTextures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import dev.historicaltextures.client.CatalogThumbnailCache;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;

@Environment(EnvType.CLIENT)
public final class ClientCatalogLoader {
	private ClientCatalogLoader() {
	}

	public static void reload() {
		CatalogThumbnailCache.clear();
		boolean loaded = false;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getResourceManager() != null) {
			Identifier catalogId = Identifier.fromNamespaceAndPath(HistoricalTextures.MOD_ID, "catalog/catalog.json");
			Resource resource = minecraft.getResourceManager().getResource(catalogId).orElse(null);
			if (resource != null) {
				try (InputStream stream = resource.open()) {
					HistoricalCatalog.loadFromStream(stream);
					loaded = true;
				} catch (Exception exception) {
					HistoricalTextures.LOGGER.warn("Failed to load catalog from ResourceManager, falling back", exception);
				}
			}
		}

		if (!loaded) {
			HistoricalCatalog.reload();
		}

		HistoricalCatalog catalog = HistoricalCatalog.get();
		HistoricalTextures.LOGGER.info(
				"Config UI catalog: {} texture variants across {} targets, {} sound events",
				catalog.textureVariants().size(),
				catalog.texturesByTarget().size(),
				catalog.soundsByEvent().size()
		);
		if (catalog.textureVariants().isEmpty()) {
			HistoricalTextures.LOGGER.error(
					"Catalog is empty. Run './gradlew :wiki-indexer:indexQuick build' to bundle wiki assets."
			);
		}
	}
}
