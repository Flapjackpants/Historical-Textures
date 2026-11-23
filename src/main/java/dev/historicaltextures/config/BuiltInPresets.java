package dev.historicaltextures.config;

import dev.historicaltextures.catalog.HistoricalCatalog;
import dev.historicaltextures.catalog.ReleaseTimeline;
import dev.historicaltextures.catalog.SoundVariant;
import dev.historicaltextures.catalog.TextureVariant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuiltInPresets {
	public static final List<String> CUTOFFS = List.of(
			"Alpha v1.2.6",
			"Beta 1.7.3",
			"1.2.5",
			"1.4.7",
			"1.6.4",
			"1.8.9",
			"1.12.2",
			"1.13.2"
	);

	private static final Pattern JE_TAG = Pattern.compile("JE(\\d+)");
	private static final Pattern RANGE = Pattern.compile("^(.+?)\\s+[–-]\\s+(.+?)$");
	private static final int SOUNDS_UNTIL_KEY = ReleaseTimeline.releaseKey("1.9");

	private BuiltInPresets() {
	}

	public static List<Preset> create(List<TextureVariant> textures, List<SoundVariant> sounds) {
		Map<String, List<TextureVariant>> texturesByTarget = groupTexturesByTarget(textures);
		Map<String, List<SoundVariant>> soundsByEvent = groupSoundsByEvent(sounds);

		List<Preset> presets = new ArrayList<>();
		for (int i = 0; i < CUTOFFS.size(); i++) {
			int cutoffKey = ReleaseTimeline.releaseKey(CUTOFFS.get(i));
			Map<String, String> textureChoices = new TreeMap<>();
			for (Map.Entry<String, List<TextureVariant>> entry : texturesByTarget.entrySet()) {
				TextureVariant best = bestTexture(entry.getValue(), cutoffKey);
				if (best != null) {
					textureChoices.put(entry.getKey(), best.id());
				}
			}
			Map<String, String> soundChoices = new TreeMap<>();
			if (cutoffKey >= 0 && cutoffKey < SOUNDS_UNTIL_KEY) {
				for (Map.Entry<String, List<SoundVariant>> entry : soundsByEvent.entrySet()) {
					SoundVariant best = bestSound(entry.getValue(), cutoffKey);
					if (best != null) {
						soundChoices.put(entry.getKey(), best.id());
					}
				}
			}
			presets.add(new Preset(CUTOFFS.get(i).replace("Alpha v", "Alpha "), textureChoices, soundChoices, true, true));
		}
		return List.copyOf(presets);
	}

	public static List<Preset> create(HistoricalCatalog catalog) {
		return create(catalog.textureVariants(), catalog.soundVariants());
	}

	private static Map<String, List<TextureVariant>> groupTexturesByTarget(List<TextureVariant> textures) {
		Map<String, List<TextureVariant>> byTarget = new LinkedHashMap<>();
		for (TextureVariant variant : textures) {
			for (String target : variant.targets()) {
				byTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(variant);
			}
		}
		return byTarget;
	}

	private static Map<String, List<SoundVariant>> groupSoundsByEvent(List<SoundVariant> sounds) {
		Map<String, List<SoundVariant>> byEvent = new LinkedHashMap<>();
		for (SoundVariant variant : sounds) {
			byEvent.computeIfAbsent(variant.soundEvent(), key -> new ArrayList<>()).add(variant);
		}
		return byEvent;
	}

	static TextureVariant bestTexture(List<TextureVariant> candidates, int cutoffKey) {
		TextureVariant best = null;
		int bestKey = -1;
		int bestJe = -1;
		for (TextureVariant variant : candidates) {
			if (!isJavaVariant(variant)) {
				continue;
			}
			int key = ReleaseTimeline.releaseKey(variant.javaVersion());
			if (key < 0 || key > cutoffKey) {
				continue;
			}
			int je = maxJeTag(variant.textureTags());
			if (best == null
					|| key > bestKey
					|| (key == bestKey && je > bestJe)
					|| (key == bestKey && je == bestJe && variant.id().compareTo(best.id()) < 0)) {
				best = variant;
				bestKey = key;
				bestJe = je;
			}
		}
		return best;
	}

	public static boolean isJavaVariant(TextureVariant variant) {
		List<String> tags = variant.textureTags();
		if (tags == null || tags.isEmpty()) {
			return true;
		}
		for (String tag : tags) {
			if (tag != null && JE_TAG.matcher(tag).matches()) {
				return true;
			}
		}
		return false;
	}

	private static int maxJeTag(List<String> textureTags) {
		int best = -1;
		if (textureTags != null) {
			for (String tag : textureTags) {
				Matcher matcher = JE_TAG.matcher(tag == null ? "" : tag);
				if (matcher.matches()) {
					best = Math.max(best, Integer.parseInt(matcher.group(1)));
				}
			}
		}
		return best;
	}

	private static SoundVariant bestSound(List<SoundVariant> candidates, int cutoffKey) {
		List<SoundVariant> ranged = new ArrayList<>();
		List<SoundVariant> historical = new ArrayList<>();
		for (SoundVariant variant : candidates) {
			if (matchesRange(variant.javaVersion(), cutoffKey) || matchesRange(variant.displayVersion(), cutoffKey)) {
				ranged.add(variant);
			} else if (isHistorical(variant.javaVersion()) || isHistorical(variant.displayVersion())) {
				historical.add(variant);
			}
		}
		List<SoundVariant> pool = ranged.isEmpty() ? historical : ranged;
		return pool.stream().min(Comparator.comparing(SoundVariant::id)).orElse(null);
	}

	private static boolean isHistorical(String version) {
		if (version == null) {
			return false;
		}
		String value = version.trim().toLowerCase(Locale.ROOT);
		return value.equals("historical") || value.equals("historical (old)");
	}

	private static boolean matchesRange(String version, int cutoffKey) {
		if (version == null) {
			return false;
		}
		Matcher matcher = RANGE.matcher(version.trim());
		if (!matcher.matches()) {
			return false;
		}
		int start = ReleaseTimeline.releaseKey(matcher.group(1).trim());
		int end = ReleaseTimeline.releaseKey(matcher.group(2).trim());
		return start >= 0 && end >= start && cutoffKey >= start && cutoffKey <= end;
	}
}
