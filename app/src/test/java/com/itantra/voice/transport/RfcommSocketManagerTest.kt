package com.itantra.voice.transport

import com.itantra.voice.transport.bluetooth.RfcommSocketManager
import com.itantra.voice.transport.framing.MessageFramer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class RfcommSocketManagerTest {

    @Test
    fun testInitialSocketStateIsDisconnected() = runTest {
        val manager = RfcommSocketManager(
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertEquals(false, manager.isConnected)
        manager.disconnect()
        assertEquals(false, manager.isConnected)
    }

    @Test
    fun testFramedStreamingOverPipedStreams() = runBlocking(Dispatchers.IO) {
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut)

        val testMessage = TransportMessage(
            sourceLanguage = "en-IN",
            targetLanguage = "hi-IN",
            text = "Hello via Bluetooth RFCOMM"
        )

        // Write framed message into piped output stream
        MessageFramer.writeFrame(testMessage, pipedOut)

        // Read framed message back from piped input stream
        val readMessage = MessageFramer.readFrame(pipedIn)

        assertEquals(testMessage.messageId, readMessage.messageId)
        assertEquals("Hello via Bluetooth RFCOMM", readMessage.text)
        assertEquals("en-IN", readMessage.sourceLanguage)
        assertEquals("hi-IN", readMessage.targetLanguage)

        pipedIn.close()
        pipedOut.close()
    }
}
