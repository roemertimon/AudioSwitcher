# Audio Switcher 🔊

A fast, lightweight Windows Audio Switcher built in Java and Swing with native CoreAudio integration.

Quickly cycle through your audio playback devices or switch directly to specific devices (e.g., Headphones or Speakers) using global keyboard shortcuts from anywhere—even during fullscreen gaming or inside other applications.

---

## ✨ Features

- **Global Cycle Shortcut**: Switch to the next enabled playback device instantly using `Ctrl + Alt + A` (customizable).
- **Dedicated Device Shortcuts**: Assign specific keyboard shortcuts for individual devices (e.g., `Ctrl + Alt + 1` for Headset, `Ctrl + Alt + 2` for Speakers).
- **Acoustic Audio Chime**: Plays a crisp, pleasant acoustic chime directly through the newly selected device upon switching.
- **On-Screen Display (OSD)**: Floating banner showing the active device name and adaptive icons (Headphones vs. Speakers), with configurable screen position (Top, Center, Bottom) and duration.
- **Device Cycle Filter**: Choose exactly which audio devices to include in the cycle shortcut (ignoring virtual or inactive devices).
- **Native Windows Audio Integration**: High-performance JNA bindings to Windows CoreAudio and `IPolicyConfig::SetDefaultEndpoint`.
- **System Tray Controls**: Quick 1-click manual switching, active device status indicators, and settings access.
- **Hardware Hot-Plug Detection**: Detects newly connected or disconnected audio hardware automatically.
- **Single Instance Guard**: Ensures only one background instance runs at a time.
- **Windows Auto-Start**: Easily toggle starting automatically with Windows via registry.
- **Modern FlatLaf UI**: Clean, dark-mode settings interface.

---

## 🚀 Getting Started

### Prerequisites
* Windows 10 or Windows 11 (64-bit)
* Java JDK 16 or newer (Java 17, 21, or 22 recommended)

### Build and Run

Run directly with Gradle:
```powershell
.\gradlew.bat run
```

Build a standalone executable fat JAR:
```powershell
.\gradlew.bat fatJar
```
The output JAR is generated at:
`build/libs/audio-switcher-1.0.0-all.jar`

Run the JAR anytime with:
```powershell
java -jar build/libs/audio-switcher-1.0.0-all.jar
```

---

## 🛠️ Project Structure

* **`com.audioswitcher.audio`**:
  * `WindowsCoreAudio`: Direct JNA COM vtable bindings to Windows CoreAudio endpoints.
  * `AudioDeviceManager`: Manages audio device state, hotplug detection, and cycling.
  * `SoundNotifier`: Generates and plays the switch notification chime on the active device.
* **`com.audioswitcher.hotkey`**:
  * `HotkeyManager`: Non-polling Win32 message loop managing multiple global shortcuts concurrently.
* **`com.audioswitcher.ui`**:
  * `OsdOverlay`: Floating translucent window displaying device icons and name.
  * `SettingsFrame`: Comprehensive settings window for shortcuts, devices, OSD, and sound preferences.
  * `TrayManager`: System tray menu and status tooltip.
* **`com.audioswitcher.startup`**:
  * `StartupManager`: Windows Registry auto-start manager.
* **`com.audioswitcher.config`**:
  * `ConfigManager` & `AppConfig`: JSON configuration stored in `%APPDATA%\JavaAudioSwitcher\config.json`.
