package dev.historicaltextures.indexer;

import java.nio.file.Path;

public final class WikiIndexerMain {
	public static void main(String[] args) throws Exception {
		Path output = Path.of("build/catalog");
		boolean quick = false;
		Path enrichCatalog = null;
		for (int index = 0; index < args.length; index++) {
			String arg = args[index];
			if ("--quick".equals(arg)) {
				quick = true;
			} else if ("--enrich-versions".equals(arg)) {
				if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
					enrichCatalog = Path.of(args[++index]);
				} else {
					enrichCatalog = Path.of("../src/main/resources/assets/historical_textures/catalog/catalog.json");
				}
			} else if (!arg.startsWith("--")) {
				output = Path.of(arg);
			}
		}
		if (enrichCatalog != null) {
			new CatalogVersionEnricher().enrich(enrichCatalog);
			return;
		}
		new WikiIndexer(output, quick).run();
	}
}
