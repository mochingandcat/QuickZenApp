package com.tambuchosecretdev.quickzenapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginGoogleActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    
    companion object {
        private const val TAG = "LoginGoogleActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_google)
        
        // Inicializar Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()
        
        // Set up window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Configurar Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // Registrar callback para el resultado de la autenticación
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    // El login con Google fue exitoso, autenticar con Firebase
                    val account = task.getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account)
                } catch (e: ApiException) {
                    // El login con Google falló
                    Log.w(TAG, "Google sign in failed", e)
                    Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d(TAG, "Sign in cancelled or failed, result code: ${result.resultCode}")
                Toast.makeText(this, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Initialize and style the login button
        val button = findViewById<MaterialButton>(R.id.btnLoginGoogle)
        button.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        
        // Configurar el click listener para el botón de login
        button.setOnClickListener {
            signIn()
        }
    }
    
    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }
    
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
        
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login exitoso, actualizar UI con la información del usuario
                    Log.d(TAG, "signInWithCredential:success")
                    goToMainActivity()
                } else {
                    // Si falla el login, mostrar un mensaje al usuario
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Autenticación fallida: ${task.exception?.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}