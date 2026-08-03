package com.audioswitcher.ui;

import com.audioswitcher.audio.AudioDeviceManager;
import com.audioswitcher.audio.SoundNotifier;
import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;
import com.audioswitcher.startup.StartupManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsFrame extends JFrame {

    private final AudioDeviceManager audioManager;
    private final ConfigManager configManager;
    private final Runnable onHotkeysChanged;

    private JTable devicesTable;
    private DefaultTableModel tableModel;
    private JTextField globalHotkeyField;
    private JCheckBox osdCheckBox;
    private JComboBox<String> osdPositionCombo;
    private JSpinner durationSpinner;
    private JCheckBox chimeCheckBox;
    private JCheckBox startupCheckBox;

    private int selectedGlobalModifiers;
    private int selectedGlobalKeyCode;
    private String selectedGlobalKeyDisplay;

    // Temporary working copy of per-device hotkeys: deviceId -> DeviceHotkey
    private final Map<String, AppConfig.DeviceHotkey> workingDeviceHotkeys = new HashMap<>();

    public SettingsFrame(AudioDeviceManager audioManager, ConfigManager configManager, Runnable onHotkeysChanged) {
        this.audioManager = audioManager;
        this.configManager = configManager;
        this.onHotkeysChanged = onHotkeysChanged;

        setTitle("Audio Switcher - Settings");
        setSize(650, 600);
        setMinimumSize(new Dimension(580, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        initUi();
        loadDataFromConfig();
    }

    private void initUi() {
        JPanel contentPane = new JPanel(new BorderLayout(12, 12));
        contentPane.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(contentPane);

        // Header
        JLabel headerLabel = new JLabel("Audio Switcher Settings");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        contentPane.add(headerLabel, BorderLayout.NORTH);

        // Center tabs or panels
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // 1. Devices Selection Section
        JPanel devicesPanel = new JPanel(new BorderLayout(6, 6));
        devicesPanel.setBorder(BorderFactory.createTitledBorder("Playback Devices & Shortcuts"));

        tableModel = new DefaultTableModel(new Object[]{"Cycle", "Device Name", "Direct Shortcut", "Status"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        devicesTable = new JTable(tableModel);
        devicesTable.setRowHeight(28);
        devicesTable.getColumnModel().getColumn(0).setMaxWidth(55);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        devicesTable.getColumnModel().getColumn(3).setMaxWidth(80);

        JScrollPane scrollPane = new JScrollPane(devicesTable);
        scrollPane.setPreferredSize(new Dimension(580, 160));
        devicesPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel devicesBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton setShortcutBtn = new JButton("Set Device Shortcut...");
        setShortcutBtn.addActionListener(e -> promptSetDeviceShortcut());

        JButton clearShortcutBtn = new JButton("Clear Shortcut");
        clearShortcutBtn.addActionListener(e -> clearSelectedDeviceShortcut());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshTableData());

        devicesBtnBar.add(setShortcutBtn);
        devicesBtnBar.add(clearShortcutBtn);
        devicesBtnBar.add(refreshBtn);
        devicesPanel.add(devicesBtnBar, BorderLayout.SOUTH);

        centerPanel.add(devicesPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // 2. Global Cycle Hotkey Section
        JPanel hotkeyPanel = new JPanel(new GridBagLayout());
        hotkeyPanel.setBorder(BorderFactory.createTitledBorder("Global Cycle Hotkey (Switches to next enabled device)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        hotkeyPanel.add(new JLabel("Cycle Shortcut:"), gbc);

        globalHotkeyField = new JTextField(16);
        globalHotkeyField.setEditable(false);
        globalHotkeyField.setHorizontalAlignment(JTextField.CENTER);
        globalHotkeyField.setFont(new Font("Segoe UI", Font.BOLD, 13));
        globalHotkeyField.setToolTipText("Click here and press modifier keys + key to change");

        globalHotkeyField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT ||
                        code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_WINDOWS) {
                    return;
                }

                int mods = 0;
                StringBuilder sb = new StringBuilder();
                if (e.isControlDown()) {
                    mods |= 0x0002;
                    sb.append("Ctrl + ");
                }
                if (e.isAltDown()) {
                    mods |= 0x0001;
                    sb.append("Alt + ");
                }
                if (e.isShiftDown()) {
                    mods |= 0x0004;
                    sb.append("Shift + ");
                }

                if (mods == 0) {
                    JOptionPane.showMessageDialog(SettingsFrame.this,
                            "Please include at least one modifier key (Ctrl, Alt, Shift).",
                            "Invalid Shortcut", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                sb.append(KeyEvent.getKeyText(code));

                selectedGlobalModifiers = mods;
                selectedGlobalKeyCode = code;
                selectedGlobalKeyDisplay = sb.toString();
                globalHotkeyField.setText(selectedGlobalKeyDisplay);
            }
        });

        gbc.gridx = 1;
        hotkeyPanel.add(globalHotkeyField, gbc);

        JButton resetHotkeyBtn = new JButton("Reset (Ctrl+Alt+A)");
        resetHotkeyBtn.addActionListener(e -> {
            selectedGlobalModifiers = 3;
            selectedGlobalKeyCode = KeyEvent.VK_A;
            selectedGlobalKeyDisplay = "Ctrl + Alt + A";
            globalHotkeyField.setText(selectedGlobalKeyDisplay);
        });
        gbc.gridx = 2;
        hotkeyPanel.add(resetHotkeyBtn, gbc);

        centerPanel.add(hotkeyPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // 3. Audio & OSD Preferences Section
        JPanel prefPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        prefPanel.setBorder(BorderFactory.createTitledBorder("Feedback & Notifications"));

        // Sound chime
        JPanel chimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        chimeCheckBox = new JCheckBox("Play audio chime on device switch");
        chimePanel.add(chimeCheckBox);
        JButton testSoundBtn = new JButton("Test Sound");
        testSoundBtn.addActionListener(e -> SoundNotifier.playSwitchChime());
        chimePanel.add(testSoundBtn);
        prefPanel.add(chimePanel);

        // Startup
        startupCheckBox = new JCheckBox("Launch automatically with Windows");
        prefPanel.add(startupCheckBox);

        // OSD checkbox
        osdCheckBox = new JCheckBox("Show On-Screen Display (OSD)");
        prefPanel.add(osdCheckBox);

        // OSD position
        JPanel posPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        posPanel.add(new JLabel("OSD Position:"));
        osdPositionCombo = new JComboBox<>(new String[]{"Top of Screen", "Center of Screen", "Bottom of Screen"});
        posPanel.add(osdPositionCombo);
        prefPanel.add(posPanel);

        // OSD duration
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        durationPanel.add(new JLabel("OSD Duration (ms):"));
        durationSpinner = new JSpinner(new SpinnerNumberModel(1800, 500, 5000, 100));
        durationPanel.add(durationSpinner);
        prefPanel.add(durationPanel);

        centerPanel.add(prefPanel);

        contentPane.add(centerPanel, BorderLayout.CENTER);

        // Bottom Actions
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton saveBtn = new JButton("Save & Apply");
        saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        saveBtn.addActionListener(e -> saveAndApply());

        JButton cancelBtn = new JButton("Close");
        cancelBtn.addActionListener(e -> setVisible(false));

        bottomBar.add(saveBtn);
        bottomBar.add(cancelBtn);
        contentPane.add(bottomBar, BorderLayout.SOUTH);
    }

    private void promptSetDeviceShortcut() {
        int selectedRow = devicesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an audio device from the table first.", "Select Device", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<AudioDevice> devices = audioManager.getDevices();
        if (selectedRow >= devices.size()) return;
        AudioDevice device = devices.get(selectedRow);

        JDialog dialog = new JDialog(this, "Set Shortcut for: " + device.getName(), true);
        dialog.setSize(400, 180);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel info = new JLabel("<html>Press your desired key combination (e.g. <b>Ctrl + Alt + 1</b>):</html>");
        panel.add(info, BorderLayout.NORTH);

        JTextField inputField = new JTextField(16);
        inputField.setHorizontalAlignment(JTextField.CENTER);
        inputField.setFont(new Font("Segoe UI", Font.BOLD, 14));
        inputField.setEditable(false);

        final int[] resultMods = new int[]{0};
        final int[] resultCode = new int[]{0};
        final String[] resultDisplay = new String[]{""};

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT ||
                        code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_WINDOWS) {
                    return;
                }

                int mods = 0;
                StringBuilder sb = new StringBuilder();
                if (e.isControlDown()) {
                    mods |= 0x0002;
                    sb.append("Ctrl + ");
                }
                if (e.isAltDown()) {
                    mods |= 0x0001;
                    sb.append("Alt + ");
                }
                if (e.isShiftDown()) {
                    mods |= 0x0004;
                    sb.append("Shift + ");
                }

                if (mods == 0) {
                    return;
                }

                sb.append(KeyEvent.getKeyText(code));
                resultMods[0] = mods;
                resultCode[0] = code;
                resultDisplay[0] = sb.toString();
                inputField.setText(resultDisplay[0]);
            }
        });
        panel.add(inputField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(ev -> {
            if (resultCode[0] != 0 && !resultDisplay[0].isEmpty()) {
                workingDeviceHotkeys.put(device.getId(), new AppConfig.DeviceHotkey(resultMods[0], resultCode[0], resultDisplay[0]));
                tableModel.setValueAt(resultDisplay[0], selectedRow, 2);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Please press a valid combination with Ctrl/Alt/Shift.", "Incomplete Shortcut", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(ev -> dialog.dispose());

        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        inputField.requestFocusInWindow();
        dialog.setVisible(true);
    }

    private void clearSelectedDeviceShortcut() {
        int selectedRow = devicesTable.getSelectedRow();
        if (selectedRow < 0) return;

        List<AudioDevice> devices = audioManager.getDevices();
        if (selectedRow < devices.size()) {
            AudioDevice dev = devices.get(selectedRow);
            workingDeviceHotkeys.remove(dev.getId());
            tableModel.setValueAt("-", selectedRow, 2);
        }
    }

    private void loadDataFromConfig() {
        AppConfig config = configManager.getConfig();

        selectedGlobalModifiers = config.getHotkeyModifiers();
        selectedGlobalKeyCode = config.getHotkeyKeyCode();
        selectedGlobalKeyDisplay = config.getHotkeyDisplayString();
        globalHotkeyField.setText(selectedGlobalKeyDisplay);

        workingDeviceHotkeys.clear();
        workingDeviceHotkeys.putAll(config.getDeviceHotkeys());

        osdCheckBox.setSelected(config.isShowOsd());
        durationSpinner.setValue(config.getOsdDurationMs());
        chimeCheckBox.setSelected(config.isPlayChimeOnSwitch());
        startupCheckBox.setSelected(StartupManager.isStartupEnabled());

        String pos = config.getOsdPosition();
        if ("CENTER".equalsIgnoreCase(pos)) {
            osdPositionCombo.setSelectedIndex(1);
        } else if ("BOTTOM".equalsIgnoreCase(pos)) {
            osdPositionCombo.setSelectedIndex(2);
        } else {
            osdPositionCombo.setSelectedIndex(0);
        }

        refreshTableData();
    }

    public void refreshTableData() {
        audioManager.refreshDevices();
        tableModel.setRowCount(0);

        List<AudioDevice> devices = audioManager.getDevices();
        for (AudioDevice device : devices) {
            String status = device.isDefault() ? "Active" : "";
            AppConfig.DeviceHotkey hk = workingDeviceHotkeys.get(device.getId());
            String shortcutStr = hk != null ? hk.getDisplayString() : "-";
            tableModel.addRow(new Object[]{device.isCycleEnabled(), device.getName(), shortcutStr, status});
        }
    }

    private void saveAndApply() {
        AppConfig config = configManager.getConfig();

        // 1. Save enabled devices
        Set<String> enabledIds = new HashSet<>();
        List<AudioDevice> devices = audioManager.getDevices();
        for (int i = 0; i < tableModel.getRowCount() && i < devices.size(); i++) {
            boolean enabled = (Boolean) tableModel.getValueAt(i, 0);
            devices.get(i).setCycleEnabled(enabled);
            if (enabled) {
                enabledIds.add(devices.get(i).getId());
            }
        }
        config.setEnabledDeviceIds(enabledIds);

        // 2. Save per-device hotkeys
        config.setDeviceHotkeys(new HashMap<>(workingDeviceHotkeys));

        // 3. Save global hotkey
        config.setHotkeyModifiers(selectedGlobalModifiers);
        config.setHotkeyKeyCode(selectedGlobalKeyCode);
        config.setHotkeyDisplayString(selectedGlobalKeyDisplay);

        // 4. Save feedback settings
        config.setPlayChimeOnSwitch(chimeCheckBox.isSelected());
        config.setShowOsd(osdCheckBox.isSelected());
        config.setOsdDurationMs((Integer) durationSpinner.getValue());

        int posIdx = osdPositionCombo.getSelectedIndex();
        if (posIdx == 1) {
            config.setOsdPosition("CENTER");
        } else if (posIdx == 2) {
            config.setOsdPosition("BOTTOM");
        } else {
            config.setOsdPosition("TOP");
        }

        // 5. Save startup
        boolean startup = startupCheckBox.isSelected();
        config.setStartWithWindows(startup);
        StartupManager.setStartupEnabled(startup);

        configManager.saveConfig();

        if (onHotkeysChanged != null) {
            onHotkeysChanged.run();
        }

        JOptionPane.showMessageDialog(this, "Settings saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }
}
