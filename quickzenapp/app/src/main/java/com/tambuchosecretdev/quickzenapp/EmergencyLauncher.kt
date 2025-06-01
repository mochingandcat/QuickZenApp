package com.tambuchosecretdev.quickzenapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tambuchosecretdev.quickzenapp.ui.theme.QuickZenAppTheme

/**
 * Actividad de emergencia que se lanza cuando la aplicación principal tiene problemas.
 * Esta actividad es muy simple y no depende de ninguna biblioteca externa problemática.
 */
class EmergencyLauncher : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Desactivar todas las inicializaciones problemáticas
        try {
            // Deshabilitar cualquier inicialización automática
            System.setProperty("com.facebook.sdk.ApplicationId", "")
            System.setProperty("com.facebook.sdk.ClientToken", "")
            System.setProperty("com.google.firebase.crashlytics.disable", "true")
            System.setProperty("firebase.crashlytics.collection.enabled", "false")
            System.setProperty("http.keepAlive", "false")
            
            Log.d("EmergencyLauncher", "Inicializaciones problemáticas desactivadas")
        } catch (e: Exception) {
            Log.e("EmergencyLauncher", "Error al desactivar inicializaciones", e)
        }
        
        // Configurar la UI mínima
        setContent {
            QuickZenAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EmergencyScreen()
                }
            }
        }
        
        // Mostrar un mensaje al usuario
        Toast.makeText(
            this,
            "Modo de emergencia activado. Funcionalidad limitada disponible.",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun EmergencyScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "QuickZen\n\nModo de emergencia activado\n\nLa aplicación está funcionando con funcionalidad limitada para evitar cierres inesperados.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}
