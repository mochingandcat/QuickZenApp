package com.tambuchosecretdev.quickzenapp.firebase

/**
 * Clase para representar una nota almacenada en la nube (versión simplificada para transferencia)
 * Esta clase debe reflejar la estructura de datos en Firebase.
 */
data class CloudNote(
    val cloudId: String,
    val title: String,
    val content: String,
    val colorId: String,
    val createdDate: Long,
    val modifiedDate: Long,
    val isInTrash: Boolean = false,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val categoryId: String? = null,
    val reminderDateTime: Long? = null,
    val imageUrls: List<String> = emptyList(),
    val labelIds: List<String> = emptyList()
)

/**
 * Clase para representar una categoría almacenada en la nube
 */
data class CloudCategory(
    val cloudId: String,
    val name: String,
    val color: String,
    val createdDate: Long,
    val modifiedDate: Long
)
