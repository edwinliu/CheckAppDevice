# DeviceVeil

DeviceVeil 是一个面向 Android 10-15 的 Xposed/LSPosed 设备指纹保护模块。模块包名为 `com.deviceveil.guard`，入口类为 `com.deviceveil.guard.HookInit`。它在目标应用进程内按配置启用 Java 层和 Native 层 Hook，用于记录、归一化或伪装常见设备标识、系统环境、文件/命令探测、反调试和反 Xposed 检测信号。

项目当前是通用模块，不内置固定业务目标、专属接口、签名算法或远端 RPC 逻辑。目标应用、进程规则和 Hook 模块都通过 DeviceVeil UI 配置后生效。

## 功能概览

- 目标配置：从已安装应用列表选择目标，也支持手动输入包名。
- 进程规则：支持 `*`、`:remote`、`!:push`、完整进程名和带 `*` 的通配模式。
- 模块开关：每个 Hook 能力都可以在 UI 中单独开启或关闭。
- 稳定模式：默认启用推荐模块组合，关闭高噪声监控、Native Hook 和调试桥。
- 设备画像：为每个目标应用数据目录生成并复用 `fake_device_info.json`。
- Java Hook：覆盖 Android Framework、系统设置、包管理、电话/WiFi/电池/音频、WebView、Canvas、账户、剪贴板、位置、VPN/代理、传感器等入口。
- Native Hook：通过 `libcorner_monit.so` 和 Dobby Hook libc、文件/proc、系统属性、DRM、网络接口、ptrace、ioctl 等低层入口。
- 监控事件：可记录设备 API、文件检测、命令执行和系统属性访问，并通过广播回传到模块进程。
- 脱敏日志：默认隐藏敏感值，只输出必要诊断信息；可在 UI 中打开敏感值日志。
- 反检测处理：过滤常见 Root、Magisk、Xposed/LSPosed、调试器、代理/VPN、敏感路径和敏感包名痕迹。

## 默认行为

首次打开 UI 时会写入 `module_config.xml` 并设置稳定预设。默认启用的模块包括：

- 开机时间伪装
- 系统 / WiFi / 电池信息
- 设备标识保护
- 广告标识 GAID / OAID
- AppSet / Firebase / FCM
- 应用列表保护
- 三方 SDK 持久标识
- 补充系统 API 保护
- 系统属性保护

默认关闭的模块包括 WebView、Canvas、剪贴板、账户、位置、VPN、行为指纹、Xposed 痕迹隐藏、Native 层 Hook、Binder/完整性/命令文件监控和 Sekiro 调试桥等。需要更强覆盖时可以手动开启，但应按目标应用逐项验证稳定性。

## 使用流程

1. 构建并安装 APK。
2. 在 LSPosed 中启用 DeviceVeil 模块，并为需要保护的应用配置作用域。
3. 打开 DeviceVeil。
4. 在“作用目标”中选择已安装应用，或手动输入包名。
5. 按需要调整进程规则和 Hook 模块开关。
6. 点击“保存配置”。
7. 强制停止目标应用后重新启动，使目标进程重新读取配置。

如果 UI 中配置了目标包名，只有匹配 `target_packages` 且通过 `process_rules` 的进程会执行 Hook。模块自身包名永远跳过。

如果目标列表为空，当前实现仍会在 LSPosed 已把模块注入到某个第三方应用时信任 LSPosed 作用域作为兜底。为了避免误 Hook，建议始终在 UI 中显式选择目标应用。

## UI 说明

- 作用目标：控制全局启用状态、目标应用列表和进程规则。
- 运行自检：显示全局开关、目标包名、进程规则、稳定模式、配置文件路径、配置可读状态、已启用模块数量和 Native Hook 状态。
- 已安装应用：按应用名或包名搜索并添加/移除目标。
- Hook 覆盖：控制稳定模式、敏感日志以及各 Hook 模块。
- 应用稳定预设：恢复默认推荐模块组合。
- 全部关闭 Hook：关闭所有 Hook 模块，并关闭稳定模式标记。

