package com.example.localnotifications.core.data.repository

import com.example.localnotifications.core.data.remote.NotificationApi
import com.example.localnotifications.core.data.remote.parser.SaxNotificationParser
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class NotificationRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: NotificationRepositoryImpl

    private val feed: String =
        javaClass.getResourceAsStream("/notifications.xml")!!.bufferedReader().readText()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .build()
            .create(NotificationApi::class.java)
        repository = NotificationRepositoryImpl(
            api = api,
            parser = SaxNotificationParser(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getNotifications returns parsed items`() = runTest {
        server.enqueue(MockResponse().setBody(feed))

        val items = repository.getNotifications().getOrThrow()

        assertEquals(7, items.size)
        assertEquals(NotificationItem(7, "Schedule me after 3 minutes.", 180), items.last())
        assertEquals("/localalerts.php", server.takeRequest().path)
    }

    @Test
    fun `getNotifications always refreshes from the network`() = runTest {
        server.enqueue(MockResponse().setBody(feed))
        server.enqueue(MockResponse().setBody(feed))

        repository.getNotifications()
        repository.getNotifications()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `invalid items are skipped but valid ones are kept`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                "<n><notification><id>1</id><title>Ok</title><timeInSeconds>5</timeInSeconds></notification>" +
                    "<notification><id>2</id><title></title><timeInSeconds>5</timeInSeconds></notification></n>",
            ),
        )

        assertEquals(listOf(NotificationItem(1, "Ok", 5)), repository.getNotifications().getOrThrow())
    }

    @Test
    fun `getNotification uses the cache after a list load`() = runTest {
        server.enqueue(MockResponse().setBody(feed))
        repository.getNotifications()

        val item = repository.getNotification(4).getOrThrow()

        assertEquals(40, item.delaySeconds)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `getNotification loads from the network when cache is empty`() = runTest {
        server.enqueue(MockResponse().setBody(feed))

        assertEquals(3, repository.getNotification(3).getOrThrow().id)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `getNotification fails with NotFound for unknown id`() = runTest {
        server.enqueue(MockResponse().setBody(feed))

        val error = repository.getNotification(99).exceptionOrNull()

        assertTrue(error is DomainError.NotFound)
        assertEquals(99, (error as DomainError.NotFound).id)
    }

    @Test
    fun `failed refresh keeps the previously loaded cache`() = runTest {
        server.enqueue(MockResponse().setBody(feed))
        repository.getNotifications()
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(repository.getNotifications().exceptionOrNull() is DomainError.Network)

        assertEquals(40, repository.getNotification(4).getOrThrow().delaySeconds)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `empty feed returns an empty list`() = runTest {
        server.enqueue(MockResponse().setBody("<xml><notifications></notifications></xml>"))

        assertEquals(emptyList<NotificationItem>(), repository.getNotifications().getOrThrow())
    }

    @Test
    fun `http error maps to Network`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(repository.getNotifications().exceptionOrNull() is DomainError.Network)
    }

    @Test
    fun `connection failure maps to Network`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertTrue(repository.getNotifications().exceptionOrNull() is DomainError.Network)
    }

    @Test
    fun `malformed body maps to Parsing`() = runTest {
        server.enqueue(MockResponse().setBody("<notifications><notification>"))

        assertTrue(repository.getNotifications().exceptionOrNull() is DomainError.Parsing)
    }
}
