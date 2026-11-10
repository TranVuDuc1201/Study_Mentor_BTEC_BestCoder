package com.example.study_mentor.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.study_mentor.OpenAIApiClient
import com.example.study_mentor.data.GamificationRepository
import com.example.study_mentor.data.Question
import com.example.study_mentor.data.QuestionType
import com.example.study_mentor.data.Quiz
import com.example.study_mentor.data.QuizRepository
import com.example.study_mentor.data.QuizRepositoryImpl
import com.example.study_mentor.data.calculateEarnedXp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Event emitted when a user finishes a quiz or earns XP.
 */
data class XpRewardEvent(
    val earnedXp: Int,
    val quizTitle: String,
    val difficulty: String
)

/**
 * ViewModel managing practice quizzes, dynamic question models, and XP reward calculations.
 */
class PracticeViewModel(
    private val quizRepository: QuizRepository = QuizRepositoryImpl(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val gamificationRepository: GamificationRepository = GamificationRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeViewModel"
        const val BASE_QUIZ_XP = 50

        // Standard difficulty multipliers for RPG XP scaling
        const val MULTIPLIER_EASY = 1.0f
        const val MULTIPLIER_MEDIUM = 1.5f
        const val MULTIPLIER_HARD = 2.0f
    }

    private val _quizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val quizzes: StateFlow<List<Quiz>> = _quizzes.asStateFlow()

    private val _isGeneratingQuiz = MutableStateFlow(false)
    val isGeneratingQuiz: StateFlow<Boolean> = _isGeneratingQuiz.asStateFlow()

    private val _quizGenerationError = MutableSharedFlow<String>()
    val quizGenerationError: SharedFlow<String> = _quizGenerationError.asSharedFlow()

    private val _quizGenerationSuccess = MutableSharedFlow<Quiz>()
    val quizGenerationSuccess: SharedFlow<Quiz> = _quizGenerationSuccess.asSharedFlow()

    private val _rewardEvents = MutableSharedFlow<XpRewardEvent>()
    val rewardEvents: SharedFlow<XpRewardEvent> = _rewardEvents.asSharedFlow()

    private var systemQuizzes: List<Quiz> = emptyList()
    private var userCustomQuizzes: List<Quiz> = emptyList()
    private var customQuizzesJob: Job? = null

    // Auth-Reactive State Management: Observes user login/logout
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            // User logged out: Immediately clear custom AI quizzes, keep only system challenges
            Log.d(TAG, "User logged out: Clearing custom AI quizzes.")
            customQuizzesJob?.cancel()
            customQuizzesJob = null
            userCustomQuizzes = emptyList()
            combineAndEmitQuizzes()
        } else {
            // User logged in: Fetch their specific custom quizzes from Firestore
            Log.d(TAG, "User authenticated (${user.uid}): Fetching custom quizzes from Firestore.")
            observeUserCustomQuizzes(user.uid)
        }
    }

    init {
        loadQuizzes()
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        customQuizzesJob?.cancel()
    }

    /**
     * Merges user custom AI quizzes at the top, followed by standard curriculum quizzes.
     */
    private fun combineAndEmitQuizzes() {
        _quizzes.value = userCustomQuizzes + systemQuizzes
    }

    /**
     * Observes available system quizzes from QuizRepository.
     */
    fun loadQuizzes() {
        viewModelScope.launch {
            quizRepository.getAvailableQuizzes().collect { quizList ->
                systemQuizzes = quizList
                combineAndEmitQuizzes()
            }
        }
    }

    /**
     * Subscribes to real-time updates for custom quizzes created by the logged-in user.
     */
    private fun observeUserCustomQuizzes(userId: String) {
        customQuizzesJob?.cancel()
        customQuizzesJob = viewModelScope.launch {
            quizRepository.getUserCustomQuizzes(userId).collect { customList ->
                userCustomQuizzes = customList
                combineAndEmitQuizzes()
            }
        }
    }

    /**
     * Submits results for a completed quiz, proportionally calculates earned XP,
     * logs/prints the output, and securely persists the earned XP to the Gamification database.
     *
     * @param quizId The ID of the quiz completed.
     * @param correctAnswers The number of correct answers achieved.
     * @return The calculated earned XP.
     */
    fun submitQuizResults(quizId: String, correctAnswers: Int): Int {
        val quiz = _quizzes.value.find { it.id == quizId }
        val totalQuestions = quiz?.questions?.size ?: 5
        val maxXp = quiz?.maxXp ?: 100

        val earnedXp = calculateEarnedXp(
            correctAnswers = correctAnswers,
            totalQuestions = totalQuestions,
            maxXp = maxXp
        )

        val logMessage = "Submitted Quiz '$quizId': $correctAnswers/$totalQuestions correct. Earned: $earnedXp XP (Max: $maxXp XP)"
        Log.d(TAG, logMessage)
        println(logMessage)

        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank() && earnedXp > 0) {
            viewModelScope.launch {
                try {
                    gamificationRepository.addXp(uid, earnedXp)
                    _rewardEvents.emit(
                        XpRewardEvent(
                            earnedXp = earnedXp,
                            quizTitle = quiz?.title ?: "Practice Quiz",
                            difficulty = "Normal"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist earned XP to GamificationRepository", e)
                }
            }
        }

        return earnedXp
    }

    /**
     * Resolves difficulty text ("Easy", "Medium", "Hard") into its corresponding RPG coefficient multiplier.
     */
    fun getMultiplierForDifficulty(difficulty: String): Float {
        return when (difficulty.lowercase().trim()) {
            "easy" -> MULTIPLIER_EASY
            "medium" -> MULTIPLIER_MEDIUM
            "hard" -> MULTIPLIER_HARD
            else -> 1.0f
        }
    }

    /**
     * Awards RPG XP when a practice quiz is completed with a multiplier.
     */
    fun completeQuiz(
        quizTitle: String = "Practice Quiz",
        difficulty: String = "Medium",
        difficultyMultiplier: Float = getMultiplierForDifficulty(difficulty),
        baseXp: Int = BASE_QUIZ_XP,
        onComplete: (Int) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "Cannot award XP: User is not authenticated.")
            onComplete(0)
            return
        }

        val finalEarnedXp = (baseXp * difficultyMultiplier).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            try {
                gamificationRepository.addXp(uid, finalEarnedXp)
                _rewardEvents.emit(
                    XpRewardEvent(
                        earnedXp = finalEarnedXp,
                        quizTitle = quizTitle,
                        difficulty = difficulty
                    )
                )
                onComplete(finalEarnedXp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to award quiz completion XP", e)
                onComplete(0)
            }
        }
    }

    /**
     * Overload accepting an explicit userId for flexible cross-module calling.
     */
    fun awardQuizCompletionXp(
        userId: String,
        baseXp: Int = BASE_QUIZ_XP,
        difficultyMultiplier: Float = 1.0f,
        onComplete: (Int) -> Unit = {}
    ) {
        if (userId.isBlank()) {
            onComplete(0)
            return
        }

        val finalEarnedXp = (baseXp * difficultyMultiplier).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            try {
                gamificationRepository.addXp(userId, finalEarnedXp)
                onComplete(finalEarnedXp)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to award XP directly for user $userId", e)
                onComplete(0)
            }
        }
    }

    /**
     * Generates a custom quiz using the AI model via OpenAIApiClient based on user preferences.
     * Instructs the AI to return raw JSON conforming directly to the Quiz and Question schemas.
     * Upon receiving the response, parses the JSON, awards dynamic XP (20 XP per question),
     * and adds the new quiz to the beginning of the active quiz list.
     *
     * @param topic Topic or subject (e.g., "History of Vietnam", "Algebra").
     * @param count Number of questions (e.g., 5 or 10).
     * @param type Question format ("MULTIPLE_CHOICE" or "SHORT_ANSWER").
     * @param language Quiz language ("English" or "Vietnamese").
     * @param onSuccess Callback invoked when generation and parsing succeed.
     * @param onError Callback invoked when an error occurs.
     */
    fun generateCustomQuiz(
        topic: String,
        count: Int = 5,
        type: String = "MULTIPLE_CHOICE",
        language: String = "English",
        onSuccess: (Quiz) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val sanitizedTopic = topic.trim()
        if (sanitizedTopic.isBlank()) {
            val errorMsg = "Please enter a quiz topic."
            viewModelScope.launch { _quizGenerationError.emit(errorMsg) }
            onError(errorMsg)
            return
        }

        val calculatedMaxXp = count * 20
        _isGeneratingQuiz.value = true

        val systemPrompt = """
            You are a strict educational assessment engine.
            You MUST respond ONLY with a raw, valid JSON object conforming exactly to the requested schema.
            DO NOT wrap your response in markdown code blocks like ```json or ```.
            DO NOT include any introductory or conversational text, explanations, or notes.
            The entire response must be valid JSON directly parseable by org.json.JSONObject.
        """.trimIndent()

        val userPrompt = """
            Generate an educational quiz on the topic: "$sanitizedTopic".
            
            Parameters:
            - Number of questions: $count
            - Question type: $type (Must be MULTIPLE_CHOICE or SHORT_ANSWER)
            - Language: $language
            - Maximum XP: $calculatedMaxXp
            
            Exact JSON structure required:
            {
              "id": "ai_${System.currentTimeMillis()}",
              "title": "<Concise Quiz Title in $language>",
              "description": "<Engaging 1-sentence description in $language>",
              "maxXp": $calculatedMaxXp,
              "questions": [
                {
                  "id": "q1",
                  "text": "<Question text in $language>",
                  "type": "$type",
                  "options": ["<Option 1>", "<Option 2>", "<Option 3>", "<Option 4>"],
                  "correctAnswer": "<Exact matching string of the correct choice>"
                }
              ]
            }

            Critical rules:
            1. Generate exactly $count questions.
            2. For MULTIPLE_CHOICE: "options" must have exactly 4 choices, and "correctAnswer" must match one of the choices exactly.
            3. For SHORT_ANSWER: set "options": null and provide a concise, unambiguous answer in "correctAnswer".
            4. All text (title, description, questions, options, answers) must be written in $language.
            5. Output ONLY the raw JSON object.
        """.trimIndent()

        viewModelScope.launch {
            try {
                val fullResponse = StringBuilder()
                OpenAIApiClient.askQuestion(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    maxTokens = 2500
                ).collect { chunk ->
                    fullResponse.append(chunk)
                }

                val rawResponseStr = fullResponse.toString()
                if (rawResponseStr.isBlank()) {
                    throw IllegalStateException("The AI model returned an empty response.")
                }

                val parsedQuiz = parseQuizFromJson(
                    rawResponse = rawResponseStr,
                    fallbackTopic = sanitizedTopic,
                    count = count,
                    defaultType = type
                )

                // Attach current user's UID to creatorId
                val currentUid = auth.currentUser?.uid
                val newQuiz = parsedQuiz.copy(
                    creatorId = currentUid,
                    isAiGenerated = true
                )

                // Save to Firestore BEFORE updating the local UI state
                quizRepository.saveCustomQuiz(newQuiz)

                // Update local UI state
                userCustomQuizzes = listOf(newQuiz) + userCustomQuizzes.filter { it.id != newQuiz.id }
                combineAndEmitQuizzes()

                _isGeneratingQuiz.value = false
                _quizGenerationSuccess.emit(newQuiz)
                onSuccess(newQuiz)
                Log.d(TAG, "Successfully created and persisted custom AI quiz: ${newQuiz.title} for user $currentUid")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating custom AI quiz", e)
                _isGeneratingQuiz.value = false
                val errorMsg = e.localizedMessage ?: "Failed to generate quiz. Please try again."
                _quizGenerationError.emit(errorMsg)
                onError(errorMsg)
            }
        }
    }

    /**
     * Parses the AI-generated JSON response into a Quiz data model.
     * Safely handles markdown fences, extra whitespace, and missing fields.
     */
    fun parseQuizFromJson(
        rawResponse: String,
        fallbackTopic: String,
        count: Int,
        defaultType: String
    ): Quiz {
        val sanitizedJson = cleanRawJsonResponse(rawResponse)
        val root = JSONObject(sanitizedJson)

        val id = root.optString("id", "ai_${System.currentTimeMillis()}")
        val title = root.optString("title", fallbackTopic.ifBlank { "Custom Quiz" })
        val description = root.optString("description", "Custom AI Quiz on $title")
        val calculatedMaxXp = count * 20
        val maxXp = if (root.has("maxXp") && root.optInt("maxXp") > 0) {
            root.optInt("maxXp")
        } else {
            calculatedMaxXp
        }

        val questionsArray = root.optJSONArray("questions") ?: JSONArray()
        val questionList = mutableListOf<Question>()

        for (i in 0 until questionsArray.length()) {
            val qObj = questionsArray.getJSONObject(i)
            val qId = qObj.optString("id", "q_${i + 1}")
            val qText = qObj.optString("text", "Question ${i + 1}")
            val typeStr = qObj.optString("type", defaultType)
            val qType = if (typeStr.equals("SHORT_ANSWER", ignoreCase = true) ||
                typeStr.contains("SHORT", ignoreCase = true)
            ) {
                QuestionType.SHORT_ANSWER
            } else {
                QuestionType.MULTIPLE_CHOICE
            }

            val options = if (qType == QuestionType.MULTIPLE_CHOICE) {
                val optArr = qObj.optJSONArray("options")
                if (optArr != null) {
                    val list = mutableListOf<String>()
                    for (j in 0 until optArr.length()) {
                        list.add(optArr.getString(j))
                    }
                    list
                } else {
                    listOf("Option A", "Option B", "Option C", "Option D")
                }
            } else {
                null
            }

            val correctAnswer = qObj.optString("correctAnswer", "")

            questionList.add(
                Question(
                    id = qId,
                    text = qText,
                    type = qType,
                    options = options,
                    correctAnswer = correctAnswer
                )
            )
        }

        if (questionList.isEmpty()) {
            throw IllegalArgumentException("AI response did not contain any valid questions.")
        }

        return Quiz(
            id = id,
            title = title,
            description = description,
            maxXp = maxXp,
            questions = questionList,
            isAiGenerated = true
        )
    }

    /**
     * Cleans raw JSON text by stripping code fence delimiters and extracting the JSON object.
     */
    fun cleanRawJsonResponse(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            cleaned = cleaned.replace(Regex("\\s*```$"), "")
            cleaned = cleaned.trim()
        }
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1)
        }
        return cleaned
    }
}
