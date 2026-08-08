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
 */
object TableQrCode {

    /** Base URL of the customer web app. Must match where the site is deployed. */
    const val WEB_BASE_URL = "https://harissdq.github.io/DigiMenu/"

    const val WEB_QUERY_KEY = "table"

    /** Canonical payload to embed in the QR code for a table. */
    fun encode(tableId: String): String = "$WEB_BASE_URL?$WEB_QUERY_KEY=" + normalize(tableId)

    /**
     * Extracts the table id from a scanned payload (web URL, deep link or raw
     * id) so a printed code can be verified against the table registry.
     */
    fun decode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        return when {
            text.startsWith("http://") || text.startsWith("https://") -> {
                val uri = android.net.Uri.parse(text)
                uri.getQueryParameter(WEB_QUERY_KEY)?.takeIf { it.isNotBlank() }
                    ?.let { return validateId(it) }
                null
            }

            else -> validateId(text) // raw id or deep link
        }
    }

    private fun validateId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val id = normalize(raw)
        return id.takeIf { TABLE_ID_REGEX.matches(it) }
    }

    /** Trims and collapses inner whitespace to underscores ("table 1" -> "Table_1" as-is case). */
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), "_")

    private val TABLE_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
}
