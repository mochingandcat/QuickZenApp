package com.tambuchosecretdev.quickzenapp

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tambuchosecretdev.quickzenapp.firebase.SyncManager
import com.tambuchosecretdev.quickzenapp.firebase.FirebaseManager
import com.tambuchosecretdev.quickzenapp.repository.NoteRepository

class NotesViewModelFactory(
    private val repository: NoteRepository,
    private val application: Application,
    private val firebaseManager: FirebaseManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(NotesViewModel::class.java) -> {
                // Crear instancia de NotesViewModel sin tipo genérico
                NotesViewModel<Any>(repository, firebaseManager) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}