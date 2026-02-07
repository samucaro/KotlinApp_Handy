package com.unibo.handy.ui.theme

import android.Manifest
import com.unibo.handy.R
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.text.SimpleDateFormat
import java.util.Locale
import com.unibo.handy.data.db.entity.MatchEntity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack

// Indirizzi schermate per Jetpack Navigation
sealed class Screen(val route: String) {
    object SignUp : Screen("signup_screen")
    object Home : Screen("home_screen")
    object ChatDetail : Screen("chat_detail/{matchId}") {
        fun createRoute(matchId: String) = "chat_detail/$matchId"
    }
}

// --- ENTRY POINT ---
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyAppEntry() {
    val viewModel: HomeVM = viewModel(factory = HomeVM.Factory)

    val state by viewModel.uiState.collectAsState()

    //var isRegistered by rememberSaveable { mutableStateOf(false) }
    val navController = rememberNavController()

    LaunchedEffect(state.userId) {
        // Se l'ID non è vuoto (quindi loadUser ha trovato qualcuno nel DB)
        if (state.userId.isNotBlank()) {
            //isRegistered = true
            navController.navigate(Screen.Home.route) {
                // "popUpTo" serve a cancellare la cronologia dato che deve tornare al Signup
                popUpTo(Screen.SignUp.route) { inclusive = true }
            }
        }
    }

    // Contenitore che cambia schermata
    NavHost(
        navController = navController,
        startDestination = Screen.SignUp.route
    ) {
        // ROTTA 1: Schermata di Signup
        composable(Screen.SignUp.route) {
            SignUpScreen(
                viewModel = viewModel,
                // Quando la registrazione manuale finisce (click bottone), passa a Home
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }
        // ROTTA 2: Schermata Main
        composable(Screen.Home.route) {
            MainScreen(
                viewModel = viewModel,
                onOpenChat = { matchId ->
                    // Quando clicco su una chat, navigo al dettaglio
                    navController.navigate(Screen.ChatDetail.createRoute(matchId))
                }
            )
        }
        composable(Screen.ChatDetail.route) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable

            // Questa è la schermata della chat vera e propria (che aggiungeremo in fondo)
            SingleChatScreen(
                viewModel = viewModel,
                matchId = matchId,
                onBack = { navController.popBackStack() }
            )
        }
    }

    /*if (isRegistered) {
        MainScreen(viewModel)
    } else {
        SignUpScreen(viewModel = viewModel)
    }*/
}

// ---------------------------------- SIGNUP SCREEN (LOGICA + UI) ----------------------------------

// 1. Wrapper Stateful (Gestisce il ViewModel)
@Composable
fun SignUpScreen(viewModel: HomeVM, onSignUpSuccess: () -> Unit) {
    SignUpContent(
        onSignUpClick = { username, email, password ->
            viewModel.updateUserProfile(username, email, password, "Generico")
            onSignUpSuccess()
        }
    )
}
// 2. Content Stateless (Solo UI)
@Composable
fun SignUpContent(
    onSignUpClick: (String, String, String) -> Unit
) {
    // Stati per i campi di testo (gestiti localmente nella UI)
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF006C75))
    ) {
        // Parte superiore: Logo e Immagine (30% dello schermo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.handy_icon),
                contentDescription = "Logo Handy App",
                modifier = Modifier
                    .size(180.dp)
                    .padding(top = 16.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        // Parte inferiore: Card bianca con i campi (70% dello schermo)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Ti diamo il benvenuto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Inizia con il  tuo account", color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                // CAMPI DI INPUT
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // BOTTONE DI REGISTRAZIONE
                Button(
                    onClick = {
                        onSignUpClick(username, email, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Registrati", fontSize = 18.sp)
                }
            }
        }
    }
}

