package com.tambuchosecretdev.quickzenapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.ui.theme.BrightCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Acerca de QuickZenApp") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
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
            Text(
                text = "QuickZenApp",
                style = MaterialTheme.typography.headlineMedium,
                color = BrightCyan
            )

            InfoSection(title = "Versión", content = "1.0.0 (Desarrollo)")

            InfoSection(
                title = "Precios y Suscripción PRO",
                content = "QuickZenApp ofrece una versión básica con funcionalidades esenciales de forma gratuita. Para desbloquear todo el potencial y acceder a características avanzadas, puedes suscribirte a MynoteszenApp PRO:\n\n- Suscripción Mensual: 2,99 €\n- Suscripción Anual: 9,98 € (¡Ahorra con el plan anual!)\n\nLa suscripción PRO te dará acceso a [Mencionar aquí brevemente algunas funciones PRO, ej: Sincronización avanzada, temas exclusivos, etc.]. Puedes gestionar tu suscripción a través de la tienda de aplicaciones."
            )

            InfoSection(
                title = "Política de No Reembolso",
                content = "Todas las compras de suscripciones a MynoteszenApp PRO son finales y no reembolsables bajo ninguna circunstancia. Te recomendamos probar la versión gratuita para asegurarte de que la aplicación cumple tus expectativas antes de suscribirte."
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "© 2024 TambuchoSecretDev. Todos los derechos reservados.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun InfoSection(title: String, content: String) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 