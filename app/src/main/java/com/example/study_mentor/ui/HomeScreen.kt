package com.example.study_mentor.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.study_mentor.data.UserStats
import com.example.study_mentor.data.currentLevelXp
import com.example.study_mentor.data.level
import com.example.study_mentor.data.levelProgress
import com.example.study_mentor.data.title
import com.example.study_mentor.ui.theme.BackgroundOffWhite
import com.example.study_mentor.ui.theme.PrimaryBlue
import com.example.study_mentor.ui.theme.PrimaryOrange

@Composable
fun HomeScreen(
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToAskAI: (initialPrompt: String?) -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val userStats by authViewModel.userStats.collectAsState()
    val user = authState.currentUser

    val selectedSubject by homeViewModel.selectedSubject.collectAsState()
    val recentQuestions by homeViewModel.recentQuestions.collectAsState()
    val filteredSuggestions = remember(selectedSubject) {
        homeViewModel.getFilteredSuggestions(selectedSubject)
    }

    var searchQuery by remember { mutableStateOf("") }
    val timeGreeting = remember { homeViewModel.getTimeBasedGreeting() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOffWhite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // ── 1. Top Row: Time-Aware Greeting & Bug-Free Sign In Button ─────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayName = if (user != null) {
                user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "Learner"
            } else {
                "Learner"
            }

            // Greeting text takes available space with weight(1f) to prevent squeezing the button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$timeGreeting, $displayName! 👋",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Ready to level up your study skills today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // "Sign In" button with wrapContentWidth and single line to prevent vertical character wrapping
            if (user == null) {
                OutlinedButton(
                    onClick = onNavigateToSignIn,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = "Sign In",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // ── 2. Dynamic Gamification Dashboard (Crossfade Auth Animation) ────────
        Crossfade(
            targetState = user != null,
            animationSpec = tween(durationMillis = 400),
            label = "GamificationDashboardCrossfade"
        ) { isLoggedIn ->
            if (isLoggedIn) {
                PersonalizedProgressCard(userStats = userStats)
            } else {
                GuestPromotionalBanner(onNavigateToSignIn = onNavigateToSignIn)
            }
        }

        // ── 3. Visual Polish for the Search Bar with Drop Shadow & Gradient CTA ─
        ElevatedCard(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = PrimaryOrange.copy(alpha = 0.22f),
                    ambientColor = Color.Black.copy(alpha = 0.05f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFECE5),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = PrimaryOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Ask anything about your homework...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val prompt = searchQuery.trim()
                            if (prompt.isNotBlank()) {
                                searchQuery = ""
                                onNavigateToAskAI(prompt)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = PrimaryOrange
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Vibrant Gradient "Ask" Button CTA
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFF7043), // Vibrant Peach-Orange
                                    Color(0xFFE06835)  // Rich Terracotta Orange
                                )
                            )
                        )
                        .clickable {
                            val prompt = searchQuery.trim()
                            if (prompt.isNotBlank()) {
                                searchQuery = ""
                                onNavigateToAskAI(prompt)
                            } else {
                                onNavigateToAskAI(null)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Ask",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // ── 4. Interactive Subject Categories (LazyRow FilterChips) ────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Explore by Subject",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(homeViewModel.subjects) { subject ->
                    val isSelected = selectedSubject == subject
                    FilterChip(
                        selected = isSelected,
                        onClick = { homeViewModel.selectSubject(subject) },
                        label = {
                            Text(
                                text = subject,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFFE2E8F0),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        )
                    )
                }
            }
        }

        // ── 5. "Recent Questions" Section with Full Empty State Handling ───────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Recent Questions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Surface(
                    color = Color(0xFFFFECE5),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (recentQuestions.isNotEmpty()) {
                // Compact Recent Activity Cards
                recentQuestions.forEach { item ->
                    RecentQuestionCard(
                        recentQuestion = item,
                        onClick = { onNavigateToAskAI(item.prompt) }
                    )
                }
            } else {
                // Friendly Empty State Card when student has not asked any questions yet
                EmptyRecentQuestionsCard()
            }
        }

        // ── 6. Enhanced "Try asking these" Suggestion Cards with Themed Tints ───
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedSubject == "All") "Try asking these" else "Try asking in $selectedSubject",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    color = Color(0xFFEEF2FF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${filteredSuggestions.size} suggestions",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Clickable Suggestion Cards styled with subject-specific tints, borders, and icons
            filteredSuggestions.forEach { suggestion ->
                QuestionCard(
                    question = suggestion.question,
                    icon = suggestion.icon,
                    tag = suggestion.subject,
                    onClick = { onNavigateToAskAI(suggestion.question) }
                )
            }
        }

        // Extra padding at bottom for navigation bar clearance
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Animated "Your Progress" ElevatedCard displayed for authenticated students.
 */
