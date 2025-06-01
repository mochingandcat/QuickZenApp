package com.tambuchosecretdev.quickzenapp.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.firebase.SyncInfo
import com.tambuchosecretdev.quickzenapp.firebase.SyncState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Componente que muestra el estado actual de la sincronización en la nube
 * y permite a los usuarios iniciar una sincronización manual.
 */
@Composable
fun SyncStatusIndicator(
    syncInfo: SyncInfo,
    onSyncRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSyncing = syncInfo.state == SyncState.SYNCING_UP || 
                    syncInfo.state == SyncState.SYNCING_DOWN ||
                    syncInfo.state == SyncState.CONNECTING ||
                    syncInfo.state == SyncState.AUTHENTICATING
    
    // Animación de rotación para el ícono de sincronización
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // Formato para la fecha de última sincronización
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ícono de estado
                    when {
                        isSyncing -> {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sincronizando",
                                modifier = Modifier.rotate(rotation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        syncInfo.state == SyncState.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Sincronización completa",
                                tint = Color.Green
                            )
                        }
                        syncInfo.state == SyncState.ERROR_CONNECTION -> {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Error de conexión",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        syncInfo.state == SyncState.ERROR_AUTHENTICATION -> {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Error de autenticación",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        syncInfo.state == SyncState.ERROR_SYNC -> {
                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                contentDescription = "Error de sincronización",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Estado de sincronización",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Texto de estado
                    Column {
                        Text(
                            text = when (syncInfo.state) {
                                SyncState.IDLE -> "Sincronización inactiva"
                                SyncState.CONNECTING -> "Conectando..."
                                SyncState.AUTHENTICATING -> "Autenticando..."
                                SyncState.SYNCING_UP -> "Subiendo cambios..."
                                SyncState.SYNCING_DOWN -> "Descargando cambios..."
                                SyncState.FAILED -> "Error de sincronización"
                                SyncState.CANCELLED -> "Sincronización cancelada"
                                SyncState.SUCCESS -> "Sincronizado"
                                SyncState.SYNCED -> "Sincronizado"
                                SyncState.SYNCING -> "Sincronizando..."
                                SyncState.ERROR_CONNECTION -> "Error de conexión"
                                SyncState.ERROR_AUTHENTICATION -> "Error de autenticación"
                                SyncState.ERROR_AUTH -> "Error de autenticación"
                                SyncState.ERROR_SYNC -> "Error de sincronización"
                                SyncState.UNKNOWN -> "Estado desconocido"
                                SyncState.NO_CONNECTION -> "Sin conexión a internet"
                                SyncState.NOT_SIGNED_IN -> "No has iniciado sesión"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Mostrar la última vez sincronizado
                        syncInfo.lastSyncTime?.let { lastSync ->
                            Text(
                                text = "Última sincronización: ${dateFormat.format(lastSync)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Botón para sincronizar manualmente
                if (syncInfo.canSync && !isSyncing) {
                    Button(
                        onClick = { 
                            // Capturamos el evento y lo aislamos para evitar propagación no deseada
                            try {
                                // Llamar directamente a la función de sincronización
                                onSyncRequest()
                            } catch (e: Exception) {
                                Log.e("SyncStatusIndicator", "Error al iniciar sincronización: ${e.message}")
                            }
                        },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            // Evitar que el botón se estire demasiado
                            .wrapContentSize()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sincronizar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sincronizar")
                        }
                    }
                }
            }
            
            // Mostrar información de errores si existe
            AnimatedVisibility(visible = syncInfo.syncError != null) {
                syncInfo.syncError?.let { error ->
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Mostrar información de cambios pendientes
            if (syncInfo.pendingChanges > 0) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Pending,
                        contentDescription = "Cambios pendientes",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cambios pendientes: ${syncInfo.pendingChanges}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Mostrar ID del dispositivo
            syncInfo.deviceId?.let { deviceId ->
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .alpha(0.7f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "ID de dispositivo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Dispositivo: $deviceId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Componente más pequeño y compacto que muestra únicamente el estado de sincronización
 * ideal para barras de herramientas o espacios reducidos.
 */
@Composable
fun CompactSyncIndicator(
    syncInfo: SyncInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSyncing = syncInfo.state == SyncState.SYNCING_UP || 
                    syncInfo.state == SyncState.SYNCING_DOWN ||
                    syncInfo.state == SyncState.CONNECTING ||
                    syncInfo.state == SyncState.AUTHENTICATING
    
    // Animación de rotación para el ícono de sincronización
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Ícono de estado
        when {
            isSyncing -> {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sincronizando",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            syncInfo.state == SyncState.SUCCESS -> {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Sincronización completa",
                    tint = Color.Green
                )
            }
            syncInfo.state == SyncState.ERROR_CONNECTION ||
            syncInfo.state == SyncState.ERROR_AUTHENTICATION ||
            syncInfo.state == SyncState.ERROR_SYNC -> {
                Icon(
                    imageVector = Icons.Default.SyncProblem,
                    contentDescription = "Error de sincronización",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Estado de sincronización",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Mostrar pendientes si hay cambios
        if (syncInfo.pendingChanges > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(syncInfo.pendingChanges.toString())
            }
        }
    }
}
