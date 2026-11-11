package dev.historicaltextures.indexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps pre-1.9 (and Bedrock-style) sound event names to current Java events.
 *
 * <p>Renames and known events are read from the wiki's sound event history page. Everything here
 * is pure and offline so it can be unit tested without network access.
 */
public final class LegacySoundEvents {
	private static final Pattern CD_TEMPLATE = Pattern.compile("\\{\\{cd\\|([^}]*)\\}\\}");
	private static final Pattern RENAME = Pattern.compile(
			"\\{\\{cd\\|([a-z0-9_.]+)\\}\\}\\s+to\\s+\\{\\{cd\\|([a-z0-9_.]+)\\}\\}"
	);
	private static final Pattern DOTTED_ID = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)+$");

	private static final int MAX_RENAME_HOPS = 10;

	private static final List<String> LEGACY_PREFIXES = List.of(
			"mob.",
			"dig.",
			"step.",
			"random.",
			"liquid.",
			"fire.",
			"portal.",
			"note.",
			"tile.",
			"minecart.",
			"damage.",
			"game.",
			"use.",
			"hit.",
			"fall."
	);

	private static final Map<String, String> EXPLICIT_EVENTS = Map.ofEntries(
			Map.entry("random.bow", "entity.arrow.shoot"),
			Map.entry("random.bowhit", "entity.arrow.hit"),
			Map.entry("random.click", "ui.button.click"),
			Map.entry("random.explode", "entity.generic.explode"),
			Map.entry("random.fizz", "block.fire.extinguish"),
			Map.entry("random.pop", "entity.item.pickup"),
			Map.entry("random.splash", "entity.generic.splash"),
			Map.entry("random.hurt", "entity.player.hurt"),
			Map.entry("random.door_open", "block.wooden_door.open"),
			Map.entry("random.door_close", "block.wooden_door.close"),
			Map.entry("random.chestopen", "block.chest.open"),
			Map.entry("random.chestclosed", "block.chest.close"),
			Map.entry("random.eat", "entity.generic.eat"),
			Map.entry("random.drink", "entity.generic.drink"),
			Map.entry("random.burp", "entity.player.burp"),
			Map.entry("random.orb", "entity.experience_orb.pickup"),
			Map.entry("random.levelup", "entity.player.levelup"),
			Map.entry("random.break", "entity.item.break"),
			Map.entry("random.fuse", "entity.tnt.primed"),
			Map.entry("random.anvil_land", "block.anvil.land"),
			Map.entry("random.anvil_use", "block.anvil.use"),
			Map.entry("random.anvil_break", "block.anvil.destroy"),
			Map.entry("random.glass", "block.glass.break"),
			Map.entry("fire.fire", "block.fire.ambient"),
			Map.entry("fire.ignite", "item.flintandsteel.use"),
			Map.entry("liquid.water", "block.water.ambient"),
			Map.entry("liquid.lava", "block.lava.ambient"),
			Map.entry("liquid.lavapop", "block.lava.pop"),
			Map.entry("liquid.splash", "entity.generic.splash"),
			Map.entry("liquid.swim", "entity.generic.swim"),
			Map.entry("portal.portal", "block.portal.ambient"),
			Map.entry("portal.travel", "block.portal.travel"),
			Map.entry("portal.trigger", "block.portal.trigger"),
			Map.entry("damage.hit", "entity.player.hurt"),
			Map.entry("damage.fallbig", "entity.generic.big_fall"),
			Map.entry("damage.fallsmall", "entity.generic.small_fall"),
			Map.entry("note.harp", "block.note_block.harp"),
			Map.entry("note.bd", "block.note_block.basedrum"),
			Map.entry("note.snare", "block.note_block.snare"),
			Map.entry("note.hat", "block.note_block.hat"),
			Map.entry("note.bass", "block.note_block.bass"),
			Map.entry("note.pling", "block.note_block.pling"),
			Map.entry("minecart.base", "entity.minecart.riding"),
			Map.entry("minecart.inside", "entity.minecart.inside")
	);

	private static final Map<String, String> MOB_NAME_FIXES = Map.of(
			"endermen", "enderman",
			"zombiepig", "zombified_piglin",
			"magmacube", "magma_cube",
			"magma", "magma_cube",
			"irongolem", "iron_golem"
	);

	private static final Map<String, String> LAST_SEGMENT_FIXES = Map.of(
			"say", "ambient",
			"idle", "ambient",
			"hit", "hurt",
			"die", "death",
			"kill", "death"
	);

	private static final Map<String, String> HORSE_SPECIALS = Map.of(
			"horse.donkey.", "donkey",
			"horse.zombie.", "zombie_horse",
			"horse.skeleton.", "skeleton_horse"
	);

	private final Map<String, String> renames;
	private final Set<String> knownEvents;

	public LegacySoundEvents(Map<String, String> renames, Set<String> knownEvents) {
		this.renames = renames == null || renames.isEmpty() ? Map.of() : Map.copyOf(renames);
		this.knownEvents = knownEvents == null || knownEvents.isEmpty() ? Set.of() : Set.copyOf(knownEvents);
	}

	/**
	 * Collects every dotted lowercase ID from {@code {{cd|...}}} templates, skipping parameters
	 * such as {@code d=and} and placeholders like {@code block.''<block>''.break}.
	 */
	public static Set<String> parseKnownEvents(String historyWikitext) {
		Set<String> events = new LinkedHashSet<>();
		if (historyWikitext == null || historyWikitext.isEmpty()) {
			return events;
		}
		Matcher template = CD_TEMPLATE.matcher(historyWikitext);
		while (template.find()) {
			for (String argument : template.group(1).split("\\|")) {
				String candidate = argument.trim();
				if (candidate.isEmpty() || candidate.contains("=")) {
					continue;
				}
				if (DOTTED_ID.matcher(candidate).matches()) {
					events.add(candidate);
				}
			}
		}
		return events;
	}

	/**
	 * Collects {@code Renamed {{cd|a}} to {{cd|b}}} and {@code Changed {{cd|a}} to {{cd|b}}}
	 * pairs, including comma and {@code and} lists within one sentence.
	 */
	public static Map<String, String> parseRenames(String historyWikitext) {
		Map<String, String> result = new LinkedHashMap<>();
		if (historyWikitext == null || historyWikitext.isEmpty()) {
			return result;
		}
		for (String line : historyWikitext.split("\\R")) {
			if (!line.contains("Renamed") && !line.contains("Changed")) {
				continue;
			}
			Matcher matcher = RENAME.matcher(line);
			while (matcher.find()) {
				String from = matcher.group(1);
				String to = matcher.group(2);
				if (!DOTTED_ID.matcher(from).matches() || !DOTTED_ID.matcher(to).matches()) {
					continue;
				}
				result.putIfAbsent(from, to);
			}
		}
		return result;
	}

	public Optional<String> toModern(String event) {
		if (event == null || event.isBlank()) {
			return Optional.empty();
		}
		String normalized = event.trim().toLowerCase(Locale.ROOT);
		List<String> candidates = candidatesFor(normalized);
		if (knownEvents.isEmpty()) {
			return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
		}
		for (String candidate : candidates) {
			if (knownEvents.contains(candidate)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private List<String> candidatesFor(String event) {
		List<String> candidates = new ArrayList<>();
		if (!isLegacy(event)) {
			add(candidates, event);
		}

		Set<String> visited = new HashSet<>();
		visited.add(event);
		String current = event;
		for (int hop = 0; hop < MAX_RENAME_HOPS; hop++) {
			String renamed = renames.get(current);
			if (renamed == null || !visited.add(renamed)) {
				break;
			}
			add(candidates, renamed);
			ruleCandidates(renamed, candidates);
			current = renamed;
		}

		ruleCandidates(event, candidates);
		return candidates;
	}

	private static void ruleCandidates(String event, List<String> candidates) {
		String explicit = EXPLICIT_EVENTS.get(event);
		if (explicit != null) {
			add(candidates, explicit);
		}
		addBlockRules(event, candidates);
		addDottedMobRules(event, candidates);
		addTwoPartMobRules(event, candidates);
	}

	private static void addBlockRules(String event, List<String> candidates) {
		addBlockRule(event, candidates, "dig.", "break");
		addBlockRule(event, candidates, "step.", "step");
		addBlockRule(event, candidates, "use.", "place");
		addBlockRule(event, candidates, "hit.", "hit");
		addBlockRule(event, candidates, "fall.", "fall");
	}

	private static void addBlockRule(String event, List<String> candidates, String prefix, String suffix) {
		if (!event.startsWith(prefix)) {
			return;
		}
		String material = event.substring(prefix.length());
		if (material.isEmpty()) {
			return;
		}
		String mappedMaterial = material.equals("cloth") ? "wool" : material;
		if (!mappedMaterial.equals(material)) {
			add(candidates, "block." + mappedMaterial + "." + suffix);
		}
		add(candidates, "block." + material + "." + suffix);
	}

	private static void addDottedMobRules(String event, List<String> candidates) {
		if (!event.startsWith("mob.")) {
			return;
		}
		String rest = event.substring("mob.".length());
		for (Map.Entry<String, String> special : HORSE_SPECIALS.entrySet()) {
			if (!rest.startsWith(special.getKey())) {
				continue;
			}
			String suffix = rest.substring(special.getKey().length());
			if (suffix.isEmpty()) {
				return;
			}
			for (String mapped : mappedLastSegment(suffix)) {
				add(candidates, "entity." + special.getValue() + "." + mapped);
			}
			return;
		}
		int dot = rest.indexOf('.');
		if (dot <= 0 || dot == rest.length() - 1) {
			return;
		}
		String name = rest.substring(0, dot);
		String suffix = rest.substring(dot + 1);
		String fixedName = MOB_NAME_FIXES.getOrDefault(name, name);
		for (String mapped : mappedLastSegment(suffix)) {
			add(candidates, "entity." + fixedName + "." + mapped);
		}
	}

	private static void addTwoPartMobRules(String event, List<String> candidates) {
		if (!event.startsWith("mob.")) {
			return;
		}
		String name = event.substring("mob.".length());
		if (name.isEmpty() || name.indexOf('.') >= 0) {
			return;
		}
		if (name.length() > "hurt".length() && name.endsWith("hurt")) {
			add(candidates, "entity." + name.substring(0, name.length() - "hurt".length()) + ".hurt");
			return;
		}
		if (name.length() > "death".length() && name.endsWith("death")) {
			add(candidates, "entity." + name.substring(0, name.length() - "death".length()) + ".death");
			return;
		}
		add(candidates, "entity." + name + ".ambient");
	}

	private static List<String> mappedLastSegment(String suffix) {
		int dot = suffix.lastIndexOf('.');
		String head = dot >= 0 ? suffix.substring(0, dot) : "";
		String last = dot >= 0 ? suffix.substring(dot + 1) : suffix;
		String mappedLast = LAST_SEGMENT_FIXES.getOrDefault(last, last);
		String mapped = head.isEmpty() ? mappedLast : head + "." + mappedLast;
		String unchanged = head.isEmpty() ? last : head + "." + last;
		if (mapped.equals(unchanged)) {
			return List.of(mapped);
		}
		return List.of(mapped, unchanged);
	}

	private static boolean isLegacy(String event) {
		for (String prefix : LEGACY_PREFIXES) {
			if (event.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static void add(List<String> candidates, String candidate) {
		if (!candidates.contains(candidate)) {
			candidates.add(candidate);
		}
	}
}
