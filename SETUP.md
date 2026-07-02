# Fresh Linux Setup

This guide describes how to build and run MJ PDF from a fresh Linux install.

## 1. Install System Packages

Install Java 17, Python, CMake, Git, and basic build tools.

Arch Linux:

```bash
sudo pacman -S jdk17-openjdk python python-requests git base-devel cmake
```

Debian/Ubuntu:

```bash
sudo apt install openjdk-17-jdk python3 python3-requests git build-essential cmake
```

## 2. Install Android SDK Components

Install Android Studio, then open:

`More Actions` > `SDK Manager`

Install these SDK components:

- Android SDK Platform `API 34`
- Android SDK Build-Tools
- Android SDK Platform-Tools
- Android SDK Command-line Tools
- NDK (Side by side)
- CMake

The default Android Studio SDK path on Linux is usually:

```text
/home/user/Android/Sdk
```

Use your actual username/path if different.

## 3. Configure `local.properties`

Create `local.properties` in the project root if it does not already exist:

```properties
sdk.dir=/home/user/Android/Sdk
AI=
AK=
AU=
```

`AI`, `AK`, and `AU` are crash-reporting values. Leave them empty for local debug builds.

## 4. Build Native Dependencies

Find the installed NDK version:

```bash
ls /home/user/Android/Sdk/ndk
```

Then run the dependency build script with `ANDROID_NDK` pointing to that NDK directory:

```bash
ANDROID_NDK=/home/user/Android/Sdk/ndk/30.0.14904198 python build_dependencies.py
```

Replace `30.0.14904198` with the NDK version installed on your machine.

The script downloads/builds the native PDF dependencies and places the resulting `.so` files under:

```text
PdfiumAndroid/src/main/jni/lib
```

## 5. Run it with Android Studio
At this point Android Studio should be able to run it with no issues.
