package com.tambuchosecretdev.quickzenapp.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Utilidad para guardar imágenes y capturas de layouts/composables
 */
object ImageSaver {
    private const val TAG = "ImageSaver"
    
    // Constantes para la optimización de imágenes
    private const val MAX_IMAGE_WIDTH = 1920
    private const val MAX_IMAGE_HEIGHT = 1080
    private const val IMAGE_QUALITY = 90
    
    /**
     * Guarda un bitmap en el almacenamiento externo
     * @param context Contexto de la aplicación
     * @param bitmap El bitmap a guardar
     * @param fileName Nombre del archivo sin extensión
     * @param folderName Nombre de la carpeta donde se guardará
     * @return Uri del archivo guardado o null si ocurrió un error
     */
    suspend fun saveBitmapToStorage(
        context: Context, 
        bitmap: Bitmap, 
        fileName: String, 
        folderName: String = "QuickZenApp"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // Para Android 10 (Q) y versiones posteriores, usamos MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folderName")
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext null
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                
                Log.d(TAG, "Imagen guardada con éxito en: $uri")
                return@withContext uri
            } 
            // Para versiones anteriores a Android 10
            else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(imagesDir, folderName)
                
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                
                val imageFile = File(appDir, "$fileName.jpg")
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                
                val uri = Uri.fromFile(imageFile)
                Log.d(TAG, "Imagen guardada con éxito en: $uri")
                return@withContext uri
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error al guardar la imagen: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Carga una imagen desde un Uri y la guarda en el almacenamiento
     * @param context Contexto de la aplicación
     * @param sourceUri Uri de la imagen a guardar
     * @param fileName Nombre del archivo sin extensión
     * @param folderName Nombre de la carpeta donde se guardará
     * @return Uri del archivo guardado o null si ocurrió un error
     */
    suspend fun saveBitmapFromUri(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        folderName: String = "QuickZenApp"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // Cargar la imagen desde el Uri
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            
            if (inputStream == null) {
                Log.e(TAG, "No se pudo abrir el stream de entrada para $sourceUri")
                return@withContext null
            }
            
            // Decodificar la imagen a bitmap
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                Log.e(TAG, "No se pudo decodificar la imagen desde $sourceUri")
                return@withContext null
            }
            
            // Guardar el bitmap
            return@withContext saveBitmapToStorage(context, bitmap, fileName, folderName)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen desde Uri: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Captura un View como bitmap
     * @param view La vista a capturar
     * @return Bitmap de la vista capturada
     */
    fun captureView(view: View): Bitmap {
        // Habilitar drawing cache para la vista
        view.isDrawingCacheEnabled = true
        view.buildDrawingCache()
        
        // Capturar el bitmap
        val bitmap = view.drawToBitmap()
        
        // Limpiar el cache
        view.destroyDrawingCache()
        view.isDrawingCacheEnabled = false
        
        return bitmap
    }
    
