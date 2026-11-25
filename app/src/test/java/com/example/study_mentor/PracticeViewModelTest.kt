package com.example.study_mentor

import com.example.study_mentor.data.GamificationRepository
import com.example.study_mentor.data.Question
import com.example.study_mentor.data.QuestionType
import com.example.study_mentor.data.Quiz
import com.example.study_mentor.data.QuizRepository
import com.example.study_mentor.data.calculateEarnedXp
import com.example.study_mentor.data.toMap
import com.example.study_mentor.ui.PracticeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val quizRepository: QuizRepository = mockk(relaxed = true)
    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val gamificationRepository: GamificationRepository = mockk(relaxed = true)
    private val aiServiceClient: AiServiceClient = mockk(relaxed = true)
    private val mockUser: FirebaseUser = mockk(relaxed = true)

    private val sampleQuiz = Quiz(
        id = "test_quiz_1",
        title = "Calculus Basics",
        description = "Test calculus quiz",
        maxXp = 100,
        questions = listOf(
            Question(id = "q1", text = "What is derivative of x^2?", type = QuestionType.MULTIPLE_CHOICE, options = listOf("2x", "x", "2", "x^2"), correctAnswer = "2x"),
            Question(id = "q2", text = "What is integral of 1 dx?", type = QuestionType.MULTIPLE_CHOICE, options = listOf("x + C", "1", "0", "x^2"), correctAnswer = "x + C")
        )
    )

    private lateinit var viewModel: PracticeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { auth.currentUser } returns mockUser
        every { mockUser.uid } returns "test_user_123"
        coEvery { quizRepository.getAvailableQuizzes() } returns flowOf(listOf(sampleQuiz))
        coEvery { quizRepository.getUserCustomQuizzes("test_user_123") } returns flowOf(emptyList())

        viewModel = PracticeViewModel(
            quizRepository = quizRepository,
            auth = auth,
            gamificationRepository = gamificationRepository,
            aiServiceClient = aiServiceClient
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadsQuizzesInitiallyIntoStateFlow() = runTest(testDispatcher) {
        advanceUntilIdle()
        val quizzes = viewModel.quizzes.value
        assertEquals(1, quizzes.size)
        assertEquals("test_quiz_1", quizzes[0].id)
        assertEquals(1, viewModel.uiState.value.quizzes.size)
    }

    @Test
    fun testSubmitQuizResults_CalculatesEarnedXpAndPersistsToGamification() = runTest(testDispatcher) {
        advanceUntilIdle()
        // 2/2 correct on 100 XP quiz = 100 + 20% bonus = 120 XP
        val earnedXp = viewModel.submitQuizResults(
            quizId = "test_quiz_1",
            correctAnswers = 2
        )

        assertEquals(120, earnedXp)
        advanceUntilIdle()
        coVerify { gamificationRepository.addXp("test_user_123", 120) }
    }

    @Test
    fun testCalculateEarnedXp_MasteryBonuses() {
        // 100% Score: +20% Bonus
        val perfectScoreXp = calculateEarnedXp(correctAnswers = 5, totalQuestions = 5, maxXp = 100)
        assertEquals(120, perfectScoreXp)

        // 80% Score: +10% Bonus (4/5 * 100 = 80 + 10 = 90)
        val highComprehensionXp = calculateEarnedXp(correctAnswers = 4, totalQuestions = 5, maxXp = 100)
        assertEquals(90, highComprehensionXp)

        // 60% Score: Normal proportional (3/5 * 100 = 60)
        val normalXp = calculateEarnedXp(correctAnswers = 3, totalQuestions = 5, maxXp = 100)
        assertEquals(60, normalXp)
    }

    @Test
    fun testGenerateCustomQuiz_SuccessAndUdfTransitions() = runTest(testDispatcher) {
        val mockJsonResponse = """
            {
              "title": "Quantum Mechanics",
              "description": "Custom test quiz",
              "questions": [
                {
                  "question": "What is Planck's constant?",
                  "options": [],
                  "correctIndex": 0,
                  "correctAnswer": "h",
                  "explanation": "Fundamental quantum constant"
                }
              ]
            }
        """.trimIndent()

        val parsedMockQuiz = Quiz(
            id = "ai_gen_999",
            title = "Quantum Mechanics",
            description = "Custom test quiz",
            maxXp = 100,
            questions = listOf(
                Question(
                    id = "q_1",
                    text = "What is Planck's constant?",
                    type = QuestionType.SHORT_ANSWER,
                    options = null,
                    correctAnswer = "h",
                    explanation = "Fundamental quantum constant"
                )
            ),
            isAiGenerated = true
        )

        coEvery {
            aiServiceClient.askQuestion(any(), any(), any(), any(), any(), any())
        } returns flowOf(mockJsonResponse)

        every {
            quizRepository.parseStructuredQuizJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(parsedMockQuiz)

        var successQuiz: Quiz? = null
        var errorMessage: String? = null

        viewModel.generateCustomQuiz(
            topic = "Quantum Mechanics",
            count = 5,
            onSuccess = { successQuiz = it },
            onError = { errorMessage = it }
        )

        advanceUntilIdle()

        assertNull("Unexpected error message: $errorMessage", errorMessage)
        assertNotNull("Success callback was not invoked", successQuiz)
        assertFalse(viewModel.isGeneratingQuiz.value)
        assertFalse(viewModel.uiState.value.isGeneratingQuiz)
        assertTrue(viewModel.quizzes.value.any { it.id == "ai_gen_999" })
        coVerify { quizRepository.saveCustomQuiz(any()) }
    }

    @Test
    fun testGenerateCustomQuiz_ShowInstantFeedbackFalse_PersistsCorrectly() = runTest(testDispatcher) {
        val mockJsonResponse = """
            {
              "title": "Quantum Mechanics Exam",
              "description": "Exam mode without instant feedback",
              "questions": [
                {
                  "question": "What is the speed of light?",
                  "options": ["3e8 m/s", "3e6 m/s", "3e10 m/s", "3e5 m/s"],
                  "correctIndex": 0,
                  "correctAnswer": "3e8 m/s",
                  "explanation": "Fundamental physical constant"
                }
              ]
            }
        """.trimIndent()

        val mockQuizWithNoInstantFeedback = Quiz(
            id = "ai_gen_no_feedback",
            title = "Quantum Mechanics Exam",
            description = "Exam mode without instant feedback",
            maxXp = 100,
            questions = listOf(
                Question(
                    id = "q_1",
                    text = "What is the speed of light?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("3e8 m/s", "3e6 m/s", "3e10 m/s", "3e5 m/s"),
                    correctAnswer = "3e8 m/s",
                    explanation = "Fundamental physical constant"
                )
            ),
            isAiGenerated = true,
            showInstantFeedback = false
        )

        coEvery {
            aiServiceClient.askQuestion(any(), any(), any(), any(), any(), any())
        } returns flowOf(mockJsonResponse)

        every {
            quizRepository.parseStructuredQuizJson(
                rawJson = any(),
                fallbackTopic = any(),
                count = any(),
                type = any(),
                language = any(),
                creatorId = any(),
                showInstantFeedback = false
            )
        } returns Result.success(mockQuizWithNoInstantFeedback)

        var resultQuiz: Quiz? = null
        viewModel.generateCustomQuiz(
            topic = "Quantum Mechanics Exam",
            count = 5,
            showInstantFeedback = false,
            onSuccess = { resultQuiz = it }
        )

        advanceUntilIdle()

        assertNotNull(resultQuiz)
        assertFalse("showInstantFeedback should be false on generated Quiz", resultQuiz!!.showInstantFeedback)
        val storedQuiz = viewModel.quizzes.value.find { it.id == "ai_gen_no_feedback" }
        assertNotNull(storedQuiz)
        assertFalse("showInstantFeedback should be false in ViewModel state", storedQuiz!!.showInstantFeedback)
        coVerify { quizRepository.saveCustomQuiz(match { !it.showInstantFeedback }) }
    }

    @Test
    fun testQuizToMap_PreservesShowInstantFeedbackFalse() {
        val quiz = Quiz(
            id = "quiz_test_map",
            title = "Test Serialization",
            showInstantFeedback = false
        )
        val map = quiz.toMap()
        assertEquals(false, map["showInstantFeedback"])
    }
}
