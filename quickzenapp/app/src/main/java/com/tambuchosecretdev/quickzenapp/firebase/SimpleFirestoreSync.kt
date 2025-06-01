package com.tambuchosecretdev.quickzenapp.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.tambuchosecretdev.quickzenapp.data.AppDatabase
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Clase simplificada para sincronización con Firestore
 * Esta implementación usa solo Firestore y evita problemas de permisos con Realtime Database
 */
class SimpleFirestoreSync(private val context: Context) {
    private val TAG = "SimpleFirestoreSync"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Listener de sincronización en tiempo real
    private var notesListener: ListenerRegistration? = null
    
    // Colección única para todas las notas
    private val NOTES_COLLECTION = "QUICKZENNEW"
    
    // Bloqueo para sincronización
    private val syncLock = Any()
    private var isSyncing = false
    
    /**
     * Método principal para sincronizar notas
     * Este método realiza una sincronización bidireccional completa
     * y además configura un listener para cambios en tiempo real
     */
    suspend fun syncAllNotes(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando sincronización simplificada con Firestore...")
            
            // Verificar autenticación
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Error: Usuario no autenticado")
                return@withContext false
            }
            
            // 1. Descargar notas del servidor
            val serverNotes = downloadNotesFromServer(userId)
            Log.d(TAG, "Descargadas ${serverNotes.size} notas del servidor")
            
            // 2. Guardar notas del servidor en la base de datos local
            if (serverNotes.isNotEmpty()) {
                saveServerNotesToLocalDb(serverNotes)
            }
            
            // 3. Iniciar listener para cambios en tiempo real
            startRealtimeSync(userId)
            
            // 3. Obtener notas locales que necesitan sincronización
            val localNotes = getLocalNotesForSync()
            Log.d(TAG, "${localNotes.size} notas locales necesitan sincronización")
            
            // 4. Subir notas locales al servidor
            if (localNotes.isNotEmpty()) {
                uploadLocalNotesToServer(localNotes, userId)
            }
            
            Log.d(TAG, "Sincronización simplificada finalizada con éxito")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la sincronización simplificada: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Descarga todas las notas del servidor para el usuario actual
     * Con manejo de fallback a caché cuando no hay conexión
     */
    private suspend fun downloadNotesFromServer(userId: String): List<Note> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Descargando notas para usuario: $userId")
            
