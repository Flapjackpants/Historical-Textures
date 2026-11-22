package dev.historicaltextures.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.historicaltextures.catalog.ClientCatalogLoader;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.client.CatalogThumbnailCache;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.pack.OverlayPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HistoricalTexturesConfigScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;
	private static final int TEXT_ACCENT = 0xFF55FF55;
	private static final int TEXT_ERROR = 0xFFFF5555;
	private static final int TARGET_ENTRY_HEIGHT = 24;
	private static final int VARIANT_ENTRY_HEIGHT = 32;
	private static final int PREVIEW_SIZE = 16;
	private static final int PREVIEW_PLACEHOLDER = 0xFF404040;
	private static final int SELECTED_ROW = 0x55335588;

	private final Screen parent;
	private Tab activeTab = Tab.BLOCKS;
	private EditBox searchBox;
	private TargetList targetList;
	private VariantList variantList;
	private TextureTarget selectedTarget;
	private String selectedSoundEvent;
	private String filter = "";

	private int col1X;
	private int col2X;
	private int col3X;
	private int col1W;
	private int col2W;
	private int previewW;
	private int listTop;
	private int listBottom;

	private Component emptyListHint;
	private Component emptyVariantHint;

	public HistoricalTexturesConfigScreen(Screen parent) {
		super(Component.translatable("screen.historical_textures.config"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientCatalogLoader.reload();
		TextureTargetRegistry.reload();

		int tabY = 8;
		int tabX = 8;
		addRenderableWidget(Button.builder(Component.literal("Blocks"), b -> switchTab(Tab.BLOCKS)).bounds(tabX, tabY, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Items"), b -> switchTab(Tab.ITEMS)).bounds(tabX + 64, tabY, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Entities"), b -> switchTab(Tab.ENTITIES)).bounds(tabX + 128, tabY, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Other"), b -> switchTab(Tab.OTHER)).bounds(tabX + 192, tabY, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Sounds"), b -> switchTab(Tab.SOUNDS)).bounds(tabX + 256, tabY, 60, 20).build());

		int margin = 8;
		int gap = 8;
		previewW = Mth.clamp(width / 4, 80, 160);
		int rest = width - 2 * margin - 2 * gap - previewW;
		col1W = rest / 2;
		col2W = rest - col1W;

		col1X = 8;
		col2X = col1X + col1W + gap;
		col3X = col2X + col2W + gap;

		searchBox = new EditBox(font, col1X, 32, col1W, 20, Component.literal("Search"));
		searchBox.setValue(filter);
		searchBox.setResponder(value -> {
			filter = value.toLowerCase(Locale.ROOT);
			rebuildLists();
		});
		addRenderableWidget(searchBox);

		listTop = 70;
		listBottom = height - 60;
		int listHeight = listBottom - listTop;

		targetList = new TargetList(minecraft, col1W, listHeight, listTop, TARGET_ENTRY_HEIGHT);
		variantList = new VariantList(minecraft, col2W, listHeight, listTop, VARIANT_ENTRY_HEIGHT);
		targetList.setX(col1X);
		targetList.setY(listTop);
		targetList.setWidth(col1W);
		targetList.setHeight(listHeight);
		variantList.setX(col2X);
		variantList.setY(listTop);
		variantList.setWidth(col2W);
		variantList.setHeight(listHeight);
		addRenderableWidget(targetList);
		addRenderableWidget(variantList);

		addRenderableWidget(Button.builder(Component.translatable("historical_textures.apply"), b -> applyChanges())
				.bounds(width / 2 - 100, height - 52, 98, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> clearAll())
				.bounds(width / 2 + 4, height - 52, 98, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("historical_textures.credits"), b ->
						minecraft.setScreenAndShow(new CreditsScreen(this)))
				.bounds(8, height - 28, 120, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width - 108, height - 28, 100, 20).build());

		rebuildLists();
	}

	private void switchTab(Tab tab) {
		activeTab = tab;
		selectedTarget = null;
		selectedSoundEvent = null;
		emptyVariantHint = null;
		rebuildLists();
	}

	private void rebuildLists() {
		rebuildTargets();
		rebuildVariants();
	}

	private void rebuildTargets() {
		List<TargetList.Entry> targetEntries = new ArrayList<>();

		if (activeTab == Tab.SOUNDS) {
			SoundTargetEntry selectedTargetEntry = null;
			for (String event : TextureTargetRegistry.soundEvents()) {
				if (!matchesFilter(event)) {
					continue;
				}
				SoundTargetEntry entry = new SoundTargetEntry(event);
				targetEntries.add(entry);
				if (event.equals(selectedSoundEvent)) {
					selectedTargetEntry = entry;
				}
			}
			targetList.replaceEntries(targetEntries);
			if (selectedTargetEntry != null) {
				targetList.setSelected(selectedTargetEntry);
			}
			emptyListHint = targetEntries.isEmpty()
					? Component.literal("No sound events in catalog. Run ./gradlew :wiki-indexer:indexSounds build and restart.")
					: null;
			return;
		}

		List<TextureTarget> targets = switch (activeTab) {
			case BLOCKS -> TextureTargetRegistry.blockTargets();
			case ITEMS -> TextureTargetRegistry.itemTargets();
			case ENTITIES -> TextureTargetRegistry.entityTargets();
			case OTHER -> TextureTargetRegistry.otherTargets();
			default -> List.of();
		};

		TextureTargetEntry selectedTargetEntry = null;
		for (TextureTarget target : targets) {
			if (!matchesFilter(target.displayName()) && !matchesFilter(target.configKey())) {
				continue;
			}
			TextureTargetEntry entry = new TextureTargetEntry(target);
			targetEntries.add(entry);
			if (target.equals(selectedTarget)) {
				selectedTargetEntry = entry;
			}
		}

		targetList.replaceEntries(targetEntries);
		if (selectedTargetEntry != null) {
			targetList.setSelected(selectedTargetEntry);
		}
		emptyListHint = targetEntries.isEmpty()
				? Component.literal("No targets loaded. Check latest.log for catalog errors (look for \"Loaded catalog\").")
				: null;
	}

	private void rebuildVariants() {
		List<VariantList.Entry> variantEntries = new ArrayList<>();

		if (activeTab == Tab.SOUNDS) {
			if (selectedSoundEvent != null) {
				variantEntries.addAll(buildSoundVariantEntries(selectedSoundEvent));
				emptyVariantHint = null;
			} else if (targetList.children().isEmpty()) {
				emptyVariantHint = null;
			} else {
				emptyVariantHint = Component.literal("Select a sound event on the left to choose a variant.");
			}
			finishVariantList(variantEntries, selectedSoundEvent, true);
			return;
		}

		if (selectedTarget != null) {
			variantEntries.addAll(buildTextureVariantEntries(selectedTarget));
		} else {
			emptyVariantHint = null;
		}

		String configKey = selectedTarget != null ? selectedTarget.configKey() : null;
		finishVariantList(variantEntries, configKey, false);
	}

	private void finishVariantList(List<VariantList.Entry> entries, String configKey, boolean soundTab) {
		variantList.replaceEntries(entries);
		variantList.setScrollAmount(0);
		if (configKey == null) {
			return;
		}
		String chosen = soundTab ? ModConfig.get().getSoundChoice(configKey) : ModConfig.get().getTextureChoice(configKey);
		for (VariantList.Entry entry : entries) {
			if (entry instanceof VariantEntry variantEntry && variantEntry.matchesChoice(chosen)) {
				variantList.setSelected(entry);
				return;
			}
		}
	}

	private List<VariantList.Entry> buildTextureVariantEntries(TextureTarget target) {
		List<VariantList.Entry> entries = new ArrayList<>();
		List<TextureVariant> variants = HistoricalCatalog.get().textureVariantsForTarget(target.configKey());
		entries.add(new VariantEntry(Component.literal("Vanilla (default)"), null, null, null, target.configKey()));
		for (TextureVariant variant : variants) {
			CatalogThumbnailCache.preload(variant.id(), variant.assetPath());
			Component label = Component.literal(variant.versionLabel());
			Component subtitle = subtitleForVariant(variant.textureTags(), variant.label());
			entries.add(new VariantEntry(label, subtitle, variant.id(), variant, target.configKey()));
		}
		if (variants.isEmpty()) {
			emptyVariantHint = Component.literal("No historical variants in catalog for this target. Try Stone, Oak Planks, or Dirt.");
		} else {
			emptyVariantHint = null;
		}
		return entries;
	}

	private List<VariantList.Entry> buildSoundVariantEntries(String event) {
		List<VariantList.Entry> entries = new ArrayList<>();
		entries.add(new VariantEntry(Component.literal("Vanilla (default)"), null, null, null, event));
		for (SoundVariant variant : HistoricalCatalog.get().soundVariantsForEvent(event)) {
			Component label = Component.literal(variant.versionLabel());
			Component subtitle = subtitleForSound(variant);
			entries.add(new VariantEntry(label, subtitle, variant.id(), null, event));
		}
		return entries;
	}

	private Component subtitleForVariant(List<String> textureTags, String fallbackLabel) {
		if (textureTags != null && !textureTags.isEmpty()) {
			return Component.literal(String.join(" / ", textureTags));
		}
		return Component.literal(fallbackLabel);
	}

	private Component subtitleForSound(SoundVariant variant) {
		String version = variant.versionLabel();
		String wiki = variant.wikiFile() != null ? variant.wikiFile().replace('_', ' ') : "";
		if (!wiki.isBlank() && !wiki.equalsIgnoreCase(version)) {
			return Component.literal(wiki);
		}
		return null;
	}

	private boolean matchesFilter(String value) {
		return filter.isEmpty() || value.toLowerCase(Locale.ROOT).contains(filter);
	}

	private void applyChanges() {
		ModConfig.get().save();
		OverlayPackManager.ApplyResult result = OverlayPackManager.applyChoices(true);
		if (minecraft.player != null) {
			if (result.hadChoices() && result.texturesWritten() == 0 && result.soundsWritten() == 0) {
				minecraft.player.sendOverlayMessage(
						Component.literal("Apply failed: catalog assets missing. Rebuild the mod with the wiki indexer.")
				);
			} else if (result.texturesWritten() > 0 || result.soundsWritten() > 0) {
				minecraft.player.sendOverlayMessage(
						Component.literal("Applied " + result.texturesWritten() + " texture(s) and "
								+ result.soundsWritten() + " sound(s).")
				);
			}
		}
	}

	private void clearAll() {
		ModConfig.get().clearAll();
		ModConfig.get().save();
		selectedTarget = null;
		selectedSoundEvent = null;
		OverlayPackManager.applyChoices(true);
		rebuildLists();
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	private String fit(String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int dotsWidth = font.width("...");
		if (maxWidth <= dotsWidth) {
			return "...";
		}
		return font.plainSubstrByWidth(text, maxWidth - dotsWidth) + "...";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, Component.literal("Targets"), col1X, 58, TEXT_WHITE);
		graphics.text(font, Component.literal("Variants"), col2X, 58, TEXT_WHITE);
		graphics.text(font, Component.literal("Preview"), col3X, 58, TEXT_WHITE);

		int y = listTop + 6;
		if (emptyListHint != null) {
			y = graphics.textWithWordWrap(font, emptyListHint, col1X + 4, y, col1W - 8, TEXT_ERROR);
			y += 2;
		}
		if (HistoricalCatalog.get().textureVariants().isEmpty()) {
			y = graphics.textWithWordWrap(font, Component.literal("Catalog not loaded — rebuild mod with wiki-indexer output."), col1X + 4, y, col1W - 8, TEXT_ERROR);
			y += 2;
		}
		if (activeTab == Tab.SOUNDS && HistoricalCatalog.get().soundVariants().isEmpty()) {
			graphics.textWithWordWrap(font, Component.literal("No sounds in catalog — run ./gradlew :wiki-indexer:indexSounds build"), col1X + 4, y, col1W - 8, TEXT_ERROR);
		}

		if (emptyVariantHint != null) {
			int rowCount = variantList != null ? variantList.children().size() : 0;
			int vy = listTop + (rowCount > 0 ? rowCount * VARIANT_ENTRY_HEIGHT : 0) + 6;
			graphics.textWithWordWrap(font, emptyVariantHint, col2X + 4, vy, col2W - 8, TEXT_MUTED);
		}

		extractPreview(graphics, mouseX, mouseY, partialTick);
	}

	private void extractPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int boxX = col3X;
		int boxY = listTop;
		int boxW = previewW;
		int boxH = listBottom - listTop;

		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xC0000000);
		graphics.outline(boxX, boxY, boxW, boxH, 0xFF555555);

		VariantList.Entry selected = variantList != null ? variantList.getSelected() : null;
		TextureVariant textureVariant = (selected instanceof VariantEntry variantEntry) ? variantEntry.textureVariant : null;

		boolean renderedPreview = false;
		if (textureVariant != null) {
			Optional<CatalogThumbnailCache.CachedPreview> opt =
					CatalogThumbnailCache.get(textureVariant.id(), textureVariant.assetPath());
			if (opt.isPresent()) {
				CatalogThumbnailCache.CachedPreview cached = opt.get();
				int srcW = cached.width();
				int srcH = Math.min(cached.width(), cached.height());
				int maxDraw = Math.min(boxW, boxH) - 16;
				if (maxDraw > 0 && srcW > 0 && srcH > 0) {
					float scale = Math.min((float) maxDraw / srcW, (float) maxDraw / srcH);
					int drawW = Math.max(1, Math.round(srcW * scale));
					int drawH = Math.max(1, Math.round(srcH * scale));
					int drawX = boxX + (boxW - drawW) / 2;
					int drawY = boxY + (boxH - drawH) / 2;
					float vMax = cached.height() > 0
							? Math.min(1.0f, (float) srcH / cached.height())
							: 1.0f;
					graphics.blit(
							cached.textureId(),
							drawX,
							drawY,
							drawX + drawW,
							drawY + drawH,
							0.0f,
							1.0f,
							0.0f,
							vMax
					);
					renderedPreview = true;
				}
			}
		}

		if (!renderedPreview) {
			Component msg = activeTab == Tab.SOUNDS
					? Component.literal("No preview for sounds.")
					: Component.literal("Select a variant to preview.");
			graphics.textWithWordWrap(font, msg, boxX + 8, boxY + 12, boxW - 16, TEXT_MUTED);
		}
	}

	private enum Tab {
		BLOCKS,
		ITEMS,
		ENTITIES,
		OTHER,
		SOUNDS
	}

	private final class TextureTargetEntry extends TargetList.Entry {
		final TextureTarget target;

		private TextureTargetEntry(TextureTarget target) {
			this.target = target;
		}

		@Override
		public Component getNarration() {
			return Component.literal(target.displayName());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (targetList.getSelected() == this) {
				graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom(), SELECTED_ROW);
			}
			int maxW = getContentRight() - (getContentX() + 4) - 4;
			int variantCount = TextureTargetRegistry.variantCount(target.configKey());
			String line = target.displayName();
			if (variantCount > 0) {
				line += " (" + variantCount + ")";
			}
			graphics.text(font, fit(line, maxW), getContentX() + 4, getContentY() + 4, TEXT_WHITE);
			String choice = ModConfig.get().getTextureChoice(target.configKey());
			if (choice != null) {
				String subtitle = HistoricalCatalog.get().textureVariant(choice)
						.map(TextureVariant::versionLabel)
						.orElse(choice);
				graphics.text(font, fit(subtitle, maxW), getContentX() + 4, getContentY() + 14, TEXT_MUTED);
			} else if (variantCount == 0) {
				graphics.text(font, fit("no catalog variants", maxW), getContentX() + 4, getContentY() + 14, TEXT_MUTED);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			selectedTarget = target;
			selectedSoundEvent = null;
			targetList.setSelected(this);
			rebuildVariants();
			return true;
		}
	}

	private final class SoundTargetEntry extends TargetList.Entry {
		private final String soundEvent;

		private SoundTargetEntry(String soundEvent) {
			this.soundEvent = soundEvent;
		}

		@Override
		public Component getNarration() {
			return Component.literal(soundEvent);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (targetList.getSelected() == this) {
				graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom(), SELECTED_ROW);
			}
			int maxW = getContentRight() - (getContentX() + 4) - 4;
			int variantCount = HistoricalCatalog.get().soundVariantsForEvent(soundEvent).size();
			String line = soundEvent;
			if (variantCount > 0) {
				line += " (" + variantCount + ")";
			}
			graphics.text(font, fit(line, maxW), getContentX() + 4, getContentY() + 4, TEXT_WHITE);
			String choice = ModConfig.get().getSoundChoice(soundEvent);
			if (choice != null) {
				String choiceSub = HistoricalCatalog.get().soundVariant(choice)
						.map(SoundVariant::versionLabel)
						.orElse(choice);
				graphics.text(font, fit(choiceSub, maxW), getContentX() + 4, getContentY() + 14, TEXT_MUTED);
			} else if (variantCount == 0) {
				graphics.text(font, fit("no catalog variants", maxW), getContentX() + 4, getContentY() + 14, TEXT_MUTED);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			selectedSoundEvent = soundEvent;
			selectedTarget = null;
			targetList.setSelected(this);
			rebuildVariants();
			return true;
		}
	}

	private final class VariantEntry extends VariantList.Entry {
		private final Component label;
		private final Component subtitle;
		private final String variantId;
		private final TextureVariant textureVariant;
		private final String configKey;

		private VariantEntry(Component label, Component subtitle, String variantId, TextureVariant textureVariant, String configKey) {
			this.label = label;
			this.subtitle = subtitle;
			this.variantId = variantId;
			this.textureVariant = textureVariant;
			this.configKey = configKey;
		}

		@Override
		public Component getNarration() {
			return label;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			if (variantList.getSelected() == this) {
				graphics.fill(getContentX(), getContentY(), getContentRight(), getContentBottom(), SELECTED_ROW);
			}
			int previewX = getContentX() + 4;
			int previewY = getContentYMiddle() - PREVIEW_SIZE / 2;
			int textX = previewX;

			if (textureVariant != null) {
				textX = previewX + PREVIEW_SIZE + 4;
				Optional<CatalogThumbnailCache.CachedPreview> preview =
						CatalogThumbnailCache.get(textureVariant.id(), textureVariant.assetPath());
				if (preview.isPresent()) {
					CatalogThumbnailCache.CachedPreview cached = preview.get();
					float vMax = cached.height() > 0
							? Math.min(1.0f, (float) cached.width() / cached.height())
							: 1.0f;
					graphics.blit(
							cached.textureId(),
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
					graphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, PREVIEW_PLACEHOLDER);
				}
			}

			int maxW = getContentRight() - textX - 4;
			int color = variantId == null ? TEXT_ACCENT : (variantList.getSelected() == this ? TEXT_ACCENT : TEXT_WHITE);
			if (subtitle != null && variantId != null) {
				graphics.text(font, fit(label.getString(), maxW), textX, getContentY() + 4, color);
				graphics.text(font, fit(subtitle.getString(), maxW), textX, getContentY() + 16, TEXT_MUTED);
			} else {
				graphics.text(font, fit(label.getString(), maxW), textX, getContentYMiddle() - 4, color);
			}
		}

		boolean matchesChoice(String chosenId) {
			if (variantId == null) {
				return chosenId == null || chosenId.isEmpty();
			}
			return variantId.equals(chosenId);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			variantList.setSelected(this);
			if (activeTab == Tab.SOUNDS) {
				ModConfig.get().setSoundChoice(configKey, variantId);
			} else {
				ModConfig.get().setTextureChoice(configKey, variantId);
			}
			ModConfig.get().save();
			return true;
		}
	}

	private static class TargetList extends ObjectSelectionList<TargetList.Entry> {
		TargetList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
			super(minecraft, width, height, top, entryHeight);
		}

		@Override
		public int getRowWidth() {
			return width - 8;
		}

		abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
		}
	}

	private static class VariantList extends ObjectSelectionList<VariantList.Entry> {
		VariantList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
			super(minecraft, width, height, top, entryHeight);
		}

		@Override
		public int getRowWidth() {
			return width - 8;
		}

		abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
		}
	}
}
