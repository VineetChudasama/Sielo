package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sielo.music.core.auth.UserManager
import com.sielo.music.ui.components.SieloArtistPhoto
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.MacondoFontFamily
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily

data class OnboardingArtist(
    val name: String,
    val origin: String,
    val imageUrl: String
)

data class OnboardingGenre(
    val name: String,
    val emoji: String,
    val description: String,
    val accentColor: Long
)

val TRENDING_ONBOARDING_ARTISTS = listOf(
    OnboardingArtist("The Weeknd", "Global Pop/R&B", "https://c.saavncdn.com/artists/The_Weeknd_002_20230227134015_500x500.jpg"),
    OnboardingArtist("Arijit Singh", "Indian Romantic/Bollywood", "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg"),
    OnboardingArtist("Taylor Swift", "Global Pop", "https://c.saavncdn.com/artists/Taylor_Swift_004_20230227134114_500x500.jpg"),
    OnboardingArtist("Diljit Dosanjh", "Indian Punjabi/Pop", "https://c.saavncdn.com/artists/Diljit_Dosanjh_004_20221006184540_500x500.jpg"),
    OnboardingArtist("Drake", "Global Hip-Hop", "https://c.saavncdn.com/artists/Drake_002_20221104113115_500x500.jpg"),
    OnboardingArtist("AP Dhillon", "Indian Punjabi Wave", "https://c.saavncdn.com/artists/AP_Dhillon_003_20230818063311_500x500.jpg"),
    OnboardingArtist("Billie Eilish", "Global Alternative", "https://c.saavncdn.com/artists/Billie_Eilish_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Shreya Ghoshal", "Indian Melodies", "https://c.saavncdn.com/artists/Shreya_Ghoshal_004_20230227134114_500x500.jpg"),
    OnboardingArtist("Bruno Mars", "Global Funk/Pop", "https://c.saavncdn.com/artists/Bruno_Mars_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Prateek Kuhad", "Indian Indie/Acoustic", "https://c.saavncdn.com/artists/Prateek_Kuhad_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Dua Lipa", "Global Disco-Pop", "https://c.saavncdn.com/artists/Dua_Lipa_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Anuv Jain", "Indian Indie Soul", "https://c.saavncdn.com/artists/Anuv_Jain_002_20230818063311_500x500.jpg"),
    OnboardingArtist("Ed Sheeran", "Global Acoustic Pop", "https://c.saavncdn.com/artists/Ed_Sheeran_002_20221006184540_500x500.jpg"),
    OnboardingArtist("A.R. Rahman", "Indian Maestro", "https://c.saavncdn.com/artists/A_R_Rahman_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Ariana Grande", "Global Pop/R&B", "https://c.saavncdn.com/artists/Ariana_Grande_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Sidhu Moose Wala", "Indian Punjabi Legend", "https://c.saavncdn.com/artists/Sidhu_Moose_Wala_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Post Malone", "Global Hip-Hop/Rock", "https://c.saavncdn.com/artists/Post_Malone_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Nucleya", "Indian Bass/EDM", "https://c.saavncdn.com/artists/Nucleya_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Kendrick Lamar", "Global Hip-Hop", "https://c.saavncdn.com/artists/Kendrick_Lamar_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Divine", "Indian Hip-Hop/Gully", "https://c.saavncdn.com/artists/DIVINE_003_20221202111153_500x500.jpg"),
    OnboardingArtist("Justin Bieber", "Global Pop", "https://c.saavncdn.com/artists/Justin_Bieber_002_20221006184540_500x500.jpg"),
    OnboardingArtist("King", "Indian Hip-Hop/Pop", "https://c.saavncdn.com/artists/King_006_20221021074717_500x500.jpg"),
    OnboardingArtist("Travis Scott", "Global Trap", "https://c.saavncdn.com/artists/Travis_Scott_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Karan Aujla", "Indian Punjabi Folk/Pop", "https://c.saavncdn.com/artists/Karan_Aujla_004_20230818063311_500x500.jpg"),
    OnboardingArtist("Olivia Rodrigo", "Global Pop-Rock", "https://c.saavncdn.com/artists/Olivia_Rodrigo_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Coldplay", "Global Alternative Rock", "https://c.saavncdn.com/artists/Coldplay_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Harry Styles", "Global Pop/Rock", "https://c.saavncdn.com/artists/Harry_Styles_002_20221006184540_500x500.jpg"),
    OnboardingArtist("SZA", "Global Contemporary R&B", "https://c.saavncdn.com/artists/SZA_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Lana Del Rey", "Global Cinematic Indie", "https://c.saavncdn.com/artists/Lana_Del_Rey_002_20221006184540_500x500.jpg"),
    OnboardingArtist("Imagine Dragons", "Global Pop Rock", "https://c.saavncdn.com/artists/Imagine_Dragons_002_20221006184540_500x500.jpg")
)

