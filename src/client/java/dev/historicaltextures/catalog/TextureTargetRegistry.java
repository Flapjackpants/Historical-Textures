package dev.historicaltextures.catalog;

import dev.historicaltextures.HistoricalTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
	private static final int ENTRY_HEIGHT = 24;
	private static List<TextureTarget> blockTargets;
	private static List<TextureTarget> itemTargets;
	private static List<TextureTarget> entityTargets;
	private static List<String> soundEvents;

	private TextureTargetRegistry() {
	}

	public static List<TextureTarget> blockTargets() {
		if (blockTargets == null) {
			blockTargets = buildBlockTargets();
			HistoricalTextures.LOGGER.info("Config UI: {} block texture targets", blockTargets.size());
		}
		return blockTargets;
	}

	public static List<TextureTarget> itemTargets() {
		if (itemTargets == null) {
			itemTargets = buildItemTargets();
			HistoricalTextures.LOGGER.info("Config UI: {} item texture targets", itemTargets.size());
		}
		return itemTargets;
	}

	public static List<TextureTarget> entityTargets() {
		if (entityTargets == null) {
			entityTargets = buildEntityTargets();
			HistoricalTextures.LOGGER.info("Config UI: {} entity texture targets", entityTargets.size());
		}
		return entityTargets;
	}

	public static List<String> soundEvents() {
		if (soundEvents == null) {
			soundEvents = buildSoundEvents();
			HistoricalTextures.LOGGER.info("Config UI: {} sound events", soundEvents.size());
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
		addCatalogTargets(byPath, TextureTargetKind.BLOCK, "/textures/block/");
		enumerateBlocks(byPath);
		return sorted(byPath);
	}

	private static List<TextureTarget> buildItemTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		addCatalogTargets(byPath, TextureTargetKind.ITEM, "/textures/item/");
		enumerateItems(byPath);
		return sorted(byPath);
	}

	private static List<TextureTarget> buildEntityTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		addCatalogTargets(byPath, TextureTargetKind.ENTITY, "/textures/entity/");
		enumerateEntities(byPath);
		return sorted(byPath);
	}

	private static void enumerateBlocks(Map<String, TextureTarget> byPath) {
		Registry<Block> registry = blockRegistry();
		int added = 0;
		int failed = 0;
		for (Identifier id : registry.keySet()) {
			try {
				Block block = registry.getValue(id);
				if (block == null) {
					continue;
				}
				String resourcePath = "minecraft:textures/block/" + id.getPath() + ".png";
				byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.BLOCK, block.getName().getString()));
				added++;
			} catch (Exception exception) {
				failed++;
			}
		}
		if (failed > 0) {
			HistoricalTextures.LOGGER.warn("Skipped {} blocks while building config UI list", failed);
		}
		HistoricalTextures.LOGGER.debug("Enumerated {} blocks for config UI", added);
	}

	private static void enumerateItems(Map<String, TextureTarget> byPath) {
		Registry<Item> registry = itemRegistry();
		int added = 0;
		int failed = 0;
		for (Identifier id : registry.keySet()) {
			try {
				Item item = registry.getValue(id);
				if (item == null || item instanceof BlockItem) {
					continue;
				}
				String resourcePath = "minecraft:textures/item/" + id.getPath() + ".png";
				ItemStack stack = new ItemStack(item);
				String label = item.getName(stack).getString();
				byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.ITEM, label));
				added++;
			} catch (Exception exception) {
				failed++;
				HistoricalTextures.LOGGER.debug("Skipped item {}: {}", id, exception.toString());
			}
		}
		if (failed > 0) {
			HistoricalTextures.LOGGER.warn("Skipped {} items while building config UI list", failed);
		}
		HistoricalTextures.LOGGER.debug("Enumerated {} items for config UI", added);
	}

	private static void enumerateEntities(Map<String, TextureTarget> byPath) {
		Registry<EntityType<?>> registry = entityRegistry();
		int added = 0;
		int failed = 0;
		for (Identifier id : registry.keySet()) {
			try {
				EntityType<?> type = registry.getValue(id);
				if (type == null) {
					continue;
				}
				String folder = id.getPath();
				String resourcePath = "minecraft:textures/entity/" + folder + "/" + folder + ".png";
				byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.ENTITY, type.getDescription().getString()));
				added++;
			} catch (Exception exception) {
				failed++;
			}
		}
		if (failed > 0) {
			HistoricalTextures.LOGGER.warn("Skipped {} entities while building config UI list", failed);
		}
		HistoricalTextures.LOGGER.debug("Enumerated {} entities for config UI", added);
	}

	private static Registry<Block> blockRegistry() {
		return registry(Registries.BLOCK, BuiltInRegistries.BLOCK);
	}

	private static Registry<Item> itemRegistry() {
		return registry(Registries.ITEM, BuiltInRegistries.ITEM);
	}

	private static Registry<EntityType<?>> entityRegistry() {
		return registry(Registries.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE);
	}

	private static <T> Registry<T> registry(ResourceKey<? extends Registry<? extends T>> key, Registry<T> fallback) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			return minecraft.level.registryAccess().lookupOrThrow(key);
		}
		return fallback;
	}

	private static void addCatalogTargets(Map<String, TextureTarget> byPath, TextureTargetKind kind, String pathFragment) {
		for (Map.Entry<String, List<TextureVariant>> entry : HistoricalCatalog.get().texturesByTarget().entrySet()) {
			String target = entry.getKey();
			if (!target.contains(pathFragment)) {
				continue;
			}
			Identifier registryId = switch (kind) {
				case BLOCK -> pathToBlockId(target);
				case ITEM -> pathToItemId(target);
				case ENTITY -> pathToEntityId(target);
			};
			String label = labelForTarget(target, registryId, kind);
			byPath.putIfAbsent(target, new TextureTarget(target, registryId, kind, label));
		}
	}

	private static String labelForTarget(String target, Identifier registryId, TextureTargetKind kind) {
		if (registryId != null) {
			return switch (kind) {
				case BLOCK -> BuiltInRegistries.BLOCK.getOptional(registryId)
						.map(block -> block.getName().getString())
						.orElseGet(registryId::toString);
				case ITEM -> BuiltInRegistries.ITEM.getOptional(registryId)
						.map(item -> item.getName(new ItemStack(item)).getString())
						.orElseGet(registryId::toString);
				case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(registryId)
						.map(type -> type.getDescription().getString())
						.orElseGet(registryId::toString);
			};
		}
		String path = target;
		if (path.startsWith("minecraft:")) {
			path = path.substring("minecraft:".length());
		}
		return path;
	}

	private static List<String> buildSoundEvents() {
		Map<String, List<SoundVariant>> fromCatalog = HistoricalCatalog.get().soundsByEvent();
		List<String> events = new ArrayList<>(fromCatalog.keySet());
		events.sort(String.CASE_INSENSITIVE_ORDER);
		return List.copyOf(events);
	}

	private static List<TextureTarget> sorted(Map<String, TextureTarget> map) {
		List<TextureTarget> list = new ArrayList<>(map.values());
		list.sort(Comparator
				.comparingInt((TextureTarget target) -> HistoricalCatalog.get().variantCountForTarget(target.configKey()))
				.reversed()
				.thenComparing(TextureTarget::displayName, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(list);
	}

	public static int variantCount(String configKey) {
		return HistoricalCatalog.get().variantCountForTarget(configKey);
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

	public static int entryHeight() {
		return ENTRY_HEIGHT;
	}
}
