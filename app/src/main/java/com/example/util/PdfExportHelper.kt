package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfExportHelper {

    suspend fun exportScreenshotsToPdf(
        context: Context,
        items: List<ScreenshotItem>
    ): Result<File> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No screenshots selected"))
        }

        val pdfDocument = PdfDocument()

        try {
            var pageIndex = 1
            for (item in items) {
                val uri = item.contentUri ?: continue
                val bitmap: Bitmap? = try {
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

                if (bitmap != null) {
                    val pageWidth = 595
                    val pageHeight = 842
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex++).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    val availableWidth = pageWidth - 40f
                    val availableHeight = pageHeight - 40f
                    val scale = minOf(availableWidth / bitmap.width.toFloat(), availableHeight / bitmap.height.toFloat())
                    val destWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val destHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    val left = 20f + (availableWidth - destWidth) / 2f
                    val top = 20f + (availableHeight - destHeight) / 2f

                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, destWidth, destHeight, true)
                    canvas.drawBitmap(scaledBitmap, left, top, null)
                    pdfDocument.finishPage(page)
                }
            }

            val exportFolder = File(context.cacheDir, "pdf_exports")
            if (!exportFolder.exists()) {
                exportFolder.mkdirs()
            }
            val outputFile = File(exportFolder, "SnapSweep_Vault_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            // Also copy to MediaStore.Downloads on Android Q+ so it is accessible in user's Downloads
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, outputFile.name)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val downloadUri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (downloadUri != null) {
                        context.contentResolver.openOutputStream(downloadUri)?.use { outStream ->
                            outputFile.inputStream().use { inStream -> inStream.copyTo(outStream) }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            pdfDocument.close()
        }
    }

    fun sharePdf(context: Context, pdfFile: File) {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "SnapSweep Screenshots PDF")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Screenshots PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
