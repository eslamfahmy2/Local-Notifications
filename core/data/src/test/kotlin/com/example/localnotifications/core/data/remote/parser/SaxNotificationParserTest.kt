package com.example.localnotifications.core.data.remote.parser

import com.example.localnotifications.core.data.remote.dto.NotificationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaxNotificationParserTest {

    private val parser = SaxNotificationParser()

    private fun parse(xml: String) = parser.parse(xml.byteInputStream())

    @Test
    fun `parses the real feed shape`() {
        val xml = javaClass.getResourceAsStream("/notifications.xml")!!
        val items = parser.parse(xml)

        assertEquals(7, items.size)
        assertEquals(NotificationDto(1, "Schedule me after 10 seconds.", 10), items.first())
        assertEquals(NotificationDto(7, "Schedule me after 3 minutes.", 180), items.last())
    }

    @Test
    fun `finds notifications regardless of wrapper elements`() {
        val items = parse("<root><a><notification><id>1</id><title>T</title><timeInSeconds>5</timeInSeconds></notification></a></root>")
        assertEquals(listOf(NotificationDto(1, "T", 5)), items)
    }

    @Test
    fun `trims whitespace and ignores unknown tags`() {
        val items = parse(
            """
            <notifications>
              <notification>
                <id> 2 </id>
                <title>  Hello  </title>
                <color>red</color>
                <timeInSeconds> 20 </timeInSeconds>
              </notification>
            </notifications>
            """.trimIndent(),
        )
        assertEquals(listOf(NotificationDto(2, "Hello", 20)), items)
    }

    @Test
    fun `missing or non numeric fields become null`() {
        val items = parse(
            "<n><notification><id>x</id><title>T</title></notification></n>",
        )
        assertEquals(listOf(NotificationDto(null, "T", null)), items)
    }

    @Test
    fun `fields do not leak between notifications`() {
        val items = parse(
            "<n><notification><id>1</id><title>A</title><timeInSeconds>1</timeInSeconds></notification>" +
                "<notification><id>2</id></notification></n>",
        )
        assertEquals(NotificationDto(2, null, null), items[1])
    }

    @Test
    fun `malformed xml throws parse exception`() {
        assertThrows(NotificationParseException::class.java) { parse("<notifications><notification>") }
    }

    @Test
    fun `empty input throws parse exception`() {
        assertThrows(NotificationParseException::class.java) { parse("") }
    }
}
