package com.tambuchosecretdev.quickzenapp.model

import androidx.compose.ui.graphics.Color
import com.tambuchosecretdev.quickzenapp.model.ColorOption

/**
 * Clase que contiene los colores disponibles para las notas
 */
object NoteAppColors {
    const val DEFAULT_COLOR_ID = "white"
    const val RED_COLOR_ID = "red_light"
    const val BLUE_COLOR_ID = "blue_light"
    const val GREEN_COLOR_ID = "green_light"
    const val YELLOW_COLOR_ID = "yellow_light"
    const val PURPLE_COLOR_ID = "purple_light"
    const val ORANGE_COLOR_ID = "orange_light"
    const val PINK_COLOR_ID = "pink_light"
    
    val palette: List<ColorOption> = listOf(
        ColorOption(DEFAULT_COLOR_ID, "Blanco", Color.White),
        ColorOption(RED_COLOR_ID, "Rojo Intenso", Color(0xFFE57373)),
        ColorOption(ORANGE_COLOR_ID, "Naranja Intenso", Color(0xFFFFA726)),
        ColorOption(YELLOW_COLOR_ID, "Amarillo Intenso", Color(0xFFFFEB3B)),
        ColorOption(GREEN_COLOR_ID, "Verde Intenso", Color(0xFF81C784)),
        ColorOption(BLUE_COLOR_ID, "Azul Intenso", Color(0xFF64B5F6)),
        ColorOption(PURPLE_COLOR_ID, "Morado Intenso", Color(0xFFBA68C8)),
        ColorOption(PINK_COLOR_ID, "Rosa Intenso", Color(0xFFF06292))
    )
    
    // Métodos principales para acceder a colores
    fun getColorByID(colorId: String): Color {
        return palette.find { it.id == colorId }?.color ?: Color.White
    }
    
    // Alias method with different casing for compatibility
    fun getColorById(colorId: String): Color {
        return getColorByID(colorId)
    }
    
    // Devuelve un mapa de ID a valor de color para la selección
    fun getAllColors(): Map<String, Long> {
        return palette.associate { it.id to it.color.value.toLong() }
    }
    
    // Método de ayuda para obtener un color por ID como Color.value
    fun getColorValueById(colorId: String): Long {
        return getColorById(colorId).value.toLong()
    }
}
