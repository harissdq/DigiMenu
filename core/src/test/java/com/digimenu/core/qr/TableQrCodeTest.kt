package com.digimenu.core.qr

import com.digimenu.core.qr.TableQrCode.TableQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableQrCodeTest {

    @Test
    fun `encode produces canonical web URL`() {
        assertEquals(
            "https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&table=Table_1",
            TableQrCode.encode("demo-restaurant", "Table_1")
        )
    }

    @Test
    fun `encode normalizes inner whitespace`() {
        assertEquals(
            "https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&table=table_1",
            TableQrCode.encode("demo-restaurant", "table 1")
        )
    }

    @Test
    fun `round-trip encode then decode yields the canonical payload`() {
        val payload = TableQrCode.encode("demo-restaurant", "Table_1")
        val decoded = TableQrCode.decode(payload)
        assertNotNull(decoded)
        assertEquals("demo-restaurant", decoded!!.restaurantId)
        assertEquals("Table_1", decoded.tableId)
        assertFalse(decoded.takeaway)
    }

    @Test
    fun `decodes web URL with table param`() {
        val payload = TableQrCode.decode("https://harissdq.github.io/DigiMenu/?restaurant=my-bistro&table=Table_1")
        assertNotNull(payload)
        assertEquals("my-bistro", payload!!.restaurantId)
        assertEquals("Table_1", payload.tableId)
        assertFalse(payload.takeaway)
    }

    @Test
    fun `decodes web URL ignoring other query params and order`() {
        val payload = TableQrCode.decode("https://digimenu.app/?utm_source=qr&table=Table_2&restaurant=b")
        assertNotNull(payload)
        assertEquals("b", payload!!.restaurantId)
        assertEquals("Table_2", payload.tableId)
    }

    @Test
    fun `decodes percent-encoded and plus values`() {
        val payload = TableQrCode.decode("https://digimenu.app/?restaurant=my+bistro&table=Table%5F1")
        assertNotNull(payload)
        assertEquals("my_bistro", payload!!.restaurantId)
        assertEquals("Table_1", payload.tableId)
    }

    @Test
    fun `decodes takeaway payload`() {
        val payload = TableQrCode.decode("https://digimenu.app/?restaurant=my-bistro&takeaway=1")
        assertNotNull(payload)
        assertEquals("my-bistro", payload!!.restaurantId)
        assertNull(payload.tableId)
        assertTrue(payload.takeaway)
    }

    @Test
    fun `encodeTakeaway produces a takeaway payload`() {
        val encoded = TableQrCode.encodeTakeaway("my-bistro")
        assertEquals(
            "https://harissdq.github.io/DigiMenu/?restaurant=my-bistro&takeaway=1",
            encoded
        )
        val payload = TableQrCode.decode(encoded)
        assertNotNull(payload)
        assertTrue(payload!!.takeaway)
    }

    @Test
    fun `web URL without restaurant falls back to the demo tenant`() {
        val payload = TableQrCode.decode("https://harissdq.github.io/DigiMenu/?table=Table_1")
        assertNotNull(payload)
        assertEquals("demo-restaurant", payload!!.restaurantId)
        assertEquals("Table_1", payload.tableId)
    }

    @Test
    fun `decodes deep link`() {
        val payload = TableQrCode.decode("digimenu://table/Table_1")
        assertNotNull(payload)
        assertEquals("Table_1", payload!!.tableId)
    }

    @Test
    fun `decodes raw id`() {
        val payload = TableQrCode.decode("Table_1")
        assertNotNull(payload)
        assertEquals("Table_1", payload!!.tableId)
    }

    @Test
    fun `decode trims surrounding whitespace`() {
        val payload = TableQrCode.decode("  Table_1  ")
        assertNotNull(payload)
        assertEquals("Table_1", payload!!.tableId)
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
    fun `rejects payloads without a table or takeaway param`() {
        assertNull(TableQrCode.decode("https://digimenu.app/"))
        assertNull(TableQrCode.decode("https://digimenu.app/?table="))
        assertNull(TableQrCode.decode("https://digimenu.app/?foo=bar"))
    }

    @Test
    fun `rejects invalid table ids`() {
        assertNull(TableQrCode.decode("https://digimenu.app/?restaurant=b&table=has space!"))
        assertNull(TableQrCode.decode("https://digimenu.app/?restaurant=b&table=".plus("x".repeat(65))))
    }

    @Test
    fun `accepts ids with underscores and hyphens`() {
        val id = "T-1_x"
        val payload = TableQrCode.decode(TableQrCode.encode("demo-restaurant", id))
        assertNotNull(payload)
        assertEquals(id, payload!!.tableId)
    }
}
