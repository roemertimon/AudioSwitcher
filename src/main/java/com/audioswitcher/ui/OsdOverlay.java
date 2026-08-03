package com.audioswitcher.ui;

import com.audioswitcher.model.AudioDevice;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class OsdOverlay extends JWindow {

    private final JLabel titleLabel;
    private final JLabel deviceNameLabel;
    private final Timer hideTimer;
    private static final int WIDTH = 440;
    private static final int HEIGHT = 110;
    private AudioDevice currentDevice;

    public OsdOverlay() {
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setSize(WIDTH, HEIGHT);

        // Custom rounded panel
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Dark modern translucent background
                g2.setColor(new Color(25, 26, 30, 240));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));

                // Subtle glowing border
                g2.setColor(new Color(60, 130, 246, 200));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 24, 24));

                // Draw device-specific icon
                drawDeviceIcon(g2, 26, 28);

                g2.dispose();
            }
        };
        panel.setLayout(null);
        panel.setOpaque(false);

        titleLabel = new JLabel("AUDIO OUTPUT SWITCHED");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLabel.setForeground(new Color(110, 165, 255));
        titleLabel.setBounds(96, 22, 320, 20);
        panel.add(titleLabel);

        deviceNameLabel = new JLabel("Playback Device");
        deviceNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        deviceNameLabel.setForeground(Color.WHITE);
        deviceNameLabel.setBounds(96, 44, 320, 36);
        panel.add(deviceNameLabel);

        setContentPane(panel);
        setBackground(new Color(0, 0, 0, 0));

        hideTimer = new Timer(1800, e -> setVisible(false));
        hideTimer.setRepeats(false);
    }

    private void drawDeviceIcon(Graphics2D g2, int x, int y) {
        String name = currentDevice != null ? currentDevice.getName().toLowerCase() : "";
        boolean isHeadphones = name.contains("head") || name.contains("kopfh") || name.contains("arctis") || name.contains("ear");

        g2.setColor(new Color(96, 165, 250));

        if (isHeadphones) {
            // Draw Headphones
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Headband arc
            g2.drawArc(x + 4, y + 6, 36, 36, 0, 180);
            // Earcups
            g2.fillRoundRect(x + 2, y + 22, 10, 22, 8, 8);
            g2.fillRoundRect(x + 32, y + 22, 10, 22, 8, 8);
        } else {
            // Draw Speaker
            int[] xPoints = {x + 4, x + 14, x + 26, x + 26, x + 14, x + 4};
            int[] yPoints = {y + 16, y + 16, y + 6, y + 44, y + 34, y + 34};
            g2.fillPolygon(xPoints, yPoints, 6);

            // Sound waves
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(x + 22, y + 12, 18, 26, -45, 90);
            g2.drawArc(x + 28, y + 4, 26, 42, -50, 100);
        }
    }

    public void showForDevice(AudioDevice device, int durationMs, String position) {
        SwingUtilities.invokeLater(() -> {
            this.currentDevice = device;
            deviceNameLabel.setText(device.getName());

            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (screen.width - WIDTH) / 2;
            int y;

            if ("CENTER".equalsIgnoreCase(position)) {
                y = (screen.height - HEIGHT) / 2;
            } else if ("BOTTOM".equalsIgnoreCase(position)) {
                y = screen.height - HEIGHT - 100;
            } else { // "TOP" default
                y = 60;
            }

            setLocation(x, y);
            repaint();

            if (!isVisible()) {
                setVisible(true);
            }

            hideTimer.stop();
            hideTimer.setInitialDelay(durationMs > 0 ? durationMs : 1800);
            hideTimer.start();
        });
    }
}
