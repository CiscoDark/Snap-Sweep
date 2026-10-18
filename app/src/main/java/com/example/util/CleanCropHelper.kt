package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CleanCropHelper {

    /**
     * Crops top 4% (status bar) and bottom 4% (navigation gesture bar) of the screenshot,
     * writes it to cacheDir/clean_shares, and opens the system Share Sheet.
     */
    suspend fun cropAndShare(
        context: Context,
        imageUri: Uri,
        displayName: String,
        topTrimPercent: Float = 0.04f,
        bottomTrimPercent: Float = 0.05f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val originalBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext Result.failure(Exception("Failed to open screenshot stream"))
            }

            val width = originalBitmap.width
            val height = originalBitmap.height

            // Calculate trim margins for status bar (top) and navigation gesture bar (bottom)
            val topMargin = (height * topTrimPercent.coerceIn(0f, 0.2f)).toInt().coerceAtLeast(0)
            val bottomMargin = (height * bottomTrimPercent.coerceIn(0f, 0.2f)).toInt().coerceAtLeast(0)
            val croppedHeight = (height - topMargin - bottomMargin).coerceAtLeast(100)

            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                topMargin,
                width,
                croppedHeight
            )

            // Save to shared cache directory
            val cacheFolder = File(context.cacheDir, "clean_shares")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val sanitizedName = displayName.filter { it.isLetterOrDigit() || it == '.' }
            val cleanFile = File(cacheFolder, "clean_${System.currentTimeMillis()}_$sanitizedName")
            FileOutputStream(cleanFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            // Expose via FileProvider
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cleanFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Clean Cropped Screenshot")
                putExtra(Intent.EXTRA_TEXT, "Shared with status & navigation bars trimmed via SnapSweep")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Clean Screenshot").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
