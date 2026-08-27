package com.audioswitcher.ui;

import com.audioswitcher.audio.AudioDeviceManager;
import com.audioswitcher.audio.SoundNotifier;
import com.audioswitcher.config.AppConfig;
import com.audioswitcher.config.ConfigManager;
import com.audioswitcher.model.AudioDevice;
import com.audioswitcher.startup.StartupManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsFrame extends JFrame {

    private final AudioDeviceManager audioManager;
    private final ConfigManager configManager;
    private final Runnable onHotkeysChanged;

    private JLabel activeDeviceBadgeLabel;
    private JTable devicesTable;
    private DefaultTableModel tableModel;
    private JTextField globalHotkeyField;

    // Preferences components
    private JCheckBox chimeCheckBox;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JCheckBox osdCheckBox;
    private JComboBox<String> osdPositionCombo;
    private JSpinner durationSpinner;
    private JCheckBox startupCheckBox;

    private int selectedGlobalModifiers;
    private int selectedGlobalKeyCode;
    private String selectedGlobalKeyDisplay;

    // Working copy of per-device shortcuts
    private final Map<String, AppConfig.DeviceHotkey> workingDeviceHotkeys = new HashMap<>();

    public SettingsFrame(AudioDeviceManager audioManager, ConfigManager configManager, Runnable onHotkeysChanged) {
        this.audioManager = audioManager;
        this.configManager = configManager;
        this.onHotkeysChanged = onHotkeysChanged;

        setTitle("Audio Switcher");
        setSize(760, 710);
        setMinimumSize(new Dimension(680, 620));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        initUi();
        loadDataFromConfig();

        // Listen for live device switches to update active badge
        audioManager.addDeviceSwitchedListener(dev -> SwingUtilities.invokeLater(this::updateActiveBadge));
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(18, 22, 18, 22));
        root.setBackground(new Color(24, 25, 28));
        setContentPane(root);

        // ================= TOP HEADER BANNER =================
        JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
        headerPanel.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel titleLabel = new JLabel("Audio Switcher");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(240, 242, 245));
        titleBox.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Fast, intuitive Windows playback switching with dedicated shortcuts");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(156, 163, 175));
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        headerPanel.add(titleBox, BorderLayout.WEST);

        // Active device status pill
        JPanel badgeCard = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        badgeCard.setBackground(new Color(33, 37, 43));
        badgeCard.setBorder(new CompoundBorder(
                new LineBorder(new Color(55, 65, 81), 1, true),
                new EmptyBorder(4, 12, 4, 12)
        ));

        JLabel dotLabel = new JLabel("●");
        dotLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        dotLabel.setForeground(new Color(34, 197, 94));
        badgeCard.add(dotLabel);

        activeDeviceBadgeLabel = new JLabel("Active: Detecting...");
        activeDeviceBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        activeDeviceBadgeLabel.setForeground(new Color(229, 231, 235));
        badgeCard.add(activeDeviceBadgeLabel);

        headerPanel.add(badgeCard, BorderLayout.EAST);
        root.add(headerPanel, BorderLayout.NORTH);

        // ================= CENTER CONTENT =================
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // 1. CARD: DEVICES & SHORTCUTS
        JPanel devicesCard = createCardPanel();
        devicesCard.setLayout(new BorderLayout(0, 10));

        JLabel tableHint = new JLabel("Select devices for cycling or assign direct shortcuts (default: Ctrl + Alt + 1, 2, 3...).");
        tableHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tableHint.setForeground(new Color(156, 163, 175));
        devicesCard.add(tableHint, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[]{"Cycle", "Device Name", "Direct Shortcut", "Action"}, 0) {
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
        devicesTable.setRowHeight(34);
        devicesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        devicesTable.setShowVerticalLines(false);
        devicesTable.setGridColor(new Color(45, 50, 58));
        devicesTable.setBackground(new Color(28, 31, 36));
        devicesTable.setForeground(Color.WHITE);
        devicesTable.setSelectionBackground(new Color(49, 65, 95));

        // Center header alignment for Cycle
        ((DefaultTableCellRenderer) devicesTable.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        devicesTable.getColumnModel().getColumn(0).setMaxWidth(60);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        devicesTable.getColumnModel().getColumn(3).setPreferredWidth(110);

        // Custom centered checkbox renderer for Cycle column
        devicesTable.getColumnModel().getColumn(0).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JCheckBox cb = new JCheckBox();
            cb.setSelected(value != null && (Boolean) value);
            cb.setHorizontalAlignment(SwingConstants.CENTER);
            cb.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return cb;
        });

        // Custom renderer for shortcut badge pill
        devicesTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                String text = value != null ? value.toString() : "-";
                l.setText(text.equals("-") ? "-" : text);
                l.setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (!isSelected) {
                    l.setForeground(text.equals("-") ? new Color(120, 125, 135) : new Color(96, 165, 250));
                }
                return l;
            }
        });

        // Custom renderer for Action column (Active vs Switch)
        devicesTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                String text = value != null ? value.toString() : "";
                if ("Active".equalsIgnoreCase(text)) {
                    l.setText("ACTIVE");
                    l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    l.setForeground(new Color(34, 197, 94));
                } else {
                    l.setText("Switch");
                    l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    l.setForeground(new Color(156, 163, 175));
                }
                return l;
            }
        });

        // Click action handling
        devicesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = devicesTable.getSelectedRow();
                int col = devicesTable.getSelectedColumn();
                if (row >= 0) {
                    if (col == 3) {
                        List<AudioDevice> devices = audioManager.getDevices();
                        if (row < devices.size()) {
                            audioManager.switchToDevice(devices.get(row).getId());
                        }
                    } else if (e.getClickCount() == 2 && col == 2) {
                        promptSetDeviceShortcut();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(devicesTable);
        scrollPane.setPreferredSize(new Dimension(680, 170));
        scrollPane.setBorder(new LineBorder(new Color(45, 50, 58), 1));
        devicesCard.add(scrollPane, BorderLayout.CENTER);

        // Neatly aligned action buttons under table
        JPanel devicesBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        devicesBtnBar.setOpaque(false);

        JButton setShortcutBtn = createStyledButton("Assign Shortcut...", false);
        setShortcutBtn.addActionListener(e -> promptSetDeviceShortcut());

        JButton clearShortcutBtn = createStyledButton("Clear", false);
        clearShortcutBtn.addActionListener(e -> clearSelectedDeviceShortcut());

        JButton autoAssignBtn = createStyledButton("Reset Numbers (1..N)", false);
        autoAssignBtn.setToolTipText("Automatically assign Ctrl + Alt + 1, 2, 3... to devices");
        autoAssignBtn.addActionListener(e -> autoAssignNumberShortcuts());

        JButton refreshBtn = createStyledButton("Refresh", false);
        refreshBtn.addActionListener(e -> refreshTableData());

        devicesBtnBar.add(setShortcutBtn);
        devicesBtnBar.add(clearShortcutBtn);
        devicesBtnBar.add(autoAssignBtn);
        devicesBtnBar.add(refreshBtn);
        devicesCard.add(devicesBtnBar, BorderLayout.SOUTH);

        centerPanel.add(devicesCard);
        centerPanel.add(Box.createVerticalStrut(12));

        // 2. CARD: GLOBAL CYCLE SHORTCUT
        JPanel cycleCard = createCardPanel();
        cycleCard.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel cycleLabel = new JLabel("Cycle Next Device:");
        cycleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cycleCard.add(cycleLabel, gbc);

        globalHotkeyField = new JTextField(15);
        globalHotkeyField.setPreferredSize(new Dimension(170, 30));
        globalHotkeyField.setEditable(false);
        globalHotkeyField.setHorizontalAlignment(JTextField.CENTER);
        globalHotkeyField.setFont(new Font("Segoe UI", Font.BOLD, 13));
        globalHotkeyField.setBackground(new Color(36, 40, 47));
        globalHotkeyField.setForeground(new Color(96, 165, 250));
        globalHotkeyField.setBorder(new CompoundBorder(new LineBorder(new Color(55, 65, 81), 1), new EmptyBorder(4, 8, 4, 8)));
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
        cycleCard.add(globalHotkeyField, gbc);

        JButton resetHotkeyBtn = createStyledButton("Default (Ctrl+Alt+S)", false);
        resetHotkeyBtn.addActionListener(e -> {
            selectedGlobalModifiers = 3;
            selectedGlobalKeyCode = KeyEvent.VK_S;
            selectedGlobalKeyDisplay = "Ctrl + Alt + S";
            globalHotkeyField.setText(selectedGlobalKeyDisplay);
        });
        gbc.gridx = 2;
        cycleCard.add(resetHotkeyBtn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        JLabel cycleHelp = new JLabel("Cycles sequentially through all devices marked with 'Cycle' in the table above.");
        cycleHelp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cycleHelp.setForeground(new Color(156, 163, 175));
        cycleCard.add(cycleHelp, gbc);

        centerPanel.add(cycleCard);
        centerPanel.add(Box.createVerticalStrut(12));

        // 3. CARD: PREFERENCES (Two clean, aligned columns)
        JPanel prefCard = createCardPanel();
        prefCard.setLayout(new GridLayout(1, 2, 24, 0));

        // Left Column: Audio Feedback & Volume
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);

        JLabel audioSectionTitle = new JLabel("Audio Notification");
        audioSectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        audioSectionTitle.setForeground(new Color(220, 225, 235));
        leftCol.add(audioSectionTitle);
        leftCol.add(Box.createVerticalStrut(8));

        chimeCheckBox = new JCheckBox("Play acoustic chime on switch");
        chimeCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        leftCol.add(chimeCheckBox);
        leftCol.add(Box.createVerticalStrut(6));

        // Volume row with slider, value label, and Test button
        JPanel volRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        volRow.setOpaque(false);

        JLabel volLabel = new JLabel("Volume:");
        volLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        volLabel.setForeground(new Color(156, 163, 175));
        volRow.add(volLabel);

        volumeSlider = new JSlider(0, 100, 35);
        volumeSlider.setPreferredSize(new Dimension(130, 24));
        volumeSlider.setOpaque(false);
        volRow.add(volumeSlider);

        volumeValueLabel = new JLabel("35%");
        volumeValueLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        volumeValueLabel.setPreferredSize(new Dimension(36, 20));
        volRow.add(volumeValueLabel);

        JButton testSoundBtn = createStyledButton("Test", false);
        testSoundBtn.setPreferredSize(new Dimension(60, 26));
        testSoundBtn.addActionListener(e -> {
            float vol = volumeSlider.getValue() / 100.0f;
            SoundNotifier.playSwitchChime(vol);
        });
        volRow.add(testSoundBtn);

        volumeSlider.addChangeListener(e -> {
            int val = volumeSlider.getValue();
            volumeValueLabel.setText(val + "%");
            SoundNotifier.setVolume(val / 100.0f);
        });

        leftCol.add(volRow);
        prefCard.add(leftCol);

        // Right Column: Display & Windows Startup
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);

        JLabel displaySectionTitle = new JLabel("Display & Startup");
        displaySectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        displaySectionTitle.setForeground(new Color(220, 225, 235));
        rightCol.add(displaySectionTitle);
        rightCol.add(Box.createVerticalStrut(8));

        osdCheckBox = new JCheckBox("Show On-Screen Display (OSD)");
        osdCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rightCol.add(osdCheckBox);
        rightCol.add(Box.createVerticalStrut(6));

        // OSD position & duration row
        JPanel osdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        osdRow.setOpaque(false);

        JLabel posLabel = new JLabel("Position:");
        posLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        posLabel.setForeground(new Color(156, 163, 175));
        osdRow.add(posLabel);

        osdPositionCombo = new JComboBox<>(new String[]{"Top of Screen", "Center of Screen", "Bottom of Screen"});
        osdPositionCombo.setPreferredSize(new Dimension(130, 26));
        osdRow.add(osdPositionCombo);

        durationSpinner = new JSpinner(new SpinnerNumberModel(1800, 500, 5000, 100));
        durationSpinner.setPreferredSize(new Dimension(65, 26));
        durationSpinner.setToolTipText("Duration in milliseconds");
        osdRow.add(durationSpinner);

        rightCol.add(osdRow);
        rightCol.add(Box.createVerticalStrut(8));

        startupCheckBox = new JCheckBox("Start with Windows (System Tray)");
        startupCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rightCol.add(startupCheckBox);

        prefCard.add(rightCol);
        centerPanel.add(prefCard);

        root.add(centerPanel, BorderLayout.CENTER);

        // ================= BOTTOM ACTIONS =================
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setOpaque(false);

        JLabel infoLabel = new JLabel("Closing this window keeps Audio Switcher running in the tray.");
        infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        infoLabel.setForeground(new Color(120, 125, 135));
        bottomBar.add(infoLabel, BorderLayout.WEST);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBtns.setOpaque(false);

        JButton minimizeBtn = createStyledButton("Minimize to Tray", false);
        minimizeBtn.setPreferredSize(new Dimension(130, 32));
        minimizeBtn.addActionListener(e -> setVisible(false));
        actionBtns.add(minimizeBtn);

        JButton saveBtn = createStyledButton("Save & Apply", true);
        saveBtn.setPreferredSize(new Dimension(120, 32));
        saveBtn.addActionListener(e -> saveAndApply());
        actionBtns.add(saveBtn);

        bottomBar.add(actionBtns, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, boolean primary) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", primary ? Font.BOLD : Font.PLAIN, 12));
        if (primary) {
            btn.setBackground(new Color(37, 99, 235));
            btn.setForeground(Color.WHITE);
        }
        return btn;
    }

    private JPanel createCardPanel() {
        JPanel card = new JPanel();
        card.setBackground(new Color(30, 33, 39));
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(45, 52, 64), 1, true),
                new EmptyBorder(12, 16, 12, 16)
        ));
        return card;
    }

    private void updateActiveBadge() {
        AudioDevice active = audioManager.getDefaultDevice();
        if (active != null) {
            String shortName = active.getName();
            if (shortName.length() > 30) {
                shortName = shortName.substring(0, 27) + "...";
            }
            activeDeviceBadgeLabel.setText("Active: " + shortName);
        } else {
            activeDeviceBadgeLabel.setText("Active: None");
        }
        refreshTableData();
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

        JDialog dialog = new JDialog(this, "Assign Shortcut: " + device.getName(), true);
        dialog.setSize(420, 190);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel info = new JLabel("<html>Press modifier keys + key (e.g. <b>Ctrl + Alt + 1</b>):</html>");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(info, BorderLayout.NORTH);

        JTextField inputField = new JTextField(16);
        inputField.setHorizontalAlignment(JTextField.CENTER);
        inputField.setFont(new Font("Segoe UI", Font.BOLD, 15));
        inputField.setBackground(new Color(36, 40, 47));
        inputField.setForeground(new Color(96, 165, 250));
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

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton okBtn = createStyledButton("Save Shortcut", true);
        okBtn.addActionListener(ev -> {
            if (resultCode[0] != 0 && !resultDisplay[0].isEmpty()) {
                workingDeviceHotkeys.put(device.getId(), new AppConfig.DeviceHotkey(resultMods[0], resultCode[0], resultDisplay[0]));
                tableModel.setValueAt(resultDisplay[0], selectedRow, 2);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Please press a valid combination (e.g. Ctrl + Alt + 1).", "Incomplete Shortcut", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton cancelBtn = createStyledButton("Cancel", false);
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

    private void autoAssignNumberShortcuts() {
        int[] numKeys = {
                KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
                KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6,
                KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9
        };
        List<AudioDevice> devices = audioManager.getDevices();
        workingDeviceHotkeys.clear();

        for (int i = 0; i < devices.size() && i < numKeys.length; i++) {
            AudioDevice d = devices.get(i);
            int number = i + 1;
            workingDeviceHotkeys.put(d.getId(), new AppConfig.DeviceHotkey(
                    3, // Ctrl + Alt
                    numKeys[i],
                    "Ctrl + Alt + " + number
            ));
        }
        refreshTableData();
    }

    private void loadDataFromConfig() {
        AppConfig config = configManager.getConfig();

        selectedGlobalModifiers = config.getHotkeyModifiers();
        selectedGlobalKeyCode = config.getHotkeyKeyCode();
        selectedGlobalKeyDisplay = config.getHotkeyDisplayString();
        globalHotkeyField.setText(selectedGlobalKeyDisplay);

        workingDeviceHotkeys.clear();
        workingDeviceHotkeys.putAll(config.getDeviceHotkeys());

        chimeCheckBox.setSelected(config.isPlayChimeOnSwitch());
        int vol = config.getChimeVolume();
        volumeSlider.setValue(vol);
        volumeValueLabel.setText(vol + "%");
        SoundNotifier.setVolume(vol / 100.0f);

        osdCheckBox.setSelected(config.isShowOsd());
        durationSpinner.setValue(config.getOsdDurationMs());
        startupCheckBox.setSelected(StartupManager.isStartupEnabled());

        String pos = config.getOsdPosition();
        if ("CENTER".equalsIgnoreCase(pos)) {
            osdPositionCombo.setSelectedIndex(1);
        } else if ("BOTTOM".equalsIgnoreCase(pos)) {
            osdPositionCombo.setSelectedIndex(2);
        } else {
            osdPositionCombo.setSelectedIndex(0);
        }

        updateActiveBadge();
    }

    public void refreshTableData() {
        audioManager.refreshDevices();
        tableModel.setRowCount(0);

        List<AudioDevice> devices = audioManager.getDevices();
        for (AudioDevice device : devices) {
            String status = device.isDefault() ? "Active" : "Switch";
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

        // 3. Save global cycle hotkey
        config.setHotkeyModifiers(selectedGlobalModifiers);
        config.setHotkeyKeyCode(selectedGlobalKeyCode);
        config.setHotkeyDisplayString(selectedGlobalKeyDisplay);

        // 4. Save feedback settings & volume
        config.setPlayChimeOnSwitch(chimeCheckBox.isSelected());
        config.setChimeVolume(volumeSlider.getValue());
        SoundNotifier.setVolume(volumeSlider.getValue() / 100.0f);

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

        JOptionPane.showMessageDialog(this, "Settings and shortcuts applied successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }
}
