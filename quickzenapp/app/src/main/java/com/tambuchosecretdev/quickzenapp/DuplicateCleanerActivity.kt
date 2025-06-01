package com.tambuchosecretdev.quickzenapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tambuchosecretdev.quickzenapp.utils.DuplicateNoteCleaner
import kotlinx.coroutines.launch

/**
 * Actividad dedicada a la limpieza de notas duplicadas
 * Esta actividad permite al usuario eliminar todas las notas duplicadas de la base de datos
 */
class DuplicateCleanerActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var messageView: TextView
    private lateinit var cleanButton: Button
    private lateinit var backButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duplicate_cleaner)
        
        // Inicializar vistas
        progressBar = findViewById(R.id.progressBar)
        messageView = findViewById(R.id.messageView)
        cleanButton = findViewById(R.id.cleanButton)
        backButton = findViewById(R.id.backButton)
        
        // Configurar listeners
        cleanButton.setOnClickListener {
            cleanDuplicates()
        }
        
        backButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * Elimina todas las notas duplicadas de la base de datos local
     */
    private fun cleanDuplicates() {
        // Mostrar progreso
        progressBar.visibility = View.VISIBLE
        cleanButton.isEnabled = false
        messageView.text = "Buscando y eliminando notas duplicadas..."
        
        lifecycleScope.launch {
            try {
                // Obtener el repositorio desde la aplicación global
                val app = application as QuickZenApp
                val repository = app.noteRepository
                val duplicateCleaner = DuplicateNoteCleaner(repository)
                
                // Ejecutar limpieza de duplicados
                val duplicatesRemoved = duplicateCleaner.cleanDuplicates()
                
                // Mostrar resultado
                if (duplicatesRemoved > 0) {
                    Log.d("DuplicateCleaner", "Se eliminaron $duplicatesRemoved notas duplicadas")
                    messageView.text = "Se eliminaron $duplicatesRemoved notas duplicadas."
                } else {
                    Log.d("DuplicateCleaner", "No se encontraron notas duplicadas")
                    messageView.text = "No se encontraron notas duplicadas."
                }
            } catch (e: Exception) {
                Log.e("DuplicateCleaner", "Error al eliminar duplicados", e)
                messageView.text = "Error al eliminar duplicados: ${e.message}"
            } finally {
                // Ocultar progreso
                progressBar.visibility = View.GONE
                cleanButton.isEnabled = true
            }
        }
    }
}
