package com.netvor.parser

import java.net.URI
import java.net.URLDecoder

data class VlessConfig(
	val address: String,
	val port: Int,
	val uuid: String,
	val network: String?,
	val security: String?,
	val headerType: String?,
	val hostHeader: String?,
	val path: String?,
	val sniList: List<String>?,
	val alpnList: List<String>?,
	val fingerprint: String?
)

object VlessLinkParser {
	fun parse(link: String): VlessConfig {
		val uri = URI(link)
		require(uri.scheme == "vless") { "Invalid scheme" }
		val uuid = uri.userInfo ?: error("Missing UUID in vless link")
		val host = uri.host ?: error("Missing host")
		val port = if (uri.port > 0) uri.port else 443
		val params = parseQuery(uri.rawQuery.orEmpty())
		val network = params["type"]
		val security = params["security"]
		val headerType = params["headerType"]
		val hostHeader = params["host"]
		val path = params["path"]?.let { decode(it) }
		val sniList = params["sni"]?.split(',')?.filter { it.isNotBlank() }
		val alpnList = params["alpn"]?.split(',')?.filter { it.isNotBlank() }
		val fp = params["fp"]
		return VlessConfig(
			address = host,
			port = port,
			uuid = uuid,
			network = network,
			security = security,
			headerType = headerType,
			hostHeader = hostHeader,
			path = path,
			sniList = sniList,
			alpnList = alpnList,
			fingerprint = fp
		)
	}

	private fun parseQuery(q: String): Map<String, String> {
		if (q.isEmpty()) return emptyMap()
		return q.split('&').mapNotNull { part ->
			val idx = part.indexOf('=')
			if (idx <= 0) return@mapNotNull null
			val k = decode(part.substring(0, idx))
			val v = decode(part.substring(idx + 1))
			k to v
		}.toMap()
	}

	private fun decode(s: String): String = URLDecoder.decode(s, Charsets.UTF_8)
}

