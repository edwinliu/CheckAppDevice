package com.deviceveil.guard;

import de.robv.android.xposed.XposedBridge;

/**
 * Native Hooks 管理类
 *
 * 功能：
 * 1. 加载 Native 库 (libcorner_monit.so)
 * 2. 提供 Java 与 Native 层的双向通信接口
 * 3. 同步伪造配置到 Native 层
 *
 * JNI 方法：
 * - getVersion(): 获取 Native 模块版本
 * - setFakeDrmId(): 设置伪造的 DRM ID
 * - setFakeAndroidId(): 设置伪造的 Android ID
 * - setFakeConfig(): 批量设置伪造配置
 * - getNativeStatus(): 获取 Native Hook 状态
 */
public class NativeHooks {
    private static final String TAG = "[设备信息记录]---[NativeHooks] ";
    private static boolean initialized = false;
    private static boolean libraryLoaded = false;

    // ============================================================================
    // Native 方法声明
    // ============================================================================

    /**
     * 获取 Native 模块版本号
     */
    public static native String nativeGetVersion();

    /**
     * 设置伪造的 DRM ID (32 字节)
     * @param drmId 32 字节的 DRM ID
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeDrmId(byte[] drmId);

    /**
     * 设置伪造的 Android ID
     * @param androidId 16 字符的 Android ID
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeAndroidId(String androidId);

    /**
     * 设置伪造的 WiFi MAC 地址
     * @param mac 6 字节的 MAC 地址
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeWifiMac(byte[] mac);

    /**
     * 批量设置伪造配置
     * @param configJson JSON 格式的配置字符串
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeConfig(String configJson);

    /**
     * 获取 Native Hook 状态
     * @return JSON 格式的状态信息
     */
    public static native String nativeGetStatus();

    /**
     * 启用/禁用 Native 日志
     * @param enable true 启用，false 禁用
     */
    public static native void nativeSetLogEnabled(boolean enable);

    /**
     * 设置伪造的开机时间
     * @param bootTime Unix 时间戳（秒）
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeBootTime(long bootTime);

    /**
     * 设置伪造的蓝牙 MAC 地址
     * @param bluetoothMac 蓝牙 MAC 地址字符串 (格式: XX:XX:XX:XX:XX:XX)
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeBluetoothMac(String bluetoothMac);

    /**
     * 设置伪造的设备序列号
     * @param serialNo 设备序列号字符串
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeSerialNo(String serialNo);

    /**
     * 设置伪造的 boot_id (UUID 格式)
     * @param bootId boot_id 字符串 (格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
     * @return 成功返回 true
     */
    public static native boolean nativeSetFakeBootId(String bootId);

    // ============================================================================
    // 文件元信息伪造 (stat/lstat/fstat) Native 方法声明
    // ============================================================================

    /**
     * 启用/禁用文件元信息伪造
     * @param enabled true 启用，false 禁用
     */
    public static native void nativeSetFakeStatEnabled(boolean enabled);

    /**
     * 设置伪造文件时间戳的基准时间
     * @param timeBase Unix 时间戳（秒），0 表示不伪造时间戳
     */
    public static native void nativeSetFakeFileTimeBase(long timeBase);

    /**
     * 设置伪造文件大小的偏移范围
     * @param sizeOffset 偏移量（字节），实际偏移将在 -offset ~ +offset 范围内随机
     */
    public static native void nativeSetFakeFileSizeOffset(long sizeOffset);

    /**
     * 设置伪造文件权限的掩码
     * @param modeMask 权限掩码（如 0755），0 表示不伪造权限
     */
    public static native void nativeSetFakeFileModeMask(int modeMask);

    /**
     * 添加需要伪造 stat 信息的路径前缀
     * @param pathPrefix 路径前缀（如 "/data"）
     * @return 成功返回 true，路径数量已满返回 false
     */
    public static native boolean nativeAddFakeStatPath(String pathPrefix);

    /**
     * 清空所有自定义的伪造 stat 路径
     */
    public static native void nativeClearFakeStatPaths();

