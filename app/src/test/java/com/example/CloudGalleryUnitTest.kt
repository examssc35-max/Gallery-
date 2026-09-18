package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloudflare.AwsSigV4Signer
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BackupRecordEntity
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.TrashEntity
import com.example.domain.model.MediaItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudGalleryUnitTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `test SigV4 signer produces valid authorization header`() {
        val signResult = AwsSigV4Signer.sign(
            method = "GET",
            host = "1234567890abcdef.r2.cloudflarestorage.com",
            path = "/my-bucket",
            queryParams = mapOf("list-type" to "2"),
            headers = mapOf("Host" to "1234567890abcdef.r2.cloudflarestorage.com"),
            payloadHash = AwsSigV4Signer.sha256Hex(ByteArray(0)),
            accessKeyId = "testAccessKey",
            secretAccessKey = "testSecretKey"
        )

        assertNotNull(signResult.authorization)
        assertTrue(signResult.authorization.startsWith("AWS4-HMAC-SHA256 Credential=testAccessKey/"))
        assertNotNull(signResult.amzDate)
        assertNotNull(signResult.payloadHash)
    }

    @Test
    fun `test room database backup record insert and query`() = runBlocking {
        val record = BackupRecordEntity(
            mediaStoreId = 101L,
            uriString = "content://media/101",
            fileName = "photo.jpg",
            fileSize = 2048L,
            dateModified = System.currentTimeMillis(),
            remoteKey = "2026/09/photo.jpg",
            status = "COMPLETED",
            errorMessage = null,
            lastBackupTime = System.currentTimeMillis()
        )

        database.backupRecordDao().insertOrUpdate(record)
        val retrieved = database.backupRecordDao().getRecordById(101L)

        assertNotNull(retrieved)
        assertEquals("photo.jpg", retrieved?.fileName)
        assertEquals("COMPLETED", retrieved?.status)
    }

    @Test
    fun `test favorite entity toggle in room database`() = runBlocking {
        database.favoriteDao().addFavorite(FavoriteEntity(mediaStoreId = 55L))
        assertTrue(database.favoriteDao().isFavorite(55L))

        database.favoriteDao().removeFavorite(55L)
        assertTrue(!database.favoriteDao().isFavorite(55L))
    }

    @Test
    fun `test trash entity insert and restore`() = runBlocking {
        val trash = TrashEntity(
            mediaStoreId = 202L,
            uriString = "content://media/202",
            name = "video.mp4",
            size = 10485760L,
            isVideo = true,
            deletedAt = System.currentTimeMillis()
        )

        database.trashDao().insertTrash(trash)
        val trashedItems = database.trashDao().getAllTrashItems()
        assertEquals(1, trashedItems.size)
        assertEquals(202L, trashedItems[0].mediaStoreId)

        database.trashDao().deleteTrashById(202L)
        assertEquals(0, database.trashDao().getAllTrashItems().size)
    }

    @Test
    fun `test duplicate grouping logic`() {
        val items = listOf(
            MediaItem(id = 1L, uriString = "content://1", name = "IMG_001.jpg", size = 5000L, mimeType = "image/jpeg", dateAdded = 100L, dateModified = 100L, durationMs = 0L, isVideo = false, albumName = "Camera", path = "/a/img1.jpg"),
            MediaItem(id = 2L, uriString = "content://2", name = "IMG_001.jpg", size = 5000L, mimeType = "image/jpeg", dateAdded = 110L, dateModified = 110L, durationMs = 0L, isVideo = false, albumName = "Downloads", path = "/b/img1.jpg"),
            MediaItem(id = 3L, uriString = "content://3", name = "OTHER.jpg", size = 8000L, mimeType = "image/jpeg", dateAdded = 120L, dateModified = 120L, durationMs = 0L, isVideo = false, albumName = "Camera", path = "/a/other.jpg")
        )

        val duplicates = items.groupBy { "${it.name}_${it.size}" }
            .filter { it.value.size > 1 }

        assertEquals(1, duplicates.size)
        assertEquals(2, duplicates.values.first().size)
    }

    @Test
    fun `test media viewer list bounds and safe initial index`() {
        val mediaList = listOf(
            MediaItem(id = 10L, uriString = "content://10", name = "photo1.jpg"),
            MediaItem(id = 11L, uriString = "content://11", name = "photo2.jpg"),
            MediaItem(id = 12L, uriString = "content://12", name = "photo3.jpg")
        )

        // Safe index coercion
        val initialIndexFirst = 0.coerceIn(0, mediaList.size - 1)
        assertEquals(0, initialIndexFirst)
        assertEquals("photo1.jpg", mediaList[initialIndexFirst].name)

        val initialIndexMid = 1.coerceIn(0, mediaList.size - 1)
        assertEquals(1, initialIndexMid)
        assertEquals("photo2.jpg", mediaList[initialIndexMid].name)

        val initialIndexLast = 2.coerceIn(0, mediaList.size - 1)
        assertEquals(2, initialIndexLast)
        assertEquals("photo3.jpg", mediaList[initialIndexLast].name)

        // Boundary checks
        val outOfBoundsNegative = (-5).coerceIn(0, mediaList.size - 1)
        assertEquals(0, outOfBoundsNegative)

        val outOfBoundsHigh = (99).coerceIn(0, mediaList.size - 1)
        assertEquals(2, outOfBoundsHigh)
    }

    @Test
    fun `test local rule-based AI provider maps video query to searchLocalMedia tool call`() = runBlocking {
        val provider = com.example.ai.provider.LocalRuleBasedAiProvider()
        val request = com.example.ai.provider.AiRequest(
            messages = listOf(
                com.example.ai.model.AiMessage(
                    sender = com.example.ai.model.MessageSender.USER,
                    text = "Find my videos"
                )
            ),
            systemInstruction = ""
        )

        val response = provider.generateResponse(request)
        assertNotNull(response.toolCall)
        assertEquals("searchLocalMedia", response.toolCall?.toolName)
        assertEquals("video", response.toolCall?.arguments?.get("mediaType"))
    }

    @Test
    fun `test local rule-based AI provider maps storage usage query to getStorageUsage tool call`() = runBlocking {
        val provider = com.example.ai.provider.LocalRuleBasedAiProvider()
        val request = com.example.ai.provider.AiRequest(
            messages = listOf(
                com.example.ai.model.AiMessage(
                    sender = com.example.ai.model.MessageSender.USER,
                    text = "How much R2 storage am I using?"
                )
            ),
            systemInstruction = ""
        )

        val response = provider.generateResponse(request)
        assertNotNull(response.toolCall)
        assertEquals("getStorageUsage", response.toolCall?.toolName)
    }

    @Test
    fun `test local rule-based AI provider maps R2 connection test`() = runBlocking {
        val provider = com.example.ai.provider.LocalRuleBasedAiProvider()
        val request = com.example.ai.provider.AiRequest(
            messages = listOf(
                com.example.ai.model.AiMessage(
                    sender = com.example.ai.model.MessageSender.USER,
                    text = "Check my R2 connection"
                )
            ),
            systemInstruction = ""
        )

        val response = provider.generateResponse(request)
        assertNotNull(response.toolCall)
        assertEquals("testR2Connection", response.toolCall?.toolName)
    }

    @Test
    fun `test local rule-based AI provider maps backup request`() = runBlocking {
        val provider = com.example.ai.provider.LocalRuleBasedAiProvider()
        val request = com.example.ai.provider.AiRequest(
            messages = listOf(
                com.example.ai.model.AiMessage(
                    sender = com.example.ai.model.MessageSender.USER,
                    text = "Back up my videos"
                )
            ),
            systemInstruction = ""
        )

        val response = provider.generateResponse(request)
        assertNotNull(response.toolCall)
        assertEquals("startBackup", response.toolCall?.toolName)
        assertEquals("true", response.toolCall?.arguments?.get("onlyVideos"))
    }

    @Test
    fun `test local rule-based AI provider maps failure diagnostic`() = runBlocking {
        val provider = com.example.ai.provider.LocalRuleBasedAiProvider()
        val request = com.example.ai.provider.AiRequest(
            messages = listOf(
                com.example.ai.model.AiMessage(
                    sender = com.example.ai.model.MessageSender.USER,
                    text = "Why did my upload fail?"
                )
            ),
            systemInstruction = ""
        )

        val response = provider.generateResponse(request)
        assertNotNull(response.toolCall)
        assertEquals("explainUploadFailure", response.toolCall?.toolName)
    }

    @Test
    fun `test local rule-based AI provider maps duplicate finder`() = runBlocking {
        val provider = com.example.ai.provider.LocalRuleBasedAiProvider()
        val request = com.example.ai.provider.AiRequest(
            messages = listOf(
                com.example.ai.model.AiMessage(
                    sender = com.example.ai.model.MessageSender.USER,
                    text = "Find duplicate files"
                )
            ),
            systemInstruction = ""
        )

        val response = provider.generateResponse(request)
        assertNotNull(response.toolCall)
        assertEquals("findDuplicates", response.toolCall?.toolName)
    }

    @Test
    fun `test CloudFileTypeResolver resolves categories correctly`() {
        val imageType = com.example.domain.model.CloudFileTypeResolver.resolve("image/jpeg", "vacation/2026/beach.jpg")
        assertEquals(com.example.domain.model.CloudFileType.IMAGE, imageType)

        val videoType = com.example.domain.model.CloudFileTypeResolver.resolve("video/mp4", "drone_footage/clip.mp4")
        assertEquals(com.example.domain.model.CloudFileType.VIDEO, videoType)

        val audioType = com.example.domain.model.CloudFileTypeResolver.resolve("audio/mpeg", "podcasts/episode1.mp3")
        assertEquals(com.example.domain.model.CloudFileType.AUDIO, audioType)

        val docType = com.example.domain.model.CloudFileTypeResolver.resolve("application/pdf", "receipts/invoice.pdf")
        assertEquals(com.example.domain.model.CloudFileType.DOCUMENT, docType)

        val archiveType = com.example.domain.model.CloudFileTypeResolver.resolve("application/zip", "backup.zip")
        assertEquals(com.example.domain.model.CloudFileType.ARCHIVE, archiveType)

        val folderType = com.example.domain.model.CloudFileTypeResolver.resolve(null, "photos/")
        assertEquals(com.example.domain.model.CloudFileType.FOLDER, folderType)
    }

    @Test
    fun `test global file type filter does not mutate folder prefix and filters correctly`() {
        // Sample R2 items representing a bucket with root files and subfolder files
        val allBucketItems = listOf(
            com.example.domain.model.R2Item(
                key = "photos/",
                name = "photos",
                size = 0L,
                lastModified = 100L,
                isFolder = true
            ),
            com.example.domain.model.R2Item(
                key = "videos/",
                name = "videos",
                size = 0L,
                lastModified = 100L,
                isFolder = true
            ),
            com.example.domain.model.R2Item(
                key = "notes.txt",
                name = "notes.txt",
                size = 500L,
                lastModified = 200L,
                isFolder = false,
                mimeType = "text/plain"
            ),
            com.example.domain.model.R2Item(
                key = "photos/summer.jpg",
                name = "summer.jpg",
                size = 204800L,
                lastModified = 300L,
                isFolder = false,
                mimeType = "image/jpeg"
            ),
            com.example.domain.model.R2Item(
                key = "videos/birthday.mp4",
                name = "birthday.mp4",
                size = 5000000L,
                lastModified = 400L,
                isFolder = false,
                mimeType = "video/mp4"
            )
        )

        // 1. Folder Browser at root:
        // Shows root folders ("photos/", "videos/") and root files ("notes.txt")
        var currentPrefix = ""
        var activeFilter: com.example.domain.model.CloudFileType? = null

        val rootItems = allBucketItems.filter {
            if (activeFilter != null) {
                !it.isFolder && it.fileType == activeFilter
            } else {
                // Folder browser: direct children of root
                val key = it.key
                !key.substringAfter('/').contains('/') && (key.endsWith('/') || !key.contains('/'))
            }
        }
        assertEquals(3, rootItems.size)
        assertTrue(rootItems.any { it.name == "photos" && it.isFolder })
        assertTrue(rootItems.any { it.name == "videos" && it.isFolder })
        assertTrue(rootItems.any { it.name == "notes.txt" && !it.isFolder })

        // 2. Global File-Type Filter: "Photos" is tapped
        // CRITICAL: currentPrefix remains "" (Bucket Root). activeFilter becomes IMAGE.
        activeFilter = com.example.domain.model.CloudFileType.IMAGE
        assertEquals("", currentPrefix) // Current prefix MUST NEVER change when selecting file type filter!

        val globalPhotos = allBucketItems.filter { !it.isFolder && it.fileType == activeFilter }
        assertEquals(1, globalPhotos.size)
        assertEquals("summer.jpg", globalPhotos[0].name)
        assertEquals("photos/summer.jpg", globalPhotos[0].key)

        // 3. Global File-Type Filter: "Videos" is tapped
        activeFilter = com.example.domain.model.CloudFileType.VIDEO
        assertEquals("", currentPrefix) // Still at Bucket Root!

        val globalVideos = allBucketItems.filter { !it.isFolder && it.fileType == activeFilter }
        assertEquals(1, globalVideos.size)
        assertEquals("birthday.mp4", globalVideos[0].name)
        assertEquals("videos/birthday.mp4", globalVideos[0].key)

        // 4. Reset to "All"
        activeFilter = null
        assertEquals("", currentPrefix)
    }
}

