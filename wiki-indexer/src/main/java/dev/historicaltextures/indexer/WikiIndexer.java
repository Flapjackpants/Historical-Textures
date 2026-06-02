package dev.historicaltextures.indexer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiIndexer {
	private static final String API = "https://minecraft.wiki/api.php";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern FILE_PATTERN = Pattern.compile("File:([^\\]|#]+)");
	private static final Pattern SOUND_EVENT_PATTERN = Pattern.compile(
			"\\b([a-z]+\\.[a-z0-9_.]+\\.[a-z0-9_.]+)\\b",
			Pattern.CASE_INSENSITIVE
	);
	private static final List<String> SEED_PAGES = List.of(
			"List_of_historical_block_textures",
			"Java_Edition_history_of_textures/Blocks",
			"Java_Edition_history_of_textures/Items",
			"Java_Edition_history_of_textures/Entities",
			"Bedrock_Edition_history_of_textures/Blocks",
			"Bedrock_Edition_history_of_textures/Items",
			"Bedrock_Edition_history_of_textures/Entities"
	);
	private static final List<String> SOUND_SEED_PAGES = List.of(
			"History_of_sounds"
	);
	private static final Map<String, String> SOUND_HEURISTICS = Map.ofEntries(
			Map.entry("cow_hurt_old", "entity.cow.hurt"),
			Map.entry("cow_old", "entity.cow.ambient"),
			Map.entry("creeper_oldhurt1", "entity.creeper.hurt"),
			Map.entry("door_opening_old", "block.wooden_door.open"),
			Map.entry("door_closing_old", "block.wooden_door.close"),
			Map.entry("explosion_old", "entity.generic.explode"),
			Map.entry("bow_shooting_old", "entity.arrow.shoot"),
			Map.entry("skeleton_death_old", "entity.skeleton.death"),
			Map.entry("xp_old", "entity.experience_orb.pickup"),
			Map.entry("arrow_old", "entity.arrow.hit"),
			Map.entry("hurt_old", "entity.player.hurt"),
			Map.entry("lava_old", "block.lava.ambient"),
			Map.entry("water_old", "liquid.water"),
			Map.entry("fall_old", "entity.generic.small_fall")
	);

	private final Path outputRoot;
	private final boolean quickMode;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	private final Map<String, String> shaToAssetPath = new LinkedHashMap<>();
	private final Set<String> processedFileKeys = new LinkedHashSet<>();
	private final Map<String, WikiTableParser.VersionInfo> versionByFile = new LinkedHashMap<>();
	private final Map<String, String> wikitextCache = new HashMap<>();
	private JsonObject overrides;
	private int mappedTextureCount;
	private int unmappedTextureCount;
	private int skippedSoundCount;

	public WikiIndexer(Path outputRoot, boolean quickMode) {
		this.outputRoot = outputRoot;
		this.quickMode = quickMode;
	}

	public void run() throws Exception {
		loadOverrides();
		Files.createDirectories(outputRoot.resolve("assets"));
		List<VariantRecord> textures = new ArrayList<>();
		List<SoundRecord> sounds = new ArrayList<>();

		if (quickMode) {
			indexOverrideFiles(textures, sounds);
		} else {
			for (String page : SEED_PAGES) {
				System.out.println("Indexing page: " + page);
				String wikitext = fetchPageWikitext(page);
				mergeVersions(WikiTableParser.parseVersionsFromWikitext(wikitext, pageEdition(page)));
				crawlPage(page, wikitext, textures);
				Thread.sleep(250);
			}
			for (String page : SOUND_SEED_PAGES) {
				System.out.println("Indexing sound page: " + page);
				String wikitext = fetchPageWikitext(page);
				mergeVersions(WikiTableParser.parseVersionsFromWikitext(wikitext, pageEdition(page)));
				crawlSoundPage(page, wikitext, sounds);
				Thread.sleep(250);
			}
			crawlHistoricalSounds(sounds);
		}

		writeCatalog(textures, sounds);
		System.out.println("Catalog written to " + outputRoot.resolve("catalog.json"));
		System.out.println("Texture variants: " + textures.size()
				+ " (mapped=" + mappedTextureCount + ", unmapped=" + unmappedTextureCount + ")");
		System.out.println("Sound variants: " + sounds.size() + " (skipped unmapped downloads=" + skippedSoundCount + ")");
		System.out.println("Version table entries: " + versionByFile.size());
	}

	private void indexOverrideFiles(List<VariantRecord> textures, List<SoundRecord> sounds) throws Exception {
		JsonObject fileTargets = overrides.getAsJsonObject("fileTargets");
		if (fileTargets != null) {
			for (var entry : fileTargets.entrySet()) {
				processTextureFile(entry.getKey(), textures, WikiFilenameUtil.CrawlContext.UNKNOWN, "overrides.json");
				Thread.sleep(100);
			}
		}
		JsonObject soundTargets = overrides.getAsJsonObject("soundTargets");
		if (soundTargets != null) {
			for (var entry : soundTargets.entrySet()) {
				processSoundFile(entry.getKey(), sounds);
				Thread.sleep(100);
			}
		}
	}

	private void loadOverrides() throws IOException {
		Path path = Path.of("wiki-indexer/overrides.json");
		if (!Files.isRegularFile(path)) {
			path = Path.of("overrides.json");
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			overrides = GSON.fromJson(reader, JsonObject.class);
		}
	}

	private void crawlPage(String pageTitle, String wikitext, List<VariantRecord> textures) throws Exception {
		WikiFilenameUtil.CrawlContext context = WikiFilenameUtil.parsePageContext(pageTitle);
		Set<String> seenOnPage = new LinkedHashSet<>();
		Matcher matcher = FILE_PATTERN.matcher(wikitext);
		while (matcher.find()) {
			String fileName = matcher.group(1).trim();
			if (!WikiFilenameUtil.isTextureFile(fileName)) {
				continue;
			}
			if (seenOnPage.add(WikiFilenameUtil.normalizeKey(fileName))) {
				processTextureFile(fileName, textures, context, pageTitle);
			}
		}
	}

	private void crawlSoundPage(String pageTitle, String wikitext, List<SoundRecord> sounds) throws Exception {
		Set<String> seenOnPage = new LinkedHashSet<>();
		Matcher matcher = FILE_PATTERN.matcher(wikitext);
		while (matcher.find()) {
			String fileName = matcher.group(1).trim();
			if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
				continue;
			}
			if (seenOnPage.add(WikiFilenameUtil.normalizeKey(fileName))) {
				processSoundFile(fileName, sounds);
			}
		}
	}

	private void crawlHistoricalSounds(List<SoundRecord> sounds) throws Exception {
		String continuation = null;
		do {
			String url = API + "?action=query&list=categorymembers&cmtitle=Category:Historical_sounds&cmlimit=50&format=json";
			if (continuation != null) {
				url += "&cmcontinue=" + encode(continuation);
			}
			JsonObject response = getJson(url);
			JsonObject query = response.getAsJsonObject("query");
			if (query != null && query.has("categorymembers")) {
				for (var member : query.getAsJsonArray("categorymembers")) {
					JsonObject object = member.getAsJsonObject();
					if (object.get("ns").getAsInt() != 6) {
						continue;
					}
					String title = object.get("title").getAsString();
					if (title.startsWith("File:")) {
						processSoundFile(title.substring("File:".length()), sounds);
					}
				}
			}
			continuation = response.has("continue")
					? response.getAsJsonObject("continue").get("cmcontinue").getAsString()
					: null;
			Thread.sleep(250);
		} while (continuation != null);
	}

	private void processTextureFile(
			String fileName,
			List<VariantRecord> textures,
			WikiFilenameUtil.CrawlContext context,
			String sourcePage
	) throws Exception {
		String fileKey = WikiFilenameUtil.normalizeKey(fileName);
		if (!processedFileKeys.add(fileKey)) {
			return;
		}
		String assetPath = downloadFile(fileName);
		if (assetPath == null) {
			return;
		}
		List<String> targets = resolveTextureTargets(fileName);
		boolean mapped = !targets.isEmpty();
		if (!mapped) {
			targets = WikiFilenameUtil.guessTargets(fileName, context);
		}
		if (mapped) {
			mappedTextureCount++;
		} else {
			unmappedTextureCount++;
		}
		List<String> textureTags = WikiFilenameUtil.parseTextureTags(fileName);
		WikiTableParser.VersionInfo version = versionForFile(fileName, textureTags);
		VariantRecord record = new VariantRecord(
				WikiFilenameUtil.toId(fileName),
				fileName,
				WikiFilenameUtil.parseEditions(fileName),
				textureTags,
				version.javaVersion(),
				version.bedrockVersion(),
				version.displayVersion(),
				sourcePage,
				assetPath,
				targets,
				WikiFilenameUtil.humanLabel(fileName),
				mapped
		);
		textures.add(record);
	}

	private void processSoundFile(String fileName, List<SoundRecord> sounds) throws Exception {
		if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
			return;
		}
		String fileKey = WikiFilenameUtil.normalizeKey(fileName);
		if (!processedFileKeys.add("sound:" + fileKey)) {
			return;
		}
		JsonObject mapping = findSoundMapping(fileName);
		String soundEvent = null;
		String vanillaSoundPath = "";
		if (mapping != null) {
			soundEvent = mapping.get("soundEvent").getAsString();
			if (mapping.has("vanillaSoundPath")) {
				vanillaSoundPath = mapping.get("vanillaSoundPath").getAsString();
			}
		} else {
			soundEvent = guessSoundEvent(fileName);
			if (soundEvent == null) {
				String pageText = fetchPageWikitext("File:" + fileName);
				soundEvent = extractSoundEventFromText(pageText);
			}
		}
		if (soundEvent == null || soundEvent.isEmpty()) {
			skippedSoundCount++;
			processedFileKeys.remove("sound:" + fileKey);
			return;
		}
		String assetPath = downloadFile(fileName);
		if (assetPath == null) {
			return;
		}
		List<String> textureTags = List.of();
		WikiTableParser.VersionInfo version = versionForFile(fileName, textureTags);
		String human = WikiFilenameUtil.humanLabel(fileName);
		String label = version.displayVersion().isEmpty() || version.displayVersion().equals(human)
				? human
				: version.displayVersion() + " — " + human;
		sounds.add(new SoundRecord(
				WikiFilenameUtil.toId(fileName),
				fileName,
				soundEvent,
				assetPath,
				vanillaSoundPath,
				label,
				textureTags,
				version.javaVersion(),
				version.bedrockVersion(),
				version.displayVersion()
		));
	}

	private WikiTableParser.VersionInfo versionForFile(String fileName, List<String> textureTags) {
		WikiTableParser.VersionInfo version = versionByFile.getOrDefault(
				WikiFilenameUtil.normalizeKey(fileName),
				WikiTableParser.VersionInfo.empty()
		);
		if (!version.javaVersion().isEmpty() || !version.bedrockVersion().isEmpty()) {
			return version;
		}
		String fallback = WikiFilenameUtil.fallbackDisplayVersion(fileName, textureTags);
		return new WikiTableParser.VersionInfo("", "", fallback);
	}

	private void mergeVersions(Map<String, WikiTableParser.VersionInfo> parsed) {
		for (Map.Entry<String, WikiTableParser.VersionInfo> entry : parsed.entrySet()) {
			versionByFile.merge(entry.getKey(), entry.getValue(), WikiTableParser.VersionInfo::merge);
		}
	}

	private static WikiTableParser.WikiEdition pageEdition(String pageTitle) {
		if (pageTitle.startsWith("Bedrock_Edition")) {
			return WikiTableParser.WikiEdition.BEDROCK;
		}
		return WikiTableParser.WikiEdition.JAVA;
	}

	private List<String> resolveTextureTargets(String fileName) {
		JsonArray array = findOverrideArray(overrides.getAsJsonObject("fileTargets"), fileName);
		if (array == null) {
			return List.of();
		}
		List<String> list = new ArrayList<>();
		for (JsonElement element : array) {
			list.add(element.getAsString());
		}
		return list;
	}

	private JsonObject findSoundMapping(String fileName) {
		JsonObject soundTargets = overrides.getAsJsonObject("soundTargets");
		if (soundTargets == null) {
			return null;
		}
		JsonElement element = findOverrideElement(soundTargets, fileName);
		return element != null ? element.getAsJsonObject() : null;
	}

	private JsonArray findOverrideArray(JsonObject object, String fileName) {
		JsonElement element = findOverrideElement(object, fileName);
		return element != null ? element.getAsJsonArray() : null;
	}

	private JsonElement findOverrideElement(JsonObject object, String fileName) {
		if (object == null) {
			return null;
		}
		if (object.has(fileName)) {
			return object.get(fileName);
		}
		String normalized = WikiFilenameUtil.normalizeKey(fileName);
		for (var entry : object.entrySet()) {
			if (WikiFilenameUtil.normalizeKey(entry.getKey()).equals(normalized)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private String guessSoundEvent(String fileName) {
		String key = WikiFilenameUtil.normalizeKey(fileName);
		if (key.endsWith(".ogg")) {
			key = key.substring(0, key.length() - 4);
		}
		return SOUND_HEURISTICS.get(key);
	}

	private String extractSoundEventFromText(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		Matcher matcher = SOUND_EVENT_PATTERN.matcher(text);
		while (matcher.find()) {
			String candidate = matcher.group(1).toLowerCase(Locale.ROOT);
			if (candidate.contains(".") && !candidate.startsWith("file.")) {
				return candidate;
			}
		}
		return null;
	}

	private String fetchPageWikitext(String pageTitle) throws Exception {
		if (wikitextCache.containsKey(pageTitle)) {
			return wikitextCache.get(pageTitle);
		}
		String url = API + "?action=parse&page=" + encode(pageTitle) + "&prop=wikitext&format=json";
		JsonObject response = getJson(url);
		JsonObject parse = response.getAsJsonObject("parse");
		String wikitext = "";
		if (parse != null && parse.has("wikitext")) {
			wikitext = parse.getAsJsonObject("wikitext").get("*").getAsString();
		}
		wikitextCache.put(pageTitle, wikitext);
		return wikitext;
	}

	private String downloadFile(String fileName) throws Exception {
		String url = API + "?action=query&titles=File:" + encode(fileName)
				+ "&prop=imageinfo&iiprop=url|sha1|mime&format=json";
		JsonObject response = getJson(url);
		JsonObject query = response.getAsJsonObject("query");
		if (query == null) {
			return null;
		}
		for (var page : query.getAsJsonObject("pages").entrySet()) {
			JsonObject pageObject = page.getValue().getAsJsonObject();
			if (!pageObject.has("imageinfo")) {
				continue;
			}
			JsonObject info = pageObject.getAsJsonArray("imageinfo").get(0).getAsJsonObject();
			String downloadUrl = info.get("url").getAsString();
			String sha1 = info.has("sha1") ? info.get("sha1").getAsString() : hashName(fileName);
			String prefix = sha1.length() >= 2 ? sha1.substring(0, 2) : "00";
			String assetPath = "assets/" + prefix + "/" + fileName;
			if (shaToAssetPath.containsKey(sha1)) {
				return shaToAssetPath.get(sha1);
			}
			Path destination = outputRoot.resolve(assetPath);
			Files.createDirectories(destination.getParent());
			HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
			HttpResponse<InputStream> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = httpResponse.body()) {
				Files.copy(body, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			shaToAssetPath.put(sha1, assetPath);
			return assetPath;
		}
		return null;
	}

	private void writeCatalog(List<VariantRecord> textures, List<SoundRecord> sounds) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 2);
		JsonArray variantArray = new JsonArray();
		for (VariantRecord texture : textures) {
			JsonObject object = new JsonObject();
			object.addProperty("id", texture.id());
			object.addProperty("wikiFile", texture.wikiFile());
			JsonArray editions = new JsonArray();
			for (String edition : texture.editions()) {
				editions.add(edition);
			}
			object.add("editions", editions);
			JsonArray tags = new JsonArray();
			for (String tag : texture.textureTags()) {
				tags.add(tag);
			}
			object.add("textureTags", tags);
			object.addProperty("javaVersion", texture.javaVersion());
			object.addProperty("bedrockVersion", texture.bedrockVersion());
			object.addProperty("displayVersion", texture.displayVersion());
			object.addProperty("introducedIn", texture.introducedIn());
			object.addProperty("assetPath", texture.assetPath());
			object.addProperty("label", texture.label());
			object.addProperty("mapped", texture.mapped());
			JsonArray targets = new JsonArray();
			for (String target : texture.targets()) {
				targets.add(target);
			}
			object.add("targets", targets);
			variantArray.add(object);
		}
		root.add("variants", variantArray);

		JsonArray soundArray = new JsonArray();
		for (SoundRecord sound : sounds) {
			JsonObject object = new JsonObject();
			object.addProperty("id", sound.id());
			object.addProperty("wikiFile", sound.wikiFile());
			object.addProperty("soundEvent", sound.soundEvent());
			object.addProperty("assetPath", sound.assetPath());
			object.addProperty("vanillaSoundPath", sound.vanillaSoundPath());
			object.addProperty("label", sound.label());
			object.addProperty("javaVersion", sound.javaVersion());
			object.addProperty("bedrockVersion", sound.bedrockVersion());
			object.addProperty("displayVersion", sound.displayVersion());
			JsonArray tags = new JsonArray();
			for (String tag : sound.textureTags()) {
				tags.add(tag);
			}
			object.add("textureTags", tags);
			soundArray.add(object);
		}
		root.add("soundVariants", soundArray);

		Files.writeString(outputRoot.resolve("catalog.json"), GSON.toJson(root));
	}

	private JsonObject getJson(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url.replace("|", "%7C")))
				.header("User-Agent", "HistoricalTexturesIndexer/1.0")
				.GET()
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		return GSON.fromJson(response.body(), JsonObject.class);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String hashName(String fileName) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		byte[] hash = digest.digest(fileName.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(hash);
	}

	private record VariantRecord(
			String id,
			String wikiFile,
			List<String> editions,
			List<String> textureTags,
			String javaVersion,
			String bedrockVersion,
			String displayVersion,
			String introducedIn,
			String assetPath,
			List<String> targets,
			String label,
			boolean mapped
	) {
	}

	private record SoundRecord(
			String id,
			String wikiFile,
			String soundEvent,
			String assetPath,
			String vanillaSoundPath,
			String label,
			List<String> textureTags,
			String javaVersion,
			String bedrockVersion,
			String displayVersion
	) {
	}
}
