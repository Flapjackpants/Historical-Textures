package dev.historicaltextures.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionOrdering {
	private static final Pattern SNAPSHOT = Pattern.compile("(\\d{2})w(\\d{2})([a-z]?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.?(\\d*)\\.?(\\d*)");
	private static final Pattern TEXTURE_TAG = Pattern.compile("(JE|BE|TU)(\\d+)", Pattern.CASE_INSENSITIVE);

	private VersionOrdering() {
	}

	public static Comparator<TextureVariant> textureVariantComparator() {
		return Comparator
				.comparingInt(VersionOrdering::textureSortKey)
				.thenComparing(TextureVariant::id);
	}

	public static Comparator<SoundVariant> soundVariantComparator() {
		return Comparator
				.comparingInt(VersionOrdering::soundSortKey)
				.thenComparing(SoundVariant::id);
	}

	public static List<TextureVariant> sortTextureVariants(List<TextureVariant> variants) {
		return variants.stream().sorted(textureVariantComparator()).toList();
	}

	public static List<SoundVariant> sortSoundVariants(List<SoundVariant> variants) {
		return variants.stream().sorted(soundVariantComparator()).toList();
	}

	private static int textureSortKey(TextureVariant variant) {
		return sortKey(variant.javaVersion(), variant.bedrockVersion(), variant.textureTags(), variant.wikiFile());
	}

	private static int soundSortKey(SoundVariant variant) {
		return sortKey(variant.javaVersion(), variant.bedrockVersion(), variant.textureTags(), variant.wikiFile());
	}

	private static int sortKey(String javaVersion, String bedrockVersion, List<String> textureTags, String wikiFile) {
		int javaKey = versionKey(javaVersion);
		if (javaKey != Integer.MAX_VALUE) {
			return javaKey;
		}
		int tagKey = textureTagKey(textureTags, wikiFile);
		if (tagKey != Integer.MAX_VALUE) {
			return tagKey;
		}
		return versionKey(bedrockVersion);
	}

	static int versionKey(String version) {
		if (version == null || version.isBlank()) {
			return Integer.MAX_VALUE;
		}
		String value = version.trim();
		Matcher snapshot = SNAPSHOT.matcher(value);
		if (snapshot.find()) {
			int year = Integer.parseInt(snapshot.group(1));
			int week = Integer.parseInt(snapshot.group(2));
			int revision = snapshot.group(3).isEmpty() ? 0 : snapshot.group(3).charAt(0) - 'a' + 1;
			return year * 100_000 + week * 100 + revision;
		}
		if (value.regionMatches(true, 0, "Beta ", 0, 5)) {
			return parseRelease(value.substring(5), 100_000);
		}
		if (value.regionMatches(true, 0, "Alpha ", 0, 6)) {
			return parseRelease(value.substring(6), 50_000);
		}
		if (value.regionMatches(true, 0, "Indev ", 0, 6)) {
			return 10_000;
		}
		if (value.regionMatches(true, 0, "Infdev ", 0, 7)) {
			return 5_000;
		}
		if (value.regionMatches(true, 0, "Classic ", 0, 8)) {
			return 1_000;
		}
		return parseRelease(value, 200_000);
	}

	private static int parseRelease(String value, int base) {
		Matcher release = RELEASE.matcher(value);
		if (!release.find()) {
			return Integer.MAX_VALUE;
		}
		int major = Integer.parseInt(release.group(1));
		int minor = release.group(2).isEmpty() ? 0 : Integer.parseInt(release.group(2));
		int patch = release.group(3).isEmpty() ? 0 : Integer.parseInt(release.group(3));
		return base + major * 10_000 + minor * 100 + patch;
	}

	private static int textureTagKey(List<String> textureTags, String wikiFile) {
		int best = Integer.MAX_VALUE;
		if (textureTags != null) {
			for (String tag : textureTags) {
				best = Math.min(best, tagKey(tag));
			}
		}
		if (best != Integer.MAX_VALUE) {
			return 300_000 + best;
		}
		Matcher matcher = TEXTURE_TAG.matcher(wikiFile == null ? "" : wikiFile);
		while (matcher.find()) {
			best = Math.min(best, tagKey(matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2)));
		}
		return best == Integer.MAX_VALUE ? Integer.MAX_VALUE : 300_000 + best;
	}

	private static int tagKey(String tag) {
		Matcher matcher = TEXTURE_TAG.matcher(tag);
		if (!matcher.find()) {
			return Integer.MAX_VALUE;
		}
		String edition = matcher.group(1).toUpperCase(Locale.ROOT);
		int number = Integer.parseInt(matcher.group(2));
		int editionOffset = switch (edition) {
			case "JE" -> 0;
			case "BE" -> 10_000;
			case "TU" -> 20_000;
			default -> 30_000;
		};
		return editionOffset + number;
	}
}
