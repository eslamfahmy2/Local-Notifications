package com.example.localnotifications.core.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit

/** Checks the Retrofit contract itself: request shape and raw body, with no parsing involved. */
class NotificationApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: NotificationApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .build()
            .create(NotificationApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `sends a GET to localalerts php`() = runTest {
        server.enqueue(MockResponse().setBody("<xml/>"))

        api.getNotifications().close()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/localalerts.php", request.path)
    }

    @Test
    fun `returns the body unchanged`() = runTest {
        val body = "<xml><notifications></notifications></xml>"
        server.enqueue(MockResponse().setBody(body))

        assertEquals(body, api.getNotifications().use { it.string() })
    }

    @Test
    fun `non 2xx response throws HttpException with the code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching { api.getNotifications() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(503, (error as HttpException).code())
    }
}
