package com.tambuchosecretdev.quickzenapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.tambuchosecretdev.quickzenapp.databinding.ActivitySplashScreenBinding

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashScreenBinding
    private lateinit var firebaseAuth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Utilizar View Binding
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()
        
        // Configurar colores y diseño si es necesario
        window.statusBarColor = ContextCompat.getColor(this, R.color.purple_700)
        
        // Temporizador para mostrar el splash por 1.5 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            // Verificar si hay un usuario logueado
            checkUserSession()
        }, 1500)
    }
    
    private fun checkUserSession() {
        // Si hay un usuario logueado, ir a MainActivity
        // Si no, ir a LoginGoogleActivity
        if (firebaseAuth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginGoogleActivity::class.java))
        }
        
        // Cerrar esta actividad
        finish()
    }
}
