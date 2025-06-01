package com.tambuchosecretdev.quickzenapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.tambuchosecretdev.quickzenapp.ui.theme.BrightCyan
import android.util.Log
import com.tambuchosecretdev.quickzenapp.model.Note
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Estado para almacenar las notas en la papelera obtenidas directamente
    val (notesInTrash, setNotesInTrash) = remember { mutableStateOf<List<Note>>(emptyList()) }
    val (isLoading, setIsLoading) = remember { mutableStateOf(true) }
    
    // Estados para los diálogos de confirmación
    var showRestoreConfirmDialog by remember { mutableStateOf<Note?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Note?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    
    // Función para cargar notas directamente
    val loadNotes = {
        setIsLoading(true)
        coroutineScope.launch {
            try {
                // Obtener notas directamente del DAO
                val notes = viewModel.getNotesInTrashDirectly()
                Log.d("TrashScreen", "Obtenidas ${notes.size} notas en papelera directamente")
                notes.forEach { note ->
                    Log.d("TrashScreen", "Nota: ID=${note.id}, Título=${note.title}, isInTrash=${note.isInTrash}")
                }
                setNotesInTrash(notes)
                if (notes.isEmpty()) {
                    Toast.makeText(context, "No hay notas en la papelera", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TrashScreen", "Error al cargar notas: ${e.message}", e)
                Toast.makeText(context, "Error al cargar notas: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setIsLoading(false)
            }
        }
    }
    
    // Cargar notas al iniciar la pantalla
    LaunchedEffect(key1 = true) {
        loadNotes()
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Papelera") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { loadNotes() }) {
                        Icon(Icons.Filled.Refresh, "Refrescar")
                    }
                    if (notesInTrash.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashDialog = true }) {
                            Icon(Icons.Filled.DeleteForever, "Vaciar papelera")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BrightCyan,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // Mostrar indicador de carga
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (notesInTrash.isEmpty()) {
                // Mostrar mensaje de papelera vacía
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "La papelera está vacía",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Las notas que elimines aparecerán aquí",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { loadNotes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refrescar")
                    }
                }
            } else {
                // Mostrar lista de notas en papelera
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Mostrar banner informativo
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Las notas permanecen en la papelera por 30 días antes de borrarse definitivamente",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    Text(
                        text = "Notas en papelera: ${notesInTrash.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notesInTrash) { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = note.title.ifEmpty { "Nota sin título" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Mostrar fecha de eliminación
                                    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                    Text(
                                        text = "Eliminada: ${dateFormat.format(Date(note.modifiedDate))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = { showRestoreConfirmDialog = note },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            )
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restaurar")
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Button(
                                            onClick = { showDeleteConfirmDialog = note },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Eliminar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo de confirmación para restaurar nota
    showRestoreConfirmDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = null },
            title = { Text("Restaurar nota") },
            text = { 
                Text("¿Quieres restaurar esta nota a tu lista de notas activas?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                viewModel.restoreNote(note.id)
                                Toast.makeText(context, "Nota restaurada correctamente", Toast.LENGTH_SHORT).show()
                                loadNotes()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al restaurar: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showRestoreConfirmDialog = null
                    }
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirmDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Diálogo de confirmación para eliminar nota permanentemente
    showDeleteConfirmDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Eliminar permanentemente") },
            text = { 
                Column {
                    Text("¿Estás seguro de eliminar permanentemente esta nota?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Esta acción no se puede deshacer.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                viewModel.deleteNotePermanently(note.id)
                                Toast.makeText(context, "Nota eliminada permanentemente", Toast.LENGTH_SHORT).show()
                                loadNotes()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Diálogo de confirmación para vaciar toda la papelera
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("Vaciar papelera") },
            text = { 
                Column {
                    Text("¿Estás seguro de eliminar permanentemente TODAS las notas en la papelera?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Esta acción eliminará ${notesInTrash.size} notas y no se puede deshacer.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                viewModel.emptyTrash()
                                Toast.makeText(context, "Papelera vaciada correctamente", Toast.LENGTH_SHORT).show()
                                loadNotes()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al vaciar papelera: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showEmptyTrashDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
} 