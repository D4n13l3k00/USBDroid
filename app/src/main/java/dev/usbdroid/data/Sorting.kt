package dev.usbdroid.data

import java.util.Locale

private fun String.sortText() = lowercase(Locale.ROOT)
fun sortedImages(images: List<ImageEntry>, sort: String, descending: Boolean, hostedPaths: Set<String> = emptySet()): List<ImageEntry> {
 val comparator = when(sort) { "size" -> compareBy<ImageEntry> { it.size }; "date" -> compareBy { it.modified }; else -> compareBy { it.title.sortText() } }.thenBy { it.title.sortText() }.thenBy { it.id }
 val withinGroup = if(descending) comparator.reversed() else comparator
 return images.sortedWith(compareByDescending<ImageEntry> { it.physicalPath != null && it.physicalPath in hostedPaths }.then(withinGroup))
}
fun sortedReleases(releases: List<Release>, sort: String, descending: Boolean): List<Release> {
 val comparator = when(sort) { "size" -> compareBy<Release> { it.size }; "version" -> Comparator { a, b -> naturalCompare(a.version, b.version) }; "arch" -> compareBy { it.arch.sortText() }; else -> compareBy { it.name.sortText() } }.thenBy { it.name.sortText() }.thenBy { it.version.sortText() }.thenBy { it.arch.sortText() }.thenBy { it.url }
 return releases.sortedWith(if(descending) comparator.reversed() else comparator)
}
fun sortedJobs(jobs: List<TransferJob>, sort: String, descending: Boolean): List<TransferJob> {
 val comparator = when(sort) { "date" -> compareBy<TransferJob> { it.createdAt }; "size" -> compareBy { it.total }; "progress" -> compareBy { if(it.total > 0) it.progress.toDouble() / it.total else -1.0 }; "state" -> compareBy { it.state }; else -> compareBy { it.title.sortText() } }.thenBy { it.title.sortText() }.thenBy { it.id }
 return jobs.sortedWith(if(descending) comparator.reversed() else comparator)
}
internal fun naturalCompare(a: String, b: String): Int {
 val parts = Regex("[0-9]+|[^0-9]+")
 val left = parts.findAll(a.sortText()).map { it.value }.toList(); val right = parts.findAll(b.sortText()).map { it.value }.toList()
 for(i in 0 until minOf(left.size, right.size)) {
  val x = left[i]; val y = right[i]
  val value = if(x.first().isDigit() && y.first().isDigit()) { val xn = x.trimStart('0').ifEmpty { "0" }; val yn = y.trimStart('0').ifEmpty { "0" }; xn.length.compareTo(yn.length).takeIf { it != 0 } ?: xn.compareTo(yn) } else x.compareTo(y)
  if(value != 0) return value
 }
 return left.size.compareTo(right.size)
}
