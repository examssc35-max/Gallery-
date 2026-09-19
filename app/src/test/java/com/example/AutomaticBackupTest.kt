package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BackupSettings
import com.example.data.local.DeletionResult
import com.example.data.local.PreferencesManager
import com.example.data.local.R2Credentials
import com.example.data.local.entity.BackupRecordEntity
import com.example.data.local.entity.TrashEntity
import com.example.data.repository.BackupRepositoryImpl
import com.example.domain.model.Album
import com.example.domain.model.BackupStatus
import com.example.domain.model.CloudFileType
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.MediaItem
import com.example.domain.model.R2Item
import com.example.domain.model.StorageStats
import com.example.domain.model.StorageUsage
import com.example.domain.repository.CloudMediaPage
import com.example.domain.repository.MediaRepository
import com.example.domain.repository.R2Repository
import com.example.domain.repository.UploadResult
import com.example.ui.backup.BackupViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomaticBackupTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var fakeMediaRepository: FakeMediaRepository
    private lateinit var fakeR2Repository: FakeR2Repository
    private lateinit var backupRepository: BackupRepositoryImpl
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesManager = PreferencesManager(context)
        fakeMediaRepository = FakeMediaRepository()
        fakeR2Repository = FakeR2Repository()

        backupRepository = BackupRepositoryImpl(
            context = context,
            mediaRepository = fakeMediaRepository,
            r2Repository = fakeR2Repository,
            database = database,
            preferencesManager = preferencesManager
        )

        viewModel = BackupViewModel(backupRepository, fakeR2Repository)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `test backup settings defaults and persistence`() = runBlocking {
        val initialSettings = preferencesManager.backupSettingsFlow.first()
        // Default photos and videos are both true for comprehensive media backup
        assertTrue(initialSettings.backupPhotos)
        assertTrue(initialSettings.backupVideos)
        assertFalse(initialSettings.isAutoBackupEnabled)

        // Update settings
        val updated = initialSettings.copy(
            isAutoBackupEnabled = true,
            wifiOnly = false,
            requireCharging = true
        )
        preferencesManager.updateBackupSettings(updated)

        val retrieved = preferencesManager.backupSettingsFlow.first()
        assertTrue(retrieved.isAutoBackupEnabled)
        assertFalse(retrieved.wifiOnly)
        assertTrue(retrieved.requireCharging)
    }

    @Test
    fun `test duplicate upload prevention via isAlreadyBackedUp`() = runBlocking {
        val testItem = MediaItem(
            id = 1001L,
            name = "test_photo.jpg",
            uriString = "content://media/external/images/media/1001",
            size = 5000L,
            dateModified = 1700000000L,
            isVideo = false
        )

        // Initially not backed up
        assertFalse(backupRepository.isAlreadyBackedUp(testItem))

        // Record successful backup
        database.backupRecordDao().insertOrUpdate(
            BackupRecordEntity(
                mediaStoreId = testItem.id,
                uriString = testItem.uriString,
                fileName = testItem.name,
                fileSize = testItem.size,
                dateModified = testItem.dateModified,
                remoteKey = "photos/test_photo.jpg",
                status = BackupStatus.COMPLETED.name,
                errorMessage = null,
                lastBackupTime = System.currentTimeMillis()
            )
        )

        // Now must be detected as already backed up
        assertTrue(backupRepository.isAlreadyBackedUp(testItem))

        // Upload single media should return AlreadyBackedUp
        val result = backupRepository.uploadSingleMedia(testItem)
        assertTrue(result is UploadResult.AlreadyBackedUp)
    }

    @Test
    fun `test accurate pending stats calculation`() = runBlocking {
        fakeMediaRepository.mediaList.clear()
        fakeMediaRepository.mediaList.addAll(
            listOf(
                MediaItem(id = 1L, name = "photo1.jpg", uriString = "content://media/1", size = 1000L, dateModified = 100L, isVideo = false),
                MediaItem(id = 2L, name = "photo2.jpg", uriString = "content://media/2", size = 2000L, dateModified = 200L, isVideo = false),
                MediaItem(id = 3L, name = "video1.mp4", uriString = "content://media/3", size = 50000L, dateModified = 300L, isVideo = true)
            )
        )

        val stats = backupRepository.getBackupStats()
        assertEquals(0, stats.backedUpCount)
        assertEquals(3, stats.pendingCount)
        assertEquals(0, stats.failedCount)

        // Simulate 1 item backed up
        database.backupRecordDao().insertOrUpdate(
            BackupRecordEntity(
                mediaStoreId = 1L,
                uriString = "content://media/1",
                fileName = "photo1.jpg",
                fileSize = 1000L,
                dateModified = 100L,
                remoteKey = "photos/photo1.jpg",
                status = BackupStatus.COMPLETED.name
            )
        )

        val updatedStats = backupRepository.getBackupStats()
        assertEquals(1, updatedStats.backedUpCount)
        assertEquals(2, updatedStats.pendingCount)
        assertEquals(0, updatedStats.failedCount)
    }

    @Test
    fun `test R2 connection requirement and state in ViewModel`() = runBlocking {
        // Disconnected
        fakeR2Repository.setCredentials(R2Credentials(secretAccessKey = "", bucketName = ""))
        viewModel.refreshStats()

        val state = viewModel.uiState.first()
        assertFalse(state.isR2Connected)

        // Triggering backup while disconnected shows message
        viewModel.triggerBackupNow()
        val afterTrigger = viewModel.uiState.first()
        assertEquals("Connect Cloudflare R2 to enable automatic backup", afterTrigger.statusMessage)

        // Connect R2
        fakeR2Repository.setCredentials(
            R2Credentials(
                accountId = "acc123",
                accessKeyId = "key123",
                secretAccessKey = "secret123",
                bucketName = "my-bucket",
                isVerified = true
            )
        )

        val connectedState = viewModel.uiState.first { it.isR2Connected }
        assertTrue(connectedState.isR2Connected)
    }

    @Test
    fun `test retry failed uploads removes deleted media records`() = runBlocking {
        // Record 1 for deleted item (not in media repository)
        database.backupRecordDao().insertOrUpdate(
            BackupRecordEntity(
                mediaStoreId = 999L,
                uriString = "content://media/999",
                fileName = "deleted.jpg",
                fileSize = 1000L,
                dateModified = 100L,
                remoteKey = "photos/deleted.jpg",
                status = BackupStatus.FAILED.name,
                errorMessage = "Error"
            )
        )

        val failedBefore = database.backupRecordDao().getRecordsByStatus(BackupStatus.FAILED.name)
        assertEquals(1, failedBefore.size)

        // Retry failed uploads should prune deleted media record
        backupRepository.retryFailedUploads()

        val failedAfter = database.backupRecordDao().getRecordsByStatus(BackupStatus.FAILED.name)
        assertEquals(0, failedAfter.size)
    }

    // Fake implementations for unit testing
    class FakeMediaRepository : MediaRepository {
        val mediaList = mutableListOf<MediaItem>()

        override suspend fun loadMediaItems(): List<MediaItem> = mediaList
        override fun observeMediaChanges(): Flow<Unit> = emptyFlow()
        override fun getMediaItemsFlow(): Flow<List<MediaItem>> = emptyFlow()
        override suspend fun getAlbums(mediaItems: List<MediaItem>): List<Album> = emptyList()
        override fun getFavoriteIdsFlow(): Flow<List<Long>> = emptyFlow()
        override suspend fun toggleFavorite(mediaId: Long) {}
        override suspend fun isFavorite(mediaId: Long): Boolean = false
        override suspend fun deleteMedia(items: List<MediaItem>): DeletionResult = DeletionResult.Success
        override fun getTrashItemsFlow(): Flow<List<TrashEntity>> = emptyFlow()
        override suspend fun moveToTrash(items: List<MediaItem>) {}
        override suspend fun restoreFromTrash(id: Long) {}
        override suspend fun emptyTrash() {}
        override fun findDuplicates(items: List<MediaItem>): List<DuplicateGroup> = emptyList()
        override fun calculateStorageStats(localItems: List<MediaItem>, cloudItems: List<R2Item>): StorageStats =
            StorageStats(0, 0L, 0, 0L, 0, 0L, 0, 0L)
    }

    class FakeR2Repository : R2Repository {
        private val _creds = MutableStateFlow(R2Credentials())
        override val credentialsFlow: Flow<R2Credentials> = _creds.asStateFlow()
        override val cachedStorageUsageFlow: Flow<StorageUsage?> = MutableStateFlow(null)

        fun setCredentials(creds: R2Credentials) {
            _creds.value = creds
        }

        override suspend fun getCredentials(): R2Credentials = _creds.value
        override suspend fun getCachedStorageUsage(): StorageUsage? = null
        override suspend fun testConnection(customCredentials: R2Credentials?): Result<Boolean> = Result.success(true)
        override suspend fun listObjects(prefix: String): Result<List<R2Item>> = Result.success(emptyList())
        override suspend fun calculateStorageUsage(onProgress: (scannedObjects: Int, scannedBytes: Long) -> Unit): Result<StorageUsage> =
            Result.success(StorageUsage(0, 0, 0, 0, 0, 0, 0, 0, System.currentTimeMillis()))
        override suspend fun listFolderPage(prefix: String, continuationToken: String?, pageSize: Int): Result<CloudMediaPage> =
            Result.success(CloudMediaPage(emptyList(), emptyList(), emptyList(), null, false))
        override suspend fun listGlobalMediaPage(fileType: CloudFileType, continuationToken: String?, targetPageSize: Int): Result<CloudMediaPage> =
            Result.success(CloudMediaPage(emptyList(), emptyList(), emptyList(), null, false))
        override suspend fun listCloudMediaPage(prefix: String, continuationToken: String?, pageSize: Int): Result<CloudMediaPage> =
            Result.success(CloudMediaPage(emptyList(), emptyList(), emptyList(), null, false))
        override suspend fun getPresignedUrl(key: String, expiresSeconds: Long): Result<String> = Result.success("https://example.com/$key")
        override suspend fun uploadMedia(item: MediaItem, onProgress: (bytes: Long, total: Long) -> Unit): Result<String> =
            Result.success("photos/${item.name}")
        override suspend fun uploadFile(fileName: String, prefix: String, inputStream: InputStream, contentLength: Long, mimeType: String, onProgress: (bytes: Long, total: Long) -> Unit): Result<String> =
            Result.success("$prefix$fileName")
        override suspend fun createFolder(folderKey: String): Result<Unit> = Result.success(Unit)
        override suspend fun downloadObject(r2Item: R2Item, destinationFile: File, onProgress: (bytes: Long, total: Long) -> Unit): Result<File> =
            Result.success(destinationFile)
        override suspend fun downloadKeyToFile(key: String, destinationFile: File, onProgress: (bytes: Long, total: Long) -> Unit): Result<File> =
            Result.success(destinationFile)
        override suspend fun deleteObject(key: String): Result<Unit> = Result.success(Unit)
        override suspend fun saveCredentials(credentials: R2Credentials) { _creds.value = credentials }
        override suspend fun disconnect() { _creds.value = R2Credentials() }
    }
}
