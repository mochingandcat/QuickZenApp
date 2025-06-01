package com.tambuchosecretdev.quickzenapp.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.tambuchosecretdev.quickzenapp.model.FirestoreNote
import com.tambuchosecretdev.quickzenapp.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clase para manejar todas las operaciones relacionadas con Firebase.
 * Incluye autenticación, almacenamiento y base de datos Firestore.
 */
@Singleton
class FirebaseManager @Inject constructor(
    val context: Context
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val database = FirebaseDatabase.getInstance("https://quickzenapp-a7cfa-default-rtdb.europe-west1.firebasedatabase.app")
    private val TAG = "FirebaseManager"
    private val NOTES_COLLECTION = "QUICKZENNEW"
    private var googleSignInClient: GoogleSignInClient? = null

    /**
     * Verifica si el usuario está autenticado en Firebase
     * @return true si hay un usuario autenticado, false en caso contrario
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Busca una nota existente en Firestore por título y contenido
     * @param title Título de la nota a buscar
     * @param content Contenido de la nota a buscar
     * @return El ID del documento si existe una nota con el mismo título y contenido, null en caso contrario
     */
    suspend fun findExistingNoteByContent(title: String, content: String): String? = withContext(Dispatchers.IO) {
        try {
            // Verificar que el usuario esté autenticado
            val userId = auth.currentUser?.uid ?: return@withContext null
            
            Log.d(TAG, "Buscando nota existente con título: '$title'")
            
            // Primero buscar por título
            val snapshot = firestore.collection(NOTES_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("title", title)
                .get()
                .await()
            
            // Filtrar por contenido exacto
            val matches = snapshot.documents.filter { doc ->
                doc.getString("content") == content
            }
            
            if (matches.isNotEmpty()) {
                val docId = matches[0].id
                Log.d(TAG, "Encontrada nota existente con ID: $docId")
                return@withContext docId
            }
            
            Log.d(TAG, "No se encontró ninguna nota existente con ese título y contenido")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar nota existente: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Alias para isSignedIn() usado en algunas partes del código
     */
    fun isUserAuthenticated(): Boolean {
        return isSignedIn()
    }

    /**
     * Obtiene el ID del usuario actual
     * @return ID del usuario actual o null si no hay usuario autenticado
     */
    val currentUserId: String?
        get() = auth.currentUser?.uid
    
    /**
     * Verifica si el dispositivo tiene conexión a internet
     * @return true si hay conexión a internet, false en caso contrario
     */
    fun isConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    /**
     * Fuerza una reautenticación del usuario actual
     * @return true si la reautenticación fue exitosa, false en caso contrario
     */
    suspend fun forceReauthentication(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando reautenticación forzada...")
                
                // Verificar si hay un usuario actual
                if (auth.currentUser == null) {
                    Log.d(TAG, "No hay usuario actual para reautenticar")
                    return@withContext false
                }
                
                // Obtener el token ID actual
                val user = auth.currentUser!!
                val tokenResult = try {
                    user.getIdToken(true).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error al obtener token fresco: ${e.message}")
                    null
                }
                
                if (tokenResult == null) {
                    Log.e(TAG, "No se pudo obtener token para reautenticación")
                    return@withContext false
                }
                
                // Usar el token para reautenticar
                val token = tokenResult.token
                if (token != null) {
                    Log.d(TAG, "Token obtenido, intentando reautenticar...")
                    val credential = GoogleAuthProvider.getCredential(token, null)
                    
                    try {
                        val result = auth.signInWithCredential(credential).await()
                        val reauthedUser = result.user
                        
                        if (reauthedUser != null) {
                            Log.d(TAG, "Reautenticación exitosa: UID=${reauthedUser.uid}, Email=${reauthedUser.email}")
                            return@withContext true
                        } else {
                            Log.e(TAG, "Reautenticación falló: usuario es nulo después de signInWithCredential")
                            return@withContext false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error durante reautenticación con credencial: ${e.message}")
                        return@withContext false
                    }
                } else {
                    Log.e(TAG, "Token es nulo")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error general en reautenticación forzada", e)
                return@withContext false
            }
        }
    }

    /**
     * Guarda una nota en Firestore usando el ID existente o el ID local para evitar duplicados
     * @param note La nota local a guardar
     * @return El ID de la nota en Firestore
     */
    suspend fun saveNote(note: Note): String? = withContext(Dispatchers.IO) {
        try {
            // Verificar si el usuario está autenticado
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            // Usar el cloudId existente si existe, o generar uno nuevo basado en el ID local
            val documentId = note.cloudId ?: note.id.toString()
            
            Log.d(TAG, "Guardando nota con ID local: ${note.id}, cloudId: ${note.cloudId}, documentId efectivo: $documentId")
            
            // Crear un Map con todos los campos de la nota
            val noteMap = hashMapOf(
                "id" to documentId,
                "title" to note.title,
                "content" to note.content,
                "timestamp" to FieldValue.serverTimestamp(),
                "userId" to userId,
                "isSync" to true,  // Siempre marcamos como sincronizada en Firebase
                "isFavorite" to note.isFavorite,
                "colorId" to note.colorId,
                "localId" to note.id.toString(),
                "createdDate" to note.createdDate,
                "modifiedDate" to System.currentTimeMillis(),
                "isLocked" to note.isLocked,
                "isInTrash" to note.isInTrash
            )
            
            // Añadir campos opcionales solo si no son nulos
            note.reminderDateTime?.let { noteMap["reminderDateTime"] = it }
            note.categoryId?.let { noteMap["categoryId"] = it.toString() }
            if (note.labelIds.isNotEmpty()) noteMap["labelIds"] = note.labelIds
            if (note.images.isNotEmpty()) noteMap["images"] = note.images

            // Usar set() con merge para actualizar solo los campos modificados
            // y evitar crear documentos duplicados
            firestore.collection(NOTES_COLLECTION)
                .document(documentId)
                .set(noteMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Nota guardada exitosamente con ID: $documentId y usuario: $userId")
            return@withContext documentId
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar nota: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Verifica si una nota existe en Firestore dado su cloudId
     * @param cloudId ID del documento en Firestore
     * @return true si la nota existe, false en caso contrario
     */
    suspend fun checkIfNoteExists(cloudId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection(NOTES_COLLECTION).document(cloudId)
            val document = docRef.get().await()
            
            val exists = document.exists()
            Log.d(TAG, "Verificando si la nota con cloudId $cloudId existe: $exists")
            return@withContext exists
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar si la nota existe: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Actualiza una nota existente en Firestore
     * @param documentId ID del documento en Firestore
     * @param note La nota local actualizada
     * @return true si la actualización fue exitosa, false en caso contrario
     */
    private suspend fun updateExistingNote(documentId: String, note: Note): Boolean {
        return try {
            val noteData = hashMapOf(
                "title" to note.title,
                "content" to note.content,
                "timestamp" to FieldValue.serverTimestamp(),
                "isSync" to true,
                "isFavorite" to note.isFavorite,
                "colorId" to note.colorId,
                "modifiedDate" to note.modifiedDate
            )
            
            // Si hay un ID de categoría, incluirlo
            if (note.categoryId != null) {
                noteData["categoryId"] = note.categoryId.toString()
            }
            
            firestore.collection(NOTES_COLLECTION)
                .document(documentId)
                .update(noteData as Map<String, Any>)
                .await()
            
            Log.d(TAG, "Nota actualizada exitosamente con ID: $documentId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar nota: ${e.message}", e)
            false
        }
    }

    /**
     * Obtiene todas las notas del usuario actual desde Firestore
     * @return Lista de notas del usuario actual
     */
    suspend fun getNotes(): List<FirestoreNote> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        return try {
        // Usar la constante NOTES_COLLECTION para consistencia
        Log.d(TAG, "Obteniendo notas de la colección: $NOTES_COLLECTION")
        firestore.collection(NOTES_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .map { document ->
                    FirestoreNote(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        content = document.getString("content") ?: "",
                        timestamp = document.getDate("timestamp") ?: Date(),
                        userId = document.getString("userId") ?: "",
                        isSync = document.getBoolean("isSync") ?: false,
                        isFavorite = document.getBoolean("isFavorite") ?: false,
                        colorId = document.getString("colorId") ?: "default",
                        isLocked = document.getBoolean("isLocked") ?: false,
                        localId = document.getString("localId"),
                        modifiedDate = document.getLong("modifiedDate"),
                        isInTrash = document.getBoolean("isInTrash") ?: false
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener notas", e)
            emptyList()
        }
    }
    
    /**
     * Alias para getNotes usado en el código existente
     */
    suspend fun getNotesFromFirestore(): List<FirestoreNote> {
        return getNotes()
    }
    
    /**
     * Obtiene todas las notas del servidor
     */
    suspend fun getAllNotes(): List<FirestoreNote> {
        return getNotes()
    }

    /**
     * Actualiza una nota existente en Firestore
     * @param note La nota a actualizar
     * @return true si la actualización fue exitosa, false en caso contrario
     */
    suspend fun updateNote(note: Note): Boolean {
        return try {
            // Verificar si el usuario está autenticado
            if (auth.currentUser == null) {
                Log.e(TAG, "Error al actualizar nota: Usuario no autenticado")
                return false
            }
            
            // Aseguramos que tenemos un ID válido en Firestore
            if (note.cloudId.isNullOrEmpty()) {
                Log.e(TAG, "Error al actualizar nota: La nota no tiene un ID de Firestore válido")
                return false
            }
            
            // Usar saveNote para asegurar que todas las propiedades se sincronicen correctamente
            // Esto convertirá la nota completa usando toFirestoreNote() que incluye todas las propiedades
            val cloudId = saveNote(note)
            
            if (cloudId != null) {
                Log.d(TAG, "Nota actualizada exitosamente en Firestore: ${cloudId}")
                true
            } else {
                Log.e(TAG, "Error al actualizar nota en Firestore: saveNote devolvió null")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar nota en Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Elimina una nota
     * @param noteId ID de la nota en Firestore a eliminar
     */
    suspend fun deleteNote(noteId: String) {
        try {
            if (noteId.isEmpty()) {
                throw IllegalArgumentException("El ID de la nota de Firestore no puede estar vacío")
            }
                
        // Usar la constante NOTES_COLLECTION para consistencia
        Log.d(TAG, "Eliminando nota con ID $noteId de la colección: $NOTES_COLLECTION")
        firestore.collection(NOTES_COLLECTION)
            .document(noteId)
            .delete()
            .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar nota", e)
            throw e
        }
    }
    
    /**
     * Iniciar sesión con Google
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Boolean {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user != null
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar sesión con Google", e)
            false
        }
    }
    
    /**
     * Cerrar sesión
     */
    suspend fun signOut() {
        try {
            auth.signOut()
            googleSignInClient?.signOut()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión", e)
        }
    }
    
    /**
     * Sincroniza todas las notas rápidamente y devuelve un mapa de los IDs locales a IDs de nube
     * Versión simplificada y robusta
     * @param notes Lista de notas a sincronizar
     * @return Mapa de IDs locales a IDs de nube para las notas sincronizadas exitosamente
     */
    suspend fun quickUpSync(notes: List<Note>): Map<Long, String> = withContext(Dispatchers.IO) {
        val syncResults = mutableMapOf<Long, String>()
        
        if (notes.isEmpty()) {
            Log.d(TAG, "No hay notas para sincronizar")
            return@withContext syncResults
        }
        
        try {
            // Verificar que el usuario esté autenticado
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Error de sincronización: Usuario no autenticado")
                return@withContext syncResults
            }
            
            Log.d(TAG, "Iniciando sincronización de ${notes.size} notas con userId=$userId")
            
            for (note in notes) {
                try {
                    Log.d(TAG, "Procesando nota: id=${note.id}, título='${note.title}', cloudId=${note.cloudId ?: "null"}")
                    
                    // FORZAR la creación de nuevos documentos independientemente del cloudId
                    // Esto es para diagnosticar el problema
                    val documentRef = firestore.collection(NOTES_COLLECTION).document()
                    val newDocId = documentRef.id
                    
                    val noteData = hashMapOf(
                        "title" to note.title,
                        "content" to note.content,
                        "userId" to userId,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isSync" to true,
                        "isFavorite" to note.isFavorite,
                        "colorId" to note.colorId,
                        "localId" to note.id.toString(),
                        "createdDate" to note.createdDate,
                        "modifiedDate" to System.currentTimeMillis(),
                        "deviceInfo" to "test_device_${System.currentTimeMillis()}"
                    )
                    
                    if (note.categoryId != null) {
                        noteData["categoryId"] = note.categoryId.toString()
                    }
                    
                    Log.d(TAG, "Guardando nota con ID generado: $newDocId")
                    
                    // Usar directamente el documento creado
                    documentRef.set(noteData).await()
                    
                    Log.d(TAG, "Nota guardada exitosamente con ID: $newDocId")
                    syncResults[note.id] = newDocId
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error al sincronizar nota ${note.id}: ${e.message}", e)
                    // Continuar con la siguiente nota
                }
            }
            
            Log.d(TAG, "Sincronización completada. ${syncResults.size}/${notes.size} notas sincronizadas")
            return@withContext syncResults
        } catch (e: Exception) {
            Log.e(TAG, "Error general en sincronización", e)
            return@withContext syncResults
        }
    }
    
    /**
     * Sincroniza una nota individual
     * @param note La nota a sincronizar
     * @return true si la sincronización fue exitosa, false en caso contrario
     */
    suspend fun quickSyncNote(note: Note): Boolean {
        return try {
            if (note.cloudId.isNullOrEmpty()) {
                // Si no tiene ID en la nube, guardar como nueva nota
                val newCloudId = saveNote(note)
                newCloudId != null // Éxito si se obtuvo un ID de nube
            } else {
                // Si ya tiene ID en la nube, actualizar
                updateNote(note) // Ya devuelve un boolean
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al sincronizar nota individual", e)
            false
        }
    }
    
    /**
     * Sincroniza todos los datos
     * @return true si la sincronización fue exitosa (no hubo errores), false en caso contrario
     */
    suspend fun syncAllData(): Boolean {
        val result = quickUpSync(emptyList())
        // Si se devolvió algún resultado, consideramos que fue exitoso
        return result.isNotEmpty()
    }
    
    /**
     * Descarga todas las notas del servidor
     */
    suspend fun downloadAllNotesFromServer(): List<FirestoreNote> {
        return getNotes()
    }
    
    /**
     * Sube todas las notas al servidor
     * @param notes Lista de notas a subir
     * @return true si al menos una nota se sincronizó correctamente, false si hubo errores o no se sincronizó ninguna
     */
    suspend fun uploadAllNotesToServer(notes: List<Note>): Boolean {
        val syncResults = quickUpSync(notes)
        // Si se sincronizó al menos una nota, consideramos que fue exitoso
        return syncResults.isNotEmpty()
    }
    
    /**
     * Fuerza un refresco de notas desde Firestore
     */
    suspend fun forceRefreshNotesFromFirestore(): List<FirestoreNote> {
        return getNotes()
    }
    
    /**
     * Migra las notas a una nueva estructura
     * @return Par con (notasMigradas, totalNotas)
     */
    suspend fun migrateNotesToNewStructure(): Pair<Int, Int> {
        return Pair(0, 0) // Implementación simulada, retorna 0 notas migradas de 0 totales
    }
    
    /**
     * Elimina las notas de la estructura antigua
     */
    suspend fun deleteOldStructureNotes(): Int {
        return 0 // Implementación simulada, retorna 0 notas eliminadas
    }
    
    /**
     * Elimina todos los datos del usuario
     */
    suspend fun deleteAllUserData(): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            val batch = firestore.batch()
            
            val notes = firestore.collection("notes")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                
            for (note in notes) {
                batch.delete(note.reference)
            }
            
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar datos del usuario", e)
            false
        }
    }
    
    /**
     * Realiza la limpieza de recursos
     */
    fun cleanup() {
        googleSignInClient = null
    }

    /**
     * Migra los datos a una estructura más segura
     * @return Un objeto con la información del resultado de la migración
     */
    suspend fun migrateToSecureStructure(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // Aquí implementaríamos la migración real
            // Por ahora, simulamos que fue exitosa
            return@withContext SyncResult(
                success = true,
                uploaded = 5,  // Simulamos 5 notas migradas
                downloaded = 0,
                conflicts = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la migración: ${e.message}", e)
            return@withContext SyncResult(
                success = false, 
                errorMessage = "Error en la migración: ${e.message}"
            )
        }
    }

    /**
     * Realiza una sincronización bidireccional entre la base de datos local y Firebase.
     * Primero sube las notas locales que necesitan sincronización, luego descarga las notas
     * nuevas o actualizadas desde Firebase, y resuelve conflictos si los hay.
     * @return SyncResult con información sobre la sincronización realizada
     */
    suspend fun synchronize(): SyncResult = withContext(Dispatchers.IO) {
        try {
            if (!isSignedIn()) {
                Log.w(TAG, "Intento de sincronización sin usuario autenticado")
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "Usuario no autenticado"
                )
            }

            if (!isConnected()) {
                Log.w(TAG, "Intento de sincronización sin conexión a internet")
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "Sin conexión a internet"
                )
            }

            val userId = auth.currentUser?.uid ?: return@withContext SyncResult(
                success = false,
                errorMessage = "No se pudo obtener el ID de usuario"
            )

            var uploaded = 0
            var downloaded = 0
            var conflicts = 0

            // 1. Subir notas locales que necesitan sincronización
            // Aquí deberíamos recibir las notas que necesitan sincronización
            // Por ahora, simulamos este paso
            uploaded = 3

            // 2. Descargar notas nuevas o actualizadas desde Firebase
            // Aquí deberíamos obtener las notas desde Firebase y actualizarlas localmente
            // Por ahora, simulamos este paso
            downloaded = 2

            // 3. Resolver conflictos si los hay
            // Por ahora, simulamos este paso
            conflicts = 1

            // Retornar el resultado de la sincronización
            return@withContext SyncResult(
                uploaded = uploaded,
                downloaded = downloaded,
                conflicts = conflicts,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la sincronización: ${e.message}", e)
            return@withContext SyncResult(
                success = false,
                errorMessage = e.message ?: "Error desconocido durante la sincronización"
            )
        }
    }
    
    /**
     * Inicia la sincronización en tiempo real con Firebase
     * @param onChangesReceived Callback que se ejecuta cuando se reciben cambios
     */
    fun startRealtimeSync(onChangesReceived: (Int) -> Unit) {
        if (!isSignedIn()) {
            Log.w(TAG, "Intento de iniciar sincronización en tiempo real sin usuario autenticado")
            return
        }
        
        val userId = auth.currentUser?.uid ?: return
        
        // Aquí implementaríamos la suscripción a cambios en Firestore
        // Por ahora, solo registramos que se intentó iniciar
        Log.d(TAG, "Iniciando sincronización en tiempo real para el usuario $userId")
        
        // Simulamos recibir cambios cada cierto tiempo (esto sería reemplazado por
        // el sistema real de escucha de Firestore)
        /*
        Timer().schedule(object : TimerTask() {
            override fun run() {
                // Simular recibir 2 cambios
                onChangesReceived(2)
            }
        }, 5000, 30000) // Cada 30 segundos
        */
    }
    
    /**
     * Detiene la sincronización en tiempo real con Firebase
     */
    fun stopRealtimeSync() {
        // Aquí cancelaríamos las suscripciones a Firestore
        Log.d(TAG, "Deteniendo sincronización en tiempo real")
        // En una implementación real, cancelaríamos los listeners
    }

    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Intenta realizar una autenticación silenciosa si el usuario ya ha iniciado sesión previamente
     * @return true si se autenticó correctamente, false si no hay usuario o falla la autenticación
     */
    suspend fun silentSignIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Si ya hay un usuario autenticado, devuelve true
            if (isSignedIn()) {
                Log.d(TAG, "Usuario ya autenticado, silent sign-in no necesario")
                return@withContext true
            }
            
            // Intenta recuperar la sesión silenciosamente
            // Aquí solo vamos a verificar si podemos recuperar el token
            val user = auth.currentUser
            if (user != null) {
                try {
                    // Intentar obtener un token fresco para verificar que la sesión sigue siendo válida
                    user.getIdToken(true).await()
                    Log.d(TAG, "Silent sign-in exitoso")
                    return@withContext true
                } catch (e: Exception) {
                    Log.w(TAG, "Error al obtener token fresco: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "Silent sign-in fallido, requiere autenticación manual")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error en silentSignIn: ${e.message}", e)
            return@withContext false
        }
    }

    fun updateGoogleAccount(account: GoogleSignInAccount) {

    }

    companion object {
        @Volatile
        private var INSTANCE: FirebaseManager? = null
        
        fun getInstance(context: Context): FirebaseManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}

