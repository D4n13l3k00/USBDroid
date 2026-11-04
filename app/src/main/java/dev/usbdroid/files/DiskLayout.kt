package dev.usbdroid.files

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

data class ImagePartition(val number: Int, val offset: Long, val bytes: Long, val name: String = "")
data class DiskLayout(val table: String, val bytes: Long, val partitions: List<ImagePartition>) {
 companion object {
  fun read(bytes: Long, read: (Long, Int) -> ByteArray): DiskLayout {
   require(bytes >= 512)
   fun at(offset: Long, size: Int): ByteArray { require(offset >= 0 && size >= 0 && offset <= bytes - size); return read(offset, size).also { require(it.size == size) { "Truncated image" } } }
   fun buffer(data: ByteArray) = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
   fun u32(b: ByteBuffer, p: Int) = b.getInt(p).toLong() and 0xffffffffL
   val iso=bytes>=32774 && String(at(32769,5),Charsets.US_ASCII)=="CD001"
   val first = at(0, 512); val b = buffer(first)
   val sector = b.getShort(11).toInt() and 65535; val cluster = first[13].toInt() and 255
   val oem = String(first, 3, 8, Charsets.US_ASCII)
   val raw = oem in listOf("EXFAT   ", "NTFS    ") || sector in listOf(512,1024,2048,4096) && cluster > 0 && cluster and (cluster - 1) == 0 && (b.getShort(14).toInt() and 65535) > 0 && first[16].toInt() in 1..2
   if(raw || b.getShort(510).toInt() and 65535 != 0xaa55 || (0..3).all { first[450 + it * 16] == 0.toByte() }) return DiskLayout("NONE", bytes, listOf(ImagePartition(0,0,bytes)))
   val parts = mutableListOf<ImagePartition>()
   fun add(number: Int, start: Long, sectors: Long, name: String = "", unit: Long = 512) {
    require(start > 0 && sectors > 0)
    val offset = Math.multiplyExact(start,unit); val length = Math.multiplyExact(sectors,unit)
    require(offset <= bytes - length) { "Partition is outside the image" }
    require(parts.none { offset < it.offset + it.bytes && it.offset < offset + length }) { "Overlapping partitions" }
    parts += ImagePartition(number,offset,length,name)
   }
   if((0..3).any { first[450 + it * 16].toInt() and 255 == 0xee }) {
    val unit = if(String(at(512,8),Charsets.US_ASCII) == "EFI PART") 512L else if(bytes >= 8192 && String(at(4096,8),Charsets.US_ASCII) == "EFI PART") 4096L else error("Invalid GPT header")
    val header = at(unit,unit.toInt()); val h = buffer(header)
    val size = h.getInt(12); require(size in 92..unit.toInt() && h.getLong(24) == 1L)
    val checksum = h.getInt(16); h.putInt(16,0); require(CRC32().apply { update(header,0,size) }.value.toInt() == checksum) { "GPT header checksum mismatch" }
    val count = h.getInt(80); val width = h.getInt(84); require(count in 1..4096 && width in 128..4096 && width % 8 == 0)
    val tableBytes = Math.multiplyExact(count,width); require(tableBytes <= 1048576) { "GPT table is too large" }
    val table = at(Math.multiplyExact(h.getLong(72),unit),tableBytes)
    require(CRC32().apply { update(table) }.value.toInt() == h.getInt(88)) { "GPT table checksum mismatch" }
    val entries = buffer(table)
    for(i in 0 until count) {
     val p = i * width
     if((p until p+16).all { table[it] == 0.toByte() }) continue
     val start = entries.getLong(p+32); val end = entries.getLong(p+40)
     if(start==0L && iso) { require(end>=0 && end<bytes/unit); continue }
     require(start >= h.getLong(40) && end <= h.getLong(48) && end >= start) { "Invalid GPT partition bounds" }
     val name = String(table,p+56,minOf(72,width-56),Charsets.UTF_16LE).substringBefore('\u0000')
     add(i+1,start,Math.addExact(Math.subtractExact(end,start),1),name,unit)
    }
    return DiskLayout("GPT",bytes,if(iso) listOf(ImagePartition(0,0,bytes))+parts else parts)
   }
   val extensions = mutableListOf<Pair<Long,Long>>()
   for(i in 0..3) {
    val p=446+i*16; val type=first[p+4].toInt() and 255
    if(type == 0) continue
    val start=u32(b,p+8); val count=u32(b,p+12)
    if(type in listOf(5,15,0x85)) { require(start > 0 && count > 0 && start <= bytes/512-count); extensions += start to count }
    else if(start==0L && iso) require(count>0 && count<=bytes/512) else add(i+1,start,count)
   }
   var logical=5
   for((index,container) in extensions.withIndex()) {
    val (base,length)=container
    val offset=base*512; val extent=length*512
    require(parts.none { offset < it.offset+it.bytes && it.offset < offset+extent }) { "Extended partition overlaps a primary partition" }
    require(extensions.take(index).none { (other,size) -> base < other+size && other < base+length }) { "Overlapping extended partitions" }

    var next=base; val visited=mutableSetOf<Long>()
    while(true) {
     require(visited.add(next) && visited.size <= 128 && next in base until base+length) { "Invalid extended partition chain" }
     val ebr=buffer(at(Math.multiplyExact(next,512),512)); require(ebr.getShort(510).toInt() and 65535 == 0xaa55) { "Invalid EBR" }
     val relative=u32(ebr,454); val count=u32(ebr,458)
     if(ebr.get(450) != 0.toByte()) { require(next+relative <= base+length-count && relative > 0); add(logical++,Math.addExact(next,relative),count) }
     if(ebr.get(466) == 0.toByte()) break
     require(ebr.get(466).toInt() and 255 in listOf(5,15,0x85))
     next=Math.addExact(base,u32(ebr,470))
    }
   }
   return DiskLayout("MBR",bytes,(if(iso) listOf(ImagePartition(0,0,bytes)) else emptyList())+parts.sortedBy { it.number })
  }
 }
}
