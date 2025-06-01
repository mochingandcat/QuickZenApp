package com.tambuchosecretdev.quickzenapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tambuchosecretdev.quickzenapp.R
import com.tambuchosecretdev.quickzenapp.MainActivity

/**
 * Receptor de broadcast para manejar las notificaciones de recordatorios
 */
class ReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Recordatorio recibido")
        
        val noteId = intent.getLongExtra("NOTE_ID", -1L)
        val noteTitle = intent.getStringExtra("NOTE_TITLE") ?: "Recordatorio"
        val noteContent = intent.getStringExtra("NOTE_CONTENT") ?: "Es hora de revisar tu nota"
        
        // Solo procesar si es un recordatorio válido
        if (noteId != -1L) {
            showNotification(context, noteId, noteTitle, noteContent)
        } else {
            Log.e(TAG, "ID de nota inválido en el recordatorio")
        }
    }
    
    private fun showNotification(context: Context, noteId: Long, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Crear canal de notificación en Android 8.0 (API 26) y versiones posteriores
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de QuickZen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Canal para recordatorios de notas en QuickZen"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent para abrir la nota cuando se toca la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_NOTE_ID", noteId)
        }
        
        // Crear PendingIntent
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            intent,
            pendingIntentFlag
        )
        
        // Construir notificación
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Mostrar notificación
        notificationManager.notify(noteId.toInt(), notification)
        Log.d(TAG, "Notificación mostrada para la nota: $title")
    }
    
    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "quickzen_reminders"
    }
}
