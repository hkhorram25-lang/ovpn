package com.netvor.xray

import android.os.Build

enum class CpuAbi(val releaseSuffix: String) {
	ARM64("android-arm64-v8a"),
	ARM32("android-armv7"),
	X86_64("android-amd64"),
	X86("android-386");

	companion object {
		fun detect(): CpuAbi {
			val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
			return when {
				abis.any { it.contains("arm64", ignoreCase = true) || it.contains("aarch64", ignoreCase = true) } -> ARM64
				abis.any { it.contains("armeabi-v7a", ignoreCase = true) } -> ARM32
				abis.any { it.contains("x86_64", ignoreCase = true) } -> X86_64
				else -> X86
			}
		}
	}
}

