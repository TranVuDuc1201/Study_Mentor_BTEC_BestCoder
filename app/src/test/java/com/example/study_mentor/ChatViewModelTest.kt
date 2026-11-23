package com.example.study_mentor

import com.example.study_mentor.data.ChatMessage
import com.example.study_mentor.data.FirestoreChatRepository
import com.example.study_mentor.data.GamificationRepository
import com.example.study_mentor.ui.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val repository: FirestoreChatRepository = mockk(relaxed = true)
    private val gamificationRepository: GamificationRepository = mockk(relaxed = true)
    private val aiServiceClient: AiServiceClient = mockk(relaxed = true)

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { auth.currentUser } returns null
        viewModel = ChatViewModel(
            auth = auth,
            repository = repository,
            gamificationRepository = gamificationRepository,
            aiServiceClient = aiServiceClient
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() {
        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("", state.inputText)
        assertNull(state.selectedSubject)
        assertNull(state.selectedFormat)
    }

    @Test
    fun testSubjectAndFormatSelectionAndToggle() {
        // Select Math
        viewModel.onSubjectSelected("Math")
        assertEquals("Math", viewModel.uiState.value.selectedSubject)

        // Toggle Math (deselects)
        viewModel.onSubjectSelected("Math")
        assertNull(viewModel.uiState.value.selectedSubject)

        // Select Step-by-Step format
        viewModel.onFormatSelected("Step-by-Step")
        assertEquals("Step-by-Step", viewModel.uiState.value.selectedFormat)

        // Reset all context filters
        viewModel.onSubjectSelected("Physics")
        viewModel.resetContextFilters()
        assertNull(viewModel.uiState.value.selectedSubject)
        assertNull(viewModel.uiState.value.selectedFormat)
    }

    @Test
    fun testInputTextStateTransition() {
        viewModel.onInputTextChanged("How do I calculate momentum?")
        assertEquals("How do I calculate momentum?", viewModel.uiState.value.inputText)
    }

    @Test
    fun testSendMessage_StreamsAndAppendsTokens() = runTest(testDispatcher) {
        val incomingTokens = listOf("To find ", "momentum, ", "use p = m × v.")
        coEvery {
            aiServiceClient.askQuestion(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flow {
            for (token in incomingTokens) {
                emit(token)
            }
        }

        viewModel.sendMessage(prompt = "Calculate momentum")

        // Before coroutines finish, user message is appended and isLoading is true
        assertTrue(viewModel.uiState.value.messages.any { it.role == "user" })
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        // After stream completes, loading is false and AI response is present with clean math
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        val aiMessage = state.messages.find { it.role == "ai" }
        assertTrue(aiMessage != null)
        assertTrue(aiMessage!!.text.contains("p = m × v"))
    }
}
