package com.audioswitcher.audio;

import com.audioswitcher.model.AudioDevice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WindowsCoreAudioTest {

    @Test
    public void testEnumeratePlaybackDevices() {
        List<AudioDevice> devices = WindowsCoreAudio.getPlaybackDevices();
        assertNotNull(devices, "Device list should not be null");
        System.out.println("Discovered " + devices.size() + " audio playback devices:");
        for (AudioDevice device : devices) {
            System.out.println(" - " + device.getName() + " (ID: " + device.getId() + ")" + (device.isDefault() ? " [DEFAULT]" : ""));
        }
        assertFalse(devices.isEmpty(), "Should find at least one active playback device on Windows");
    }
}
