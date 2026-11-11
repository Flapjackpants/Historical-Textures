package dev.historicaltextures.indexer;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps minecraft.wiki {@code Category:X sounds} files (e.g. {@code Amethyst place1.ogg}) to registry sound events.
 */
public final class WikiSoundCategoryUtil {
	private static final Pattern TRAILING_INDEX = Pattern.compile("\\d+$");
	private static final List<String> COMPOUND_ACTIONS = List.of(
			// Longest phrases first (sorted below at runtime for matching)
			"convert to drowned",
			"step sand",
			"idle air",
			"smash ground",
			"smash air",
			"dash ready",
			"hit metal",
			"hit wood",
			"breaks door",
			"fill dragon breath",
			"fill water",
			"fence gate toggle",
			"trapdoor toggle",
			"door toggle",
			"button press",
			"empty",
			"fill",
			"dispose",
			"shatter",
			"dig",
			"destroy",
			"land",
			"use",
			"toggle",
			"press",
			"drip",
			"enter",
			"exit",
			"shear",
			"work",
			"click",
			"insert",
			"extract",
			"pickup",
			"pop",
			"ring",
			"scrape",
			"wax on",
			"wax off",
			"resonate",
			"shimmer",
			"chime",
			"place",
			"break",
			"step",
			"hurt",
			"death",
			"idle",
			"jump",
			"slide",
			"dash",
			"sit",
			"stand",
			"till",
			"peel",
			"flatten",
			"block",
			"smash",
			"ambient",
			"say",
			"attack",
			"shoot",
			"hit",
			"fall",
			"open",
			"close",
			"splash",
			"swim",
			"jump",
			"slide",
			"throw",
			"drink",
			"eat",
			"roar",
			"scream",
			"moan",
			"charge",
			"explode"
	);

	private static final Set<String> EXCLUDED_CATEGORIES = Set.of(
			"articles missing a sounds section",
			"articles with inline audio links",
			"pages missing sounds",
			"pages needing sounds",
			"pages needing historical sounds",
			"minecraft dungeons",
			"minecraft legends",
			"mega spud",
			"peelgrass",
			"gravtater",
			"potone",
			"event server sounds",
			"cave ambient sounds"
	);
	private static final Pattern EDITION_SUFFIX = Pattern.compile(
			"(?i)\\s+(?:JE|BE|TU)\\d+(?:\\s+(?:JE|BE|TU)\\d+)*\\s*$"
	);

	private static final Set<String> ENTITY_SUBJECTS = Set.of(
			"allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze", "camel", "camel_husk", "cat",
			"chicken", "cod", "cow", "creeper", "dolphin", "donkey", "drowned", "elder_guardian", "ender_dragon",
			"enderman", "endermite", "evoker", "fox", "frog", "ghast", "glow_squid", "goat", "guardian",
			"hoglin", "horse", "husk", "illusioner", "iron_golem", "llama", "magma_cube", "mooshroom", "mule",
			"ocelot", "panda", "parrot", "phantom", "pig", "piglin", "piglin_brute", "pillager", "polar_bear",
			"pufferfish", "rabbit", "ravager", "salmon", "sheep", "shulker", "silverfish", "skeleton", "slime",
			"sniffer", "snow_golem", "spider", "squid", "stray", "strider", "tadpole", "trader_llama", "tropical_fish",
			"turtle", "vex", "villager", "vindicator", "wandering_trader", "warden", "witch", "wither", "wither_skeleton",
			"wolf", "zoglin", "zombie", "zombie_horse", "zombie_villager", "zombified_piglin"
	);

	private WikiSoundCategoryUtil() {
	}

