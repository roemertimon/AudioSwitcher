<div align="center">

# Audio Switcher

**Fast, distraction-free audio playback switching on Windows with global shortcuts.**

[![Windows build](https://github.com/roemertimon/audio-switcher/actions/workflows/build.yml/badge.svg)](https://github.com/roemertimon/audio-switcher/actions/workflows/build.yml)
![Java 17+](https://img.shields.io/badge/Java-17+-orange)
![Platform: Windows](https://img.shields.io/badge/platform-Windows-0078D6)
[![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

</div>

**Audio Switcher** is a Windows desktop application for switching audio playback devices instantly using system-wide keyboard shortcuts. Cycle through your enabled outputs with a single hotkey, jump straight to your headphones or speakers with dedicated number shortcuts, and get clear feedback through an acoustic chime and an on-screen display banner.

> The application runs quietly in the Windows notification area (system tray) without background console windows, administrator privileges, or external helper executables.

## Features

- **Sequential cycling**: Tap a global shortcut (`Ctrl + Alt + S` by default) to cycle through only the devices you want.
- **Direct numbering**: Every detected output gets a numbered direct shortcut (`Ctrl + Alt + 1` through `Ctrl + Alt + 9`) for immediate one-touch switching from any game or application.
- **Acoustic chime**: Plays a soft, dual-harmonic bell tone directly through the newly activated device with an adjustable volume slider.
- **On-Screen Display (OSD)**: Shows a floating overlay with adaptive vector icons for headphones and speakers, configurable screen position (top, center, bottom), and adjustable duration.
- **Device filtering**: Uncheck any virtual, disabled, or unused endpoints to exclude them from the cycling loop.
- **Native Windows integration**: Communicates directly with Windows CoreAudio COM interfaces (`IMMDeviceEnumerator`, `IPolicyConfig`) via JNA.
- **Tray integration**: Right-click the system tray icon to switch devices with a single click, open settings, or exit cleanly.
- **Single-instance protection**: If launched while already running, Audio Switcher silently brings the existing window to the foreground instead of creating duplicate processes.
- **Windows auto-start**: Optionally launches minimized to the tray when logging into Windows.

## Screenshots

<p align="center">
  <img src="docs/screenshots/dashboard.png" alt="Audio Switcher settings dashboard" width="90%">
  <br><sub><b>Settings dashboard</b><br>Device table with cycle checkboxes, direct shortcut pills, and audio/display preferences.</sub>
</p>

## Getting started

### Requirements

- Windows 10 or Windows 11 (64-bit)
- [Java Runtime Environment (JRE) or JDK 17+](https://adoptium.net/)

### Download

To run Audio Switcher directly without building from source, download `audio-switcher-1.0.0-all.jar` from the [Releases](../../releases) page. It is a standalone fat JAR containing all dependencies and can be launched by double-clicking it or running:

```powershell
javaw -jar audio-switcher-1.0.0-all.jar
```

Every push is built and verified by GitHub Actions, which also produces a ready-to-run JAR as a workflow artifact.

### Build from source

Clone the repository and run the application from its root directory using the Gradle wrapper:

```powershell
git clone https://github.com/roemertimon/audio-switcher.git
cd audio-switcher
.\gradlew.bat run
```

To build a standalone executable fat JAR:

```powershell
.\gradlew.bat fatJar
```

The output file is generated at `build/libs/audio-switcher-1.0.0-all.jar` and includes all runtime libraries.

Run the automated test suite with:

```powershell
.\gradlew.bat test
```

## Using Audio Switcher

### Default keyboard shortcuts

| Action | Default shortcut | Notes |
| --- | --- | --- |
| Cycle to next playback device | `Ctrl + Alt + S` | Advances sequentially through all checked devices in the cycle list |
| Switch directly to Device 1 | `Ctrl + Alt + 1` | Automatically assigned to the first detected output |
| Switch directly to Device 2 | `Ctrl + Alt + 2` | Automatically assigned to the second detected output |
| Switch directly to Device 3..9 | `Ctrl + Alt + 3..9` | Sequentially indexed for subsequent outputs |
| Switch from system tray | Right-click tray icon → click device | Instant 1-click manual switch |
| Restore default 1..N numbers | Click **Reset Numbers (1..N)** | Re-assigns clean sequential numbers across all devices |

### Customizing shortcuts

- **Reassigning a device shortcut**: Double-click any device's shortcut pill in the table (or select the row and click **Assign Shortcut...**). Press your desired key combination in the dialog and click **Save Shortcut**.
- **Clearing a shortcut**: Select a device and click **Clear** to remove its direct shortcut without removing the device from cycling.
- **Changing the cycle hotkey**: Click into the **Global Cycle Shortcut** field and press your preferred combination, or click **Default** to restore `Ctrl + Alt + S`.

### Feedback and preferences

- **Audio Notification**: Tick **Play acoustic chime on switch** to hear a pleasant bell chime right on the new endpoint. Move the **Volume** slider to set the level (default is 35%), and click **Test** to preview it without changing devices.
- **On-Screen Display (OSD)**: Tick **Show floating banner** to show a modern rounded overlay with the device name and icon whenever a switch occurs. Choose between **Top of Screen**, **Center of Screen**, or **Bottom of Screen**, and set the display duration between 1.0 and 3.0 seconds.
- **Windows Auto-Start**: Tick **Start Audio Switcher automatically with Windows** and click **Save & Apply**. Audio Switcher registers a run entry under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` and boots quietly into the system tray.

## Configuration storage

Settings are saved locally as human-readable JSON at:

```text
%APPDATA%\JavaAudioSwitcher\config.json
```

The configuration preserves custom shortcuts, cycle inclusion flags, notification volume, OSD placement, and startup preferences across reboots.

## Project structure

```text
audio-switcher/
├── .github/workflows/           GitHub Actions build and test CI workflow
├── docs/screenshots/           Application screenshots
├── src/main/java/com/audioswitcher/
│   ├── audio/
│   │   ├── AudioDeviceManager.java   Device enumeration, state tracking, and cycling
│   │   ├── SoundNotifier.java        Acoustic chime synthesizer and playback
│   │   └── WindowsCoreAudio.java     JNA COM bindings to CoreAudio & IPolicyConfig
│   ├── config/
│   │   ├── AppConfig.java            Configuration model and defaults
│   │   └── ConfigManager.java        JSON config persistence in %APPDATA%
│   ├── hotkey/
│   │   └── HotkeyManager.java        Win32 RegisterHotKey message loop
│   ├── model/
│   │   └── AudioDevice.java          Audio endpoint model
│   ├── startup/
│   │   └── StartupManager.java       Windows Registry auto-start manager
│   ├── ui/
│   │   ├── OsdOverlay.java           Floating on-screen display banner
│   │   ├── SettingsFrame.java        Main dashboard and preferences window
│   │   └── TrayManager.java          System tray icon and context menu
│   └── AudioSwitcherApp.java         Application entry point and lifecycle
├── src/test/java/               Unit tests for CoreAudio device enumeration
├── build.gradle                 Gradle build script and dependencies
├── settings.gradle              Gradle project settings
└── gradlew.bat                  Gradle wrapper executable
```

## Built with

- Java 17+ and Swing with [FlatLaf](https://www.formdev.com/flatlaf/) for the dark interface
- [JNA (Java Native Access)](https://github.com/java-native-access/jna) for Win32 API calls (`RegisterHotKey`, Windows Registry, Named Mutex) and CoreAudio COM interfaces (`IMMDeviceEnumerator`, `IPolicyConfig`)
- [Gson](https://github.com/google/gson) for configuration persistence
- [JUnit 5](https://junit.org/junit5/) for the test suite
- Gradle for build automation

## License

Audio Switcher is released under the [MIT License](LICENSE). You may freely use, copy, modify, merge, publish, distribute, sublicense, and sell it, including in your own projects. The only condition is that the copyright notice and permission notice from [LICENSE](LICENSE) are included in all copies or substantial portions of the software. See [LICENSE](LICENSE) for the full text.