val TRENDING_ONBOARDING_GENRES = listOf(
    OnboardingGenre("Pop", "✨", "Chart-topping hooks & anthems", 0xFFE06C75),
    OnboardingGenre("Hip-Hop & Rap", "🎤", "Punchy beats, trap & rhymes", 0xFFD19A66),
    OnboardingGenre("Bollywood & Desi", "🎬", "Soulful Indian romantic melodies", 0xFFE5C07B),
    OnboardingGenre("Indie & Alternative", "🎸", "Raw acoustic & independent feels", 0xFF98C379),
    OnboardingGenre("R&B & Soul", "🌙", "Smooth late-night grooves", 0xFFC678DD),
    OnboardingGenre("Electronic & EDM", "⚡", "Bass drops, festival & progressive house", 0xFF61AFEF),
    OnboardingGenre("Rock & Metal", "⚡", "Electric riffs & stadium anthems", 0xFFE06C75),
    OnboardingGenre("Lo-Fi & Chill", "☕", "Relaxing beats to study & focus", 0xFF56B6C2),
    OnboardingGenre("Acoustic & Folk", "🌿", "Intimate guitars & soothing vocals", 0xFF98C379),
    OnboardingGenre("Punjabi Hits", "🔥", "High-energy bhangra & modern flow", 0xFFD19A66)
)

/**
 * Authentication Dialog (Google OAuth + Normal Email/Password)
 */
