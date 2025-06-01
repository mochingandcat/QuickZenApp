package com.tambuchosecretdev.quickzenapp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tambuchosecretdev.quickzenapp.utils.PlayServicesChecker

/**
 * Actividad de inicio que muestra la pantalla de bienvenida con opción de crear nota directamente
 */
class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Verificar Google Play Services
        checkGooglePlayServices()
        
        // Crear layout principal
        val rootLayout = RelativeLayout(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }
        
        // Contenedor central para el título y texto de carga
        val centerContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        
        // Título de la aplicación
        val titleTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = getString(R.string.app_name)
            textSize = 36f
            setTextColor(resources.getColor(R.color.purple_500, theme))
            gravity = Gravity.CENTER
        }
        
        // Texto de carga
        val loadingTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            text = "Tus notas, simples y seguras"
            textSize = 18f
            setTextColor(resources.getColor(R.color.purple_700, theme))
        }
        
        // Botón para entrar a la aplicación
        val enterButton = Button(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32
                gravity = Gravity.CENTER
            }
            text = "Entrar a la app"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(resources.getColor(R.color.purple_500, theme))
            }
            setPadding(40, 20, 40, 20)
            setOnClickListener {
                // Verificar Google Play Services antes de continuar
                if (checkGooglePlayServices()) {
                    // Navegar a la pantalla principal
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
        
        // Botón flotante para añadir nota directamente
        val addNoteButton = ImageButton(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(120, 120).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.ALIGN_PARENT_END)
                bottomMargin = 50
                rightMargin = 50
            }
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(resources.getColor(R.color.purple_500, theme))
            }
            contentDescription = "Añadir nueva nota"
            setOnClickListener {
                // Verificar Google Play Services antes de continuar
                if (checkGooglePlayServices()) {
                    // Navegar directamente a la pantalla de creación de notas
                    Toast.makeText(this@SplashActivity, "Creando nueva nota...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                        // Añadir un extra para indicar que debe ir directamente a crear nota
                        putExtra("CREATE_NOTE", true)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
        
        // Añadir vistas al layout
        centerContainer.addView(titleTextView)
        centerContainer.addView(loadingTextView)
        centerContainer.addView(enterButton)
        
        rootLayout.addView(centerContainer)
        rootLayout.addView(addNoteButton)
        
        // Establecer el layout como contenido de la actividad
        setContentView(rootLayout)
    }
    
    /**
     * Verifica la disponibilidad y versión de Google Play Services
     * @return true si Google Play Services está disponible y actualizado
     */
    private fun checkGooglePlayServices(): Boolean {
        val checker = PlayServicesChecker(this)
        val isAvailable = checker.checkGooglePlayServices(this, true)
        
        // Mostrar información sobre la versión instalada
        val version = checker.getPlayServicesVersion()
        Log.d(TAG, "Versión de Google Play Services: $version")
        
        // Si no está disponible, mostrar un mensaje adicional con la versión
        if (!isAvailable) {
            showPlayServicesVersionDialog(version)
        }
        
        return isAvailable
    }
    
    /**
     * Muestra un diálogo con información sobre la versión de Google Play Services
     */
    private fun showPlayServicesVersionDialog(version: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Información de Google Play Services")
            .setMessage("Versión instalada: $version\n\nSe requiere Google Play Services actualizado para el correcto funcionamiento de la aplicación, especialmente para la sincronización y migración de notas.")
            .setPositiveButton("Entendido", null)
            .show()
    }
}
