package com.example.ai.provider

import com.example.ai.model.MessageSender
import java.util.Locale
import java.util.regex.Pattern

/**
 * 100% On-Device, Offline, Privacy-First AI Provider.
 *
 * Guarantees:
 * - Runs completely on local CPU
 * - Zero network calls made by this provider
 * - No user data or metadata leaves the device
 * - Translates natural language queries directly into safe application tool calls
 */
class LocalRuleBasedAiProvider : AiProvider {

    override val id: String = "ON_DEVICE"
    override val displayName: String = "On-Device Assistant (100% Private)"
    override val isLocal: Boolean = true

    override suspend fun generateResponse(request: AiRequest): AiResponse {
        val lastUserMessage = request.messages
            .filter { it.sender == MessageSender.USER }
            .lastOrNull()?.text?.trim() ?: ""

        if (lastUserMessage.isBlank()) {
            return AiResponse(
                text = "Hello! I'm your private CloudGallery assistant. How can I help you today?",
                toolCall = null
            )
        }

        val query = lastUserMessage.lowercase(Locale.ROOT)
        val words = query.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }.toSet()
        fun hasWord(vararg targets: String): Boolean = targets.any { it in words }

        // 0. Smart Collections queries
        if (query.contains("nature") || query.contains("outdoors") || query.contains("landscape") || query.contains("forest") || query.contains("flowers")) {
            return AiResponse(
                text = "Opening Nature & Outdoors Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "nature"))
            )
        }

        if (query.contains("food") || query.contains("meal") || query.contains("dinner") || query.contains("cooking") || hasWord("drink", "drinks")) {
            return AiResponse(
                text = "Opening Food & Dining Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "food"))
            )
        }

        if (query.contains("group photo") || query.contains("friends") || query.contains("people") || query.contains("selfie") || query.contains("family")) {
            val collectionId = if (query.contains("group") || query.contains("friends")) "friends_groups" else if (query.contains("family")) "family_moments" else "people"
            return AiResponse(
                text = "Opening People Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to collectionId))
            )
        }

        if (hasWord("pet", "pets", "animal", "animals", "dog", "dogs", "cat", "cats", "puppy", "puppies", "kitten", "kittens")) {
            return AiResponse(
                text = "Opening Animals & Pets Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "animals_pets"))
            )
        }

        if (query.contains("travel") || query.contains("vacation") || query.contains("trip") || query.contains("holiday")) {
            return AiResponse(
                text = "Opening Travel Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "travel"))
            )
        }

        if (query.contains("document") || query.contains("receipt") || query.contains("paper") || query.contains("text photo")) {
            return AiResponse(
                text = "Opening Documents Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "documents"))
            )
        }

        if (query.contains("sport") || query.contains("fitness") || query.contains("workout") || query.contains("running")) {
            return AiResponse(
                text = "Opening Sports Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "sports"))
            )
        }

        if (query.contains("vehicle") || hasWord("car", "cars") || query.contains("automobile") || hasWord("bike", "bikes")) {
            return AiResponse(
                text = "Opening Vehicles Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "vehicles"))
            )
        }

        if (query.contains("building") || query.contains("architecture") || query.contains("house")) {
            return AiResponse(
                text = "Opening Buildings & Architecture Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "buildings"))
            )
        }

        if (query.contains("event") || query.contains("party") || query.contains("concert") || query.contains("celebration")) {
            return AiResponse(
                text = "Opening Events & Gatherings Smart Collection...",
                toolCall = ToolCallRequest("openSmartCollection", mapOf("collectionId" to "events"))
            )
        }

        if (query.contains("smart collection") || query.contains("smart collections") || query.contains("categories") || query.contains("collections")) {
            return AiResponse(
                text = "Loading your Smart Collections...",
                toolCall = ToolCallRequest("getSmartCollections")
            )
        }

        if (query.contains("analyze media") || query.contains("categorize photos") || query.contains("run smart collections") || query.contains("start analysis")) {
            return AiResponse(
                text = "Starting on-device analysis for Smart Collections...",
                toolCall = ToolCallRequest("analyzeUnprocessedMedia")
            )
        }

