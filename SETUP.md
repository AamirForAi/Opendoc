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
```

The crash-reporting values (`AI`, `AK`, `AU`) are committed in `gradle.properties`, so no
extra setup is needed. To send crash reports to a different backend for local testing,
override them in `~/.gradle/gradle.properties` or pass `-PAI=... -PAK=... -PAU=...`.

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

By default the script builds PDFium from source, which needs depot_tools plus gn and ninja and takes a while. For a faster setup, pass `--pdfium prebuilt` to download the prebuilt PDFium instead. Add `--abi <abi>` (for example `--abi arm64-v8a`) to build a single architecture rather than all four.

The script downloads/builds the native PDF dependencies and places the resulting `.so` files under:

```text
PdfiumAndroid/src/main/jni/lib
```

## 5. Run it with Android Studio
At this point Android Studio should be able to run it with no issues.

## 6. Build the Debug APK

From the project root:

```bash
./gradlew :app:assembleDebug
```

The APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 7. Build the Release APKs

From the project root:

```bash
./gradlew :app:assembleRelease
```

This produces one APK per architecture plus a universal one under:

```text
app/build/outputs/apk/release/
```

The release APKs are unsigned. Sign each APK you distribute with your release keystore:

```bash
apksigner sign --ks your-keystore.jks --out mj-pdf.apk app-arm64-v8a-release-unsigned.apk
```

`apksigner` is in the SDK's `build-tools` directory.
