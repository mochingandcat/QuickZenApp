package com.tambuchosecretdev.quickzenapp.firebase

// import com.google.firebase.firestore.QuerySnapshot // No se usa directamente en el código corregido, se puede quitar si no hay otros usos
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Clase auxiliar para manejar operaciones específicas de Firestore
 * Separada para mejorar la organización del código y facilitar el mantenimiento
 */
class FirestoreHelper(private val firestore: FirebaseFirestore) {

    private val auth = FirebaseAuth.getInstance()
    private var firestoreListener: ListenerRegistration? = null
    private val TAG = "FirestoreHelper"

    // Obtener el ID del usuario actual
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    enum class ChangeType {
        ADDED, MODIFIED, REMOVED
    }

    /**
     * Guarda una nota en Firestore con manejo mejorado de errores y timeouts
     * @return El cloudId de la nota guardada/actualizada, o null si falla.
     */
    suspend fun saveNote(note: Note): String? = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.w(TAG, "saveNote: Usuario no autenticado.")
            return@withContext null
        }

        try {
            Log.d(TAG, "Guardando nota en Firestore para usuario: $userId, Título: ${note.title}")

            val noteMap = hashMapOf<String, Any>(
                "title" to note.title,
                "content" to note.content,
                "modifiedDate" to note.modifiedDate,
                "createdDate" to note.createdDate,
                "isFavorite" to note.isFavorite,
                "isInTrash" to note.isInTrash,
                "colorId" to note.colorId,
            )
            note.categoryId?.let {
                noteMap["categoryId"] = it.toString()
            } // Firestore prefiere strings para IDs
            note.reminderDateTime?.let { noteMap["reminderDateTime"] = it }
            note.isLocked?.let {
                noteMap["isLocked"] = it
            } // Añadir isLocked si existe en el modelo Note

            // Usar estructura de colección segura: users/{userId}/notes/{noteId}
            val docRef = if (note.cloudId != null && note.cloudId!!.isNotBlank()) {
                Log.d(TAG, "Actualizando nota existente con cloudId: ${note.cloudId}")
                firestore.collection("users").document(userId).collection("notes").document(note.cloudId!!)
            } else {
                Log.d(TAG, "Creando nueva nota en Firestore")
                firestore.collection("users").document(userId).collection("notes").document()
            }

            withTimeoutOrNull(15000) {
                docRef.set(noteMap).await()
                Log.d(TAG, "Nota guardada/actualizada en Firestore con ID: ${docRef.id}")
                docRef.id // Retorna el ID del documento (nuevo o existente)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar nota en Firestore: ${e.message}", e)
            null
        }
    }

    /**
     * SOLUCIÓN RADICAL: Descarga todas las notas desde Firestore.
     * Método simplificado y directo para obtener todas las notas del servidor.
     */
    suspend fun downloadAllNotesFromServer(): List<Note> = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.e(TAG, "[RADICAL] downloadAllNotesFromServer: Usuario no autenticado. ABORTANDO.")
            return@withContext emptyList<Note>()
        }

        try {
            Log.d(
                TAG,
                "[RADICAL] DESCARGANDO TODAS LAS NOTAS desde Firestore para usuario: $userId"
            )

            // Usar Source.SERVER para forzar una consulta al servidor
            val snapshot = try {
                firestore.collection("users").document(userId).collection("notes")
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "[RADICAL] Error al obtener notas del servidor: ${e.message}", e)
                null
            }

            if (snapshot == null) {
                Log.e(TAG, "[RADICAL] No se pudieron obtener notas del servidor. ABORTANDO.")
                return@withContext emptyList<Note>()
            }

            val notes = snapshot.documents.mapNotNull { document ->
                try {
                    documentToNote(document)
                } catch (e: Exception) {
                    Log.e(TAG, "[RADICAL] Error al convertir documento a nota: ${document.id}", e)
                    null
                }
            }

            Log.d(TAG, "[RADICAL] ÉXITO: Se descargaron ${notes.size} notas del servidor")
            notes.forEach { note ->
                Log.d(TAG, "[RADICAL] Nota descargada - ID: ${note.cloudId}, Título: ${note.title}")
            }

            return@withContext notes
        } catch (e: Exception) {
            Log.e(TAG, "[RADICAL] ERROR FATAL al descargar notas: ${e.message}", e)
            return@withContext emptyList<Note>()
        }
    }

    /**
     * SOLUCIÓN RADICAL: Sube todas las notas locales a Firestore.
     * Método simplificado y directo para subir todas las notas al servidor.
     * @param notes Lista de notas locales a subir
     * @return Número de notas subidas exitosamente
     */
    suspend fun uploadAllNotesToServer(notes: List<Note>): Int = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.e(TAG, "[RADICAL] uploadAllNotesToServer: Usuario no autenticado. ABORTANDO.")
            return@withContext 0
        }

        if (notes.isEmpty()) {
            Log.w(TAG, "[RADICAL] uploadAllNotesToServer: Lista de notas vacía. Nada que subir.")
            return@withContext 0
        }

        Log.d(TAG, "[RADICAL] SUBIENDO ${notes.size} NOTAS a Firestore para usuario: $userId")

        var successCount = 0

        try {
            // Procesar cada nota individualmente para mayor robustez
            for (note in notes) {
                try {
                    val cloudId = saveNote(note)
                    if (cloudId != null) {
                        Log.d(
                            TAG,
                            "[RADICAL] Nota subida exitosamente - Título: ${note.title}, CloudID: $cloudId"
                        )
                        successCount++
                    } else {
                        Log.e(TAG, "[RADICAL] Error al subir nota - Título: ${note.title}")
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "[RADICAL] Error al procesar nota individual - Título: ${note.title}",
                        e
                    )
                    // Continuar con la siguiente nota a pesar del error
                }
            }

            Log.d(
                TAG,
                "[RADICAL] SUBIDA COMPLETADA: $successCount/${notes.size} notas subidas exitosamente"
            )
            return@withContext successCount
        } catch (e: Exception) {
            Log.e(TAG, "[RADICAL] ERROR FATAL al subir notas: ${e.message}", e)
            return@withContext successCount // Devolver el número de notas que se pudieron subir antes del error
        }
    }

    /**
     * Obtiene todas las notas desde Firestore para el usuario actual.
     * Método original mantenido por compatibilidad.
     */
    suspend fun getNotes(): List<Note> = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: return@withContext emptyList<Note>()
        Log.d(TAG, "Obteniendo notas para usuario: $userId")

        return@withContext firestore.collection("users").document(userId).collection("notes")
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                try {
                    documentToNote(document)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al convertir documento a nota: ${document.id}", e)
                    null
                }
            }
    }

    /**
     * Convierte un documento de Firestore a un objeto Note.
     */
    private fun documentToNote(document: DocumentSnapshot): Note? {
        return try {
            val cloudId = document.id
            val title = document.getString("title") ?: ""
            val content = document.getString("content") ?: ""
            val modifiedDate = document.getTimestamp("modifiedDate")?.toDate()?.time
                ?: document.getLong("modifiedDate") ?: System.currentTimeMillis()
            val createdDate = document.getTimestamp("createdDate")?.toDate()?.time
                ?: document.getLong("createdDate") ?: System.currentTimeMillis()
            val isFavorite = document.getBoolean("isFavorite") ?: false
            val isInTrash = document.getBoolean("isInTrash") ?: false
            val colorId = document.getString("colorId") ?: NoteAppColors.DEFAULT_COLOR_ID
            val categoryIdStr = document.getString("categoryId")
            val categoryId = categoryIdStr?.toLongOrNull()
            val reminderDateTime = document.getLong("reminderDateTime")
            val isLocked = document.getBoolean("isLocked") ?: false

            Note(
                id = 0,
                title = title,
                content = content,
                createdDate = createdDate,
                modifiedDate = modifiedDate,
                isInTrash = isInTrash,
                colorId = colorId,
                images = emptyList(),
                categoryId = categoryId,
                cloudId = cloudId,
                needsSync = false,
                reminderDateTime = reminderDateTime,
                isFavorite = isFavorite,
                isLocked = isLocked,
                userId=UUID.randomUUID().toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al convertir DocumentSnapshot a Note: ${document.id}", e)
            null
        }
    }

    /**
     * Elimina una nota de Firestore.
     * @return true si la eliminación fue exitosa, false en caso contrario.
     */
    suspend fun deleteNote(cloudId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.e(TAG, "deleteNote: Usuario no autenticado. ABORTANDO.")
            return@withContext false
        }

        if (cloudId.isBlank()) {
            Log.w(TAG, "deleteNote: cloudId está vacío.")
            return@withContext false
        }

        try {
            Log.d(TAG, "Eliminando nota de Firestore para usuario: $userId")
            withTimeoutOrNull(15000) {
                firestore.collection("users").document(userId).collection("notes")
                    .document(cloudId)
                    .delete()
                    .await()
            }
            Log.d(TAG, "Nota eliminada de Firestore: $cloudId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar nota de Firestore: $cloudId", e)
            false
        }
    }

    /**
     * Configura un listener en tiempo real para las notas del usuario actual.
     * @param onNoteEvent Callback para cuando una nota cambia
     * @return ListenerRegistration que se puede usar para remover el listener
     */
    fun setupRealtimeListener(onNoteEvent: (Note, ChangeType) -> Unit): ListenerRegistration? {
        val userId = currentUserId ?: run {
            Log.e(
                TAG,
                "setupRealtimeListener: Usuario no autenticado. No se puede configurar listener."
            )
            return null
        }

        Log.d(TAG, "Configurando listener en tiempo real para usuario: $userId")

        // Limpiar listener anterior si existe
        firestoreListener?.remove()
        firestoreListener = null

        try {
            // Configurar nuevo listener con más detalles de depuración - usando estructura jerárquica
            val collectionRef = firestore.collection("users").document(userId).collection("notes")
            Log.d(TAG, "Referencia a colección de notas: ${collectionRef.path}")

            firestoreListener =
                collectionRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, e ->
                    if (e != null) {
                        Log.e(TAG, "Error en listener de Firestore: ${e.message}", e)
                        return@addSnapshotListener
                    }

                    if (snapshots == null) {
                        Log.w(TAG, "Snapshot es nulo en listener de Firestore")
                        return@addSnapshotListener
                    }

                    Log.d(TAG, "Snapshot recibido con ${snapshots.documentChanges.size} cambios")

                    for (dc in snapshots.documentChanges) {
                        Log.d(
                            TAG,
                            "Procesando cambio de tipo: ${dc.type} para documento: ${dc.document.id}"
                        )

                        val note = documentToNote(dc.document)
                        if (note == null) {
                            Log.w(TAG, "No se pudo convertir documento a nota: ${dc.document.id}")
                            continue
                        }

                        // Verificar si es un cambio local pendiente
                        val isPendingWrite = dc.document.metadata.hasPendingWrites()
                        Log.d(
                            TAG,
                            "Nota ${note.cloudId} - ¿Tiene escrituras pendientes?: $isPendingWrite"
                        )

                        if (isPendingWrite) {
                            Log.d(TAG, "Cambio local ignorado para la nota: ${note.cloudId}")
                            continue
                        }

                        // Procesar el cambio según su tipo
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                Log.d(
                                    TAG,
                                    "Nota AÑADIDA desde Firestore: ${note.cloudId}, Título: ${note.title}"
                                )
                                onNoteEvent(note, ChangeType.ADDED)
                            }

                            DocumentChange.Type.MODIFIED -> {
                                Log.d(
                                    TAG,
                                    "Nota MODIFICADA desde Firestore: ${note.cloudId}, Título: ${note.title}"
                                )
                                onNoteEvent(note, ChangeType.MODIFIED)
                            }

                            DocumentChange.Type.REMOVED -> {
                                Log.d(
                                    TAG,
                                    "Nota ELIMINADA desde Firestore: ${note.cloudId}, Título: ${note.title}"
                                )
                                onNoteEvent(note, ChangeType.REMOVED)
                            }

                            else -> Log.d(TAG, "Tipo de cambio desconocido: ${dc.type}")
                        }
                    }
                }

            Log.d(
                TAG,
                "Listener de Firestore configurado exitosamente para notas del usuario: $userId"
            )
            return firestoreListener
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar listener de Firestore", e)
            return null
        }
    }

    /**
     * Actualiza una nota existente en Firestore. Similar a saveNote pero explícito para actualización.
     * @return true si la actualización fue exitosa, false en caso contrario.
     */
    suspend fun updateNoteInFirestore(note: Note): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.w(TAG, "updateNoteInFirestore: Usuario no autenticado.")
            return@withContext false
        }
        if (note.cloudId == null || note.cloudId!!.isBlank()) {
            Log.w(TAG, "updateNoteInFirestore: cloudId es nulo o vacío. No se puede actualizar.")
            return@withContext false
        }

        try {
            saveNote(note) != null
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar nota en Firestore: ${note.cloudId}", e)
            false
        }
    }

    /**
     * Limpia los recursos (listeners, etc.)
     */
    fun cleanup() {
        firestoreListener?.remove()
        firestoreListener = null
        Log.d(TAG, "Listener de Firestore removido y limpiado.")
    }

    /**
     * SOLUCIÓN RADICAL: Fuerza una actualización de las notas desde el servidor.
     * Este método es similar a downloadAllNotesFromServer() pero está diseñado para ser
     * llamado desde la interfaz de usuario cuando el usuario quiere forzar una actualización.
     * Si no se puede conectar al servidor, intenta obtener datos de la caché local.
     * @return Lista de notas actualizadas o null si hay un error
     */
    suspend fun forceRefreshNotes(): List<Note>? = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.e(TAG, "[RADICAL] forceRefreshNotes: Usuario no autenticado. ABORTANDO.")
            return@withContext null
        }
        
        try {
            Log.d(TAG, "[RADICAL] FORZANDO ACTUALIZACIÓN DE NOTAS para usuario: $userId")
            
            // No limpiamos la caché para mantener datos si hay problemas de conexión
            // firestore.clearPersistence().await() <- Removido para evitar borrar datos locales
            
            // Primero intentar obtener datos del servidor
            var snapshot = try {
                Log.d(TAG, "[RADICAL] Intentando obtener datos del SERVIDOR...")
                firestore.collection("users").document(userId).collection("notes")
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "[RADICAL] No se pudieron obtener notas del servidor: ${e.message}")
                Log.d(TAG, "[RADICAL] Intentando obtener datos de la CACHÉ local...")
                
                // Si falla, intentar obtener de la caché
                try {
                    firestore.collection("users").document(userId).collection("notes")
                        .get(com.google.firebase.firestore.Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Log.e(TAG, "[RADICAL] Error al obtener notas de la caché: ${cacheEx.message}", cacheEx)
                    null
                }
            }
            
            // Si ambos métodos fallaron
            if (snapshot == null) {
                // Último intento con Source.DEFAULT (intenta servidor, luego caché)
                Log.d(TAG, "[RADICAL] Intentando obtener datos con Source.DEFAULT...")
                try {
                    snapshot = firestore.collection("users").document(userId).collection("notes")
                        .get(com.google.firebase.firestore.Source.DEFAULT)
                        .await()
                } catch (e: Exception) {
                    Log.e(TAG, "[RADICAL] Error final al obtener notas: ${e.message}", e)
                    return@withContext null
                }
            }
            
            if (snapshot == null) {
                Log.e(TAG, "[RADICAL] No se pudieron obtener notas de ninguna fuente. ABORTANDO.")
                return@withContext null
            }
            
            val notes = snapshot.documents.mapNotNull { document ->
                try {
                    documentToNote(document)
                } catch (e: Exception) {
                    Log.e(TAG, "[RADICAL] Error al convertir documento a nota: ${document.id}", e)
                    null
                }
            }
            
            val source = if (snapshot.metadata.isFromCache) "CACHÉ LOCAL" else "SERVIDOR"
            Log.d(TAG, "[RADICAL] ÉXITO: Se obtuvieron ${notes.size} notas desde $source")
            return@withContext notes
        } catch (e: Exception) {
            Log.e(TAG, "[RADICAL] ERROR FATAL al obtener notas: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Migra notas de la estructura antigua (colección "notes") a la nueva estructura (users/{userId}/notes/{noteId})
     * @return Par con el número de notas migradas exitosamente y el número total de notas encontradas
     */
    suspend fun migrateNotesToUserStructure(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.e(TAG, "migrateNotesToUserStructure: Usuario no autenticado. ABORTANDO.")
            return@withContext Pair(0, 0)
        }
        
        try {
            Log.d(TAG, "Iniciando migración de notas para usuario: $userId")
            
            // 1. Obtener todas las notas de la estructura antigua
            val oldNotesSnapshot = try {
                firestore.collection("notes")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener notas antiguas: ${e.message}", e)
                return@withContext Pair(0, 0)
            }
            
            val totalNotes = oldNotesSnapshot.documents.size
            Log.d(TAG, "Se encontraron $totalNotes notas para migrar")
            
            if (totalNotes == 0) {
                Log.d(TAG, "No hay notas para migrar")
                return@withContext Pair(0, 0)
            }
            
            var successCount = 0
            
            // 2. Migrar cada nota a la nueva estructura
            for (oldDoc in oldNotesSnapshot.documents) {
                try {
                    val oldId = oldDoc.id
                    
                    // Crear documento en la nueva ubicación con el mismo ID
                    val newDocRef = firestore.collection("users").document(userId).collection("notes").document(oldId)
                    
                    // Preparar datos para la nueva ubicación
                    val noteData = oldDoc.data ?: continue
                    
                    // El userId ya no es necesario en el documento ya que está implícito en la ruta
                    noteData.remove("userId")
                    
                    // Guardar nota en la nueva ubicación
                    newDocRef.set(noteData).await()
                    
                    Log.d(TAG, "Nota migrada exitosamente: $oldId - ${noteData["title"]}")
                    successCount++
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error al migrar nota: ${oldDoc.id}", e)
                    // Continuar con la siguiente nota
                }
            }
            
            Log.d(TAG, "Migración completada: $successCount/$totalNotes notas migradas")
            return@withContext Pair(successCount, totalNotes)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error general en la migración: ${e.message}", e)
            return@withContext Pair(0, 0)
        }
    }

    /**
     * Elimina las notas de la estructura antigua después de confirmar que se migraron correctamente
     * @return Número de notas eliminadas
     */
    suspend fun deleteOldNotes(): Int = withContext(Dispatchers.IO) {
        val userId = currentUserId ?: run {
            Log.e(TAG, "deleteOldNotes: Usuario no autenticado. ABORTANDO.")
            return@withContext 0
        }
        
        try {
            // Obtener todas las notas de la estructura antigua
            val oldNotesSnapshot = firestore.collection("notes")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            var deletedCount = 0
            
            // Eliminar cada nota antigua
            for (oldDoc in oldNotesSnapshot.documents) {
                try {
                    firestore.collection("notes").document(oldDoc.id).delete().await()
                    deletedCount++
                    Log.d(TAG, "Nota antigua eliminada: ${oldDoc.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al eliminar nota antigua: ${oldDoc.id}", e)
                }
            }
            
            Log.d(TAG, "Limpieza completada: $deletedCount notas antiguas eliminadas")
            return@withContext deletedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar notas antiguas: ${e.message}", e)
            return@withContext 0
        }
    }

    companion object {
        private const val STATIC_TAG = "FirestoreHelperCompanion"
        // No se necesita la función listenToFirestoreNotes aquí, ya que se maneja como método de instancia.
    }
}
