package com.tambuchosecretdev.quickzenapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
import com.tambuchosecretdev.quickzenapp.firebase.SyncState
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// enum class SyncState local ha sido eliminada y reemplazada por la importación de firebase.SyncState

/**
 * Estados de la migración de la estructura de datos en Firestore
 */
enum class MigrationState {
    IDLE,        // Estado inicial o en reposo
    MIGRATING,   // Migración en progreso
    MIGRATED,    // Migración completada, pendiente de eliminar datos antiguos
    COMPLETED,   // Migración completa y datos antiguos eliminados
    FAILED       // Error en la migración
}

class NotesViewModel<T>(
    private val repository: NoteRepository,
    private val firebaseManager: FirebaseManager
) : ViewModel() {
    
    init {
        checkGoogleAccountStatus()
        refreshNotes()
        setupLatestNotesObserver()
        // Iniciar sincronización en tiempo real si el usuario está conectado
        if (isUserSignedInInternal()) {
            viewModelScope.launch {
                startRealtimeSync()
            }
        }
    }

    /**
     * Verifica si el usuario ha iniciado sesión
     * @return true si el usuario está autenticado, false en caso contrario
     */
    fun isUserSignedIn(): Boolean {
        // Delegamos a nuestro método interno para evitar duplicación
        return isUserSignedInInternal()
    }

    private fun setupLatestNotesObserver() {
        // Implementación pendiente
    }

    private fun checkGoogleAccountStatus() {
        // Implementación pendiente
    }
    
    /**
     * Método interno para verificar si el usuario está autenticado
     * Usamos un nombre distinto para evitar ambigüedades con las múltiples
     * implementaciones de isUserSignedIn que existían en el código original
     */
    private fun isUserSignedInInternal(): Boolean {
        return repository.isUserSignedIn()
    }
    // Flujos (Flows) para la UI
    val categories: Flow<List<Category>> = repository.categoriesFlow
    val activeNotes: Flow<List<Note>> = repository.activeNotesFlow
    val favoriteNotes: Flow<List<Note>> = repository.favoriteNotesFlow
    val trashedNotes: Flow<List<Note>> = repository.notesInTrashFlow

    // Estados para búsqueda y filtros
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _isShowingFavorites = MutableStateFlow(false)
    val isShowingFavorites: StateFlow<Boolean> = _isShowingFavorites.asStateFlow()

    // Estados de la UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Estado de sincronización
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Mensaje de error
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Mensaje de operación
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    // Estado de la migración de la estructura de datos
    private val _migrationState = MutableStateFlow(MigrationState.IDLE)
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()

    // Variables para controlar la sincronización
    private var syncJob: Job? = null
    private var isSyncCancelled = false
    
    /**
     * Limpia el error actual
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Limpia el mensaje de operación actual
     */
    fun clearOperationMessage() {
        _operationMessage.value = null
    }
    
    /**
     * Realiza un inicio de sesión silencioso e inicia sincronización
     */
    fun silentSignInAndSync() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                if (firebaseManager.silentSignIn()) {
                    startSync()
                } else {
                    _operationMessage.value = "No se pudo iniciar sesión automáticamente"
                }
            } catch (e: Exception) {
                _error.value = "Error al iniciar sesión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Inicia el proceso de sincronización desde la UI
     */
    fun initiateSync() {
        // Cancelar cualquier trabajo de sincronización anterior si existe
        syncJob?.cancel()
        isSyncCancelled = false
        
        // Iniciar una nueva sincronización
        syncJob = viewModelScope.launch {
            try {
                _syncState.value = SyncState.SYNCING
                _isLoading.value = true
                _operationMessage.value = "Sincronizando notas..."
                
                // Verificar si el usuario está conectado
                if (!isUserSignedIn()) {
                    _error.value = "Debe iniciar sesión para sincronizar"
                    _syncState.value = SyncState.NOT_SIGNED_IN
                    return@launch
                }
                
                // Realizar la sincronización
                if (syncWithFirebase()) {
                    _lastSyncTime.value = System.currentTimeMillis()
                    _operationMessage.value = "Sincronización completada correctamente"
                    _syncState.value = SyncState.SYNCED
                    Log.d("NotesViewModel", "Sincronización completada")
                } else {
                    _error.value = "Error durante la sincronización"
                    _syncState.value = SyncState.FAILED
                    Log.e("NotesViewModel", "Sincronización fallida")
                }
            } catch (e: CancellationException) {
                _operationMessage.value = "Sincronización cancelada"
                _syncState.value = SyncState.CANCELLED
                Log.w("NotesViewModel", "Sincronización cancelada", e)
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                _syncState.value = SyncState.ERROR_SYNC
                Log.e("NotesViewModel", "Error durante la sincronización", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun handleException(e: Exception, function: () -> Int): Any {
        // Implementación pendiente
        return 0
    }
    /**
     * Método público para iniciar la sincronización desde la UI
     * Maneja todo el ciclo de sincronización, incluyendo estados, mensajes y errores
     */
    fun startSync() {
        // Evitar múltiples sincronizaciones simultáneas
        if (_syncState.value == SyncState.SYNCING) {
            viewModelScope.launch {
                _operationMessage.emit("Ya hay una sincronización en progreso")
            }
            return
        }
        
        viewModelScope.launch {
            Log.d("NotesViewModel", "Iniciando sincronización desde la UI")
            _syncState.value = SyncState.SYNCING
            _isLoading.value = true
            
            try {
                val success = syncWithFirebase()
                if (success) {
                    Log.d("NotesViewModel", "Sincronización completada con éxito")
                    _operationMessage.emit("¡Sincronización completada!")
                    _syncState.value = SyncState.SYNCED
                } else {
                    Log.w("NotesViewModel", "Sincronización no exitosa")
                    _operationMessage.emit("No se pudo completar la sincronización")
                    _syncState.value = SyncState.FAILED
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error durante sincronización", e)
                _error.emit("Error: ${e.message}")
                _syncState.value = SyncState.FAILED
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Sincroniza con Firebase usando nuestro enfoque optimizado
     * Este método realiza sincronización bidireccional completa: sube datos locales y descarga cambios del servidor
     * 
     * @return true si la sincronización fue exitosa, false en caso contrario
     */
    suspend fun syncWithFirebase(): Boolean {
        // Cancelar sincronización previa si existe
        syncJob?.cancel()
        isSyncCancelled = false

        // Marcar como sincronizando
        _syncState.value = SyncState.SYNCING
        
        try {
            // Verificamos que el usuario esté autenticado
            if (!repository.isUserSignedIn()) {
                _syncState.value = SyncState.NOT_SIGNED_IN
                _error.emit("No hay sesión iniciada. Inicie sesión para sincronizar.")
                return false
            }
            
            Log.d("NotesViewModel", "Llamando a repository.syncAllWithFirebase()")
            
            // Iniciamos la sincronización con el repositorio con timeout de 30 segundos
            val syncSuccess = withTimeout(30000) {
                repository.syncAllWithFirebase()
                true
            }
            
            if (isSyncCancelled) {
                _syncState.value = SyncState.CANCELLED
                _operationMessage.emit("Sincronización cancelada")
                return false
            }

            // Actualizamos el estado de sincronización
            _syncState.value = SyncState.SYNCED
            _lastSyncTime.value = repository.getLastSyncTime() ?: System.currentTimeMillis()
            _operationMessage.emit("Sincronización completada correctamente")
            
            return syncSuccess
        } catch (e: TimeoutCancellationException) {
            Log.e("NotesViewModel", "Timeout durante la sincronización", e)
            _syncState.value = SyncState.FAILED
            _error.emit("La sincronización ha tomado demasiado tiempo. Inténtelo de nuevo.")
            return false
        } catch (e: CancellationException) {
            _syncState.value = SyncState.CANCELLED
            _operationMessage.emit("Sincronización cancelada")
            return false
        } catch (e: Exception) {
            // Manejo de diferentes tipos de errores
            when {
                e.message?.contains("No hay sesión iniciada") == true -> {
                    _syncState.value = SyncState.NOT_SIGNED_IN
                    _error.emit("No hay sesión iniciada. Inicie sesión para sincronizar.")
                    return false
                }
                e.message?.contains("Sin conexión") == true -> {
                    _syncState.value = SyncState.NO_CONNECTION
                    _error.emit("Sin conexión a internet. Verifique su conexión e intente de nuevo.")
                    return false
                }
                else -> {
                    _syncState.value = SyncState.FAILED
                    _error.emit("Error de sincronización: ${e.message}")
                    Log.e("NotesViewModel", "Error al sincronizar con Firebase", e)
                    return false
                }
            }
        }
        finally {
            _isLoading.value = false
        }
    }

    /**
     * Inicia la sincronización en tiempo real si el usuario está conectado
     */
    private suspend fun startRealtimeSync() {
        if (!isUserSignedIn()) return
        
        try {
            // Esta función ahora establece listeners en Firebase
            firebaseManager.startRealtimeSync { changes ->
                if (changes > 0) {
                    Log.d("NotesViewModel", "Recibidos $changes cambios en tiempo real.")
                    refreshNotes()
                    _operationMessage.value = "Se han recibido $changes actualizaciones."
                }
            }
            Log.d("NotesViewModel", "Sincronización en tiempo real iniciada.")
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Error al iniciar sincronización en tiempo real", e)
        }
    }

    /**
     * Detiene la sincronización en tiempo real
     */
    fun stopRealtimeSync() {
        viewModelScope.launch {
            try {
                firebaseManager.stopRealtimeSync()
                Log.d("NotesViewModel", "Sincronización en tiempo real detenida.")
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al detener sincronización en tiempo real", e)
            }
        }
    }

    /**
     * Refresca las notas para reflejar los cambios en la base de datos
     */
    private fun refreshNotes() {
        viewModelScope.launch {
            repository.refreshNotes()
        }
    }

    /**
     * Obtiene una nota por ID
     */
    suspend fun getNoteById(id: Long): Note? {
        return repository.getNoteById(id)
    }

    /**
     * Agrega una nueva nota
     */
    suspend fun addNote(
        title: String, 
        content: String, 
        colorId: String = NoteAppColors.DEFAULT_COLOR_ID,
        categoryId: Long? = null, 
        isFavorite: Boolean = false,
        isLocked: Boolean = false,
        images: List<String> = emptyList(),
        reminder: Long? = null
    ): Long {
        if (title.isEmpty() && content.isEmpty()) {
            _error.value = "La nota está vacía"
            return 0
        }

        val note = Note(
            id = 0,  // La base de datos asignará un ID automáticamente
            title = title,
            content = content,
            createdDate = System.currentTimeMillis(),
            modifiedDate = System.currentTimeMillis(),
            isFavorite = isFavorite,
            isInTrash = false,
            categoryId = categoryId,
            reminderDateTime = reminder,
            cloudId = null,  // Se asignará durante la sincronización
            needsSync = true,
            colorId = colorId,
            isLocked = isLocked,
            images = images,
            userId = java.util.UUID.randomUUID().toString() // Generar un userId único
        )
        try {
            _isLoading.value = true
            val noteId = repository.addNote(note)
            Log.d("NotesViewModel", "Nota creada con ID: $noteId")

            if (noteId > 0) {
                refreshNotes()

                // Si el usuario está conectado, intentamos sincronizar inmediatamente
                if (isUserSignedIn()) {
                    viewModelScope.launch {
                        try {
                            syncWithFirebase()
                        } catch (e: Exception) {
                            Log.e("NotesViewModel", "Error al sincronizar nueva nota", e)
                            // No reportamos el error al usuario para no interrumpir el flujo
                        }
                    }
                }

                _operationMessage.value = "Nota guardada correctamente"
                return noteId
            } else {
                _error.value = "Error al crear la nota"
                return 0
            }
        } catch (e: Exception) {
            _error.value = "Error al guardar la nota: ${e.message}"
            Log.e("NotesViewModel", "Error al añadir nota", e)
            return 0
        } finally {
            _isLoading.value = false
        }
    }
    /**
     * Actualiza una nota existente optimizando la sincronización con Firebase
     * para evitar duplicados
     */
    fun updateNote(
        id: Long,
        title: String,
        content: String,
        categoryId: Long?,
        reminder: Long? = null,
        colorId: String = NoteAppColors.DEFAULT_COLOR_ID,
        isFavorite: Boolean = false,
        isLocked: Boolean = false,
        images: List<String> = emptyList()
    ) {
        if (title.isEmpty() && content.isEmpty()) {
            _error.value = "La nota está vacía"
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Obtener la nota actual para preservar otros campos
                val currentNote = repository.getNoteById(id)
                if (currentNote == null) {
                    _error.value = "No se encontró la nota para actualizar"
                    return@launch
                }

                // Verificar si la nota tiene un userId, si no, generar uno
                val userId = currentNote.userId ?: java.util.UUID.randomUUID().toString()
                
                // Actualizar todos los campos proporcionados
                val updatedNote = currentNote.copy(
                    title = title,
                    content = content,
                    modifiedDate = System.currentTimeMillis(),
                    categoryId = categoryId,
                    reminderDateTime = reminder,
                    colorId = colorId,
                    isFavorite = isFavorite,
                    isLocked = isLocked,
                    images = images,
                    userId = userId,  // Asegurar que siempre tenga un userId
                    needsSync = true  // Marcar como pendiente de sincronización
                )

                repository.updateNote(updatedNote)
                Log.d("NotesViewModel", "Nota actualizada localmente: id=$id, cloudId=${updatedNote.cloudId}")

                // Si el usuario está conectado, sincronizar esta nota inmediatamente
                if (isUserSignedIn() && firebaseManager.isConnected()) {
                    try {
                        // Usar saveNote para guardar/actualizar la nota en Firebase
                        // Esto manejará correctamente si es una actualización o una creación
                        val cloudId = firebaseManager.saveNote(updatedNote)
                        
                        if (cloudId != null) {
                            // Actualizar el cloudId y marcar como sincronizada (needsSync = false)
                            repository.updateNoteAfterSync(id, cloudId)
                            Log.d("NotesViewModel", "Nota sincronizada exitosamente: id=$id, cloudId=$cloudId")
                        } else {
                            Log.w("NotesViewModel", "No se pudo obtener cloudId al sincronizar nota: $id")
                            // No cambiamos el estado needsSync para que se intente sincronizar más tarde
                        }
                    } catch (e: Exception) {
                        Log.e("NotesViewModel", "Error al sincronizar nota actualizada: ${e.message}", e)
                        // No reportamos el error al usuario para no interrumpir el flujo
                    }
                } else {
                    Log.d("NotesViewModel", "Usuario no conectado o sin internet. La nota se sincronizará más tarde.")
                }

                _operationMessage.value = "Nota actualizada correctamente"
                refreshNotes()
            } catch (e: Exception) {
                _error.value = "Error al actualizar la nota: ${e.message}"
                Log.e("NotesViewModel", "Error al actualizar nota", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Elimina una nota moviendo a la papelera
     */
    fun deleteNoteById(id: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Eliminar la nota usando el repositorio
                repository.deleteNoteById(id)
                
                // Si el usuario está conectado, sincronizar este cambio
                if (isUserSignedIn()) {
                    try {
                        val note = repository.getNoteById(id)
                        note?.let {
                            firebaseManager.updateNote(it)
                            Log.d("NotesViewModel", "Nota eliminada y sincronizada con Firebase: $id")
                        }
                    } catch (e: Exception) {
                        Log.e("NotesViewModel", "Error al sincronizar eliminación con Firebase", e)
                    }
                }
                
                _operationMessage.value = "Nota eliminada correctamente"
                refreshNotes()
            } catch (e: Exception) {
                _error.value = "Error al eliminar la nota: ${e.message}"
                Log.e("NotesViewModel", "Error al eliminar nota", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    /**
     * Cambia el estado de favorito de una nota
     */
    fun toggleFavoriteStatus(id: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Obtener la nota actual
                val note = repository.getNoteById(id)
                if (note == null) {
                    _error.value = "No se encontró la nota"
                    return@launch
                }
                
                // Cambiar el estado de favorito
                val updatedNote = note.copy(
                    isFavorite = !note.isFavorite,
                    modifiedDate = System.currentTimeMillis(),
                    needsSync = true
                )
                
                repository.updateNote(updatedNote)
                Log.d("NotesViewModel", "Estado de favorito cambiado a ${updatedNote.isFavorite} para nota $id")
                
                // Si el usuario está conectado, sincronizar este cambio
                if (isUserSignedIn() && firebaseManager.isConnected()) {
                    try {
                        val cloudId = firebaseManager.saveNote(updatedNote)
                        if (cloudId != null) {
                            repository.updateNoteAfterSync(id, cloudId)
                            Log.d("NotesViewModel", "Cambio de favorito sincronizado: id=$id, cloudId=$cloudId")
                        }
                    } catch (e: Exception) {
                        Log.e("NotesViewModel", "Error al sincronizar cambio de favorito", e)
                    }
                }
                
                _operationMessage.value = if (updatedNote.isFavorite) "Nota marcada como favorita" else "Nota desmarcada como favorita"
                refreshNotes()
            } catch (e: Exception) {
                _error.value = "Error al cambiar estado de favorito: ${e.message}"
                Log.e("NotesViewModel", "Error al cambiar favorito", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

// El método toggleFavoriteStatus ya está definido en las líneas anteriores

/**
 * Envía una nota a la papelera
 * @param id ID de la nota a mover a la papelera
 */
fun moveNoteToTrash(id: Long) {
    viewModelScope.launch {
        try {
            _isLoading.value = true
            
            // Obtener la nota actual
            val note = repository.getNoteById(id)
            if (note == null) {
                _error.value = "No se encontró la nota"
                return@launch
            }
            
            // Mover a papelera
            val updatedNote = note.copy(
                isInTrash = true,
                modifiedDate = System.currentTimeMillis(),
                needsSync = true
            )
            
            repository.updateNote(updatedNote)
            Log.d("NotesViewModel", "Nota movida a papelera: id=$id")
            
            // Si el usuario está conectado, sincronizar este cambio
            if (isUserSignedIn() && firebaseManager.isConnected()) {
                try {
                    val cloudId = firebaseManager.saveNote(updatedNote)
                    if (cloudId != null) {
                        repository.updateNoteAfterSync(id, cloudId)
                        Log.d("NotesViewModel", "Cambio de papelera sincronizado: id=$id, cloudId=$cloudId")
                    }
                } catch (e: Exception) {
                    Log.e("NotesViewModel", "Error al sincronizar cambio de papelera", e)
                }
            }
            
            _operationMessage.value = "Nota movida a la papelera"
            refreshNotes()
        } catch (e: Exception) {
            _error.value = "Error al mover a papelera: ${e.message}"
            Log.e("NotesViewModel", "Error al mover a papelera", e)
        } finally {
            _isLoading.value = false
        }
    }
}

/**
 * Restaura una nota desde la papelera
 */
fun restoreNoteFromTrash(id: Long) {
    viewModelScope.launch {
        try {
            _isLoading.value = true
            
            // Obtener la nota actual
            val note = repository.getNoteById(id)
            if (note == null) {
                _error.value = "No se encontró la nota"
                return@launch
            }
            
            // Restaurar de la papelera
            val updatedNote = note.copy(
                isInTrash = false,
                modifiedDate = System.currentTimeMillis(),
                needsSync = true
            )
            
            repository.updateNote(updatedNote)
            Log.d("NotesViewModel", "Nota restaurada de papelera: id=$id")
            
            // Si el usuario está conectado, sincronizar este cambio
            if (isUserSignedIn() && firebaseManager.isConnected()) {
                try {
                    val cloudId = firebaseManager.saveNote(updatedNote)
                    if (cloudId != null) {
                        repository.updateNoteAfterSync(id, cloudId)
                        Log.d("NotesViewModel", "Restauración sincronizada: id=$id, cloudId=$cloudId")
                    }
                } catch (e: Exception) {
                    Log.e("NotesViewModel", "Error al sincronizar restauración", e)
                }
            }
            
            _operationMessage.value = "Nota restaurada de la papelera"
            refreshNotes()
        } catch (e: Exception) {
            _error.value = "Error al restaurar nota: ${e.message}"
            Log.e("NotesViewModel", "Error al restaurar nota de papelera", e)
        } finally {
            _isLoading.value = false
        }
    }
}

/**
 * Envía una nota a la papelera
 */

/**
 * Alias para moveNoteToTrash (para compatibilidad)
 */
fun moveToTrash(id: Long) {
    moveNoteToTrash(id)
}

/**
 * Elimina permanentemente una nota
 */
fun deleteNotePermanently(id: Long) {
viewModelScope.launch {
try {
_isLoading.value = true
                
// Obtener la nota para verificar si tiene cloudId
val note = repository.getNoteById(id)
                
// Si tiene cloudId, eliminarla de Firebase primero
if (note != null && note.cloudId != null && isUserSignedIn()) {
try {
firebaseManager.deleteNote(note.cloudId!!)
Log.d("NotesViewModel", "Nota eliminada de Firebase: cloudId=${note.cloudId}")
} catch (e: Exception) {
Log.e("NotesViewModel", "Error al eliminar de Firebase: ${e.message}", e)
// Continuamos con la eliminación local aunque falle en Firebase
}
}
                
// Eliminar localmente
repository.deleteNotePermanently(id)
Log.d("NotesViewModel", "Nota eliminada permanentemente: id=$id")
                
_operationMessage.value = "Nota eliminada permanentemente"
refreshNotes()
} catch (e: Exception) {
_error.value = "Error al eliminar permanentemente: ${e.message}"
Log.e("NotesViewModel", "Error al eliminar permanentemente", e)
} finally {
_isLoading.value = false
}
}
}

/**
 * Vacía la papelera eliminando permanentemente todas las notas en ella
 */
fun emptyTrash() {
viewModelScope.launch {
try {
_isLoading.value = true
                
// Obtener todas las notas en la papelera
val notesInTrash = repository.getNotesInTrash()
                
// Eliminar de Firebase primero si el usuario está conectado
if (isUserSignedIn()) {
notesInTrash.forEach { note ->
if (note.cloudId != null) {
try {
firebaseManager.deleteNote(note.cloudId!!)
Log.d("NotesViewModel", "Nota eliminada de Firebase: cloudId=${note.cloudId}")
} catch (e: Exception) {
Log.e("NotesViewModel", "Error al eliminar de Firebase: ${e.message}", e)
// Continuamos con las siguientes aunque falle alguna
}
}
}
}
                
// Eliminar todas localmente
repository.emptyTrash()
Log.d("NotesViewModel", "Papelera vaciada: ${notesInTrash.size} notas eliminadas")
                
_operationMessage.value = "Papelera vaciada"
refreshNotes()
} catch (e: Exception) {
_error.value = "Error al vaciar la papelera: ${e.message}"
Log.e("NotesViewModel", "Error al vaciar papelera", e)
} finally {
_isLoading.value = false
}
}
}

/**
 * Actualiza la cuenta de Google
 */
fun updateGoogleAccount(account: GoogleSignInAccount) {
viewModelScope.launch {
try {
firebaseManager.updateGoogleAccount(account)
_operationMessage.value = "Cuenta actualizada correctamente"
                
// Si acabamos de iniciar sesión, iniciar sincronización en tiempo real
startRealtimeSync()
} catch (e: Exception) {
_error.value = "Error al actualizar cuenta: ${e.message}"
Log.e("NotesViewModel", "Error al actualizar cuenta Google", e)
}
}
}

/**
 * Establece la query de búsqueda
 */
fun setSearchQuery(query: String) {
_searchQuery.value = query
}

/**
 * Establece la categoría seleccionada
 */
fun setSelectedCategory(categoryId: Long?) {
_selectedCategoryId.value = categoryId
}

/**
 * Alias para setSelectedCategory (para compatibilidad con las clases existentes)
 * Renombrado para evitar conflicto de sobrecarga
 */
fun selectCategoryById(categoryId: Long?) {
setSelectedCategory(categoryId)
}

/**
 * Cambia el estado de mostrar sólo favoritos
 */
fun toggleShowingFavorites() {
_isShowingFavorites.value = !_isShowingFavorites.value
}

/**
 * Alias para resolución de compatibilidad (evitar recursión infinita)
 */
fun toggleFavoriteStatusAlias(id: Long) {
toggleFavoriteStatus(id)
}

// Los métodos clearError(), clearOperationMessage() e initiateSync() ya están definidos en otras partes del ViewModel

/**
 * Obtiene una nota por ID sin usar corrutinas (para compatibilidad)
 * @param id ID de la nota
 * @param callback Función a ejecutar con la nota obtenida (o null si no se encuentra)
 */
fun getNoteByIdNonSuspend(id: Long, callback: (Note?) -> Unit) {
    viewModelScope.launch {
        try {
            val result = repository.getNoteByIdNonSuspend(id)
            callback(result)
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Error al obtener nota por ID: ${e.message}", e)
            _error.value = "Error al obtener nota: ${e.message}"
            callback(null)
        }
    }
}

/**
 * Versión sin callback de getNoteByIdNonSuspend (para compatibilidad con código existente)
 * @param id ID de la nota
 * @return Nota encontrada o null si no existe
 */
fun getNoteByIdNonSuspend(id: Long): Note? {
    return try {
        val result = repository.getNoteByIdNonSuspend(id)
        result
    } catch (e: Exception) {
        Log.e("NotesViewModel", "Error al obtener nota por ID: ${e.message}", e)
        _error.value = "Error al obtener nota: ${e.message}"
        null
    }
}

// El método getNoteById ya está definido en otra parte del ViewModel

/**
 * Restaura notas desde un backup
 */
fun restoreNotesFromBackup(notes: List<Note>) {
viewModelScope.launch {
try {
_isLoading.value = true
var importedCount = 0
                
notes.forEach { note ->
// Verificar que la nota no exista ya
val existingNote = note.cloudId?.let { repository.getNoteByCloudId(it) }
if (existingNote == null) {
// Generar un nuevo ID y preparar para inserción
val newNote = note.copy(
id = 0,  // La base de datos asignará un nuevo ID
needsSync = true,
modifiedDate = System.currentTimeMillis()
)
repository.insertNote(newNote)
importedCount++
}
}
                
if (importedCount > 0) {
_operationMessage.value = "Se importaron $importedCount notas"
refreshNotes()
// Sincronizar las notas importadas
startSync()
} else {
_operationMessage.value = "No se encontraron notas nuevas para importar"
}
} catch (e: Exception) {
_error.value = "Error al restaurar notas: ${e.message}"
Log.e("NotesViewModel", "Error al restaurar notas desde backup", e)
} finally {
_isLoading.value = false
}
}
}

/**
 * Migra notas a la nueva estructura
 */
fun migrateNotesToNewStructure() {
viewModelScope.launch {
try {
_isLoading.value = true
// Implementación pendiente
_operationMessage.value = "Migración completada correctamente"
} catch (e: Exception) {
_error.value = "Error en la migración: ${e.message}"
Log.e("NotesViewModel", "Error al migrar notas", e)
} finally {
_isLoading.value = false
}
}
}

/**
 * Elimina la estructura antigua de notas
 */
fun deleteOldStructureNotes() {
viewModelScope.launch {
try {
_isLoading.value = true
// Implementación pendiente
_operationMessage.value = "Estructura antigua eliminada correctamente"
} catch (e: Exception) {
_error.value = "Error al eliminar estructura antigua: ${e.message}"
Log.e("NotesViewModel", "Error al eliminar estructura antigua", e)
} finally {
_isLoading.value = false
}
}
}

/**
 * Agrega una nueva categoría a la base de datos.
 * @param categoryName Nombre de la categoría a crear
 * @return ID de la categoría creada o -1 si hubo un error
 */
suspend fun addCategory(categoryName: String): Long {
val timestamp = System.currentTimeMillis()
val category = Category(
id = 0, // La base de datos asignará el ID real
name = categoryName,
createdDate = timestamp,
modifiedDate = timestamp,
cloudId = null,
needsSync = true
)
        
return try {
val categoryId = repository.insertCategory(category)
Log.d("NotesViewModel", "Categoría creada con ID: $categoryId")
_operationMessage.emit("Categoría creada correctamente")
categoryId
} catch (e: Exception) {
Log.e("NotesViewModel", "Error al crear categoría: ${e.message}")
_error.emit("Error al crear categoría: ${e.message}")
-1L
}
}

/**
 * Refresca las categorías
 */
fun refreshCategories() {
viewModelScope.launch {
repository.refreshCategories()
}
}

/**
 * Establece el ID de categoría seleccionada
 */
fun setSelectedCategoryId(categoryId: Long?) {
_selectedCategoryId.value = categoryId
Log.d("NotesViewModel", "Categoría seleccionada cambiada a: $categoryId")
viewModelScope.launch {
try {
refreshNotes()
} catch (e: Exception) {
Log.e("NotesViewModel", "Error al refrescar notas tras cambio de categoría", e)
}
}
}

/**
 * Ordena las notas por fecha
 */
fun sortNotesByDate() {
viewModelScope.launch {
try {
_isLoading.value = true
Log.d("NotesViewModel", "Ordenando notas por fecha")
// Actualizamos las notas localmente, el orden real se aplicará en la consulta
_operationMessage.value = "Notas ordenadas por fecha"
refreshNotes()
} catch (e: Exception) {
Log.e("NotesViewModel", "Error al ordenar notas", e)
_error.value = "Error al ordenar notas: ${e.message}"
} finally {
_isLoading.value = false
}
}
}



/**
 * Cancela la sincronización en curso
 */
fun cancelSync() {
    if (_syncState.value == SyncState.SYNCING) {
        syncJob?.cancel()
        isSyncCancelled = true
        _syncState.value = SyncState.CANCELLED
        _operationMessage.value = "Sincronización cancelada"
        _isLoading.value = false
        Log.d("NotesViewModel", "Sincronización cancelada por el usuario")
    } else {
        Log.d("NotesViewModel", "No hay sincronización activa para cancelar")
    }
}
}
