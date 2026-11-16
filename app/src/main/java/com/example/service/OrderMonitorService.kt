package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.StoreRepository
import com.example.util.OrderAlertManager
import com.example.util.StoreUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OrderMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = StoreRepository()
    private var monitorJob: Job? = null
    private var activeUid: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = OrderAlertManager.buildMonitorNotification(this)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            OrderAlertManager.MONITOR_NOTIFICATION_ID,
            notification,
            fgsType
        )

        if (intent?.action == ACTION_STOP_RING) {
            OrderAlertManager.stopLoudRinging(this)
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP_SERVICE) {
            OrderAlertManager.stopLoudRinging(this)
            OrderAlertManager.clearAllOrderNotifications(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val uid = intent?.getStringExtra(EXTRA_STORE_UID)
            ?: FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (activeUid != uid || monitorJob?.isActive != true) {
            activeUid = uid
            startListeningForOrders(uid)
        }
        return START_STICKY
    }

    private fun startListeningForOrders(uid: String) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            repo.observeStoreOrders(uid).collect { orders ->
                OrderAlertManager.syncActiveOrderNotifications(this@OrderMonitorService, orders)
                val hasActionableOrder = orders.any { StoreUtils.isActionableKotOrder(it) }
                if (hasActionableOrder && OrderAlertManager.isSoundEnabled) {
                    OrderAlertManager.startLoudRinging(this@OrderMonitorService)
                } else {
                    OrderAlertManager.stopLoudRinging(this@OrderMonitorService)
                }
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        OrderAlertManager.stopLoudRinging(this)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        OrderAlertManager.stopLoudRinging(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STORE_UID = "extra_store_uid"
        const val ACTION_STOP_RING = "com.example.action.STOP_RING"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        fun start(context: Context, uid: String) {
            if (uid.isBlank()) return
            val intent = Intent(context, OrderMonitorService::class.java).apply {
                putExtra(EXTRA_STORE_UID, uid)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OrderMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