    /**
     * 获取文件元信息伪造的状态
     * @return JSON 格式的状态信息
     */
    public static native String nativeGetFakeStatStatus();

    // ============================================================================
    // 传感器 Hook (行为指纹防护) Native 方法声明
    // ============================================================================

    /**
     * 启用/禁用传感器数据伪造
     * @param enabled true 启用，false 禁用
     */
    public static native void nativeSetSensorFakeEnabled(boolean enabled);

    /**
     * 设置传感器噪声级别
     * @param sensorType 传感器类型 (1=加速度计, 2=磁力计, 4=陀螺仪, 5=光感, 8=接近)
     * @param noiseLevel 噪声级别
     */
    public static native void nativeSetSensorNoiseLevel(int sensorType, float noiseLevel);

    // ============================================================================
    // 命令执行监控 Native 方法声明
    // ============================================================================

    /**
     * 启用/禁用命令执行监控
     * @param enabled true 启用，false 禁用
     */
    public static native void nativeSetCmdMonitorEnabled(boolean enabled);

    /**
     * 获取命令执行监控状态
     * @return 是否启用
     */
    public static native boolean nativeIsCmdMonitorEnabled();

    // ============================================================================
    // 初始化方法
    // ============================================================================

    public static void init() {
        if (initialized) {
            XposedBridge.log(TAG + "Already initialized, skipping...");
            return;
        }

        // 加载 Native 库
        if (!libraryLoaded) {
            try {
                System.loadLibrary("corner_monit");
                libraryLoaded = true;
                XposedBridge.log(TAG + "Native library (libcorner_monit.so) loaded successfully");
            } catch (UnsatisfiedLinkError e) {
                XposedBridge.log(TAG + "Failed to load native library: " + e.getMessage());
                return;
            }
        }

        // 同步伪造配置到 Native 层
        syncFakeConfigToNative();

        initialized = true;
        XposedBridge.log(TAG + "Native hooks initialized via JNI_OnLoad");

        // 打印 Native 模块信息
        try {
            String version = nativeGetVersion();
            String status = nativeGetStatus();
            XposedBridge.log(TAG + "Native version: " + version);
            XposedBridge.log(TAG + "Native status: " + status);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "JNI methods not yet registered: " + e.getMessage());
        }
    }

    /**
     * 同步伪造配置到 Native 层
     * 优先使用 HookInit 中从文件加载/生成的动态配置
     * 如果动态配置不可用，则回退到 FakeData 中的静态默认值
     */
    private static void syncFakeConfigToNative() {
        try {
            // 同步 DRM ID（优先使用动态配置）
            byte[] drmId = (HookInit.Drm_id != null) ? HookInit.Drm_id : FakeData.FAKE_DRM_ID;
            boolean drmResult = nativeSetFakeDrmId(drmId);
            XposedBridge.log(TAG + "Sync DRM ID to Native: " + (drmResult ? "success" : "failed") +
                    (HookInit.Drm_id != null ? " (from file)" : " (default)"));

            // 同步 Android ID（优先使用动态配置）
            String androidId = (HookInit.Android_id != null) ? HookInit.Android_id : FakeData.FAKE_ANDROID_ID;
            boolean androidIdResult = nativeSetFakeAndroidId(androidId);
            XposedBridge.log(TAG + "Sync Android ID to Native: " + (androidIdResult ? "success" : "failed") +
                    (HookInit.Android_id != null ? " (from file)" : " (default)"));

            // Android 15 普通应用看到的是受限 MAC，不同步随机真值形态的地址。
            byte[] wifiMac = FakeData.ANDROID15_NON_ROOT_PRIVACY_MODE
                    ? FakeData.RESTRICTED_MAC_BYTES
                    : ((HookInit.Wifi_mac != null) ? HookInit.Wifi_mac : FakeData.FAKE_NETWORK_MAC_BYTES);
            boolean macResult = nativeSetFakeWifiMac(wifiMac);
            XposedBridge.log(TAG + "Sync WiFi MAC to Native: " + (macResult ? "success" : "failed") +
                    (FakeData.ANDROID15_NON_ROOT_PRIVACY_MODE ? " (android15 restricted)" :
                            (HookInit.Wifi_mac != null ? " (from file)" : " (default)")));

            // 同步开机时间（使用动态配置）
            if (HookInit.FAKE_BOOT_TIME > 0) {
                boolean bootTimeResult = nativeSetFakeBootTime(HookInit.FAKE_BOOT_TIME);
                XposedBridge.log(TAG + "Sync Boot Time to Native: " + (bootTimeResult ? "success" : "failed") +
                        " (value: " + HookInit.FAKE_BOOT_TIME + ")");
            }

            // 同步蓝牙 MAC 地址（优先使用动态配置）
            String bluetoothMac = (HookInit.bluetooth_address != null) ? HookInit.bluetooth_address : FakeData.FAKE_BLUETOOTH_MAC;
            boolean btMacResult = nativeSetFakeBluetoothMac(bluetoothMac);
            XposedBridge.log(TAG + "Sync Bluetooth MAC to Native: " + (btMacResult ? "success" : "failed") +
                    " (value: " + bluetoothMac + ")" +
                    (HookInit.bluetooth_address != null ? " (from file)" : " (default)"));

            // 同步设备序列号。Android 15 非特权应用不应看到真实/完整硬件序列号。
            String serialNo = FakeData.RESTRICTED_BUILD_SERIAL;
            boolean serialResult = nativeSetFakeSerialNo(serialNo);
            XposedBridge.log(TAG + "Sync Serial No to Native: " + (serialResult ? "success" : "failed") +
                    " (value: " + serialNo + ")");

            // 同步 boot_id（优先使用动态配置）
            String bootId = (HookInit.Boot_id != null) ? HookInit.Boot_id : FakeData.FAKE_BOOT_ID;
            boolean bootIdResult = nativeSetFakeBootId(bootId);
            XposedBridge.log(TAG + "Sync Boot ID to Native: " + (bootIdResult ? "success" : "failed") +
                    " (value: " + bootId + ")" +
                    (HookInit.Boot_id != null ? " (from file)" : " (default)"));

            // 同步文件元信息伪造配置
            syncFakeStatConfigToNative();

        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to sync config (JNI not ready): " + e.getMessage());
        }
    }

    /**
     * 同步文件元信息伪造配置到 Native 层
     */
    private static void syncFakeStatConfigToNative() {
        try {
            // 启用文件元信息伪造
            nativeSetFakeStatEnabled(FakeData.FAKE_STAT_ENABLED);
            XposedBridge.log(TAG + "Sync Fake Stat Enabled: " + FakeData.FAKE_STAT_ENABLED);

            // 设置伪造时间基准 (使用伪造的开机时间为基础)
            long fakeTimeBase = FakeData.FAKE_FILE_TIME_BASE;
            if (fakeTimeBase <= 0 && HookInit.FAKE_BOOT_TIME > 0) {
                // 如果没有配置，使用开机时间作为基准
                fakeTimeBase = HookInit.FAKE_BOOT_TIME;
            }
            if (fakeTimeBase > 0) {
                nativeSetFakeFileTimeBase(fakeTimeBase);
                XposedBridge.log(TAG + "Sync Fake File Time Base: " + fakeTimeBase);
            }

            // 设置文件大小偏移范围
            if (FakeData.FAKE_FILE_SIZE_OFFSET > 0) {
                nativeSetFakeFileSizeOffset(FakeData.FAKE_FILE_SIZE_OFFSET);
                XposedBridge.log(TAG + "Sync Fake File Size Offset: " + FakeData.FAKE_FILE_SIZE_OFFSET);
            }

            // 设置文件权限掩码
            if (FakeData.FAKE_FILE_MODE_MASK > 0) {
                nativeSetFakeFileModeMask(FakeData.FAKE_FILE_MODE_MASK);
                XposedBridge.log(TAG + "Sync Fake File Mode Mask: " + Integer.toOctalString(FakeData.FAKE_FILE_MODE_MASK));
            }

            // 添加自定义伪造路径
            nativeClearFakeStatPaths();
            for (String path : FakeData.FAKE_STAT_PATHS) {
                boolean result = nativeAddFakeStatPath(path);
                XposedBridge.log(TAG + "Add Fake Stat Path '" + path + "': " + (result ? "success" : "failed"));
            }

            // 打印状态
            String statStatus = nativeGetFakeStatStatus();
            XposedBridge.log(TAG + "Fake Stat Status: " + statStatus);

        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to sync fake stat config: " + e.getMessage());
        }
    }

    // ============================================================================
    // 公共接口
    // ============================================================================

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    /**
     * 动态更新 DRM ID
     */
    public static boolean updateDrmId(byte[] newDrmId) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot update DRM ID: library not loaded");
            return false;
        }
        if (newDrmId == null || newDrmId.length != 32) {
            XposedBridge.log(TAG + "Invalid DRM ID: must be 32 bytes");
            return false;
        }
        try {
            return nativeSetFakeDrmId(newDrmId);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to update DRM ID: " + e.getMessage());
            return false;
        }
    }

    /**
     * 动态更新 Android ID
     */
    public static boolean updateAndroidId(String newAndroidId) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot update Android ID: library not loaded");
            return false;
        }
        if (newAndroidId == null || newAndroidId.length() != 16) {
            XposedBridge.log(TAG + "Invalid Android ID: must be 16 characters");
            return false;
        }
        try {
            return nativeSetFakeAndroidId(newAndroidId);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to update Android ID: " + e.getMessage());
            return false;
        }
    }

    /**
     * 动态更新 WiFi MAC 地址
     */
    public static boolean updateWifiMac(byte[] newMac) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot update WiFi MAC: library not loaded");
            return false;
        }
        if (newMac == null || newMac.length != 6) {
            XposedBridge.log(TAG + "Invalid WiFi MAC: must be 6 bytes");
            return false;
        }
        try {
            return nativeSetFakeWifiMac(newMac);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to update WiFi MAC: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置 Native 日志开关
     */
    public static void setNativeLogEnabled(boolean enabled) {
        if (!libraryLoaded) {
            return;
        }
        try {
            nativeSetLogEnabled(enabled);
            XposedBridge.log(TAG + "Native log " + (enabled ? "enabled" : "disabled"));
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set log state: " + e.getMessage());
        }
    }

    // ============================================================================
    // 文件元信息伪造公共接口
    // ============================================================================

    /**
     * 启用/禁用文件元信息伪造
     * @param enabled true 启用，false 禁用
     */
    public static void setFakeStatEnabled(boolean enabled) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set fake stat: library not loaded");
            return;
        }
        try {
            nativeSetFakeStatEnabled(enabled);
            XposedBridge.log(TAG + "Fake stat " + (enabled ? "enabled" : "disabled"));
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set fake stat state: " + e.getMessage());
        }
    }

    /**
     * 设置伪造文件时间戳的基准时间
     * @param timeBase Unix 时间戳（秒），0 表示不伪造时间戳
     */
    public static void setFakeFileTimeBase(long timeBase) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set fake file time: library not loaded");
            return;
        }
        try {
            nativeSetFakeFileTimeBase(timeBase);
            XposedBridge.log(TAG + "Fake file time base set to: " + timeBase);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set fake file time: " + e.getMessage());
        }
    }

    /**
     * 设置伪造文件大小的偏移范围
     * @param sizeOffset 偏移量（字节），实际偏移将在 -offset ~ +offset 范围内随机
     */
    public static void setFakeFileSizeOffset(long sizeOffset) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set fake file size: library not loaded");
            return;
        }
        try {
            nativeSetFakeFileSizeOffset(sizeOffset);
            XposedBridge.log(TAG + "Fake file size offset set to: " + sizeOffset);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set fake file size: " + e.getMessage());
        }
    }

    /**
     * 设置伪造文件权限的掩码
     * @param modeMask 权限掩码（如 0755），0 表示不伪造权限
     */
    public static void setFakeFileModeMask(int modeMask) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set fake file mode: library not loaded");
            return;
        }
        try {
            nativeSetFakeFileModeMask(modeMask);
            XposedBridge.log(TAG + "Fake file mode mask set to: " + Integer.toOctalString(modeMask));
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set fake file mode: " + e.getMessage());
        }
    }

    /**
     * 添加需要伪造 stat 信息的路径前缀
     * @param pathPrefix 路径前缀（如 "/data"）
     * @return 成功返回 true，路径数量已满返回 false
     */
    public static boolean addFakeStatPath(String pathPrefix) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot add fake stat path: library not loaded");
            return false;
        }
        if (pathPrefix == null || pathPrefix.isEmpty()) {
            XposedBridge.log(TAG + "Invalid path prefix");
            return false;
        }
        try {
            boolean result = nativeAddFakeStatPath(pathPrefix);
            XposedBridge.log(TAG + "Add fake stat path '" + pathPrefix + "': " + (result ? "success" : "failed"));
            return result;
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to add fake stat path: " + e.getMessage());
            return false;
        }
    }

    /**
     * 清空所有自定义的伪造 stat 路径
     */
    public static void clearFakeStatPaths() {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot clear fake stat paths: library not loaded");
            return;
        }
        try {
            nativeClearFakeStatPaths();
            XposedBridge.log(TAG + "Fake stat paths cleared");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to clear fake stat paths: " + e.getMessage());
        }
    }

    /**
     * 获取文件元信息伪造的状态
     * @return JSON 格式的状态信息，失败返回 null
     */
    public static String getFakeStatStatus() {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot get fake stat status: library not loaded");
            return null;
        }
        try {
            return nativeGetFakeStatStatus();
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to get fake stat status: " + e.getMessage());
            return null;
        }
    }

    // ============================================================================
    // 传感器 Hook (行为指纹防护) 公共接口
    // ============================================================================

    /**
     * 启用/禁用传感器数据伪造
     * @param enabled true 启用，false 禁用
     */
    public static void setSensorFakeEnabled(boolean enabled) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set sensor fake: library not loaded");
            return;
        }
        try {
            nativeSetSensorFakeEnabled(enabled);
            XposedBridge.log(TAG + "Sensor fake " + (enabled ? "enabled" : "disabled"));
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set sensor fake state: " + e.getMessage());
        }
    }

    /**
     * 设置传感器噪声级别
     * @param sensorType 传感器类型 (1=加速度计, 2=磁力计, 4=陀螺仪, 5=光感, 8=接近)
     * @param noiseLevel 噪声级别
     */
    public static void setSensorNoiseLevel(int sensorType, float noiseLevel) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set sensor noise: library not loaded");
            return;
        }
        try {
            nativeSetSensorNoiseLevel(sensorType, noiseLevel);
            XposedBridge.log(TAG + "Sensor type " + sensorType + " noise level set to: " + noiseLevel);
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set sensor noise level: " + e.getMessage());
        }
    }

    // ============================================================================
    // 命令执行监控公共接口
    // ============================================================================

    /**
     * 启用/禁用命令执行监控
     * @param enabled true 启用，false 禁用
     */
    public static void setCmdMonitorEnabled(boolean enabled) {
        if (!libraryLoaded) {
            XposedBridge.log(TAG + "Cannot set cmd monitor: library not loaded");
            return;
        }
        try {
            nativeSetCmdMonitorEnabled(enabled);
            XposedBridge.log(TAG + "Command monitor " + (enabled ? "enabled" : "disabled"));
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to set cmd monitor state: " + e.getMessage());
        }
    }

    /**
     * 获取命令执行监控状态
     * @return 是否启用，库未加载时返回 false
     */
    public static boolean isCmdMonitorEnabled() {
        if (!libraryLoaded) {
            return false;
        }
        try {
            return nativeIsCmdMonitorEnabled();
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log(TAG + "Failed to get cmd monitor state: " + e.getMessage());
            return false;
        }
    }
}
