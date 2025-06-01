package com.tambuchosecretdev.quickzenapp.components

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.tambuchosecretdev.quickzenapp.utils.ImageSaver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Componente reutilizable para mostrar una miniatura de imagen con botón para eliminarla y guardarla
 */
@Composable
fun ImageThumbnail(
    imageUri: Uri,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier.size(100.dp)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(Color.White) // Fondo blanco para mejor contraste
    ) {
        // Imagen
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(imageUri)
                .crossfade(true)
                .size(coil.size.Size.ORIGINAL) // Usar tamaño original
                .build(),
            onError = {
                Log.e("ImageThumbnail", "Error al cargar miniatura: $imageUri", it.result.throwable)
            }
        )
        
        Image(
            painter = painter,
            contentDescription = "Miniatura de imagen",
            contentScale = ContentScale.Inside, // Inside respeta el tamaño original y cabe todo
            alignment = Alignment.Center, // Centrar la imagen
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp) // Un poco de padding interno
        )
        
        // Botones para eliminar y guardar
        Row(
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            // Botón para guardar
            IconButton(
                onClick = {
                    // Usar la utilidad ImageSaver para guardar la imagen
                    coroutineScope.launch {
                        try {
                            // Generar un nombre único basado en la fecha/hora
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val fileName = "QuickZen_$timestamp"
                            
                            // Si es una imagen local, simplemente copiamos el archivo
                            if (imageUri.scheme == "file" || imageUri.scheme == "content") {
                                // Usar la utilidad para guardar
                                val savedUri = ImageSaver.saveBitmapFromUri(context, imageUri, fileName)
                                
                                if (savedUri != null) {
                                    Toast.makeText(context, "Imagen guardada en galería", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No se pudo guardar la imagen", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageThumbnail", "Error al guardar imagen: ${e.message}", e)
                            Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Guardar imagen",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Botón para eliminar
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
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
