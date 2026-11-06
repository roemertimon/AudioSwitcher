package com.audioswitcher.audio;

import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AudioDeviceManager {

    private final ConfigManager configManager;
    private final List<AudioDevice> cachedDevices = new ArrayList<>();
    private final List<Consumer<AudioDevice>> deviceSwitchedListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> devicesChangedListeners = new CopyOnWriteArrayList<>();

    public AudioDeviceManager(ConfigManager configManager) {
        this.configManager = configManager;
        refreshDevices();
    }

    public synchronized void refreshDevices() {
        List<AudioDevice> freshList = WindowsCoreAudio.getPlaybackDevices();
        AppConfig config = configManager.getConfig();
        Set<String> enabledIds = config.getEnabledDeviceIds();
        Set<String> enabledNames = config.getEnabledDeviceNames();
        Map<String, AppConfig.DeviceHotkey> hotkeys = config.getDeviceHotkeys();

        boolean firstRun = !config.isInitialized();
        boolean configUpdated = false;

        // Clean up duplicate hotkeys: remove duplicate key combos that clash with each other
        Set<String> registeredKeyCombos = new HashSet<>();
        // Add cycle hotkey combination so device hotkeys don't conflict with it
        registeredKeyCombos.add(config.getHotkeyModifiers() + ":" + config.getHotkeyKeyCode());

        // Device name mapping for resilient recovery across Windows endpoint GUID changes
        for (AudioDevice device : freshList) {
            // Check if there is an existing hotkey mapping for this device name whose ID changed
            if (!hotkeys.containsKey(device.getId())) {
                for (Map.Entry<String, AppConfig.DeviceHotkey> entry : new ArrayList<>(hotkeys.entrySet())) {
                    AppConfig.DeviceHotkey savedHk = entry.getValue();
                    if (savedHk != null && device.getName().equalsIgnoreCase(savedHk.getDeviceName())) {
                        // Migrate shortcut to the new endpoint ID
                        hotkeys.remove(entry.getKey());
                        savedHk.setDeviceName(device.getName());
                        hotkeys.put(device.getId(), savedHk);
                        configUpdated = true;
                        break;
                    }
                }
            }

            // Sync device name in hotkey if present
            AppConfig.DeviceHotkey currentHk = hotkeys.get(device.getId());
            if (currentHk != null && currentHk.getDeviceName() == null) {
                currentHk.setDeviceName(device.getName());
                configUpdated = true;
            }
        }

        // Available number keys for auto-assigning clean default shortcuts (Ctrl + Alt + 1..9)
        int[] numKeys = {
                KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
                KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6,
                KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9
        };

        if (firstRun) {
            enabledIds.clear();
            enabledNames.clear();

            for (int i = 0; i < freshList.size(); i++) {
                AudioDevice device = freshList.get(i);
                enabledIds.add(device.getId());
                enabledNames.add(device.getName());
                device.setCycleEnabled(true);

                if (i < numKeys.length) {
                    int number = i + 1;
                    hotkeys.put(device.getId(), new AppConfig.DeviceHotkey(
                            3, // Ctrl (2) + Alt (1)
                            numKeys[i],
                            "Ctrl + Alt + " + number,
                            device.getName()
                    ));
                }
            }
            config.setInitialized(true);
            configUpdated = true;
        } else {
            // Normal update: Respect user choices! NEVER silently re-assign shortcuts.
            for (AudioDevice device : freshList) {
                boolean isEnabled = enabledIds.contains(device.getId()) || enabledNames.contains(device.getName());
                device.setCycleEnabled(isEnabled);
                if (isEnabled && !enabledIds.contains(device.getId())) {
                    enabledIds.add(device.getId());
                    configUpdated = true;
                }
            }
        }

        if (configUpdated) {
            configManager.saveConfig();
        }

        cachedDevices.clear();
        cachedDevices.addAll(freshList);

        for (Runnable listener : devicesChangedListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized List<AudioDevice> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(cachedDevices));
    }

    public synchronized List<AudioDevice> getCycleEnabledDevices() {
        List<AudioDevice> enabled = new ArrayList<>();
        for (AudioDevice d : cachedDevices) {
            if (d.isCycleEnabled()) {
                enabled.add(d);
            }
        }
        return enabled;
    }

    public synchronized AudioDevice getDefaultDevice() {
        for (AudioDevice d : cachedDevices) {
            if (d.isDefault()) {
                return d;
            }
        }
        return cachedDevices.isEmpty() ? null : cachedDevices.get(0);
    }

    /**
     * Cycles to the next enabled playback device. If a device fails to switch, continues
     * through the cycle list until a working device is successfully activated.
     */
    public synchronized AudioDevice cycleToNextDevice() {
        refreshDevices();
        List<AudioDevice> cycleList = getCycleEnabledDevices();
        if (cycleList.isEmpty()) {
            cycleList = cachedDevices; // fallback if user unchecked all
        }
        if (cycleList.isEmpty()) {
            return null;
        }

        AudioDevice current = getDefaultDevice();
        int currentIndex = -1;
        if (current != null) {
            for (int i = 0; i < cycleList.size(); i++) {
                if (cycleList.get(i).getId().equalsIgnoreCase(current.getId())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        // Try candidate devices in circular sequence until one succeeds
        for (int step = 1; step <= cycleList.size(); step++) {
            int nextIndex = (currentIndex + step) % cycleList.size();
            AudioDevice candidate = cycleList.get(nextIndex);
            if (current != null && candidate.getId().equalsIgnoreCase(current.getId()) && cycleList.size() > 1) {
                continue;
            }

            boolean success = switchToDevice(candidate.getId());
            if (success) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Switches to a specific device ID.
     */
    public synchronized boolean switchToDevice(String deviceId) {
        boolean success = WindowsCoreAudio.setDefaultPlaybackDevice(deviceId);
        if (success) {
            AudioDevice newlyActive = null;
            for (AudioDevice d : cachedDevices) {
                boolean isMatch = d.getId().equalsIgnoreCase(deviceId);
                d.setDefault(isMatch);
                d.setDefaultCommunication(isMatch);
                if (isMatch) {
                    newlyActive = d;
                }
            }

            if (newlyActive != null) {
                for (Consumer<AudioDevice> listener : deviceSwitchedListeners) {
                    try {
                        listener.accept(newlyActive);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return success;
    }

    public void addDeviceSwitchedListener(Consumer<AudioDevice> listener) {
        deviceSwitchedListeners.add(listener);
    }

    public void addDevicesChangedListener(Runnable listener) {
        devicesChangedListeners.add(listener);
    }
}
