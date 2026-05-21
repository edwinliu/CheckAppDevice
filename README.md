# DeviceVeil

DeviceVeil is an Android 10-15 Xposed/LSPosed module for device fingerprint protection. Its package name is `com.deviceveil.guard`, and its Xposed entry point is `com.deviceveil.guard.HookInit`. The module enables configurable Java-layer and native-layer hooks inside selected target app processes to observe, normalize, or spoof common device identifiers, system environment values, file/command probes, anti-debugging signals, and anti-Xposed checks.

This repository is a generic module. It does not include hard-coded business targets, app-specific APIs, signing algorithms, or remote RPC logic. Target apps, process rules, and hook modules are configured from the DeviceVeil UI.

## Features

- Target selection: choose installed apps from the UI or add package names manually.
- Process rules: supports `*`, `:remote`, `!:push`, full process names, and wildcard patterns.
- Per-module switches: every hook capability can be enabled or disabled independently.
- Stable mode: enables the recommended module set and keeps noisy monitors, native hooks, and debug bridges disabled by default.
- Per-target profile: generates and reuses `fake_device_info.json` in each target app data directory.
- Java hooks: Android framework APIs, system settings, package manager, telephony, WiFi, battery, audio, WebView, Canvas, accounts, clipboard, location, VPN/proxy, sensors, and related surfaces.
- Native hooks: `libcorner_monit.so` with Dobby hooks for libc, files/proc, system properties, DRM, network interfaces, `ptrace`, `ioctl`, and related low-level entry points.
- Monitoring events: optional reporting for device API reads, file checks, command execution, and system property access.
- Redacted logs: sensitive values are masked by default, with a UI switch for full-value diagnostics.
- Anti-detection handling: filters common Root, Magisk, Xposed/LSPosed, debugger, proxy/VPN, sensitive path, and sensitive package traces.

## Default Behavior

On first launch, the UI writes `module_config.xml` and applies the stable preset. The default enabled modules are:

- Boot time spoofing
- System / WiFi / battery information
- Device identifier protection
- Advertising IDs, GAID / OAID
- AppSet / Firebase / FCM
- Package list protection
- Third-party SDK persistent IDs
- Additional system API protection
- System property protection

Modules such as WebView, Canvas, clipboard, accounts, location, VPN, behavior fingerprinting, Xposed trace hiding, native hooks, Binder monitoring, integrity monitoring, command/file monitoring, and the Sekiro debug bridge are disabled by default. Enable them only as needed and validate stability per target app.

## Usage

1. Build and install the APK.
2. Enable DeviceVeil in LSPosed and include the apps you want to protect in the LSPosed scope.
3. Open DeviceVeil.
4. In the target section, select installed apps or manually enter package names.
5. Adjust process rules and hook module switches as needed.
6. Tap save.
7. Force stop and reopen the target app so its process reloads the configuration.

When target packages are configured in the UI, hooks run only for processes that match `target_packages` and pass `process_rules`. The module package itself is always skipped.

If the target list is empty, the current implementation can still trust the LSPosed scope as a fallback when LSPosed injects the module into a third-party app. To avoid accidental hooks, always configure explicit targets in the DeviceVeil UI.

## UI

- Target scope: global enable switch, target app list, and process rules.
- Runtime self-check: global state, target packages, process rules, stable mode, config path, readability, enabled module count, and native hook status.
- Installed apps: search by app label or package name, then add or remove targets.
- Hook coverage: stable mode, sensitive logging, and all hook module switches.
- Apply stable preset: restores the default recommended module set.
- Disable all hooks: disables every hook module and clears the stable mode flag.

Saved changes do not affect already-running target processes. Force stop the target app and launch it again.

## Process Rules

Process rules are stored in `process_rules`. Multiple rules can be separated by spaces, newlines, commas, or tabs.

| Rule | Meaning |
| --- | --- |
| `*` | Allow all processes of the target app |
| `:remote` | Match `target.package:remote` or any process ending in `:remote` |
| `com.example.app` | Match the exact package/main process name |
| `com.example.app:*` | Match process names using a wildcard pattern |
| `!:push` | Exclude the `:push` subprocess |

Rules starting with `!` are exclusions and take precedence. If there are no include rules, any process not excluded is allowed.

## Hook Modules

### Recommended Protection

- `BootTimeFakeHook`: handles `SystemClock`, `/proc/uptime`, `/proc/stat`, `boot_id`, and related boot-time signals.
- `SystemInfoFakeHook`: handles WiFi, scan results, battery, system settings, audio, network state, timezone, and locale.
- `FakeDeviceModule`: handles Android ID, restricted telephony IDs, Build serial, WiFi/NetworkInterface MAC, DRM ID, install/update time, and external storage state.
- `AdIdFakeHook`: handles Google Advertising ID, MSA OAID, and Huawei/Xiaomi/OPPO/VIVO vendor OAID SDKs.
- `AppSetIdFakeHook`: handles App Set ID, Firebase Installation ID, FCM token, and selected `SubscriptionManager` APIs.
- `PackageManagerHook`: filters sensitive packages and handles package lists, package info, install source, install/update time, and reflective access.
- `PersistentIdFakeHook`: handles common SDK IDs stored in SharedPreferences, Firebase, AppsFlyer, Leanplum, Crashlytics/FCM, and similar identifiers.
- `MissingInfoHook`: covers additional APIs such as StorageManager, UserManager, Display, Biometric, PackageInstaller, environment variables, Settings, input method, wallpaper, camera, and codecs.
- `SystemPropertiesFakeHook`: handles sensitive values exposed through `android.os.SystemProperties.get()`.

