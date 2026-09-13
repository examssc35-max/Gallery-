package com.example.ai.orchestrator

import com.example.ai.model.AiActionResult
import com.example.ai.model.AiMessage
import com.example.ai.model.AiProviderType
import com.example.ai.model.MessageSender
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiRequest
import com.example.ai.provider.GeminiApiProvider
import com.example.ai.provider.LocalRuleBasedAiProvider
import com.example.ai.provider.ToolCallRequest
import com.example.ai.tools.DateFilterType
import com.example.ai.tools.GalleryAssistantTools
import com.example.ai.tools.ToolResult
import com.example.data.local.PreferencesManager
import com.example.domain.model.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AiAgentOrchestrator(
    private val tools: GalleryAssistantTools,
    private val preferencesManager: PreferencesManager
) {
    private val localProvider = LocalRuleBasedAiProvider()

    suspend fun getActiveProvider(): AiProvider = withContext(Dispatchers.IO) {
        val providerMode = preferencesManager.aiProviderModeFlow.first()
        if (providerMode == AiProviderType.GEMINI_BYOK) {
            GeminiApiProvider(
                apiKeyProvider = { preferencesManager.aiGeminiApiKeyFlow.first() },
                fallbackLocalProvider = localProvider
            )
        } else {
            localProvider
        }
    }

    suspend fun processUserMessage(
        userText: String,
        history: List<AiMessage>
    ): AiMessage = withContext(Dispatchers.IO) {
        val provider = getActiveProvider()

        val fullHistory = history + AiMessage(
            sender = MessageSender.USER,
            text = userText
        )

        val request = AiRequest(
            messages = fullHistory,
            systemInstruction = "You are the CloudGallery AI Assistant."
        )

        val response = provider.generateResponse(request)

        if (response.toolCall != null) {
            val (resultMessage, actionResult, followUpSuggestions) = executeTool(response.toolCall)
            AiMessage(
                sender = MessageSender.ASSISTANT,
                text = if (response.text.isNotBlank() && !response.text.startsWith("Executing")) {
                    "${response.text}\n\n$resultMessage"
                } else {
                    resultMessage
                },
                actionResult = actionResult,
                suggestedActions = followUpSuggestions
            )
        } else {
            AiMessage(
                sender = MessageSender.ASSISTANT,
                text = response.text,
                suggestedActions = getDefaultSuggestedActions()
            )
        }
    }

    private suspend fun executeTool(
        toolCall: ToolCallRequest
    ): Triple<String, AiActionResult?, List<String>> = withContext(Dispatchers.IO) {
        when (toolCall.toolName) {
            "searchLocalMedia" -> {
                val mediaType = toolCall.arguments["mediaType"]
                val albumName = toolCall.arguments["albumName"]
                val minSizeBytes = toolCall.arguments["minSizeBytes"]?.toLongOrNull()
                val dateFilterStr = toolCall.arguments["dateFilter"]
                val dateFilter = try {
                    if (dateFilterStr != null) DateFilterType.valueOf(dateFilterStr) else DateFilterType.ALL
                } catch (_: Exception) {
                    DateFilterType.ALL
                }

                when (val res = tools.searchLocalMedia(
                    mediaType = mediaType,
                    minSizeBytes = minSizeBytes,
                    albumName = albumName,
                    dateFilter = dateFilter
                )) {
                    is ToolResult.Success -> {
                        val count = res.data.size
                        val msg = if (count > 0) {
                            "Found $count matching item(s) on your device."
                        } else {
                            "No matching media items found."
                        }
                        val suggestions = if (count > 0) {
                            listOf("Back up my videos", "Find large files", "Show cloud storage usage")
                        } else {
                            listOf("Find my videos", "Show cloud files", "Refresh the gallery")
                        }
                        Triple(msg, res.actionResult, suggestions)
                    }
                    is ToolResult.Error -> {
                        Triple("Failed to search local media: ${res.message}", null, getDefaultSuggestedActions())
                    }
                }
            }

            "searchCloudMedia" -> {
                val catStr = toolCall.arguments["category"]
                val cat = try {
                    if (catStr != null) MediaCategory.valueOf(catStr) else null
                } catch (_: Exception) {
                    null
                }
                when (val res = tools.searchCloudMedia(category = cat)) {
                    is ToolResult.Success -> {
                        val count = res.data.size
                        val msg = if (count > 0) {
                            "Found $count item(s) in your Cloudflare R2 bucket."
                        } else {
                            "No files found in your R2 bucket. Try running a backup to sync photos or videos!"
                        }
                        Triple(msg, res.actionResult, listOf("Show cloud storage usage", "Check my R2 connection", "Back up my videos"))
                    }
                    is ToolResult.Error -> {
                        Triple("Cloud Search: ${res.message}", null, listOf("Help with Cloudflare R2", "Check my R2 connection", "Settings"))
                    }
                }
            }

            "getStorageUsage" -> {
                when (val res = tools.getStorageUsage()) {
                    is ToolResult.Success -> {
                        Triple(
                            "Here is your latest storage breakdown:",
                            res.actionResult,
                            listOf("Find duplicate files", "Find large files", "Show cloud files")
                        )
                    }
                    is ToolResult.Error -> {
                        Triple("Storage calculation error: ${res.message}", null, getDefaultSuggestedActions())
                    }
                }
            }

            "getBackupStatus" -> {
                when (val res = tools.getBackupStatus()) {
                    is ToolResult.Success -> {
                        Triple(
                            "Here is your current backup status:",
                            res.actionResult,
                            listOf("Back up my videos", "Why did my upload fail?", "Show cloud files")
                        )
                    }
                    is ToolResult.Error -> {
                        Triple("Could not retrieve backup status: ${res.message}", null, getDefaultSuggestedActions())
                    }
                }
            }

            "startBackup" -> {
                val onlyVideos = toolCall.arguments["onlyVideos"]?.toBooleanStrictOrNull() ?: false
                when (val res = tools.startBackup(onlyVideos = onlyVideos)) {
                    is ToolResult.Success -> {
                        Triple(
                            res.data,
                            res.actionResult,
                            listOf("Show cloud storage usage", "Show cloud files", "Why did my upload fail?")
                        )
                    }
                    is ToolResult.Error -> {
                        Triple(
                            "Backup failed: ${res.message}",
                            AiActionResult.SimpleActionResult(
                                title = "Backup Incomplete",
                                message = res.message,
                                isSuccess = false
                            ),
                            listOf("Why did my upload fail?", "Check my R2 connection", "Help with Cloudflare R2")
                        )
                    }
                }
            }

            "explainUploadFailure" -> {
                when (val res = tools.explainUploadFailure()) {
                    is ToolResult.Success -> {
                        Triple(
                            "Diagnostics complete. Here are the findings and suggested fixes:",
                            res.actionResult,
                            listOf("Check my R2 connection", "Back up my videos", "Show cloud storage usage")
                        )
                    }
                    is ToolResult.Error -> {
                        Triple("Diagnostic check failed: ${res.message}", null, getDefaultSuggestedActions())
                    }
                }
            }

            "testR2Connection" -> {
                when (val res = tools.testR2Connection()) {
                    is ToolResult.Success -> {
                        Triple(
                            res.data.message,
                            res.actionResult,
                            listOf("Show cloud storage usage", "Show cloud files", "Back up my videos")
                        )
                    }
                    is ToolResult.Error -> {
                        Triple("Connection test error: ${res.message}", null, listOf("Help with Cloudflare R2", "Settings"))
                    }
                }
            }

            "findDuplicates" -> {
                when (val res = tools.findDuplicates()) {
                    is ToolResult.Success -> {
                        val d = res.data
                        val msg = if (d.groupCount > 0) {
                            "Found ${d.groupCount} duplicate group(s) with ${d.duplicateItemsCount} redundant files wasting ${d.formattedWastedSize}."
                        } else {
                            "No duplicate media files detected on your device. Your gallery is clean!"
                        }
                        Triple(msg, res.actionResult, listOf("Find large files", "Show cloud storage usage", "Refresh the gallery"))
                    }
                    is ToolResult.Error -> {
                        Triple("Error finding duplicates: ${res.message}", null, getDefaultSuggestedActions())
                    }
                }
            }

            "refreshGallery" -> {
                when (val res = tools.refreshGallery()) {
                    is ToolResult.Success -> {
                        Triple(res.data, res.actionResult, listOf("Find my videos", "Show today's photos", "Find large files"))
                    }
                    is ToolResult.Error -> {
                        Triple("Failed to refresh gallery: ${res.message}", null, getDefaultSuggestedActions())
                    }
                }
            }

            else -> {
                Triple("Action completed.", null, getDefaultSuggestedActions())
            }
        }
    }

    fun getDefaultSuggestedActions(): List<String> = listOf(
        "Find my videos",
        "Find my screenshots",
        "Back up my videos",
        "Show cloud storage usage",
        "Find large files",
        "Show cloud files",
        "Find duplicate files",
        "Help with Cloudflare R2",
        "Why did my upload fail?"
    )
}
