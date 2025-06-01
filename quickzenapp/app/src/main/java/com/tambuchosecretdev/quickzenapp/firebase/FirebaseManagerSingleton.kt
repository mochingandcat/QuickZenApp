package com.tambuchosecretdev.quickzenapp.firebase

import android.content.Context
import android.util.Log
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager

/**
 * Singleton para manejar la instancia única de FirebaseManager
 * Esto evita crear múltiples instancias y mejora la gestión de recursos
 */
object FirebaseManagerSingleton {
    private const val TAG = "FirebaseManagerSingleton"
    private var instance: FirebaseManager? = null
    
    /**
     * Obtiene la instancia única de FirebaseManager
     * Si no existe, la crea
     */
    fun getInstance(context: Context): FirebaseManager {
        return instance ?: synchronized(this) {
            instance ?: FirebaseManager(context.applicationContext).also { 
                instance = it
                Log.d(TAG, "Nueva instancia de FirebaseManager creada")
            }
        }
    }
    
    /**
     * Limpia la instancia de FirebaseManager
     * Útil para pruebas o cuando se cierra la aplicación
     */
    fun cleanup() {
        instance?.cleanup()
        instance = null
        Log.d(TAG, "Instancia de FirebaseManager limpiada")
    }
}
