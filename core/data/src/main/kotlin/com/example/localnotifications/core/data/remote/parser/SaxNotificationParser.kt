package com.example.localnotifications.core.data.remote.parser

import com.example.localnotifications.core.data.remote.dto.NotificationDto
import java.io.InputStream
import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * SAX based parser: streaming, no extra dependency, and identical behaviour on the JVM and Android.
 *
 * It looks for `<notification>` elements wherever they are, so the wrapper elements around them
 * (the feed uses `<xml>` then `<notifications>`) do not matter. Unknown tags are ignored.
 */
class SaxNotificationParser @Inject constructor() : NotificationParser {

    override fun parse(input: InputStream): List<NotificationDto> {
        val handler = NotificationHandler()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                hardenAgainstExternalEntities()
            }
            factory.newSAXParser().parse(input, handler)
        } catch (e: SAXException) {
            throw NotificationParseException(e)
        } catch (e: ParserConfigurationException) {
            throw NotificationParseException(e)
        }
        return handler.items
    }

    /** Best effort: not every parser implementation (e.g. Android's) supports every feature. */
    private fun SAXParserFactory.hardenAgainstExternalEntities() {
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        ).forEach { (feature, value) ->
            runCatching { setFeature(feature, value) }
        }
    }

    private class NotificationHandler : DefaultHandler() {

        val items = mutableListOf<NotificationDto>()

        private val text = StringBuilder()
        private var id: Int? = null
        private var title: String? = null
        private var timeInSeconds: Int? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            text.setLength(0)
            if (qName == TAG_NOTIFICATION) {
                id = null
                title = null
                timeInSeconds = null
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName) {
                TAG_ID -> id = text.toString().trim().toIntOrNull()
                TAG_TITLE -> title = text.toString().trim()
                TAG_TIME -> timeInSeconds = text.toString().trim().toIntOrNull()
                TAG_NOTIFICATION -> items += NotificationDto(id, title, timeInSeconds)
            }
        }

        private companion object {
            const val TAG_NOTIFICATION = "notification"
            const val TAG_ID = "id"
            const val TAG_TITLE = "title"
            const val TAG_TIME = "timeInSeconds"
        }
    }
}
