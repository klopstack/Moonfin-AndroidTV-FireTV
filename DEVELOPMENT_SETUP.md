# Development Environment Setup

This guide will help you set up your local development environment for building Moonfin for Android TV.

## Prerequisites

### 1. Java Development Kit (JDK) 21

The project requires **Java 21** (not Java 25 or other versions).

**Install JDK 21:**

```bash
# Fedora/RHEL
sudo dnf install java-21-openjdk-devel

# Debian/Ubuntu
sudo apt install openjdk-21-jdk

# Arch Linux
sudo pacman -S jdk21-openjdk
```

**Set JAVA_HOME** (add to `~/.bashrc` or `~/.zshrc`):

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

Apply changes:
```bash
source ~/.bashrc
```

**Verify installation:**
```bash
java -version
# Should show: openjdk version "21.x.x"
```

### 2. Android SDK

You need the Android SDK to compile Android applications.

#### Option A: Install Android Studio (Recommended for full IDE support)

Download from: https://developer.android.com/studio

Or via Flathub:
```bash
flatpak install flathub com.google.AndroidStudio
```

**Note:** If using Flatpak, the SDK will be at:
```
~/.var/app/com.google.AndroidStudio/config/Google/AndroidStudio*/sdk
```

#### Option B: Install Command-Line Tools Only (Lightweight)

```bash
# Download and extract command-line tools
cd ~
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Set up environment variables
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Install required SDK components
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Add to your shell profile (`~/.bashrc` or `~/.zshrc`):
```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

### 3. Configure Project

Create `local.properties` in the project root:

```bash
cd /path/to/AndroidTV-FireTV
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

**Note:** This file is gitignored and must be created locally on each machine.

## Building the Project

### Debug Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/moonfin-androidtv-v1.3.1-debug.apk`

### Release Build

Requires `keystore.properties` (see [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md)):

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/moonfin-androidtv-v1.3.1-release.apk`

### Build and Install

Connect an Android TV device via ADB and run:

```bash
# Debug
./gradlew installDebug

# Release
./gradlew installRelease
```

## Running Tests

```bash
# Unit tests
./gradlew test

# Lint checks
./gradlew detekt
```

## Common Issues

### "SDK location not found"

**Solution:** Create `local.properties` with your SDK path:
```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

### "Toolchain installation does not provide the required capabilities"

**Solution:** Ensure you're using Java 21, not Java 25 or another version:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew assembleDebug
```

Add the export to your `~/.bashrc` to make it permanent.

### Gradle daemon issues

**Solution:** Stop and restart Gradle:
```bash
./gradlew --stop
./gradlew assembleDebug
```

### ADB device not found

**Solution:** Enable USB debugging on your Android TV device and connect via USB or network ADB:
```bash
# Check connected devices
adb devices

# Connect via network (if supported)
adb connect <device-ip>:5555
```

## IDE Setup

### Android Studio

1. Open the project: **File → Open** → Select project root directory
2. Android Studio will automatically detect the Gradle configuration
3. Wait for Gradle sync to complete
4. Set JDK to Java 21: **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**

### VS Code (with Copilot)

The project includes `.github/copilot-instructions.md` for GitHub Copilot integration.

Recommended extensions:
- Kotlin Language
- Gradle for Java
- Android iOS Emulator

## Project Structure

```
AndroidTV-FireTV/
├── app/                    # Main Android TV application
├── playback/              # Playback modules
│   ├── core/             # Core playback abstractions
│   ├── jellyfin/         # Jellyfin-specific playback
│   └── media3/           # ExoPlayer & Media3 integration
├── preference/            # Shared preferences module
├── buildSrc/             # Custom Gradle build logic
├── gradle/               # Gradle wrapper and version catalog
├── local.properties      # SDK location (gitignored, create locally)
├── keystore.properties   # Release signing config (gitignored)
└── build.gradle.kts      # Root build configuration
```

## Additional Resources

- **Architecture & Contribution Guide:** See `.github/copilot-instructions.md`
- **Keystore Setup:** See `KEYSTORE_SETUP.md`
- **Jellyfin SDK Docs:** https://github.com/jellyfin/jellyfin-sdk-kotlin
- **Android TV Development:** https://developer.android.com/tv

## Quick Start Checklist

- [ ] Install Java 21
- [ ] Install Android SDK
- [ ] Create `local.properties` with SDK path
- [ ] Set `JAVA_HOME` environment variable
- [ ] Run `./gradlew assembleDebug`
- [ ] Install APK: `adb install app/build/outputs/apk/debug/*.apk`

For questions or issues, refer to the project's GitHub Issues or Discussions.
