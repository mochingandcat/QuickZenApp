package com.tambuchosecretdev.quickzenapp.model

import androidx.compose.ui.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "notes")
@TypeConverters(ImageListConverter::class, BooleanConverter::class)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdDate: Long = System.currentTimeMillis(),
    val modifiedDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "isInTrash") val isInTrash: Boolean = false, // Indica si la nota está en la papelera
    val colorId: String = NoteAppColors.DEFAULT_COLOR_ID, // Campo para almacenar el ID del color
    val images: List<String> = emptyList(), // Lista de rutas relativas a imágenes
    @ColumnInfo(index=true) val categoryId: Long? = null, // Foreign key for Category
    var cloudId: String? = null, // ID de la nota en la nube
    val needsSync: Boolean = false, // Indica si la nota necesita sincronización
    val reminderDateTime: Long? = null, // Timestamp para el recordatorio
    val isFavorite: Boolean = false, // Re-añadiendo campo para favorito
    val isLocked: Boolean = false, // Para bloquear la nota con PIN
    val labelIds: List<String> = emptyList(), // IDs de las etiquetas asociadas
    val userId: String? = null // ID del usuario propietario de la nota
)

/**
 * Conversor para almacenar listas de strings (rutas de imágenes) en la base de datos
 */
class ImageListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromImageList(images: List<String>): String {
        return gson.toJson(images)
    }

    @TypeConverter
    fun toImageList(imagesString: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(imagesString, type) ?: emptyList()
    }
}

/**
 * Conversor para manejar la conversión de booleanos a enteros en la base de datos
 */
class BooleanConverter {
    @TypeConverter
    fun fromBoolean(value: Boolean): Int {
        return if (value) 1 else 0
    }

    @TypeConverter
    fun toBoolean(value: Int): Boolean {
        return value == 1
    }
}

// Propiedad de extensión para acceder al objeto Color de Compose directamente desde una Nota
val Note.color: Color
    get() = NoteAppColors.getColorByID(this.colorId)




