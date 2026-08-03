package com.audioswitcher.audio;

import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

        // If enabledIds is empty (first launch), enable all detected devices by default
        boolean firstRun = enabledIds == null || enabledIds.isEmpty();
        if (firstRun) {
            enabledIds = new java.util.HashSet<>();
        }

        for (AudioDevice device : freshList) {
            if (firstRun) {
                enabledIds.add(device.getId());
                device.setCycleEnabled(true);
            } else {
                device.setCycleEnabled(enabledIds.contains(device.getId()));
            }
        }

        if (firstRun) {
            config.setEnabledDeviceIds(enabledIds);
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
            // Update cached states
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
