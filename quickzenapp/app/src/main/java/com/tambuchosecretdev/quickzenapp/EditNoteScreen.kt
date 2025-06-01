package com.tambuchosecretdev.quickzenapp

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.tambuchosecretdev.quickzenapp.components.ImageThumbnail
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    viewModel: NotesViewModel<Any?>,
    noteId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var existingImages by remember { mutableStateOf<MutableList<String>>(mutableListOf()) }
    var noteColorId by remember { mutableStateOf(NoteAppColors.DEFAULT_COLOR_ID) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf(false) }
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    
    // Variables para la función de añadir categoría
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    
    // Formato para mostrar la fecha y hora
    val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    // Load the note data if editing
    // Cargar datos de la nota cuando se abre para editar
    LaunchedEffect(noteId) {
        if (noteId != null && noteId != 0L) {
            coroutineScope.launch {
                try {
                    // Mostrar un mensaje de carga
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cargando nota...", Toast.LENGTH_SHORT).show()
                    }
                    
                    val note = viewModel.getNoteById(noteId)
                    if (note != null) {
                        Log.d("EditNoteScreen", "Cargando nota: ${note.id}, título: ${note.title}, contenido: ${note.content.take(50)}...")
                        Log.d("EditNoteScreen", "Imágenes cargadas: ${note.images}")
                        
                        // Actualizar los estados con los datos de la nota
                        title = note.title
                        content = note.content
                        existingImages = note.images.toMutableList() // Convertir a lista mutable para facilitar modificaciones
                        noteColorId = note.colorId
                        selectedCategoryId = note.categoryId
                        isFavorite = note.isFavorite
                        isLocked = note.isLocked
                        
                        // Procesar las imágenes existentes
                        if (note.images.isNotEmpty()) {
                            try {
                                // Convertir la primera imagen a Uri para mostrarla como principal
                                val firstImage = note.images.first()
                                Log.d("EditNoteScreen", "Cargando imagen principal: $firstImage")
                                
                                // Asegurarse de que la URI sea válida
                                try {
                                    // Intentar crear una URI válida
                                    val uri = Uri.parse(firstImage)
                                    
                                    // Verificar si la URI es válida
                                    if (uri.scheme != null && (uri.scheme == "content" || uri.scheme == "file")) {
                                        imageUri = uri
                                    } else {
                                        // Si no tiene esquema, intentar crear una URI de archivo
                                        val file = File(context.filesDir, firstImage)
                                        if (file.exists()) {
                                            imageUri = Uri.fromFile(file)
                                        } else {
                                            // Intentar como ruta relativa
                                            imageUri = Uri.parse("file://${context.filesDir}/$firstImage")
                                            Log.d("EditNoteScreen", "Creando URI desde ruta relativa: ${imageUri}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("EditNoteScreen", "Error al parsear URI: $firstImage", e)
                                    imageUri = null
                                }
                                
                                // Registrar todas las imágenes para depuración
                                note.images.forEachIndexed { index, img ->
                                    Log.d("EditNoteScreen", "Imagen $index: $img")
                                }
                            } catch (e: Exception) {
                                Log.e("EditNoteScreen", "Error al procesar imágenes: ${e.message}", e)
                                imageUri = null
                            }
                        } else {
                            Log.d("EditNoteScreen", "La nota no tiene imágenes")
                            imageUri = null
                        }
                        
                        // Notificar que la carga fue exitosa
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Nota cargada correctamente", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("EditNoteScreen", "No se encontró la nota con ID: $noteId")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error: No se encontró la nota", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EditNoteScreen", "Error al cargar la nota: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al cargar la nota: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Gallery image picker
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }
    
    // Function to save the note - implementación más robusta
    val saveNote: () -> Unit = {
        Log.d("EditNoteScreen", "Save note button clicked")
        
        // Mostrar un Toast de "Guardando..."
        Toast.makeText(context, "Guardando nota...", Toast.LENGTH_SHORT).show()
        
        coroutineScope.launch {
            try {
                // Registrar información detallada sobre el intento de guardado
                Log.d("EditNoteScreen", "Iniciando guardado de nota")
                Log.d("EditNoteScreen", "Título: $title")
                Log.d("EditNoteScreen", "Contenido: ${if (content.length > 50) content.take(50) + "..." else content}")
                Log.d("EditNoteScreen", "Color: $noteColorId")
                Log.d("EditNoteScreen", "Categoría: $selectedCategoryId")
                Log.d("EditNoteScreen", "Favorito: $isFavorite")
                Log.d("EditNoteScreen", "Bloqueada: $isLocked")
                Log.d("EditNoteScreen", "Imágenes: ${existingImages.size}")
                
                // Verificar que la nota no esté completamente vacía
                if (title.isBlank() && content.isBlank() && existingImages.isEmpty()) {
                    Log.w("EditNoteScreen", "La nota está vacía - no se guardará")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "La nota no puede estar vacía", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Usar un título por defecto si está en blanco
                val finalTitle = if (title.isBlank()) "Nota sin título" else title
                
                // Procesar las imágenes
                val finalImages = mutableListOf<String>()
                
                // Añadir la nueva imagen si existe y no está ya en la lista
                if (imageUri != null) {
                    val uriString = imageUri.toString()
                    if (!existingImages.contains(uriString)) {
                        finalImages.add(uriString)
                        Log.d("EditNoteScreen", "Añadiendo nueva imagen: $uriString")
                    }
                }
                
                // Añadir imágenes existentes (filtrar las que puedan ser inválidas)
                existingImages.forEach { imagePath ->
                    if (imagePath.isNotBlank()) {
                        finalImages.add(imagePath)
                    }
                }
                
                val currentTimeMillis = System.currentTimeMillis()
                
                // Si estamos editando una nota existente
                if (noteId != null && noteId != 0L) {
                    Log.d("EditNoteScreen", "Actualizando nota existente con ID: $noteId")
                    
                    try {
                        val existingNote = viewModel.getNoteById(noteId)
                        if (existingNote == null) {
                            Log.e("EditNoteScreen", "No se encontró la nota con ID: $noteId")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: No se encontró la nota", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        
                        // Crear objeto actualizado con los valores finales
                        val updatedNote = existingNote.copy(
                            title = finalTitle,
                            content = content,
                            images = finalImages,
                            colorId = noteColorId,
                            categoryId = selectedCategoryId,
                            isFavorite = isFavorite,
                            isLocked = isLocked,
                            modifiedDate = currentTimeMillis,
                            needsSync = true
                        )
                        
                        // Guardar la nota actualizada con todos los campos
                        viewModel.updateNote(
                            id= updatedNote.id,
                            title= finalTitle,
                            content= content,
                            categoryId= selectedCategoryId,
                            reminder= updatedNote.reminderDateTime,
                            colorId= noteColorId,
                            isFavorite= isFavorite,
                            isLocked= isLocked,
                            images= finalImages
                        )
                        
                        Log.d("EditNoteScreen", "Nota actualizada exitosamente: ${updatedNote.id}")
                        Log.d("EditNoteScreen", "Fecha de modificación: ${dateFormatter.format(Date(currentTimeMillis))}")
                        Log.d("EditNoteScreen", "Imágenes guardadas: ${finalImages.size}")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Nota actualizada correctamente", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("EditNoteScreen", "Error al actualizar nota: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error al actualizar nota: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                } 
                // Si es una nueva nota
                else {
                    Log.d("EditNoteScreen", "Creando nueva nota con título: $finalTitle")
                    try {
                        // Intentar guardar la nueva nota
                        val newNoteId = viewModel.addNote(
                            title = finalTitle,
                            content = content,
                            images = finalImages,
                            colorId = noteColorId,
                            categoryId = selectedCategoryId,
                            isFavorite = isFavorite,
                            isLocked = isLocked
                        )
                        
                        Log.d("EditNoteScreen", "Nueva nota creada con ID: $newNoteId")
                        Log.d("EditNoteScreen", "Imágenes guardadas: ${finalImages.size}")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Nota creada correctamente", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("EditNoteScreen", "Error al crear nota: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error al crear nota: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
                
                // Pequeña pausa para que el usuario pueda ver el Toast
                delay(500)
                
                // Registrar el fin del proceso de guardado
                Log.d("EditNoteScreen", "Proceso de guardado completado exitosamente")
                
                // Navegar a la pantalla de inicio después de guardar
                withContext(Dispatchers.Main) {
                    onNavigateToHome()
                }
                
            } catch (e: Exception) {
                Log.e("EditNoteScreen", "Error al guardar la nota", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
                
    // Función para añadir una nueva categoría
    val addCategoryToDb = {
        if (newCategoryName.isNotBlank()) {
            // Usamos coroutineScope para llamar a funciones suspendidas
            coroutineScope.launch {
                try {
                    // Llamamos el método del viewModel dentro de una coroutine
                    val categoryId = viewModel.addCategory(newCategoryName)
                    Log.d("EditNoteScreen", "Categoría añadida con ID: $categoryId")
                    
                    // Refrescamos las categorías para ver la actualización
                    viewModel.refreshCategories()
                } catch (e: Exception) {
                    Log.e("EditNoteScreen", "Error al añadir categoría: ${e.message}")
                }
            }
            
            // La UI se actualizará automáticamente cuando las categorías cambien
            Log.d("EditNoteScreen", "Intentando añadir categoría: $newCategoryName")
        }
    }

    // Handle the back button press - make sure to handle unsaved changes
    BackHandler {
        onNavigateBack()
    }
    
    // Dialog para añadir nueva categoría
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddCategoryDialog = false
                newCategoryName = ""
            },
            title = { Text("Añadir categoría") },
            text = {
                Column {
                    Text("Introduce el nombre de la nueva categoría:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nombre de categoría") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addCategoryToDb()
                        showAddCategoryDialog = false
                        newCategoryName = ""
                    },
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showAddCategoryDialog = false
                        newCategoryName = ""
                    }
                ) {
                    Text("Cancelar")
                }
            },
            properties = DialogProperties(dismissOnClickOutside = true)
        )
    }

    Scaffold(
        containerColor = NoteAppColors.getColorById(noteColorId),
        topBar = {
            TopAppBar(
                title = { Text(if (noteId != null && noteId != 0L) "Editar nota" else "Crear nota") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Favorite and lock buttons
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color.Yellow else Color.Gray
                        )
                    }
                    IconButton(onClick = { isLocked = !isLocked }) {
                        Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Bloquear",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    // Save button
                    IconButton(onClick = saveNote) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar nota")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()) {

            // Title input field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content input field
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Contenido") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f),
                colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Image Picker y vista previa de imágenes
            Text("Imágenes adjuntas", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Vista previa de imágenes con LazyRow
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mostrar imágenes existentes
                items(existingImages) { imagePath ->
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        // Usar un color simple como fondo para la imagen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                        
                        // Cargar la imagen con múltiples métodos de respaldo
                        val painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(imagePath) // Intentar la ruta directamente
                                .crossfade(true)
                                .placeholder(R.drawable.ic_launcher_background)
                                .error(R.drawable.ic_launcher_foreground)
                                .build(),
                            onError = { 
                                Log.e("EditNoteScreen", "Error al cargar miniatura: $imagePath", it.result.throwable)
                            }
                        )
                        
                        Image(
                            painter = painter,
                            contentDescription = "Imagen adjunta",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Botón para eliminar la imagen
                        IconButton(
                            onClick = { 
                                val newList = existingImages.filter { it != imagePath }.toMutableList()
                                existingImages = newList
                                Log.d("EditNoteScreen", "Imagen eliminada: $imagePath, quedan: ${existingImages.size}")
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Eliminar imagen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                // Mostrar la nueva imagen si existe y no está en existingImages
                if (imageUri != null && !existingImages.contains(imageUri.toString())) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(imageUri)
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = "Nueva imagen",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Botón para eliminar la imagen
                            IconButton(
                                onClick = { 
                                    imageUri = null 
                                    Log.d("EditNoteScreen", "Imagen nueva eliminada")
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.7f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Eliminar imagen",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Botón para añadir nueva imagen
                item {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { 
                                galleryLauncher.launch("image/*")
                                Log.d("EditNoteScreen", "Abriendo selector de imágenes")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir imagen",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sección de previsualización de imágenes
            if (existingImages.isNotEmpty() || imageUri != null) {
                Text(
                    text = "Imágenes adjuntas:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                // Usar LazyRow para mostrar todas las imágenes como miniaturas
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mostrar imágenes existentes
                    items(existingImages) { imagePath ->
                        // Parsear la URI fuera del composable
                        val parsedUri = remember(imagePath) {
                            try {
                                Uri.parse(imagePath)
                            } catch (e: Exception) {
                                Log.e("EditNoteScreen", "Error al parsear URI: $imagePath", e)
                                null
                            }
                        }
                        
                        // Solo mostrar la miniatura si la URI se pudo parsear
                        if (parsedUri != null) {
                            ImageThumbnail(
                                imageUri = parsedUri,
                                onRemoveClick = {
                                    // Crear una nueva lista sin la imagen eliminada
                                    val newList = existingImages.filter { it != imagePath }.toMutableList()
                                    existingImages = newList
                                    Log.d("EditNoteScreen", "Imagen eliminada: $imagePath, quedan: ${existingImages.size}")
                                    
                                    // Si era la imagen principal y hay otras, seleccionar la primera
                                    val currentImageUri = imageUri // Capturar el valor actual
                                    if (currentImageUri != null && parsedUri.toString() == currentImageUri.toString() && existingImages.isNotEmpty()) {
                                        imageUri = Uri.parse(existingImages.first())
                                    } else if (existingImages.isEmpty()) {
                                        imageUri = null
                                    }
                                }
                            )
                        }
                    }
                    }
                    
                    // Mostrar la imagen recién seleccionada si no está en existingImages
                    // Mostrar la imagen recién seleccionada si existe y no está en la lista
                    val currentImageUri = imageUri // Crear una copia local inmutable
                    if (currentImageUri != null) {
                        val uriString = currentImageUri.toString()
                        if (!existingImages.contains(uriString)) {
                            item {
                                ImageThumbnail(
                                    imageUri = currentImageUri,
                                    onRemoveClick = { imageUri = null }
                                )
                            }
                        }
                    }
                }
                
                // Mostrar la imagen principal en tamaño grande
                imageUri?.let { uri ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Vista previa:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                onError = {
                                    Log.e("EditNoteScreen", "Error al cargar imagen: $uri", it.result.throwable)
                                }
                            ),
                            contentDescription = "Vista previa de imagen",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Color selection
            Text(text = "Color de la nota:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ColorPalette(selectedColorId = noteColorId) { newColorId -> noteColorId = newColorId }

            Spacer(modifier = Modifier.height(16.dp))

            // Category selection
            Text(text = "Categoría", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Improved category selection with proper UI feedback
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable { expandedCategory = true }  // Always opens dropdown on click
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Show the selected category name or "Sin categoría"
                        Text(categories.find { category -> category.id == selectedCategoryId }?.name ?: "Seleccionar categoría")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Abrir categorías")
                    }
                }

                // Dropdown menu for categories with improved visibility
                DropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.surface),
                    properties = PopupProperties(focusable = true)
                ) {
                    // "Sin categoría" option
                    DropdownMenuItem(
                        text = { Text("Sin categoría") },
                        onClick = {
                            selectedCategoryId = null
                            expandedCategory = false
                        }
                    )
                    
                    // Divider for visual separation
                    Divider()
                    
                    // Opción para añadir nueva categoría
                    DropdownMenuItem(
                        text = { Text("➕ Añadir nueva categoría") },
                        onClick = {
                            expandedCategory = false
                            showAddCategoryDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Añadir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    // Divider before existing categories
                    if ((categories as List<*>).isNotEmpty()) {
                        Divider()
                        
                        // Encabezado de categorías existentes
                        Text(
                            "Categorías existentes", 
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Show all available categories
                    // Utilizamos una solución más compatible con ambigüedad de tipos
                    for (category in categories) {
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategoryId = category.id
                                expandedCategory = false
                            },
                            // Visual indicator for the currently selected category
                            trailingIcon = if (category.id == selectedCategoryId) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button at bottom
            Button(
                onClick = saveNote,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar nota")
            }
        }
    }

fun item(function: @Composable () -> Unit) {
    TODO("Not yet implemented")
}


@Composable
fun ColorPalette(
    selectedColorId: String,
    onColorSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items(NoteAppColors.palette) { colorOption ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorOption.composeColor)
                    .border(
                        width = 2.dp,
                        color = if (selectedColorId == colorOption.id) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorOption.id) }
            ) {
                if (selectedColorId == colorOption.id) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (colorOption.id == NoteAppColors.DEFAULT_COLOR_ID) Color.Black else Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
