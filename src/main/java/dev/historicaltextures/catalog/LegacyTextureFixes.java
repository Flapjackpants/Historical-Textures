package dev.historicaltextures.catalog;

/**
 * Repairs legacy texture layouts that predate model changes, applied to files
 * written into the overlay pack. Pure Java: no Minecraft imports.
 */
public final class LegacyTextureFixes {
	private LegacyTextureFixes() {
	}

	public static boolean isCakeSideTarget(String relativePath) {
		return "textures/block/cake_side.png".equals(relativePath)
				|| "textures/block/cake_inner.png".equals(relativePath);
	}

	/**
	 * Some legacy cake side/inner art was authored in the top half of the
	 * texture while rows height/2..height-1 are fully transparent; the 26.3
	 * cake model samples the bottom half. Moves the art down when the bottom
	 * half is empty.
	 */
	public static boolean shiftTopHalfDown(int[] argb, int width, int height) {
		if (argb == null || argb.length != width * height) {
			throw new IllegalArgumentException(
					"argb length " + (argb == null ? "null" : argb.length) + " != " + width + "x" + height
			);
		}
		if (height < 2 || height % 2 != 0 || width <= 0) {
			return false;
		}
		int half = height / 2;
		boolean topHasPixel = false;
		for (int y = 0; y < half && !topHasPixel; y++) {
			for (int x = 0; x < width; x++) {
				if ((argb[y * width + x] >>> 24) != 0) {
					topHasPixel = true;
					break;
				}
			}
		}
		if (!topHasPixel) {
			return false;
		}
		for (int y = half; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if ((argb[y * width + x] >>> 24) != 0) {
					return false;
				}
			}
		}
		for (int y = half - 1; y >= 0; y--) {
			System.arraycopy(argb, y * width, argb, (y + half) * width, width);
		}
		for (int i = 0; i < half * width; i++) {
			argb[i] = 0;
		}
		return true;
	}
}
