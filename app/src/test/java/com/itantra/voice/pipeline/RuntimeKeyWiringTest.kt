package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import com.itantra.voice.network.SarvamApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A key entered in the app must reach the HTTP request.
 *
 * `isAvailable()` and the network client used to read from two separate providers. The
 * status bar consulted the runtime key and said SARVAM CLOUD; every real request then
 * used the empty build-time key and failed with "key is missing". Asserting on the
 * outgoing header is the only check that catches that class of bug — a passing
 * `isAvailable()` proves nothing about what goes over the wire.
 */
class RuntimeKeyWiringTest {

    private class Capturing : Interceptor {
        var lastRequest: Request? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            lastRequest = chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"translated_text":"ok","transcript":"ok","audios":["QUJD"]}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun http(capturing: Capturing) = OkHttpClient.Builder().addInterceptor(capturing).build()

    @Test
    fun `translator sends the runtime key, not the build-time one`() = runTest {
        val capturing = Capturing()
        val translator = SarvamTranslator(
            apiKeyProvider = { "runtime-key-from-app" },
            customHttpClient = http(capturing)
        )

        assertTrue(translator.isAvailable())
        val result = translator.translate("नमस्ते", Language.HINDI, Language.ENGLISH)

        assertTrue("translate failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(
            "runtime-key-from-app",
            capturing.lastRequest!!.header(SarvamApiClient.AUTH_HEADER)
        )
    }

    @Test
    fun `speech pipeline sends the runtime key on every stage`() = runTest {
        val capturing = Capturing()
        val pipeline = SarvamSpeechPipeline(
            apiKeyProvider = { "runtime-key-from-app" },
            customHttpClient = http(capturing),
            base64Decoder = { ByteArray(3) }
        )

        pipeline.transcribe(ByteArray(64), Language.HINDI).getOrThrow()
        assertEquals("runtime-key-from-app", capturing.lastRequest!!.header(SarvamApiClient.AUTH_HEADER))

        pipeline.translate("x", Language.HINDI, Language.ENGLISH).getOrThrow()
        assertEquals("runtime-key-from-app", capturing.lastRequest!!.header(SarvamApiClient.AUTH_HEADER))

        pipeline.synthesize("x", Language.ENGLISH, isEmergency = false).getOrThrow()
        assertEquals("runtime-key-from-app", capturing.lastRequest!!.header(SarvamApiClient.AUTH_HEADER))
    }

    @Test
    fun `a key changed after construction is picked up without rebuilding`() = runTest {
        // The operator pastes a key after the pipeline was built in onCreate. The provider
        // is a lambda read per request, so the new value must flow through.
        var key = ""
        val capturing = Capturing()
        val translator = SarvamTranslator(
            apiKeyProvider = { key },
            customHttpClient = http(capturing)
        )
        assertTrue(!translator.isAvailable())

        key = "pasted-later"
        assertTrue(translator.isAvailable())
        translator.translate("x", Language.HINDI, Language.ENGLISH).getOrThrow()
        assertEquals("pasted-later", capturing.lastRequest!!.header(SarvamApiClient.AUTH_HEADER))
    }
}
