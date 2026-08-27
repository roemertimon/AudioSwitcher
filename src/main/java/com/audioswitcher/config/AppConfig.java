package com.audioswitcher.config;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Application configuration model, serialized to JSON.
 */
public class AppConfig {

    public static class DeviceHotkey {
        private int modifiers;
        private int keyCode;
        private String displayString;

        public DeviceHotkey() {
        }

        public DeviceHotkey(int modifiers, int keyCode, String displayString) {
            this.modifiers = modifiers;
            this.keyCode = keyCode;
            this.displayString = displayString;
        }

        public int getModifiers() {
            return modifiers;
        }

        public void setModifiers(int modifiers) {
            this.modifiers = modifiers;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public void setKeyCode(int keyCode) {
            this.keyCode = keyCode;
        }

        public String getDisplayString() {
            return displayString;
        }

        public void setDisplayString(String displayString) {
            this.displayString = displayString;
        }
    }

    // Global cycle hotkey: default Ctrl + Alt + S (S for Sound / Switch)
    private int hotkeyModifiers = 3; // 1 (ALT) + 2 (CTRL) = 3
    private int hotkeyKeyCode = KeyEvent.VK_S;
    private String hotkeyDisplayString = "Ctrl + Alt + S";

    // Dedicated hotkeys per specific audio device: deviceId -> DeviceHotkey
    // E.g. Device 1 = Ctrl + Alt + 1, Device 2 = Ctrl + Alt + 2, etc.
    private Map<String, DeviceHotkey> deviceHotkeys = new HashMap<>();

    // Device IDs enabled for the cycle shortcut
    private Set<String> enabledDeviceIds = new HashSet<>();

    // OSD settings
    private boolean showOsd = true;
    private int osdDurationMs = 1800;
    private String osdPosition = "TOP"; // "TOP", "CENTER", "BOTTOM"

    // Audio chime notification
    private boolean playChimeOnSwitch = true;
    private int chimeVolume = 35; // Default 35% (gentle and pleasant)

    // Windows startup setting
    private boolean startWithWindows = false;

    // First run flag
    private boolean initialized = false;

    public int getHotkeyModifiers() {
        return hotkeyModifiers;
    }

    public void setHotkeyModifiers(int hotkeyModifiers) {
        this.hotkeyModifiers = hotkeyModifiers;
    }

    public int getHotkeyKeyCode() {
        return hotkeyKeyCode;
    }

    public void setHotkeyKeyCode(int hotkeyKeyCode) {
        this.hotkeyKeyCode = hotkeyKeyCode;
    }

    public String getHotkeyDisplayString() {
        return hotkeyDisplayString;
    }

    public void setHotkeyDisplayString(String hotkeyDisplayString) {
        this.hotkeyDisplayString = hotkeyDisplayString;
    }

    public Map<String, DeviceHotkey> getDeviceHotkeys() {
        if (deviceHotkeys == null) {
            deviceHotkeys = new HashMap<>();
        }
        return deviceHotkeys;
    }

    public void setDeviceHotkeys(Map<String, DeviceHotkey> deviceHotkeys) {
        this.deviceHotkeys = deviceHotkeys;
    }

    public Set<String> getEnabledDeviceIds() {
        if (enabledDeviceIds == null) {
            enabledDeviceIds = new HashSet<>();
        }
        return enabledDeviceIds;
    }

    public void setEnabledDeviceIds(Set<String> enabledDeviceIds) {
        this.enabledDeviceIds = enabledDeviceIds;
    }

    public boolean isShowOsd() {
        return showOsd;
    }

    public void setShowOsd(boolean showOsd) {
        this.showOsd = showOsd;
    }

    public int getOsdDurationMs() {
        return osdDurationMs;
    }

    public void setOsdDurationMs(int osdDurationMs) {
        this.osdDurationMs = osdDurationMs;
    }

    public String getOsdPosition() {
        return osdPosition == null ? "TOP" : osdPosition;
    }

    public void setOsdPosition(String osdPosition) {
        this.osdPosition = osdPosition;
    }

    public boolean isPlayChimeOnSwitch() {
        return playChimeOnSwitch;
    }

    public void setPlayChimeOnSwitch(boolean playChimeOnSwitch) {
        this.playChimeOnSwitch = playChimeOnSwitch;
    }

    public int getChimeVolume() {
        return chimeVolume <= 0 ? 35 : chimeVolume;
    }

    public void setChimeVolume(int chimeVolume) {
        this.chimeVolume = Math.max(0, Math.min(100, chimeVolume));
    }

    public boolean isStartWithWindows() {
        return startWithWindows;
    }

    public void setStartWithWindows(boolean startWithWindows) {
        this.startWithWindows = startWithWindows;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
