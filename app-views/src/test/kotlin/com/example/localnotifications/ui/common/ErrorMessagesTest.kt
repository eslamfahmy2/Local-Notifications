package com.example.localnotifications.ui.common

import com.example.localnotifications.R
import com.example.localnotifications.feature.detail.DetailError
import com.example.localnotifications.feature.list.ListError
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun `every list error has its own message`() {
        val expected = mapOf(
            ListError.Network to R.string.error_network,
            ListError.Parsing to R.string.error_parsing,
            ListError.Unknown to R.string.error_unknown,
        )

        assertEquals(ListError.entries.toSet(), expected.keys)
        expected.forEach { (error, message) -> assertEquals(error.name, message, error.messageRes()) }
        assertEquals(ListError.entries.size, ListError.entries.map { it.messageRes() }.toSet().size)
    }

    @Test
    fun `every detail error has its own message`() {
        val expected = mapOf(
            DetailError.NotFound to R.string.error_not_found,
            DetailError.Network to R.string.error_network,
            DetailError.Parsing to R.string.error_parsing,
            DetailError.Unknown to R.string.error_unknown,
        )

        assertEquals(DetailError.entries.toSet(), expected.keys)
        expected.forEach { (error, message) -> assertEquals(error.name, message, error.messageRes()) }
        assertEquals(DetailError.entries.size, DetailError.entries.map { it.messageRes() }.toSet().size)
    }

    @Test
    fun `list and detail use the same text for shared errors`() {
        assertEquals(ListError.Network.messageRes(), DetailError.Network.messageRes())
        assertEquals(ListError.Parsing.messageRes(), DetailError.Parsing.messageRes())
        assertEquals(ListError.Unknown.messageRes(), DetailError.Unknown.messageRes())
    }
}
