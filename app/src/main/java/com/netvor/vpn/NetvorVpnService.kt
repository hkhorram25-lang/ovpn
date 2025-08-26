package com.netvor.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.netvor.MainActivity
import com.netvor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log
import com.netvor.config.ConfigRepository
import com.netvor.xray.XrayManager
import com.netvor.bus.AppBus
import android.net.TrafficStats
import kotlinx.coroutines.delay

class NetvorVpnService : VpnService() {

	private var vpnInterface: ParcelFileDescriptor? = null
	private var serviceScope: CoroutineScope? = null
	private lateinit var xrayManager: XrayManager
	private lateinit var configRepository: ConfigRepository
	private var xrayProcess: Process? = null
	private var startRxBytes: Long = 0
	private var startTxBytes: Long = 0
	private var startTimeMs: Long = 0

	override fun onCreate() {
		super.onCreate()
		serviceScope = CoroutineScope(Dispatchers.IO + Job())
		startForeground(1, buildNotification())
		xrayManager = XrayManager(applicationContext)
		configRepository = ConfigRepository(applicationContext)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		serviceScope?.launch {
			try {
				// Ensure xray exists before establishing TUN to avoid premature service crash
				val cfg = configRepository.writeDefaultConfigIfMissing()
				xrayManager.ensureXrayPresent()
				setupTun()
				vpnInterface?.let { fd ->
					xrayProcess = xrayManager.runXray(fd, cfg)
					readXrayLogs(xrayProcess!!)
				}
				startRxBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid()).coerceAtLeast(0)
				startTxBytes = TrafficStats.getUidTxBytes(android.os.Process.myUid()).coerceAtLeast(0)
				startTimeMs = System.currentTimeMillis()
				AppBus.updateStatus(AppBus.Status(true, startTimeMs, 0, 0))
				serviceScope?.launch { updateStatsLoop() }
			} catch (t: Throwable) {
				Log.e("NetvorVpnService", "start failed", t)
				stopSelf()
			}
		}
		return START_STICKY
	}

	private fun readXrayLogs(p: Process) {
		serviceScope?.launch {
			try {
				p.inputStream.bufferedReader().useLines { seq ->
					seq.forEach { line -> AppBus.emitLog(line) }
				}
			} catch (_: Throwable) { }
		}
	}

	private suspend fun updateStatsLoop() {
		while (true) {
			val rx = TrafficStats.getUidRxBytes(android.os.Process.myUid()).coerceAtLeast(0)
			val tx = TrafficStats.getUidTxBytes(android.os.Process.myUid()).coerceAtLeast(0)
			val drx = (rx - startRxBytes).coerceAtLeast(0)
			val dtx = (tx - startTxBytes).coerceAtLeast(0)
			AppBus.updateStatus(AppBus.Status(true, startTimeMs, drx, dtx))
			delay(1000)
		}
	}

	private fun setupTun() {
		if (vpnInterface != null) return
		val builder = Builder()
		builder.setSession("Netvor")
		builder.addAddress("10.10.0.2", 32)
		builder.addDnsServer("1.1.1.1")
		builder.addRoute("0.0.0.0", 0)
		try { builder.addDisallowedApplication(packageName) } catch (_: Throwable) { }
		vpnInterface = builder.establish()
	}

	private fun buildNotification(): Notification {
		val channelId = "netvor_vpn"
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(channelId, "Netvor", NotificationManager.IMPORTANCE_LOW)
			val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			nm.createNotificationChannel(channel)
		}
		val pendingIntent = PendingIntent.getActivity(
			this,
			0,
			Intent(this, MainActivity::class.java),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
		return NotificationCompat.Builder(this, channelId)
			.setContentTitle("Netvor")
			.setContentText("در حال اجرا")
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentIntent(pendingIntent)
			.setOngoing(true)
			.build()
	}

	override fun onDestroy() {
		serviceScope?.cancel()
		vpnInterface?.close()
		vpnInterface = null
		xrayProcess?.destroy()
		xrayProcess = null
		AppBus.updateStatus(AppBus.Status(false, 0, 0, 0))
		super.onDestroy()
	}
}

