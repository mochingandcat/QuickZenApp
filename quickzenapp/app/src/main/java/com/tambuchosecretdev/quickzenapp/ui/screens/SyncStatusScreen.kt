@file:Suppress("UNREACHABLE_CODE")

package com.tambuchosecretdev.quickzenapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tambuchosecretdev.quickzenapp.NotesViewModel
import com.tambuchosecretdev.quickzenapp.firebase.SyncInfo
import com.tambuchosecretdev.quickzenapp.ui.components.SyncStatusIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.tambuchosecretdev.quickzenapp.firebase.SyncState as FirebaseSyncState

/**
 * Pantalla que muestra el estado detallado de la sincronización en la nube
 * y permite a los usuarios verificar y solucionar problemas de sincronización.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    viewModel: NotesViewModel<Any?>,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Obtenemos estados reales del ViewModel
    val syncState = viewModel.syncState.collectAsState().value
    val lastSyncTime = viewModel.lastSyncTime.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value
    val error = viewModel.error.collectAsState().value
    val operationMessage = viewModel.operationMessage.collectAsState().value
    val isUserSignedIn = viewModel.isUserSignedIn()
    val activeNotes = viewModel.activeNotes.collectAsState(initial = emptyList()).value

    // Crear un objeto SyncInfo local para mantener compatibilidad con el código existente
    val syncInfo = remember(syncState, lastSyncTime) {
        SyncInfo(
            isOnline = true,  // Asumimos que está online si la pantalla está visible
            state = syncState,
            lastSyncTime = lastSyncTime,
            pendingChanges = 0,  // No tenemos esta información, asumimos 0
            syncError = null,
            deviceId = null,
            canSync = isUserSignedIn  // Asumimos que puede sincronizar si está autenticado
        )
    }

    val scrollState = rememberScrollState()

    // Verificamos el estado de sincronización al cargar la pantalla
    LaunchedEffect(Unit) {
        // Verificar el estado de sincronización al cargar la pantalla
        viewModel.initiateSync()
    }

    // Comentamos temporalmente el manejo de errores
    /*
    error?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            // viewModel.clearError() // Asegúrate de que esta función exista en tu ViewModel
        }
    }
    */

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estado de Sincronización") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
                .verticalScroll(scrollState)
        ) {
            // Indicador principal de estado de sincronización
            SyncStatusIndicator(
                syncInfo = syncInfo,
                onSyncRequest = {
                    viewModel.startSync()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de Autenticación
            Text(
                text = "Estado de Autenticación",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isUserSignedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Autenticado",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sesión iniciada con Google")
                        }

                        // Botón para cerrar sesión
                        TextButton(
                            onClick = {
                                scope.launch {
                                    // La función signOut se debe implementar en otra parte
                                Toast.makeText(context, "Esta funcionalidad no está disponible actualmente", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cerrar Sesión")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "No autenticado",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("No has iniciado sesión")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Debes iniciar sesión con Google para sincronizar entre dispositivos",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Botón para iniciar sesión
                        Button(
                            onClick = {
                                // Este flujo requeriría integración completa con la API de Google Sign-In
                                // Actualmente la función requiere una cuenta de Google como parámetro
                                // Se necesita implementar la UI para seleccionar la cuenta priméro
                                Toast.makeText(
                                    context,
                                    "Inicia sesión con Google desde la pantalla principal",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "Iniciar sesión",
                                modifier = Modifier.size(width = 18.dp, height = 18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Iniciar Sesión con Google")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección de Datos
            Text(
                text = "Datos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Notas locales:")
                        Text("${(activeNotes as? List<*>)?.size ?: 0}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cambios pendientes:")
                        Text("${syncInfo.pendingChanges}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (lastSyncTime != 0L) {
                        // Creamos la fecha usando java.util.Date para evitar ambigüedad
                        val date = java.util.Date(lastSyncTime)
                        val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            .format(date)
                        Text("Última sincronización: $formattedTime", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón para sincronizar
                    Button(
                        onClick = {
                            scope.launch {
                                // viewModel.syncWithFirebase()
                                // Comentado temporalmente hasta implementar el método
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = isUserSignedIn && (isLoading == false) && syncInfo.canSync
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sincronizar",
                            modifier = Modifier.size(width = 18.dp, height = 18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sincronizar Ahora")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección de Diagnóstico
            Text(
                text = "Diagnóstico",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Conexión a Internet
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        if (syncInfo.isOnline) {
                            Icon(
                                imageVector = Icons.Default.SignalWifi4Bar,
                                contentDescription = "Conectado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(width = 24.dp, height = 24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SignalWifiOff,
                                contentDescription = "Sin conexión",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(width = 24.dp, height = 24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (syncInfo.isOnline) "Conectado a Internet" else "Sin conexión a Internet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Autenticación
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        if (isUserSignedIn) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Autenticado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(width = 24.dp, height = 24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "No autenticado",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(width = 24.dp, height = 24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUserSignedIn) "Autenticación correcta" else "No autenticado",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Estado de sincronización
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        when (syncState) {
                            FirebaseSyncState.IDLE -> {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "No sincronizado",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sincronización inactiva",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.CONNECTING -> {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Conectando",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conectando con el servidor...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.AUTHENTICATING -> {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Autenticando",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Autenticando usuario...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.SYNCING_UP -> {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Subiendo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Subiendo datos a la nube...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.SYNCING_DOWN -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Descargando",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Descargando datos de la nube...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.SUCCESS -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Sincronizado",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "¡Sincronización completada con éxito!",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.ERROR_SYNC -> {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Error durante la sincronización. Por favor, inténtalo de nuevo.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.ERROR_CONNECTION -> {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Error de conexión. Verifica tu conexión a internet.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            FirebaseSyncState.ERROR_AUTHENTICATION -> {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Error de autenticación. Por favor, inicia sesión de nuevo.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "No sincronizado",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 24.dp, height = 24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Estado de sincronización desconocido.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Mostrar ID del dispositivo
                    syncInfo.deviceId?.let { deviceId ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Dispositivo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ID de este dispositivo: $deviceId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón para verificar estado
                    Button(
                        onClick = {
                            // Verificar si hay notas para mostrar
                            val hasNotes = activeNotes.isNullOrEmpty().not()
                            viewModel.startSync()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Verificar",
                            modifier = Modifier.size(width = 18.dp, height = 18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verificar Estado")
                    }
                }
            }

            // Mostrar mensajes de error o éxito
            val currentError = error
            if (currentError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentError.toString(),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            val currentOperationMessage = operationMessage
            if (currentOperationMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Información",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentOperationMessage.toString(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Mostrar indicador de carga
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}



