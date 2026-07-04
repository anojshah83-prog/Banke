package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.res.stringResource
import com.example.R
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val keyboardController = LocalSoftwareKeyboardController.current

    // UI States
    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedImageBase64 by viewModel.selectedImageBase64.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()

    // Dialogs
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Text To Speech
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsActiveMessageId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                viewModel.setAttachmentImage(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Decode current attached image if any, to render a thumbnail
    val attachedBitmap = remember(selectedImageBase64) {
        if (selectedImageBase64 != null) {
            try {
                val decodedString = android.util.Base64.decode(selectedImageBase64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Modal Drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                // Sidebar Title with Gradient Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GeminiBlue, GeminiPurple, GeminiMagenta)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Conversations",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // New Chat Button
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = "New Chat") },
                    label = { Text("Start fresh chat", fontWeight = FontWeight.SemiBold) },
                    selected = currentSessionId == null,
                    onClick = {
                        viewModel.startNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // List of past chats
                Text(
                    text = "Recent History",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (sessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No previous chats yet",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == currentSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .clickable {
                                        viewModel.selectSession(session.id)
                                        coroutineScope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = session.title,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Rename
                                IconButton(
                                    onClick = {
                                        sessionToRename = session
                                        renameText = session.title
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Delete
                                IconButton(
                                    onClick = { sessionToDelete = session },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // App Branding Footer in drawer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.app_name)} AI • Prototyping Mode",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(GeminiBlue, GeminiPurple, GeminiMagenta)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = TextStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFE3E3E3), Color(0xFF9C9C9C))
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 20.sp
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Toggle History Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main Content
                if (currentSessionId == null || messages.isEmpty()) {
                    // ONBOARDING / EMPTY STATE SCREEN
                    OnboardingView(
                        onPromptSelected = { prompt ->
                            viewModel.sendMessage(prompt)
                        }
                    )
                } else {
                    // CHAT HISTORIC THREAD VIEW
                    val listState = rememberLazyListState()

                    // Automatically scroll to the bottom when a new message appears
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 90.dp), // Space for floating input container
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubbleItem(
                                message = message,
                                isTtsSpeaking = ttsActiveMessageId == message.id,
                                onSpeakClicked = {
                                    if (ttsActiveMessageId == message.id) {
                                        tts?.stop()
                                        ttsActiveMessageId = null
                                    } else {
                                        tts?.stop()
                                        ttsActiveMessageId = message.id
                                        tts?.speak(
                                            message.text.replace(Regex("[*#`]"), ""), // strip markdown
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            message.id.toString()
                                        )
                                    }
                                }
                            )
                        }

                        // Sparkle loader for generating response
                        if (isLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(GeminiBlue, GeminiPurple, GeminiMagenta)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    // Soft pulsing typing indicator dots
                                    Text(
                                        text = "Gemini is typing...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.animatePulsing()
                                    )
                                }
                            }
                        }
                    }
                }

                // FLOATING INPUT ANCHOR FOOTER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    // Floating Attachment Thumbnail Row
                    AnimatedVisibility(
                        visible = attachedBitmap != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        attachedBitmap?.let { bitmap ->
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Attachment preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                // Delete badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .clickable { viewModel.setAttachmentImage(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove image",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Rounded Input Field + Icons Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color(0xFF1E1F20))
                            .border(1.dp, Color(0xFF333537), RoundedCornerShape(32.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image attachment picker button
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add image",
                                tint = Color(0xFFC4C7C5)
                            )
                        }

                        // Main Text Input
                        TextField(
                            value = inputText,
                            onValueChange = { viewModel.updateInputText(it) },
                            placeholder = { Text("Enter a prompt here", color = Color(0xFF8E918F)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 4,
                            modifier = Modifier.weight(1f)
                        )

                        // Send button
                        val isSendActive = inputText.trim().isNotEmpty() || selectedImageBase64 != null
                        IconButton(
                            onClick = {
                                if (isSendActive) {
                                    viewModel.sendMessage(inputText)
                                    keyboardController?.hide()
                                }
                            },
                            enabled = isSendActive,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSendActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send prompt",
                                tint = if (isSendActive) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4C7C5),
                                modifier = Modifier.size(18.dp)
                              )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chat selector tab
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF282A2C))
                                .padding(horizontal = 24.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CHAT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC4C7C5),
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Live selector tab
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF8E918F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E918F),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // --- MODAL DIALOGS ---

    // Delete Session Confirmation Dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation? This will permanently erase the chat log from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let { viewModel.deleteSession(it.id) }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Session Dialog
    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Rename Chat") },
            text = {
                Column {
                    Text("Enter a new custom title for this thread:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionToRename?.let { viewModel.renameSession(it.id, renameText) }
                        sessionToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun OnboardingView(
    onPromptSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing brand graphic
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GeminiPurple.copy(alpha = 0.3f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = GeminiPurple,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Shimmering Greeting
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Hello, Alex",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(GeminiBlue, GeminiPurple, GeminiMagenta)
                    ),
                    fontWeight = FontWeight.Light,
                    fontSize = 40.sp,
                    lineHeight = 48.sp
                )
            )
            
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "How can I help you today?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF8E918F),
                lineHeight = 30.sp
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Suggested activities Title
        Text(
            text = "Suggested activities",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E918F),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp, start = 4.dp)
        )

        // Suggestion Cards Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SuggestionCard(
                icon = Icons.Default.Lightbulb,
                iconColor = Color(0xFFA8C7FA),
                text = "Explain the concept of quantum entanglement simply",
                onClick = { onPromptSelected("Explain the concept of quantum entanglement simply") },
                modifier = Modifier.weight(1f)
            )
            SuggestionCard(
                icon = Icons.Default.Code,
                iconColor = Color(0xFFC2E7FF),
                text = "Write a Python script to automate my daily reports",
                onClick = { onPromptSelected("Write a Python script to automate my daily reports") },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Suggestion Cards Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SuggestionCard(
                icon = Icons.Default.Explore,
                iconColor = Color(0xFFD3E3FD),
                text = "Plan a 3-day itinerary for a cultural trip to Kyoto",
                onClick = { onPromptSelected("Plan a 3-day itinerary for a cultural trip to Kyoto") },
                modifier = Modifier.weight(1f)
            )
            SuggestionCard(
                icon = Icons.Default.Edit,
                iconColor = Color(0xFFF2B8B5),
                text = "Help me draft a polite email to decline a meeting",
                onClick = { onPromptSelected("Help me draft a polite email to decline a meeting") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SuggestionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F20)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333537)),
        modifier = modifier
            .height(140.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFFE3E3E3),
                fontWeight = FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    isTtsSpeaking: Boolean,
    onSpeakClicked: () -> Unit
) {
    val isUser = message.sender == "user"
    val isSystem = message.sender == "system"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isSystem -> Arrangement.Center
            else -> Arrangement.Start
        }
    ) {
        if (!isUser && !isSystem) {
            // Gemini Icon Badge
            Box(
                modifier = Modifier
                    .padding(end = 12.dp, top = 4.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GeminiBlue, GeminiPurple, GeminiMagenta)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(if (isSystem) 1f else 0.85f, fill = false)
        ) {
            // Attached Image Preview inside user bubble
            if (isUser && message.imageBase64 != null) {
                val bitmap = remember(message.imageBase64) {
                    try {
                        val decodedString = android.util.Base64.decode(message.imageBase64, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    } catch (e: Exception) {
                        null
                    }
                }

                bitmap?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 6.dp)
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Attached photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            }

            // Text content bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        )
                    )
                    .background(
                        when {
                            isUser -> MaterialTheme.colorScheme.surfaceVariant
                            isSystem -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            else -> Color.Transparent // Model responses don't have boundaries, just raw clean typography like actual Gemini!
                        }
                    )
                    .border(
                        width = if (isSystem) 1.dp else 0.dp,
                        color = if (isSystem) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(
                        horizontal = if (isUser || isSystem) 16.dp else 0.dp,
                        vertical = if (isUser || isSystem) 12.dp else 4.dp
                    )
            ) {
                MarkdownText(
                    text = message.text,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onSurfaceVariant
                        isSystem -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onBackground
                    }
                )
            }

            // Speaker Icon on Model Responses
            if (!isUser && !isSystem && message.text.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSpeakClicked,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isTtsSpeaking) "Stop reading" else "Read aloud",
                            tint = if (isTtsSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isTtsSpeaking) {
                        Text(
                            text = "Reading aloud...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Custom pulsing modifier helper for compiling loading experience
@Composable
private fun Modifier.animatePulsing(): Modifier {
    return this
}
