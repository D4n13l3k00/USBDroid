package dev.usbdroid.usb

import android.content.Context
import dev.usbdroid.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {
 fun quote(value: String): String { require(!value.contains('\u0000')); return "'" + value.replace("'", "'\"'\"'") + "'" }
 fun run(script: String, timeout: Long = 30): String {
  val p = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
  val output = StringBuilder(); val reader = Thread { p.inputStream.bufferedReader().useLines { it.forEach { line -> synchronized(output) { if(output.length < 100000) output.append(line).append('\n') } } } }.apply { isDaemon = true; start() }
  if(!p.waitFor(timeout, TimeUnit.SECONDS)) { p.destroyForcibly(); error("Root command timed out") }; reader.join(2000)
  val result = synchronized(output) { output.toString().trim() }; check(p.exitValue() == 0) { result.ifBlank { "Root command failed (${p.exitValue()})" } }; return result
 }
}
interface Shell { fun run(script: String): String }
object SuShell: Shell { override fun run(script: String) = RootShell.run(script) }
class UsbIo(val shell: Shell) {
 fun read(path: String) = shell.run("cat ${RootShell.quote(path)}")
 fun write(path: String, value: String) {
  val command = "printf '%s\\n' ${RootShell.quote(value)} > ${RootShell.quote(path)}"
  if(path.endsWith("/UDC") && value.isBlank() && read(path).isBlank()) return
  if(!path.endsWith("/UDC") || value.isBlank()) { shell.run(command); return }
  var failure: Exception? = null
  // Android's FunctionFS clients reopen descriptors asynchronously after an unbind.
  repeat(20) {
   try { if(read(path) != value) shell.run(command); return } catch(e: Exception) {
    if(!e.message.orEmpty().contains("No such device")) throw e
    failure = e; Thread.sleep(250)
   }
  }
  throw failure ?: IllegalStateException("USB controller could not be rebound")
 }
 fun exists(path: String) = shell.run("if [ -f ${RootShell.quote(path)} ]; then echo yes; fi") == "yes"
 fun validate(path: String) { require((path.startsWith("/sys/") || path.startsWith("/config/usb_gadget/")) && !path.split('/').contains("..") && !path.contains('\n')) { "Invalid USB path" } }
}
data class Lun(val path: String, val file: String, val readOnly: Boolean, val cdrom: Boolean, val supportsReadOnly: Boolean, val supportsCdrom: Boolean) { val title get() = path.substringAfter("functions/", path.removePrefix("/sys/")) }
data class UsbCapabilities(val backend: String, val readOnly: Boolean, val cdrom: Boolean)
data class HostingSession(val lun: String, val image: String, val backend: String)
data class UsbStatus(val root: Boolean = false, val mode: String = "", val cable: String = "", val luns: List<Lun> = emptyList(), val error: String? = null)
enum class CompatibilityCheck { NO_SESSIONS, HEALTHY, REPAIRED }
enum class HostMode(val label: String, val ro: Boolean, val cd: Boolean) { READ_ONLY("USB • read only", true, false), WRITABLE("USB • read / write", false, false), CDROM("CD-ROM", true, true) }

