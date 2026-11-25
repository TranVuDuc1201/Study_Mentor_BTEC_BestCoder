package com.example.study_mentor.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Repository interface defining quiz retrieval, management, and structured AI generation.
 * Designed to be swapped seamlessly between local mock data and a remote Firestore database.
 */
interface QuizRepository {
    /**
     * Retrieves all active quizzes available for students to practice.
     */
    fun getAvailableQuizzes(): Flow<List<Quiz>>

    /**
     * Fetches an individual quiz by its unique identifier.
     */
    suspend fun getQuizById(quizId: String): Quiz?

    /**
     * Saves a custom AI-generated quiz to the "CustomQuizzes" collection in Firestore.
     */
    suspend fun saveCustomQuiz(quiz: Quiz)

    /**
     * Fetches real-time updates of custom quizzes created by the specified user (where creatorId == userId).
     */
    fun getUserCustomQuizzes(userId: String): Flow<List<Quiz>>

    /**
     * Parses and safely deserializes an LLM structured JSON string into a validated domain [Quiz].
     */
    fun parseStructuredQuizJson(
        rawJson: String,
        fallbackTopic: String,
        count: Int,
        type: String,
        language: String,
        creatorId: String?,
        showInstantFeedback: Boolean
    ): Result<Quiz>
}

/**
 * Initial Repository implementation with seed quiz data.
 * Ready for future extension with Firestore collections for educator/teacher submissions.
 */
class QuizRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : QuizRepository {

