package com.tambuchosecretdev.quickzenapp.utils

import android.util.Log
import android.widget.Toast
import com.tambuchosecretdev.quickzenapp.MainActivity
import com.tambuchosecretdev.quickzenapp.R

/**
 * Clase de utilidad para funciones comunes de la aplicación
 */
object AppUtils {
    /**
     * Muestra la interfaz principal después de iniciar sesión
     */
    fun MainActivity.mostrarInterfazPrincipal() {
        Log.d("MainActivity", "Mostrando interfaz principal")

        // Establecer el layout principal
        setContentView(R.layout.activity_main)

        // Mostrar mensaje de bienvenida
        Toast.makeText(this, "¡Iniciando QuickZen!", Toast.LENGTH_SHORT).show()

        // Intentar iniciar sincronización en segundo plano
        try {
            val method = MainActivity::class.java.getDeclaredMethod("startBackgroundSync")
            method.isAccessible = true
            method.invoke(this)
            Log.d("MainActivity", "Sincronización en segundo plano iniciada")
        } catch (e: Exception) {
            Log.w("MainActivity", "No se pudo iniciar la sincronización: ${e.message}")
        }
    }
}


