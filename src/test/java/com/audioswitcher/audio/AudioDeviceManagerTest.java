package com.audioswitcher.audio;

import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDeviceManagerTest {

    @Test
    public void testAudioDeviceManagerInitializationAndDevices() {
        ConfigManager configManager = new ConfigManager();
        AudioDeviceManager manager = new AudioDeviceManager(configManager);

        List<AudioDevice> devices = manager.getDevices();
        assertNotNull(devices);

        AudioDevice def = manager.getDefaultDevice();
        if (!devices.isEmpty()) {
            assertNotNull(def, "If playback devices exist, default device should not be null");
            System.out.println("Default audio device: " + def.getName() + " (ID: " + def.getId() + ")");
        }
    }

    @Test
    public void testCycleEnabledDevicesFallback() {
        ConfigManager configManager = new ConfigManager();
        AudioDeviceManager manager = new AudioDeviceManager(configManager);

        List<AudioDevice> cycleList = manager.getCycleEnabledDevices();
        assertNotNull(cycleList);
    }
}
