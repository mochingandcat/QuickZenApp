package com.tambuchosecretdev.quickzenapp

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMenuScreen(
    onBackupSyncClick: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Eliminar contenedores adicionales alrededor del menú
        drawerContent = {
            MenuContent(drawerState, scope, context, onBackupSyncClick)
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("QuickZen") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
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
            ) {
                Text("Bienvenido a QuickZen")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Usa el menú de navegación para explorar las opciones.")
            }
        }
    }
}

@Composable
private fun MenuContent(drawerState: DrawerState, scope: CoroutineScope, context: Context, onBackupSyncClick: () -> Unit) {
    // Crear un scrollState explícito
    val scrollState = rememberScrollState()
    
    // Usar ModalDrawerSheet sin modificadores adicionales
    ModalDrawerSheet {
        // Hacemos que el contenido sea scrolleable
        Column(
            modifier = Modifier
                // Asegurar que el contenido sea lo suficientemente grande (1000dp)
                .height(1000.dp)
                // Aplicar scroll vertical
                .verticalScroll(scrollState)
                // Padding para mejor apariencia
                .padding(8.dp)
        ) {
            // Header con el título
            Spacer(Modifier.height(12.dp))
            Text(
                "QuickZen",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge
            )
            Divider()
            
            // Elementos del menú basándonos en la captura de pantalla
            
            // Papeles
            DrawerMenuItem(
                icon = Icons.Default.Description, // O el icono adecuado
                label = "Papeles",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Papeles seleccionado")
                }
            )
            
            // Escribir nota
            DrawerMenuItem(
                icon = Icons.Default.Edit, // O el icono adecuado
                label = "Escribir nota",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Escribir nota seleccionado")
                }
            )
            
            // Descargar nota
            DrawerMenuItem(
                icon = Icons.Default.Download, // O el icono adecuado
                label = "Descargar nota",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Descargar nota seleccionado")
                }
            )
            
            // Papelera
            DrawerMenuItem(
                icon = Icons.Default.Delete,
                label = "Papelera",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Papelera seleccionada")
                }
            )
            
            // Actualizar todo
            DrawerMenuItem(
                icon = Icons.Default.Refresh, // O el icono adecuado
                label = "Actualizar todo",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Actualizar todo seleccionado")
                }
            )
            
            // Compartir
            DrawerMenuItem(
                icon = Icons.Default.Share,
                label = "Compartir",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Compartir seleccionado")
                }
            )
            
            // Configuración
            DrawerMenuItem(
                icon = Icons.Default.Settings,
                label = "Configuración",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Configuración seleccionada")
                }
            )
            
            // Ayuda de...
            DrawerMenuItem(
                icon = Icons.Default.Help, // O el icono adecuado
                label = "Ayuda de...",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Ayuda seleccionada")
                }
            )
            
            // Separated/PIN
            DrawerMenuItem(
                icon = Icons.Default.Lock, // O el icono adecuado
                label = "Separated/PIN",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Separated/PIN seleccionado")
                }
            )
            
            // Terminar
            DrawerMenuItem(
                icon = Icons.Default.ExitToApp, // O el icono adecuado
                label = "Terminar",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Terminar seleccionado")
                }
            )
            
            // Información de Cuenta
            DrawerMenuItem(
                icon = Icons.Default.Person,
                label = "Información de Cuenta",
                onClick = {
                    scope.launch { drawerState.close() }
                    showAccountInfo(context)
                }
            )
            
            // Sobre
            DrawerMenuItem(
                icon = Icons.Default.Info,
                label = "Sobre",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Sobre seleccionado")
                }
            )
            
            // Finalizar
            DrawerMenuItem(
                icon = Icons.Default.Close, // O el icono adecuado
                label = "Finalizar",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Finalizar seleccionado")
                }
            )
            
            // Separador antes de Cerrar sesión
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Botón de sincronización con Firebase (reemplaza al botón de migración)
            DrawerMenuItem(
                icon = Icons.Default.Refresh,
                label = "Sincronizar notas",
                onClick = {
                    scope.launch { drawerState.close() }
                    // Usar la pantalla de sincronización dentro de la app actual en lugar de una actividad separada
                    onBackupSyncClick()
                }
            )
            
            // Botón para eliminar notas duplicadas
            DrawerMenuItem(
                icon = Icons.Default.CleaningServices,
                label = "Eliminar duplicados",
                onClick = {
                    scope.launch { drawerState.close() }
                    val cleanerIntent = android.content.Intent(context, DuplicateCleanerActivity::class.java)
                    context.startActivity(cleanerIntent)
                }
            )
            
            // Cerrar sesión (siempre al final)
            DrawerMenuItem(
                icon = Icons.Default.Logout,
                label = "Cerrar sesión",
                onClick = {
                    scope.launch { drawerState.close() }
                    showToast(context, "Cerrando sesión...")
                }
            )
            
            // Espacio adicional al final para asegurar espacio de scroll
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Eliminamos las funciones personalizadas de scrollbar que causaban conflictos

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun AccountInfoDialog(onDismissRequest: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Información de la Cuenta") },
        text = {
            Column {
                if (user != null) {
                    Text(
                        "Email:", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(user.email ?: "No disponible")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Nombre:", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(user.displayName ?: "No disponible")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Proveedor:", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val providerData = user.providerData
                    if (providerData.isNotEmpty()) {
                        Text(providerData[0].providerId.replace(".", " ").capitalize())
                    } else {
                        Text("No disponible")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "ID de Usuario:", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(user.uid)
                } else {
                    Text("No has iniciado sesión")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Cerrar")
            }
        }
    )
}

private fun showAccountInfo(context: Context) {
    // Debemos usar una Activity para mostrar el diálogo
    val intent = android.content.Intent(context, MainActivity::class.java).apply {
        putExtra("SHOW_ACCOUNT_INFO", true)
        addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    context.startActivity(intent)
    
    // Mostrar un mensaje de toast como retroalimentación inmediata
    Toast.makeText(context, "Mostrando información de la cuenta...", Toast.LENGTH_SHORT).show()
}

@Composable
fun AccountInfoScreen() {
    var showDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Mostrar información de la cuenta")
        }
        
        if (showDialog) {
            AccountInfoDialog(onDismissRequest = { showDialog = false })
        }
    }
}