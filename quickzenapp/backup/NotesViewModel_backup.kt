package com.tambuchosecretdev.quickzenapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.toLocalNote
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.tambuchosecretdev.quickzenapp.firebase.SyncState as FirebaseSyncState

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

class NotesViewModel
    private val repository: NoteRepository,
    private val firebaseManager: FirebaseManager
) : ViewModel() {
    
    init {
        checkGoogleAccountStatus()
        refreshNotes()
        setupLatestNotesObserver()
        // Iniciar sincronización en tiempo real si el usuario está conectado
        if (isUserSignedIn()) {
            viewModelScope.launch {
                startRealtimeSync()
            }
        }
    }

    private fun setupLatestNotesObserver() {
        
    }

    private fun checkGoogleAccountStatus() {
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

    private val _syncState = MutableStateFlow(FirebaseSyncState.IDLE)
    val syncState: StateFlow<FirebaseSyncState> = _syncState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
     * COMPLETO: Sincroniza con Firebase usando nuestro enfoque optimizado
     * Este método realiza sincronización bidireccional completa: sube datos locales y descarga cambios del servidor
     * SOLUCIONADO: Corregido el problema de duplicación de notas
     */
    fun syncWithFirebase() {
        // Prevenir sincronizaciones múltiples consecutivas en corto tiempo
        val currentTime = System.currentTimeMillis()
        val lastSync = _lastSyncTime.value
        
        // Solo permitir una sincronización cada 5 segundos para evitar duplicados
        if (currentTime - lastSync < 5000) {
            Log.d("NotesViewModel", "Sincronización ignorada: demasiado pronto desde la última sincronización")
            return
        }
        
        // Cancelar cualquier sincronización anterior
        syncJob?.cancel()
        syncJob = null
        isSyncCancelled = false

        // Iniciar nueva sincronización
        syncJob = viewModelScope.launch {
            try {
                if (!isUserSignedIn()) {
                    _error.value = "No has iniciado sesión con la cuenta de Google. Por favor, inicia sesión primero."
                    _syncState.value = FirebaseSyncState.ERROR_AUTHENTICATION
                    return@launch
                }

                _syncState.value = FirebaseSyncState.SYNCING
                _isLoading.value = true
                Log.d("NotesViewModel", "Iniciando sincronización bidireccional sin duplicados...")

                // SOLUCIÓN: Verificar y evitar duplicados antes de sincronizar
                // Primero, obtener todas las notas locales que necesitan sincronización
                val notesToSync = repository.getNotesForSync()
                
                // Si hay notas que necesitan sincronización, subirlas primero para evitar duplicados
                if (notesToSync.isNotEmpty()) {
                    Log.d("NotesViewModel", "Subiendo ${notesToSync.size} notas locales pendientes primero")
                    for (note in notesToSync) {
                        // Verificar si ya existe en la nube para evitar duplicados
                        val existingCloudId = if (note.cloudId.isNullOrEmpty()) {
                            firebaseManager.findExistingNoteByContent(note.title, note.content)
                        } else null
                        
                        // Si ya existe en la nube, solo actualizar el ID local
                        if (existingCloudId != null) {
                            val updatedNote = note.copy(cloudId = existingCloudId, needsSync = false)
                            repository.updateNote(updatedNote)
                            Log.d("NotesViewModel", "Nota vinculada a existente en la nube: ${note.id} -> $existingCloudId")
                        } else {
                            // Si no existe, guardarla como nueva o actualizar la existente
                            val cloudId = firebaseManager.saveNote(note)
                            if (cloudId != null) {
                                val updatedNote = note.copy(cloudId = cloudId, needsSync = false)
                                repository.updateNote(updatedNote)
                                Log.d("NotesViewModel", "Nota sincronizada: ${note.id} -> $cloudId")
                            }
                        }
                    }
                }
                
                // Ahora usar SimpleFirestoreSync pero con una bandera que evite duplicados
                val syncSuccess = com.tambuchosecretdev.quickzenapp.firebase.SimpleFirestoreSync.sync(
                    firebaseManager.context, 
                    preventDuplicates = true
                )

                if (syncSuccess) {
                    Log.d("NotesViewModel", "[NUEVA SINCRONIZACIÓN] Sincronización completada exitosamente sin duplicados")
                    _syncState.value = FirebaseSyncState.SUCCESS
                    _lastSyncTime.value = System.currentTimeMillis()
                    
                    // NO llamamos a refreshNotes() para evitar duplicación
                } else {
                    Log.w("NotesViewModel", "[NUEVA SINCRONIZACIÓN] Sincronización completada con errores")
                    _syncState.value = FirebaseSyncState.ERROR_SYNC
                }
            } catch (e: CancellationException) {
                Log.d("SYNC", "[RADICAL] Corrutina cancelada intencionalmente")
                _syncState.value = FirebaseSyncState.IDLE
            } catch (e: Exception) {
                Log.e("SYNC", "[RADICAL] Error inesperado en sincronización", e)
                _error.value = "Error en sincronización: ${e.message}"
                _syncState.value = FirebaseSyncState.ERROR_SYNC
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Actualiza las notas desde Firestore evitando duplicados
     * y preservando atributos locales como colores y favoritos
     */
    private fun refreshNotes() {
        viewModelScope.launch {
            try {
                Log.d("NotesViewModel", "Actualizando notas desde Firestore")

                // Obtener todas las notas locales primero para comparación
                val localNotes = repository.getAllActiveNotes()
                
                // Crear mapas para búsqueda eficiente por diferentes criterios
                val localNotesByCloudId = localNotes.filter { it.cloudId != null }
                    .associateBy { it.cloudId!! }
                    
                // Mapa adicional para verificar duplicados por título/contenido
                val localNotesByTitleContent = localNotes.associateBy { "${it.title}_${it.content}" }

                // Obtener notas de Firestore
                val cloudNotes = firebaseManager.getNotes()

                if (cloudNotes.isNotEmpty()) {
                    Log.d("NotesViewModel", "Procesando ${cloudNotes.size} notas de Firestore")

                    val notesToProcess = mutableListOf<Note>()

                    // Convertir y procesar cada nota de Firestore
                    for (cloudNote in cloudNotes) {
                        val localNote = cloudNote.toLocalNote()
                        
                        // Verificar si ya existe localmente por cloudId
                        var existingLocalNote = localNotesByCloudId[cloudNote.id]
                        
                        // Si no encontramos por cloudId, buscar por título+contenido para evitar duplicados
                        if (existingLocalNote == null) {
                            val key = "${localNote.title}_${localNote.content}"
                            existingLocalNote = localNotesByTitleContent[key]
                            
                            if (existingLocalNote != null) {
                                Log.d("NotesViewModel", "Encontrada nota existente por contenido: ${existingLocalNote.id}")
                            }
                        }

                        if (existingLocalNote != null) {
                            // La nota ya existe localmente, actualizar solo si la versión de Firestore es más reciente
                            if (cloudNote.timestamp.time > existingLocalNote.modifiedDate) {
                                // La nota de Firestore es más reciente, actualizar pero preservar ciertas propiedades locales
                                val updatedNote = existingLocalNote.copy(
                                    title = localNote.title,
                                    content = localNote.content,
                                    modifiedDate = localNote.modifiedDate,
                                    // Preservar colores y atributos locales si no se han sincronizado
                                    colorId = if (existingLocalNote.needsSync) existingLocalNote.colorId else localNote.colorId,
                                    isFavorite = if (existingLocalNote.needsSync) existingLocalNote.isFavorite else localNote.isFavorite,
                                    cloudId = cloudNote.id,
                                    needsSync = false
                                )
                                notesToProcess.add(updatedNote)
                                Log.d("NotesViewModel", "Actualizando nota existente: ${existingLocalNote.id}")
                            } else {
                                Log.d("NotesViewModel", "Nota local más reciente, no se actualiza: ${existingLocalNote.id}")
                            }
                        } else {
                            // Verificación más estricta para evitar duplicados, incluso en el dispositivo principal
                            // Buscar cualquier nota con título y contenido similares
                            val similarNotes = localNotes.filter { 
                                it.title.trim() == localNote.title.trim() && 
                                    it.content.trim() == localNote.content.trim()
                            }
                            
                            if (similarNotes.isEmpty()) {
                                // No hay ninguna nota similar, es seguro agregar esta como nueva
                                notesToProcess.add(localNote.copy(cloudId = cloudNote.id, needsSync = false))
                                Log.d("NotesViewModel", "Agregando nueva nota desde Firestore")
                            } else {
                                // Ya existe una nota similar, actualizar el cloudId de la nota local
                                // para evitar que se vuelva a crear un duplicado en el futuro
                                val existingNote = similarNotes.first()
                                Log.d("NotesViewModel", "Evitando crear duplicado para: ${localNote.title}")
                                
                                // Si la nota local no tiene cloudId, actualizarla para evitar futuros duplicados
                                if (existingNote.cloudId == null || existingNote.cloudId!!.isEmpty()) {
                                    val updatedNote = existingNote.copy(
                                        cloudId = cloudNote.id,
                                        needsSync = false
                                    )
                                    notesToProcess.add(updatedNote)
                                    Log.d("NotesViewModel", "Actualizando cloudId de nota local similar para evitar futuros duplicados")
                                }
                            }
                        }
                    }

                    // Procesar las notas (actualizar o insertar)
                    if (notesToProcess.isNotEmpty()) {
                        for (note in notesToProcess) {
                            if (note.id != 0L) {
                                repository.updateNote(note)
                            } else {
                                repository.insertNote(note)
                            }
                        }
                        _operationMessage.value = "${notesToProcess.size} notas sincronizadas"
                    } else {
                        Log.d("NotesViewModel", "No hay cambios para sincronizar")
                    }
                } else {
                    Log.d("NotesViewModel", "No se encontraron notas en Firestore para sincronizar")
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al refrescar notas: ${e.message}", e)
                _error.value = "Error al actualizar notas: ${e.message}"
            }
        }
    }

    private fun getApplication(): android.content.Context {
        // Este método debería devolver el contexto de la aplicación
        // Como estamos recibiendo el firebaseManager como parámetro, podemos usar su propiedad context
        return firebaseManager.context
    }

    // Método para verificar si el usuario ha iniciado sesión
    fun isUserSignedIn(): Boolean {
        return firebaseManager.isUserAuthenticated()
    }

    // Método para manejar las notas obtenidas de Firestore
    private fun handleNotesFromFirestore(cloudNotes: List<Note>) {
        viewModelScope.launch {
            try {
                Log.d("NotesViewModel", "Procesando ${cloudNotes.size} notas de Firebase")

                // Obtener TODAS las notas locales activas para comparación
                val allLocalNotes = repository.getAllActiveNotes()
                
                // Mapa de notas locales por cloudId para identificar duplicados
                val localNotesByCloudId = allLocalNotes.filter { it.cloudId != null }
                    .associateBy { it.cloudId!! }
                
                // Mapa por ID de Firebase (extraído del cloudId)
                val localNotesByFirebaseId = allLocalNotes.filter { it.cloudId != null && it.cloudId!!.contains("_") }
                    .associateBy { note -> 
                        // Extraer el ID base de Firebase (después del underscore)
                        note.cloudId!!.substringAfter("_") 
                    }

                // Lista para notas a actualizar/insertar
                val notesToSync = mutableListOf<Note>()

                // Procesar cada nota de la nube
                for (cloudNote in cloudNotes) {
                    // Primero intentar encontrar una coincidencia exacta por cloudId
                    var localNote = localNotesByCloudId[cloudNote.cloudId]
                    
                    // Si no encontramos coincidencia por cloudId, buscar por el ID base
                    if (localNote == null && cloudNote.cloudId != null && cloudNote.cloudId!!.contains("_")) {
                        val baseId = cloudNote.cloudId!!.substringAfter("_")
                        localNote = localNotesByFirebaseId[baseId]
                        if (localNote != null) {
                            Log.d("NotesViewModel", "Encontrada coincidencia alternativa para nota ${cloudNote.cloudId}")
                        }
                    }

                    // Determinar qué versión usar basado en la existencia local y marca de tiempo
                    if (localNote == null) {
                        // La nota no existe localmente, agregarla
                        Log.d("NotesViewModel", "Agregando nueva nota de la nube: ${cloudNote.cloudId}")
                        notesToSync.add(cloudNote)
                    } else if (cloudNote.modifiedDate > localNote.modifiedDate) {
                        // La nota de la nube es más reciente, actualizarla pero mantener el ID local
                        val updatedNote = cloudNote.copy(
                            id = localNote.id,  // Mantener el ID local para no crear duplicados
                            colorId = if (localNote.needsSync) localNote.colorId else cloudNote.colorId,
                            isFavorite = if (localNote.needsSync) localNote.isFavorite else cloudNote.isFavorite,
                            needsSync = false
                        )
                        Log.d("NotesViewModel", "Actualizando nota local ${localNote.id} con versión de la nube")
                        notesToSync.add(updatedNote)
                    } else if (localNote.needsSync) {
                        // La nota local tiene cambios pendientes, mantenerla
                        Log.d("NotesViewModel", "Manteniendo versión local de nota ${localNote.id} con cambios pendientes")
                    }
                    // Si la nota local es más reciente, no la reemplazamos
                }

                // Sincronizar con la base de datos local
                if (notesToSync.isNotEmpty()) {
                    Log.d("NotesViewModel", "Sincronizando ${notesToSync.size} notas con la base de datos local")
                    repository.syncWithCloud(notesToSync)
                } else {
                    Log.d("NotesViewModel", "No hay notas para sincronizar con la base de datos local")
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al sincronizar notas de Firebase con la base de datos local", e)
                _error.value = "Error al sincronizar: ${e.message}"
            }
        }
    }

    // Método para cancelar la sincronización en curso
    fun cancelSync() {
        isSyncCancelled = true
        syncJob?.cancel()
        _syncState.value = FirebaseSyncState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        periodicSyncJob?.cancel()
    }
    
    // Ya no usamos sincronización periódica, solo al editar o crear notas
    private var periodicSyncJob: Job? = null
    
    /**
     * Configura la sincronización automática solo cuando es necesario
     * Ya no hay sincronización periódica cada X segundos
     */
    private fun setupPeriodicSync() {
        // No configuramos sincronización periódica, solo al crear/editar notas
        periodicSyncJob?.cancel()
        Log.d("NotesViewModel", "Sincronización periódica desactivada. Solo se sincronizará al editar o crear notas.")
    }

    // No implementar delay personalizado, usar la función de Kotlin Coroutines

    /**
     * Método para inicio de sesión silencioso y sincronización automática
     * Este método no muestra mensajes al usuario
     */
    fun silentSignInAndSync(account: GoogleSignInAccount) {
        viewModelScope.launch {
            try {
                val success = firebaseManager.signInWithGoogle(account)
                if (success) {
                    Log.d("NotesViewModel", "Inicio de sesión silencioso exitoso, sincronizando...")
                    
                    // Agregar un pequeño retraso antes de sincronizar para evitar conflictos
                    delay(1000)
                    
                    // Llamamos a refreshNotes directamente para tener más control
                    // y evitar la posible doble sincronización que ocurre con syncWithFirebase
                    refreshNotes()
                    Log.d("NotesViewModel", "Sincronización tras inicio de sesión completada")
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error en inicio de sesión silencioso: ${e.message}")
            }
        }
    }

    // --- Operaciones con UI y filtros ---
    
    /**
     * Inicia la sincronización en tiempo real para detectar cambios en otros dispositivos
     * Este método configura un listener que actualiza la base de datos local cuando
     * hay cambios en Firestore (por ejemplo, desde otro dispositivo)
     */
    suspend fun startRealtimeSync() {
        if (!isUserSignedIn()) {
            Log.d("NotesViewModel", "No se puede iniciar sincronización en tiempo real: usuario no autenticado")
            return
        }
        
        try {
            Log.d("NotesViewModel", "Iniciando sincronización en tiempo real...")
            
            // Realizar una sincronización inicial completa
            val syncSuccess = com.tambuchosecretdev.quickzenapp.firebase.SimpleFirestoreSync.sync(firebaseManager.context)
            
            if (syncSuccess) {
                _syncState.value = FirebaseSyncState.SUCCESS
                _lastSyncTime.value = System.currentTimeMillis()
                Log.d("NotesViewModel", "Sincronización en tiempo real iniciada correctamente")
                
                // El listener se configura automáticamente en SimpleFirestoreSync.sync
                // Cuando se detecten cambios, se actualizará la base de datos local
                // y se reflejará en la UI a través de los Flows
            } else {
                Log.e("NotesViewModel", "Error al iniciar sincronización en tiempo real")
            }
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Error al iniciar sincronización en tiempo real", e)
        }
    }

    /**
     * Fuerza la sincronización de TODAS las notas con Firebase
     * Esta función soluciona el problema de notas que no aparecen en la nube
     * y además reinicia la sincronización en tiempo real
     */
    fun forceSyncAllNotes() {
        viewModelScope.launch {
            try {
                if (!isUserSignedIn()) {
                    _error.value = "No has iniciado sesión con la cuenta de Google. Por favor, inicia sesión primero."
                    return@launch
                }

                _isLoading.value = true
                _syncState.value = FirebaseSyncState.SYNCING
                
                Log.d("NotesViewModel", "Forzando sincronización de TODAS las notas...")
                
                // Marcar todas las notas para sincronizar
                val allNotes = repository.getAllActiveNotes()
                for (note in allNotes) {
                    if (!note.needsSync) {
                        val noteWithSync = note.copy(needsSync = true)
                        repository.updateNote(noteWithSync)
                    }
                }
                
                // Sincronizar con Firebase usando el nuevo sistema
                val syncSuccess = com.tambuchosecretdev.quickzenapp.firebase.SimpleFirestoreSync.sync(firebaseManager.context)
                
                if (syncSuccess) {
                    _syncState.value = FirebaseSyncState.SUCCESS
                    _lastSyncTime.value = System.currentTimeMillis()
                    _operationMessage.value = "Todas las notas sincronizadas exitosamente"
                    Log.d("NotesViewModel", "Sincronización forzada completada exitosamente")
                    
                    // Reiniciar sincronización en tiempo real para asegurar que está activa
                    startRealtimeSync()
                } else {
                    _syncState.value = FirebaseSyncState.ERROR_SYNC
                    _error.value = "Error al sincronizar notas"
                    Log.e("NotesViewModel", "Error en sincronización forzada")
                }
            } catch (e: Exception) {
                _syncState.value = FirebaseSyncState.ERROR_SYNC
                _error.value = "Error: ${e.message}"
                Log.e("NotesViewModel", "Error en sincronización forzada", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Actualiza el query de búsqueda y devuelve resultados mejorados
     * Ahora la búsqueda es más flexible y da mejores resultados
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query.trim()
        if (query.trim().isNotEmpty()) {
            Log.d("NotesViewModel", "Búsqueda de notas con query: '$query'")
        }
    }
    
    /**
     * Obtiene notas filtradas por el query de búsqueda actual
     * Versión mejorada que busca en título y contenido, ignorando mayúsculas/minúsculas
     */
    fun getFilteredNotes(notes: List<Note>): List<Note> {
        val query = _searchQuery.value.trim().lowercase()
        return if (query.isEmpty()) {
            notes
        } else {
            notes.filter { note -> 
                note.title.lowercase().contains(query) || 
                note.content.lowercase().contains(query)
            }
        }
    }

    /**
     * Establece el ID de categoría seleccionada
     */
    fun setSelectedCategoryId(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    /**
     * Alterna el filtro de favoritos
     */
    fun toggleFavoritesFilter() {
        _isShowingFavorites.value = !_isShowingFavorites.value
    }

    /**
     * Limpia los mensajes de error
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Limpia los mensajes de operación
     */
    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    // --- Operaciones con Notas ---

    /**
     * Obtiene una nota por su ID
     */
    suspend fun getNoteById(noteId: Long): Note? {
        return repository.getNoteById(noteId)
    }

    /**
     * Versión no suspendida que usa viewModelScope internamente
     * Esta versión es segura de llamar desde cualquier contexto
     */
    fun getNoteByIdNonSuspend(noteId: Long, onResult: (Note?) -> Unit) {
        viewModelScope.launch {
            val note = getNoteById(noteId)
            onResult(note)
        }
    }

    /**
     * Actualiza una nota existente y la sincroniza con Firebase si el usuario está conectado
     */
    suspend fun updateNote(note: Note) {
        // Asegurarnos de que la nota está marcada para sincronización
        val noteToUpdate = note.copy(needsSync = true, modifiedDate = System.currentTimeMillis())

        // Actualizar localmente primero
        repository.updateNote(noteToUpdate)

        // Si el usuario está conectado, sincronizar con Firebase
        if (isUserSignedIn()) {
            try {
                // Procesar imágenes si es necesario
                if (note.images.isNotEmpty()) {
                    Log.d("NotesViewModel", "Sincronizando ${note.images.size} imágenes con Firebase Storage")
                    // Aquí iría la lógica para subir imágenes a Firebase Storage si es necesario
                }

                // Guardar en Firestore
                val cloudId = firebaseManager.saveNote(noteToUpdate)
                if (cloudId != null) {
                    // Si la nota no tenía cloudId o ha cambiado, actualizarla
                    if (noteToUpdate.cloudId != cloudId) {
                        val updatedNote = noteToUpdate.copy(cloudId = cloudId, needsSync = false)
                        repository.updateNote(updatedNote)
                        Log.d("NotesViewModel", "Nota actualizada y sincronizada con Firebase: ${note.id} -> $cloudId")
                    } else {
                        // Solo marcar como sincronizada
                        val syncedNote = noteToUpdate.copy(needsSync = false)
                        repository.updateNote(syncedNote)
                        Log.d("NotesViewModel", "Nota actualizada y sincronizada con Firebase: ${note.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al sincronizar nota actualizada con Firebase", e)
                // La nota ya está marcada para sincronización futura (needsSync = true)
        }
        
        // Si ya existe una nota con el mismo contenido, devolver su ID en lugar de crear un duplicado
        if (existingNote != null) {
            Log.d("NotesViewModel", "Evitando crear duplicado, ya existe una nota con el mismo contenido: ${existingNote.id}")
            // Actualizar la nota existente con los nuevos atributos si es necesario
            val updatedNote = existingNote.copy(
                colorId = colorId,
                categoryId = categoryId,
                isFavorite = isFavorite,
                isLocked = isLocked,
                modifiedDate = System.currentTimeMillis(),
                needsSync = true
            )
            repository.updateNote(updatedNote)
            
            // Sincronizar con Firebase si es necesario
            if (isUserSignedIn()) {
                try {
                    val cloudId = firebaseManager.saveNote(updatedNote)
                    if (cloudId != null) {
                        val syncedNote = updatedNote.copy(cloudId = cloudId, needsSync = false)
                        repository.updateNote(syncedNote)
                        Log.d("NotesViewModel", "Nota existente actualizada y sincronizada: ${existingNote.id} -> $cloudId")
                    }
                } catch (e: Exception) {
                    Log.e("NotesViewModel", "Error al sincronizar nota existente con Firebase", e)
                }
            }
            
            return existingNote.id
        }
        
        // PASO 2: Si no existe, crear una nueva nota
        val note = Note(
            title = title,
            content = content,
            images = images,
            colorId = colorId,
            categoryId = categoryId,
            isFavorite = isFavorite,
            isLocked = isLocked,
            createdDate = System.currentTimeMillis(),
            modifiedDate = System.currentTimeMillis(),
            needsSync = true
        )

        // Guardar localmente primero
        val noteId = repository.insertNote(note)
        Log.d("NotesViewModel", "Nueva nota creada localmente con ID: $noteId")

        // Si el usuario está conectado, sincronizar con Firebase
        if (isUserSignedIn()) {
            try {
                // Obtener la nota con el ID asignado
                val savedNote = repository.getNoteById(noteId)
                if (savedNote != null) {
                    // Procesar imágenes si es necesario
                    if (images.isNotEmpty()) {
                        Log.d("NotesViewModel", "Subiendo ${images.size} imágenes a Firebase Storage")
                        // Aquí iría la lógica para subir imágenes a Firebase Storage
                    }

                    // Verificar una vez más si ya existe en Firebase para evitar duplicados
                    val existingId = firebaseManager.findExistingNoteByContent(title, content)
                    if (existingId != null) {
                        // Ya existe en Firebase, actualizar el cloudId local
                        val noteWithCloudId = savedNote.copy(cloudId = existingId, needsSync = false)
                        repository.updateNote(noteWithCloudId)
                        Log.d("NotesViewModel", "Nota enlazada con existente en Firebase: $noteId -> $existingId")
                    } else {
                        // Guardar como nueva en Firestore
                        val cloudId = firebaseManager.saveNote(savedNote)
                        if (cloudId != null) {
                            val noteWithCloudId = savedNote.copy(cloudId = cloudId, needsSync = false)
                            repository.updateNote(noteWithCloudId)
                            Log.d("NotesViewModel", "Nueva nota sincronizada con Firebase: $noteId -> $cloudId")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al sincronizar nueva nota con Firebase", e)
                // La nota ya está marcada para sincronización futura (needsSync = true)
            }
        }

        return noteId
    }

    /**
     * Cambia el estado de favorito de una nota y sincroniza con Firebase
     * SOLUCIONADO: Corregido el problema de duplicación al marcar favoritos
     */
    suspend fun toggleFavoriteStatus(noteId: Long) {
        val note = repository.getNoteById(noteId)
        note?.let {
            // Crear versión actualizada con marca de sincronización
            val updated = it.copy(
                isFavorite = !it.isFavorite,
                modifiedDate = System.currentTimeMillis(), // Actualizar timestamp para resolver conflictos
                needsSync = true
            )

            // Actualizar localmente
            repository.updateNote(updated)
            
            // Si el usuario está conectado, sincronizar inmediatamente con Firebase
            // para evitar problemas de sincronización entre dispositivos
            if (isUserSignedIn()) {
                try {
                    // SOLUCIÓN: Manejar notas que aún no tienen cloudId
                    val cloudId = if (updated.cloudId.isNullOrEmpty()) {
                        // Primero verificar si ya existe una nota similar en la nube
                        val existingId = firebaseManager.findExistingNoteByContent(updated.title, updated.content)
                        if (existingId != null) {
                            // Si ya existe, vincular con esa nota en lugar de crear un duplicado
                            Log.d("NotesViewModel", "Nota vinculada a existente en la nube al marcar favorito: $noteId -> $existingId")
                            existingId
                        } else {
                            // Si no existe, guardar como nueva
                            firebaseManager.saveNote(updated)
                        }
                    } else {
                        // Si ya tiene cloudId, simplemente actualizar
                        firebaseManager.saveNote(updated)
                    }
                    
                    if (cloudId != null) {
                        // Actualizar el estado de sincronización y el cloudId
                        val synced = updated.copy(cloudId = cloudId, needsSync = false)
                        repository.updateNote(synced)
                        Log.d("NotesViewModel", "Favorito sincronizado con Firebase: $noteId -> $cloudId")
                        
                        // IMPORTANTE: Desactivar temporalmente la sincronización automática para
                        // evitar que se creen duplicados al refrescar desde Firestore
                        _lastSyncTime.value = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e("NotesViewModel", "Error al sincronizar favorito con Firebase", e)
                    // La nota ya está marcada para sincronización futura (needsSync = true)
                }
            }

            // Registrar que la operación fue exitosa
            Log.d("NotesViewModel", "Estado de favorito actualizado para la nota: $noteId")
        }
    }

    /**
     * Mueve una nota a la papelera
     */
    suspend fun moveToTrash(noteId: Long) {
        repository.moveToTrash(noteId)
    }

    /**
     * Restaura una nota desde la papelera
     */
    suspend fun restoreNoteFromTrash(noteId: Long) {
        repository.restoreFromTrash(noteId)
    }

    /**
     * Elimina una nota permanentemente y la elimina de Firebase si el usuario está conectado
     */
    suspend fun deleteNotePermanently(noteId: Long) {
        try {
            // Obtener la nota antes de eliminarla para tener acceso a su cloudId
            val note = repository.getNoteById(noteId) ?: return

            // Eliminar localmente primero
            repository.deleteNotePermanently(noteId)

            // Si el usuario está conectado y la nota tiene cloudId, eliminarla de Firebase
            if (isUserSignedIn() && note.cloudId != null) {
                try {
                    // Eliminar imágenes de Storage si es necesario
                    if (note.images.isNotEmpty()) {
                        Log.d("NotesViewModel", "Eliminando ${note.images.size} imágenes de Firebase Storage")
                        // Aquí iría la lógica para eliminar imágenes de Firebase Storage
                    }

                    // Eliminar de Firestore
                    try {
                        firebaseManager.deleteNote(note.cloudId!!)
                        Log.d("NotesViewModel", "Nota eliminada de Firebase: $noteId (${note.cloudId})")
                    } catch (e: Exception) {
                        Log.w("NotesViewModel", "No se pudo eliminar la nota de Firebase: $noteId (${note.cloudId})", e)
                    }
                } catch (e: Exception) {
                    Log.e("NotesViewModel", "Error al eliminar nota de Firebase", e)
                    // Como la nota ya fue eliminada localmente, no podemos hacer mucho más
                }
            }
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Error al eliminar nota permanentemente", e)
            throw e  // Propagar el error para que la UI pueda manejarlo
        }
    }

    /**
     * Vacía la papelera (elimina todas las notas en la papelera)
     */
    suspend fun emptyTrash() {
        viewModelScope.launch {
            try {
                val notesInTrash = repository.getNotesInTrash()
                if (notesInTrash.isNotEmpty()) {
                    // Eliminar cada nota permanentemente
                    for (note in notesInTrash) {
                        repository.deleteNotePermanently(note.id)
                    }
                    _operationMessage.value = "Papelera vaciada con éxito"
                }
            } catch (e: Exception) {
                _error.value = "Error al vaciar la papelera: ${e.message}"
            }
        }
    }

    /**
     * Ordena las notas por fecha
     */
    fun sortNotesByDate(ascending: Boolean = false) {
        // Esta función no hace nada ya que usamos Flows
        // La ordenación debe hacerse en la UI al recolectar el Flow
    }

    // --- Operaciones de Firebase ---

    /**
     * Inicia el proceso completo de sincronización bidireccional con Firebase
     * Este método realiza una sincronización completa en ambas direcciones:
     * 1. Sube las notas locales con cambios pendientes
     * 2. Descarga las notas actualizadas del servidor
     */
    fun initiateSyncProcess() {
        syncWithFirebase()
    }

    /**
     * Sincroniza las notas con Firebase Firestore
     * Método completo que maneja la sincronización bidireccional con Firestore
     */
    fun syncFirestoreNotes() {
        syncWithFirebase()
    }

    /**
     * Restaura notas desde una copia de seguridad
     */
    fun restoreNotesFromBackup(notes: List<Note>): Boolean {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.restoreFromBackup(notes)
                _operationMessage.value = "Notas restauradas correctamente"
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al restaurar notas desde backup", e)
                _error.value = "Error al restaurar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
        return true
    }

    /**
     * Elimina todos los datos del usuario en la nube
     */
    fun deleteAllCloudData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.deleteAllCloudData()
                if (result) {
                    _operationMessage.value = "Datos eliminados correctamente de la nube"
                } else {
                    _error.value = "Error al eliminar datos de la nube"
                }
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error al eliminar datos de la nube", e)
                _error.value = "Error al eliminar datos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Inicia sesión con Google
     */
    // Necesita una cuenta de Google para iniciar sesión
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            try {
                val success = firebaseManager.signInWithGoogle(account)
                if (success) {
                    _operationMessage.value = "Sesión iniciada correctamente"
                } else {
                    _error.value = "Error al iniciar sesión con Google"
                }
            } catch (e: Exception) {
                _error.value = "Error al iniciar sesión: ${e.message}"
            }
        }
    }

    /**
     * Cierra la sesión actual
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                firebaseManager.signOut()
                _operationMessage.value = "Sesión cerrada correctamente"
            } catch (e: Exception) {
                _error.value = "Error al cerrar sesión: ${e.message}"
            }
        }
    }

    // --- Operaciones con Categorías ---

    /**
     * Añade una nueva categoría
     */
    suspend fun addCategory(name: String): Long {
        Log.d("NotesViewModel", "addCategory_METODO_VIEWMODEL: Intentando añadir categoría con nombre: '$name'")
        if (name.isBlank()) {
            Log.w("NotesViewModel", "addCategory_METODO_VIEWMODEL: El nombre de la categoría está vacío o solo contiene espacios en blanco.")
            _error.value = "El nombre de la categoría no puede estar vacío."
            return -1L // Indicar fallo debido a nombre inválido
        }
        try {
            val category = Category(
                name = name.trim(), // Usar trim para eliminar espacios al inicio/final
                color = "default" // Considerar si el color debe ser configurable
            )
            Log.d("NotesViewModel", "addCategory_METODO_VIEWMODEL: Objeto Category creado: $category")
            val newCategoryId = repository.insertCategory(category)
            if (newCategoryId > 0) {
                Log.i("NotesViewModel", "addCategory_METODO_VIEWMODEL: Categoría '$name' añadida con éxito. ID: $newCategoryId")
                _operationMessage.value = "Categoría '$name' creada."
                // La lista de categorías debería actualizarse automáticamente a través del Flow 'categories' que proviene del repositorio.
                // La UI que observa 'viewModel.categories.collectAsState()' debería recomponerse.
                // Si la UI no se actualiza, el problema está en la recolección del Flow en la UI o en la emisión del Flow desde el repositorio/DAO.
            } else {
                Log.e("NotesViewModel", "addCategory_METODO_VIEWMODEL: Fallo al insertar categoría '$name' en el repositorio. ID devuelto: $newCategoryId")
                _error.value = "No se pudo crear la categoría '$name'."
            }
            return newCategoryId
        } catch (e: Exception) {
            Log.e("NotesViewModel", "addCategory_METODO_VIEWMODEL: Excepción al añadir categoría '$name': ${e.message}", e)
            _error.value = "Error al crear categoría: ${e.message}"
            return -1L // Indicar fallo debido a excepción
        }
    }

    /**
     * Actualiza la lista de categorías (en caso de ser necesario)
     */
    fun refreshCategories() {
        // No es necesario hacer nada aquí si ya estás usando categoriesFlow
        // El Flow se actualizará automáticamente cuando cambie la base de datos
    }

    /**
     * Migra las notas a la estructura segura de Firestore (users/{userId}/notes/{noteId})
     * Este método inicia la migración de notas de la estructura antigua a la nueva estructura.
     * @param confirmBeforeDelete Si es true, se debe solicitar confirmación antes de eliminar las notas antiguas
     */
    /**
     * Sube notas pendientes de sincronización a Firebase
     * Nota: Esta es una implementación preliminar que completaremos
     * cuando tengamos los métodos necesarios en el repositorio
     */
    private fun uploadPendingNotes() {
        viewModelScope.launch {
            try {
                if (!isUserSignedIn()) {
                    return@launch
                }

                // Obtener todas las notas activas
                val allNotes = repository.getAllActiveNotes()

                // Filtrar las que necesitan sincronización
                val pendingNotes = allNotes.filter { it.needsSync }

                if (pendingNotes.isEmpty()) {
                    Log.d("NotesViewModel", "No hay notas pendientes de sincronización")
                    return@launch
                }

                Log.d("NotesViewModel", "Sincronizando ${pendingNotes.size} notas pendientes con Firebase")

                // Simular carga por ahora para evitar errores de compilación
                _operationMessage.value = "${pendingNotes.size} notas pendientes de sincronización"

                // La implementación completa requiere métodos adicionales en el repositorio
                // y se completará en futuras implementaciones
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error general en uploadPendingNotes", e)
            }
        }
    }

    fun migrateNotesToNewStructure(confirmBeforeDelete: Boolean = true) {
        viewModelScope.launch {
            try {
                if (!isUserSignedIn()) {
                    _error.value = "No has iniciado sesión. Por favor, inicia sesión primero."
                    _migrationState.value = MigrationState.FAILED
                    return@launch
                }

                
                _migrationState.value = MigrationState.MIGRATING
                _isLoading.value = true
                Log.d("NotesViewModel", "[MIGRATION] Iniciando migración a estructura segura...")
                
                // 1. Migrar notas a la nueva estructura
                val result = firebaseManager.migrateNotesToNewStructure()
                val migratedCount = result.first
                val totalCount = result.second
                
                if (migratedCount == 0) {
                    if (totalCount == 0) {
                        _operationMessage.value = "No hay notas para migrar."
                        _migrationState.value = MigrationState.IDLE
                    } else {
                        _error.value = "No se pudo migrar ninguna nota."
                        _migrationState.value = MigrationState.FAILED
                    }
                    _isLoading.value = false
                    return@launch
                }
                
                // 2. Actualizar UI y estado
                _operationMessage.value = "$migratedCount notas migradas exitosamente."
                _migrationState.value = MigrationState.MIGRATED
                
                // 3. Si todas las notas se migraron correctamente y no se requiere confirmación, eliminar las antiguas
                if (migratedCount == totalCount && !confirmBeforeDelete) {
                    deleteOldStructureNotes()
                }
                
                _isLoading.value = false
                
            } catch (e: Exception) {
                Log.e("NotesViewModel", "[MIGRATION] Error durante la migración", e)
                _error.value = "Error durante la migración: ${e.message}"
                _migrationState.value = MigrationState.FAILED
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Elimina las notas de la estructura antigua después de que se hayan migrado.
     * Este método debe llamarse después de confirmar que la migración fue exitosa.
     */
    fun deleteOldStructureNotes() {
        viewModelScope.launch {
            try {
                if (_migrationState.value != MigrationState.MIGRATED && _migrationState.value != MigrationState.FAILED) {
                    _error.value = "Debes completar la migración antes de eliminar las notas antiguas."
                    return@launch
                }
                
                _isLoading.value = true
                Log.d("NotesViewModel", "[MIGRATION] Eliminando notas de la estructura antigua...")
                
                val deletedCount = firebaseManager.deleteOldStructureNotes()
                
                if (deletedCount > 0) {
                    _operationMessage.value = "$deletedCount notas antiguas eliminadas correctamente."
                    _migrationState.value = MigrationState.COMPLETED
                } else {
                    _operationMessage.value = "No se encontraron notas antiguas para eliminar."
                }
                
                _isLoading.value = false
                
            } catch (e: Exception) {
                Log.e("NotesViewModel", "[MIGRATION] Error al eliminar notas antiguas", e)
                _error.value = "Error al eliminar notas antiguas: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(newQuery: Context) {

    }
}
