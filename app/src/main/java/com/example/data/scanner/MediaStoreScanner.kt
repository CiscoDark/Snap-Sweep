package com.example.data.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.model.ScreenshotItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreScanner {

    fun hasReadImagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    suspend fun scanScreenshots(context: Context, installTimestamp: Long = 0L): List<ScreenshotItem> = withContext(Dispatchers.IO) {
        if (!hasReadImagesPermission(context)) {
            return@withContext emptyList()
        }

        val items = mutableListOf<ScreenshotItem>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.RELATIVE_PATH)
            projection.add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        } else {
            projection.add(MediaStore.Images.Media.DATA)
        }

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        }

        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("%Screenshots%", "%Screenshots%", "%Screenshot%")
        } else {
            arrayOf("%Screenshots%", "%Screenshot%")
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    c.getColumnIndex(MediaStore.Images.Media.DATA)
                }

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val displayName = c.getString(nameCol) ?: "Screenshot_$id.png"
                    val dateAddedSec = c.getLong(dateCol)
                    val dateAddedMillis = if (dateAddedSec > 0) dateAddedSec * 1000L else System.currentTimeMillis()
                    val sizeBytes = c.getLong(sizeCol)
                    val width = if (widthCol != -1) c.getInt(widthCol) else 1080
                    val height = if (heightCol != -1) c.getInt(heightCol) else 2400
                    val relativePath = if (pathCol != -1) c.getString(pathCol) ?: "Pictures/Screenshots" else "Pictures/Screenshots"

                    val isLegacy = installTimestamp > 0L && dateAddedMillis < installTimestamp

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    items.add(
                        ScreenshotItem(
                            id = id.toString(),
                            contentUri = contentUri,
                            displayName = displayName,
                            dateAddedMillis = dateAddedMillis,
                            sizeBytes = if (sizeBytes > 0) sizeBytes else 1_850_000L,
                            width = if (width > 0) width else 1080,
                            height = if (height > 0) height else 2400,
                            relativePath = relativePath,
                            isLegacy = isLegacy
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items
    }
}
