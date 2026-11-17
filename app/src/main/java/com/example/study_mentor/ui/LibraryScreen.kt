package com.example.study_mentor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.study_mentor.data.ChatSession
import com.example.study_mentor.ui.theme.BackgroundOffWhite
import com.example.study_mentor.ui.theme.PrimaryBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Visual styling metadata for subject categories in the Library screen.
 */
private data class LibrarySubjectConfig(
    val icon: ImageVector,
    val primaryColor: Color,
    val containerColor: Color,
    val label: String
)

/**
 * Returns themed icon, primary accent, and container color based on the session's explicit subject or title.
 */
private fun getLibrarySubjectConfig(session: ChatSession): LibrarySubjectConfig {
    val effectiveSubject = session.displaySubject.lowercase().trim()
    val titleLower = session.title.lowercase().trim()

    return when {
        effectiveSubject == "math" || titleLower.contains("math") || titleLower.contains("derivative") ||
                titleLower.contains("integral") || titleLower.contains("algebra") ||
                titleLower.contains("equation") || titleLower.contains("solve") -> LibrarySubjectConfig(
            icon = Icons.Default.Calculate,
            primaryColor = Color(0xFF2563EB),     // Vibrant Royal Blue
            containerColor = Color(0xFFEFF6FF),   // Soft Blue Pastel
            label = "Math"
        )

        effectiveSubject == "physics" || titleLower.contains("physics") || titleLower.contains("gravity") ||
                titleLower.contains("newton") || titleLower.contains("velocity") -> LibrarySubjectConfig(
            icon = Icons.Default.Science,
            primaryColor = Color(0xFF0D9488),     // Modern Teal Accent
            containerColor = Color(0xFFCCFBF1),   // Light Teal Pastel
            label = "Physics"
        )

        effectiveSubject == "chemistry" || titleLower.contains("chemistry") || titleLower.contains("molecule") ||
                titleLower.contains("atom") || titleLower.contains("reaction") || titleLower.contains("acid") -> LibrarySubjectConfig(
            icon = Icons.Default.Science,
            primaryColor = Color(0xFF059669),     // Forest Emerald
            containerColor = Color(0xFFD1FAE5),   // Light Mint Pastel
            label = "Chemistry"
        )

        effectiveSubject == "literature" || titleLower.contains("literature") || titleLower.contains("poem") ||
                titleLower.contains("novel") || titleLower.contains("essay") -> LibrarySubjectConfig(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            primaryColor = Color(0xFF7C3AED),     // Royal Violet
            containerColor = Color(0xFFF5F3FF),   // Lavender Pastel
            label = "Literature"
        )

        effectiveSubject == "history" || titleLower.contains("history") || titleLower.contains("war") ||
                titleLower.contains("revolution") -> LibrarySubjectConfig(
            icon = Icons.Default.HistoryEdu,
            primaryColor = Color(0xFFD97706),     // Warm Amber
            containerColor = Color(0xFFFEF3C7),   // Golden Honey Pastel
            label = "History"
        )

        else -> LibrarySubjectConfig(
            icon = Icons.AutoMirrored.Filled.Chat,
            primaryColor = PrimaryBlue,          // Study Mentor Signature Soft Orange
            containerColor = Color(0xFFFFEDE7),   // Gentle Terracotta Peach
            label = if (session.displaySubject != "General") session.displaySubject else "General"
        )
    }
}

