package com.audioswitcher.model;

import java.util.Objects;

/**
 * Represents a Windows audio endpoint device (Speakers, Headphones, DAC, etc.).
 */
public class AudioDevice {
    private final String id;
    private final String name;
    private boolean isDefault;
    private boolean isDefaultCommunication;
    private boolean isCycleEnabled;

    public AudioDevice(String id, String name, boolean isDefault, boolean isDefaultCommunication, boolean isCycleEnabled) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
        this.isDefaultCommunication = isDefaultCommunication;
        this.isCycleEnabled = isCycleEnabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isDefaultCommunication() {
        return isDefaultCommunication;
    }

    public void setDefaultCommunication(boolean defaultCommunication) {
        isDefaultCommunication = defaultCommunication;
    }

    public boolean isCycleEnabled() {
        return isCycleEnabled;
    }

    public void setCycleEnabled(boolean cycleEnabled) {
        isCycleEnabled = cycleEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioDevice that = (AudioDevice) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + (isDefault ? " [Default]" : "");
    }
}
