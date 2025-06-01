package com.tambuchosecretdev.quickzenapp.utils

import android.content.Context
import android.util.Log

/**
 * Clase utilitaria para deshabilitar completamente la inicialización automática de Facebook SDK
 * y evitar los errores relacionados con Facebook que están causando cierres de la aplicación.
 */
object FacebookSdkDisabler {
    
    private const val TAG = "FacebookSdkDisabler"
    
    /**
     * Deshabilita la inicialización automática de Facebook SDK
     */
    fun disableFacebookSdk(context: Context) {
        try {
            // Establecer propiedades del sistema para deshabilitar Facebook SDK
            System.setProperty("com.facebook.sdk.ApplicationId", "")
            System.setProperty("com.facebook.sdk.ClientToken", "")
            System.setProperty("com.facebook.sdk.AutoLogAppEventsEnabled", "false")
            System.setProperty("com.facebook.sdk.AdvertiserIDCollectionEnabled", "false")
            
            // Intentar deshabilitar cualquier inicialización automática de Facebook SDK
            val properties = System.getProperties()
            properties.setProperty("com.facebook.sdk.DisableAppEventCollection", "true")
            properties.setProperty("com.facebook.sdk.DisableLoggingBehavior", "true")
            
            Log.d(TAG, "Facebook SDK deshabilitado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al deshabilitar Facebook SDK", e)
        }
    }
}
