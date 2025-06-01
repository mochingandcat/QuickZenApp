package com.tambuchosecretdev.quickzenapp

import android.util.Log
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clase utilitaria para detectar y eliminar notas duplicadas en la base de datos local.
 * Soluciona el problema de creación de múltiples copias de notas al sincronizar.
 */
class DuplicateNoteFixer(private val repository: NoteRepository) {

    companion object {
        private const val TAG = "DuplicateNoteFixer"
    }

    /**
     * Encuentra y elimina notas duplicadas, manteniendo solo la versión más reciente
     * o la que tenga un ID en la nube.
     * 
     * @return Número de duplicados eliminados
     */
    suspend fun removeDuplicates(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando eliminación de duplicados...")
            
            // 1. Obtener todas las notas activas
            val allNotes = repository.getAllActiveNotes()
            Log.d(TAG, "Total de notas activas: ${allNotes.size}")
            
            // 2. Agrupar notas por título+contenido para encontrar duplicados
            val groupedNotes = allNotes.groupBy { "${it.title.trim()}_${it.content.trim()}" }
            
            // 3. Filtrar solo los grupos que tienen más de una nota (duplicados)
            val duplicateGroups = groupedNotes.filter { it.value.size > 1 }
            Log.d(TAG, "Grupos con duplicados encontrados: ${duplicateGroups.size}")
            
            var totalRemoved = 0
            
            // 4. Procesar cada grupo de duplicados
            duplicateGroups.forEach { (key, notes) ->
                Log.d(TAG, "Procesando grupo de duplicados con clave: $key (${notes.size} notas)")
                
                // 4.1 Ordenar notas: primero las que tienen cloudId, luego por fecha de modificación descendente
                val sortedNotes = notes.sortedWith(
                    compareByDescending<Note> { !it.cloudId.isNullOrEmpty() }
                        .thenByDescending { it.modifiedDate }
                )
                
                // 4.2 Mantener la primera nota (la mejor según nuestros criterios) y eliminar el resto
                val noteToKeep = sortedNotes.first()
                val notesToRemove = sortedNotes.drop(1)
                
                Log.d(TAG, "Manteniendo nota con ID: ${noteToKeep.id}, eliminando ${notesToRemove.size} duplicados")
                
                // 4.3 Eliminar duplicados
                notesToRemove.forEach { duplicateNote ->
                    Log.d(TAG, "Eliminando duplicado con ID: ${duplicateNote.id}")
                    repository.deleteNoteById(duplicateNote.id)
                    totalRemoved++
                }
                
                // 4.4 Si la nota a mantener no tiene cloudId pero alguna de las eliminadas sí,
                // actualizar la nota principal con ese cloudId
                if (noteToKeep.cloudId.isNullOrEmpty()) {
                    val noteWithCloudId = notesToRemove.find { !it.cloudId.isNullOrEmpty() }
                    if (noteWithCloudId != null) {
                        val updatedNote = noteToKeep.copy(
                            cloudId = noteWithCloudId.cloudId,
                            needsSync = true
                        )
                        repository.updateNote(updatedNote)
                        Log.d(TAG, "Actualizada nota principal con cloudId: ${noteWithCloudId.cloudId}")
                    }
                }
            }
            
            Log.d(TAG, "Proceso completado. Total de duplicados eliminados: $totalRemoved")
            return@withContext totalRemoved
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar duplicados", e)
            return@withContext 0
        }
    }
    
    /**
     * Verifica si existe una nota con el mismo título y contenido
     * @return La nota existente si se encuentra, null en caso contrario
     */
    suspend fun findExistingNote(title: String, content: String): Note? = withContext(Dispatchers.IO) {
        try {
            val allNotes = repository.getAllActiveNotes()
            return@withContext allNotes.find { 
                it.title.trim() == title.trim() && it.content.trim() == content.trim() 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar nota existente", e)
            return@withContext null
        }
    }
}
