package dev.usbdroid
import dev.usbdroid.ui.ErrorKind
import dev.usbdroid.ui.errorKind
import org.junit.Assert.*
import org.junit.Test

class ErrorKindTest {
 @Test fun usbTimeoutIsNotReportedAsNetworkError() { assertEquals(ErrorKind.HOST, errorKind("USB connection timed out")) }
 @Test fun changedImageHasConflictExplanation() { assertEquals(ErrorKind.CONFLICT, errorKind("Image changed since preview; check it again")) }
 @Test fun spaceAndImageErrorsHaveSpecificTitles() {
  assertEquals(ErrorKind.SPACE, errorKind("No space left on device"))
  assertEquals(ErrorKind.IMAGE, errorKind("Filesystem was not mounted"))
 }
}