    private val seedQuizzes = listOf(
        Quiz(
            id = "quiz_math_calculus",
            title = "High School Math — Functions & Calculus",
            description = "Core calculus topics: function monotonicity, extrema, domain, and definite integrals.",
            maxXp = 100,
            questions = listOf(
                Question(
                    id = "math_q1",
                    text = "On which of the following intervals is the function f(x) = x³ - 3x + 2 strictly increasing?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("(-∞, -1) and (1, +∞)", "(-1, 1)", "(0, 2)", "(-∞, 1)"),
                    correctAnswer = "(-∞, -1) and (1, +∞)"
                ),
                Question(
                    id = "math_q2",
                    text = "What is the domain of the function f(x) = log₂(x - 3)?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("(3, +∞)", "[3, +∞)", "(-∞, 3)", "All real numbers except 3"),
                    correctAnswer = "(3, +∞)"
                ),
                Question(
                    id = "math_q3",
                    text = "What is the local minimum value of the function f(x) = x⁴ - 2x² + 3?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "2"
                ),
                Question(
                    id = "math_q4",
                    text = "In 3D space, which vector is normal to the plane 2x - y + 3z - 5 = 0?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("n = (2, -1, 3)", "n = (2, 1, 3)", "n = (2, -1, -5)", "n = (-1, 3, -5)"),
                    correctAnswer = "n = (2, -1, 3)"
                ),
                Question(
                    id = "math_q5",
                    text = "Evaluate the definite integral of (2x + 1) dx from 0 to 1.",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "2"
                )
            )
        ),
        Quiz(
            id = "quiz_physics_waves",
            title = "High School Physics — Harmonic Motion & Waves",
            description = "Key concepts in spring oscillators, wave speed, phase difference, and interference.",
            maxXp = 120,
            questions = listOf(
                Question(
                    id = "phys_q1",
                    text = "For a spring oscillator with spring constant k and mass m, what is the angular frequency ω?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("ω = √(k / m)", "ω = √(m / k)", "ω = 2π√(k / m)", "ω = 2π√(m / k)"),
                    correctAnswer = "ω = √(k / m)"
                ),
                Question(
                    id = "phys_q2",
                    text = "Given the oscillation equation x = 5cos(2πt + π/4) cm, what is the amplitude in cm?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "5"
                ),
                Question(
                    id = "phys_q3",
                    text = "Two harmonic oscillations of the same frequency are in opposite phase when their phase difference is:",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("(2k + 1)π", "2kπ", "(2k + 1)π / 2", "kπ"),
                    correctAnswer = "(2k + 1)π"
                ),
                Question(
                    id = "phys_q4",
                    text = "A wave travels with speed v = 12 m/s and frequency f = 6 Hz. What is the wavelength in meters?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "2"
                ),
                Question(
                    id = "phys_q5",
                    text = "In two-source in-phase wave interference, constructive interference points have path difference d₂ - d₁ equal to:",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("kλ", "(k + 0.5)λ", "(2k + 1)λ / 4", "kλ / 2"),
                    correctAnswer = "kλ"
                )
            )
        ),
        Quiz(
            id = "quiz_chemistry_reactions",
            title = "High School Chemistry — Organic Reactions & Stoichiometry",
            description = "Practice questions covering esters, carbohydrates, saponification, and redox reactions.",
            maxXp = 100,
            questions = listOf(
                Question(
                    id = "chem_q1",
                    text = "What is the molecular formula of methyl acetate?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("C₃H₆O₂", "C₄H₈O₂", "C₂H₄O₂", "C₃H₈O₂"),
                    correctAnswer = "C₃H₆O₂"
                ),
                Question(
                    id = "chem_q2",
                    text = "Which of the following carbohydrates is a disaccharide?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Sucrose", "Glucose", "Fructose", "Starch"),
                    correctAnswer = "Sucrose"
                ),
                Question(
                    id = "chem_q3",
                    text = "How many moles of glycerol are produced by the complete hydrolysis of 1 mole of triglyceride?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "1"
                ),
                Question(
                    id = "chem_q4",
                    text = "What is the ester formed from the reaction between acetic acid (CH₃COOH) and ethanol (C₂H₅OH)?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Ethyl acetate", "Methyl formate", "Ethyl formate", "Propyl acetate"),
                    correctAnswer = "Ethyl acetate"
                )
            )
        )
    )

    override fun getAvailableQuizzes(): Flow<List<Quiz>> {
        return flowOf(seedQuizzes)
    }

    override suspend fun getQuizById(quizId: String): Quiz? {
        val seed = seedQuizzes.find { it.id == quizId }
        if (seed != null) return seed

        return try {
            val doc = firestore.collection("CustomQuizzes").document(quizId).get().await()
            if (doc.exists()) documentToQuiz(doc) else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveCustomQuiz(quiz: Quiz) {
        val docRef = if (quiz.id.isNotBlank()) {
            firestore.collection("CustomQuizzes").document(quiz.id)
        } else {
            firestore.collection("CustomQuizzes").document()
        }
        val toSave = if (quiz.id.isBlank()) quiz.copy(id = docRef.id) else quiz
        docRef.set(toSave.toMap()).await()
    }

    override fun getUserCustomQuizzes(userId: String): Flow<List<Quiz>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = firestore.collection("CustomQuizzes")
            .whereEqualTo("creatorId", userId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val quizzes = snapshot?.documents?.mapNotNull { doc ->
                documentToQuiz(doc)
            } ?: emptyList()
            trySend(quizzes)
        }

        awaitClose { registration.remove() }
    }

    /**
     * Defensive Json parser instance configured for safe deserialization without regex hacks.
     */
    private val defensiveJson = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = true
    }

    override fun parseStructuredQuizJson(
        rawJson: String,
        fallbackTopic: String,
        count: Int,
        type: String,
        language: String,
        creatorId: String?,
        showInstantFeedback: Boolean
    ): Result<Quiz> = runCatching {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("AI service returned an empty quiz payload.")
        }

        val dto = defensiveJson.decodeFromString<GeneratedQuizDto>(trimmed)

        if (dto.questions.isEmpty()) {
            throw IllegalArgumentException("AI quiz response contains no questions.")
        }

        val defaultIsShortAnswer = type.contains("SHORT", ignoreCase = true)
        val domainQuestions = dto.questions.mapIndexed { index, qDto ->
            val isShortAnswer = defaultIsShortAnswer || qDto.options.isEmpty()
            val questionType = if (isShortAnswer) QuestionType.SHORT_ANSWER else QuestionType.MULTIPLE_CHOICE

            val options = if (isShortAnswer) {
                null
            } else {
                if (qDto.options.size < 2) {
                    throw IllegalArgumentException("Multiple choice question ${index + 1} must have at least 2 options.")
                }
                qDto.options
            }

            // Validate correctIndex bounds for multiple-choice questions
            val resolvedCorrectAnswer = if (isShortAnswer) {
                qDto.correctAnswer.ifBlank { "Answer" }
            } else {
                val opts = options ?: emptyList()
                if (qDto.correctIndex in opts.indices) {
                    opts[qDto.correctIndex]
                } else if (qDto.correctAnswer.isNotBlank() && qDto.correctAnswer in opts) {
                    qDto.correctAnswer
                } else {
                    throw IndexOutOfBoundsException(
                        "correctIndex ${qDto.correctIndex} is out of bounds for options count ${opts.size} in question ${index + 1}."
                    )
                }
            }

            Question(
                id = "q_${index + 1}",
                text = qDto.question.ifBlank { "Question ${index + 1}" },
                type = questionType,
                options = options,
                correctAnswer = resolvedCorrectAnswer,
                explanation = qDto.explanation
            )
        }

        val title = dto.title.ifBlank { fallbackTopic.ifBlank { "Custom Quiz" } }
        val description = dto.description.ifBlank { "Curriculum practice on $title" }
        val maxXp = count * 20

        Quiz(
            id = "ai_${System.currentTimeMillis()}",
            title = title,
            description = description,
            maxXp = maxXp,
            questions = domainQuestions,
            isAiGenerated = true,
            creatorId = creatorId,
            showInstantFeedback = showInstantFeedback
        )
    }

    private fun documentToQuiz(doc: DocumentSnapshot): Quiz? {
        val data = doc.data ?: return null
        val id = doc.id
        val title = data["title"] as? String ?: ""
        val description = data["description"] as? String ?: ""
        val maxXp = (data["maxXp"] as? Number)?.toInt() ?: 100
        val isAiGenerated = data["isAiGenerated"] as? Boolean ?: (data["aiGenerated"] as? Boolean) ?: true
        val creatorId = data["creatorId"] as? String
        val showInstantFeedback = data["showInstantFeedback"] as? Boolean
            ?: (data["instantFeedback"] as? Boolean)
            ?: true
        val questionsRaw = data["questions"] as? List<*> ?: emptyList<Any>()
        val questions = questionsRaw.mapNotNull { item ->
            val qMap = item as? Map<*, *> ?: return@mapNotNull null
            val qId = qMap["id"] as? String ?: ""
            val qText = qMap["text"] as? String ?: ""
            val qTypeStr = qMap["type"] as? String ?: "MULTIPLE_CHOICE"
            val qType = if (qTypeStr.contains("SHORT", ignoreCase = true)) {
                QuestionType.SHORT_ANSWER
            } else {
                QuestionType.MULTIPLE_CHOICE
            }
            val qOptions = (qMap["options"] as? List<*>)?.mapNotNull { it as? String }
            val qCorrectAnswer = qMap["correctAnswer"] as? String ?: ""
            val qExplanation = qMap["explanation"] as? String ?: ""
            Question(
                id = qId,
                text = qText,
                type = qType,
                options = qOptions,
                correctAnswer = qCorrectAnswer,
                explanation = qExplanation
            )
        }
        return Quiz(
            id = id,
            title = title,
            description = description,
            maxXp = maxXp,
            questions = questions,
            isAiGenerated = isAiGenerated,
            creatorId = creatorId,
            showInstantFeedback = showInstantFeedback
        )
    }
}
