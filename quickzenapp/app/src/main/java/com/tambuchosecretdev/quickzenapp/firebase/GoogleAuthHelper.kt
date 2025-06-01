package com.tambuchosecretdev.quickzenapp.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.tambuchosecretdev.quickzenapp.R

/**
 * Clase auxiliar para manejar la autenticación con Google
 */
class GoogleAuthHelper(private val context: Context) {
    
    private var googleSignInClient: GoogleSignInClient
    
    init {
        try {
            // Obtener una referencia al recurso string fuera del Builder para validar
            val webClientId = context.getString(R.string.default_web_client_id)
            Log.d(TAG, "WebClientId obtenido: $webClientId")
            
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            
            // Inicializar el cliente con contexto de aplicación para evitar leaks
            googleSignInClient = GoogleSignIn.getClient(context.applicationContext, gso)
            Log.d(TAG, "GoogleSignInClient inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar GoogleSignInClient", e)
            throw e
        }
    }
    
    /**
     * Obtiene el intent para iniciar el proceso de inicio de sesión con Google
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    /**
     * Inicia el proceso de inicio de sesión con Google
     * @param activityResultLauncher el launcher para iniciar la actividad de resultado
     */
    fun signIn(activityResultLauncher: ActivityResultLauncher<Intent>) {
        val signInIntent = googleSignInClient.signInIntent
        activityResultLauncher.launch(signInIntent)
    }
    
    /**
     * Maneja el resultado del proceso de inicio de sesión con Google
     * @param task la tarea con el resultado del inicio de sesión
     * @return la cuenta de Google si el inicio de sesión fue exitoso, null en caso contrario
     */
    fun handleSignInResult(task: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return try {
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "signInResult:success, email=${account?.email}")
            account
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=${e.statusCode}", e)
            null
        }
    }
    
    /**
     * Cierra la sesión actual de Google
     */
    fun signOut() {
        googleSignInClient.signOut()
    }
    
    companion object {
        private const val TAG = "GoogleAuthHelper"
    }
} 