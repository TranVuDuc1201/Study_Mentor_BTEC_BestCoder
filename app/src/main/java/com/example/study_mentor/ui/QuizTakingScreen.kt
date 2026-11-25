package com.example.study_mentor.ui

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.study_mentor.R
import com.example.study_mentor.data.Question
import com.example.study_mentor.data.QuestionType
import com.example.study_mentor.data.Quiz
import com.example.study_mentor.ui.theme.BackgroundOffWhite
import com.example.study_mentor.ui.theme.PrimaryBlue
import com.example.study_mentor.ui.theme.SecondaryGreen

@Composable
fun QuizTakingScreen(
    quizId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: PracticeViewModel = viewModel()
) {
    val context = LocalContext.current

    // Instantiate MediaPlayer exactly once across recompositions to prevent stuttering/re-triggering
    val mediaPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.bg_music_beneath_rain)?.apply {
                isLooping = true
            }
        } catch (e: Exception) {
            null
        }
    }

    // Wrap start() and release() strictly inside DisposableEffect(Unit)
    DisposableEffect(Unit) {
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Ignore if playback fails
        }

        onDispose {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.stop()
                }
            } catch (_: Exception) {}
            mediaPlayer?.release()
        }
    }

    val quizzes by viewModel.quizzes.collectAsState()
    val quiz = remember(quizzes, quizId) {
        quizzes.find { it.id == quizId }
    }

    androidx.compose.runtime.LaunchedEffect(quizId) {
        if (quiz == null) {
            viewModel.getQuizById(quizId)
        }
    }

    Scaffold(
        containerColor = BackgroundOffWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (quiz == null) {
                // Loading or Not Found State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Quiz...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onNavigateBack) {
                        Text("Return to Practice")
                    }
                }
            } else if (quiz.questions.isEmpty()) {
                // Empty Quiz Fallback
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No questions found for this quiz.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            } else {
                // Active Quiz Taking Flow
                QuizActiveSession(
                    quiz = quiz,
                    onQuizCompleted = { correctAnswers ->
                        viewModel.submitQuizResults(quiz.id, correctAnswers)
                    },
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun QuizActiveSession(
    quiz: Quiz,
    onQuizCompleted: (correctCount: Int) -> Int,
    onNavigateBack: () -> Unit
) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var correctAnswersCount by remember { mutableIntStateOf(0) }

    // Map to track user answers for all questions (used for final scoring upon submission)
    val userAnswers = remember { mutableStateMapOf<Int, String>() }

    // User response for current question
    var selectedOption by remember(currentQuestionIndex) {
        mutableStateOf(userAnswers[currentQuestionIndex])
    }
    var shortAnswerText by remember(currentQuestionIndex) {
        mutableStateOf(userAnswers[currentQuestionIndex] ?: "")
    }

    // Completion Dialog State
    var showCompletionDialog by remember { mutableStateOf(false) }
    var earnedXpResult by remember { mutableIntStateOf(0) }
    var isReviewing by remember { mutableStateOf(false) }

    val totalQuestions = quiz.questions.size
    val currentQuestion = quiz.questions.getOrNull(currentQuestionIndex) ?: return
    val isLastQuestion = currentQuestionIndex == totalQuestions - 1

    val animatedProgress by animateFloatAsState(
        targetValue = (currentQuestionIndex + 1).toFloat() / totalQuestions.toFloat(),
        label = "quiz_progress"
    )

    // Check if the current question has a non-blank user answer
    val hasAnsweredCurrent = when (currentQuestion.type) {
        QuestionType.MULTIPLE_CHOICE -> selectedOption != null
        QuestionType.SHORT_ANSWER -> shortAnswerText.trim().isNotBlank()
    }

    if (isReviewing) {
        QuizReviewView(
            quiz = quiz,
            userAnswers = userAnswers,
            onNavigateBack = onNavigateBack
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        // ── Top Header & Navigation ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit Quiz",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quiz.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Question ${currentQuestionIndex + 1} of $totalQuestions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // Max XP Pill
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${quiz.maxXp} XP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // ── Progress Bar ────────────────────────────────────────────────────
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // ── Question Card ───────────────────────────────────────────────────
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Question Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (currentQuestion.type) {
                        QuestionType.MULTIPLE_CHOICE -> Color(0xFFE0E7FF)
                        QuestionType.SHORT_ANSWER -> Color(0xFFFEF3C7)
                    }
                ) {
                    Text(
                        text = when (currentQuestion.type) {
                            QuestionType.MULTIPLE_CHOICE -> "Multiple Choice"
                            QuestionType.SHORT_ANSWER -> "Short Answer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (currentQuestion.type) {
                            QuestionType.MULTIPLE_CHOICE -> PrimaryBlue
                            QuestionType.SHORT_ANSWER -> Color(0xFFD97706)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Question Prompt
                Text(
                    text = currentQuestion.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ── Question Options / Input ─────────────────────────────────────────
        when (currentQuestion.type) {
            QuestionType.MULTIPLE_CHOICE -> {
                val options = currentQuestion.options.orEmpty()
                val isFeedbackActive = quiz.showInstantFeedback && selectedOption != null

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = selectedOption == option
                        val isCorrectAnswer = option.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)

                        val backgroundColor: Color
                        val contentColor: Color
                        val borderColor: Color

                        if (isFeedbackActive) {
                            when {
                                // 1. Current option is the correctAnswer -> distinct Green, regardless of user pick
                                isCorrectAnswer -> {
                                    backgroundColor = Color(0xFF4CAF50)
                                    contentColor = Color.White
                                    borderColor = Color(0xFF4CAF50)
                                }
                                // 2. Current option is selectedOption AND NOT the correctAnswer -> distinct Red
                                isSelected && !isCorrectAnswer -> {
                                    backgroundColor = Color(0xFFF44336)
                                    contentColor = Color.White
                                    borderColor = Color(0xFFF44336)
                                }
                                // 3. Other unselected, wrong options remain default/gray
                                else -> {
                                    backgroundColor = Color(0xFFF5F5F5)
                                    contentColor = Color(0xFF757575)
                                    borderColor = Color(0xFFE0E0E0)
                                }
                            }
                        } else if (!quiz.showInstantFeedback && isSelected) {
                            // Instant feedback off: highlight selectedOption with PrimaryBlue, user can change answer
                            backgroundColor = PrimaryBlue
                            contentColor = Color.White
                            borderColor = PrimaryBlue
                        } else {
                            // Default unselected state
                            backgroundColor = Color.White
                            contentColor = MaterialTheme.colorScheme.onSurface
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        }

                        val selectAction = {
                            if (!isFeedbackActive) {
                                selectedOption = option
                                userAnswers[currentQuestionIndex] = option
                            }
                        }

                        OutlinedCard(
                            onClick = selectAction,
                            enabled = !isFeedbackActive,
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = backgroundColor,
                                disabledContainerColor = backgroundColor
                            ),
                            border = BorderStroke(
                                width = if (isSelected || (isFeedbackActive && isCorrectAnswer)) 2.dp else 1.dp,
                                color = borderColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected || (isFeedbackActive && isCorrectAnswer),
                                    onClick = selectAction,
                                    enabled = !isFeedbackActive,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = contentColor,
                                        unselectedColor = if (isFeedbackActive) Color(0xFF9E9E9E) else MaterialTheme.colorScheme.outlineVariant,
                                        disabledSelectedColor = contentColor,
                                        disabledUnselectedColor = Color(0xFFBDBDBD)
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected || (isFeedbackActive && isCorrectAnswer)) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isFeedbackActive) {
                                    if (isCorrectAnswer) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Correct",
                                            tint = contentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Incorrect",
                                            tint = contentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Instant feedback helper banner
                    if (isFeedbackActive) {
                        val isUserCorrect = selectedOption?.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUserCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, if (isUserCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isUserCorrect) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (isUserCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (isUserCorrect) "Correct! Well done." else "Incorrect. The correct answer is: ${currentQuestion.correctAnswer}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isUserCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                    if (currentQuestion.explanation.isNotBlank()) {
                                        Text(
                                            text = currentQuestion.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isUserCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            QuestionType.SHORT_ANSWER -> {
                OutlinedCard(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Your Answer",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shortAnswerText,
                            onValueChange = {
                                shortAnswerText = it
                                userAnswers[currentQuestionIndex] = it
                            },
                            placeholder = { Text("Type your answer here...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { /* IME done */ }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(16.dp))

        // ── Next / Submit Button ─────────────────────────────────────────────
        Button(
            onClick = {
                // Record the final response for this question in userAnswers map
                val currentAnswer = when (currentQuestion.type) {
                    QuestionType.MULTIPLE_CHOICE -> selectedOption.orEmpty().trim()
                    QuestionType.SHORT_ANSWER -> shortAnswerText.trim()
                }
                userAnswers[currentQuestionIndex] = currentAnswer

                if (!isLastQuestion) {
                    currentQuestionIndex++
                } else {
                    // Final question submitted!
                    // Calculate the final score only upon complete submission
                    val totalCorrect = quiz.questions.indices.count { idx ->
                        val q = quiz.questions[idx]
                        val ans = userAnswers[idx].orEmpty().trim()
                        ans.equals(q.correctAnswer.trim(), ignoreCase = true)
                    }
                    correctAnswersCount = totalCorrect
                    earnedXpResult = onQuizCompleted(totalCorrect)
                    showCompletionDialog = true
                }
            },
            enabled = hasAnsweredCurrent,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLastQuestion) "Submit Quiz" else "Next Question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isLastQuestion) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // ── Celebratory Completion Dialog ───────────────────────────────────────
    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = { /* Force explicit button click */ },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Trophy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Quiz Completed! 🎉",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Great job finishing ${quiz.title}!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BackgroundOffWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Score: $correctAnswersCount / $totalQuestions correct",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "XP",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "+$earnedXpResult XP Earned",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showCompletionDialog = false
                            isReviewing = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Review Answers",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            showCompletionDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Return to Practice",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            containerColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
    }
}

/**
 * Full-screen Answer Review View allowing users to scroll through all quiz questions,
 * inspect their own submitted answers, view the correct answers for any missed questions,
 * and read pedagogical explanations.
 */
@Composable
fun QuizReviewView(
    quiz: Quiz,
    userAnswers: Map<Int, String>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalQuestions = quiz.questions.size
    val correctCount = quiz.questions.indices.count { idx ->
        val q = quiz.questions[idx]
        val ans = userAnswers[idx].orEmpty().trim()
        ans.equals(q.correctAnswer.trim(), ignoreCase = true)
    }

    Scaffold(
        containerColor = BackgroundOffWhite,
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to Practice",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Answer Review",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = quiz.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "$correctCount / $totalQuestions Correct",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800), // Primary soft-orange
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Return to Practice",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(quiz.questions) { index, question ->
                val userAnswer = userAnswers[index]?.trim()
                val isCorrect = !userAnswer.isNullOrBlank() &&
                    userAnswer.equals(question.correctAnswer.trim(), ignoreCase = true)

                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header Row: Question Number + Status pill/badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Question ${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (question.type) {
                                        QuestionType.MULTIPLE_CHOICE -> Color(0xFFEEF2FF)
                                        QuestionType.SHORT_ANSWER -> Color(0xFFFEF3C7)
                                    }
                                ) {
                                    Text(
                                        text = when (question.type) {
                                            QuestionType.MULTIPLE_CHOICE -> "MCQ"
                                            QuestionType.SHORT_ANSWER -> "Short Answer"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when (question.type) {
                                            QuestionType.MULTIPLE_CHOICE -> PrimaryBlue
                                            QuestionType.SHORT_ANSWER -> Color(0xFFD97706)
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Status Badge: Correct / Incorrect
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                border = BorderStroke(1.dp, if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isCorrect) "Correct" else "Incorrect",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }
                        }

                        // Question Text
                        Text(
                            text = question.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Your Answer Row
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCorrect) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                            border = BorderStroke(
                                1.dp,
                                if (isCorrect) Color(0xFFBBF7D0) else Color(0xFFFECACA)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Your Answer: ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = if (userAnswer.isNullOrBlank()) "Not answered" else userAnswer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCorrect) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                        }

                        // Correct Answer Row (Displayed clearly if user was incorrect/unanswered)
                        if (!isCorrect) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Correct Answer: ${question.correctAnswer}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        }

                        // Explanation (if available) in a soft tinted box labeled "Explanation"
                        if (question.explanation.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFF7ED), // Soft warm amber/orange tint
                                border = BorderStroke(1.dp, Color(0xFFFFEDD5)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFFEA580C),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Explanation",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEA580C)
                                        )
                                    }
                                    Text(
                                        text = question.explanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9A3412)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
