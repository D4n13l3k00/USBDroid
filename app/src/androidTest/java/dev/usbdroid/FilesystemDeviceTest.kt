package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.usbdroid.data.*
import dev.usbdroid.files.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class FilesystemDeviceTest {
 @Test fun workerRepairsCopyAndKeepsBackup() = runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.app
  val file=File.createTempFile(".worker-fs-test-",".partial",app.library.directory)
  val imageId=java.util.UUID.randomUUID().toString(); val jobId=java.util.UUID.randomUUID().toString()
  val dao=app.database.dao(); var backup: File?=null
  val request=androidx.work.OneTimeWorkRequestBuilder<ImageWorker>().setInputData(androidx.work.workDataOf("id" to jobId)).build()
  try {
   FatFormatter.format(file,64L*1048576,forceFat32=true)
   RandomAccessFile(file,"rw").use { out ->
    val boot=ByteArray(512); out.readFully(boot); val b=ByteBuffer.wrap(boot).order(ByteOrder.LITTLE_ENDIAN)
    val reserved=b.getShort(14).toInt() and 65535; val fatSectors=b.getInt(36)
    for(index in 0..1) { out.seek((reserved+index.toLong()*fatSectors)*512+4); out.writeInt(Integer.reverseBytes(0x07ffffff)) }
    out.fd.sync()
   }
   val originalHash=hash(file)
   dao.putImage(ImageEntry(imageId,"Filesystem repair test",file.path,file.canonicalPath,file.length(),file.lastModified(),"default"))
   dao.putJob(TransferJob(jobId,"FS_CHECK","Filesystem repair test",org.json.JSONObject().put("image",imageId).put("partition",0).put("repair",true).toString()))
   androidx.work.WorkManager.getInstance(app).enqueue(request).result.get()
   val completed=kotlinx.coroutines.withTimeout(120000) {
    while(true) { val job=dao.job(jobId)!!; if(job.state in listOf("DONE","FAILED","CANCELLED")) return@withTimeout job; kotlinx.coroutines.delay(250) }
    error("Unreachable")
   }
   backup=org.json.JSONObject(completed.args).optString("_original").takeIf { it.isNotBlank() }?.let(::File)
   assertEquals(completed.error,"DONE",completed.state)
   val result=org.json.JSONObject(completed.result)
   assertTrue(result.toString(),result.getBoolean("clean")); assertTrue(result.getBoolean("repair"))
   assertNotNull(backup); assertEquals(originalHash,hash(backup!!))
   assertNotEquals(result.toString(),originalHash,hash(file))
   assertTrue(FilesystemTools(app).check(file.path,0,false).clean)
  } finally {
   androidx.work.WorkManager.getInstance(app).cancelWorkById(request.id).result.get()
   backup?.delete(); file.delete(); dao.deleteImage(imageId)
   dao.jobs().first().filter { org.json.JSONObject(it.args).optString("image")==imageId }.forEach { dao.deleteJob(it.id) }
  }
 }
 private fun hash(file: File): String {
  val digest=MessageDigest.getInstance("SHA-256")
  file.inputStream().use { stream -> val buffer=ByteArray(1048576); while(true) { val n=stream.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
  return digest.digest().joinToString("") { "%02x".format(it) }
 }
 @Test fun nativeChecksDoNotModifyImages() = runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.app
  for(fs in ImageFilesystem.entries.filter { it!=ImageFilesystem.NONE }) {
   val image=File.createTempFile(".checker-test-",".partial",app.library.directory)
   try {
    RandomAccessFile(image,"rw").use { it.setLength(128L*1048576) }
    ImageFormatter.format(app,image,app.library.directory,fs)
    val before=hash(image)
    val info=ImageInspector(app).inspect(image.path)
    assertEquals(fs.label,info.partitions.single().filesystem)
    val checked=FilesystemTools(app).check(image.path,0,false)
    assertTrue("${fs.label}: ${checked.details}",checked.clean)
    assertEquals(before,hash(image))
   } finally { image.delete() }
  }
 }
 @Test fun secondPartitionIsMountedWithItsOwnBounds() = runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.app
  val image=File.createTempFile(".multi-test-",".partial",app.library.directory)
  var mountId: String?=null
  try {
   RandomAccessFile(image,"rw").use { out ->
    out.setLength(130L*1048576)
    for(number in 1..2) {
     val volume=File.createTempFile(".volume-test-",".partial",app.library.directory)
     try {
      FatFormatter.format(volume,64L*1048576,if(number==1) "FIRST" else "SECOND",true)
      out.seek((if(number==1) 1L else 65L)*1048576)
      volume.inputStream().use { input -> val buffer=ByteArray(1048576); while(true) { val n=input.read(buffer); if(n<0) break; out.write(buffer,0,n) } }
     } finally { volume.delete() }
    }
    val header=ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
    for(number in 0..1) { val p=446+number*16; header.put(p+4,12); header.putInt(p+8,(if(number==0) 1 else 65)*2048); header.putInt(p+12,64*2048) }
    header.putShort(510,0xaa55.toShort()); out.seek(0); out.write(header.array()); out.fd.sync()
   }
   val metadata=ImageInspector(app).inspect(image.path)
   assertEquals(listOf("FIRST","SECOND"),metadata.partitions.map { it.label })
   val access=app.imageAccess
   val second=access.mount(image.path,"Multi test",false,2); mountId=second.id
   assertEquals(65L*1048576,second.offset); assertEquals(64L*1048576,second.bytes)
   access.create(second.id,"","second.txt",false)
   val original=access.readText(second.id,"second.txt")
   access.writeText(second.id,"second.txt","second partition",original.second)
   access.unmount(second.id); mountId=null
   val first=access.mount(image.path,"Multi test",true,1); mountId=first.id
   assertTrue(access.children(first.id,"").none { it.name=="second.txt" })
   access.unmount(first.id); mountId=null
   assertTrue(FilesystemTools(app).check(image.path,2,false).clean)
  } finally { mountId?.let { app.imageAccess.unmount(it) }; image.delete() }
 }
}
