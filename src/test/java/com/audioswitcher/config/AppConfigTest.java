package com.audioswitcher.config;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

public class AppConfigTest {

    @Test
    public void testDefaultConfigValues() {
        AppConfig config = new AppConfig();

        assertEquals(3, config.getHotkeyModifiers()); // Ctrl + Alt
        assertEquals(KeyEvent.VK_S, config.getHotkeyKeyCode());
        assertEquals("Ctrl + Alt + S", config.getHotkeyDisplayString());
        assertTrue(config.isShowOsd());
        assertTrue(config.isPlayChimeOnSwitch());
        assertEquals(35, config.getChimeVolume());
        assertTrue(config.isStartMinimized(), "startMinimized should default to true");
        assertFalse(config.isStartWithWindows());
        assertFalse(config.isInitialized());
        assertNotNull(config.getDeviceHotkeys());
        assertNotNull(config.getEnabledDeviceIds());
        assertNotNull(config.getEnabledDeviceNames());
    }

    @Test
    public void testDeviceHotkeyWithDeviceName() {
        AppConfig.DeviceHotkey hk = new AppConfig.DeviceHotkey(3, KeyEvent.VK_1, "Ctrl + Alt + 1", "Arctis 7 Game");

        assertEquals(3, hk.getModifiers());
        assertEquals(KeyEvent.VK_1, hk.getKeyCode());
        assertEquals("Ctrl + Alt + 1", hk.getDisplayString());
        assertEquals("Arctis 7 Game", hk.getDeviceName());
        assertTrue(hk.matches(3, KeyEvent.VK_1));
        assertFalse(hk.matches(1, KeyEvent.VK_1));
    }

    @Test
    public void testStartMinimizedProperty() {
        AppConfig config = new AppConfig();
        config.setStartMinimized(false);
        assertFalse(config.isStartMinimized());

        config.setStartMinimized(true);
        assertTrue(config.isStartMinimized());
    }

    @Test
    public void testHiddenDeviceProperties() {
        AppConfig config = new AppConfig();
        assertNotNull(config.getHiddenDeviceIds());
        assertNotNull(config.getHiddenDeviceNames());

        config.getHiddenDeviceIds().add("device-123");
        config.getHiddenDeviceNames().add("nvidia high definition audio");

        assertTrue(config.isDeviceHidden("device-123", "Some Name"));
        assertTrue(config.isDeviceHidden("other-id", "NVIDIA High Definition Audio"));
        assertFalse(config.isDeviceHidden("other-id", "Realtek Audio"));
    }
}
