package com.netvor.config

import android.content.Context
import java.io.File
import com.netvor.parser.VlessLinkParser
import com.netvor.parser.VlessConfig

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

	fun saveVlessLink(link: String): File {
		val cfg = VlessLinkParser.parse(link)
		val conf = buildXrayConfig(cfg)
		val f = File(configDir(), "config.json")
		f.writeText(conf)
		return f
	}

	private fun buildXrayConfig(cfg: VlessConfig): String {
		val serverName = (cfg.sniList?.firstOrNull() ?: cfg.address)
		val alpnJson = cfg.alpnList?.joinToString(prefix = "[", postfix = "]") { "\"${it}\"" }
		val hostHeaderList = (cfg.hostHeader?.split(',')?.filter { it.isNotBlank() }) ?: emptyList()
		val hostJson = if (hostHeaderList.isNotEmpty()) hostHeaderList.joinToString(prefix = "[", postfix = "]") { "\"${it}\"" } else null
		val path = cfg.path ?: "/"
		val tlsSettings = if (cfg.security == "tls") {
			buildString {
				append("\"security\":\"tls\",")
				append("\"tlsSettings\":{\"serverName\":\"${serverName}\"")
				if (alpnJson != null) append(",\"alpn\":${alpnJson}")
				if (!cfg.fingerprint.isNullOrBlank()) append(",\"fingerprint\":\"${cfg.fingerprint}\"")
				append("}")
			}
		} else null
		val tcpHeader = if (cfg.network == "tcp" && cfg.headerType == "http") {
			buildString {
				append("\"tcpSettings\":{\"header\":{\"type\":\"http\",\"request\":{")
				append("\"path\":[\"${path}\"]")
				if (hostJson != null) append(",\"headers\":{\"Host\":${hostJson}}")
				append("}}}")
			}
		} else null
		val streamSettings = buildString {
			append("\"streamSettings\":{")
			append("\"network\":\"${cfg.network ?: "tcp"}\"")
			if (tlsSettings != null) append(",${tlsSettings}")
			if (tcpHeader != null) append(",${tcpHeader}")
			append("}")
		}
		return buildString {
			append("{")
			append("\"log\":{\"loglevel\":\"warning\"},")
			append("\"inbounds\":[{\"listen\":\"127.0.0.1\",\"port\":10808,\"protocol\":\"socks\",\"settings\":{\"udp\":true}}],")
			append("\"outbounds\":[{")
			append("\"protocol\":\"vless\",")
			append("\"settings\":{\"vnext\":[{\"address\":\"${cfg.address}\",\"port\":${cfg.port},\"users\":[{\"id\":\"${cfg.uuid}\",\"encryption\":\"none\"}]}]} ,")
			append(streamSettings)
			append("}],")
			append("\"dns\":{\"servers\":[\"1.1.1.1\",\"8.8.8.8\"]}")
			append("}")
		}
	}
}

