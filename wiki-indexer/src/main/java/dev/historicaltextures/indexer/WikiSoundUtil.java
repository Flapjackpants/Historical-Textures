package dev.historicaltextures.indexer;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiSoundUtil {
	private static final Pattern FILE_HISTORY_DATE = Pattern.compile(
			"\\|\\s*current\\s*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\s*([^|]+?)\\s*\\|",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern TAG_PATTERN = Pattern.compile("(?:^|[\\s_])(JE|BE|TU)(\\d+)", Pattern.CASE_INSENSITIVE);

	private WikiSoundUtil() {
	}

	public record SoundMapping(String soundEvent, String vanillaSoundPath, String versionHint) {
	}

	public static Optional<SoundMapping> mapFile(String fileName) {
		String key = WikiFilenameUtil.normalizeKey(fileName);
		if (key.endsWith(".ogg")) {
			key = key.substring(0, key.length() - 4);
		}

		Optional<SoundMapping> mapping = mapByKey(key);
		if (mapping.isPresent()) {
			return mapping;
		}
		return mapByPattern(fileName, key);
	}

	public static String inferVersionLabel(String fileName, String filePageWikitext) {
		WikiTableParser.VersionInfo tableVersion = WikiTableParser.parseVersionsFromWikitext(
				filePageWikitext == null ? "" : filePageWikitext
		).get(WikiFilenameUtil.normalizeKey(fileName));
		if (tableVersion != null && !tableVersion.displayVersion().isEmpty()) {
			return tableVersion.displayVersion();
		}

		String fromHistory = parseUploadDateLabel(filePageWikitext);
		if (fromHistory != null) {
			return fromHistory;
		}

		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.contains("revision 1")) {
			return "Revision 1";
		}
		if (lower.contains("revision 2")) {
			return "Revision 2";
		}
		if (lower.contains(" old") || lower.endsWith("_old") || lower.contains("_old.")) {
			return "Historical (old)";
		}
		if (lower.contains("je1")) {
			return "JE1";
		}

		Matcher tag = TAG_PATTERN.matcher(fileName);
		if (tag.find()) {
			return tag.group(1).toUpperCase(Locale.ROOT) + tag.group(2);
		}

		if (lower.contains("door close") && !lower.contains("old")) {
			return "1.9+";
		}
		if (lower.contains("door open") && !lower.contains("old")) {
			return "1.9+";
		}

		return WikiFilenameUtil.humanLabel(fileName);
	}

	private static String parseUploadDateLabel(String wikitext) {
		if (wikitext == null || wikitext.isEmpty()) {
			return null;
		}
		Matcher matcher = FILE_HISTORY_DATE.matcher(wikitext);
		if (!matcher.find()) {
			return null;
		}
		String raw = matcher.group(1).trim();
		if (raw.isEmpty()) {
			return null;
		}
		// e.g. "09:15, 24 March 2016" -> keep human-readable date portion
		int comma = raw.indexOf(',');
		if (comma >= 0 && comma + 1 < raw.length()) {
			return raw.substring(comma + 1).trim();
		}
		return raw;
	}

	private static Optional<SoundMapping> mapByKey(String key) {
		return switch (key) {
			case "arrow_old" -> optional("entity.arrow.hit", "sounds/random/bowhit.ogg", "Historical (old)");
			case "bow_shooting_old" -> optional("entity.arrow.shoot", "sounds/random/bow.ogg", "Historical (old)");
			case "cow_hurt_old", "cowhurt1", "cowhurt2", "cowhurt3" ->
					optional("entity.cow.hurt", "sounds/mob/cow/hurt1.ogg", "Historical (old)");
			case "cow_old", "cow1", "cow2", "cow3", "cow4" ->
					optional("entity.cow.ambient", "sounds/mob/cow/say1.ogg", "Historical (old)");
			case "creeper_oldhurt1", "creeper_oldhurt2", "creeper_oldhurt3", "creeper_oldhurt4" ->
					optional("entity.creeper.hurt", "sounds/mob/creeper/say1.ogg", "Historical (old)");
			case "door_closing_old" ->
					optional("block.wooden_door.close", "sounds/random/door_close.ogg", "0.0.14a – 1.8.9");
			case "door_opening_old" ->
					optional("block.wooden_door.open", "sounds/random/door_open.ogg", "0.0.14a – 1.8.9");
			case "door_close" ->
					optional("block.wooden_door.close", "sounds/block/wooden_door/close1.ogg", "1.9+");
			case "door_open" ->
					optional("block.wooden_door.open", "sounds/block/wooden_door/open1.ogg", "1.9+");
			case "explosion_old" -> optional("entity.generic.explode", "sounds/random/explode1.ogg", "Historical (old)");
			case "flint_and_steel_old" ->
					optional("item.flint_and_steel.use", "sounds/item/flintandsteel/use.ogg", "Historical (old)");
			case "hurt_old", "hurtflesh1", "hurtflesh2", "hurtflesh3" ->
					optional("entity.player.hurt", "sounds/damage/hit1.ogg", "Historical (old)");
			case "lava_old" -> optional("block.lava.ambient", "sounds/block/lava/ambient.ogg", "Historical (old)");
			case "skeleton_death_old" ->
					optional("entity.skeleton.death", "sounds/mob/skeleton/death.ogg", "Historical (old)");
			case "skeleton_death_revision_1" ->
					optional("entity.skeleton.death", "sounds/mob/skeleton/death.ogg", "Revision 1");
			case "skeleton_hurt1_revision_1", "skeleton_hurt2_revision_1",
					"skeleton_hurt3_revision_1", "skeleton_hurt4_revision_1" ->
					optional("entity.skeleton.hurt", "sounds/mob/skeleton/hurt1.ogg", "Revision 1");
			case "water_splash_old" ->
					optional("entity.player.splash", "sounds/liquid/splash.ogg", "Historical (old)");
			case "xp_old" -> optional("entity.experience_orb.pickup", "sounds/random/orb.ogg", "Historical (old)");
			case "fallbig1", "fallbig2" -> optional("entity.generic.big_fall", "sounds/damage/fallbig.ogg", "Historical");
			case "fallsmall" -> optional("entity.generic.small_fall", "sounds/damage/fallsmall.ogg", "Historical");
			case "birds_screaming_loop" -> optional("ambient.cave", "sounds/ambient/cave/cave1.ogg", "Historical");
			case "cave_chimes" -> optional("ambient.cave", "sounds/ambient/cave/cave2.ogg", "Historical");
			case "calm4" -> optional("music.creative", "sounds/music/game/calm4.ogg", "Historical");
			case "ocean" -> optional("ambient.ocean", "sounds/ambient/ocean/ocean1.ogg", "Historical");
			case "waterfall" -> optional("ambient.underwater.loop", "sounds/ambient/underwater/underwater1.ogg", "Historical");
			case "block_of_resin_dig" -> optional("block.resin.break", "sounds/block/resin/break1.ogg", "1.21+");
			case "ominous_bottle_dispose_je1" ->
					optional("item.ominous_bottle.dispose", "sounds/item/ominous_bottle/dispose.ogg", "JE1");
			default -> Optional.empty();
		};
	}

	private static Optional<SoundMapping> mapByPattern(String fileName, String key) {
		String lower = key.replace('_', ' ');
		if (lower.contains("door") && lower.contains("clos")) {
			if (lower.contains("old")) {
				return optional("block.wooden_door.close", "sounds/random/door_close.ogg", "0.0.14a – 1.8.9");
			}
			return optional("block.wooden_door.close", "sounds/block/wooden_door/close1.ogg", "1.9+");
		}
		if (lower.contains("door") && lower.contains("open")) {
			if (lower.contains("old")) {
				return optional("block.wooden_door.open", "sounds/random/door_open.ogg", "0.0.14a – 1.8.9");
			}
			return optional("block.wooden_door.open", "sounds/block/wooden_door/open1.ogg", "1.9+");
		}
		if (lower.contains("skeleton") && lower.contains("death")) {
			return optional("entity.skeleton.death", "sounds/mob/skeleton/death.ogg", "Historical");
		}
		if (lower.contains("skeleton") && lower.contains("hurt")) {
			return optional("entity.skeleton.hurt", "sounds/mob/skeleton/hurt1.ogg", "Historical");
		}
		if (lower.contains("cow") && lower.contains("hurt")) {
			return optional("entity.cow.hurt", "sounds/mob/cow/hurt1.ogg", "Historical");
		}
		if (lower.contains("cow")) {
			return optional("entity.cow.ambient", "sounds/mob/cow/say1.ogg", "Historical");
		}
		if (lower.contains("creeper")) {
			return optional("entity.creeper.hurt", "sounds/mob/creeper/say1.ogg", "Historical");
		}
		if (lower.contains("explosion")) {
			return optional("entity.generic.explode", "sounds/random/explode1.ogg", "Historical");
		}
		if (lower.contains("bow")) {
			return optional("entity.arrow.shoot", "sounds/random/bow.ogg", "Historical");
		}
		if (lower.contains("arrow")) {
			return optional("entity.arrow.hit", "sounds/random/bowhit.ogg", "Historical");
		}
		if (lower.contains("hurt")) {
			return optional("entity.player.hurt", "sounds/damage/hit1.ogg", "Historical");
		}
		if (lower.contains("lava")) {
			return optional("block.lava.ambient", "sounds/block/lava/ambient.ogg", "Historical");
		}
		if (lower.contains("water") && lower.contains("splash")) {
			return optional("entity.player.splash", "sounds/liquid/splash.ogg", "Historical");
		}
		if (lower.contains("xp") || lower.contains("orb")) {
			return optional("entity.experience_orb.pickup", "sounds/random/orb.ogg", "Historical");
		}
		return Optional.empty();
	}

	private static Optional<SoundMapping> optional(String event, String vanillaPath, String versionHint) {
		return Optional.of(new SoundMapping(event, vanillaPath, versionHint));
	}
}
