package com.example.study_mentor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.study_mentor.OpenAIApiClient
import com.example.study_mentor.data.ChatMessage
import com.example.study_mentor.data.ChatSession
import com.example.study_mentor.data.FirestoreChatRepository
import com.example.study_mentor.data.GamificationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// Expose typealias for backward compatibility if referenced from UI package
typealias ChatMessage = com.example.study_mentor.data.ChatMessage

// ─── UI State ───────────────────────────────────────────────────────────────

/**
 * The complete UI state for the chat screen.
 * @param messages            Ordered list of chat messages (oldest first).
 * @param isLoading           True while awaiting the initial AI response token.
 * @param errorMessage        Non-null when the last API call encountered an error.
 * @param selectedImageUri    URI of the image chosen from gallery for preview.
 * @param selectedImageBase64 Compressed Base64 representation of the selected image.
 * @param currentSessionId    ID of the active Firestore chat session, or null if new.
 * @param inputText           Current user query draft in the chat input field.
 * @param selectedSubject     Currently active subject for dynamic prompt injection.
 * @param selectedFormat      Currently active output format for dynamic prompt injection.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedImageUri: Uri? = null,
    val selectedImageBase64: String? = null,
    val currentSessionId: String? = null,
    val inputText: String = "",
    val selectedSubject: String? = null,
    val selectedFormat: String? = null
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class ChatViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val repository: FirestoreChatRepository = FirestoreChatRepository(),
    private val gamificationRepository: GamificationRepository = GamificationRepository()
) : ViewModel() {

    companion object {
        val SUBJECT_OPTIONS = listOf("Math", "Physics", "Chemistry", "History", "Literature", "General")
        val FORMAT_OPTIONS = listOf("Step-by-step", "Explain Concept", "Check my work", "Summary")
        const val DEFAULT_SUBJECT = "Math"
        const val DEFAULT_FORMAT = "Step-by-step"
        const val DEFAULT_IMAGE_PROMPT = "Solve this math problem step-by-step and explain the concepts."
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Shared flow for triggering camera capture from external UI controls (e.g., MainScreen center FAB)
    private val _captureTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureTrigger: SharedFlow<Unit> = _captureTrigger.asSharedFlow()

    private var isStreaming = false

    /**
     * Triggers a CameraX photo capture request in CameraScannerScreen.
     */
    fun triggerCameraCapture() {
        _captureTrigger.tryEmit(Unit)
    }

    /**
     * Updates the current input text query.
     */
    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Updates or toggles the active subject for dynamic prompt injection.
     * Passing the currently selected subject deselects it (sets to null).
     */
    fun onSubjectSelected(subject: String?) {
        _uiState.update { current ->
            val newSubject = if (current.selectedSubject == subject) null else subject
            current.copy(selectedSubject = newSubject)
        }
    }

    /**
     * Updates or toggles the active output format for dynamic prompt injection.
     * Passing the currently selected format deselects it (sets to null).
     */
    fun onFormatSelected(format: String?) {
        _uiState.update { current ->
            val newFormat = if (current.selectedFormat == format) null else format
            current.copy(selectedFormat = newFormat)
        }
    }

    /**
     * Clears both subject and output format context chips back to null.
     */
    fun resetContextFilters() {
        _uiState.update { it.copy(selectedSubject = null, selectedFormat = null) }
    }

    /**
     * Dynamically builds the final prompt based on the selected Subject and Output Format.
     */
    fun buildEffectivePrompt(
        userInput: String,
        subject: String? = _uiState.value.selectedSubject,
        format: String? = _uiState.value.selectedFormat,
        hasImage: Boolean = true
    ): String {
        val trimmed = userInput.trim()
        if (trimmed.startsWith("Act as an expert in")) {
            return trimmed
        }
        val subjectPrefix = if (!subject.isNullOrBlank()) "Act as an expert in $subject. " else ""
        val formatSuffix = if (!format.isNullOrBlank()) " Provide a $format." else ""

        val instruction = if (trimmed.isNotBlank()) {
            trimmed
        } else if (hasImage) {
            if (!format.isNullOrBlank()) {
                "Analyze the attached image and provide a $format."
            } else {
                "Analyze the attached image and solve/explain step-by-step."
            }
        } else {
            if (!format.isNullOrBlank()) {
                "Provide a $format explanation."
            } else {
                "How can I help you today?"
            }
        }
        return "$subjectPrefix$instruction$formatSuffix".trim()
    }

    /**
     * Loads an existing chat session's messages from Firestore.
     */
    fun loadSession(sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(currentSessionId = sessionId, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getMessagesForSession(uid, sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    /**
     * Resets the active session so the next message initiates a fresh session.
     */
    fun startNewChat() {
        _uiState.update {
            it.copy(
                currentSessionId = null,
                messages = emptyList(),
                errorMessage = null,
                selectedImageUri = null,
                selectedImageBase64 = null,
                inputText = "",
                selectedSubject = null,
                selectedFormat = null
            )
        }
    }

    /**
     * Handles gallery image selection. Decodes the image, downscales it to max 1024x1024,
     * compresses it as JPEG at 80% quality, and stores its Base64 string on Dispatchers.IO.
     */
    fun onImageSelected(uri: Uri?, context: Context) {
        if (uri == null) {
            clearSelectedImage()
            return
        }

        // Show thumbnail immediately in UI while compression runs in background
        _uiState.update { it.copy(selectedImageUri = uri, selectedImageBase64 = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base64 = processAndCompressImage(context, uri)
                _uiState.update { it.copy(selectedImageBase64 = base64) }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to compress and encode image", e)
            }
        }
    }

    /** Resets the selected image URI and Base64 string. */
    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null, selectedImageBase64 = null) }
    }

    /**
     * Safely reads, downsamples, scales (max 1024x1024), and compresses an image to JPEG 80%.
     */
    fun processAndCompressImage(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver

        // 1. Decode bounds first to calculate inSampleSize and prevent OutOfMemory (OOM)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return null

        val maxDim = 1024
        var inSampleSize = 1
        if (rawHeight > maxDim || rawWidth > maxDim) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                inSampleSize *= 2
            }
        }

        // 2. Decode the downsampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // 3. Scale proportionally so max(width, height) <= 1024
        val currentWidth = sampledBitmap.width
        val currentHeight = sampledBitmap.height
        val longestSide = maxOf(currentWidth, currentHeight)

        val finalBitmap = if (longestSide > maxDim) {
            val scale = maxDim.toFloat() / longestSide
            val targetWidth = (currentWidth * scale).toInt()
            val targetHeight = (currentHeight * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetWidth, targetHeight, true)
            if (scaled != sampledBitmap) {
                sampledBitmap.recycle()
            }
            scaled
        } else {
            sampledBitmap
        }

        // 4. Compress to JPEG at 80% quality
        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        finalBitmap.recycle()

        // 5. Encode byte array to Base64 without line wraps
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * One-tap image dispatch: receives a captured/cropped image URI and user prompt (or default prompt),
     * dynamically builds the prompt with expert subject and format injection, displays the user message bubble,
     * compresses the image on IO, and streams the AI response.
     */
    fun sendImagePrompt(context: Context, imageUri: Uri, prompt: String? = null) {
        if (isStreaming) return

        val trimmedPrompt = (prompt ?: _uiState.value.inputText).trim()
        val effectivePrompt = buildEffectivePrompt(
            userInput = trimmedPrompt,
            subject = _uiState.value.selectedSubject,
            format = _uiState.value.selectedFormat,
            hasImage = true
        )

        // Set the selected image URI and clear input text draft
        _uiState.update {
            it.copy(
                selectedImageUri = imageUri,
                selectedImageBase64 = null,
                inputText = ""
            )
        }

        val bubbleText = "[Image attached]\n$effectivePrompt"
        val userMessage = ChatMessage(
            role = "user",
            text = bubbleText,
            timestamp = System.currentTimeMillis()
        )

        // Show user message and thinking indicator right away
        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMessage,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val base64 = try {
                processAndCompressImage(context, imageUri)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to compress image in sendImagePrompt", e)
                null
            }

            _uiState.update { it.copy(selectedImageBase64 = base64) }

            executeAiStream(effectivePrompt, base64)
        }
    }

    /**
     * Sends a user prompt to the AI and streams the reply token-by-token
     * into the UI state in real time, persisting messages to Cloud Firestore.
     *
     * @param prompt The question or instruction typed by the user. If null, the active
     *               [ChatUiState.inputText] is used. If blank and an image is attached
     *               ("Lazy User" flow), dynamically injects the selected Subject and Format prompt.
     */
    fun sendMessage(prompt: String? = null) {
        val rawPrompt = prompt ?: _uiState.value.inputText
        val trimmedPrompt = rawPrompt.trim()
        val base64ToSend = _uiState.value.selectedImageBase64
        val hasImage = base64ToSend != null || _uiState.value.selectedImageUri != null

        // Dynamic Prompt Injection: builds prompt based on selected Subject and Format
        val effectivePrompt = buildEffectivePrompt(
            userInput = trimmedPrompt,
            subject = _uiState.value.selectedSubject,
            format = _uiState.value.selectedFormat,
            hasImage = hasImage
        )

        if ((effectivePrompt.isBlank() && base64ToSend == null) || isStreaming) return

        // Clear active text input and thumbnail immediately from the input area
        _uiState.update { it.copy(inputText = "") }
        clearSelectedImage()

        // Indicate in chat history that an image was attached
        val bubbleText = if (base64ToSend != null) {
            if (effectivePrompt.isNotBlank()) "[Image attached]\n$effectivePrompt" else "[Image attached]"
        } else {
            effectivePrompt
        }

        // 1. Immediately append the user message and start the thinking indicator
        val userMessage = ChatMessage(
            role = "user",
            text = bubbleText,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMessage,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            executeAiStream(effectivePrompt, base64ToSend)
        }
    }

    /**
     * Core streaming orchestration: creates/updates Firestore session, logs messages,
     * awards XP, and streams tokens from OpenAI into the StateFlow.
     */
    private suspend fun executeAiStream(effectivePrompt: String, base64ToSend: String?) {
        isStreaming = true
        var isFirstChunk = true
        val responseBuilder = StringBuilder()
        val uid = auth.currentUser?.uid

        // Auto-create a session in Firestore if currentSessionId is null
        var activeSessionId = _uiState.value.currentSessionId
        val currentSubject = _uiState.value.selectedSubject
        val currentFormat = _uiState.value.selectedFormat

        if (activeSessionId == null && uid != null) {
            try {
                val sessionTitle = if (effectivePrompt.isNotBlank()) {
                    effectivePrompt.split("\\s+".toRegex()).take(5).joinToString(" ")
                } else {
                    "New Image Chat"
                }
                activeSessionId = repository.createNewSession(
                    uid = uid,
                    title = sessionTitle,
                    subject = currentSubject,
                    outputFormat = currentFormat
                )
                _uiState.update { it.copy(currentSessionId = activeSessionId) }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to create session in Firestore", e)
            }
        }

        // Save user message to Firestore
        val userMessage = _uiState.value.messages.lastOrNull { it.role == "user" }
        if (uid != null && activeSessionId != null && userMessage != null) {
            try {
                repository.saveMessage(uid, activeSessionId, userMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to save user message to Firestore", e)
            }
        }

        // Award fixed XP reward (+10 XP) for successfully prompting the AI
        if (uid != null) {
            try {
                gamificationRepository.addXp(uid, 10)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to award XP for chat prompt", e)
            }
        }

        try {
            // Collect streaming text chunks directly as Flow<String> (with optional image)
            OpenAIApiClient.askQuestion(effectivePrompt, base64ToSend)
                .collect { chunkText ->
                    // Strip asterisks so raw Markdown doesn't leak into the UI
                    val cleanDelta = chunkText.replace("*", "")
                    if (cleanDelta.isEmpty()) return@collect

                    responseBuilder.append(cleanDelta)
                    val accumulatedText = responseBuilder.toString()

                    if (isFirstChunk) {
                        isFirstChunk = false
                        // First token received: dismiss the thinking spinner and append the AI bubble
                        val aiMessage = ChatMessage(
                            role = "ai",
                            text = accumulatedText,
                            timestamp = System.currentTimeMillis()
                        )
                        _uiState.update { current ->
                            current.copy(
                                messages = current.messages + aiMessage,
                                isLoading = false
                            )
                        }
                    } else {
                        // Subsequent chunks: update the AI bubble text character-by-character
                        _uiState.update { current ->
                            if (current.messages.isEmpty()) return@update current
                            val updatedList = current.messages.toMutableList()
                            val lastIndex = updatedList.lastIndex
                            updatedList[lastIndex] = updatedList[lastIndex].copy(text = accumulatedText)
                            current.copy(messages = updatedList)
                        }
                    }
                }

            // If the stream finished without emitting any tokens
            if (isFirstChunk) {
                val emptyNotice = ChatMessage(
                    role = "ai",
                    text = "Sorry, I received an empty response. Please try again.",
                    timestamp = System.currentTimeMillis()
                )
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages + emptyNotice,
                        isLoading = false
                    )
                }
            } else {
                // Save final AI message to Firestore
                val finalAiText = responseBuilder.toString()
                if (finalAiText.isNotBlank() && uid != null && activeSessionId != null) {
                    try {
                        val finalAiMessage = ChatMessage(
                            role = "ai",
                            text = finalAiText,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.saveMessage(uid, activeSessionId, finalAiMessage)
                        repository.updateSessionPreview(uid, activeSessionId, finalAiText.take(160))
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to save AI message to Firestore", e)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("OpenAI_Debug", "Streaming Error", e)
            val errorText = "Debug Error: ${e.message ?: "Connection failed"}"
            _uiState.update { current ->
                if (isFirstChunk) {
                    current.copy(
                        messages = current.messages + ChatMessage(role = "ai", text = errorText),
                        isLoading = false,
                        errorMessage = errorText
                    )
                } else {
                    current.copy(
                        isLoading = false,
                        errorMessage = errorText
                    )
                }
            }
        } finally {
            isStreaming = false
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Clears any displayed error message. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
