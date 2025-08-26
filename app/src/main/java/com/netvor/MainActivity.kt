package com.netvor

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netvor.ui.theme.NetvorTheme
import androidx.compose.ui.platform.LocalContext
import com.netvor.vpn.NetvorVpnService
import androidx.core.content.ContextCompat
import com.netvor.ui.NetvorTabs
import com.netvor.config.ConfigRepository

class MainActivity : ComponentActivity() {

	private var onVpnPermissionGranted: (() -> Unit)? = null

	private val vpnPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) {
		if (it.resultCode == RESULT_OK) {
			onVpnPermissionGranted?.invoke()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			NetvorTheme {
				Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
					val appCtx = applicationContext
					val repo = remember { ConfigRepository(appCtx) }
					var refresh by remember { mutableStateOf(0) }
					NetvorTabs(
						onConnectToggle = { shouldConnect ->
							if (shouldConnect) {
								prepareAndStartVpn()
							} else {
								val stop = Intent(this, NetvorVpnService::class.java).apply { action = NetvorVpnService.ACTION_STOP }
								ContextCompat.startForegroundService(this, stop)
							}
						},
						onImport = { link ->
							try { repo.saveVlessLink(link); refresh++ ; true } catch (_: Throwable) { false }
						},
						configs = remember(refresh) { repo.listConfigs() },
						active = remember(refresh) { repo.getActiveConfigName() },
						onActivate = { name -> repo.activateConfig(name); refresh++ },
						onDelete = { name -> repo.deleteConfig(name); refresh++ }
					)
				}
			}
		}
	}

	private fun prepareAndStartVpn() {
		val intent = VpnService.prepare(this)
		if (intent != null) {
			onVpnPermissionGranted = {
				try {
					val start = Intent(this, NetvorVpnService::class.java).apply { action = NetvorVpnService.ACTION_START }
					ContextCompat.startForegroundService(this, start)
				} catch (t: Throwable) { com.netvor.bus.AppBus.emitLog("startForegroundService error: ${t.message}") }
			}
			vpnPermissionLauncher.launch(intent)
		} else {
			try {
				val start = Intent(this, NetvorVpnService::class.java).apply { action = NetvorVpnService.ACTION_START }
				ContextCompat.startForegroundService(this, start)
			} catch (t: Throwable) { com.netvor.bus.AppBus.emitLog("startForegroundService error: ${t.message}") }
		}
	}
}

