package com.tambuchosecretdev.quickzenapp.firebase

/**
 * Clase que representa el resultado de una operación de sincronización.
 * Contiene información sobre las notas subidas, descargadas y los conflictos resueltos.
 */
data class SyncResult(
    val uploaded: Int = 0,    // Número de notas subidas al servidor
    val downloaded: Int = 0,  // Número de notas descargadas del servidor
    val conflicts: Int = 0,   // Número de conflictos resueltos
    val success: Boolean = true, // Si la sincronización fue exitosa
    val errorMessage: String? = null // Mensaje de error si la sincronización falló
)
