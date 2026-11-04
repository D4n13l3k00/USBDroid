# USBDroid 1.3.0

## Русский

### Новое

- **Создание образов с MBR и GPT.** Можно выбрать образ без таблицы разделов или с одним разделом MBR/GPT. Выбор доступен и при создании образа из папки.
- **Экран информации об образе:** таблица разделов, файловые системы, метки томов, размеры разделов и доступное свободное место.
- **Выбор раздела** при открытии образа на телефоне.
- **Проверка и исправление файловых систем.** Исправление выполняется на копии с сохранением резервной копии исходного образа. Резервную копию можно экспортировать.

### Исправления USB

- Исправлена ошибка **`Device or resource busy`** при подключении образов на устройствах, где системный USB Gadget HAL повторно занимает контроллер. Режим совместимости теперь освобождает его через AIDL HAL и восстанавливает исходные USB-функции после извлечения.
- **Режим совместимости добавлен в первоначальную настройку** — на шаги выбора USB-системы и подключения тестового образа.
- При обнаружении занятого контроллера приложение объясняет причину и предлагает включить совместимость. Повторное подключение выполняется пользователем.
- Уточнены статусы USB и MTP: готовность к подключению, фактическое соединение и приостановка USB.
- Исправлена очистка MTP-конфигурации при завершении доступа к папке.

### Ограничения

- Для NTFS доступна базовая проверка; полная проверка выполняется через `chkdsk` в Windows.
- Для Btrfs доступна проверка без автоматического исправления.
- Поддержка USB Mass Storage и загрузки BIOS/UEFI зависит от ядра телефона и компьютера.

## English

### New

- **Create images with MBR or GPT.** Choose an image without a partition table or with a single MBR/GPT partition. This option is also available when creating an image from a folder.
- **Image information screen:** partition table, file systems, volume labels, partition sizes, and available free space.
- **Partition selection** when opening an image on the phone.
- **File system checks and repairs.** Repairs run on a copy while preserving a backup of the original image. The backup can be exported.

### USB fixes

- Fixed **`Device or resource busy`** errors when connecting images on devices where the system USB Gadget HAL reclaims the controller. Compatibility mode now releases it through the AIDL HAL and restores the original USB functions after ejecting.
- **Compatibility mode is now available during first-run setup**, on the USB-system selection and test-image connection steps.
- When the controller is occupied, the app explains the cause and offers to enable compatibility mode. Reconnecting remains a user action.
- Clarified USB and MTP statuses: ready to connect, actually connected, and USB suspended.
- Fixed MTP configuration cleanup when folder sharing ends.

### Limitations

- NTFS supports basic checks; use `chkdsk` in Windows for a full check.
- Btrfs supports checks without automatic repair.
- USB Mass Storage and BIOS/UEFI boot support depend on the phone kernel and the computer.
