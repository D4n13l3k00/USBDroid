package dev.usbdroid.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File

object Catalog {
 fun parse(text: String, repository: CatalogRepository): List<Release> {
  val array = JSONArray(text)
  return buildList { for(i in 0 until array.length()) { val distro = array.getJSONObject(i); val releases = distro.optJSONArray("releases") ?: continue
   for(j in 0 until releases.length()) { val item = releases.getJSONObject(j); add(Release(distro.getString("name"), item.optString("version"), item.optString("arch"), item.getString("url"), item.optLong("size"), repository.allowHttp)) }
  } }
 }
 fun load(client: OkHttpClient, repositories: List<CatalogRepository>, cache: File): Pair<List<Release>, List<String>> {
  val releases = mutableListOf<Release>(); val warnings = mutableListOf<String>()
  for(repo in repositories.filter { it.enabled }) {
   val file = File(cache, "catalog-${repo.id.hashCode()}.json")
   try {
    require(repo.url.startsWith("https://") || repo.allowHttp && repo.url.startsWith("http://")) { "HTTPS required" }
    val text = client.newCall(Request.Builder().url(repo.url).build()).execute().use { require(it.request.url.isHttps || repo.allowHttp); check(it.isSuccessful) { "HTTP ${it.code}" }; it.body!!.string() }
    val parsed = parse(text, repo); file.writeText(text); releases += parsed
   } catch(e: Exception) { warnings += "${repo.title}: ${e.message}"; if(file.exists()) releases += runCatching { parse(file.readText(), repo) }.getOrDefault(emptyList()) }
  }
  return releases to warnings
 }
}
