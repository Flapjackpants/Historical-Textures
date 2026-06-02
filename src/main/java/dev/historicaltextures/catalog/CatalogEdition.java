package dev.historicaltextures.catalog;

public enum CatalogEdition {
	JE,
	BE;

	public static CatalogEdition fromTag(String tag) {
		return switch (tag.toUpperCase()) {
			case "JE" -> JE;
			case "BE" -> BE;
			default -> throw new IllegalArgumentException("Unknown edition tag: " + tag);
		};
	}
}