保存配置后不会影响已经运行的目标进程。必须强制停止目标应用并重新打开。

## 进程规则

进程规则保存在 `process_rules` 中，多个规则可用空格、换行、逗号或制表符分隔。

| 规则 | 含义 |
| --- | --- |
| `*` | 允许目标应用的所有进程 |
| `:remote` | 匹配 `目标包名:remote` 或任意以 `:remote` 结尾的进程 |
| `com.example.app` | 只匹配完整包名/主进程名 |
| `com.example.app:*` | 匹配符合通配模式的进程名 |
| `!:push` | 排除 `:push` 子进程 |

排除规则以 `!` 开头，命中后优先返回不允许。没有包含规则时，未被排除的进程默认允许。

## Hook 模块

### 推荐保护

- `BootTimeFakeHook`：处理 `SystemClock`、`/proc/uptime`、`/proc/stat`、`boot_id` 等开机时间相关信号。
- `SystemInfoFakeHook`：处理 WiFi、扫描结果、电池、系统设置、音频、网络状态、时区、语言等系统环境。
- `FakeDeviceModule`：处理 Android ID、受限电话标识、Build serial、WiFi/NetworkInterface MAC、DRM ID、安装/更新时间、外部存储状态。
- `AdIdFakeHook`：处理 Google Advertising ID、MSA OAID、华为/小米/OPPO/VIVO 等厂商 OAID SDK。
- `AppSetIdFakeHook`：处理 App Set ID、Firebase Installation ID、FCM token、SubscriptionManager 部分入口。
- `PackageManagerHook`：过滤敏感包名，处理应用列表、包信息、安装来源、安装/更新时间和相关反射访问。
- `PersistentIdFakeHook`：处理 SharedPreferences 中常见 SDK 持久 ID、Firebase、AppsFlyer、Leanplum、Crashlytics/FCM 等标识。
- `MissingInfoHook`：补充 StorageManager、UserManager、Display、Biometric、PackageInstaller、环境变量、Settings、输入法、壁纸、相机/编解码等入口。
- `SystemPropertiesFakeHook`：处理 `android.os.SystemProperties.get()` 暴露的敏感系统属性。

### 高级保护

- `WebViewFakeHook`：处理 WebSettings 和 WebView User-Agent 相关信号。
- `CanvasFakeHook`：对 Bitmap/Canvas 相关读取注入轻微噪声。
- `ClipboardFakeHook`：限制剪贴板读取。
- `BootloaderFakeHook`：处理 bootloader、verified boot、安全补丁、设备策略和锁屏状态等字段。
- `AccountFakeHook`：隐藏或伪造 AccountManager/Google 账户信息。
- `UsageStatsFakeHook`：处理 UsageStatsManager 和 NetworkStatsManager。
- `FontListFakeHook`：过滤字体列表和字体文件探测。
- `LocationFakeHook`：处理 LocationManager、Location、FusedLocationProvider 和 Geocoder。
- `VpnDetectionBypassHook`：过滤 VPN/代理相关 NetworkInterface、NetworkCapabilities 和 ConnectivityManager 信号。
- `BehaviorFingerprintHook`：处理传感器、触摸坐标、输入节奏和部分 WebView 行为指纹。
- `XposedTraceBypassHook`：隐藏 Method/Field/ClassLoader/Thread 等字符串中的 Xposed 痕迹。
- `FlutterRnHook`：处理 Flutter 和 React Native 常见设备信息插件。
- `NativeHooks`：加载 `libcorner_monit.so` 并同步 Java 层生成的伪造配置到 Native 层。

### 监控与调试

- `DeviceLogger`：记录 Android ID、电话、WiFi、蓝牙、传感器、显示、广告 ID 等设备 API 访问。
- `CmdAndFileLogger`：记录并处理 `Runtime.exec()`、`ProcessBuilder.start()`、`File.exists()`、`File.listFiles()`、系统属性和 Play Integrity 相关调用。
- `BinderHook`：监控部分 Binder transaction。
- `IntegrityCheckHook`：监控 Zip、CRC32 和 MessageDigest 等完整性检查入口；默认不重写 CRC/digest。
- `Sekiro`：可选调试桥，读取目标数据目录下的 `sekiro_config.json`。