// ------------------------------------------ MAIN SCREEN ------------------------------------------
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainScreen(
    viewModel: HomeVM,
    onOpenChat: (String) -> Unit
) {
    var selectedTab by remember() { mutableIntStateOf(0) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        if (granted) {
            // Permesso concesso!
        }
    }

    // Chiede i permessi alla prima apertura dell'app
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavBarItem(0, "Home", Icons.Default.Home, selectedTab) { selectedTab = 0 }
                NavBarItem(1, "Attività", Icons.AutoMirrored.Filled.List, selectedTab) { selectedTab = 1 }
                NavBarItem(2, "Chat", Icons.Default.Sms, selectedTab) { selectedTab = 2 }
                NavBarItem(3, "Profilo", Icons.Default.Person, selectedTab) { selectedTab = 3 }
            }
        }
    ) { innerPadding ->
        // Contenuto che cambia in base al Tab selezionato
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(Color(0xFFF5F7F8))) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> ActivityScreen(
                    viewModel = viewModel,
                    onChatClick = { matchId ->
                        // Aggiornare poi navController.navigate("chat/$matchId")
                        Log.d("HandyUI", "Apro chat con: $matchId")
                    }
                )
                2 -> ChatScreen(
                    viewModel = viewModel,
                    onChatClick = onOpenChat
                )
                3 -> ProfileScreen(viewModel)
            }
        }
    }
}


// ---------------------------------------- HOME SCREEN TAB ----------------------------------------

// 1. Wrapper Stateful
@Composable
fun HomeScreen(viewModel: HomeVM) {
    val state by viewModel.uiState.collectAsState()

    HomeContent(
        state = state,
        onToggleHelperMode = { viewModel.toggleHelperMode(it) },
        onDismissMatchPopup = { viewModel.dismissMatchPopup() },
        onSearchParamUpdate = { cat, radius -> viewModel.updateSearchParameters(cat, radius) },
        onSendHelpRequest = { viewModel.sendHelpRequest() }
    )
}
// 2. Content Stateless (Solo UI)
@Composable
fun HomeContent(
    state: HomeUiState,
    onToggleHelperMode: (Boolean) -> Unit,
    onDismissMatchPopup: () -> Unit,
    onSearchParamUpdate: (String, Float) -> Unit,
    onSendHelpRequest: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = HandyPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Posizione Attuale", fontSize = 12.sp, color = Color.Gray)
                Text("Bologna, Italia (Simulato)", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Indicatore ID Utente (Troncato)
            Surface(color = HandyPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = "ID: ${state.userId.take(8)}...",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = HandyPrimary
                )
            }
        }

        // Switch Principale (Modalità)
        ModeSwitchCard(state.isHelperMode, onToggleHelperMode)

        Spacer(modifier = Modifier.height(24.dp))


        if (state.isHelperMode) {
            HelperView()
        } else {
            RequesterView(
                state = state,
                onCategorySelect = { cat -> onSearchParamUpdate(cat, state.toleranceRadius) },
                onRadiusChange = { rad -> onSearchParamUpdate(state.selectedCategory, rad) },
                onSearchClick = onSendHelpRequest
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(state.statusMessage, fontSize = 12.sp, color = Color.Gray)

        // POPUP MATCH TROVATO
        if (state.showMatchSuccess) {
            AlertDialog(
                onDismissRequest = onDismissMatchPopup,
                title = {
                    Text(text = "MATCH TROVATO! 🎉", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("Un Helper è disponibile vicino a te!")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.statusMessage)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismissMatchPopup,
                        colors = ButtonDefaults.buttonColors(containerColor = HandySecondary)
                    ) {
                        Text("Contatta Ora")
                    }
                },
                icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = HandyPrimary) }
            )
        }
    }
}

// ---------------------------------------- PROFILE SCREEN ----------------------.------------------

// 1. Wrapper Stateful
@Composable
fun ProfileScreen(viewModel: HomeVM) {
    val state by viewModel.uiState.collectAsState()
    ProfileContent(username = state.username)
}
// 2. Content Stateless
@Composable
fun ProfileContent(username: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HandyPrimary)
    ) {
        // Parte superiore: Immagine e Nome
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LetterAvatar(username)
                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    username,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Parte inferiore: Card bianca con i campi
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Profilo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------- ACTIVITY SCREEN ----------------------------------------
// 1. Wrapper Stateful (Collega i dati)
@Composable
fun ActivityScreen(viewModel: HomeVM, onChatClick: (String) -> Unit) {
    // Osserviamo la lista dei match dal ViewModel
    val state by viewModel.uiState.collectAsState()

    ActivityContent(
        matchesList = state.matchesList,
        onChatClick = onChatClick
    )
}
// 2. Content Stateless (Disegna la UI)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityContent(
    matchesList: List<MatchEntity>,
    onChatClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // TopBar integrata nella pagina
        TopAppBar(
            title = { Text("Le tue Attività", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (matchesList.isEmpty()) {
            EmptyStateMessage()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Match Recenti",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(matchesList) { match ->
                    ActivityMatchItem(match, onChatClick)
                }
            }
        }
    }
}

