package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LongStitchHelper {

    /**
     * Stitches multiple screenshots vertically into a single tall continuous bitmap,
     * writes it to cacheDir/stitched_shares, and shares it via the native Share Sheet.
     */
    suspend fun stitchAndShare(
        context: Context,
        items: List<ScreenshotItem>
    ): Result<File> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No screenshots selected to stitch"))
        }

        try {
            val targetWidth = 1080 // Standardize width for crisp alignment & memory safety
            val loadedBitmaps = mutableListOf<Bitmap>()
            var totalHeight = 0

            for (item in items) {
                val uri = item.contentUri ?: continue
                val original: Bitmap? = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                if (original != null) {
                    val scale = targetWidth.toFloat() / original.width.toFloat()
                    val scaledHeight = (original.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(original, targetWidth, scaledHeight, true)
                    loadedBitmaps.add(scaled)
                    totalHeight += scaledHeight
                }
            }

            if (loadedBitmaps.isEmpty()) {
                return@withContext Result.failure(Exception("No readable screenshot images to stitch"))
            }

            // Create unified canvas and stitch
            val stitchedBitmap = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitchedBitmap)

            var currentY = 0f
            for (bitmap in loadedBitmaps) {
                canvas.drawBitmap(bitmap, 0f, currentY, null)
                currentY += bitmap.height
            }

            // Save to shared cache
            val outputDir = File(context.cacheDir, "stitched_shares")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, "SnapSweep_Stitched_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                stitchedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            // Expose via FileProvider and launch Share Sheet
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "SnapSweep Stitched Long Screenshot")
                putExtra(Intent.EXTRA_TEXT, "Stitched ${items.size} screenshots vertically with SnapSweep")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Stitched Screenshot").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Stitches screenshots vertically into a continuous tall Bitmap, saves it to
     * MediaStore / Kept Vault, marks it as Kept in the database, and launches Share Sheet.
     */
    suspend fun stitchAndSaveToVault(
        context: Context,
        items: List<ScreenshotItem>,
        repository: com.example.data.repository.ScreenshotRepository
    ): Result<File> = withContext(Dispatchers.IO) {
        val shareResult = stitchAndShare(context, items)
        if (shareResult.isFailure) {
            return@withContext shareResult
        }
        val file = shareResult.getOrThrow()

        try {
            val fileName = file.name
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            }

            // Create ScreenshotItem and insert into Kept Vault
            val newItem = ScreenshotItem(
                id = "stitched_${System.currentTimeMillis()}",
                contentUri = uri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                displayName = fileName,
                dateAddedMillis = System.currentTimeMillis(),
                sizeBytes = file.length(),
                isKept = true,
                category = "Memos",
                width = 1080,
                height = 1920
            )
            repository.markAsKept(newItem, "Memos")
            repository.scanRealMedia()

            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success(file)
        }
    }
}
