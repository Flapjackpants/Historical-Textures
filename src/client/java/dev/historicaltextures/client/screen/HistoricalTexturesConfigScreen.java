package dev.historicaltextures.client.screen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.historicaltextures.catalog.ClientCatalogLoader;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.LegacyBabies;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.client.CatalogThumbnailCache;
import dev.historicaltextures.client.SoundPreviewPlayer;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.config.Preset;
import dev.historicaltextures.config.TextureGroups;
import dev.historicaltextures.pack.LegacyBabyResolver;
import dev.historicaltextures.pack.OverlayPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.SharedConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class HistoricalTexturesConfigScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;
	private static final int TEXT_ACCENT = 0xFF55FF55;
	private static final int TEXT_ERROR = 0xFFFF5555;
	private static final int TARGET_ENTRY_HEIGHT = 26;
	private static final int VARIANT_ENTRY_HEIGHT = 32;
	private static final int PREVIEW_SIZE = 16;
	private static final int PREVIEW_PLACEHOLDER = 0xFF404040;
	private static final int SELECTED_ROW = 0x55335588;
	private static final float PREVIEW_SPIN_DEGREES_PER_SECOND = 30.0f;
	private static final float PREVIEW_DRAG_SENSITIVITY = 2.5f;
	private static final float PREVIEW_PITCH_LIMIT = 80.0f;
	private static final float PREVIEW_PLANE_PITCH_LIMIT = 30.0f;
	private static final float PREVIEW_DEFAULT_YAW = 30.0f;
	private static final float PREVIEW_DEFAULT_PITCH = 25.0f;
	private static final long PREVIEW_RESUME_DELAY_MS = 1000L;

	private final Screen parent;
	private static Preset lastApplied;
	private Tab activeTab = Tab.BLOCKS;
	private EditBox searchBox;
	private EntryList targetList;
	private EntryList variantList;
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

	private float previewYaw = PREVIEW_DEFAULT_YAW;
	private float previewPitch = PREVIEW_DEFAULT_PITCH;
	private boolean previewDragging;
	private long previewResumeAtMs;
	private long previewLastFrameMs;
	private TextureTarget previewTarget;
	private Button hoodButton;
	private Button soundPlayButton;
	private boolean timelineDragging;

	public HistoricalTexturesConfigScreen(Screen parent) {
		super(Component.translatable("screen.historical_textures.config"));
		this.parent = parent;
		if (lastApplied == null) {
			lastApplied = ModConfig.get().snapshot("");
		}
	}

	@Override
	protected void init() {
		ClientCatalogLoader.reload();
		TextureTargetRegistry.reload();
		previewLastFrameMs = Util.getMillis();

		int tabY = 8;
		int tabX = 8;
		int tabIndex = 0;
		for (Tab tab : Tab.values()) {
			addRenderableWidget(Button.builder(Component.literal(tab.label), b -> switchTab(tab))
					.bounds(tabX + 64 * tabIndex, tabY, 60, 20).build());
			tabIndex++;
		}

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
			targetList.setScrollAmount(0);
		});
		addRenderableWidget(searchBox);

		listTop = 70;
		listBottom = height - 60;
		int listHeight = listBottom - listTop;

		targetList = new EntryList(minecraft, col1W, listHeight, listTop, TARGET_ENTRY_HEIGHT);
		variantList = new EntryList(minecraft, col2W, listHeight, listTop, VARIANT_ENTRY_HEIGHT);
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

		addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> clearAll())
				.bounds(width / 2 - 49, height - 52, 98, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("historical_textures.credits"), b ->
						minecraft.setScreenAndShow(new CreditsScreen(this)))
				.bounds(8, height - 28, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Presets…"), b ->
						minecraft.setScreenAndShow(new PresetsScreen(this)))
				.bounds(width / 2 - 50, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width - 108, height - 28, 100, 20).build());

		hoodButton = Button.builder(Component.literal(""), b -> toggleHood())
				.bounds(col3X + 4, listBottom - 24, previewW - 8, 20)
				.build();
		hoodButton.visible = false;
		hoodButton.setTooltip(Tooltip.create(
				Component.literal("Old villagers had no hood. Applies when you leave this screen.")));
		addRenderableWidget(hoodButton);

		soundPlayButton = Button.builder(Component.literal("Play"), b -> SoundPreviewPlayer.toggle())
				.bounds(col3X + (previewW - 60) / 2, listTop + (listBottom - listTop) / 2 - 34, 60, 20)
				.build();
		soundPlayButton.visible = false;
		addRenderableWidget(soundPlayButton);

		rebuildLists();
	}

	private void switchTab(Tab tab) {
		SoundPreviewPlayer.stop();
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
		List<EntryList.Row> targetEntries = new ArrayList<>();

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
			if (!matchesTargetFilter(target)) {
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
		List<EntryList.Row> variantEntries = new ArrayList<>();

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
		} else if (targetList.children().isEmpty()) {
			emptyVariantHint = null;
		} else {
			emptyVariantHint = Component.literal("Select a target on the left to see its variants.");
		}

		String configKey = selectedTarget != null ? selectedTarget.configKey() : null;
		finishVariantList(variantEntries, configKey, false);
	}

	private void finishVariantList(List<EntryList.Row> entries, String configKey, boolean soundTab) {
		variantList.replaceEntries(entries);
		variantList.setScrollAmount(0);
		if (configKey == null) {
			return;
		}
		String chosen;
		if (soundTab) {
			List<String> soundChoices = ModConfig.get().getSoundChoices(configKey);
			chosen = soundChoices.isEmpty() ? null : soundChoices.get(0);
		} else {
			chosen = ModConfig.get().getTextureChoice(configKey);
		}
		for (EntryList.Row entry : entries) {
			if (entry instanceof VariantEntry variantEntry && variantEntry.matchesChoice(chosen)) {
				variantList.setSelected(entry);
				return;
			}
		}
	}

	private List<EntryList.Row> buildTextureVariantEntries(TextureTarget target) {
		if (TextureTargetRegistry.isBabyTarget(target)) {
			return buildBabyVariantEntries(target);
		}
		if (target.isGroup()) {
			return buildGroupVariantEntries(target);
		}
		List<EntryList.Row> entries = new ArrayList<>();
		List<TextureVariant> variants = HistoricalCatalog.get().textureVariantsForTarget(target.configKey());
		entries.add(vanillaEntry(target.configKey(), null, null));
		if (CatalogThumbnailCache.hasBuiltIn(target.resourcePath())) {
			entries.add(currentEntry(target, null, null));
		}
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

	private List<EntryList.Row> buildBabyVariantEntries(TextureTarget target) {
		List<EntryList.Row> entries = new ArrayList<>();
		entries.add(vanillaEntry(target.configKey(), Map.of(), target.members()));
		Map<String, String> legacyChoices = new LinkedHashMap<>();
		for (String member : target.members()) {
			legacyChoices.put(member, LegacyBabies.VARIANT_ID);
		}
		entries.add(new VariantEntry(
				Component.literal("Before baby update"),
				Component.literal("Pre-26.1 model, uses the adult texture"),
				LegacyBabies.VARIANT_ID,
				null,
				target.configKey(),
				legacyChoices,
				target.members()
		));
		emptyVariantHint = null;
		return entries;
	}

	private List<EntryList.Row> buildGroupVariantEntries(TextureTarget target) {
		List<EntryList.Row> entries = new ArrayList<>();
		List<TextureGroups.GroupVersion> versions = TextureTargetRegistry.groupVersions(target.configKey());
		entries.add(vanillaEntry(target.configKey(), Map.of(), target.members()));
		Map<String, String> currentChoices = new TreeMap<>();
		for (String member : target.members()) {
			if (CatalogThumbnailCache.hasBuiltIn(member)) {
				currentChoices.put(member, HistoricalCatalog.CURRENT_VARIANT_ID);
			}
		}
		if (!currentChoices.isEmpty()) {
			entries.add(currentEntry(target, currentChoices, target.members()));
		}
		for (TextureGroups.GroupVersion version : versions) {
			TextureVariant previewVariant = groupPreviewVariant(target, version);
			if (previewVariant != null) {
				CatalogThumbnailCache.preload(previewVariant.id(), previewVariant.assetPath());
			}
			entries.add(new VariantEntry(
					Component.literal(version.label()),
					Component.literal(version.choices().size() + " textures"),
					version.label(),
					previewVariant,
					target.configKey(),
					version.choices(),
					target.members()
			));
		}
		if (versions.isEmpty()) {
			emptyVariantHint = Component.literal("No historical variants in catalog for this block.");
		} else {
			emptyVariantHint = null;
		}
		return entries;
	}

	private TextureVariant groupPreviewVariant(TextureTarget target, TextureGroups.GroupVersion version) {
		String representative = version.choices().get(target.resourcePath());
		if (representative == null) {
			for (String member : target.members()) {
				representative = version.choices().get(member);
				if (representative != null) {
					break;
				}
			}
		}
		if (representative == null) {
			return null;
		}
		return HistoricalCatalog.get().textureVariant(representative).orElse(null);
	}

	private List<EntryList.Row> buildSoundVariantEntries(String event) {
		List<EntryList.Row> entries = new ArrayList<>();
		entries.add(vanillaEntry(event, null, null));
		for (SoundVariant variant : HistoricalCatalog.get().soundVariantsForEvent(event)) {
			Component label = Component.literal(variant.versionLabel());
			Component subtitle = subtitleForSound(variant);
			entries.add(new VariantEntry(label, subtitle, variant.id(), null, event));
		}
		return entries;
	}

	private VariantEntry vanillaEntry(String configKey, Map<String, String> groupChoices, List<String> groupMembers) {
		return new VariantEntry(
				Component.literal("Vanilla (default)"),
				null,
				null,
				null,
				configKey,
				groupChoices,
				groupMembers
		);
	}

	private VariantEntry currentEntry(TextureTarget target, Map<String, String> groupChoices, List<String> groupMembers) {
		return new VariantEntry(
				Component.literal("Current (" + SharedConstants.getCurrentVersion().name() + ")"),
				Component.literal("Built-in texture"),
				HistoricalCatalog.CURRENT_VARIANT_ID,
				null,
				target.configKey(),
				groupChoices,
				groupMembers
		);
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

	private boolean matchesTargetFilter(TextureTarget target) {
		if (matchesFilter(target.displayName()) || matchesFilter(target.configKey())) {
			return true;
		}
		if (target.isGroup()) {
			for (String member : target.members()) {
				if (matchesFilter(member)) {
					return true;
				}
			}
		}
		return false;
	}

	private String singleSubtitle(TextureTarget target) {
		String choice = ModConfig.get().getTextureChoice(target.configKey());
		if (choice == null) {
			return null;
		}
		return HistoricalCatalog.get().textureVariant(choice)
				.map(TextureVariant::versionLabel)
				.orElse(choice);
	}

	private String groupSubtitle(TextureTarget target) {
		for (TextureGroups.GroupVersion version : TextureTargetRegistry.groupVersions(target.configKey())) {
			if (TextureGroups.matches(version, target.members(), ModConfig.get()::getTextureChoice)) {
				return version.label();
			}
		}
		for (String member : target.members()) {
			if (ModConfig.get().getTextureChoice(member) != null) {
				return "Custom";
			}
		}
		return null;
	}

	static void applyAndReport(Minecraft minecraft) {
		lastApplied = ModConfig.get().snapshot("");
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
		minecraft.setScreenAndShow(new ConfirmScreen(yes -> {
			if (yes) {
				ModConfig.get().clearAll();
				ModConfig.get().save();
				selectedTarget = null;
				selectedSoundEvent = null;
			}
			minecraft.setScreenAndShow(this);
			if (yes) {
				rebuildLists();
			}
		}, Component.literal("Clear all selections?"),
				Component.literal("Every texture and sound selection will be removed. Changes apply when you leave this screen."),
				Component.literal("Clear all"), CommonComponents.GUI_CANCEL));
	}

	@Override
	public void onClose() {
		SoundPreviewPlayer.stop();
		if (!ModConfig.get().snapshot("").equals(lastApplied)) {
			ModConfig.get().save();
			minecraft.setScreenAndShow(parent);
			applyAndReport(minecraft);
		} else {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public void removed() {
		super.removed();
		SoundPreviewPlayer.stop();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		updateHoodButton();
		extractPreview(graphics, mouseX, mouseY, partialTick);
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
	}

	private void extractPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (soundPlayButton != null) {
			soundPlayButton.visible = false;
		}
		int boxX = col3X;
		int boxY = listTop;
		int boxW = previewW;
		int boxH = listBottom - listTop;

		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xC0000000);
		graphics.outline(boxX, boxY, boxW, boxH, 0xFF555555);

		CatalogThumbnailCache.CachedPreview preview = currentPreviewTexture();
		VariantPreviewRenderer.Shape shape = effectivePreviewShape(preview);
		resetPreviewMotion(shape);
		updatePreviewMotion(shape);

		if (shape == VariantPreviewRenderer.Shape.NONE || preview == null) {
			if (activeTab == Tab.SOUNDS) {
				extractSoundPreview(graphics, boxX, boxY, boxW, boxH);
				return;
			}
			graphics.textWithWordWrap(font, Component.literal("Select a variant to preview."), boxX + 8, boxY + 12, boxW - 16, TEXT_MUTED);
			return;
		}

		int inset = 4;
		int innerX0 = boxX + inset;
		int innerY0 = boxY + inset;
		int innerX1 = boxX + boxW - inset;
		int innerY1 = boxY + boxH - inset;
		Optional<ModelPart> part = switch (shape) {
			case CUBE -> Optional.of(VariantPreviewRenderer.cube(preview.width(), preview.height()));
			case PLANE -> Optional.of(VariantPreviewRenderer.plane(preview.width(), preview.height()));
			case CROSS -> Optional.of(VariantPreviewRenderer.cross(preview.width(), preview.height()));
			case ENTITY -> entityPreviewPart();
			default -> Optional.empty();
		};
		part.ifPresent(modelPart -> VariantPreviewRenderer.extract(
				graphics,
				shape,
				modelPart,
				preview.textureId(),
				innerX0,
				innerY0,
				innerX1,
				innerY1,
				previewPitch,
				previewYaw
		));
		if (shape == VariantPreviewRenderer.Shape.FLAT) {
			extractFlatPreview(graphics, preview, boxX, boxY, boxW, boxH);
		}
	}

	private void extractSoundPreview(GuiGraphicsExtractor graphics, int boxX, int boxY, int boxW, int boxH) {
		VariantEntry entry = selectedVariantEntry();
		SoundVariant variant = entry != null && entry.variantId != null
				? HistoricalCatalog.get().soundVariant(entry.variantId).orElse(null)
				: null;
		if (variant == null) {
			SoundPreviewPlayer.stop();
			graphics.textWithWordWrap(font, Component.literal("Select a historical sound to preview it."),
					boxX + 8, boxY + 12, boxW - 16, TEXT_MUTED);
			return;
		}
		if (!SoundPreviewPlayer.load(entry.variantId, variant.assetPath())) {
			graphics.textWithWordWrap(font, Component.literal("Can't play this sound."),
					boxX + 8, boxY + 12, boxW - 16, TEXT_ERROR);
			return;
		}

		soundPlayButton.setMessage(Component.literal(SoundPreviewPlayer.isPlaying() ? "Pause" : "Play"));
		soundPlayButton.visible = true;

		int[] timeline = soundTimelineRect();
		graphics.fill(timeline[0], timeline[1], timeline[0] + timeline[2], timeline[1] + 6, 0xFF303030);
		float duration = SoundPreviewPlayer.durationSeconds();
		float position = duration > 0.0f ? SoundPreviewPlayer.positionSeconds() / duration : 0.0f;
		int fillWidth = Math.round(timeline[2] * Mth.clamp(position, 0.0f, 1.0f));
		if (fillWidth > 0) {
			graphics.fill(timeline[0], timeline[1], timeline[0] + fillWidth, timeline[1] + 6, 0xFF55FF55);
		}
		int knobX = Math.min(timeline[0] + fillWidth, timeline[0] + timeline[2] - 2);
		graphics.fill(knobX, timeline[1], knobX + 2, timeline[1] + 6, 0xFFFFFFFF);

		String time = formatSoundTime(SoundPreviewPlayer.positionSeconds())
				+ " / " + formatSoundTime(duration);
		graphics.text(font, Component.literal(time),
				boxX + boxW / 2 - font.width(time) / 2, timeline[1] + 10, TEXT_MUTED);
	}

	private int[] soundTimelineRect() {
		int middle = listTop + (listBottom - listTop) / 2;
		return new int[]{col3X + 8, middle - 6, previewW - 16};
	}

	private boolean soundPlayerVisible() {
		return activeTab == Tab.SOUNDS && soundPlayButton != null && soundPlayButton.visible;
	}

	private boolean isInsideTimeline(double mouseX, double mouseY) {
		if (!soundPlayerVisible()) {
			return false;
		}
		int[] timeline = soundTimelineRect();
		return mouseX >= timeline[0] && mouseX < timeline[0] + timeline[2]
				&& mouseY >= timeline[1] - 2 && mouseY <= timeline[1] + 8;
	}

	private void seekTimeline(double mouseX) {
		int[] timeline = soundTimelineRect();
		if (timeline[2] <= 0) {
			return;
		}
		SoundPreviewPlayer.seek((float) ((mouseX - timeline[0]) / timeline[2]));
	}

	private static String formatSoundTime(float seconds) {
		int total = Math.max(0, Math.round(seconds));
		return total / 60 + ":" + String.format(Locale.ROOT, "%02d", total % 60);
	}

	private void extractFlatPreview(GuiGraphicsExtractor graphics, CatalogThumbnailCache.CachedPreview preview, int boxX, int boxY, int boxW, int boxH) {
		int srcW = preview.width();
		int srcH = Math.min(preview.width(), preview.height());
		int maxDraw = Math.min(boxW, boxH) - 16;
		if (maxDraw <= 0 || srcW <= 0 || srcH <= 0) {
			return;
		}
		float scale = Math.min((float) maxDraw / srcW, (float) maxDraw / srcH);
		int drawW = Math.max(1, Math.round(srcW * scale));
		int drawH = Math.max(1, Math.round(srcH * scale));
		int drawX = boxX + (boxW - drawW) / 2;
		int drawY = boxY + (boxH - drawH) / 2;
		float vMax = preview.height() > 0
				? Math.min(1.0f, (float) srcH / preview.height())
				: 1.0f;
		graphics.blit(
				preview.textureId(),
				drawX,
				drawY,
				drawX + drawW,
				drawY + drawH,
				0.0f,
				1.0f,
				0.0f,
				vMax
		);
	}

	private VariantPreviewRenderer.Shape previewShape() {
		if (activeTab == Tab.SOUNDS) {
			return VariantPreviewRenderer.Shape.NONE;
		}
		if (selectedBabyEntry().isPresent()) {
			return VariantPreviewRenderer.Shape.ENTITY;
		}
		return VariantPreviewRenderer.shapeFor(selectedTarget);
	}

	private VariantPreviewRenderer.Shape effectivePreviewShape(CatalogThumbnailCache.CachedPreview preview) {
		VariantPreviewRenderer.Shape shape = previewShape();
		if (selectedBabyEntry().isPresent()) {
			return shape;
		}
		if (shape != VariantPreviewRenderer.Shape.ENTITY || preview == null || selectedTarget == null) {
			return shape;
		}
		VariantEntry entry = selectedVariantEntry();
		boolean hasLegacyModel = entry != null && entry.textureVariant != null
				&& VariantPreviewRenderer.legacyModel(selectedTarget, entry.textureVariant).isPresent();
		if (hasLegacyModel) {
			return shape;
		}
		Optional<CatalogThumbnailCache.CachedPreview> vanilla =
				CatalogThumbnailCache.getVanilla(selectedTarget.resourcePath());
		if (vanilla.isPresent() && vanilla.get().height() > 0 && preview.height() > 0) {
			float previewAspect = (float) preview.width() / preview.height();
			float vanillaAspect = (float) vanilla.get().width() / vanilla.get().height();
			if (Math.abs(previewAspect - vanillaAspect) > 0.02f) {
				return VariantPreviewRenderer.Shape.FLAT;
			}
		}
		return shape;
	}

	private Optional<net.minecraft.client.model.geom.ModelPart> entityPreviewPart() {
		Optional<LegacyBabies.Entry> babyEntry = selectedBabyEntry();
		if (babyEntry.isPresent()) {
			String cacheKey = babyCacheKey(babyEntry.get());
			Optional<JsonObject> jem = LegacyBabyResolver.babyJem(babyEntry.get(), ModConfig.get());
			Optional<net.minecraft.client.model.geom.ModelPart> babyModel =
					jem.flatMap(jemJson -> VariantPreviewRenderer.legacyBabyModel(cacheKey, jemJson));
			if (babyModel.isPresent()) {
				return babyModel;
			}
		}
		VariantEntry entry = selectedVariantEntry();
		if (entry != null && entry.textureVariant != null && selectedTarget != null) {
			Optional<net.minecraft.client.model.geom.ModelPart> legacy =
					VariantPreviewRenderer.legacyModel(selectedTarget, entry.textureVariant);
			if (legacy.isPresent()) {
				return legacy;
			}
		}
		return VariantPreviewRenderer.entityModel(selectedTarget);
	}

	private VariantEntry selectedVariantEntry() {
		EntryList.Row selected = variantList != null ? variantList.getSelected() : null;
		return selected instanceof VariantEntry variantEntry ? variantEntry : null;
	}

	private Optional<LegacyBabies.Entry> selectedBabyEntry() {
		if (selectedTarget == null || !TextureTargetRegistry.isBabyTarget(selectedTarget)) {
			return Optional.empty();
		}
		VariantEntry entry = selectedVariantEntry();
		if (entry == null || !LegacyBabies.VARIANT_ID.equals(entry.variantId)) {
			return Optional.empty();
		}
		return LegacyBabies.forBabyTexture(selectedTarget.resourcePath());
	}

	private static String babyCacheKey(LegacyBabies.Entry entry) {
		String choice = ModConfig.get().getTextureChoice(entry.adultTexture());
		return entry.emfModel() + "|" + (choice != null ? choice : "vanilla");
	}

	private CatalogThumbnailCache.CachedPreview currentPreviewTexture() {
		VariantEntry variantEntry = selectedVariantEntry();
		if (variantEntry != null && variantEntry.textureVariant != null) {
			Optional<CatalogThumbnailCache.CachedPreview> opt = Optional.empty();
			if (selectedTarget != null) {
				Optional<String> legacySource =
						VariantPreviewRenderer.legacyTextureSource(selectedTarget, variantEntry.textureVariant);
				if (legacySource.isPresent()) {
					opt = CatalogThumbnailCache.getLegacy(legacySource.get());
				}
			}
			if (opt.isEmpty()) {
				boolean stripHood = ModConfig.get().hideVillagerHood()
						&& selectedTarget != null
						&& selectedTarget.resourcePath() != null
						&& OverlayPackManager.isVillagerHoodTarget(
								selectedTarget.resourcePath().replaceFirst("^minecraft:", ""));
				opt = CatalogThumbnailCache.get(
						variantEntry.textureVariant.id(),
						variantEntry.textureVariant.assetPath(),
						stripHood
				);
			}
			return opt.orElse(null);
		}
		if (variantEntry != null
				&& HistoricalCatalog.CURRENT_VARIANT_ID.equals(variantEntry.variantId)
				&& selectedTarget != null) {
			Optional<CatalogThumbnailCache.CachedPreview> opt =
					CatalogThumbnailCache.getBuiltIn(selectedTarget.resourcePath());
			return opt.orElse(null);
		}
		Optional<LegacyBabies.Entry> babyEntry = selectedBabyEntry();
		if (babyEntry.isPresent()) {
			String cacheKey = babyCacheKey(babyEntry.get());
			Optional<CatalogThumbnailCache.CachedPreview> opt = LegacyBabyResolver
					.adultTextureBytes(babyEntry.get(), ModConfig.get())
					.flatMap(bytes -> CatalogThumbnailCache.getBytes(cacheKey, bytes));
			return opt.orElse(null);
		}
		if (selectedTarget != null) {
			Optional<CatalogThumbnailCache.CachedPreview> opt =
					CatalogThumbnailCache.getVanilla(selectedTarget.resourcePath());
			return opt.orElse(null);
		}
		return null;
	}

	private void resetPreviewMotion(VariantPreviewRenderer.Shape shape) {
		if (selectedTarget == previewTarget) {
			return;
		}
		previewTarget = selectedTarget;
		previewYaw = PREVIEW_DEFAULT_YAW;
		previewPitch = defaultPitch(shape);
		previewLastFrameMs = Util.getMillis();
	}

	private float defaultPitch(VariantPreviewRenderer.Shape shape) {
		return switch (shape) {
			case CUBE, CROSS, ENTITY -> PREVIEW_DEFAULT_PITCH;
			case PLANE, FLAT, NONE -> 0.0f;
		};
	}

	private void updatePreviewMotion(VariantPreviewRenderer.Shape shape) {
		long now = Util.getMillis();
		float elapsedSeconds = Mth.clamp((now - previewLastFrameMs) / 1000.0f, 0.0f, 0.1f);
		previewLastFrameMs = now;
		if (!isThreeDimensional(shape) || previewDragging) {
			return;
		}
		if (now < previewResumeAtMs) {
			return;
		}
		previewYaw += PREVIEW_SPIN_DEGREES_PER_SECOND * elapsedSeconds;
	}

	private static boolean isThreeDimensional(VariantPreviewRenderer.Shape shape) {
		return shape != VariantPreviewRenderer.Shape.NONE && shape != VariantPreviewRenderer.Shape.FLAT;
	}

	private boolean hoodApplies() {
		if (activeTab != Tab.ENTITIES || selectedTarget == null || selectedTarget.resourcePath() == null) {
			return false;
		}
		return OverlayPackManager.isVillagerHoodTarget(
				selectedTarget.resourcePath().replaceFirst("^minecraft:", ""));
	}

	private void updateHoodButton() {
		if (hoodButton == null) {
			return;
		}
		boolean applies = hoodApplies();
		hoodButton.visible = applies;
		hoodButton.active = applies;
		if (applies) {
			hoodButton.setMessage(Component.literal(
					ModConfig.get().hideVillagerHood() ? "Hood: Hidden" : "Hood: Shown"));
		}
	}

	private void toggleHood() {
		ModConfig.get().setHideVillagerHood(!ModConfig.get().hideVillagerHood());
		ModConfig.get().save();
		updateHoodButton();
	}

	private boolean isInsidePreview(double mouseX, double mouseY) {
		if (hoodButton != null && hoodButton.visible
				&& mouseX >= hoodButton.getX() && mouseX < hoodButton.getX() + hoodButton.getWidth()
				&& mouseY >= hoodButton.getY() && mouseY < hoodButton.getY() + hoodButton.getHeight()) {
			return false;
		}
		int boxX = col3X;
		int boxY = listTop;
		int boxW = previewW;
		int boxH = listBottom - listTop;
		return mouseX >= boxX && mouseX < boxX + boxW && mouseY >= boxY && mouseY < boxY + boxH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
				&& isInsideTimeline(event.x(), event.y())) {
			timelineDragging = true;
			seekTimeline(event.x());
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
				&& isThreeDimensional(previewShape())
				&& isInsidePreview(event.x(), event.y())) {
			previewDragging = true;
			previewLastFrameMs = Util.getMillis();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (timelineDragging) {
			seekTimeline(event.x());
			return true;
		}
		if (previewDragging) {
			previewYaw += (float) dragX * PREVIEW_DRAG_SENSITIVITY;
			float limit = previewShape() == VariantPreviewRenderer.Shape.PLANE
					? PREVIEW_PLANE_PITCH_LIMIT
					: PREVIEW_PITCH_LIMIT;
			previewPitch = Mth.clamp(previewPitch + (float) dragY * PREVIEW_DRAG_SENSITIVITY, -limit, limit);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (timelineDragging) {
			timelineDragging = false;
			return true;
		}
		if (previewDragging) {
			previewDragging = false;
			previewResumeAtMs = Util.getMillis() + PREVIEW_RESUME_DELAY_MS;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void extractTargetRow(GuiGraphicsExtractor graphics, EntryList.Row row, String name, int variantCount,
			String subtitle) {
		if (targetList.getSelected() == row) {
			graphics.fill(row.getContentX(), row.getContentY(), row.getContentRight(), row.getContentBottom(), SELECTED_ROW);
		}
		int maxW = row.getContentRight() - (row.getContentX() + 4) - 4;
		String line = variantCount > 0 ? name + " (" + variantCount + ")" : name;
		graphics.text(font, ColumnList.fit(font, line, maxW), row.getContentX() + 4, row.getContentY() + 2, TEXT_WHITE);
		if (subtitle != null) {
			graphics.text(font, ColumnList.fit(font, subtitle, maxW), row.getContentX() + 4, row.getContentY() + 12, TEXT_MUTED);
		} else if (variantCount == 0) {
			graphics.text(font, ColumnList.fit(font, "no catalog variants", maxW), row.getContentX() + 4, row.getContentY() + 12, TEXT_MUTED);
		}
	}

	private enum Tab {
		BLOCKS("Blocks"),
		ITEMS("Items"),
		ENTITIES("Entities"),
		OTHER("Other"),
		SOUNDS("Sounds");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private final class TextureTargetEntry extends EntryList.Row {
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
			extractTargetRow(graphics, this, target.displayName(),
					TextureTargetRegistry.variantCount(target.configKey()),
					target.isGroup() ? groupSubtitle(target) : singleSubtitle(target));
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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

	private final class SoundTargetEntry extends EntryList.Row {
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
			List<String> choices = ModConfig.get().getSoundChoices(soundEvent);
			String subtitle;
			if (choices.isEmpty()) {
				subtitle = null;
			} else if (choices.size() == 1) {
				String choice = choices.get(0);
				subtitle = HistoricalCatalog.get().soundVariant(choice).map(SoundVariant::versionLabel).orElse(choice);
			} else {
				subtitle = choices.size() + " sounds";
			}
			extractTargetRow(graphics, this, soundEvent,
					HistoricalCatalog.get().soundVariantsForEvent(soundEvent).size(), subtitle);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			selectedSoundEvent = soundEvent;
			selectedTarget = null;
			SoundPreviewPlayer.stop();
			targetList.setSelected(this);
			rebuildVariants();
			return true;
		}
	}

	private final class VariantEntry extends EntryList.Row {
		private final Component label;
		private final Component subtitle;
		private final String variantId;
		private final TextureVariant textureVariant;
		private final String configKey;
		private final Map<String, String> groupChoices;
		private final List<String> groupMembers;

		private VariantEntry(Component label, Component subtitle, String variantId, TextureVariant textureVariant, String configKey) {
			this(label, subtitle, variantId, textureVariant, configKey, null, null);
		}

		private VariantEntry(Component label, Component subtitle, String variantId, TextureVariant textureVariant,
				String configKey, Map<String, String> groupChoices, List<String> groupMembers) {
			this.label = label;
			this.subtitle = subtitle;
			this.variantId = variantId;
			this.textureVariant = textureVariant;
			this.configKey = configKey;
			this.groupChoices = groupChoices;
			this.groupMembers = groupMembers;
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

			if (textureVariant != null || HistoricalCatalog.CURRENT_VARIANT_ID.equals(variantId)) {
				textX = previewX + PREVIEW_SIZE + 4;
				Optional<CatalogThumbnailCache.CachedPreview> preview = textureVariant != null
						? CatalogThumbnailCache.get(textureVariant.id(), textureVariant.assetPath())
						: CatalogThumbnailCache.getBuiltIn(selectedTarget != null ? selectedTarget.resourcePath() : configKey);
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
				graphics.text(font, ColumnList.fit(font, label.getString(), maxW), textX, getContentY() + 4, color);
				graphics.text(font, ColumnList.fit(font, subtitle.getString(), maxW), textX, getContentY() + 16, TEXT_MUTED);
			} else {
				graphics.text(font, ColumnList.fit(font, label.getString(), maxW), textX, getContentYMiddle() - 4, color);
			}
		}

		boolean matchesChoice(String chosenId) {
			if (groupChoices != null) {
				return TextureGroups.matches(
						new TextureGroups.GroupVersion("", 0, groupChoices),
						groupMembers != null ? groupMembers : List.of(),
						ModConfig.get()::getTextureChoice
				);
			}
			if (variantId == null) {
				return chosenId == null || chosenId.isEmpty();
			}
			return variantId.equals(chosenId);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			variantList.setSelected(this);
			if (groupChoices != null) {
				if (groupMembers != null) {
					for (String member : groupMembers) {
						ModConfig.get().setTextureChoice(member, groupChoices.get(member));
					}
				}
				ModConfig.get().save();
				rebuildVariants();
				return true;
			}
			if (activeTab == Tab.SOUNDS) {
				ModConfig.get().setSoundChoices(configKey, variantId == null ? List.of() : List.of(variantId));
			} else {
				ModConfig.get().setTextureChoice(configKey, variantId);
			}
			ModConfig.get().save();
			return true;
		}
	}

	private static final class EntryList extends ColumnList<EntryList.Row> {
		EntryList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
			super(minecraft, width, height, top, entryHeight);
		}

		abstract static class Row extends ObjectSelectionList.Entry<Row> {
		}
	}
}
