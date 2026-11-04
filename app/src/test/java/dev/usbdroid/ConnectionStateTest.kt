package dev.usbdroid

import dev.usbdroid.usb.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateTest {
 @Test fun configurationIsDistinctFromCable() { assertEquals(ConnectionState.ATTACHED, ConnectionState.from("addressed")); assertEquals(ConnectionState.CONFIGURED, ConnectionState.from("CONFIGURED")) }
 @Test fun suspendDoesNotClaimConfiguration() { assertEquals(ConnectionState.SUSPENDED, ConnectionState.from("suspended")) }
 @Test fun conflictingControllersAreUnknown() { assertEquals(ConnectionState.UNKNOWN, ConnectionState.from("configured\nnot attached")) }
 @Test fun missingStateIsUnknown() { assertEquals(ConnectionState.UNKNOWN, ConnectionState.from("")); assertEquals(ConnectionState.DETACHED, ConnectionState.from("not-attached")) }
}
