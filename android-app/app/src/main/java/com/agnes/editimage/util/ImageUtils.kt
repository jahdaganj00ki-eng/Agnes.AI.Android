package com.agnes.editimage.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

fun readBytes(context: Context, uri: Uri): ByteArray {
    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
}

fun imageDimensions(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.isEmpty()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return if (opts.outWidth > 0 && opts.outHeight > 0) {
        opts.outWidth to opts.outHeight
    } else {
        null
    }
}

fun decodeBitmap(bytes: ByteArray, maxSize: Int = 2048): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val name = "agnes_edit_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collection, values) ?: return false
        val ok = context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        ok
    } catch (_: Exception) {
        false
    }
}
