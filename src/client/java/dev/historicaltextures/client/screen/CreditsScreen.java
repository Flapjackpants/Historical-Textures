package dev.historicaltextures.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class CreditsScreen extends Screen {
	private final Screen parent;

	public CreditsScreen(Screen parent) {
		super(Component.literal("Historical Textures — Credits"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
				.bounds(width / 2 - 50, height - 28, 100, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int y = 40;
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFF);
		for (String line : ATTRIBUTION) {
			graphics.centeredText(font, Component.literal(line), width / 2, y, 0xC0C0C0);
			y += 12;
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private static final String[] ATTRIBUTION = {
			"Texture and sound files are documented on minecraft.wiki.",
			"Wiki content is licensed under CC BY-SA 3.0.",
			"Minecraft assets are property of Mojang/Microsoft.",
			"Use this mod for personal/educational purposes.",
			"https://minecraft.wiki/w/History_of_textures",
			"https://minecraft.wiki/w/Category:Historical_sounds"
	};
}
