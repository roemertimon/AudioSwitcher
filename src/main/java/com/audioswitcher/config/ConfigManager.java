package com.audioswitcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private static final String APP_DIR_NAME = "JavaAudioSwitcher";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private AppConfig config;

    public ConfigManager() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home", ".");
        }
        File baseDir = new File(appData, APP_DIR_NAME);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        this.configFile = new File(baseDir, CONFIG_FILE_NAME);
        loadConfig();
    }

    public void loadConfig() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, AppConfig.class);
            } catch (Exception e) {
                System.err.println("Failed to load config, initializing default: " + e.getMessage());
            }
        }
        if (config == null) {
            config = new AppConfig();
        }
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public AppConfig getConfig() {
        return config;
    }
}
