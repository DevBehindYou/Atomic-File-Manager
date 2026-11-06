package com.devbehindyou.atomicfilemanager.data.share

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.devbehindyou.atomicfilemanager.AtomicApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress

/** What the Wi-Fi share screen shows. */
sealed interface WifiShareState {
    data object Off : WifiShareState

    data class Running(
        val folder: File,
        val url: String,
        val pin: String,
        val allowUpload: Boolean,
        val server: ShareServer,
    ) : WifiShareState

    data class Stopped(val message: String) : WifiShareState
}

/**
 * Starts and stops the guarded Wi-Fi share (ALL_IN_ONE_PLAN.md 3.4) and keeps its notification.
 * One share at a time. It listens only on the phone's Wi-Fi address, so it is never reachable
 * over mobile data; with no Wi-Fi it refuses to start.
 */
class WifiShareController(private val context: Context) {
    val transfers = ShareTransfers()
    private val _state = MutableStateFlow<WifiShareState>(WifiShareState.Off)
    val state: StateFlow<WifiShareState> = _state.asStateFlow()

    @Synchronized
    fun start(
        folder: File,
        allowUpload: Boolean,
    ): WifiShareState {
        stop()
        val address = wifiAddress() ?: return WifiShareState.Stopped(NO_WIFI).also { _state.value = it }
        if (!folder.isDirectory || !folder.canRead()) {
            return WifiShareState.Stopped("That folder can't be read.").also { _state.value = it }
        }
        val server =
            try {
                ShareServer(
                    folder,
                    address,
                    allowUpload = allowUpload,
                    onStop = ::onServerStopped,
                    transfers = transfers,
                )
            } catch (e: IOException) {
                return WifiShareState.Stopped("The share couldn't start: ${e.message}").also { _state.value = it }
            }
        val running = WifiShareState.Running(folder, server.url, server.pin, allowUpload, server)
        _state.value = running
        showNotification(running)
        return running
    }

    /** Stops the share the user is looking at; also used by the notification's Stop. */
    @Synchronized
    fun stop() {
        (_state.value as? WifiShareState.Running)?.server?.stop(ShareStopReason.USER)
    }

    /** Back to Off once the screen has shown why it stopped. */
    fun clear() {
        if (_state.value !is WifiShareState.Running) _state.value = WifiShareState.Off
    }

    private fun onServerStopped(reason: ShareStopReason) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        _state.value = WifiShareState.Stopped(reason.message)
    }

    /** The phone's IPv4 address on Wi-Fi, or null when it isn't on Wi-Fi. */
    private fun wifiAddress(): InetAddress? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return connectivity.allNetworks
            .filter { connectivity.getNetworkCapabilities(it)?.hasTransport(TRANSPORT_WIFI) == true }
            .flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty() }
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }

    private fun showNotification(running: WifiShareState.Running) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wi-Fi share", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, WifiShareStopReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Sharing ${running.folder.name} on Wi-Fi")
                .setContentText("${running.url} · PIN ${running.pin}")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "Stop", stop)
                .build()
        // Without the permission (Android 13+, refused) the share still runs; the screen shows it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "wifi_share"
        const val NOTIFICATION_ID = 0x5A1E
        const val NO_WIFI = "Connect to Wi-Fi first. The share only works on the local Wi-Fi, never on mobile data."
    }
}

/** The notification's Stop button. */
class WifiShareStopReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        (context.applicationContext as? AtomicApp)?.container?.wifiShare?.stop()
    }
}
