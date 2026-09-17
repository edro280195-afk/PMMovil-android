package com.example.pmmovil.data.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface

fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val normalized = ((degrees % 360f) + 360f) % 360f
    if (normalized == 0f) return source
    val matrix = Matrix().apply { postRotate(normalized) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

fun applyExifOrientation(source: Bitmap, path: String): Bitmap {
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    var transform = true

    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> transform = false
    }

    if (!transform) return source
    return runCatching {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }.getOrElse { source }
}
