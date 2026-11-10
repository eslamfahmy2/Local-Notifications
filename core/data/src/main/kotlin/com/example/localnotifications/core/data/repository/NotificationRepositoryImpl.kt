package com.example.localnotifications.core.data.repository

import com.example.localnotifications.core.data.mapper.toDomainOrNull
import com.example.localnotifications.core.data.remote.NotificationApi
import com.example.localnotifications.core.data.remote.parser.NotificationParseException
import com.example.localnotifications.core.data.remote.parser.NotificationParser
import com.example.localnotifications.core.domain.error.DomainError
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.core.domain.repository.NotificationRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Loads notifications from the remote feed and keeps the last successful result in memory,
 * so detail lookups by id do not hit the network again.
 */
class NotificationRepositoryImpl @Inject constructor(
    private val api: NotificationApi,
    private val parser: NotificationParser,
    private val ioDispatcher: CoroutineDispatcher,
) : NotificationRepository {

    private val refreshLock = Mutex()

    @Volatile
    private var cache: List<NotificationItem> = emptyList()

    override suspend fun getNotifications(): Result<List<NotificationItem>> =
        runCatchingDomain { refresh() }

    override suspend fun getNotification(id: Int): Result<NotificationItem> =
        runCatchingDomain {
            cache.firstOrNull { it.id == id }
                ?: refresh().firstOrNull { it.id == id }
                ?: throw DomainError.NotFound(id)
        }

    private suspend fun refresh(): List<NotificationItem> = refreshLock.withLock {
        withContext(ioDispatcher) {
            api.getNotifications().use { body ->
                parser.parse(body.byteStream())
            }.mapNotNull { it.toDomainOrNull() }
        }.also { cache = it }
    }

    private suspend fun <T> runCatchingDomain(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e.toDomainError())
        }

    private fun Throwable.toDomainError(): DomainError = when (this) {
        is DomainError -> this
        is HttpException, is IOException -> DomainError.Network(this)
        is NotificationParseException -> DomainError.Parsing(this)
        else -> DomainError.Unknown(this)
    }
}
