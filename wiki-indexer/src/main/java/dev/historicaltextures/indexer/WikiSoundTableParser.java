package dev.historicaltextures.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses wiki {@code {{SoundTable}}} templates into file-to-sound-event mappings.
 *
 * <p>Only Java and Bedrock tables are read; other table types are skipped. A {@code SoundLine}
 * without sounds reuses the previous line's sounds, which is how rowspanned rows are written.
 */
public final class WikiSoundTableParser {
	private static final Pattern TABLE_START = Pattern.compile(
			"\\{\\{\\s*SoundTable\\b",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern LINE_START = Pattern.compile(
			"\\{\\{\\s*SoundLine\\b",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern SOUND_KEY = Pattern.compile("sound(\\d*)");
	private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)+$");

	public enum Edition {
		JAVA,
		BEDROCK
	}

	public record Entry(String fileName, String id, Edition edition) {
	}

	private WikiSoundTableParser() {
	}

	public static List<Entry> parse(String wikitext) {
		if (wikitext == null || wikitext.isEmpty()) {
			return List.of();
		}
		List<Entry> entries = new ArrayList<>();
		Matcher tableMatcher = TABLE_START.matcher(wikitext);
		int searchFrom = 0;
		while (searchFrom < wikitext.length() && tableMatcher.find(searchFrom)) {
			int start = tableMatcher.start();
			int close = balancedClose(wikitext, start);
			String body = wikitext.substring(tableMatcher.end(), close);
			searchFrom = Math.max(close + 2, start + 1);
			Edition edition = tableEdition(body);
			if (edition == null) {
				continue;
			}
			collectLines(body, edition, entries);
		}
		return List.copyOf(entries);
	}

	private static Edition tableEdition(String body) {
		for (String param : splitParams(body)) {
			int equals = param.indexOf('=');
			if (equals < 0) {
				continue;
			}
			String key = param.substring(0, equals).trim().toLowerCase(Locale.ROOT);
			if (!key.equals("type")) {
				continue;
			}
			String value = param.substring(equals + 1).trim().toLowerCase(Locale.ROOT);
			if (value.equals("java")) {
				return Edition.JAVA;
			}
			if (value.equals("bedrock")) {
				return Edition.BEDROCK;
			}
			return null;
		}
		return null;
	}

	private static void collectLines(String body, Edition edition, List<Entry> entries) {
		List<String> previousSounds = List.of();
		Matcher lineMatcher = LINE_START.matcher(body);
		int searchFrom = 0;
		while (searchFrom < body.length() && lineMatcher.find(searchFrom)) {
			int start = lineMatcher.start();
			int close = balancedClose(body, start);
			String lineBody = body.substring(lineMatcher.end(), close);
			searchFrom = Math.max(close + 2, start + 1);

			Map<Integer, String> soundsByIndex = new TreeMap<>();
			String id = null;
			for (String param : splitParams(lineBody)) {
				int equals = param.indexOf('=');
				if (equals < 0) {
					continue;
				}
				String key = param.substring(0, equals).trim().toLowerCase(Locale.ROOT);
				String value = param.substring(equals + 1).trim();
				Matcher soundKey = SOUND_KEY.matcher(key);
				if (soundKey.matches()) {
					if (isOgg(value)) {
						String index = soundKey.group(1);
						soundsByIndex.put(index.isEmpty() ? 1 : Integer.parseInt(index), value);
					}
					continue;
				}
				if (key.equals("id")) {
					id = cleanId(value);
				}
			}

			List<String> sounds = List.copyOf(soundsByIndex.values());
			if (id == null) {
				if (!sounds.isEmpty()) {
					previousSounds = sounds;
				}
				continue;
			}
			if (!sounds.isEmpty()) {
				previousSounds = sounds;
			}
			List<String> effective = sounds.isEmpty() ? previousSounds : sounds;
			for (String sound : effective) {
				entries.add(new Entry(sound, id, edition));
			}
		}
	}

	private static List<String> splitParams(String text) {
		List<String> params = new ArrayList<>();
		int braceDepth = 0;
		int bracketDepth = 0;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			if (i + 1 < text.length() && text.charAt(i) == '{' && text.charAt(i + 1) == '{') {
				braceDepth++;
				i++;
				continue;
			}
			if (i + 1 < text.length() && text.charAt(i) == '}' && text.charAt(i + 1) == '}') {
				if (braceDepth > 0) {
					braceDepth--;
				}
				i++;
				continue;
			}
			if (i + 1 < text.length() && text.charAt(i) == '[' && text.charAt(i + 1) == '[') {
				bracketDepth++;
				i++;
				continue;
			}
			if (i + 1 < text.length() && text.charAt(i) == ']' && text.charAt(i + 1) == ']') {
				if (bracketDepth > 0) {
					bracketDepth--;
				}
				i++;
				continue;
			}
			if (text.charAt(i) == '|' && braceDepth == 0 && bracketDepth == 0) {
				params.add(text.substring(start, i));
				start = i + 1;
			}
		}
		params.add(text.substring(start));
		return params;
	}

	private static int balancedClose(String text, int start) {
		int depth = 0;
		int i = start;
		while (i + 1 < text.length()) {
			char current = text.charAt(i);
			char next = text.charAt(i + 1);
			if (current == '{' && next == '{') {
				depth++;
				i += 2;
				continue;
			}
			if (current == '}' && next == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
				i += 2;
				continue;
			}
			i++;
		}
		return text.length();
	}

	private static String cleanId(String value) {
		String cleaned = value.trim().toLowerCase(Locale.ROOT);
		for (int i = 0; i < cleaned.length(); i++) {
			char current = cleaned.charAt(i);
			if (Character.isWhitespace(current) || current == '<' || current == '{') {
				cleaned = cleaned.substring(0, i);
				break;
			}
		}
		return VALID_ID.matcher(cleaned).matches() ? cleaned : null;
	}

	private static boolean isOgg(String value) {
		return value.toLowerCase(Locale.ROOT).endsWith(".ogg");
	}
}