	public static boolean isExcludedCategory(String categoryTitle) {
		if (categoryTitle == null) {
			return true;
		}
		String lower = categoryTitle.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(" sounds")) {
			return true;
		}
		for (String excluded : EXCLUDED_CATEGORIES) {
			if (lower.contains(excluded)) {
				return true;
			}
		}
		return false;
	}

	public static Optional<WikiSoundUtil.SoundMapping> mapCategoryFile(String categoryTitle, String fileName) {
		if (categoryTitle == null || fileName == null) {
			return Optional.empty();
		}
		String subjectLabel = subjectFromCategory(categoryTitle);
		if (subjectLabel.isEmpty()) {
			return Optional.empty();
		}
		ParsedFileName parsed = parseFileName(fileName);
		if (parsed.action().isEmpty()) {
			return Optional.empty();
		}
		SoundDomain domain = domainFor(categoryTitle, subjectLabel, parsed.action());
		String registryId = toRegistryId(subjectLabel, parsed.subjectOverride(), domain);
		String eventAction = toEventAction(parsed.action(), domain);
		if (registryId.isEmpty() || eventAction.isEmpty()) {
			return Optional.empty();
		}
		String soundEvent = domain.prefix() + registryId + "." + eventAction;
		String vanillaPath = guessVanillaPath(soundEvent, fileName, domain, registryId, eventAction);
		String versionHint = WikiSoundUtil.inferVersionLabel(fileName, "");
		return Optional.of(new WikiSoundUtil.SoundMapping(soundEvent, vanillaPath, versionHint));
	}

	private static SoundDomain domainFor(String categoryTitle, String subjectLabel, String action) {
		String lower = categoryTitle.toLowerCase(Locale.ROOT);
		if (lower.startsWith("category:item ") || lower.contains("item sounds")) {
			return SoundDomain.ITEM;
		}
		if (lower.startsWith("category:block ") && !lower.contains(" sounds")) {
			return SoundDomain.BLOCK;
		}
		String snake = toSnakeCase(subjectLabel);
		if (ENTITY_SUBJECTS.contains(snake) || isEntityAction(action)) {
			return SoundDomain.ENTITY;
		}
		if (isBlockAction(action)) {
			return SoundDomain.BLOCK;
		}
		return SoundDomain.ENTITY;
	}

	private static boolean isEntityAction(String action) {
		return switch (action) {
			case "hurt", "death", "idle", "idle air", "say", "ambient", "attack", "roar", "scream", "moan", "charge",
					"shoot", "jump", "slide", "dash", "sit", "stand", "smash", "smash ground", "smash air",
					"step sand" -> true;
			default -> false;
		};
	}

	private static boolean isBlockAction(String action) {
		return switch (action) {
			case "place", "break", "step", "hit", "fall", "resonate", "shimmer", "chime", "shatter",
					"dig", "destroy", "land", "use", "toggle", "press", "drip", "enter", "exit", "shear", "work",
					"click", "insert", "extract", "pickup", "pop", "ring", "scrape", "wax on", "wax off",
					"door toggle", "trapdoor toggle", "fence gate toggle", "button press" -> true;
			default -> false;
		};
	}

	private static String subjectFromCategory(String categoryTitle) {
		String name = categoryTitle;
		if (name.startsWith("Category:")) {
			name = name.substring("Category:".length());
		}
		if (name.endsWith(" sounds")) {
			name = name.substring(0, name.length() - " sounds".length());
		}
		if (name.regionMatches(true, 0, "Old ", 0, 4)) {
			name = name.substring(4);
		}
		return name.trim();
	}

	private static ParsedFileName parseFileName(String fileName) {
		String base = fileName;
		int dot = base.lastIndexOf('.');
		if (dot >= 0) {
			base = base.substring(0, dot);
		}
		base = stripEditionMarkers(base);
		String lower = base.toLowerCase(Locale.ROOT);
		List<String> actions = COMPOUND_ACTIONS.stream()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.toList();
		for (String action : actions) {
			String pattern = ".*" + Pattern.quote(action) + "\\d*$";
			if (lower.matches(pattern)) {
				int actionStart = lower.lastIndexOf(action);
				String prefix = base.substring(0, actionStart).trim();
				return new ParsedFileName(action, stripTrailingIndex(prefix));
			}
		}
		return new ParsedFileName("", "");
	}

	private static String stripEditionMarkers(String base) {
		Matcher matcher = EDITION_SUFFIX.matcher(base);
		if (matcher.find()) {
			return base.substring(0, matcher.start()).trim();
		}
		return base.trim();
	}

	private static String stripTrailingIndex(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		Matcher matcher = TRAILING_INDEX.matcher(value);
		if (matcher.find()) {
			return value.substring(0, matcher.start()).trim();
		}
		return value.trim();
	}

	private static String toRegistryId(String categorySubject, String fileSubjectOverride, SoundDomain domain) {
		String label = fileSubjectOverride != null && !fileSubjectOverride.isEmpty()
				? fileSubjectOverride
				: categorySubject;
		String snake = toSnakeCase(label);
		if (snake.isEmpty()) {
			return "";
		}
		if (domain == SoundDomain.BLOCK) {
			if (snake.endsWith("_block") || snake.endsWith("_cluster") || snake.contains("_ore")
					|| snake.endsWith("_bricks") || snake.endsWith("_log") || snake.endsWith("_wood")) {
				return snake;
			}
			if (!snake.contains("_")) {
				return snake + "_block";
			}
		}
		return snake;
	}

	private static String toEventAction(String action, SoundDomain domain) {
		return switch (action) {
			case "idle", "say" -> domain == SoundDomain.ENTITY ? "ambient" : action;
			case "idle air" -> "idle_air";
			case "smash ground", "smash air", "smash" -> "smash";
			case "step sand" -> "step";
			case "dash ready" -> "dash_ready";
			case "dash" -> "dash";
			case "till" -> "till";
			case "peel" -> "peel";
			case "flatten" -> "flatten";
			case "block" -> "block";
			case "hit metal" -> "attack_iron_door";
			case "hit wood" -> "break_wooden_door";
			case "breaks door" -> "break_wooden_door";
			case "convert to drowned" -> "converted_to_drowned";
			case "fill water", "fill dragon breath", "fill" -> "fill";
			case "empty" -> "empty";
			case "dispose" -> "dispose";
			case "resonate", "shimmer", "chime" -> "chime";
			case "shatter", "dig", "destroy" -> "break";
			case "land" -> "fall";
			case "use" -> "use";
			case "door toggle", "trapdoor toggle", "fence gate toggle", "toggle" -> "open";
			case "button press", "press", "click" -> "click";
			case "drip" -> "drip";
			case "enter" -> "enter";
			case "exit" -> "exit";
			case "shear" -> "shear";
			case "work" -> "work";
			case "insert" -> "insert";
			case "extract" -> "extract";
			case "pickup" -> "pickup";
			case "pop" -> "pop";
			case "ring" -> "ring";
			case "scrape" -> "scrape";
			case "wax on" -> "wax_on";
			case "wax off" -> "wax_off";
			case "shoot" -> domain == SoundDomain.ENTITY ? "shoot" : action;
			default -> action.replace(' ', '_');
		};
	}

	private static String guessVanillaPath(
			String soundEvent,
			String fileName,
			SoundDomain domain,
			String registryId,
			String eventAction
	) {
		String index = "1";
		Matcher matcher = TRAILING_INDEX.matcher(fileName.replaceFirst("(?i)\\.ogg$", "").replaceFirst("(?i)\\.wav$", ""));
		if (matcher.find()) {
			index = matcher.group();
		}
		String mobAction = switch (eventAction) {
			case "ambient" -> "say";
			default -> eventAction;
		};
		return switch (domain) {
			case BLOCK -> "sounds/block/" + registryId + "/" + eventAction + index + ".ogg";
			case ENTITY -> "sounds/mob/" + registryId + "/" + mobAction + index + ".ogg";
			case ITEM -> "sounds/item/" + registryId + "/" + eventAction + index + ".ogg";
		};
	}

	private static String toSnakeCase(String label) {
		if (label == null || label.isBlank()) {
			return "";
		}
		String normalized = label.trim()
				.replace("'", "")
				.replace("\"", "")
				.replace("-", " ");
		normalized = normalized.replaceAll("\\s+", " ");
		return normalized.toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	private record ParsedFileName(String action, String subjectOverride) {
	}

	private enum SoundDomain {
		BLOCK("block."),
		ENTITY("entity."),
		ITEM("item.");

		private final String prefix;

		SoundDomain(String prefix) {
			this.prefix = prefix;
		}

		String prefix() {
			return prefix;
		}
	}
}
