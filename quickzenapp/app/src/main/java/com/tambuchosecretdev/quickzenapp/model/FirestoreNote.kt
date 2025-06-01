package com.tambuchosecretdev.quickzenapp.model

import android.util.Log
import java.util.Date
import java.util.UUID

/**
 * Modelo de nota para Firestore.
 * Esta versión es más simple y está adaptada para funcionar con Firestore.
 */
data class FirestoreNote(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val timestamp: Date = Date(),
    val userId: String = "",
    val isSync: Boolean = false,
    val isFavorite: Boolean = false,
    val colorId: String = "default",
    val localId: String? = null,
    val createdDate: Long? = null,
    val modifiedDate: Long? = null,
    val labelIds: List<String>? = null,
    val isLocked: Boolean? = false,
    val reminderDateTime: Long? = null,
    val categoryId: String? = null,
    val images: List<String>? = emptyList(),
    val isInTrash: Boolean? = false
) {
    // Propiedad calculada para convertir categoryId String a Long
    val categoryIdLong: Long?
        get() = categoryId?.toLongOrNull()
}

/**
 * Extensión para convertir una FirestoreNote a una Note local
 */
fun FirestoreNote.toLocalNote(): Note {
    // Extraer localId si existe
    var localIdLong = 0L
    try {
        // Intentamos obtener el localId almacenado
        val localIdStr = this.localId
        if (!localIdStr.isNullOrEmpty()) {
            localIdLong = localIdStr.toLongOrNull() ?: 0L
        }
    } catch (e: Exception) {
        Log.e("FirestoreNote", "Error al obtener localId", e)
    }
    
    return Note(
        // Usar el localId si está disponible, de lo contrario generar uno nuevo
        id = if (localIdLong > 0) localIdLong else 0,
        title = this.title.ifEmpty { "Nota sin título" },
        content = this.content,
        createdDate = this.createdDate ?: this.timestamp.time,
        modifiedDate = this.modifiedDate ?: this.timestamp.time,
        isInTrash = this.isInTrash ?: false,
        colorId = this.colorId, // Ya está sincronizada si viene de Firebase
        images = this.images ?: emptyList(),
        categoryId = this.categoryIdLong,
        cloudId = this.id,
        needsSync = false,
        reminderDateTime = this.reminderDateTime,
        isFavorite = this.isFavorite,
        isLocked = this.isLocked ?: false,
        labelIds = this.labelIds ?: emptyList(),
        userId=UUID.randomUUID().toString()
    )
}

/**
 * Extensión para convertir una Note local a una FirestoreNote
 */
fun Note.toFirestoreNote(): FirestoreNote {
    return FirestoreNote(
        id = this.cloudId ?: "",
        title = this.title,
        content = this.content,
        timestamp = Date(this.modifiedDate),
        isSync = !this.needsSync,
        isFavorite = this.isFavorite,
        colorId = this.colorId,
        localId = this.id.toString(), // Preservar el ID local
        createdDate = this.createdDate,
        modifiedDate = this.modifiedDate,
        labelIds = this.labelIds,
        isLocked = this.isLocked,
        reminderDateTime = this.reminderDateTime,
        categoryId = this.categoryId?.toString(),
        images = this.images,
        isInTrash = this.isInTrash
    )
}
