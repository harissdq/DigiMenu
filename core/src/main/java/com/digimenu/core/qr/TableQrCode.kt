package com.digimenu.core.qr

import android.net.Uri

/** Outcome of parsing a scanned QR payload. */
sealed interface QrParseResult {
    /** The QR encoded a table and decoded to its canonical id. */
    data class Table(val tableId: String) : QrParseResult

    /** The QR payload did not map to a table. */
    data object NotATable : QrParseResult
}

/**
 * Canonical QR payload format + parser for table QR codes.
 *
 * A table QR encodes exactly one piece of information — the canonical table id
 * (e.g. "Table_1") — wrapped in a stable, versionable payload. The manager app
 * generates it with [encode]; the customer app decodes it with [decode]. No
 * server round-trip is required to turn a payload into a table id, which makes
 * scanning work offline (verification against the table registry is a separate,
 * optional step in [QrTableResolver]).
 *
 * All of the following payloads resolve to the same table id:
 *  - Deep link : digimenu://table/Table_1
 *  - Web URL   : https://digimenu.app/t/Table_1
 *  - Web URL   : https://digimenu.app/?table=Table_1
 *  - Raw id    : Table_1
 */
object TableQrCode {

    const val SCHEME = "digimenu"
    const val HOST = "table"
    const val WEB_HOST = "digimenu.app"
    const val WEB_QUERY_KEY = "table"

    /** Canonical payload to embed in the QR code for a table. */
    fun encode(tableId: String): String = "$SCHEME://$HOST/" + normalize(tableId)

    fun decode(raw: String): QrParseResult {
        val id = tryDecode(raw) ?: return QrParseResult.NotATable
        return QrParseResult.Table(id)
    }

    private fun tryDecode(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        return when {
            text.startsWith("$SCHEME://") -> {
                val uri = Uri.parse(text)
                if (uri.host != HOST) null
                else validateId(uri.path?.trim('/'))
            }

            text.startsWith("http://") || text.startsWith("https://") -> {
                val uri = Uri.parse(text)
                if (uri.host != WEB_HOST) return null
                uri.getQueryParameter(WEB_QUERY_KEY)?.takeIf { it.isNotBlank() }
                    ?.let { return validateId(it) }
                val segments = uri.pathSegments
                when {
                    segments.size >= 2 && segments[0] == "t" -> validateId(segments[1])
                    segments.size == 1 -> validateId(segments[0])
                    else -> null
                }
            }

            else -> validateId(text) // raw id, e.g. from a plain text QR
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