        if (query.contains("clear smart collections") || query.contains("clear classification")) {
            return AiResponse(
                text = "Clearing Smart Collections classification data...",
                toolCall = ToolCallRequest("clearClassificationData")
            )
        }

        if (query.contains("collection status") || query.contains("analysis status")) {
            return AiResponse(
                text = "Checking Smart Collections analysis progress...",
                toolCall = ToolCallRequest("getAnalysisStatus")
            )
        }

        // 1. Check R2 Connection
        if (query.contains("test r2") || query.contains("check r2") || query.contains("check my r2") ||
            query.contains("r2 connection") || query.contains("test connection") || query.contains("ping r2")
        ) {
            return AiResponse(
                text = "Testing your Cloudflare R2 connection now...",
                toolCall = ToolCallRequest("testR2Connection")
            )
        }

        // 2. Storage Usage / R2 space
        if (query.contains("storage usage") || query.contains("how much r2 storage") ||
            query.contains("cloud storage usage") || query.contains("how much space") ||
            query.contains("storage stats") || query.contains("r2 storage") ||
            query.contains("storage analyzer")
        ) {
            return AiResponse(
                text = "Analyzing your storage breakdown across local device and Cloudflare R2...",
                toolCall = ToolCallRequest("getStorageUsage")
            )
        }

        // 3. Backup commands
        if (query.contains("back up my videos") || query.contains("backup videos") ||
            query.contains("back up videos") || query.contains("backup all new videos")
        ) {
            return AiResponse(
                text = "Starting video backup to your Cloudflare R2 bucket...",
                toolCall = ToolCallRequest("startBackup", mapOf("onlyVideos" to "true"))
            )
        }

        if (query.contains("start backup") || query.contains("run backup") ||
            query.contains("back up") || query.contains("backup now") ||
            query.contains("upload selected") || query.contains("save this photo to r2")
        ) {
            return AiResponse(
                text = "Running backup to Cloudflare R2...",
                toolCall = ToolCallRequest("startBackup", mapOf("onlyVideos" to "false"))
            )
        }

        if (query.contains("backup status") || query.contains("upload status") ||
            query.contains("is backup running") || query.contains("check backup")
        ) {
            return AiResponse(
                text = "Checking current backup and sync status...",
                toolCall = ToolCallRequest("getBackupStatus")
            )
        }

        // 4. Diagnostic: Why did upload fail?
        if (query.contains("why did my upload fail") || query.contains("upload failed") ||
            query.contains("explain why something failed") || query.contains("why failed") ||
            query.contains("troubleshoot") || query.contains("upload error")
        ) {
            return AiResponse(
                text = "Running a diagnostic on your backup and network settings to see why an upload might have failed...",
                toolCall = ToolCallRequest("explainUploadFailure")
            )
        }

        // 5. Cloud files / R2 files
        if (query.contains("cloud files") || query.contains("stored in the cloud") ||
            query.contains("show cloud") || query.contains("r2 files") ||
            query.contains("files in cloud") || query.contains("browse r2")
        ) {
            val category = when {
                query.contains("video") -> "VIDEO"
                query.contains("photo") || query.contains("image") -> "PHOTO"
                else -> null
            }
            val args = mutableMapOf<String, String>()
            if (category != null) args["category"] = category
            return AiResponse(
                text = "Searching files stored in your Cloudflare R2 bucket...",
                toolCall = ToolCallRequest("searchCloudMedia", args)
            )
        }

        // 6. Duplicates
        if (query.contains("duplicate") || query.contains("find duplicates") || query.contains("duplicates")) {
            return AiResponse(
                text = "Scanning your gallery for duplicate files and wasted space...",
                toolCall = ToolCallRequest("findDuplicates")
            )
        }

