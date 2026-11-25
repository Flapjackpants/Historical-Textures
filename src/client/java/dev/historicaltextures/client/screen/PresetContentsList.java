package dev.historicaltextures.client.screen;

import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.client.CatalogThumbnailCache;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PresetContentsList extends ColumnList<PresetContentsList.Entry> {
	private static final int ROW_HEIGHT = 20;
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;
	private static final int TEXT_HEADER = 0xFFAAAAAA;
	private static final int PREVIEW_SIZE = 16;
	private static final int PREVIEW_PLACEHOLDER = 0xFF404040;

	private final Font font;
	private final Map<String, String> targetNames = new HashMap<>();

	PresetContentsList(Minecraft minecraft, int width, int height, int top) {
		super(minecraft, width, height, top, ROW_HEIGHT);
		this.font = minecraft.font;
	}

	void show(Map<String, String> textureChoices, Map<String, List<String>> soundChoices, boolean hideVillagerHood) {
		targetNames.clear();
		collectTargetNames();
		replaceEntries(buildRows(textureChoices, soundChoices, hideVillagerHood));
		setScrollAmount(0);
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

	private List<Entry> buildRows(Map<String, String> textureChoices, Map<String, List<String>> soundChoices,
			boolean hideVillagerHood) {
		List<Entry> rows = new ArrayList<>();
		if (textureChoices.isEmpty() && soundChoices.isEmpty()) {
			rows.add(new TextEntry("No changes (everything vanilla)"));
			rows.add(new TextEntry("Villager hood: " + (hideVillagerHood ? "Hidden" : "Shown")));
			return rows;
		}

		Map<Section, List<TextureRow>> texturesBySection = new EnumMap<>(Section.class);
		for (Section section : Section.values()) {
			texturesBySection.put(section, new ArrayList<>());
		}
		for (Map.Entry<String, String> choice : textureChoices.entrySet()) {
			texturesBySection.get(sectionForTextureKey(choice.getKey()))
					.add(new TextureRow(choice.getKey(), choice.getValue(), textureName(choice.getKey()),
							textureVersion(choice.getValue())));
		}
		for (List<TextureRow> sectionRows : texturesBySection.values()) {
			sectionRows.sort(Comparator.comparing(TextureRow::name, String.CASE_INSENSITIVE_ORDER));
		}

		List<SoundRow> soundRows = new ArrayList<>();
		for (Map.Entry<String, List<String>> choice : soundChoices.entrySet()) {
			soundRows.add(new SoundRow(choice.getKey(), choice.getValue(), soundVersion(choice.getValue())));
		}
		soundRows.sort(Comparator.comparing(SoundRow::event, String.CASE_INSENSITIVE_ORDER));

		for (Section section : Section.values()) {
			List<TextureRow> textureRows = texturesBySection.get(section);
			List<SoundRow> sectionSoundRows = section == Section.SOUNDS ? soundRows : List.of();
			int count = textureRows.size() + sectionSoundRows.size();
			if (count == 0) {
				continue;
			}
			rows.add(new HeaderEntry(section.label + " (" + count + ")"));
			for (TextureRow row : textureRows) {
				rows.add(new TextureEntry(row));
			}
			for (SoundRow row : sectionSoundRows) {
				rows.add(new SoundEntry(row));
			}
		}
		rows.add(new TextEntry("Villager hood: " + (hideVillagerHood ? "Hidden" : "Shown")));
		return rows;
	}

	private Section sectionForTextureKey(String key) {
		if (key != null && key.contains("textures/block/")) {
			return Section.BLOCKS;
		}
		if (key != null && key.contains("textures/item/")) {
			return Section.ITEMS;
		}
		if (key != null && key.contains("textures/entity/")) {
			return Section.ENTITIES;
		}
		return Section.OTHER;
	}

	private String textureName(String key) {
		return targetNames.getOrDefault(key, TextureTargetRegistry.humanize(key));
	}

	private String textureVersion(String variantId) {
		if (HistoricalCatalog.CURRENT_VARIANT_ID.equals(variantId)) {
			return "Current (" + SharedConstants.getCurrentVersion().name() + ")";
		}
		String version = HistoricalCatalog.get().textureVariant(variantId)
				.map(TextureVariant::versionLabel)
				.orElse(variantId);
		return version == null ? "" : version;
	}

	private String soundVersion(List<String> variantIds) {
		if (variantIds == null || variantIds.isEmpty()) {
			return "";
		}
		if (variantIds.size() == 1) {
			return soundVersion(variantIds.get(0));
		}
		return variantIds.size() + " sounds";
	}

	private String soundVersion(String variantId) {
		String version = HistoricalCatalog.get().soundVariant(variantId)
				.map(SoundVariant::versionLabel)
				.orElse(variantId);
		return version == null ? "" : version;
	}

	private String combinedText(String name, String version) {
		if (version == null || version.isEmpty()) {
			return name;
		}
		return name + " " + version;
	}

	private void drawLabeledRow(GuiGraphicsExtractor graphics, int textX, int contentRight, int centerY,
			String name, String version) {
		String safeVersion = version == null ? "" : version;
		int textRight = contentRight - 4;
		int versionWidth = Math.min(font.width(safeVersion), Math.max(24, (textRight - textX) / 2));
		String shownVersion = fit(font, safeVersion, versionWidth);
		int nameMaxWidth = textRight - font.width(shownVersion) - 4 - textX;
		String shownName = fit(font, name, Math.max(8, nameMaxWidth));
		int y = centerY - 4;
		graphics.text(font, shownName, textX, y, TEXT_WHITE);
		if (!shownVersion.isEmpty()) {
			graphics.text(font, shownVersion, textRight - font.width(shownVersion), y, TEXT_MUTED);
		}
	}

	private enum Section {
		BLOCKS("Blocks"),
		ITEMS("Items"),
		ENTITIES("Entities"),
		OTHER("Other"),
		SOUNDS("Sounds");

		private final String label;

		Section(String label) {
			this.label = label;
		}
	}

	private record TextureRow(String key, String variantId, String name, String version) {
	}

	private record SoundRow(String event, List<String> variantIds, String version) {
	}

	abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}
	}

	private final class HeaderEntry extends Entry {
		private final String text;

		private HeaderEntry(String text) {
			this.text = text;
		}

		@Override
		public Component getNarration() {
			return Component.literal(text);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
				float partialTick) {
			graphics.text(font, fit(font, text, getContentRight() - getContentX() - 8),
					getContentX() + 4, getContentYMiddle() - 4, TEXT_HEADER);
		}
	}

	private final class TextEntry extends Entry {
		private final String text;

		private TextEntry(String text) {
			this.text = text;
		}

		@Override
		public Component getNarration() {
			return Component.literal(text);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
				float partialTick) {
			graphics.text(font, fit(font, text, getContentRight() - getContentX() - 8),
					getContentX() + 4, getContentYMiddle() - 4, TEXT_WHITE);
		}
	}

	private final class TextureEntry extends Entry {
		private final TextureRow row;
		private Optional<CatalogThumbnailCache.CachedPreview> preview;
		private boolean previewLoaded;

		private TextureEntry(TextureRow row) {
			this.row = row;
		}

		private Optional<CatalogThumbnailCache.CachedPreview> preview() {
			if (!previewLoaded) {
				preview = resolvePreview();
				previewLoaded = true;
			}
			return preview;
		}

		private Optional<CatalogThumbnailCache.CachedPreview> resolvePreview() {
			if (HistoricalCatalog.CURRENT_VARIANT_ID.equals(row.variantId())) {
				return CatalogThumbnailCache.getBuiltIn(row.key());
			}
			return HistoricalCatalog.get().textureVariant(row.variantId())
					.map(variant -> CatalogThumbnailCache.get(variant.id(), variant.assetPath()))
					.orElseGet(Optional::empty);
		}

		@Override
		public Component getNarration() {
			return Component.literal(combinedText(row.name(), row.version()));
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
				float partialTick) {
			int previewX = getContentX() + 4;
			int previewY = getContentYMiddle() - PREVIEW_SIZE / 2;
			Optional<CatalogThumbnailCache.CachedPreview> cached = preview();
			if (cached.isPresent()) {
				CatalogThumbnailCache.CachedPreview cachedPreview = cached.get();
				float vMax = cachedPreview.height() > 0
						? Math.min(1.0f, (float) cachedPreview.width() / cachedPreview.height())
						: 1.0f;
				graphics.blit(
						cachedPreview.textureId(),
						previewX,
						previewY,
						previewX + PREVIEW_SIZE,
						previewY + PREVIEW_SIZE,
						0.0f,
						1.0f,
						0.0f,
						vMax
				);
			} else {
				graphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE,
						PREVIEW_PLACEHOLDER);
			}
			drawLabeledRow(graphics, previewX + PREVIEW_SIZE + 4, getContentRight(), getContentYMiddle(),
					row.name(), row.version());
		}
	}

	private final class SoundEntry extends Entry {
		private final SoundRow row;

		private SoundEntry(SoundRow row) {
			this.row = row;
		}

		@Override
		public Component getNarration() {
			return Component.literal(combinedText(row.event(), row.version()));
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
				float partialTick) {
			drawLabeledRow(graphics, getContentX() + 4, getContentRight(), getContentYMiddle(),
					row.event(), row.version());
		}
	}
}
