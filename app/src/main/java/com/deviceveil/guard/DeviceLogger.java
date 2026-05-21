/**
 * ============================================================================
 * 文件名：DeviceLogger.java
 * 功能：设备信息监控模块
 *
 * 主要功能：
 * 1. 监控应用对设备标识符的访问（Android ID、IMEI、IMSI 等）
 * 2. 监控网络信息访问（WiFi MAC、SSID、BSSID、蓝牙地址等）
 * 3. 监控 SIM 卡和运营商信息访问
 * 4. 监控传感器和显示信息访问
 * 5. 监控广告 ID 访问
 * 6. 检测并隐藏已安装的敏感应用包
 * 7. 监控调试器连接状态
 * 8. 监控系统属性查询（Bootloader、SELinux 状态等）
 *
 * 作者：DeviceVeil Team
 * 版本：4.10
 * 日期：2024-12-06
 * ============================================================================
 */
package com.deviceveil.guard;

import static com.deviceveil.guard.HookInit.Android_id;
import static com.deviceveil.guard.HookInit.bluetooth_address;

import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 设备信息监控器
 *
 * 该类负责在 Java 层拦截所有与设备信息相关的 API 调用，
 * 记录应用尝试获取的设备信息，并可选择性地修改返回值。
 */
public class DeviceLogger {
    // ============================================================================
    // 常量定义
    // ============================================================================

    /** 日志标签，用于在 Xposed 日志中标识本模块的输出 */
    private static final String TAG = "[设备信息记录]---[DeviceLogger] ";

    // ============================================================================
    // 主初始化方法
    // ============================================================================

