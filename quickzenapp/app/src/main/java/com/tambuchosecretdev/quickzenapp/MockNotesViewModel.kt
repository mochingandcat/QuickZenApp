package com.tambuchosecretdev.quickzenapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tambuchosecretdev.quickzenapp.firebase.SyncState
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Implementación simplificada del ViewModel para pruebas
 * Esta clase simula las funcionalidades básicas del NotesViewModel
 * sin dependencias complejas
 */
class MockNotesViewModel : ViewModel() {
    // Simulación de notas
    private val _activeNotes = MutableStateFlow<List<Note>>(
        listOf(
            Note(
                id = 1L,
                title = "Bienvenido a QuickZenApp",
                content = "Esta es una nota de ejemplo. Todas las funcionalidades están siendo integradas.",
                createdDate = System.currentTimeMillis(),
                modifiedDate = System.currentTimeMillis(),
                colorId = NoteAppColors.BLUE_COLOR_ID,
                isFavorite = true,
                userId=UUID.randomUUID().toString()
            ),
            Note(
                id = 2L,
                title = "Lista de tareas",
                content = "1. Completar autenticación\n2. Implementar almacenamiento local\n3. Configurar sincronización con Firebase",
                createdDate = System.currentTimeMillis() - 86400000,
                modifiedDate = System.currentTimeMillis() - 86400000,
                colorId = NoteAppColors.GREEN_COLOR_ID,
                isFavorite = false,
                userId=UUID.randomUUID().toString()
            ),
            Note(
                id = 3L,
                title = "Nota con PIN",
                content = "Esta nota tiene un PIN de seguridad (simulado).",
                createdDate = System.currentTimeMillis() - 172800000,
                modifiedDate = System.currentTimeMillis() - 172800000,
                colorId = NoteAppColors.PURPLE_COLOR_ID,
                isLocked = true,
                userId=UUID.randomUUID().toString()
            )
        )
    )
    val activeNotes: StateFlow<List<Note>> = _activeNotes.asStateFlow()

    // Simulación de categorías
    private val _categories = MutableStateFlow<List<Category>>(
        listOf(
            Category(id = 1L, name = "Personal"),
            Category(id = 2L, name = "Trabajo"),
            Category(id = 3L, name = "Ideas")
        )
    )
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    // Estados para la interfaz
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    // Estado de sincronización
    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(System.currentTimeMillis() - 3600000) // 1 hora atrás
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    // Filtros
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    // Métodos simulados para interacción
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoriteStatus(note: Note) {
        val updatedNotes = _activeNotes.value.map {
            if (it.id == note.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        _activeNotes.value = updatedNotes
        _operationMessage.value = if (note.isFavorite) "Nota eliminada de favoritos" else "Nota agregada a favoritos"
    }

    fun moveToTrash(note: Note) {
        val updatedNotes = _activeNotes.value.filterNot { it.id == note.id }
        _activeNotes.value = updatedNotes
        _operationMessage.value = "Nota movida a la papelera"
    }

    // Eventos
    private val _noteCreatedEvent = MutableSharedFlow<Long>()
    val noteCreatedEvent: SharedFlow<Long> = _noteCreatedEvent.asSharedFlow()
    
    // Simulamos usuario autenticado
    private val _isUserSignedIn = MutableStateFlow(true)
    val isUserSignedIn: StateFlow<Boolean> = _isUserSignedIn.asStateFlow()
    
    // Estado de cuenta
    private val _userAccount = MutableStateFlow<Any?>(null)
    val userAccount: StateFlow<Any?> = _userAccount.asStateFlow()

    // Funciones requeridas por HomeScreen
    fun setSelectedCategoryId(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }
    
    override fun onCleared() {
        // Nada que limpiar en esta implementación simulada
        super.onCleared()
    }
    
    fun signOut() {
        // Simulación de cierre de sesión
        _isUserSignedIn.value = false
    }
    
    fun signInWithGoogle() {
        // Simulación de inicio de sesión
        _isUserSignedIn.value = true
    }
    
    fun isUserSignedIn(): Boolean {
        return _isUserSignedIn.value
    }
    
    fun syncWithFirebase() {
        // Simulación de sincronización
        _syncState.value = SyncState.SYNCING
        viewModelScope.launch {
            delay(1500) // Simulamos un proceso de sincronización
            _syncState.value = SyncState.IDLE // Usamos IDLE en lugar de SYNC_COMPLETE
            _lastSyncTime.value = System.currentTimeMillis()
            _operationMessage.value = "Sincronización completada"
        }
    }
    
    suspend fun addNote(
        title: String,
        content: String,
        colorId: String = NoteAppColors.DEFAULT_COLOR_ID,
        images: List<String> = emptyList(),
        categoryId: Long? = null,
        reminderDateTime: Long? = null,
        isFavorite: Boolean = false,
        isLocked: Boolean = false
    ): Long {
        // Simulación de creación de nota
        val newId = (_activeNotes.value.maxOfOrNull { it.id } ?: 0) + 1
        val newNote = Note(
            id = newId,
            title = title,
            content = content,
            createdDate = System.currentTimeMillis(),
            modifiedDate = System.currentTimeMillis(),
            colorId = colorId,
            images = images,
            categoryId = categoryId,
            reminderDateTime = reminderDateTime,
            isFavorite = isFavorite,
            isLocked = isLocked,
            userId=UUID.randomUUID().toString()
        )
        
        _activeNotes.value = _activeNotes.value + newNote
        _operationMessage.value = "Nota creada correctamente"
        
        // Emitir el evento en una corrutina separada para no bloquear
        viewModelScope.launch {
            _noteCreatedEvent.emit(newId)
        }
        
        // Devolver el ID de la nota creada
        return newId
    }
    
    fun updateNote(note: Note) {
        // Simulación de actualización de nota
        val updatedNotes = _activeNotes.value.map {
            if (it.id == note.id) note else it
        }
        _activeNotes.value = updatedNotes
        _operationMessage.value = "Nota actualizada"
    }
    
    fun getNoteById(id: Long): Note? {
        return _activeNotes.value.find { it.id == id }
    }
    
    fun addCategory(name: String) {
        val newId = (_categories.value.maxOfOrNull { it.id } ?: 0) + 1
        val newCategory = Category(id = newId, name = name)
        _categories.value = _categories.value + newCategory
        _operationMessage.value = "Categoría creada: $name"
    }
    
    fun getUserEmail(): String {
        return "usuario.prueba@gmail.com"
    }
}
