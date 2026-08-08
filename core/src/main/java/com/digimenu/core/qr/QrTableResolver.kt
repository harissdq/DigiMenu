package com.digimenu.core.qr

import com.digimenu.core.data.TableRepository
import com.digimenu.core.firebase.FirebaseRefs
import javax.inject.Inject
import javax.inject.Singleton

/** Result of turning a scanned payload into a usable, verified table. */
sealed interface QrResolution {
    /** The table exists and is ready to order from. */
    data class Valid(val tableId: String, val tableLabel: String) : QrResolution

    /** The payload parsed as a table but that table is not in the registry. */
    data class UnknownTable(val tableId: String) : QrResolution

    /** The payload is not a table QR at all. */
    data object NotATable : QrResolution

    /** Could not reach the database to verify the table. */
    data object Offline : QrResolution
}

/**
 * End-to-end QR-to-table mapping: decode the payload with [TableQrCode], then
 * verify it against the restaurant's table registry so only real tables can
 * start an order. The customer app uses this the moment a QR is scanned.
 */
@Singleton
class QrTableResolver @Inject constructor(
    private val tableRepository: TableRepository,
) {

    suspend fun resolve(
        raw: String,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ): QrResolution {
        val parsed = TableQrCode.decode(raw)
        if (parsed !is QrParseResult.Table) return QrResolution.NotATable
        return try {
            if (tableRepository.exists(parsed.tableId, restaurantId)) {
                QrResolution.Valid(
                    tableId = parsed.tableId,
                    tableLabel = parsed.tableId,
                )
            } else {
                QrResolution.UnknownTable(parsed.tableId)
            }
        } catch (_: Throwable) {
            QrResolution.Offline
        }
    }
}
