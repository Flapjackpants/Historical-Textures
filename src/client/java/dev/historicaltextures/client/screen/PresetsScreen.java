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
import java.util.Map;

public final class PresetsScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;
	private static final int SELECTED_ROW = 0x55335588;
	private static final int ENTRY_HEIGHT = 26;

	private final Screen parent;
	private List<Preset> builtIns = List.of();
	private PresetList presetList;
	private PresetContentsList contentsList;
	private Button applyButton;
	private Button overwriteButton;
	private Button deleteButton;
	private String selectedPresetName;

	public PresetsScreen(Screen parent) {
		super(Component.literal("Presets"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		builtIns = BuiltInPresets.create(HistoricalCatalog.get());

		int listTop = 28;
		int listBottom = height - 40;
		int listHeight = listBottom - listTop;
		int listWidth = (width - 24) * 2 / 5;
		presetList = new PresetList(minecraft, listWidth, listHeight, listTop, ENTRY_HEIGHT);
		presetList.setX(8);

		int contentsX = 8 + listWidth + 8;
		int contentsWidth = width - 8 - contentsX;
		contentsList = new PresetContentsList(minecraft, contentsWidth, listHeight, listTop);
		contentsList.setX(contentsX);
		contentsList.setY(listTop);

		addRenderableWidget(presetList);
		addRenderableWidget(contentsList);
		rebuildEntries();

		int[] widths = buttonWidths();
		int buttonY = height - 28;
		int x = 8;
		applyButton = addRenderableWidget(Button.builder(Component.literal("Apply"), b -> applySelected())
				.bounds(x, buttonY, widths[0], 20).build());
		x += widths[0] + 4;
		overwriteButton = addRenderableWidget(Button.builder(Component.literal("Overwrite"), b -> overwriteSelected())
				.bounds(x, buttonY, widths[1], 20).build());
		x += widths[1] + 4;
		deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSelected())
				.bounds(x, buttonY, widths[2], 20).build());
		x += widths[2] + 4;
		addRenderableWidget(Button.builder(Component.literal("Save current…"), b ->
						minecraft.setScreenAndShow(new SavePresetScreen(this)))
				.bounds(x, buttonY, widths[3], 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(width - 8 - widths[4], buttonY, widths[4], 20).build());
	}

	private int[] buttonWidths() {
		int[] widths = {50, 64, 46, 78, 50};
		int available = width - 32;
		int total = 0;
		for (int buttonWidth : widths) {
			total += buttonWidth;
		}
		if (total <= available) {
			return widths;
		}
		double scale = (double) available / total;
		total = 0;
		for (int i = 0; i < widths.length; i++) {
			widths[i] = Math.max(32, (int) Math.round(widths[i] * scale));
			total += widths[i];
		}
		while (total > available) {
			int largest = 0;
			for (int i = 1; i < widths.length; i++) {
				if (widths[i] > widths[largest]) {
					largest = i;
				}
			}
			if (widths[largest] <= 32) {
				break;
			}
			widths[largest]--;
			total--;
		}
		return widths;
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

		PresetEntry selectedEntry = null;
		for (PresetList.Entry entry : entries) {
			if (entry instanceof PresetEntry presetEntry
					&& presetEntry.preset.name().equalsIgnoreCase(selectedPresetName)) {
				selectedEntry = presetEntry;
				break;
			}
		}
		if (selectedEntry == null && !entries.isEmpty()) {
			selectedEntry = (PresetEntry) entries.get(0);
		}
		if (selectedEntry != null) {
			presetList.setSelected(selectedEntry);
			selectedPresetName = selectedEntry.preset.name();
			showPreset(selectedEntry.preset);
		} else {
			contentsList.show(Map.of(), Map.of(), ModConfig.get().hideVillagerHood());
		}
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

	private void showPreset(Preset preset) {
		contentsList.show(preset.textureChoices(), preset.soundChoices(), preset.hideVillagerHood());
	}

	private void updateButtons() {
		Preset selected = selectedPreset();
		applyButton.active = selected != null;
		overwriteButton.active = selected != null && !selected.builtIn();
		deleteButton.active = selected != null && !selected.builtIn();
	}

	private void applySelected() {
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
					Component.literal("This replaces your current selections.")));
		} else {
			applyPreset(preset);
		}
	}

	private void applyPreset(Preset preset) {
		ModConfig.get().applyPreset(preset);
		ModConfig.get().save();
		minecraft.setScreenAndShow(parent);
		HistoricalTexturesConfigScreen.applyAndReport(minecraft);
	}

	private void overwriteSelected() {
		Preset preset = selectedPreset();
		if (preset == null || preset.builtIn()) {
			return;
		}
		minecraft.setScreenAndShow(new ConfirmSavePresetScreen(this, this, preset.name()));
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
				Component.literal("Apply loads the preset and reloads resources."),
				width / 2, height - 38, TEXT_MUTED);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
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
			graphics.text(font, ColumnList.fit(font, preset.name(), maxW), getContentX() + 4, getContentY() + 3, TEXT_WHITE);
			graphics.text(font, ColumnList.fit(font, subtitle, maxW), getContentX() + 4, getContentY() + 15, TEXT_MUTED);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			presetList.setSelected(this);
			selectedPresetName = preset.name();
			showPreset(preset);
			if (doubleClick) {
				applySelected();
			}
			return true;
		}
	}

	private static final class PresetList extends ColumnList<PresetList.Entry> {
		PresetList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
			super(minecraft, width, height, top, entryHeight);
		}

		abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
		}
	}
}
