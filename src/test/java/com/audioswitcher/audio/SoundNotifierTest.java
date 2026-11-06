package com.audioswitcher.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SoundNotifierTest {

    @Test
    public void testGenerateChimeBufferVolumeClamping() {
        byte[] zeroBuffer = SoundNotifier.generateChimeBuffer(0.0f);
        assertEquals(0, zeroBuffer.length, "Zero volume should produce an empty buffer");

        byte[] normalBuffer = SoundNotifier.generateChimeBuffer(0.35f);
        assertTrue(normalBuffer.length > 0, "Normal volume should produce a valid PCM buffer");
        // 44100 Hz * 0.22 sec * 4 bytes = 38808 bytes
        assertEquals(38808, normalBuffer.length, "Chime buffer size should match 220ms 16-bit stereo PCM");

        byte[] loudBuffer = SoundNotifier.generateChimeBuffer(1.0f);
        assertEquals(normalBuffer.length, loudBuffer.length, "Buffer lengths should be identical across volume levels");
    }

    @Test
    public void testSetVolumeAndPrebuffering() {
        SoundNotifier.setVolume(0.5f);
        assertEquals(0.5f, SoundNotifier.getVolume(), 0.001f);

        SoundNotifier.setVolume(-0.2f);
        assertEquals(0.0f, SoundNotifier.getVolume(), 0.001f);

        SoundNotifier.setVolume(1.5f);
        assertEquals(1.0f, SoundNotifier.getVolume(), 0.001f);

        // Reset to default
        SoundNotifier.setVolume(0.35f);
        assertEquals(0.35f, SoundNotifier.getVolume(), 0.001f);
    }

    @Test
    public void testChimeSynthesisPerformance() {
        long start = System.nanoTime();
        byte[] buffer = SoundNotifier.generateChimeBuffer(0.4f);
        long elapsedMicros = (System.nanoTime() - start) / 1000;

        assertNotNull(buffer);
        System.out.println("Chime buffer synthesis took: " + elapsedMicros + " microseconds");
        assertTrue(elapsedMicros < 50_000, "Synthesis should complete in less than 50ms");
    }
}
