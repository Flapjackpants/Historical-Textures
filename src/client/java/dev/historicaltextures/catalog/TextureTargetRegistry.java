package dev.historicaltextures.catalog;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextureTargetRegistry {
	private static List<TextureTarget> blockTargets;
	private static List<TextureTarget> itemTargets;
	private static List<TextureTarget> entityTargets;
	private static List<String> soundEvents;

	private TextureTargetRegistry() {
	}

	public static List<TextureTarget> blockTargets() {
		if (blockTargets == null) {
			blockTargets = buildBlockTargets();
		}
		return blockTargets;
	}

	public static List<TextureTarget> itemTargets() {
		if (itemTargets == null) {
			itemTargets = buildItemTargets();
		}
		return itemTargets;
	}

	public static List<TextureTarget> entityTargets() {
		if (entityTargets == null) {
			entityTargets = buildEntityTargets();
		}
		return entityTargets;
	}

	public static List<String> soundEvents() {
		if (soundEvents == null) {
			soundEvents = buildSoundEvents();
		}
		return soundEvents;
	}

	public static void reload() {
		blockTargets = null;
		itemTargets = null;
		entityTargets = null;
		soundEvents = null;
	}

	private static List<TextureTarget> buildBlockTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		HistoricalCatalog catalog = HistoricalCatalog.get();
		for (Map.Entry<String, List<TextureVariant>> entry : catalog.texturesByTarget().entrySet()) {
			String path = entry.getKey();
			if (!path.contains("/textures/block/")) {
				continue;
			}
			Identifier blockId = pathToBlockId(path);
			if (blockId != null) {
				Block block = BuiltInRegistries.BLOCK.get(blockId).map(h -> h.value()).orElse(null);
				String name = block != null ? block.getName().getString() : blockId.toString();
				byPath.put(path, new TextureTarget(path, blockId, TextureTargetKind.BLOCK, name));
			}
		}
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			String path = "textures/block/" + id.getPath() + ".png";
			String resourcePath = "minecraft:" + path;
			byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.BLOCK, block.getName().getString()));
		}
		return sorted(byPath);
	}

	private static List<TextureTarget> buildItemTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		HistoricalCatalog catalog = HistoricalCatalog.get();
		for (String path : catalog.texturesByTarget().keySet()) {
			if (!path.contains("/textures/item/")) {
				continue;
			}
			Identifier itemId = pathToItemId(path);
			if (itemId != null) {
				Item item = BuiltInRegistries.ITEM.get(itemId).map(h -> h.value()).orElse(null);
				String name = item != null ? item.getName(ItemStack.EMPTY).getString() : itemId.toString();
				byPath.put(path, new TextureTarget(path, itemId, TextureTargetKind.ITEM, name));
			}
		}
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof BlockItem) {
				continue;
			}
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			String path = "textures/item/" + id.getPath() + ".png";
			String resourcePath = "minecraft:" + path;
			byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.ITEM, item.getName(ItemStack.EMPTY).getString()));
		}
		return sorted(byPath);
	}

	private static List<TextureTarget> buildEntityTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		HistoricalCatalog catalog = HistoricalCatalog.get();
		for (String path : catalog.texturesByTarget().keySet()) {
			if (!path.contains("/textures/entity/")) {
				continue;
			}
			Identifier entityId = pathToEntityId(path);
			if (entityId != null) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId).map(h -> h.value()).orElse(null);
				String name = type != null ? type.getDescription().getString() : entityId.toString();
				byPath.put(path, new TextureTarget(path, entityId, TextureTargetKind.ENTITY, name));
			}
		}
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			String folder = id.getPath();
			String path = "textures/entity/" + folder + "/" + folder + ".png";
			String resourcePath = "minecraft:" + path;
			byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.ENTITY, type.getDescription().getString()));
		}
		return sorted(byPath);
	}

	private static List<String> buildSoundEvents() {
		Map<String, List<SoundVariant>> fromCatalog = HistoricalCatalog.get().soundsByEvent();
		List<String> events = new ArrayList<>(fromCatalog.keySet());
		events.sort(String.CASE_INSENSITIVE_ORDER);
		return List.copyOf(events);
	}

	private static List<TextureTarget> sorted(Map<String, TextureTarget> map) {
		List<TextureTarget> list = new ArrayList<>(map.values());
		list.sort(Comparator.comparing(TextureTarget::displayName, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(list);
	}

	private static Identifier pathToBlockId(String target) {
		String path = extractTexturePath(target);
		if (path == null) {
			return null;
		}
		String file = path.substring("textures/block/".length());
		if (file.endsWith(".png")) {
			file = file.substring(0, file.length() - 4);
		}
		return Identifier.tryParse("minecraft:" + file);
	}

	private static Identifier pathToItemId(String target) {
		String path = extractTexturePath(target);
		if (path == null) {
			return null;
		}
		String file = path.substring("textures/item/".length());
		if (file.endsWith(".png")) {
			file = file.substring(0, file.length() - 4);
		}
		return Identifier.tryParse("minecraft:" + file);
	}

	private static Identifier pathToEntityId(String target) {
		String path = extractTexturePath(target);
		if (path == null) {
			return null;
		}
		String remainder = path.substring("textures/entity/".length());
		int slash = remainder.indexOf('/');
		if (slash < 0) {
			return Identifier.tryParse("minecraft:" + remainder.replace(".png", ""));
		}
		return Identifier.tryParse("minecraft:" + remainder.substring(0, slash));
	}

	private static String extractTexturePath(String target) {
		String path = target;
		if (path.startsWith("minecraft:")) {
			path = path.substring("minecraft:".length());
		}
		if (path.startsWith("textures/")) {
			return path;
		}
		return null;
	}
}
