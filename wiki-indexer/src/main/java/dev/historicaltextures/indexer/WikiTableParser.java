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
			String display = !left.displayVersion.isEmpty() ? left.displayVersion : right.displayVersion;
			return new VersionInfo(java, bedrock, display);
		}
	}

	private WikiTableParser() {
	}

	public static Map<String, VersionInfo> parseVersionsFromWikitext(String wikitext) {
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
			parseTable(table, versions);
			index = end + 2;
		}
		return versions;
	}

	private static void parseTable(String table, Map<String, VersionInfo> versions) {
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
				VersionInfo cellVersion = extractVersion(cell);
				rowVersion = VersionInfo.merge(rowVersion, cellVersion);
			}
			if (files.isEmpty()) {
				continue;
			}
			if (rowVersion.displayVersion.isEmpty()) {
				rowVersion = inferVersionFromCells(cells);
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

	private static VersionInfo extractVersion(String cell) {
		String java = "";
		String bedrock = "";
		String display = "";

		Matcher snapshot = SNAPSHOT.matcher(cell);
		if (snapshot.find()) {
			java = snapshot.group(1);
			display = "JE " + java;
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
					display = display.isEmpty() ? "JE " + java : display;
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
					display = "JE " + java;
				}
			}
		}

		if (display.isEmpty()) {
			display = buildDisplay(java, bedrock);
		}
		return new VersionInfo(java, bedrock, display);
	}

	private static VersionInfo inferVersionFromCells(List<String> cells) {
		for (String cell : cells) {
			VersionInfo info = extractVersion(cell);
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
			parts.add("JE " + javaVersion);
		}
		if (bedrockVersion != null && !bedrockVersion.isEmpty()) {
			parts.add("BE " + bedrockVersion);
		}
		return String.join(" / ", parts);
	}
}
