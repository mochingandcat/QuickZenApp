package com.tambuchosecretdev.quickzenapp


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tambuchosecretdev.quickzenapp.ui.theme.BrightCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onGettingStartedClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Bienvenido/a a MynoteszenApp") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BrightCyan,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "¡Hola!",
                style = MaterialTheme.typography.headlineLarge,
                color = BrightCyan
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "MynoteszenApp te ayuda a organizar tus ideas y tareas de forma sencilla y eficiente.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Puedes crear notas, categorizarlas, establecer recordatorios y mucho más.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onGettingStartedClick) {
                Text("Comenzar")
            }
        }
    }
} 