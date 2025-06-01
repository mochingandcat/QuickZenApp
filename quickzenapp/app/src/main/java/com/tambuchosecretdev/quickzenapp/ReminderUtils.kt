package com.tambuchosecretdev.quickzenapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tambuchosecretdev.quickzenapp.notification.ReminderReceiver

fun scheduleReminder(context: Context, timeInMillis: Long, title: String, message: String) {
    // Asegúrate de que ReminderReceiver esté importado correctamente
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("title", title)
        putExtra("message", message)
    }

    // Usamos el tiempo en milisegundos como un ID único para PendingIntent
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        timeInMillis.toInt(), // ID único generado a partir del tiempo
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // Esto asegura que el intent se actualice si ya existe
    )

    // Configurar el AlarmManager para disparar el recordatorio
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,   // RTC_WAKEUP para que dispare el recordatorio incluso si el teléfono está en modo de bajo consumo
        timeInMillis,             // El tiempo específico en milisegundos cuando debe dispararse el recordatorio
        pendingIntent             // El PendingIntent que activará el receiver
    )
}




