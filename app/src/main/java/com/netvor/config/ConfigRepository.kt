package com.netvor.config

import android.content.Context
import java.io.File
import com.netvor.parser.VlessLinkParser
import com.netvor.parser.VlessConfig

class ConfigRepository(private val appContext: Context) {

	private fun configDir(): File = File(appContext.filesDir, "config").apply { mkdirs() }
    private fun configsRoot(): File = File(appContext.filesDir, "configs").apply { mkdirs() }
    private fun activeMarker(): File = File(configDir(), "active.txt")

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
		val name = cfgNameFromLink(link)
		val f = File(configsRoot(), "$name.json")
		f.writeText(conf)
		// mark as active by copying into config.json
		val active = File(configDir(), "config.json")
		active.writeText(conf)
		activeMarker().writeText(name)
		return active
	}

    fun listConfigs(): List<String> {
        return configsRoot().listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    fun getActiveConfigName(): String? = activeMarker().takeIf { it.exists() }?.readText()?.trim().takeIf { !it.isNullOrBlank() }

    fun activateConfig(name: String): File {
        val src = File(configsRoot(), "$name.json")
        require(src.exists()) { "Config not found" }
        val dst = File(configDir(), "config.json")
        dst.writeText(src.readText())
        activeMarker().writeText(name)
        return dst
    }

    fun deleteConfig(name: String) {
        File(configsRoot(), "$name.json").delete()
        val active = getActiveConfigName()
        if (active == name) {
            // clear active
            activeMarker().delete()
        }
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

    private fun cfgNameFromLink(link: String): String {
        return try {
            val idx = link.indexOf('#')
            if (idx >= 0 && idx + 1 < link.length) {
                val name = link.substring(idx + 1).trim()
                if (name.isNotBlank()) return sanitize(name)
            }
            "config_${System.currentTimeMillis()}"
        } catch (_: Throwable) { "config_${System.currentTimeMillis()}" }
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
    }
}

