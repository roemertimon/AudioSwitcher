package com.audioswitcher.audio;

import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
        Map<String, AppConfig.DeviceHotkey> hotkeys = config.getDeviceHotkeys();

        boolean firstRun = !config.isInitialized() || enabledIds.isEmpty();
        boolean configUpdated = false;

        if (firstRun) {
            enabledIds = new HashSet<>();
        }

        // Available number keys for auto-assigning clean default shortcuts (Ctrl + Alt + 1..9)
        int[] numKeys = {
                KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
                KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6,
                KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9
        };

        for (int i = 0; i < freshList.size(); i++) {
            AudioDevice device = freshList.get(i);

            // Auto-enable in cycle on first run
            if (firstRun) {
                enabledIds.add(device.getId());
                device.setCycleEnabled(true);
                configUpdated = true;
            } else {
                device.setCycleEnabled(enabledIds.contains(device.getId()));
            }

            // Auto-assign clean default shortcut (Ctrl + Alt + 1..9) if device has none
            if (!hotkeys.containsKey(device.getId()) && i < numKeys.length) {
                int number = i + 1;
                hotkeys.put(device.getId(), new AppConfig.DeviceHotkey(
                        3, // Ctrl (2) + Alt (1)
                        numKeys[i],
                        "Ctrl + Alt + " + number
                ));
                configUpdated = true;
            }
        }

        if (firstRun) {
            config.setEnabledDeviceIds(enabledIds);
            config.setInitialized(true);
            configUpdated = true;
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
     * Cycles to the next enabled playback device.
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

        int nextIndex = (currentIndex + 1) % cycleList.size();
        AudioDevice nextDevice = cycleList.get(nextIndex);
        boolean success = switchToDevice(nextDevice.getId());

        if (success) {
            return nextDevice;
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
