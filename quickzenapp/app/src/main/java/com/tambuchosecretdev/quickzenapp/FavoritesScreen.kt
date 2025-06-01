package com.tambuchosecretdev.quickzenapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.NotesViewModel
import com.tambuchosecretdev.quickzenapp.ui.components.NoteItem
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: NotesViewModel<Any?>,
    onNavigateBack: () -> Unit,
    onNoteClick: (Long) -> Unit // Para navegar a EditNoteScreen
) {
    val favoriteNotes by viewModel.favoriteNotes.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notas Favoritas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (favoriteNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tienes notas favoritas todavía.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    items(favoriteNotes, key = { note -> note.id }) { note ->
                        NoteItem(
                            note = note,
                            context = context,
                            onNoteClick = { onNoteClick(note.id) },
                            onToggleFavorite = { 
                                coroutineScope.launch {
                                    viewModel.toggleFavoriteStatus(note.id)
                                }
                            },
                            onMoveToTrash = { 
                                coroutineScope.launch {
                                    viewModel.moveToTrash(note.id)
                                }
                            },
                            onShowPinDialog = {
                                onNoteClick(it.id)
                            }
                        )
                    }
                }
            }
        }
    }
} 