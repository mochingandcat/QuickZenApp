package com.tambuchosecretdev.quickzenapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.tambuchosecretdev.quickzenapp.ui.components.PinEntryDialog
import com.tambuchosecretdev.quickzenapp.ui.screens.TrashScreen
import com.tambuchosecretdev.quickzenapp.ui.theme.QuickZenAppTheme
import com.tambuchosecretdev.quickzenapp.utils.PinManager
import com.tambuchosecretdev.quickzenapp.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Se inicializará en onCreate
    private lateinit var viewModel: NotesViewModel<Any?>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var themeManager: ThemeManager
    private lateinit var pinManager: PinManager

    companion object {
        private const val TAG = "MainActivity"
        private const val APP_VERSION = "1.0.3"
        private const val CREATOR = "TambuchoSecretDev"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar Firebase Auth y gestores
        firebaseAuth = FirebaseAuth.getInstance()
        themeManager = ThemeManager(this)
        // Deshabilitando temporalmente el uso del PIN para evitar errores
pinManager = PinManager(applicationContext)
// Guardando una referencia al contexto de la aplicación para un acceso más seguro
val appContext = applicationContext
        
        try {
            // Verificar autenticación
            if (firebaseAuth.currentUser == null) {
                Log.w(TAG, "No hay usuario autenticado, redirigiendo a LoginGoogleActivity")
                startActivity(Intent(this, LoginGoogleActivity::class.java))
                finish()
                return
            }
            
            // Mostrar mensaje de bienvenida con el nombre del usuario
            val userName = firebaseAuth.currentUser?.displayName ?: "Usuario"
            Toast.makeText(this, "Bienvenido, $userName", Toast.LENGTH_SHORT).show()
            
            // Mostrar un toast con información sobre cómo acceder a la información de la cuenta
            Toast.makeText(this, "Presiona el menú y selecciona 'Información de Cuenta' para ver detalles de tu sesión", Toast.LENGTH_LONG).show()
            
            // Agregar un acceso directo temporal a la información de la cuenta
            lifecycleScope.launch {
                delay(2000) // Esperar 2 segundos para que sea visible después del mensaje de bienvenida
                val accountInfoIntent = Intent(this@MainActivity, AccountInfoActivity::class.java)
                startActivity(accountInfoIntent)
            }
            
            // Inicializar las dependencias para el NotesViewModel
            // Obtener instancias de dependencias desde la clase Application (QuickZenApp)
            val appInstance = application as QuickZenApp
            val noteRepositoryFromApp = appInstance.noteRepository
            
            // Obtener la última cuenta de Google que inició sesión
            lifecycleScope.launch {
                try {
                    val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    
                    val googleSignInClient = GoogleSignIn.getClient(this@MainActivity, googleSignInOptions)
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                    
                    if (googleAccount != null) {
                        Log.d(TAG, "Cuenta de Google encontrada, iniciando sincronización automática")
                        // Iniciaremos la sincronización automática una vez que el ViewModel esté inicializado
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al obtener la última cuenta de Google: ${e.message}")
                }
            }
            val firebaseManagerFromApp = appInstance.firebaseManager
            
            // Crear el factory para el ViewModel
            val factory = NotesViewModelFactory(noteRepositoryFromApp, application, firebaseManagerFromApp)
            
            // Inicializar el ViewModel usando el factory
            viewModel =ViewModelProvider(this, factory)[NotesViewModel::class.java] as NotesViewModel<Any?>
            
            // Iniciar sincronización automática con la cuenta guardada
            lifecycleScope.launch {
                try {
                    val googleAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                    if (googleAccount != null) {
                        Log.d(TAG, "Iniciando sincronización automática con la cuenta ${googleAccount.email}")
                        viewModel.updateGoogleAccount(googleAccount)
                        viewModel.silentSignInAndSync()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en sincronización automática: ${e.message}")
                }
            }
            
            // Comprobar si debemos mostrar la información de la cuenta
            val showAccountInfo = intent.getBooleanExtra("SHOW_ACCOUNT_INFO", false)
            
            // Verificar si venimos de crear/editar una nota y necesitamos sincronizar
            val syncNeeded = intent.getBooleanExtra("SYNC_NEEDED", false)
            if (syncNeeded) {
                Log.d(TAG, "Se detectó bandera SYNC_NEEDED, forzando sincronización...")
                lifecycleScope.launch {
                    try {
                        // Esperar un momento para que la UI principal se cargue primero
                        delay(500)
                        // Forzar sincronización para actualizar la UI con los nuevos datos
                        viewModel.initiateSync()
                        // Mostrar mensaje de éxito
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Sincronización completada", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al sincronizar al volver de crear/editar nota", e)
                    }
                }                
            }
            
            // Usar el HomeScreen original con todas sus funcionalidades y el ViewModel real
            setContent {
                val isDarkTheme = remember { mutableStateOf(themeManager.isDarkTheme()) }
                
                QuickZenAppTheme(darkTheme = isDarkTheme.value) {
                    var currentScreen by remember { mutableStateOf("home") }

                    // State for PIN Entry Dialog
                    var showPinEntryDialog by remember { mutableStateOf(false) }
                    var pinEntryInput by remember { mutableStateOf("") }
                    var pinEntryError by remember { mutableStateOf<String?>(null) }
                    var noteIdToOpenWithPin by remember { mutableStateOf<Long?>(null) }
                    
                    // Estado para el diálogo de información de la cuenta
                    var showAccountInfoDialog by remember { mutableStateOf(showAccountInfo) }
                    
                    // Mostrar el diálogo de información de la cuenta si es necesario
                    if (showAccountInfoDialog) {
                        AccountInfoDialog(onDismissRequest = { showAccountInfoDialog = false })
                    }
                    
                    when (currentScreen) {
                        "home" -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onNoteClick = { noteId ->
                                    try {
                                        lifecycleScope.launch {
                                            try {
                                                val note = viewModel.getNoteById(noteId)
                                                if (note != null && note.isLocked) {
                                                    if (pinManager.isPinSet()) {
                                                        noteIdToOpenWithPin = note.id
                                                        pinEntryInput = ""
                                                        pinEntryError = null
                                                        showPinEntryDialog = true
                                                    } else {
                                                        Log.w(TAG, "Note ${note.id} is locked, but no PIN is set. Prompting user to set PIN.")
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@MainActivity, "Para abrir esta nota, primero configura un PIN de aplicación.", Toast.LENGTH_LONG).show()
                                                            // Opcional: Mostrar un AlertDialog para navegar a PinSecurityScreen
                                                            // currentScreen = "pinSecurity" // Esto navegaría a la pantalla de PIN
                                                        }
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        val intent = Intent(this@MainActivity, CreateNoteActivity::class.java).apply {
                                                            putExtra("NOTE_ID", noteId)
                                                        }
                                                        startActivity(intent)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Log.e(TAG, "Error al abrir nota: ${e.message}")
                                                    Toast.makeText(this@MainActivity, "Error al abrir la nota", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error general al procesar la nota: ${e.message}")
                                        Toast.makeText(this, "Error al abrir la nota. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                                        // Ya no se intenta abrir CreateNoteActivity si hay un error general aquí.
                                        // El error específico ya se maneja dentro del lifecycleScope.launch
                                    }
                                },
                                onAddNoteClick = {
                                    try {
                                        startActivity(Intent(this, CreateNoteActivity::class.java))
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error al crear nueva nota: ${e.message}")
                                        Toast.makeText(this, "Error al crear nueva nota: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTrashClick = {
                                    currentScreen = "trash"
                                },
                                onThemeChanged = { newDarkTheme ->
                                    isDarkTheme.value = newDarkTheme
                                    themeManager.setDarkTheme(newDarkTheme)
                                    Toast.makeText(
                                        this, 
                                        "Tema cambiado a ${if (newDarkTheme) "oscuro" else "claro"}", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onAboutClick = {
                                    // Instead of AlertDialog, navigate to AboutScreen
                                    currentScreen = "about"
                                },
                                onPinSecurityClick = { // Renamed from onNavigateToPinSecurity
                                    currentScreen = "pinSecurity"
                                },
                                onFavoritesClick = {
                                    currentScreen = "favorites"
                                },
                                onSignOutClick = {
                                    firebaseAuth.signOut()
                                    Toast.makeText(this, "Cerrando sesión...", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, LoginGoogleActivity::class.java))
                                    finish()
                                },
                                onBackupSyncClick = {
                                    // Cambiar a la pantalla de backup/sincronización
                                    currentScreen = "backupSync"
                                },
                                onPrivacyPolicyClick = {
                                    val intent = Intent(this@MainActivity, PrivacyPolicyActivity::class.java)
                                    startActivity(intent)
                                },
                                onMigracionFirestoreClick = {
                                    // Navegar a la pantalla de migración de Firestore
                                    val intent = Intent(this@MainActivity, MigracionFirestoreActivity::class.java)
                                    startActivity(intent)
                                },
                                onNavigateToPinSecurity = { currentScreen = "pinSecurity" } // Parámetro añadido a HomeScreen
                             )
                        }
                        "trash" -> {
                            TrashScreen(
                                viewModel = viewModel, 
                                onBackClick = { currentScreen = "home" },
                                onRestoreNote = { noteId -> 
                                    lifecycleScope.launch {
                                        viewModel.restoreNoteFromTrash(noteId)
                                    }
                                    Toast.makeText(this, "Nota restaurada", Toast.LENGTH_SHORT).show()
                                },
                                onDeletePermanently = { noteId ->
                                    // Usar la versión no suspendida del método
                                    viewModel.deleteNotePermanently(noteId)
                                    Toast.makeText(this, "Nota eliminada permanentemente", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        "pinSecurity" -> {
                            PinSecurityScreen(onNavigateBack = { currentScreen = "home" })
                        }
                        "about" -> {
                            AboutScreen(onNavigateBack = { currentScreen = "home" })
                        }
                        "favorites" -> {
                            FavoritesScreen(
                                viewModel = viewModel,
                                onNavigateBack = { currentScreen = "home" },
                                onNoteClick = { noteId ->
                                    // Verificar si la nota está bloqueada
                                    lifecycleScope.launch {
                                        val note = withContext(Dispatchers.IO) {
                                            viewModel.getNoteById(noteId)
                                        }
                                        if (note != null && note.isLocked) {
                                            // Si está bloqueada, mostrar diálogo de PIN
                                            showPinEntryDialog = true
                                            noteIdToOpenWithPin = noteId
                                        } else {
                                            // Si no está bloqueada, abrir directamente
                                            val intent = Intent(this@MainActivity, CreateNoteActivity::class.java).apply {
                                                putExtra("NOTE_ID", noteId)
                                            }
                                            startActivity(intent)
                                        }
                                    }
                                }
                            )
                        }
                        "backupSync" -> {
                            BackupSyncScreen(
                                viewModel= viewModel,
                                onNavigateBack= { currentScreen = "home" }
                            )
                        }
                    }

                    // PIN Entry Dialog
                    if (showPinEntryDialog) {
                        PinEntryDialog(
                            title = "Nota Protegida",
                            message = "Introduce tu PIN para abrir esta nota.",
                            pinInput = pinEntryInput,
                            onPinInputChange = { pinEntryInput = it },
                            errorText = pinEntryError,
                            onConfirm = {
                                if (pinManager.checkPin(pinEntryInput)) {
                                    showPinEntryDialog = false
                                    noteIdToOpenWithPin?.let { noteId ->
                                        val intent = Intent(this@MainActivity, CreateNoteActivity::class.java).apply {
                                            putExtra("NOTE_ID", noteId)
                                        }
                                        startActivity(intent)
                                    }
                                    noteIdToOpenWithPin = null // Reset
                                    pinEntryInput = "" // Clear PIN input
                                    pinEntryError = null // Clear error
                                } else {
                                    pinEntryError = "PIN incorrecto. Inténtalo de nuevo."
                                }
                            },
                            onDismiss = {
                                showPinEntryDialog = false
                                pinEntryInput = ""
                                pinEntryError = null
                                noteIdToOpenWithPin = null
                            }
                        )
                    }
                    }
                }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el ViewModel", e)
            Toast.makeText(this, "Error al inicializar la aplicación: ${e.message}", Toast.LENGTH_LONG).show()
            // En caso de error, mostrar un mensaje de error y volver a la pantalla de login
            Toast.makeText(this, "Error al inicializar el ViewModel. Volviendo a la pantalla de login.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error al inicializar el ViewModel. Volviendo a la pantalla de login.")
            
            // Redirigir al usuario a la pantalla de login
            startActivity(Intent(this, LoginGoogleActivity::class.java))
            finish()
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // Manejar el intent para mostrar la información de la cuenta
        if (intent.getBooleanExtra("SHOW_ACCOUNT_INFO", false)) {
            setContent {
                QuickZenAppTheme(darkTheme = themeManager.isDarkTheme()) {
                    AccountInfoDialog(onDismissRequest = { 
                        // Volver a mostrar la pantalla principal
                        recreate()
                    })
                }
            }
        }
    }
    
    @Composable
    private fun AccountInfoDialog(onDismissRequest: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Información de la Cuenta") },
            text = {
                Column {
                    if (user != null) {
                        Text(
                            "Email:", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(user.email ?: "No disponible")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Nombre:", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(user.displayName ?: "No disponible")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Proveedor:", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val providerData = user.providerData
                        if (providerData.isNotEmpty()) {
                            // Convertir el provider ID a un formato más legible
                            val providerId = providerData[0].providerId
                            val providerName = when {
                                providerId.contains("google") -> "Google"
                                providerId.contains("facebook") -> "Facebook"
                                providerId.contains("twitter") -> "Twitter"
                                providerId.contains("github") -> "GitHub"
                                providerId.contains("apple") -> "Apple"
                                providerId.contains("microsoft") -> "Microsoft"
                                providerId.contains("yahoo") -> "Yahoo"
                                providerId.contains("phone") -> "Teléfono"
                                providerId.contains("password") -> "Email/Contraseña"
                                else -> providerId
                            }
                            Text(providerName)
                        } else {
                            Text("No disponible")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "ID de Usuario:", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(user.uid)
                    } else {
                        Text("No has iniciado sesión")
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismissRequest) {
                    Text("Cerrar")
                }
            }
        )
    }
}
