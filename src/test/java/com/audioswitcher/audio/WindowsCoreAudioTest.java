package com.audioswitcher.audio;

import com.audioswitcher.model.AudioDevice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WindowsCoreAudioTest {

    @Test
    public void testEnumeratePlaybackDevices() {
        List<AudioDevice> devices = WindowsCoreAudio.getPlaybackDevices();
        assertNotNull(devices, "Device list should never be null, even when no audio hardware is present");

        System.out.println("Discovered " + devices.size() + " audio playback devices:");
        for (AudioDevice device : devices) {
            System.out.println(" - " + device.getName() + " (ID: " + device.getId() + ")" + (device.isDefault() ? " [DEFAULT]" : ""));
            assertNotNull(device.getId(), "Device ID should not be null");
            assertNotNull(device.getName(), "Device name should not be null");
            assertFalse(device.getName().isBlank(), "Device name should not be blank");
        }

        if (devices.isEmpty()) {
            System.out.println("No active playback endpoints found (expected in headless CI virtual environments).");
        }
    }

    @Test
    public void testSetDefaultPlaybackDeviceTiming() {
        List<AudioDevice> devices = WindowsCoreAudio.getPlaybackDevices();
        if (devices.isEmpty()) return;

        AudioDevice defaultDev = null;
        for (AudioDevice d : devices) {
            if (d.isDefault()) {
                defaultDev = d;
                break;
            }
        }
        if (defaultDev == null) defaultDev = devices.get(0);

        long start = System.currentTimeMillis();
        boolean success = WindowsCoreAudio.setDefaultPlaybackDevice(defaultDev.getId());
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("setDefaultPlaybackDevice took: " + elapsed + " ms. Success=" + success);
        assertTrue(success, "Switching to default device should succeed");
    }
}
