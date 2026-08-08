package com.digimenu.core.qr

/**
 * Canonical QR payload format for table QR codes.
 *
 * A table QR encodes the customer-facing menu page URL with the table id as a
 * query parameter. Scanning the QR opens the restaurant's menu straight in the
 * customer's phone browser (no customer app needed) and pre-selects the table.
 *
 * The canonical payload is `https://harissdq.github.io/DigiMenu/?table=Table_1`.
 * Change [WEB_BASE_URL] if the web page is hosted elsewhere.
 *
 * This class is deliberately pure JVM (no Android framework classes) so it can
 * be unit-tested on the JVM.
 */
object TableQrCode {

    /** Base URL of the customer web app. Must match where the site is deployed. */
    const val WEB_BASE_URL = "https://harissdq.github.io/DigiMenu/"

    const val WEB_QUERY_KEY = "table"

    const val DEEP_LINK_SCHEME = "digimenu://table/"

    /** Canonical payload to embed in the QR code for a table. */
    fun encode(tableId: String): String = "$WEB_BASE_URL?$WEB_QUERY_KEY=" + normalize(tableId)

    /**
     * Extracts the table id from a scanned payload (web URL, deep link or raw
     * id) so a printed code can be verified against the table registry.
     * Returns `null` when the payload does not encode a valid table id.
     */
    fun decode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        return when {
            text.startsWith("http://") || text.startsWith("https://") ->
                decodeWebUrl(text)

            text.startsWith(DEEP_LINK_SCHEME) ->
                validateId(text.removePrefix(DEEP_LINK_SCHEME))

            else -> validateId(text) // raw id, e.g. from a plain-text QR
        }
    }

    private fun decodeWebUrl(url: String): String? {
        val queryIndex = url.indexOf('?')
        if (queryIndex < 0) return null
        val query = url.substring(queryIndex + 1)
        return query.split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq > 0 && part.substring(0, eq) == WEB_QUERY_KEY) {
                    urlDecode(part.substring(eq + 1))
                } else {
                    null
                }
            }
            .firstOrNull { it.isNotBlank() }
            ?.let { validateId(it) }
    }

    private fun validateId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val id = normalize(raw)
        return id.takeIf { TABLE_ID_REGEX.matches(it) }
    }

    /** Trims and collapses inner whitespace to underscores ("table 1" -> "Table_1" as-is case). */
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), "_")

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
}
