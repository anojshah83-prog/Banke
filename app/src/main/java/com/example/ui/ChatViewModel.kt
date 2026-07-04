package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GeminiRequest
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.ChatDatabase
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ChatRepository

    init {
        val database = ChatDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())
    }

    // List of all conversations (for history/drawer)
    val allSessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current selected Session ID. If null, we show the "new chat" suggestion screen.
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    // Messages for the current selected Session
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForSession(sessionId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Selected attachment image Base64 string (optional)
    private val _selectedImageBase64 = MutableStateFlow<String?>(null)
    val selectedImageBase64: StateFlow<String?> = _selectedImageBase64.asStateFlow()

    // Temporary input text holding (useful when rotating or managing states)
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setAttachmentImage(bitmap: Bitmap?) {
        if (bitmap == null) {
            _selectedImageBase64.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                // Compress standard size for API payload
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                _selectedImageBase64.value = base64
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectSession(sessionId: String?) {
        _currentSessionId.value = sessionId
    }

    fun startNewChat() {
        _currentSessionId.value = null
        _selectedImageBase64.value = null
        _inputText.value = ""
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSessionWithMessages(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        if (newTitle.trim().isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSessionTitle(sessionId, newTitle.trim())
        }
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        val imageBase64 = _selectedImageBase64.value

        if (prompt.isEmpty() && imageBase64 == null) return

        // Clear input inputs immediately for responsive UX
        _inputText.value = ""
        _selectedImageBase64.value = null

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Establish session if not exists
            var sessionId = _currentSessionId.value
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString()
                // First 5 words or 25 chars of prompt as default title
                val sessionTitle = if (prompt.isNotEmpty()) {
                    if (prompt.length > 30) prompt.take(30) + "..." else prompt
                } else {
                    "Image Chat"
                }
                val newSession = ChatSession(id = sessionId, title = sessionTitle)
                repository.insertSession(newSession)
                _currentSessionId.value = sessionId
            } else {
                // Update session last updated timestamp
                val currentSessions = allSessions.value
                val match = currentSessions.find { it.id == sessionId }
                if (match != null) {
                    repository.insertSession(match.copy(lastUpdated = System.currentTimeMillis()))
                }
            }

            // 2. Insert User Message
            val userMsg = ChatMessage(
                sessionId = sessionId,
                sender = "user",
                text = prompt,
                imageBase64 = imageBase64
            )
            repository.insertMessage(userMsg)

            // 3. Trigger Gemini API Call
            _isLoading.value = true
            try {
                // Load whole session history
                val messagesHistory = repository.getMessagesForSession(sessionId).first()
                val apiContents = mutableListOf<Content>()

                // Build context history from Room messages
                messagesHistory.forEach { msg ->
                    // Only include "user" or "model" roles to respect Gemini REST API schema
                    if (msg.sender == "user" || msg.sender == "model") {
                        val parts = mutableListOf<Part>()
                        if (msg.text.isNotEmpty()) {
                            parts.add(Part(text = msg.text))
                        }
                        if (msg.imageBase64 != null) {
                            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = msg.imageBase64)))
                        }
                        if (parts.isNotEmpty()) {
                            apiContents.add(Content(role = msg.sender, parts = parts))
                        }
                    }
                }

                // Check API key configuration
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    // API Key is not configured (fallback mock response with instructions)
                    val noticeMsg = ChatMessage(
                        sessionId = sessionId,
                        sender = "system",
                        text = "⚠️ **API Key is Missing or Unconfigured**\n\nIt looks like your Gemini API key has not been set up in AI Studio yet.\n\n**To set it up:**\n1. Open the **Secrets panel** in Google AI Studio (bottom left).\n2. Create a secret named **`GEMINI_API_KEY`**.\n3. Paste your Gemini API key as the value.\n\n*Note: This prototype is running in sandbox mode. I will generate a simulated response for now.*"
                    )
                    repository.insertMessage(noticeMsg)

                    // Wait 1.5s then insert simulated text
                    kotlinx.coroutines.delay(1500)
                    val mockResponse = getSimulatedResponse(prompt)
                    val modelMsg = ChatMessage(
                        sessionId = sessionId,
                        sender = "model",
                        text = mockResponse
                    )
                    repository.insertMessage(modelMsg)
                    return@launch
                }

                // Call the actual Gemini REST endpoint
                val request = GeminiRequest(
                    contents = apiContents,
                    systemInstruction = Content(parts = listOf(Part(text = "You are Gemini, a helpful, highly capable AI assistant built by Google. Keep answers well-structured and concise.")))
                )

                val response = RetrofitClient.geminiService.generateContent(apiKey, request)
                val modelResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: response.error?.message?.let { "Error from API: $it" }
                    ?: "No content generated. Please try again."

                val modelMsg = ChatMessage(
                    sessionId = sessionId,
                    sender = "model",
                    text = modelResponseText
                )
                repository.insertMessage(modelMsg)

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = ChatMessage(
                    sessionId = sessionId,
                    sender = "system",
                    text = "❌ **Network / Connection Error**\n\nFailed to reach Gemini: ${e.localizedMessage ?: "Unknown error"}. Please check your internet connection."
                )
                repository.insertMessage(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getSimulatedResponse(prompt: String): String {
        val query = prompt.lowercase()
        return when {
            query.contains("hello") || query.contains("hi") -> {
                "Hello there! I'm Gemini, your friendly AI companion. How can I help you today?\n\nSince you are in offline sandbox mode, I can provide simulated responses to questions about Android, Kotlin, and designs! Set your actual `GEMINI_API_KEY` to unlock full real-time intelligence!"
            }
            query.contains("code") || query.contains("kotlin") -> {
                "Here is a beautiful Kotlin code snippet for you:\n\n```kotlin\nfun main() {\n    val greeting = \"Hello from simulated Gemini!\"\n    println(greeting)\n}\n```\nTo enable full live model support, please configure your `GEMINI_API_KEY` in the AI Studio Secrets panel!"
            }
            query.contains("weather") -> {
                "Since I am in simulated sandbox mode, I cannot retrieve live forecasts. But in the universe of Android development, it's always a beautiful day to compile! ☀️ Install your `GEMINI_API_KEY` to connect me to real-time search capabilities."
            }
            query.contains("who are you") || query.contains("gemini") -> {
                "I am a simulation of Google's Gemini, built inside this elegant Jetpack Compose client application. I feature an offline-first Room database for saving your chat histories, a beautiful sliding drawer navigation, and high-fidelity gradients!"
            }
            else -> {
                "I received your prompt: \"$prompt\"\n\nThis is a simulated AI response. Once you add a valid `GEMINI_API_KEY` using the Secrets panel in AI Studio, I'll be able to query the live Gemini 3.5 Flash model and answer this with absolute precision!"
            }
        }
    }
}
