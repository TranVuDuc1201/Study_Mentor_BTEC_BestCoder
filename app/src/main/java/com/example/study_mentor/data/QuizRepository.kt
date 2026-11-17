package com.example.study_mentor.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Repository interface defining quiz retrieval and management.
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
            id = "quiz_grade_1_math",
            title = "Grade 1 Math",
            description = "Fundamental addition, subtraction, and basic counting skills.",
            maxXp = 100,
            questions = listOf(
                Question(
                    id = "g1_q1",
                    text = "What is 1 + 1?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("1", "2", "3", "4"),
                    correctAnswer = "2"
                ),
                Question(
                    id = "g1_q2",
                    text = "What is 5 - 2?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "3"
                ),
                Question(
                    id = "g1_q3",
                    text = "Which number comes right after 9?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("8", "10", "11", "12"),
                    correctAnswer = "10"
                ),
                Question(
                    id = "g1_q4",
                    text = "If you have 3 apples and eat 1, how many are left?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "2"
                ),
                Question(
                    id = "g1_q5",
                    text = "How many sides does a triangle have?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("2", "3", "4", "5"),
                    correctAnswer = "3"
                )
            )
        ),
        Quiz(
            id = "quiz_grade_2_math",
            title = "Grade 2 Math",
            description = "Two-digit addition, subtraction, early multiplication, and number patterns.",
            maxXp = 150,
            questions = listOf(
                Question(
                    id = "g2_q1",
                    text = "What is 15 + 12?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("25", "26", "27", "28"),
                    correctAnswer = "27"
                ),
                Question(
                    id = "g2_q2",
                    text = "What is 50 - 18?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "32"
                ),
                Question(
                    id = "g2_q3",
                    text = "What is 4 x 5?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("15", "18", "20", "24"),
                    correctAnswer = "20"
                ),
                Question(
                    id = "g2_q4",
                    text = "What number plus 14 equals 30?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "16"
                ),
                Question(
                    id = "g2_q5",
                    text = "Which of the following is an even number?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("13", "17", "24", "31"),
                    correctAnswer = "24"
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
        docRef.set(toSave).await()
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

    private fun documentToQuiz(doc: DocumentSnapshot): Quiz? {
        return try {
            doc.toObject(Quiz::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            val data = doc.data ?: return null
            val id = doc.id
            val title = data["title"] as? String ?: ""
            val description = data["description"] as? String ?: ""
            val maxXp = (data["maxXp"] as? Number)?.toInt() ?: 100
            val isAiGenerated = data["isAiGenerated"] as? Boolean ?: true
            val creatorId = data["creatorId"] as? String
            val showInstantFeedback = data["showInstantFeedback"] as? Boolean ?: true
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
                Question(
                    id = qId,
                    text = qText,
                    type = qType,
                    options = qOptions,
                    correctAnswer = qCorrectAnswer
                )
            }
            Quiz(
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
}
