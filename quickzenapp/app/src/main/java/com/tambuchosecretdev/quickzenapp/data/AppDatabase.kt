package com.tambuchosecretdev.quickzenapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.tambuchosecretdev.quickzenapp.model.Note
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.ImageListConverter
import com.tambuchosecretdev.quickzenapp.dao.NoteDao
import com.tambuchosecretdev.quickzenapp.dao.CategoryDao

// Define la base de datos con las entidades Note y Category, y la versión actual.
@Database(entities = [Note::class, Category::class], version = 9, exportSchema = false)
@TypeConverters(ImageListConverter::class) // Descomentado para habilitar la conversión de listas de imágenes
abstract class AppDatabase : RoomDatabase() {

    // DAOs para interactuar con las tablas.
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        // Singleton para la instancia de la base de datos.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // MIGRATION from 1 to 2: Add isInTrash column to notes table.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN isInTrash INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRATION from 2 to 3: Add categories table and categoryId column to notes.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create categories table
                database.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                // Add categoryId column to notes table
                database.execSQL("ALTER TABLE notes ADD COLUMN categoryId INTEGER")
                // SQLite no soporta añadir FK con ALTER TABLE de forma simple.
                // Se omite por simplicidad, considerar Room AutoMigrations para mayor complejidad.
            }
        }

        // MIGRATION from 3 to 4: Add cloudId and needsSync columns to notes and categories.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN cloudId TEXT")
                database.execSQL("ALTER TABLE notes ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE categories ADD COLUMN cloudId TEXT")
                database.execSQL("ALTER TABLE categories ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRATION from 4 to 5: Add reminderDateTime column to notes table.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN reminderDateTime INTEGER")
            }
        }

        // MIGRATION from 5 to 6: Add isFavorite column to notes table.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRATION from 6 to 7: Add isLocked column to notes table.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRATION from 7 to 8: Add labelIds column to notes table.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // labelIds is List<String> which is converted to TEXT (JSON String) by ImageListConverter
                database.execSQL("ALTER TABLE notes ADD COLUMN labelIds TEXT NOT NULL DEFAULT '[]'")
            }
        }
        
        // MIGRATION from 8 to 9: Recreate notes table to ensure all required columns exist
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Crear una tabla temporal con la estructura completa
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdDate INTEGER NOT NULL,
                        modifiedDate INTEGER NOT NULL,
                        isInTrash INTEGER NOT NULL DEFAULT 0,
                        colorId TEXT NOT NULL DEFAULT 'blue',
                        images TEXT NOT NULL DEFAULT '[]',
                        categoryId INTEGER,
                        cloudId TEXT,
                        needsSync INTEGER NOT NULL DEFAULT 0,
                        reminderDateTime INTEGER,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        isLocked INTEGER NOT NULL DEFAULT 0,
                        labelIds TEXT NOT NULL DEFAULT '[]',
                        userId TEXT
                    )
                """)
                
                // Copiar datos de la tabla original a la nueva, manejando columnas faltantes
                database.execSQL("""
                    INSERT INTO notes_new (id, title, content, createdDate, modifiedDate, isInTrash, 
                    colorId, images, categoryId, cloudId, needsSync, reminderDateTime, isFavorite, isLocked, labelIds, userId)
                    SELECT 
                        id, 
                        title, 
                        content, 
                        createdDate, 
                        modifiedDate, 
                        isInTrash, 
                        COALESCE(colorId, 'blue') as colorId,
                        COALESCE(images, '[]') as images,
                        categoryId,
                        cloudId,
                        COALESCE(needsSync, 0) as needsSync,
                        reminderDateTime,
                        COALESCE(isFavorite, 0) as isFavorite,
                        COALESCE(isLocked, 0) as isLocked,
                        COALESCE(labelIds, '[]') as labelIds,
                        userId
                    FROM notes
                """)
                
                // Eliminar la tabla original
                database.execSQL("DROP TABLE notes")
                
                // Renombrar la nueva tabla
                database.execSQL("ALTER TABLE notes_new RENAME TO notes")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notes_database"
                )
                .addMigrations(
                    MIGRATION_1_2, 
                    MIGRATION_2_3, 
                    MIGRATION_3_4, 
                    MIGRATION_4_5, 
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9
                )
                .fallbackToDestructiveMigration() // Considerar eliminar en producción y manejar todas las migraciones
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Lógica opcional al crear la base de datos por primera vez.
                        // Por ejemplo, pre-poblar con datos.
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
