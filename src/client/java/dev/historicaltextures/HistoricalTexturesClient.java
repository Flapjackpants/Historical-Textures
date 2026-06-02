package dev.historicaltextures;

import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.pack.OverlayPackManager;
import dev.historicaltextures.pack.OverlayResourcePackProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class HistoricalTexturesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		OverlayResourcePackProvider.register();
		HistoricalCatalog.reload();
		ModConfig.reload();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> OverlayPackManager.applyChoices(false));
	}
}
