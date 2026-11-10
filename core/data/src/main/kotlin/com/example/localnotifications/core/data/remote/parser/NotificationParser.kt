package com.example.localnotifications.core.data.remote.parser

import com.example.localnotifications.core.data.remote.dto.NotificationDto
import java.io.InputStream

/** Turns a feed document into raw items. */
interface NotificationParser {

    /**
     * @throws NotificationParseException if the content is not a readable document.
     * @throws java.io.IOException if reading the stream fails.
     */
    fun parse(input: InputStream): List<NotificationDto>
}

/** The document was reached but is not well-formed. */
class NotificationParseException(cause: Throwable) : Exception("Malformed notification feed", cause)
