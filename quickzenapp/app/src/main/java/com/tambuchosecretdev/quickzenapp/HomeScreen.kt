
package com.tambuchosecretdev.quickzenapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.tambuchosecretdev.quickzenapp.firebase.SyncState
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import com.tambuchosecretdev.quickzenapp.ui.components.NoteItem
import com.tambuchosecretdev.quickzenapp.ui.components.PinEntryDialog
import com.tambuchosecretdev.quickzenapp.ui.theme.BrightCyan
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tambuchosecretdev.quickzenapp.firebase.SyncState as FirebaseSyncState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotesViewModel<Any?>,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit,
    onTrashClick: () -> Unit,
    onNavigateToPinSecurity: () -> Unit,
    onThemeChanged: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
    onPinSecurityClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onBackupSyncClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onMigracionFirestoreClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val notes by viewModel.activeNotes.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState(initial = false)
    val error by viewModel.error.collectAsState(initial = null)
    val operationMessage by viewModel.operationMessage.collectAsState(initial = null)
    
    // Estado de sincronización
    val syncState by viewModel.syncState.collectAsState(initial = SyncState.IDLE)
    val lastSyncTime by viewModel.lastSyncTime.collectAsState(initial = 0L)

    // --- Obtener estados de filtro y búsqueda del ViewModel ---
    val currentSearchQuery by viewModel.searchQuery.collectAsState(initial = "")
    val currentSelectedCategoryId by viewModel.selectedCategoryId.collectAsState(initial = null)

    var showShareOptions by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    var categoryFilterExpanded by remember { mutableStateOf(false) }

    // Estados para el diálogo de PIN al abrir nota bloqueada
    var showPinEntryDialogForOpenNote by remember { mutableStateOf(false) }
    var pinInputForOpenNote by remember { mutableStateOf("") }
    var pinErrorForOpenNote by remember { mutableStateOf<String?>(null) }
    var noteToOpenAfterPin by remember { mutableStateOf<Note?>(null) }

    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                importNotesFromFile(context, it, viewModel)
            }
        }
    )

    if (operationMessage != null) {
        LaunchedEffect(operationMessage) {
            Toast.makeText(context, operationMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearOperationMessage()
        }
    }

    if (error != null) {
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                // Reducir el ancho del menú para que quepa mejor en pantallas pequeñas
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // Añadimos un Column con scroll para permitir desplazamiento vertical
                Column(modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "QuickZen", 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), 
                    style = MaterialTheme.typography.titleLarge
                )
                Divider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, null) }, 
                    label = { Text("Inicio", style = MaterialTheme.typography.bodyMedium) }, 
                    selected = true, 
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, null) }, 
                    label = { Text("Papelera", style = MaterialTheme.typography.bodyMedium) }, 
                    selected = false, 
                    onClick = { scope.launch { drawerState.close() }; onTrashClick() },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // Se eliminaron los botones de sincronización manual ya que ahora es automática
                // Divider para separar secciones
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                
                // Opciones de apariencia y configuración
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Palette, null) }, 
                    label = { Text("Cambiar tema", style = MaterialTheme.typography.bodyMedium) }, 
                    selected = false, 
                    onClick = { 
                        scope.launch { drawerState.close() }
                        val newDarkTheme = changeTheme(context)
                        onThemeChanged(newDarkTheme) 
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Share, null) }, 
                    label = { Text("Compartir app", style = MaterialTheme.typography.bodyMedium) }, 
                    selected = false, 
                    onClick = { 
                        scope.launch { drawerState.close() }
                        showShareOptions = true 
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, null) }, 
                    label = { Text("Configuración", style = MaterialTheme.typography.bodyMedium) }, 
                    selected = false, 
                    onClick = { 
                        scope.launch { drawerState.close() }
                        showSettingsDialog = true 
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // Se eliminó el botón de Sincronización y Respaldo ya que ahora es automática
                
                // Botón de sincronización
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
                    label = { Text("Sincronizar notas") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        // Mostrar mensaje de inicio de sincronización
                        Toast.makeText(context, "Sincronizando notas...", Toast.LENGTH_SHORT).show()
                        Log.d("HomeScreen", "Iniciando sincronización manual desde el botón...")
                        
                        // Usar el nuevo método startSync que maneja toda la lógica de sincronización
                        viewModel.startSync()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Acerca de") },
                    label = { Text("Acerca de", style = MaterialTheme.typography.bodyMedium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onAboutClick()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Password, contentDescription = "Seguridad PIN") },
                    label = { Text("Seguridad PIN", style = MaterialTheme.typography.bodyMedium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onPinSecurityClick()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Star, contentDescription = "Notas Favoritas") },
                    label = { Text("Favoritas", style = MaterialTheme.typography.bodyMedium) },
                    selected = false, 
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onFavoritesClick()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // Se eliminó el botón de Copias y sincronización ya que ahora es automática
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PrivacyTip, contentDescription = "Política de Privacidad") },
                    label = { Text("Privacidad", style = MaterialTheme.typography.bodyMedium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onPrivacyPolicyClick()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión") },
                    label = { Text("Cerrar sesión", style = MaterialTheme.typography.bodyMedium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        Toast.makeText(context, "Cerrando sesión de sincronización en la nube. Las notas permanecerán en el dispositivo.", Toast.LENGTH_LONG).show()
                        onSignOutClick()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                // Espacio adicional al final para asegurar que todo sea visible al hacer scroll
                Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
        content = {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            OutlinedTextField(
                                value = currentSearchQuery, 
                                onValueChange = { newQuery -> viewModel.setSearchQuery(newQuery) }, 
                                placeholder = { Text("Buscar notas...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, "Buscar") },
                                trailingIcon = {
                                    if (currentSearchQuery.isNotEmpty()) { 
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) { 
                                            Icon(Icons.Default.Close, "Limpiar búsqueda")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Search
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Abrir menú lateral")
                            }
                        },
                        actions = {
                            // Indicador de sincronización
                            when (syncState) {
                                FirebaseSyncState.SYNCING_UP, FirebaseSyncState.SYNCING_DOWN, FirebaseSyncState.CONNECTING, FirebaseSyncState.AUTHENTICATING -> 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Sincronizando...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                // Cancelar sincronización en progreso
                                                Toast.makeText(context, "Cancelando sincronización...", Toast.LENGTH_SHORT).show()
                                                // Este valor se revisará en el ViewModel en la próxima comprobación de timeout
                                                viewModel.cancelSync()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Cancelar sincronización",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                FirebaseSyncState.SUCCESS -> IconButton(
                                    onClick = { 
                                        val syncTime = lastSyncTime
                                        val message = if (syncTime > 0) {
                                            val dateObj = Date(syncTime)
                                            "Última sincronización: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(dateObj)}"
                                        } else {
                                            "Sincronizado"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CloudDone, 
                                        contentDescription = "Sincronizado"
                                    )
                                }
                                FirebaseSyncState.ERROR_SYNC, FirebaseSyncState.ERROR_CONNECTION, FirebaseSyncState.ERROR_AUTHENTICATION -> IconButton(
                                    onClick = {
                                        Toast.makeText(context, "Error en la última sincronización. Iniciando sincronización manual...", Toast.LENGTH_LONG).show()
                                        viewModel.initiateSync()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = "Error de sincronización",
                                        tint = Color.Red
                                    )
                                }
                                else -> {
                                    // Convertimos el icono verde en un botón que inicia la sincronización
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Iniciando sincronización manual...", Toast.LENGTH_SHORT).show()
                                            viewModel.initiateSync()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = "Iniciar sincronización manual",
                                            tint = Color(0xFF4CAF50) // Verde para indicar que está activa
                                        )
                                    }
                                }
                            }
                            
                            IconButton(onClick = { categoryFilterExpanded = true }) {
                                Icon(Icons.Default.FilterList, "Filtrar por categoría")
                            }
                            CategoryFilterDropdown(
                                expanded = categoryFilterExpanded,
                                onDismissRequest = { categoryFilterExpanded = false },
                                categories = categories,
                                selectedCategoryId = currentSelectedCategoryId, 
                                onCategorySelected = { categoryId ->
                                    viewModel.setSelectedCategoryId(categoryId) 
                                    categoryFilterExpanded = false
                                }
                            )
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Más opciones")
                            }
                            HomeScreenDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                onImportClick = {
                                    importFileLauncher.launch("*/*")
                                    showMenu = false
                                },
                                onExportClick = {
                                    exportAllNotes(context, notes)
                                    showMenu = false
                                },
                                onSortByDateClick = { 
                                    viewModel.sortNotesByDate() 
                                    showMenu = false
                                }
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = BrightCyan,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onAddNoteClick,
                        containerColor = BrightCyan,
                        contentColor = Color.Black
                    ) {
                        Icon(Icons.Filled.Add, "Añadir nueva nota")
                    }
                }
            ) { paddingValues ->
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (notes.isNullOrEmpty() && currentSearchQuery.isNotEmpty()) { 
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        Text("No se encontraron notas para \"$currentSearchQuery\"")
                    }
                } else if (notes.isNullOrEmpty() && currentSelectedCategoryId != null) { 
                     Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        val categoryName = categories.find { category -> category.id == currentSelectedCategoryId }?.name ?: "desconocida"
                        Text("No hay notas en la categoría \"$categoryName\"")
                    }
                } else if (notes.isNullOrEmpty()) { 
                     Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        Text("No hay notas. ¡Toca el botón '+' para añadir una!")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        items(notes, key = { note -> note.id }) { note ->
                            NoteItem(
                                note = note,
                                context = LocalContext.current,
                                onNoteClick = { noteId -> onNoteClick(noteId) }, // Pass HomeScreen's onNoteClick for navigation
                                onToggleFavorite = { viewModel.toggleFavoriteStatus(note.id) },
                                onShowPinDialog = { clickedNote ->
                                    noteToOpenAfterPin = clickedNote
                                    showPinEntryDialogForOpenNote = true
                                },
                                onMoveToTrash = { noteToTrash -> viewModel.moveNoteToTrash(noteToTrash.id) }
                            )
                        }
                    }
                }
            }
        }
    )

    if (showShareOptions) {
        ShareOptionsDialog(
            onDismiss = { showShareOptions = false },
            onShareText = {
                shareApp(context, "text")
                showShareOptions = false
            },
            onShareImage = { 
                shareApp(context, "image")
                showShareOptions = false
            }
        )
    }
    
    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onExportNotes = {
                 exportAllNotes(context, notes)
                 showSettingsDialog = false
            },
            onImportNotes = {
                importFileLauncher.launch("*/*")
                showSettingsDialog = false
            }
        )
    }

    if (showPinEntryDialogForOpenNote) {
        Log.d("HomeScreenDebug", "Showing PinEntryDialog for open note. Title: Ingresar PIN")
        PinEntryDialog(
            title = "Ingresar PIN",
            pinInput = pinInputForOpenNote,
            onPinInputChange = { pinInputForOpenNote = it },
            errorText = pinErrorForOpenNote,
            onConfirm = {
                // Funcionalidad de PIN desactivada temporalmente, permitiendo abrir directamente
                // val pinManager = PinManager(context.applicationContext)
                if (true) { // Siempre permitir abrir la nota bloqueada
                    showPinEntryDialogForOpenNote = false
                    noteToOpenAfterPin?.let { onNoteClick(it.id) }
                    noteToOpenAfterPin = null 
                } else {
                    pinErrorForOpenNote = "PIN incorrecto."
                }
            },
            onDismiss = {
                showPinEntryDialogForOpenNote = false
                noteToOpenAfterPin = null 
            }
        )
    }
}

