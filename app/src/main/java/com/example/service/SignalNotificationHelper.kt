package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ScalpSignal
import com.example.data.model.SignalAction

class SignalNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "scalp_signals_channel_v2"
        const val CHANNEL_NAME = "Sinyal Scalping Real-Time Alert"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Notifikasi instan & audio chime sinyal BUY/SELL untuk XAU/USD & EUR/USD"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun playInAppAlert() {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = false
            }
            ringtone?.play()
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 220), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 180, 100, 220), -1)
            }
        } catch (_: Exception) {}
    }

    fun postSignalNotification(signal: ScalpSignal) {
        // Trigger in-app chime and haptic feedback
        playInAppAlert()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionEmoji = if (signal.action == SignalAction.BUY) "🟢 BUY" else "🔴 SELL"
        val title = "$actionEmoji ${signal.instrument.symbol} (${signal.timeframe.code})"

        val entryFormatted = signal.instrument.formatPrice(signal.entryPrice)
        val slFormatted = signal.instrument.formatPrice(signal.stopLoss)
        val tpFormatted = signal.instrument.formatPrice(signal.takeProfit1)

        val content = "Entry: $entryFormatted | SL: $slFormatted | TP: $tpFormatted | Akurasi: ${signal.confidence}%"

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val bigText = """
            Sinyal Scalping Baru Terdeteksi!
            Arah: ${signal.action.label.uppercase()} (${signal.strength.label})
            Harga Masuk: $entryFormatted
            Stop Loss Otomatis: $slFormatted (-${"%.1f".format(signal.slPips)} pips)
            Target TP1: $tpFormatted (+${"%.1f".format(signal.tp1Pips)} pips)
            Rasio Risiko: 1:${"%.1f".format(signal.riskReward)}
            Analisis: ${signal.reason}
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .build()

        val notificationId = (signal.timestamp % 10000).toInt()
        notificationManager.notify(notificationId, notification)
    }
}