    /**
     * 初始化所有设备信息监控 Hook
     *
     * 该方法是设备信息监控模块的入口点，负责：
     * 1. 检测 Android 版本并记录系统信息
     * 2. 打印完整的设备指纹信息
     * 3. 初始化所有监控 Hook（网络、蓝牙、SIM 卡、传感器等）
     *
     * @param lpparam Xposed 加载包参数，包含目标应用的 ClassLoader
     */
    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        // 检测 Android 版本（Android 15 引入了新的隐私限制）
        boolean isAndroid15OrHigher = Build.VERSION.SDK_INT >= Constants.ANDROID_15_SDK;
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "设备信息监控模块初始化");
        XposedBridge.log(TAG + "Android 版本: " + Build.VERSION.SDK_INT +
                         " (Android 15+: " + isAndroid15OrHigher + ")");
        XposedBridge.log(TAG + "目标应用: " + lpparam.packageName);
        XposedBridge.log(TAG + "========================================");

        // 步骤 1: 检测模拟器特征
        String fingerprint = Build.FINGERPRINT;
        String model = Build.MODEL;
        boolean possibleEmulator = checkEmulatorIndicators(fingerprint, model, Build.HARDWARE, Build.BRAND);
        if (possibleEmulator) {
            XposedBridge.log(TAG + "⚠️ 检测到可能的模拟器环境");
        }

        // 步骤 2: 初始化网络相关监控
        XposedBridge.log(TAG + "[初始化] 网络信息监控模块...");
        hookWifiManager(lpparam);           // WiFi MAC、SSID、BSSID
        hookNetworkInterface(lpparam);      // 网络接口 MAC 地址

        // 步骤 3: 初始化蓝牙监控
        XposedBridge.log(TAG + "[初始化] 蓝牙信息监控模块...");
        hookBluetoothAdapter(lpparam);      // 蓝牙 MAC 地址和设备名称

        // 步骤 4: 初始化电话和 SIM 卡监控
        XposedBridge.log(TAG + "[初始化] 电话和 SIM 卡信息监控模块...");
        hookTelephonyManager(lpparam);      // IMEI、MEID
        hookTelephonyManagerExtended(lpparam); // ICCID、IMSI、手机号码、运营商

        // 步骤 5: 初始化广告 ID 监控
        XposedBridge.log(TAG + "[初始化] 广告 ID 监控模块...");
        hookAdvertisingId(lpparam);         // Google 广告 ID (GAID)

        // 步骤 6: 初始化传感器监控
        XposedBridge.log(TAG + "[初始化] 传感器信息监控模块...");
        hookSensorManager(lpparam);         // 传感器列表

        // 步骤 7: 初始化显示信息监控
        XposedBridge.log(TAG + "[初始化] 显示信息监控模块...");
        hookDisplayMetrics(lpparam);        // 屏幕分辨率、DPI

        // 步骤 8: 初始化系统设置和包管理监控
        XposedBridge.log(TAG + "[初始化] 系统设置和包管理监控模块...");
        hookSettingsSecureGetString(lpparam);   // Android ID、蓝牙地址
        hookContentResolverQuery(lpparam);      // ContentProvider 直接查询 Android ID
        hookGetInstalledPackages(lpparam);      // 已安装应用列表（隐藏敏感包）
        hookDebug_isDebuggerConnected(lpparam); // 调试器连接状态
        hookSystemPropertiesBootloader(lpparam); // Bootloader 和 SELinux 状态

        // 步骤 9: 打印完整设备指纹信息（用于分析）
        XposedBridge.log(TAG + "[设备指纹] 开始收集完整设备信息...");
        String buildInfo =
                "【完整设备指纹-2024版】\n" +
                        "  MODEL=" + Build.MODEL + " (设备型号)\n" +
                        "  MANUFACTURER=" + Build.MANUFACTURER + " (制造商)\n" +
                        "  BRAND=" + Build.BRAND + " (品牌)\n" +
                        "  DEVICE=" + Build.DEVICE + " (设备代号)\n" +
                        "  PRODUCT=" + Build.PRODUCT + " (产品名称)\n" +
                        "  BOARD=" + Build.BOARD + " (主板)\n" +
                        "  HARDWARE=" + Build.HARDWARE + " (硬件名称)\n" +
                        "  FINGERPRINT=" + Build.FINGERPRINT + " (构建指纹)\n" +
                        "  DISPLAY=" + Build.DISPLAY + " (显示 ID)\n" +
                        "  ID=" + Build.ID + " (构建 ID)\n" +
                        "  BOOTLOADER=" + Build.BOOTLOADER + " (Bootloader 版本)\n" +
                        "  TIME=" + new Date(Build.TIME) + " (" + Build.TIME + ") (构建时间)\n" +
                        "  TYPE=" + Build.TYPE + " (构建类型)\n" +
                        "  TAGS=" + Build.TAGS + " (构建标签)\n" +
                        "  USER=" + Build.USER + " (构建用户)\n" +
                        "  HOST=" + Build.HOST + " (构建主机)\n" +
                        "  INCREMENTAL=" + Build.VERSION.INCREMENTAL + " (增量版本)\n" +
                        "  SDK_INT=" + Build.VERSION.SDK_INT + " (SDK 版本)\n" +
                        "  RELEASE=" + Build.VERSION.RELEASE + " (Android 版本)\n" +
                        "  CODENAME=" + Build.VERSION.CODENAME + " (代号)\n" +
                        "  SECURITY_PATCH=" + Build.VERSION.SECURITY_PATCH + " (安全补丁)\n" +
                        "  SUPPORTED_ABIS=" + Arrays.toString(Build.SUPPORTED_ABIS) + " (支持的 ABI)\n" +
                        "  SUPPORTED_32_BIT_ABIS=" + Arrays.toString(Build.SUPPORTED_32_BIT_ABIS) + " (32位 ABI)\n" +
                        "  SUPPORTED_64_BIT_ABIS=" + Arrays.toString(Build.SUPPORTED_64_BIT_ABIS) + " (64位 ABI)\n" +
                        "  BASE_OS=" + Build.VERSION.BASE_OS + " (基础系统)\n" +
                        "  PREVIEW_SDK_INT=" + Build.VERSION.PREVIEW_SDK_INT + " (预览 SDK)\n";

        XposedBridge.log(TAG + buildInfo);
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "✅ 设备信息监控模块初始化完成");
        XposedBridge.log(TAG + "========================================");
    }

    // ============================================================================
    // 辅助方法
    // ============================================================================

    /**
     * 检查设备是否为模拟器
     *
     * 通过检查设备指纹、型号、硬件名称和品牌中是否包含模拟器特征关键词
     * 来判断当前设备是否为模拟器环境。
     *
     * @param fingerprint 设备指纹
     * @param model 设备型号
     * @param hardware 硬件名称
     * @param brand 品牌名称
     * @return 如果检测到模拟器特征返回 true，否则返回 false
     */
    private static boolean checkEmulatorIndicators(String fingerprint, String model, String hardware, String brand) {
        String lower = (fingerprint + model + hardware + brand).toLowerCase();
        for (String indicator : Constants.EMULATOR_INDICATORS) {
            if (lower.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    private static void hookSettingsSecureGetString(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.provider.Settings.Secure",
                    lpparam.classLoader,
                    "getString",
                    android.content.ContentResolver.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[1];
                            if ("android_id".equals(key)) {
                                MonitorReporter.api("Settings.Secure.getString", "android_id");
                                String fakeAndroidId = Android_id != null ? Android_id : FakeData.FAKE_ANDROID_ID;
                                param.setResult(fakeAndroidId);

                            } else if ("bluetooth_address".equals(key)) {
                                MonitorReporter.api("Settings.Secure.getString", "bluetooth_address");
                                if (bluetooth_address != null) {
                                    param.setResult(bluetooth_address);
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Settings.Secure.getString 失败: " + t.toString());
        }

        // 补充：Hook getStringForUser (多用户场景)
        try {
            XposedHelpers.findAndHookMethod(
                    "android.provider.Settings$Secure",
                    lpparam.classLoader,
                    "getStringForUser",
                    android.content.ContentResolver.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[1];
                            int userId = (int) param.args[2];
                            if ("android_id".equals(key)) {
                                MonitorReporter.api("Settings.Secure.getStringForUser", "android_id user=" + userId);
                                param.setResult(Android_id != null ? Android_id : FakeData.FAKE_ANDROID_ID);
                            } else if ("bluetooth_address".equals(key)) {
                                MonitorReporter.api("Settings.Secure.getStringForUser", "bluetooth_address user=" + userId);
                                if (bluetooth_address != null) {
                                    param.setResult(bluetooth_address);
                                }
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook Settings.Secure.getStringForUser 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Settings.Secure.getStringForUser 失败: " + t.toString());
        }
    }

    // ========== ContentResolver.query() Hook - 拦截直接查询 Settings ContentProvider ==========
    private static void hookContentResolverQuery(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.content.ContentResolver",
                    lpparam.classLoader,
                    "query",
                    Uri.class,
                    String[].class,
                    String.class,
                    String[].class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Uri uri = (Uri) param.args[0];
                            if (uri == null) return;

                            String uriStr = uri.toString();
                            // 检查是否查询 settings/secure 中的 android_id
                            if (uriStr.contains("settings/secure") && uriStr.contains("android_id")) {
                                MonitorReporter.api("ContentResolver.query", uriStr);
                                XposedBridge.log(TAG + "【ContentResolver.query】检测到直接查询 android_id: " + uriStr);

                                Cursor originalCursor = (Cursor) param.getResult();
                                if (originalCursor != null) {
                                    // 创建伪造的 Cursor 返回伪造的 Android ID
                                    MatrixCursor fakeCursor = new MatrixCursor(new String[]{"value"});
                                    fakeCursor.addRow(new Object[]{Android_id});
                                    param.setResult(fakeCursor);
                                    XposedBridge.log(TAG + "【ContentResolver.query】已替换 android_id 为: " + Android_id);
                                }
                            } else if (uriStr.contains("settings/secure") && uriStr.contains("bluetooth_address")) {
                                MonitorReporter.api("ContentResolver.query", uriStr);
                                XposedBridge.log(TAG + "【ContentResolver.query】检测到直接查询 bluetooth_address: " + uriStr);

                                Cursor originalCursor = (Cursor) param.getResult();
                                if (originalCursor != null) {
                                    MatrixCursor fakeCursor = new MatrixCursor(new String[]{"value"});
                                    fakeCursor.addRow(new Object[]{bluetooth_address});
                                    param.setResult(fakeCursor);
                                    XposedBridge.log(TAG + "【ContentResolver.query】已替换 bluetooth_address 为: " + bluetooth_address);
                                }
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook ContentResolver.query 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook ContentResolver.query 失败: " + t.toString());
        }
    }


    private static void hookGetInstalledPackages(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // ❌ 错误：Hook 抽象类 PackageManager
            // Class<?> pmClass = XposedHelpers.findClass("android.content.pm.PackageManager", classLoader);

            // ✅ 正确：Hook 具体实现类 ApplicationPackageManager
            Class<?> appPmClass = XposedHelpers.findClass(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    appPmClass,
                    "getInstalledPackages",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                @SuppressWarnings("unchecked")
                                List<PackageInfo> list = (List<PackageInfo>) param.getResult();
                                if (list == null) return;

                                List<PackageInfo> filtered = new ArrayList<>();
                                for (PackageInfo pkg : list) {
                                    if (pkg != null && pkg.packageName != null) {
                                        boolean hide = false;
                                        for (String hidden : Constants.HIDDEN_PACKAGES) {
                                            if (pkg.packageName.equals(hidden)) {
                                                hide = true;
                                                break;
                                            }
                                        }
                                        if (!hide) {
                                            filtered.add(pkg);
                                        }
                                    }
                                }
                                param.setResult(filtered);
                                XposedBridge.log("[+] getInstalledPackages 过滤完成: " + list.size() + " → " + filtered.size());
                            } catch (Throwable t) {
                                XposedBridge.log("[-] 过滤异常: " + t.toString());
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("[-] Hook ApplicationPackageManager 失败: " + t.toString());
        }
    }


    private static void hookDebug_isDebuggerConnected(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.Debug",
                    lpparam.classLoader,
                    "isDebuggerConnected",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            boolean isConnected = (boolean) param.getResult();
                            if (isConnected) {
                                XposedBridge.log(TAG + "⚠️ 检测到调试器已连接！");
                            } else {
                                XposedBridge.log(TAG + "调试器检查: 未连接");
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Debug.isDebuggerConnected 失败: " + t.toString());
        }
    }

    // ========== Bootloader/SELinux Hook ==========
    private static void hookSystemPropertiesBootloader(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.SystemProperties",
                    lpparam.classLoader,
                    "get",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if ("ro.boot.verifiedbootstate".equals(key) || "ro.boot.veritymode".equals(key) || "ro.build.selinux".equals(key)) {
                                MonitorReporter.property("SystemProperties.get", key);
                                XposedBridge.log(TAG + "【Bootloader/SELinux 查询】get(\"" + key + "\") - 检查解锁/模式 (A15 兼容)");
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            String value = (String) param.getResult();
                            if ("ro.boot.verifiedbootstate".equals(key)) {
                                XposedBridge.log(TAG + "【Bootloader 状态】" + key + " = " + value + " (orange=解锁, green=锁定)");
                            } else if ("ro.build.selinux".equals(key)) {
                                XposedBridge.log(TAG + "【SELinux 状态】" + key + " = " + value + " (enforcing=严格, permissive=宽松)");
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook SystemProperties Bootloader 失败: " + t.toString());
        }
    }

    // ========== TelephonyManager Hook ==========
    private static void hookTelephonyManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> telephonyClass = XposedHelpers.findClass("android.telephony.TelephonyManager", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getImei",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String imei = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getImei", "IMEI");
                            XposedBridge.log(TAG + "【IMEI 被读取】值为: \"" + (imei != null ? "***" + imei.substring(imei.length() - 4) : "null") + "\" (A15 隐私掩码)");
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getMeid",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String meid = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getMeid", "MEID");
                            XposedBridge.log(TAG + "【MEID 被读取】值为: \"" + (meid != null ? "***" + meid.substring(meid.length() - 4) : "null") + "\"");
                        }
                    });
            XposedBridge.log(TAG + "Hook TelephonyManager 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook TelephonyManager 失败: " + t.toString() + " (A15: 需 READ_PHONE_STATE)");
        }
    }

    // ========== 新增：WiFi Manager Hook ==========
    private static void hookWifiManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> wifiInfoClass = XposedHelpers.findClass("android.net.wifi.WifiInfo", lpparam.classLoader);

            // Hook getMacAddress()
            XposedHelpers.findAndHookMethod(
                    wifiInfoClass,
                    "getMacAddress",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String mac = (String) param.getResult();
                            MonitorReporter.api("WifiInfo.getMacAddress", String.valueOf(mac));
                            XposedBridge.log(TAG + "【WiFi MAC 地址被读取】值为: \"" + (mac != null ? mac : "null") + "\"");
                        }
                    });

            // Hook getSSID()
            XposedHelpers.findAndHookMethod(
                    wifiInfoClass,
                    "getSSID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String ssid = (String) param.getResult();
                            MonitorReporter.api("WifiInfo.getSSID", String.valueOf(ssid));
                            XposedBridge.log(TAG + "【WiFi SSID 被读取】值为: \"" + (ssid != null ? ssid : "null") + "\"");
                        }
                    });

            // Hook getBSSID()
            XposedHelpers.findAndHookMethod(
                    wifiInfoClass,
                    "getBSSID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String bssid = (String) param.getResult();
                            MonitorReporter.api("WifiInfo.getBSSID", String.valueOf(bssid));
                            XposedBridge.log(TAG + "【WiFi BSSID 被读取】值为: \"" + (bssid != null ? bssid : "null") + "\"");
                        }
                    });

            XposedBridge.log(TAG + "Hook WifiInfo 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook WifiInfo 失败: " + t.toString());
        }
    }

    // ========== 新增：NetworkInterface Hook ==========
    private static void hookNetworkInterface(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> networkInterfaceClass = XposedHelpers.findClass("java.net.NetworkInterface", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    networkInterfaceClass,
                    "getHardwareAddress",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            byte[] mac = (byte[]) param.getResult();
                            MonitorReporter.api("NetworkInterface.getHardwareAddress", mac == null ? "null" : "byte[" + mac.length + "]");
                            if (mac != null) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < mac.length; i++) {
                                    sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                                }
                                XposedBridge.log(TAG + "【网络接口 MAC 地址被读取】值为: \"" + sb.toString() + "\"");
                            }
                        }
                    });

            XposedBridge.log(TAG + "Hook NetworkInterface 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook NetworkInterface 失败: " + t.toString());
        }
    }

    // ========== 新增：BluetoothAdapter Hook ==========
    private static void hookBluetoothAdapter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> bluetoothAdapterClass = XposedHelpers.findClass("android.bluetooth.BluetoothAdapter", lpparam.classLoader);

            // Hook getAddress()
            XposedHelpers.findAndHookMethod(
                    bluetoothAdapterClass,
                    "getAddress",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String address = (String) param.getResult();
                            MonitorReporter.api("BluetoothAdapter.getAddress", String.valueOf(address));
                            XposedBridge.log(TAG + "【蓝牙 MAC 地址被读取】值为: \"" + (address != null ? address : "null") + "\"");
                        }
                    });

            // Hook getName()
            XposedHelpers.findAndHookMethod(
                    bluetoothAdapterClass,
                    "getName",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = (String) param.getResult();
                            MonitorReporter.api("BluetoothAdapter.getName", String.valueOf(name));
                            XposedBridge.log(TAG + "【蓝牙设备名称被读取】值为: \"" + (name != null ? name : "null") + "\"");
                        }
                    });

            XposedBridge.log(TAG + "Hook BluetoothAdapter 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook BluetoothAdapter 失败: " + t.toString());
        }
    }

    // ========== 新增：TelephonyManager 扩展 Hook (SIM 卡信息) ==========
    private static void hookTelephonyManagerExtended(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> telephonyClass = XposedHelpers.findClass("android.telephony.TelephonyManager", lpparam.classLoader);

            // Hook getSimSerialNumber() - SIM 卡序列号 (ICCID)
            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getSimSerialNumber",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String iccid = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getSimSerialNumber", "ICCID");
                            XposedBridge.log(TAG + "【SIM 卡序列号 (ICCID) 被读取】值为: \"" + (iccid != null ? "***" + iccid.substring(Math.max(0, iccid.length() - 4)) : "null") + "\"");
                        }
                    });

            // Hook getSubscriberId() - IMSI
            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getSubscriberId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String imsi = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getSubscriberId", "IMSI");
                            XposedBridge.log(TAG + "【IMSI 被读取】值为: \"" + (imsi != null ? "***" + imsi.substring(Math.max(0, imsi.length() - 4)) : "null") + "\"");
                        }
                    });

            // Hook getLine1Number() - 手机号码
            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getLine1Number",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String phoneNumber = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getLine1Number", "phone");
                            XposedBridge.log(TAG + "【手机号码被读取】值为: \"" + (phoneNumber != null ? "***" + phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)) : "null") + "\"");
                        }
                    });

            // Hook getNetworkOperator() - 运营商代码
            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getNetworkOperator",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String operator = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getNetworkOperator", String.valueOf(operator));
                            XposedBridge.log(TAG + "【运营商代码被读取】值为: \"" + (operator != null ? operator : "null") + "\"");
                        }
                    });

            // Hook getSimOperator() - SIM 卡运营商
            XposedHelpers.findAndHookMethod(
                    telephonyClass,
                    "getSimOperator",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String simOperator = (String) param.getResult();
                            MonitorReporter.api("TelephonyManager.getSimOperator", String.valueOf(simOperator));
                            XposedBridge.log(TAG + "【SIM 卡运营商被读取】值为: \"" + (simOperator != null ? simOperator : "null") + "\"");
                        }
                    });

            XposedBridge.log(TAG + "Hook TelephonyManager Extended 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook TelephonyManager Extended 失败: " + t.toString());
        }
    }

    // ========== 新增：广告 ID Hook ==========
    private static void hookAdvertisingId(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Google Play Services 广告 ID
            Class<?> advertisingIdClientClass = XposedHelpers.findClass(
                    "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    advertisingIdClientClass,
                    "getId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String gaid = (String) param.getResult();
                            MonitorReporter.api("AdvertisingIdClient.Info.getId", "GAID");
                            XposedBridge.log(TAG + "【Google 广告 ID (GAID) 被读取】值为: \"" + (gaid != null ? gaid : "null") + "\"");
                        }
                    });

            XposedBridge.log(TAG + "Hook AdvertisingId 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook AdvertisingId 失败 (可能未安装 Google Play Services): " + t.toString());
        }
    }

    // ========== 新增：传感器 Hook ==========
    private static void hookSensorManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> sensorManagerClass = XposedHelpers.findClass("android.hardware.SensorManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    sensorManagerClass,
                    "getSensorList",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int type = (int) param.args[0];
                            List<?> sensors = (List<?>) param.getResult();
                            MonitorReporter.api("SensorManager.getSensorList", "type=" + type + ", count=" + (sensors == null ? 0 : sensors.size()));
                            if (sensors != null && !sensors.isEmpty()) {
                                XposedBridge.log(TAG + "【传感器列表被读取】类型: " + type + ", 数量: " + sensors.size());
                            }
                        }
                    });

            XposedBridge.log(TAG + "Hook SensorManager 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook SensorManager 失败: " + t.toString());
        }
    }

    // ========== 新增：显示信息 Hook ==========
    private static void hookDisplayMetrics(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> displayClass = XposedHelpers.findClass("android.view.Display", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    displayClass,
                    "getMetrics",
                    android.util.DisplayMetrics.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            android.util.DisplayMetrics metrics = (android.util.DisplayMetrics) param.args[0];
                            MonitorReporter.api("Display.getMetrics", metrics.widthPixels + "x" + metrics.heightPixels + " dpi=" + metrics.densityDpi);
                            XposedBridge.log(TAG + "【显示信息被读取】分辨率: " + metrics.widthPixels + "x" + metrics.heightPixels +
                                    ", DPI: " + metrics.densityDpi + ", Density: " + metrics.density);
                        }
                    });

            XposedBridge.log(TAG + "Hook Display 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Display 失败: " + t.toString());
        }
    }
}
