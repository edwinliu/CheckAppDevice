/**
 * ============================================================================
 * 文件名：corner_monit.cpp
 * 功能：Native 层 Hook 初始化入口 + JNI 通信接口
 *
 * 主要功能：
 * 1. 初始化文件 I/O 重定向系统
 * 2. 配置需要隐藏的敏感路径
 * 3. 启动 Native 层 Hook 框架
 * 4. 提供 Java 与 Native 层的 JNI 通信接口
 *
 * 作者：DeviceVeil Team
 * 版本：4.11
 * 日期：2025-01-09
 * ============================================================================
 */

#include <main.h>
#include <mutex>
#include "SensorHook.h"
// #include "KeymasterHook.h"

using namespace std;

static const char* getLibc32Path(int apiLevel) {
    return apiLevel >= 29 ? "/apex/com.android.runtime/lib/bionic/libc.so" : "/system/lib/libc.so";
}

static const char* getLibc64Path(int apiLevel) {
    return apiLevel >= 29 ? "/apex/com.android.runtime/lib64/bionic/libc.so" : "/system/lib64/libc.so";
}

// ============================================================================
// 全局配置变量（可通过 JNI 动态修改）
// ============================================================================

// 模块版本信息
static const char* MODULE_VERSION = "4.11";
static const char* BUILD_DATE = "2025-01-09";

// 伪造配置（线程安全）
static mutex g_configMutex;
uint8_t g_fakeDrmId[32] = {
    0xA1, 0xB2, 0xC3, 0xD4, 0xE5, 0xF6, 0x07, 0x18,
    0x29, 0x3A, 0x4B, 0x5C, 0x6D, 0x7E, 0x8F, 0x90,
    0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE, 0xF0,
    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88
};
char g_fakeAndroidId[32] = "a1b2c3d4e5f67890";
// WiFi MAC 地址 (6 字节) - 与 FakeData.FAKE_NETWORK_MAC_BYTES 保持一致
uint8_t g_fakeWifiMac[6] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55};
// 蓝牙 MAC 地址 (字符串格式 XX:XX:XX:XX:XX:XX) - 可通过 JNI 动态更新
char g_fakeBluetoothMac[32] = "00:11:22:33:44:55";
// 设备序列号 - 可通过 JNI 动态更新
char g_fakeSerialNo[64] = "ABC123456789";
// 伪造的开机时间 (Unix 时间戳，秒) - 可通过 JNI 动态更新
int64_t g_fakeBootTime = 0;
// 伪造的 boot_id (UUID 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx) - 可通过 JNI 动态更新
char g_fakeBootId[64] = "00000000-0000-0000-0000-000000000000";
static bool g_logEnabled = true;
static bool g_configSynced = false;

// 命令执行监控开关
bool g_cmdMonitorEnabled = true;

// ============================================================================
// 文件元信息伪造配置 (stat/lstat/fstat)
// ============================================================================

// 伪造的文件时间戳基准 (Unix 时间戳，秒) - 用于计算文件时间
int64_t g_fakeFileTimeBase = 0;

// 伪造的文件大小偏移 (随机偏移量)
int64_t g_fakeFileSizeOffset = 0;

// 是否启用文件元信息伪造
bool g_fakeStatEnabled = false;

// 伪造的文件权限掩码 (默认保持原始权限)
mode_t g_fakeFileModeMask = 0;

// 需要伪造 stat 的路径前缀列表 (注意: 这些变量在 IOReplace.h 中声明为 extern)
char g_fakeStatPaths[MAX_FAKE_STAT_PATHS][256];
int g_fakeStatPathCount = 0;
static mutex g_fakeStatMutex;

// Hook 状态统计
static int g_hookCount = 0;
static int g_drmHookCount = 0;

// JavaVM 全局引用（用于回调 Java）
static JavaVM* g_javaVM = nullptr;

// ============================================================================
// 配置区域：敏感路径列表
// ============================================================================

/**
 * 需要进行路径重定向的敏感文件列表
 *
 * 这些路径通常与 Root 检测、框架检测相关
 * 系统会将这些路径重定向到随机生成的路径，使检测失效
 *
 * 分类：
 * - Root 相关：su 二进制文件、Superuser.apk
 * - Magisk 相关：magisk 二进制、模块路径
 * - Xposed/EdXposed 相关：riru、edxp 库文件
 */