            // Primero intentar obtener datos del servidor
            var snapshot = try {
                Log.d(TAG, "Intentando obtener datos del SERVIDOR...")
                firestore.collection(NOTES_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Error al descargar notas del servidor: ${e.message}")
                Log.d(TAG, "Intentando obtener datos de la CACHÉ local...")
                
                // Si falla, intentar obtener de la caché
                try {
                    firestore.collection(NOTES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Log.e(TAG, "Error al obtener notas de la caché: ${cacheEx.message}")
                    null
                }
            }
            
            // Si ambos métodos fallaron
            if (snapshot == null) {
                Log.d(TAG, "Intentando obtener datos con Source.DEFAULT...")
                // Último intento con Source.DEFAULT (intenta servidor, luego caché)
                snapshot = try {
                    firestore.collection(NOTES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .get(Source.DEFAULT)
                        .await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error final al obtener notas: ${e.message}")
                    return@withContext emptyList()
                }
            }
            
            val source = if (snapshot?.metadata?.isFromCache!!) "CACHÉ LOCAL" else "SERVIDOR"
            Log.d(TAG, "Datos obtenidos desde: $source")
            
            val notes = snapshot.documents.mapNotNull { document ->
                try {
                    // Convertir documento a objeto Note
                    val title = document.getString("title") ?: ""
                    val content = document.getString("content") ?: ""
                    val createdDate = document.getLong("createdDate") ?: System.currentTimeMillis()
                    val modifiedDate = document.getLong("modifiedDate") ?: System.currentTimeMillis()
                    val isFavorite = document.getBoolean("isFavorite") ?: false
                    val isInTrash = document.getBoolean("isInTrash") ?: false
                    val colorId = document.getString("colorId") ?: NoteAppColors.DEFAULT_COLOR_ID
                    val isLocked = document.getBoolean("isLocked") ?: false
                    val categoryId = document.getString("categoryId")?.toLongOrNull()

                    Note(
                        id = 0, // Se asignará un ID local al insertarlo
                        title = title,
                        content = content,
                        createdDate = createdDate,
                        modifiedDate = modifiedDate,
                        isInTrash = isInTrash,
                        colorId = colorId,
                        categoryId = categoryId,
                        cloudId = document.id,
                        needsSync = false,
                        isFavorite = isFavorite,
                        isLocked = isLocked,
                        userId=UUID.randomUUID().toString() // Ya está sincronizada
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error al convertir documento a nota: ${document.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Descargadas ${notes.size} notas del servidor")
            return@withContext notes
        } catch (e: Exception) {
            Log.e(TAG, "Error al descargar notas del servidor: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Guarda las notas descargadas del servidor en la base de datos local
     */
    private suspend fun saveServerNotesToLocalDb(notes: List<Note>) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Guardando ${notes.size} notas del servidor en la base de datos local")
            val database = AppDatabase.getInstance(context)
            database.noteDao().insertOrUpdateAll(notes)
            Log.d(TAG, "Notas del servidor guardadas exitosamente en la base de datos local")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar notas del servidor en la base de datos local: ${e.message}", e)
        }
    }
    
    /**
     * Obtiene las notas locales que necesitan sincronización
     */
    private suspend fun getLocalNotesForSync(): List<Note> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Obteniendo notas locales que necesitan sincronización")
            val database = AppDatabase.getInstance(context)
            val notes = database.noteDao().getNotesForSync()
            Log.d(TAG, "Obtenidas ${notes.size} notas locales que necesitan sincronización")
            return@withContext notes
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener notas locales para sincronización: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Verifica si una nota ya existe en Firestore por título y contenido
     * para evitar crear duplicados. Método mejorado para mejor detección.
     */
    /**
     * Verifica si una nota ya existe en Firestore por título, contenido y otros metadatos
     * para evitar crear duplicados. Método mejorado con múltiples criterios.
     */
    private suspend fun findExistingNoteId(note: Note, userId: String): String? = withContext(Dispatchers.IO) {
        if (note.title.isBlank() && note.content.isBlank()) {
            Log.d(TAG, "Nota vacía, no se puede buscar duplicados")
            return@withContext null
        }

        try {
            // 1. Búsqueda por cloudId si existe
            note.cloudId?.let { cloudId ->
                if (cloudId.isNotEmpty()) {
                    try {
                        val doc = firestore.collection(NOTES_COLLECTION)
                            .document(cloudId)
                            .get(Source.SERVER)
                            .await()
                        
                        if (doc.exists()) {
                            Log.d(TAG, "Encontrada nota por cloudId: $cloudId")
                            return@withContext cloudId
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error al buscar por cloudId: ${e.message}")
                    }
                }
            }

            // 2. Búsqueda por título y contenido exactos (ignorando espacios)
            val trimmedTitle = note.title.trim()
            val trimmedContent = note.content.trim()
            
            // Solo buscar si tenemos suficiente contenido
            if (trimmedTitle.isNotEmpty() || trimmedContent.length > 20) {
                var query = firestore.collection(NOTES_COLLECTION)
                    .whereEqualTo("userId", userId)
                
                val conditions = mutableListOf<Query>()
                
                // Si el título no está vacío, buscar por título
                if (trimmedTitle.isNotEmpty()) {
                    conditions.add(firestore.collection(NOTES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("title", trimmedTitle))
                }
                
                // Si el contenido es significativo, buscar por contenido
                if (trimmedContent.length > 20) {
                    conditions.add(firestore.collection(NOTES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("content", trimmedContent))
                }
                
                // Buscar en paralelo
                val results = conditions.map { condition ->
                    try {
                        condition.get(Source.SERVER).await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error en consulta: ${e.message}")
                        null
                    }
                }
                
                // Procesar resultados
                results.forEach { snapshot ->
                    snapshot?.documents?.forEach { doc ->
                        val docTitle = doc.getString("title")?.trim() ?: ""
                        val docContent = doc.getString("content")?.trim() ?: ""
                        
                        // Verificar coincidencia exacta de título o contenido
                        if ((trimmedTitle.isNotEmpty() && docTitle == trimmedTitle) ||
                            (trimmedContent.length > 20 && docContent == trimmedContent)) {
                            Log.d(TAG, "Encontrada nota duplicada: ${doc.id}")
                            return@withContext doc.id
                        }
                        
                        // Verificar si es una versión anterior de la misma nota
                        val docModified = doc.getLong("modifiedDate") ?: 0L
                        if (docModified <= note.modifiedDate + 1000) { // Margen de 1 segundo
                            Log.d(TAG, "Encontrada versión anterior de la nota: ${doc.id}")
                            return@withContext doc.id
                        }
                    }
                }
            }
            
            // 3. Si no se encontró coincidencia, verificar por contenido similar
            if (trimmedContent.length > 50) {
                try {
                    val similarNotes = firestore.collection(NOTES_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .whereGreaterThanOrEqualTo("content", trimmedContent.take(20))
                        .whereLessThanOrEqualTo("content", "${trimmedContent.take(20)}~")
                        .get(Source.SERVER)
                        .await()
                    
                    for (doc in similarNotes.documents) {
                        val docContent = doc.getString("content")?.trim() ?: ""
                        // Si el contenido es muy similar (más del 90% de coincidencia)
                        if (calculateSimilarity(trimmedContent, docContent) > 0.9) {
                            Log.d(TAG, "Encontrada nota con contenido similar: ${doc.id}")
                            return@withContext doc.id
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error al buscar contenido similar: ${e.message}")
                }
            }
            
            Log.d(TAG, "No se encontró ninguna nota duplicada")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en findExistingNoteId: ${e.message}", e)
            return@withContext null
        }
    }
    
    // Función auxiliar para calcular similitud entre cadenas
    private fun calculateSimilarity(str1: String, str2: String): Double {
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        if (longer.length == 0) return 1.0
        
        return (longer.length - levenshteinDistance(longer, shorter)) / longer.length.toDouble()
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1[i - 1] != s2[j - 1]) {
                            newValue = minOf(newValue, lastValue, costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) {
                costs[s2.length] = lastValue
            }
        }
        return costs[s2.length]
    }
    
    /**
     * Sube las notas locales al servidor con prevención de duplicados mejorada
     */
    private suspend fun uploadLocalNotesToServer(notes: List<Note>, userId: String) = withContext(Dispatchers.IO) {
        if (notes.isEmpty()) {
            Log.d(TAG, "No hay notas para subir")
            return@withContext
        }
        
        Log.d(TAG, "🔄 Iniciando subida de ${notes.size} notas al servidor")
        var successCount = 0
        var duplicateCount = 0
        
        try {
            val database = AppDatabase.getInstance(context)
            val batch = firestore.batch()
            var batchSize = 0
            val maxBatchSize = 400 // Límite de operaciones por lote de Firestore
            
            // Procesar cada nota
            for (note in notes) {
                try {
                    // Verificar si la nota ya existe en el servidor
                    val existingNoteId = findExistingNoteId(note, userId)
                    
                    val noteRef = if (existingNoteId != null) {
                        // Actualizar nota existente
                        Log.d(TAG, "Actualizando nota existente: $existingNoteId")
                        duplicateCount++
                        firestore.collection(NOTES_COLLECTION).document(existingNoteId)
                    } else {
                        // Crear nueva nota
                        Log.d(TAG, "Creando nueva nota")
                        firestore.collection(NOTES_COLLECTION).document()
                    }
                    
                    // Crear datos de la nota
                    val noteData = hashMapOf(
                        "userId" to userId,
                        "title" to note.title,
                        "content" to note.content,
                        "createdDate" to (note.createdDate.takeIf { it > 0 } ?: System.currentTimeMillis()),
                        "modifiedDate" to System.currentTimeMillis(),
                        "isFavorite" to note.isFavorite,
                        "isInTrash" to note.isInTrash,
                        "colorId" to note.colorId,
                        "isLocked" to (note.isLocked ?: false)
                    )
                    
                    // Añadir categoryId si existe
                    note.categoryId?.let { noteData["categoryId"] = it.toString() }
                    
                    // Añadir operación al lote
                    batch.set(noteRef, noteData, SetOptions.merge())
                    batchSize++
                    
                    // Actualizar el cloudId local
                    if (existingNoteId == null) {
                        note.cloudId = noteRef.id
                        database.noteDao().updateNoteCloudId(note.id, noteRef.id)
                    } else {
                        note.cloudId = existingNoteId
                        database.noteDao().updateNoteCloudId(note.id, existingNoteId)
                    }
                    
                    // Marcar como sincronizada
                    database.noteDao().updateNoteSyncStatus(note.id, false)
                    
                    // Ejecutar lote si alcanzamos el tamaño máximo
                    if (batchSize >= maxBatchSize) {
                        batch.commit().await()
                        Log.d(TAG, "Lote de $batchSize notas subido")
                        successCount += batchSize
                        batchSize = 0
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar nota ${note.id}: ${e.message}", e)
                    // Continuar con la siguiente nota
                }
            }
            
            // Ejecutar cualquier operación restante
            if (batchSize > 0) {
                batch.commit().await()
                Log.d(TAG, "Lote final de $batchSize notas subido")
                successCount += batchSize
            }
            
            Log.d(TAG, "✅ Subida completada: $successCount notas actualizadas, $duplicateCount duplicados detectados")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error crítico durante la subida de notas: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Inicia un listener para sincronización en tiempo real desde Firestore
     * Este método configura un listener que detecta cambios en la colección de notas
     * y actualiza la base de datos local cuando detecta cambios en el servidor
     */
    private fun startRealtimeSync(userId: String) {
        try {
            // Detener listener anterior si existe
            stopRealtimeSync()
            
            Log.d(TAG, "🔔 Iniciando sincronización en tiempo real para usuario: $userId")
            
            // Usar una variable atómica para evitar procesamiento paralelo
            val isProcessing = AtomicBoolean(false)
            
            notesListener = firestore.collection(NOTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("modifiedDate", Query.Direction.DESCENDING) // Ordenar para recibir primero los más recientes
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "❌ Error en listener de tiempo real: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot == null || snapshot.metadata.isFromCache) {
                        // Ignorar actualizaciones desde caché local
                        return@addSnapshotListener
                    }
                    
                    // Evitar procesamiento en paralelo
                    if (isProcessing.getAndSet(true)) {
                        Log.d(TAG, "⚠️ Ya hay un proceso de sincronización en curso, omitiendo...")
                        return@addSnapshotListener
                    }
                    
                    try {
                        val changedNotes = mutableListOf<Note>()
                        var hasChanges = false
                        
                        // Procesar solo los cambios recientes (últimos 5 minutos)
                        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
                        
                        for (change in snapshot.documentChanges) {
                            try {
                                val doc = change.document
                                val data = doc.data
                                
                                // Ignorar cambios antiguos
                                val modifiedDate = data["modifiedDate"] as? Long ?: 0L
                                if (modifiedDate < fiveMinutesAgo) {
                                    Log.d(TAG, "Ignorando cambio antiguo: ${doc.id} (${Date(modifiedDate)})")
                                    continue
                                }
                                
                                // Verificar si es una eliminación
                                if (change.type == DocumentChange.Type.REMOVED) {
                                    // Manejar eliminación si es necesario
                                    Log.d(TAG, "Nota eliminada en tiempo real: ${doc.id}")
                                    continue
                                }
                                
                                // Crear objeto Note
                                val note = Note(
                                    id = 0, // Se asignará al guardar
                                    title = data["title"] as? String ?: "",
                                    content = data["content"] as? String ?: "",
                                    createdDate = (data["createdDate"] as? Long) ?: System.currentTimeMillis(),
                                    modifiedDate = modifiedDate,
                                    isInTrash = (data["isInTrash"] as? Boolean) ?: false,
                                    colorId = data["colorId"] as? String ?: NoteAppColors.DEFAULT_COLOR_ID,
                                    cloudId = doc.id,
                                    needsSync = false,
                                    isFavorite = (data["isFavorite"] as? Boolean) ?: false,
                                    isLocked = (data["isLocked"] as? Boolean) ?: false,
                                    userId=UUID.randomUUID().toString()
                                )
                                
                                // El acceso a la base de datos se debe hacer en la corrutina
                                // Añadimos la nota a la lista para verificar en la corrutina
                                changedNotes.add(note)
                                hasChanges = true
                                
                                if (change.type == DocumentChange.Type.ADDED) {
                                    Log.d(TAG, "➕ Nueva nota detectada: ${doc.id}")
                                } else {
                                    Log.d(TAG, "🔄 Cambio detectado para: ${doc.id}")
                                }
                                
                                // La verificación de versiones se hará en la corrutina
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error al procesar cambio: ${e.message}", e)
                            }
                        }
                        
                        // Guardar cambios si hay actualizaciones
                        if (hasChanges) {
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    Log.d(TAG, "Verificando y guardando ${changedNotes.size} cambios en tiempo real...")
                                    
                                    // Filtrar para evitar duplicados (solo notas nuevas o más recientes)
                                    val database = AppDatabase.getInstance(context)
                                    val uniqueNotes = mutableListOf<Note>()
                                    
                                    for (note in changedNotes) {
                                        // Buscar si la nota ya existe localmente
                                        val existingNote = database.noteDao().getNoteByCloudId(note.cloudId ?: "")
                                        
                                        if (existingNote == null) {
                                            // Es una nota nueva
                                            Log.d(TAG, "Guardando nueva nota: ${note.cloudId}")
                                            uniqueNotes.add(note)
                                        } else if (note.modifiedDate > existingNote.modifiedDate) {
                                            // La versión remota es más reciente
                                            Log.d(TAG, "Actualizando nota existente: ${note.cloudId}")
                                            uniqueNotes.add(note)
                                        } else {
                                            Log.d(TAG, "Omitiendo nota más antigua: ${note.cloudId}")
                                        }
                                    }
                                    
                                    if (uniqueNotes.isNotEmpty()) {
                                        saveServerNotesToLocalDb(uniqueNotes)
                                        Log.d(TAG, "✅ Guardados ${uniqueNotes.size} cambios")
                                    } else {
                                        Log.d(TAG, "No hay cambios nuevos para guardar")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error al guardar cambios: ${e.message}", e)
                                } finally {
                                    isProcessing.set(false)
                                }
                            }
                        } else {
                            Log.d(TAG, "No se detectaron cambios relevantes")
                            isProcessing.set(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error en procesamiento de cambios: ${e.message}", e)
                        isProcessing.set(false)
                    }
                }
            
            Log.d(TAG, "✅ Listener de tiempo real configurado correctamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al configurar sincronización en tiempo real: ${e.message}", e)
        }
    }
    
    /**
     * Detiene el listener de sincronización en tiempo real
     */
    fun stopRealtimeSync() {
        notesListener?.remove()  // Este método es correcto para ListenerRegistration
        notesListener = null
        Log.d(TAG, "Listener de sincronización en tiempo real detenido")
    }
    
    companion object {
        /**
         * Método estático para facilitar la sincronización desde cualquier parte de la aplicación
         */
        suspend fun sync(context: Context): Boolean {
            return SimpleFirestoreSync(context).syncAllNotes()
        }
    }
}
