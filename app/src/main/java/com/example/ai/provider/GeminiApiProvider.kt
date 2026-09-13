package com.example.ai.provider

import com.example.ai.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional External AI Provider using Google Gemini REST API.
 *
 * Privacy & Security Rules:
 * - User supplies their own Gemini API key (BYOK).
 * - NEVER sends raw photos, videos, or file bytes.
 * - NEVER sends Cloudflare R2 credentials, Secret Access Key, or Keystore data.
 * - Only transmits user text queries and safe tool signatures.
 */
class GeminiApiProvider(
    private val apiKeyProvider: suspend () -> String,
    private val model: String = "gemini-3.5-flash",
    private val fallbackLocalProvider: LocalRuleBasedAiProvider = LocalRuleBasedAiProvider()
) : AiProvider {

    override val id: String = "GEMINI_BYOK"
    override val displayName: String = "Gemini AI (BYOK - Bring Your Own Key)"
    override val isLocal: Boolean = false

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun generateResponse(request: AiRequest): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            // Gracefully fallback to local on-device parser
            return@withContext fallbackLocalProvider.generateResponse(request)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            // Construct JSON request safely
            val jsonRoot = JSONObject()

            // System instructions
            val systemInstructionJson = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are CloudGallery AI Assistant, an expert in photo & video management, Cloudflare R2 cloud sync, and storage optimization. Use the provided tools to answer user requests. Never invent credentials or file contents. CloudGallery is private and secure.")
                    })
                }
                put("parts", parts)
            }
            jsonRoot.put("systemInstruction", systemInstructionJson)

            // Contents (conversation history - user queries and assistant responses only)
            val contentsArray = JSONArray()
            request.messages.takeLast(10).forEach { msg ->
                val role = if (msg.sender == MessageSender.USER) "user" else "model"
                val contentObj = JSONObject().apply {
                    put("role", role)
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", msg.text) })
                    }
                    put("parts", parts)
                }
                contentsArray.put(contentObj)
            }
            jsonRoot.put("contents", contentsArray)

            // Safe Tool Declarations
            val functionDeclarations = JSONArray()

            functionDeclarations.put(JSONObject().apply {
                put("name", "searchLocalMedia")
                put("description", "Search local photos and videos on the device by media type, album, size, or date.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    val props = JSONObject().apply {
                        put("mediaType", JSONObject().apply { put("type", "STRING"); put("description", "video or photo") })
                        put("minSizeBytes", JSONObject().apply { put("type", "NUMBER"); put("description", "Minimum file size in bytes") })
                        put("albumName", JSONObject().apply { put("type", "STRING"); put("description", "e.g. Screenshots or Camera") })
                        put("dateFilter", JSONObject().apply { put("type", "STRING"); put("description", "TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH") })
                    }
                    put("properties", props)
                })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "searchCloudMedia")
                put("description", "Search files stored in the Cloudflare R2 bucket.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    val props = JSONObject().apply {
                        put("category", JSONObject().apply { put("type", "STRING"); put("description", "PHOTO, VIDEO, or OTHER") })
                        put("query", JSONObject().apply { put("type", "STRING"); put("description", "Filename search term") })
                    }
                    put("properties", props)
                })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "getStorageUsage")
                put("description", "Retrieve cloud R2 and local device storage usage breakdown.")
                put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "getBackupStatus")
                put("description", "Get the current backup and sync status.")
                put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "startBackup")
                put("description", "Trigger a backup of media to Cloudflare R2.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    val props = JSONObject().apply {
                        put("onlyVideos", JSONObject().apply { put("type", "BOOLEAN"); put("description", "True to back up only videos") })
                    }
                    put("properties", props)
                })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "testR2Connection")
                put("description", "Test the connection and bucket permissions for Cloudflare R2.")
                put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "explainUploadFailure")
                put("description", "Diagnose why an upload or backup might have failed.")
                put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "findDuplicates")
                put("description", "Find duplicate photos or videos to clean up space.")
                put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) })
            })

            functionDeclarations.put(JSONObject().apply {
                put("name", "refreshGallery")
                put("description", "Rescan the device MediaStore to refresh the gallery library.")
                put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject()) })
            })

            val toolsArray = JSONArray().apply {
                put(JSONObject().apply { put("functionDeclarations", functionDeclarations) })
            }
            jsonRoot.put("tools", toolsArray)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonRoot.toString().toRequestBody(mediaType)
            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // If API key is invalid, quota exceeded, or network issue, fallback safely
                return@withContext fallbackLocalProvider.generateResponse(request)
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var responseText = ""
            var toolCall: ToolCallRequest? = null

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.has("text")) {
                        responseText += part.getString("text")
                    }
                    if (part.has("functionCall")) {
                        val fn = part.getJSONObject("functionCall")
                        val fnName = fn.getString("name")
                        val fnArgsObj = fn.optJSONObject("args")
                        val argsMap = mutableMapOf<String, String>()
                        if (fnArgsObj != null) {
                            val keys = fnArgsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                argsMap[key] = fnArgsObj.optString(key)
                            }
                        }
                        toolCall = ToolCallRequest(fnName, argsMap)
                    }
                }
            }

            if (toolCall != null || responseText.isNotBlank()) {
                AiResponse(
                    text = if (responseText.isBlank() && toolCall != null) "Executing ${toolCall.toolName}..." else responseText,
                    toolCall = toolCall
                )
            } else {
                fallbackLocalProvider.generateResponse(request)
            }
        } catch (e: Exception) {
            // Seamlessly fall back to local provider on any network or parsing failure
            fallbackLocalProvider.generateResponse(request)
        }
    }
}
