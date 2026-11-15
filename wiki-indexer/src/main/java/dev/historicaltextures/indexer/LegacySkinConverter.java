package dev.historicaltextures.indexer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Converts pre-1.8 {@code 64x32} skin layouts to the modern {@code 64x64} layout.
 *
 * <p>The conversion mirrors the copies Minecraft applies to legacy skins: the left leg and arm
 * reuse the right leg and arm pixels, mirrored. Alpha is preserved as-is, so notch transparency
 * and the no-alpha passes are intentionally not applied.
 */
public final class LegacySkinConverter {
	private static final int MODERN_SIZE = 64;
	private static final int LEGACY_HEIGHT = 32;

	/** {@code x, y, dx, dy, width, height} mirrored copies, in the order Minecraft applies them. */
	private static final int[][] LEGACY_COPIES = {
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

	private LegacySkinConverter() {
	}

	public static BufferedImage convert(BufferedImage legacy) {
		if (legacy == null) {
			throw new IllegalArgumentException("legacy texture is null");
		}
		int width = legacy.getWidth();
		int height = legacy.getHeight();
		if (width != MODERN_SIZE || (height != LEGACY_HEIGHT && height != MODERN_SIZE)) {
			throw new IllegalArgumentException(
					"expected a 64x32 or 64x64 skin, got " + width + "x" + height);
		}
		BufferedImage modern = new BufferedImage(width, MODERN_SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				modern.setRGB(x, y, legacy.getRGB(x, y));
			}
		}
		if (height == MODERN_SIZE) {
			return modern;
		}
		for (int[] copy : LEGACY_COPIES) {
			copyRect(modern, copy[0], copy[1], copy[2], copy[3], copy[4], copy[5]);
		}
		return modern;
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage: LegacySkinConverter <input.png> <output.png>");
			System.exit(2);
		}
		BufferedImage input = ImageIO.read(Path.of(args[0]).toFile());
		if (input == null) {
			throw new IllegalArgumentException("not a readable image: " + args[0]);
		}
		Path output = Path.of(args[1]);
		Files.createDirectories(output.toAbsolutePath().getParent());
		ImageIO.write(convert(input), "png", output.toFile());
	}

	private static void copyRect(BufferedImage image, int x, int y, int dx, int dy, int width, int height) {
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				image.setRGB(x + dx + (width - 1 - i), y + dy + j, image.getRGB(x + i, y + j));
			}
		}
	}
}
