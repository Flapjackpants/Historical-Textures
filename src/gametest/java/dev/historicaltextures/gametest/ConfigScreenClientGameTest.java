package dev.historicaltextures.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.TextureTarget;
import dev.historicaltextures.catalog.TextureTargetRegistry;
import dev.historicaltextures.catalog.TextureVariant;
import dev.historicaltextures.client.screen.HistoricalTexturesConfigScreen;
import dev.historicaltextures.config.ModConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigScreenClientGameTest implements FabricClientGameTest {

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
				if (validIds.contains(val)) {
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

		// 05h preview_item
		String stepItem = phase + "_05h_preview_item";
		clickButton(context, "Items");
		context.waitTicks(3);
		setSearch(context, "apple");
		context.waitTicks(2);
		clickTargetRow(context, failures, 0, stepItem, "preview_item_target");
		context.waitTicks(2);
		clickVariantRow(context, Math.min(1, Math.max(0, listSize(context, 1) - 1)));
		context.waitTicks(3);
		String itemShape = previewShapeName(context);
		check(failures, stepItem, "preview_item_shape", "PLANE".equals(itemShape), "shape=" + itemShape);
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
		check(failures, stepOther, "preview_other_shape", "FLAT".equals(otherShape), "shape=" + otherShape);
		recordScreenshot(context, stepOther, screenshots);

		// Restore tab and filter for the resize step
		clickButton(context, "Blocks");
		context.waitTicks(2);
		setSearch(context, "");
		context.waitTicks(2);
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

	private void clickTargetRow(ClientGameTestContext context, List<String> failures, int rowIndex, String step, String checkName) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		AbstractSelectionList<?> targets = !lists.isEmpty() ? lists.get(0) : null;
		int size = targets != null ? targets.children().size() : 0;
		check(failures, step, checkName, targets != null && rowIndex < size,
				"rowIndex=" + rowIndex + " size=" + size);
		if (targets != null && rowIndex < size) {
			clickRow(context, targets, rowIndex);
			context.waitTicks(3);
		}
	}

	private void clickVariantRow(ClientGameTestContext context, int rowIndex) {
		List<AbstractSelectionList<?>> lists = getLists(context);
		AbstractSelectionList<?> variants = lists.size() >= 2 ? lists.get(1) : null;
		if (variants != null && rowIndex < variants.children().size()) {
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
