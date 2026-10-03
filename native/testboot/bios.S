.code16
.section .text
.global _start
_start:
 cli
 xor %ax,%ax
 mov %ax,%ds
 mov %ax,%ss
 mov $0x7c00,%sp
 sti
 mov $message,%si
next:
 lodsb
 test %al,%al
 jz done
 mov $0x0e,%ah
 mov $7,%bx
 int $0x10
 jmp next
done:
 cli
 hlt
 jmp done
message:
 .asciz "\r\nUSBDroid boot test successful (BIOS).\r\nUSB image booted correctly. Restart your computer.\r\n"
