package dev.historicaltextures.indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpscaleNormalizerTest {
	@Test
	void downscalesBlockUpscaledImage() {
		Random random = new Random(42);
		BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				source.setRGB(x, y, random.nextInt());
			}
		}
		BufferedImage upscaled = upscale(source, 10);
		assertEquals(640, upscaled.getWidth());
		assertEquals(320, upscaled.getHeight());

		BufferedImage result = UpscaleNormalizer.downscaleIfBlockUpscaled(upscaled, 10);

		assertNotSame(upscaled, result);
		assertEquals(64, result.getWidth());
		assertEquals(32, result.getHeight());
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				assertEquals(source.getRGB(x, y), result.getRGB(x, y), "pixel " + x + "," + y);
			}
		}
	}

	@Test
	void returnsSameInstanceWhenNotUpscaled() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		assertSame(image, UpscaleNormalizer.downscaleIfBlockUpscaled(image, 10));
	}

	@Test
	void returnsSameInstanceWhenBlockNotUniform() {
		Random random = new Random(7);
		BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				source.setRGB(x, y, random.nextInt());
			}
		}
		BufferedImage upscaled = upscale(source, 10);
		upscaled.setRGB(15, 15, upscaled.getRGB(15, 15) ^ 0xFF);

		assertSame(upscaled, UpscaleNormalizer.downscaleIfBlockUpscaled(upscaled, 10));
	}

	@Test
	void returnsSameInstanceWhenResultTooSmall() {
		BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		assertSame(image, UpscaleNormalizer.downscaleIfBlockUpscaled(image, 10));
	}

	@Test
	void normalizeFileRewritesOnce(@TempDir Path tempDir) throws IOException {
		Path png = tempDir.resolve("rabbit.png");
		BufferedImage upscaled = upscale(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), 10);
		assertTrue(ImageIO.write(upscaled, "png", png.toFile()));

		assertTrue(UpscaleNormalizer.normalizeFile(png));

		BufferedImage written = ImageIO.read(png.toFile());
		assertEquals(64, written.getWidth());
		assertEquals(32, written.getHeight());
		assertFalse(UpscaleNormalizer.normalizeFile(png));
	}

	private static BufferedImage upscale(BufferedImage source, int factor) {
		BufferedImage result = new BufferedImage(
				source.getWidth() * factor,
				source.getHeight() * factor,
				BufferedImage.TYPE_INT_ARGB
		);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				result.setRGB(x, y, source.getRGB(x / factor, y / factor));
			}
		}
		return result;
	}
}
