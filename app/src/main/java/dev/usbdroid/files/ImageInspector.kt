package dev.usbdroid.files

import android.content.Context
import dev.usbdroid.app
import dev.usbdroid.usb.RootShell
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PartitionInfo(val partition: ImagePartition, val filesystem: String, val label: String = "", val freeBytes: Long? = null, val estimated: Boolean = false)
data class ImageInfo(val layout: DiskLayout, val partitions: List<PartitionInfo>, val modified: Long = 0, val mountIds: Set<String> = emptySet())

class ImageInspector(private val context: Context) {
 fun layout(path: String): DiskLayout {
  val size = RootShell.inMountNamespace("stat -c %s ${RootShell.quote(path)}").toLong()
  return try { DiskLayout.read(size) { offset, length -> read(path,offset,length) } }
  catch(e: IllegalArgumentException) { if(path.endsWith(".iso",true)) DiskLayout("UNKNOWN",size,listOf(ImagePartition(0,0,size))) else throw e }
  catch(e: IllegalStateException) { if(path.endsWith(".iso",true)) DiskLayout("UNKNOWN",size,listOf(ImagePartition(0,0,size))) else throw e }
 }
 fun read(path: String, offset: Long, length: Int): ByteArray {
  require(offset >= 0 && length in 0..1048576)
  if(File(path).canRead()) return RandomAccessFile(path,"r").use { it.seek(offset); ByteArray(length).also(it::readFully) }
  val file = File.createTempFile("image-probe-", ".bin", context.cacheDir)
  return try {
   val skip=offset/512; val extra=(offset%512).toInt(); val count=(length+extra+511)/512
   RootShell.inMountNamespace("set -e; dd if=${RootShell.quote(path)} of=${RootShell.quote(file.path)} bs=512 skip=$skip count=$count 2>/dev/null")
   val data=file.readBytes(); require(data.size >= extra+length); data.copyOfRange(extra,extra+length)
  } finally { file.delete() }
 }
 fun inspect(path: String): ImageInfo {
  val layout=layout(path)
  return ImageInfo(layout,layout.partitions.map { part ->
   val header=read(path,part.offset,minOf(part.bytes,4096).toInt())
   val b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
   val oem=if(header.size >= 11) String(header,3,8,Charsets.US_ASCII) else ""
   var fs="UNKNOWN"; var label=""; var free: Long?=null; var estimated=false
   when {
    oem=="NTFS    " -> fs="NTFS"
    oem=="EXFAT   " -> { fs="exFAT"; val used=header[112].toInt() and 255; if(used in 0..100) { free=part.bytes/100*(100-used); estimated=true } }
    header.size >= 2048 && b.getShort(1080).toInt() and 65535 == 0xef53 -> {
     fs=if(b.getInt(1120) and 0x40 != 0 || b.getInt(1120) and 0x80 != 0) "ext4" else if(b.getInt(1116) and 4 != 0) "ext3" else "ext2"; label=String(header,1144,16,Charsets.UTF_8).trimEnd('\u0000')
     val count=(b.getInt(1036).toLong() and 0xffffffffL) + if(b.getInt(1120) and 0x80 != 0) (b.getInt(1368).toLong() and 0xffffffffL)*4294967296L else 0
     val shift=b.getInt(1048); if(shift in 0..6) free=runCatching { Math.multiplyExact(count,1024L shl shift) }.getOrNull()?.takeIf { it in 0..part.bytes }
    }
    header.size >= 512 && (b.getShort(11).toInt() and 65535) in listOf(512,1024,2048,4096) && header[16].toInt() in 1..2 && (b.getShort(14).toInt() and 65535) > 0 -> {
     val fat32=b.getShort(17)==0.toShort() && b.getShort(22)==0.toShort(); fs=if(fat32) "FAT32" else "FAT"
     label=String(header,if(fat32) 71 else 43,11,Charsets.US_ASCII).trim()
     if(fat32) {
      val sector=b.getShort(11).toInt() and 65535; val spc=header[13].toInt() and 255; val info=b.getShort(48).toInt() and 65535
      if(info in 1 until (b.getShort(14).toInt() and 65535) && part.bytes >= (info+1L)*sector) {
       val f=ByteBuffer.wrap(read(path,part.offset+info.toLong()*sector,512)).order(ByteOrder.LITTLE_ENDIAN)
       if(f.getInt(0)==0x41615252 && f.getInt(484)==0x61417272) free=(f.getInt(488).toLong() and 0xffffffffL).takeIf { it != 0xffffffffL }?.let { runCatching { Math.multiplyExact(it,sector.toLong()*spc) }.getOrNull() }?.takeIf { it in 0..part.bytes }
       estimated=free!=null
      }
     }
    }
   }
   if(fs=="UNKNOWN" && part.bytes>=65536+72) { if(String(read(path,part.offset+65536+64,8),Charsets.US_ASCII)=="_BHRfS_M") fs="Btrfs" }
   if(fs=="UNKNOWN" && part.bytes>=32774) { val signature=String(read(path,part.offset+32769,5),Charsets.US_ASCII); if(signature=="CD001") fs="ISO9660" else if(signature=="BEA01") fs="UDF" }
   context.app.imageAccess.mounts.value.firstOrNull { it.image==path && it.offset==part.offset }?.let { mounted ->
    if(RootShell.isMounted(mounted.directory)) runCatching { RootShell.inMountNamespace("stat -f -c '%S %a' ${RootShell.quote(mounted.directory)}").trim().split(Regex("\\s+")).let { Math.multiplyExact(it[0].toLong(),it[1].toLong()) } }.getOrNull()?.takeIf { it in 0..part.bytes }?.let { free=it; estimated=false }
   }
   PartitionInfo(part,fs,label,free,estimated)
  })
 }
}
