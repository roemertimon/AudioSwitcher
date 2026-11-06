package com.audioswitcher.ui;

import com.audioswitcher.audio.AudioDeviceManager;
import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

public class TrayManager {

    private final AudioDeviceManager audioManager;
    private final SettingsFrame settingsFrame;
    private final ConfigManager configManager;
    private TrayIcon trayIcon;

    public TrayManager(AudioDeviceManager audioManager, SettingsFrame settingsFrame, ConfigManager configManager) {
        this.audioManager = audioManager;
        this.settingsFrame = settingsFrame;
        this.configManager = configManager;

        initTray();
        audioManager.addDevicesChangedListener(this::updateTrayMenu);
        audioManager.addDeviceSwitchedListener(device -> updateTrayMenu());
    }

    private void initTray() {
        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray is not supported on this platform.");
            return;
        }

        Image iconImage = createSpeakerIcon();
        trayIcon = new TrayIcon(iconImage, "Audio Switcher");
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    settingsFrame.refreshTableData();
                    settingsFrame.setVisible(true);
                    settingsFrame.toFront();
                }
            }
        });

        updateTrayMenu();

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Failed to add icon to SystemTray: " + e.getMessage());
        }
    }

    public synchronized void updateTrayMenu() {
        if (trayIcon == null) return;

        PopupMenu popup = new PopupMenu();

        MenuItem headerItem = new MenuItem("Audio Switcher");
        headerItem.setEnabled(false);
        popup.add(headerItem);
        popup.addSeparator();

        AppConfig config = configManager.getConfig();
        String cycleShortcut = config.getHotkeyDisplayString();
        MenuItem switchItem = new MenuItem("Next Device (" + cycleShortcut + ")");
        switchItem.addActionListener(e -> audioManager.cycleToNextDevice());
        popup.add(switchItem);
        popup.addSeparator();

        // Audio devices list
        List<AudioDevice> devices = audioManager.getDevices();
        for (AudioDevice device : devices) {
            AppConfig.DeviceHotkey hk = config.getDeviceHotkeys().get(device.getId());
            String hkSuffix = (hk != null && hk.getDisplayString() != null) ? " [" + hk.getDisplayString() + "]" : "";
            String label = (device.isDefault() ? "✔  " : "    ") + device.getName() + hkSuffix;
            MenuItem devItem = new MenuItem(label);
            devItem.addActionListener(e -> audioManager.switchToDevice(device.getId()));
            popup.add(devItem);
        }

        popup.addSeparator();

        MenuItem settingsItem = new MenuItem("Settings...");
        settingsItem.addActionListener(e -> {
            settingsFrame.refreshTableData();
            settingsFrame.setVisible(true);
            settingsFrame.toFront();
        });
        popup.add(settingsItem);

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        });
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        AudioDevice active = audioManager.getDefaultDevice();
        if (active != null) {
            trayIcon.setToolTip("Audio Switcher: " + active.getName());
        }
    }

    private Image createSpeakerIcon() {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Circular background
        g2.setColor(new Color(37, 99, 235));
        g2.fillOval(2, 2, size - 4, size - 4);

        // White speaker cone
        g2.setColor(Color.WHITE);
        int[] x = {8, 13, 20, 20, 13, 8};
        int[] y = {13, 13, 7, 25, 19, 19};
        g2.fillPolygon(x, y, 6);

        // Sound arc
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(19, 10, 8, 12, -45, 90);

        g2.dispose();
        return img;
    }
}
