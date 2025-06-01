package com.tambuchosecretdev.quickzenapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.tambuchosecretdev.quickzenapp.ui.theme.QuickZenAppTheme

class AccountInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickZenAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AccountInfoScreen(
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoScreen(onBackPressed: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Información de la Cuenta") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
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
        ) {
            if (user != null) {
                InfoItem(title = "Email", value = user.email ?: "No disponible")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                InfoItem(title = "Nombre", value = user.displayName ?: "No disponible")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Convertir el provider ID a un formato más legible
                val providerData = user.providerData
                // Mostrar ambos nombres: Google y Firebase
                val providerName = if (providerData.isNotEmpty()) {
                    val providerId = providerData[0].providerId
                    when {
                        providerId.contains("google") -> "Google / Firebase"
                        providerId.contains("facebook") -> "Facebook"
                        providerId.contains("twitter") -> "Twitter"
                        providerId.contains("github") -> "GitHub"
                        providerId.contains("apple") -> "Apple"
                        providerId.contains("microsoft") -> "Microsoft"
                        providerId.contains("yahoo") -> "Yahoo"
                        providerId.contains("phone") -> "Teléfono"
                        providerId.contains("password") -> "Email/Contraseña"
                        else -> "Google / Firebase" // Mostrar ambos por defecto
                    }
                } else "No disponible"
                
                InfoItem(title = "Proveedor", value = providerName)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                InfoItem(title = "ID de Usuario", value = user.uid)
            } else {
                Text("No has iniciado sesión")
            }
        }
    }
}

@Composable
fun InfoItem(title: String, value: String) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}
