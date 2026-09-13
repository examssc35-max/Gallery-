package com.example.data.local

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.domain.model.Album
import com.example.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeletionResult {
    data object Success : DeletionResult()
    data class RequiresUserConsent(val intentSender: IntentSender, val uris: List<Uri>) : DeletionResult()
    data class Failure(val error: String) : DeletionResult()
}

class MediaStoreDataSource(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    fun observeMediaStore(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        val imagesUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val videosUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.registerContentObserver(imagesUri, true, observer)
        contentResolver.registerContentObserver(videosUri, true, observer)
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    suspend fun getMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        val projectionList = mutableListOf(
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        val imageProjection = projectionList.toTypedArray()

        val imageCollectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val videoCollectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val videoDurationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.DURATION
        } else {
            "duration"
        }

        // Query Images (including Screenshots across all standard directories and external volumes)
        try {
            contentResolver.query(
                imageCollectionUri,
                imageProjection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val dateModCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    if (idCol == -1) continue
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "IMG_$id.jpg" else "IMG_$id.jpg"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val rawDateAdded = if (dateAddedCol != -1) cursor.getLong(dateAddedCol) else 0L
                    val rawDateMod = if (dateModCol != -1) cursor.getLong(dateModCol) else 0L

                    val dateAdded = when {
                        rawDateAdded > 0L -> rawDateAdded * 1000L
                        rawDateMod > 0L -> rawDateMod * 1000L
                        else -> System.currentTimeMillis()
                    }
                    val dateMod = when {
                        rawDateMod > 0L -> rawDateMod * 1000L
                        else -> dateAdded
                    }

                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val rawBucket = if (bucketCol != -1) cursor.getString(bucketCol)?.trim() else null
                    val relativePath = if (relPathCol != -1) cursor.getString(relPathCol)?.trim() else null

                    // Determine the album name accurately, especially for Screenshots across Android versions & OEMs
                    val albumName = when {
                        !rawBucket.isNullOrBlank() && !rawBucket.equals("Camera", ignoreCase = true) -> rawBucket
                        !relativePath.isNullOrBlank() -> {
                            val segment = relativePath.trim('/').split('/').lastOrNull()?.trim()
                            if (!segment.isNullOrBlank()) segment else "Pictures"
                        }
                        path.isNotBlank() -> {
                            try {
                                File(path).parentFile?.name?.trim()?.takeIf { it.isNotBlank() } ?: "Pictures"
                            } catch (_: Exception) {
                                "Pictures"
                            }
                        }
                        name.contains("screenshot", ignoreCase = true) -> "Screenshots"
                        !rawBucket.isNullOrBlank() -> rawBucket
                        else -> "Pictures"
                    }

                    val finalAlbumName = if (albumName.equals("Camera", ignoreCase = true) &&
                        (name.contains("screenshot", ignoreCase = true) ||
                         relativePath?.contains("screenshot", ignoreCase = true) == true ||
                         path.contains("screenshot", ignoreCase = true))) {
                        "Screenshots"
                    } else {
                        albumName
                    }

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
                            albumName = finalAlbumName
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Query Videos
        try {
            val videoProjectionList = projectionList.toMutableList().apply {
                add(videoDurationCol)
            }
            val videoProjection = videoProjectionList.toTypedArray()

            contentResolver.query(
                videoCollectionUri,
                videoProjection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val dateModCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(videoDurationCol)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    if (idCol == -1) continue
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "VID_$id.mp4" else "VID_$id.mp4"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val rawDateAdded = if (dateAddedCol != -1) cursor.getLong(dateAddedCol) else 0L
                    val rawDateMod = if (dateModCol != -1) cursor.getLong(dateModCol) else 0L

                    val dateAdded = when {
                        rawDateAdded > 0L -> rawDateAdded * 1000L
                        rawDateMod > 0L -> rawDateMod * 1000L
                        else -> System.currentTimeMillis()
                    }
                    val dateMod = when {
                        rawDateMod > 0L -> rawDateMod * 1000L
                        else -> dateAdded
                    }

                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val rawBucket = if (bucketCol != -1) cursor.getString(bucketCol)?.trim() else null
                    val relativePath = if (relPathCol != -1) cursor.getString(relPathCol)?.trim() else null

                    val albumName = when {
                        !rawBucket.isNullOrBlank() -> rawBucket
                        !relativePath.isNullOrBlank() -> {
                            val segment = relativePath.trim('/').split('/').lastOrNull()?.trim()
                            if (!segment.isNullOrBlank()) segment else "Movies"
                        }
                        path.isNotBlank() -> {
                            try {
                                File(path).parentFile?.name?.trim()?.takeIf { it.isNotBlank() } ?: "Movies"
                            } catch (_: Exception) {
                                "Movies"
                            }
                        }
                        else -> "Movies"
                    }

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
                            albumName = albumName
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
