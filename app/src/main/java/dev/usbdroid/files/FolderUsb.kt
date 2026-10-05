package dev.usbdroid.files

import android.content.Context
import dev.usbdroid.usb.RootShell
import dev.usbdroid.usb.UsbController
import dev.usbdroid.usb.ConfigFsBackend
import dev.usbdroid.usb.UsbIo
import dev.usbdroid.usb.SuShell
import dev.usbdroid.usb.Lun
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class FolderShare(val folder: String, val gadget: String, val sandbox: String, val pid: Int)
class FolderUsb(private val context: Context, private val usb: UsbController, private val access: ImageAccess) {
 private val journal = File(context.filesDir, "folder-mtp.json")
 private val _state = MutableStateFlow<FolderShare?>(null)
 val state = _state.asStateFlow()
 private fun q(s: String) = RootShell.quote(s)
 private fun run(s: String) = RootShell.inMountNamespace(s)
 private fun boot() = run("cat /proc/sys/kernel/random/boot_id")
 suspend fun refresh() = usb.serialized {
  _state.value = if(!journal.exists()) null else JSONObject(journal.readText()).takeIf { it.getString("boot") == boot() }?.let { FolderShare(it.getString("folder"), it.getString("gadget"), it.getString("sandbox"), it.optInt("pid")) }
 }
 private fun save(state: JSONObject) {
  val temp = File(context.filesDir, "folder-mtp.tmp"); temp.writeText(state.toString()); check(temp.renameTo(journal))
 }
 suspend fun start(folder: String, readOnly: Boolean) {
  val current = usb.inspect(); check(current.root) { current.error ?: "Root required" }
  require(!usb.hasSessions() && current.luns.all { it.file.isBlank() }) { "Eject USB images first" }
  usb.serialized {
   val usbJournal = File(context.filesDir, "usb-state.json")
   if(usbJournal.exists()) {
    val usbState = JSONObject(usbJournal.readText())
    require(usbState.optString("boot") != boot() || (usbState.optJSONObject("luns")?.length() ?: 0) == 0) { "Eject USB images first" }
   }
   if(journal.exists() && JSONObject(journal.readText()).optString("boot") != boot()) journal.delete()
   require(!journal.exists()) { "Stop the previous folder share first" }
   val path = run("readlink -f ${q(folder)}"); require(path.startsWith('/') && path != "/" && path.none { it == '\n' || it == '\r' || it == '"' }) { "Invalid folder" }
   run("test -d ${q(path)}")
   // FunctionFS name is unique so Android's own MTP daemon never opens these endpoints.
   val base = run("for p in /config/usb_gadget /sys/kernel/config/usb_gadget; do [ -d \"\$p\" ] && { echo \"\$p\"; break; }; done")
   require(base.startsWith('/')) { "ConfigFS is unavailable" }
   val gadget = "$base/usbdroid_mtp"; val sandbox = "/data/local/tmp/usbdroid-mtp-${UUID.randomUUID()}"
   val state = JSONObject().put("boot", boot()).put("folder", path).put("gadget", gadget).put("sandbox", sandbox)
   val controllers = JSONObject()
   run("find ${q(base)} -mindepth 1 -maxdepth 1 -type d").lines().filter { it.startsWith('/') && it != gadget }.forEach { g -> val udc = run("cat ${q("$g/UDC")}"); if(udc.isNotBlank()) controllers.put("$g/UDC", udc) }
   state.put("controllers", controllers)
   save(state)
   try {
    run("set -e; mkdir -p ${q("$gadget/configs/c.1")} ${q("$gadget/strings/0x409")} ${q("$gadget/functions/ffs.usbdroid_mtp")}; printf '0x18d1\\n' > ${q("$gadget/idVendor")}; printf '0x2d13\\n' > ${q("$gadget/idProduct")}; printf 'USBDroid\\n' > ${q("$gadget/strings/0x409/manufacturer")}; printf 'USBDroid Folders\\n' > ${q("$gadget/strings/0x409/product")}; printf 'USBDroid-MTP\\n' > ${q("$gadget/strings/0x409/serialnumber")}; [ -L ${q("$gadget/configs/c.1/mtp")} ] || ln -s ${q("$gadget/functions/ffs.usbdroid_mtp")} ${q("$gadget/configs/c.1/mtp")}")
    run("set -e; mkdir -p ${q("$sandbox/storage")} ${q("$sandbox/dev/mtp")} ${q("$sandbox/tmp")} ${q("$sandbox/proc")}; mount --bind ${q(path)} ${q("$sandbox/storage")}")
    if(readOnly) run("mount -o remount,bind,ro ${q("$sandbox/storage")}")
    run("set -e; mount -t proc -o nosuid,nodev,noexec proc ${q("$sandbox/proc")}; mount -t functionfs usbdroid_mtp ${q("$sandbox/dev/mtp")}; cp ${q("${context.applicationInfo.nativeLibraryDir}/libumtprd.so")} ${q("$sandbox/umtprd")}; chmod 755 ${q("$sandbox/umtprd")}")
    val config = File(context.filesDir, "folder-mtp.conf")
    config.writeText("""
     loop_on_disconnect 1
     storage "/storage" "${File(path).name.replace('"', '_')}" "${if(readOnly) "ro" else "rw"}"
     manufacturer "USBDroid"
     product "USBDroid Folders"
     serial "USBDroid-MTP"
     firmware_version "1.2"
     usb_functionfs_mode 1
     usb_dev_path "/dev/mtp/ep0"
     usb_epin_path "/dev/mtp/ep1"
     usb_epout_path "/dev/mtp/ep2"
     usb_epint_path "/dev/mtp/ep3"
     usb_max_packet_size 0x200
     sync_when_close 1
    """.trimIndent())
    run("cp ${q(config.path)} ${q("$sandbox/config")}")
    require(run("chroot ${q(sandbox)} /umtprd --check-storage /config").contains("MTP_STORAGE_READY=1")) { "MTP storage is unavailable" }
    val pid = run("chroot ${q(sandbox)} /umtprd -conf /config < /dev/null > ${q(File(context.filesDir, "folder-mtp.log").path)} 2>&1 & echo \$!").toInt()
    state.put("pid", pid); save(state)
    run("for i in 1 2 3 4 5 6 7 8 9 10; do [ -e ${q("$sandbox/dev/mtp/ep1")} ] && exit 0; sleep 0.2; done; exit 1")
    val controller = controllers.keys().asSequence().map { controllers.getString(it) }.firstOrNull() ?: run("ls /sys/class/udc | head -n 1")
    require(controller.isNotBlank()) { "USB controller unavailable" }
    controllers.keys().forEach { p -> require(run("cat ${q(p)}") == controllers.getString(p)) { "USB configuration changed" }; run("printf '\\n' > ${q(p)}") }
    UsbIo(SuShell).write("$gadget/UDC", controller)
    _state.value = FolderShare(path, gadget, sandbox, pid)
   } catch(e: Exception) { runCatching { stopNow(state) }.onFailure { e.addSuppressed(it) }; throw e }
  }
 }
 private fun stopNow(state: JSONObject) {
  require(state.getString("boot") == boot()) { "Folder share belongs to a previous boot" }
  val gadget = state.getString("gadget"); val sandbox = state.getString("sandbox")
  require(gadget.endsWith("/usbdroid_mtp") && sandbox.matches(Regex("/data/local/tmp/usbdroid-mtp-[a-f0-9-]+")))
  run("[ ! -f ${q("$gadget/UDC")} ] || printf '\\n' > ${q("$gadget/UDC")}")
  val pid = state.optInt("pid")
  if(pid > 1 && run("readlink /proc/$pid/root 2>/dev/null || true") == sandbox) run("kill -TERM $pid; for i in 1 2 3 4 5; do kill -0 $pid 2>/dev/null || exit 0; sleep 0.2; done; kill -KILL $pid 2>/dev/null || true")
  listOf("$sandbox/dev/mtp", "$sandbox/storage", "$sandbox/proc").forEach { target ->
   if(RootShell.isMounted(target)) run("umount ${q(target)}")
  }
  val controllers = state.getJSONObject("controllers"); controllers.keys().forEach { node ->
   val current = run("cat ${q(node)}")
   require(current.isBlank() || current == controllers.getString(node)) { "USB controller changed; original configuration was not overwritten" }
   if(current.isBlank()) UsbIo(SuShell).write(node, controllers.getString(node))
  }
  run("set -e; rm -f ${q("$sandbox/umtprd")} ${q("$sandbox/config")}; rmdir ${q("$sandbox/storage")} ${q("$sandbox/dev/mtp")} ${q("$sandbox/dev")} ${q("$sandbox/tmp")}; if [ -d ${q("$sandbox/proc")} ]; then rmdir ${q("$sandbox/proc")}; fi; rmdir ${q(sandbox)}")
  journal.delete(); _state.value = null
 }
 suspend fun stop() = usb.serialized { if(journal.exists()) { val state = JSONObject(journal.readText()); if(state.getString("boot") != boot()) { journal.delete(); _state.value = null } else stopNow(state) } }
 suspend fun alive(): Boolean = usb.serialized { val s = _state.value ?: return@serialized false; run("readlink /proc/${s.pid}/root 2>/dev/null || true") == s.sandbox && run("cat ${q("${s.gadget}/UDC")}").isNotBlank() }
}
