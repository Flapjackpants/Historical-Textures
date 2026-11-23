package dev.historicaltextures.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class SavePresetScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;

	private final PresetsScreen presets;
	private EditBox nameBox;
	private Button nextButton;

	public SavePresetScreen(PresetsScreen presets) {
		super(Component.literal("Save preset"));
		this.presets = presets;
	}

	@Override
	protected void init() {
		nameBox = new EditBox(font, width / 2 - 150, height / 2 - 30, 300, 20,
				Component.literal("Preset name"));
		nameBox.setMaxLength(32);
		nameBox.setResponder(value -> updateNext());
		addRenderableWidget(nameBox);

		nextButton = addRenderableWidget(Button.builder(Component.literal("Next"), b -> next())
				.bounds(width / 2 - 102, height / 2 + 14, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(width / 2 + 2, height / 2 + 14, 100, 20).build());
		updateNext();
	}

	private void updateNext() {
		nextButton.active = isValidName(nameBox.getValue().trim());
	}

	private boolean isValidName(String name) {
		if (name.isEmpty()) {
			return false;
		}
		for (String builtIn : presets.builtInNames()) {
			if (builtIn.equalsIgnoreCase(name)) {
				return false;
			}
		}
		return true;
	}

	private void next() {
		String name = nameBox.getValue().trim();
		if (!isValidName(name)) {
			return;
		}
		minecraft.setScreenAndShow(new ConfirmSavePresetScreen(this, presets, name));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, height / 2 - 56, TEXT_WHITE);
		graphics.centeredText(font,
				Component.literal("Name the preset for your current selections."),
				width / 2, height / 2 - 44, TEXT_MUTED);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(presets);
	}
}
