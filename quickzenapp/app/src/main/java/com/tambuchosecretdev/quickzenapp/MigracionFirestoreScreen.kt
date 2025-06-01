package com.tambuchosecretdev.quickzenapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigracionFirestoreScreen(
    navController: NavHostController,
    viewModel: NotesViewModel<Any?>
) {
    val migrationState by viewModel.migrationState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    
    // Dialogo de confirmación
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Migración a Estructura Segura") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta informativa
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Información Importante",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Esta herramienta migrará tus notas a una nueva estructura más segura en Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "• La nueva estructura organiza tus notas como: users/{userId}/notes/{noteId}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        
                        Text(
                            text = "• Esta estructura garantiza que solo tú tengas acceso a tus notas",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        
                        Text(
                            text = "• El proceso es seguro y tus notas serán copiadas antes de eliminar las antiguas",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                
                // Estado actual
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Estado Actual",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val statusText = when (migrationState) {
                            MigrationState.IDLE -> "No iniciada"
                            MigrationState.MIGRATING -> "Migración en progreso..."
                            MigrationState.MIGRATED -> "Notas migradas - Pendiente eliminar antiguas"
                            MigrationState.COMPLETED -> "Migración completada"
                            MigrationState.FAILED -> "Error en la migración"
                        }
                        
                        val statusColor = when (migrationState) {
                            MigrationState.IDLE -> Color.Gray
                            MigrationState.MIGRATING -> MaterialTheme.colorScheme.primary
                            MigrationState.MIGRATED -> MaterialTheme.colorScheme.tertiary
                            MigrationState.COMPLETED -> MaterialTheme.colorScheme.primary
                            MigrationState.FAILED -> MaterialTheme.colorScheme.error
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(
                                    color = statusColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val icon = when (migrationState) {
                                    MigrationState.COMPLETED -> Icons.Default.CheckCircle
                                    MigrationState.FAILED -> Icons.Default.Error
                                    else -> Icons.Default.Sync
                                }
                                
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(start = 8.dp),
                                        color = statusColor
                                    )
                                }
                            }
                        }
                        
                        // Mensajes
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        if (operationMessage != null) {
                            Text(
                                text = operationMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                
                // Botones de acción
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.migrateNotesToNewStructure() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isLoading == false && migrationState != MigrationState.MIGRATING && migrationState != MigrationState.COMPLETED
                    ) {
                        Text("Iniciar Migración")
                    }
                    
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isLoading == false && migrationState == MigrationState.MIGRATED,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Eliminar Notas Antiguas")
                    }
                    
                    TextButton(
                        onClick = { viewModel.clearError() },
                        enabled = errorMessage != null
                    ) {
                        Text("Limpiar Errores")
                    }
                    
                    TextButton(
                        onClick = { viewModel.clearOperationMessage() },
                        enabled = operationMessage != null
                    ) {
                        Text("Limpiar Mensajes")
                    }
                }
                
                // Información adicional
                Text(
                    text = "© 2025 QuickZen App\nEsta actualización mejora la seguridad de tus datos",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                )
            }
        }
    )
    
    // Diálogo de confirmación
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar eliminación") },
            text = { 
                Text("¿Estás seguro de que quieres eliminar las notas antiguas? " +
                    "Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteOldStructureNotes()
                        showConfirmDialog = false
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMigracionScreen() {
    // Vista previa no disponible debido a que requiere un ViewModel real
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Vista previa no disponible")
    }
}
