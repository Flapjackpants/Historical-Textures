package dev.historicaltextures;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.historicaltextures.client.screen.HistoricalTexturesConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HistoricalTexturesConfigScreen::new;
	}
}
