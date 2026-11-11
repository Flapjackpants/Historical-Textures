package dev.historicaltextures.indexer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads texture paths and sound events from the vanilla client assets in Loom's cache.
 */
public final class VanillaAssets {
	private static final String TEXTURE_ROOT = "assets/minecraft/textures/";
	private static final String TEXTURE_PREFIX = "minecraft:";
	private static final List<String> ALL_TEXTURE_DIRS = List.of(
			"textures/block/",
			"textures/item/",
			"textures/entity/",
			"textures/particle/",
			"textures/painting/",
			"textures/mob_effect/",
			"textures/map/"
	);

	private final Set<String> texturePaths;
	private final Set<String> soundEvents;

	VanillaAssets(Set<String> texturePaths, Set<String> soundEvents) {
		this.texturePaths = Set.copyOf(texturePaths);
		this.soundEvents = Set.copyOf(soundEvents);
	}

	public static Optional<VanillaAssets> load(Path loomCacheDir, String minecraftVersion) {
		if (loomCacheDir == null || minecraftVersion == null || minecraftVersion.isBlank()) {
			return Optional.empty();
		}
		Path jar = loomCacheDir.resolve(minecraftVersion).resolve("minecraft-client.jar");
		if (!Files.isRegularFile(jar)) {
			return Optional.empty();
		}
		Set<String> textures = new TreeSet<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (name.startsWith(TEXTURE_ROOT)
						&& name.toLowerCase(Locale.ROOT).endsWith(".png")
						&& !entry.isDirectory()) {
					textures.add(name.substring("assets/minecraft/".length()));
				}
			}
		} catch (IOException exception) {
			return Optional.empty();
		}
		return Optional.of(new VanillaAssets(textures, loadSoundEvents(loomCacheDir, minecraftVersion)));
	}

	private static Set<String> loadSoundEvents(Path loomCacheDir, String minecraftVersion) {
		try {
			Path indexFile = findIndexFile(loomCacheDir.resolve("assets").resolve("indexes"), minecraftVersion);
			if (indexFile == null) {
				return Set.of();
			}
			JsonObject index = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject();
			JsonObject objects = index.getAsJsonObject("objects");
			if (objects == null) {
				return Set.of();
			}
			JsonElement soundsEntry = objects.get("minecraft/sounds.json");
			if (soundsEntry == null || !soundsEntry.isJsonObject()) {
				return Set.of();
			}
			JsonElement hashElement = soundsEntry.getAsJsonObject().get("hash");
			if (hashElement == null) {
				return Set.of();
			}
			String hash = hashElement.getAsString();
			if (hash.length() < 2) {
				return Set.of();
			}
			Path objectFile = loomCacheDir.resolve("assets").resolve("objects")
					.resolve(hash.substring(0, 2)).resolve(hash);
			if (!Files.isRegularFile(objectFile)) {
				return Set.of();
			}
			JsonObject sounds = JsonParser.parseString(Files.readString(objectFile)).getAsJsonObject();
			return Set.copyOf(sounds.keySet());
		} catch (Exception exception) {
			return Set.of();
		}
	}

	private static Path findIndexFile(Path indexDir, String minecraftVersion) throws IOException {
		if (!Files.isDirectory(indexDir)) {
			return null;
		}
		List<Path> candidates = new ArrayList<>();
		try (Stream<Path> stream = Files.list(indexDir)) {
			stream.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.filter(path -> {
						String name = path.getFileName().toString();
						return name.startsWith(minecraftVersion + "-") || name.equals(minecraftVersion + ".json");
					})
					.forEach(candidates::add);
		}
		candidates.sort((left, right) -> left.getFileName().toString()
				.compareTo(right.getFileName().toString()));
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	public Set<String> soundEvents() {
		return soundEvents;
	}

	public Set<String> texturePaths() {
		return texturePaths;
	}

	public List<String> resolveTexture(String name, WikiFilenameUtil.CrawlContext context) {
		String registryPath = WikiFilenameUtil.toRegistryPath(name);
		if (registryPath.isEmpty()) {
			return List.of();
		}
		String fileName = registryPath + ".png";
		for (String dir : preferredDirs(context)) {
			List<String> matches = matchingUnder(dir, fileName);
			if (!matches.isEmpty()) {
				return matches;
			}
		}
		Set<String> basenameMatches = new TreeSet<>();
		for (String dir : ALL_TEXTURE_DIRS) {
			basenameMatches.addAll(matchingUnder(dir, fileName));
		}
		if (!basenameMatches.isEmpty()) {
			return List.copyOf(basenameMatches);
		}
		if (context == WikiFilenameUtil.CrawlContext.ENTITIES
				|| context == WikiFilenameUtil.CrawlContext.UNKNOWN) {
			return entityVariants(registryPath);
		}
		return List.of();
	}

	private List<String> preferredDirs(WikiFilenameUtil.CrawlContext context) {
		return switch (context) {
			case BLOCKS -> List.of("textures/block/");
			case ITEMS -> List.of("textures/item/");
			case ENTITIES -> List.of("textures/entity/");
			case PARTICLES -> List.of("textures/particle/");
			case PAINTINGS -> List.of("textures/painting/");
			case EFFECTS -> List.of("textures/mob_effect/");
			case MAP_ICONS -> List.of("textures/map/");
			case UNKNOWN -> List.of();
		};
	}

	private List<String> matchingUnder(String dir, String fileName) {
		Set<String> matches = new TreeSet<>();
		for (String path : texturePaths) {
			if (!path.startsWith(dir)) {
				continue;
			}
			if (fileNameOf(path).equals(fileName)) {
				matches.add(TEXTURE_PREFIX + path);
			}
		}
		return List.copyOf(matches);
	}

	private List<String> entityVariants(String registryPath) {
		String dir = "textures/entity/" + registryPath + "/";
		Set<String> matches = new TreeSet<>();
		for (String path : texturePaths) {
			if (!path.startsWith(dir)) {
				continue;
			}
			String fileName = fileNameOf(path);
			if (fileName.startsWith(registryPath + "_") && !fileName.contains("_baby")) {
				matches.add(TEXTURE_PREFIX + path);
			}
		}
		return List.copyOf(new LinkedHashSet<>(matches));
	}

	private static String fileNameOf(String path) {
		int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}
}
