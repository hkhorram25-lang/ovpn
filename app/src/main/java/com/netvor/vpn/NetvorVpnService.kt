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
import android.content.pm.ServiceInfo
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
import com.netvor.tun.Tun2SocksManager

class NetvorVpnService : VpnService() {

	private var vpnInterface: ParcelFileDescriptor? = null
	private var serviceScope: CoroutineScope? = null
	private lateinit var xrayManager: XrayManager
	private lateinit var configRepository: ConfigRepository
	private var xrayProcess: Process? = null
	private var tun2socksProcess: Process? = null
	private var startRxBytes: Long = 0
	private var startTxBytes: Long = 0
	private var startTimeMs: Long = 0

	companion object {
		const val ACTION_START = "com.netvor.action.START"
		const val ACTION_STOP = "com.netvor.action.STOP"
	}

	override fun onCreate() {
		super.onCreate()
		serviceScope = CoroutineScope(Dispatchers.IO + Job())
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			startForeground(1, buildNotification())
		}
		xrayManager = XrayManager(applicationContext)
		configRepository = ConfigRepository(applicationContext)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_STOP -> {
				stopVpn()
				return START_NOT_STICKY
			}
			else -> {
				serviceScope?.launch {
					try {
						val cfg = configRepository.writeDefaultConfigIfMissing()
						xrayManager.ensureXrayPresent()
						setupTun()
						vpnInterface?.let { fd ->
							xrayProcess = xrayManager.runXray(fd, cfg)
							readXrayLogs(xrayProcess!!)
							val t2s = Tun2SocksManager(applicationContext)
							tun2socksProcess = t2s.run(fd, "127.0.0.1:10808")
						}
						startRxBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid()).coerceAtLeast(0)
						startTxBytes = TrafficStats.getUidTxBytes(android.os.Process.myUid()).coerceAtLeast(0)
						startTimeMs = System.currentTimeMillis()
						AppBus.updateStatus(AppBus.Status(true, startTimeMs, 0, 0))
						serviceScope?.launch { updateStatsLoop() }
					} catch (t: Throwable) {
						Log.e("NetvorVpnService", "start failed", t)
						stopVpn()
					}
				}
				return START_NOT_STICKY
			}
		}
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

	private fun stopVpn() {
		try { tun2socksProcess?.destroy() } catch (_: Throwable) { }
		tun2socksProcess = null
		try { xrayProcess?.destroy() } catch (_: Throwable) { }
		xrayProcess = null
		try { vpnInterface?.close() } catch (_: Throwable) { }
		vpnInterface = null
		AppBus.updateStatus(AppBus.Status(false, 0, 0, 0))
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			stopForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			@Suppress("DEPRECATION")
			stopForeground(true)
		}
		stopSelf()
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

