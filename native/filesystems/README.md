# Форматтеры

exfatprogs, e2fsprogs, ntfs-3g и btrfs-progs с библиотеками для ARM64, ARMv7 и x86_64. FAT16/FAT32 реализованы в Kotlin.

`provenance-*.json` содержит версии, URL пакетов и SHA-256. `sources/` содержит исходные архивы, `recipes/` — закреплённые рецепты и патчи Termux. Commit указан в `termux-commit.txt`.

В упакованных ELF удалены RPATH/RUNPATH: теги DT_RPATH и DT_RUNPATH заменены на DT_DEBUG со значением 0. Приложение задаёт локальный путь библиотек и mke2fs.conf. Лицензии находятся в `app/src/main/assets/licenses/filesystems`.
