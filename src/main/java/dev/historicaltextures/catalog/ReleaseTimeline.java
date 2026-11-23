package dev.historicaltextures.catalog;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseTimeline {
	private static final Pattern DEEP_DARK = Pattern.compile("Deep Dark Experimental Snapshot", Pattern.CASE_INSENSITIVE);
	private static final Pattern BETA_PRERELEASE = Pattern.compile("^Beta\\s+1\\.9\\s+Prerelease(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BETA_VERSION = Pattern.compile("^(\\d+)[._](\\d+)(?:[._](\\d+))?$");
	private static final Pattern ALPHA_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");
	private static final Pattern CLASSIC = Pattern.compile("^0\\.(\\d+)(?:\\.(\\d+))?(.*)$");
	private static final Pattern NUMBERED_REVISION = Pattern.compile("_(\\d+)");
	private static final Pattern DATE = Pattern.compile("(\\d{8})");
	private static final Pattern LEADING_RELEASE = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
	private static final Pattern PRERELEASE_MARKER = Pattern.compile(".*(pre[- ]?release|prerelease|release candidate|-pre\\d*| pre\\d*| rc\\d*|preview).*", Pattern.CASE_INSENSITIVE);
	private static final Pattern RELEASE = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");
	private static final Pattern SNAPSHOT = Pattern.compile("^(\\d{2})w(\\d{2})([a-zA-Z])?$");
	private static final Pattern DIGITS = Pattern.compile("\\d+");

	private static final int FIRST_INDEV_DATE = 20091223;
	private static final int FIRST_INDEV_KEY = 600_000;
	private static final int FIRST_INFODEV_DATE = 20100227;
	private static final int FIRST_INFODEV_KEY = 700_000;
	private static final int FIRST_CLASSIC_KEY = 500_000;
	private static final int FIRST_PRE_CLASSIC_KEY = 400_000;
	private static final int FIRST_ALPHA_KEY = 800_000;
	private static final int FIRST_BETA_KEY = 900_000;

	private record ReleaseDate(String version, LocalDate date) {
	}

	private static final List<ReleaseDate> RELEASE_DATES = List.of(
			new ReleaseDate("1.0", LocalDate.of(2011, 11, 18)),
			new ReleaseDate("1.1", LocalDate.of(2012, 1, 12)),
			new ReleaseDate("1.2", LocalDate.of(2012, 3, 1)),
			new ReleaseDate("1.3", LocalDate.of(2012, 8, 1)),
			new ReleaseDate("1.4", LocalDate.of(2012, 10, 25)),
			new ReleaseDate("1.5", LocalDate.of(2013, 3, 13)),
			new ReleaseDate("1.6", LocalDate.of(2013, 7, 1)),
			new ReleaseDate("1.7", LocalDate.of(2013, 10, 25)),
			new ReleaseDate("1.8", LocalDate.of(2014, 9, 2)),
			new ReleaseDate("1.9", LocalDate.of(2016, 2, 29)),
			new ReleaseDate("1.10", LocalDate.of(2016, 6, 8)),
			new ReleaseDate("1.11", LocalDate.of(2016, 11, 14)),
			new ReleaseDate("1.12", LocalDate.of(2017, 6, 7)),
			new ReleaseDate("1.13", LocalDate.of(2018, 7, 18)),
			new ReleaseDate("1.14", LocalDate.of(2019, 4, 23)),
			new ReleaseDate("1.15", LocalDate.of(2019, 12, 10)),
			new ReleaseDate("1.16", LocalDate.of(2020, 6, 23)),
			new ReleaseDate("1.17", LocalDate.of(2021, 6, 8)),
			new ReleaseDate("1.18", LocalDate.of(2021, 11, 30)),
			new ReleaseDate("1.19", LocalDate.of(2022, 6, 7)),
			new ReleaseDate("1.20", LocalDate.of(2023, 6, 7)),
			new ReleaseDate("1.21", LocalDate.of(2024, 6, 13)),
			new ReleaseDate("1.21.2", LocalDate.of(2024, 10, 22)),
			new ReleaseDate("1.21.4", LocalDate.of(2024, 12, 3)),
			new ReleaseDate("1.21.5", LocalDate.of(2025, 3, 25)),
			new ReleaseDate("1.21.6", LocalDate.of(2025, 6, 17)),
			new ReleaseDate("1.21.9", LocalDate.of(2025, 9, 30)),
			new ReleaseDate("1.21.11", LocalDate.of(2025, 12, 9)),
			new ReleaseDate("26.1", LocalDate.of(2026, 3, 24)),
			new ReleaseDate("26.2", LocalDate.of(2026, 6, 16)),
			new ReleaseDate("26.3", LocalDate.of(2026, 9, 29))
	);

	private ReleaseTimeline() {
	}

	public static int releaseKey(String version) {
		if (version == null || version.isBlank()) {
			return -1;
		}
		String value = version.trim();

		if (DEEP_DARK.matcher(value).find()) {
			return releaseKey("1.19") - 1;
		}

		Matcher betaPrerelease = BETA_PRERELEASE.matcher(value);
		if (betaPrerelease.matches()) {
			int revision = betaPrerelease.group(1) == null ? 0 : Integer.parseInt(betaPrerelease.group(1));
			return FIRST_BETA_KEY + 1 * 10_000 + 9 * 100 + revision;
		}

		String lower = value.toLowerCase(java.util.Locale.ROOT);
		String betaBody = null;
		if (lower.startsWith("beta ")) {
			betaBody = value.substring(5).trim();
		} else if (value.contains("_")) {
			betaBody = value;
		}
		if (betaBody != null) {
			Matcher beta = BETA_VERSION.matcher(betaBody);
			if (beta.matches()) {
				return eraKey(FIRST_BETA_KEY, beta);
			}
		}

		String alphaBody = null;
		if (lower.startsWith("alpha ")) {
			alphaBody = value.substring(6).trim();
		} else if (value.startsWith("v") || value.startsWith("V")) {
			alphaBody = value;
		}
		if (alphaBody != null) {
			if (alphaBody.startsWith("v") || alphaBody.startsWith("V")) {
				alphaBody = alphaBody.substring(1);
			}
			Matcher alpha = ALPHA_VERSION.matcher(alphaBody);
			if (alpha.matches()) {
				return eraKey(FIRST_ALPHA_KEY, alpha);
			}
		}

		if (lower.startsWith("rd-") || lower.contains("cave game tech test")) {
			Matcher digits = DIGITS.matcher(value);
			int build = digits.find() ? Integer.parseInt(digits.group()) % 10_000 : 0;
			return FIRST_PRE_CLASSIC_KEY + build;
		}

		if (value.startsWith("0.") || lower.contains("survival test") || lower.startsWith("classic ")) {
			return classicKey(value);
		}

		Integer date = findDate(value);
		if (date != null) {
			if (date >= FIRST_INDEV_DATE && date <= 20100226) {
				return FIRST_INDEV_KEY + (date - FIRST_INDEV_DATE);
			}
			if (date >= FIRST_INFODEV_DATE) {
				return FIRST_INFODEV_KEY + (date - 20100000);
			}
		}
		if (lower.startsWith("indev")) {
			return FIRST_INDEV_KEY;
		}

		Matcher leading = LEADING_RELEASE.matcher(value);
		if (leading.lookingAt() && PRERELEASE_MARKER.matcher(value).matches()) {
			return releaseTripleKey(leading) - 1;
		}

		Matcher release = RELEASE.matcher(value);
		if (release.matches()) {
			return releaseTripleKey(release);
		}

		Matcher snapshot = SNAPSHOT.matcher(value);
		if (snapshot.matches()) {
			return snapshotKey(snapshot);
		}

		return -1;
	}

	private static int eraKey(int base, Matcher matcher) {
		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
		return base + major * 10_000 + minor * 100 + patch;
	}

	private static int classicKey(String value) {
		Matcher classic = CLASSIC.matcher(value);
		if (!classic.matches()) {
			return FIRST_CLASSIC_KEY;
		}
		int minor = Integer.parseInt(classic.group(1));
		int patch = classic.group(2) == null ? 0 : Integer.parseInt(classic.group(2));
		String rest = classic.group(3) == null ? "" : classic.group(3);
		int revision = 0;
		if (!rest.isEmpty() && Character.isLetter(rest.charAt(0))) {
			revision = Character.toLowerCase(rest.charAt(0)) - 'a' + 1;
		}
		Matcher numbered = NUMBERED_REVISION.matcher(rest);
		while (numbered.find()) {
			revision = Math.max(revision, Integer.parseInt(numbered.group(1)));
		}
		return FIRST_CLASSIC_KEY + minor * 1_000 + patch * 10 + revision;
	}

	private static Integer findDate(String value) {
		Matcher matcher = DATE.matcher(value);
		while (matcher.find()) {
			String digits = matcher.group(1);
			try {
				LocalDate date = LocalDate.parse(digits, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
				return Integer.parseInt(digits);
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static int releaseTripleKey(Matcher matcher) {
		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
		return major * 1_000_000 + minor * 1_000 + patch;
	}

	private static int snapshotKey(Matcher snapshot) {
		int year = 2000 + Integer.parseInt(snapshot.group(1));
		int week = Integer.parseInt(snapshot.group(2));
		LocalDate approximate = LocalDate.of(year, 1, 1).plusWeeks(week - 1);
		for (ReleaseDate release : RELEASE_DATES) {
			if (!release.date().isBefore(approximate)) {
				return releaseKey(release.version()) - 1;
			}
		}
		return 27_000_000 - 1;
	}
}
