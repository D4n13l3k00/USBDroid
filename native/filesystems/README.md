# Форматирование и проверка

exfatprogs, e2fsprogs, ntfs-3g и btrfs-progs с библиотеками для ARM64, ARMv7 и x86_64. FAT16/FAT32 создаются в Kotlin. Для проверки включены e2fsck, fsck.exfat, ntfsfix и btrfs check. FAT проверяется системной fsck_msdos.

`provenance-*.json` содержит версии, URL пакетов и SHA-256. `sources/` содержит исходные архивы, `recipes/` — закреплённые рецепты и патчи Termux. Commit указан в `termux-commit.txt`.

В упакованных ELF удалены RPATH/RUNPATH: теги DT_RPATH и DT_RUNPATH заменены на DT_DEBUG со значением 0. Приложение задаёт локальный путь библиотек и mke2fs.conf. Лицензии находятся в `app/src/main/assets/licenses/filesystems`.