vector<string> sensitivePathsForRedirection{
    // === Root 检测相关路径 ===
    "/system/app/Superuser.apk",        // Superuser 应用
    "/sbin/su",                         // su 二进制（sbin 目录）
    "/system/bin/su",                   // su 二进制（system/bin）
    "/system/xbin/su",                  // su 二进制（system/xbin）
    "/data/local/xbin/su",              // su 二进制（data/local/xbin）
    "/data/local/bin/su",               // su 二进制（data/local/bin）
    "/system/sd/xbin/su",               // su 二进制（system/sd/xbin）
    "/system/bin/failsafe/su",          // su 二进制（failsafe）
    "/data/local/su",                   // su 二进制（data/local）
    "/su/bin/su",                       // su 二进制（su/bin）

    // === Magisk 相关路径 ===
    "/sbin/magisk",                     // Magisk 主程序
    "/sbin/magisk32",                   // Magisk 32位版本
    "/sbin/magisk64",                   // Magisk 64位版本
    "/sbin/magiskinit",                 // Magisk 初始化程序
    "/sbin/magiskpolicy",               // Magisk SELinux 策略工具
    "/data/adb/magisk",                 // Magisk 数据目录
    "/data/user/0/com.topjohnwu.magisk", // Magisk Manager 数据

    // === Xposed/EdXposed/Riru 相关路径 ===
    "/sbin/riru.prop",                  // Riru 属性文件
    "/sbin/supolicy",                   // SuperSU 策略文件
    "/system/lib/libriru_edxp.so",      // EdXposed Riru 模块（32位）
    "/system/lib/libriru_edxp.so.s",    // EdXposed Riru 模块签名（32位）
    "/system/lib/libsandhook.edxp.so",  // EdXposed SandHook（32位）
    "/system/lib/libsandhook.edxp.so.s",// EdXposed SandHook 签名（32位）
    "/system/lib64/libriru_edxp.so",    // EdXposed Riru 模块（64位）
    "/system/lib64/libsandhook.edxp.so",// EdXposed SandHook（64位）
    "/system/lib64/libsandhook.edxp.so.s", // EdXposed SandHook 签名（64位）
    "/lib/armeabi-v7a/libriru.so",      // Riru 核心库（32位）
    "/lib/armeabi-v7a/libriruhide.so",  // Riru Hide 模块（32位）
    "/lib/armeabi-v7a/libriruloader.so",// Riru 加载器（32位）
    "/lib/arm64-v8a/libriru.so",        // Riru 核心库（64位）
    "/lib/arm64-v8a/libriruhide.so",    // Riru Hide 模块（64位）
    "/lib/arm64-v8a/libriruloader.so",  // Riru 加载器（64位）
    "/lib/arm64-v8a/librirud.so",       // Riru 守护进程（64位）
    "/data/adb/modules/riru-core/allow_install_app", // Riru 核心模块配置
    "/data/misc/riru/api_version",      // Riru API 版本
    "/data/misc/riru/version_code",     // Riru 版本代码
    "/data/misc/riru/version_name"      // Riru 版本名称
};

/**
 * 需要完全禁止访问的路径列表
 *
 * 这些路径会直接返回"不存在"，而不是重定向
 * 用于更强力的隐藏效果
 */
vector<string> forbiddenPaths{
    "/sbin/su"                          // 完全禁止访问 su 二进制
};




// ============================================================================
// 辅助函数：获取系统信息
// ============================================================================

/**
 * 获取 Android SDK 版本号
 *
 * @param outVersion 输出缓冲区，用于存储版本号字符串
 * @param bufferSize 缓冲区大小
 * @return 成功返回版本号（整数），失败返回 -1
 */
static int getAndroidSdkVersion(char* outVersion, size_t bufferSize) {
    if (__system_property_get("ro.build.version.sdk", outVersion) > 0) {
        int sdkVersion = atoi(outVersion);
        LOGE("[系统信息] Android SDK 版本: %d (%s)", sdkVersion, outVersion);
        return sdkVersion;
    }
    LOGE("[系统信息] ❌ 无法获取 SDK 版本");
    return -1;
}

