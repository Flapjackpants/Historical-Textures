package dev.historicaltextures.indexer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CatalogVersionEnricher {
	private static final String API = "https://minecraft.wiki/api.php";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<String> SEED_PAGES = List.of(
			"List_of_historical_block_textures",
			"Java_Edition_history_of_textures/Blocks",
			"Java_Edition_history_of_textures/Items",
			"Java_Edition_history_of_textures/Entities",
			"Bedrock_Edition_history_of_textures/Blocks",
			"Bedrock_Edition_history_of_textures/Items",
			"Bedrock_Edition_history_of_textures/Entities",
			"History_of_sounds"
	);

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	private final Map<String, String> wikitextCache = new HashMap<>();

	public void enrich(Path catalogPath) throws Exception {
		Map<String, WikiTableParser.VersionInfo> versionByFile = new HashMap<>();
		for (String page : SEED_PAGES) {
			System.out.println("Fetching versions from: " + page);
			String wikitext = fetchPageWikitext(page);
			WikiTableParser.WikiEdition edition = pageEdition(page);
			mergeVersions(versionByFile, WikiTableParser.parseVersionsFromWikitext(wikitext, edition));
			Thread.sleep(250);
		}
		System.out.println("Parsed version entries: " + versionByFile.size());

		JsonObject root;
		try (Reader reader = Files.newBufferedReader(catalogPath)) {
			root = GSON.fromJson(reader, JsonObject.class);
		}
		int texturesUpdated = enrichVariantArray(root.getAsJsonArray("variants"), versionByFile);
		int soundsUpdated = enrichVariantArray(root.getAsJsonArray("soundVariants"), versionByFile);
		Files.writeString(catalogPath, GSON.toJson(root));
		System.out.println("Updated " + texturesUpdated + " texture variant(s) and " + soundsUpdated + " sound variant(s) in " + catalogPath);
	}

	private static int enrichVariantArray(JsonArray array, Map<String, WikiTableParser.VersionInfo> versionByFile) {
		if (array == null) {
			return 0;
		}
		int updated = 0;
		for (var element : array) {
			JsonObject object = element.getAsJsonObject();
			String wikiFile = object.has("wikiFile") ? object.get("wikiFile").getAsString() : "";
			if (wikiFile.isEmpty()) {
				continue;
			}
			WikiTableParser.VersionInfo version = versionForFile(wikiFile, versionByFile);
			if (version.javaVersion().isEmpty() && version.bedrockVersion().isEmpty()) {
				continue;
			}
			object.addProperty("javaVersion", version.javaVersion());
			object.addProperty("bedrockVersion", version.bedrockVersion());
			object.addProperty("displayVersion", version.displayVersion());
			updated++;
		}
		return updated;
	}

	private static WikiTableParser.VersionInfo versionForFile(
			String wikiFile,
			Map<String, WikiTableParser.VersionInfo> versionByFile
	) {
		WikiTableParser.VersionInfo version = versionByFile.getOrDefault(
				WikiFilenameUtil.normalizeKey(wikiFile),
				WikiTableParser.VersionInfo.empty()
		);
		if (!version.displayVersion().isEmpty()) {
			return version;
		}
		List<String> textureTags = WikiFilenameUtil.parseTextureTags(wikiFile);
		String fallback = WikiFilenameUtil.fallbackDisplayVersion(wikiFile, textureTags);
		return new WikiTableParser.VersionInfo("", "", fallback);
	}

	private static void mergeVersions(
			Map<String, WikiTableParser.VersionInfo> target,
			Map<String, WikiTableParser.VersionInfo> source
	) {
		for (Map.Entry<String, WikiTableParser.VersionInfo> entry : source.entrySet()) {
			target.merge(entry.getKey(), entry.getValue(), WikiTableParser.VersionInfo::merge);
		}
	}

	private static WikiTableParser.WikiEdition pageEdition(String pageTitle) {
		if (pageTitle.startsWith("Bedrock_Edition")) {
			return WikiTableParser.WikiEdition.BEDROCK;
		}
		return WikiTableParser.WikiEdition.JAVA;
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
}
