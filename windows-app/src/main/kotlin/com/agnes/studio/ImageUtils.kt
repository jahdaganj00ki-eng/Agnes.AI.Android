package com.agnes.studio

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.io.File
import java.util.concurrent.TimeUnit

fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

fun imageDimensions(bytes: ByteArray): Pair<Int, Int>? = try {
    val img = Image.makeFromEncoded(bytes)
    if (img.width <= 0 || img.height <= 0) null else img.width to img.height
} catch (_: Exception) {
    null
}

fun saveBytes(bytes: ByteArray, file: File): Boolean = try {
    file.writeBytes(bytes)
    true
} catch (_: Exception) {
    false
}

suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
        resp.body?.bytes() ?: ByteArray(0)
    }
}
