package com.example.study_mentor.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Question format type supporting scalable assessments created by educators/teachers.
 */
enum class QuestionType {
    MULTIPLE_CHOICE,
    SHORT_ANSWER
}

/**
 * DTO representing an individual question returned by LLM Structured Outputs.
 */
@Serializable
data class GeneratedQuestionDto(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val correctAnswer: String = "",
    val explanation: String = ""
)

/**
 * DTO representing the root quiz response returned by LLM Structured Outputs.
 */
@Serializable
data class GeneratedQuizDto(
    val title: String = "",
    val description: String = "",
    val questions: List<GeneratedQuestionDto> = emptyList()
)

/**
 * Represents an individual quiz question.
 *
 * @param id Unique identifier for the question.
 * @param text The question prompt presented to the student.
 * @param type The type of question (MULTIPLE_CHOICE or SHORT_ANSWER).
 * @param options List of choices for multiple choice questions, or null for short answer questions.
 * @param correctAnswer The case-insensitive expected answer string.
 * @param explanation Explanation of the solution and reasoning.
 */
data class Question(
    val id: String = "",
    val text: String = "",
    val type: QuestionType = QuestionType.MULTIPLE_CHOICE,
    val options: List<String>? = null,
    val correctAnswer: String = "",
    val explanation: String = ""
)

/**
 * Represents a complete Quiz module.
 * Designed to be scalable for future database storage (e.g., Firestore) by Teachers.
 *
 * @param id Unique identifier for the quiz.
 * @param title Title of the quiz (e.g., "Grade 1 Math").
 * @param description Brief description or curriculum guidance.
 * @param maxXp Maximum experience points awarded for a perfect score.
 * @param questions The list of questions included in this quiz.
 * @param isAiGenerated Whether the quiz was created dynamically via AI.
 * @param creatorId ID of the creator or educator who submitted the quiz.
 * @param showInstantFeedback Whether to reveal correct/incorrect answers immediately or after submission.
 */
data class Quiz(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val maxXp: Int = 100,
    val questions: List<Question> = emptyList(),
    val isAiGenerated: Boolean = false,
    val creatorId: String? = null,
    val showInstantFeedback: Boolean = true
)

/**
 * Utility function to calculate earned XP based on quiz performance and mastery accuracy.
 *
 * Distributes XP proportionally with gamification mastery bonuses:
 * - Base proportional XP: (correct / total) * maxXp
 * - Perfect Accuracy Mastery Bonus: +20% bonus XP when 100% of questions are answered correctly.
 * - High Comprehension Bonus: +10% bonus XP when accuracy is >= 80%.
 *
 * @param correctAnswers Number of questions correctly answered by the student.
 * @param totalQuestions Total number of questions in the quiz.
 * @param maxXp Maximum XP designated for this quiz.
 * @return Proportionally distributed XP with mastery bonuses, minimum 0.
 */
fun calculateEarnedXp(correctAnswers: Int, totalQuestions: Int, maxXp: Int): Int {
    if (totalQuestions <= 0 || maxXp <= 0 || correctAnswers <= 0) return 0
    val safeCorrect = correctAnswers.coerceIn(0, totalQuestions)
    val accuracyRatio = safeCorrect.toDouble() / totalQuestions.toDouble()
    val baseEarned = accuracyRatio * maxXp.toDouble()

    // Gamification Mastery Bonus rewarding deep comprehension:
    val masteryBonus = when {
        safeCorrect == totalQuestions -> maxXp * 0.20 // 100% Perfect Score
        accuracyRatio >= 0.80 -> maxXp * 0.10          // 80%+ High Comprehension
        else -> 0.0
    }

    return (baseEarned + masteryBonus).roundToInt()
}
