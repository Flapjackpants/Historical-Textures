package dev.historicaltextures.indexer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiTableParser {
	private static final Pattern WIKI_FILE_LINK = Pattern.compile("\\[\\[(?:File:|Image:)?([^\\]|#]+?)(?:\\|[^\\]]*)?\\]\\]", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLAIN_FILE = Pattern.compile("(?:File:|Image:)([^\\]|#\\s]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern VERSION_LINK = Pattern.compile("\\[\\[[^\\]|#\\|]*\\|([^\\]]+)\\]\\]|\\[\\[([^\\]]+)\\]\\]");
	private static final Pattern SNAPSHOT = Pattern.compile("\\b(\\d{2}w\\d{2}[a-z]?)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern RELEASE = Pattern.compile("\\b((?:Java Edition )?(?:Beta )?(?:Alpha )?(?:Indev )?(?:Infdev )?(?:Classic )?\\d+(?:\\.\\d+)*)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern BEDROCK = Pattern.compile("\\b(?:Bedrock Edition )?(\\d+\\.\\d+(?:\\.\\d+)?)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HISTORY_LINE = Pattern.compile("\\{\\{HistoryLine", Pattern.CASE_INSENSITIVE);

	public enum WikiEdition {
		JAVA,
		BEDROCK,
		UNKNOWN
	}

	public record VersionInfo(
			String javaVersion,
			String bedrockVersion,
			String displayVersion
	) {
		public static VersionInfo empty() {
			return new VersionInfo("", "", "");
		}

		public static VersionInfo merge(VersionInfo left, VersionInfo right) {
			String java = !left.javaVersion.isEmpty() ? left.javaVersion : right.javaVersion;
			String bedrock = !left.bedrockVersion.isEmpty() ? left.bedrockVersion : right.bedrockVersion;
			String display = buildDisplay(java, bedrock);
			return new VersionInfo(java, bedrock, display);
		}

		public static VersionInfo forEdition(String version, WikiEdition edition) {
			if (version == null || version.isEmpty()) {
				return empty();
			}
			return switch (edition) {
				case BEDROCK -> {
					String bedrock = cleanBedrockVersion(version);
					yield new VersionInfo("", bedrock, buildDisplay("", bedrock));
				}
				case JAVA, UNKNOWN -> {
					String java = cleanJavaVersion(version);
					yield new VersionInfo(java, "", buildDisplay(java, ""));
				}
			};
		}
	}

	private WikiTableParser() {
	}

	public static Map<String, VersionInfo> parseVersionsFromWikitext(String wikitext) {
		return parseVersionsFromWikitext(wikitext, WikiEdition.JAVA);
	}

	public static Map<String, VersionInfo> parseVersionsFromWikitext(String wikitext, WikiEdition edition) {
		Map<String, VersionInfo> versions = new LinkedHashMap<>();
		mergeVersionMaps(versions, parseHistoryLinesFromWikitext(wikitext, edition));
		mergeVersionMaps(versions, parseWikitableVersions(wikitext, edition));
		return versions;
	}

	public static Map<String, VersionInfo> parseHistoryLinesFromWikitext(String wikitext, WikiEdition edition) {
		Map<String, VersionInfo> versions = new LinkedHashMap<>();
		if (wikitext == null || wikitext.isEmpty()) {
			return versions;
		}
		Matcher marker = HISTORY_LINE.matcher(wikitext);
		while (marker.find()) {
			int start = marker.start();
			int end = findTemplateEnd(wikitext, start);
			if (end < 0) {
				break;
			}
			parseHistoryLineTemplate(wikitext.substring(start, end), edition, versions);
			marker.region(end, wikitext.length());
		}
		return versions;
	}

	private static Map<String, VersionInfo> parseWikitableVersions(String wikitext, WikiEdition edition) {
		Map<String, VersionInfo> versions = new LinkedHashMap<>();
		if (wikitext == null || wikitext.isEmpty()) {
			return versions;
		}
		int index = 0;
		while ((index = wikitext.indexOf("{|", index)) >= 0) {
			int end = wikitext.indexOf("|}", index);
			if (end < 0) {
				break;
			}
			String table = wikitext.substring(index, end);
			parseTable(table, edition, versions);
			index = end + 2;
		}
		return versions;
	}

	private static void parseHistoryLineTemplate(String template, WikiEdition edition, Map<String, VersionInfo> versions) {
		int pipe = template.indexOf('|');
		if (pipe < 0) {
			return;
		}
		int close = template.lastIndexOf("}}");
		if (close <= pipe) {
			return;
		}
		List<String> args = splitTemplateArgs(template.substring(pipe + 1, close));
		String version = versionFromHistoryLineArgs(args);
		if (version.isEmpty()) {
			return;
		}
		VersionInfo rowVersion = VersionInfo.forEdition(version, edition);
		for (String file : extractFiles(template)) {
			String key = WikiFilenameUtil.normalizeKey(file);
			versions.merge(key, rowVersion, VersionInfo::merge);
		}
	}

	private static String versionFromHistoryLineArgs(List<String> args) {
		String dev = "";
		String positional = "";
		for (String arg : args) {
			String trimmed = arg.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.startsWith("dev=")) {
				dev = trimmed.substring(4).trim();
			} else if (trimmed.startsWith("link=") || trimmed.startsWith("slink=")) {
				continue;
			} else if (!containsFileLink(trimmed) && positional.isEmpty()) {
				positional = trimmed;
			}
		}
		return pickHistoryLineVersion(dev, positional);
	}

	private static String pickHistoryLineVersion(String dev, String positional) {
		if (!dev.isEmpty()) {
			Matcher snapshot = SNAPSHOT.matcher(dev);
			if (snapshot.find()) {
				return snapshot.group(1);
			}
			return dev;
		}
		return positional;
	}

	private static boolean containsFileLink(String value) {
		return value.contains("[[File:") || value.contains("[[Image:");
	}

	private static int findTemplateEnd(String text, int start) {
		int depth = 0;
		for (int index = start; index < text.length() - 1; index++) {
			if (text.charAt(index) == '{' && text.charAt(index + 1) == '{') {
				depth++;
				index++;
			} else if (text.charAt(index) == '}' && text.charAt(index + 1) == '}') {
				depth--;
				if (depth == 0) {
					return index + 2;
				}
				index++;
			}
		}
		return -1;
	}

	private static List<String> splitTemplateArgs(String inner) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int bracketDepth = 0;
		int braceDepth = 0;
		for (int index = 0; index < inner.length(); index++) {
			char character = inner.charAt(index);
			if (character == '[' && index + 1 < inner.length() && inner.charAt(index + 1) == '[') {
				bracketDepth++;
				current.append("[[");
				index++;
				continue;
			}
			if (character == ']' && index + 1 < inner.length() && inner.charAt(index + 1) == ']') {
				bracketDepth--;
				current.append("]]");
				index++;
				continue;
			}
			if (character == '{' && index + 1 < inner.length() && inner.charAt(index + 1) == '{') {
				braceDepth++;
				current.append("{{");
				index++;
				continue;
			}
			if (character == '}' && index + 1 < inner.length() && inner.charAt(index + 1) == '}') {
				braceDepth--;
				current.append("}}");
				index++;
				continue;
			}
			if (character == '|' && bracketDepth == 0 && braceDepth == 0) {
				parts.add(current.toString());
				current = new StringBuilder();
				continue;
			}
			current.append(character);
		}
		parts.add(current.toString());
		return parts;
	}

	private static void parseTable(String table, WikiEdition edition, Map<String, VersionInfo> versions) {
		String[] rows = table.split("\\|-");
		for (String row : rows) {
			if (!row.contains("File:") && !row.toLowerCase(Locale.ROOT).contains(".png")) {
				continue;
			}
			List<String> cells = splitCells(row);
			if (cells.isEmpty()) {
				continue;
			}
			List<String> files = new ArrayList<>();
			VersionInfo rowVersion = VersionInfo.empty();
			for (String cell : cells) {
				files.addAll(extractFiles(cell));
				VersionInfo cellVersion = extractVersion(cell, edition);
				rowVersion = VersionInfo.merge(rowVersion, cellVersion);
			}
			if (files.isEmpty()) {
				continue;
			}
			if (rowVersion.displayVersion.isEmpty()) {
				rowVersion = inferVersionFromCells(cells, edition);
			}
			for (String file : files) {
				String key = WikiFilenameUtil.normalizeKey(file);
				versions.merge(key, rowVersion, VersionInfo::merge);
			}
		}
	}

	private static List<String> splitCells(String row) {
		List<String> cells = new ArrayList<>();
		for (String part : row.split("\\|\\|")) {
			String cell = part.replaceFirst("^\\|", "").trim();
			if (!cell.isEmpty() && !cell.startsWith("{|")) {
				cells.add(stripWikiMarkup(cell));
			}
		}
		return cells;
	}

	private static String stripWikiMarkup(String cell) {
		return cell
				.replaceAll("(?s)\\{\\{[^}]+\\}\\}", " ")
				.replace("'''", "")
				.replace("''", "")
				.replace("<br>", " ")
				.replace("<br/>", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static List<String> extractFiles(String cell) {
		List<String> files = new ArrayList<>();
		Matcher wikiMatcher = WIKI_FILE_LINK.matcher(cell);
		while (wikiMatcher.find()) {
			addFileIfPng(files, wikiMatcher.group(1).trim());
		}
		Matcher plainMatcher = PLAIN_FILE.matcher(cell);
		while (plainMatcher.find()) {
			addFileIfPng(files, plainMatcher.group(1).trim());
		}
		return files;
	}

	private static void addFileIfPng(List<String> files, String file) {
		if (file.toLowerCase(Locale.ROOT).endsWith(".png")) {
			files.add(file);
		}
	}

	private static VersionInfo extractVersion(String cell, WikiEdition edition) {
		String java = "";
		String bedrock = "";
		String display = "";

		Matcher snapshot = SNAPSHOT.matcher(cell);
		if (snapshot.find()) {
			java = snapshot.group(1);
			display = java;
		}

		Matcher versionLink = VERSION_LINK.matcher(cell);
		while (versionLink.find()) {
			String value = versionLink.group(1) != null ? versionLink.group(1) : versionLink.group(2);
			if (value == null || value.isEmpty()) {
				continue;
			}
			value = value.trim();
			if (value.toLowerCase(Locale.ROOT).contains("bedrock")) {
				String extracted = extractBedrock(value);
				if (!extracted.isEmpty()) {
					bedrock = extracted;
				}
			} else if (java.isEmpty()) {
				java = cleanJavaVersion(value);
				if (!java.isEmpty()) {
					display = display.isEmpty() ? java : display;
				}
			}
		}

		if (bedrock.isEmpty()) {
			Matcher bedrockMatcher = BEDROCK.matcher(cell);
			if (bedrockMatcher.find() && cell.toLowerCase(Locale.ROOT).contains("bedrock")) {
				bedrock = bedrockMatcher.group(1);
			}
		}

		if (java.isEmpty()) {
			Matcher release = RELEASE.matcher(cell);
			if (release.find() && !cell.toLowerCase(Locale.ROOT).contains("bedrock")) {
				java = cleanJavaVersion(release.group(1));
				if (!display.isEmpty()) {
					display = java;
				}
			}
		}

		if (display.isEmpty()) {
			display = buildDisplay(java, bedrock);
		}
		if (edition == WikiEdition.BEDROCK && !bedrock.isEmpty()) {
			return VersionInfo.forEdition(bedrock, WikiEdition.BEDROCK);
		}
		if (!java.isEmpty()) {
			return VersionInfo.forEdition(java, WikiEdition.JAVA);
		}
		if (!bedrock.isEmpty()) {
			return VersionInfo.forEdition(bedrock, WikiEdition.BEDROCK);
		}
		return VersionInfo.empty();
	}

	private static VersionInfo inferVersionFromCells(List<String> cells, WikiEdition edition) {
		for (String cell : cells) {
			VersionInfo info = extractVersion(cell, edition);
			if (!info.displayVersion.isEmpty()) {
				return info;
			}
		}
		return VersionInfo.empty();
	}

	private static String cleanJavaVersion(String value) {
		String cleaned = value
				.replace("Java Edition", "")
				.replace("Bedrock Edition", "")
				.trim();
		if (cleaned.startsWith("Beta ")) {
			return cleaned;
		}
		if (cleaned.startsWith("Alpha ")) {
			return cleaned;
		}
		if (cleaned.matches("\\d+(\\.\\d+)*")) {
			return cleaned;
		}
		Matcher snapshot = SNAPSHOT.matcher(cleaned);
		if (snapshot.find()) {
			return snapshot.group(1);
		}
		return cleaned;
	}

	private static String cleanBedrockVersion(String value) {
		String cleaned = value
				.replace("Bedrock Edition", "")
				.replace("Java Edition", "")
				.trim();
		if (cleaned.startsWith("v") && cleaned.length() > 1 && Character.isDigit(cleaned.charAt(1))) {
			cleaned = cleaned.substring(1);
		}
		if (cleaned.regionMatches(true, 0, "beta ", 0, 5)) {
			return cleaned.substring(5).trim();
		}
		if (cleaned.regionMatches(true, 0, "build ", 0, 6)) {
			return cleaned.substring(6).trim();
		}
		Matcher bedrockMatcher = BEDROCK.matcher(cleaned);
		if (bedrockMatcher.find()) {
			return bedrockMatcher.group(1);
		}
		return cleaned;
	}

	private static String extractBedrock(String value) {
		Matcher matcher = BEDROCK.matcher(value);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return "";
	}

	public static String buildDisplay(String javaVersion, String bedrockVersion) {
		List<String> parts = new ArrayList<>();
		if (javaVersion != null && !javaVersion.isEmpty()) {
			parts.add(javaVersion);
		}
		if (bedrockVersion != null && !bedrockVersion.isEmpty()) {
			parts.add(bedrockVersion);
		}
		return String.join(" / ", parts);
	}

	private static void mergeVersionMaps(Map<String, VersionInfo> target, Map<String, VersionInfo> source) {
		for (Map.Entry<String, VersionInfo> entry : source.entrySet()) {
			target.merge(entry.getKey(), entry.getValue(), VersionInfo::merge);
		}
	}
}
