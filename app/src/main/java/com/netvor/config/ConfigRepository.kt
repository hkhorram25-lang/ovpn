package com.netvor.config

import android.content.Context
import java.io.File

class ConfigRepository(private val appContext: Context) {

	private fun configDir(): File = File(appContext.filesDir, "config").apply { mkdirs() }

	fun writeDefaultConfigIfMissing(): File {
		val f = File(configDir(), "config.json")
		if (!f.exists()) {
			f.writeText(
				"""
				{
				  "log": {"loglevel": "warning"},
				  "inbounds": [
					{"listen":"127.0.0.1","port":10808,"protocol":"socks","settings":{}}
				  ],
				  "outbounds": [
					{"protocol": "freedom", "settings": {}}
				  ]
				}
				""".trimIndent()
			)
		}
		return f
	}
}