### Advanced Protection

- `WebViewFakeHook`: handles WebSettings and WebView User-Agent related signals.
- `CanvasFakeHook`: injects light noise into Bitmap/Canvas reads.
- `ClipboardFakeHook`: restricts clipboard reads.
- `BootloaderFakeHook`: handles bootloader, verified boot, security patch, device policy, and keyguard state fields.
- `AccountFakeHook`: hides or fakes AccountManager and Google account data.
- `UsageStatsFakeHook`: handles UsageStatsManager and NetworkStatsManager.
- `FontListFakeHook`: filters font lists and font file probes.
- `LocationFakeHook`: handles LocationManager, Location, FusedLocationProvider, and Geocoder.
- `VpnDetectionBypassHook`: filters VPN/proxy signals from NetworkInterface, NetworkCapabilities, and ConnectivityManager.
- `BehaviorFingerprintHook`: handles sensors, touch coordinates, input timing, and selected WebView behavior fingerprints.
- `XposedTraceBypassHook`: hides Xposed traces in Method, Field, ClassLoader, and Thread strings.
- `FlutterRnHook`: handles common Flutter and React Native device-info plugins.
- `NativeHooks`: loads `libcorner_monit.so` and syncs Java-generated fake config into native code.

### Monitoring And Debugging

- `DeviceLogger`: logs access to Android ID, telephony, WiFi, Bluetooth, sensors, display, advertising ID, and other device APIs.
- `CmdAndFileLogger`: logs and handles `Runtime.exec()`, `ProcessBuilder.start()`, `File.exists()`, `File.listFiles()`, system properties, and Play Integrity related calls.
- `BinderHook`: monitors selected Binder transactions.
- `IntegrityCheckHook`: monitors Zip, CRC32, and MessageDigest integrity-check entry points. CRC/digest rewriting is disabled in the current code.
- `Sekiro`: optional debug bridge using `sekiro_config.json` in the target data directory.

Monitoring modules can be noisy and may affect performance. Use them temporarily when tracing call paths.

## Native Layer

The native library is `libcorner_monit.so`, loaded by `NativeHooks.init()` inside the target process. It is built with CMake, Dobby static libraries, and the Android NDK.

Main native coverage includes:

- Files and paths: `open`, `open64`, `openat`, `__openat`, `access`, `faccessat`, `readlink`, `readlinkat`.
- File reads: `read`, `pread`, `pread64`, used for filtering `/proc/self/maps`, `/proc/*/status`, and related content.
- File metadata: `stat`, `lstat`, `fstat`, `newfstatat`, `statfs`, `mmap`, `mmap64`, `process_vm_readv`.
- Command execution: `execve`, `system`, `popen`.
- Anti-debugging: `ptrace(PTRACE_TRACEME)` and `TracerPid` normalization.
- Environment and properties: `getenv`, `__system_property_get`, `uname`.
- Network interfaces: `getifaddrs`, `ioctl(SIOCGIFHWADDR)`.
- DRM: `AMediaDrm_getPropertyByteArray`.
- Module enumeration: `dl_iterate_phdr`.
- Selected syscall stubs: supplemental hooks for direct syscall paths.

The current Gradle/NDK configuration packages only `arm64-v8a` and `armeabi-v7a`. The repository contains x86/x86_64 Dobby libraries, but `app/build.gradle` does not enable x86 ABI filters.

## Configuration And Data Files

Module configuration:

```text
/data/data/com.deviceveil.guard/shared_prefs/module_config.xml
```

Target runtime files:

```text
/data/data/<target_package>/fake_device_info.json
/data/data/<target_package>/sekiro_config.json
```

`fake_device_info.json` stores the per-target device profile. It includes Android ID, boot_id, Bluetooth MAC, install/update times, DRM ID, restricted WiFi MAC, GAID, OAID, App Set ID, Firebase ID, FCM token, WiFi, battery, audio, timezone, and locale fields. Delete this file or increase `profile_reset_token` in the config to regenerate the profile on the next target launch.

`sekiro_config.json` is used only when the Sekiro module is enabled. Defaults from `ConfigManager` are group `deviceguard`, client id `950`, and endpoint `192.168.31.58:5612`.

## Android 15 Privacy Strategy

