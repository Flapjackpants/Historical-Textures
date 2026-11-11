package dev.historicaltextures;

import dev.historicaltextures.catalog.ClientCatalogLoader;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.pack.OverlayPackManager;
import net.fabricmc.api.ClientModInitializer;

import java.io.IOException;

public final class HistoricalTexturesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCatalogLoader.reload();
		ModConfig.reload();

		// The overlay pack is required, so vanilla's initial resource load selects it; no reload needed.
		try {
			OverlayPackManager.generateOverlayPack();
		} catch (IOException exception) {
			HistoricalTextures.LOGGER.error("Failed to generate overlay pack", exception);
		}
	}
}
