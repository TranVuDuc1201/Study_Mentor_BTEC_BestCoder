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
import kotlinx.coroutines.flow.update
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

    val subjects = listOf("All", "Math", "Physics", "Chemistry", "Biology", "Literature")

    private val _selectedSubject = MutableStateFlow("All")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    private val allSuggestions = listOf(
        QuestionSuggestion(
            question = "Can you give me a hint on determining function monotonicity with a parameter?",
            subject = "Math",
            icon = Icons.Outlined.Calculate
        ),
        QuestionSuggestion(
            question = "How do I find the angle between two planes in 3D geometry step-by-step?",
            subject = "Math",
            icon = Icons.Outlined.Calculate
        ),
        QuestionSuggestion(
            question = "How do I apply integration by parts to evaluate complex definite integrals?",
            subject = "Math",
            icon = Icons.Outlined.Calculate
        ),
        QuestionSuggestion(
            question = "How do I analyze kinetic and potential energy graphs in simple harmonic motion?",
            subject = "Physics",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "Guide me step-by-step on finding the phase difference in an AC series RLC circuit.",
            subject = "Physics",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "What is the core concept behind the photoelectric effect and threshold frequency?",
            subject = "Physics",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "What is the key principle behind electron balance and mass conservation in redox reactions?",
            subject = "Chemistry",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "How do I determine the molecular formula of a monofunctional ester from vapor density?",
            subject = "Chemistry",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "What are the core steps to solving Mendelian genetics and gene linkage problems?",
            subject = "Biology",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "Explain the molecular mechanism of transcription and translation in eukaryotic cells.",
            subject = "Biology",
            icon = Icons.Outlined.Science
        ),
        QuestionSuggestion(
            question = "Give me an outline for analyzing thematic conflict and character arcs in literature.",
            subject = "Literature",
            icon = Icons.AutoMirrored.Outlined.MenuBook
        ),
        QuestionSuggestion(
            question = "What are effective steps to structure a persuasive argumentative essay?",
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
                prompt = "How do I find vertical and horizontal asymptotes of y = (2x - 1)/(x + 1)?",
                timeAgo = "2 hours ago",
                subject = "Math"
            ),
            RecentQuestion(
                id = "rq_2",
                prompt = "Guide me step-by-step on calculating fringe width in Young's double-slit experiment.",
                timeAgo = "Yesterday",
                subject = "Physics"
            )
        )
    )
    val recentQuestions: StateFlow<List<RecentQuestion>> = _recentQuestions.asStateFlow()

    /**
     * Updates currently active subject filter chip.
     */
    fun selectSubject(subject: String) {
        _selectedSubject.update { subject }
    }

    /**
     * Filters suggestions according to selected category.
     */
    fun getFilteredSuggestions(subject: String): List<QuestionSuggestion> {
        return if (subject.equals("All", ignoreCase = true)) {
            // Show a diverse cross-section from each subject
            listOf(
                allSuggestions.first { it.subject == "Math" },
                allSuggestions.first { it.subject == "Physics" },
                allSuggestions.first { it.subject == "Chemistry" },
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
