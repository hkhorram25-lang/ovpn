package com.netvor.bus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object AppBus {

	private val _logs = MutableSharedFlow<String>(replay = 100)
	val logs = _logs.asSharedFlow()

	fun emitLog(line: String) {
		try { _logs.tryEmit(line) } catch (_: Throwable) { }
	}

	data class Status(
		val connected: Boolean,
		val startTimeMs: Long,
		val rxBytes: Long,
		val txBytes: Long
	)

	private val _status = MutableStateFlow(Status(false, 0L, 0L, 0L))
	val status = _status.asStateFlow()

	fun updateStatus(newStatus: Status) {
		_status.value = newStatus
	}
}

