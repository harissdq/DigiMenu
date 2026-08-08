package com.digimenu.core.qr

/**
 * Canonical QR payload format for table and take-away QR codes.
 *
 * A table QR encodes the customer-facing menu page URL with the restaurant id
 * and the table id as query parameters. Scanning the QR opens that
 * restaurant's menu straight in the customer's phone browser (no customer app
 * needed) and pre-selects the table.
 *
 * A take-away QR has no table: it opens the same page in take-away mode where
 * the customer adds name, phone and a delivery address.
 *
 * The canonical payload is
 * `https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&table=Table_1`.
 * Change [WEB_BASE_URL] if the web page is hosted elsewhere.
 *
 * This class is deliberately pure JVM (no Android framework classes) so it can
 * be unit-tested on the JVM.
 */
object TableQrCode {

    /** Base URL of the customer web app. Must match where the site is deployed. */
    const val WEB_BASE_URL = "https://harissdq.github.io/DigiMenu/"

    const val RESTAURANT_QUERY_KEY = "restaurant"
    const val WEB_QUERY_KEY = "table"
    const val TAKEAWAY_QUERY_KEY = "takeaway"

    const val DEEP_LINK_SCHEME = "digimenu://table/"

    /** Canonical payload to embed in a QR code for a physical table. */
    fun encode(restaurantId: String, tableId: String): String =
        "$WEB_BASE_URL?$RESTAURANT_QUERY_KEY=" + urlEncode(normalize(restaurantId)) +
            "&$WEB_QUERY_KEY=" + urlEncode(normalize(tableId))

    /** Canonical payload for the public take-away QR (customers order from home). */
    fun encodeTakeaway(restaurantId: String): String =
        "$WEB_BASE_URL?$RESTAURANT_QUERY_KEY=" + urlEncode(normalize(restaurantId)) +
            "&$TAKEAWAY_QUERY_KEY=1"

    /** A decoded QR payload. For dine-in, [tableId] is set; for take-away [takeaway] is true. */
    data class TableQrPayload(
        val restaurantId: String,
        val tableId: String?,
        val takeaway: Boolean,
    )

    /**
     * Extracts the payload from a scanned code (web URL, deep link or raw id).
     * Returns `null` when the payload does not encode a valid table or
     * take-away code.
     */
    fun decode(raw: String): TableQrPayload? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        return when {
            text.startsWith("http://") || text.startsWith("https://") ->
                decodeWebUrl(text)

            text.startsWith(DEEP_LINK_SCHEME) ->
                validateId(text.removePrefix(DEEP_LINK_SCHEME))
                    ?.let { TableQrPayload(DEFAULT_RESTAURANT_ID, it, takeaway = false) }

            else -> // raw id, e.g. from a plain-text QR
                validateId(text)?.let { TableQrPayload(DEFAULT_RESTAURANT_ID, it, takeaway = false) }
        }
    }

    private fun decodeWebUrl(url: String): TableQrPayload? {
        val queryIndex = url.indexOf('?')
        if (queryIndex < 0) return null
        val params = parseQuery(url.substring(queryIndex + 1))
        val restaurantId = validateId(params[RESTAURANT_QUERY_KEY] ?: DEFAULT_RESTAURANT_ID)
            ?: return null
        if (params[TAKEAWAY_QUERY_KEY] == "1") {
            return TableQrPayload(restaurantId, tableId = null, takeaway = true)
        }
        val table = params[WEB_QUERY_KEY]
        if (table.isNullOrBlank()) return null
        return validateId(table)?.let { TableQrPayload(restaurantId, it, takeaway = false) }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val result = HashMap<String, String>()
        query.split('&').forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                result[part.substring(0, eq)] = urlDecode(part.substring(eq + 1))
            }
        }
        return result
    }

    private fun validateId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val id = normalize(raw)
        return id.takeIf { TABLE_ID_REGEX.matches(it) }
    }

    /** Trims and collapses inner whitespace to underscores ("table 1" -> "table_1"). */
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), "_")

    private fun urlEncode(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                ' ', '+', '&', '=', '?', '#', '%' -> sb.append(percentEncode(c))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun percentEncode(c: Char): String =
        String.format("%%%02X", c.code)

    private fun urlDecode(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    sb.append(hex.toChar())
                    i += 3
                    continue
                }
            }
            sb.append(if (c == '+') ' ' else c)
            i++
        }
        return sb.toString()
    }

    private val TABLE_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")

    private const val DEFAULT_RESTAURANT_ID = "demo-restaurant"
}
