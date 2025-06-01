package com.tambuchosecretdev.quickzenapp.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestiona el tema de la aplicación (claro/oscuro)
 */
class ThemeManager(context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)
    
    /**
     * Verifica si el tema oscuro está activado
     * @return true si el tema oscuro está activado, false en caso contrario
     */
    fun isDarkTheme(): Boolean {
        return sharedPreferences.getBoolean(KEY_DARK_THEME, false)
    }
    
    /**
     * Establece el tema de la aplicación
     * @param isDarkTheme true para activar el tema oscuro, false para el tema claro
     */
    fun setDarkTheme(isDarkTheme: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DARK_THEME, isDarkTheme).apply()
    }
    
    /**
     * Cambia el tema actual al opuesto
     * @return el nuevo estado del tema (true si es oscuro, false si es claro)
     */
    fun toggleTheme(): Boolean {
        val newTheme = !isDarkTheme()
        setDarkTheme(newTheme)
        return newTheme
    }
    
    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
