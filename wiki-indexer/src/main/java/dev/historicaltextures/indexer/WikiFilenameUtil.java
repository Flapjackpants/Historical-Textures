package dev.historicaltextures.indexer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiFilenameUtil {
	private static final Pattern TEXTURE_SUFFIX = Pattern.compile(
			"\\s*\\((?:top|side|bottom|front|back|overlay|particle|texture)[^)]*\\)",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TAG_PATTERN = Pattern.compile("(?:^|[\\s_])(JE|BE|TU)(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern EDITION_PATTERN = Pattern.compile("(?:^|[\\s_])(JE|BE|TU)(\\d+)", Pattern.CASE_INSENSITIVE);

	public enum CrawlContext {
		BLOCKS,
		ITEMS,
		ENTITIES,
		UNKNOWN
	}

	private WikiFilenameUtil() {
	}

	public static String normalizeKey(String fileName) {
		if (fileName == null) {
			return "";
		}
		String value = fileName.trim();
		if (value.regionMatches(true, 0, "File:", 0, 5)) {
			value = value.substring(5).trim();
		}
		return value.replace(' ', '_').toLowerCase(Locale.ROOT);
	}

	public static boolean isTextureFile(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".png")) {
			return false;
		}
		return lower.contains("(texture)")
				|| lower.contains("(top_texture)")
				|| lower.contains("(side_texture)")
				|| lower.contains("(bottom_texture)")
				|| lower.contains("(front_texture)")
				|| lower.contains("(back_texture)")
				|| lower.contains("(overlay_texture)")
				|| lower.contains("(particle_texture)");
	}

	public static CrawlContext parsePageContext(String pageTitle) {
		String lower = pageTitle.toLowerCase(Locale.ROOT);
		if (lower.contains("/blocks") || lower.contains("block_textures")) {
			return CrawlContext.BLOCKS;
		}
		if (lower.contains("/items") || lower.contains("item_textures")) {
			return CrawlContext.ITEMS;
		}
		if (lower.contains("/entities") || lower.contains("entity_textures")) {
			return CrawlContext.ENTITIES;
		}
		return CrawlContext.UNKNOWN;
	}

	public static String extractBaseName(String fileName) {
		String name = fileName;
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		Matcher suffixMatcher = TEXTURE_SUFFIX.matcher(name);
		if (suffixMatcher.find()) {
			name = name.substring(0, suffixMatcher.start()).trim();
		}
		return name.replace('_', ' ').trim();
	}

	public static String toRegistryPath(String displayName) {
		return displayName.trim()
				.replace(' ', '_')
				.replaceAll("[^a-zA-Z0-9_]", "")
				.toLowerCase(Locale.ROOT);
	}

	public static String textureSuffix(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.contains("(top_texture)")) {
			return "_top";
		}
		if (lower.contains("(side_texture)")) {
			return "_side";
		}
		if (lower.contains("(bottom_texture)")) {
			return "_bottom";
		}
		if (lower.contains("(front_texture)")) {
			return "_front";
		}
		if (lower.contains("(back_texture)")) {
			return "_back";
		}
		if (lower.contains("(overlay_texture)")) {
			return "_overlay";
		}
		if (lower.contains("(particle_texture)")) {
			return "_particle";
		}
		return "";
	}

	public static List<String> guessTargets(String fileName, CrawlContext context) {
		String baseName = extractBaseName(fileName);
		if (baseName.isEmpty()) {
			return List.of();
		}
		String registryPath = toRegistryPath(baseName);
		String suffix = textureSuffix(fileName);
		List<String> guesses = new ArrayList<>();
		switch (context) {
			case BLOCKS -> guesses.add("minecraft:textures/block/" + registryPath + suffix + ".png");
			case ITEMS -> guesses.add("minecraft:textures/item/" + registryPath + suffix + ".png");
			case ENTITIES -> {
				String folder = registryPath;
				guesses.add("minecraft:textures/entity/" + folder + "/" + registryPath + suffix + ".png");
			}
			case UNKNOWN -> {
				if (!suffix.isEmpty()) {
					guesses.add("minecraft:textures/block/" + registryPath + suffix + ".png");
				} else {
					guesses.add("minecraft:textures/block/" + registryPath + ".png");
					guesses.add("minecraft:textures/item/" + registryPath + ".png");
				}
			}
		}
		return List.copyOf(guesses);
	}

	public static List<String> parseTextureTags(String fileName) {
		Set<String> tags = new LinkedHashSet<>();
		Matcher matcher = TAG_PATTERN.matcher(fileName);
		while (matcher.find()) {
			tags.add(matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2));
		}
		return List.copyOf(tags);
	}

	public static List<String> parseEditions(String fileName) {
		Set<String> editions = new LinkedHashSet<>();
		Matcher matcher = EDITION_PATTERN.matcher(fileName);
		while (matcher.find()) {
			editions.add(matcher.group(1).toUpperCase(Locale.ROOT));
		}
		if (editions.isEmpty()) {
			editions.add("JE");
		}
		return List.copyOf(editions);
	}

	public static String humanLabel(String fileName) {
		return fileName.replace('_', ' ');
	}

	public static String toId(String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase(Locale.ROOT);
	}

	public static String fallbackDisplayVersion(String fileName, List<String> textureTags) {
		if (!textureTags.isEmpty()) {
			return String.join(" / ", textureTags);
		}
		List<String> tags = parseTextureTags(fileName);
		if (!tags.isEmpty()) {
			return String.join(" / ", tags);
		}
		return humanLabel(fileName);
	}
}