/**
 * 获取 Android SDK 预览版本号
 *
 * @param outVersion 输出缓冲区，用于存储预览版本号字符串
 * @param bufferSize 缓冲区大小
 * @return 成功返回预览版本号（整数），失败返回 0
 */
static int getAndroidPreviewSdkVersion(char* outVersion, size_t bufferSize) {
    if (__system_property_get("ro.build.version.preview_sdk", outVersion) > 0) {
        int previewSdk = atoi(outVersion);
        if (previewSdk > 0) {
            LOGE("[系统信息] Android 预览版本: %d", previewSdk);
        }
        return previewSdk;
    }
    return 0;
}

/**
 * 获取当前进程的包名
 *
 * @param outPackageName 输出缓冲区，用于存储包名
 * @param bufferSize 缓冲区大小
 * @return 成功返回 true，失败返回 false
 */
static bool getCurrentPackageName(char* outPackageName, size_t bufferSize) {
    // 构造 /proc/[pid]/cmdline 路径
    string cmdlinePath = "/proc/" + to_string(getpid()) + "/cmdline";

    // 打开 cmdline 文件
    FILE *fp = fopen(cmdlinePath.c_str(), "r");
    if (fp == nullptr) {
        LOGE("[系统信息] ❌ 无法打开 %s", cmdlinePath.c_str());
        return false;
    }

    // 读取包名
    size_t bytesRead = fread(outPackageName, 1, bufferSize - 1, fp);
    fclose(fp);

    if (bytesRead > 0) {
        outPackageName[bytesRead] = '\0';  // 确保字符串结尾
        LOGE("[系统信息] 当前应用包名: %s", outPackageName);
        return true;
    }

    LOGE("[系统信息] ❌ 无法读取包名");
    return false;
}

/**
 * 初始化路径重定向系统
 *
 * @param sdkVersion SDK 版本号
 * @param previewSdkVersion 预览 SDK 版本号
 * @param packageName 应用包名
 * @return 成功返回 true，失败返回 false
 */
static bool initializePathRedirection(int sdkVersion, int previewSdkVersion, const char* packageName) {
    LOGE("[初始化] ========== 开始初始化路径重定向系统 ==========");

    // 1. 初始化路径重定向列表
    LOGE("[初始化] 步骤 1/3: 初始化敏感路径重定向列表...");
    IOReplace::substituteCharacter(sensitivePathsForRedirection);

    // 2. 初始化禁止访问路径列表
    LOGE("[初始化] 步骤 2/3: 初始化禁止访问路径列表...");
    IOReplace::initForbidPath(forbiddenPaths);

    // 3. 构造应用数据目录路径
    string appDataPath = string("/data/user/0/") + packageName + "/";
    LOGE("[初始化] 应用数据目录: %s", appDataPath.c_str());

    // 4. 启动 I/O Hook 系统
    LOGE("[初始化] 步骤 3/3: 启动 I/O Hook 系统...");
    LOGE("[初始化] - 32位 libc 路径: %s", getLibc32Path(sdkVersion));
    LOGE("[初始化] - 64位 libc 路径: %s", getLibc64Path(sdkVersion));

    IOReplace::startUniformer(
        getLibc32Path(sdkVersion),  // 32位 libc.so 路径
        getLibc64Path(sdkVersion),  // 64位 libc.so 路径
        appDataPath.c_str(),        // 应用数据目录
        sdkVersion,                 // SDK 版本
        previewSdkVersion           // 预览 SDK 版本
    );

    LOGE("[初始化] ========== 路径重定向系统初始化完成 ==========");
    return true;
}