@Composable
fun AuthDialog(
    userManager: UserManager,
    onDismiss: () -> Unit
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGoogleAccountPickerOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val webClientId = "61989343599-737bujb5d1dvp3t3tr93ibfroc1v3ht0.apps.googleusercontent.com"

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
    }

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val idToken = account.idToken
                val name = account.displayName ?: "Google User"
                val email = account.email ?: "user@gmail.com"
                val photoUrl = account.photoUrl?.toString()

                if (!idToken.isNullOrBlank()) {
                    try {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        FirebaseAuth.getInstance().signInWithCredential(credential)
                            .addOnCompleteListener { fbTask ->
                                val fbUser = FirebaseAuth.getInstance().currentUser
                                val finalName = fbUser?.displayName ?: name
                                val finalEmail = fbUser?.email ?: email
                                val finalPhoto = fbUser?.photoUrl?.toString() ?: photoUrl
                                userManager.signInWithGoogle(finalName, finalEmail, finalPhoto)
                                onDismiss()
                            }
                    } catch (_: Exception) {
                        userManager.signInWithGoogle(name, email, photoUrl)
                        onDismiss()
                    }
                } else {
                    userManager.signInWithGoogle(name, email, photoUrl)
                    onDismiss()
                }
            }
        } catch (e: Exception) {
            // If user explicitly cancelled, do nothing; otherwise open fallback account chooser
            if (e is ApiException && (e.statusCode == 12501 || e.statusCode == 12502)) {
                // User cancelled
            } else {
                isGoogleAccountPickerOpen = true
            }
        }
    }

    BackHandler(enabled = userManager.currentUser.value == null) {
        (context as? android.app.Activity)?.finish()
    }

    Dialog(
        onDismissRequest = {
            if (userManager.currentUser.value != null) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = userManager.currentUser.value != null,
            dismissOnClickOutside = userManager.currentUser.value != null,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PaletteDarkNavy.copy(alpha = 0.85f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PaletteOxfordBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with Brand & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sielo",
                            fontFamily = MacondoFontFamily,
                            fontSize = 32.sp,
                            color = PaletteCream
                        )
                        if (userManager.currentUser.value != null) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isSignUpMode) "Create your audiophile profile" else "Welcome back to your sound",
                        color = PaletteSand,
                        fontFamily = SoraFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Google OAuth Button
                    Button(
                        onClick = {
                            try {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleLauncher.launch(googleSignInClient.signInIntent)
                                }
                            } catch (e: Exception) {
                                isGoogleAccountPickerOpen = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F2937),
                            contentColor = PaletteCream
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Google 'G' Icon Badge
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color(0xFF4285F4),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in with Google",
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(BorderSubtle)
                        )
                        Text(
                            text = "  OR  ",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(BorderSubtle)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Normal Login Form
                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it; errorMessage = null },
                            label = { Text("Full Name", color = TextSecondary) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PaletteSand) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PaletteCream,
                                unfocusedTextColor = PaletteCream,
                                focusedBorderColor = PaletteSand,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it; errorMessage = null },
                        label = { Text("Email Address", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PaletteSand) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PaletteCream,
                            unfocusedTextColor = PaletteCream,
                            focusedBorderColor = PaletteSand,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; errorMessage = null },
                        label = { Text("Password", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PaletteSand) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PaletteCream,
                            unfocusedTextColor = PaletteCream,
                            focusedBorderColor = PaletteSand,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF5350),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                val result = userManager.registerWithEmail(nameInput, emailInput, passwordInput)
                                if (result.isFailure) {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Registration failed."
                                } else {
                                    onDismiss()
                                }
                            } else {
                                val result = userManager.signInWithEmail(emailInput, passwordInput)
                                if (result.isFailure) {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Login failed."
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSand,
                            contentColor = PaletteDarkNavy
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (isSignUpMode) "CREATE ACCOUNT" else "SIGN IN",
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            isSignUpMode = !isSignUpMode
                            errorMessage = null
                        }
                    ) {
                        Text(
                            text = if (isSignUpMode) "Already have an account? Sign In" else "New to Sielo? Create an Account",
                            color = PaletteSageGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(BorderSubtle)
                        )
                        Text(
                            text = "  OR  ",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(BorderSubtle)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            userManager.signInAsGuest()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1F2937),
                            contentColor = PaletteCream
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "Continue as Guest",
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Google OAuth Account Chooser Sheet
    if (isGoogleAccountPickerOpen) {
        GoogleAccountChooserDialog(
            onAccountSelected = { name, email, photo ->
                isGoogleAccountPickerOpen = false
                userManager.signInWithGoogle(name, email, photo)
                onDismiss()
            },
            onDismiss = { isGoogleAccountPickerOpen = false }
        )
    }
}

/**
 * Google Account Selector Modal (Google OAuth UI)
 */
