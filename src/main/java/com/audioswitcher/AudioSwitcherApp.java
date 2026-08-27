package com.audioswitcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.audioswitcher.audio.AudioDeviceManager;
import com.audioswitcher.audio.SoundNotifier;
import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.hotkey.HotkeyManager;
import com.audioswitcher.ui.OsdOverlay;
import com.audioswitcher.ui.SettingsFrame;
import com.audioswitcher.ui.TrayManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AudioSwitcherApp {

    private static final String MUTEX_NAME = "Local\\AudioSwitcherSingleInstanceMutex";
    private static HANDLE appMutex = null;

    public static void main(String[] args) {
        // 1. Single Instance Check via Windows Mutex
        appMutex = Kernel32.INSTANCE.CreateMutex(null, true, MUTEX_NAME);
        if (Kernel32.INSTANCE.GetLastError() == WinError.ERROR_ALREADY_EXISTS) {
            JOptionPane.showMessageDialog(null,
                    "Audio Switcher is already running in the background (check your system tray icon).",
                    "Audio Switcher",
                    JOptionPane.INFORMATION_MESSAGE);
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
                JOptionPane.showMessageDialog(null,
                        "System Tray is not supported on this platform.",
                        "Audio Switcher",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            // 3. Initialize components
            ConfigManager configManager = new ConfigManager();
            AppConfig config = configManager.getConfig();
            SoundNotifier.setVolume(config.getChimeVolume() / 100.0f);

            AudioDeviceManager audioManager = new AudioDeviceManager(configManager);
            OsdOverlay osdOverlay = new OsdOverlay();
            HotkeyManager hotkeyManager = new HotkeyManager();

            // Device switch feedback: Sound chime & OSD overlay
            audioManager.addDeviceSwitchedListener(device -> {
                if (config.isPlayChimeOnSwitch()) {
                    SoundNotifier.playSwitchChime();
                }
                if (config.isShowOsd()) {
                    osdOverlay.showForDevice(device, config.getOsdDurationMs(), config.getOsdPosition());
                }
            });

            // Re-registers all hotkeys (cycle + individual device hotkeys)
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
                int deviceHkId = HotkeyManager.DEVICE_HOTKEY_BASE_ID;
                Map<String, AppConfig.DeviceHotkey> devHotkeys = config.getDeviceHotkeys();
                for (Map.Entry<String, AppConfig.DeviceHotkey> entry : devHotkeys.entrySet()) {
                    String deviceId = entry.getKey();
                    AppConfig.DeviceHotkey hk = entry.getValue();
                    if (hk != null && hk.getKeyCode() != 0) {
                        final int id = deviceHkId++;
                        entries.add(new HotkeyManager.HotkeyEntry(
                                id,
                                hk.getModifiers(),
                                hk.getKeyCode(),
                                () -> audioManager.switchToDevice(deviceId)
                        ));
                    }
                }

                hotkeyManager.setHotkeys(entries);
            };

            updateHotkeysRunnable.run();

            // 4. UI: Settings & System Tray
            SettingsFrame settingsFrame = new SettingsFrame(audioManager, configManager, updateHotkeysRunnable);
            new TrayManager(audioManager, settingsFrame, configManager);

            // Open the clean dashboard on startup so the user immediately sees the app!
            settingsFrame.setVisible(true);
            settingsFrame.toFront();

            // 5. Background hot-plug detection timer (checks every 5 seconds for newly plugged/unplugged hardware)
            Timer hotPlugTimer = new Timer(5000, e -> audioManager.refreshDevices());
            hotPlugTimer.setRepeats(true);
            hotPlugTimer.start();

            System.out.println("==================================================");
            System.out.println("  Audio Switcher started successfully!");
            System.out.println("  Active Playback Device: " + (audioManager.getDefaultDevice() != null ? audioManager.getDefaultDevice().getName() : "None"));
            System.out.println("  Cycle Shortcut: " + config.getHotkeyDisplayString());
            System.out.println("  Default Number Shortcuts: Ctrl + Alt + 1, 2, 3...");
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
