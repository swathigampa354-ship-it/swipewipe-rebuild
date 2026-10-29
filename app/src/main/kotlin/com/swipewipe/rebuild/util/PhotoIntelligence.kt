package com.swipewipe.rebuild.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.security.MessageDigest

object PhotoIntelligence {

    fun averageHash(source: InputStream): ByteArray {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeStream(source, null, opts)
            ?: return ByteArray(0)
        val small = extractThumbnail(bmp, 9, 9)
        if (small !== bmp) bmp.recycle()
        val w = small.width
        val h = small.height
        val gray = LongArray(w * h)
        var sum = 0L
        fun lum(px: Int): Long {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = lum(small.getPixel(x, y))
                gray[y * w + x] = v
                sum += v
            }
        }
        val mean = sum / (w * h)
        var bits = StringBuilder(w * h)
        for (v in gray) bits.append(if (v >= mean) '1' else '0')
        val bytes = ByteArray((bits.length + 7) / 8)
        bits.forEachIndexed { i, c ->
            if (c == '1') bytes[i / 8] = (bytes[i / 8].toInt() or (0x80 shr (i % 8))).toByte()
        }
        small.recycle()
        return bytes
    }

    private fun extractThumbnail(src: Bitmap, tw: Int, th: Int): Bitmap {
        val scale = maxOf(tw * 1.0 / src.width, th * 1.0 / src.height)
        val w = Math.max(1, Math.round(src.width * scale))
        val h = Math.max(1, Math.round(src.height * scale))
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun hammingDistance(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        var d = 0
        for (i in 0 until n) {
            var x = a[i].toInt() xor b[i].toInt()
            while (x != 0) {
                x = x and (x - 1)
                d++
            }
        }
        val extra = Math.abs(a.size - b.size) * 8
        return d + extra
    }

    fun similarDistanceThreshold(): Int = 16

    fun exifRotateDegrees(source: InputStream): Int {
        val exif = ExifInterface(source)
        val orient = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        return when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    fun applyRotation(bmp: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bmp else Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(degrees.toFloat()) }, true
        )

    fun sha256Prefix(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).take(12)
            .joinToString("") { "%02x".format(it) }
}
