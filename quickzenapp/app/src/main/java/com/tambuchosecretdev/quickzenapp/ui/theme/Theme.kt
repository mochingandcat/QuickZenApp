package com.tambuchosecretdev.quickzenapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tambuchosecretdev.quickzenapp.ui.theme.DarkBackground
import com.tambuchosecretdev.quickzenapp.ui.theme.DarkOnBackground
import com.tambuchosecretdev.quickzenapp.ui.theme.DarkOnSurface
import com.tambuchosecretdev.quickzenapp.ui.theme.DarkSurface
import com.tambuchosecretdev.quickzenapp.ui.theme.LightBackground
import com.tambuchosecretdev.quickzenapp.ui.theme.LightOnBackground
import com.tambuchosecretdev.quickzenapp.ui.theme.LightOnSurface
import com.tambuchosecretdev.quickzenapp.ui.theme.LightSurface
import com.tambuchosecretdev.quickzenapp.ui.theme.Pink40
import com.tambuchosecretdev.quickzenapp.ui.theme.Pink80
import com.tambuchosecretdev.quickzenapp.ui.theme.Purple40
import com.tambuchosecretdev.quickzenapp.ui.theme.Purple80
import com.tambuchosecretdev.quickzenapp.ui.theme.PurpleGrey40
import com.tambuchosecretdev.quickzenapp.ui.theme.PurpleGrey80

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun QuickZenAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Cambio a false para evitar problemas con colores dinámicos
    content: @Composable () -> Unit
) {
    // Simplificamos la lógica para evitar problemas con SharedPreferences
    val useDarkTheme = darkTheme
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // Aplicamos el tema a la barra de estado de forma segura
    val view = LocalView.current
    if (!view.isInEditMode) {
        try {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
            }
        } catch (e: Exception) {
            // Ignoramos errores en la personalización de la barra de estado
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}