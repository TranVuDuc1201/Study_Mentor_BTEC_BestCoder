package com.example.study_mentor

import com.example.study_mentor.data.QuestionType
import com.example.study_mentor.data.QuizRepositoryImpl
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuizDeserializationUnitTest {

    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private lateinit var repository: QuizRepositoryImpl

    @Before
    fun setUp() {
        repository = QuizRepositoryImpl(firestore = firestore)
    }

    @Test
    fun testValidStructuredJson_ParsesCleanlyIntoDomainQuiz() {
        val validJson = """
            {
              "title": "Quantum Mechanics 101",
              "description": "Essential concepts of wave-particle duality and uncertainty.",
              "questions": [
                {
                  "question": "What does the photoelectric effect demonstrate?",
                  "options": [
                    "Particle nature of light",
                    "Wave nature of light",
                    "Continuous energy absorption",
                    "Electromagnetic deflection"
                  ],
                  "correctIndex": 0,
                  "correctAnswer": "Particle nature of light",
                  "explanation": "Einstein showed light behaves as discrete quanta (photons)."
                },
                {
                  "question": "Write the de Broglie wavelength equation.",
                  "options": [],
                  "correctIndex": 0,
                  "correctAnswer": "lambda = h / p",
                  "explanation": "Wavelength equals Planck's constant divided by momentum."
                }
              ]
            }
        """.trimIndent()

        val result = repository.parseStructuredQuizJson(
            rawJson = validJson,
            fallbackTopic = "Quantum Mechanics",
            count = 2,
            type = "MULTIPLE_CHOICE",
            language = "English",
            creatorId = "user_456",
            showInstantFeedback = true
        )

        assertTrue(result.isSuccess)
        val quiz = result.getOrNull()
        assertNotNull(quiz)
        assertEquals("Quantum Mechanics 101", quiz!!.title)
        assertEquals(40, quiz.maxXp) // 2 * 20
        assertEquals(2, quiz.questions.size)

        // Multiple choice question
        val q1 = quiz.questions[0]
        assertEquals(QuestionType.MULTIPLE_CHOICE, q1.type)
        assertEquals(4, q1.options?.size)
        assertEquals("Particle nature of light", q1.correctAnswer)
        assertEquals("Einstein showed light behaves as discrete quanta (photons).", q1.explanation)

        // Short answer question (empty options list)
        val q2 = quiz.questions[1]
        assertEquals(QuestionType.SHORT_ANSWER, q2.type)
        assertEquals(null, q2.options)
        assertEquals("lambda = h / p", q2.correctAnswer)
    }

    @Test
    fun testMalformedJson_ReturnsFailureWithoutUnhandledException() {
        val malformedJson = """
            {
              "title": "Broken Quiz",
              "description": "Incomplete JSON payload...
        """.trimIndent()

        val result = repository.parseStructuredQuizJson(
            rawJson = malformedJson,
            fallbackTopic = "Math",
            count = 5,
            type = "MULTIPLE_CHOICE",
            language = "English",
            creatorId = null,
            showInstantFeedback = true
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Exception)
    }

    @Test
    fun testEmptyQuestionsArray_ReturnsFailure() {
        val emptyQuestionsJson = """
            {
              "title": "Empty Quiz",
              "description": "No questions provided",
              "questions": []
            }
        """.trimIndent()

        val result = repository.parseStructuredQuizJson(
            rawJson = emptyQuestionsJson,
            fallbackTopic = "Chemistry",
            count = 5,
            type = "MULTIPLE_CHOICE",
            language = "English",
            creatorId = null,
            showInstantFeedback = true
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testOutOfBoundsCorrectIndex_ReturnsFailure() {
        val invalidIndexJson = """
            {
              "title": "Invalid Index Quiz",
              "description": "Index out of range",
              "questions": [
                {
                  "question": "What is 2 + 2?",
                  "options": ["1", "2", "3", "4"],
                  "correctIndex": 99,
                  "correctAnswer": "5",
                  "explanation": "Invalid"
                }
              ]
            }
        """.trimIndent()

        val result = repository.parseStructuredQuizJson(
            rawJson = invalidIndexJson,
            fallbackTopic = "Arithmetic",
            count = 1,
            type = "MULTIPLE_CHOICE",
            language = "English",
            creatorId = null,
            showInstantFeedback = true
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IndexOutOfBoundsException)
    }

    @Test
    fun testUnknownJsonKeys_IgnoredSafely() {
        val extraKeysJson = """
            {
              "title": "Extra Keys Quiz",
              "description": "Payload with unexpected metadata",
              "unknown_field_123": "Should be ignored by defensiveJson",
              "questions": [
                {
                  "question": "What is the capital of France?",
                  "options": ["London", "Paris", "Berlin", "Rome"],
                  "correctIndex": 1,
                  "correctAnswer": "Paris",
                  "explanation": "Paris is the capital of France.",
                  "random_debug_info": 42
                }
              ]
            }
        """.trimIndent()

        val result = repository.parseStructuredQuizJson(
            rawJson = extraKeysJson,
            fallbackTopic = "Geography",
            count = 1,
            type = "MULTIPLE_CHOICE",
            language = "English",
            creatorId = null,
            showInstantFeedback = true
        )

        assertTrue(result.isSuccess)
        val quiz = result.getOrNull()
        assertNotNull(quiz)
        assertEquals("Paris", quiz!!.questions[0].correctAnswer)
    }
}
