package com.audioswitcher.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Generates and plays a clean, instant acoustic chime tone with adjustable volume,
 * zero double-triggering, and immediate cancellation of prior sounds.
 */
public class SoundNotifier {

    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);
    private static volatile float currentVolume = 0.35f; // default gentle volume (35%)
    private static volatile byte[] cachedChimeBuffer = generateChimeBuffer(0.35f);

    private static final Object soundLock = new Object();
    private static volatile SourceDataLine currentLine = null;
    private static volatile Thread currentPlaybackThread = null;

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
     * Synthesizes audio buffer for a crisp bell chime scaled by the specified volume (0.0 to 1.0).
     */
    public static byte[] generateChimeBuffer(float volume) {
        if (volume <= 0.001f) {
            return new byte[0];
        }
        int sampleRate = 44100;
        double durationSeconds = 0.16; // 160ms snappy, non-lingering chime
        int totalSamples = (int) (sampleRate * durationSeconds);
        byte[] buffer = new byte[totalSamples * 4]; // 16-bit stereo = 4 bytes per sample

        double freq1 = 659.25; // E5
        double freq2 = 987.77; // B5
        double freq3 = 1318.51; // E6 shimmer

        // Master gain scaled by user volume setting
        double masterGain = 0.35 * Math.pow(volume, 1.3);

        for (int i = 0; i < totalSamples; i++) {
            double t = (double) i / sampleRate;

            // 5ms snappy attack to prevent clicks, smooth bell decay
            double attack = Math.min(1.0, t / 0.005);
            double decay = Math.exp(-t * 18.0);
            double envelope = attack * decay;

            // Combined harmonic wave
            double wave1 = Math.sin(2.0 * Math.PI * freq1 * t);
            double wave2 = Math.sin(2.0 * Math.PI * freq2 * t) * 0.5;
            double wave3 = Math.sin(2.0 * Math.PI * freq3 * t) * 0.18;

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
     * Plays the switch notification sound asynchronously on the newly active default device.
     * Any previous playing sound is immediately stopped to eliminate delayed double sounds.
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

        synchronized (soundLock) {
            // Cancel and close any active line immediately so sounds NEVER overlap or play double
            if (currentLine != null) {
                try {
                    currentLine.stop();
                    currentLine.flush();
                    currentLine.close();
                } catch (Exception ignored) {
                }
                currentLine = null;
            }
            if (currentPlaybackThread != null) {
                currentPlaybackThread.interrupt();
                currentPlaybackThread = null;
            }

            Thread playThread = new Thread(() -> {
                SourceDataLine line = null;
                try {
                    // Short 20ms pause allows Windows to route default DirectSound endpoint cleanly
                    Thread.sleep(20);

                    line = AudioSystem.getSourceDataLine(FORMAT);
                    currentLine = line;
                    line.open(FORMAT, 4096);
                    line.start();
                    line.write(chimeBytes, 0, chimeBytes.length);
                    line.drain();
                } catch (InterruptedException e) {
                    // Canceled by a newer switch event - clean exit
                } catch (Exception e) {
                    System.err.println("Failed to play audio chime: " + e.getMessage());
                } finally {
                    if (line != null) {
                        try {
                            line.stop();
                            line.close();
                        } catch (Exception ignored) {
                        }
                    }
                    synchronized (soundLock) {
                        if (currentLine == line) {
                            currentLine = null;
                        }
                    }
                }
            }, "ChimePlayThread");

            playThread.setDaemon(true);
            currentPlaybackThread = playThread;
            playThread.start();
        }
    }
}
