package com.tambuchosecretdev.quickzenapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.firebase.SyncState

/**
 * Componente reutilizable que muestra el estado de sincronización
 */
@Composable
fun SyncStatusComponent(
    syncState: SyncState,
    isLoading: Boolean,
    lastSyncTime: Long,
    onSyncClick: () -> Unit,
    onForceSyncClick: () -> Unit
) {
    val syncStateText = when (syncState) {
        SyncState.IDLE -> "En espera"
        SyncState.SYNCING -> "Sincronizando..."
        SyncState.SYNCING_UP -> "Subiendo datos..."
        SyncState.SYNCING_DOWN -> "Descargando datos..."
        SyncState.SUCCESS, SyncState.SYNCED -> "Sincronización completa"
        SyncState.CONNECTING -> "Conectando..."
        SyncState.AUTHENTICATING -> "Autenticando..."
        SyncState.FAILED -> "Error de sincronización"
        SyncState.CANCELLED -> "Sincronización cancelada"
        SyncState.NO_CONNECTION -> "Sin conexión a internet"
        SyncState.NOT_SIGNED_IN -> "No has iniciado sesión"
        SyncState.ERROR_AUTHENTICATION, SyncState.ERROR_AUTH -> "Error de autenticación"
        SyncState.ERROR_CONNECTION -> "Error de conexión"
        SyncState.ERROR_SYNC -> "Error de sincronización"
        SyncState.UNKNOWN -> "Estado desconocido"
        else -> "Estado desconocido"
    }
    
    val syncIcon = when (syncState) {
        SyncState.SUCCESS, SyncState.SYNCED -> Icons.Filled.CloudDone
        SyncState.SYNCING, SyncState.SYNCING_UP, SyncState.SYNCING_DOWN -> Icons.Filled.CloudSync
        SyncState.ERROR_CONNECTION, SyncState.NO_CONNECTION -> Icons.Filled.CloudOff
        SyncState.ERROR_AUTHENTICATION, SyncState.ERROR_AUTH, SyncState.ERROR_SYNC, SyncState.FAILED -> Icons.Filled.Error
        SyncState.IDLE -> Icons.Filled.Cloud
        SyncState.CONNECTING, SyncState.AUTHENTICATING -> Icons.Filled.Sync
        SyncState.CANCELLED -> Icons.Filled.CloudOff
        SyncState.NOT_SIGNED_IN -> Icons.Filled.AccountCircle
        SyncState.UNKNOWN -> Icons.Filled.Cloud
        else -> Icons.Filled.Cloud
    }
    
    val syncColor = when (syncState) {
        SyncState.SUCCESS, SyncState.SYNCED -> MaterialTheme.colorScheme.primary
        SyncState.SYNCING, SyncState.SYNCING_UP, SyncState.SYNCING_DOWN, 
        SyncState.CONNECTING, SyncState.AUTHENTICATING -> MaterialTheme.colorScheme.tertiary
        SyncState.IDLE, SyncState.UNKNOWN -> Color.Gray
        SyncState.ERROR_CONNECTION, SyncState.ERROR_AUTHENTICATION, 
        SyncState.ERROR_AUTH, SyncState.ERROR_SYNC, SyncState.FAILED -> MaterialTheme.colorScheme.error
        SyncState.CANCELLED -> Color.Gray
        SyncState.NO_CONNECTION -> MaterialTheme.colorScheme.error
        SyncState.NOT_SIGNED_IN -> MaterialTheme.colorScheme.secondary
        else -> Color.Gray
    }
    
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()) }
    val lastSyncText = if (lastSyncTime > 0) {
        "Última sincronización: ${dateFormat.format(java.util.Date(lastSyncTime))}"
    } else {
        "No sincronizado recientemente"
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Estado actual
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Estado de Sincronización",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(
                            color = syncColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = null,
                            tint = syncColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        Text(
                            text = syncStateText,
                            color = syncColor,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(start = 8.dp),
                                color = syncColor
                            )
                        }
                    }
                }
                
                Text(
                    text = lastSyncText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                
                // Botones
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncClick,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && syncState != SyncState.SYNCING
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Sincronizar")
                    }
                    
                    Button(
                        onClick = onForceSyncClick,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && syncState != SyncState.SYNCING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Forzar")
                    }
                }
            }
        }
    }
}
