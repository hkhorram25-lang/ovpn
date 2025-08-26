package com.netvor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netvor.bus.AppBus
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

enum class TabItem(val title: String) { Dashboard("داشبورد"), Logs("لاگ"), Import("ایمپورت"), Manage("مدیریت"), About("درباره") }

@Composable
fun NetvorTabs(
	onConnectToggle: (Boolean) -> Unit,
	onImport: (String) -> Boolean,
	configs: List<String>,
	active: String?,
	onActivate: (String) -> Unit,
	onDelete: (String) -> Unit,
) {
	var current by remember { mutableStateOf(TabItem.Dashboard) }
	Column(
		Modifier
			.fillMaxSize()
			.background(Brush.verticalGradient(listOf(Color(0xFF0E0E10), Color(0xFF1F2240))))) {
		TabRow(selectedTabIndex = current.ordinal) {
			TabItem.values().forEachIndexed { index, tab ->
				Tab(
					selected = current.ordinal == index,
					onClick = { current = tab },
					text = { Text(tab.title) }
				)
			}
		}
		when (current) {
			TabItem.Dashboard -> DashboardTab(onConnectToggle)
			TabItem.Logs -> LogsTab()
			TabItem.Import -> ImportTab(onImport)
			TabItem.Manage -> ManageTab(configs, active, onActivate, onDelete)
			TabItem.About -> AboutTab()
		}
	}
}

@Composable
private fun DashboardTab(onToggle: (Boolean) -> Unit) {
	val status by AppBus.status.collectAsState()
	val connected = status.connected
	val alphaAnim by animateFloatAsState(targetValue = if (connected) 1f else 0.9f, animationSpec = tween(900, easing = FastOutSlowInEasing), label = "titleAlpha")
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Netvor", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.alpha(alphaAnim))
		Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			StatBox("دانلود", humanBytes(status.rxBytes))
			StatBox("آپلود", humanBytes(status.txBytes))
		}
		Text("زمان اجرا: ${uptimeText(status.startTimeMs)}")
		PingBox()
		CircularConnectButton(connected = connected, onToggle = { onToggle(!connected) })
	}
}

@Composable
private fun PingBox() {
    var pingMs by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val start = System.currentTimeMillis()
            try {
                java.net.InetAddress.getByName("1.1.1.1").isReachable(1000)
                pingMs = (System.currentTimeMillis() - start).toInt()
            } catch (_: Throwable) { pingMs = null }
            delay(3000)
        }
    }
    Text("پینگ: ${pingMs?.let { "$it ms" } ?: "نامشخص"}")
}

@Composable
private fun CircularConnectButton(connected: Boolean, onToggle: () -> Unit) {
    val progress by animateFloatAsState(targetValue = if (connected) 1f else 0f, animationSpec = tween(600), label = "progress")
    val secColor = MaterialTheme.colorScheme.secondary
    val priColor = MaterialTheme.colorScheme.primary
    Button(onClick = onToggle) {
        Canvas(modifier = Modifier.size(64.dp)) {
            drawArc(
                color = secColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )
            drawCircle(color = priColor, radius = size.minDimension / 4f, center = Offset(size.width/2, size.height/2))
        }
        Text(if (connected) "قطع" else "اتصال", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LogsTab() {
	val logs = remember { mutableStateListOf<String>() }
	LaunchedEffect(Unit) {
		AppBus.logs.collectLatest { line -> logs.add(line); if (logs.size > 2000) logs.removeFirst() }
	}
	Column(Modifier.fillMaxSize().padding(16.dp)) {
		Text("لاگ‌ها", style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		Text(logs.joinToString("\n"))
	}
}

@Composable
private fun ImportTab(onImport: (String) -> Boolean) {
	var link by remember { mutableStateOf("") }
	var saved by remember { mutableStateOf<Boolean?>(null) }
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		OutlinedTextField(value = link, onValueChange = { link = it; saved = null }, label = { Text("VLESS لینک") }, modifier = Modifier.fillMaxWidth())
		Button(enabled = link.startsWith("vless://"), onClick = { saved = onImport(link) }) { Text("ایمپورت") }
		when (saved) {
			true -> Text("ذخیره شد")
			false -> Text("ناموفق")
			null -> {}
		}
	}
}

@Composable
private fun ManageTab(configs: List<String>, active: String?, onActivate: (String) -> Unit, onDelete: (String) -> Unit) {
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text("مدیریت کانفیگ‌ها", style = MaterialTheme.typography.titleMedium)
		configs.forEach { name ->
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(if (name == active) "* $name" else name, modifier = Modifier.weight(1f))
				TextButton(onClick = { onActivate(name) }) { Text("فعال") }
				TextButton(onClick = { onDelete(name) }) { Text("حذف") }
			}
		}
	}
}

@Composable
private fun AboutTab() {
	Column(Modifier.fillMaxSize().padding(16.dp)) {
		Text("Netvor - نسخه آزمایشی با Xray-core")
		Text("ساخته‌شده برای تست")
	}
}

@Composable
private fun StatBox(title: String, value: String) {
	Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Text(title)
		Text(value, style = MaterialTheme.typography.titleMedium)
	}
}

private fun humanBytes(bytes: Long): String {
	val units = arrayOf("B", "KB", "MB", "GB")
	var b = bytes.toDouble()
	var i = 0
	while (b > 1024 && i < units.lastIndex) { b /= 1024; i++ }
	return String.format("%.1f %s", b, units[i])
}

private fun uptimeText(startTimeMs: Long): String {
	if (startTimeMs <= 0) return "0s"
	val sec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
	val h = sec / 3600
	val m = (sec % 3600) / 60
	val s = sec % 60
	return "%02d:%02d:%02d".format(h, m, s)
}