// ---------------------------------------- ACTIVITY SCREEN ----------------------------------------
// 1. Wrapper Stateful (Collega i dati)
@Composable
fun ChatScreen(
    viewModel: HomeVM,
    onChatClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ChatContent(matchesList = state.matchesList, onChatClick = onChatClick)
}
// 2. Content Stateless (Disegna la UI)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(matchesList: List<MatchEntity>, onChatClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Messaggi", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (matchesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessuna conversazione attiva", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchesList) { match ->
                    ChatItem(match, onChatClick)
                }
            }
        }
    }
}

@Composable
fun ChatItem(match: MatchEntity, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(match.requesterId) } // Apre la chat singola
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Rotondo
        Surface(
            shape = CircleShape,
            color = Color(0xFF006C75), // HandyPrimary
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = match.username.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Nome e Ultimo Messaggio (Simulato per ora)
        Column(modifier = Modifier.weight(1f)) {
            Text(match.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Tocca per chattare...", // Qui in futuro metteremo l'ultimo messaggio vero
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1
            )
        }

        // Orario (Simulato dalla data del match)
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(match.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
    Divider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)
}

// ---------------------------------------- SINGLE CHAT SCREEN ----------------------------------------
// Questa è la schermata che si apre quando clicchi su un elemento della lista sopra

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChatScreen(
    viewModel: HomeVM,
    matchId: String,
    onBack: () -> Unit
) {
    // Recupera i messaggi specifici per questo matchId
    val messages by viewModel.getChatMessages(matchId).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Scroll automatico in basso quando arriva un messaggio
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat", fontWeight = FontWeight.Bold)
                        // Mostriamo l'ID o il Nome (in futuro passeremo il nome completo)
                        Text("Utente ${matchId.take(4)}...", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            // Barra di Input (In basso)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color.LightGray, CircleShape)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Scrivi un messaggio...") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 3
                )
                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(matchId, inputText)
                        inputText = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia", tint = Color(0xFF006C75))
                }
            }
        }
    ) { padding ->
        // Area Messaggi
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFECE5DD)) // Sfondo beige stile WhatsApp
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isMe = msg.senderId != matchId
                    MessageBubble(msg, isMe)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: com.unibo.handy.data.db.entity.ChatMessagesEntity, isMe: Boolean) {
    val bubbleColor = if (isMe) Color(0xFFDCF8C6) else Color.White // Verde per me, Bianco per l'altro
    val alignment = if (isMe) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(msg.message, fontSize = 16.sp)
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(msg.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// --- Componente: La singola riga (Card) del Match ---
@Composable
fun ActivityMatchItem(match: MatchEntity, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(match.requesterId) },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar con Iniziale
            Surface(
                shape = CircleShape,
                color = HandyPrimary,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = match.username.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Dati Testuali
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Categoria: ${match.category}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )

                // Formattazione Data
                val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(match.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // Icona freccia
            Icon(
                imageVector = Icons.Default.ChevronRight, // O ArrowForward
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

// --- Componente: Messaggio Vuoto ---
@Composable
fun EmptyStateMessage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsOff, // O un'altra icona appropriata
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.LightGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nessuna attività recente",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
        Text(
            text = "Quando troverai un match, apparirà qui.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
    }
}

// ------------------------------------ COMPONENTI UI SPECIFICI ------------------------------------

// Fleg Gestione Modalità
@Composable
fun ModeSwitchCard(isHelper: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(if (isHelper) "Modalità Helper" else "Modalità Richiedente", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (isHelper) "Sei visibile per lavori" else "Cerca professionisti", fontSize = 14.sp, color = Color.Gray)
            }
            Switch(checked = isHelper, onCheckedChange = onToggle)
        }
    }
}


