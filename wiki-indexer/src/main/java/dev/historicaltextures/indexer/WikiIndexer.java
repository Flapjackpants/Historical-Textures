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
import java.util.Optional;
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
			"Java_Edition_history_of_sound_events"
	);
	private static final List<String> SOUND_CATEGORY_ROOTS = List.of(
			"Category:Historical_sounds",
			"Category:Block sounds",
			"Category:Entity sounds",
			"Category:Item sounds"
	);

	private final Path outputRoot;
	private final boolean quickMode;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	private final Map<String, String> shaToAssetPath = new LinkedHashMap<>();
	private final Set<String> processedFileKeys = new LinkedHashSet<>();
	private final Set<String> crawledSoundCategories = new LinkedHashSet<>();
	private final Map<String, WikiTableParser.VersionInfo> versionByFile = new LinkedHashMap<>();
	private final Map<String, String> wikitextCache = new HashMap<>();
	private final boolean soundsOnly;
	private JsonObject overrides;
	private int mappedTextureCount;
	private int unmappedTextureCount;
	private int skippedSoundCount;

	public WikiIndexer(Path outputRoot, boolean quickMode) {
		this(outputRoot, quickMode, false);
	}

	public WikiIndexer(Path outputRoot, boolean quickMode, boolean soundsOnly) {
		this.outputRoot = outputRoot;
		this.quickMode = quickMode;
		this.soundsOnly = soundsOnly;
	}

	public void run() throws Exception {
		loadOverrides();
		Files.createDirectories(outputRoot.resolve("assets"));
		List<VariantRecord> textures = new ArrayList<>();
		List<SoundRecord> sounds = new ArrayList<>();

		if (soundsOnly) {
			loadExistingTextures(textures);
			loadExistingSounds(sounds);
			indexAllHistoricalSounds(sounds);
		} else if (quickMode) {
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
			indexWikiSoundCatalog(sounds);
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
				processSoundFile(entry.getKey(), null, sounds);
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
				processSoundFile(fileName, null, sounds);
			}
		}
	}

	private void loadExistingSounds(List<SoundRecord> sounds) throws IOException {
		Path catalogPath = outputRoot.resolve("catalog.json");
		if (!Files.isRegularFile(catalogPath)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(catalogPath)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null || !root.has("soundVariants")) {
				return;
			}
			int restored = 0;
			for (var element : root.getAsJsonArray("soundVariants")) {
				JsonObject object = element.getAsJsonObject();
				String wikiFile = object.get("wikiFile").getAsString();
				String assetPath = object.get("assetPath").getAsString();
				if (!Files.isRegularFile(outputRoot.resolve(assetPath))) {
					continue;
				}
				List<String> tags = new ArrayList<>();
				if (object.has("textureTags")) {
					for (var tag : object.getAsJsonArray("textureTags")) {
						tags.add(tag.getAsString());
					}
				}
				sounds.add(new SoundRecord(
						object.get("id").getAsString(),
						wikiFile,
						object.get("soundEvent").getAsString(),
						assetPath,
						object.has("vanillaSoundPath") ? object.get("vanillaSoundPath").getAsString() : "",
						object.has("label") ? object.get("label").getAsString() : wikiFile,
						tags,
						object.has("javaVersion") ? object.get("javaVersion").getAsString() : "",
						object.has("bedrockVersion") ? object.get("bedrockVersion").getAsString() : "",
						object.has("displayVersion") ? object.get("displayVersion").getAsString() : ""
				));
				processedFileKeys.add("sound:" + WikiFilenameUtil.normalizeKey(wikiFile));
				restored++;
			}
			if (restored > 0) {
				System.out.println("Restored " + restored + " existing sound variant(s) from catalog");
			}
		}
	}

	private void loadExistingTextures(List<VariantRecord> textures) throws IOException {
		Path catalogPath = outputRoot.resolve("catalog.json");
		if (!Files.isRegularFile(catalogPath)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(catalogPath)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null || !root.has("variants")) {
				return;
			}
			for (var element : root.getAsJsonArray("variants")) {
				JsonObject object = element.getAsJsonObject();
				List<String> editions = new ArrayList<>();
				if (object.has("editions")) {
					for (var edition : object.getAsJsonArray("editions")) {
						editions.add(edition.getAsString());
					}
				}
				List<String> tags = new ArrayList<>();
				if (object.has("textureTags")) {
					for (var tag : object.getAsJsonArray("textureTags")) {
						tags.add(tag.getAsString());
					}
				}
				List<String> targets = new ArrayList<>();
				if (object.has("targets")) {
					for (var target : object.getAsJsonArray("targets")) {
						targets.add(target.getAsString());
					}
				}
				textures.add(new VariantRecord(
						object.get("id").getAsString(),
						object.get("wikiFile").getAsString(),
						editions,
						tags,
						object.has("javaVersion") ? object.get("javaVersion").getAsString() : "",
						object.has("bedrockVersion") ? object.get("bedrockVersion").getAsString() : "",
						object.has("displayVersion") ? object.get("displayVersion").getAsString() : "",
						object.has("introducedIn") ? object.get("introducedIn").getAsString() : "",
						object.get("assetPath").getAsString(),
						targets,
						object.has("label") ? object.get("label").getAsString() : object.get("wikiFile").getAsString(),
						!object.has("mapped") || object.get("mapped").getAsBoolean()
				));
			}
		}
	}

	private void indexAllHistoricalSounds(List<SoundRecord> sounds) throws Exception {
		for (String page : SOUND_SEED_PAGES) {
			System.out.println("Indexing sound page: " + page);
			String wikitext = fetchPageWikitext(page);
			versionByFile.putAll(WikiTableParser.parseVersionsFromWikitext(wikitext, pageEdition(page)));
			crawlSoundPage(page, wikitext, sounds);
			Thread.sleep(250);
		}
		indexWikiSoundCatalog(sounds);
	}

	private void indexWikiSoundCatalog(List<SoundRecord> sounds) throws Exception {
		for (String root : SOUND_CATEGORY_ROOTS) {
			System.out.println("Crawling sound category tree: " + root);
			crawlSoundCategoryRecursive(root, sounds);
		}
		crawlDiscoveredSoundCategories(sounds);
	}

	private void crawlSoundCategoryRecursive(String categoryTitle, List<SoundRecord> sounds) throws Exception {
		if (!crawledSoundCategories.add(categoryTitle)) {
			return;
		}
		if (WikiSoundCategoryUtil.isExcludedCategory(categoryTitle)) {
			return;
		}
		String continuation = null;
		do {
			String url = API + "?action=query&list=categorymembers&cmtitle=" + encode(categoryTitle)
					+ "&cmlimit=50&format=json";
			if (continuation != null) {
				url += "&cmcontinue=" + encode(continuation);
			}
			JsonObject response = getJson(url);
			JsonObject query = response.getAsJsonObject("query");
			if (query != null && query.has("categorymembers")) {
				for (var member : query.getAsJsonArray("categorymembers")) {
					JsonObject object = member.getAsJsonObject();
					int ns = object.get("ns").getAsInt();
					String title = object.get("title").getAsString();
					if (ns == 14 && title.startsWith("Category:")) {
						crawlSoundCategoryRecursive(title, sounds);
					} else if (ns == 6 && title.startsWith("File:")) {
						processSoundFile(title.substring("File:".length()), categoryTitle, sounds);
					}
				}
			}
			continuation = response.has("continue")
					? response.getAsJsonObject("continue").get("cmcontinue").getAsString()
					: null;
			Thread.sleep(200);
		} while (continuation != null);
	}

	private void crawlDiscoveredSoundCategories(List<SoundRecord> sounds) throws Exception {
		List<String> discovered = discoverSoundCategories();
		System.out.println("Discovered " + discovered.size() + " wiki sound categories");
		for (String category : discovered) {
			if (crawledSoundCategories.contains(category)) {
				continue;
			}
			crawlSoundCategoryRecursive(category, sounds);
		}
	}

	private List<String> discoverSoundCategories() throws Exception {
		List<String> categories = new ArrayList<>();
		String offset = null;
		do {
			String url = API + "?action=query&list=search&srsearch=" + encode("Category:sounds")
					+ "&srnamespace=14&srlimit=500&format=json";
			if (offset != null) {
				url += "&sroffset=" + encode(offset);
			}
			JsonObject response = getJson(url);
			JsonObject query = response.getAsJsonObject("query");
			if (query != null && query.has("search")) {
				for (var element : query.getAsJsonArray("search")) {
					String title = element.getAsJsonObject().get("title").getAsString();
					if (!title.endsWith(" sounds")) {
						continue;
					}
					if (!WikiSoundCategoryUtil.isExcludedCategory(title)) {
						categories.add(title);
					}
				}
			}
			offset = null;
			if (response.has("continue")) {
				JsonObject cont = response.getAsJsonObject("continue");
				if (cont.has("sroffset")) {
					offset = cont.get("sroffset").getAsString();
				}
			}
			Thread.sleep(200);
		} while (offset != null);
		return categories;
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
		if (version.javaVersion().isEmpty() && version.bedrockVersion().isEmpty() && version.displayVersion().isEmpty()) {
			String fallback = WikiFilenameUtil.fallbackDisplayVersion(fileName, textureTags);
			version = new WikiTableParser.VersionInfo("", "", fallback);
		}
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
		processSoundFile(fileName, null, sounds);
	}

	private void processSoundFile(String fileName, String wikiSoundCategory, List<SoundRecord> sounds) throws Exception {
		String lowerName = fileName.toLowerCase(Locale.ROOT);
		if (!lowerName.endsWith(".ogg") && !lowerName.endsWith(".wav")) {
			return;
		}
		if (lowerName.endsWith(".wav")) {
			// Minecraft resource packs expect .ogg; skip wav until conversion is added.
			return;
		}
		String fileKey = WikiFilenameUtil.normalizeKey(fileName);
		if (!processedFileKeys.add("sound:" + fileKey)) {
			return;
		}
		JsonObject overrideMapping = findSoundMapping(fileName);
		String soundEvent = null;
		String vanillaSoundPath = "";
		String versionHint = "";
		if (overrideMapping != null) {
			soundEvent = overrideMapping.get("soundEvent").getAsString();
			if (overrideMapping.has("vanillaSoundPath")) {
				vanillaSoundPath = overrideMapping.get("vanillaSoundPath").getAsString();
			}
			if (overrideMapping.has("versionHint")) {
				versionHint = overrideMapping.get("versionHint").getAsString();
			}
		} else {
			Optional<WikiSoundUtil.SoundMapping> guessed = Optional.empty();
			if (wikiSoundCategory != null) {
				guessed = WikiSoundCategoryUtil.mapCategoryFile(wikiSoundCategory, fileName);
			}
			if (guessed.isEmpty()) {
				guessed = WikiSoundUtil.mapFile(fileName);
			}
			if (guessed.isPresent()) {
				soundEvent = guessed.get().soundEvent();
				vanillaSoundPath = guessed.get().vanillaSoundPath();
				versionHint = guessed.get().versionHint();
			}
		}
		String pageText = "";
		if (soundEvent == null || soundEvent.isEmpty()) {
			pageText = fetchPageWikitext("File:" + fileName);
			soundEvent = extractSoundEventFromText(pageText);
		}
		if (soundEvent == null || soundEvent.isEmpty()) {
			skippedSoundCount++;
			processedFileKeys.remove("sound:" + fileKey);
			System.out.println("Skipped unmapped sound: " + fileName);
			return;
		}
		if (pageText.isEmpty()) {
			pageText = fetchPageWikitext("File:" + fileName);
		}
		String assetPath;
		try {
			assetPath = downloadFile(fileName);
		} catch (Exception exception) {
			skippedSoundCount++;
			processedFileKeys.remove("sound:" + fileKey);
			System.out.println("Failed to download sound " + fileName + ": " + exception.getMessage());
			return;
		}
		if (assetPath == null) {
			skippedSoundCount++;
			processedFileKeys.remove("sound:" + fileKey);
			System.out.println("Missing wiki asset for sound: " + fileName);
			return;
		}
		List<String> textureTags = List.of();
		WikiTableParser.VersionInfo version;
		if (!versionHint.isEmpty()) {
			version = new WikiTableParser.VersionInfo(versionHint, "", versionHint);
		} else {
			version = versionForFile(fileName, textureTags);
			if (version.javaVersion().isEmpty() && version.bedrockVersion().isEmpty()) {
				String inferred = WikiSoundUtil.inferVersionLabel(fileName, pageText);
				version = new WikiTableParser.VersionInfo(inferred, "", inferred);
			}
		}
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
		if (!version.displayVersion().isEmpty()) {
			return version;
		}
		return WikiTableParser.VersionInfo.empty();
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
		String wikitext = "";
		try {
			String url = API + "?action=parse&page=" + encode(pageTitle) + "&prop=wikitext&format=json";
			JsonObject response = getJson(url);
			if (response.has("error")) {
				System.out.println("Wiki parse error for " + pageTitle + ": " + response.get("error"));
			} else {
				JsonObject parse = response.getAsJsonObject("parse");
				if (parse != null && parse.has("wikitext")) {
					wikitext = parse.getAsJsonObject("wikitext").get("*").getAsString();
				}
			}
		} catch (Exception exception) {
			System.out.println("Failed to fetch wikitext for " + pageTitle + ": " + exception.getMessage());
		}
		wikitextCache.put(pageTitle, wikitext);
		return wikitext;
	}

	private String downloadFile(String fileName) throws Exception {
		try {
			String url = API + "?action=query&titles=File:" + encode(fileName)
					+ "&prop=imageinfo&iiprop=url|sha1|mime&format=json";
			JsonObject response = getJson(url);
			if (response.has("error")) {
				return null;
			}
			JsonObject query = response.getAsJsonObject("query");
			if (query == null || !query.has("pages")) {
				return null;
			}
			for (var page : query.getAsJsonObject("pages").entrySet()) {
				JsonObject pageObject = page.getValue().getAsJsonObject();
				if (pageObject.has("missing") || !pageObject.has("imageinfo")) {
					continue;
				}
				JsonObject info = pageObject.getAsJsonArray("imageinfo").get(0).getAsJsonObject();
				if (!info.has("url")) {
					continue;
				}
				String downloadUrl = info.get("url").getAsString();
				String sha1 = info.has("sha1") ? info.get("sha1").getAsString() : hashName(fileName);
				String prefix = sha1.length() >= 2 ? sha1.substring(0, 2) : "00";
				String assetPath = "assets/" + prefix + "/" + fileName;
				if (shaToAssetPath.containsKey(sha1)) {
					return shaToAssetPath.get(sha1);
				}
				Path destination = outputRoot.resolve(assetPath);
				if (Files.isRegularFile(destination)) {
					shaToAssetPath.put(sha1, assetPath);
					return assetPath;
				}
				Files.createDirectories(destination.getParent());
				HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
						.header("User-Agent", "HistoricalTexturesIndexer/1.0")
						.GET()
						.build();
				HttpResponse<InputStream> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
				if (httpResponse.statusCode() >= 400) {
					System.out.println("HTTP " + httpResponse.statusCode() + " downloading " + fileName);
					return null;
				}
				try (InputStream body = httpResponse.body()) {
					Files.copy(body, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				shaToAssetPath.put(sha1, assetPath);
				return assetPath;
			}
		} catch (Exception exception) {
			System.out.println("Download error for " + fileName + ": " + exception.getMessage());
			return null;
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
		String body = response.body();
		if (body == null || body.isBlank()) {
			return new JsonObject();
		}
		try {
			return GSON.fromJson(body, JsonObject.class);
		} catch (com.google.gson.JsonSyntaxException exception) {
			JsonObject error = new JsonObject();
			error.addProperty("error", "invalid_json_response");
			error.addProperty("body", body.length() > 200 ? body.substring(0, 200) : body);
			return error;
		}
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
