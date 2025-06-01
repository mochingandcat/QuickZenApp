package com.tambuchosecretdev.quickzenapp.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tambuchosecretdev.quickzenapp.data.AppDatabase
import com.tambuchosecretdev.quickzenapp.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Clase para solucionar problemas de sincronización
 * Esta implementación corrige los problemas de inconsistencia en las colecciones de Firestore
 */
class SyncFix(private val context: Context) {
    private val TAG = "SyncFix"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Colección única para todas las notas
    private val NOTES_COLLECTION = "notes"
    
    /**
     * Método principal para sincronizar notas
     * Este método realiza una sincronización bidireccional completa
     */
    suspend fun syncAllNotes(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando sincronización completa...")
            
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
            
            // 3. Obtener notas locales que necesitan sincronización
            val localNotes = getLocalNotesForSync()
            Log.d(TAG, "${localNotes.size} notas locales necesitan sincronización")
            
            // 4. Subir notas locales al servidor
            if (localNotes.isNotEmpty()) {
                uploadLocalNotesToServer(localNotes, userId)
            }
            
            Log.d(TAG, "Sincronización completa finalizada con éxito")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la sincronización: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Descarga todas las notas del servidor para el usuario actual
     */
    private suspend fun downloadNotesFromServer(userId: String): List<Note> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Descargando notas del servidor para usuario: $userId")
            
            // Consultar la colección de notas filtrando por userId
            val snapshot = firestore.collection(NOTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .get(Source.SERVER)
                .await()
            
            val notes = snapshot.documents.mapNotNull { document ->
                try {
                    // Convertir documento a objeto Note
                    val title = document.getString("title") ?: ""
                    val content = document.getString("content") ?: ""
                    val createdDate = document.getLong("createdDate") ?: System.currentTimeMillis()
                    val modifiedDate = document.getLong("modifiedDate") ?: System.currentTimeMillis()
                    val isFavorite = document.getBoolean("isFavorite") ?: false
                    val isInTrash = document.getBoolean("isInTrash") ?: false
                    val colorId = document.getLong("colorId")?.toInt() ?: 0
                    val isLocked = document.getBoolean("isLocked") ?: false
                    val categoryId = document.getString("categoryId")?.toLongOrNull()
                    
                    Note(
                        id = 0, // Se asignará un ID local al insertarlo
                        title = title,
                        content = content,
                        createdDate = createdDate,
                        modifiedDate = modifiedDate,
                        isInTrash = isInTrash,
                        colorId =colorId.toString(),
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
     * Sube las notas locales al servidor
     */
    private suspend fun uploadLocalNotesToServer(notes: List<Note>, userId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Subiendo ${notes.size} notas locales al servidor")
            val database = AppDatabase.getInstance(context)
            
            for (note in notes) {
                try {
                    // Preparar datos para Firestore
                    val noteData = hashMapOf(
                        "title" to note.title,
                        "content" to note.content,
                        "createdDate" to note.createdDate,
                        "modifiedDate" to note.modifiedDate,
                        "isFavorite" to note.isFavorite,
                        "isInTrash" to note.isInTrash,
                        "colorId" to note.colorId,
                        ("isLocked" to note.isLocked ?: false) as Pair<Any, Any>,
                        "userId" to userId
                    )
                    
                    note.categoryId?.let { noteData["categoryId"] = it.toString() }
                    
                    // Referencia al documento en Firestore
                    val docRef = if (note.cloudId != null && note.cloudId!!.isNotEmpty()) {
                        // Actualizar nota existente
                        firestore.collection(NOTES_COLLECTION).document(note.cloudId!!)
                    } else {
                        // Crear nueva nota
                        firestore.collection(NOTES_COLLECTION).document()
                    }
                    
                    // Guardar en Firestore
                    docRef.set(noteData).await()
                    
                    // Actualizar estado de sincronización en la base de datos local
                    database.noteDao().updateNoteSyncStatus(note.id, docRef.id)
                    
                    Log.d(TAG, "Nota subida exitosamente: ${note.id} -> ${docRef.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al subir nota ${note.id}: ${e.message}", e)
                    // Continuar con la siguiente nota
                }
            }
            
            Log.d(TAG, "Subida de notas locales completada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al subir notas locales al servidor: ${e.message}", e)
        }
    }

    companion object {
        /**
         * Método estático para facilitar la sincronización desde cualquier parte de la aplicación
         */
        suspend fun sync(context: Context): Boolean {
            return SyncFix(context).syncAllNotes()
        }
    }
}
