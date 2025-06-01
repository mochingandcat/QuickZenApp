package com.tambuchosecretdev.quickzenapp.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tambuchosecretdev.quickzenapp.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // Inserta o actualiza múltiples notas en la base de datos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(notes: List<Note>)

    // Inserta una nueva nota en la base de datos y devuelve el ID generado
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    // Actualiza una nota existente
    @Update
    suspend fun updateNote(note: Note)

    // Elimina una nota de la base de datos (eliminación permanente)
    @Delete
    suspend fun deleteNote(note: Note)

    // Obtiene todas las notas de la base de datos ordenadas por fecha de modificación
    @Query("SELECT * FROM notes ORDER BY modifiedDate DESC")
    suspend fun getAllNotes(): List<Note>
    
    // Obtiene solo las notas activas (no en papelera) ordenadas por fecha de modificación
    @Query("SELECT * FROM notes WHERE isInTrash = 0 ORDER BY modifiedDate DESC")
    fun getActiveNotesFlow(): Flow<List<Note>>
    
    // Obtiene solo las notas en papelera ordenadas por fecha de modificación
    @Query("SELECT * FROM notes WHERE isInTrash = 1 ORDER BY modifiedDate DESC")
    fun getNotesInTrashFlow(): Flow<List<Note>>

    // Obtiene una nota por su ID
    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Long): Note?
    
    // Cuenta las notas en la papelera
    @Query("SELECT COUNT(*) FROM notes WHERE isInTrash = 1")
    suspend fun countNotesInTrash(): Int
    
    // Mover a la papelera (actualizar el campo isInTrash)
    @Query("UPDATE notes SET isInTrash = 1, needsSync = 1, modifiedDate = :timestamp WHERE id = :noteId")
    suspend fun moveToTrash(noteId: Long, timestamp: Long = System.currentTimeMillis())
    
    // Restaurar de la papelera
    @Query("UPDATE notes SET isInTrash = 0, needsSync = 1, modifiedDate = :timestamp WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: Long, timestamp: Long = System.currentTimeMillis())
    
    // Vaciar la papelera (eliminar permanentemente todas las notas en papelera)
    @Query("DELETE FROM notes WHERE isInTrash = 1")
    suspend fun emptyTrash()
    
    // Verificar si una nota existe
    @Query("SELECT EXISTS(SELECT 1 FROM notes WHERE id = :id LIMIT 1)")
    suspend fun noteExists(id: Long): Boolean

    // Nuevos métodos para sincronización
    @Query("UPDATE notes SET needsSync = 1 WHERE id = :noteId")
    suspend fun markNoteForSync(noteId: Long)

    @Query("UPDATE notes SET needsSync = 1")
    suspend fun markAllNotesForSync(): Int

    @Query("SELECT * FROM notes WHERE needsSync = 1")
    suspend fun getNotesForSync(): List<Note>

    @Query("UPDATE notes SET cloudId = :cloudId, needsSync = 0 WHERE id = :noteId")
    suspend fun updateNoteSyncStatus(noteId: Long, cloudId: String)
    
    @Query("UPDATE notes SET needsSync = 0 WHERE id = :noteId")
    suspend fun markNoteAsSynced(noteId: Long)

    @Query("UPDATE notes SET isFavorite = :isFavorite, needsSync = 1, modifiedDate = :timestamp WHERE id = :noteId")
    suspend fun updateFavoriteStatus(noteId: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isLocked = :isLocked, needsSync = 1, modifiedDate = :timestamp WHERE id = :noteId")
    suspend fun updateLockStatus(noteId: Long, isLocked: Boolean, timestamp: Long = System.currentTimeMillis())

    // Versiones síncronas para operaciones internas si son necesarias
    @Query("SELECT * FROM notes WHERE isInTrash = 0 ORDER BY modifiedDate DESC")
    suspend fun getActiveNotes(): List<Note>

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNotePermanently(noteId: Long)

    @Query("SELECT * FROM notes WHERE isInTrash = 1 ORDER BY modifiedDate DESC")
    suspend fun getNotesInTrash(): List<Note>

    // Obtiene solo las notas favoritas (no en papelera) ordenadas por fecha de modificación
    @Query("SELECT * FROM notes WHERE isFavorite = 1 AND isInTrash = 0 ORDER BY modifiedDate DESC")
    fun getFavoriteNotesFlow(): Flow<List<Note>>
    
    // Eliminar todas las notas activas (no las que están en papelera)
    @Query("DELETE FROM notes WHERE isInTrash = 0")
    suspend fun deleteAllActiveNotes()
    
    // Métodos para soporte de Firestore
    
    // Obtener nota por cloudId
    @Query("SELECT * FROM notes WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getNoteByCloudId(cloudId: String): Note?
    
    // Actualizar estado de firestore de una nota
    @Query("UPDATE notes SET cloudId = :cloudId, needsSync = :needsSync WHERE id = :noteId")
    suspend fun updateNoteFirestoreStatus(noteId: Long, cloudId: String, needsSync: Boolean)
    
    // Obtener notas que necesitan sincronización con Firestore
    @Query("SELECT * FROM notes WHERE needsSync = 1")
    suspend fun getNotesNeedingSync(): List<Note>
    
    // Obtener todas las notas que tienen un cloudId asignado
    @Query("SELECT * FROM notes WHERE cloudId IS NOT NULL AND cloudId != ''")
    suspend fun getNotesWithCloudId(): List<Note>
    
    // Actualizar todas las notas con un determinado cloudId
    @Query("UPDATE notes SET title = :title, content = :content, modifiedDate = :modifiedDate, " +
           "isFavorite = :isFavorite, isInTrash = :isInTrash, isLocked = :isLocked, needsSync = 0 WHERE cloudId = :cloudId")
    suspend fun updateNoteByCloudId(
        cloudId: String, 
        title: String, 
        content: String, 
        modifiedDate: Long,
        isFavorite: Boolean,
        isInTrash: Boolean,
        isLocked: Boolean
    )

    @Query("UPDATE notes SET needsSync = :b WHERE id = :id")
    suspend fun updateNeedsSyncStatus(id: Long, b: Boolean)
    
    // Actualizar solo el cloudId de una nota
    @Query("UPDATE notes SET cloudId = :cloudId WHERE id = :noteId")
    suspend fun updateNoteCloudId(noteId: Long, cloudId: String)
    
    // Actualizar el estado de sincronización de una nota
    @Query("UPDATE notes SET needsSync = :needsSync WHERE id = :noteId")
    suspend fun updateNoteSyncStatus(noteId: Long, needsSync: Boolean)

    @Query("SELECT * FROM notes WHERE title = :title AND content = :content LIMIT 1")
    suspend fun findByTitleAndContent(title: String, content: String): Note?

    @Update
    suspend fun update(note: Note)
}
