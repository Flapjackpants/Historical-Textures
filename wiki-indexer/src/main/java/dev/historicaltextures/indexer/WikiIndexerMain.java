package dev.historicaltextures.indexer;

import java.nio.file.Path;

public final class WikiIndexerMain {
	public static void main(String[] args) throws Exception {
		Path output = Path.of("build/catalog");
		boolean quick = false;
		for (String arg : args) {
			if ("--quick".equals(arg)) {
				quick = true;
			} else if (!arg.startsWith("--")) {
				output = Path.of(arg);
			}
		}
		new WikiIndexer(output, quick).run();
	}
}
