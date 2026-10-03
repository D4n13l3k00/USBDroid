package dev.usbdroid
import dev.usbdroid.data.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class DownloadTest {
 private fun test(block: (MockWebServer, File, ResumableDownload) -> Unit) { val server = MockWebServer(); server.start(); val file = File.createTempFile("partial", ".img"); try { block(server, file, ResumableDownload(OkHttpClient())) } finally { server.shutdown(); file.delete() } }
 @Test fun rangeContinuesSameEntity() = test { server, file, downloader ->
  file.writeText("abc"); server.enqueue(MockResponse().setResponseCode(206).addHeader("ETag", "v1").addHeader("Content-Range", "bytes 3-5/6").setBody("def"))
  downloader.download(server.url("/image").toString(), file, DownloadIdentity("v1"), true, { false }, { _, _ -> }, { _, _ -> })
  assertEquals("abcdef", file.readText()); val request = server.takeRequest(); assertEquals("bytes=3-", request.getHeader("Range")); assertEquals("v1", request.getHeader("If-Range"))
 }
 @Test fun ignoredRangeRestartsSafely() = test { server, file, downloader ->
  file.writeText("old"); server.enqueue(MockResponse().addHeader("ETag", "v2").setBody("new image"))
  downloader.download(server.url("/image").toString(), file, DownloadIdentity("v1"), true, { false }, { _, _ -> }, { _, _ -> }); assertEquals("new image", file.readText())
 }
 @Test fun changedEntityNeverAppends() = test { server, file, downloader ->
  file.writeText("abc"); server.enqueue(MockResponse().setResponseCode(206).addHeader("ETag", "v2").addHeader("Content-Range", "bytes 3-5/6").setBody("XYZ"))
  assertThrows(IllegalArgumentException::class.java) { downloader.download(server.url("/image").toString(), file, DownloadIdentity("v1"), true, { false }, { _, _ -> }, { _, _ -> }) }; assertEquals("abc", file.readText())
 }
 @Test fun invalidRangeRejected() = test { server, file, downloader ->
  file.writeText("abc"); server.enqueue(MockResponse().setResponseCode(206).addHeader("ETag", "v1").addHeader("Content-Range", "bytes 2-4/5").setBody("XYZ"))
  assertThrows(IllegalArgumentException::class.java) { downloader.download(server.url("/image").toString(), file, DownloadIdentity("v1"), true, { false }, { _, _ -> }, { _, _ -> }) }
 }
 @Test fun httpRequiresExplicitOptIn() = test { server, file, downloader -> assertThrows(IllegalArgumentException::class.java) { downloader.download(server.url("/").toString(), file, DownloadIdentity(), false, { false }, { _, _ -> }, { _, _ -> }) } }
 @Test fun catalogRetainsVersionsAndArchitectures() { val releases = Catalog.parse("""[{"name":"Linux","releases":[{"version":"1","arch":"amd64","url":"https://a/1.iso","size":123},{"version":"2","arch":"arm64","url":"https://a/2.iso"}]}]""", CatalogRepository("test", "Test", "https://a")); assertEquals(2, releases.size); assertEquals("arm64", releases[1].arch); assertEquals(123L, releases[0].size) }
}
