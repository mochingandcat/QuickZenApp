package com.tambuchosecretdev.quickzenapp

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tambuchosecretdev.quickzenapp.components.ImageThumbnail
import com.tambuchosecretdev.quickzenapp.model.Category
import com.tambuchosecretdev.quickzenapp.model.NoteAppColors
import com.tambuchosecretdev.quickzenapp.ui.theme.QuickZenAppTheme
import com.tambuchosecretdev.quickzenapp.utils.ImageSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateNoteActivity : ComponentActivity() {
    private lateinit var viewModel: NotesViewModel<Any?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar el ViewModel utilizando la Factory
        val application = application as QuickZenApp // Asegúrate de que QuickZenApp expone las dependencias
        val noteRepository = application.noteRepository // Asume que noteRepository está en QuickZenApp
        val firebaseManager = application.firebaseManager // Asume que firebaseManager está en QuickZenApp
        val viewModelFactory = NotesViewModelFactory(noteRepository, application, firebaseManager)
        viewModel =ViewModelProvider(this, viewModelFactory)[NotesViewModel::class.java] as NotesViewModel<Any?>
        
        // Obtener el ID de la nota si estamos editando una existente
        // Usar una forma segura de obtener el ID para evitar NullPointerException
        val noteId = if (intent.hasExtra("NOTE_ID")) intent.getLongExtra("NOTE_ID", 0L) else 0L
        val isEditing = noteId > 0L
        
        Log.d("CreateNoteActivity", "onCreate: noteId=$noteId, isEditing=$isEditing, hasExtras=${intent.extras != null}")
        
        // Debugging - registrar todos los extras del intent para depuración
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                Log.d("CreateNoteActivity", "Extra: $key = ${bundle.get(key)}")
            }
        } ?: Log.d("CreateNoteActivity", "No hay extras en el intent")
        
        setContent {
            QuickZenAppTheme {
                // Cargamos la nota existente si estamos editando
                val categories by viewModel.categories.collectAsState(initial = emptyList())
                
                if (isEditing) {
                    // Si estamos editando, cargar los datos de la nota existente
                    var noteLoaded by remember { mutableStateOf(false) }
                    var initialTitle by remember { mutableStateOf("") }
                    var initialContent by remember { mutableStateOf("") }
                    var initialColorId by remember { mutableStateOf(NoteAppColors.DEFAULT_COLOR_ID) }
                    var initialCategoryId by remember { mutableStateOf<Long?>(null) }
                    var initialIsFavorite by remember { mutableStateOf(false) }
                    var initialIsLocked by remember { mutableStateOf(false) }
                    var initialImages by remember { mutableStateOf<List<String>>(emptyList()) }
                    
                    // Cargar los datos de la nota existente usando una variable que indicará si ya se cargó la nota
                    var loadStarted by remember { mutableStateOf(false) }
                    
                    if (!loadStarted) {
                        loadStarted = true
                        // Usar la versión no suspendida de getNoteById
                        val noteByIdNonSuspend=viewModel.getNoteByIdNonSuspend(noteId!!) { note ->
                            if (note != null) {
                                // Actualizamos todos los estados con los datos de la nota
                                initialTitle=note.title
                                initialContent=note.content
                                initialColorId=note.colorId
                                initialCategoryId=note.categoryId
                                initialIsFavorite=note.isFavorite
                                initialIsLocked=note.isLocked
                                initialImages=note.images
                                noteLoaded=true
                                Log.d(
                                    "CreateNoteActivity",
                                    "Nota cargada: $noteId, título: ${note.title}"
                                )
                            } else {
                                Toast.makeText(
                                    this@CreateNoteActivity,
                                    "Error: No se encontró la nota",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    }
                    
                    if (noteLoaded) {
                        // Mostrar la pantalla de edición con los datos cargados
                        CreateNoteScreen(
                            categories = categories,
                            initialTitle = initialTitle,
                            initialContent = initialContent,
                            initialColorId = initialColorId,
                            initialCategoryId = initialCategoryId,
                            initialIsFavorite = initialIsFavorite,
                            initialIsLocked = initialIsLocked,
                            initialImages = initialImages,
                            onBackPressed = { finish() },
                            onNoteSaved = { title, content, colorId, categoryId, isFavorite, isLocked, images ->
                                // Actualizar la nota existente
                                updateExistingNote(noteId, title, content, colorId, categoryId, isFavorite, isLocked, images)
                            },
                            onAddCategory = { categoryName ->
                                lifecycleScope.launch {
                                    val newId = viewModel.addCategory(categoryName)
                                    if (newId > 0) {
                                        Toast.makeText(this@CreateNoteActivity, "Categoría '$categoryName' creada", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@CreateNoteActivity, "Error al crear categoría", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        // Mostrar indicador de carga mientras se obtienen los datos
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // Pantalla para crear una nueva nota
                    CreateNoteScreen(
                        categories = categories,
                        onBackPressed = { finish() },
                        onNoteSaved = { title, content, colorId, categoryId, isFavorite, isLocked, images ->
                            // Guardar la nota usando el ViewModel
                            saveNote(title, content, colorId, categoryId, isFavorite, isLocked, images)
                        },
                        onAddCategory = { categoryName ->
                            lifecycleScope.launch {
                                val newId = viewModel.addCategory(categoryName)
                                if (newId > 0) {
                                    Toast.makeText(this@CreateNoteActivity, "Categoría '$categoryName' creada", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@CreateNoteActivity, "Error al crear categoría", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    class LaunchedEffect(unit: Unit, function: () -> Any) : Parcelable {
        constructor(parcel: Parcel) : this(
            TODO("unit"),
            TODO("function")
        ) {
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {

        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<LaunchedEffect> {
            override fun createFromParcel(parcel: Parcel): LaunchedEffect {
                return LaunchedEffect(parcel)
            }

            override fun newArray(size: Int): Array<LaunchedEffect?> {
                return arrayOfNulls(size)
            }
        }

    }

    class CircularProgressIndicator {

    }

    private fun saveNote(title: String, content: String, colorId: String, categoryId: Long?, 
                        isFavorite: Boolean, isLocked: Boolean, images: List<String>) {
        // Mostrar un indicador de carga
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Guardando nota...")
            setCancelable(false)
            show()
        }
        
        // Usar lifecycleScope para mantener la corrutina vinculada al ciclo de vida de la actividad
        lifecycleScope.launch {
            try {
                // Validar campos obligatorios
                if (title.isBlank() && content.isBlank()) {
                    throw IllegalArgumentException("La nota debe tener un título o contenido")
                }
                
                // Llamar a la función suspendida para guardar la nota
                val noteId = viewModel.addNote(
                    title = title,
                    content = content,
                    colorId = colorId,
                    categoryId = categoryId,
                    isFavorite = isFavorite,
                    isLocked = isLocked,
                    images = images
                )
                
                // Verificar si la operación fue exitosa
                if (noteId > 0) {
                    // Forzar sincronización inmediata con Firebase para actualizar la UI
                    try {
                        // Sincronizar con Firebase para asegurar que la UI se actualiza
                        viewModel.initiateSync()
                        Log.d("CreateNoteActivity", "Sincronización forzada después de guardar nota")
                    } catch (e: Exception) {
                        Log.e("CreateNoteActivity", "Error al sincronizar después de guardar nota", e)
                        // No bloqueamos el flujo si falla la sincronización, continuamos normalmente
                    }
                    
                    // Nota guardada correctamente
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@CreateNoteActivity, "Nota guardada correctamente", Toast.LENGTH_SHORT).show()
                        
                        // Navegar de vuelta a la pantalla principal sin crear una nueva instancia
                        // Para asegurar que volvemos a la MainActivity existente y no creamos una nueva
                        val intent = Intent(this@CreateNoteActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            // Añadir un extra para indicar que venimos de guardar una nota, para forzar recarga
                            putExtra("FROM_EDIT", true)
                            putExtra("SYNC_NEEDED", true) // Nueva bandera para indicar que se necesita sincronizar
                        }
                        startActivity(intent)
                        finish() // Cerrar esta actividad
                    }
                } else {
                    throw Exception("No se pudo guardar la nota: ID inválido")
                }
            } catch (e: Exception) {
                // Manejar errores
                Log.e("CreateNoteActivity", "Error al guardar nota", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    // Mostrar mensaje de error al usuario
                    android.app.AlertDialog.Builder(this@CreateNoteActivity)
                        .setTitle("Error")
                        .setMessage("No se pudo guardar la nota: ${e.message ?: "Error desconocido"}")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
        }
    }
    
    private fun updateExistingNote(noteId: Long, title: String, content: String, colorId: String, categoryId: Long?, 
                        isFavorite: Boolean, isLocked: Boolean, images: List<String>) {
        // Mostrar un indicador de carga
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Actualizando nota...")
            setCancelable(false)
            show()
        }
        
        // Usar lifecycleScope para mantener la corrutina vinculada al ciclo de vida de la actividad
        lifecycleScope.launch {
            try {
                // Validar campos obligatorios
                if (title.isBlank() && content.isBlank()) {
                    throw IllegalArgumentException("La nota debe tener un título o contenido")
                }
                
                // Utilizar la versión no suspendida para obtener la nota
                var noteUpdated = false
                var updatingError: Exception? = null
                
                viewModel.getNoteByIdNonSuspend(noteId) { currentNote ->
                    if (currentNote == null) {
                        updatingError = Exception("No se encontró la nota a actualizar")
                        return@getNoteByIdNonSuspend
                    }
                    
                    try {
                        // Crear una copia actualizada de la nota
                        val updatedNote = currentNote.copy(
                            title = title,
                            content = content,
                            colorId = colorId,
                            categoryId = categoryId,
                            isFavorite = isFavorite,
                            isLocked = isLocked,
                            images = images,
                            modifiedDate = System.currentTimeMillis(),
                            needsSync = true
                        )
                        
                        // Actualizar la nota en la base de datos (dentro de otra coroutine)
                        lifecycleScope.launch {
                            viewModel.updateNote(
                                id= updatedNote.id,
                                title= updatedNote.title,
                                content= updatedNote.content,
                                categoryId= updatedNote.categoryId,
                                reminder= updatedNote.reminderDateTime
                            )
                            noteUpdated = true
                        }
                    } catch (e: Exception) {
                        updatingError = e
                    }
                }
                
                // Esperar a que se complete la actualización o se genere un error
                var attempts = 0
                while (!noteUpdated && updatingError == null && attempts < 50) {
                    delay(100) // Esperar 100ms
                    attempts++
                }
                
                if (updatingError != null) {
                    throw updatingError!!
                }
                
                if (!noteUpdated) {
                    throw Exception("Timeout al actualizar la nota")
                }
                
                // Nota actualizada correctamente
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@CreateNoteActivity, "Nota actualizada correctamente", Toast.LENGTH_SHORT).show()
                    
                    // Navegar de vuelta a la pantalla principal
                    val intent = Intent(this@CreateNoteActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP ; Intent.FLAG_ACTIVITY_SINGLE_TOP
                        // Añadir un extra para indicar que venimos de editar una nota
                        putExtra("FROM_EDIT", true)
                    }
                    startActivity(intent)
                    finish() // Cerrar esta actividad
                }
            } catch (e: Exception) {
                // Manejar errores
                Log.e("CreateNoteActivity", "Error al actualizar nota", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    // Mostrar mensaje de error al usuario
                    android.app.AlertDialog.Builder(this@CreateNoteActivity)
                        .setTitle("Error")
                        .setMessage("No se pudo actualizar la nota: ${e.message ?: "Error desconocido"}")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
        }
    }
}

private operator fun Any.compareTo(i: Int): Int {
    TODO("Not yet implemented")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteScreen(
    categories: List<Category> = emptyList(),
    initialTitle: String = "",
    initialContent: String = "",
    initialColorId: String = NoteAppColors.DEFAULT_COLOR_ID,
    initialCategoryId: Long? = null,
    initialIsFavorite: Boolean = false,
    initialIsLocked: Boolean = false,
    initialImages: List<String> = emptyList(),
    onBackPressed: () -> Unit,
    onNoteSaved: (String, String, String, Long?, Boolean, Boolean, List<String>) -> Unit,
    onAddCategory: (String) -> Unit // Nuevo callback
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Estados para los campos de la nota
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var selectedColorId by remember { mutableStateOf(initialColorId) }
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    var isFavorite by remember { mutableStateOf(initialIsFavorite) }
    var isLocked by remember { mutableStateOf(initialIsLocked) }
    val images = remember { mutableStateListOf<String>().apply {
        // Cargar las imágenes iniciales si existen
        if (initialImages.isNotEmpty()) {
            addAll(initialImages)
        }
    } }
    
    // Estados para diálogos
    var showColorPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    
    // Obtener el color seleccionado
    val backgroundColor = NoteAppColors.getColorById(selectedColorId)
    
    // Estado para mostrar el progreso de carga de imágenes
    var isImageLoading by remember { mutableStateOf(false) }
    
    // Launcher para seleccionar imágenes con optimización
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Mostrar indicador de carga
            isImageLoading = true
            
            // Procesar y optimizar la imagen en un hilo secundario
            scope.launch {
                try {
                    // Usar ImageSaver para optimizar la imagen
                    val optimizedUri = ImageSaver.processImageForNote(context, selectedUri)
                    
                    // Añadir la imagen optimizada a la lista
                    optimizedUri?.let { 
                        images.add(it.toString())
                        Toast.makeText(context, "Imagen añadida y optimizada", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(context, "No se pudo procesar la imagen", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("CreateNoteActivity", "Error al procesar imagen: ${e.message}", e)
                    Toast.makeText(context, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
                } finally {
                    isImageLoading = false
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Nueva Nota") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    // Botón para guardar
                    IconButton(
                        onClick = { 
                            if (title.isNotBlank() || content.isNotBlank()) {
                                if (isLocked && !showPinDialog) {
                                    showPinDialog = true
                                } else {
                                    onNoteSaved(title, content, selectedColorId, selectedCategoryId, isFavorite, isLocked, images.toList())
                                }
                            } else {
                                Toast.makeText(context, "La nota no puede estar vacía", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Guardar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .background(backgroundColor.copy(alpha = 0.1f)), // Color de fondo según el color seleccionado
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Título
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Contenido
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Contenido") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                singleLine = false
            )
            
            // Imágenes añadidas
            if (images.isNotEmpty() || isImageLoading) {
                Text(
                    text = "Imágenes adjuntas:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mostrar indicador de carga si está procesando una imagen
                    if (isImageLoading) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Mostrar miniaturas de imágenes
                    images.forEachIndexed { index, uriString ->
                        // Verificar si el URI es válido antes de intentar mostrarlo
                        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
                        
                        if (uri != null) {
                            // Si el URI es válido, mostrar la miniatura
                            ImageThumbnail(
                                imageUri = uri,
                                onRemoveClick = { images.removeAt(index) },
                                modifier = Modifier.size(100.dp)
                            )
                        } else {
                            // Fallback por si hay un problema con el URI
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray)
                            ) {
                                Text(
                                    text = "Error",
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red
                                )
                                
                                IconButton(
                                    onClick = { images.removeAt(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Eliminar imagen",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Barra de herramientas para las opciones
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Opciones de la nota",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Seleccionar color
                        IconButton(
                            onClick = { showColorPicker = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(backgroundColor, CircleShape)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        }
                        
                        // Seleccionar categoría
                        IconButton(onClick = { showCategoryPicker = true }) {
                            Icon(Icons.Default.Category, "Categoría")
                        }
                        
                        // Marcar como favorito
                        IconButton(onClick = { isFavorite = !isFavorite }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Añadir imagen (deshabilitado durante la carga)
                        IconButton(
                            onClick = { showImagePicker = true },
                            enabled = !isImageLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image, 
                                contentDescription = "Añadir imagen",
                                tint = if (isImageLoading) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Compartir nota
                        IconButton(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TITLE, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Compartir nota")
                            context.startActivity(shareIntent)
                        }) {
                            Icon(Icons.Default.Share, "Compartir")
                        }
                        
                        // Bloquear/desbloquear con PIN
                        IconButton(onClick = { isLocked = !isLocked }) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Bloqueada" else "Desbloqueada",
                                tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo selector de colores
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Seleccionar color") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Usar la paleta directamente en lugar de getAllColors que no existe
                        NoteAppColors.palette.forEach { colorOption ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colorOption.color)
                                    .border(
                                        width = if (selectedColorId == colorOption.id) 3.dp else 1.dp,
                                        color = if (selectedColorId == colorOption.id) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColorId = colorOption.id
                                        showColorPicker = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
    
    // Diálogo selector de categorías
    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Seleccionar categoría") },
            text = {
                Column {
                    if (categories.isEmpty()) {
                        Text("No hay categorías disponibles")
                    } else {
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategoryId = category.id
                                        showCategoryPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            color = if (selectedCategoryId == category.id)
                                                MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(category.name)
                            }
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Opción para crear nueva categoría
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCategoryPicker = false
                                showAddCategoryDialog = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir categoría"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear nueva categoría")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Diálogo para añadir nueva categoría
    if (showAddCategoryDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        var categoryNameError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Crear nueva categoría") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { 
                            newCategoryName = it
                            if (it.isNotBlank()) categoryNameError = "" 
                        },
                        label = { Text("Nombre de la categoría") },
                        isError = categoryNameError.isNotEmpty(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (categoryNameError.isNotEmpty()) {
                        Text(
                            text = categoryNameError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            onAddCategory(newCategoryName.trim())
                            showAddCategoryDialog = false
                        } else {
                            categoryNameError = "El nombre no puede estar vacío"
                        }
                    }
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Diálogo para seleccionar imagen
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Añadir imagen") },
            text = { Text("Selecciona una imagen de tu galería") },
            confirmButton = {
                TextButton(onClick = { 
                    imagePicker.launch("image/*")
                    showImagePicker = false
                }) {
                    Text("Seleccionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Cancelar")
                }
            }
        )
            /*
            title = { Text("Configurar PIN") },
            text = {
                Column {
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { 
                            if (it.length <= 4) pin = it 
                            errorMessage = ""
                        },
                        label = { Text("PIN de 4 dígitos") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (pin.length == 4 && pin.all { it.isDigit() }) {
                        // Guardar nota con PIN
                        onNoteSaved(title, content, selectedColorId, selectedCategoryId, isFavorite, isLocked, images.toList())
                        // En un caso real, aquí también guardaríamos el PIN usando PinManager
                        showPinDialog = false
                    } else {
                        errorMessage = "El PIN debe tener 4 dígitos"
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    isLocked = false
                    showPinDialog = false 
                }) {
                    Text("Cancelar")
                }
            }
        }
    }
    */
    // Fin del bloque AlertDialog comentado
}
}