@Composable
private fun PersonalizedProgressCard(userStats: UserStats) {
    val animatedProgress by animateFloatAsState(
        targetValue = userStats.levelProgress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "xpProgressBarAnimation"
    )

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Level & Title, Daily Streak Fire Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFECE5),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Level",
                                tint = PrimaryOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Level ${userStats.level} • ${userStats.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${userStats.totalXp} Total XP Earned",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                // Daily Streak Pill Badge with Fire Icon
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFCC80))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${userStats.streakDays} Day Streak",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            // XP Progress Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "XP to Level ${userStats.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "${userStats.currentLevelXp} / 100 XP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryOrange
                )
            }

            // Animated Smooth Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = PrimaryOrange,
                trackColor = Color(0xFFFFE0D4)
            )
        }
    }
}

/**
 * Promotional Banner displayed when student is in guest mode.
 */
@Composable
private fun GuestPromotionalBanner(
    onNavigateToSignIn: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFE06835), // Primary Soft Orange
                            Color(0xFFFF8A50)  // Warm Peach Glow
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFE082),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "LEVEL UP YOUR LEARNING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.95f),
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "Track your progress for free: Sign up to save answers, earn XP, maintain streaks, and unlock custom AI quizzes.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Button(
                    onClick = onNavigateToSignIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = PrimaryOrange
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create free account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Compact Recent Activity Question Card.
 * /* TODO: Fetch from Room/Firestore History */
 */
@Composable
private fun RecentQuestionCard(
    recentQuestion: RecentQuestion,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF1F5F9),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recentQuestion.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recentQuestion.timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Text(
                        text = recentQuestion.subject,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryOrange
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Ask again",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Friendly Empty State Card displayed when student has no recent questions.
 */
@Composable
private fun EmptyRecentQuestionsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFECE5),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = PrimaryOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = "No questions asked yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "You haven't asked any questions yet. Start exploring above!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }
    }
}

/**
 * Visual styling theme for subjects to give suggestion cards distinct personalities.
 */
private data class SubjectStyle(
    val backgroundColor: Color,
    val borderColor: Color,
    val iconColor: Color,
    val iconContainerColor: Color
)

private fun getSubjectStyle(subject: String): SubjectStyle {
    return when (subject.lowercase().trim()) {
        "math" -> SubjectStyle(
            backgroundColor = Color(0xFFF8FAFC),
            borderColor = Color(0xFFD4E2FC),
            iconColor = Color(0xFF2563EB),
            iconContainerColor = Color(0xFFDBEAFE)
        )
        "science" -> SubjectStyle(
            backgroundColor = Color(0xFFF0FDF4),
            borderColor = Color(0xFFBBF7D0),
            iconColor = Color(0xFF16A34A),
            iconContainerColor = Color(0xFFDCFCE7)
        )
        "history" -> SubjectStyle(
            backgroundColor = Color(0xFFFFFBEB),
            borderColor = Color(0xFFFDE68A),
            iconColor = Color(0xFFD97706),
            iconContainerColor = Color(0xFFFEF3C7)
        )
        "literature" -> SubjectStyle(
            backgroundColor = Color(0xFFFAF5FF),
            borderColor = Color(0xFFE9D5FF),
            iconColor = Color(0xFF9333EA),
            iconContainerColor = Color(0xFFF3E8FF)
        )
        else -> SubjectStyle(
            backgroundColor = Color(0xFFFFF7ED),
            borderColor = Color(0xFFFFEDD5),
            iconColor = Color(0xFFE06835),
            iconContainerColor = Color(0xFFFFECE5)
        )
    }
}

/**
 * Enhanced Clickable Question Card featuring soft background tint,
 * subtle colored border, and subject-specific icon badge.
 */
@Composable
fun QuestionCard(
    question: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit = {}
) {
    val style = remember(tag) { getSubjectStyle(tag) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = style.backgroundColor),
        border = BorderStroke(1.dp, style.borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subject-colored Icon Circle Container
            Surface(
                shape = CircleShape,
                color = style.iconContainerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tag,
                        tint = style.iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Subject Badge with Icon + Tag
                Surface(
                    color = style.iconContainerColor,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = style.iconColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = style.iconColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Ask this question",
                tint = style.iconColor.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
