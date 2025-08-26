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

class NetvorVpnService : VpnService() {

	private var vpnInterface: ParcelFileDescriptor? = null
	private var serviceScope: CoroutineScope? = null
	private lateinit var xrayManager: XrayManager
	private lateinit var configRepository: ConfigRepository
	private var xrayProcess: Process? = null

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
				}
			} catch (t: Throwable) {
				Log.e("NetvorVpnService", "start failed", t)
				stopSelf()
			}
		}
		return START_STICKY
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
			.setSmallIcon(R.drawable.ic_vpn_key)
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
		super.onDestroy()
	}
}

