package com.tambuchosecretdev.quickzenapp

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import com.tambuchosecretdev.quickzenapp.utils.FacebookSdkDisabler
import com.tambuchosecretdev.quickzenapp.utils.PinManager
import com.tambuchosecretdev.quickzenapp.data.AppDatabase
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
// Asegúrate de que las siguientes importaciones sean correctas para tu estructura de paquetes
import com.tambuchosecretdev.quickzenapp.dao.NoteDao
import com.tambuchosecretdev.quickzenapp.dao.CategoryDao
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository
import java.util.concurrent.Executors

class QuickZenApp : Application() {
    
    companion object {
        private const val TAG = "QuickZenApp"
        lateinit var appContext: Context
            private set
    }
    
    // Executor para tareas en segundo plano
    val backgroundExecutor = Executors.newFixedThreadPool(3)

    // Scope de Coroutines para toda la aplicación
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // PinManager para gestionar el PIN de seguridad - accesible públicamente
    val pinManager by lazy { PinManager(appContext) }

    // Instancia de la base de datos
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    // Instancia de FirebaseManager
    val firebaseManager: FirebaseManager by lazy {
        FirebaseManager(appContext)
    }

    // Instancia de NoteDao
    private val noteDao: com.tambuchosecretdev.quickzenapp.dao.NoteDao by lazy {
        appDatabase.noteDao()
    }

    // Instancia de CategoryDao
    private val categoryDao: com.tambuchosecretdev.quickzenapp.dao.CategoryDao by lazy {
        appDatabase.categoryDao()
    }

    // Instancia de NoteRepository
    val noteRepository: com.tambuchosecretdev.quickzenapp.repository.NoteRepository by lazy {
        com.tambuchosecretdev.quickzenapp.repository.NoteRepository(noteDao, categoryDao, firebaseManager, applicationScope)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // 1. Establecer el contexto de la aplicación
            appContext = applicationContext
            
            // 2. Configurar StrictMode para detectar operaciones en el hilo principal
            configureStrictMode()
            
            // 3. Configurar manejador de excepciones para hilos normales
            setupUncaughtExceptionHandler()
            
            // 4. Configurar manejador global de excepciones para coroutines
            setupCoroutineExceptionHandler()
            
            // 4. Inicialización segura de Firebase en segundo plano
            backgroundExecutor.execute {
                initializeFirebaseSafely()
            }
            
            // 5. Deshabilitar Facebook SDK (si existe)
            try {
                FacebookSdkDisabler.disableFacebookSdk(this)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo deshabilitar Facebook SDK", e)
            }
            
            Log.d(TAG, "Aplicación iniciada correctamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico en onCreate de la aplicación", e)
        }
    }
    
    private fun configureStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build())
            
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build())
        }
    }
    
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Excepción no capturada en el hilo: ${thread.name}", throwable)
            } catch (e: Exception) {
                // No hacer nada para evitar bucles
            } finally {
                // Si no es una excepción de coroutine cancelada, llamar al manejador por defecto
                if (!throwable.toString().contains("StandaloneCoroutine was cancelled") && 
                    !throwable.toString().contains("kotlinx.coroutines")) {
                    defaultHandler?.uncaughtException(thread, throwable)
                } else {
                    // Solo registramos la excepción de coroutine sin cerrar la app
                    Log.w(TAG, "Coroutine cancelada detectada: ${throwable.message}")
                }
            }
        }
    }
    
    private fun setupCoroutineExceptionHandler() {
        // Establecer un manejador global de excepciones para coroutines
        kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            try {
                if (throwable is kotlinx.coroutines.CancellationException) {
                    // Las CancellationException son normales y esperadas cuando se cancela una coroutine
                    Log.d(TAG, "Coroutine cancelada normalmente: ${throwable.message}")
                } else {
                    // Para otras excepciones, registramos pero no cerramos la app
                    Log.e(TAG, "Error en coroutine: ${throwable.message}", throwable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al manejar excepción de coroutine: ${e.message}")
            }
        }.also { handler ->
            // Establecer este manejador como predeterminado para las coroutines globales
            kotlinx.coroutines.GlobalScope.launch(handler) {
                // Esta coroutine solo existe para establecer el manejador global
                Log.d(TAG, "Manejador global de excepciones para coroutines inicializado")
            }
        }
    }
    
    private fun initializeFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase inicializado correctamente")
                
                // Configurar Firestore
                try {
                    val db = FirebaseFirestore.getInstance()
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                    db.firestoreSettings = settings
                    
                    // Verificar la conexión con Firestore
                    db.collection("test").document("test")
                        .get()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "Conexión con Firestore establecida correctamente")
                            } else {
                                Log.e(TAG, "Error al conectar con Firestore", task.exception)
                            }
                        }
                    
                    Log.d(TAG, "Firestore configurado correctamente")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al configurar Firestore", e)
                }
            } else {
                Log.d(TAG, "Firebase ya estaba inicializado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar Firebase", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel() // Cancelar el scope cuando la aplicación termina
        Log.d(TAG, "Application terminated and applicationScope cancelled.")
    }
}
