package dev.historicaltextures.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.LegacyModels;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetKind;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.client.CatalogThumbnailCache;
import dev.historicaltextures.client.SoundPreviewPlayer;
import dev.historicaltextures.client.screen.HistoricalTexturesConfigScreen;
import dev.historicaltextures.config.ModConfig;
import dev.historicaltextures.config.Preset;
import dev.historicaltextures.config.PresetStore;
import dev.historicaltextures.pack.OverlayPackManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigScreenClientGameTest implements FabricClientGameTest {
	private static final List<FaceDiagnosticView> PREVIEW_VIEWS = List.of(
			new FaceDiagnosticView("front", 0.0f, 0.0f),
			new FaceDiagnosticView("right", 90.0f, 0.0f),
			new FaceDiagnosticView("back", 180.0f, 0.0f),
			new FaceDiagnosticView("left", 270.0f, 0.0f),
			new FaceDiagnosticView("top", 0.0f, 80.0f),
			new FaceDiagnosticView("bottom", 0.0f, -80.0f)
	);

	private static final List<FaceDiagnosticTarget> FACE_DIAGNOSTIC_TARGETS = List.of(
			new FaceDiagnosticTarget(
					"minecraft:textures/entity/cow/cow_temperate.png",
					"minecraft:cow",
					"minecraft:temperate",
					EntityTypes.COW,
					List.of("cow_texture_je1_png", "cow_texture_je5_png", "cow_texture_png"),
					null
			),
			new FaceDiagnosticTarget(
					"minecraft:textures/entity/piglin/zombified_piglin.png",
					"minecraft:zombified_piglin",
					null,
					EntityTypes.ZOMBIFIED_PIGLIN,
					List.of("zombie_pigman_texture_je0_png", "zombie_pigman_texture_je2_png"),
					null
			),
			new FaceDiagnosticTarget(
					"minecraft:textures/entity/pig/pig_temperate.png",
					"minecraft:pig",
					"minecraft:temperate",
					EntityTypes.PIG,
					List.of("pig_texture_je1_png"),
					null
			),
			new FaceDiagnosticTarget(
					"minecraft:textures/entity/rabbit/rabbit_brown.png",
					"minecraft:rabbit",
					null,
					EntityTypes.RABBIT,
					List.of("brown_rabbit_texture_je2_png"),
					"RabbitType:0"
			),
			new FaceDiagnosticTarget(
					"minecraft:textures/entity/rabbit/rabbit_caerbannog.png",
					"minecraft:rabbit",
					null,
					EntityTypes.RABBIT,
					List.of("killer_bunny_texture_je1_png"),
					"RabbitType:99"
			)
	);

	private record FaceDiagnosticView(String name, float yaw, float pitch) {
	}

	private record FaceDiagnosticTarget(
			String configKey,
			String entityId,
			String entityVariant,
			EntityTypeTest<Entity, ? extends Entity> entityType,
			List<String> variantIds,
			String summonNbt
	) {
	}

	private static final double DIAGNOSTIC_PLAYER_EYE_HEIGHT = 1.62;

	private static final String VILLAGER_PROFESSION_KEY =
			"minecraft:textures/entity/villager/profession/farmer.png";
	private static final String VILLAGER_FARMER_VARIANT = "farmer_texture_je1_png";
	private static final String VILLAGER_LIBRARIAN_KEY =
			"minecraft:textures/entity/villager/profession/librarian.png";
	private static final String VILLAGER_LIBRARIAN_VARIANT = "librarian_texture_je1_png";

	private static final FaceDiagnosticTarget VILLAGER_HOOD_TARGET = new FaceDiagnosticTarget(
			VILLAGER_LIBRARIAN_KEY,
			"minecraft:villager",
			null,
			EntityTypes.VILLAGER,
			List.of(),
			null
	);

	@Override
	public void runTest(ClientGameTestContext context) {
		int initialWidth = context.computeOnClient(client -> client.getWindow().getWidth());
		int initialHeight = context.computeOnClient(client -> client.getWindow().getHeight());
		List<String> failures = new ArrayList<>();
		List<Path> screenshots = new ArrayList<>();

		// Phase A: from title screen
		runPhase(context, "A", failures, screenshots, initialWidth, initialHeight);

		// Phase B: inside world
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			runPhase(context, "B", failures, screenshots, initialWidth, initialHeight);
			runFaceDiagnostic(context, sp, failures, screenshots);
			runHoodWorldDiagnostic(context, sp, failures, screenshots);
		}

		if (!screenshots.isEmpty()) {
			Path dir = screenshots.get(0).getParent().toAbsolutePath();
			String dirLine = "[HT-GAMETEST] SCREENSHOT_DIR: " + dir;
			HistoricalTextures.LOGGER.info(dirLine);
			System.out.println(dirLine);
			for (Path p : screenshots) {
				String fileLine = "[HT-GAMETEST] SCREENSHOT_FILE: " + p.getFileName();
				HistoricalTextures.LOGGER.info(fileLine);
				System.out.println(fileLine);
			}
		}

		if (!failures.isEmpty()) {
			throw new AssertionError("Gametest assertion failures (" + failures.size() + "):\n" + String.join("\n", failures));
		}
	}

	private void runPhase(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots,
			int initialWidth,
			int initialHeight
	) {
		ModConfig.get().clearAll();
		ModConfig.get().save();
		Set<String> initialChoices = Set.copyOf(ModConfig.get().textureChoices().keySet());

		// 01 open
		String step01 = phase + "_01_open";
		context.setScreen(() -> new HistoricalTexturesConfigScreen(null));
		context.waitTicks(5);
		List<AbstractSelectionList<?>> lists01 = getLists(context);
		boolean hasLists01 = lists01.size() >= 2;
		check(failures, step01, "lists_present", hasLists01, "found=" + lists01.size());
		AbstractSelectionList<?> targets01 = hasLists01 ? lists01.get(0) : null;
		int targets01Size = targets01 != null ? targets01.children().size() : 0;
		check(failures, step01, "target_list_non_empty", targets01Size > 0, "size=" + targets01Size);
		int blockTargetsSize = context.computeOnClient(client -> TextureTargetRegistry.blockTargets().size());
		String logLine01 = String.format("[HT-GAMETEST] %s block_targets_size INFO size=%d", step01, blockTargetsSize);
		HistoricalTextures.LOGGER.info(logLine01);
		System.out.println(logLine01);

		String step01Row0Narration = targets01 != null ? getRowNarration(targets01, 0) : "";

		// names_clean check
		String firstOffender = null;
		List<List<TextureTarget>> allTargetLists = context.computeOnClient(client -> List.of(
				TextureTargetRegistry.blockTargets(),
				TextureTargetRegistry.itemTargets(),
				TextureTargetRegistry.entityTargets(),
				TextureTargetRegistry.otherTargets()
		));
		for (List<TextureTarget> list : allTargetLists) {
			for (TextureTarget target : list) {
				String name = target.displayName();
				if (name.contains(":") || name.contains(".png") || name.contains("_")) {
					firstOffender = target.configKey() + " -> " + name;
					break;
				}
			}
			if (firstOffender != null) {
				break;
			}
		}
		check(failures, step01, "names_clean", firstOffender == null, firstOffender == null ? "all clean" : "offender: " + firstOffender);

		// names_unique check
		String duplicateName = null;
		for (List<TextureTarget> list : allTargetLists) {
			Set<String> seen = new java.util.HashSet<>();
			for (TextureTarget target : list) {
				String lower = target.displayName().toLowerCase(Locale.ROOT);
				if (!seen.add(lower)) {
					duplicateName = target.configKey() + " -> " + target.displayName();
					break;
				}
			}
			if (duplicateName != null) {
				break;
			}
		}
		check(failures, step01, "names_unique", duplicateName == null, duplicateName == null ? "all unique" : "duplicate: " + duplicateName);

		// entity_qualifiers check
		List<String> entityNames = context.computeOnClient(client ->
				TextureTargetRegistry.entityTargets().stream().map(TextureTarget::displayName).toList()
		);
		boolean hasCowCold = entityNames.contains("Cow (Cold)");
		boolean hasCowWarm = entityNames.contains("Cow (Warm)");
		boolean hasPigCold = entityNames.contains("Pig (Cold)");
		boolean allQualifiersPresent = hasCowCold && hasCowWarm && hasPigCold;
		check(failures, step01, "entity_qualifiers", allQualifiersPresent,
				"Cow(Cold)=" + hasCowCold + " Cow(Warm)=" + hasCowWarm + " Pig(Cold)=" + hasPigCold);

		// scrollbar_inside check
		boolean scrollbarsInside = true;
		StringBuilder sbDetails = new StringBuilder();
		for (int i = 0; i < Math.min(2, lists01.size()); i++) {
			AbstractSelectionList<?> list = lists01.get(i);
			int rowLeft = list.getRowLeft();
			int rowRight = list.getRowRight();
			int listX = list.getX();
			int listW = list.getWidth();
			boolean inside = rowLeft >= listX && rowRight <= listX + listW;
			if (!inside) {
				scrollbarsInside = false;
				sbDetails.append("list[").append(i).append("]: rowLeft=").append(rowLeft)
						.append(" listX=").append(listX).append(" rowRight=").append(rowRight)
						.append(" listRight=").append(listX + listW).append("; ");
			}
		}
		if (lists01.size() < 2) {
			scrollbarsInside = false;
			sbDetails.append("expected at least 2 lists, found ").append(lists01.size());
		}
		check(failures, step01, "scrollbar_inside", scrollbarsInside,
				scrollbarsInside ? "both lists inside" : sbDetails.toString().trim());

		recordScreenshot(context, step01, screenshots);

		// 02 click_target
		String step02 = phase + "_02_click_target";
		List<AbstractSelectionList<?>> lists02 = getLists(context);
		AbstractSelectionList<?> targets02 = !lists02.isEmpty() ? lists02.get(0) : null;
		if (targets02 != null && !targets02.children().isEmpty()) {
			clickRow(context, targets02, 0);
		} else {
			check(failures, step02, "target_list_clickable", false, "targets is empty or null");
		}
		lists02 = getLists(context);
		targets02 = !lists02.isEmpty() ? lists02.get(0) : null;
		AbstractSelectionList<?> variants02 = lists02.size() >= 2 ? lists02.get(1) : null;

		int variants02Size = variants02 != null ? variants02.children().size() : 0;
		check(failures, step02, "variant_list_non_empty", variants02Size > 0, "size=" + variants02Size);

		Object selected02 = targets02 != null ? targets02.getSelected() : null;
		boolean selectedValid02 = targets02 != null && selected02 != null && targets02.children().contains(selected02);
		check(failures, step02, "target_selected_valid", selectedValid02, "selected=" + selected02);

		Object focused02 = targets02 != null ? targets02.getFocused() : null;
		boolean focusedValid02 = targets02 != null && (focused02 == null || targets02.children().contains(focused02));
		check(failures, step02, "target_focused_valid", focusedValid02, "focused=" + focused02);

		recordScreenshot(context, step02, screenshots);

		// 03 click_variant
		String step03 = phase + "_03_click_variant";
		List<AbstractSelectionList<?>> lists03 = getLists(context);
		AbstractSelectionList<?> variants03 = lists03.size() >= 2 ? lists03.get(1) : null;
		int variantCount = variants03 != null ? variants03.children().size() : 0;
		int variantTargetIdx = variantCount > 1 ? 1 : 0;
		if (variants03 != null && variantCount > 0) {
			clickRow(context, variants03, variantTargetIdx);
		} else {
			check(failures, step03, "variant_clickable", false, "variants is empty or null");
		}
		lists03 = getLists(context);
		variants03 = lists03.size() >= 2 ? lists03.get(1) : null;
		Object selected03 = variants03 != null ? variants03.getSelected() : null;
		boolean contained03 = variants03 != null && selected03 != null && variants03.children().contains(selected03);
		boolean matchesIndex03 = variants03 != null && variantTargetIdx < variants03.children().size() && selected03 == variants03.children().get(variantTargetIdx);
		check(failures, step03, "variant_selected_valid", contained03 && matchesIndex03, "selected=" + selected03 + " expectedIndex=" + variantTargetIdx + " contained=" + contained03 + " matchesIndex=" + matchesIndex03);

		Map<String, String> currentChoices = ModConfig.get().textureChoices();
		boolean choiceSaved = false;
		String choiceDetails = "empty";
		for (Map.Entry<String, String> entry : currentChoices.entrySet()) {
			if (!initialChoices.contains(entry.getKey()) && entry.getValue() != null) {
				String key = entry.getKey();
				String val = entry.getValue();
				List<String> validIds = HistoricalCatalog.get().textureVariantsForTarget(key)
						.stream().map(TextureVariant::id).toList();
				if (validIds.contains(val) || HistoricalCatalog.CURRENT_VARIANT_ID.equals(val)) {
					choiceSaved = true;
					choiceDetails = "key=" + key + " value=" + val;
					break;
				} else {
					choiceDetails = "key=" + key + " value=" + val + " not in " + validIds;
				}
			}
		}
		check(failures, step03, "variant_choice_saved", choiceSaved, choiceDetails);

		recordScreenshot(context, step03, screenshots);

		// 04 click_target_again
		String step04 = phase + "_04_click_target_again";
		List<AbstractSelectionList<?>> lists04 = getLists(context);
		AbstractSelectionList<?> targets04 = !lists04.isEmpty() ? lists04.get(0) : null;
		int targetCount04 = targets04 != null ? targets04.children().size() : 0;
		int targetIdx04 = targetCount04 > 1 ? 1 : 0;
		if (targets04 != null && targetCount04 > 0) {
			clickRow(context, targets04, targetIdx04);
		} else {
			check(failures, step04, "target_clickable", false, "targets is empty or null");
		}
		lists04 = getLists(context);
		targets04 = !lists04.isEmpty() ? lists04.get(0) : null;
		AbstractSelectionList<?> variants04 = lists04.size() >= 2 ? lists04.get(1) : null;

		int variants04Size = variants04 != null ? variants04.children().size() : 0;
		check(failures, step04, "variant_list_non_empty", variants04Size > 0, "size=" + variants04Size);

		Object selected04 = targets04 != null ? targets04.getSelected() : null;
		boolean selectedValid04 = targets04 != null && selected04 != null && targets04.children().contains(selected04);
		check(failures, step04, "target_selected_valid", selectedValid04, "selected=" + selected04);

		Object focused04 = targets04 != null ? targets04.getFocused() : null;
		boolean focusedValid04 = targets04 != null && (focused04 == null || targets04.children().contains(focused04));
		check(failures, step04, "target_focused_valid", focusedValid04, "focused=" + focused04);

		recordScreenshot(context, step04, screenshots);

		// 05 tab_entities
		String step05 = phase + "_05_tab_entities";
		List<AbstractSelectionList<?>> listsBeforeTab = getLists(context);
		AbstractSelectionList<?> targetsBeforeTab = !listsBeforeTab.isEmpty() ? listsBeforeTab.get(0) : null;
		String blocksTabRow0Narration = targetsBeforeTab != null ? getRowNarration(targetsBeforeTab, 0) : "";

		Button entitiesButton = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) return null;
			for (var child : screen.children()) {
				if (child instanceof Button b && "Entities".equals(b.getMessage().getString())) {
					return b;
				}
			}
			return null;
		});

		if (entitiesButton != null) {
			int btnX = context.computeOnClient(client -> entitiesButton.getX() + entitiesButton.getWidth() / 2);
			int btnY = context.computeOnClient(client -> entitiesButton.getY() + entitiesButton.getHeight() / 2);
			clickGui(context, btnX, btnY);
			context.waitTicks(5);
		} else {
			check(failures, step05, "entities_button_found", false, "Entities button not found");
		}

		List<AbstractSelectionList<?>> lists05 = getLists(context);
		AbstractSelectionList<?> targets05 = !lists05.isEmpty() ? lists05.get(0) : null;
		int targets05Size = targets05 != null ? targets05.children().size() : 0;
		check(failures, step05, "target_list_non_empty", targets05Size > 0, "size=" + targets05Size);

		String entitiesTabRow0Narration = targets05 != null ? getRowNarration(targets05, 0) : "";
		boolean tabChanged = !entitiesTabRow0Narration.isEmpty() && !entitiesTabRow0Narration.equals(blocksTabRow0Narration);
		check(failures, step05, "tab_changed", tabChanged, "blocksRow0=" + blocksTabRow0Narration + " entitiesRow0=" + entitiesTabRow0Narration);

		recordScreenshot(context, step05, screenshots);

		// 05b tab_back_blocks
		String step05b = phase + "_05b_tab_back_blocks";
		Button blocksButton = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) return null;
			for (var child : screen.children()) {
				if (child instanceof Button b && "Blocks".equals(b.getMessage().getString())) {
					return b;
				}
			}
			return null;
		});

		if (blocksButton != null) {
			int btnX = context.computeOnClient(client -> blocksButton.getX() + blocksButton.getWidth() / 2);
			int btnY = context.computeOnClient(client -> blocksButton.getY() + blocksButton.getHeight() / 2);
			clickGui(context, btnX, btnY);
			context.waitTicks(5);
		} else {
			check(failures, step05b, "blocks_button_found", false, "Blocks button not found");
		}

		List<AbstractSelectionList<?>> lists05b = getLists(context);
		AbstractSelectionList<?> targets05b = !lists05b.isEmpty() ? lists05b.get(0) : null;
		String backBlocksRow0Narration = targets05b != null ? getRowNarration(targets05b, 0) : "";
		boolean backEquals = !backBlocksRow0Narration.isEmpty() && backBlocksRow0Narration.equals(step01Row0Narration);
		check(failures, step05b, "tab_back_blocks", backEquals, "expected=" + step01Row0Narration + " actual=" + backBlocksRow0Narration);

		recordScreenshot(context, step05b, screenshots);

		runPreviewSteps(context, phase, failures, screenshots);
		runHoodSteps(context, phase, failures, screenshots);
		runCurrentVariantSteps(context, phase, failures, screenshots);
		runPresetSteps(context, phase, failures, screenshots);
		runCakeGroupSteps(context, phase, failures, screenshots);
		runSoundSteps(context, phase, failures, screenshots);

		// 05j search_scroll_top
		String stepScroll = phase + "_05j_search_scroll_top";
		double maxScroll = context.computeOnClient(client -> {
			AbstractSelectionList<?> list = firstSelectionList(client);
			if (list == null) {
				return -1.0;
			}
			int max = list.maxScrollAmount();
			if (max > 0) {
				list.setScrollAmount(max);
			}
			return (double) max;
		});
		context.waitTicks(2);
		double scrollAfterMax = targetListScrollAmount(context);
		if (maxScroll <= 0 || scrollAfterMax <= 0.0) {
			String infoLine = "[HT-GAMETEST] " + stepScroll + " search_scroll_max INFO max=" + maxScroll + " scroll=" + scrollAfterMax;
			HistoricalTextures.LOGGER.info(infoLine);
			System.out.println(infoLine);
		}
		check(failures, stepScroll, "search_scroll_max", maxScroll <= 0 || scrollAfterMax > 0.0,
				"max=" + maxScroll + " scroll=" + scrollAfterMax);
		setSearch(context, "stone");
		context.waitTicks(2);
		double scrollAfterSearch = targetListScrollAmount(context);
		check(failures, stepScroll, "search_scroll_top", scrollAfterSearch == 0.0,
				"scroll=" + scrollAfterSearch);
		recordScreenshot(context, stepScroll, screenshots);
		setSearch(context, "");
		context.waitTicks(2);

		// 06 resize
		String step06 = phase + "_06_resize";
		EditBox searchBoxBeforeResize = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) return null;
			for (var child : screen.children()) {
				if (child instanceof EditBox eb) {
					return eb;
				}
			}
			return null;
		});

		if (searchBoxBeforeResize != null) {
			int sbX = context.computeOnClient(client -> searchBoxBeforeResize.getX() + searchBoxBeforeResize.getWidth() / 2);
			int sbY = context.computeOnClient(client -> searchBoxBeforeResize.getY() + searchBoxBeforeResize.getHeight() / 2);
			clickGui(context, sbX, sbY);
			context.waitTicks(2);
			context.getInput().typeChars("stone");
			context.waitTicks(2);
		}

		context.getInput().resizeWindow(1280, 720);
		context.waitTicks(5);

		EditBox searchBoxAfterResize = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) return null;
			for (var child : screen.children()) {
				if (child instanceof EditBox eb) {
					return eb;
				}
			}
			return null;
		});

		String queryValue = searchBoxAfterResize != null ? context.computeOnClient(client -> searchBoxAfterResize.getValue()) : null;
		boolean searchValEquals = "stone".equals(queryValue);

		List<AbstractSelectionList<?>> lists06 = getLists(context);
		AbstractSelectionList<?> targets06 = !lists06.isEmpty() ? lists06.get(0) : null;
		AbstractSelectionList<?> variants06 = lists06.size() >= 2 ? lists06.get(1) : null;

		int targets06Size = targets06 != null ? targets06.children().size() : 0;
		check(failures, step06, "target_list_non_empty", targets06Size > 0, "size=" + targets06Size);

		boolean allContainStone = targets06 != null && !targets06.children().isEmpty();
		String failedNarration = null;
		if (targets06 != null) {
			for (int i = 0; i < targets06.children().size(); i++) {
				String narr = getRowNarration(targets06, i).toLowerCase(Locale.ROOT);
				if (!narr.contains("stone")) {
					allContainStone = false;
					failedNarration = getRowNarration(targets06, i);
					break;
				}
			}
		}
		boolean searchSynced = searchValEquals && allContainStone;
		check(failures, step06, "search_synced", searchSynced, "boxValue=" + queryValue + " allContainStone=" + allContainStone + (failedNarration != null ? " failedOn=" + failedNarration : ""));

		context.runOnClient(client -> {
			if (searchBoxAfterResize != null) {
				searchBoxAfterResize.setValue("");
			}
		});
		context.waitTicks(2);

		String variantState = variants06 != null ? ("size=" + variants06.children().size() + " selected=" + variants06.getSelected()) : "variants=null";
		String logLine06 = String.format("[HT-GAMETEST] %s variant_list_state INFO %s", step06, variantState);
		HistoricalTextures.LOGGER.info(logLine06);
		System.out.println(logLine06);

		recordScreenshot(context, step06, screenshots);

		// 07 done
		String step07 = phase + "_07_done";
		Button doneButton = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) return null;
			for (var child : screen.children()) {
				if (child instanceof Button b && CommonComponents.GUI_DONE.getString().equals(b.getMessage().getString())) {
					return b;
				}
			}
			return null;
		});

		if (doneButton != null) {
			int btnX = context.computeOnClient(client -> doneButton.getX() + doneButton.getWidth() / 2);
			int btnY = context.computeOnClient(client -> doneButton.getY() + doneButton.getHeight() / 2);
			clickGui(context, btnX, btnY);
			context.waitTicks(5);
		} else {
			check(failures, step07, "done_button_found", false, "Done button not found");
		}

		Screen finalScreen = context.computeOnClient(client -> client.gui.screen());
		boolean closed = !(finalScreen instanceof HistoricalTexturesConfigScreen);
		check(failures, step07, "screen_closed", closed, "currentScreen=" + (finalScreen != null ? finalScreen.getClass().getName() : "null"));

		recordScreenshot(context, step07, screenshots);

		// Restore window size
		context.getInput().resizeWindow(initialWidth, initialHeight);
		context.waitTicks(2);

		ModConfig.get().clearAll();
		ModConfig.get().save();
	}

	private void runFaceDiagnostic(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<String> failures,
			List<Path> screenshots
	) {
		runPreviewFaceDiagnostic(context, screenshots);
		runWorldFaceDiagnostic(context, sp, failures, screenshots);
		boolean rabbitModelPresent = rabbitLegacyModelPresent(context);
		check(failures, "diag_rabbit", "rabbit_legacy_model", rabbitModelPresent,
				"legacyModel(brown JE2) present=" + rabbitModelPresent);
	}

	private void runPreviewFaceDiagnostic(ClientGameTestContext context, List<Path> screenshots) {
		context.setScreen(() -> new HistoricalTexturesConfigScreen(null));
		context.waitTicks(5);
		clickButton(context, "Entities");
		context.waitTicks(3);
		setPreviewField(context, "previewResumeAtMs", Long.MAX_VALUE);

		for (FaceDiagnosticTarget target : FACE_DIAGNOSTIC_TARGETS) {
			for (String variantId : target.variantIds()) {
				if (!selectDiagnosticTarget(context, target.configKey())) {
					logDiagnosticWarn("diag_preview target_missing target=" + target.configKey());
					continue;
				}
				int variantIndex = findVariantRowById(context, variantId);
				if (variantIndex < 0) {
					logDiagnosticWarn("diag_preview variant_missing target=" + target.configKey() + " variant=" + variantId);
					continue;
				}
				clickVariantRow(context, variantIndex);
				context.waitTicks(5);
				for (FaceDiagnosticView view : PREVIEW_VIEWS) {
					setPreviewAngles(context, view.yaw(), view.pitch());
					context.waitTicks(2);
					float actualYaw = previewYaw(context);
					float actualPitch = previewPitch(context);
					recordScreenshot(context, "diag_preview_" + variantId + "_" + view.name(), screenshots);
					logDiagnosticInfo(String.format(
							Locale.ROOT,
							"[HT-GAMETEST] diag_preview %s %s INFO yaw=%.2f pitch=%.2f shape=%s",
							variantId,
							view.name(),
							actualYaw,
							actualPitch,
							previewShapeName(context)
					));
				}
			}
		}

		context.runOnClient(client -> client.setScreenAndShow(null));
		context.waitTicks(2);
	}

	private void runWorldFaceDiagnostic(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<String> failures,
			List<Path> screenshots
	) {
		sp.getServer().runCommand("time set noon");
		try {
			sp.getServer().runCommand("gamerule advance_time false");
		} catch (RuntimeException exception) {
			logDiagnosticWarn("diag_world gamerule_failed command=gamerule advance_time false error=" + exception.getMessage());
		}
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("fill -9 100 -9 10 100 10 minecraft:stone");
		sp.getServer().runCommand("fill -8 101 -8 9 108 9 minecraft:air");
		sp.getServer().runCommand("gamemode spectator @p");
		context.waitTicks(5);

		boolean hudWasHidden = hideDiagnosticHud(context);
		try {
			for (FaceDiagnosticTarget target : FACE_DIAGNOSTIC_TARGETS) {
				for (String variantId : target.variantIds()) {
					ModConfig.get().clearAll();
					ModConfig.get().setTextureChoice(target.configKey(), variantId);
					ModConfig.get().save();
					OverlayPackManager.ApplyResult result = applyDiagnosticOverlay(context);
					String step = "diag_world_" + variantId;
					check(
							failures,
							step,
							"diag_overlay_applied",
							result.texturesWritten() > 0,
							"texturesWritten=" + result.texturesWritten() + " soundsWritten=" + result.soundsWritten() + " skipped=" + result.skipped()
					);

					sp.getServer().runCommand("kill @e[type=!player]");
					sp.getServer().runCommand(
							"summon " + target.entityId() + " 0.5 101 0.5 " + diagnosticSummonNbt(target)
					);
					context.waitTicks(10);
					double[] anchor = diagnosticMobAnchor(sp, target);
					if (anchor == null) {
						logDiagnosticWarn("diag_world mob_missing target=" + target.configKey() + " variant=" + variantId);
						continue;
					}
					captureDiagnosticWorldViews(context, sp, screenshots, variantId, anchor);
				}
			}

			ModConfig.get().clearAll();
			ModConfig.get().save();
			applyDiagnosticOverlay(context);
		} finally {
			restoreDiagnosticHud(context, hudWasHidden);
		}
	}

	private boolean selectDiagnosticTarget(ClientGameTestContext context, String configKey) {
		setSearch(context, configKey);
		int rowIndex = findDiagnosticTargetRow(context, configKey);
		if (rowIndex < 0) {
			return false;
		}
		ensureRowVisible(context, 0, rowIndex);
		List<AbstractSelectionList<?>> lists = getLists(context);
		AbstractSelectionList<?> targets = !lists.isEmpty() ? lists.get(0) : null;
		if (targets == null || rowIndex >= targets.children().size()) {
			return false;
		}
		clickRow(context, targets, rowIndex);
		context.waitTicks(3);
		return true;
	}

	private int findDiagnosticTargetRow(ClientGameTestContext context, String configKey) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return -1;
			}
			Object listObject;
			try {
				listObject = screenFieldValue(screen, "targetList");
			} catch (ReflectiveOperationException exception) {
				return -1;
			}
			if (!(listObject instanceof AbstractSelectionList<?> targetList)) {
				return -1;
			}
			for (int index = 0; index < targetList.children().size(); index++) {
				Object entry = targetList.children().get(index);
				try {
					var field = entry.getClass().getDeclaredField("target");
					field.setAccessible(true);
					if (field.get(entry) instanceof TextureTarget target && configKey.equals(target.configKey())) {
						return index;
					}
				} catch (ReflectiveOperationException ignored) {
				}
			}
			return -1;
		});
		return result instanceof Integer index ? index : -1;
	}

	private void setPreviewField(ClientGameTestContext context, String name, Object value) {
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return;
			}
			try {
				var field = screen.getClass().getDeclaredField(name);
				field.setAccessible(true);
				field.set(screen, value);
			} catch (ReflectiveOperationException ignored) {
			}
		});
	}

	private void setPreviewAngles(ClientGameTestContext context, float yaw, float pitch) {
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return;
			}
			try {
				var yawField = screen.getClass().getDeclaredField("previewYaw");
				yawField.setAccessible(true);
				yawField.setFloat(screen, yaw);
				var pitchField = screen.getClass().getDeclaredField("previewPitch");
				pitchField.setAccessible(true);
				pitchField.setFloat(screen, pitch);
			} catch (ReflectiveOperationException ignored) {
			}
		});
	}

	private float previewPitch(ClientGameTestContext context) {
		Object value = previewField(context, "previewPitch");
		return value instanceof Float pitch ? pitch : 0.0f;
	}

	private OverlayPackManager.ApplyResult applyDiagnosticOverlay(ClientGameTestContext context) {
		OverlayPackManager.ApplyResult result = context.computeOnClient(client -> OverlayPackManager.applyChoices(true));
		context.waitFor(client -> client.gui.overlay() == null);
		context.waitTicks(20);
		return result;
	}

	private boolean hideDiagnosticHud(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			boolean wasHidden = client.gui.hud.isHidden();
			if (!wasHidden) {
				client.gui.hud.toggle();
			}
			return wasHidden;
		});
	}

	private void restoreDiagnosticHud(ClientGameTestContext context, boolean wasHidden) {
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden() != wasHidden) {
				client.gui.hud.toggle();
			}
		});
	}

	private static String diagnosticSummonNbt(FaceDiagnosticTarget target) {
		StringBuilder nbt = new StringBuilder("{NoAI:1b,Rotation:[0f,0f]");
		if (target.entityVariant() != null) {
			nbt.append(",variant:\"").append(target.entityVariant()).append('"');
		}
		if (target.summonNbt() != null) {
			nbt.append(',').append(target.summonNbt());
		}
		return nbt.append('}').toString();
	}

	private double[] diagnosticMobAnchor(TestSingleplayerContext sp, FaceDiagnosticTarget target) {
		return sp.getServer().computeOnServer(server -> {
			var level = server.overworld();
			AABB box = new AABB(-1.0, 100.0, -1.0, 2.0, 104.0, 2.0);
			List<? extends Entity> entities = level.getEntities(target.entityType(), box, entity -> true);
			Entity nearest = null;
			double nearestDistance = Double.MAX_VALUE;
			for (Entity entity : entities) {
				double distance = entity.distanceToSqr(0.5, 101.0, 0.5);
				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearest = entity;
				}
			}
			if (nearest == null) {
				return null;
			}
			var eye = nearest.getEyePosition();
			return new double[]{eye.x(), eye.y(), eye.z(), nearest.getBoundingBox().maxY};
		});
	}

	private void captureDiagnosticWorldViews(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<Path> screenshots,
			String variantId,
			double[] anchor
	) {
		double x = anchor[0];
		double y = anchor[1];
		double z = anchor[2];
		double topY = anchor[3] + 2.0;
		double cameraY = y - DIAGNOSTIC_PLAYER_EYE_HEIGHT;
		double cameraTopY = topY - DIAGNOSTIC_PLAYER_EYE_HEIGHT;
		captureDiagnosticWorldView(context, sp, screenshots, variantId, "front", x, cameraY, z + 3.0, 180.0f, 0.0f);
		captureDiagnosticWorldView(context, sp, screenshots, variantId, "back", x, cameraY, z - 3.0, 0.0f, 0.0f);
		captureDiagnosticWorldView(context, sp, screenshots, variantId, "left", x + 3.0, cameraY, z, 90.0f, 0.0f);
		captureDiagnosticWorldView(context, sp, screenshots, variantId, "right", x - 3.0, cameraY, z, 270.0f, 0.0f);
		captureDiagnosticWorldView(context, sp, screenshots, variantId, "top", x, cameraTopY, z, 0.0f, 90.0f);
	}

	private void captureDiagnosticWorldView(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<Path> screenshots,
			String variantId,
			String view,
			double x,
			double y,
			double z,
			float yaw,
			float pitch
	) {
		sp.getServer().runCommand(String.format(
				Locale.ROOT,
				"tp @p %.3f %.3f %.3f %.1f %.1f",
				x,
				y,
				z,
				yaw,
				pitch
		));
		context.waitTicks(10);
		recordScreenshot(context, "diag_world_" + variantId + "_" + view, screenshots);
	}

	private void logDiagnosticInfo(String message) {
		HistoricalTextures.LOGGER.info(message);
		System.out.println(message);
	}

	private void logDiagnosticWarn(String message) {
		HistoricalTextures.LOGGER.warn(message);
		System.out.println(message);
	}

	private void runHoodSteps(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots
	) {
		ModConfig.get().setHideVillagerHood(true);
		ModConfig.get().save();

		// hood_button_hidden_default: Blocks tab, block target selected
		String stepHidden = phase + "_hood_button_hidden_default";
		clickButton(context, "Blocks");
		context.waitTicks(3);
		setSearch(context, "");
		context.waitTicks(2);
		clickTargetRow(context, failures, 0, stepHidden, "hood_block_target");
		context.waitTicks(2);
		String hiddenState = hoodButtonState(context);
		check(failures, stepHidden, "hood_button_hidden_default",
				!hiddenState.startsWith("visible"), "state=" + hiddenState);

		// hood_button_visible: Entities tab, villager profession target
		String stepVisible = phase + "_hood_button_visible";
		clickButton(context, "Entities");
		context.waitTicks(3);
		boolean farmerSelected = selectDiagnosticTarget(context, VILLAGER_PROFESSION_KEY);
		context.waitTicks(2);
		String visibleState = hoodButtonState(context);
		check(failures, stepVisible, "hood_button_visible",
				farmerSelected
						&& visibleState.startsWith("visible")
						&& visibleState.contains("Hood: Hidden")
						&& ModConfig.get().hideVillagerHood(),
				"selected=" + farmerSelected + " state=" + visibleState
						+ " config=" + ModConfig.get().hideVillagerHood());

		// hood_toggle: flip off then back on
		String stepToggle = phase + "_hood_toggle";
		clickHoodButton(context);
		context.waitTicks(2);
		String toggledOff = hoodButtonState(context);
		check(failures, stepToggle, "hood_toggle",
				toggledOff.contains("Hood: Shown") && !ModConfig.get().hideVillagerHood(),
				"state=" + toggledOff + " config=" + ModConfig.get().hideVillagerHood());
		clickHoodButton(context);
		context.waitTicks(2);
		String toggledOn = hoodButtonState(context);
		check(failures, stepToggle, "hood_toggle_restore",
				toggledOn.contains("Hood: Hidden") && ModConfig.get().hideVillagerHood(),
				"state=" + toggledOn + " config=" + ModConfig.get().hideVillagerHood());
		recordScreenshot(context, stepToggle, screenshots);

		// hood_mcmeta_written
		String stepMcmeta = phase + "_hood_mcmeta";
		Path mcmeta = OverlayPackManager.overlayDirectory().resolve(
				"assets/minecraft/textures/entity/villager/profession/farmer.png.mcmeta");
		try {
			ModConfig.get().clearAll();
			ModConfig.get().setTextureChoice(VILLAGER_PROFESSION_KEY, VILLAGER_FARMER_VARIANT);
			ModConfig.get().setHideVillagerHood(true);
			ModConfig.get().save();
			generateOverlay(context);
			String content = Files.exists(mcmeta) ? Files.readString(mcmeta) : "";
			check(failures, stepMcmeta, "hood_mcmeta_written",
					content.contains("\"hat\": \"full\""),
					"exists=" + Files.exists(mcmeta) + " content=" + content.replace('\n', ' '));

			ModConfig.get().setHideVillagerHood(false);
			ModConfig.get().save();
			generateOverlay(context);
			check(failures, stepMcmeta, "hood_mcmeta_absent_when_shown",
					!Files.exists(mcmeta), "exists=" + Files.exists(mcmeta));
		} catch (Exception exception) {
			check(failures, stepMcmeta, "hood_mcmeta_written", false,
					"error=" + exception);
		}

		runHoodPixelChecks(context, phase, failures);

		// Restore config and overlay
		ModConfig.get().clearAll();
		ModConfig.get().setHideVillagerHood(true);
		ModConfig.get().save();
		generateOverlay(context);

		// Restore tab and filter for the resize step
		clickButton(context, "Blocks");
		context.waitTicks(2);
		setSearch(context, "");
		context.waitTicks(2);
	}

	private void runHoodPixelChecks(
			ClientGameTestContext context,
			String phase,
			List<String> failures
	) {
		String step = phase + "_hood_pixels";
		Path overlay = OverlayPackManager.overlayDirectory().resolve(
				"assets/minecraft/textures/entity/villager/profession/librarian.png");
		TextureVariant variant = HistoricalCatalog.get()
				.textureVariant(VILLAGER_LIBRARIAN_VARIANT)
				.orElse(null);
		try {
			ModConfig.get().clearAll();
			ModConfig.get().setTextureChoice(VILLAGER_LIBRARIAN_KEY, VILLAGER_LIBRARIAN_VARIANT);
			ModConfig.get().setHideVillagerHood(true);
			ModConfig.get().save();
			generateOverlay(context);
			String cleared = hoodPixelsClearedDetail(overlay);
			check(failures, step, "hood_pixels_cleared", cleared == null,
					cleared == null ? "ok" : cleared);

			ModConfig.get().setHideVillagerHood(false);
			ModConfig.get().save();
			generateOverlay(context);
			String kept = hoodPixelsKeptDetail(overlay, variant);
			check(failures, step, "hood_pixels_kept", kept == null,
					kept == null ? "ok" : kept);
		} catch (Exception exception) {
			check(failures, step, "hood_pixels_cleared", false, "error=" + exception);
			check(failures, step, "hood_pixels_kept", false, "error=" + exception);
		} finally {
			ModConfig.get().clearAll();
			ModConfig.get().setHideVillagerHood(true);
			ModConfig.get().save();
			generateOverlay(context);
		}
	}

	private String hoodPixelsClearedDetail(Path png) {
		if (!Files.isRegularFile(png)) {
			return "missing overlay";
		}
		try (InputStream input = Files.newInputStream(png);
				NativeImage image = NativeImage.read(input)) {
			if (image.getWidth() != 64 || image.getHeight() != 64) {
				return "size=" + image.getWidth() + "x" + image.getHeight();
			}
			for (int y = 0; y < 18; y++) {
				for (int x = 32; x < 64; x++) {
					if ((image.getPixel(x, y) >>> 24) != 0) {
						return "opaque hood pixel " + x + "," + y;
					}
				}
			}
			for (int y = 47; y < 64; y++) {
				for (int x = 30; x < 64; x++) {
					if ((image.getPixel(x, y) >>> 24) != 0) {
						return "opaque hood pixel " + x + "," + y;
					}
				}
			}
			if ((image.getPixel(8, 12) >>> 24) == 0) {
				return "head pixel transparent";
			}
			return null;
		} catch (Exception exception) {
			return "error=" + exception;
		}
	}

	private String hoodPixelsKeptDetail(Path overlay, TextureVariant variant) {
		if (variant == null) {
			return "missing variant";
		}
		if (!Files.isRegularFile(overlay)) {
			return "missing overlay";
		}
		byte[] expected;
		try (InputStream input = HistoricalCatalog.get().openAsset(variant.assetPath())) {
			if (input == null) {
				return "missing catalog asset " + variant.assetPath();
			}
			expected = input.readAllBytes();
		} catch (Exception exception) {
			return "catalog read error=" + exception;
		}
		try {
			return Arrays.equals(Files.readAllBytes(overlay), expected) ? null : "bytes differ";
		} catch (Exception exception) {
			return "overlay read error=" + exception;
		}
	}

	private void generateOverlay(ClientGameTestContext context) {
		context.computeOnClient(client -> {
			try {
				OverlayPackManager.generateOverlayPack();
			} catch (java.io.IOException exception) {
				HistoricalTextures.LOGGER.warn("diag overlay generate failed", exception);
			}
			return null;
		});
	}

	private String hoodButtonState(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return "no-screen";
			}
			for (var child : screen.children()) {
				if (child instanceof Button button
						&& button.getMessage().getString().startsWith("Hood:")) {
					return (button.visible ? "visible" : "hidden")
							+ " msg=" + button.getMessage().getString();
				}
			}
			return "absent";
		});
		return String.valueOf(result);
	}

	private void clickHoodButton(ClientGameTestContext context) {
		Button button = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			for (var child : screen.children()) {
				if (child instanceof Button candidate
						&& candidate.getMessage().getString().startsWith("Hood:")) {
					return candidate;
				}
			}
			return null;
		});
		if (button == null) {
			return;
		}
		int btnX = context.computeOnClient(client -> button.getX() + button.getWidth() / 2);
		int btnY = context.computeOnClient(client -> button.getY() + button.getHeight() / 2);
		clickGui(context, btnX, btnY);
	}

	private void runHoodWorldDiagnostic(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<String> failures,
			List<Path> screenshots
	) {
		sp.getServer().runCommand("fill -9 100 -9 10 100 10 minecraft:stone");
		sp.getServer().runCommand("fill -8 101 -8 9 108 9 minecraft:air");
		sp.getServer().runCommand("gamemode spectator @p");
		context.waitTicks(5);

		boolean hudWasHidden = hideDiagnosticHud(context);
		try {
			ModConfig.get().clearAll();
			ModConfig.get().setTextureChoice(VILLAGER_LIBRARIAN_KEY, VILLAGER_LIBRARIAN_VARIANT);
			ModConfig.get().save();

			sp.getServer().runCommand("kill @e[type=!player]");
			try {
				sp.getServer().runCommand(
						"summon minecraft:villager 0.5 101 0.5 "
								+ "{NoAI:1b,Rotation:[0f,0f],VillagerData:{profession:\"minecraft:librarian\",type:\"minecraft:snow\",level:1}}"
				);
			} catch (RuntimeException exception) {
				logDiagnosticWarn("diag_world_hood summon_failed error=" + exception.getMessage());
			}
			context.waitTicks(10);
			double[] anchor = diagnosticMobAnchor(sp, VILLAGER_HOOD_TARGET);
			if (anchor == null) {
				logDiagnosticWarn("diag_world_hood mob_missing");
				return;
			}

			for (boolean hide : new boolean[]{true, false}) {
				ModConfig.get().setHideVillagerHood(hide);
				ModConfig.get().save();
				OverlayPackManager.ApplyResult result = applyDiagnosticOverlay(context);
				String tag = hide ? "hidden" : "shown";
				check(
						failures,
						"diag_world_hood_" + tag,
						"diag_overlay_applied",
						result.texturesWritten() > 0,
						"texturesWritten=" + result.texturesWritten()
								+ " soundsWritten=" + result.soundsWritten()
								+ " skipped=" + result.skipped()
				);
				captureHoodWorldViews(context, sp, screenshots, "diag_world_hood_" + tag, anchor);
			}
		} finally {
			ModConfig.get().clearAll();
			ModConfig.get().setHideVillagerHood(true);
			ModConfig.get().save();
			applyDiagnosticOverlay(context);
			restoreDiagnosticHud(context, hudWasHidden);
		}
	}

	private void captureHoodWorldViews(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<Path> screenshots,
			String shotPrefix,
			double[] anchor
	) {
		double x = anchor[0];
		double y = anchor[1];
		double z = anchor[2];
		double cameraY = y - DIAGNOSTIC_PLAYER_EYE_HEIGHT;
		captureHoodWorldView(context, sp, screenshots, shotPrefix + "_front", x, cameraY, z + 3.0, 180.0f, 0.0f);
		captureHoodWorldView(context, sp, screenshots, shotPrefix + "_left", x + 3.0, cameraY, z, 90.0f, 0.0f);
	}

	private void captureHoodWorldView(
			ClientGameTestContext context,
			TestSingleplayerContext sp,
			List<Path> screenshots,
			String shotName,
			double x,
			double y,
			double z,
			float yaw,
			float pitch
	) {
		sp.getServer().runCommand(String.format(
				Locale.ROOT,
				"tp @p %.3f %.3f %.3f %.1f %.1f",
				x,
				y,
				z,
				yaw,
				pitch
		));
		context.waitTicks(10);
		recordScreenshot(context, shotName, screenshots);
	}

	private void runPreviewSteps(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots
	) {
		// 05c preview_block
		String stepBlock = phase + "_05c_preview_block";
		setSearch(context, "gold ore");
		context.waitTicks(2);
		int targetIndex = findRowIndex(context, 0, "Gold Ore", false);
		clickTargetRow(context, failures, targetIndex, stepBlock, "preview_block_target");
		int variantIndex = Math.min(1, Math.max(0, listSize(context, 1) - 1));
		clickVariantRow(context, variantIndex);
		context.waitTicks(3);

		String blockShape = previewShapeName(context);
		check(failures, stepBlock, "preview_block_shape", "CUBE".equals(blockShape), "shape=" + blockShape);
		float defaultPitch = previewPitch(context);
		check(failures, stepBlock, "preview_default_from_above", defaultPitch > 0.0f, "pitch=" + defaultPitch);
		float yawFirst = previewYaw(context);
		recordScreenshot(context, stepBlock + "_a", screenshots);
		context.waitTicks(20);
		float yawSecond = previewYaw(context);
		boolean spun = Math.abs(yawSecond - yawFirst) > 1.0f;
		check(failures, stepBlock, "preview_block_spin", spun,
				"yawFirst=" + yawFirst + " yawSecond=" + yawSecond);
		recordScreenshot(context, stepBlock + "_b", screenshots);

		// 05d preview_drag
		String stepDrag = phase + "_05d_preview_drag";
		int[] box = previewBox(context);
		int guiScale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int centerX = box[0] + box[2] / 2;
		int centerY = box[1] + box[3] / 2;
		context.getInput().setCursorPos((double) centerX * guiScale, (double) centerY * guiScale);
		context.waitTick();
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		float yawAtPress = previewYaw(context);
		boolean draggingAtPress = previewDragging(context);
		for (int step = 1; step <= 4; step++) {
			context.getInput().moveCursor(10.0 * guiScale, 0.0);
			context.waitTicks(2);
		}
		float yawAtDragEnd = previewYaw(context);
		boolean draggingAtDragEnd = previewDragging(context);
		check(failures, stepDrag, "preview_drag_active", draggingAtPress && draggingAtDragEnd,
				"atPress=" + draggingAtPress + " atDragEnd=" + draggingAtDragEnd);
		boolean dragRotated = Math.abs(yawAtDragEnd - yawAtPress) > 20.0f;
		check(failures, stepDrag, "preview_drag_rotates", dragRotated,
				"yawAtPress=" + yawAtPress + " yawAtDragEnd=" + yawAtDragEnd);

		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		float yawReleased = previewYaw(context);
		boolean draggingAfterRelease = previewDragging(context);
		check(failures, stepDrag, "preview_drag_released", !draggingAfterRelease,
				"dragging=" + draggingAfterRelease);
		context.waitTicks(10);
		float yawHeld = previewYaw(context);
		boolean pausedAfterRelease = Math.abs(yawHeld - yawReleased) <= 1.0f;
		check(failures, stepDrag, "preview_resume_delay", pausedAfterRelease,
				"yawReleased=" + yawReleased + " yawHeld=" + yawHeld);
		context.waitTicks(40);
		float yawResumed = previewYaw(context);
		boolean resumed = Math.abs(yawResumed - yawHeld) > 2.0f;
		check(failures, stepDrag, "preview_spin_resumed", resumed,
				"yawHeld=" + yawHeld + " yawResumed=" + yawResumed);
		recordScreenshot(context, stepDrag, screenshots);

		// 05e preview_vanilla
		String stepVanilla = phase + "_05e_preview_vanilla";
		clickVariantRow(context, 0);
		context.waitTicks(3);
		String vanillaShape = previewShapeName(context);
		boolean vanillaTexture = previewTextureId(context) != null;
		check(failures, stepVanilla, "preview_vanilla_shape", "CUBE".equals(vanillaShape), "shape=" + vanillaShape);
		check(failures, stepVanilla, "preview_vanilla_texture", vanillaTexture,
				"texture=" + previewTextureId(context));
		recordScreenshot(context, stepVanilla, screenshots);

		// 05f preview_plant
		String stepPlant = phase + "_05f_preview_plant";
		setSearch(context, "dandelion");
		context.waitTicks(2);
		clickTargetRow(context, failures, findRowIndex(context, 0, "Dandelion", false), stepPlant, "preview_plant_target");
		context.waitTicks(2);
		String plantShape = previewShapeName(context);
		check(failures, stepPlant, "preview_plant_shape", "PLANE".equals(plantShape), "shape=" + plantShape);
		recordScreenshot(context, stepPlant, screenshots);

		// 05g preview_entity
		String stepEntity = phase + "_05g_preview_entity";
		clickButton(context, "Entities");
		context.waitTicks(3);
		setSearch(context, "cow");
		context.waitTicks(2);
		clickTargetRow(context, failures, findRowIndex(context, 0, "Temperate", true), stepEntity, "preview_entity_target");
		context.waitTicks(2);
		clickVariantRow(context, Math.min(1, Math.max(0, listSize(context, 1) - 1)));
		context.waitTicks(3);
		String entityShape = previewShapeName(context);
		check(failures, stepEntity, "preview_entity_shape", "ENTITY".equals(entityShape), "shape=" + entityShape);
		recordScreenshot(context, stepEntity, screenshots);

		// 05g2 preview_entity_legacy
		String stepLegacy = phase + "_05g2_preview_entity_legacy";
		int legacyVariantIndex = findVariantRowIndex(context, 1, "Prerelease 3");
		if (legacyVariantIndex < 0) {
			legacyVariantIndex = Math.min(1, Math.max(0, listSize(context, 1) - 1));
		}
		clickVariantRow(context, legacyVariantIndex);
		context.waitTicks(3);
		boolean legacyModelUsed = legacyModelSelected(context);
		check(failures, stepLegacy, "preview_entity_legacy_model", legacyModelUsed,
				"variantIndex=" + legacyVariantIndex + " legacyModel=" + legacyModelUsed);
		recordScreenshot(context, stepLegacy, screenshots);

		// 05g3 preview_entity_modern
		String stepModern = phase + "_05g3_preview_entity_modern";
		clickVariantRow(context, 0);
		context.waitTicks(3);
		String modernShape = previewShapeName(context);
		Object modernTexture = previewTextureId(context);
		boolean modernTextureOk = modernTexture != null && modernTexture.toString().contains("preview/vanilla/");
		boolean modernModel = modernEntityModelPresent(context);
		check(failures, stepModern, "preview_entity_modern_shape", "ENTITY".equals(modernShape), "shape=" + modernShape);
		check(failures, stepModern, "preview_entity_modern_model", modernModel && modernTextureOk,
				"model=" + modernModel + " texture=" + modernTexture);
		recordScreenshot(context, stepModern, screenshots);

		// 05g4 preview_steve
		String stepSteve = phase + "_05g4_preview_steve";
		setSearch(context, "steve");
		context.waitTicks(2);
		clickTargetRow(context, failures, findRowIndex(context, 0, "steve", true), stepSteve, "preview_steve_target");
		context.waitTicks(2);
		int steveVariantIndex = findVariantRowById(context, "steve_texture_je1_png");
		if (steveVariantIndex < 0) {
			steveVariantIndex = Math.min(1, Math.max(0, listSize(context, 1) - 1));
		}
		clickVariantRow(context, steveVariantIndex);
		context.waitTicks(3);
		Object steveTexture = previewTextureId(context);
		boolean steveLegacyTexture = steveTexture != null && steveTexture.toString().contains("preview/legacy/");
		check(failures, stepSteve, "preview_steve_legacy_texture", steveLegacyTexture,
				"variantIndex=" + steveVariantIndex + " texture=" + steveTexture);
		recordScreenshot(context, stepSteve, screenshots);

		// 05g5 preview_mismatch_flat
		String stepMismatch = phase + "_05g5_preview_mismatch_flat";
		String[] mismatchCandidate = findMismatchCandidate(context);
		if (mismatchCandidate == null) {
			String skipLine = "[HT-GAMETEST] " + stepMismatch + " preview_mismatch_flat SKIP no candidate";
			HistoricalTextures.LOGGER.info(skipLine);
			System.out.println(skipLine);
			check(failures, stepMismatch, "preview_mismatch_flat", true, "skipped: no candidate");
		} else {
			setSearch(context, mismatchCandidate[0]);
			context.waitTicks(2);
			int mismatchTargetIndex = findRowIndex(context, 0, mismatchCandidate[0], false);
			clickTargetRow(context, failures, mismatchTargetIndex, stepMismatch, "preview_mismatch_target");
			context.waitTicks(2);
			int mismatchVariantIndex = findVariantRowById(context, mismatchCandidate[2]);
			if (mismatchVariantIndex < 0) {
				check(failures, stepMismatch, "preview_mismatch_variant_found", false,
						"variantId=" + mismatchCandidate[2]);
			} else {
				clickVariantRow(context, mismatchVariantIndex);
				context.waitTicks(3);
				String mismatchShape = effectivePreviewShapeName(context);
				check(failures, stepMismatch, "preview_mismatch_flat", "FLAT".equals(mismatchShape),
						"target=" + mismatchCandidate[1] + " variant=" + mismatchCandidate[2] + " shape=" + mismatchShape);
				recordScreenshot(context, stepMismatch, screenshots);
			}
		}
		setSearch(context, "");
		context.waitTicks(2);

		// 05h preview_item
		String stepItem = phase + "_05h_preview_item";
		clickButton(context, "Items");
		context.waitTicks(3);
		setSearch(context, "apple");
		context.waitTicks(2);
		String itemGeo = targetListGeometry(context, 0);
		String itemGeoLine = "[HT-GAMETEST] " + stepItem + " target_row_geometry INFO " + itemGeo;
		HistoricalTextures.LOGGER.info(itemGeoLine);
		System.out.println(itemGeoLine);
		clickTargetRow(context, failures, 0, stepItem, "preview_item_target");
		context.waitTicks(2);
		clickVariantRow(context, Math.min(1, Math.max(0, listSize(context, 1) - 1)));
		context.waitTicks(3);
		String itemShape = previewShapeName(context);
		check(failures, stepItem, "preview_item_shape", "FLAT".equals(itemShape),
				"shape=" + itemShape + " state=" + previewState(context));
		recordScreenshot(context, stepItem, screenshots);

		// 05i preview_other
		String stepOther = phase + "_05i_preview_other";
		clickButton(context, "Other");
		context.waitTicks(3);
		setSearch(context, "");
		context.waitTicks(2);
		clickTargetRow(context, failures, 0, stepOther, "preview_other_target");
		context.waitTicks(3);
		String otherShape = previewShapeName(context);
		check(failures, stepOther, "preview_other_shape", "FLAT".equals(otherShape),
				"shape=" + otherShape + " state=" + previewState(context));
		recordScreenshot(context, stepOther, screenshots);

		// Restore tab and filter for the resize step
		clickButton(context, "Blocks");
		context.waitTicks(2);
		setSearch(context, "");
		context.waitTicks(2);
	}

	private void runCurrentVariantSteps(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots
	) {
		String stoneKey = "minecraft:textures/block/stone.png";

		// current_entry_present: Blocks tab, stone target -> variant row 1 is "Current (...)"
		String stepEntry = phase + "_current_entry_present";
		boolean stoneSelected = selectDiagnosticTarget(context, stoneKey);
		context.waitTicks(2);
		List<AbstractSelectionList<?>> lists = getLists(context);
		AbstractSelectionList<?> variants = lists.size() >= 2 ? lists.get(1) : null;
		String row1 = variants != null && variants.children().size() > 1
				? getRowNarration(variants, 1)
				: "";
		check(failures, stepEntry, "current_entry_present",
				stoneSelected && row1.toLowerCase(Locale.ROOT).startsWith("current ("),
				"selected=" + stoneSelected + " row1=" + row1);
		recordScreenshot(context, stepEntry, screenshots);

		// current_choice_saved: click the Current row -> choice "current", preview uses builtin texture
		String stepSaved = phase + "_current_choice_saved";
		int currentRow = findVariantRowIndex(context, 1, "current (");
		if (currentRow < 0) {
			currentRow = 1;
		}
		clickVariantRow(context, currentRow);
		context.waitTicks(3);
		String saved = context.computeOnClient(client -> ModConfig.get().getTextureChoice(stoneKey));
		Object textureId = previewTextureId(context);
		String textureIdString = String.valueOf(textureId);
		check(failures, stepSaved, "current_choice_saved",
				HistoricalCatalog.CURRENT_VARIANT_ID.equals(saved) && textureIdString.contains("builtin"),
				"choice=" + saved + " texture=" + textureIdString);
		recordScreenshot(context, stepSaved, screenshots);

		// current_overlay_written: overlay stone.png equals the built-in resource bytes
		String stepOverlay = phase + "_current_overlay";
		Path overlayPng = OverlayPackManager.overlayDirectory()
				.resolve("assets/minecraft/textures/block/stone.png");
		try {
			ModConfig.get().clearAll();
			ModConfig.get().setTextureChoice(stoneKey, HistoricalCatalog.CURRENT_VARIANT_ID);
			ModConfig.get().save();
			generateOverlay(context);
			byte[] expected = builtinResourceBytes(context, stoneKey);
			byte[] actual = Files.isRegularFile(overlayPng) ? Files.readAllBytes(overlayPng) : null;
			check(failures, stepOverlay, "current_overlay_written",
					expected != null && actual != null && Arrays.equals(actual, expected),
					"expected=" + (expected == null ? "null" : expected.length)
							+ " actual=" + (actual == null ? "missing" : actual.length));
		} catch (Exception exception) {
			check(failures, stepOverlay, "current_overlay_written", false, "error=" + exception);
		}

		// current_mcmeta_copied: farmer profession -> current copies the vanilla .mcmeta too
		String stepMcmeta = phase + "_current_mcmeta";
		Path mcmeta = OverlayPackManager.overlayDirectory().resolve(
				"assets/minecraft/textures/entity/villager/profession/farmer.png.mcmeta");
		try {
			ModConfig.get().clearAll();
			ModConfig.get().setTextureChoice(VILLAGER_PROFESSION_KEY, HistoricalCatalog.CURRENT_VARIANT_ID);
			ModConfig.get().save();
			generateOverlay(context);
			String content = Files.exists(mcmeta) ? Files.readString(mcmeta) : "";
			check(failures, stepMcmeta, "current_mcmeta_copied",
					content.contains("full"),
					"exists=" + Files.exists(mcmeta) + " content=" + content.replace('\n', ' '));
		} catch (Exception exception) {
			check(failures, stepMcmeta, "current_mcmeta_copied", false, "error=" + exception);
		}

		// Restore config, overlay and filter for the resize step
		ModConfig.get().clearAll();
		ModConfig.get().setHideVillagerHood(true);
		ModConfig.get().save();
		generateOverlay(context);
		setSearch(context, "");
		context.waitTicks(2);
	}

	private byte[] builtinResourceBytes(ClientGameTestContext context, String resourcePath) {
		Object result = context.computeOnClient(client -> {
			try {
				Identifier id = Identifier.parse(resourcePath);
				var supplier = client.getVanillaPackResources().fullResources()
						.getResource(PackType.CLIENT_RESOURCES, id);
				if (supplier == null) {
					return null;
				}
				try (InputStream input = supplier.get()) {
					return input == null ? null : input.readAllBytes();
				}
			} catch (Exception exception) {
				return null;
			}
		});
		return result instanceof byte[] bytes ? bytes : null;
	}

	private void runPresetSteps(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots
	) {
		String step = phase + "_presets";
		String stoneKey = "minecraft:textures/block/stone.png";

		// setup: stone target + first catalog variant
		ModConfig.get().clearAll();
		ModConfig.get().save();
		boolean stoneSelected = selectDiagnosticTarget(context, stoneKey);
		context.waitTicks(2);
		int variantRow = firstCatalogVariantRow(context);
		if (variantRow >= 0) {
			clickVariantRow(context, variantRow);
		}
		context.waitTicks(2);
		String savedVariant = context.computeOnClient(client -> ModConfig.get().getTextureChoice(stoneKey));
		check(failures, step, "preset_setup_choice",
				stoneSelected && savedVariant != null && !savedVariant.isEmpty(),
				"selected=" + stoneSelected + " variant=" + savedVariant);

		// open the Presets screen
		clickButton(context, "Presets…");
		context.waitTicks(5);
		int[] info = presetScreenInfo(context);
		check(failures, step, "presets_screen_open", info[0] == 1, "info=" + Arrays.toString(info));
		check(failures, step, "presets_builtin_rows", info[1] >= 8,
				"builtins=" + info[1] + " total=" + info[2]);
		recordScreenshot(context, step + "_list", screenshots);

		// Save current… -> name -> confirm summary -> Save
		clickButton(context, "Save current…");
		context.waitTicks(4);
		EditBox nameBox = findEditBox(context);
		if (nameBox != null) {
			int ex = context.computeOnClient(client -> nameBox.getX() + nameBox.getWidth() / 2);
			int ey = context.computeOnClient(client -> nameBox.getY() + nameBox.getHeight() / 2);
			clickGui(context, ex, ey);
			context.waitTicks(2);
			context.getInput().typeChars("HT Test");
			context.waitTicks(2);
		}
		String typed = context.computeOnClient(client -> nameBox == null ? "" : nameBox.getValue());
		check(failures, step, "preset_name_entered", "HT Test".equals(typed), "value=" + typed);
		clickButton(context, "Next");
		context.waitTicks(4);
		String confirmTitle = screenTitle(context);
		check(failures, step, "confirm_save_title",
				confirmTitle.contains("Are you sure"), "title=" + confirmTitle);
		boolean summaryStone = summaryRowsContain(context, "stone");
		check(failures, step, "confirm_summary_stone", summaryStone, "found=" + summaryStone);
		recordScreenshot(context, step + "_confirm_save", screenshots);
		clickButton(context, "Save");
		context.waitTicks(4);
		check(failures, step, "preset_saved", PresetStore.get().exists("HT Test"),
				"exists=" + PresetStore.get().exists("HT Test"));

		// clear choices, then apply HT Test -> restores the stone choice
		ModConfig.get().clearAll();
		ModConfig.get().save();
		clickSelectionRow(context, 0, presetRowIndex(context, "HT Test"));
		context.waitTicks(2);
		clickButton(context, "Apply");
		waitForPresetApply(context);
		String restored = context.computeOnClient(client -> ModConfig.get().getTextureChoice(stoneKey));
		check(failures, step, "preset_load_restores",
				savedVariant != null && savedVariant.equals(restored),
				"expected=" + savedVariant + " actual=" + restored);
		String afterApplyTitle = screenTitle(context);
		check(failures, step, "preset_apply_returns_config",
				!"Presets".equals(afterApplyTitle), "title=" + afterApplyTitle);

		// apply the built-in Beta 1.7.3 preset (confirm because choices exist)
		clickButton(context, "Presets…");
		waitForScreenTitle(context, "Presets", 120);
		context.waitTicks(2);
		clickSelectionRow(context, 0, presetRowIndex(context, "Beta 1.7.3"));
		context.waitTicks(2);
		boolean blocksHeader = selectionRowNarrationStartsWith(context, "Blocks (");
		check(failures, step, "preset_contents_panel", blocksHeader, "found=" + blocksHeader);
		recordScreenshot(context, step + "_contents", screenshots);
		clickButton(context, "Apply");
		context.waitTicks(4);
		String loadTitle = screenTitle(context);
		boolean loadConfirm = loadTitle.contains("Load preset");
		check(failures, step, "builtin_load_confirm", loadConfirm, "title=" + loadTitle);
		if (loadConfirm) {
			clickButton(context, "Yes");
		}
		waitForPresetApply(context);
		int choiceCount = ModConfig.get().textureChoices().size();
		check(failures, step, "builtin_load_choices", choiceCount > 100, "count=" + choiceCount);
		String afterBuiltinTitle = screenTitle(context);
		check(failures, step, "builtin_load_screen",
				!"Presets".equals(afterBuiltinTitle), "title=" + afterBuiltinTitle);

		// overwrite HT Test with the current selections before deleting it
		String overwriteStone = context.computeOnClient(client -> HistoricalCatalog.get()
				.textureVariantsForTarget(stoneKey)
				.stream()
				.map(TextureVariant::id)
				.filter(id -> !id.equals(savedVariant))
				.findFirst()
				.orElse(null));
		ModConfig.get().setTextureChoice(stoneKey, overwriteStone);
		ModConfig.get().save();
		clickButton(context, "Presets…");
		waitForScreenTitle(context, "Presets", 120);
		context.waitTicks(2);
		clickSelectionRow(context, 0, presetRowIndex(context, "HT Test"));
		context.waitTicks(2);
		clickButton(context, "Overwrite");
		context.waitTicks(4);
		String overwriteTitle = screenTitle(context);
		check(failures, step, "preset_overwrite_confirm",
				overwriteTitle.contains("Are you sure"), "title=" + overwriteTitle);
		clickButton(context, "Save");
		context.waitTicks(4);
		Preset overwritten = PresetStore.get().userPresets().stream()
				.filter(preset -> preset.name().equalsIgnoreCase("HT Test"))
				.findFirst()
				.orElse(null);
		String storedStone = overwritten == null ? null : overwritten.textureChoices().get(stoneKey);
		boolean stoneMatches = overwritten != null
				&& (overwriteStone == null ? storedStone == null : overwriteStone.equals(storedStone));
		check(failures, step, "preset_overwritten", stoneMatches,
				"expected=" + overwriteStone + " actual=" + storedStone);

		// delete HT Test via the UI
		clickSelectionRow(context, 0, presetRowIndex(context, "HT Test"));
		context.waitTicks(2);
		clickButton(context, "Delete");
		context.waitTicks(4);
		String deleteTitle = screenTitle(context);
		boolean deleteConfirm = deleteTitle.contains("Delete preset");
		check(failures, step, "delete_confirm", deleteConfirm, "title=" + deleteTitle);
		if (deleteConfirm) {
			clickButton(context, "Yes");
			context.waitTicks(4);
		}
		check(failures, step, "preset_deleted", !PresetStore.get().exists("HT Test"),
				"exists=" + PresetStore.get().exists("HT Test"));

		// back to the config screen and restore state
		clickButton(context, "Back");
		context.waitTicks(4);
		ModConfig.get().clearAll();
		ModConfig.get().save();
		PresetStore.get().delete("HT Test");
		setSearch(context, "");
		context.waitTicks(2);
	}

	private void runCakeGroupSteps(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots
	) {
		String step = phase + "_cake_group";
		clickButton(context, "Blocks");
		context.waitTicks(3);
		setSearch(context, "cake");
		context.waitTicks(2);

		Object cakeRow = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return new Object[]{-1, false, false};
			}
			try {
				Object listObject = screenFieldValue(screen, "targetList");
				if (!(listObject instanceof AbstractSelectionList<?> targetList)) {
					return new Object[]{-1, false, false};
				}
				boolean cakeSideRow = false;
				for (int index = 0; index < targetList.children().size(); index++) {
					String narration = getRowNarration(targetList, index);
					if ("cake side".equalsIgnoreCase(narration)) {
						cakeSideRow = true;
					}
					Object entry = targetList.children().get(index);
					var field = entry.getClass().getDeclaredField("target");
					field.setAccessible(true);
					if (field.get(entry) instanceof TextureTarget target
							&& "Cake".equals(target.displayName())) {
						boolean isGroup = target.isGroup()
								&& target.members().contains("minecraft:textures/block/cake_side.png");
						return new Object[]{index, isGroup, cakeSideRow};
					}
				}
				return new Object[]{-1, false, cakeSideRow};
			} catch (ReflectiveOperationException exception) {
				return new Object[]{-1, false, false};
			}
		});
		int cakeIndex = cakeRow instanceof Object[] row && row[0] instanceof Integer index ? index : -1;
		boolean cakeIsGroup = cakeRow instanceof Object[] row && Boolean.TRUE.equals(row[1]);
		boolean cakeSideRowExists = cakeRow instanceof Object[] row && Boolean.TRUE.equals(row[2])
				|| cakeSideRowExistsLater(context);
		check(failures, step, "cake_group_target", cakeIndex >= 0 && cakeIsGroup,
				"rowIndex=" + cakeIndex + " isGroup=" + cakeIsGroup);
		check(failures, step, "cake_side_row_hidden", !cakeSideRowExists,
				"cakeSideRow=" + cakeSideRowExists);
		if (cakeIndex < 0) {
			return;
		}
		clickTargetRow(context, failures, cakeIndex, step, "cake_group_click");
		context.waitTicks(2);

		int betaRow = findVariantRowIndex(context, 1, "beta 1.2");
		check(failures, step, "cake_beta_row", betaRow >= 0, "rowIndex=" + betaRow);
		if (betaRow >= 0) {
			clickVariantRow(context, betaRow);
			context.waitTicks(3);
		}
		recordScreenshot(context, step + "_variants", screenshots);

		String sideChoice = context.computeOnClient(client ->
				ModConfig.get().getTextureChoice("minecraft:textures/block/cake_side.png"));
		String topChoice = context.computeOnClient(client ->
				ModConfig.get().getTextureChoice("minecraft:textures/block/cake_top.png"));
		check(failures, step, "cake_side_choice",
				"cake_side_texture_je1_be1_png".equals(sideChoice), "choice=" + sideChoice);
		check(failures, step, "cake_top_choice",
				"cake_top_texture_je1_be1_png".equals(topChoice), "choice=" + topChoice);

		ModConfig.get().clearAll();
		ModConfig.get().save();
		generateOverlay(context);
		setSearch(context, "");
		context.waitTicks(2);
	}

	private void runSoundSteps(
			ClientGameTestContext context,
			String phase,
			List<String> failures,
			List<Path> screenshots
	) {
		String step = phase + "_sounds";
		clickButton(context, "Sounds");
		context.waitTicks(3);
		setSearch(context, "");
		context.waitTicks(2);

		int eventIndex = context.computeOnClient(client -> {
			List<String> events = TextureTargetRegistry.soundEvents();
			for (int index = 0; index < events.size(); index++) {
				if (!HistoricalCatalog.get().soundVariantsForEvent(events.get(index)).isEmpty()) {
					return index;
				}
			}
			return -1;
		});
		check(failures, step, "sound_event_found", eventIndex >= 0, "index=" + eventIndex);
		if (eventIndex < 0) {
			clickButton(context, "Blocks");
			return;
		}
		clickSelectionRow(context, 0, eventIndex);
		context.waitTicks(3);

		int variantRow = firstCatalogVariantRow(context);
		check(failures, step, "sound_variant_row", variantRow >= 0, "rowIndex=" + variantRow);
		if (variantRow < 0) {
			clickButton(context, "Blocks");
			return;
		}
		clickVariantRow(context, variantRow);
		context.waitTicks(3);

		Object[] selected = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			try {
				var listField = screen.getClass().getDeclaredField("variantList");
				listField.setAccessible(true);
				Object list = listField.get(screen);
				if (!(list instanceof AbstractSelectionList<?> selection)) {
					return null;
				}
				Object entry = selection.getSelected();
				if (entry == null) {
					return null;
				}
				var idField = entry.getClass().getDeclaredField("variantId");
				idField.setAccessible(true);
				String id = (String) idField.get(entry);
				String assetPath = id == null ? null : HistoricalCatalog.get()
						.soundVariant(id)
						.map(SoundVariant::assetPath)
						.orElse(null);
				return new Object[]{id, assetPath};
			} catch (ReflectiveOperationException exception) {
				return null;
			}
		});
		String variantId = selected != null && selected[0] instanceof String id ? id : null;
		String assetPath = selected != null && selected[1] instanceof String path ? path : null;

		boolean playButton = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return false;
			}
			for (var child : screen.children()) {
				if (child instanceof Button button && button.visible
						&& "Play".equals(button.getMessage().getString())) {
					return true;
				}
			}
			return false;
		});
		check(failures, step, "sound_play_button", playButton, "visible=" + playButton);

		float duration = context.computeOnClient(client -> SoundPreviewPlayer.durationSeconds());
		check(failures, step, "sound_duration", duration > 0.0f, "duration=" + duration);

		boolean playable = variantId != null && assetPath != null
				&& Boolean.TRUE.equals(context.computeOnClient(client ->
						SoundPreviewPlayer.load(variantId, assetPath)));

		clickButton(context, "Play");
		context.waitTicks(10);
		String playLabel = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return "";
			}
			for (var child : screen.children()) {
				if (child instanceof Button button && button.visible) {
					String message = button.getMessage().getString();
					if ("Play".equals(message) || "Pause".equals(message)) {
						return message;
					}
				}
			}
			return "";
		});
		if (playable) {
			check(failures, step, "sound_playing", "Pause".equals(playLabel), "label=" + playLabel);
		} else {
			checkSkipped(step, "sound_playing", "sound failed to load (no audio device?)");
		}
		recordScreenshot(context, step + "_player", screenshots);

		clickButton(context, "Blocks");
		context.waitTicks(3);
		boolean stillPlaying = context.computeOnClient(client -> SoundPreviewPlayer.isPlaying());
		check(failures, step, "sound_stopped", !stillPlaying, "isPlaying=" + stillPlaying);
	}

	private void checkSkipped(String step, String checkName, String reason) {
		String line = String.format("[HT-GAMETEST] %s %s SKIPPED %s", step, checkName, reason);
		HistoricalTextures.LOGGER.info(line);
		System.out.println(line);
	}

	private boolean cakeSideRowExistsLater(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return false;
			}
			try {
				Object listObject = screenFieldValue(screen, "targetList");
				if (!(listObject instanceof AbstractSelectionList<?> targetList)) {
					return false;
				}
				for (int index = 0; index < targetList.children().size(); index++) {
					if ("cake side".equalsIgnoreCase(getRowNarration(targetList, index))) {
						return true;
					}
				}
				return false;
			} catch (ReflectiveOperationException exception) {
				return false;
			}
		});
		return Boolean.TRUE.equals(result);
	}

	private int firstCatalogVariantRow(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			List<AbstractSelectionList<?>> lists = selectionLists(client);
			if (lists.size() < 2) {
				return -1;
			}
			AbstractSelectionList<?> variants = lists.get(1);
			for (int index = 0; index < variants.children().size(); index++) {
				String narration = getRowNarration(variants, index).toLowerCase(Locale.ROOT);
				if (!narration.startsWith("vanilla") && !narration.startsWith("current")) {
					return index;
				}
			}
			return -1;
		});
		return result instanceof Integer index ? index : -1;
	}

	private int[] presetScreenInfo(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null || !"Presets".equals(screen.getTitle().getString())) {
				return new int[]{0, 0, 0};
			}
			List<AbstractSelectionList<?>> lists = selectionLists(client);
			int builtIns = 0;
			int total = 0;
			if (!lists.isEmpty()) {
				AbstractSelectionList<?> list = lists.get(0);
				total = list.children().size();
				for (Object child : list.children()) {
					try {
						var field = child.getClass().getDeclaredField("preset");
						field.setAccessible(true);
						if (field.get(child) instanceof Preset preset && preset.builtIn()) {
							builtIns++;
						}
					} catch (ReflectiveOperationException ignored) {
					}
				}
			}
			return new int[]{1, builtIns, total};
		});
		return result instanceof int[] info ? info : new int[]{0, 0, 0};
	}

	private int presetRowIndex(ClientGameTestContext context, String name) {
		Object result = context.computeOnClient(client -> {
			List<AbstractSelectionList<?>> lists = selectionLists(client);
			if (lists.isEmpty()) {
				return -1;
			}
			AbstractSelectionList<?> list = lists.get(0);
			for (int index = 0; index < list.children().size(); index++) {
				if (getRowNarration(list, index).equalsIgnoreCase(name)) {
					return index;
				}
			}
			return -1;
		});
		return result instanceof Integer index ? index : -1;
	}

	private void clickSelectionRow(ClientGameTestContext context, int listIndex, int rowIndex) {
		if (rowIndex < 0) {
			return;
		}
		ensureRowVisible(context, listIndex, rowIndex);
		List<AbstractSelectionList<?>> lists = getLists(context);
		if (listIndex < lists.size()) {
			AbstractSelectionList<?> list = lists.get(listIndex);
			if (rowIndex < list.children().size()) {
				clickRow(context, list, rowIndex);
				context.waitTicks(2);
			}
		}
	}

	private String screenTitle(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			return screen == null ? "" : screen.getTitle().getString();
		});
		return String.valueOf(result);
	}

	private void waitForPresetApply(ClientGameTestContext context) {
		context.waitTicks(5);
		context.waitFor(client -> client.gui.overlay() == null);
		context.waitTicks(20);
	}

	private void waitForScreenTitle(ClientGameTestContext context, String title, int maxTicks) {
		for (int tick = 0; tick < maxTicks && !title.equals(screenTitle(context)); tick++) {
			context.waitTicks(1);
		}
	}

	private boolean summaryRowsContain(ClientGameTestContext context, String needle) {
		Object result = context.computeOnClient(client -> {
			String lowered = needle.toLowerCase(Locale.ROOT);
			for (AbstractSelectionList<?> list : selectionLists(client)) {
				for (int index = 0; index < list.children().size(); index++) {
					if (getRowNarration(list, index).toLowerCase(Locale.ROOT).contains(lowered)) {
						return true;
					}
				}
			}
			return false;
		});
		return Boolean.TRUE.equals(result);
	}

	private boolean selectionRowNarrationStartsWith(ClientGameTestContext context, String prefix) {
		Object result = context.computeOnClient(client -> {
			for (AbstractSelectionList<?> list : selectionLists(client)) {
				for (int index = 0; index < list.children().size(); index++) {
					if (getRowNarration(list, index).startsWith(prefix)) {
						return true;
					}
				}
			}
			return false;
		});
		return Boolean.TRUE.equals(result);
	}

	private EditBox findEditBox(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			for (var child : screen.children()) {
				if (child instanceof EditBox editBox) {
					return editBox;
				}
			}
			return null;
		});
	}

	private static List<AbstractSelectionList<?>> selectionLists(Minecraft client) {
		Screen screen = client.gui.screen();
		List<AbstractSelectionList<?>> lists = new ArrayList<>();
		if (screen == null) {
			return lists;
		}
		for (var child : screen.children()) {
			if (child instanceof AbstractSelectionList<?> candidate) {
				lists.add(candidate);
			}
		}
		lists.sort(Comparator.comparingInt(AbstractSelectionList::getX));
		return lists;
	}

	private void setSearch(ClientGameTestContext context, String value) {
		EditBox box = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			for (var child : screen.children()) {
				if (child instanceof EditBox editBox) {
					return editBox;
				}
			}
			return null;
		});
		if (box == null) {
			return;
		}
		context.runOnClient(client -> box.setValue(value));
		context.waitTicks(2);
	}

	private void clickButton(ClientGameTestContext context, String label) {
		Button button = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			for (var child : screen.children()) {
				if (child instanceof Button candidate && label.equals(candidate.getMessage().getString())) {
					return candidate;
				}
			}
			return null;
		});
		if (button == null) {
			return;
		}
		int btnX = context.computeOnClient(client -> button.getX() + button.getWidth() / 2);
		int btnY = context.computeOnClient(client -> button.getY() + button.getHeight() / 2);
		clickGui(context, btnX, btnY);
		context.waitTicks(4);
	}

	private int listSize(ClientGameTestContext context, int listIndex) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		return listIndex < lists.size() ? lists.get(listIndex).children().size() : 0;
	}

	private int findRowIndex(ClientGameTestContext context, int listIndex, String needle, boolean contains) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		if (listIndex >= lists.size()) {
			return 0;
		}
		AbstractSelectionList<?> list = lists.get(listIndex);
		String lowered = needle.toLowerCase(Locale.ROOT);
		for (int index = 0; index < list.children().size(); index++) {
			String narration = getRowNarration(list, index).toLowerCase(Locale.ROOT);
			boolean match = contains ? narration.contains(lowered) : narration.equals(lowered);
			if (match) {
				return index;
			}
		}
		return 0;
	}

	private int findVariantRowIndex(ClientGameTestContext context, int listIndex, String needle) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		if (listIndex >= lists.size()) {
			return -1;
		}
		AbstractSelectionList<?> list = lists.get(listIndex);
		String lowered = needle.toLowerCase(Locale.ROOT);
		for (int index = 0; index < list.children().size(); index++) {
			if (getRowNarration(list, index).toLowerCase(Locale.ROOT).contains(lowered)) {
				return index;
			}
		}
		return -1;
	}

	private void ensureRowVisible(ClientGameTestContext context, int listIndex, int rowIndex) {
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return;
			}
			List<AbstractSelectionList<?>> lists = new ArrayList<>();
			for (var child : screen.children()) {
				if (child instanceof AbstractSelectionList<?> candidate) {
					lists.add(candidate);
				}
			}
			lists.sort(Comparator.comparingInt(AbstractSelectionList::getX));
			if (listIndex >= lists.size()) {
				return;
			}
			AbstractSelectionList<?> list = lists.get(listIndex);
			if (list.children().isEmpty() || rowIndex < 0 || rowIndex >= list.children().size()) {
				return;
			}
			int itemHeight = list.getRowBottom(0) - list.getRowTop(0);
			if (itemHeight <= 0) {
				return;
			}
			int rowTop = list.getRowTop(rowIndex);
			int rowBottom = list.getRowBottom(rowIndex);
			int viewTop = list.getY();
			int viewBottom = list.getY() + list.getHeight();
			double scroll = -1;
			if (rowBottom > viewBottom + 1) {
				scroll = (rowIndex + 1) * (double) itemHeight - list.getHeight() + 2;
			} else if (rowTop < viewTop - 1) {
				scroll = Math.max(0, rowIndex * (double) itemHeight - 2);
			}
			if (scroll >= 0) {
				list.setScrollAmount(scroll);
			}
		});
		context.waitTicks(2);
	}

	private String targetListGeometry(ClientGameTestContext context, int rowIndex) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return "no-screen";
			}
			AbstractSelectionList<?> list = null;
			for (var child : screen.children()) {
				if (child instanceof AbstractSelectionList<?> candidate) {
					if (list == null || candidate.getX() < list.getX()) {
						list = candidate;
					}
				}
			}
			if (list == null) {
				return "no-list";
			}
			String scroll = "?";
			try {
				var field = AbstractSelectionList.class.getDeclaredField("scrollAmount");
				field.setAccessible(true);
				scroll = String.valueOf(field.get(list));
			} catch (ReflectiveOperationException ignored) {
			}
			return "x=" + list.getX() + " y=" + list.getY() + " w=" + list.getWidth() + " h=" + list.getHeight()
					+ " children=" + list.children().size()
					+ " rowLeft=" + list.getRowLeft() + " rowRight=" + list.getRowRight()
					+ " top" + rowIndex + "=" + (rowIndex < list.children().size() ? list.getRowTop(rowIndex) : -1)
					+ " bottom" + rowIndex + "=" + (rowIndex < list.children().size() ? list.getRowBottom(rowIndex) : -1)
					+ " scroll=" + scroll;
		});
		return String.valueOf(result);
	}

	private String previewState(ClientGameTestContext context) {
		Object state = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return "no-screen";
			}
			try {
				Object tab = screenFieldValue(screen, "activeTab");
				Object target = screenFieldValue(screen, "selectedTarget");
				Object sound = screenFieldValue(screen, "selectedSoundEvent");
				return "tab=" + tab + " target=" + (target == null ? "null" : "set") + " sound=" + sound;
			} catch (ReflectiveOperationException exception) {
				return "err";
			}
		});
		return String.valueOf(state);
	}

	private int findVariantRowById(ClientGameTestContext context, String variantId) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return -1;
			}
			try {
				var field = screen.getClass().getDeclaredField("variantList");
				field.setAccessible(true);
				Object list = field.get(screen);
				if (!(list instanceof AbstractSelectionList<?> selection)) {
					return -1;
				}
				for (int index = 0; index < selection.children().size(); index++) {
					Object entry = selection.children().get(index);
					var variantField = entry.getClass().getDeclaredField("textureVariant");
					variantField.setAccessible(true);
					Object variant = variantField.get(entry);
					if (variant instanceof TextureVariant textureVariant && variantId.equals(textureVariant.id())) {
						return index;
					}
				}
				return -1;
			} catch (ReflectiveOperationException exception) {
				return -1;
			}
		});
		return result instanceof Integer index ? index : -1;
	}

	private boolean legacyModelSelected(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return false;
			}
			try {
				Object target = screenFieldValue(screen, "selectedTarget");
				Object list = screenFieldValue(screen, "variantList");
				if (target == null || !(list instanceof AbstractSelectionList<?> selection)) {
					return false;
				}
				Object selected = selection.getSelected();
				if (selected == null) {
					return false;
				}
				Object variant = screenFieldValue(selected, "textureVariant");
				if (variant == null) {
					return false;
				}
				Class<?> renderer = Class.forName("dev.historicaltextures.client.screen.VariantPreviewRenderer");
				var method = renderer.getDeclaredMethod(
						"legacyModel",
						TextureTarget.class,
						TextureVariant.class
				);
				method.setAccessible(true);
				Object optional = method.invoke(null, target, variant);
				return optional instanceof Optional<?> opt && opt.isPresent();
			} catch (ReflectiveOperationException exception) {
				return false;
			}
		});
		return Boolean.TRUE.equals(result);
	}

	private boolean rabbitLegacyModelPresent(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			TextureVariant variant = HistoricalCatalog.get()
					.textureVariant("brown_rabbit_texture_je2_png")
					.orElse(null);
			if (variant == null) {
				return false;
			}
			TextureTarget target = new TextureTarget(
					"minecraft:textures/entity/rabbit/rabbit_brown.png",
					Identifier.tryParse("minecraft:rabbit"),
					TextureTargetKind.ENTITY,
					"Rabbit"
			);
			try {
				Class<?> renderer = Class.forName("dev.historicaltextures.client.screen.VariantPreviewRenderer");
				var method = renderer.getDeclaredMethod("legacyModel", TextureTarget.class, TextureVariant.class);
				method.setAccessible(true);
				Object optional = method.invoke(null, target, variant);
				return optional instanceof Optional<?> opt && opt.isPresent();
			} catch (ReflectiveOperationException exception) {
				return false;
			}
		});
		return Boolean.TRUE.equals(result);
	}

	private boolean modernEntityModelPresent(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return false;
			}
			try {
				Object target = screenFieldValue(screen, "selectedTarget");
				if (target == null) {
					return false;
				}
				Class<?> renderer = Class.forName("dev.historicaltextures.client.screen.VariantPreviewRenderer");
				var method = renderer.getDeclaredMethod("entityModel", TextureTarget.class);
				method.setAccessible(true);
				Object optional = method.invoke(null, target);
				return optional instanceof Optional<?> opt && opt.isPresent();
			} catch (ReflectiveOperationException exception) {
				return false;
			}
		});
		return Boolean.TRUE.equals(result);
	}

	private static Object screenFieldValue(Object instance, String name) throws ReflectiveOperationException {
		var field = instance.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	private String[] findMismatchCandidate(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			int targetsScanned = 0;
			int variantsScanned = 0;
			int previewsResolved = 0;
			String firstAspectPair = null;
			for (TextureTarget target : TextureTargetRegistry.entityTargets()) {
				Optional<CatalogThumbnailCache.CachedPreview> vanilla =
						CatalogThumbnailCache.getVanilla(target.resourcePath());
				if (vanilla.isEmpty() || vanilla.get().height() <= 0) {
					continue;
				}
				targetsScanned++;
				float vanillaAspect = (float) vanilla.get().width() / vanilla.get().height();
				for (TextureVariant variant : HistoricalCatalog.get().textureVariantsForTarget(target.configKey())) {
					variantsScanned++;
					Map<String, String> files = LegacyModels.filesFor(target.configKey(), variant.id());
					boolean hasJem = files.keySet().stream().anyMatch(dest -> dest != null && dest.endsWith(".jem"));
					if (hasJem) {
						continue;
					}
					Optional<CatalogThumbnailCache.CachedPreview> preview =
							resolvePreviewAspect(target, variant);
					if (preview.isEmpty() || preview.get().height() <= 0) {
						continue;
					}
					previewsResolved++;
					float aspect = (float) preview.get().width() / preview.get().height();
					if (firstAspectPair == null) {
						firstAspectPair = target.configKey() + " variant=" + variant.id()
								+ " preview=" + preview.get().width() + "x" + preview.get().height()
								+ " vanilla=" + vanilla.get().width() + "x" + vanilla.get().height();
					}
					if (Math.abs(aspect - vanillaAspect) > 0.02f) {
						return new String[]{target.displayName(), target.configKey(), variant.id()};
					}
				}
			}
			String stats = "targets=" + targetsScanned + " variants=" + variantsScanned
					+ " resolved=" + previewsResolved + " first=" + firstAspectPair;
			HistoricalTextures.LOGGER.info("[HT-GAMETEST] mismatch_scan_stats INFO " + stats);
			System.out.println("[HT-GAMETEST] mismatch_scan_stats INFO " + stats);
			return null;
		});
		return result instanceof String[] candidate ? candidate : null;
	}

	private static Optional<CatalogThumbnailCache.CachedPreview> resolvePreviewAspect(TextureTarget target, TextureVariant variant) {
		String path = target.resourcePath();
		int colon = path.indexOf(':');
		String noNamespace = colon >= 0 ? path.substring(colon + 1) : path;
		for (Map.Entry<String, String> file : LegacyModels.filesFor(target.configKey(), variant.id()).entrySet()) {
			if (file.getKey() != null && file.getKey().endsWith(".png") && file.getKey().equals(noNamespace)) {
				return CatalogThumbnailCache.getLegacy(file.getValue());
			}
		}
		return CatalogThumbnailCache.get(variant.id(), variant.assetPath());
	}

	private void clickTargetRow(ClientGameTestContext context, List<String> failures, int rowIndex, String step, String checkName) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		AbstractSelectionList<?> targets = !lists.isEmpty() ? lists.get(0) : null;
		int size = targets != null ? targets.children().size() : 0;
		check(failures, step, checkName, targets != null && rowIndex < size,
				"rowIndex=" + rowIndex + " size=" + size);
		if (targets != null && rowIndex < size) {
			ensureRowVisible(context, 0, rowIndex);
			clickRow(context, targets, rowIndex);
			context.waitTicks(3);
		}
	}

	private void clickVariantRow(ClientGameTestContext context, int rowIndex) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		AbstractSelectionList<?> variants = lists.size() >= 2 ? lists.get(1) : null;
		if (variants != null && rowIndex < variants.children().size()) {
			ensureRowVisible(context, 1, rowIndex);
			clickRow(context, variants, rowIndex);
			context.waitTicks(3);
		}
	}

	private Object previewField(ClientGameTestContext context, String name) {
		return context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			try {
				var field = screen.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return field.get(screen);
			} catch (ReflectiveOperationException exception) {
				return null;
			}
		});
	}

	private Object previewCall(ClientGameTestContext context, String name) {
		return context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return null;
			}
			try {
				var method = screen.getClass().getDeclaredMethod(name);
				method.setAccessible(true);
				return method.invoke(screen);
			} catch (ReflectiveOperationException exception) {
				return null;
			}
		});
	}

	private String previewShapeName(ClientGameTestContext context) {
		Object shape = previewCall(context, "previewShape");
		return shape instanceof Enum<?> enumValue ? enumValue.name() : "null";
	}

	private String effectivePreviewShapeName(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return "null";
			}
			try {
				var textureMethod = screen.getClass().getDeclaredMethod("currentPreviewTexture");
				textureMethod.setAccessible(true);
				Object preview = textureMethod.invoke(screen);
				for (var method : screen.getClass().getDeclaredMethods()) {
					if (method.getName().equals("effectivePreviewShape")) {
						method.setAccessible(true);
						Object shape = method.invoke(screen, preview);
						return shape instanceof Enum<?> enumValue ? enumValue.name() : "null";
					}
				}
				return "no-method";
			} catch (ReflectiveOperationException exception) {
				return "err";
			}
		});
		return String.valueOf(result);
	}

	private Object previewTextureId(ClientGameTestContext context) {
		Object preview = previewCall(context, "currentPreviewTexture");
		if (preview == null) {
			return null;
		}
		try {
			var method = preview.getClass().getDeclaredMethod("textureId");
			method.setAccessible(true);
			return method.invoke(preview);
		} catch (ReflectiveOperationException exception) {
			return null;
		}
	}

	private float previewYaw(ClientGameTestContext context) {
		Object value = previewField(context, "previewYaw");
		return value instanceof Float yaw ? yaw : 0.0f;
	}

	private boolean previewDragging(ClientGameTestContext context) {
		Object value = previewField(context, "previewDragging");
		return value instanceof Boolean dragging && dragging;
	}

	private int[] previewBox(ClientGameTestContext context) {
		int[] box = new int[4];
		box[0] = intField(context, "col3X");
		box[1] = intField(context, "listTop");
		box[2] = intField(context, "previewW");
		box[3] = intField(context, "listBottom") - box[1];
		return box;
	}

	private int intField(ClientGameTestContext context, String name) {
		Object value = previewField(context, name);
		return value instanceof Integer number ? number : 0;
	}

	private void clickGui(ClientGameTestContext context, int guiX, int guiY) {
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((double) guiX * scale, (double) guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private void clickRow(ClientGameTestContext context, AbstractSelectionList<?> list, int rowIndex) {
		int clickX = context.computeOnClient(client -> (list.getRowLeft() + list.getRowRight()) / 2);
		int clickY = context.computeOnClient(client -> (list.getRowTop(rowIndex) + list.getRowBottom(rowIndex)) / 2);
		clickGui(context, clickX, clickY);
	}

	private String getRowNarration(AbstractSelectionList<?> list, int rowIndex) {
		if (rowIndex < 0 || rowIndex >= list.children().size()) {
			return "";
		}
		Object entry = list.children().get(rowIndex);
		if (entry instanceof ObjectSelectionList.Entry<?> objEntry) {
			return objEntry.getNarration().getString();
		}
		return entry.toString();
	}

	private static AbstractSelectionList<?> firstSelectionList(Minecraft client) {
		Screen screen = client.gui.screen();
		if (screen == null) {
			return null;
		}
		List<AbstractSelectionList<?>> lists = new ArrayList<>();
		for (var child : screen.children()) {
			if (child instanceof AbstractSelectionList<?> candidate) {
				lists.add(candidate);
			}
		}
		lists.sort(Comparator.comparingInt(AbstractSelectionList::getX));
		return lists.isEmpty() ? null : lists.get(0);
	}

	private double targetListScrollAmount(ClientGameTestContext context) {
		Object result = context.computeOnClient(client -> {
			AbstractSelectionList<?> list = firstSelectionList(client);
			return list == null ? -1.0 : list.scrollAmount();
		});
		return result instanceof Double amount ? amount : -1.0;
	}

	private List<AbstractSelectionList<?>> getLists(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			if (screen == null) {
				return List.<AbstractSelectionList<?>>of();
			}
			List<AbstractSelectionList<?>> result = new ArrayList<>();
			for (var child : screen.children()) {
				if (child instanceof AbstractSelectionList<?> selList) {
					result.add(selList);
				}
			}
			result.sort(Comparator.comparingInt(AbstractSelectionList::getX));
			return result;
		});
	}

	private void check(List<String> failures, String step, String checkName, boolean condition, String details) {
		String status = condition ? "PASS" : "FAIL";
		String line = String.format("[HT-GAMETEST] %s %s %s %s", step, checkName, status, details);
		HistoricalTextures.LOGGER.info(line);
		System.out.println(line);
		if (!condition) {
			failures.add(line);
		}
	}

	private void recordScreenshot(ClientGameTestContext context, String name, List<Path> screenshots) {
		Path p = context.takeScreenshot(name);
		if (p != null) {
			screenshots.add(p);
		}
	}
}
