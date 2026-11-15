package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacySkinConverterTest {
	private static final int LEGACY_HEIGHT = 32;
	private static final int MODERN_HEIGHT = 64;

	private static final int[][] COPIES = {
			{4, 16, 16, 32, 4, 4},
			{8, 16, 16, 32, 4, 4},
			{0, 20, 24, 32, 4, 12},
			{4, 20, 16, 32, 4, 12},
			{8, 20, 8, 32, 4, 12},
			{12, 20, 16, 32, 4, 12},
			{44, 16, -8, 32, 4, 4},
			{48, 16, -8, 32, 4, 4},
			{40, 20, 0, 32, 4, 12},
			{44, 20, -8, 32, 4, 12},
			{48, 20, -16, 32, 4, 12},
			{52, 20, -8, 32, 4, 12}
	};

	@Test
	void keepsLegacyRowsAndAddsTransparentBottom() {
		BufferedImage input = legacySkin();
		BufferedImage output = LegacySkinConverter.convert(input);

		assertEquals(64, output.getWidth());
		assertEquals(MODERN_HEIGHT, output.getHeight());
		for (int y = 0; y < LEGACY_HEIGHT; y++) {
			for (int x = 0; x < 64; x++) {
				assertEquals(pixel(input, x, y), pixel(output, x, y), "pixel (" + x + ", " + y + ")");
			}
		}
	}

	@Test
	void mirrorsLegTopFromTheRightLeg() {
		BufferedImage input = legacySkin();
		BufferedImage output = LegacySkinConverter.convert(input);

		// Copy (4, 16, 16, 32, 4, 4): out(4 + 16 + (4 - 1 - i), 16 + 32 + j) = in(4 + i, 16 + j).
		assertEquals(pixel(input, 4, 16), pixel(output, 23, 48));
		assertEquals(pixel(input, 7, 16), pixel(output, 20, 48));
	}

	@Test
	void mirrorsLegAndArmSides() {
		BufferedImage input = legacySkin();
		BufferedImage output = LegacySkinConverter.convert(input);

		// Copy (4, 20, 16, 32, 4, 12): out(20 + 3, 52) = in(4, 20).
		assertEquals(pixel(input, 4, 20), pixel(output, 20 + 3, 52));
		// Copy (48, 20, -16, 32, 4, 12): out(32 + 3, 52) = in(48, 20).
		assertEquals(pixel(input, 48, 20), pixel(output, 32 + 3, 52));
		// Copy (40, 20, 0, 32, 4, 12): out(40 + 0 + 3, 52) = in(40, 20).
		assertEquals(pixel(input, 40, 20), pixel(output, 40 + 0 + 3, 52));
	}

	@Test
	void appliesEveryVanillaLegacyCopy() {
		BufferedImage input = legacySkin();
		BufferedImage output = LegacySkinConverter.convert(input);

		for (int[] copy : COPIES) {
			int x = copy[0];
			int y = copy[1];
			int dx = copy[2];
			int dy = copy[3];
			int width = copy[4];
			int height = copy[5];
			for (int i = 0; i < width; i++) {
				for (int j = 0; j < height; j++) {
					int sourceX = x + i;
					int sourceY = y + j;
					int targetX = x + dx + (width - 1 - i);
					int targetY = y + dy + j;
					assertEquals(pixel(input, sourceX, sourceY), pixel(output, targetX, targetY),
							"copy " + x + "," + y + " -> " + targetX + "," + targetY);
				}
			}
		}
	}

	@Test
	void leavesUntouchedBottomPixelsTransparent() {
		BufferedImage output = LegacySkinConverter.convert(legacySkin());

		assertEquals(0, pixel(output, 0, 48) >>> 24, "out(0, 48) alpha");
		assertEquals(0, pixel(output, 56, 60) >>> 24, "out(56, 60) alpha");
	}

	@Test
	void copiesModernTextureUnchanged() {
		BufferedImage input = new BufferedImage(64, MODERN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < MODERN_HEIGHT; y++) {
			for (int x = 0; x < 64; x++) {
				input.setRGB(x, y, 0xFF000000 | (x << 8) | y);
			}
		}

		BufferedImage output = LegacySkinConverter.convert(input);

		assertNotSame(input, output);
		assertEquals(BufferedImage.TYPE_INT_ARGB, output.getType());
		assertEquals(64, output.getWidth());
		assertEquals(MODERN_HEIGHT, output.getHeight());
		for (int y = 0; y < MODERN_HEIGHT; y++) {
			for (int x = 0; x < 64; x++) {
				assertEquals(pixel(input, x, y), pixel(output, x, y), "pixel (" + x + ", " + y + ")");
			}
		}
	}

	@Test
	void rejectsUnsupportedSizes() {
		assertThrows(IllegalArgumentException.class,
				() -> LegacySkinConverter.convert(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)));
		assertThrows(IllegalArgumentException.class,
				() -> LegacySkinConverter.convert(new BufferedImage(64, 16, BufferedImage.TYPE_INT_ARGB)));
	}

	private static BufferedImage legacySkin() {
		BufferedImage image = new BufferedImage(64, LEGACY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < LEGACY_HEIGHT; y++) {
			for (int x = 0; x < 64; x++) {
				image.setRGB(x, y, 0xFF000000 | (x << 8) | y);
			}
		}
		return image;
	}

	private static int pixel(BufferedImage image, int x, int y) {
		return image.getRGB(x, y);
	}
}
