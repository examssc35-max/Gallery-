package com.example.domain.repository

import com.example.data.local.DeletionResult
import com.example.data.local.entity.TrashEntity
import com.example.domain.model.Album
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.model.StorageStats
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun loadMediaItems(): List<MediaItem>
    fun observeMediaChanges(): Flow<Unit>
    suspend fun getAlbums(mediaItems: List<MediaItem>): List<Album>
    fun getFavoriteIdsFlow(): Flow<List<Long>>
    suspend fun toggleFavorite(mediaId: Long)
    suspend fun isFavorite(mediaId: Long): Boolean
    suspend fun deleteMedia(items: List<MediaItem>): DeletionResult

    // Trash / Recycle Bin
    fun getTrashItemsFlow(): Flow<List<TrashEntity>>
    suspend fun moveToTrash(items: List<MediaItem>)
    suspend fun restoreFromTrash(id: Long)
    suspend fun emptyTrash()

    // Duplicates & Storage
    fun findDuplicates(items: List<MediaItem>): List<DuplicateGroup>
    fun calculateStorageStats(localItems: List<MediaItem>, cloudItems: List<R2Item>): StorageStats
}
