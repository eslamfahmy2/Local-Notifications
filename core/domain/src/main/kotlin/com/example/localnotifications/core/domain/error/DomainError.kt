package com.example.localnotifications.core.domain.error

/**
 * Failures the domain layer exposes. Data-layer exceptions (IOException, XmlPullParserException...)
 * are translated into these so upper layers never depend on infrastructure types.
 */
sealed class DomainError(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable? = null) : DomainError("Network error", cause)
    class Parsing(cause: Throwable? = null) : DomainError("Could not read notifications", cause)
    class NotFound(val id: Int) : DomainError("Notification $id not found")
    class Unknown(cause: Throwable? = null) : DomainError("Unexpected error", cause)
}
