package com.example.study_mentor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.study_mentor.data.ChatSession
import com.example.study_mentor.data.FirestoreChatRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing chat library state, search, category filtering,
 * swipe-to-delete operations, and synchronizing with Cloud Firestore.
 */
class LibraryViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val repository: FirestoreChatRepository = FirestoreChatRepository()
) : ViewModel() {

    // Dynamic categories aligning with Ask AI subjects
    val categories = listOf("All", "Math", "Physics", "Chemistry", "History", "Literature")

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isFilterVisible = MutableStateFlow(false)
    val isFilterVisible: StateFlow<Boolean> = _isFilterVisible.asStateFlow()

    init {
        // Populate mock data initially so the UI looks lively and interactive out-of-the-box
        _sessions.value = getSampleMockSessions()
    }

    /**
     * Combined reactive stream that filters sessions based on search query, subject, and category.
     */
    val filteredSessions: StateFlow<List<ChatSession>> = combine(
        _sessions,
        _searchQuery,
        _selectedCategory
    ) { sessionList, query, category ->
        sessionList.filter { session ->
            val matchesCategory = if (category == "All") {
                true
            } else {
                session.displaySubject.equals(category, ignoreCase = true) ||
                        session.category.equals(category, ignoreCase = true) ||
                        session.subject.equals(category, ignoreCase = true) ||
                        inferCategory(session.title).equals(category, ignoreCase = true)
            }

            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                session.title.contains(query, ignoreCase = true) ||
                        session.lastResponsePreview.contains(query, ignoreCase = true) ||
                        session.displaySubject.contains(query, ignoreCase = true) ||
                        (session.outputFormat?.contains(query, ignoreCase = true) == true)
            }

            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _sessions.value
    )

    /**
     * Checks if a user is currently authenticated and listens to their real chat sessions from Firestore.
     */
    fun fetchSessions() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.getChatSessions(uid).collect { firestoreSessions ->
                if (firestoreSessions.isNotEmpty()) {
                    _sessions.value = firestoreSessions
                } else if (_sessions.value.isEmpty()) {
                    _sessions.value = getSampleMockSessions()
                }
            }
        }
    }

    /**
     * Updates the active search query.
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Toggles the filter chip row visibility below the search bar.
     */
    fun toggleFilterVisibility() {
        _isFilterVisible.value = !_isFilterVisible.value
    }

    /**
     * Selects a category for filtering.
     */
    fun onCategorySelect(category: String) {
        _selectedCategory.value = category
    }

    /**
     * Resets both search query and category filter to defaults.
     */
    fun resetFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = "All"
    }

    /**
     * Deletes a chat session optimistically and synchronizes with Firestore.
     */
    fun deleteChat(chatId: String) {
        _sessions.value = _sessions.value.filter { it.id != chatId }

        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.deleteChatSession(uid, chatId)
            } catch (e: Exception) {
                // Log or handle deletion failure if needed
            }
        }
    }

    /**
     * Helper to infer subject category when legacy sessions don't have an explicit subject saved.
     */
    private fun inferCategory(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("math") || lower.contains("derivative") || lower.contains("integral") ||
                    lower.contains("algebra") || lower.contains("calculate") || lower.contains("solve") ||
                    lower.contains("quadratic") || lower.contains("matrix") || lower.contains("equation") -> "Math"

            lower.contains("physics") || lower.contains("gravity") || lower.contains("newton") ||
                    lower.contains("velocity") || lower.contains("quantum") -> "Physics"

            lower.contains("chemistry") || lower.contains("molecule") || lower.contains("atom") ||
                    lower.contains("reaction") || lower.contains("acid") || lower.contains("periodic") -> "Chemistry"

            lower.contains("literature") || lower.contains("poem") || lower.contains("novel") ||
                    lower.contains("shakespeare") || lower.contains("essay") || lower.contains("metaphor") ||
                    lower.contains("author") || lower.contains("grammar") -> "Literature"

            lower.contains("history") || lower.contains("war") || lower.contains("revolution") ||
                    lower.contains("century") || lower.contains("empire") || lower.contains("civilization") -> "History"

            else -> "General"
        }
    }

    /**
     * High-quality mock study chats with explicit subjects and output formats.
     */
    private fun getSampleMockSessions(): List<ChatSession> {
        val now = System.currentTimeMillis()
        val hour = 3600 * 1000L
        val day = 24 * hour

        return listOf(
            ChatSession(
                id = "mock_math_1",
                title = "Solve for x in quadratic 2x² + 5x - 3 = 0",
                lastResponsePreview = "The quadratic roots are x = 0.5 and x = -3 using the quadratic formula.",
                category = "Math",
                subject = "Math",
                outputFormat = "Step-by-step",
                createdAt = now - (2 * hour)
            ),
            ChatSession(
                id = "mock_phy_1",
                title = "Explain Newton's Third Law with real-world examples",
                lastResponsePreview = "For every action, there is an equal and opposite reaction. Examples include rocket propulsion...",
                category = "Physics",
                subject = "Physics",
                outputFormat = "Explain Concept",
                createdAt = now - (5 * hour)
            ),
            ChatSession(
                id = "mock_lit_1",
                title = "Analysis of Gatsby's green light symbolism",
                lastResponsePreview = "The green light at the end of Daisy's dock represents Gatsby's unreachable dreams and the American Dream...",
                category = "Literature",
                subject = "Literature",
                outputFormat = "Summary",
                createdAt = now - (1 * day)
            ),
            ChatSession(
                id = "mock_chem_1",
                title = "Balance equation: Fe + O₂ → Fe₂O₃",
                lastResponsePreview = "Balanced chemical equation is 4Fe + 3O₂ → 2Fe₂O₃ with oxidation state calculations.",
                category = "Chemistry",
                subject = "Chemistry",
                outputFormat = "Check my work",
                createdAt = now - (2 * day)
            ),
            ChatSession(
                id = "mock_hist_1",
                title = "Main causes of the Industrial Revolution in Britain",
                lastResponsePreview = "Key catalysts included vast coal deposits, technological inventions like the steam engine, and access to trade routes...",
                category = "History",
                subject = "History",
                outputFormat = "Step-by-step",
                createdAt = now - (3 * day)
            ),
            ChatSession(
                id = "mock_math_2",
                title = "Find the derivative of f(x) = x³ · sin(x)",
                lastResponsePreview = "Using the product rule: f'(x) = 3x²·sin(x) + x³·cos(x).",
                category = "Math",
                subject = "Math",
                outputFormat = "Step-by-step",
                createdAt = now - (4 * day)
            )
        )
    }
}
