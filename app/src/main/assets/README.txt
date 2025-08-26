Place Xray binaries here with these exact names:
- xray-arm64-v8a (ELF executable for arm64-v8a)
- xray-armeabi-v7a (ELF executable for armeabi-v7a)
- xray-x86_64 (ELF executable for x86_64)
- xray-x86 (ELF executable for x86)

Each file must be marked executable after being copied at runtime. The app will select the appropriate binary by ABI, copy to internal storage, and exec it.