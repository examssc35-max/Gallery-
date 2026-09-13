package com.example.ai.provider

import com.example.ai.model.AiMessage

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val isRequired: Boolean = false
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList()
)

data class ToolCallRequest(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap()
)

data class AiRequest(
    val messages: List<AiMessage>,
    val systemInstruction: String,
    val tools: List<ToolDefinition> = emptyList()
)

data class AiResponse(
    val text: String,
    val toolCall: ToolCallRequest? = null
)

interface AiProvider {
    val id: String
    val displayName: String
    val isLocal: Boolean

    suspend fun generateResponse(
        request: AiRequest
    ): AiResponse
}