// ============================================================================
// JNI 方法实现（供 Java 层调用）
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeGetVersion(JNIEnv *env, jclass clazz) {
    char versionInfo[128];
    snprintf(versionInfo, sizeof(versionInfo), "%s (build: %s)", MODULE_VERSION, BUILD_DATE);
    return env->NewStringUTF(versionInfo);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeDrmId(JNIEnv *env, jclass clazz, jbyteArray drmId) {
    if (drmId == nullptr) {
        LOGE("[JNI] nativeSetFakeDrmId: 参数为空");
        return JNI_FALSE;
    }
    jsize len = env->GetArrayLength(drmId);
    if (len != 32) {
        LOGE("[JNI] nativeSetFakeDrmId: DRM ID 长度错误 (期望 32, 实际 %d)", len);
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    env->GetByteArrayRegion(drmId, 0, 32, reinterpret_cast<jbyte*>(g_fakeDrmId));
    LOGE("[JNI] nativeSetFakeDrmId: DRM ID 已更新");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeAndroidId(JNIEnv *env, jclass clazz, jstring androidId) {
    if (androidId == nullptr) {
        LOGE("[JNI] nativeSetFakeAndroidId: 参数为空");
        return JNI_FALSE;
    }
    const char* idStr = env->GetStringUTFChars(androidId, nullptr);
    if (idStr == nullptr) {
        return JNI_FALSE;
    }
    size_t len = strlen(idStr);
    if (len != 16) {
        LOGE("[JNI] nativeSetFakeAndroidId: Android ID 长度错误 (期望 16, 实际 %zu)", len);
        env->ReleaseStringUTFChars(androidId, idStr);
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    strncpy(g_fakeAndroidId, idStr, sizeof(g_fakeAndroidId) - 1);
    g_fakeAndroidId[sizeof(g_fakeAndroidId) - 1] = '\0';
    env->ReleaseStringUTFChars(androidId, idStr);
    LOGE("[JNI] nativeSetFakeAndroidId: Android ID 已更新为 %s", g_fakeAndroidId);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeConfig(JNIEnv *env, jclass clazz, jstring configJson) {
    if (configJson == nullptr) {
        LOGE("[JNI] nativeSetFakeConfig: 参数为空");
        return JNI_FALSE;
    }
    const char* jsonStr = env->GetStringUTFChars(configJson, nullptr);
    if (jsonStr == nullptr) {
        return JNI_FALSE;
    }
    LOGE("[JNI] nativeSetFakeConfig: 收到配置 (长度=%zu)", strlen(jsonStr));
    // TODO: 解析 JSON 配置并应用
    // 当前仅记录日志，后续可扩展为完整的 JSON 解析
    lock_guard<mutex> lock(g_configMutex);
    g_configSynced = true;
    env->ReleaseStringUTFChars(configJson, jsonStr);
    LOGE("[JNI] nativeSetFakeConfig: 配置已同步");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeGetStatus(JNIEnv *env, jclass clazz) {
    lock_guard<mutex> lock(g_configMutex);
    char statusJson[512];
    char drmIdHex[65] = {0};
    for (int i = 0; i < 32; i++) {
        snprintf(drmIdHex + i * 2, 3, "%02X", g_fakeDrmId[i]);
    }
    snprintf(statusJson, sizeof(statusJson),
        "{\"version\":\"%s\",\"build\":\"%s\",\"logEnabled\":%s,"
        "\"configSynced\":%s,\"hookCount\":%d,\"drmHookCount\":%d,"
        "\"fakeDrmId\":\"%s\",\"fakeAndroidId\":\"%s\"}",
        MODULE_VERSION, BUILD_DATE,
        g_logEnabled ? "true" : "false",
        g_configSynced ? "true" : "false",
        g_hookCount, g_drmHookCount,
        drmIdHex, g_fakeAndroidId);
    return env->NewStringUTF(statusJson);
}

extern "C" JNIEXPORT void JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetLogEnabled(JNIEnv *env, jclass clazz, jboolean enable) {
    lock_guard<mutex> lock(g_configMutex);
    g_logEnabled = (enable == JNI_TRUE);
    LOGE("[JNI] nativeSetLogEnabled: 日志 %s", g_logEnabled ? "已启用" : "已禁用");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeWifiMac(JNIEnv *env, jclass clazz, jbyteArray macBytes) {
    if (macBytes == nullptr) {
        LOGE("[JNI] nativeSetFakeWifiMac: 参数为空");
        return JNI_FALSE;
    }
    jsize len = env->GetArrayLength(macBytes);
    if (len != 6) {
        LOGE("[JNI] nativeSetFakeWifiMac: MAC 地址长度错误 (期望 6, 实际 %d)", len);
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    env->GetByteArrayRegion(macBytes, 0, 6, reinterpret_cast<jbyte*>(g_fakeWifiMac));
    LOGE("[JNI] nativeSetFakeWifiMac: MAC 已更新为 %02X:%02X:%02X:%02X:%02X:%02X",
         g_fakeWifiMac[0], g_fakeWifiMac[1], g_fakeWifiMac[2],
         g_fakeWifiMac[3], g_fakeWifiMac[4], g_fakeWifiMac[5]);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeBootTime(JNIEnv *env, jclass clazz, jlong bootTime) {
    if (bootTime <= 0) {
        LOGE("[JNI] nativeSetFakeBootTime: 无效的开机时间 (%lld)", (long long)bootTime);
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    g_fakeBootTime = bootTime;
    LOGE("[JNI] nativeSetFakeBootTime: 开机时间已更新为 %lld", (long long)g_fakeBootTime);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeBluetoothMac(JNIEnv *env, jclass clazz, jstring bluetoothMac) {
    if (bluetoothMac == nullptr) {
        LOGE("[JNI] nativeSetFakeBluetoothMac: 参数为空");
        return JNI_FALSE;
    }
    const char* macStr = env->GetStringUTFChars(bluetoothMac, nullptr);
    if (macStr == nullptr) {
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    strncpy(g_fakeBluetoothMac, macStr, sizeof(g_fakeBluetoothMac) - 1);
    g_fakeBluetoothMac[sizeof(g_fakeBluetoothMac) - 1] = '\0';
    env->ReleaseStringUTFChars(bluetoothMac, macStr);
    LOGE("[JNI] nativeSetFakeBluetoothMac: 蓝牙 MAC 已更新为 %s", g_fakeBluetoothMac);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeSerialNo(JNIEnv *env, jclass clazz, jstring serialNo) {
    if (serialNo == nullptr) {
        LOGE("[JNI] nativeSetFakeSerialNo: 参数为空");
        return JNI_FALSE;
    }
    const char* serialStr = env->GetStringUTFChars(serialNo, nullptr);
    if (serialStr == nullptr) {
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    strncpy(g_fakeSerialNo, serialStr, sizeof(g_fakeSerialNo) - 1);
    g_fakeSerialNo[sizeof(g_fakeSerialNo) - 1] = '\0';
    env->ReleaseStringUTFChars(serialNo, serialStr);
    LOGE("[JNI] nativeSetFakeSerialNo: 序列号已更新为 %s", g_fakeSerialNo);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeBootId(JNIEnv *env, jclass clazz, jstring bootId) {
    if (bootId == nullptr) {
        LOGE("[JNI] nativeSetFakeBootId: 参数为空");
        return JNI_FALSE;
    }
    const char* bootIdStr = env->GetStringUTFChars(bootId, nullptr);
    if (bootIdStr == nullptr) {
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_configMutex);
    strncpy(g_fakeBootId, bootIdStr, sizeof(g_fakeBootId) - 1);
    g_fakeBootId[sizeof(g_fakeBootId) - 1] = '\0';
    env->ReleaseStringUTFChars(bootId, bootIdStr);
    LOGE("[JNI] nativeSetFakeBootId: boot_id 已更新为 %s", g_fakeBootId);
    return JNI_TRUE;
}

// ============================================================================
// 文件元信息伪造 JNI 方法
// ============================================================================

/**
 * 启用/禁用文件元信息伪造
 * @param enabled 是否启用
 * @return 成功返回 true
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeStatEnabled(JNIEnv *env, jclass clazz, jboolean enabled) {
    lock_guard<mutex> lock(g_fakeStatMutex);
    g_fakeStatEnabled = (enabled == JNI_TRUE);
    LOGE("[JNI] nativeSetFakeStatEnabled: 文件元信息伪造 %s", g_fakeStatEnabled ? "已启用" : "已禁用");
    return JNI_TRUE;
}

/**
 * 设置伪造的文件时间基准
 * @param timeBase Unix 时间戳（秒）
 * @return 成功返回 true
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeFileTimeBase(JNIEnv *env, jclass clazz, jlong timeBase) {
    if (timeBase <= 0) {
        LOGE("[JNI] nativeSetFakeFileTimeBase: 无效的时间基准 (%lld)", (long long)timeBase);
        return JNI_FALSE;
    }
    lock_guard<mutex> lock(g_fakeStatMutex);
    g_fakeFileTimeBase = timeBase;
    LOGE("[JNI] nativeSetFakeFileTimeBase: 文件时间基准已更新为 %lld", (long long)g_fakeFileTimeBase);
    return JNI_TRUE;
}

/**
 * 设置伪造的文件大小偏移
 * @param sizeOffset 文件大小偏移量
 * @return 成功返回 true
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeFileSizeOffset(JNIEnv *env, jclass clazz, jlong sizeOffset) {
    lock_guard<mutex> lock(g_fakeStatMutex);
    g_fakeFileSizeOffset = sizeOffset;
    LOGE("[JNI] nativeSetFakeFileSizeOffset: 文件大小偏移已更新为 %lld", (long long)g_fakeFileSizeOffset);
    return JNI_TRUE;
}

/**
 * 设置伪造的文件权限掩码
 * @param modeMask 权限掩码 (如 0644)
 * @return 成功返回 true
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetFakeFileModeMask(JNIEnv *env, jclass clazz, jint modeMask) {
    lock_guard<mutex> lock(g_fakeStatMutex);
    g_fakeFileModeMask = (mode_t)modeMask;
    LOGE("[JNI] nativeSetFakeFileModeMask: 文件权限掩码已更新为 %o", g_fakeFileModeMask);
    return JNI_TRUE;
}

/**
 * 添加需要伪造 stat 的路径前缀
 * @param pathPrefix 路径前缀 (如 "/data", "/storage")
 * @return 成功返回 true
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeAddFakeStatPath(JNIEnv *env, jclass clazz, jstring pathPrefix) {
    if (pathPrefix == nullptr) {
        LOGE("[JNI] nativeAddFakeStatPath: 参数为空");
        return JNI_FALSE;
    }
    const char* pathStr = env->GetStringUTFChars(pathPrefix, nullptr);
    if (pathStr == nullptr) {
        return JNI_FALSE;
    }

    lock_guard<mutex> lock(g_fakeStatMutex);
    if (g_fakeStatPathCount >= MAX_FAKE_STAT_PATHS) {
        LOGE("[JNI] nativeAddFakeStatPath: 路径列表已满 (最大 %d)", MAX_FAKE_STAT_PATHS);
        env->ReleaseStringUTFChars(pathPrefix, pathStr);
        return JNI_FALSE;
    }

    strncpy(g_fakeStatPaths[g_fakeStatPathCount], pathStr, 255);
    g_fakeStatPaths[g_fakeStatPathCount][255] = '\0';
    g_fakeStatPathCount++;

    env->ReleaseStringUTFChars(pathPrefix, pathStr);
    LOGE("[JNI] nativeAddFakeStatPath: 已添加路径前缀 '%s' (总数: %d)",
         g_fakeStatPaths[g_fakeStatPathCount - 1], g_fakeStatPathCount);
    return JNI_TRUE;
}

/**
 * 清除所有伪造 stat 的路径
 * @return 成功返回 true
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeClearFakeStatPaths(JNIEnv *env, jclass clazz) {
    lock_guard<mutex> lock(g_fakeStatMutex);
    g_fakeStatPathCount = 0;
    memset(g_fakeStatPaths, 0, sizeof(g_fakeStatPaths));
    LOGE("[JNI] nativeClearFakeStatPaths: 已清除所有伪造路径");
    return JNI_TRUE;
}

/**
 * 获取文件元信息伪造状态
 * @return JSON 格式的状态信息
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeGetFakeStatStatus(JNIEnv *env, jclass clazz) {
    lock_guard<mutex> lock(g_fakeStatMutex);
    char statusJson[1024];
    snprintf(statusJson, sizeof(statusJson),
        "{\"enabled\":%s,\"fileTimeBase\":%lld,\"fileSizeOffset\":%lld,"
        "\"fileModeMask\":%d,\"pathCount\":%d}",
        g_fakeStatEnabled ? "true" : "false",
        (long long)g_fakeFileTimeBase,
        (long long)g_fakeFileSizeOffset,
        (int)g_fakeFileModeMask,
        g_fakeStatPathCount);
    return env->NewStringUTF(statusJson);
}

// ============================================================================
// 传感器 Hook JNI 方法 (行为指纹防护)
// ============================================================================

/**
 * 启用/禁用传感器数据伪造
 * @param enabled 是否启用
 */
extern "C" JNIEXPORT void JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetSensorFakeEnabled(JNIEnv *env, jclass clazz, jboolean enabled) {
    SensorHook::setEnabled(enabled == JNI_TRUE);
}

/**
 * 设置传感器噪声级别
 * @param sensorType 传感器类型 (1=加速度计, 2=磁力计, 4=陀螺仪, 5=光感, 8=接近)
 * @param noiseLevel 噪声级别
 */
extern "C" JNIEXPORT void JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetSensorNoiseLevel(JNIEnv *env, jclass clazz, jint sensorType, jfloat noiseLevel) {
    SensorHook::setNoiseLevel(sensorType, noiseLevel);
}

// ============================================================================
// 命令执行监控 JNI 方法
// ============================================================================

/**
 * 启用/禁用命令执行监控
 * @param enabled 是否启用
 */
extern "C" JNIEXPORT void JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeSetCmdMonitorEnabled(JNIEnv *env, jclass clazz, jboolean enabled) {
    lock_guard<mutex> lock(g_configMutex);
    g_cmdMonitorEnabled = (enabled == JNI_TRUE);
    LOGE("[JNI] nativeSetCmdMonitorEnabled: 命令监控 %s", g_cmdMonitorEnabled ? "已启用" : "已禁用");
}

/**
 * 获取命令执行监控状态
 * @return 是否启用
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_deviceveil_guard_NativeHooks_nativeIsCmdMonitorEnabled(JNIEnv *env, jclass clazz) {
    lock_guard<mutex> lock(g_configMutex);
    return g_cmdMonitorEnabled ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// JNI 入口函数
// ============================================================================

/**
 * JNI 加载入口函数
 *
 * 当 Native 库被加载时，系统会自动调用此函数
 * 这是整个 Native Hook 系统的初始化入口点
 *
 * 初始化流程：
 * 1. 获取系统版本信息（SDK 版本、预览版本）
 * 2. 获取当前应用包名
 * 3. 初始化路径重定向系统
 * 4. 启动文件 I/O Hook 框架
 *
 * @param vm Java 虚拟机指针
 * @param reserved 保留参数（未使用）
 * @return JNI 版本号（JNI_VERSION_1_6）
 */
jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    // 保存 JavaVM 引用（用于后续回调 Java）
    g_javaVM = vm;

    LOGE("============================================================");
    LOGE("  Native Hook 模块加载成功");
    LOGE("  版本: %s", MODULE_VERSION);
    LOGE("  构建日期: %s", BUILD_DATE);
    LOGE("============================================================");

    // 获取 JNI 环境
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("[错误] ❌ 无法获取 JNI 环境");
        return JNI_ERR;
    }

    // 步骤 1: 获取 Android SDK 版本
    char sdkVersionString[16] = {0};
    int sdkVersion = getAndroidSdkVersion(sdkVersionString, sizeof(sdkVersionString));
    if (sdkVersion < 0) {
        LOGE("[错误] ❌ SDK 版本获取失败，初始化中止");
        return JNI_ERR;
    }

    // 步骤 2: 获取 Android 预览 SDK 版本（可选）
    char previewSdkVersionString[16] = {0};
    int previewSdkVersion = getAndroidPreviewSdkVersion(previewSdkVersionString, sizeof(previewSdkVersionString));

    // 步骤 3: 获取当前应用包名
    char packageName[64] = {0};
    if (!getCurrentPackageName(packageName, sizeof(packageName))) {
        LOGE("[错误] ❌ 包名获取失败，初始化中止");
        return JNI_ERR;
    }

    // 步骤 4: 初始化路径重定向系统 (包含 DRM Hook)
    // 注意: DRM Hook 在 IOReplace::startUniformer() -> startIOHook() -> hookAMediaDrm() 中初始化
    if (!initializePathRedirection(sdkVersion, previewSdkVersion, packageName)) {
        LOGE("[错误] ❌ 路径重定向系统初始化失败");
        return JNI_ERR;
    }

    // 步骤 5: 初始化传感器 Hook (行为指纹防护)
    SensorHook::init();

    // 步骤 6: 初始化 Keymaster Hook (Key Attestation 监控)
    // KeymasterHook::init();

    LOGE("============================================================");
    LOGE("  ✅ Native Hook 模块初始化完成");
    LOGE("============================================================");

    return JNI_VERSION_1_6;
}
