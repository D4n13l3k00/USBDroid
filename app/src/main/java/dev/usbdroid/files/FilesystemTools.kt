package dev.usbdroid.files

import android.content.Context
import android.system.Os
import dev.usbdroid.app
import dev.usbdroid.usb.RootShell
import java.io.File
import java.util.UUID

data class FilesystemResult(val code: Int, val clean: Boolean, val repaired: Boolean, val limited: Boolean, val details: String)

class FilesystemTools(private val context: Context) {
 companion object { fun repairable(fs: String) = fs in setOf("FAT","FAT32","exFAT","ext2","ext3","ext4") }
 suspend fun check(path: String, number: Int, repair: Boolean): FilesystemResult = context.app.usb.withDetachedImage(path) {
  val info=ImageInspector(context).inspect(path).partitions.firstOrNull { it.partition.number==number } ?: error("Partition no longer exists")
  require(!repair || repairable(info.filesystem)) { "Automatic repair is not available for this filesystem" }
  val native=File(context.applicationInfo.nativeLibraryDir)
  val libs=File(context.filesDir,"formatter-libs").apply { mkdirs() }
  for((alias,target) in listOf("libz.so.1" to "libfs_z.so","libzstd.so.1" to "libfs_zstd.so")) {
   val link=File(libs,alias); val expected=File(native,target).path
   synchronized(dev.usbdroid.data.ImageFormatter) { if(runCatching { Os.readlink(link.path) }.getOrNull()!=expected) { link.delete(); Os.symlink(expected,link.path) } }
  }
  val args=when(info.filesystem) {
   "FAT","FAT32" -> listOf("/system/bin/fsck_msdos") + if(repair) listOf("-p","-f") else listOf("-n")
   "ext2","ext3","ext4" -> listOf(File(native,"libe2fsck.so").path,"-f",if(repair) "-p" else "-n")
   "exFAT" -> listOf(File(native,"libfsck_exfat.so").path,if(repair) "-p" else "-n")
   "NTFS" -> listOf(File(native,"libntfsfix.so").path,"-n")
   "Btrfs" -> listOf(File(native,"libbtrfs_check.so").path,"check","--readonly","--mode","lowmem")
   else -> error("Unsupported filesystem: ${info.filesystem}")
  }
  fun q(s: String)=RootShell.quote(s)
  fun run(s: String)=RootShell.inMountNamespace(s,600)
  run("test -x ${q(args[0])}")
  val journal=File(context.filesDir,"filesystem-loop.json")
  fun cleanup(record: org.json.JSONObject) {
   val alias=record.getString("alias"); val loop=record.getString("loop")
   require(alias==AccessPolicy.loopAlias(record.getString("id")) && loop.matches(Regex("/dev/(block/)?loop[0-9]+")))
   val current=run("losetup ${q(loop)} 2>/dev/null || true")
   if(current.contains(alias)) run("losetup -d ${q(loop)}")
   if(RootShell.isMounted(alias)) run("umount ${q(alias)}")
   run("rm -f ${q(alias)}"); journal.delete()
  }
  if(journal.exists()) {
   val record=org.json.JSONObject(journal.readText())
   if(record.getString("boot")==run("cat /proc/sys/kernel/random/boot_id")) cleanup(record) else journal.delete()
  }
  val id=UUID.randomUUID().toString(); val alias=AccessPolicy.loopAlias(id); val loop=run("losetup -f")
  require(loop.matches(Regex("/dev/(block/)?loop[0-9]+")))
  val record=org.json.JSONObject().put("id",id).put("alias",alias).put("loop",loop).put("boot",run("cat /proc/sys/kernel/random/boot_id"))
  DurableJournal.write(journal,record.toString())
  try {
   run("set -e; mkdir -p /data/local/tmp/ud-loop; test ! -e ${q(alias)}; touch ${q(alias)}; mount --bind ${q(path)} ${q(alias)}; losetup ${if(repair) "" else "-r"} -o ${info.partition.offset} -S ${info.partition.bytes} ${q(loop)} ${q(alias)}")
   val log=File.createTempFile("fs-check-",".log",context.cacheDir)
   val code: Int
   val output: String
   try {
    code=run("LD_LIBRARY_PATH=${q(libs.path+":"+native.path)} ${args.joinToString(" ") { q(it) }} ${q(loop)} > ${q(log.path)} 2>&1; result=\$?; printf '%s' \"\$result\"").toInt()
    output=java.io.RandomAccessFile(log,"r").use { file -> file.seek(maxOf(0,file.length()-16000)); ByteArray(minOf(16000,file.length()).toInt()).also(file::readFully).toString(Charsets.UTF_8) }
   } finally { log.delete() }
   val clean=code==0 || repair && info.filesystem in setOf("ext2","ext3","ext4") && code in 1..2
   FilesystemResult(code,clean,repair && clean,info.filesystem=="NTFS",output.trim())
  } finally { if(repair) run("sync"); cleanup(record) }
 }
}
