package com.tambuchosecretdev.quickzenapp

import android.util.Log
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Clase utilitaria para manejar la sincronización sin crear duplicados.
 * Esta clase funciona como un complemento del NotesViewModel para solucionar
 * el problema de duplicación de notas.
 */
class NoDuplicatesSync(
    private val repository: NoteRepository,
    private val firebaseManager: FirebaseManager
) {
    companion object {
        private const val TAG = "NoDuplicatesSync"
    }

    /**
     * Realiza una sincronización completa sin crear duplicados
     */
    suspend fun syncWithoutDuplicates(): Boolean = withContext(Dispatchers.IO) {
        try {
            // PASO 1: Limpiar duplicados existentes
            val duplicatesRemoved = removeDuplicates()
            if (duplicatesRemoved > 0) {
                Log.d(TAG, "Se eliminaron $duplicatesRemoved notas duplicadas antes de sincronizar")
            }

            // PASO 2: Sincronizar con Firebase (subir cambios locales primero)
            val notesToSync = repository.getNotesForSync()
            for (note in notesToSync) {
                syncSingleNote(note)
            }

            // PASO 3: Descargar cambios de Firebase
            val firestoreNotes = firebaseManager.getNotes()
            
            // Convertir notas de Firestore a notas locales
            val cloudNotes = firestoreNotes.map { firestoreNote -> 
                Note(
                    id = 0, // ID local se asignará al guardar
                    title = firestoreNote.title,
                    content = firestoreNote.content,
                    createdDate = firestoreNote.createdDate ?: firestoreNote.timestamp.time,
                    modifiedDate = firestoreNote.modifiedDate ?: firestoreNote.timestamp.time,
                    isInTrash = firestoreNote.isInTrash ?: false,
                    colorId = firestoreNote.colorId.ifEmpty { NoteAppColors.DEFAULT_COLOR_ID },
                    images = firestoreNote.images ?: emptyList(),
                    categoryId = firestoreNote.categoryIdLong,
                    cloudId = firestoreNote.id,
                    needsSync = false,
                    reminderDateTime = firestoreNote.reminderDateTime,
                    isFavorite = firestoreNote.isFavorite,
                    isLocked = firestoreNote.isLocked ?: false,
                    labelIds = firestoreNote.labelIds ?: emptyList(),
                    userId=UUID.randomUUID().toString()
                )
            }
            
            handleCloudNotes(cloudNotes)

            // PASO 4: Verificar y eliminar duplicados que puedan haberse creado
            val finalDuplicatesRemoved = removeDuplicates()
            if (finalDuplicatesRemoved > 0) {
                Log.d(TAG, "Se eliminaron $finalDuplicatesRemoved notas duplicadas después de sincronizar")
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización sin duplicados", e)
            return@withContext false
        }
    }

    /**
     * Sincroniza una sola nota con Firebase, evitando duplicados
     */
    suspend fun syncSingleNote(note: Note): String? = withContext(Dispatchers.IO) {
        try {
            // Si la nota no tiene cloudId, verificar si ya existe en la nube
            val existingCloudId = if (note.cloudId.isNullOrEmpty()) {
                firebaseManager.findExistingNoteByContent(note.title, note.content)
            } else null

            // Si ya existe, vincular con esa nota
            if (existingCloudId != null) {
                val updatedNote = note.copy(cloudId = existingCloudId, needsSync = false)
                repository.updateNote(updatedNote)
                Log.d(TAG, "Nota vinculada a existente en la nube: ${note.id} -> $existingCloudId")
                return@withContext existingCloudId
            }

            // Si no existe o ya tiene cloudId, guardar/actualizar
            val cloudId = firebaseManager.saveNote(note)
            if (cloudId != null) {
                val updatedNote = note.copy(cloudId = cloudId, needsSync = false)
                repository.updateNote(updatedNote)
                Log.d(TAG, "Nota sincronizada con Firebase: ${note.id} -> $cloudId")
                return@withContext cloudId
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error al sincronizar nota individual", e)
            return@withContext null
        }
    }

    /**
     * Maneja las notas descargadas de Firebase, evitando duplicados
     */
    private suspend fun handleCloudNotes(cloudNotes: List<Note>) = withContext(Dispatchers.IO) {
        try {
            // Obtener notas locales para comparación
            val localNotes = repository.getAllActiveNotes()
            val localNotesByCloudId = localNotes.filter { !it.cloudId.isNullOrEmpty() }
                .associateBy { it.cloudId!! }
            val localNotesByContent = localNotes.associateBy { "${it.title.trim()}_${it.content.trim()}" }

            for (cloudNote in cloudNotes) {
                // Verificar si ya existe localmente
                val existingByCloudId = localNotesByCloudId[cloudNote.cloudId]
                val existingByContent = localNotesByContent["${cloudNote.title.trim()}_${cloudNote.content.trim()}"]

                if (existingByCloudId != null) {
                    // Ya existe por cloudId, actualizar solo si la versión de Firebase es más reciente
                    if (cloudNote.modifiedDate > existingByCloudId.modifiedDate) {
                        val updatedNote = existingByCloudId.copy(
                            title = cloudNote.title,
                            content = cloudNote.content,
                            modifiedDate = cloudNote.modifiedDate,
                            needsSync = false
                        )
                        repository.updateNote(updatedNote)
                        Log.d(TAG, "Actualizada nota existente por cloudId: ${existingByCloudId.id}")
                    }
                } else if (existingByContent != null) {
                    // Existe por contenido pero no por cloudId, vincular
                    if (existingByContent.cloudId.isNullOrEmpty()) {
                        val updatedNote = existingByContent.copy(
                            cloudId = cloudNote.cloudId,
                            needsSync = false
                        )
                        repository.updateNote(updatedNote)
                        Log.d(TAG, "Vinculada nota existente por contenido: ${existingByContent.id} -> ${cloudNote.cloudId}")
                    }
                } else {
                    // No existe, insertar como nueva
                    val newNote = cloudNote.copy(id = 0, cloudId = cloudNote.cloudId, needsSync = false)
                    repository.insertNote(newNote)
                    Log.d(TAG, "Insertada nueva nota desde Firebase: ${cloudNote.cloudId}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al manejar notas de Firebase", e)
        }
    }

    /**
     * Encuentra y elimina notas duplicadas
     * @return Número de duplicados eliminados
     */
    suspend fun removeDuplicates(): Int = withContext(Dispatchers.IO) {
        try {
            // Obtener todas las notas activas
            val allNotes = repository.getAllActiveNotes()
            
            // Agrupar por título+contenido para encontrar duplicados
            val groupedNotes = allNotes.groupBy { "${it.title.trim()}_${it.content.trim()}" }
            
            // Filtrar grupos con más de una nota (duplicados)
            val duplicateGroups = groupedNotes.filter { it.value.size > 1 }
            
            var totalRemoved = 0
            
            // Procesar cada grupo de duplicados
            duplicateGroups.forEach { (_, notes) ->
                // Ordenar: primero con cloudId, luego por fecha de modificación
                val sortedNotes = notes.sortedWith(
                    compareByDescending<Note> { !it.cloudId.isNullOrEmpty() }
                        .thenByDescending { it.modifiedDate }
                )
                
                // Mantener la primera, eliminar el resto
                val noteToKeep = sortedNotes.first()
                val notesToRemove = sortedNotes.drop(1)
                
                // Eliminar duplicados
                notesToRemove.forEach { duplicateNote ->
                    repository.deleteNoteById(duplicateNote.id)
                    totalRemoved++
                }
                
                // Si la nota a mantener no tiene cloudId pero alguna eliminada sí,
                // actualizar con ese cloudId
                if (noteToKeep.cloudId.isNullOrEmpty()) {
                    val noteWithCloudId = notesToRemove.find { !it.cloudId.isNullOrEmpty() }
                    if (noteWithCloudId != null) {
                        val updatedNote = noteToKeep.copy(
                            cloudId = noteWithCloudId.cloudId,
                            needsSync = true
                        )
                        repository.updateNote(updatedNote)
                    }
                }
            }
            
            return@withContext totalRemoved
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar duplicados", e)
            return@withContext 0
        }
    }
}
