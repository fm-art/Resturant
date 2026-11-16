package com.example.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.model.StoreOrder
import com.example.service.OrderMonitorService
import java.util.Collections

object OrderAlertManager {

    const val ORDER_CHANNEL_ID = "kot_orders_loud_alarm_v1"
    const val MONITOR_CHANNEL_ID = "store_monitor_channel_v1"
    const val MONITOR_NOTIFICATION_ID = 1001

    private val notifiedOrderIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var mediaPlayer: MediaPlayer? = null
    private var previousAlarmVolume: Int? = null

    @Volatile
    var isSoundEnabled: Boolean = true
        private set

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
        if (!enabled) {
            stopLoudRinging()
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val alarmSoundUri = getAlarmOrRingtoneUri()
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (manager.getNotificationChannel(ORDER_CHANNEL_ID) == null) {
            val orderChannel = NotificationChannel(
                ORDER_CHANNEL_ID,
                "New Order Alerts (Loud Ring)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud ringing alerts and heads-up notifications for incoming KOT orders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(alarmSoundUri, alarmAttrs)
                runCatching { setBypassDnd(true) }
            }
            manager.createNotificationChannel(orderChannel)
        }

        if (manager.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
            val monitorChannel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Store Live Order Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps store connected in background to receive new orders immediately"
                setShowBadge(false)
            }
            manager.createNotificationChannel(monitorChannel)
        }
    }

    fun buildMonitorNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Gossaigaon Store Online")
            .setContentText("Listening for incoming grocery KOT orders...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpen)
            .build()
    }

    fun showNewOrderNotification(context: Context, order: StoreOrder) {
        if (order.orderId.isBlank()) return
        if (!notifiedOrderIds.add(order.orderId)) return

        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("order_id", order.orderId)
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            order.orderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, OrderMonitorService::class.java).apply {
            action = OrderMonitorService.ACTION_STOP_RING
        }
        val pendingStop = PendingIntent.getService(
            context,
            order.orderId.hashCode() + 1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shortCode = order.orderId.takeLast(6)
        val totalItems = order.items.sumOf { it.qty.coerceAtLeast(1) }
        val amount = EarningsCalculator.formatRupees(order.itemsValue)
        val payMode = order.paymentMode.ifBlank { "COD" }
        val itemsSummary = order.items.take(3).joinToString(", ") { "${it.qty}x ${it.name}" }

        val title = "NEW ORDER #$shortCode ($amount)"
        val body = "$totalItems items • $payMode" +
            if (itemsSummary.isNotBlank()) "\n$itemsSummary" else ""

        val notification = NotificationCompat.Builder(context, ORDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("$totalItems items • $payMode • Tap to Accept")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setSound(getAlarmOrRingtoneUri())
            .setVibrate(longArrayOf(0, 600, 300, 600, 300, 800))
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Ring",
                pendingStop
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(order.orderId), notification)
        }
    }

    fun syncActiveOrderNotifications(context: Context, orders: List<StoreOrder>) {
        val actionable = orders.filter { StoreUtils.isActionableKotOrder(it) }
        val actionableIds = actionable.map { it.orderId }.toSet()

        val staleIds = notifiedOrderIds.filter { it !in actionableIds }
        staleIds.forEach { oldId ->
            cancelOrderNotification(context, oldId)
        }

        actionable.forEach { order ->
            showNewOrderNotification(context, order)
        }
    }

    fun cancelOrderNotification(context: Context, orderId: String) {
        notifiedOrderIds.remove(orderId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationIdFor(orderId))
        }
    }

    fun clearAllOrderNotifications(context: Context) {
        val ids = notifiedOrderIds.toList()
        ids.forEach { cancelOrderNotification(context, it) }
    }

    @Synchronized
    fun startLoudRinging(context: Context) {
        if (!isSoundEnabled) return
        if (mediaPlayer?.isPlaying == true) return

        val appCtx = context.applicationContext
        boostAlarmVolumeTemporarily(appCtx)
        startVibration(appCtx)

        val uri = getAlarmOrRingtoneUri()
        val started = runCatching {
            stopPlayerOnly()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appCtx, uri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            mediaPlayer = player
            true
        }.getOrDefault(false)

        if (!started) {
            StoreUtils.playOrderChimePulse()
        }
    }

    @Synchronized
    fun stopLoudRinging(context: Context? = null) {
        stopPlayerOnly()
        context?.let {
            stopVibration(it.applicationContext)
            restoreAlarmVolume(it.applicationContext)
        }
    }

    @Synchronized
    fun isRinging(): Boolean = mediaPlayer?.isPlaying == true

    private fun stopPlayerOnly() {
        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            }
        }
        mediaPlayer = null
    }

    private fun boostAlarmVolumeTemporarily(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = (max * 0.85f).toInt().coerceAtLeast(1)
            if (current < target) {
                if (previousAlarmVolume == null) {
                    previousAlarmVolume = current
                }
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        }
    }

    private fun restoreAlarmVolume(context: Context) {
        val prev = previousAlarmVolume ?: return
        previousAlarmVolume = null
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.setStreamVolume(AudioManager.STREAM_ALARM, prev, 0)
        }
    }

    private fun startVibration(context: Context) {
        runCatching {
            val pattern = longArrayOf(0, 500, 300, 500, 600)
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration(context: Context) {
        runCatching {
            getVibrator(context)?.cancel()
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun getAlarmOrRingtoneUri(): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun notificationIdFor(orderId: String): Int =
        2000 + (orderId.hashCode() and 0x7FFF)
}
