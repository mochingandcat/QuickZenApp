package com.tambuchosecretdev.quickzenapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.tambuchosecretdev.quickzenapp.model.Note
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Funciones auxiliares para HomeScreen
 */

/**
 * Implementa el cambio de tema en la aplicación
 * Retorna el nuevo estado del tema (true = dark, false = light)
 */
fun changeTheme(context: Context): Boolean {
    // Obtiene el estado actual del tema
    val themePreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    val currentDarkTheme = themePreferences.getBoolean("dark_theme", false)
    
    // Cambia el tema al opuesto
    val newDarkTheme = !currentDarkTheme
    themePreferences.edit().putBoolean("dark_theme", newDarkTheme).apply()
    
    // Muestra un mensaje de confirmación
    val themeText = if (newDarkTheme) "oscuro" else "claro"
    Toast.makeText(
        context, 
        "Tema cambiado a $themeText",
        Toast.LENGTH_SHORT
    ).show()
    
    return newDarkTheme
}

/**
 * Implementa la exportación de todas las notas
 */
fun exportAllNotes(context: Context, notes: List<Note>) {
    if (notes.isEmpty()) {
        Toast.makeText(context, "No hay notas para exportar.", Toast.LENGTH_LONG).show()
        return
    }

    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val currentDateTime = dateFormat.format(Date())
    val fileName = "notas_exportadas_$currentDateTime.txt"
    
    try {
        // Crear un archivo en el directorio de descargas
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            Toast.makeText(context, "No se pudo crear el directorio de descargas", Toast.LENGTH_LONG).show()
            return
        }

        val file = File(downloadsDir, fileName)
        
        // Escribir el contenido de las notas al archivo
        FileWriter(file).use { fileWriter ->
            BufferedWriter(fileWriter).use { bufferedWriter ->
                bufferedWriter.write("NOTAS EXPORTADAS - ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date())}\n\n")
                
                notes.forEach { note ->
                    try {
                        bufferedWriter.write("--- NOTA: ${note.title} ---\n")
                        bufferedWriter.write("Fecha de creación: ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(note.createdDate))}\n")
                        bufferedWriter.write("Fecha de modificación: ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(note.modifiedDate))}\n")
                        if (note.categoryId != null) {
                            bufferedWriter.write("Categoría: ${note.categoryId}\n")
                        }
                        if (note.isFavorite) {
                            bufferedWriter.write("Favorita: Sí\n")
                        }
                        if (note.isLocked) {
                            bufferedWriter.write("Bloqueada: Sí\n")
                        }
                        bufferedWriter.write("\n${note.content}\n\n")
                        bufferedWriter.write("--------------------\n\n")
                    } catch (e: Exception) {
                        Log.e("ExportNotes", "Error exporting note ${note.id}: ${e.message}")
                    }
                }
            }
        }
        
        // Verificar que el archivo se creó correctamente
        if (file.exists() && file.length() > 0) {
            Toast.makeText(context, "Notas exportadas a $fileName", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Error al crear el archivo de exportación", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error al exportar notas: ${e.message}", Toast.LENGTH_LONG).show()
        Log.e("ExportNotes", "Error during export", e)
    }
}

/**
 * Lanza el selector de imágenes de la galería
 */
fun openGallery(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error al abrir la galería: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copia una imagen de la galería a la aplicación
 */
fun copyImageToAppStorage(context: Context, imageUri: Uri): String? {
    try {
        // Crear directorio si no existe
        val imagesDir = File(context.filesDir, "note_images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        
        // Generar nombre único para la imagen
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "NOTE_IMG_$timestamp.jpg"
        val destFile = File(imagesDir, imageFileName)
        
        // Copiar el contenido de la imagen
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Devolver la ruta relativa a la imagen
        return "note_images/$imageFileName"
    } catch (e: Exception) {
        Toast.makeText(context, "Error al guardar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
        return null
    }
}

/**
 * Obtiene la Uri completa a partir de una ruta relativa
 */
fun getImageUri(context: Context, relativePath: String): Uri {
    val file = File(context.filesDir, relativePath)
    return Uri.fromFile(file)
}

/**
 * Importa notas desde un archivo de texto.
 */
fun importNotesFromFile(context: Context, uri: Uri, viewModel: NotesViewModel<Any?>) {
    // Usamos una corrutina para poder llamar a la función suspendida addNote
    kotlinx.coroutines.MainScope().launch {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(context, "Error al abrir el archivo de importación", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Verificar el tipo de archivo
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != "text/plain" && mimeType != "application/json") {
                Toast.makeText(context, "Formato de archivo no soportado. Use archivos .txt o .json", Toast.LENGTH_LONG).show()
                return@launch
            }

            val content = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            if (content.isBlank()) {
                Toast.makeText(context, "El archivo de importación está vacío.", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Basic parser for the exported format
            val notesFromFile = mutableListOf<Pair<String, String>>() // Pair of (title, content)
            val lines = content.split('\n')
            var currentTitle: String? = null
            var currentContent = StringBuilder()
            var isInNote = false

            for (line in lines) {
                if (line.startsWith("--- NOTA:")) {
                    // If we were accumulating content for a previous note, save it
                    if (currentTitle != null && currentContent.isNotBlank()) {
                        notesFromFile.add(Pair(currentTitle!!, currentContent.toString().trim()))
                    }
                    // Start new note
                    currentTitle = line.substringAfter("--- NOTA:").substringBeforeLast(" ---").trim()
                    currentContent = StringBuilder()
                    isInNote = true
                } else if (line == "--------------------") {
                    // End of a note section, save it if valid
                    if (currentTitle != null && currentContent.isNotBlank()) {
                        notesFromFile.add(Pair(currentTitle!!, currentContent.toString().trim()))
                    }
                    currentTitle = null // Reset for next potential note
                    currentContent = StringBuilder()
                    isInNote = false
                } else if (isInNote && currentTitle != null) {
                    // Skip date lines for now, just append content
                    if (!line.startsWith("Fecha de creación:") && !line.startsWith("Fecha de modificación:")) {
                        currentContent.append(line).append('\n')
                    }
                }
            }
            // Add the last note if any
            if (currentTitle != null && currentContent.isNotBlank()) {
                notesFromFile.add(Pair(currentTitle!!, currentContent.toString().trim()))
            }

            if (notesFromFile.isEmpty()) {
                Toast.makeText(context, "No se encontraron notas válidas en el archivo.", Toast.LENGTH_LONG).show()
                return@launch
            }

            var importedCount = 0
            var errorCount = 0
            
            // Ahora estamos dentro de una corrutina y podemos llamar a funciones suspendidas
            for ((title, content) in notesFromFile) {
                try {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        // Llamada a la función suspendida y manejo de su resultado
                        val noteId = viewModel.addNote(title = title, content = content, categoryId = null)
                        if (noteId > 0) {
                            importedCount++
                            Log.d("ImportNotes", "Nota importada con ID: $noteId")
                        } else {
                            errorCount++
                            Log.e("ImportNotes", "Error al importar nota: ID inválido")
                        }
                    } else {
                        errorCount++
                        Log.w("ImportNotes", "Nota no válida: título o contenido vacío")
                    }
                } catch (e: Exception) {
                    errorCount++
                    Log.e("ImportNotes", "Error importing note: ${e.message}")
                }
            }

            val message = when {
                importedCount > 0 && errorCount > 0 -> "$importedCount notas importadas, $errorCount errores."
                importedCount > 0 -> "$importedCount notas importadas correctamente."
                else -> "No se pudo importar ninguna nota."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("ImportNotes", "Error during import", e)
        }
    }
}