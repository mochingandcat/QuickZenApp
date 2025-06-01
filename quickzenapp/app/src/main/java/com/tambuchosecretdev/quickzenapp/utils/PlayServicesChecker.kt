package com.tambuchosecretdev.quickzenapp.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Clase de utilidad para verificar la disponibilidad de Google Play Services
 * y su versión en el dispositivo del usuario.
 */
class PlayServicesChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "PlayServicesChecker"
        private const val PLAY_SERVICES_RESOLUTION_REQUEST = 9000
        
        // Versión mínima recomendada de Google Play Services
        private const val MIN_VERSION_MAJOR = 25
        private const val MIN_VERSION_MINOR = 19
        private const val MIN_VERSION_PATCH = 33
        private const val TARGET_VERSION_CODE = 100400 // Código de versión de referencia
    }
    
    /**
     * Verifica si Google Play Services está disponible y actualizado.
     * @param activity La actividad desde la que se llama (necesaria para mostrar diálogos)
     * @param showDialog Si es true, muestra un diálogo al usuario si hay problemas
     * @return true si Google Play Services está disponible y actualizado
     */
    fun checkGooglePlayServices(activity: Activity, showDialog: Boolean = true): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        
        return when {
            resultCode == ConnectionResult.SUCCESS -> {
                // Google Play Services está disponible y actualizado
                Log.d(TAG, "Google Play Services está disponible y actualizado")
                
                // Verificar si la versión instalada es suficientemente reciente
                if (isPlayServicesVersionSufficient()) {
                    true
                } else {
                    if (showDialog) {
                        showUpdateRecommendedDialog(activity)
                    }
                    // Consideramos que es utilizable aunque no sea la versión óptima
                    true
                }
            }
            googleApiAvailability.isUserResolvableError(resultCode) -> {
                // El error puede ser resuelto por el usuario
                Log.d(TAG, "Google Play Services necesita ser actualizado o reparado")
                if (showDialog) {
                    googleApiAvailability.getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)?.show()
                }
                false
            }
            else -> {
                // Error no resolvible
                Log.e(TAG, "Este dispositivo no es compatible con Google Play Services")
                if (showDialog) {
                    showNonResolvableErrorDialog(activity)
                }
                false
            }
        }
    }
    
    /**
     * Verifica si la versión instalada de Google Play Services es suficientemente reciente.
     * @return true si la versión es igual o superior a la mínima recomendada
     */
    private fun isPlayServicesVersionSufficient(): Boolean {
        try {
            val packageInfo = context.packageManager.getPackageInfo("com.google.android.gms", 0)
            val versionName = packageInfo.versionName
            
            Log.d(TAG, "Versión de Google Play Services: $versionName")
            
            // Parsear la versión (formato típico: "20.7.0")
            val versionParts = versionName.split(".")
            if (versionParts.size >= 3) {
                val major = versionParts[0].toIntOrNull() ?: 0
                val minor = versionParts[1].toIntOrNull() ?: 0
                val patch = versionParts[2].toIntOrNull() ?: 0
                
                return when {
                    major > MIN_VERSION_MAJOR -> true
                    major == MIN_VERSION_MAJOR && minor > MIN_VERSION_MINOR -> true
                    major == MIN_VERSION_MAJOR && minor == MIN_VERSION_MINOR && patch >= MIN_VERSION_PATCH -> true
                    else -> false
                }
            }
            
            // Si no podemos determinar la versión, asumimos que es suficiente
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar la versión de Google Play Services", e)
            // En caso de error, asumimos que es suficiente para no bloquear al usuario
            return true
        }
    }
    
    /**
     * Muestra un diálogo recomendando actualizar Google Play Services.
     */
    private fun showUpdateRecommendedDialog(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Actualización recomendada")
            .setMessage("Se recomienda actualizar Google Play Services para mejorar el rendimiento y la seguridad de la aplicación.")
            .setPositiveButton("Actualizar") { _, _ ->
                openPlayStore(activity)
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }
    
    /**
     * Muestra un diálogo para error no resolvible de Google Play Services.
     */
    private fun showNonResolvableErrorDialog(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Google Play Services no disponible")
            .setMessage("Esta aplicación requiere Google Play Services, que no está disponible en tu dispositivo. Algunas funciones podrían no estar disponibles.")
            .setPositiveButton("Entendido", null)
            .show()
    }
    
    /**
     * Abre Google Play Store en la página de Google Play Services.
     */
    private fun openPlayStore(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=com.google.android.gms")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir Google Play Store", e)
            Toast.makeText(activity, "No se pudo abrir Google Play Store", Toast.LENGTH_SHORT).show()
            
            // Intentar abrir en el navegador como alternativa
            try {
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")
                }
                activity.startActivity(webIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error al abrir el navegador", e)
            }
        }
    }
    
    /**
     * Obtiene la versión actual de Google Play Services como string.
     * @return String con la versión o "Desconocida" si no se puede determinar
     */
    fun getPlayServicesVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo("com.google.android.gms", 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "Desconocida"
        }
    }
}
