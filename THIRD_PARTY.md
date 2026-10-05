# Сторонние компоненты

- Syslinux isohybrid: GPL-2.0-or-later. Исходники в `native/src`, лицензия в `native/COPYING`. MBR сохраняет условия MIT из заголовков исходников.
- Форматтеры и их библиотеки: исходные архивы и рецепты в `native/filesystems`, лицензии и provenance в `app/src/main/assets/licenses/filesystems`.
- AndroidX, Kotlin/coroutines и OkHttp: Apache-2.0.

Тексты лицензий включены в APK. Лицензия USBDroid не заменяет условия сторонних компонентов.

## uMTP-Responder

[uMTP-Responder](https://github.com/viveris/uMTP-Responder), GPL-3.0-or-later. Source and license: `native/mtp/`; pinned revision: `UPSTREAM_COMMIT`. Android adapter disables POSIX message-queue IPC and wakes stopped USB threads with SIGUSR1. Built as a static executable for ARM64, ARMv7 and x86_64.
