package dev.usbdroid.files

object TransferPolicy {
 fun sources(relatives: List<String>, parent: String): List<String> {
  AccessPolicy.relative(parent)
  require(relatives.isNotEmpty())
  val sources = relatives.distinct().onEach { require(it.isNotBlank()); AccessPolicy.relative(it) }
   .filter { child -> relatives.none { other -> other != child && child.startsWith("$other/") } }
  require(sources.map { it.substringAfterLast('/') }.distinct().size == sources.size) { "Duplicate destination names" }
  require(sources.none { parent == it || parent.startsWith("$it/") }) { "Cannot copy a folder into itself" }
  return sources
 }
}
