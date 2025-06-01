package com.tambuchosecretdev.quickzenapp.utils

import android.util.Log
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clase utilitaria para detectar y eliminar notas duplicadas
 */
class DuplicateNoteCleaner(private val repository: NoteRepository) {
    
    companion object {
        private const val TAG = "DuplicateNoteCleaner"
    }
    
    /**
     * Busca y elimina notas duplicadas
     * @return Número de duplicados eliminados
     */
    suspend fun cleanDuplicates(): Int = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener todas las notas activas
            val allNotes = repository.getAllActiveNotes()
            Log.d(TAG, "Buscando duplicados entre ${allNotes.size} notas activas")
            
            // 2. Agrupar por título+contenido para identificar duplicados
            val groupedNotes = allNotes.groupBy { "${it.title.trim()}_${it.content.trim()}" }
            
            // 3. Filtrar grupos con más de una nota (duplicados)
            val duplicateGroups = groupedNotes.filter { it.value.size > 1 }
            
            if (duplicateGroups.isEmpty()) {
                Log.d(TAG, "No se encontraron duplicados")
                return@withContext 0
            }
            
            Log.d(TAG, "Se encontraron ${duplicateGroups.size} grupos de notas duplicadas")
            var totalRemoved = 0
            
            // 4. Procesar cada grupo de duplicados
            duplicateGroups.forEach { (key, notes) ->
                // Ordenar notas: primero las que tienen cloudId, luego por fecha de modificación
                val sortedNotes = notes.sortedWith(
                    compareByDescending<Note> { !it.cloudId.isNullOrEmpty() }
                        .thenByDescending { it.modifiedDate }
                )
                
                // La primera nota es la que mantendremos
                val noteToKeep = sortedNotes.first()
                val notesToRemove = sortedNotes.drop(1)
                
                Log.d(TAG, "Manteniendo nota con ID ${noteToKeep.id}, eliminando ${notesToRemove.size} duplicados")
                
                // Eliminar duplicados
                notesToRemove.forEach { duplicateNote ->
                    try {
                        repository.deleteNotePermanently(duplicateNote.id)
                        totalRemoved++
                        Log.d(TAG, "Eliminada nota duplicada con ID: ${duplicateNote.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al eliminar nota duplicada: ${e.message}")
                    }
                }
                
                // Si la nota principal no tiene cloudId pero alguna de las eliminadas sí,
                // actualizar la nota principal con ese cloudId
                if (noteToKeep.cloudId.isNullOrEmpty()) {
                    val noteWithCloudId = notesToRemove.find { !it.cloudId.isNullOrEmpty() }
                    if (noteWithCloudId != null) {
                        val updatedNote = noteToKeep.copy(
                            cloudId = noteWithCloudId.cloudId,
                            needsSync = true
                        )
                        repository.updateNote(updatedNote)
                        Log.d(TAG, "Actualizada nota principal con cloudId de duplicado: ${noteWithCloudId.cloudId}")
                    }
                }
            }
            
            return@withContext totalRemoved
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar duplicados: ${e.message}", e)
            return@withContext 0
        }
    }
}