@Composable
fun HelperView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("In ascolto...", fontSize = 20.sp, color = HandyPrimary, fontWeight = FontWeight.SemiBold)
        Text("Il sistema sta proteggendo la tua posizione.", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))

        // Animazione Radar
        RadarAnimation()
    }
}
@Composable
fun RequesterView(
    state: HomeUiState,
    onCategorySelect: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onSearchClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Di cosa hai bisogno?", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        // Categorie (Pulsanti semplici per ora, da introdurre meccannismo migliore)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("Idraulico", state.selectedCategory == "Idraulico") { onCategorySelect("Idraulico") }
            CategoryChip("Elettricista", state.selectedCategory == "Elettricista") { onCategorySelect("Elettricista") }
            CategoryChip("Medico", state.selectedCategory == "Medico") { onCategorySelect("Medico") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Raggio di ricerca: ${state.toleranceRadius.toInt()} km", fontWeight = FontWeight.Bold)
        Slider(
            value = state.toleranceRadius,
            onValueChange = { onRadiusChange(it) },
            valueRange = 1f..50f,
            steps = 4
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSearchClick,
            colors = ButtonDefaults.buttonColors(containerColor = HandySecondary),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trova Aiuto Vicino a Me", fontSize = 18.sp)
        }
    }
}

// --------------------------------------- ACCESSORI GRAFICI ---------------------------------------
@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(HandyPrimary.copy(alpha = 0.3f))
        )
        Icon(
            imageVector = Icons.Default.Settings, // Icona ingranaggio o simbolo helper
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(60.dp)
                .background(HandyPrimary, CircleShape)
                .padding(12.dp)
        )
    }
}

@Composable
fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null) } } else null
    )
}

@Composable
fun RowScope.NavBarItem(index: Int, label: String, icon: ImageVector, selectedIndex: Int, onClick: () -> Unit) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = index == selectedIndex,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(indicatorColor = HandyPrimary.copy(alpha = 0.2f))
    )
}

@Composable
fun PlaceholderScreen(title: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}

@Composable
fun LetterAvatar(
    name: String
) {
    val initials = if (name.isNotBlank()) {
        name.trim().take(2).uppercase()
    } else {
        ""
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(75.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, HandyPrimaryLight, CircleShape)
    ) {
        Text(
            text = initials,
            color = HandyPrimaryLight,
            fontSize = (75 / 3.5).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- SEZIONE PREVIEWS ---

@Preview(showBackground = true, name = "1. Anteprima Registrazione")
@Composable
fun SignUpPreview() {
    // Usiamo il Content Stateless, quindi non crasha!
    SignUpContent(onSignUpClick = { _, _, _ -> })
}

@Preview(showBackground = true, name = "2. Anteprima Home Richiedente")
@Composable
fun HomeRequesterPreview() {
    // Creiamo uno stato finto per la preview
    val fakeState = HomeUiState(
        userId = "User123",
        isHelperMode = false,
        selectedCategory = "Idraulico",
        toleranceRadius = 15f
    )

    HomeContent(
        state = fakeState,
        onToggleHelperMode = {},
        onDismissMatchPopup = {},
        onSearchParamUpdate = { _, _ -> },
        onSendHelpRequest = {}
    )
}

@Preview(showBackground = true, name = "3. Anteprima Home Helper")
@Composable
fun HomeHelperPreview() {
    val fakeState = HomeUiState(
        userId = "HelperBob",
        isHelperMode = true,
        statusMessage = "In attesa di richieste..."
    )

    HomeContent(
        state = fakeState,
        onToggleHelperMode = {},
        onDismissMatchPopup = {},
        onSearchParamUpdate = { _, _ -> },
        onSendHelpRequest = {}
    )
}

@Preview(showBackground = true, name = "4. Anteprima Profilo")
@Composable
fun ProfilePreview() {
    ProfileContent(username = "User123")
}