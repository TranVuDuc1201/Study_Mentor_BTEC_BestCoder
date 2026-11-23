package com.example.study_mentor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.study_mentor.data.Quiz
import com.example.study_mentor.ui.theme.BackgroundOffWhite
import com.example.study_mentor.ui.theme.PrimaryBlue
import com.example.study_mentor.ui.theme.SecondaryGreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    onNavigateToQuiz: (String) -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    viewModel: PracticeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()
    val isGenerating by viewModel.isGeneratingQuiz.collectAsState()

    // 1. Data Model & UI Split: System vs. Custom AI Quizzes
    val systemQuizzes = remember(quizzes) { quizzes.filter { !it.isAiGenerated } }
    val aiCustomQuizzes = remember(quizzes) { quizzes.filter { it.isAiGenerated } }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showAuthRequiredDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 2. Strict Auth Gate for AI Quiz Generation
    val handleCreateQuizClick: () -> Unit = {
        if (FirebaseAuth.getInstance().currentUser != null) {
            showBottomSheet = true
        } else {
            showAuthRequiredDialog = true
        }
    }

    // Listen for generation errors
    LaunchedEffect(Unit) {
        viewModel.quizGenerationError.collect { errorMsg ->
            snackbarHostState.showSnackbar(
                message = "⚠️ $errorMsg",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Listen for generation success
    LaunchedEffect(Unit) {
        viewModel.quizGenerationSuccess.collect { newQuiz ->
            val result = snackbarHostState.showSnackbar(
                message = "✨ AI Quiz '${newQuiz.title}' created!",
                actionLabel = "Start Quiz",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToQuiz(newQuiz.id)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundOffWhite),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Screen Header
            item {
                Column {
                    Text(
                        text = "Daily Practice & Assessments",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Practice curriculum topics, sharpen problem-solving skills, and earn XP",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }

            // 2. Daily Streak Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                            ),
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Streak",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "14-Day Streak! Complete today's quiz to keep it going",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 3. AI Custom Quiz Generator Card (Prominent Banner with Gradient and Sparkles)
            item {
                AiQuizGeneratorCard(
                    onClick = handleCreateQuizClick
                )
            }

            // 4. Section 1: "Your AI Quizzes" (Only show if aiCustomQuizzes is not empty)
            if (aiCustomQuizzes.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Your AI Quizzes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                color = Color(0xFFF3E8FF),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${aiCustomQuizzes.size}",
                                    color = Color(0xFF9333EA),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Quizzes dynamically generated by AI tailored to your study preferences",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }

                items(
                    items = aiCustomQuizzes,
                    key = { it.id }
                ) { quiz ->
                    QuizCard(
                        quiz = quiz,
                        onStartQuiz = {
                            onNavigateToQuiz(quiz.id)
                        }
                    )
                }
            }

            // 5. Section 2: "System Challenges"
            item {
                Column {
                    Text(
                        text = "Standard Curriculum Challenges",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${systemQuizzes.size} standard curriculum quizzes available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            items(
                items = systemQuizzes,
                key = { it.id }
            ) { quiz ->
                QuizCard(
                    quiz = quiz,
                    onStartQuiz = {
                        onNavigateToQuiz(quiz.id)
                    }
                )
            }
        }

        // Floating Snackbar for user feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
        )

        // Loading Overlay while generating quiz
        if (isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = PrimaryBlue,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(64.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "AI is Crafting Your Quiz...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Structuring questions, verifying options, and calculating XP. This will take a few seconds ✨",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet to configure custom quiz preferences
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.resetGenerationStatus()
                showBottomSheet = false
            },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AiQuizBottomSheetContent(
                uiState = uiState,
                onDismiss = {
                    coroutineScope.launch {
                        sheetState.hide()
                        viewModel.resetGenerationStatus()
                        showBottomSheet = false
                    }
                },
                onGenerate = { topic, count, type, language, showInstantFeedback ->
                    viewModel.generateCustomQuiz(
                        topic = topic,
                        count = count,
                        type = type,
                        language = language,
                        showInstantFeedback = showInstantFeedback,
                        onSuccess = {
                            coroutineScope.launch {
                                sheetState.hide()
                                showBottomSheet = false
                            }
                        }
                    )
                }
            )
        }
    }

    // Strict Auth Gate Dialog when user attempts to create custom quiz while unauthenticated
    if (showAuthRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showAuthRequiredDialog = false },
            title = {
                Text(
                    text = "Authentication Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Authentication Required. Please log in to generate custom AI quizzes and earn XP.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAuthRequiredDialog = false
                        onNavigateToSignIn()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Log In", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAuthRequiredDialog = false }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

/**
 * Prominent card showcasing the AI Quiz Generator with a gradient background,
 * sparkle icon, and call-to-action button.
 */
@Composable
fun AiQuizGeneratorCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4338CA), // Deep Indigo
                            Color(0xFF6366F1), // Indigo
                            Color(0xFF8B5CF6), // Purple
                            Color(0xFFD946EF)  // Fuchsia
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Tag & Sparkle Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFFE082),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AI POWERED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Quiz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Title and Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Generate Custom Quiz with AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Prompt the AI on any topic, set question count & format, and earn up to 200 XP!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // Action Button
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF4F46E5)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create AI Quiz",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bottom Sheet content collecting quiz parameters: Topic, Number of Questions,
 * Question Type, and Language.
 */
@Composable
fun AiQuizBottomSheetContent(
    uiState: PracticeUiState = PracticeUiState(),
    onDismiss: () -> Unit,
    onGenerate: (topic: String, count: Int, type: String, language: String, showInstantFeedback: Boolean) -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var questionCount by remember { mutableIntStateOf(5) } // 5 or 10
    var questionType by remember { mutableStateOf("MULTIPLE_CHOICE") } // "MULTIPLE_CHOICE" or "SHORT_ANSWER"
    var language by remember { mutableStateOf("English") } // "English" or "Vietnamese"
    var showInstantFeedback by remember { mutableStateOf(true) }

    val topicSuggestions = listOf(
        "Calculus & Functions",
        "Harmonic Motion & Waves",
        "Organic Chemistry & Esters",
        "Genetics & Heredity",
        "World History",
        "Grammar & Reading Comprehension"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Scrollable content area for quiz configuration options
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sheet Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEEF2FF),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = "AI Custom Quiz Generator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize your AI study session",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Inline Error Banner with Retry Button
            val errorMessage = (uiState.generationStatus as? QuizGenerationStatus.Error)?.message
                ?: uiState.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "⚠️ Generation Failed",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFB91C1C)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                        Button(
                            onClick = {
                                if (topic.isNotBlank()) {
                                    onGenerate(topic.trim(), questionCount, questionType, language, showInstantFeedback)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // 1. Topic Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Topic or Subject *",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    placeholder = { Text("e.g., Quadratic Equations, Wave Optics, Organic Chemistry...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = PrimaryBlue
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = BackgroundOffWhite,
                        unfocusedContainerColor = BackgroundOffWhite
                    )
                )

                // Topic Suggestion Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topicSuggestions.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (topic == suggestion) Color(0xFFE0E7FF) else BackgroundOffWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (topic == suggestion) PrimaryBlue else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable { topic = suggestion }
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (topic == suggestion) PrimaryBlue else Color.DarkGray,
                                fontWeight = if (topic == suggestion) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 2. Number of Questions & Dynamic XP Display
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Number of Questions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Award: +${questionCount * 20} XP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD97706)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(5 to "100 Max XP", 10 to "200 Max XP").forEach { (count, xpTag) ->
                        val isSelected = questionCount == count
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else BackgroundOffWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { questionCount = count }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { questionCount = count },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Column {
                                    Text(
                                        text = "$count Questions",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) PrimaryBlue else Color.DarkGray
                                    )
                                    Text(
                                        text = xpTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Question Type Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Question Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        "MULTIPLE_CHOICE" to "Multiple Choice",
                        "SHORT_ANSWER" to "Short Answer"
                    ).forEach { (typeKey, label) ->
                        val isSelected = questionType == typeKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else BackgroundOffWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { questionType = typeKey }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { questionType = typeKey },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) PrimaryBlue else Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }

            // 4. Language Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Quiz Language",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("English", "Vietnamese").forEach { lang ->
                        val isSelected = language == lang
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else BackgroundOffWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { language = lang }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { language = lang },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Text(
                                    text = if (lang == "Vietnamese") "Tiếng Việt" else "English",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) PrimaryBlue else Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }

            // 5. Instant Feedback Configuration Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BackgroundOffWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Show instant correct/incorrect feedback",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (showInstantFeedback) "Reveal answers right after tapping" else "Keep answers hidden until completion",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = showInstantFeedback,
                        onCheckedChange = { showInstantFeedback = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue
                        )
                    )
                }
            }
        }

        // Pinned Bottom Button Container (Always visible & accessible)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // 6. Submit "Generate" Button
                Button(
                    onClick = {
                        if (topic.isNotBlank()) {
                            onGenerate(topic.trim(), questionCount, questionType, language, showInstantFeedback)
                        }
                    },
                    enabled = topic.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        disabledContainerColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate Quiz (+${questionCount * 20} Max XP)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Quiz Card binding real data from the Quiz model.
 * Adds visual distinction and "AI" badge for AI-generated quizzes.
 */
@Composable
fun QuizCard(
    quiz: Quiz,
    onStartQuiz: () -> Unit = {}
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Leading Icon with distinct styling for AI quizzes
                Surface(
                    shape = CircleShape,
                    color = if (quiz.isAiGenerated) Color(0xFFF3E8FF) else BackgroundOffWhite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (quiz.isAiGenerated) Icons.Filled.AutoAwesome else Icons.Outlined.Create,
                        contentDescription = if (quiz.isAiGenerated) "AI Quiz" else "Quiz",
                        tint = if (quiz.isAiGenerated) Color(0xFF9333EA) else PrimaryBlue,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Badges: Subject / AI Badge and XP Tags
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (quiz.isAiGenerated) {
                            Surface(
                                color = Color(0xFFF3E8FF),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = Color(0xFF9333EA),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "AI Custom",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9333EA),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            TagPill(
                                text = "Standard",
                                backgroundColor = Color(0xFFE0E7FF),
                                textColor = PrimaryBlue
                            )
                        }

                        TagPill(
                            text = "+${quiz.maxXp} XP",
                            backgroundColor = Color(0xFFFEF3C7),
                            textColor = Color(0xFFD97706)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = quiz.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${quiz.questions.size} questions • Max ${quiz.maxXp} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    if (quiz.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quiz.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Readiness indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SecondaryGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ready to start",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Complete Quiz Button
                Button(
                    onClick = onStartQuiz,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Complete Quiz", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Backward compatibility overload for QuizCard.
 */
@Composable
fun QuizCard(
    title: String,
    subject: String,
    difficulty: String,
    subtitle: String,
    icon: ImageVector,
    onPlayClick: () -> Unit = {}
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = CircleShape,
                    color = BackgroundOffWhite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagPill(text = subject, backgroundColor = Color(0xFFE0E7FF), textColor = PrimaryBlue)
                        TagPill(text = difficulty, backgroundColor = Color(0xFFFEF3C7), textColor = Color(0xFFD97706))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SecondaryGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ready to start",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Complete Quiz", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TagPill(text: String, backgroundColor: Color, textColor: Color) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
