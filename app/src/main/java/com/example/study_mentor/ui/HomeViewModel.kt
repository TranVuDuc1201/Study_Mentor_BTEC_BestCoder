package com.example.study_mentor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Science
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Model representing a recommended prompt suggestion filtered by subject.
 */
data class QuestionSuggestion(
    val question: String,
    val subject: String,
    val icon: ImageVector
)

/**
 * Model representing recent activity / questions asked by the student.
 */
data class RecentQuestion(
    val id: String,
    val prompt: String,
    val timeAgo: String,
    val subject: String
)

/**
 * ViewModel for HomeScreen managing time-aware greetings, category selection,
 * prompt suggestions, and recent question history.
 */
class HomeViewModel : ViewModel() {

    val subjects = listOf("All", "Math", "Science", "History", "Literature")

    private val _selectedSubject = MutableStateFlow("All")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    private val allSuggestions = listOf(
        QuestionSuggestion(
            question = "How do I solve quadratic equations using the quadratic formula?",
            subject = "Math",
            icon = Icons.Outlined.Calculate
        ),
        QuestionSuggestion(
            question = "What is the Pythagorean theorem and how is it used?",
            subject = "Math",
            icon = Icons.Outlined.Calculate
        ),
        QuestionSuggestion(
            question = "Can you explain derivative rules with simple examples?",
            subject = "Math",
            icon = Icons.Outlined.Calculate
        ),
        QuestionSuggestion(
            question = "What is the function of mitochondria in animal and plant cells?",
            subject = "Science",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "Explain Newton's three laws of motion with real-life examples.",
            subject = "Science",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "How does photosynthesis convert sunlight and CO2 into glucose?",
            subject = "Science",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "Explain the key causes and impact of the French Revolution.",
            subject = "History",
            icon = Icons.Outlined.HistoryEdu
        ),
        QuestionSuggestion(
            question = "What were the major turning points of World War II?",
            subject = "History",
            icon = Icons.Outlined.HistoryEdu
        ),
        QuestionSuggestion(
            question = "Summarize the history and cultural impact of the ancient Silk Road.",
            subject = "History",
            icon = Icons.Outlined.HistoryEdu
        ),
        QuestionSuggestion(
            question = "Analyze the central themes and tragic conflict in Shakespeare's Hamlet.",
            subject = "Literature",
            icon = Icons.AutoMirrored.Outlined.MenuBook
        ),
        QuestionSuggestion(
            question = "What are the common poetic devices and how are they used effectively?",
            subject = "Literature",
            icon = Icons.AutoMirrored.Outlined.MenuBook
        ),
        QuestionSuggestion(
            question = "Compare and contrast Romanticism vs Realism in 19th-century literature.",
            subject = "Literature",
            icon = Icons.AutoMirrored.Outlined.MenuBook
        )
    )

    /* TODO: Fetch from Room/Firestore History */
    // Mocked recent question history for local prototype
    private val _recentQuestions = MutableStateFlow(
        listOf(
            RecentQuestion(
                id = "rq_1",
                prompt = "How do I calculate the derivative of e^(2x) using chain rule?",
                timeAgo = "2 hours ago",
                subject = "Math"
            ),
            RecentQuestion(
                id = "rq_2",
                prompt = "What is the fundamental difference between mitosis and meiosis?",
                timeAgo = "Yesterday",
                subject = "Science"
            )
        )
    )
    val recentQuestions: StateFlow<List<RecentQuestion>> = _recentQuestions.asStateFlow()

    /**
     * Updates currently active subject filter chip.
     */
    fun selectSubject(subject: String) {
        _selectedSubject.value = subject
    }

    /**
     * Filters suggestions according to selected category.
     */
    fun getFilteredSuggestions(subject: String): List<QuestionSuggestion> {
        return if (subject.equals("All", ignoreCase = true)) {
            // Show a diverse cross-section from each subject
            listOf(
                allSuggestions.first { it.subject == "Math" },
                allSuggestions.first { it.subject == "Science" },
                allSuggestions.first { it.subject == "History" },
                allSuggestions.first { it.subject == "Literature" }
            )
        } else {
            allSuggestions.filter { it.subject.equals(subject, ignoreCase = true) }
        }
    }

    /**
     * Returns a time-aware greeting based on device wall-clock time.
     */
    fun getTimeBasedGreeting(calendar: Calendar = Calendar.getInstance()): String {
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
