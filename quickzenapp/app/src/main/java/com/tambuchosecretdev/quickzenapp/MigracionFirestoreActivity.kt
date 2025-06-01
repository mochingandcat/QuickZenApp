package com.tambuchosecretdev.quickzenapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.tambuchosecretdev.quickzenapp.ui.theme.QuickZenAppTheme
import com.tambuchosecretdev.quickzenapp.utils.ThemeManager

/**
 * Actividad que muestra la pantalla de migración a la estructura segura de Firestore
 */
class MigracionFirestoreActivity : ComponentActivity() {

    private lateinit var viewModel: NotesViewModel<Any?>
    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el gestor de temas
        themeManager = ThemeManager(this)
        
        // Obtener instancias de dependencias desde la clase Application (QuickZenApp)
        val appInstance = application as QuickZenApp
        val noteRepositoryFromApp = appInstance.noteRepository
        val firebaseManagerFromApp = appInstance.firebaseManager

        // Inicializar el ViewModel
        viewModel =ViewModelProvider(
            this,
            NotesViewModelFactory(noteRepositoryFromApp, application, firebaseManagerFromApp)
        )[NotesViewModel::class.java] as NotesViewModel<Any?>

        setContent {
            QuickZenAppTheme(darkTheme = themeManager.isDarkTheme()) {
                val navController = rememberNavController()
                
                MigracionFirestoreScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}
