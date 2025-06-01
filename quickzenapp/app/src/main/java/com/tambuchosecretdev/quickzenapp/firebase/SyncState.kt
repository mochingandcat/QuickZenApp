package com.tambuchosecretdev.quickzenapp.firebase

/**
 * Estados posibles durante el proceso de sincronización
 */
enum class SyncState {
    UNKNOWN,            // Estado inicial o desconocido
    IDLE,               // Sin actividad de sincronización
    SYNCING_UP,         // Subiendo datos al servidor
    SYNCING_DOWN,       // Descargando datos del servidor
    CONNECTING,         // Estableciendo conexión
    AUTHENTICATING,     // Autenticando con el servidor
    SUCCESS,            // Sincronización exitosa
    SYNCED,             // Sincronización completada exitosamente (alias de SUCCESS)
    SYNCING,            // Sincronización en curso (general)
    ERROR_CONNECTION,   // Error de conexión
    ERROR_SYNC,         // Error durante la sincronización
    ERROR_AUTHENTICATION, // Error de autenticación
    ERROR_AUTH,         // Alias para ERROR_AUTHENTICATION
    FAILED,             // Error general durante la sincronización
    CANCELLED,          // Sincronización cancelada por el usuario
    NO_CONNECTION,      // Sin conexión a internet
    NOT_SIGNED_IN       // Usuario no autenticado
}
