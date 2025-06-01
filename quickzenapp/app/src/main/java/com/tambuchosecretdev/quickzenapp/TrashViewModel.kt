package com.tambuchosecretdev.quickzenapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import android.util.Log
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.delay

/**
 * ViewModel para manejar la lógica de la papelera de reciclaje.
 */
class TrashViewModel(private val repository: NoteRepository) : ViewModel() {

    // Estado para las notas en la papelera
    private val _notesInTrash = MutableStateFlow<List<Note>>(emptyList())
    val notesInTrash: StateFlow<List<Note>> = _notesInTrash.asStateFlow()

    // Estado para indicar si se está cargando
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Estado para manejar errores
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Estado para mostrar mensajes de operación exitosa
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        Log.d("TrashViewModel", "Inicializando TrashViewModel")
        loadNotesInTrash()
        
        // Carga de emergencia por si falla el Flow
        viewModelScope.launch {
            delay(500) // Pequeña pausa para asegurar que la BD esté lista
            loadTrashNotesDirectly()
        }
    }

    // Método de emergencia para cargar notas directamente sin Flow
    private suspend fun loadTrashNotesDirectly() {
        try {
            Log.d("TrashViewModel", "Cargando notas de papelera directamente (método de emergencia)")
            val trashNotes = repository.getNotesInTrash()
            if (_notesInTrash.value.isEmpty() && trashNotes.isNotEmpty()) {
                Log.d("TrashViewModel", "Encontradas ${trashNotes.size} notas en la carga directa")
                val _notesInTrash = MutableStateFlow<List<Note>>(emptyList())

            }
        } catch (e: Exception) {
            Log.e("TrashViewModel", "Error en carga directa: ${e.message}", e)
        }
    }

    private fun loadNotesInTrash() {
        viewModelScope.launch {
            try {
                Log.d("TrashViewModel", "Iniciando loadNotesInTrash()")
                _isLoading.value = true
                _error.value = null

                repository.notesInTrashFlow.collect { notes ->
                    Log.d("TrashViewModel", "Notas en papelera recibidas: ${notes.size}")
                    Log.d("TrashViewModel", "Detalles de las notas en papelera:")
                    notes.forEach { note ->
                        Log.d("TrashViewModel", "Nota ID: ${note.id}, Título: ${note.title}, isInTrash: ${note.isInTrash}")
                    }
                    _notesInTrash.value = notes
                }
            } catch (e: Exception) {
                Log.e("TrashViewModel", "Error cargando notas en papelera", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }


    // Método público para recargar las notas de la papelera
    // Puede ser llamado desde la UI cuando sea necesario
    fun refreshTrashNotes() {
        Log.d("TrashViewModel", "Iniciando refreshTrashNotes()")
        
        // Método principal usando el Flow
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.notesInTrashFlow.collect { notes ->
                    Log.d("TrashViewModel", "Notas refrescadas: ${notes.size}")
                    Log.d("TrashViewModel", "Detalles de las notas refrescadas:")
                    notes.forEach { note ->
                        Log.d("TrashViewModel", "Nota ID: ${note.id}, Título: ${note.title}, isInTrash: ${note.isInTrash}")
                    }
                    _notesInTrash.value = notes
                }
            } catch (e: Exception) {
                Log.e("TrashViewModel", "Error al refrescar notas: ${e.message}", e)
                _error.value = "Error al refrescar notas: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.d("TrashViewModel", "Finalizado refreshTrashNotes()")
            }
        }
        
        // Método de emergencia - carga directa
        viewModelScope.launch {
            loadTrashNotesDirectly()
        }
    }

    // Restaurar una nota de la papelera
    fun restoreNote(noteId: Long) {
        Log.d("TrashViewModel", "Restaurando nota: $noteId")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.restoreFromTrash(noteId)
                
                _operationMessage.value = "Nota restaurada correctamente"
            } catch (e: Exception) {
                Log.e("TrashViewModel", "Error al restaurar nota: ${e.message}", e)
                _error.value = "Error al restaurar la nota: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Eliminar una nota permanentemente
    fun deleteNotePermanently(noteId: Long) {
        Log.d("TrashViewModel", "Eliminando permanentemente nota: $noteId")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.deleteNotePermanently(noteId)
                
                _operationMessage.value = "Nota eliminada permanentemente"
            } catch (e: Exception) {
                Log.e("TrashViewModel", "Error al eliminar nota: ${e.message}", e)
                _error.value = "Error al eliminar la nota: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Vaciar la papelera (eliminar permanentemente todas las notas)
    fun emptyTrash() {
        Log.d("TrashViewModel", "Vaciando papelera")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.emptyTrash()
                
                _operationMessage.value = "Papelera vaciada correctamente"
            } catch (e: Exception) {
                Log.e("TrashViewModel", "Error al vaciar papelera: ${e.message}", e)
                _error.value = "Error al vaciar la papelera: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Limpiar errores
    fun clearError() {
        _error.value = null
    }
    
    // Limpiar mensajes de operación
    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    // Método público para obtener las notas de la papelera directamente
    suspend fun getNotesInTrashDirectly(): List<Note> {
        Log.d("TrashViewModel", "Obteniendo notas en papelera directamente desde ViewModel")
        return try {
            val notes = repository.getNotesInTrash()
            Log.d("TrashViewModel", "ViewModel obtuvo ${notes.size} notas en papelera directamente")
            notes
        } catch (e: Exception) {
            Log.e("TrashViewModel", "Error al obtener notas en papelera directamente: ${e.message}", e)
            emptyList()
        }
    }
}

/**
 * Factory para crear instancias de TrashViewModel con el repositorio necesario.
 */
class TrashViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrashViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 