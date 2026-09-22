package com.sielo.music.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.UrbanistFontFamily
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.room.model.ActiveRoomState
import com.sielo.music.room.model.RoomChatMessage
import com.sielo.music.room.model.RoomParticipant
import com.sielo.music.room.model.RoomQueueItem
import com.sielo.music.room.model.SavedRoomSession
import com.sielo.music.room.qr.CameraQrScannerDialog
import com.sielo.music.room.qr.QrCodeGenerator
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.ListenTogetherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    modifier: Modifier = Modifier,
    viewModel: ListenTogetherViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val roomState by viewModel.roomState.collectAsState()
    val roomNotification by viewModel.roomNotification.collectAsState()

    LaunchedEffect(roomNotification) {
        roomNotification?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearRoomNotification()
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showQuickJoinDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showChooseNewHostDialog by remember { mutableStateOf(false) }

    // Scanned QR result state for prompting username
    var scannedRoomId by remember { mutableStateOf<String?>(null) }
    var scannedRoomKey by remember { mutableStateOf<String?>(null) }
    var showScannedJoinDialog by remember { mutableStateOf(false) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraScanner = true
        } else {
            Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        var detectedClipboardRoom by remember { mutableStateOf<Pair<String, String>?>(null) }
        var dismissedClipboardRoomId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(roomState) {
            if (roomState == null) {
                val clip = clipboardManager.getText()?.text
                if (!clip.isNullOrBlank()) {
                    val extracted = viewModel.extractRoomCredentials(clip)
                    if (extracted != null && extracted.first != dismissedClipboardRoomId) {
                        detectedClipboardRoom = extracted
                    }
                }
            }
        }

        if (roomState == null) {
            // LOBBY VIEW
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = detectedClipboardRoom != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PaletteOxfordBlue),
                        border = BorderStroke(1.dp, PaletteSand),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📋 Room Code in Clipboard",
                                    color = PaletteSand,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Join Room: ${detectedClipboardRoom?.first}?",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        dismissedClipboardRoomId = detectedClipboardRoom?.first
                                        detectedClipboardRoom = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val room = detectedClipboardRoom
                                        detectedClipboardRoom = null
                                        if (room != null) {
                                            viewModel.joinRoom(room.first, room.second, viewModel.getSavedUserName())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
                                ) {
                                    Text("Join", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                ListenTogetherLobby(
                    onCreateRoomClick = { showCreateDialog = true },
                    onScanQrClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            showCameraScanner = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onQuickJoinClick = { showQuickJoinDialog = true }
                )
            }
        } else {
            // ACTIVE ROOM VIEW
            ActiveRoomContent(
                state = roomState!!,
                viewModel = viewModel,
                onShowQrCode = { showQrDialog = true },
                onAddSongClick = { showSearchSheet = true },
                onLeaveRoom = {
                    viewModel.leaveRoom()
                },
                onNavigateBack = onNavigateBack
            )
        }

        // CHOOSE NEW HOST MODAL (When host is leaving voluntary)
        if (showChooseNewHostDialog && roomState != null) {
            val otherParticipants = roomState!!.participants.filter {
                it.id != roomState!!.localUserId && !it.name.equals(roomState!!.localUserName, ignoreCase = true)
            }.distinctBy { it.name.lowercase().trim() }
            ChooseNewHostDialog(
                participants = otherParticipants,
                onDismiss = { showChooseNewHostDialog = false },
                onTransferAndLeave = { selectedId ->
                    showChooseNewHostDialog = false
                    viewModel.transferHostAndLeave(selectedId)
                },
                onLeaveAnyway = {
                    showChooseNewHostDialog = false
                    viewModel.leaveRoom()
                }
            )
        }

        // CREATE ROOM MODAL
        if (showCreateDialog) {
            CreateRoomDialog(
                defaultName = viewModel.getSavedUserName(),
                onDismiss = { showCreateDialog = false },
                onCreate = { hostName ->
                    viewModel.createRoom(hostName)
                    showCreateDialog = false
                }
            )
        }

        // QUICK JOIN DIALOG (Asks ONLY Room ID and Username)
        if (showQuickJoinDialog) {
            QuickJoinRoomDialog(
                defaultName = viewModel.getSavedUserName(),
                onDismiss = { showQuickJoinDialog = false },
                onJoin = { roomId, userName ->
                    val success = viewModel.joinRoom(roomId, "", userName)
                    if (success) {
                        showQuickJoinDialog = false
                    } else {
                        Toast.makeText(context, "Please enter a valid Room ID", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // CAMERA QR SCANNER DIALOG
        if (showCameraScanner) {
            CameraQrScannerDialog(
                onDismiss = { showCameraScanner = false },
                onQrCodeScanned = { rawResult ->
                    showCameraScanner = false
                    val parsed = viewModel.parseRoomQr(rawResult)
                    if (parsed == null) {
                        Toast.makeText(context, "Wrong QR: This QR code is not of a Sielo room", Toast.LENGTH_LONG).show()
                    } else {
                        scannedRoomId = parsed.first
                        scannedRoomKey = parsed.second
                        showScannedJoinDialog = true
                    }
                }
            )
        }

        // SCANNED JOIN DIALOG (After camera QR scan, asks for username)
        if (showScannedJoinDialog && scannedRoomId != null) {
            ScannedJoinDialog(
                roomId = scannedRoomId!!,
                defaultName = viewModel.getSavedUserName(),
                onDismiss = {
                    showScannedJoinDialog = false
                    scannedRoomId = null
                    scannedRoomKey = null
                },
                onJoin = { userName ->
                    viewModel.joinRoom(scannedRoomId!!, scannedRoomKey ?: "", userName)
                    showScannedJoinDialog = false
                    scannedRoomId = null
                    scannedRoomKey = null
                }
            )
        }

        // DYNAMIC QR CODE DIALOG
        if (showQrDialog && roomState != null) {
            RoomQrDialog(
                state = roomState!!,
                onDismiss = { showQrDialog = false },
                onShare = { viewModel.shareRoomQrWithImage(context, roomState!!) }
            )
        }

        // IN-ROOM SONG SEARCH SHEET
        if (showSearchSheet) {
            RoomSearchBottomSheet(
                viewModel = viewModel,
                onDismiss = { showSearchSheet = false },
                onTrackSelected = { track ->
                    viewModel.addTrackToRoom(track)
                    showSearchSheet = false
                    Toast.makeText(context, "Added '${track.title}' to Room Queue", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ListenTogetherLobby(
    onCreateRoomClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onQuickJoinClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 210.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Listen Together",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Synchronized audio streaming with friends",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PaletteOxfordBlue)
                        .border(1.dp, BorderGlass, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "E2EE",
                        tint = PaletteSand,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Host Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(SurfaceElevated, SurfaceDark)
                        )
                    )
                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(PaletteSlateBlue.copy(alpha = 0.5f))
                                .border(1.dp, PaletteSand.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = PaletteCream,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Start a Live Session",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Private Listening Room",
                                color = PaletteSageGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = "Create a private room. Share via dynamic QR code or link to listen simultaneously with live chat and collaborative queue.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 20.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PaletteSand)
                            .clickable { onCreateRoomClick() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Private Room",
                            color = PaletteDarkNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Join a Session Section: "Scan QR with Camera" & "Quick Join"
        item {
            Text(
                text = "Join a Session",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Filled.QrCodeScanner,
                    title = "Scan QR Code",
                    subtitle = "Camera & Gallery",
                    onClick = onScanQrClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Filled.Login,
                    title = "Quick Join",
                    subtitle = "Join with Room ID",
                    onClick = onQuickJoinClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Feature Highlights
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark.copy(alpha = 0.6f))
                    .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ROOM FEATURES",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    FeatureItem(text = "Private Room (Zero Server Data Retention)")
                    FeatureItem(text = "Dynamic QR Code Generation for Instant Scanning")
                    FeatureItem(text = "Live Chat with Real-Time Typing Indicators")
                    FeatureItem(text = "Collaborative Song Search & Shared Queue with Skip")
                    FeatureItem(text = "Sub-150ms Synchronized Audio Drift Correction")
                }
            }
        }

        // Generous bottom padding so content never gets obscured by MiniPlayerIsland
        item {
            Spacer(modifier = Modifier.height(180.dp))
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(PaletteSand)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark.copy(alpha = 0.9f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PaletteSageGreen,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ActiveRoomContent(
    state: ActiveRoomState,
    viewModel: ListenTogetherViewModel,
    onShowQrCode: () -> Unit,
    onAddSongClick: () -> Unit,
    onLeaveRoom: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }

    // Intercept back gesture in active room to minimize/navigate back to app without leaving room
    BackHandler {
        onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // TOP ROOM BAR - Fixed clean layout without overlapping buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Top-Left Back Arrow: Navigates back/minimizes room view without leaving the room
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to App",
                        tint = PaletteCream,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))

                // Room ID chip (tap to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Sielo Room Link", viewModel.getInviteLink(state))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied Room Link to Clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.isConnected) PaletteSageGreen else PaletteSand)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.roomId,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Top Actions: QR Code, Share, Leave with precise spacing
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // QR Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { onShowQrCode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = "Room QR Code",
                        tint = PaletteCream,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Share Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { viewModel.shareRoomQrWithImage(context, state) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = PaletteSand,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Leave Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { showLeaveConfirmDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Leave Room",
                        tint = Color(0xFFE63946),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // SYNCED PLAYER CARD WITH PREVIOUS, PLAY/PAUSE, NEXT CONTROLS
        RoomPlayerCard(
            state = state,
            onPrevious = { viewModel.skipPrevious() },
            onTogglePlay = { viewModel.togglePlayPause() },
            onNext = { viewModel.skipTrack() }
        )

        // PARTICIPANTS DROPDOWN
        RoomParticipantsDropdown(state = state)

        // TAB ROW (Live Chat & Collaborative Queue)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PaletteDarkNavy,
            contentColor = PaletteCream,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PaletteSand,
                    height = 3.dp
                )
            },
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderGlass)
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Queue (${state.queue.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        // TAB CONTENT WITH SMOOTH HORIZONTAL SLIDE ANIMATION
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(animationSpec = tween(200)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(animationSpec = tween(200)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "RoomTabTransition",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { tabIndex ->
            if (tabIndex == 0) {
                LiveChatView(
                    state = state,
                    onSendMessage = { text, replyText, replySender ->
                        viewModel.sendChatMessage(text, replyText, replySender)
                    },
                    onInputChange = { viewModel.onChatInputChanged(it) }
                )
            } else {
                CollaborativeQueueView(
                    state = state,
                    onAddSongClick = onAddSongClick,
                    onSkipTrack = { viewModel.skipTrack() },
                    onRemoveTrack = { track -> viewModel.removeTrackFromRoom(track) },
                    onTrackClick = { track -> viewModel.addTrackToRoom(track) }
                )
            }
        }
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = {
                Text(
                    text = "Leave Room?",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to leave room '${state.roomId}'? You will be disconnected from the active listening session.",
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 13.5.sp
                )
            },
            containerColor = PaletteOxfordBlue,
            shape = RoundedCornerShape(18.dp),
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                ) {
                    Text("Leave", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Cancel", color = PaletteSand)
                }
            }
        )
    }
}

@Composable
private fun RoomPlayerCard(
    state: ActiveRoomState,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit
) {
    val track = state.currentTrack

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song artwork
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!track?.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track?.thumbnailUrl,
                        contentDescription = track?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = PaletteSand,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "No Song Playing",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Add songs from Queue",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Playback Controls: Previous, Play/Pause, Next
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Previous Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { onPrevious() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = PaletteCream,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PaletteSand)
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = PaletteDarkNavy,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Next Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Song",
                        tint = PaletteCream,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomParticipantsDropdown(state: ActiveRoomState) {
    var isExpanded by remember { mutableStateOf(false) }
    val cleanStatus = remember(state.connectionStatus) {
        state.connectionStatus
            .replace("(?i)\\(encrypted\\)".toRegex(), "")
            .replace("(?i)encrypted".toRegex(), "")
            .trim()
    }

    val displayParticipants = remember(state.participants, state.localUserId, state.localUserName) {
        val local = state.participants.find { it.id == state.localUserId }
            ?: RoomParticipant(state.localUserId, state.localUserName, isHost = state.isHost)
        val others = state.participants.filter {
            it.id != state.localUserId && !it.name.equals(state.localUserName, ignoreCase = true)
        }.distinctBy { it.name.lowercase().trim() }
        listOf(local) + others
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
    ) {
        // Dropdown Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = "Members",
                        tint = PaletteSand,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${displayParticipants.size} Member${if (displayParticipants.size != 1) "s" else ""}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = cleanStatus,
                        color = if (state.isConnected) PaletteSageGreen else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) "Hide" else "View all",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Expanded Members List
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderGlass)
                )
                Spacer(modifier = Modifier.height(6.dp))

                displayParticipants.forEach { p ->
                    val isLocal = p.id == state.localUserId
                    val songsCount = maxOf(
                        state.addedSongsCount[p.name] ?: 0,
                        state.queue.count { it.addedBy.equals(p.name, ignoreCase = true) }
                    )
                    val initial = p.name.trim().take(1).uppercase().ifBlank { "?" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (p.isHost) PaletteSand.copy(alpha = 0.2f) else SurfaceElevated)
                                    .border(1.dp, if (p.isHost) PaletteSand else BorderGlass, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    color = if (p.isHost) PaletteSand else PaletteCream,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = p.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (p.isHost) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PaletteSand.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Host",
                                            color = PaletteSand,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (isLocal) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(You)",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Songs added badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$songsCount song${if (songsCount != 1) "s" else ""} added",
                                color = PaletteCream.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChatView(
    state: ActiveRoomState,
    onSendMessage: (String, String?, String?) -> Unit,
    onInputChange: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<RoomChatMessage?>(null) }
    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.chatMessages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages yet.\nSay hello to everyone in the room!\n(Swipe left on any message to reply)",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(state.chatMessages, key = { index, msg -> "${msg.id}_$index" }) { index, message ->
                    val nextMessage = state.chatMessages.getOrNull(index + 1)
                    val isLastInGroup = nextMessage == null ||
                            nextMessage.isSystemEvent ||
                            nextMessage.senderId != message.senderId

                    ChatMessageBubble(
                        message = message,
                        isOwn = message.senderId == state.localUserId,
                        isLastInGroup = isLastInGroup,
                        onSwipeReply = { replyTarget ->
                            replyingToMessage = replyTarget
                        }
                    )
                }
            }
        }

        // DYNAMIC "(username) is typing..." INDICATOR
        AnimatedVisibility(
            visible = state.typingUsers.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            TypingIndicatorBanner(typingUsers = state.typingUsers)
        }

        // REPLY PREVIEW BANNER
        AnimatedVisibility(
            visible = replyingToMessage != null,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
        ) {
            replyingToMessage?.let { replyTarget ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated)
                        .border(1.dp, BorderGlass, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PaletteSand)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Replying",
                        tint = PaletteSand,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyTarget.senderName}",
                            color = PaletteSand,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = replyTarget.text,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { replyingToMessage = null },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel reply",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Chat Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = {
                    messageText = it
                    onInputChange(it)
                },
                placeholder = {
                    Text(
                        text = if (replyingToMessage != null) "Reply to ${replyingToMessage?.senderName}..." else "Type a message...",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText, replyingToMessage?.text, replyingToMessage?.senderName)
                        messageText = ""
                        replyingToMessage = null
                    }
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText, replyingToMessage?.text, replyingToMessage?.senderName)
                        messageText = ""
                        replyingToMessage = null
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (messageText.isNotBlank()) PaletteSand else SurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) PaletteDarkNavy else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: RoomChatMessage,
    isOwn: Boolean,
    isLastInGroup: Boolean,
    onSwipeReply: (RoomChatMessage) -> Unit
) {
    if (message.isSystemEvent) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark.copy(alpha = 0.85f))
                    .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = PaletteSand,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = message.text,
                    color = PaletteCream.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }

    val timeString = remember(message.timestamp) {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        sdf.format(java.util.Date(message.timestamp))
    }

    val initial = remember(message.senderName) {
        message.senderName.trim().take(1).uppercase().ifBlank { "?" }
    }

    val density = LocalDensity.current
    val triggerPx = remember(density) { with(density) { 50.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 80.dp.toPx() } }
    val iconThresholdPx = remember(density) { with(density) { 40.dp.toPx() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value <= -triggerPx) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeReply(message)
                            }
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetX.animateTo(0f)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount < 0 || offsetX.value < 0f) {
                            change.consume()
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDragPx, 0f)
                            coroutineScope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        }
                    }
                )
            }
    ) {
        // Animated Reply Icon indicator revealed behind bubble on drag
        if (offsetX.value < -8f) {
            val replyAlpha = (-offsetX.value / iconThresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .alpha(replyAlpha)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PaletteSlateBlue)
                    .border(1.dp, PaletteSand.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = PaletteSand,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                // Peer avatar on the left
                if (!isOwn) {
                    if (isLastInGroup) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PaletteSlateBlue)
                                .border(1.dp, BorderGlass, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = PaletteCream,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Message body & time column
                Column(
                    horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (!isOwn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = message.senderName,
                                color = if (message.isHost) PaletteSand else PaletteSageGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                            if (message.isHost) {
                                Text(text = " (Host)", color = PaletteSand, fontSize = 10.sp)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isOwn) 16.dp else 4.dp,
                                    bottomEnd = if (isOwn) 4.dp else 16.dp
                                )
                            )
                            .background(if (isOwn) PaletteSlateBlue else SurfaceElevated)
                            .border(
                                1.dp,
                                if (isOwn) PaletteSand.copy(alpha = 0.2f) else BorderGlass,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Column {
                            // If message is a reply to another message
                            if (!message.replyToText.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.25f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(26.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (isOwn) PaletteSand else PaletteSageGreen)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = message.replyToSender ?: "Reply",
                                                color = if (isOwn) PaletteSand else PaletteSageGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = message.replyToText,
                                                color = PaletteCream.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            Text(
                                text = message.text,
                                color = PaletteCream,
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    if (isLastInGroup) {
                        Text(
                            text = timeString,
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(
                                top = 2.dp,
                                start = if (!isOwn) 4.dp else 0.dp,
                                end = if (isOwn) 4.dp else 0.dp
                            )
                        )
                    }
                }

                // Own user avatar on the right side
                if (isOwn) {
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isLastInGroup) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PaletteSand)
                                .border(1.dp, PaletteDarkNavy.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = PaletteDarkNavy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBanner(typingUsers: Set<String>) {
    val typingText = when {
        typingUsers.size == 1 -> "${typingUsers.first()} is typing..."
        typingUsers.size == 2 -> "${typingUsers.toList()[0]} and ${typingUsers.toList()[1]} are typing..."
        else -> "${typingUsers.size} people are typing..."
    }

    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteDarkNavy.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PaletteSand.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = typingText,
            color = PaletteSand,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CollaborativeQueueView(
    state: ActiveRoomState,
    onAddSongClick: () -> Unit,
    onSkipTrack: () -> Unit,
    onRemoveTrack: (SieloTrack) -> Unit,
    onTrackClick: (SieloTrack) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Queue Header with "Add Song" Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "UP NEXT IN ROOM",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PaletteSand)
                    .clickable { onAddSongClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = PaletteDarkNavy, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Song", color = PaletteDarkNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Queue Items
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.queue.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Room queue is empty", color = TextMuted, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onAddSongClick,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated)
                            ) {
                                Text(text = "Search & Add Songs", color = PaletteSand)
                            }
                        }
                    }
                }
            } else {
                items(state.queue) { item ->
                    val isCurrent = item.track.id == state.currentTrack?.id
                    RoomQueueRow(
                        item = item,
                        isCurrent = isCurrent,
                        onSkip = onSkipTrack,
                        onRemove = { onRemoveTrack(item.track) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun RoomQueueRow(
    item: RoomQueueItem,
    isCurrent: Boolean,
    onSkip: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) SurfaceElevated else SurfaceDark)
            .border(
                1.dp,
                if (isCurrent) PaletteSand.copy(alpha = 0.4f) else BorderGlass,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PaletteDarkNavy),
                contentAlignment = Alignment.Center
            ) {
                if (!item.track.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.track.thumbnailUrl,
                        contentDescription = item.track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(imageVector = Icons.Filled.MusicNote, contentDescription = null, tint = PaletteSand)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.track.title,
                    color = if (isCurrent) PaletteSand else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.track.artist,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• Added by ${item.addedBy}",
                        color = PaletteSageGreen,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            if (isCurrent) {
                IconButton(onClick = onSkip) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skip Song",
                        tint = PaletteSand,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove from queue",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSearchBottomSheet(
    viewModel: ListenTogetherViewModel,
    onDismiss: () -> Unit,
    onTrackSelected: (SieloTrack) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(BorderGlass)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Search & Add Song to Room",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(text = "Search song, artist, album...", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSand,
                    unfocusedBorderColor = BorderGlass,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PaletteSand)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                                .clickable { onTrackSelected(track) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PaletteDarkNavy)
                            ) {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PaletteSand)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = "Add", color = PaletteDarkNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaletteOxfordBlue,
        title = {
            Text(text = "Create Room", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Enter your display name for the session. A private room will be generated.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaletteSand,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
            ) {
                Text("Create Room", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Quick Join Dialog: asks ONLY Room ID and Username.
 */
@Composable
private fun QuickJoinRoomDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onJoin: (String, String) -> Unit
) {
    var roomId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaletteOxfordBlue,
        title = {
            Text(text = "Quick Join Room", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the Room ID and choose your display name.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = roomId,
                    onValueChange = { roomId = it },
                    label = { Text("Room ID (e.g. K4M8X2)") },
                    placeholder = { Text("e.g. K4M8X2") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaletteSand,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaletteSand,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(roomId, userName) },
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
            ) {
                Text("Join Room", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Rejoin Dialog: Prompts the user to directly rejoin an active room session
 * when reopening the app after an abrupt exit or disconnect.
 */
@Composable
fun RejoinRoomDialog(
    session: SavedRoomSession,
    onRejoin: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaletteOxfordBlue,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PaletteSlateBlue.copy(alpha = 0.5f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = PaletteSand,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = "Rejoin Room?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "You were previously in a Listen Together session. Would you like to rejoin now?",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Room ID:", color = TextMuted, fontSize = 12.sp)
                            Text(session.roomId, color = PaletteSand, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Role:", color = TextMuted, fontSize = 12.sp)
                            Text(
                                if (session.isHost) "Host" else "Listener",
                                color = PaletteSageGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Display Name:", color = TextMuted, fontSize = 12.sp)
                            Text(session.userName, color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRejoin,
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
            ) {
                Text("Rejoin Room", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary)
            }
        }
    )
}

/**
 * Scanned Join Dialog: Shown after camera QR scan to input display username.
 */
@Composable
private fun ScannedJoinDialog(
    roomId: String,
    defaultName: String,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var userName by remember { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaletteOxfordBlue,
        title = {
            Text(text = "Join Scanned Room", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Detected Room ID: $roomId",
                    color = PaletteSand,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Enter your display name to join the synchronized session.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaletteSand,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(userName) },
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
            ) {
                Text("Join Session", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun RoomQrDialog(
    state: ActiveRoomState,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val inviteLink = "https://vineetchudasama.github.io/Sielo/room/?id=${state.roomId}&key=${state.roomKey}"

    val qrBitmap = remember(inviteLink) {
        QrCodeGenerator.generateQrImageBitmap(inviteLink, context = context, sizePx = 480)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan to Join",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // High-Res Dynamically Generated QR Code
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PaletteDarkNavy)
                        .border(2.dp, PaletteSand.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "Room QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Room ID: ${state.roomId}",
                    color = PaletteSand,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Point camera to scan or share the direct invite link below.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Sielo Room Link", inviteLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link Copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, tint = PaletteCream, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Copy Link", color = PaletteCream, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null, tint = PaletteDarkNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Share", color = PaletteDarkNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChooseNewHostDialog(
    participants: List<RoomParticipant>,
    onDismiss: () -> Unit,
    onTransferAndLeave: (String) -> Unit,
    onLeaveAnyway: () -> Unit
) {
    var selectedParticipantId by remember {
        mutableStateOf(participants.minByOrNull { it.joinedAt }?.id ?: participants.firstOrNull()?.id ?: "")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PaletteDarkNavy),
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PaletteSand.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = PaletteSand,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Choose New Host",
                            color = PaletteCream,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily
                        )
                        Text(
                            text = "Select who will host before you leave",
                            color = PaletteSageGreen,
                            fontSize = 12.sp,
                            fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    items(participants) { p ->
                        val isSelected = p.id == selectedParticipantId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) PaletteSand.copy(alpha = 0.15f) else SurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) PaletteSand else BorderGlass,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedParticipantId = p.id }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) PaletteSand else PaletteSlateBlue.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = p.name.take(1).uppercase(),
                                        color = if (isSelected) PaletteDarkNavy else PaletteCream,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = p.name,
                                        color = PaletteCream,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily
                                    )
                                    val isNextJoined = p.id == participants.minByOrNull { it.joinedAt }?.id
                                    if (isNextJoined) {
                                        Text(
                                            text = "Next in queue (earliest joined)",
                                            color = PaletteSand,
                                            fontSize = 10.sp,
                                            fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily
                                        )
                                    }
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedParticipantId = p.id },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PaletteSand,
                                    unselectedColor = PaletteSlateBlue
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Button(
                    onClick = {
                        if (selectedParticipantId.isNotBlank()) {
                            onTransferAndLeave(selectedParticipantId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaletteSand,
                        contentColor = PaletteDarkNavy
                    )
                ) {
                    Text(
                        text = "Transfer Host & Leave",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onLeaveAnyway) {
                        Text(
                            text = "Leave Without Transferring",
                            color = Color(0xFFE63946),
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = PaletteSageGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
