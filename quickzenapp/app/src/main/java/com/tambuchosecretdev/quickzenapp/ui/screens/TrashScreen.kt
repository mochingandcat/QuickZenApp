package com.tambuchosecretdev.quickzenapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.NotesViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: NotesViewModel<Any?>,
    onBackClick: () -> Unit,
    onRestoreNote: (Long) -> Unit,
    onDeletePermanently: (Long) -> Unit
) {
    // Usar collectAsState para Flow<List<Note>>
    val trashedNotes = viewModel.trashedNotes.collectAsState(initial = emptyList()).value
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papelera") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (trashedNotes.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay elementos en la papelera")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Las notas eliminadas se mantienen aquí durante 30 días",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trashedNotes) { note ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = note.title?.ifEmpty { "(Sin título)" } ?: "(Sin título)",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content?.ifEmpty { "(Sin contenido)" } ?: "(Sin contenido)",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Mostrar fecha de modificación como fecha de eliminación
                            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            val date = Date(note.modifiedDate ?: System.currentTimeMillis())
                            val elimDate = formatter.format(date)
                            
                            // Calcular fecha límite (30 días después de la eliminación)
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = note.modifiedDate ?: System.currentTimeMillis()
                            calendar.add(Calendar.DAY_OF_YEAR, 30)
                            val limitDate = formatter.format(calendar.time)
                            
                            Column {
                                Text(
                                    text = "Eliminado: $elimDate",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Se eliminará permanentemente el: $limitDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Botones de acción
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { onRestoreNote(note.id) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restaurar")
                                }
                                
                                Button(
                                    onClick = { onDeletePermanently(note.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
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
