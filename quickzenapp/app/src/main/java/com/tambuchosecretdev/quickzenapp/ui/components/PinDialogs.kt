package com.tambuchosecretdev.quickzenapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PinEntryDialog(
    title: String,
    message: String? = null,
    pinInput: String,
    onPinInputChange: (String) -> Unit,
    errorText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 8) onPinInputChange(it) },
                    label = { Text("PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth()
                )
                errorText?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun PinInputDialog(
    showConfirmField: Boolean,
    newPin: String,
    onNewPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit,
    errorText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 8) onNewPinChange(it) },
                    label = { Text(if (showConfirmField) "Nuevo PIN" else "PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errorText != null
                )
                if (showConfirmField) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 8) onConfirmPinChange(it) },
                        label = { Text("Confirmar Nuevo PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = errorText != null
                    )
                }
                errorText?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
} 