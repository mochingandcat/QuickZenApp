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
            val localNotesMap = localNotes.filter { it.cloudId != null }
                .associateBy { it.cloudId!! }
                
            // Obtener notas de Firestore
            val cloudNotes = firebaseManager.getNotes()
            
            if (cloudNotes.isNotEmpty()) {
                Log.d("NotesViewModel", "Procesando ${cloudNotes.size} notas de Firestore")
                
                val notesToProcess = mutableListOf<Note>()
                
                // Convertir y procesar cada nota de Firestore
                for (cloudNote in cloudNotes) {
                    val localNote = cloudNote.toLocalNote()
                    
                    // Verificar si ya existe localmente por cloudId
                    val existingLocalNote = localNotesMap[cloudNote.id]
                    
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
                        // La nota no existe localmente, agregarla como nueva
                        notesToProcess.add(localNote.copy(cloudId = cloudNote.id, needsSync = false))
                        Log.d("NotesViewModel", "Agregando nueva nota desde Firestore")
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
