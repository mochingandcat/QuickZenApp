package com.tambuchosecretdev.quickzenapp.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteItem(
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
                shareNoteIntent(context, note)
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

// Función auxiliar para compartir una nota
fun shareNoteIntent(context: Context, note: Note) {
    val shareText = "${note.title}\n\n${note.content}"
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_SUBJECT, note.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    
    val shareIntent = Intent.createChooser(sendIntent, "Compartir nota")
    context.startActivity(shareIntent)
}
