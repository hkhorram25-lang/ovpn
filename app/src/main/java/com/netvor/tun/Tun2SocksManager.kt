package com.netvor.tun

import android.content.Context
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.system.Os
import android.system.OsConstants

class Tun2SocksManager(private val appContext: Context) {

	private fun binDir(): File = File(appContext.codeCacheDir, "tun2socks").apply { mkdirs() }
	private fun binFile(): File = File(binDir(), "tun2socks")

	suspend fun ensurePresent(): File = withContext(Dispatchers.IO) {
		val f = binFile()
		if (!f.exists()) copyFromAssets()
		try { Os.chmod(f.absolutePath, OsConstants.S_IRWXU) } catch (_: Throwable) { f.setExecutable(true) }
		f
	}

	private fun assetName(): String {
		val abis = android.os.Build.SUPPORTED_ABIS?.toList().orEmpty()
		return when {
			abis.any { it.contains("arm64") || it.contains("aarch64") } -> "tun2socks-arm64-v8a"
			abis.any { it.contains("armeabi-v7a") } -> "tun2socks-armeabi-v7a"
			abis.any { it.contains("x86_64") } -> "tun2socks-x86_64"
			else -> "tun2socks-x86"
		}
	}

	private suspend fun copyFromAssets() = withContext(Dispatchers.IO) {
		appContext.assets.open(assetName()).use { input ->
			FileOutputStream(binFile()).use { out -> input.copyTo(out) }
		}
	}

	suspend fun run(tunFd: ParcelFileDescriptor, socksAddr: String = "127.0.0.1:10808"): Process = withContext(Dispatchers.IO) {
		val bin = ensurePresent()
		// Arguments may vary depending on build; using common flags for go-tun2socks
		ProcessBuilder(
			bin.absolutePath,
			"--tunfd", tunFd.fd.toString(),
			"--tunmtu", "1500",
			"--socks-server-addr", socksAddr,
			"--enable-udprelay"
		)
			.directory(binDir())
			.redirectErrorStream(true)
			.start()
	}
}

