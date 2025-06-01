package com.tambuchosecretdev.quickzenapp.firebase

// Importar la definición correcta de SyncState
import com.tambuchosecretdev.quickzenapp.firebase.SyncState


/**
 * Clase que contiene información sobre el estado de sincronización
 */
data class SyncInfo(
    val isOnline: Boolean = false,
    val state: SyncState = SyncState.UNKNOWN,
    val lastSyncTime: Long? = null,
    val pendingChanges: Int = 0,
    val syncError: String? = null,
    val deviceId: String? = null,
    val canSync: Boolean = false
)
