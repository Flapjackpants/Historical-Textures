package dev.historicaltextures.client.screen;

import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.config.PresetStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ConfirmSavePresetScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;

	private final Screen parent;
	private final Screen presetsScreen;
	private final String presetName;
	private PresetContentsList contentsList;

	public ConfirmSavePresetScreen(Screen parent, Screen presetsScreen, String presetName) {
		super(Component.literal("Are you sure you want to save this current configuration?"));
		this.parent = parent;
		this.presetsScreen = presetsScreen;
		this.presetName = presetName;
	}

	@Override
	protected void init() {
		int listTop = 56;
		int listBottom = height - 64;
		contentsList = new PresetContentsList(minecraft, width - 16, listBottom - listTop, listTop);
		contentsList.setX(8);
		contentsList.setY(listTop);
		ModConfig config = ModConfig.get();
		contentsList.show(config.textureChoices(), config.soundChoices(), config.hideVillagerHood());
		addRenderableWidget(contentsList);

		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
				.bounds(width / 2 - 102, height - 52, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(width / 2 + 2, height - 52, 100, 20).build());
	}

	private void save() {
		PresetStore.get().save(ModConfig.get().snapshot(presetName));
		minecraft.setScreenAndShow(presetsScreen);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 14, TEXT_WHITE);
		String subtitle = "Preset: " + presetName
				+ (PresetStore.get().exists(presetName) ? " (overwrites existing)" : "");
		graphics.centeredText(font, Component.literal(subtitle), width / 2, 30, TEXT_MUTED);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