The project enables `FakeData.ANDROID15_NON_ROOT_PRIVACY_MODE` by default. The goal is to mimic what a normal app can see under the modern Android permission model instead of inventing full hardware identifiers that ordinary apps should not receive.

- IMEI, MEID, IMSI, and ICCID default to `null` or restricted system semantics.
- `Build.getSerial()` and serial-related properties default to `unknown`.
- WiFi MAC and native network-interface MAC default to `02:00:00:00:00:00` or restricted values.
- SSID, BSSID, and network ID prefer system-restricted shapes.
- Sensitive `/sys`, `/proc`, boot, and verified-boot paths are hidden or normalized.

This reduces contradictions where a normal app appears to obtain hardware identifiers it should not be able to access.

## Project Layout

```text
app/src/main/
├── AndroidManifest.xml                 # Xposed metadata, launcher Activity, broadcast receiver
├── assets/xposed_init                  # LSPosed entry class
├── java/com/deviceveil/guard/
│   ├── MainActivity.java               # Configuration UI
│   ├── HookInit.java                   # IXposedHookLoadPackage entry point
│   ├── ModuleConfig.java               # Config reading, caching, process rules, defaults
│   ├── ModuleRuntime.java              # Runtime config cache in target processes
│   ├── Constants.java                  # Package names, paths, hidden lists, log limits
│   ├── FakeData.java                   # Default fake values and Android 15 privacy policy
│   ├── RandomIdGenerator.java          # Stable profile field generation
│   ├── NativeHooks.java                # JNI bridge and native config sync
│   ├── MonitorReporter.java            # Sends monitor events from target process
│   ├── MonitorEventReceiver.java       # Receives monitor events in module process
│   ├── MonitorEventStore.java          # Stores monitor counts and recent events
│   └── *Hook.java                      # Hook modules
└── cpp/
    ├── CMakeLists.txt                  # Native build config
    ├── corner_monit.cpp                # JNI exports, native hook init, config sync
    ├── Foundation/IOReplace.cpp        # libc/proc/path/command/property hooks
    ├── Foundation/SandboxFs.cpp        # Path sandbox helpers
    ├── Foundation/SensorHook.cpp       # Native sensor handling
    ├── Foundation/HookFunction.cpp     # Dobby hook wrapper
    ├── Foundation/Symbol.cpp           # Symbol lookup
    └── jniLibs/                        # Dobby static libraries
```

## Build

Requirements:

- Android Studio / Gradle Wrapper
- JDK 17
- Android Gradle Plugin 8.3.0
- Gradle 8.7
- compileSdk 34, targetSdk 34, minSdk 29
- NDK `25.0.8775105`
- CMake `3.22.1`

Commands:

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release builds currently do not enable minification:

```gradle
minifyEnabled false
```

## Troubleshooting

- In the UI, the config path should be `/data/data/com.deviceveil.guard/shared_prefs/module_config.xml`.
- The config readability check should be positive. If it is not, save the config again.
- Force stop the target app after every config change.
- Make sure both LSPosed scope and DeviceVeil's target list include the target app.
- If the target app uses multiple processes, verify that `process_rules` covers the subprocesses.
- Start with the stable preset, then enable advanced and monitoring modules one by one.
- If native hooks cause instability, disable the Native Hook module first.
- Search logs for `DeviceVeil` or `[设备信息记录]`.

## Known Limits

- No hardware-backed remote attestation closure is provided. Android Keystore, TEE/StrongBox, Play Integrity, SafetyNet, certificate extensions, and attestation challenges may still expose real security state or inconsistent fields.
- Binder coverage is not a full structural rewrite. The project includes Binder monitoring, but many system-service values are primarily covered through Java wrapper hooks.
- Native hooks cannot guarantee coverage for every direct syscall, inline assembly path, statically linked library, self-checking routine, or anti-hook framework.
- WebView, Canvas, GPU, fonts, resolution, system version, and drivers have cross-field consistency requirements. Over-spoofing individual fields can create new fingerprints.
- Monitoring, Binder, integrity, and native modules may add performance overhead or compatibility risk.
- Config reading depends on the target process being able to access the module config XML. Some ROMs, SELinux policies, or multi-user environments may need additional handling.
- Target app private data paths, APK paths, `Android/data/<package>`, and `Android/obb/<package>` are excluded by path rules, but complex storage redirection may require more rules.

## Roadmap Ideas

- Move certificate, integrity, and remote-attestation handling into a separate advanced module disabled by default.
- Add a UI button for device-profile reset by updating `profile_reset_token`.
- Display `MonitorEventStore` recent events and per-target counters in the UI.
- Split Native Hook into smaller per-capability switches.
- Add tests for multi-user paths, Work Profile, and storage redirection.
- Build consistency test samples for WebView, Canvas, GPU, and fonts.

## Scope

DeviceVeil is useful for generic Android device fingerprint protection, hook coverage validation, anti-detection signal observation, and local privacy-field normalization. It should not be treated as a complete risk-control bypass framework and does not promise to bypass any specific app, business API, or hardware attestation chain.
