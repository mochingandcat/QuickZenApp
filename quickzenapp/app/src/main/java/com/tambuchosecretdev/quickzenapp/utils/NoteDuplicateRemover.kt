package com.tambuchosecretdev.quickzenapp.utils

import android.util.Log
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clase utilitaria para detectar y eliminar notas duplicadas
 */
class NoteDuplicateRemover(private val repository: NoteRepository) {
    
    companion object {
        private const val TAG = "NoteDuplicateRemover"
    }
    
    /**
     * Elimina notas duplicadas basándose en el contenido (título + texto)
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
            duplicateGroups.forEach { (key, notes) ->
                Log.d(TAG, "Encontrado grupo de duplicados: $key con ${notes.size} notas")
                
                // Ordenar notas: primero las que tienen cloudId, luego por fecha de modificación
                val sortedNotes = notes.sortedWith(
                    compareByDescending<Note> { !it.cloudId.isNullOrEmpty() }
                        .thenByDescending { it.modifiedDate }
                )
                
                // Mantener la primera nota (la mejor según nuestros criterios)
                val noteToKeep = sortedNotes.first()
                val notesToRemove = sortedNotes.drop(1)
                
                Log.d(TAG, "Manteniendo nota ID: ${noteToKeep.id}, eliminando ${notesToRemove.size} duplicados")
                
                // Eliminar duplicados
                notesToRemove.forEach { duplicateNote ->
                    repository.deleteNotePermanently(duplicateNote.id)
                    totalRemoved++
                    Log.d(TAG, "Eliminada nota duplicada ID: ${duplicateNote.id}")
                }
                
                // Si la nota principal no tiene cloudId pero alguna eliminada sí,
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
            
            return@withContext totalRemoved
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar duplicados", e)
            return@withContext 0
        }
    }
}
