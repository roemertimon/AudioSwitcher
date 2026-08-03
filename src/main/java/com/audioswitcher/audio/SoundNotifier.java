package com.audioswitcher.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates and plays a clean, crystal-clear chime sound directly on the active playback device.
 */
public class SoundNotifier {

    private static final ExecutorService soundExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundChimeThread");
        t.setDaemon(true);
        return t;
    });

    private static byte[] precomputedChimeBytes = null;
    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);

    static {
        generateChimeData();
    }

    /**
     * Synthesizes a clean, high-fidelity acoustic bell chime:
     * Dual-harmonic frequency (D5 ~587Hz & A5 ~880Hz) with smooth exponential decay.
     */
    private static void generateChimeData() {
        int sampleRate = 44100;
        double durationSeconds = 0.28;
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buffer = new byte[totalSamples * 4]; // 16-bit stereo = 4 bytes per sample

        double freq1 = 587.33; // D5
        double freq2 = 880.00; // A5
        double freq3 = 1174.66; // D6 (harmonic shimmer)

        for (int i = 0; i < totalSamples; i++) {
            double t = (double) i / sampleRate;

            // Attack & exponential decay envelope
            double attack = Math.min(1.0, t / 0.008); // 8ms attack to eliminate clicks
            double decay = Math.exp(-t * 12.0); // Smooth bell-like decay
            double envelope = attack * decay;

            // Combined harmonic wave
            double wave1 = Math.sin(2.0 * Math.PI * freq1 * t);
            double wave2 = Math.sin(2.0 * Math.PI * freq2 * t) * 0.6;
            double wave3 = Math.sin(2.0 * Math.PI * freq3 * t) * 0.25;

            double sampleVal = (wave1 + wave2 + wave3) * envelope * 0.6;
            short sample = (short) (Math.max(-1.0, Math.min(1.0, sampleVal)) * 32767);

            int byteIndex = i * 4;
            // Left channel (Little-Endian)
            buffer[byteIndex] = (byte) (sample & 0xFF);
            buffer[byteIndex + 1] = (byte) ((sample >> 8) & 0xFF);
            // Right channel (Little-Endian)
            buffer[byteIndex + 2] = (byte) (sample & 0xFF);
            buffer[byteIndex + 3] = (byte) ((sample >> 8) & 0xFF);
        }

        precomputedChimeBytes = buffer;
    }

    /**
     * Plays the switch notification sound asynchronously on the newly active default device.
     */
    public static void playSwitchChime() {
        soundExecutor.submit(() -> {
            try {
                // Short 40ms pause to ensure Windows CoreAudio has completed endpoint route switch
                Thread.sleep(40);

                SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
                line.open(FORMAT, 4096);
                line.start();
                line.write(precomputedChimeBytes, 0, precomputedChimeBytes.length);
                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                System.err.println("Failed to play audio chime: " + e.getMessage());
            }
        });
    }
}
