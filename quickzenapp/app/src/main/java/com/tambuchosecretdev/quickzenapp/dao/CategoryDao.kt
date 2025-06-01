package com.tambuchosecretdev.quickzenapp.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tambuchosecretdev.quickzenapp.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    // Nuevos métodos para sincronización
    @Query("UPDATE categories SET needsSync = 1")
    suspend fun markAllCategoriesForSync(): Int

    @Query("SELECT * FROM categories WHERE needsSync = 1")
    suspend fun getCategoriesNeedingSync(): List<Category>

    // Añadido para obtener todas las categorías como una lista síncrona (dentro de suspend)
    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesList(): List<Category>

    // Añadido para actualizar el estado de sincronización de una categoría específica
    @Query("UPDATE categories SET needsSync = 0, cloudId = :cloudId WHERE id = :categoryId")
    suspend fun updateCategorySyncStatus(categoryId: Long, cloudId: String?)
}

