package com.tambuchosecretdev.quickzenapp.firebase

/**
 * Estados posibles durante la sincronización con Firebase
 */
enum class FirebaseSyncState {
    IDLE,           // Estado inicial o en reposo
    SYNCING,        // Sincronización en proceso
    SYNCED,         // Sincronización completada con éxito
    FAILED,         // Error durante la sincronización
    CANCELLED,      // Sincronización cancelada por el usuario
    NO_CONNECTION,  // Sin conexión a internet
    NOT_SIGNED_IN   // Usuario no autenticado
}