@Composable
fun CategoryFilterDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Todas las categorías") },
            onClick = { onCategorySelected(null) }
        )
        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

@Composable
fun HomeScreenDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onSortByDateClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Importar notas") },
            onClick = onImportClick
        )
        DropdownMenuItem(
            text = { Text("Exportar notas") },
            onClick = onExportClick
        )
        DropdownMenuItem(
            text = { Text("Ordenar por fecha") },
            onClick = onSortByDateClick
        )
    }
}

/* Esta función ha sido reemplazada por com.tambuchosecretdev.quickzenapp.ui.components.NoteItem */
@Composable
private fun OldNoteItem(
    note: Note,
    context: Context,
    onNoteClick: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onShowPinDialog: (Note) -> Unit,
    onMoveToTrash: (Note) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable { 
                if (note.isLocked) {
                    onShowPinDialog(note)
                } else {
                    onNoteClick(note.id)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = NoteAppColors.getColorById(note.colorId)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (note.isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Nota bloqueada",
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifEmpty { "Nota sin título" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Modificado: ${dateFormat.format(Date(note.modifiedDate))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, 
                    contentDescription = if (note.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (note.isFavorite) Color.Yellow else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Mover a papelera",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { 
                com.tambuchosecretdev.quickzenapp.ui.components.shareNoteIntent(context, note)
            }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Compartir nota",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar nota") },
            text = { Text("¿Qué deseas hacer con esta nota?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMoveToTrash(note)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Mover a papelera")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}


@Composable
fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir aplicación") },
        text = { Text("Elige cómo quieres compartir la aplicación:") },
        confirmButton = {
            Column { 
                TextButton(onClick = onShareText) {
                    Text("Compartir como Texto")
                }
                Spacer(modifier = Modifier.height(8.dp)) 
                TextButton(onClick = onShareImage) {
                    Text("Compartir como Imagen")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun shareApp(context: Context, type: String) {
    val appName = context.getString(R.string.app_name)
    val packageName = context.packageName
    val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"
    val shareMessage = "¡Echa un vistazo a $appName! Descárgala aquí: $playStoreUrl"

    val intent = Intent(Intent.ACTION_SEND)

    if (type == "text") {
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, shareMessage)
    } else if (type == "image") {
        Toast.makeText(context, "Compartir como imagen aún no implementado. Compartiendo como texto.", Toast.LENGTH_LONG).show()
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, shareMessage)

    }

    try {
        context.startActivity(Intent.createChooser(intent, "Compartir $appName usando"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo iniciar la acción de compartir.", Toast.LENGTH_SHORT).show()
    }
}


@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onExportNotes: () -> Unit,
    onImportNotes: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración") },
        text = {
            Column {
                Text("Aquí puedes gestionar la importación y exportación de tus notas.")
            }
        },
        confirmButton = {
            Column { 
                TextButton(onClick = {
                    onImportNotes()
                }) {
                    Text("Importar Notas (.txt/.json)")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    onExportNotes()
                }) {
                    Text("Exportar Notas (.txt)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

// Esta función ha sido reemplazada por la versión en com.tambuchosecretdev.quickzenapp.ui.components.NoteItem
private fun oldShareNoteIntent(context: Context, note: Note) {
    val shareText = "${note.title}\n\n${note.content}"
    val intent = Intent(Intent.ACTION_SEND)

    if (note.images.isNotEmpty()) {
        val imagePath = note.images[0] // Tomamos la primera imagen
        val imageFile = File(context.filesDir, imagePath) // Asumimos que imagePath es relativo a filesDir

        if (imageFile.exists()) {
            try {
                val imageUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider", // Debe coincidir con android:authorities en Manifest
                    imageFile
                )

                intent.type = "image/*" // Tipo MIME para imagen
                intent.putExtra(Intent.EXTRA_STREAM, imageUri)
                intent.putExtra(Intent.EXTRA_SUBJECT, note.title) // Asunto (para emails, etc.)
                intent.putExtra(Intent.EXTRA_TEXT, shareText) // Cuerpo del texto
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Permiso necesario
                Log.d("ShareFeature", "Intent para compartir con imagen: $imageUri, texto: $shareText")
            } catch (e: IllegalArgumentException) {
                Log.e("ShareFeature", "Error al obtener URI para compartir: ${e.message}")
                // Fallback a compartir solo texto si hay error con la URI
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, shareText)
            }
        } else {
            Log.w("ShareFeature", "Archivo de imagen no encontrado en: ${imageFile.absolutePath}")
            // Fallback a compartir solo texto si el archivo no existe
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, shareText)
        }
    } else {
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, shareText)
        Log.d("ShareFeature", "Intent para compartir solo texto: $shareText")
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Compartir nota vía..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo iniciar la acción de compartir.", Toast.LENGTH_SHORT).show()
        Log.e("ShareFeature", "Error al iniciar actividad de compartir: ${e.message}")
    }
}
