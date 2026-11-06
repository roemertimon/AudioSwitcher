package com.audioswitcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.audioswitcher.audio.AudioDeviceManager;
import com.audioswitcher.audio.SoundNotifier;
import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.hotkey.HotkeyManager;
import com.audioswitcher.model.AudioDevice;
import com.audioswitcher.ui.OsdOverlay;
import com.audioswitcher.ui.SettingsFrame;
import com.audioswitcher.ui.TrayManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AudioSwitcherApp {

    private static final String MUTEX_NAME = "Local\\AudioSwitcherSingleInstanceMutex";
    private static HANDLE appMutex = null;

    public static void main(String[] args) {
        // Parse startup arguments
        boolean startMinimizedArg = false;
        if (args != null) {
            for (String arg : args) {
                if ("--minimized".equalsIgnoreCase(arg) || "-minimized".equalsIgnoreCase(arg)
                        || "--tray".equalsIgnoreCase(arg) || "-tray".equalsIgnoreCase(arg)) {
                    startMinimizedArg = true;
                    break;
                }
            }
        }
        final boolean startMinimized = startMinimizedArg;

        // 1. Single Instance Check: if already running, bring existing window to front and exit quietly
        appMutex = Kernel32.INSTANCE.CreateMutex(null, true, MUTEX_NAME);
        if (Kernel32.INSTANCE.GetLastError() == WinError.ERROR_ALREADY_EXISTS) {
            HWND hwnd = User32.INSTANCE.FindWindow(null, "Audio Switcher");
            if (hwnd != null) {
                User32.INSTANCE.ShowWindow(hwnd, 9); // SW_RESTORE
                User32.INSTANCE.SetForegroundWindow(hwnd);
            }
            System.out.println("Audio Switcher is already active. Existing window brought to front.");
            System.exit(0);
        }

        // 2. Set modern FlatLaf dark look and feel
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            System.err.println("Failed to initialize FlatDarkLaf, using system fallback.");
        }

        SwingUtilities.invokeLater(() -> {
            if (!SystemTray.isSupported()) {
                System.err.println("System Tray is not supported on this platform.");
            }

            // 3. Initialize components
            ConfigManager configManager = new ConfigManager();
            AppConfig config = configManager.getConfig();
            SoundNotifier.setVolume(config.getChimeVolume() / 100.0f);

            AudioDeviceManager audioManager = new AudioDeviceManager(configManager);
            OsdOverlay osdOverlay = new OsdOverlay();
            HotkeyManager hotkeyManager = new HotkeyManager();

            // Device switch feedback: Sound chime & OSD overlay (instant responsiveness)
            audioManager.addDeviceSwitchedListener(device -> {
                if (config.isPlayChimeOnSwitch()) {
                    SoundNotifier.playSwitchChime();
                }
                if (config.isShowOsd()) {
                    osdOverlay.showForDevice(device, config.getOsdDurationMs(), config.getOsdPosition());
                }
            });

            // Re-registers all hotkeys (cycle + active per-device hotkeys without collision)
            Runnable updateHotkeysRunnable = () -> {
                List<HotkeyManager.HotkeyEntry> entries = new ArrayList<>();

                // Global cycle hotkey (ID = 1)
                entries.add(new HotkeyManager.HotkeyEntry(
                        HotkeyManager.CYCLE_HOTKEY_ID,
                        config.getHotkeyModifiers(),
                        config.getHotkeyKeyCode(),
                        audioManager::cycleToNextDevice
                ));

                // Dedicated per-device hotkeys (ID >= 100)
                // ONLY register hotkeys for devices that are CURRENTLY CONNECTED to prevent conflicts
                int deviceHkId = HotkeyManager.DEVICE_HOTKEY_BASE_ID;
                Map<String, AppConfig.DeviceHotkey> devHotkeys = config.getDeviceHotkeys();
                List<AudioDevice> connectedDevices = audioManager.getDevices();

                for (AudioDevice device : connectedDevices) {
                    AppConfig.DeviceHotkey hk = devHotkeys.get(device.getId());
                    if (hk != null && hk.getKeyCode() != 0) {
                        final int id = deviceHkId++;
                        final String targetId = device.getId();
                        entries.add(new HotkeyManager.HotkeyEntry(
                                id,
                                hk.getModifiers(),
                                hk.getKeyCode(),
                                () -> audioManager.switchToDevice(targetId)
                        ));
                    }
                }

                hotkeyManager.setHotkeys(entries);
            };

            updateHotkeysRunnable.run();

            // When audio hardware devices change, automatically refresh hotkeys for active hardware
            audioManager.addDevicesChangedListener(updateHotkeysRunnable);

            // 4. UI: Settings & System Tray
            SettingsFrame settingsFrame = new SettingsFrame(audioManager, configManager, updateHotkeysRunnable);
            new TrayManager(audioManager, settingsFrame, configManager);

            // Handle start minimized: if requested via CLI or configured for Windows startup, keep hidden in tray
            if (startMinimized || (config.isStartWithWindows() && config.isStartMinimized())) {
                settingsFrame.setVisible(false);
            } else {
                settingsFrame.setVisible(true);
                settingsFrame.toFront();
            }

            // 5. Background hot-plug detection timer (checks every 5 seconds for newly plugged/unplugged hardware)
            Timer hotPlugTimer = new Timer(5000, e -> audioManager.refreshDevices());
            hotPlugTimer.setRepeats(true);
            hotPlugTimer.start();

            System.out.println("==================================================");
            System.out.println("  Audio Switcher started successfully!");
            System.out.println("  Active Playback Device: " + (audioManager.getDefaultDevice() != null ? audioManager.getDefaultDevice().getName() : "None"));
            System.out.println("  Cycle Shortcut: " + config.getHotkeyDisplayString());
            System.out.println("  Start Minimized: " + (startMinimized || (config.isStartWithWindows() && config.isStartMinimized())));
            System.out.println("==================================================");

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                hotPlugTimer.stop();
                hotkeyManager.stop();
                if (appMutex != null) {
                    Kernel32.INSTANCE.ReleaseMutex(appMutex);
                    Kernel32.INSTANCE.CloseHandle(appMutex);
                }
            }));
        });
    }
}
