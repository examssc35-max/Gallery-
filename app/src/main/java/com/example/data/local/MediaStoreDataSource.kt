package com.example.data.local

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeletionResult {
    data object Success : DeletionResult()
    data class RequiresUserConsent(val intentSender: IntentSender, val uris: List<Uri>) : DeletionResult()
    data class Failure(val error: String) : DeletionResult()
}

class MediaStoreDataSource(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun getMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )

        val videoDurationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.DURATION
        } else {
            "duration"
        }

        // Query Images
        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Camera" else "Camera"

                    items.add(
                        MediaItem(
                            id = id,
                            uriString = contentUri.toString(),
                            name = name,
                            path = path,
                            size = size,
                            dateAdded = dateAdded,
                            dateModified = dateMod,
                            mimeType = mime,
                            isVideo = false,
                            durationMs = 0L,
                            width = width,
                            height = height,
                            albumName = bucket
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Query Videos
        try {
            val videoProjection = projection.toMutableList().apply {
                add(videoDurationCol)
            }.toTypedArray()

            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(videoDurationCol)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = cursor.getString(nameCol) ?: "VID_$id.mp4"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Movies" else "Movies"
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L

                    items.add(
                        MediaItem(
                            id = id,
                            uriString = contentUri.toString(),
                            name = name,
                            path = path,
                            size = size,
                            dateAdded = dateAdded,
                            dateModified = dateMod,
                            mimeType = mime,
                            isVideo = true,
                            durationMs = duration,
                            width = width,
                            height = height,
                            albumName = bucket
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Sort all items newest first
        items.sortedByDescending { it.dateAdded }
    }

    suspend fun getAlbums(mediaItems: List<MediaItem>): List<Album> = withContext(Dispatchers.Default) {
        val groups = mediaItems.groupBy { it.albumName }
        groups.map { (name, items) ->
            val cover = items.firstOrNull()?.uriString ?: ""
            val hasOnlyVideos = items.all { it.isVideo }
            Album(
                id = name,
                name = name,
                coverUri = cover,
                itemCount = items.size,
                isVideoAlbum = hasOnlyVideos
            )
        }.sortedByDescending { it.itemCount }
    }

    suspend fun deleteMediaItems(items: List<MediaItem>): DeletionResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext DeletionResult.Success
        val uris = items.map { Uri.parse(it.uriString) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                return@withContext DeletionResult.RequiresUserConsent(pendingIntent.intentSender, uris)
            } catch (e: Exception) {
                return@withContext DeletionResult.Failure("Failed to initiate delete: ${e.message}")
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10 scoped storage
            for (uri in uris) {
                try {
                    val rows = contentResolver.delete(uri, null, null)
                    if (rows == 0) {
                        return@withContext DeletionResult.Failure("Could not delete item: $uri")
                    }
                } catch (e: RecoverableSecurityException) {
                    return@withContext DeletionResult.RequiresUserConsent(
                        e.userAction.actionIntent.intentSender,
                        listOf(uri)
                    )
                } catch (e: Exception) {
                    return@withContext DeletionResult.Failure(e.message ?: "Deletion error")
                }
            }
            return@withContext DeletionResult.Success
        } else {
            // Pre-Android 10
            var deletedAll = true
            for (uri in uris) {
                try {
                    val rows = contentResolver.delete(uri, null, null)
                    if (rows == 0) deletedAll = false
                } catch (e: Exception) {
                    return@withContext DeletionResult.Failure(e.message ?: "Deletion error")
                }
            }
            return@withContext if (deletedAll) DeletionResult.Success else DeletionResult.Failure("Some files could not be deleted")
        }
    }
}
