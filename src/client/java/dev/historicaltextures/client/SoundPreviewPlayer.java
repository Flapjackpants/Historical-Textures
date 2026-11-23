package dev.historicaltextures.client;

import dev.historicaltextures.HistoricalTextures;
import dev.historicaltextures.catalog.HistoricalCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class SoundPreviewPlayer {
	private static Clip clip;
	private static AudioFormat format;
	private static String loadedVariantId;
	private static String failedKey;

	private SoundPreviewPlayer() {
	}

	public static synchronized boolean load(String variantId, String assetPath) {
		if (variantId == null) {
			stop();
			return false;
		}
		if (variantId.equals(loadedVariantId) && clip != null) {
			return true;
		}
		String key = variantId + "|" + assetPath;
		if (key.equals(failedKey)) {
			return false;
		}
		stop();
		try {
			byte[] bytes;
			AudioFormat decodedFormat;
			try (JOrbisAudioStream stream = new JOrbisAudioStream(HistoricalCatalog.get().openAsset(assetPath))) {
				decodedFormat = stream.getFormat();
				ByteBuffer buffer = stream.readAll();
				bytes = new byte[buffer.remaining()];
				buffer.get(bytes);
			}
			Clip newClip = AudioSystem.getClip();
			newClip.open(decodedFormat, bytes, 0, bytes.length);
			clip = newClip;
			format = decodedFormat;
			loadedVariantId = variantId;
			failedKey = null;
			applyVolume();
			return true;
		} catch (IOException | LineUnavailableException | IllegalArgumentException | NullPointerException e) {
			failedKey = key;
			HistoricalTextures.LOGGER.warn("Failed to preview sound {}: {}", variantId, e.toString());
			return false;
		}
	}

	public static synchronized void toggle() {
		if (clip == null) {
			return;
		}
		if (clip.isRunning()) {
			clip.stop();
			return;
		}
		applyVolume();
		if (clip.getFrameLength() > 0 && clip.getFramePosition() >= clip.getFrameLength()) {
			clip.setFramePosition(0);
		}
		clip.start();
	}

	public static synchronized void seek(float fraction) {
		if (clip == null) {
			return;
		}
		float clamped = Math.max(0.0f, Math.min(1.0f, fraction));
		clip.setFramePosition(Math.round(clamped * clip.getFrameLength()));
	}

	public static synchronized void stop() {
		if (clip != null) {
			clip.stop();
			clip.close();
			clip = null;
		}
		format = null;
		loadedVariantId = null;
	}

	public static synchronized boolean isLoaded(String variantId) {
		return clip != null && variantId != null && variantId.equals(loadedVariantId);
	}

	public static synchronized boolean isPlaying() {
		return clip != null && clip.isRunning();
	}

	public static synchronized float positionSeconds() {
		if (clip == null || format == null) {
			return 0.0f;
		}
		return clip.getFramePosition() / format.getFrameRate();
	}

	public static synchronized float durationSeconds() {
		if (clip == null || format == null) {
			return 0.0f;
		}
		return clip.getFrameLength() / format.getFrameRate();
	}

	private static void applyVolume() {
		if (clip == null) {
			return;
		}
		try {
			FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			Minecraft minecraft = Minecraft.getInstance();
			float volume = minecraft != null
					? minecraft.options.getFinalSoundSourceVolume(SoundSource.MASTER)
					: 1.0f;
			float gainDb = (float) (20.0 * Math.log10(Math.max(volume, 0.0001)));
			control.setValue(Math.min(Math.max(gainDb, control.getMinimum()), control.getMaximum()));
		} catch (IllegalArgumentException | ClassCastException ignored) {
		}
	}
}
