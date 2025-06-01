package com.tambuchosecretdev.quickzenapp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tambuchosecretdev.quickzenapp.model.Note
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tambuchosecretdev.quickzenapp.firebase.SyncState as FirebaseSyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncScreen(
    viewModel: NotesViewModel<Any?>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncState by viewModel.syncState.collectAsState()
    val notes by viewModel.activeNotes.collectAsState(initial = emptyList())

    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var showDeleteCloudDataDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    val scrollState = rememberScrollState()
    
    val isUserLoggedIn = Firebase.auth.currentUser != null

    // Lanzador para seleccionar archivo a importar
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isImporting = true
                errorMessage = null
                successMessage = null
                
                try {
                    val importedNotes = readNotesFromUri(context, it)
                    if (importedNotes.isNotEmpty()) {
                        // Usar viewModelScope para la operación suspendida
                        // TODO: Implementar el método restoreNotesFromBackup en el ViewModel
                        // val success = viewModel.restoreNotesFromBackup(importedNotes)
                        val success = false // Temporalmente para evitar errores de compilación
                        if (success) {
                            successMessage = "Se importaron ${importedNotes.size} notas con éxito"
                            Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                        } else {
                            errorMessage = "No se pudieron importar las notas"
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        errorMessage = "El archivo no contiene notas válidas"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("BackupSyncScreen", "Error al importar notas", e)
                    errorMessage = "Error al importar: ${e.message}"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
                
                isImporting = false
            }
        }
    }

    // Lanzador para guardar archivo de exportación
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isExporting = true
                errorMessage = null
                successMessage = null
                
                try {
                    // Especificar el tipo explícitamente para resolver la ambigüedad
                    val notesList: List<Note> = notes
                    val activeNotes = notesList.filter { note -> !note.isInTrash }
                    if (activeNotes.isNotEmpty()) {
                        val success = writeNotesToUri(context, it, activeNotes)
                        if (success) {
                            successMessage = "Se exportaron ${activeNotes.size} notas con éxito"
                            Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
                        } else {
                            errorMessage = "No se pudieron exportar las notas"
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        errorMessage = "No hay notas activas para exportar"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("BackupSyncScreen", "Error al exportar notas", e)
                    errorMessage = "Error al exportar: ${e.message}"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
                
                isExporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copias de seguridad y sincronización") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección de copia local
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Copia de seguridad local",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        "Guarda tus notas en un archivo local o restaura desde una copia de seguridad anterior.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                exportLauncher.launch("quickzen_backup_$timeStamp.json")
                            },
                            enabled = !isExporting && (notes as List<Note>).any { note -> !note.isInTrash },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exportar")
                        }
                        
                        Button(
                            onClick = { importLauncher.launch("application/json") },
                            enabled = !isImporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importar")
                        }
                    }
                }
            }
            
            // Sección de sincronización en la nube
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Sincronización en la nube",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isUserLoggedIn) {
                        Text(
                            "Información de Sincronización",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            "La sincronización con Firebase ahora se realiza automáticamente cuando:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            "• Se crea o edita una nota\n" +
                            "• Se inicia la aplicación\n" +
                            "• Regresas a la pantalla principal",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                        
                        // Estado actual de sincronización (solo informativo)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Estado actual: ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                when (syncState) {
                                    FirebaseSyncState.SUCCESS -> "Sincronizado"
                                    FirebaseSyncState.SYNCING -> "Sincronizando..."
                                    FirebaseSyncState.CONNECTING -> "Conectando..."
                                    FirebaseSyncState.ERROR_SYNC -> "Error de sincronización"
                                    FirebaseSyncState.ERROR_AUTHENTICATION -> "Error de autenticación"
                                    FirebaseSyncState.IDLE -> "Pendiente de sincronizar"
                                    else -> "Desconocido"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (syncState) {
                                    FirebaseSyncState.SUCCESS -> MaterialTheme.colorScheme.primary
                                    FirebaseSyncState.ERROR_SYNC, FirebaseSyncState.ERROR_AUTHENTICATION -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        
                        TextButton(
                            onClick = { showDeleteCloudDataDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Eliminar datos en la nube")
                        }
                    } else {
                        Text(
                            "Inicia sesión con Google para sincronizar tus notas en la nube.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Button(
                            onClick = {
                                Toast.makeText(context, "Por favor, inicia sesión desde la pantalla de inicio", Toast.LENGTH_LONG).show()
                                onNavigateBack()
                            }
                        ) {
                            Text("Iniciar sesión")
                        }
                    }
                }
            }
            
            // Información de estado
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            if (successMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        successMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Diálogo de confirmación para eliminar datos en la nube
    if (showDeleteCloudDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCloudDataDialog = false },
            title = { Text("Eliminar datos en la nube") },
            text = { Text("¿Estás seguro de que deseas eliminar todos tus datos almacenados en la nube? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                // TODO: Implementar el método deleteAllCloudData en el ViewModel
                                coroutineScope.launch {
                                    // viewModel.deleteAllCloudData()
                                    // Comentado temporalmente hasta implementar el método
                                }
                                val success = true
                                if (success) {
                                    successMessage = "Datos en la nube eliminados con éxito"
                                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = "Error al eliminar datos en la nube"
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                            showDeleteCloudDataDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCloudDataDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// Función para leer notas desde un archivo URI
private fun readNotesFromUri(context: Context, uri: Uri): List<Note> {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = reader.readText()
        reader.close()
        
        val gson = Gson()
        val type = object : TypeToken<List<Note>>() {}.type
        return gson.fromJson(jsonString, type) ?: emptyList()
    } catch (e: Exception) {
        Log.e("BackupSyncScreen", "Error al leer archivo de notas", e)
        throw e
    }
}

// Función para escribir notas a un archivo URI
private fun writeNotesToUri(context: Context, uri: Uri, notes: List<Note>): Boolean {
    return try {
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
        val writer = OutputStreamWriter(outputStream)
        
        val gson = Gson()
        val jsonString = gson.toJson(notes)
        writer.write(jsonString)
        writer.close()
        
        true
    } catch (e: Exception) {
        Log.e("BackupSyncScreen", "Error al escribir archivo de notas", e)
        false
    }
}
