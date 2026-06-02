package dev.historicaltextures.indexer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
	private static final Pattern EDITION_PATTERN = Pattern.compile("_(JE|BE)(\\d+)");
	private static final List<String> SEED_PAGES = List.of(
			"List_of_historical_block_textures",
			"Java_Edition_history_of_textures/Blocks",
			"Java_Edition_history_of_textures/Items",
			"Java_Edition_history_of_textures/Entities",
			"Bedrock_Edition_history_of_textures/Blocks",
			"Bedrock_Edition_history_of_textures/Items",
			"Bedrock_Edition_history_of_textures/Entities"
	);

	private final Path outputRoot;
	private final boolean quickMode;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	private final Map<String, String> shaToAssetPath = new LinkedHashMap<>();
	private final Set<String> downloadedFiles = new LinkedHashSet<>();
	private JsonObject overrides;

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
				crawlPage(page, textures);
				Thread.sleep(250);
			}
			crawlHistoricalSounds(sounds);
		}

		writeCatalog(textures, sounds);
		System.out.println("Catalog written to " + outputRoot.resolve("catalog.json"));
		System.out.println("Texture variants: " + textures.size() + ", sound variants: " + sounds.size());
	}

	private void indexOverrideFiles(List<VariantRecord> textures, List<SoundRecord> sounds) throws Exception {
		JsonObject fileTargets = overrides.getAsJsonObject("fileTargets");
		if (fileTargets != null) {
			for (var entry : fileTargets.entrySet()) {
				processTextureFile(entry.getKey(), textures, "overrides.json");
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

	private void crawlPage(String pageTitle, List<VariantRecord> textures) throws Exception {
		String url = API + "?action=parse&page=" + encode(pageTitle) + "&prop=images|wikitext&format=json";
		JsonObject response = getJson(url);
		JsonObject parse = response.getAsJsonObject("parse");
		if (parse == null) {
			return;
		}
		JsonArray images = parse.getAsJsonArray("images");
		if (images != null) {
			for (var element : images) {
				String title = element.getAsString();
				if (!title.startsWith("File:")) {
					continue;
				}
				String fileName = title.substring("File:".length());
				if (!isTextureFile(fileName)) {
					continue;
				}
				processTextureFile(fileName, textures, pageTitle);
			}
		}
		String wikitext = parse.has("wikitext") ? parse.getAsJsonObject("wikitext").get("*").getAsString() : "";
		Matcher matcher = FILE_PATTERN.matcher(wikitext);
		while (matcher.find()) {
			String fileName = matcher.group(1).trim();
			if (isTextureFile(fileName)) {
				processTextureFile(fileName, textures, pageTitle);
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

	private void processTextureFile(String fileName, List<VariantRecord> textures, String sourcePage) throws Exception {
		if (!downloadedFiles.add(fileName)) {
			return;
		}
		String assetPath = downloadFile(fileName);
		if (assetPath == null) {
			return;
		}
		List<String> targets = resolveTextureTargets(fileName);
		boolean mapped = !targets.isEmpty();
		if (!mapped) {
			targets = guessTargetsFromFileName(fileName);
		}
		VariantRecord record = new VariantRecord(
				toId(fileName),
				fileName,
				parseEditions(fileName),
				sourcePage,
				assetPath,
				targets,
				humanLabel(fileName),
				mapped
		);
		textures.add(record);
	}

	private void processSoundFile(String fileName, List<SoundRecord> sounds) throws Exception {
		if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
			return;
		}
		if (!downloadedFiles.add(fileName)) {
			return;
		}
		String assetPath = downloadFile(fileName);
		if (assetPath == null) {
			return;
		}
		JsonObject soundTargets = overrides.getAsJsonObject("soundTargets");
		if (soundTargets == null || !soundTargets.has(fileName)) {
			return;
		}
		JsonObject mapping = soundTargets.getAsJsonObject(fileName);
		sounds.add(new SoundRecord(
				toId(fileName),
				fileName,
				mapping.get("soundEvent").getAsString(),
				assetPath,
				mapping.has("vanillaSoundPath") ? mapping.get("vanillaSoundPath").getAsString() : "",
				fileName
		));
	}

	private List<String> resolveTextureTargets(String fileName) {
		JsonObject fileTargets = overrides.getAsJsonObject("fileTargets");
		if (fileTargets != null && fileTargets.has(fileName)) {
			List<String> list = new ArrayList<>();
			for (var element : fileTargets.getAsJsonArray(fileName)) {
				list.add(element.getAsString());
			}
			return list;
		}
		return List.of();
	}

	private List<String> guessTargetsFromFileName(String fileName) {
		String base = fileName;
		int underscore = base.indexOf("_(");
		if (underscore > 0) {
			base = base.substring(0, underscore);
		}
		base = base.replace(' ', '_').toLowerCase(Locale.ROOT);
		List<String> guesses = new ArrayList<>();
		if (fileName.contains("(top_texture)")) {
			guesses.add("minecraft:textures/block/" + base + "_top.png");
		} else if (fileName.contains("(side_texture)")) {
			guesses.add("minecraft:textures/block/" + base + "_side.png");
		} else if (fileName.contains("(texture)")) {
			guesses.add("minecraft:textures/block/" + base + ".png");
			guesses.add("minecraft:textures/item/" + base + ".png");
			guesses.add("minecraft:textures/entity/" + base + "/" + base + ".png");
		}
		return guesses;
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
		root.addProperty("schemaVersion", 1);
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

	private static List<String> parseEditions(String fileName) {
		Set<String> editions = new LinkedHashSet<>();
		Matcher matcher = EDITION_PATTERN.matcher(fileName);
		while (matcher.find()) {
			editions.add(matcher.group(1));
		}
		if (editions.isEmpty()) {
			editions.add("JE");
		}
		return List.copyOf(editions);
	}

	private static boolean isTextureFile(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") && lower.contains("(texture)");
	}

	private static String humanLabel(String fileName) {
		return fileName.replace('_', ' ');
	}

	private static String toId(String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase(Locale.ROOT);
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
			String label
	) {
	}
}
