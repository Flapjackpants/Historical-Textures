package dev.historicaltextures.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.config.BuiltInPresets;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.config.Preset;
import dev.historicaltextures.config.PresetStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class PresetsScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;
	private static final int SELECTED_ROW = 0x55335588;
	private static final int ENTRY_HEIGHT = 26;

	private final Screen parent;
	private List<Preset> builtIns = List.of();
	private PresetList presetList;
	private Button loadButton;
	private Button deleteButton;

	public PresetsScreen(Screen parent) {
		super(Component.literal("Presets"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		builtIns = BuiltInPresets.create(HistoricalCatalog.get());

		int listTop = 28;
		int listBottom = height - 64;
		presetList = new PresetList(minecraft, width - 16, listBottom - listTop, listTop, ENTRY_HEIGHT);
		presetList.setX(8);
		rebuildEntries();
		addRenderableWidget(presetList);

		loadButton = addRenderableWidget(Button.builder(Component.literal("Load"), b -> loadSelected())
				.bounds(8, height - 28, 80, 20).build());
		deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSelected())
				.bounds(96, height - 28, 80, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save current…"), b ->
						minecraft.setScreenAndShow(new SavePresetScreen(this)))
				.bounds(184, height - 28, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(width - 88, height - 28, 80, 20).build());
	}

	private void rebuildEntries() {
		List<PresetList.Entry> entries = new ArrayList<>();
		for (Preset preset : builtIns) {
			entries.add(new PresetEntry(preset));
		}
		for (Preset preset : PresetStore.get().userPresets()) {
			entries.add(new PresetEntry(preset));
		}
		presetList.replaceEntries(entries);
		presetList.setScrollAmount(0);
	}

	List<String> builtInNames() {
		return builtIns.stream().map(Preset::name).toList();
	}

	private Preset selectedPreset() {
		if (presetList == null) {
			return null;
		}
		return presetList.getSelected() instanceof PresetEntry entry ? entry.preset : null;
	}

	private void updateButtons() {
		Preset selected = selectedPreset();
		loadButton.active = selected != null;
		deleteButton.active = selected != null && !selected.builtIn();
	}

	private void loadSelected() {
		Preset preset = selectedPreset();
		if (preset == null) {
			return;
		}
		if (ModConfig.get().hasChoices()) {
			minecraft.setScreenAndShow(new ConfirmScreen(yes -> {
				if (yes) {
					applyPreset(preset);
				} else {
					minecraft.setScreenAndShow(this);
				}
			}, Component.literal("Load preset \"" + preset.name() + "\"?"),
					Component.literal("This replaces your current selections. Click Apply & Reload afterwards to see them in game.")));
		} else {
			applyPreset(preset);
		}
	}

	private void applyPreset(Preset preset) {
		ModConfig.get().applyPreset(preset);
		ModConfig.get().save();
		minecraft.setScreenAndShow(parent);
	}

	private void deleteSelected() {
		Preset preset = selectedPreset();
		if (preset == null || preset.builtIn()) {
			return;
		}
		minecraft.setScreenAndShow(new ConfirmScreen(yes -> {
			if (yes) {
				PresetStore.get().delete(preset.name());
			}
			minecraft.setScreenAndShow(this);
		}, Component.literal("Delete preset \"" + preset.name() + "\"?"),
				Component.literal("This removes the saved preset.")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		updateButtons();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, TEXT_WHITE);
		graphics.centeredText(font,
				Component.literal("Loaded presets take effect after Apply & Reload."),
				width / 2, height - 48, TEXT_MUTED);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	private String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int dotsWidth = font.width("...");
		if (maxWidth <= dotsWidth) {
			return "...";
		}
		return font.plainSubstrByWidth(text, maxWidth - dotsWidth) + "...";
	}

	private final class PresetEntry extends PresetList.Entry {
		private final Preset preset;

		private PresetEntry(Preset preset) {
			this.preset = preset;
		}

		@Override
		public Component getNarration() {
			return Component.literal(preset.name());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (presetList.getSelected() == this) {
				graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom(), SELECTED_ROW);
			}
			int maxW = getContentRight() - (getContentX() + 4) - 4;
			String kind = preset.builtIn() ? "Built-in" : "Saved";
			String subtitle = kind + " · " + preset.textureChoices().size()
					+ " textures, " + preset.soundChoices().size() + " sounds";
			graphics.text(font, fit(preset.name(), maxW), getContentX() + 4, getContentY() + 3, TEXT_WHITE);
			graphics.text(font, fit(subtitle, maxW), getContentX() + 4, getContentY() + 15, TEXT_MUTED);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			presetList.setSelected(this);
			return true;
		}
	}

	private static final class PresetList extends ObjectSelectionList<PresetList.Entry> {
		PresetList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
			super(minecraft, width, height, top, entryHeight);
		}

		@Override
		public int getRowWidth() {
			return width - 14;
		}

		@Override
		protected int scrollBarX() {
			return getX() + width - scrollbarWidth();
		}

		@Override
		public int getRowLeft() {
			return getX() + 2;
		}

		abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
		}
	}
}
