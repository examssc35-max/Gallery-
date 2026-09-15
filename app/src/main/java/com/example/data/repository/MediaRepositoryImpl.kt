package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DeletionResult
import com.example.data.local.MediaStoreDataSource
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.TrashEntity
import com.example.domain.model.Album
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.model.StorageStats
import com.example.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MediaRepositoryImpl(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val database: AppDatabase
) : MediaRepository {

    override suspend fun loadMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mediaStoreDataSource.getMediaItems()
        val favoriteIds = database.favoriteDao().getAllFavoriteIdsFlow().first().toSet()
        val trashIds = database.trashDao().getAllTrashItems().map { it.mediaStoreId }.toSet()

        // Filter out items in local trash and tag favorites
        items.filter { it.id !in trashIds }.map { item ->
            if (item.id in favoriteIds) {
                item.copy(isFavorite = true)
            } else {
                item
            }
        }
    }

    override fun observeMediaChanges(): Flow<Unit> {
        return mediaStoreDataSource.observeMediaStore()
    }

    override fun getMediaItemsFlow(): Flow<List<MediaItem>> = kotlinx.coroutines.flow.flow {
        emit(loadMediaItems())
        observeMediaChanges().collect {
            emit(loadMediaItems())
        }
    }

    override suspend fun getAlbums(mediaItems: List<MediaItem>): List<Album> {
        return mediaStoreDataSource.getAlbums(mediaItems)
    }

    override fun getFavoriteIdsFlow(): Flow<List<Long>> {
        return database.favoriteDao().getAllFavoriteIdsFlow()
    }

    override suspend fun toggleFavorite(mediaId: Long) = withContext(Dispatchers.IO) {
        if (database.favoriteDao().isFavorite(mediaId)) {
            database.favoriteDao().removeFavorite(mediaId)
        } else {
            database.favoriteDao().addFavorite(FavoriteEntity(mediaStoreId = mediaId))
        }
    }

    override suspend fun isFavorite(mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        database.favoriteDao().isFavorite(mediaId)
    }

    override suspend fun deleteMedia(items: List<MediaItem>): DeletionResult {
        return mediaStoreDataSource.deleteMediaItems(items)
    }

    override fun getTrashItemsFlow(): Flow<List<TrashEntity>> {
        return database.trashDao().getAllTrashItemsFlow()
    }

    override suspend fun moveToTrash(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        for (item in items) {
            database.trashDao().insertTrash(
                TrashEntity(
                    mediaStoreId = item.id,
                    uriString = item.uriString,
                    name = item.name,
                    size = item.size,
                    isVideo = item.isVideo
                )
            )
        }
    }

    override suspend fun restoreFromTrash(id: Long) = withContext(Dispatchers.IO) {
        database.trashDao().deleteTrashById(id)
    }

    override suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        database.trashDao().clearTrash()
    }

    override fun findDuplicates(items: List<MediaItem>): List<DuplicateGroup> {
        // Group by size and name or size (> 0)
        return items
            .filter { it.size > 1024L } // ignore zero or tiny files
            .groupBy { "${it.size}_${it.name.substringBeforeLast('.')}" }
            .filter { it.value.size > 1 }
            .map { (key, groupItems) ->
                DuplicateGroup(
                    signature = key,
                    size = groupItems.first().size,
                    items = groupItems
                )
            }
            .sortedByDescending { it.size * it.items.size }
    }

    override fun calculateStorageStats(
        localItems: List<MediaItem>,
        cloudItems: List<R2Item>
    ): StorageStats {
        var photoCount = 0
        var photoBytes = 0L
        var videoCount = 0
        var videoBytes = 0L

        for (item in localItems) {
            if (item.isVideo) {
                videoCount++
                videoBytes += item.size
            } else {
                photoCount++
                photoBytes += item.size
            }
        }

        val cloudFiles = cloudItems.filter { !it.isFolder }
        val cloudObjectCount = cloudFiles.size
        val cloudEstimatedBytes = cloudFiles.sumOf { it.size }

        return StorageStats(
            photoCount = photoCount,
            photoBytes = photoBytes,
            videoCount = videoCount,
            videoBytes = videoBytes,
            totalCount = photoCount + videoCount,
            totalBytes = photoBytes + videoBytes,
            cloudObjectCount = cloudObjectCount,
            cloudEstimatedBytes = cloudEstimatedBytes
        )
    }
}