@Composable
fun LibraryScreen(
    authViewModel: AuthViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    onSessionClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = authState.currentUser

    val allSessions by libraryViewModel.sessions.collectAsState()
    val filteredSessions by libraryViewModel.filteredSessions.collectAsState()
    val searchQuery by libraryViewModel.searchQuery.collectAsState()
    val selectedCategory by libraryViewModel.selectedCategory.collectAsState()
    val isFilterVisible by libraryViewModel.isFilterVisible.collectAsState()

    // Sync user sessions when user logs in
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            libraryViewModel.fetchSessions()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOffWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ─── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "My Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your AI study conversations & solutions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentUser == null) {
            // ─── Logged-out State ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(96.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color.Gray,
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Your library is waiting",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Log in to see your saved questions, solutions, and full study history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = onNavigateToLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(
                            text = "Log In / Sign Up",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            // ─── Authenticated Content ──────────────────────────────────────

            // 1. Search Bar with Filter Button
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { libraryViewModel.onSearchQueryChange(it) },
                placeholder = { Text("Search your chats and notes...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotBlank()) PrimaryBlue else Color.Gray
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { libraryViewModel.onSearchQueryChange("") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Filter Toggle Button
                        IconButton(
                            onClick = { libraryViewModel.toggleFilterVisibility() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isFilterVisible || selectedCategory != "All") {
                                    PrimaryBlue.copy(alpha = 0.15f)
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Toggle category filters",
                                        tint = if (isFilterVisible || selectedCategory != "All") {
                                            PrimaryBlue
                                        } else {
                                            Color.Gray
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                singleLine = true
            )

            // Animated Filter Chips Row
            AnimatedVisibility(
                visible = isFilterVisible,
                enter = expandVertically(animationSpec = spring()) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring()) + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(libraryViewModel.categories) { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { libraryViewModel.onCategorySelect(category) },
                                label = {
                                    Text(
                                        text = category,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── List / Empty State Switching ───────────────────────────────
            if (allSessions.isEmpty()) {
                // Empty State 1: Completely empty library
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFEDE7),
                            modifier = Modifier.size(92.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Your library is empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Start asking the AI to build your study history! Every question, solution, and formula will be automatically saved here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredSessions.isEmpty()) {
                // Empty State 2: Search or Category filter returned no results
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FilterAltOff,
                                    contentDescription = "No results",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No matching chats found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No chats matched \"$searchQuery\" in \"$selectedCategory\"."
                            } else {
                                "No chats found in category \"$selectedCategory\"."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = { libraryViewModel.resetFilters() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Filters")
                        }
                    }
                }
            } else {
                // ─── Chat Sessions List with Swipe-to-Delete ────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp)
                ) {
                    items(
                        items = filteredSessions,
                        key = { it.id }
                    ) { session ->
                        SwipeableChatSessionItem(
                            session = session,
                            onClick = { onSessionClick(session.id) },
                            onDelete = { libraryViewModel.deleteChat(session.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wraps the ElevatedCard in a Material 3 SwipeToDismissBox with animated delete background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableChatSessionItem(
    session: ChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled

            val backgroundColor by animateColorAsState(
                targetValue = if (isDismissing) Color(0xFFEF4444) else Color(0xFFFEE2E2),
                label = "deleteBackgroundColor"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Chat",
                        tint = if (isDismissing) Color.White else Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Delete",
                        color = if (isDismissing) Color.White else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) {
        PremiumChatSessionCard(
            session = session,
            onClick = onClick
        )
    }
}

/**
 * Premium Chat History Card designed with ElevatedCard, circular colored subject badge,
 * explicit metadata tags, bold prompt title, preview subtitle, and timestamp.
 */
@Composable
fun PremiumChatSessionCard(
    session: ChatSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subjectConfig = remember(session.subject, session.category, session.title) {
        getLibrarySubjectConfig(session)
    }

    val formattedDate = remember(session.createdAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(session.createdAt))
    }

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading: Circular Subject Icon with vibrant accent container
            Surface(
                shape = CircleShape,
                color = subjectConfig.containerColor,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = subjectConfig.icon,
                        contentDescription = subjectConfig.label,
                        tint = subjectConfig.primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle Column: Subject Tag + Format Tag + Title + Subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Top Metadata Row: Subject Tag + Format Tag + Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Exact Subject Tag Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = subjectConfig.containerColor
                    ) {
                        Text(
                            text = subjectConfig.label,
                            color = subjectConfig.primaryColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Optional Output Format Tag Badge
                    if (!session.outputFormat.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = session.outputFormat,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Formatted Date
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title (User Prompt) - Bold, 1 line, ellipsis
                Text(
                    text = session.title.ifBlank { "Untitled Question" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle (AI Response Preview) - Gray, 1 line, ellipsis
                Text(
                    text = session.lastResponsePreview.ifBlank {
                        "Tap to open and view the AI mentor explanation..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing: Forward Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Chat",
                tint = Color.LightGray.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
