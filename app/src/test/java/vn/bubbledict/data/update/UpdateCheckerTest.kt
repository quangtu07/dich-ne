package vn.bubbledict.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F12 — đọc manifest cập nhật: so versionCode, chặn sha256 rác, lỗi mạng thì im lặng.
 * Không dùng mạng thật: MockWebServer.
 */
class UpdateCheckerTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    private val validSha = "3e2761fc15a8d6daf6ffb65a2affe5a14db3b0e7bb7e169f459e0fbf7f532e16"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker(current: Int = 1) = UpdateChecker(client = client, currentVersionCode = current)

    private fun url(): String = server.url("/update.json").toString()

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun newerVersion_returnsAvailable() = runBlocking {
        respond(
            """{"versionCode":2,"versionName":"1.0.1","apkUrl":"http://host/app.apk","sha256":"$validSha"}""",
        )
        val result = checker(current = 1).check(url())
        assertTrue(result is UpdateChecker.Result.Available)
        val manifest = (result as UpdateChecker.Result.Available).manifest
        assertEquals(2, manifest.versionCode)
        assertEquals("1.0.1", manifest.versionName)
    }

    @Test
    fun sameOrOlderVersion_returnsUpToDate() = runBlocking {
        respond("""{"versionCode":1,"apkUrl":"http://host/app.apk","sha256":"$validSha"}""")
        assertTrue(checker(current = 1).check(url()) is UpdateChecker.Result.UpToDate)

        respond("""{"versionCode":0,"apkUrl":"http://host/app.apk","sha256":"$validSha"}""")
        assertTrue(checker(current = 1).check(url()) is UpdateChecker.Result.UpToDate)
    }

    @Test
    fun malformedJson_failsBadManifest() = runBlocking {
        respond("""not json at all""")
        val failed = checker().check(url())
        assertEquals(
            UpdateErrorCodes.BAD_MANIFEST,
            (failed as UpdateChecker.Result.Failed).code,
        )
    }

    @Test
    fun invalidSha256_isRejectedEvenWhenNewer() = runBlocking {
        respond("""{"versionCode":2,"apkUrl":"http://host/app.apk","sha256":"abc123"}""")
        val failed = checker().check(url())
        assertEquals(
            UpdateErrorCodes.BAD_MANIFEST,
            (failed as UpdateChecker.Result.Failed).code,
        )
    }

    @Test
    fun httpError_failsNetwork() = runBlocking {
        respond("""{"versionCode":2,"apkUrl":"http://host/app.apk","sha256":"$validSha"}""", code = 500)
        val failed = checker().check(url())
        assertEquals(UpdateErrorCodes.NETWORK, (failed as UpdateChecker.Result.Failed).code)
    }

    @Test
    fun unknownFields_areIgnored() = runBlocking {
        respond(
            """{"versionCode":3,"apkUrl":"http://host/app.apk","sha256":"$validSha",
                "notes":"Sửa lỗi bong bóng","minSdk":26,"extra":{"a":1}}""",
        )
        val result = checker(current = 1).check(url())
        assertTrue(result is UpdateChecker.Result.Available)
        assertEquals("Sửa lỗi bong bóng", (result as UpdateChecker.Result.Available).manifest.notes)
    }

    @Test
    fun manifestWithoutApkUrl_isRejected() = runBlocking {
        respond("""{"versionCode":2,"sha256":"$validSha"}""")
        val failed = checker().check(url())
        assertEquals(UpdateErrorCodes.BAD_MANIFEST, (failed as UpdateChecker.Result.Failed).code)
    }
}