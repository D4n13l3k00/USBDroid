<h1 align="center">💿 USBDroid</h1>

<p align="center">
  <a href="https://github.com/D4n13l3k00/USBDroid/actions/workflows/check.yml"><img src="https://github.com/D4n13l3k00/USBDroid/actions/workflows/check.yml/badge.svg" alt="CI: Build and check"></a>
  <a href="https://github.com/D4n13l3k00/USBDroid/releases"><img src="https://img.shields.io/badge/version-v1.0.0-blue" alt="Версия v1.0.0"></a>
  <a href="https://github.com/D4n13l3k00/USBDroid/releases"><img src="https://img.shields.io/github/downloads/D4n13l3k00/USBDroid/total" alt="Скачивания релизов"></a>
  <img src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/root-required-orange" alt="Требуется root">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue" alt="GPL-3.0-or-later"></a>
</p>

<p align="center">
Открытая альтернатива DriveDroid для Android. Позволяет использовать телефон как USB-накопитель или CD-ROM, подключая к компьютеру образы IMG и ISO.
</p>

<p align="center">Русский · <a href="README.en.md">English</a></p>

## 💾 Возможности

- Подключение образов только для чтения, с записью или как CD-ROM. Поддержка нескольких образов при наличии свободных LUN;
- Библиотека образов с импортом и экспортом. Можно хранить файлы в папке приложения или подключать существующие внешние файлы;
- Создание пустых и отформатированных образов: FAT16, FAT32, exFAT, ext4, NTFS и Btrfs. Изменение размера файлов и вычисление контрольных сумм;
- Загрузки из каталогов и по прямым ссылкам. Встроенный isohybrid для подготовки подходящих ISO к USB-загрузке;
- OTA-обновления через GitHub Releases.

## 🎨 Интерфейс

Jetpack Compose и Material 3. Динамические цвета системы, светлая и тёмная темы, AMOLED-режим. Мультиязычность.

## 📋 Требования

Android 8 или новее, root с разрешением для USBDroid и поддержка USB Mass Storage в ядре телефона. Доступные режимы подключения зависят от устройства.

## 🔧 Сборка

```powershell
.\gradlew.bat assembleRelease
```

```bash
./gradlew assembleRelease
```

## 📜 Лицензия

Исходный код USBDroid распространяется под [GPL-3.0-or-later](LICENSE).

Встроенные компоненты сохраняют свои лицензии, перечисленные в [THIRD_PARTY.md](THIRD_PARTY.md).
