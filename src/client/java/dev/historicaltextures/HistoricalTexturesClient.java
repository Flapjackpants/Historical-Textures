package dev.historicaltextures;

import dev.historicaltextures.catalog.ClientCatalogLoader;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.pack.OverlayPackManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class HistoricalTexturesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCatalogLoader.reload();
		ModConfig.reload();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> OverlayPackManager.applyChoices(true));
	}
}
