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

        // On physical machines devices will be present; on headless CI VMs in Azure without audio hardware it is empty.
        // What matters is that CoreAudio COM initialization and device enumeration succeed without native crashes.
        if (devices.isEmpty()) {
            System.out.println("No active playback endpoints found (expected in headless CI virtual environments).");
        }
    }
}
