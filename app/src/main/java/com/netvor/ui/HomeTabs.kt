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
	Column(Modifier.fillMaxSize()) {
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
	var connected by remember { mutableStateOf(false) }
	Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Netvor", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
		Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			StatBox("دانلود", humanBytes(status.rxBytes))
			StatBox("آپلود", humanBytes(status.txBytes))
		}
		Text("زمان اجرا: ${uptimeText(status.startTimeMs)}")
		Button(onClick = { connected = !connected; onToggle(connected) }) { Text(if (connected) "قطع اتصال" else "اتصال") }
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

