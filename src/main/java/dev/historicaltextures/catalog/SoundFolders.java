package dev.historicaltextures.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class SoundFolders {
	private static final Pattern TRAILING_DIGITS = Pattern.compile("[\\s_]*\\d+$");

	public record SoundFolder(String key, String label, List<SoundVariant> members) {
		public SoundFolder {
			members = members == null ? List.of() : List.copyOf(members);
		}
	}

	private SoundFolders() {}

	public static String folderKey(String wikiFile) {
		return strip(wikiFile, true);
	}

	public static String folderLabel(String wikiFile) {
		return strip(wikiFile, false);
	}

	public static List<SoundFolder> group(List<SoundVariant> variants) {
		if (variants == null || variants.isEmpty()) {
			return List.of();
		}
		Map<String, FolderBuilder> groups = new LinkedHashMap<>();
		for (SoundVariant variant : variants) {
			String key = folderKey(variant.wikiFile());
			FolderBuilder builder = groups.computeIfAbsent(
					key,
					k -> new FolderBuilder(k, folderLabel(variant.wikiFile()))
			);
			builder.members.add(variant);
		}
		List<SoundFolder> result = new ArrayList<>(groups.size());
		for (FolderBuilder builder : groups.values()) {
			result.add(new SoundFolder(builder.key, builder.label, builder.members));
		}
		return List.copyOf(result);
	}

	private static String strip(String wikiFile, boolean lowercase) {
		if (wikiFile == null) {
			return "";
		}
		String noExt = wikiFile;
		if (noExt.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
			noExt = noExt.substring(0, noExt.length() - 4);
		}
		String stripped = TRAILING_DIGITS.matcher(noExt).replaceFirst("").trim();
		if (stripped.isEmpty()) {
			stripped = noExt.trim();
		}
		return lowercase ? stripped.toLowerCase(Locale.ROOT) : stripped;
	}

	private static final class FolderBuilder {
		final String key;
		final String label;
		final List<SoundVariant> members = new ArrayList<>();

		FolderBuilder(String key, String label) {
			this.key = key;
			this.label = label;
		}
	}
}
