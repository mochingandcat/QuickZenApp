package com.tambuchosecretdev.quickzenapp.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.tambuchosecretdev.quickzenapp.dao.CategoryDao
import com.tambuchosecretdev.quickzenapp.dao.NoteDao
import com.tambuchosecretdev.quickzenapp.data.AppDatabase
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import com.tambuchosecretdev.quickzenapp.model.toLocalNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * Estados para el proceso de migración de datos
 */
enum class MigrationState {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}

private val Any.id: String
    get() = this.toString() // Devuelve una representación en String del objeto

/**
 * Repositorio que maneja las operaciones de notas y categorías.
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val firebaseManager: FirebaseManager,
    private val externalScope: CoroutineScope // Scope externo para coroutines
) {

    // --- Flows para la UI ---
    val activeNotesFlow: Flow<List<Note>> = noteDao.getActiveNotesFlow()
    val notesInTrashFlow: Flow<List<Note>> = noteDao.getNotesInTrashFlow()
    val categoriesFlow: Flow<List<Category>> = categoryDao.getAllCategories()
    val favoriteNotesFlow: Flow<List<Note>> = noteDao.getFavoriteNotesFlow()

    // --- Operaciones con Notas ---
    suspend fun getNoteById(noteId: Long): Note? {
        try {
            Log.d("NoteRepository", "Fetching note by id: $noteId")
            return noteDao.getNoteById(noteId)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error fetching note by id: $noteId", e)
            throw e // Rethrow the exception for further handling
        }
    }
    
    /**
     * Versión no suspendida de getNoteById para usar desde funciones que no son corrutinas
     */
    fun getNoteByIdNonSuspend(noteId: Long): Note? {
        return try {
            Log.d("NoteRepository", "Fetching note by id (non-suspend): $noteId")
            // Usamos runBlocking para ejecutar la corrutina de manera bloqueante
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                noteDao.getNoteById(noteId)
            }
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error fetching note by id (non-suspend): $noteId", e)
            null
        }
    }

    suspend fun insertNote(note: Note): Long {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "[SAVE PERF] Inserting note: ${note.title}")
                var startTimeMillis: Long

                val noteToInsert=
                    note.copy(needsSync=true, modifiedDate=System.currentTimeMillis())

                Log.d("NoteRepository", "[SAVE PERF] Attempting Room insert for: ${note.title}")
                startTimeMillis=System.currentTimeMillis()
                val localId=noteDao.insertNote(noteToInsert)
                Log.d(
                    "NoteRepository",
                    "[SAVE PERF] Room insert for ${note.title} took: ${System.currentTimeMillis() - startTimeMillis}ms. Local ID: $localId"
                )

                if (localId > 0) {
                    val noteWithLocalId=note.copy(id=localId)

                    Log.d(
                        "NoteRepository",
                        "[SAVE PERF] Attempting Firebase save for: ${noteWithLocalId.title}, Local ID: ${noteWithLocalId.id}"
                    )
                    startTimeMillis=System.currentTimeMillis()
                    val cloudId=firebaseManager.saveNote(noteWithLocalId)
                    val firebaseSaveDuration=System.currentTimeMillis() - startTimeMillis
                    Log.d(
                        "NoteRepository",
                        "[SAVE PERF] Firebase save for ${noteWithLocalId.title} took: ${firebaseSaveDuration}ms. Cloud ID: $cloudId"
                    )

                    if (cloudId != null) {
                        Log.d(
                            "NoteRepository",
                            "[SAVE PERF] Attempting Room sync status update for: ${noteWithLocalId.title}, Local ID: $localId, Cloud ID: $cloudId"
                        )
                        startTimeMillis=System.currentTimeMillis()
                        noteDao.updateNoteSyncStatus(localId, cloudId)
                        Log.d(
                            "NoteRepository",
                            "[SAVE PERF] Room sync status update for ${noteWithLocalId.title} took: ${System.currentTimeMillis() - startTimeMillis}ms"
                        )

                        Log.d(
                            "NoteRepository",
                            "Note ${note.title} synced with Firebase, cloudId: $cloudId"
                        )
                    } else {
                        Log.w(
                            "NoteRepository",
                            "Note ${note.title} inserted locally but failed to sync to Firebase. Firebase save duration: ${firebaseSaveDuration}ms"
                        )
                    }
                }
                localId
            } catch (e: Exception) {
                Log.e("NoteRepository", "[SAVE PERF] Error inserting note: ${note.title}", e)
                throw e
            }
        }
    }

    suspend fun updateNote(note: Note) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Updating note: ${note.id}")
                
                // Obtener la nota actual para preservar los campos que no se están actualizando
                val currentNote = noteDao.getNoteById(note.id)
                
                // Crear una copia de la nota con los campos actualizados
                val noteToUpdate = note.copy(
                    needsSync = true,
                    modifiedDate = System.currentTimeMillis(),
                    // Preservar el cloudId si no se está actualizando
                    cloudId = note.cloudId ?: currentNote?.cloudId,
                    // Preservar el userId si no se está actualizando
                    userId = note.userId ?: currentNote?.userId ?: java.util.UUID.randomUUID().toString(),
                    // Asegurarse de que el colorId se actualice correctamente
                    colorId = note.colorId.ifEmpty { currentNote?.colorId ?: NoteAppColors.DEFAULT_COLOR_ID }
                )
                
                // Actualizar la nota en la base de datos local
                noteDao.updateNote(noteToUpdate)
                
                // Intentar sincronizar con Firebase
                try {
                    val cloudId = firebaseManager.saveNote(noteToUpdate)
                    if (cloudId != null) {
                        if (noteToUpdate.cloudId == null) {
                            noteDao.updateNoteSyncStatus(noteToUpdate.id, cloudId)
                        }
                        Log.d("NoteRepository", "Note ${note.id} updated and synced with Firebase.")
                    } else {
                        Log.w(
                            "NoteRepository",
                            "Note ${note.id} updated locally but failed to sync to Firebase."
                        )
                    }
                } catch (firebaseError: Exception) {
                    Log.e("NoteRepository", "Error syncing note ${note.id} with Firebase", firebaseError)
                    // No lanzamos la excepción para que la actualización local se mantenga
                }
                
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error updating note: ${note.id}", e)
                throw e
            }
        }
    }

    suspend fun moveToTrash(noteId: Long) {
        try {
            Log.d("NoteRepository", "Moviendo nota a papelera: $noteId")
            noteDao.moveToTrash(noteId)
            Log.d("NoteRepository", "Nota movida a papelera exitosamente: $noteId")
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error al mover nota a papelera: $noteId", e)
            throw e
        }
    }

    suspend fun restoreFromTrash(noteId: Long) {
        try {
            Log.d("NoteRepository", "Restaurando nota de papelera: $noteId")
            noteDao.restoreFromTrash(noteId)
            Log.d("NoteRepository", "Nota restaurada exitosamente: $noteId")
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error al restaurar nota de papelera: $noteId", e)
            throw e
        }
    }

    suspend fun deleteNotePermanently(noteId: Long) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Deleting note permanently: $noteId")
                val noteToDelete=noteDao.getNoteById(noteId)
                noteDao.deleteNotePermanently(noteId)

                noteToDelete?.cloudId?.let {
                    try {
                        firebaseManager.deleteNote(it)
                        Log.d(
                            "NoteRepository",
                            "Note $noteId (cloudId: $it) deleted from Firebase."
                        )
                    } catch (e: Exception) {
                        Log.w(
                            "NoteRepository",
                            "Note $noteId (cloudId: $it) deleted locally but failed to delete from Firebase.",
                            e
                        )
                    }
                }
                Log.d("NoteRepository", "Note $noteId deleted permanently successfully.")
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error deleting note permanently: $noteId", e)
                throw e
            }
        }
    }

    suspend fun deleteNote(note: Note) {
        deleteNotePermanently(note.id)
    }

    suspend fun emptyTrash() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Vaciando papelera")
                val notesInTrash=noteDao.getNotesInTrash()
                noteDao.emptyTrash()
                for (note in notesInTrash) {
                    note.cloudId?.let { cloudId ->
                        firebaseManager.deleteNote(cloudId)
                    }
                }
                Log.d(
                    "NoteRepository",
                    "Papelera vaciada exitosamente y sincronizada con Firebase."
                )
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al vaciar papelera", e)
                throw e
            }
        }
    }

    suspend fun getNotesInTrash(): List<Note> {
        try {
            Log.d("NoteRepository", "Obteniendo notas en papelera directamente")
            val notes=noteDao.getNotesInTrash()
            Log.d("NoteRepository", "Obtenidas ${notes.size} notas en papelera")
            return notes
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error al obtener notas en papelera directamente", e)
            throw e
        }
    }

    suspend fun updateNoteFavoriteStatus(noteId: Long, isFavorite: Boolean) {
        try {
            Log.d("NoteRepository", "Updating favorite status for note: $noteId to $isFavorite")
            noteDao.updateFavoriteStatus(noteId, isFavorite)
            val note=noteDao.getNoteById(noteId)
            if (note != null && note.cloudId != null) firebaseManager.saveNote(note)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error updating favorite status for note: $noteId", e)
            throw e
        }
    }

    suspend fun updateNoteLockStatus(noteId: Long, isLocked: Boolean) {
        try {
            Log.d("NoteRepository", "Updating lock status for note: $noteId to $isLocked")
            noteDao.updateLockStatus(noteId, isLocked)
            val note=noteDao.getNoteById(noteId)
            if (note != null && note.cloudId != null) firebaseManager.saveNote(note)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error updating lock status for note: $noteId", e)
            throw e
        }
    }

    // --- Operaciones con Categorías ---
    suspend fun getAllCategories(): List<Category> {
        try {
            Log.d("NoteRepository", "Getting all categories list")
            return categoryDao.getAllCategoriesList()
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error getting all categories list", e)
            throw e
        }
    }

    suspend fun getCategoryById(id: Long): Category? {
        try {
            Log.d("NoteRepository", "Fetching category by id: $id")
            return categoryDao.getCategoryById(id)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error fetching category by id: $id", e)
            throw e
        }
    }

    suspend fun insertCategory(category: Category): Long {
        try {
            Log.d("NoteRepository", "Inserting category: ${category.name}")
            val categoryToInsert=category.copy(needsSync=true)
            return categoryDao.insert(categoryToInsert)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error inserting category: ${category.name}", e)
            throw e
        }
    }

    suspend fun updateCategory(category: Category) {
        try {
            Log.d("NoteRepository", "Updating category: ${category.id}")
            val categoryToUpdate=category.copy(needsSync=true)
            categoryDao.update(categoryToUpdate)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error updating category: ${category.id}", e)
            throw e
        }
    }

    suspend fun deleteCategory(category: Category) {
        try {
            Log.d("NoteRepository", "Deleting category: ${category.id}")
            categoryDao.delete(category)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error deleting category: ${category.id}", e)
            throw e
        }
    }

    // --- Operaciones de Sincronización (directas al DAO que ya son suspend) ---

    suspend fun getNotesForSync(): List<Note> {
        try {
            Log.d("NoteRepository", "Getting notes for sync")
            return noteDao.getNotesForSync()
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error getting notes for sync", e)
            throw e
        }
    }

    /**
     * Obtiene todas las notas que tienen un cloudId asignado
     * Útil para verificar notas eliminadas en la nube
     */
    suspend fun getNotesWithCloudId(): List<Note> {
        try {
            Log.d("NoteRepository", "Getting notes with cloudId")
            return noteDao.getNotesWithCloudId()
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error getting notes with cloudId", e)
            return emptyList()
        }
    }

    suspend fun updateNoteSyncStatus(noteId: Long, cloudId: String) {
        try {
            Log.d("NoteRepository", "Updating note sync status for note: $noteId with cloudId: $cloudId")
            withContext(Dispatchers.IO) {
                val note = noteDao.getNoteById(noteId)
                if (note != null) {
                    val updatedNote = note.copy(
                        cloudId = cloudId,
                        needsSync = false  // Marcar como sincronizada para evitar duplicados
                    )
                    noteDao.update(updatedNote)
                    Log.d("NoteRepository", "Updated note sync status: $noteId -> cloudId=$cloudId, needsSync=false")
                } else {
                    Log.e("NoteRepository", "Could not find note with ID: $noteId")
                }
            }
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error updating sync status for note: $noteId", e)
            throw e
        }
    }

    /**
     * Actualiza el cloudId de una nota y la marca como ya sincronizada (needsSync = false)
     * para evitar que se vuelva a sincronizar en el próximo ciclo de sincronización
     */
    suspend fun updateNoteAfterSync(noteId: Long, cloudId: String) {
        try {
            Log.d("NoteRepository", "Actualizando estado de sincronización completo para nota: $noteId")
            withContext(Dispatchers.IO) {
                // Obtener la nota actual
                val note = noteDao.getNoteById(noteId)
                if (note != null) {
                    // Actualizar la nota con el nuevo cloudId y marcarla como sincronizada
                    val updatedNote = note.copy(
                        cloudId = cloudId,
                        needsSync = false  // Marcar como ya sincronizada
                    )
                    noteDao.update(updatedNote)
                    Log.d("NoteRepository", "Nota $noteId actualizada con cloudId=$cloudId y marcada como sincronizada")
                } else {
                    Log.e("NoteRepository", "No se encontró la nota con ID: $noteId")
                }
            }
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error al actualizar estado de sincronización para nota: $noteId", e)
            throw e
        }
    }

    suspend fun markAllCategoriesForSync(): Int {
        try {
            Log.d("NoteRepository", "Marking all categories for sync")
            return categoryDao.markAllCategoriesForSync()
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error marking all categories for sync", e)
            throw e
        }
    }

    suspend fun getCategoriesForSync(): List<Category> {
        try {
            Log.d("NoteRepository", "Getting categories for sync")
            return categoryDao.getCategoriesNeedingSync()
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error getting categories for sync", e)
            throw e
        }
    }

    suspend fun updateCategorySyncStatus(categoryId: Long, cloudId: String?) {
        try {
            Log.d("NoteRepository", "Updating category sync status for category: $categoryId")
            categoryDao.updateCategorySyncStatus(categoryId, cloudId)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error updating sync status for category: $categoryId", e)
            throw e
        }
    }

    /**
     * Marca todas las notas existentes para sincronización
     */
    suspend fun markAllNotesForSync() {
        withContext(Dispatchers.IO) {
            try {
                // Obtener todas las notas activas (no en papelera)
                val activeNotes = noteDao.getActiveNotes()
                Log.d("NoteRepository", "Marcando ${activeNotes.size} notas para sincronización")
                
                // Actualizar cada nota para marcarla para sincronización
                for (note in activeNotes) {
                    noteDao.updateNeedsSyncStatus(note.id, true)
                    Log.d("NoteRepository", "Nota marcada para sincronización: id=${note.id}, título='${note.title}'")
                }
                
                // También marcar todas las notas con una consulta general
                // La función markAllNotesForSync() es suspend, por lo que debe ser llamada desde un contexto suspend
                try {
                    val count = noteDao.markAllNotesForSync()
                    Log.d("NoteRepository", "$count notas marcadas para sincronización con consulta general")
                } catch (e: Exception) {
                    Log.e("NoteRepository", "Error al marcar todas las notas para sincronización: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al marcar notas para sincronización: ${e.message}")
                throw e
            }
        }
    }

    // Función mejorada para sincronización bidireccional con Firebase
    suspend fun syncAllWithFirebase() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Iniciando sincronización bidireccional completa...")
                
                // 1. Asegurar que el usuario está autenticado
                if (!firebaseManager.isUserSignedIn()) {
                    Log.e("NoteRepository", "Usuario no autenticado. Abortando sincronización.")
                    throw Exception("Usuario no autenticado. Inicie sesión para sincronizar.")
                }
                
                // 2. Verificar conectividad
                if (!firebaseManager.isConnected()) {
                    Log.e("NoteRepository", "Sin conexión a internet. Abortando sincronización.")
                    throw Exception("Sin conexión a internet.")
                }
                
                // Primero: Descargar notas desde el servidor
                Log.d("NoteRepository", "FASE 1: Descargando notas desde el servidor...")
                val downloadSuccess = downloadAllNotesFromServer()
                Log.d("NoteRepository", "Descarga de notas completada: $downloadSuccess")
                
                // 3. Obtener solo las notas que necesitan sincronización
                // Después de la descarga, ahora subimos las notas locales que necesitan sincronización
                Log.d("NoteRepository", "FASE 2: Subiendo notas locales al servidor...")
                val notesToSync = noteDao.getNotesNeedingSync()
                Log.d("NoteRepository", "Total de notas que necesitan sincronización: ${notesToSync.size}")
                
                if (notesToSync.isEmpty()) {
                    Log.d("NoteRepository", "No hay notas que necesiten sincronización con el servidor.")
                    saveLastSyncTime(System.currentTimeMillis())
                    return@withContext
                }
                
                // 4. Sincronizar cada nota individualmente para mejor control
                val syncResults = mutableMapOf<Long, String>()
                val collection = firebaseManager.firestore.collection("QUICKZENNEW")
                // Obtener el ID de usuario y asegurarse de que no sea nulo
                val userId = firebaseManager.auth.currentUser?.uid
                if (userId.isNullOrEmpty()) {
                    Log.e("NoteRepository", "ID de usuario nulo o vacío, abortando sincronización")
                    throw Exception("ID de usuario no disponible")
                }
                
                // MEJORA: Crear un mapa de IDs existentes tanto por localId como por documentId
                // Esto garantiza que no creemos documentos duplicados en Firestore
                val existingNotesByLocalId = mutableMapOf<Long, String>() // Map de localId a cloudId
                val existingDocuments = mutableMapOf<String, String>() // Map de cloudId a cloudId (para verificar existencia)
                
                try {
                    Log.d("NoteRepository", "Buscando notas existentes del usuario en Firestore...")
                    val querySnapshot = collection
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    
                    for (document in querySnapshot.documents) {
                        // Siempre registrar el documento por su ID
                        val cloudId = document.id
                        existingDocuments[cloudId] = cloudId
                        
                        // También registrar la relación localId -> cloudId
                        val localIdStr = document.getString("localId")
                        if (!localIdStr.isNullOrEmpty()) {
                            try {
                                val localId = localIdStr.toLong()
                                existingNotesByLocalId[localId] = document.id
                                Log.d("NoteRepository", "Nota existente en la nube: localId=$localId, cloudId=${document.id}")
                            } catch (e: NumberFormatException) {
                                Log.e("NoteRepository", "Error al convertir localId a Long: $localIdStr")
                            }
                        }
                    }
                    Log.d("NoteRepository", "Encontradas ${existingNotesByLocalId.size} notas existentes en la nube")
                } catch (e: Exception) {
                    Log.e("NoteRepository", "Error al consultar notas existentes: ${e.message}")
                }
                
                // Sincronizar cada nota que necesita sincronización
                for (note in notesToSync) {
                    try {
                        // LÓGICA MEJORADA: Determinación del cloudId efectivo
                        var effectiveCloudId: String? = null
                        
                        // 1. Si la nota ya tiene un cloudId válido y ese documento existe, usarlo
                        if (!note.cloudId.isNullOrEmpty() && existingDocuments.containsKey(note.cloudId)) {
                            effectiveCloudId = note.cloudId
                            Log.d("NoteRepository", "Usando cloudId existente de la nota local: $effectiveCloudId")
                        } 
                        // 2. Si no tiene cloudId válido, buscar si existe un documento con su localId
                        else if (existingNotesByLocalId.containsKey(note.id)) {
                            effectiveCloudId = existingNotesByLocalId[note.id]
                            Log.d("NoteRepository", "Encontrado documento existente por localId: $effectiveCloudId")
                        }
                        // 3. Si tiene cloudId pero no se encontró el documento, verificar si fue renombrado
                        else if (!note.cloudId.isNullOrEmpty()) {
                            Log.w("NoteRepository", "La nota tiene cloudId ${note.cloudId} pero no se encontró en Firestore")
                            // No asignar effectiveCloudId, se creará uno nuevo
                        }
                        
                        Log.d("NoteRepository", "Sincronizando nota: id=${note.id}, título='${note.title}', efectiveCloudId=$effectiveCloudId")
                        
                        // Crear mapa con todos los datos necesarios
                        val noteData = hashMapOf(
                            "title" to note.title,
                            "content" to note.content,
                            "userId" to userId,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "localId" to note.id.toString(),
                            "createdDate" to note.createdDate,
                            "modifiedDate" to System.currentTimeMillis(),
                            "colorId" to note.colorId,
                            "isFavorite" to note.isFavorite,
                            "isSync" to true, // Marca explícitamente como sincronizada
                            "isLocked" to note.isLocked,
                            "isInTrash" to note.isInTrash
                        )
                        
                        // Agregar campos opcionales solo si no son nulos
                        if (note.categoryId != null) {
                            noteData["categoryId"] = note.categoryId.toString()
                        }
                        
                        if (note.reminderDateTime != null) {
                            noteData["reminderDateTime"] = note.reminderDateTime
                        }
                        
                        if (note.labelIds.isNotEmpty()) {
                            noteData["labelIds"] = note.labelIds
                        }
                        
                        if (note.images.isNotEmpty()) {
                            noteData["images"] = note.images
                        }
                        
                        // Decidir si actualizar o crear
                        if (effectiveCloudId != null) {
                            // Actualizar documento existente
                            Log.d("NoteRepository", "Actualizando nota existente con cloudId: $effectiveCloudId")
                            val existingDocRef = collection.document(effectiveCloudId)
                            existingDocRef.set(noteData).await()
                            
                            // Registrar éxito al actualizar
                            Log.d("NoteRepository", "Nota actualizada con éxito: $effectiveCloudId")
                            syncResults[note.id] = effectiveCloudId
                            
                            // Actualizar el cloudId local si es diferente
                            if (note.cloudId != effectiveCloudId) {
                                Log.d("NoteRepository", "Actualizando cloudId local de ${note.cloudId} a $effectiveCloudId")
                                noteDao.updateNoteSyncStatus(note.id, effectiveCloudId)
                            } else {
                                // Solo marcar como sincronizada
                                noteDao.markNoteAsSynced(note.id)
                            }
                        } else {
                            // Crear nuevo documento con ID aleatorio
                            val newDocRef = collection.document() // Genera ID aleatorio
                            val newDocId = newDocRef.id
                            Log.d("NoteRepository", "Creando nueva nota con ID: $newDocId")
                            newDocRef.set(noteData).await()
                            
                            // Registrar éxito al crear nueva
                            Log.d("NoteRepository", "Nota nueva guardada con éxito: $newDocId")
                            syncResults[note.id] = newDocId
                            
                            // Actualizar estado de sincronización en la base de datos local
                            noteDao.updateNoteSyncStatus(note.id, newDocId)
                        }
                        
                    } catch (e: Exception) {
                        Log.e("NoteRepository", "Error al sincronizar nota ${note.id}: ${e.message}")
                        // Continuar con la siguiente nota
                    }
                }
                
                // 5. Guardar timestamp y registrar resultado
                val syncTime = System.currentTimeMillis()
                saveLastSyncTime(syncTime)
                
                if (syncResults.isEmpty() && !downloadSuccess) {
                    Log.w("NoteRepository", "SINCRONIZACIÓN FALLIDA: No se pudo sincronizar ninguna nota en ninguna dirección.")
                    throw Exception("No se pudo sincronizar ninguna nota.")
                } else {
                    val resultMessage = StringBuilder()
                    resultMessage.append("SINCRONIZACIÓN BIDIRECCIONAL COMPLETADA: ")
                    if (downloadSuccess) resultMessage.append("Descarga exitosa. ")
                    if (syncResults.isNotEmpty()) resultMessage.append("${syncResults.size} notas subidas al servidor.")
                    
                    Log.d("NoteRepository", resultMessage.toString())
                }
                
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error durante la sincronización: ${e.message}")
                throw e
            }
        }
    }

    // Función simplificada para sincronizar una sola nota (como Google Keep)
    suspend fun quickSyncSingleNote(note: Note): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val success=firebaseManager.quickSyncNote(note)
                if (success && note.cloudId != null) {
                    // Actualizar estado de sincronización en la base de datos local
                    noteDao.updateNoteSyncStatus(note.id, note.cloudId!!)
                    Log.d("NoteRepository", "Note ${note.id} successfully synced to cloud")
                }
                success
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error syncing single note", e)
                false
            }
        }
    }

    // Guardar timestamp de última sincronización
    private suspend fun saveLastSyncTime(time: Long) {
        withContext(Dispatchers.IO) {
            try {
                val prefs=
                    firebaseManager.context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_sync_time", time).apply()
                Log.d("NoteRepository", "Last sync time saved: ${Date(time)}")
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error saving last sync time", e)
            }
        }
    }

    // Obtener timestamp de última sincronización
    suspend fun getLastSyncTime(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val prefs=
                    firebaseManager.context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val time=prefs.getLong("last_sync_time", 0)
                Log.d(
                    "NoteRepository",
                    "Retrieved last sync time: ${if (time > 0) Date(time) else "Never"}"
                )
                time
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error retrieving last sync time", e)
                0L
            }
        }
    }

    // Nueva función para configurar sincronización en tiempo real
    private var notesListener: ValueEventListener?=null
    private var categoriesListener: ValueEventListener?=null

    private fun setupRealtimeSync() {
        // Solo configurar si el usuario está autenticado
        if (!firebaseManager.isSignedIn()) {
            Log.w("NoteRepository", "User not signed in. Realtime sync not available.")
            return
        }

        val userId=firebaseManager.currentUserId ?: return

        // Removemos listeners existentes si los hay
        removeRealtimeListeners()

        // Configurar listener para notas en tiempo real
        val notesRef=firebaseManager.database.getReference("notes").child(userId)
        notesListener=object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(
                    "NoteRepository",
                    "Detected change in cloud notes. Starting background sync..."
                )

                // Lanzar coroutine en el scope de aplicación para no bloquear el listener
                externalScope.launch(Dispatchers.IO) {
                    try {
                        syncAllWithFirebase()
                        Log.d(
                            "NoteRepository",
                            "Background sync completed after cloud change detection"
                        )
                        // Actualizar timestamp de última sincronización exitosa
                        saveLastSyncTime(System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.e("NoteRepository", "Error in background sync after cloud change", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("NoteRepository", "Realtime notes listener cancelled: ${error.message}")
            }
        }

        // Configurar listener para categorías en tiempo real
        val categoriesRef=firebaseManager.database.getReference("categories").child(userId)
        categoriesListener=object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(
                    "NoteRepository",
                    "Detected change in cloud categories. Starting background sync..."
                )

                // Lanzar coroutine en el scope de aplicación
                externalScope.launch(Dispatchers.IO) {
                    try {
                        syncAllWithFirebase()
                        Log.d(
                            "NoteRepository",
                            "Categories background sync completed after cloud change"
                        )
                    } catch (e: Exception) {
                        Log.e("NoteRepository", "Error in categories background sync", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("NoteRepository", "Realtime categories listener cancelled: ${error.message}")
            }
        }

        // Configurar para mantener datos en caché para acceso offline
        // notesRef.keepSynced(true) // COMENTADO PARA DEBUG
        // categoriesRef.keepSynced(true) // COMENTADO PARA DEBUG

        // Activar listeners
        // notesRef.addValueEventListener(notesListener!!) // COMENTADO PARA DEBUG
        // categoriesRef.addValueEventListener(categoriesListener!!) // COMENTADO PARA DEBUG

        Log.d("NoteRepository", "Realtime sync listeners (TEMPORARILY DISABLED FOR DEBUGGING)")
    }

    // Función para remover listeners y evitar memory leaks
    fun removeRealtimeListeners() {
        val userId=firebaseManager.currentUserId

        notesListener?.let {
            if (userId != null) {
                firebaseManager.database.getReference("notes").child(userId).removeEventListener(it)
            }
            notesListener=null
        }

        categoriesListener?.let {
            if (userId != null) {
                firebaseManager.database.getReference("categories").child(userId)
                    .removeEventListener(it)
            }
            categoriesListener=null
        }

        Log.d("NoteRepository", "Realtime sync listeners removed")
    }

    // Comprobar y forzar sincronización si han pasado más de 15 minutos
    suspend fun checkAndForceSync() {
        withContext(Dispatchers.IO) {
            try {
                // Solo sincronizar si el usuario está autenticado
                if (!firebaseManager.isSignedIn() || firebaseManager.auth.currentUser?.isAnonymous == true) {
                    Log.d(
                        "NoteRepository",
                        "User not authenticated or in guest mode. Skipping sync check."
                    )
                    return@withContext
                }

                val lastSyncTime=getLastSyncTime()
                val currentTime=System.currentTimeMillis()
                val fifteenMinutesInMillis=15 * 60 * 1000L

                if (lastSyncTime == 0L || currentTime - lastSyncTime > fifteenMinutesInMillis) {
                    Log.d(
                        "NoteRepository",
                        "Last sync was more than 15 minutes ago. Forcing sync..."
                    )
                    syncAllWithFirebase()
                } else {
                    Log.d("NoteRepository", "Last sync was recent. Skipping forced sync.")
                }
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error checking sync status", e)
            }
        }
    }

    // Método para verificar si el usuario está autenticado y no es anónimo
    fun isUserSignedInWithGoogle(): Boolean {
        return firebaseManager.isSignedIn() &&
                firebaseManager.auth.currentUser?.isAnonymous != true
    }

    // Método para eliminar todas las notas activas (no las que están en la papelera)
    suspend fun deleteAllActiveNotes() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Eliminando todas las notas activas...")
                noteDao.deleteAllActiveNotes()
                Log.d("NoteRepository", "Todas las notas activas eliminadas correctamente")
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al eliminar todas las notas activas", e)
                throw e
            }
        }
    }

    // Método para iniciar sesión con Google
    suspend fun signInWithGoogle(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Iniciando proceso de autenticación con Google")
                // Aquí se delegaría al FirebaseManager para el proceso de autenticación
                // En la implementación real, esto requeriría más pasos de UI
                // Como esto es solo un método de ejemplo y requiere una cuenta de Google válida
                // Vamos a retornar false por defecto ya que no tenemos una cuenta disponible aquí
                // En una implementación real, esta cuenta vendría de la UI de login
                val isSignedIn=
                    false // No podemos llamar a firebaseManager.signInWithGoogle() sin una cuenta

                if (isSignedIn) {
                    // Configurar la sincronización en tiempo real si el inicio es exitoso
                    setupRealtimeSync()
                    Log.d("NoteRepository", "Inicio de sesión con Google exitoso")
                } else {
                    Log.w("NoteRepository", "No se pudo iniciar sesión con Google")
                }

                isSignedIn
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error en inicio de sesión con Google", e)
                false
            }
        }
    }

    // Método para cerrar sesión
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Cerrando sesión...")

                // Primero eliminar los listeners de tiempo real
                removeRealtimeListeners()

                // Luego cerrar sesión en Firebase
                firebaseManager.signOut()

                Log.d("NoteRepository", "Sesión cerrada correctamente")
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al cerrar sesión", e)
                throw e
            }
        }
    }

    // Método para activar la sincronización en tiempo real
    fun enableRealtimeSync() {
        if (isUserSignedInWithGoogle()) {
            setupRealtimeSync()
            Log.d("NoteRepository", "Sincronización en tiempo real activada")
        } else {
            Log.w(
                "NoteRepository",
                "No se puede activar sincronización en tiempo real sin iniciar sesión"
            )
        }
    }

    // Método para desactivar la sincronización en tiempo real
    fun disableRealtimeSync() {
        try {
            removeRealtimeListeners()
            Log.d("NoteRepository", "Sincronización en tiempo real desactivada")
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error al desactivar sincronización en tiempo real", e)
        }
    }

    suspend fun getNoteByCloudId(cloudId: String): Note? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Buscando nota por cloudId: $cloudId")
                noteDao.getNoteByCloudId(cloudId)
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al buscar nota por cloudId: $cloudId", e)
                null
            }
        }
    }

    /**
     * Versión no suspendida de getNoteByCloudId para usar desde funciones que no son corrutinas
     */
    fun getNoteByCloudIdNonSuspend(cloudId: String): Note? {
        return try {
            Log.d("NoteRepository", "Fetching note by cloudId (non-suspend): $cloudId")
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                noteDao.getNoteByCloudId(cloudId)
            }
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error fetching note by cloudId (non-suspend): $cloudId", e)
            null
        }
    }

    /**
     * Busca notas por título y contenido
     * Útil para encontrar posibles duplicados durante la sincronización
     */
    suspend fun searchNotesByTitleAndContent(title: String, content: String): List<Note> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Buscando notas por título y contenido")
                // Implementación manual ya que no existe el método en el DAO
                val allNotes=noteDao.getActiveNotes()
                return@withContext allNotes.filter {
                    it.title.equals(title, ignoreCase=true) &&
                            it.content.equals(content, ignoreCase=true)
                }
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al buscar notas por título y contenido", e)
                emptyList()
            }
        }
    }

    /**
     * Actualiza el estado de sincronización de una nota
     */
    suspend fun updateNoteFirestoreStatus(
        noteId: Long,
        cloudId: String,
        needsSync: Boolean=false
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Actualizando estado de sincronización para nota $noteId")
                noteDao.updateNoteFirestoreStatus(noteId, cloudId, needsSync)
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al actualizar estado de sincronización", e)
            }
        }
    }

    /**
     * Importa notas desde Firestore a la base de datos local
     */
    suspend fun importNotesFromFirestore(): Int {
        return withContext(Dispatchers.IO) {
            var importedCount=0
            try {
                Log.d("NoteRepository", "Iniciando importación de notas desde Firestore")

                // Obtener notas desde Firestore
                val firestoreNotes=firebaseManager.getNotesFromFirestore()

                if (firestoreNotes != null) {
                    val notesCount=firestoreNotes.size
                    Log.d("NoteRepository", "Obtenidas $notesCount notas desde Firestore")

                    // Procesar cada nota
                    for (firestoreNote in firestoreNotes) {
                        try {
                            // Verificar si ya existe localmente
                            val cloudId=
                                firestoreNote.id // En FirestoreNote, id es equivalente a cloudId
                            val existingNote=noteDao.getNoteByCloudId(cloudId)

                            // Reestructuramos todo el bloque if-else para evitar problemas de sintaxis
                            when {
                                // Caso 1: La nota no existe localmente
                                existingNote == null -> {
                                    // Insertar nueva nota
                                    val localNote=firestoreNote.toLocalNote()
                                    val localId=noteDao.insertNote(localNote)
                                    if (localId > 0) {
                                        importedCount++
                                        Log.d(
                                            "NoteRepository",
                                            "Nota de Firestore insertada localmente, cloudId: $cloudId, localId: $localId"
                                        )
                                    }
                                }

                                // Caso 2: La nota existe localmente y necesita actualizarse
                                else -> {
                                    // Actualizar nota existente con los datos de Firestore
                                    val updatedNote=existingNote.copy(
                                        title=firestoreNote.title,
                                        content=firestoreNote.content,
                                        modifiedDate=firestoreNote.timestamp.time,
                                        isFavorite=existingNote.isFavorite,
                                        isInTrash=existingNote.isInTrash,
                                        colorId=existingNote.colorId,
                                        categoryId=existingNote.categoryId,
                                        reminderDateTime=existingNote.reminderDateTime,
                                        needsSync=false
                                    )
                                    noteDao.updateNote(updatedNote)
                                    importedCount++
                                    Log.d(
                                        "NoteRepository",
                                        "Nota de Firestore actualizada localmente, cloudId: $cloudId"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NoteRepository", "Error procesando nota de Firestore", e)
                        }
                    }

                    Log.d(
                        "NoteRepository",
                        "Importación completada. $importedCount notas importadas/actualizadas"
                    )
                }
                return@withContext importedCount
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error global en importación desde Firestore", e)
                return@withContext importedCount
            }
        }
    }

    /**
     * Restaura notas desde una lista de backup
     * Borra todas las notas activas y las reemplaza con las del backup
     */
    suspend fun restoreFromBackup(notesFromBackup: List<Note>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Iniciando restauración desde backup...")

                // Eliminar todas las notas activas existentes
                deleteAllActiveNotes()

                // Insertar las notas del backup
                var restoredCount=0
                for (backupNote in notesFromBackup) {
                    try {
                        // Crear una nueva nota basada en la nota del backup
                        val newNote=backupNote.copy(
                            id=0, // Permitir que Room asigne un nuevo ID
                            cloudId=null, // Resetear el ID de la nube
                            needsSync=true, // Marcar para sincronización
                            modifiedDate=System.currentTimeMillis() // Actualizar fecha de modificación
                        )

                        val insertedId=noteDao.insertNote(newNote)
                        if (insertedId > 0) {
                            restoredCount++
                        }
                    } catch (e: Exception) {
                        Log.e("NoteRepository", "Error al restaurar nota individual", e)
                    }
                }

                Log.d(
                    "NoteRepository",
                    "Restauración completada. $restoredCount/${notesFromBackup.size} notas restauradas"
                )

                // Intentar sincronizar con la nube si el usuario está conectado
                if (isUserSignedInWithGoogle()) {
                    try {
                        syncAllWithFirebase()
                    } catch (e: Exception) {
                        Log.w(
                            "NoteRepository",
                            "Error al sincronizar después de restaurar: ${e.message}"
                        )
                    }
                }

                return@withContext restoredCount > 0
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error global en restauración desde backup", e)
                return@withContext false
            }
        }
    }

    /**
     * Método para verificar si una nota existe por título y contenido
     * Utilizado para evitar duplicados durante la sincronización
     */
    private suspend fun noteExistsByTitleAndContent(title: String, content: String): Boolean {
        return searchNotesByTitleAndContent(title, content).isNotEmpty()
    }

    /**
     * Método principal para sincronizar con Firebase
     * Este método es llamado periódicamente desde MainActivity
     */
    suspend fun syncWithFirebase() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Iniciando sincronización con Firebase")

                // Verificar si el usuario está conectado
                if (!firebaseManager.isSignedIn()) {
                    Log.d("NoteRepository", "Usuario no conectado, omitiendo sincronización")
                    return@withContext
                }

                // 1. Subir notas locales pendientes de sincronización
                val notesForSync=getNotesForSync()
                if (notesForSync.isNotEmpty()) {
                    Log.d(
                        "NoteRepository",
                        "Sincronizando ${notesForSync.size} notas locales a Firebase"
                    )
                    for (note in notesForSync) {
                        try {
                            val cloudId=firebaseManager.saveNote(note)
                            if (cloudId != null) {
                                updateNoteSyncStatus(note.id, cloudId)
                                Log.d("NoteRepository", "Nota sincronizada: ${note.title}")
                            }
                        } catch (e: Exception) {
                            Log.e("NoteRepository", "Error al sincronizar nota: ${note.title}", e)
                        }
                    }
                }

                // 2. Descargar notas de Firebase
                try {
                    val notesFromCloud=firebaseManager.getAllNotes()
                    if (notesFromCloud != null && notesFromCloud is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        syncWithCloud(notesFromCloud as List<Note>)
                    }
                } catch (e: Exception) {
                    Log.e("NoteRepository", "Error al obtener notas de Firebase", e)
                }

                // 3. Guardar timestamp de sincronización
                saveLastSyncTime(System.currentTimeMillis())
                Log.d("NoteRepository", "Sincronización con Firebase completada")

            } catch (e: Exception) {
                Log.e("NoteRepository", "Error general en sincronización con Firebase", e)
            }
        }
    }

    /**
     * Sincroniza las notas locales con las notas recibidas de la nube
     * @param notesFromCloud Lista de notas recibidas de Firestore
     */
    suspend fun syncWithCloud(notesFromCloud: List<Note>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Sincronizando ${notesFromCloud.size} notas desde la nube")

                // Obtener todas las notas locales para comparación
                val localNotes=noteDao.getAllNotes()

                // Crear mapas para búsqueda rápida
                val localNotesByCloudId=localNotes
                    .filter { it.cloudId != null }
                    .associateBy { it.cloudId!! }

                val localNotesByFirebaseId=localNotes
                    .filter { it.cloudId != null && it.cloudId!!.contains("_") }
                    .associateBy { it.cloudId!!.substringAfter("_") }

                // Procesar cada nota de la nube
                for (cloudNote in notesFromCloud) {
                    // Si la cloudNote ya tiene un ID local asignado (desde NotesViewModel),
                    // usamos ese ID directamente
                    if (cloudNote.id > 0) {
                        Log.d(
                            "NoteRepository",
                            "Actualizando nota con ID local preexistente: ${cloudNote.id}"
                        )
                        noteDao.updateNote(cloudNote)
                        continue
                    }

                    // Buscar por cloudId
                    var localNote=cloudNote.cloudId?.let { cloudId ->
                        localNotesByCloudId[cloudId]
                    }

                    // Si no se encuentra, buscar por el ID base en cloudId
                    if (localNote == null && cloudNote.cloudId != null && cloudNote.cloudId!!.contains(
                            "_"
                        )
                    ) {
                        val baseId=cloudNote.cloudId!!.substringAfter("_")
                        val matchingNote=localNotesByFirebaseId[baseId]
                        if (matchingNote != null) {
                            Log.d(
                                "NoteRepository",
                                "Encontrada coincidencia alternativa por ID base: $baseId"
                            )
                            localNote=matchingNote
                        }
                    }

                    if (localNote != null) {
                        // La nota existe localmente, actualizar manteniendo el ID local
                        val updatedNote=cloudNote.copy(
                            id=localNote.id,  // Mantener el ID local para evitar duplicados
                            title=cloudNote.title,
                            content=cloudNote.content,
                            modifiedDate=cloudNote.modifiedDate,
                            isFavorite=cloudNote.isFavorite,
                            isInTrash=cloudNote.isInTrash,
                            colorId=cloudNote.colorId,
                            categoryId=cloudNote.categoryId,
                            reminderDateTime=cloudNote.reminderDateTime,
                            needsSync=false,
                            cloudId=cloudNote.cloudId // Asegurar que el cloudId se mantenga
                        )
                        noteDao.updateNote(updatedNote)
                        Log.d(
                            "NoteRepository",
                            "Nota actualizada desde la nube: ${cloudNote.title} con ID local: ${localNote.id}"
                        )
                    } else {
                        // La nota realmente no existe localmente, insertarla como nueva
                        val newNote=cloudNote.copy(
                            id=0, // Asignar nuevo ID local
                            needsSync=false
                        )
                        val newId=noteDao.insertNote(newNote)
                        Log.d(
                            "NoteRepository",
                            "Nueva nota insertada desde la nube: ${cloudNote.title} con nuevo ID: $newId"
                        )
                    }
                }

                Log.d("NoteRepository", "Sincronización con la nube completada")
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al sincronizar con la nube", e)
            }
        }
    }

    /**
     * SOLUCIÓN RADICAL: Descarga todas las notas desde el servidor.
     * reemplazando las notas existentes con el mismo cloudId.
     * @return true si la descarga fue exitosa, false en caso contrario
     */
    /**
     * Descarga todas las notas desde el servidor y las actualiza en la base de datos local.
     * Versión mejorada que preserva todos los campos importantes y maneja correctamente la conversión.
     */
    suspend fun downloadAllNotesFromServer(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("NoteRepository", "Iniciando descarga de notas desde el servidor...")

                // 1. Obtener ID del usuario actual
                val userId = firebaseManager.auth.currentUser?.uid
                if (userId.isNullOrEmpty()) {
                    Log.e("NoteRepository", "Error: ID de usuario no disponible para descarga")
                    return@withContext false
                }

                // 2. Descargar todas las notas del servidor
                val notesFromServer = firebaseManager.downloadAllNotesFromServer()
                Log.d("NoteRepository", "Se encontraron ${notesFromServer.size} notas en el servidor")

                if (notesFromServer.isEmpty()) {
                    Log.d("NoteRepository", "No hay notas en el servidor para descargar")
                    return@withContext true // Consideramos éxito si no hay notas que sincronizar
                }

                // 3. Procesar y guardar las notas descargadas
                var insertCount = 0
                var updateCount = 0

                for (firestoreNote in notesFromServer) {
                    try {
                        val cloudId = firestoreNote.id
                        if (cloudId.isEmpty()) {
                            Log.e("NoteRepository", "Error: Nota del servidor sin ID válido, saltando")
                            continue
                        }

                        // Verificar si la nota ya existe localmente por cloudId
                        val existingNoteByCloudId = getNoteByCloudId(cloudId)
                        
                        // Verificar si la nota ya existe por localId
                        val localId = firestoreNote.localId?.toLongOrNull()
                        val existingNoteById = if (localId != null && localId > 0) {
                            noteDao.getNoteById(localId)
                        } else null
                        
                        // Decidir si actualizar una existente o insertar como nueva
                        if (existingNoteByCloudId != null) {
                            // La nota existe por cloudId - actualizar
                            val updatedNote = existingNoteByCloudId.copy(
                                title = firestoreNote.title.ifEmpty { existingNoteByCloudId.title },
                                content = firestoreNote.content.ifEmpty { existingNoteByCloudId.content },
                                modifiedDate = firestoreNote.modifiedDate ?: firestoreNote.timestamp.time,
                                colorId = firestoreNote.colorId.ifEmpty { existingNoteByCloudId.colorId },
                                isFavorite = firestoreNote.isFavorite,
                                isLocked = firestoreNote.isLocked ?: existingNoteByCloudId.isLocked,
                                isInTrash = firestoreNote.isInTrash ?: existingNoteByCloudId.isInTrash,
                                needsSync = false // Marcar como sincronizada
                            )
                            
                            noteDao.updateNote(updatedNote)
                            updateCount++
                            Log.d("NoteRepository", "Nota actualizada por cloudId: ${existingNoteByCloudId.id}, título: ${updatedNote.title}")
                        } else if (existingNoteById != null) {
                            // La nota existe por localId pero no tenía cloudId - actualizar cloudId y contenido
                            val updatedNote = existingNoteById.copy(
                                title = firestoreNote.title.ifEmpty { existingNoteById.title },
                                content = firestoreNote.content.ifEmpty { existingNoteById.content },
                                cloudId = cloudId, // Asignar cloudId que no tenía
                                modifiedDate = firestoreNote.modifiedDate ?: firestoreNote.timestamp.time,
                                colorId = firestoreNote.colorId.ifEmpty { existingNoteById.colorId },
                                isFavorite = firestoreNote.isFavorite,
                                isLocked = firestoreNote.isLocked ?: existingNoteById.isLocked,
                                isInTrash = firestoreNote.isInTrash ?: existingNoteById.isInTrash,
                                needsSync = false // Marcar como sincronizada
                            )
                            
                            noteDao.updateNote(updatedNote)
                            updateCount++
                            Log.d("NoteRepository", "Nota actualizada por localId: ${existingNoteById.id}, asignando cloudId: $cloudId")
                        } else {
                            // La nota no existe localmente - insertar como nueva
                            val localNote = firestoreNote.toLocalNote()
                            val newId = noteDao.insertNote(localNote)
                            insertCount++
                            Log.d("NoteRepository", "Nueva nota insertada: ID=$newId, título=${localNote.title}")
                        }
                    } catch (e: Exception) {
                        Log.e("NoteRepository", "Error al procesar nota del servidor: ${e.message}")
                    }
                }

                Log.d("NoteRepository", "Descarga completada: $insertCount nuevas notas, $updateCount notas actualizadas")
                
                // Guardar timestamp de sincronización exitosa
                saveLastSyncTime(System.currentTimeMillis())

                // Éxito si al menos una nota se insertó o actualizó
                return@withContext (insertCount > 0 || updateCount > 0)
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error durante la descarga de notas: ${e.message}")
                return@withContext false
            }
        }
    }

    /**
     * SOLUCIÓN RADICAL: Sube todas las notas locales al servidor.
     * Este método sube todas las notas locales al servidor, incluyendo las que ya están sincronizadas.
     * @return true si la subida fue exitosa, false en caso contrario
     */
    suspend fun uploadAllNotesToServer(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "NoteRepository",
                    "[RADICAL] Iniciando SUBIDA MANUAL de todas las notas al servidor..."
                )

                // 1. Obtener todas las notas locales (activas, no en papelera)
                val localNotes=noteDao.getActiveNotes()

                if (localNotes.isEmpty()) {
                    Log.d("NoteRepository", "[RADICAL] No hay notas locales para subir.")
                    return@withContext true // Consideramos éxito si no hay notas que subir
                }

                Log.d(
                    "NoteRepository",
                    "[RADICAL] Se encontraron ${localNotes.size} notas locales. Subiendo al servidor..."
                )

                // 2. Subir todas las notas al servidor
                val uploadSuccess=firebaseManager.uploadAllNotesToServer(localNotes)

                // El método devuelve un boolean, no un contador
                Log.d(
                    "NoteRepository",
                    "[RADICAL] SUBIDA COMPLETADA: ${if (uploadSuccess) "exitosa" else "fallida"} para ${localNotes.size} notas"
                )

                // 3. Marcar las notas como sincronizadas si la subida fue exitosa
                if (uploadSuccess) {
                    for (note in localNotes) {
                        try {
                            noteDao.updateNeedsSyncStatus(note.id, false)
                        } catch (e: Exception) {
                            Log.e(
                                "NoteRepository",
                                "[RADICAL] Error al actualizar estado de sincronización de nota - ID: ${note.id}",
                                e
                            )
                        }
                    }

                    // Guardar timestamp de sincronización exitosa
                    saveLastSyncTime(System.currentTimeMillis())
                    return@withContext true
                }

                return@withContext false
            } catch (e: Exception) {
                Log.e("NoteRepository", "[RADICAL] ERROR FATAL durante la subida de notas", e)
                return@withContext false
            }
        }
    }

    /**
     * SOLUCIÓN RADICAL: Procesa las notas recibidas de Firestore.
     * Este método se usa para procesar las notas recibidas del método forceRefreshNotes().
     * @param notesFromFirestore Lista de notas recibidas de Firestore
     * @return true si el procesamiento fue exitoso, false en caso contrario
     */
    suspend fun processNotesFromFirestore(notesFromFirestore: List<Note>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "NoteRepository",
                    "[RADICAL] Procesando ${notesFromFirestore.size} notas recibidas de Firestore..."
                )

                if (notesFromFirestore.isEmpty()) {
                    Log.d("NoteRepository", "[RADICAL] No hay notas para procesar.")
                    return@withContext true // Consideramos éxito si no hay notas que procesar
                }

                var successCount=0
                for (note in notesFromFirestore) {
                    try {
                        // Verificar si la nota ya existe en la base de datos local
                        val existingNote=note.cloudId?.let { getNoteByCloudId(it) }

                        if (existingNote != null) {
                            // Actualizar la nota existente
                            val updatedNote=existingNote.copy(
                                title=note.title,
                                content=note.content,
                                modifiedDate=note.modifiedDate,
                                isFavorite=note.isFavorite,
                                isInTrash=note.isInTrash,
                                colorId=note.colorId,
                                categoryId=note.categoryId,
                                reminderDateTime=note.reminderDateTime,
                                needsSync=false,
                                isLocked=note.isLocked
                            )
                            updateNote(updatedNote)
                            Log.d(
                                "NoteRepository",
                                "[RADICAL] Nota actualizada: ${note.title} (ID: ${existingNote.id})"
                            )
                        } else {
                            // Insertar nueva nota
                            val newNote=note.copy(needsSync=false)
                            val newId=insertNote(newNote)
                            Log.d(
                                "NoteRepository",
                                "[RADICAL] Nueva nota insertada: ${note.title} (ID: $newId)"
                            )
                        }
                        successCount++
                    } catch (e: Exception) {
                        Log.e(
                            "NoteRepository",
                            "[RADICAL] Error al procesar nota individual: ${note.title}",
                            e
                        )
                        // Continuar con la siguiente nota a pesar del error
                    }
                }

                Log.d(
                    "NoteRepository",
                    "[RADICAL] Procesamiento completado: $successCount/${notesFromFirestore.size} notas procesadas exitosamente"
                )
                return@withContext successCount == notesFromFirestore.size
            } catch (e: Exception) {
                Log.e("NoteRepository", "[RADICAL] Error al procesar notas de Firestore", e)
                return@withContext false
            }
        }
    }

    /**
     * Obtiene una nota por su cloudId (ID en Firestore)
     * @param cloudId El ID de la nota en Firestore
     * @return La nota si existe, null en caso contrario
     */
    suspend fun getNoteBycloudId(cloudId: String): Note? {
        return withContext(Dispatchers.IO) {
            try {
                val notes=noteDao.getNoteByCloudId(cloudId)
                notes
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al buscar nota por cloudId: $cloudId", e)
                null
            }
        }
    }

    /**
     * Crea una copia de seguridad de las notas en la nube usando una estructura compartida
     * que permite que todas las cuentas accedan a los mismos backups.
     *
     * @param notes Lista de notas para incluir en el backup
     * @return true si la copia se creó correctamente, false en caso contrario
     */
    suspend fun createCloudBackup(notes: List<Note>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isUserSignedInWithGoogle()) {
                    Log.w("NoteRepository", "No se puede crear copia en la nube sin autenticación")
                    return@withContext false
                }

                Log.d("NoteRepository", "Creando copia de seguridad compartida en la nube...")

                // Primero realizamos una sincronización completa si es posible
                try {
                    syncAllWithFirebase()
                } catch (e: Exception) {
                    Log.w(
                        "NoteRepository",
                        "No se pudo sincronizar antes de crear backup: ${e.message}"
                    )
                    // Continuamos a pesar del error de sincronización
                }

                // Obtener datos del usuario actual (mantenemos la referencia para saber quién creó el backup)
                val userId=firebaseManager.auth.currentUser?.uid ?: return@withContext false
                val timestamp=System.currentTimeMillis()

                // Crear estructura para el backup (incluye userId para identificar quién lo creó)
                val backupData=mapOf(
                    "timestamp" to timestamp,
                    "deviceInfo" to Build.MODEL,
                    "notesCount" to notes.size,
                    "createdDate" to timestamp,
                    "userId" to userId,  // Incluimos el userId como un campo, no como parte de la ruta
                    "userEmail" to (firebaseManager.auth.currentUser?.email
                        ?: "desconocido") // Email para identificación
                )

                // Guardar en Firebase bajo una estructura compartida: /shared_backups/{timestamp}
                // Esta estructura permite que cualquier usuario pueda acceder a todos los backups
                firebaseManager.database.getReference("shared_backups")
                    .child(timestamp.toString())
                    .setValue(backupData)
                    .await()

                Log.d("NoteRepository", "Copia de seguridad compartida creada correctamente")
                true
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al crear copia de seguridad compartida", e)
                false
            }
        }
    }

    /**
     * Elimina todos los datos del usuario en la nube
     * @return true si la operación fue exitosa, false en caso contrario
     */
    suspend fun deleteAllCloudData(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isUserSignedInWithGoogle()) {
                    Log.w(
                        "NoteRepository",
                        "No hay sesión iniciada. No se pueden eliminar datos en la nube."
                    )
                    return@withContext false
                }

                val result=firebaseManager.deleteAllUserData()
                if (result) {
                    Log.d("NoteRepository", "Datos en la nube eliminados correctamente")
                } else {
                    Log.e("NoteRepository", "Error al eliminar datos en la nube")
                }

                return@withContext result
            } catch (e: Exception) {
                Log.e("NoteRepository", "Excepción al eliminar datos en la nube", e)
                return@withContext false
            }
        }
    }

    suspend fun getAllActiveNotes(): List<Note> {
        return noteDao.getActiveNotes()
    }
    
    /**
     * Verifica si el usuario está autenticado en Firebase
     * @return true si el usuario está autenticado, false en caso contrario
     */
    fun isUserSignedIn(): Boolean {
        return firebaseManager.isSignedIn()
    }

    suspend fun addNote(note: Note): Long {
        return withContext(Dispatchers.IO) {
            try {
                noteDao.insertNote(note)
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al añadir nota: ${e.message}")
                -1L
            }
        }
    }

    /**
     * Inserta o actualiza una nota evitando duplicados por título y contenido
     * @param note La nota a guardar
     * @return ID local de la nota (nuevo o existente) o null si ocurrió un error
     */
    suspend fun safeInsertOrUpdateNote(note: Note): Long? {
        val duplicate = noteDao.findByTitleAndContent(note.title, note.content)
        if (duplicate != null) {
            Log.w("NoteRepository", "Nota duplicada detectada. No se insertará de nuevo.")
            return duplicate.id
        }

        val localId = noteDao.insertNote(note)
        val noteWithLocalId = note.copy(id = localId)
        val cloudId = firebaseManager.saveNote(noteWithLocalId)
        if (cloudId != null) {
            noteDao.updateNoteSyncStatus(localId, cloudId)
            Log.d("NoteRepository", "Nota sincronizada con Firebase. cloudId: $cloudId")
        } else {
            Log.e("NoteRepository", "Error al sincronizar nota con Firebase.")
        }

        return localId
    }

    fun refreshNotes() {
        // Notificar a los observadores que los datos han cambiado
        // No se necesita una implementación específica ya que los Flows
        // se actualizan automáticamente cuando cambia la base de datos
    }

    suspend fun refreshCategories() {
        // Este método puede ser llamado para asegurar que los datos estén actualizados
        // La implementación actual con Flow actualiza automáticamente los datos
    }
    
    /**
     * Elimina notas duplicadas moviendo las repetidas a la papelera
     * @return Número de notas duplicadas encontradas y movidas a la papelera
     */
    suspend fun deduplicateNotes(): Int = withContext(Dispatchers.IO) {
        try {
            val allNotes = noteDao.getAllNotes()
            val seen = mutableSetOf<Pair<String, String>>()
            var duplicatesCount = 0
            
            for (note in allNotes) {
                val key = note.title.trim() to note.content.trim()
                if (seen.contains(key)) {
                    // Es un duplicado, mover a la papelera
                    noteDao.moveToTrash(note.id)
                    Log.i("NoteRepository", "Nota duplicada encontrada y movida a papelera: ID=${note.id}, Título=${note.title}")
                    duplicatesCount++
                } else {
                    seen.add(key)
                }
            }
            
            Log.d("NoteRepository", "Proceso de deduplicación completado: $duplicatesCount notas duplicadas encontradas")
            return@withContext duplicatesCount
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error al deduplicar notas: ${e.message}", e)
            return@withContext 0
        }
    }

    suspend fun deleteNoteById(id: Long) {
        withContext(Dispatchers.IO) {
            try {
                // Primero buscamos la nota para marcarla como eliminada
                val note = noteDao.getNoteById(id)
                note?.let {
                    // Marcamos la nota como eliminada (en la papelera) y necesita sincronización
                    val updatedNote = it.copy(isInTrash = true, modifiedDate = System.currentTimeMillis(), needsSync = true)
                    noteDao.updateNote(updatedNote)
                    Log.d("NoteRepository", "Nota movida a la papelera: $id")
                } ?: Log.w("NoteRepository", "No se encontró la nota a eliminar: $id")
            } catch (e: Exception) {
                Log.e("NoteRepository", "Error al eliminar nota: ${e.message}")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NoteRepository? = null
        
        fun getInstance(context: Context, scope: CoroutineScope): NoteRepository {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getInstance(context)
                val firebaseManager = FirebaseManager.getInstance(context)
                val instance = NoteRepository(
                    noteDao = database.noteDao(),
                    categoryDao = database.categoryDao(),
                    firebaseManager = firebaseManager,
                    externalScope = scope
                )
                INSTANCE = instance
                instance
            }
        }
    }
}

private fun Any.copy(
    title: String,
    content: String,
    modifiedDate: Long,
    isFavorite: Boolean,
    isInTrash: Boolean,
    colorId: String,
    categoryId: Long?,
    reminderDateTime: Long?,
    needsSync: Boolean,
    isLocked: Boolean
): Note {
    // Asumimos que 'this' es una Note y creamos una copia con los nuevos valores
    if (this is Note) {
        return Note(
            id = this.id,
            title = title,
            content = content,
            createdDate = this.createdDate,
            modifiedDate = modifiedDate,
            isInTrash = isInTrash,
            colorId = colorId,
            categoryId = categoryId,
            cloudId = this.cloudId,
            needsSync = needsSync,
            reminderDateTime = reminderDateTime,
            isFavorite = isFavorite,
            isLocked = isLocked,
            userId=UUID.randomUUID().toString()
        )
    } else {
        throw IllegalArgumentException("El objeto no es una Note")
    }
}