监控类模块日志较多，可能影响性能。建议只在排查调用链时临时开启。

## Native 层能力

Native 库名为 `libcorner_monit.so`，由 `NativeHooks.init()` 在目标进程中加载。构建时使用 CMake、Dobby 静态库和 Android NDK。

主要覆盖点包括：

- 文件与路径：`open`、`open64`、`openat`、`__openat`、`access`、`faccessat`、`readlink`、`readlinkat`。
- 文件读取：`read`、`pread`、`pread64`，用于过滤 `/proc/self/maps`、`/proc/*/status` 等内容。
- 文件元信息：`stat`、`lstat`、`fstat`、`newfstatat`、`statfs`、`mmap`、`mmap64`、`process_vm_readv`。
- 命令执行：`execve`、`system`、`popen`。
- 反调试：`ptrace(PTRACE_TRACEME)`、`TracerPid` 归一化。
- 环境与属性：`getenv`、`__system_property_get`、`uname`。
- 网络接口：`getifaddrs`、`ioctl(SIOCGIFHWADDR)`。
- DRM：`AMediaDrm_getPropertyByteArray`。
- 模块枚举：`dl_iterate_phdr`。
- 部分 syscall stub：对直接 syscall 路径做补充 Hook。

当前 Gradle/NDK 只打包 `arm64-v8a` 和 `armeabi-v7a`。仓库中虽然保留了 x86/x86_64 的 Dobby 静态库，但 `app/build.gradle` 的 `abiFilters` 未启用 x86。

## 配置与数据文件

模块配置文件：

```text
/data/data/com.deviceveil.guard/shared_prefs/module_config.xml
```

目标应用运行时文件：

```text
/data/data/<target_package>/fake_device_info.json
/data/data/<target_package>/sekiro_config.json
```

`fake_device_info.json` 保存目标应用专属设备画像。它包含 Android ID、boot_id、蓝牙 MAC、安装/更新时间、DRM ID、受限 WiFi MAC、GAID、OAID、App Set ID、Firebase ID、FCM token、WiFi、电池、音频、时区和语言等字段。删除该文件或提高配置中的 `profile_reset_token` 后，目标应用下次启动会重新生成画像。

`sekiro_config.json` 仅在 Sekiro 模块开启时使用。默认值来自 `ConfigManager`：group 为 `deviceguard`，client id 为 `950`，地址为 `192.168.31.58:5612`。

## Android 15 普通应用策略

项目默认启用 `FakeData.ANDROID15_NON_ROOT_PRIVACY_MODE`，尽量模拟普通应用在现代 Android 权限模型下能看到的结果，而不是生成一套完整硬件真值。

- IMEI、MEID、IMSI、ICCID 默认返回 `null` 或系统限制语义。
- `Build.getSerial()` 和相关 serial 属性默认返回 `unknown`。
- WiFi MAC 和 native 网络接口 MAC 默认返回 `02:00:00:00:00:00` 或受限值。
- SSID、BSSID、Network ID 默认优先保持系统限制形态。
- `/sys`、`/proc` 和 boot/verified boot 相关敏感路径会被隐藏或归一化。

这样做可以降低“普通应用拿到了不该拿到的硬件标识”带来的指纹矛盾。

## 项目结构

