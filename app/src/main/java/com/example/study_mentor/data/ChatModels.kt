package com.example.study_mentor.data

import java.util.UUID

/**
 * Represents a chat conversation session stored in Firestore / local persistence.
 * Firestore document path: users/{uid}/chat_sessions/{sessionId}
 *
 * @param id Unique ID of the session document.
 * @param title Human-readable title summarizing the conversation topic (user's prompt).
 * @param lastResponsePreview Quick preview snippet of the AI's answer.
 * @param category Legacy category fallback (e.g., "Math", "Science", "Literature", "General").
 * @param subject Explicit subject selected by user during Ask AI (e.g., "Math", "History", "Physics").
 * @param outputFormat Explicit output format requested by user (e.g., "Step-by-step", "Summary").
 * @param createdAt Epoch timestamp (ms) when the session was created.
 */
data class ChatSession(
    val id: String = "",
    val title: String = "",
    val lastResponsePreview: String = "",
    val category: String = "General",
    val subject: String? = null,
    val outputFormat: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the user-facing subject label with graceful fallback.
     */
    val displaySubject: String
        get() = subject?.takeIf { it.isNotBlank() }
            ?: category.takeIf { it.isNotBlank() && it != "General" }
            ?: "General"
}

/**
 * Represents an individual message in a chat session stored in Firestore.
 * Firestore document path: users/{uid}/chat_sessions/{sessionId}/messages/{messageId}
 *
 * @param id Unique ID of the message.
 * @param role Sender role: either "user" or "ai".
 * @param text The text body of the message.
 * @param timestamp Epoch timestamp (ms) when the message was created.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
