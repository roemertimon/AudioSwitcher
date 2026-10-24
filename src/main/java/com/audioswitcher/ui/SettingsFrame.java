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
import java.awt.image.BufferedImage;
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

    // Setting controls
    private JCheckBox chimeCheckBox;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JCheckBox osdCheckBox;
    private JComboBox<String> osdPositionCombo;
    private JComboBox<String> osdDurationCombo;
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
        setIconImage(createAppIcon());
        setSize(780, 745);
        setResizable(false); // Fixed, perfectly proportioned window (no accidental stretching or distortion)
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        initUi();
        loadDataFromConfig();

        audioManager.addDeviceSwitchedListener(dev -> SwingUtilities.invokeLater(this::updateActiveBadge));
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));
        root.setBackground(new Color(24, 25, 29));
        setContentPane(root);

        // ================= 1. HEADER BANNER =================
        JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
        headerPanel.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel titleLabel = new JLabel("Audio Switcher");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(245, 247, 250));
        titleBox.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Manage playback devices and system-wide keyboard shortcuts");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(156, 163, 175));
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        headerPanel.add(titleBox, BorderLayout.WEST);

        // Active output badge (spacious, never cut off)
        JPanel badgeCard = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        badgeCard.setBackground(new Color(33, 37, 44));
        badgeCard.setBorder(new CompoundBorder(
                new LineBorder(new Color(55, 65, 81), 1, true),
                new EmptyBorder(4, 14, 4, 14)
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

        // ================= 2. MAIN BODY (VERTICAL FLOW) =================
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setOpaque(false);

        // ---------------- SECTION A: PLAYBACK DEVICES TABLE ----------------
        JPanel devicesCard = createCardPanel();
        devicesCard.setLayout(new BorderLayout(0, 8));

        JPanel tableHeaderBox = new JPanel(new BorderLayout());
        tableHeaderBox.setOpaque(false);
        JLabel tableTitle = createSectionTitle("PLAYBACK DEVICES");
        JLabel tableTip = new JLabel("Double-click a shortcut pill to reassign keys");
        tableTip.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tableTip.setForeground(new Color(140, 148, 160));
        tableHeaderBox.add(tableTitle, BorderLayout.WEST);
        tableHeaderBox.add(tableTip, BorderLayout.EAST);
        devicesCard.add(tableHeaderBox, BorderLayout.NORTH);

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
        devicesTable.setRowHeight(34);
        devicesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        devicesTable.setShowVerticalLines(false);
        devicesTable.setGridColor(new Color(45, 50, 58));
        devicesTable.setBackground(new Color(28, 31, 36));
        devicesTable.setForeground(Color.WHITE);
        devicesTable.setSelectionBackground(new Color(49, 65, 95));

        // Center header
        ((DefaultTableCellRenderer) devicesTable.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);

        // Exact column widths: Device Name gets maximum comfortable width
        devicesTable.getColumnModel().getColumn(0).setMinWidth(55);
        devicesTable.getColumnModel().getColumn(0).setMaxWidth(60);
        devicesTable.getColumnModel().getColumn(0).setPreferredWidth(55);

        devicesTable.getColumnModel().getColumn(1).setPreferredWidth(420);

        devicesTable.getColumnModel().getColumn(2).setMinWidth(140);
        devicesTable.getColumnModel().getColumn(2).setMaxWidth(160);
        devicesTable.getColumnModel().getColumn(2).setPreferredWidth(150);

        devicesTable.getColumnModel().getColumn(3).setMinWidth(85);
        devicesTable.getColumnModel().getColumn(3).setMaxWidth(100);
        devicesTable.getColumnModel().getColumn(3).setPreferredWidth(90);

        // Custom centered checkbox
        devicesTable.getColumnModel().getColumn(0).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JCheckBox cb = new JCheckBox();
            cb.setSelected(value != null && (Boolean) value);
            cb.setHorizontalAlignment(SwingConstants.CENTER);
            cb.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return cb;
        });

        // Custom shortcut pill renderer
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

        // Custom action/status renderer
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
        scrollPane.setPreferredSize(new Dimension(740, 165));
        scrollPane.setBorder(new LineBorder(new Color(45, 50, 58), 1));
        devicesCard.add(scrollPane, BorderLayout.CENTER);

        // Buttons under table
        JPanel devicesBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        devicesBtnBar.setOpaque(false);

        JButton setShortcutBtn = createStyledButton("Assign Shortcut...", false);
        setShortcutBtn.addActionListener(e -> promptSetDeviceShortcut());

        JButton clearShortcutBtn = createStyledButton("Clear", false);
        clearShortcutBtn.addActionListener(e -> clearSelectedDeviceShortcut());

        JButton autoAssignBtn = createStyledButton("Reset Numbers (1..N)", false);
        autoAssignBtn.setToolTipText("Auto-assign Ctrl + Alt + 1, 2, 3... to all devices");
        autoAssignBtn.addActionListener(e -> autoAssignNumberShortcuts());

        JButton refreshBtn = createStyledButton("Refresh", false);
        refreshBtn.addActionListener(e -> refreshTableData());

        devicesBtnBar.add(setShortcutBtn);
        devicesBtnBar.add(clearShortcutBtn);
        devicesBtnBar.add(autoAssignBtn);
        devicesBtnBar.add(refreshBtn);
        devicesCard.add(devicesBtnBar, BorderLayout.SOUTH);

        bodyPanel.add(devicesCard);
        bodyPanel.add(Box.createVerticalStrut(10));

        // ---------------- SECTION B: GLOBAL CYCLE SHORTCUT ----------------
        JPanel cycleCard = createCardPanel();
        cycleCard.setLayout(new BorderLayout(14, 0));

        JPanel cycleLeft = new JPanel();
        cycleLeft.setLayout(new BoxLayout(cycleLeft, BoxLayout.Y_AXIS));
        cycleLeft.setOpaque(false);

        JLabel cycleTitle = new JLabel("Global Cycle Shortcut");
        cycleTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cycleTitle.setForeground(new Color(240, 242, 245));
        cycleLeft.add(cycleTitle);

        JLabel cycleSub = new JLabel("Cycles through all devices marked with a checkmark in the table above");
        cycleSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cycleSub.setForeground(new Color(156, 163, 175));
        cycleLeft.add(Box.createVerticalStrut(2));
        cycleLeft.add(cycleSub);
        cycleCard.add(cycleLeft, BorderLayout.WEST);

        JPanel cycleRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        cycleRight.setOpaque(false);

        globalHotkeyField = new JTextField(15);
        globalHotkeyField.setPreferredSize(new Dimension(160, 32));
        globalHotkeyField.setMinimumSize(new Dimension(160, 32));
        globalHotkeyField.setEditable(false);
        globalHotkeyField.setHorizontalAlignment(JTextField.CENTER);
        globalHotkeyField.setFont(new Font("Segoe UI", Font.BOLD, 13));
        globalHotkeyField.setBackground(new Color(36, 40, 47));
        globalHotkeyField.setForeground(new Color(96, 165, 250));
        globalHotkeyField.setBorder(new CompoundBorder(new LineBorder(new Color(55, 65, 81), 1), new EmptyBorder(4, 8, 4, 8)));
        globalHotkeyField.setToolTipText("Click here and press keys to reassign");

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
        cycleRight.add(globalHotkeyField);

        JButton resetHotkeyBtn = createStyledButton("Default", false);
        resetHotkeyBtn.setToolTipText("Reset to Ctrl + Alt + S");
        resetHotkeyBtn.setPreferredSize(new Dimension(80, 32));
        resetHotkeyBtn.addActionListener(e -> {
            selectedGlobalModifiers = 3;
            selectedGlobalKeyCode = KeyEvent.VK_S;
            selectedGlobalKeyDisplay = "Ctrl + Alt + S";
            globalHotkeyField.setText(selectedGlobalKeyDisplay);
        });
        cycleRight.add(resetHotkeyBtn);

        cycleCard.add(cycleRight, BorderLayout.EAST);
        bodyPanel.add(cycleCard);
        bodyPanel.add(Box.createVerticalStrut(10));

        // ---------------- SECTION C: PREFERENCES & FEEDBACK (ZERO OVERLAP 2-LINE SECTIONS) ----------------
        JPanel prefCard = createCardPanel();
        prefCard.setLayout(new BoxLayout(prefCard, BoxLayout.Y_AXIS));

        JLabel prefSectionTitle = createSectionTitle("PREFERENCES & FEEDBACK");
        prefCard.add(prefSectionTitle);
        prefCard.add(Box.createVerticalStrut(10));

        // 1. Audio Notification
        JLabel audioTitle = new JLabel("Audio Notification");
        audioTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        audioTitle.setForeground(new Color(230, 235, 245));
        prefCard.add(audioTitle);
        prefCard.add(Box.createVerticalStrut(4));

        JPanel audioRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        audioRow.setOpaque(false);

        chimeCheckBox = new JCheckBox("Play acoustic chime on switch");
        chimeCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        audioRow.add(chimeCheckBox);

        JLabel volLabel = new JLabel("Volume:");
        volLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        volLabel.setForeground(new Color(156, 163, 175));
        audioRow.add(volLabel);

        volumeSlider = new JSlider(0, 100, 35);
        volumeSlider.setPreferredSize(new Dimension(120, 24));
        volumeSlider.setOpaque(false);
        audioRow.add(volumeSlider);

        volumeValueLabel = new JLabel("35%");
        volumeValueLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        volumeValueLabel.setPreferredSize(new Dimension(36, 20));
        audioRow.add(volumeValueLabel);

        JButton testSoundBtn = createStyledButton("Test", false);
        testSoundBtn.setPreferredSize(new Dimension(58, 26));
        testSoundBtn.addActionListener(e -> {
            float vol = volumeSlider.getValue() / 100.0f;
            SoundNotifier.playSwitchChime(vol);
        });
        audioRow.add(testSoundBtn);

        volumeSlider.addChangeListener(e -> {
            int val = volumeSlider.getValue();
            volumeValueLabel.setText(val + "%");
            SoundNotifier.setVolume(val / 100.0f);
        });

        prefCard.add(audioRow);
        prefCard.add(Box.createVerticalStrut(10));

        JSeparator sep1 = new JSeparator();
        sep1.setForeground(new Color(45, 50, 60));
        prefCard.add(sep1);
        prefCard.add(Box.createVerticalStrut(10));

        // 2. On-Screen Display (OSD)
        JLabel osdTitle = new JLabel("On-Screen Display (OSD)");
        osdTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        osdTitle.setForeground(new Color(230, 235, 245));
        prefCard.add(osdTitle);
        prefCard.add(Box.createVerticalStrut(4));

        JPanel osdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        osdRow.setOpaque(false);

        osdCheckBox = new JCheckBox("Show floating banner");
        osdCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        osdRow.add(osdCheckBox);

        JLabel posLabel = new JLabel("Position:");
        posLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        posLabel.setForeground(new Color(156, 163, 175));
        osdRow.add(posLabel);

        osdPositionCombo = new JComboBox<>(new String[]{"Top of Screen", "Center of Screen", "Bottom of Screen"});
        osdPositionCombo.setPreferredSize(new Dimension(140, 26));
        osdRow.add(osdPositionCombo);

        JLabel durLabel = new JLabel("Duration:");
        durLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        durLabel.setForeground(new Color(156, 163, 175));
        osdRow.add(durLabel);

        osdDurationCombo = new JComboBox<>(new String[]{"1.0 s", "1.5 s", "1.8 s", "2.5 s", "3.0 s"});
        osdDurationCombo.setPreferredSize(new Dimension(85, 26));
        osdRow.add(osdDurationCombo);

        prefCard.add(osdRow);
        prefCard.add(Box.createVerticalStrut(10));

        JSeparator sep2 = new JSeparator();
        sep2.setForeground(new Color(45, 50, 60));
        prefCard.add(sep2);
        prefCard.add(Box.createVerticalStrut(10));

        // 3. Windows Auto-Start
        JLabel startupTitle = new JLabel("Windows Auto-Start");
        startupTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        startupTitle.setForeground(new Color(230, 235, 245));
        prefCard.add(startupTitle);
        prefCard.add(Box.createVerticalStrut(4));

        JPanel startupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        startupRow.setOpaque(false);

        startupCheckBox = new JCheckBox("Start Audio Switcher automatically with Windows (in background tray)");
        startupCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        startupRow.add(startupCheckBox);

        prefCard.add(startupRow);
        bodyPanel.add(prefCard);

        root.add(bodyPanel, BorderLayout.CENTER);

        // ================= 3. BOTTOM ACTIONS BAR =================
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setOpaque(false);

        JLabel infoLabel = new JLabel("Audio Switcher stays running in the system tray when closing this window.");
        infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        infoLabel.setForeground(new Color(130, 135, 145));
        bottomBar.add(infoLabel, BorderLayout.WEST);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionBtns.setOpaque(false);

        JButton minimizeBtn = createStyledButton("Minimize to Tray", false);
        minimizeBtn.setPreferredSize(new Dimension(135, 34));
        minimizeBtn.addActionListener(e -> setVisible(false));
        actionBtns.add(minimizeBtn);

        JButton saveBtn = createStyledButton("Save & Apply", true);
        saveBtn.setPreferredSize(new Dimension(130, 34));
        saveBtn.addActionListener(e -> saveAndApply());
        actionBtns.add(saveBtn);

        bottomBar.add(actionBtns, BorderLayout.EAST);
        root.add(bottomBar, BorderLayout.SOUTH);
    }

    private JLabel createSectionTitle(String title) {
        JLabel l = new JLabel(title);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(new Color(140, 148, 160));
        return l;
    }

    private Image createAppIcon() {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(37, 99, 235));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(Color.WHITE);
        int[] x = {8, 13, 20, 20, 13, 8};
        int[] y = {13, 13, 7, 25, 19, 19};
        g2.fillPolygon(x, y, 6);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(19, 10, 8, 12, -45, 90);
        g2.dispose();
        return img;
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
            if (shortName.length() > 34) {
                shortName = shortName.substring(0, 31) + "...";
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

        int duration = config.getOsdDurationMs();
        if (duration <= 1100) osdDurationCombo.setSelectedIndex(0); // 1.0s
        else if (duration <= 1600) osdDurationCombo.setSelectedIndex(1); // 1.5s
        else if (duration <= 2000) osdDurationCombo.setSelectedIndex(2); // 1.8s
        else if (duration <= 2600) osdDurationCombo.setSelectedIndex(3); // 2.5s
        else osdDurationCombo.setSelectedIndex(4); // 3.0s

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

        // Parse duration combo
        int durIdx = osdDurationCombo.getSelectedIndex();
        int[] durations = {1000, 1500, 1800, 2500, 3000};
        config.setOsdDurationMs(durations[Math.max(0, Math.min(durations.length - 1, durIdx))]);

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
