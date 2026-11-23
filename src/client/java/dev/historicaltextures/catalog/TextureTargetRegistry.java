package dev.historicaltextures.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.config.TextureGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public final class TextureTargetRegistry {
	private static final int ENTRY_HEIGHT = 26;
	private static List<TextureTarget> blockTargets;
	private static List<TextureTarget> itemTargets;
	private static List<TextureTarget> entityTargets;
	private static List<TextureTarget> otherTargets;
	private static List<String> soundEvents;
	private static Map<String, List<TextureGroups.GroupVersion>> groupVersions;

	private TextureTargetRegistry() {
	}

	public static String humanize(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
		String s = path;
		int lastSlash = s.lastIndexOf('/');
		if (lastSlash >= 0) {
			s = s.substring(lastSlash + 1);
		}
		if (s.endsWith(".png")) {
			s = s.substring(0, s.length() - ".png".length());
		}
		int colon = s.indexOf(':');
		if (colon >= 0) {
			s = s.substring(colon + 1);
		}
		String[] parts = s.split("_");
		List<String> words = new ArrayList<>();
		for (String part : parts) {
			if (!part.isEmpty()) {
				String capitalized = Character.toUpperCase(part.charAt(0))
						+ (part.length() > 1 ? part.substring(1).toLowerCase(Locale.ROOT) : "");
				words.add(capitalized);
			}
		}
		return String.join(" ", words);
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

	public static List<TextureTarget> otherTargets() {
		if (otherTargets == null) {
			otherTargets = buildOtherTargets();
			HistoricalTextures.LOGGER.info("Config UI: {} other texture targets", otherTargets.size());
		}
		return otherTargets;
	}

	public static void reload() {
		blockTargets = null;
		itemTargets = null;
		entityTargets = null;
		otherTargets = null;
		soundEvents = null;
		groupVersions = null;
	}

	private static List<TextureTarget> buildBlockTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		addCatalogTargets(byPath, TextureTargetKind.BLOCK, "textures/block/");
		enumerateBlocks(byPath);
		applyBlockGroups(byPath);
		return sorted(byPath);
	}

	private static void applyBlockGroups(Map<String, TextureTarget> byPath) {
		Registry<Block> registry = blockRegistry();
		Set<String> allBlockPaths = new TreeSet<>();
		for (Identifier id : registry.keySet()) {
			if ("minecraft".equals(id.getNamespace())) {
				allBlockPaths.add(id.getPath());
			}
		}
		Function<String, Optional<JsonObject>> loader = TextureTargetRegistry::loadJsonResource;
		Map<String, List<TextureGroups.GroupVersion>> versions = new HashMap<>();
		int groups = 0;
		for (Identifier id : registry.keySet()) {
			if (!"minecraft".equals(id.getNamespace())) {
				continue;
			}
			try {
				Block block = registry.getValue(id);
				if (block == null) {
					continue;
				}
				String path = id.getPath();
				Set<String> textures = BlockModelTextures.collect(path, loader);
				List<String> members = TextureGroups.members(path, textures, allBlockPaths);
				if (members.size() < 2) {
					continue;
				}
				for (String member : members) {
					byPath.remove(member);
				}
				byPath.remove("minecraft:textures/block/" + path + ".png");
				TextureTarget group = new TextureTarget(
						"minecraft:textures/block/" + path + ".png",
						id,
						TextureTargetKind.BLOCK,
						block.getName().getString(),
						members
				);
				byPath.put(group.configKey(), group);
				versions.put(group.configKey(),
						TextureGroups.versions(members, HistoricalCatalog.get().texturesByTarget()));
				groups++;
			} catch (Exception exception) {
				HistoricalTextures.LOGGER.debug("Skipped block group {}: {}", id, exception.toString());
			}
		}
		groupVersions = versions;
		HistoricalTextures.LOGGER.info("Config UI: {} block groups", groups);
	}

	private static Optional<JsonObject> loadJsonResource(String path) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.getResourceManager() == null) {
				return Optional.empty();
			}
			Optional<Resource> resource = minecraft.getResourceManager()
					.getResource(Identifier.parse(path));
			if (resource.isEmpty()) {
				return Optional.empty();
			}
			try (var stream = resource.get().open()) {
				return Optional.of(JsonParser.parseReader(
						new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
			}
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	public static List<TextureGroups.GroupVersion> groupVersions(String configKey) {
		if (groupVersions == null) {
			blockTargets();
		}
		Map<String, List<TextureGroups.GroupVersion>> versions = groupVersions;
		return versions == null ? List.of() : versions.getOrDefault(configKey, List.of());
	}

	private static List<TextureTarget> buildItemTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		addCatalogTargets(byPath, TextureTargetKind.ITEM, "textures/item/");
		enumerateItems(byPath);
		return sorted(byPath);
	}

	private static List<TextureTarget> buildEntityTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		addCatalogTargets(byPath, TextureTargetKind.ENTITY, "textures/entity/");
		enumerateEntities(byPath);
		return sorted(byPath);
	}

	private static List<TextureTarget> buildOtherTargets() {
		Map<String, TextureTarget> byPath = new LinkedHashMap<>();
		for (String target : HistoricalCatalog.get().texturesByTarget().keySet()) {
			if (!isOtherTarget(target)) {
				continue;
			}
			byPath.putIfAbsent(target, new TextureTarget(target, null, TextureTargetKind.OTHER, otherLabel(target)));
		}
		return sorted(byPath);
	}

	private static boolean isOtherTarget(String target) {
		String path = target;
		if (path.startsWith("minecraft:")) {
			path = path.substring("minecraft:".length());
		}
		return !path.startsWith("textures/block/")
				&& !path.startsWith("textures/item/")
				&& !path.startsWith("textures/entity/");
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
				String descId = item.getDescriptionId();
				String label = net.minecraft.locale.Language.getInstance().has(descId)
						? Component.translatable(descId).getString()
						: humanize(resourcePath);
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
				String label = entityLabel(resourcePath, id, type.getDescription().getString());
				byPath.putIfAbsent(resourcePath, new TextureTarget(resourcePath, id, TextureTargetKind.ENTITY, label));
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
				case OTHER -> null;
			};
			String label = labelForTarget(target, registryId, kind);
			byPath.putIfAbsent(target, new TextureTarget(target, registryId, kind, label));
		}
	}

	private static String labelForTarget(String target, Identifier registryId, TextureTargetKind kind) {
		if (kind == TextureTargetKind.OTHER) {
			return otherLabel(target);
		}
		if (registryId != null) {
			return switch (kind) {
				case BLOCK -> BuiltInRegistries.BLOCK.getOptional(registryId)
						.map(block -> block.getName().getString())
						.orElseGet(() -> humanize(target));
				case ITEM -> BuiltInRegistries.ITEM.getOptional(registryId)
						.map(item -> {
							String descId = item.getDescriptionId();
							return net.minecraft.locale.Language.getInstance().has(descId)
									? Component.translatable(descId).getString()
									: humanize(target);
						})
						.orElseGet(() -> humanize(target));
				case ENTITY -> {
					String baseName = BuiltInRegistries.ENTITY_TYPE.getOptional(registryId)
							.map(type -> type.getDescription().getString())
							.orElseGet(() -> humanize(target));
					yield entityLabel(target, registryId, baseName);
				}
				case OTHER -> otherLabel(target);
			};
		}
		return humanize(target);
	}

	private static String otherLabel(String target) {
		String path = target;
		if (path.startsWith("minecraft:")) {
			path = path.substring("minecraft:".length());
		}
		if (path.startsWith("textures/")) {
			path = path.substring("textures/".length());
		}
		int slash = path.indexOf('/');
		if (slash < 0) {
			return humanize(path);
		}
		String firstFolder = path.substring(0, slash);
		String file = path.substring(path.lastIndexOf('/') + 1);
		return humanize(file) + " (" + humanize(firstFolder) + ")";
	}

	private static List<String> buildSoundEvents() {
		Map<String, List<SoundVariant>> fromCatalog = HistoricalCatalog.get().soundsByEvent();
		List<String> events = new ArrayList<>(fromCatalog.keySet());
		events.sort(String.CASE_INSENSITIVE_ORDER);
		return List.copyOf(events);
	}

	private static String extractStem(String target) {
		String s = target;
		int lastSlash = s.lastIndexOf('/');
		if (lastSlash >= 0) {
			s = s.substring(lastSlash + 1);
		}
		if (s.endsWith(".png")) {
			s = s.substring(0, s.length() - ".png".length());
		}
		int colon = s.indexOf(':');
		if (colon >= 0) {
			s = s.substring(colon + 1);
		}
		return s;
	}

	private static String entityLabel(String target, Identifier registryId, String baseName) {
		if (registryId == null) {
			return humanize(target);
		}
		String stem = extractStem(target);
		String idPath = registryId.getPath();
		if (stem.equalsIgnoreCase(idPath)) {
			return baseName;
		}
		String[] idParts = idPath.toLowerCase(Locale.ROOT).split("_");
		Set<String> idTokens = new HashSet<>();
		for (String part : idParts) {
			if (!part.isEmpty()) {
				idTokens.add(part);
			}
		}
		String[] stemParts = stem.split("_");
		List<String> remaining = new ArrayList<>();
		for (String part : stemParts) {
			if (!part.isEmpty() && !idTokens.contains(part.toLowerCase(Locale.ROOT))) {
				remaining.add(part);
			}
		}
		String qualifier;
		if (remaining.isEmpty()) {
			qualifier = humanize(stem);
		} else {
			qualifier = humanize(String.join("_", remaining));
		}
		return baseName + " (" + qualifier + ")";
	}

	private static List<TextureTarget> sorted(Map<String, TextureTarget> map) {
		Map<String, Integer> counts = new HashMap<>();
		for (TextureTarget target : map.values()) {
			counts.merge(target.displayName().toLowerCase(Locale.ROOT), 1, Integer::sum);
		}

		List<TextureTarget> list = new ArrayList<>();
		for (TextureTarget target : map.values()) {
			if (counts.get(target.displayName().toLowerCase(Locale.ROOT)) > 1) {
				String extra = humanize(target.resourcePath());
				if (!extra.isEmpty()) {
					String newName = target.displayName() + " (" + extra + ")";
					if (!newName.equalsIgnoreCase(target.displayName())) {
						target = new TextureTarget(target.resourcePath(), target.registryId(), target.kind(), newName);
					}
				}
			}
			list.add(target);
		}

		Map<String, Integer> secondCounts = new HashMap<>();
		for (TextureTarget target : list) {
			secondCounts.merge(target.displayName().toLowerCase(Locale.ROOT), 1, Integer::sum);
		}
		if (secondCounts.values().stream().anyMatch(c -> c > 1)) {
			List<TextureTarget> resolved = new ArrayList<>();
			for (TextureTarget target : list) {
				if (secondCounts.get(target.displayName().toLowerCase(Locale.ROOT)) > 1) {
					String path = target.resourcePath();
					if (path.startsWith("minecraft:")) {
						path = path.substring("minecraft:".length());
					}
					if (path.startsWith("textures/")) {
						path = path.substring("textures/".length());
					}
					if (path.endsWith(".png")) {
						path = path.substring(0, path.length() - 4);
					}
					String extra = humanize(path.replace('/', '_'));
					String newName = target.displayName() + " (" + extra + ")";
					target = new TextureTarget(target.resourcePath(), target.registryId(), target.kind(), newName);
				}
				resolved.add(target);
			}
			list = resolved;
		}

		list.sort(Comparator
				.comparingInt((TextureTarget target) -> variantCount(target.configKey()))
				.reversed()
				.thenComparing(TextureTarget::displayName, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(list);
	}

	public static int variantCount(String configKey) {
		if (configKey != null && configKey.startsWith("group:")) {
			if (groupVersions == null) {
				blockTargets();
			}
			Map<String, List<TextureGroups.GroupVersion>> versions = groupVersions;
			return versions == null ? 0 : versions.getOrDefault(configKey, List.of()).size();
		}
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
