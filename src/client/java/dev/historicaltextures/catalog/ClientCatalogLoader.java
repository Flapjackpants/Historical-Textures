package dev.historicaltextures.catalog;

import dev.historicaltextures.HistoricalTextures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;

@Environment(EnvType.CLIENT)
public final class ClientCatalogLoader {
	private ClientCatalogLoader() {
	}

	public static void reload() {
		Identifier catalogId = Identifier.fromNamespaceAndPath(HistoricalTextures.MOD_ID, "catalog/catalog.json");
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getResourceManager() != null) {
			Resource resource = minecraft.getResourceManager().getResource(catalogId).orElse(null);
			if (resource != null) {
				try (InputStream stream = resource.open()) {
					HistoricalCatalog.loadFromStream(stream);
					return;
				} catch (Exception exception) {
					HistoricalTextures.LOGGER.warn("Failed to load catalog from ResourceManager, falling back", exception);
				}
			}
		}
		HistoricalCatalog.reload();
	}
}
