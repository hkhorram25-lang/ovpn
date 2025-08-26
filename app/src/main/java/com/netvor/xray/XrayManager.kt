package com.netvor.xray

import android.content.Context
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class XrayManager(private val appContext: Context) {

	private fun binariesDir(): File = File(appContext.filesDir, "xray").apply { mkdirs() }

	private fun binaryFile(): File = File(binariesDir(), "xray")

	suspend fun ensureXrayPresent(): File = withContext(Dispatchers.IO) {
		val bin = binaryFile()
		if (!bin.exists()) {
			copyFromAssets()
		}
		bin.setExecutable(true)
		bin
	}

	private suspend fun copyFromAssets() = withContext(Dispatchers.IO) {
		val assetName = when (CpuAbi.detect()) {
			CpuAbi.ARM64 -> "xray-arm64-v8a"
			CpuAbi.ARM32 -> "xray-armeabi-v7a"
			CpuAbi.X86_64 -> "xray-x86_64"
			CpuAbi.X86 -> "xray-x86"
		}
		appContext.assets.open(assetName).use { input: InputStream ->
			FileOutputStream(binaryFile()).use { out -> input.copyTo(out) }
		}
	}

	suspend fun runXray(tunInterface: ParcelFileDescriptor, configFile: File): Process = withContext(Dispatchers.IO) {
		val bin = ensureXrayPresent()
		ProcessBuilder(bin.absolutePath, "run", "-c", configFile.absolutePath)
			.directory(binariesDir())
			.redirectErrorStream(true)
			.start()
	}
}

