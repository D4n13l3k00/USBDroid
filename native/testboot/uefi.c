/* USBDroid's own standalone UEFI test application; no external runtime. */
typedef unsigned long long UINTN;
typedef unsigned short CHAR16;
typedef struct { void *Reset; UINTN (*OutputString)(void *, CHAR16 *); } TextOutput;
typedef struct {
 unsigned char Header[24]; CHAR16 *FirmwareVendor; unsigned int FirmwareRevision;
 void *ConsoleInHandle; void *ConIn; void *ConsoleOutHandle; TextOutput *ConOut;
} SystemTable;
UINTN efi_main(void *image, SystemTable *system) {
 (void)image;
 system->ConOut->OutputString(system->ConOut, (CHAR16 *)L"\r\nUSBDroid boot test successful (UEFI x64).\r\nUSB image booted correctly. Restart your computer.\r\n");
 for (;;) { __asm__ volatile("pause"); }
 return 0;
}
