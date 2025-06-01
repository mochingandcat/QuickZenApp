package com.tambuchosecretdev.quickzenapp

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.ui.components.PinEntryDialog
import com.tambuchosecretdev.quickzenapp.ui.components.PinInputDialog
import com.tambuchosecretdev.quickzenapp.ui.theme.BrightCyan
import com.tambuchosecretdev.quickzenapp.utils.PinManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSecurityScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pinManager = remember { PinManager(context) }
    var isPinSet by remember { 
        val currentPinStatus = pinManager.isPinSet()
        Log.d("PinSecurityScreen", "Initial PinManager.isPinSet(): $currentPinStatus")
        mutableStateOf(currentPinStatus) 
    }

    // Estados para los diálogos de PIN
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showEnterCurrentPinDialogForChange by remember { mutableStateOf(false) }
    var showEnterCurrentPinDialogForRemove by remember { mutableStateOf(false) }

    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Log.d("PinSecurityScreen", "Recomposing. isPinSet: $isPinSet, showSetPinDialog: $showSetPinDialog, showChangePinDialog: $showChangePinDialog")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Seguridad del PIN") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BrightCyan,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPinSet) {
                Text("PIN de la aplicación configurado.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { 
                    Log.d("PinSecurityScreen", "Change PIN button clicked")
                    currentPinInput = ""
                    errorText = null
                    showEnterCurrentPinDialogForChange = true 
                    Log.d("PinSecurityScreen", "showEnterCurrentPinDialogForChange set to: $showEnterCurrentPinDialogForChange")
                }) {
                    Text("Cambiar PIN")
                }
                Button(onClick = { 
                    Log.d("PinSecurityScreen", "Remove PIN button clicked")
                    currentPinInput = ""
                    errorText = null
                    showEnterCurrentPinDialogForRemove = true 
                    Log.d("PinSecurityScreen", "showEnterCurrentPinDialogForRemove set to: $showEnterCurrentPinDialogForRemove")
                }) {
                    Text("Eliminar PIN")
                }
            } else {
                Text("No hay un PIN configurado para la aplicación.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { 
                    Log.d("PinSecurityScreen", "Set PIN button clicked")
                    newPinInput = ""
                    confirmPinInput = ""
                    errorText = null
                    showSetPinDialog = true
                    Log.d("PinSecurityScreen", "showSetPinDialog set to: $showSetPinDialog")
                }) {
                    Text("Establecer PIN")
                }
            }
        }
    }

    // --- Diálogos para la gestión del PIN ---

    // Diálogo para ESTABLECER nuevo PIN
    if (showSetPinDialog) {
        Log.d("PinSecurityScreen", "Attempting to show PinInputDialog for Set PIN.")
        PinInputDialog(
            title = "Establecer Nuevo PIN",
            showConfirmField = true,
            newPin = newPinInput,
            onNewPinChange = { newPinInput = it },
            confirmPin = confirmPinInput,
            onConfirmPinChange = { confirmPinInput = it },
            errorText = errorText,
            onConfirm = {
                Log.d("PinSecurityScreen", "Set PIN Dialog: Confirm clicked.")
                if (newPinInput.length < 4) {
                    errorText = "El PIN debe tener al menos 4 dígitos."
                } else if (newPinInput != confirmPinInput) {
                    errorText = "Los PINs no coinciden."
                } else {
                    if (pinManager.savePin(newPinInput)) {
                        isPinSet = true
                        showSetPinDialog = false
                        newPinInput = ""
                        confirmPinInput = ""
                        errorText = null
                        Toast.makeText(context, "PIN establecido correctamente.", Toast.LENGTH_SHORT).show()
                        Log.d("PinSecurityScreen", "PIN Saved successfully. isPinSet: $isPinSet, showSetPinDialog: $showSetPinDialog")
                    } else {
                        errorText = "Error al guardar el PIN."
                         Log.e("PinSecurityScreen", "Error saving PIN.")
                    }
                }
            },
            onDismiss = {
                Log.d("PinSecurityScreen", "Set PIN Dialog: Dismissed.")
                showSetPinDialog = false
                newPinInput = ""
                confirmPinInput = ""
                errorText = null
            }
        )
    }

    // Diálogo para ingresar PIN ACTUAL (antes de cambiar)
    if (showEnterCurrentPinDialogForChange) {
        Log.d("PinSecurityScreen", "Attempting to show PinEntryDialog for Change PIN (Enter Current).")
        PinEntryDialog(
            title = "Ingresar PIN Actual",
            message = "Ingresa tu PIN actual para continuar.",
            pinInput = currentPinInput,
            onPinInputChange = { currentPinInput = it },
            errorText = errorText,
            onConfirm = {
                Log.d("PinSecurityScreen", "Enter Current PIN (for Change) Dialog: Confirm clicked.")
                if (pinManager.checkPin(currentPinInput)) {
                    showEnterCurrentPinDialogForChange = false
                    newPinInput = ""
                    confirmPinInput = ""
                    errorText = null
                    showChangePinDialog = true
                    currentPinInput = ""
                    Log.d("PinSecurityScreen", "Current PIN correct. showChangePinDialog: $showChangePinDialog")
                } else {
                    errorText = "PIN actual incorrecto."
                    Log.w("PinSecurityScreen", "Current PIN incorrect.")
                }
            },
            onDismiss = {
                Log.d("PinSecurityScreen", "Enter Current PIN (for Change) Dialog: Dismissed.")
                showEnterCurrentPinDialogForChange = false
                currentPinInput = ""
                errorText = null
            }
        )
    }

    // Diálogo para CAMBIAR PIN (ingresar nuevo y confirmar)
    if (showChangePinDialog) {
        Log.d("PinSecurityScreen", "Attempting to show PinInputDialog for Change PIN (Enter New).")
        PinInputDialog(
            title = "Establecer Nuevo PIN",
            showConfirmField = true,
            newPin = newPinInput,
            onNewPinChange = { newPinInput = it },
            confirmPin = confirmPinInput,
            onConfirmPinChange = { confirmPinInput = it },
            errorText = errorText,
            onConfirm = {
                Log.d("PinSecurityScreen", "Change PIN Dialog: Confirm clicked.")
                if (newPinInput.length < 4) {
                    errorText = "El nuevo PIN debe tener al menos 4 dígitos."
                } else if (newPinInput != confirmPinInput) {
                    errorText = "Los PINs no coinciden."
                } else if (pinManager.savePin(newPinInput)) {
                    isPinSet = true
                    showChangePinDialog = false
                    Toast.makeText(context, "PIN cambiado correctamente.", Toast.LENGTH_SHORT).show()
                    Log.d("PinSecurityScreen", "PIN Changed successfully. showChangePinDialog: $showChangePinDialog")
                } else {
                    Toast.makeText(context, "Error al cambiar el PIN.", Toast.LENGTH_SHORT).show()
                    Log.e("PinSecurityScreen", "Error changing PIN.")
                }
            },
            onDismiss = {
                Log.d("PinSecurityScreen", "Change PIN Dialog: Dismissed.")
                showChangePinDialog = false
                newPinInput = ""
                confirmPinInput = ""
                errorText = null
            }
        )
    }

    // Diálogo para ingresar PIN ACTUAL (antes de eliminar)
    if (showEnterCurrentPinDialogForRemove) {
        Log.d("PinSecurityScreen", "Attempting to show PinEntryDialog for Remove PIN.")
        PinEntryDialog(
            title = "Confirmar Eliminación de PIN",
            message = "Ingresa tu PIN actual para eliminarlo. Esta acción no se puede deshacer.",
            pinInput = currentPinInput,
            onPinInputChange = { currentPinInput = it },
            errorText = errorText,
            onConfirm = {
                Log.d("PinSecurityScreen", "Remove PIN Dialog: Confirm clicked.")
                if (pinManager.checkPin(currentPinInput)) {
                    if (pinManager.removePin()) {
                        isPinSet = false
                        showEnterCurrentPinDialogForRemove = false
                        currentPinInput = ""
                        errorText = null
                        Toast.makeText(context, "PIN eliminado correctamente.", Toast.LENGTH_SHORT).show()
                        Log.d("PinSecurityScreen", "PIN Removed successfully. isPinSet: $isPinSet")
                    } else {
                        errorText = "Error al eliminar el PIN."
                        Log.e("PinSecurityScreen", "Error removing PIN from PinManager.")
                    }
                } else {
                    errorText = "PIN actual incorrecto."
                    Log.w("PinSecurityScreen", "Current PIN incorrect for removal.")
                }
            },
            onDismiss = {
                Log.d("PinSecurityScreen", "Remove PIN Dialog: Dismissed.")
                showEnterCurrentPinDialogForRemove = false
                currentPinInput = ""
                errorText = null
            }
        )
    }
} 