    /**
     * Captura un layout completo y lo guarda como imagen
     * Similar al enfoque sugerido por el usuario, permite capturar layouts
     * con sus componentes anidados (ImageViews, TextViews, etc.)
     * 
     * @param context Contexto de la aplicación
     * @param layout El layout a capturar (por ejemplo, un LinearLayout, ConstraintLayout, etc.)
     * @param fileName Nombre del archivo sin extensión
     * @param folderName Nombre de la carpeta donde se guardará
     * @return Uri de la imagen guardada o null si ocurrió un error
     */
    suspend fun captureAndSaveLayout(
        context: Context,
        layout: View,
        fileName: String,
        folderName: String = "QuickZenApp"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // Habilitar drawing cache y construirlo
            layout.isDrawingCacheEnabled = true
            layout.buildDrawingCache()
            
            // Obtener el bitmap del cache
            val bitmap = layout.drawToBitmap()
            
            // Guardar el bitmap usando el método existente
            val result = saveBitmapToStorage(context, bitmap, fileName, folderName)
            
            // Limpiar el cache para liberar memoria
            layout.destroyDrawingCache()
            layout.isDrawingCacheEnabled = false
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error al capturar y guardar layout: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Crea una vista temporal con un composable y la captura como bitmap
     * @param context Contexto de la aplicación
     * @param content El composable a capturar
     * @param width Ancho de la vista (por defecto WRAP_CONTENT)
     * @param height Alto de la vista (por defecto WRAP_CONTENT)
     * @return Bitmap del composable
     */
    fun captureComposable(
        context: Context,
        content: @Composable () -> Unit,
        width: Int = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        height: Int = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    ): Bitmap {
        // Crear una vista ComposeView temporal
        val composeView = ComposeView(context).apply {
            // Configurar para que se componga una única vez
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }
        
        // Medir y ajustar el tamaño de la vista
        composeView.measure(width, height)
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
        
        // Capturar el bitmap
        return captureView(composeView)
    }
    
    /**
     * Optimiza una imagen desde un Uri para su uso en la aplicación
     * - Redimensiona si es demasiado grande
     * - Corrige la rotación según EXIF
     * - Comprime para ahorrar espacio
     *
     * @param context Contexto de la aplicación
     * @param imageUri Uri de la imagen a optimizar
     * @return Uri de la imagen optimizada guardada en almacenamiento interno de la app
     */
    suspend fun processImageForNote(context: Context, imageUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            // Obtener el nombre original del archivo si es posible
            var fileName = getFileNameFromUri(context, imageUri) ?: "image_${System.currentTimeMillis()}"
            if (!fileName.contains(".")) {
                fileName = "$fileName.jpg"
            }
            
            // Decodificar el bitmap desde Uri con opciones para muestreo
            val bitmap = decodeSampledBitmapFromUri(context, imageUri, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
                ?: return@withContext null
            
            // Corregir orientación según EXIF si es necesario
            val rotatedBitmap = correctOrientation(context, imageUri, bitmap)
            
            // Guardar en almacenamiento interno de la app
            val internalFile = saveToInternalStorage(context, rotatedBitmap, fileName)
            if (internalFile != null) {
                // Crear un content Uri usando FileProvider
                return@withContext FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".fileprovider",
                    internalFile
                )
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar imagen: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Obtiene el nombre de archivo desde un Uri
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) {
                    fileName = cursor.getString(columnIndex)
                }
            }
        }
        return fileName
    }
    
    /**
     * Decodifica un bitmap desde un Uri con muestreo para evitar OutOfMemoryError
     */
    private fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        try {
            // Primera decodificación para verificar dimensiones
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            // Calcular factor de muestreo
            options.apply {
                inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
                inJustDecodeBounds = false
            }
            
            // Decodificar con el factor de muestreo
            var bitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error al decodificar bitmap: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Calcula el factor de muestreo óptimo
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Calcular el mayor factor de muestreo que es potencia de 2 y mantiene
            // el ancho y alto más grandes que los requeridos
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Corrige la orientación de la imagen según los metadatos EXIF
     */
    private fun correctOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            // En Android 10+ no podemos acceder directamente al archivo, así que intentamos con ExifInterface del InputStream
            if (uri.scheme == "content") {
                var orientation = ExifInterface.ORIENTATION_NORMAL
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val exif = ExifInterface(inputStream)
                            orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al leer EXIF: ${e.message}", e)
                    return bitmap
                }
                
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
                    else -> return bitmap
                }
                
                return Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
            }
            
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error al corregir orientación: ${e.message}", e)
            return bitmap
        }
    }
    
    /**
     * Guarda un bitmap en el almacenamiento interno de la aplicación
     */
    private fun saveToInternalStorage(context: Context, bitmap: Bitmap, fileName: String): File? {
        try {
            // Usar directorio de archivos interno para imágenes
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Crear un archivo con nombre único para evitar colisiones
            val uniqueFileName = "${UUID.randomUUID()}_$fileName"
            val file = File(imagesDir, uniqueFileName)
            
            // Comprimir y guardar
            FileOutputStream(file).use { outputStream ->
                // Usamos JPEG para mejor compresión de fotos
                bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
                outputStream.flush()
            }
            
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar en almacenamiento interno: ${e.message}", e)
            return null
        }
    }
}
