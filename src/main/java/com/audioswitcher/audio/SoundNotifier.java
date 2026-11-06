package com.audioswitcher.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates and plays a clean, gentle acoustic chime sound with adjustable volume and zero lag.
 */
public class SoundNotifier {

    private static final ExecutorService soundExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundChimeThread");
        t.setDaemon(true);
        return t;
    });

    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);
    private static volatile float currentVolume = 0.35f; // default gentle volume (35%)
    private static volatile byte[] cachedChimeBuffer = generateChimeBuffer(0.35f);

    public static synchronized void setVolume(float volume) {
        float clamped = Math.max(0.0f, Math.min(1.0f, volume));
        if (Math.abs(currentVolume - clamped) > 0.001f || cachedChimeBuffer == null) {
            currentVolume = clamped;
            cachedChimeBuffer = generateChimeBuffer(clamped);
        }
    }

    public static float getVolume() {
        return currentVolume;
    }

    /**
     * Synthesizes audio buffer for a bell chime scaled by the specified volume (0.0 to 1.0).
     */
    public static byte[] generateChimeBuffer(float volume) {
        if (volume <= 0.001f) {
            return new byte[0];
        }
        int sampleRate = 44100;
        double durationSeconds = 0.22;
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buffer = new byte[totalSamples * 4]; // 16-bit stereo = 4 bytes per sample

        double freq1 = 587.33; // D5
        double freq2 = 880.00; // A5
        double freq3 = 1174.66; // D6 shimmer

        // Master gain scaled by user volume setting (gentle curve)
        double masterGain = 0.35 * Math.pow(volume, 1.4);

        for (int i = 0; i < totalSamples; i++) {
            double t = (double) i / sampleRate;

            // Fast 6ms attack to eliminate clicks, smooth exponential decay
            double attack = Math.min(1.0, t / 0.006);
            double decay = Math.exp(-t * 15.0);
            double envelope = attack * decay;

            // Combined harmonic wave
            double wave1 = Math.sin(2.0 * Math.PI * freq1 * t);
            double wave2 = Math.sin(2.0 * Math.PI * freq2 * t) * 0.55;
            double wave3 = Math.sin(2.0 * Math.PI * freq3 * t) * 0.2;

            double sampleVal = (wave1 + wave2 + wave3) * envelope * masterGain;
            short sample = (short) (Math.max(-1.0, Math.min(1.0, sampleVal)) * 32767);

            int byteIndex = i * 4;
            // Left channel (Little-Endian)
            buffer[byteIndex] = (byte) (sample & 0xFF);
            buffer[byteIndex + 1] = (byte) ((sample >> 8) & 0xFF);
            // Right channel (Little-Endian)
            buffer[byteIndex + 2] = (byte) (sample & 0xFF);
            buffer[byteIndex + 3] = (byte) ((sample >> 8) & 0xFF);
        }

        return buffer;
    }

    /**
     * Plays the switch notification sound asynchronously on the newly active default device with zero latency.
     */
    public static void playSwitchChime() {
        playSwitchChime(currentVolume);
    }

    public static void playSwitchChime(float volume) {
        if (volume <= 0.01f) {
            return;
        }

        final byte[] chimeBytes;
        if (Math.abs(volume - currentVolume) < 0.01f && cachedChimeBuffer != null) {
            chimeBytes = cachedChimeBuffer;
        } else {
            chimeBytes = generateChimeBuffer(volume);
        }

        if (chimeBytes.length == 0) {
            return;
        }

        soundExecutor.submit(() -> {
            try {
                SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
                line.open(FORMAT, Math.max(2048, chimeBytes.length));
                line.start();
                line.write(chimeBytes, 0, chimeBytes.length);
                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                System.err.println("Failed to play audio chime: " + e.getMessage());
            }
        });
    }
}
