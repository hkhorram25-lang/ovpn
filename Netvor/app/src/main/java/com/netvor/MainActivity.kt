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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netvor.ui.theme.NetvorTheme
import com.netvor.vpn.NetvorVpnService

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
					HomeScreen(
						onToggle = { shouldConnect ->
							if (shouldConnect) {
								prepareAndStartVpn()
							} else {
								stopService(Intent(this, NetvorVpnService::class.java))
							}
						}
					)
				}
			}
		}
	}

	private fun prepareAndStartVpn() {
		val intent = VpnService.prepare(this)
		if (intent != null) {
			onVpnPermissionGranted = { startService(Intent(this, NetvorVpnService::class.java)) }
			vpnPermissionLauncher.launch(intent)
		} else {
			startService(Intent(this, NetvorVpnService::class.java))
		}
	}
}

@Composable
private fun HomeScreen(onToggle: (Boolean) -> Unit) {
	var connected by remember { mutableStateOf(false) }
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(text = "Netvor")
		Button(onClick = {
			connected = !connected
			onToggle(connected)
		}) {
			Text(if (connected) "قطع اتصال" else "اتصال")
		}
	}
}

