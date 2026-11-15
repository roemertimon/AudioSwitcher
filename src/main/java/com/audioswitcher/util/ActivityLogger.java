package com.audioswitcher.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight, thread-safe activity logger that records device switch events,
 * hotkey triggers, and suppressed shortcuts into %APPDATA%\JavaAudioSwitcher\switcher.log.
 */
public class ActivityLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static File logFile = null;

    static {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                appData = System.getProperty("user.home");
            }
            File dir = new File(appData, "JavaAudioSwitcher");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            logFile = new File(dir, "switcher.log");
        } catch (Exception ignored) {
        }
    }

    public static synchronized void log(String message) {
        String line = "[" + LocalDateTime.now().format(FORMATTER) + "] " + message;
        System.out.println(line);
        if (logFile != null) {
            try {
                if (!logFile.getParentFile().exists()) {
                    logFile.getParentFile().mkdirs();
                }
                try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, StandardCharsets.UTF_8, true))) {
                    pw.println(line);
                    pw.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