@Composable
fun GoogleAccountChooserDialog(
    onAccountSelected: (name: String, email: String, photo: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val suggestedAccounts = listOf(
        Triple("Vineet Kumar", "vineet.music@gmail.com", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop&q=80"),
        Triple("Audiophile User", "audiophile.listener@gmail.com", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&auto=format&fit=crop&q=80")
    )

    var customName by remember { mutableStateOf("") }
    var customEmail by remember { mutableStateOf("") }
    var isAddingCustom by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E2430),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Choose a Google Account",
                            color = PaletteCream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "to continue to Sielo",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                suggestedAccounts.forEach { (name, email, photo) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAccountSelected(name, email, photo) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PaletteDarkNavy)
                                .border(1.dp, BorderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(1),
                                color = PaletteSand,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = name,
                                color = PaletteCream,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = email,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (isAddingCustom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Name", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PaletteCream, unfocusedTextColor = PaletteCream),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it },
                        label = { Text("Google Email", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PaletteCream, unfocusedTextColor = PaletteCream),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (customEmail.isNotBlank()) {
                                onAccountSelected(if (customName.isNotBlank()) customName else "Google User", customEmail, null)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSand, contentColor = PaletteDarkNavy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue with Account", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { isAddingCustom = true }) {
                        Text("+ Use another Google account", color = PaletteSageGreen, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Onboarding Flow for New Users:
 * Step 1: Select at least 5 favorite artists from 30+ trending artists worldwide (including Indian artists)
 * Step 2: Select favorite music genres from 10 trending genres
 */
@Composable
fun NewUserOnboardingScreen(
    userManager: UserManager,
    onFinished: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Artists, 2: Genres
    val selectedArtists = remember { mutableStateListOf<String>() }
    val selectedGenres = remember { mutableStateListOf<String>() }

    // System Back Gesture handling
    BackHandler {
        if (step == 2) {
            step = 1
        } else {
            userManager.openAuthDialog()
            userManager.closeOnboarding()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (step == 2) {
                                step = 1
                            } else {
                                userManager.openAuthDialog()
                                userManager.closeOnboarding()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PaletteCream,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sielo",
                        fontFamily = MacondoFontFamily,
                        fontSize = 28.sp,
                        color = PaletteCream
                    )
                }

                // Step Indicator Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (step == 1) PaletteSand else PaletteSlateBlue.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (step == 2) PaletteSand else PaletteSlateBlue.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (step == 1) {
                // STEP 1: Favorite Artists
                Text(
                    text = "Pick your favorite artists",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Choose at least 5 artists to tune recommendations",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val count = selectedArtists.size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (count >= 5) PaletteSageGreen.copy(alpha = 0.2f) else PaletteSand.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$count/5 selected",
                            color = if (count >= 5) PaletteSageGreen else PaletteSand,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(TRENDING_ONBOARDING_ARTISTS) { artist ->
                        val isSelected = selectedArtists.contains(artist.name)
                        OnboardingArtistTile(
                            artist = artist,
                            isSelected = isSelected,
                            onToggle = {
                                if (isSelected) {
                                    selectedArtists.remove(artist.name)
                                } else {
                                    selectedArtists.add(artist.name)
                                }
                            }
                        )
                    }
                }
            } else {
                // STEP 2: Favorite Genres
                Text(
                    text = "Pick your music vibes",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select genres you love from the top 10 trending genres",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(TRENDING_ONBOARDING_GENRES) { genre ->
                        val isSelected = selectedGenres.contains(genre.name)
                        OnboardingGenreTile(
                            genre = genre,
                            isSelected = isSelected,
                            onToggle = {
                                if (isSelected) {
                                    selectedGenres.remove(genre.name)
                                } else {
                                    selectedGenres.add(genre.name)
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Navigation Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                if (step == 1) {
                    val canContinue = selectedArtists.size >= 5
                    Button(
                        onClick = { step = 2 },
                        enabled = canContinue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSand,
                            contentColor = PaletteDarkNavy,
                            disabledContainerColor = PaletteOxfordBlue,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (canContinue) "CONTINUE TO GENRES" else "SELECT AT LEAST ${5 - selectedArtists.size} MORE",
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (canContinue) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { step = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PaletteOxfordBlue,
                                contentColor = PaletteCream
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                        ) {
                            Text("BACK", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val finalGenres = if (selectedGenres.isEmpty()) listOf("Pop", "Bollywood & Desi", "Hip-Hop & Rap") else selectedGenres
                                userManager.completeOnboarding(selectedArtists, finalGenres)
                                onFinished()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PaletteSand,
                                contentColor = PaletteDarkNavy
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(52.dp)
                        ) {
                            Text("START LISTENING", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingArtistTile(
    artist: OnboardingArtist,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.04f else 1.0f, label = "tileScale")

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PaletteOxfordBlue else PaletteOxfordBlue.copy(alpha = 0.5f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PaletteSand else BorderGlass,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
        ) {
            SieloArtistPhoto(
                imageUrl = artist.imageUrl,
                name = artist.name,
                modifier = Modifier.fillMaxSize()
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PaletteSand.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PaletteSand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            color = if (isSelected) PaletteSand else PaletteCream,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = artist.origin,
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingGenreTile(
    genre: OnboardingGenre,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(genre.accentColor).copy(alpha = if (isSelected) 0.55f else 0.25f),
                        PaletteOxfordBlue
                    )
                )
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PaletteSand else Color(genre.accentColor).copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = genre.emoji, fontSize = 22.sp)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(PaletteSand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = genre.name,
                    color = if (isSelected) PaletteSand else PaletteCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = genre.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
