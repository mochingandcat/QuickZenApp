package com.tambuchosecretdev.quickzenapp.model

import androidx.compose.ui.graphics.Color

/**
 * Clase que representa una opción de color para las notas
 * @property id Identificador único del color
 * @property name Nombre descriptivo del color
 * @property color Valor del color en formato Color de Compose
 */
data class ColorOption(
    val id: String,
    val name: String,
    val color: Color
) {
    // Propiedad para compatibilidad con código existente
    val composeColor: Color
        get() = color
}
