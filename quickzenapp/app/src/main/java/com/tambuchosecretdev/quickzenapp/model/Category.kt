package com.tambuchosecretdev.quickzenapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "#FFFFFF", // Color de la categoría
    val createdDate: Long = System.currentTimeMillis(), // Fecha de creación
    val modifiedDate: Long = System.currentTimeMillis(), // Fecha de última modificación
    val cloudId: String? = null, // ID de la categoría en la nube
    val needsSync: Boolean = false // Indica si la categoría necesita sincronización
)