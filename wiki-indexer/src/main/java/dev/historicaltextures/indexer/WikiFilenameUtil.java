package dev.historicaltextures.indexer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiFilenameUtil {
	private static final String EDITION_GROUP = "(JE|BE|PE|TU|CU|EE|LCE)";
	private static final Pattern TAG_PATTERN = Pattern.compile(
			"(?:^|[\\s_])" + EDITION_GROUP + "(\\d+)",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern EDITION_PATTERN = Pattern.compile(
			"(?:^|[\\s_])" + EDITION_GROUP + "(\\d+)",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern EDITION_TAG_TOKEN = Pattern.compile(
			"(?i)\\s+" + EDITION_GROUP + "\\d+"
	);
	private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]*)\\)");

	public enum CrawlContext {
		BLOCKS,
		ITEMS,
		ENTITIES,
		PARTICLES,
		PAINTINGS,
		EFFECTS,
		MAP_ICONS,
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
		return isTextureFile(fileName, CrawlContext.UNKNOWN);
	}

	public static boolean isTextureFile(String fileName, CrawlContext context) {
		if (fileName == null) {
			return false;
		}
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".png")) {
			return false;
		}
		String spaced = lower.replace('_', ' ');
		Matcher parenthetical = PARENTHETICAL.matcher(spaced);
		while (parenthetical.find()) {
			String content = parenthetical.group(1).trim();
			if (content.contains("texture") || content.equals("item") || content.equals("held")) {
				return true;
			}
		}
		return context != CrawlContext.UNKNOWN && TAG_PATTERN.matcher(spaced).find();
	}

	public static CrawlContext parsePageContext(String pageTitle) {
		String lower = pageTitle.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (lower.contains("/blocks") || lower.contains("block_textures")) {
			return CrawlContext.BLOCKS;
		}
		if (lower.contains("/items") || lower.contains("item_textures") || lower.contains("item_texture_history")) {
			return CrawlContext.ITEMS;
		}
		if (lower.contains("/entities") || lower.contains("entity_textures")) {
			return CrawlContext.ENTITIES;
		}
		if (lower.contains("/particles")) {
			return CrawlContext.PARTICLES;
		}
		if (lower.contains("/paintings")) {
			return CrawlContext.PAINTINGS;
		}
		if (lower.contains("/effects")) {
			return CrawlContext.EFFECTS;
		}
		if (lower.contains("/map_icons")) {
			return CrawlContext.MAP_ICONS;
		}
		return CrawlContext.UNKNOWN;
	}

	public static String extractBaseName(String fileName) {
		String name = fileName;
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		int parenthetical = name.indexOf('(');
		if (parenthetical >= 0) {
			name = name.substring(0, parenthetical);
		}
		name = EDITION_TAG_TOKEN.matcher(name).replaceAll("");
		return name.replace('_', ' ').trim();
	}

	public static String toRegistryPath(String displayName) {
		return displayName.trim()
				.replace(' ', '_')
				.replaceAll("[^a-zA-Z0-9_]", "")
				.toLowerCase(Locale.ROOT);
	}

	public static String textureSuffix(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT).replace('_', ' ');
		if (lower.contains("(top texture")) {
			return "_top";
		}
		if (lower.contains("(side texture")) {
			return "_side";
		}
		if (lower.contains("(bottom texture")) {
			return "_bottom";
		}
		if (lower.contains("(front texture")) {
			return "_front";
		}
		if (lower.contains("(back texture")) {
			return "_back";
		}
		if (lower.contains("(overlay texture")) {
			return "_overlay";
		}
		if (lower.contains("(particle texture")) {
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
			case PARTICLES -> guesses.add("minecraft:textures/particle/" + registryPath + suffix + ".png");
			case PAINTINGS -> guesses.add("minecraft:textures/painting/" + registryPath + suffix + ".png");
			case EFFECTS -> guesses.add("minecraft:textures/mob_effect/" + registryPath + suffix + ".png");
			case MAP_ICONS -> guesses.add("minecraft:textures/map/decorations/" + registryPath + suffix + ".png");
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