        // 7. Screenshots
        if (query.contains("screenshot") || query.contains("screenshots")) {
            return AiResponse(
                text = "Finding your screenshots...",
                toolCall = ToolCallRequest(
                    "searchLocalMedia",
                    mapOf("albumName" to "Screenshots")
                )
            )
        }

        // 8. Large files / Size query
        val sizeMatcher = Pattern.compile("(\\d+)\\s*(mb|gb)", Pattern.CASE_INSENSITIVE).matcher(query)
        if (query.contains("large") || query.contains("largest") || sizeMatcher.find()) {
            val minBytes = if (sizeMatcher.find(0)) {
                val num = sizeMatcher.group(1)?.toLongOrNull() ?: 100L
                val unit = sizeMatcher.group(2)?.lowercase(Locale.ROOT) ?: "mb"
                if (unit == "gb") num * 1024L * 1024L * 1024L else num * 1024L * 1024L
            } else {
                50L * 1024L * 1024L // Default 50 MB
            }

            val isVid = query.contains("video")
            val args = mutableMapOf(
                "minSizeBytes" to minBytes.toString()
            )
            if (isVid) args["mediaType"] = "video"

            return AiResponse(
                text = "Searching for large files...",
                toolCall = ToolCallRequest("searchLocalMedia", args)
            )
        }

        // 9. Today's photos / Recent photos
        if (query.contains("today") || query.contains("today's photos") || query.contains("todays photos")) {
            return AiResponse(
                text = "Looking for photos taken today...",
                toolCall = ToolCallRequest(
                    "searchLocalMedia",
                    mapOf("dateFilter" to "TODAY", "mediaType" to "photo")
                )
            )
        }

        // 10. Videos query
        if (query.contains("video") || query.contains("videos") || query.contains("my videos")) {
            return AiResponse(
                text = "Finding all videos in your local gallery...",
                toolCall = ToolCallRequest("searchLocalMedia", mapOf("mediaType" to "video"))
            )
        }

        // 11. Photos / Pictures query
        if (query.contains("photo") || query.contains("photos") || query.contains("pictures") || query.contains("images")) {
            return AiResponse(
                text = "Finding your photos...",
                toolCall = ToolCallRequest("searchLocalMedia", mapOf("mediaType" to "photo"))
            )
        }

        // 12. Refresh gallery
        if (query.contains("refresh") || query.contains("reload") || query.contains("rescan")) {
            return AiResponse(
                text = "Refreshing local media library...",
                toolCall = ToolCallRequest("refreshGallery")
            )
        }

        // 13. Help with Cloudflare R2
        if (query.contains("help with cloudflare r2") || query.contains("help with r2") ||
            query.contains("setup r2") || query.contains("how to setup r2") || query.contains("configure r2")
        ) {
            return AiResponse(
                text = """
                    Here is how to set up Cloudflare R2 with CloudGallery:

                    1. Go to your Cloudflare Dashboard → R2 Object Storage.
                    2. Create an R2 Bucket (e.g., 'cloudgallery-backup').
                    3. Under R2 Account details, copy your Account ID.
                    4. Go to 'Manage R2 API Tokens' and create a token with 'Object Read & Write' permissions.
                    5. Copy the generated Access Key ID and Secret Access Key.
                    6. In CloudGallery, tap 'Settings & R2' and enter these credentials, then tap 'Test Connection'.

                    Security Note: Your keys are encrypted locally on your device using Android Keystore and are never shared or sent to any AI provider.
                """.trimIndent()
            )
        }

        // 14. Fallback conversational response
        return AiResponse(
            text = "I'm ready to help you manage your gallery and R2 storage! You can ask me to:\n\n" +
                    "• \"Show my videos\"\n" +
                    "• \"Find screenshots\"\n" +
                    "• \"Show videos larger than 500 MB\"\n" +
                    "• \"How much R2 storage am I using?\"\n" +
                    "• \"Back up my videos\"\n" +
                    "• \"Why did my upload fail?\"\n" +
                    "• \"Check my R2 connection\"\n" +
                    "• \"Find duplicate files\""
        )
    }
}