```text
app/src/main/
├── AndroidManifest.xml                 # Xposed 元数据、启动 Activity、广播接收器
├── assets/xposed_init                  # LSPosed 入口类
├── java/com/deviceveil/guard/
│   ├── MainActivity.java               # 配置 UI
│   ├── HookInit.java                   # IXposedHookLoadPackage 入口
│   ├── ModuleConfig.java               # 配置读取、缓存、进程规则、模块默认值
│   ├── ModuleRuntime.java              # 目标进程运行时配置缓存
│   ├── Constants.java                  # 包名、路径、隐藏列表、日志限制
│   ├── FakeData.java                   # 默认伪造值和 Android 15 隐私策略
│   ├── RandomIdGenerator.java          # 稳定画像字段生成
│   ├── NativeHooks.java                # JNI 桥和 Native 配置同步
│   ├── MonitorReporter.java            # 目标进程监控事件发送
│   ├── MonitorEventReceiver.java       # 模块进程监控事件接收
│   ├── MonitorEventStore.java          # 监控计数和最近事件存储
│   └── *Hook.java                      # 各类 Hook 模块
└── cpp/
    ├── CMakeLists.txt                  # Native 构建配置
    ├── corner_monit.cpp                # JNI 导出、Native Hook 初始化、配置同步
    ├── Foundation/IOReplace.cpp        # libc/proc/路径/命令/属性 Hook
    ├── Foundation/SandboxFs.cpp        # 路径沙箱辅助
    ├── Foundation/SensorHook.cpp       # Native 传感器处理
    ├── Foundation/HookFunction.cpp     # Dobby Hook 封装
    ├── Foundation/Symbol.cpp           # 符号查找
    └── jniLibs/                        # Dobby 静态库
```

## 构建

环境要求：

- Android Studio / Gradle Wrapper
- JDK 17
- Android Gradle Plugin 8.3.0
- Gradle 8.7
- compileSdk 34，targetSdk 34，minSdk 29
- NDK `25.0.8775105`
- CMake `3.22.1`

常用命令：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建当前未启用混淆：

```gradle
minifyEnabled false
```

## 排错建议

- UI 中“配置文件”应显示 `/data/data/com.deviceveil.guard/shared_prefs/module_config.xml`。
- “配置已设为全局可读”应为“是”；如果不是，重新保存配置。
- 修改配置后必须强制停止目标应用。
- LSPosed 作用域和 DeviceVeil UI 目标列表都应包含目标应用。
- 若目标有多个进程，确认 `process_rules` 是否覆盖目标子进程。
- 先使用稳定预设验证基础能力，再逐个开启高级和监控模块。
- Native Hook 出问题时，先关闭“Native 层 Hook”确认是否由 Native 覆盖点引起。
- 日志关键词可搜索 `DeviceVeil` 或 `[设备信息记录]`。

## 已知边界

- 不提供硬件级远端证明闭环。Android Keystore、TEE/StrongBox、Play Integrity、SafetyNet、证书扩展和 attestation challenge 仍可能暴露真实安全状态或不一致字段。
- Binder 不是全量结构级重写。项目包含 Binder 监控，但大量系统服务数据仍主要由 Java 包装层 Hook 覆盖。
- Native Hook 不能保证覆盖所有直接 syscall、内联汇编、静态链接库、自校验代码和反 Hook 框架。
- WebView、Canvas、GPU、字体、分辨率、系统版本和驱动之间存在组合一致性，单独伪造过多字段可能形成新指纹。
- 监控模块、Binder 模块、完整性模块和 Native 模块可能带来性能成本或兼容性风险。
- 配置读取依赖目标进程能访问模块配置 XML。不同 ROM、SELinux 策略或多用户环境可能需要额外处理。
- 目标应用自身数据目录、APK 路径、`Android/data/<包名>` 和 `Android/obb/<包名>` 等路径会被排除，复杂存储重定向场景可能仍需补规则。

## 后续改进方向

- 将证书/完整性/远端证明相关能力独立为默认关闭的高级模块。
- 增加 UI 中的设备画像重置按钮，直接维护 `profile_reset_token`。
- 在 UI 中展示 `MonitorEventStore` 的最近事件和按目标统计。
- 为 Native Hook 增加按能力拆分的子开关，而不是单一总开关。
- 增加多用户路径、Work Profile 和存储重定向场景的测试。
- 为 WebView/Canvas/GPU/字体建立一致性校验样本。

## 适用范围

DeviceVeil 适合做通用 Android 设备指纹保护、Hook 覆盖验证、反检测信号观察和本地隐私字段归一化。它不应被理解为完整的风控绕过框架，也不承诺绕过任何具体应用、具体业务接口或硬件证明链路。
