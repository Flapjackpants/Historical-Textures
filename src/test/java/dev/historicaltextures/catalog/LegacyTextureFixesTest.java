package dev.historicaltextures.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTextureFixesTest {
	private static int[] image(int width, int height, int fillFromRow, int fillToRow, int value) {
		int[] pixels = new int[width * height];
		for (int y = fillFromRow; y <= fillToRow; y++) {
			for (int x = 0; x < width; x++) {
				pixels[y * width + x] = value;
			}
		}
		return pixels;
	}

	@Test
	void shiftsTopHalfDownWhenBottomIsTransparent() {
		int width = 4;
		int height = 4;
		int[] argb = image(width, height, 0, 1, 0xFF112233);
		// distinct values per top row
		for (int x = 0; x < width; x++) {
			argb[x] = 0xFF000001;
			argb[width + x] = 0xFF000002;
		}

		assertTrue(LegacyTextureFixes.shiftTopHalfDown(argb, width, height));

		int[] expected = new int[width * height];
		expected[2 * width] = 0xFF000001;
		expected[2 * width + 1] = 0xFF000001;
		expected[2 * width + 2] = 0xFF000001;
		expected[2 * width + 3] = 0xFF000001;
		expected[3 * width] = 0xFF000002;
		expected[3 * width + 1] = 0xFF000002;
		expected[3 * width + 2] = 0xFF000002;
		expected[3 * width + 3] = 0xFF000002;
		assertArrayEquals(expected, argb);
	}

	@Test
	void leavesArrayUntouchedWhenBottomHalfHasPixels() {
		int width = 4;
		int height = 4;
		int[] argb = image(width, height, 0, 1, 0xFF112233);
		argb[3 * width + 1] = 0x80FF0000;
		int[] copy = argb.clone();

		assertFalse(LegacyTextureFixes.shiftTopHalfDown(argb, width, height));
		assertArrayEquals(copy, argb);
	}

	@Test
	void returnsFalseWhenFullyTransparent() {
		int[] argb = new int[4 * 4];
		assertFalse(LegacyTextureFixes.shiftTopHalfDown(argb, 4, 4));
	}

	@Test
	void returnsFalseForOddHeight() {
		int[] argb = image(4, 5, 0, 1, 0xFF112233);
		assertFalse(LegacyTextureFixes.shiftTopHalfDown(argb, 4, 5));
	}

	@Test
	void throwsOnLengthMismatch() {
		assertThrows(
				IllegalArgumentException.class,
				() -> LegacyTextureFixes.shiftTopHalfDown(new int[10], 4, 4)
		);
	}

	@Test
	void cakeSideTargetDetection() {
		assertTrue(LegacyTextureFixes.isCakeSideTarget("textures/block/cake_side.png"));
		assertTrue(LegacyTextureFixes.isCakeSideTarget("textures/block/cake_inner.png"));
		assertFalse(LegacyTextureFixes.isCakeSideTarget("textures/block/cake_top.png"));
		assertFalse(LegacyTextureFixes.isCakeSideTarget("textures/block/cake_bottom.png"));
		assertFalse(LegacyTextureFixes.isCakeSideTarget("minecraft:textures/block/cake_side.png"));
	}
}
