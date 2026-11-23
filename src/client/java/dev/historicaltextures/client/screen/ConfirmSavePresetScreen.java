package dev.historicaltextures.client.screen;

import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.config.PresetStore;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfirmSavePresetScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;
	private static final int TEXT_HEADER = 0xFFAAAAAA;
	private static final int SELECTED_ROW = 0x55335588;
	private static final int ENTRY_HEIGHT = 15;

	private final Screen parent;
	private final Screen presetsScreen;
	private final String presetName;
	private final Map<String, String> targetNames = new HashMap<>();
	private SummaryList summaryList;

	public ConfirmSavePresetScreen(Screen parent, Screen presetsScreen, String presetName) {
		super(Component.literal("Are you sure you want to save this current configuration?"));
		this.parent = parent;
		this.presetsScreen = presetsScreen;
		this.presetName = presetName;
	}

	@Override
	protected void init() {
		collectTargetNames();

		int listTop = 56;
		int listBottom = height - 64;
		summaryList = new SummaryList(minecraft, width - 16, listBottom - listTop, listTop, ENTRY_HEIGHT);
		summaryList.setX(8);
		summaryList.replaceEntries(buildRows());
		addRenderableWidget(summaryList);

		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
				.bounds(width / 2 - 102, height - 52, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(width / 2 + 2, height - 52, 100, 20).build());
	}

	private void collectTargetNames() {
		for (List<TextureTarget> targets : List.of(
				TextureTargetRegistry.blockTargets(),
				TextureTargetRegistry.itemTargets(),
				TextureTargetRegistry.entityTargets(),
				TextureTargetRegistry.otherTargets())) {
			for (TextureTarget target : targets) {
				targetNames.putIfAbsent(target.configKey(), target.displayName());
			}
		}
	}

	private String targetDisplayName(String configKey) {
		return targetNames.getOrDefault(configKey, TextureTargetRegistry.humanize(configKey));
	}

	private String textureLabel(String variantId) {
		if (HistoricalCatalog.CURRENT_VARIANT_ID.equals(variantId)) {
			return "Current (" + SharedConstants.getCurrentVersion().name() + ")";
		}
		return HistoricalCatalog.get().textureVariant(variantId)
				.map(variant -> withWikiFile(variant.versionLabel(), variant.wikiFile()))
				.orElse(variantId);
	}

	private String withWikiFile(String label, String wikiFile) {
		if (wikiFile == null || wikiFile.isBlank()) {
			return label;
		}
		String candidate = label + " (" + wikiFile + ")";
		int maxWidth = summaryList != null ? summaryList.getWidth() - 24 : width - 40;
		return font.width(candidate) <= maxWidth ? candidate : label;
	}

	private String soundLabel(String variantId) {
		return HistoricalCatalog.get().soundVariant(variantId)
				.map(SoundVariant::versionLabel)
				.orElse(variantId);
	}

	private List<SummaryList.Entry> buildRows() {
		ModConfig config = ModConfig.get();
		Map<String, String> textures = config.textureChoices();
		Map<String, String> sounds = config.soundChoices();
		List<SummaryList.Entry> rows = new ArrayList<>();
		if (textures.isEmpty() && sounds.isEmpty()) {
			rows.add(new TextEntry("No selections (preset will reset everything)", false));
			return rows;
		}

		rows.add(new TextEntry("Textures (" + textures.size() + ")", true));
		List<Map.Entry<String, String>> textureRows = new ArrayList<>(textures.entrySet());
		textureRows.sort(Comparator.comparing(entry -> targetDisplayName(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
		for (Map.Entry<String, String> entry : textureRows) {
			rows.add(new TextEntry(
					targetDisplayName(entry.getKey()) + " → " + textureLabel(entry.getValue()),
					false));
		}

		rows.add(new TextEntry("Sounds (" + sounds.size() + ")", true));
		List<Map.Entry<String, String>> soundRows = new ArrayList<>(sounds.entrySet());
		soundRows.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
		for (Map.Entry<String, String> entry : soundRows) {
			rows.add(new TextEntry(entry.getKey() + " → " + soundLabel(entry.getValue()), false));
		}

		rows.add(new TextEntry("Villager hood: " + (config.hideVillagerHood() ? "Hidden" : "Shown"), true));
		return rows;
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

	private final class TextEntry extends SummaryList.Entry {
		private final String text;
		private final boolean header;

		private TextEntry(String text, boolean header) {
			this.text = text;
			this.header = header;
		}

		@Override
		public Component getNarration() {
			return Component.literal(text);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (summaryList.getSelected() == this) {
				graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom(), SELECTED_ROW);
			}
			String shown = font.plainSubstrByWidth(text, getContentRight() - getContentX() - 8);
			graphics.text(font, Component.literal(shown), getContentX() + 4, getContentYMiddle() - 4,
					header ? TEXT_HEADER : TEXT_WHITE);
		}
	}

	private static final class SummaryList extends ObjectSelectionList<SummaryList.Entry> {
		SummaryList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
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
