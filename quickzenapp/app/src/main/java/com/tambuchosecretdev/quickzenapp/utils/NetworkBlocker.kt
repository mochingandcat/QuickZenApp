package com.tambuchosecretdev.quickzenapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

/**
 * Clase utilitaria para bloquear conexiones de red no esenciales durante el inicio de la aplicación.
 * Esto puede ayudar a prevenir que bibliotecas como Facebook SDK intenten establecer conexiones
 * que están causando cierres de la aplicación.
 */
object NetworkBlocker {
    
    private const val TAG = "NetworkBlocker"
    
    /**
     * Bloquea temporalmente las conexiones de red no esenciales
     */
    fun blockNonEssentialConnections(context: Context) {
        try {
            // Establecer propiedades del sistema para indicar que no hay conexión
            System.setProperty("http.keepAlive", "false")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                // Crear una solicitud de red con capacidades limitadas
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .build()
                
                // Registrar un callback que no hace nada, solo para tener control sobre las conexiones
                connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    // No implementamos ningún método para evitar conexiones automáticas
                })
                
                Log.d(TAG, "Conexiones no esenciales bloqueadas")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al bloquear conexiones no esenciales", e)
        }
    }
}
