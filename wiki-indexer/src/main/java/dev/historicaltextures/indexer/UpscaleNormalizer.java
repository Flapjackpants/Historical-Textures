package dev.historicaltextures.indexer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class UpscaleNormalizer {
	private UpscaleNormalizer() {
	}

	public static BufferedImage downscaleIfBlockUpscaled(BufferedImage image, int factor) {
		if (image == null) {
			return null;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		if (factor <= 1 || width % factor != 0 || height % factor != 0) {
			return image;
		}
		int newWidth = width / factor;
		int newHeight = height / factor;
		if (newWidth < 16 || newHeight < 16) {
			return image;
		}
		for (int blockY = 0; blockY < newHeight; blockY++) {
			for (int blockX = 0; blockX < newWidth; blockX++) {
				int argb = image.getRGB(blockX * factor, blockY * factor);
				for (int y = blockY * factor; y < (blockY + 1) * factor; y++) {
					for (int x = blockX * factor; x < (blockX + 1) * factor; x++) {
						if (image.getRGB(x, y) != argb) {
							return image;
						}
					}
				}
			}
		}
		BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < newHeight; y++) {
			for (int x = 0; x < newWidth; x++) {
				result.setRGB(x, y, image.getRGB(x * factor, y * factor));
			}
		}
		return result;
	}

	public static boolean normalizeFile(Path png) {
		try {
			BufferedImage image = ImageIO.read(png.toFile());
			if (image == null) {
				return false;
			}
			BufferedImage normalized = downscaleIfBlockUpscaled(image, 10);
			if (normalized == image) {
				return false;
			}
			return ImageIO.write(normalized, "png", png.toFile());
		} catch (IOException | RuntimeException exception) {
			System.out.println("Upscale normalization skipped for " + png + ": " + exception.getMessage());
			return false;
		}
	}
}
