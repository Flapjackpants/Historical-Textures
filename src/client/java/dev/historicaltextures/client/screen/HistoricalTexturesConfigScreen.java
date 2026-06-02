package dev.historicaltextures.client.screen;

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
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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

	private final Screen parent;
	private Tab activeTab = Tab.BLOCKS;
	private EditBox searchBox;
	private TargetList targetList;
	private VariantList variantList;
	private TextureTarget selectedTarget;
	private String selectedSoundEvent;
	private String filter = "";

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
		addRenderableWidget(Button.builder(Component.literal("Blocks"), b -> switchTab(Tab.BLOCKS)).bounds(tabX, tabY, 72, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Items"), b -> switchTab(Tab.ITEMS)).bounds(tabX + 76, tabY, 72, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Entities"), b -> switchTab(Tab.ENTITIES)).bounds(tabX + 152, tabY, 72, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Sounds"), b -> switchTab(Tab.SOUNDS)).bounds(tabX + 228, tabY, 72, 20).build());

		searchBox = new EditBox(font, tabX, tabY + 26, width / 2 - 16, 20, Component.literal("Search"));
		searchBox.setResponder(value -> {
			filter = value.toLowerCase(Locale.ROOT);
			rebuildLists();
		});
		addRenderableWidget(searchBox);

		int listTop = tabY + 52;
		int listHeight = height - listTop - 64;
		int leftWidth = width / 2 - 12;
		int rightX = width / 2 + 4;

		targetList = new TargetList(minecraft, leftWidth, listHeight, listTop, TARGET_ENTRY_HEIGHT);
		variantList = new VariantList(minecraft, leftWidth, listHeight, listTop, VARIANT_ENTRY_HEIGHT);
		targetList.setX(8);
		targetList.setY(listTop);
		targetList.setWidth(leftWidth);
		targetList.setHeight(listHeight);
		variantList.setX(rightX);
		variantList.setY(listTop);
		variantList.setWidth(leftWidth);
		variantList.setHeight(listHeight);
		addRenderableWidget(targetList);
		addRenderableWidget(variantList);

		addRenderableWidget(Button.builder(Component.translatable("historical_textures.apply"), b -> applyChanges())
				.bounds(width / 2 - 100, height - 52, 98, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> clearAll())
				.bounds(width / 2 + 4, height - 52, 98, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("historical_textures.credits"), b ->
						minecraft.setScreen(new CreditsScreen(this)))
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
		List<TargetList.Entry> targetEntries = new ArrayList<>();
		List<VariantList.Entry> variantEntries = new ArrayList<>();

		if (activeTab == Tab.SOUNDS) {
			for (String event : TextureTargetRegistry.soundEvents()) {
				if (!matchesFilter(event)) {
					continue;
				}
				targetEntries.add(new SoundTargetEntry(event));
			}
			if (selectedSoundEvent != null) {
				variantEntries.addAll(buildSoundVariantEntries(selectedSoundEvent));
			}
			targetList.replaceEntries(targetEntries);
			variantList.replaceEntries(variantEntries);
			variantList.setScrollAmount(0);
			emptyListHint = targetEntries.isEmpty()
					? Component.literal("No sound events in catalog. Run the wiki indexer or check latest.log.")
					: null;
			return;
		}

		List<TextureTarget> targets = switch (activeTab) {
			case BLOCKS -> TextureTargetRegistry.blockTargets();
			case ITEMS -> TextureTargetRegistry.itemTargets();
			case ENTITIES -> TextureTargetRegistry.entityTargets();
			default -> List.of();
		};

		for (TextureTarget target : targets) {
			if (!matchesFilter(target.displayName()) && !matchesFilter(target.configKey())) {
				continue;
			}
			targetEntries.add(new TextureTargetEntry(target));
		}

		if (selectedTarget != null) {
			variantEntries.addAll(buildTextureVariantEntries(selectedTarget));
		} else {
			emptyVariantHint = null;
		}

		targetList.replaceEntries(targetEntries);
		variantList.replaceEntries(variantEntries);
		variantList.setScrollAmount(0);
		emptyListHint = targetEntries.isEmpty()
				? Component.literal("No targets loaded. Check latest.log for catalog errors (look for \"Loaded catalog\").")
				: null;
	}

	private Component emptyListHint;
	private Component emptyVariantHint;

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
		if (variant.wikiFile() != null && !variant.wikiFile().isBlank()) {
			return Component.literal(variant.wikiFile().replace('_', ' '));
		}
		return Component.literal(variant.label());
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
		minecraft.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, Component.literal("Targets"), 8, 40, TEXT_WHITE);
		graphics.text(font, Component.literal("Variants"), width / 2 + 4, 40, TEXT_WHITE);
		if (emptyListHint != null) {
			graphics.text(font, emptyListHint, 8, height / 2, TEXT_ERROR);
		}
		if (emptyVariantHint != null) {
			graphics.text(font, emptyVariantHint, width / 2 + 4, height / 2, TEXT_MUTED);
		}
		if (HistoricalCatalog.get().textureVariants().isEmpty()) {
			graphics.text(font, Component.literal("Catalog not loaded — rebuild mod with wiki-indexer output."), 8, height / 2 + 12, TEXT_ERROR);
		}
	}

	private enum Tab {
		BLOCKS,
		ITEMS,
		ENTITIES,
		SOUNDS
	}

	private final class TextureTargetEntry extends TargetList.Entry {
		private final TextureTarget target;

		private TextureTargetEntry(TextureTarget target) {
			this.target = target;
		}

		@Override
		public Component getNarration() {
			return Component.literal(target.displayName());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int variantCount = TextureTargetRegistry.variantCount(target.configKey());
			String line = target.displayName();
			if (variantCount > 0) {
				line += " (" + variantCount + ")";
			}
			graphics.text(font, line, getContentX() + 4, getContentY() + 4, TEXT_WHITE);
			String choice = ModConfig.get().getTextureChoice(target.configKey());
			if (choice != null) {
				graphics.text(font, Component.literal(choice), getContentX() + 4, getContentY() + 14, TEXT_MUTED);
			} else if (variantCount == 0) {
				graphics.text(font, Component.literal("no catalog variants"), getContentX() + 4, getContentY() + 14, TEXT_MUTED);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			selectedTarget = target;
			selectedSoundEvent = null;
			rebuildLists();
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
			graphics.text(font, soundEvent, getContentX() + 4, getContentYMiddle() - 4, TEXT_WHITE);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			selectedSoundEvent = soundEvent;
			selectedTarget = null;
			rebuildLists();
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
			int previewX = getContentX() + 4;
			int previewY = getContentYMiddle() - PREVIEW_SIZE / 2;
			int textX = previewX;

			if (textureVariant != null) {
				textX = previewX + PREVIEW_SIZE + 4;
				Optional<CatalogThumbnailCache.CachedPreview> preview =
						CatalogThumbnailCache.get(textureVariant.id(), textureVariant.assetPath());
				if (preview.isPresent()) {
					CatalogThumbnailCache.CachedPreview cached = preview.get();
					graphics.blit(
							cached.textureId(),
							previewX,
							previewY,
							0,
							0,
							PREVIEW_SIZE,
							PREVIEW_SIZE,
							cached.width(),
							cached.height()
					);
				} else {
					graphics.fill(previewX, previewY, previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE, PREVIEW_PLACEHOLDER);
				}
			}

			int color = variantId == null ? TEXT_ACCENT : TEXT_WHITE;
			if (subtitle != null && variantId != null) {
				graphics.text(font, label, textX, getContentY() + 4, color);
				graphics.text(font, subtitle, textX, getContentY() + 16, TEXT_MUTED);
			} else {
				graphics.text(font, label, textX, getContentYMiddle() - 4, color);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (activeTab == Tab.SOUNDS) {
				ModConfig.get().setSoundChoice(configKey, variantId);
			} else {
				ModConfig.get().setTextureChoice(configKey, variantId);
			}
			ModConfig.get().save();
			rebuildLists();
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
