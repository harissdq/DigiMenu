package com.digimenu.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TableQrCodeTest {

    @Test
    fun `encode produces canonical web URL`() {
        assertEquals(
            "https://harissdq.github.io/DigiMenu/?table=Table_1",
            TableQrCode.encode("Table_1")
        )
    }

    @Test
    fun `encode normalizes inner whitespace`() {
        assertEquals(
            "https://harissdq.github.io/DigiMenu/?table=table_1",
            TableQrCode.encode("table 1")
        )
    }

    @Test
    fun `round-trip encode then decode yields the canonical id`() {
        val id = "Table_1"
        val payload = TableQrCode.encode(id)
        assertEquals(id, TableQrCode.decode(payload))
    }

    @Test
    fun `decodes web URL with query param`() {
        assertEquals("Table_1", TableQrCode.decode("https://harissdq.github.io/DigiMenu/?table=Table_1"))
    }

    @Test
    fun `decodes web URL ignoring other query params and order`() {
        assertEquals(
            "Table_2",
            TableQrCode.decode("https://digimenu.app/?utm_source=qr&table=Table_2")
        )
    }

    @Test
    fun `decodes percent-encoded and plus values`() {
        assertEquals(
            "Table_1",
            TableQrCode.decode("https://digimenu.app/?table=Table%5F1")
        )
    }

    @Test
    fun `decodes deep link`() {
        assertEquals("Table_1", TableQrCode.decode("digimenu://table/Table_1"))
    }

    @Test
    fun `decodes raw id`() {
        assertEquals("Table_1", TableQrCode.decode("Table_1"))
    }

    @Test
    fun `decode trims surrounding whitespace`() {
        assertEquals("Table_1", TableQrCode.decode("  Table_1  "))
    }

    @Test
    fun `normalize collapses multiple inner whitespace`() {
        assertEquals("Table_3", TableQrCode.normalize("Table    3"))
    }

    @Test
    fun `rejects empty and blank payloads`() {
        assertNull(TableQrCode.decode(""))
        assertNull(TableQrCode.decode("   "))
    }

    @Test
    fun `rejects payloads without a table param`() {
        assertNull(TableQrCode.decode("https://digimenu.app/"))
        assertNull(TableQrCode.decode("https://digimenu.app/?table="))
        assertNull(TableQrCode.decode("https://digimenu.app/?foo=bar"))
    }

    @Test
    fun `rejects invalid table ids`() {
        assertNull(TableQrCode.decode("https://digimenu.app/?table=has space!"))
        assertNull(TableQrCode.decode("https://digimenu.app/?table=".plus("x".repeat(65))))
    }

    @Test
    fun `accepts ids with underscores and hyphens`() {
        val id = "T-1_x"
        assertNotNull(TableQrCode.decode(TableQrCode.encode(id)))
        assertEquals(id, TableQrCode.decode(TableQrCode.encode(id)))
    }
}
