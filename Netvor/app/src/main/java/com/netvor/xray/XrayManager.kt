package com.netvor.xray

import android.content.Context
import android.os.ParcelFileDescriptor
import com.netvor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class XrayManager(private val appContext: Context) {

	private fun binariesDir(): File = File(appContext.filesDir, "xray").apply { mkdirs() }

	private fun binaryFile(): File = File(binariesDir(), "xray")

	private fun archiveFile(): File = File(binariesDir(), "xray.zip")

	private fun releaseUrl(): String {
		val abi = CpuAbi.detect().releaseSuffix
		val version = BuildConfig.XRAY_VERSION
		val repo = BuildConfig.XRAY_REPO
		// Official releases typically: Xray-android-<abi>.zip under assets
		return "https://github.com/$repo/releases/download/$version/Xray-${abi}.zip"
	}

	suspend fun ensureXrayPresent(): File = withContext(Dispatchers.IO) {
		val bin = binaryFile()
		if (!bin.exists()) {
			downloadAndExtract()
		}
		bin.setExecutable(true)
		bin
	}

	private suspend fun downloadAndExtract() = withContext(Dispatchers.IO) {
		val url = URL(releaseUrl())
		val conn = (url.openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			connectTimeout = 20000
			readTimeout = 30000
		}
		conn.inputStream.use { input ->
			FileOutputStream(archiveFile()).use { out ->
				input.copyTo(out)
			}
		}
		ZipInputStream(archiveFile().inputStream()).use { zis ->
			var entry = zis.nextEntry
			while (entry != null) {
				val name = entry.name
				if (!entry.isDirectory) {
					val outFile = if (name.endsWith("xray")) binaryFile() else File(binariesDir(), name.substringAfterLast('/'))
					FileOutputStream(outFile).use { fos ->
						zis.copyTo(fos)
					}
				}
				entry = zis.nextEntry
			}
		}
	}

	suspend fun runXray(tunInterface: ParcelFileDescriptor, configFile: File): Process = withContext(Dispatchers.IO) {
		val bin = ensureXrayPresent()
		// Xray supports --config config.json and uses TUN via sniffed fd via protect() or tun2socks like solution.
		// For simplicity here, we run with provided config; full TUN plumbing will be handled separately.
		ProcessBuilder(bin.absolutePath, "run", "-c", configFile.absolutePath)
			.directory(binariesDir())
			.redirectErrorStream(true)
			.start()
	}
}

