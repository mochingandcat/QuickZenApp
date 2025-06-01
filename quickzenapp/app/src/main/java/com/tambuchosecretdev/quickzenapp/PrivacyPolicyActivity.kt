package com.tambuchosecretdev.quickzenapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tambuchosecretdev.quickzenapp.ui.screens.PrivacyPolicyScreen
import com.tambuchosecretdev.quickzenapp.ui.theme.QuickZenAppTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            QuickZenAppTheme {
                PrivacyPolicyScreen(
                    onNavigateBack = {
                        finish()
                    }
                )
            }
        }
    }
}
