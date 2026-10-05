<h1 align="center">💿 USBDroid</h1>

<p align="center">
  <a href="https://github.com/D4n13l3k00/USBDroid/actions/workflows/check.yml"><img src="https://github.com/D4n13l3k00/USBDroid/actions/workflows/check.yml/badge.svg" alt="CI: Build and check"></a>
  <a href="https://github.com/D4n13l3k00/USBDroid/releases"><img src="https://img.shields.io/badge/version-v1.1.0-blue" alt="Version v1.1.0"></a>
  <a href="https://github.com/D4n13l3k00/USBDroid/releases"><img src="https://img.shields.io/github/downloads/D4n13l3k00/USBDroid/total" alt="Release downloads"></a>
  <img src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/root-required-orange" alt="Root required">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue" alt="GPL-3.0-or-later"></a>
</p>

<p align="center">
An open-source alternative to DriveDroid for Android. Connect IMG and ISO images to your computer by using your phone as a USB drive or CD-ROM.
</p>

<p align="center"><a href="README.md">Русский</a> · English</p>

## 💾 Features

- Connect images in read-only, writable or CD-ROM mode. Connect multiple images when the kernel provides enough LUNs.
- Import and export disk images. Store files in the app folder or use existing files from other locations.
- Create blank or formatted images: FAT16, FAT32, exFAT, ext4, NTFS and Btrfs. Resize image files and calculate checksums.
- Download images from catalogs or direct URLs. Built-in isohybrid prepares compatible ISO images for USB boot.
- Share a selected folder over MTP or as a temporary USB image. Browse and edit image contents on the phone or through SAF-compatible file managers.
- OTA updates through GitHub Releases.

## 🎨 Interface

Jetpack Compose and Material 3. Dynamic system colors, light and dark themes, an AMOLED mode and multiple languages.

## 📋 Requirements

Android 8 or later, root access granted to USBDroid and USB Mass Storage support in the phone kernel. Available connection modes depend on the device.

## 🔧 Build

```powershell
.\gradlew.bat assembleRelease
```

```bash
./gradlew assembleRelease
```

## 📜 License

USBDroid source code is licensed under [GPL-3.0-or-later](LICENSE).

Bundled components retain their own licenses, listed in [THIRD_PARTY.md](THIRD_PARTY.md).
