package com.example.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.model.AiActionResult
import com.example.ai.model.AiMessage
import com.example.ai.model.AiProviderType
import com.example.ai.model.MessageSender
import com.example.ai.orchestrator.AiAgentOrchestrator
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiAssistantUiState(
    val messages: List<AiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val suggestedActions: List<String> = emptyList(),
    val isCancelled: Boolean = false
)

class AiAssistantViewModel(
    private val orchestrator: AiAgentOrchestrator,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    val providerMode: StateFlow<AiProviderType> = preferencesManager.aiProviderModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderType.ON_DEVICE)

    private var activeJob: Job? = null

    init {
        initializeWelcomeMessage()
    }

    private fun initializeWelcomeMessage() {
        val welcome = AiMessage(
            sender = MessageSender.ASSISTANT,
            text = "Hello! I am your private CloudGallery assistant. How can I help you today?",
            suggestedActions = orchestrator.getDefaultSuggestedActions()
        )
        _uiState.value = AiAssistantUiState(
            messages = listOf(welcome),
            suggestedActions = welcome.suggestedActions
        )
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        val userMessage = AiMessage(
            sender = MessageSender.USER,
            text = trimmed
        )

        val thinkingMessage = AiMessage(
            sender = MessageSender.ASSISTANT,
            text = "Thinking...",
            isThinking = true
        )

        val currentMessages = _uiState.value.messages + userMessage

        _uiState.value = _uiState.value.copy(
            messages = currentMessages + thinkingMessage,
            isLoading = true,
            errorMessage = null,
            isCancelled = false
        )

        activeJob = viewModelScope.launch {
            try {
                val assistantResponse = orchestrator.processUserMessage(trimmed, currentMessages)
                _uiState.value = _uiState.value.copy(
                    messages = currentMessages + assistantResponse,
                    isLoading = false,
                    suggestedActions = assistantResponse.suggestedActions.ifEmpty {
                        orchestrator.getDefaultSuggestedActions()
                    }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        messages = currentMessages + AiMessage(
                            sender = MessageSender.ASSISTANT,
                            text = "Action was cancelled."
                        ),
                        isLoading = false,
                        isCancelled = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        messages = currentMessages + AiMessage(
                            sender = MessageSender.ASSISTANT,
                            text = "Sorry, I encountered an issue: ${e.message}. You can retry or check your settings."
                        ),
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            } finally {
                activeJob = null
            }
        }
    }

    fun cancelOngoingRequest() {
        activeJob?.cancel()
        activeJob = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isCancelled = true
        )
    }

    fun clearConversation() {
        cancelOngoingRequest()
        initializeWelcomeMessage()
    }
}
