package com.tambuchosecretdev.quickzenapp.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tambuchosecretdev.quickzenapp.model.FirestoreNote
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.toLocalNote
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
import com.tambuchosecretdev.quickzenapp.firebase.SyncInfo
import com.tambuchosecretdev.quickzenapp.firebase.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gestor de sincronización que monitorea y administra la sincronización entre
 * el dispositivo local y Firebase Cloud.
 */
class SyncManager(private val context: Context, private val firebaseManager: FirebaseManager) {
    // Repositorio de notas que se inicializará más tarde
    private var noteRepository: com.tambuchosecretdev.quickzenapp.repository.NoteRepository? = null
    
    /**
     * Establece el repositorio de notas para operaciones de sincronización
     */
    fun setNoteRepository(repository: com.tambuchosecretdev.quickzenapp.repository.NoteRepository) {
        this.noteRepository = repository
    }
    
    private val _syncInfo = MutableStateFlow(SyncInfo())
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()
    
    private val pendingChangesCount = AtomicInteger(0)
    
    // Listeners y registros para limpieza
    private var syncListener: ListenerRegistration? = null
    private var firestoreListener: ListenerRegistration? = null
    
    private val firestore = FirebaseFirestore.getInstance()
    // auth instance will be exposed from firebaseManager
    val auth: FirebaseAuth get() = firebaseManager.auth
    
    // Obtener un ID único para este dispositivo (para mostrar entre qué dispositivos se sincronizan los datos)
    // Método para verificar y actualizar el estado de la conexión
    fun updateConnectionState() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        _syncInfo.update {
            it.copy(
                isOnline = isConnected,
                state = if (isConnected) it.state else SyncState.ERROR_CONNECTION
            )
        }
        Log.d("SyncManager", "Connection state updated. Is connected: $isConnected")
    }

    // Método para verificar el estado de la sincronización (puede ser más complejo)
    fun checkSyncStatus() {
        // Placeholder: Lógica para verificar si hay cambios pendientes, etc.
        // Esto podría implicar verificar noteRepository o firebaseManager
        if (noteRepository == null) {
            Log.w("SyncManager", "checkSyncStatus called but noteRepository is null.")
            _syncInfo.update { it.copy(state = SyncState.ERROR_SYNC, syncError = "Error: Repositorio no disponible") }
            return
        }
        // Aquí iría la lógica real, por ahora solo un log
        Log.d("SyncManager", "Checking sync status...")
        // Ejemplo: Actualizar syncInfo basado en alguna lógica
        // _syncInfo.update { it.copy(syncState = SyncState.SYNCED, lastSyncMessage = "Sincronización verificada") }
    }

    private val deviceId: String by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceModel = Build.MODEL
        "$deviceModel-${androidId.takeLast(6)}"
    }
    
    /**
     * Delega el inicio de sesión con Google a firebaseManager
     * @return true si el inicio de sesión fue exitoso
     */
    /**
     * Este método necesita ser actualizado para recibir una cuenta de Google
     * @param account La cuenta de Google para iniciar sesión
     */
    suspend fun performSignIn(account: GoogleSignInAccount): Boolean {
        return firebaseManager.signInWithGoogle(account)
    }
    
    /**
     * Delega el cierre de sesión a firebaseManager
     */
    suspend fun signOut() {
        firebaseManager.signOut()
    }
    
    /**
     * Delega la sincronización de todas las notas a firebaseManager
     * @return true si la sincronización fue exitosa
     */
    suspend fun performManualSync(): Boolean {
        return firebaseManager.syncAllData()
    }
    
    /**
     * Obtiene las notas desde Firestore a través de firebaseManager
     * @return lista de notas desde Firestore o lista vacía si hay error
     */
    suspend fun getNotesFromFirestore(): List<Note> {
        // Convertir la lista de FirestoreNote a Note usando la extensión toLocalNote
        return firebaseManager.getNotesFromFirestore()?.map { it.toLocalNote() } ?: emptyList()
    }
    
    /**
     * Guarda una nota en Firestore a través de firebaseManager
     * @param note La nota a guardar
     * @return ID de la nota en la nube o null si falla
     */
    suspend fun saveNote(note: Note): String? {
        return firebaseManager.saveNote(note)
    }
    
    /**
     * Elimina una nota de Firestore a través de firebaseManager
     * @param cloudId ID de la nota en la nube
     * @return true si la eliminación fue exitosa, false en caso contrario
     */
    suspend fun deleteNote(cloudId: String): Boolean {
        try {
            firebaseManager.deleteNote(cloudId)
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Limpia recursos cuando el ViewModel se destruye
     */
    fun cleanup() {
        syncListener?.remove()
        syncListener = null
        firestoreListener?.remove()
        firestoreListener = null
    }
}
