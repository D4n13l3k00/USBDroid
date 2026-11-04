package dev.usbdroid.files

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DiskLayoutTest {
 private fun read(bytes: ByteArray) = DiskLayout.read(bytes.size.toLong()) { offset, size -> bytes.copyOfRange(offset.toInt(),offset.toInt()+size) }
 private fun entry(b: ByteBuffer, sector: Int, index: Int, type: Int, start: Int, count: Int) {
  val p=sector*512+446+index*16; b.put(p+4,type.toByte()); b.putInt(p+8,start); b.putInt(p+12,count); b.putShort(sector*512+510,0xaa55.toShort())
 }
 @Test fun twoPrimaryPartitions() {
  val data=ByteArray(8*1048576); val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
  entry(b,0,0,12,2048,4096); entry(b,0,1,7,8192,4096)
  val result=read(data); assertEquals("MBR",result.table); assertEquals(listOf(1,2),result.partitions.map { it.number }); assertEquals(4194304L,result.partitions[1].offset)
 }
 @Test fun extendedPartitions() {
  val data=ByteArray(8*1048576); val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
  entry(b,0,0,15,2048,12000); entry(b,2048,0,12,2048,2048); entry(b,2048,1,15,4096,6000); entry(b,6144,0,7,2048,2048)
  assertEquals(listOf(5,6),read(data).partitions.map { it.number })
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsExtendedCycle() {
  val data=ByteArray(8*1048576); val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
  entry(b,0,0,15,2048,12000); entry(b,2048,1,15,0,12000); read(data)
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsOverlap() {
  val data=ByteArray(8*1048576); val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
  entry(b,0,0,12,2048,4096); entry(b,0,1,7,4096,4096); read(data)
 }
 @Test fun rawImageHasOneVolume() { val info=read(ByteArray(1048576)); assertEquals("NONE",info.table); assertEquals(0,info.partitions.single().number) }
 @Test fun hybridIsoKeepsOpticalVolumeAndEfiPartition() {
  val data=ByteArray(1048576); val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
  entry(b,0,0,0x17,0,2048); entry(b,0,1,0xef,128,32)
  "CD001".toByteArray().copyInto(data,32769)
  val result=read(data)
  assertEquals("MBR",result.table); assertEquals(listOf(0,2),result.partitions.map { it.number }); assertEquals(0L,result.partitions.first().offset)
 }
}
