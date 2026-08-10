package com.digimenu.manager.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/** Decodes a base64 JPEG/PNG into a bitmap, downscaled to at most [maxDim]. */
fun base64ToBitmap(data: String, maxDim: Int = 512): Bitmap? = runCatching {
    val bytes = Base64.decode(data, Base64.DEFAULT)
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}.getOrNull()

/** Downscales and JPEG-compresses bytes so the DB write stays small. */
fun compressPhoto(bytes: ByteArray, maxDim: Int = 720, quality: Int = 72): ByteArray? {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val w = src.width
    val h = src.height
    val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
    val out = Bitmap.createScaledBitmap(src, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
    if (out != src) src.recycle()
    val stream = java.io.ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    out.recycle()
    return stream.toByteArray()
}