interface UsbBackend {
 val id: String
 fun capture(lun: Lun): JSONObject
 fun activate(lun: Lun, mode: String, snapshot: JSONObject)
 fun restore(snapshot: JSONObject)
}
class ConfigFsBackend(private val io: UsbIo, private val gadget: String, private val persist: () -> Unit = {}): UsbBackend {
 override val id = "configfs:$gadget"
 override fun capture(lun: Lun): JSONObject {
  val configs = io.shell.run("find ${RootShell.quote("$gadget/configs")} -mindepth 1 -maxdepth 1 -type d").lines().filter { it.startsWith("/") }
  require(configs.isNotEmpty()) { "No ConfigFS configuration" }
  // Android generally exposes one config. If multiple exist, pick the one already containing functions.
  val config = configs.firstOrNull { io.shell.run("find ${RootShell.quote(it)} -maxdepth 1 -type l").isNotBlank() } ?: configs.first()
  val hidden = JSONObject()
  val existing = io.shell.run("for p in ${RootShell.quote(config)}/*; do if [ -L \"${'$'}p\" ]; then printf '%s\\t%s\\n' \"${'$'}p\" \"${'$'}(readlink -f \"${'$'}p\")\"; fi; done")
  for(line in existing.lines()) { val pair = line.split('\t', limit = 2); if(pair.size == 2 && pair[1].substringAfterLast('/').let { it.contains("mtp", true) || it.contains("ptp", true) || it.contains("adb", true) }) hidden.put(pair[0], pair[1]) }
  val descriptors = JSONObject()
  for(name in listOf("bDeviceClass", "bDeviceSubClass", "bDeviceProtocol")) if(io.exists("$gadget/$name")) descriptors.put("$gadget/$name", io.read("$gadget/$name"))
  val locales = io.shell.run("find ${RootShell.quote("$gadget/strings")} -mindepth 1 -maxdepth 1 -type d").lines().filter { it.startsWith("/") }
  for(locale in locales) for(name in listOf("manufacturer", "product", "serialnumber")) if(io.exists("$locale/$name")) descriptors.put("$locale/$name", io.read("$locale/$name"))
  val otherControllers = JSONObject()
  if(gadget.endsWith("/usbdroid")) {
   val siblings = io.shell.run("find ${RootShell.quote(gadget.substringBeforeLast('/'))} -mindepth 1 -maxdepth 1 -type d").lines().filter { it.startsWith("/") && it != gadget }
   for(other in siblings) if(io.exists("$other/UDC")) { val controller = io.read("$other/UDC"); if(controller.isNotBlank()) otherControllers.put("$other/UDC", controller) }
  }
  return JSONObject().put("gadget", gadget).put("config", config).put("udc", io.read("$gadget/UDC")).put("links", JSONObject()).put("hiddenLinks", hidden).put("descriptors", descriptors).put("otherControllers", otherControllers)
 }
 override fun activate(lun: Lun, mode: String, snapshot: JSONObject) {
  if(snapshot.optBoolean("compatibility")) {
   io.shell.run("setprop sys.usb.config cdrom; setprop sys.usb.configfs 1")
  }
  val function = lun.path.substringBefore("/lun."); val config = snapshot.getString("config")
  val controller = snapshot.getString("udc").ifBlank { io.shell.run("ls /sys/class/udc | head -n 1") }
  require(controller.isNotBlank()) { "USB device controller unavailable" }
  snapshot.put("activeController", controller)
  val existing = io.shell.run("for p in ${RootShell.quote(config)}/*; do if [ -L \"${'$'}p\" ]; then readlink -f \"${'$'}p\"; fi; done")
  val needsLink = function !in existing.lines()
  val link = "$config/usbdroid-${function.hashCode().toUInt()}"
  val links = snapshot.getJSONObject("links"); if(needsLink) links.put(link, function)
  if(snapshot.getString("udc").isBlank()) snapshot.put("boundByUs", true)
  persist()
  val otherControllers = snapshot.optJSONObject("otherControllers") ?: JSONObject()
  for(path in otherControllers.keys()) { io.validate(path); val current = io.read(path); require(current.isBlank() || current == otherControllers.getString(path)) { "USB controller changed by another process" }; if(current.isNotBlank()) io.write(path, "") }
  io.write("$gadget/UDC", "")
  val hidden = snapshot.optJSONObject("hiddenLinks") ?: JSONObject()
  for(path in hidden.keys()) { io.validate(path); io.shell.run("if [ -L ${RootShell.quote(path)} ] && [ \"${'$'}(readlink -f ${RootShell.quote(path)})\" = ${RootShell.quote(hidden.getString(path))} ]; then rm ${RootShell.quote(path)}; fi") }
  val descriptors = snapshot.optJSONObject("descriptors") ?: JSONObject()
  val serial = descriptors.keys().asSequence().firstOrNull { it.endsWith("/serialnumber") }?.let { descriptors.getString(it) }.orEmpty()
  val anonymousId = java.security.MessageDigest.getInstance("SHA-256").digest((serial.ifBlank { gadget }).toByteArray()).take(6).joinToString("") { "%02x".format(it) }
  for(path in descriptors.keys()) { io.validate(path); io.write(path, when(path.substringAfterLast('/')) { "manufacturer" -> "USBDroid"; "product" -> "USBDroid USB Disk"; "serialnumber" -> "USBDroid-$anonymousId"; else -> "0" }) }
  if(needsLink) io.shell.run("ln -s ${RootShell.quote(function)} ${RootShell.quote(link)}")
  io.write("$gadget/UDC", controller)
  if(snapshot.optBoolean("compatibility")) io.shell.run("setprop sys.usb.state cdrom")
  if(needsLink) check(io.shell.run("if [ -L ${RootShell.quote(link)} ]; then readlink -f ${RootShell.quote(link)}; fi") == function) { "Android USB service removed the mass-storage function" }
 }
 fun maintain(luns: List<Lun>, snapshot: JSONObject): Boolean {
  val config = snapshot.getString("config")
  val targets = io.shell.run("for p in ${RootShell.quote(config)}/*; do if [ -L \"${'$'}p\" ]; then readlink -f \"${'$'}p\"; fi; done").lines()
  val controller = snapshot.optString("activeController")
  val others = snapshot.optJSONObject("otherControllers") ?: JSONObject()
  val hidden = snapshot.optJSONObject("hiddenLinks") ?: JSONObject()
  val changed = hidden.keys().asSequence().any { hidden.getString(it) in targets } || luns.any { it.path.substringBefore("/lun.") !in targets } || controller.isBlank() || io.read("$gadget/UDC") != controller || others.keys().asSequence().any { io.read(it).isNotBlank() }
  if(!changed) return false
  for(lun in luns) activate(lun, "mass_storage", snapshot)
  return true
 }
 override fun restore(snapshot: JSONObject) {
  val links = snapshot.getJSONObject("links")
  val hidden = snapshot.optJSONObject("hiddenLinks") ?: JSONObject(); val descriptors = snapshot.optJSONObject("descriptors") ?: JSONObject()
  if(links.length() == 0 && hidden.length() == 0 && descriptors.length() == 0 && !snapshot.optBoolean("boundByUs")) return
  io.write("$gadget/UDC", "")
  try {
   for(link in links.keys()) { io.validate(link); val function = links.getString(link); io.shell.run("if [ -L ${RootShell.quote(link)} ] && [ \"${'$'}(readlink -f ${RootShell.quote(link)})\" = ${RootShell.quote(function)} ]; then rm ${RootShell.quote(link)}; fi") }
   for(link in hidden.keys()) { io.validate(link); val function = hidden.getString(link); io.shell.run("if [ ! -e ${RootShell.quote(link)} ] && [ ! -L ${RootShell.quote(link)} ]; then ln -s ${RootShell.quote(function)} ${RootShell.quote(link)}; elif [ \"${'$'}(readlink -f ${RootShell.quote(link)})\" != ${RootShell.quote(function)} ]; then echo 'USB link changed by another process'; exit 1; fi") }
   for(path in descriptors.keys()) { io.validate(path); io.write(path, descriptors.getString(path)) }
  }
  finally {
   io.write("$gadget/UDC", snapshot.getString("udc"))
   val otherControllers = snapshot.optJSONObject("otherControllers") ?: JSONObject()
   for(path in otherControllers.keys()) { io.validate(path); io.write(path, otherControllers.getString(path)) }
   snapshot.optJSONObject("usbProperties")?.let { props ->
    for(name in listOf("sys.usb.configfs", "sys.usb.config", "sys.usb.state")) io.shell.run("setprop $name ${RootShell.quote(props.getString(name))}")
   }
  }
 }
}
class LegacyBackend(private val io: UsbIo, private val kind: String): UsbBackend {
 override val id = kind
 override fun capture(lun: Lun): JSONObject = when(kind) {
  "functions" -> JSONObject().put("functions", io.read("/sys/class/android_usb/android0/functions")).put("enable", io.read("/sys/class/android_usb/android0/enable"))
  "samsung" -> JSONObject().put("menu", io.read("/sys/devices/platform/android_usb/UsbMenuSel"))
  else -> JSONObject().put("mode", io.shell.run("getprop sys.usb.config"))
 }
 override fun activate(lun: Lun, mode: String, snapshot: JSONObject) { when(kind) {
  "functions" -> { io.write("/sys/class/android_usb/android0/enable", "0"); try { io.write("/sys/class/android_usb/android0/functions", mode) } finally { io.write("/sys/class/android_usb/android0/enable", "1") } }
  "samsung" -> io.write("/sys/devices/platform/android_usb/UsbMenuSel", "UMS")
  else -> io.shell.run("setprop sys.usb.config ${RootShell.quote(mode)}")
 } }
 override fun restore(snapshot: JSONObject) { when(kind) {
  "functions" -> { io.write("/sys/class/android_usb/android0/enable", "0"); try { io.write("/sys/class/android_usb/android0/functions", snapshot.getString("functions")) } finally { io.write("/sys/class/android_usb/android0/enable", snapshot.getString("enable")) } }
  "samsung" -> io.write("/sys/devices/platform/android_usb/UsbMenuSel", snapshot.getString("menu"))
  else -> io.shell.run("setprop sys.usb.config ${RootShell.quote(snapshot.getString("mode"))}")
 } }
}
class UsbController(private val file: File, shell: Shell = SuShell) {
 private val _changes = kotlinx.coroutines.flow.MutableSharedFlow<UsbStatus>(replay = 1, extraBufferCapacity = 1)
 val changes: kotlinx.coroutines.flow.SharedFlow<UsbStatus> = _changes
 constructor(context: Context, shell: Shell = SuShell): this(File(context.filesDir, "usb-state.json"), shell)
 private val io = UsbIo(shell)
 private val mutex = Mutex()
 private fun load(): JSONObject = if(file.exists()) JSONObject(file.readText()) else JSONObject()
 private fun save(state: JSONObject) { val tmp = File(file.parent, "usb-state.tmp"); tmp.writeText(state.toString()); check(tmp.renameTo(file)) }
 suspend fun hasSessions(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
  val state = load()
  if(state.has("boot") && state.getString("boot") != io.read("/proc/sys/kernel/random/boot_id")) { check(file.renameTo(File(file.parent, "usb-state.previous-boot.json"))); return@withLock false }
  state.optJSONObject("luns")?.length()?.let { it > 0 } ?: false
 } }
 private fun backend(lun: Lun, settings: Preferences, persist: () -> Unit = {}): UsbBackend = if(lun.path.contains("/functions/") && settings.usbSystem in listOf("auto", "configfs")) ConfigFsBackend(io, lun.path.substringBefore("/functions/"), persist) else LegacyBackend(io, when(settings.usbSystem) { "functions", "samsung" -> settings.usbSystem; else -> "setprop" })
 private fun fromId(id: String): UsbBackend = if(id.startsWith("configfs:")) ConfigFsBackend(io, id.removePrefix("configfs:")) else LegacyBackend(io, id)
 private fun backingFile(path: String): String {
  val candidate = when { path.startsWith("/0/") -> "/storage/emulated$path"; path.startsWith("/data/media/") -> path.replaceFirst("/data/media/", "/storage/emulated/"); else -> return path }
  return if(io.shell.run("if [ -r ${RootShell.quote(candidate)} ]; then echo yes; fi") == "yes") candidate else path
 }
 private fun <T> unbound(path: String, block: () -> T): T {
  if(!path.contains("/functions/")) return block()
  val udcPath = "${path.substringBefore("/functions/")}/UDC"; val controller = io.read(udcPath)
  if(controller.isNotBlank()) io.write(udcPath, "")
  try { return block() } finally { if(controller.isNotBlank()) io.write(udcPath, controller) }
 }
 suspend fun inspect(): UsbStatus = withContext(Dispatchers.IO) { mutex.withLock { inspectNow().also { _changes.tryEmit(it) } } }
 private fun inspectNow(): UsbStatus = try {
  check(io.shell.run("id -u") == "0") { "Root access denied; allow USBDroid in KernelSU / Magisk" }
  ensurePrivateGadget()
  val paths = io.shell.run("""
   for base in /config/usb_gadget /sys/kernel/config/usb_gadget; do for p in "${'$'}base"/*/functions/mass_storage.*/lun.*/file; do [ -f "${'$'}p" ] && readlink -f "${'$'}p"; done; done
   for p in /sys/class/android_usb/android*/f_mass_storage/lun*/file; do [ -f "${'$'}p" ] && readlink -f "${'$'}p"; done
   find /sys/devices/platform /sys/devices/gadget /sys/devices/soc /sys/devices/soc.0 /sys/devices/virtual/android_usb -maxdepth 9 -name file -path '*lun*' 2>/dev/null || true
  """.trimIndent()).lines().filter { it.startsWith("/") }.distinct()
  val luns = paths.mapNotNull { path -> runCatching { val p = path.removeSuffix("/file"); io.validate(p); val ro = io.exists("$p/ro"); val cd = io.exists("$p/cdrom"); Lun(p, backingFile(io.read(path)), if(ro) io.read("$p/ro") == "1" else true, cd && io.read("$p/cdrom") == "1", ro, cd) }.getOrNull() }
  val privateLuns = luns.filter { it.path.contains("/usb_gadget/usbdroid/") }
  val visibleLuns = if(privateLuns.isNotEmpty()) privateLuns + luns.filter { it.file.isNotBlank() && it !in privateLuns } else luns
  UsbStatus(true, io.shell.run("getprop sys.usb.state"), io.shell.run("cat /sys/class/udc/*/state 2>/dev/null || cat /sys/class/android_usb/android0/state 2>/dev/null || true"), visibleLuns)
 } catch(e: Exception) { UsbStatus(error = e.message) }
 suspend fun prepareConfigfs(): UsbStatus = withContext(Dispatchers.IO) { mutex.withLock {
  ensurePrivateGadget()
  inspectNow()
 } }
 private fun ensurePrivateGadget() {
  io.shell.run("""
   set -e
   for base in /config/usb_gadget /sys/kernel/config/usb_gadget; do
    [ -d "${'$'}base" ] || continue
    g="${'$'}base/usbdroid"
    [ -f "${'$'}g/functions/mass_storage.0/lun.0/file" ] && exit 0
    mkdir -p "${'$'}g"
    printf '0x18d1\n' > "${'$'}g/idVendor"
    printf '0x2d12\n' > "${'$'}g/idProduct"
    printf '0x0200\n' > "${'$'}g/bcdUSB"
    mkdir -p "${'$'}g/strings/0x409"
    printf 'USBDroid\n' > "${'$'}g/strings/0x409/manufacturer"
    printf 'USBDroid USB Disk\n' > "${'$'}g/strings/0x409/product"
    printf 'USBDroid-storage\n' > "${'$'}g/strings/0x409/serialnumber"
    mkdir -p "${'$'}g/configs/c.1"
    printf '250\n' > "${'$'}g/configs/c.1/MaxPower"
    mkdir -p "${'$'}g/functions/mass_storage.0"
    ln -s "${'$'}g/functions/mass_storage.0" "${'$'}g/configs/c.1/usbdroid-ms"
    exit 0
   done
  """.trimIndent())
 }
 private fun snapshotLun(lun: Lun) = JSONObject().put("file", backingFile(io.read("${lun.path}/file"))).put("ro", if(lun.supportsReadOnly) io.read("${lun.path}/ro") else "").put("cdrom", if(lun.supportsCdrom) io.read("${lun.path}/cdrom") else "").put("removable", if(io.exists("${lun.path}/removable")) io.read("${lun.path}/removable") else "").put("inquiry_string", if(io.exists("${lun.path}/inquiry_string")) io.read("${lun.path}/inquiry_string") else "")
 private fun restoreLun(path: String, state: JSONObject) { unbound(path) { io.validate(path); io.write("$path/file", ""); for(key in listOf("cdrom", "ro", "removable", "inquiry_string")) if(state.optString(key).isNotEmpty() || (key == "inquiry_string" && io.exists("$path/$key"))) io.write("$path/$key", state.getString(key)); io.write("$path/file", state.getString("file")) } }
 suspend fun host(image: DiskImage, lun: Lun, mode: HostMode, settings: Preferences) = withContext(Dispatchers.IO) { mutex.withLock {
  io.validate(lun.path); require(!mode.cd || lun.supportsCdrom); require(lun.supportsReadOnly || mode.ro == lun.readOnly)
  val path = io.shell.run("readlink -f ${RootShell.quote(image.path)}"); require(path.startsWith("/")); io.shell.run("test -r ${RootShell.quote(path)}")
  require(io.shell.run("stat -c '%s' ${RootShell.quote(path)}").toLong() >= 614400) { "Image too small" }
  var state = load(); val boot = io.read("/proc/sys/kernel/random/boot_id")
  if(state.optString("boot", boot) != boot) { file.renameTo(File(file.parent, "usb-state.previous-boot.json")); state = JSONObject() }
  state.put("boot", boot)
  val sessions = state.optJSONObject("luns") ?: JSONObject().also { state.put("luns", it) }
  val backends = state.optJSONObject("backends") ?: JSONObject().also { state.put("backends", it) }
  val b = backend(lun, settings) { save(state) }; val previous = snapshotLun(lun)
  val isNew = !sessions.has(lun.path)
  require(isNew || sessions.getJSONObject(lun.path).getString("backend") == b.id) { "Eject the image before changing its USB backend" }
  if(isNew) sessions.put(lun.path, previous.put("backend", b.id).put("image", path))
  val newBackend = !backends.has(b.id)
  if(newBackend) backends.put(b.id, b.capture(lun))
  val backendState = backends.getJSONObject(b.id)
  if(settings.usbCompatibility && settings.autoUsb && b.id.startsWith("configfs:") && !backendState.optBoolean("compatibility")) {
   val props = JSONObject()
   for(name in listOf("sys.usb.config", "sys.usb.configfs", "sys.usb.state")) props.put(name, io.shell.run("getprop $name"))
   backendState.put("usbProperties", props).put("compatibility", true)
  }
  val backendBefore = JSONObject(backendState.toString())
  val activeUdc = if(b.id.startsWith("configfs:")) io.read("${b.id.removePrefix("configfs:")}/UDC") else ""
  save(state)
  try {
   unbound(lun.path) {
   io.write("${lun.path}/file", "")
   if(io.exists("${lun.path}/removable")) io.write("${lun.path}/removable", "1")
   if(io.exists("${lun.path}/inquiry_string")) io.write("${lun.path}/inquiry_string", "USBDroid" + (if(mode.cd) "USB CD-ROM" else "USB Disk").padEnd(16) + "1.00")
   if(lun.supportsCdrom) io.write("${lun.path}/cdrom", if(mode.cd) "1" else "0")
   if(lun.supportsReadOnly) io.write("${lun.path}/ro", if(mode.ro) "1" else "0")
   io.write("${lun.path}/file", path)
   check(backingFile(io.read("${lun.path}/file")) == path) { "Kernel did not confirm image" }
   if(settings.autoUsb) { b.activate(lun, "mass_storage", backendState); save(state) }
   sessions.getJSONObject(lun.path).put("image", path).put("activeRo", mode.ro).put("activeCdrom", mode.cd); save(state)
   }
  } catch(e: Exception) {
   // Persist attempted links before rollback. Never discard a journal when restoration fails.
   save(state)
   val rollback = runCatching {
    restoreLun(lun.path, previous)
    if(newBackend) b.restore(backendState)
    else if(b.id.startsWith("configfs:")) {
     val delta = JSONObject(backendState.toString()); val added = JSONObject()
     val oldLinks = backendBefore.getJSONObject("links")
     val links = backendState.getJSONObject("links")
     for(link in links.keys()) if(!oldLinks.has(link)) added.put(link, links.getString(link))
     delta.put("links", added).put("hiddenLinks", JSONObject()).put("descriptors", JSONObject()).put("otherControllers", JSONObject()).put("boundByUs", false)
     delta.remove("usbProperties"); delta.put("compatibility", false)
     delta.put("udc", activeUdc)
     b.restore(delta); backends.put(b.id, backendBefore)
    }
   }
   if(rollback.isSuccess) { if(isNew) sessions.remove(lun.path); if(newBackend) backends.remove(b.id); save(state) }
   else e.addSuppressed(rollback.exceptionOrNull()!!)
   throw e
  }
 } }
 suspend fun maintainCompatibility(): CompatibilityCheck = withContext(Dispatchers.IO) { mutex.withLock {
  val state = load(); val sessions = state.optJSONObject("luns") ?: return@withLock CompatibilityCheck.NO_SESSIONS
  if(sessions.length() == 0) return@withLock CompatibilityCheck.NO_SESSIONS
  require(state.getString("boot") == io.read("/proc/sys/kernel/random/boot_id")) { "USB journal belongs to previous boot" }
  val backends = state.getJSONObject("backends")
  var repaired = false
  for(id in backends.keys()) {
   val snapshot = backends.getJSONObject(id)
   if(!id.startsWith("configfs:") || !snapshot.optBoolean("compatibility")) continue
   val luns = sessions.keys().asSequence().filter { sessions.getJSONObject(it).getString("backend") == id }.map { path ->
    val session = sessions.getJSONObject(path); val expected = session.getString("image")
    val current = backingFile(io.read("$path/file"))
    require(current.isBlank() || current == expected) { "USB image changed by another process" }
    if(current.isBlank()) {
     unbound(path) {
      if(session.has("activeRo") && io.exists("$path/ro")) io.write("$path/ro", if(session.getBoolean("activeRo")) "1" else "0")
      if(session.has("activeCdrom") && io.exists("$path/cdrom")) io.write("$path/cdrom", if(session.getBoolean("activeCdrom")) "1" else "0")
      io.write("$path/file", expected)
     }
     repaired = true
    }
    Lun(path, expected, true, false, false, false)
   }.toList()
   if(luns.isNotEmpty() && ConfigFsBackend(io, id.removePrefix("configfs:")) { save(state) }.maintain(luns, snapshot)) repaired = true
  }
  if(repaired) { save(state); _changes.tryEmit(inspectNow()) }
  if(repaired) CompatibilityCheck.REPAIRED else CompatibilityCheck.HEALTHY
 } }
 suspend fun eject(lun: Lun) = withContext(Dispatchers.IO) { mutex.withLock { val state = load(); restoreOne(state, lun.path); if(state.optJSONObject("luns")?.length() == 0) file.delete() } }
 private fun restoreOne(state: JSONObject, path: String) {
  val sessions = state.optJSONObject("luns") ?: error("No USBDroid session for this device")
  require(state.getString("boot") == io.read("/proc/sys/kernel/random/boot_id")) { "USB journal belongs to previous boot" }
  val session = sessions.optJSONObject(path) ?: error("Device is managed by another application")
  val id = session.getString("backend"); restoreLun(path, session)
  val others = sessions.keys().asSequence().filter { it != path }.any { sessions.getJSONObject(it).getString("backend") == id }
  if(!others) { val backends = state.getJSONObject("backends"); fromId(id).restore(backends.getJSONObject(id)); backends.remove(id) }
  sessions.remove(path); save(state)
 }
 suspend fun ejectAll() = withContext(Dispatchers.IO) { mutex.withLock { val state = load(); val sessions = state.optJSONObject("luns") ?: return@withLock; for(path in sessions.keys().asSequence().toList()) restoreOne(state, path); file.delete() } }
 suspend fun setMode(mode: String, permanent: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock { require(mode in listOf("none", "adb", "mtp", "mtp,adb", "mass_storage", "mass_storage,adb", "ptp", "ptp,adb", "rndis", "rndis,adb")); io.shell.run("setprop ${if(permanent) "persist.sys.usb.config" else "sys.usb.config"} ${RootShell.quote(mode)}") } }
 suspend fun report(): String {
  val status = inspect()
  return withContext(Dispatchers.IO) { mutex.withLock { buildString {
   appendLine("USBDroid ${dev.usbdroid.BuildConfig.VERSION_NAME}")
   appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT).format(java.util.Date())}")
   appendLine("\n[Device]")
   appendLine("Android ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
   appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}; device=${android.os.Build.DEVICE}")
   appendLine("Build: ${android.os.Build.DISPLAY}; security patch: ${android.os.Build.VERSION.SECURITY_PATCH}")
   appendLine("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
   appendLine("\n[USB]")
   appendLine("Root: ${status.root}; mode: ${status.mode}; cable: ${status.cable}")
   if(status.error != null) appendLine("Error: ${status.error}")
   status.luns.forEach { appendLine("${it.path}\n  file=${it.file}\n  readOnly=${it.readOnly}; cdrom=${it.cdrom}; supportsRO=${it.supportsReadOnly}; supportsCD=${it.supportsCdrom}") }
   if(status.root) {
    fun read(label: String, command: String) { appendLine("\n[$label]"); appendLine(runCatching { io.shell.run(command) }.getOrElse { "Unavailable: ${it.message}" }) }
    read("Kernel and SELinux", "uname -a; getenforce")
    read("USB properties", "for p in sys.usb.config sys.usb.state sys.usb.configfs sys.usb.controller persist.sys.usb.config; do printf '%s=' \"\$p\"; getprop \"\$p\"; done")
    read("Gadget links", "find /config/usb_gadget /sys/kernel/config/usb_gadget -maxdepth 4 -type l -exec ls -l {} \\; 2>/dev/null || true")
    read("USB controllers", "ls -l /sys/class/udc; for f in /config/usb_gadget/*/UDC /sys/kernel/config/usb_gadget/*/UDC; do if [ -f \"\$f\" ]; then printf '%s=' \"\$f\"; cat \"\$f\"; fi; done")
   }
   appendLine("\n[Recovery journal]")
   appendLine(if(file.exists()) runCatching { JSONObject(file.readText()).toString(2) }.getOrElse { "Unreadable: ${it.message}" } else "No active recovery journal")
  } } }
 }
}
