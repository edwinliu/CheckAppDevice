/**
 * ============================================================================
 * 文件名：BinderHook.java
 * 功能：Binder IPC 通信监控模块
 *
 * 主要功能：
 * 1. Hook BinderProxy.transact() 方法，监控所有 IPC 通信
 * 2. 过滤与设备信息相关的系统服务调用
 * 3. 记录 IPC 调用的接口描述符和事务代码
 *
 * 技术说明：
 * - BinderProxy.transact() 是 Java 层 Binder IPC 的最后一个方法
 * - 所有跨进程调用（TelephonyManager、WifiManager 等）最终都会经过此方法
 * - 通过 getInterfaceDescriptor() 可以识别目标系统服务
 *
 * 作者：DeviceVeil Team
 * 版本：4.10
 * 日期：2024-12-06
 * ============================================================================
 */
package com.deviceveil.guard;

import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Binder IPC 通信监控器
 *
 * 该类负责在 Java 层拦截所有 Binder IPC 通信，
 * 重点监控与设备信息获取相关的系统服务调用。
 */
public class BinderHook {
    private static final String TAG = "[设备信息记录]---[BinderHook] ";

    // 需要监控的系统服务接口描述符
    private static final Set<String> MONITORED_INTERFACES = new HashSet<>();

    // Android 版本常量
    private static final int ANDROID_10 = 29;
    private static final int ANDROID_11 = 30;
    private static final int ANDROID_12 = 31;
    private static final int ANDROID_13 = 33;
    private static final int ANDROID_14 = 34;
    private static final int ANDROID_15 = 35;

    // 当前 Android 版本
    private static final int SDK_INT = Build.VERSION.SDK_INT;

    // 动态事务码缓存：接口名 -> (方法名 -> 事务码)
    private static final ConcurrentHashMap<String, Map<String, Integer>> transactionCodeCache = new ConcurrentHashMap<>();

    // 反向映射缓存：接口名 -> (事务码 -> 方法名)
    private static final ConcurrentHashMap<String, Map<Integer, String>> reverseCodeCache = new ConcurrentHashMap<>();

    static {
        // 电话服务 - IMEI、IMSI、手机号等
        MONITORED_INTERFACES.add("com.android.internal.telephony.ITelephony");
        MONITORED_INTERFACES.add("com.android.internal.telephony.IPhoneSubInfo");
        MONITORED_INTERFACES.add("com.android.internal.telephony.ISub");

        // WiFi 服务 - MAC 地址、SSID 等
        MONITORED_INTERFACES.add("android.net.wifi.IWifiManager");

        // 蓝牙服务 - 蓝牙 MAC 地址
        MONITORED_INTERFACES.add("android.bluetooth.IBluetooth");
        MONITORED_INTERFACES.add("android.bluetooth.IBluetoothManager");

        // DRM 服务 - 设备唯一 ID
        MONITORED_INTERFACES.add("android.media.IMediaDrmService");

        // 包管理服务 - 已安装应用列表
        MONITORED_INTERFACES.add("android.content.pm.IPackageManager");

        // 设置服务 - Android ID
        MONITORED_INTERFACES.add("android.provider.ISettings");

        // 设备标识服务
        MONITORED_INTERFACES.add("android.os.IDeviceIdentifiersPolicyService");

        // 网络服务
        MONITORED_INTERFACES.add("android.net.IConnectivityManager");
        MONITORED_INTERFACES.add("android.net.INetworkManagementService");

        // Android 12+ 新增的隐私相关服务
        if (SDK_INT >= ANDROID_12) {
            MONITORED_INTERFACES.add("android.app.admin.IDevicePolicyManager");
        }

        // Android 14+ 新增的设备标识服务
        if (SDK_INT >= ANDROID_14) {
            MONITORED_INTERFACES.add("android.os.IVirtualizationService");
        }

        // Android 15+ 新增的隐私沙盒服务
        if (SDK_INT >= ANDROID_15) {
            MONITORED_INTERFACES.add("android.adservices.IAdServicesManager");
            MONITORED_INTERFACES.add("android.health.connect.IHealthConnectService");
        }
    }

    // 控制是否启用详细日志（调用频率高时可关闭）
    private static boolean verboseLogging = true;

    // 统计计数器
    private static long totalTransactCalls = 0;
    private static long monitoredTransactCalls = 0;

    /**
     * 初始化 BinderProxy.transact() Hook
     */
    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "Binder IPC 监控模块初始化");
        XposedBridge.log(TAG + "Android 版本: " + SDK_INT + " (Android " + getAndroidVersionName() + ")");
        XposedBridge.log(TAG + "监控的服务接口数量: " + MONITORED_INTERFACES.size());
        XposedBridge.log(TAG + "========================================");

        // 初始化动态事务码缓存
        initTransactionCodeCache();

        hookBinderProxyTransact(lpparam);

        XposedBridge.log(TAG + "✅ Binder IPC 监控模块初始化完成");
    }

    /**
     * 获取 Android 版本名称
     */
    private static String getAndroidVersionName() {
        switch (SDK_INT) {
            case ANDROID_10: return "10 (Q)";
            case ANDROID_11: return "11 (R)";
            case ANDROID_12: return "12 (S)";
            case ANDROID_13: return "13 (T)";
            case ANDROID_14: return "14 (U)";
            case ANDROID_15: return "15 (V)";
            default: return String.valueOf(SDK_INT);
        }
    }

    /**
     * 初始化动态事务码缓存
     * 通过反射获取 AIDL Stub 类的 TRANSACTION_xxx 常量
     */
    private static void initTransactionCodeCache() {
        // 尝试加载各服务的 Stub 类并缓存事务码
        loadTransactionCodes("com.android.internal.telephony.IPhoneSubInfo$Stub");
        loadTransactionCodes("com.android.internal.telephony.ITelephony$Stub");
        loadTransactionCodes("android.net.wifi.IWifiManager$Stub");
        loadTransactionCodes("android.bluetooth.IBluetooth$Stub");
        loadTransactionCodes("android.bluetooth.IBluetoothManager$Stub");
        loadTransactionCodes("android.content.pm.IPackageManager$Stub");

        XposedBridge.log(TAG + "动态事务码缓存初始化完成，已缓存 " + transactionCodeCache.size() + " 个接口");
    }

    /**
     * 通过反射加载 Stub 类的事务码常量
     */
    private static void loadTransactionCodes(String stubClassName) {
        try {
            Class<?> stubClass = Class.forName(stubClassName);
            Map<String, Integer> methodToCode = new HashMap<>();
            Map<Integer, String> codeToMethod = new HashMap<>();

            for (Field field : stubClass.getDeclaredFields()) {
                String fieldName = field.getName();
                if (fieldName.startsWith("TRANSACTION_")) {
                    field.setAccessible(true);
                    int code = field.getInt(null);
                    String methodName = fieldName.substring("TRANSACTION_".length());
                    methodToCode.put(methodName, code);
                    codeToMethod.put(code, methodName);
                }
            }

            if (!methodToCode.isEmpty()) {
                // 提取接口名（去掉 $Stub 后缀）
                String interfaceName = stubClassName.replace("$Stub", "");
                transactionCodeCache.put(interfaceName, methodToCode);
                reverseCodeCache.put(interfaceName, codeToMethod);
                XposedBridge.log(TAG + "  已加载 " + interfaceName + " 的 " + methodToCode.size() + " 个事务码");
            }
        } catch (ClassNotFoundException e) {
            // 某些类在特定 Android 版本可能不存在，忽略
        } catch (Throwable t) {
            XposedBridge.log(TAG + "加载事务码失败 " + stubClassName + ": " + t.getMessage());
        }
    }

    /**
     * 通过动态缓存获取方法名
     */
    private static String getDynamicMethodName(String descriptor, int code) {
        Map<Integer, String> codeMap = reverseCodeCache.get(descriptor);
        if (codeMap != null) {
            return codeMap.get(code);
        }
        return null;
    }

    /**
     * 通过动态缓存获取事务码
     */
    private static Integer getDynamicTransactionCode(String descriptor, String methodName) {
        Map<String, Integer> methodMap = transactionCodeCache.get(descriptor);
        if (methodMap != null) {
            return methodMap.get(methodName);
        }
        return null;
    }

    /**
     * Hook BinderProxy.transact() 方法
     *
     * 方法签名：public boolean transact(int code, Parcel data, Parcel reply, int flags)
     * - code: 事务代码，标识具体的 IPC 操作
     * - data: 发送的数据
     * - reply: 接收的响应数据
     * - flags: 标志位（0=同步，FLAG_ONEWAY=异步）
     */
    private static void hookBinderProxyTransact(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> binderProxyClass = XposedHelpers.findClass(
                    "android.os.BinderProxy",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    binderProxyClass,
                    "transact",
                    int.class,           // code
                    android.os.Parcel.class,  // data
                    android.os.Parcel.class,  // reply
                    int.class,           // flags
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            totalTransactCalls++;

                            try {
                                IBinder binder = (IBinder) param.thisObject;
                                if (binder == null) return;

                                String descriptor = binder.getInterfaceDescriptor();
                                if (descriptor == null || descriptor.isEmpty()) return;

                                if (MONITORED_INTERFACES.contains(descriptor)) {
                                    monitoredTransactCalls++;

                                    int code = (int) param.args[0];
                                    android.os.Parcel data = (android.os.Parcel) param.args[1];
                                    int flags = (int) param.args[3];

                                    // 保存描述符和事务码供 afterHookedMethod 使用
                                    param.setObjectExtra("descriptor", descriptor);
                                    param.setObjectExtra("code", code);

                                    String flagStr = (flags == 0) ? "SYNC" : "ONEWAY";
                                    String serviceName = getServiceName(descriptor);
                                    String methodName = getTransactionMethodName(descriptor, code);

                                    if (verboseLogging) {
                                        XposedBridge.log(TAG + "┌──────────────────────────────────────");
                                        XposedBridge.log(TAG + "│ 【IPC 调用】");
                                        XposedBridge.log(TAG + "│ 服务: " + serviceName);
                                        XposedBridge.log(TAG + "│ 接口: " + descriptor);
                                        XposedBridge.log(TAG + "│ 事务码: " + code + (methodName != null ? " (" + methodName + ")" : ""));
                                        XposedBridge.log(TAG + "│ 模式: " + flagStr);

                                        // 记录 Parcel 数据大小
                                        if (data != null) {
                                            XposedBridge.log(TAG + "│ 请求数据大小: " + data.dataSize() + " bytes");
                                            logParcelData(data, "请求");
                                        }

                                        // 记录调用堆栈（仅高价值调用）
                                        if (isHighValueTransaction(descriptor, code)) {
                                            logCallStack();
                                        }
                                    }

                                    logHighValueTransaction(descriptor, code);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "│ ⚠️ beforeHookedMethod 异常: " + t.getMessage());
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String descriptor = (String) param.getObjectExtra("descriptor");
                                Integer code = (Integer) param.getObjectExtra("code");
                                if (descriptor == null || code == null) return;

                                android.os.Parcel reply = (android.os.Parcel) param.args[2];
                                if (reply == null) return;

                                if (verboseLogging) {
                                    XposedBridge.log(TAG + "│ 【返回数据】");
                                    XposedBridge.log(TAG + "│ 返回数据大小: " + reply.dataSize() + " bytes");
                                }

                                // 解析返回数据
                                parseReplyParcel(descriptor, code, reply);

                                // 伪造返回数据
                                boolean faked = fakeReplyParcel(descriptor, code, reply);
                                if (faked && verboseLogging) {
                                    XposedBridge.log(TAG + "│ ✅ 已伪造返回数据");
                                }

                                if (verboseLogging) {
                                    XposedBridge.log(TAG + "└──────────────────────────────────────");
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "│ ⚠️ afterHookedMethod 异常: " + t.getMessage());
                            }
                        }
                    }
            );

            XposedBridge.log(TAG + "Hook BinderProxy.transact() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook BinderProxy.transact() 失败: " + t.getMessage());
        }
    }

    /**
     * 从接口描述符提取简短的服务名称
     */
    private static String getServiceName(String descriptor) {
        if (descriptor == null) return "Unknown";

        // 提取最后一个点后面的部分
        int lastDot = descriptor.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < descriptor.length() - 1) {
            return descriptor.substring(lastDot + 1);
        }
        return descriptor;
    }

    /**
     * 记录高价值的 IPC 事务（与设备信息直接相关）
     *
     * 事务码是 AIDL 接口中方法的顺序编号，从 FIRST_CALL_TRANSACTION (1) 开始
     */
    private static void logHighValueTransaction(String descriptor, int code) {
        String detail = null;

        switch (descriptor) {
            case "com.android.internal.telephony.ITelephony":
                detail = getTelephonyTransactionName(code);
                break;
            case "com.android.internal.telephony.IPhoneSubInfo":
                detail = getPhoneSubInfoTransactionName(code);
                break;
            case "android.net.wifi.IWifiManager":
                detail = getWifiTransactionName(code);
                break;
            case "android.bluetooth.IBluetooth":
            case "android.bluetooth.IBluetoothManager":
                detail = getBluetoothTransactionName(code);
                break;
            case "android.content.pm.IPackageManager":
                detail = getPackageManagerTransactionName(code);
                break;
        }

        if (detail != null) {
            XposedBridge.log(TAG + "  └─ 可能操作: " + detail);
        }
    }

    // 事务码映射方法（基于 AOSP 源码，可能因 Android 版本而异）
    private static String getTelephonyTransactionName(int code) {
        // ITelephony.aidl 中的方法顺序
        switch (code) {
            case 1: return "dial";
            case 5: return "getDeviceId (IMEI)";
            case 6: return "getDeviceIdForPhone";
            case 7: return "getImeiForSlot";
            case 8: return "getMeidForSlot";
            default: return null;
        }
    }

    private static String getPhoneSubInfoTransactionName(int code) {
        // IPhoneSubInfo.aidl 中的方法顺序
        switch (code) {
            case 1: return "getDeviceId (IMEI)";
            case 2: return "getDeviceIdForPhone";
            case 3: return "getNaiForSubscriber";
            case 4: return "getImeiForSubscriber";
            case 5: return "getDeviceSvn";
            case 6: return "getSubscriberId (IMSI)";
            case 7: return "getSubscriberIdForSubscriber";
            case 8: return "getGroupIdLevel1";
            case 9: return "getGroupIdLevel1ForSubscriber";
            case 10: return "getIccSerialNumber (ICCID)";
            case 11: return "getIccSerialNumberForSubscriber";
            case 12: return "getLine1Number";
            case 13: return "getLine1NumberForSubscriber";
            default: return null;
        }
    }

    private static String getWifiTransactionName(int code) {
        // IWifiManager.aidl 中的方法顺序（简化版）
        switch (code) {
            case 1: return "getSupportedFeatures";
            case 10: return "getConnectionInfo (MAC/SSID)";
            case 11: return "setWifiEnabled";
            case 12: return "getWifiEnabledState";
            default: return null;
        }
    }

    private static String getBluetoothTransactionName(int code) {
        // IBluetooth.aidl 中的方法顺序（简化版）
        switch (code) {
            case 6: return "getAddress (蓝牙 MAC)";
            case 7: return "getName";
            case 8: return "setName";
            default: return null;
        }
    }

    private static String getPackageManagerTransactionName(int code) {
        // IPackageManager.aidl 中的方法顺序（简化版）
        switch (code) {
            case 1: return "checkPackageStartable";
            case 6: return "getPackageInfo";
            case 7: return "getPackageUid";
            case 42: return "getInstalledPackages";
            case 43: return "getInstalledApplications";
            default: return null;
        }
    }

    /**
     * 设置是否启用详细日志
     */
    public static void setVerboseLogging(boolean enabled) {
        verboseLogging = enabled;
        XposedBridge.log(TAG + "详细日志模式: " + (enabled ? "开启" : "关闭"));
    }

    /**
     * 获取统计信息
     */
    public static String getStatistics() {
        return String.format("IPC 统计: 总调用 %d, 监控命中 %d (%.2f%%)",
                totalTransactCalls,
                monitoredTransactCalls,
                totalTransactCalls > 0 ? (monitoredTransactCalls * 100.0 / totalTransactCalls) : 0);
    }

    /**
     * 解析 IPC 返回的 Parcel 数据
     *
     * Parcel 格式通常为：
     * - 4 字节: 异常码 (0 = 无异常)
     * - 后续: 返回值数据
     */
    private static void parseReplyParcel(String descriptor, int code, android.os.Parcel reply) {
        int savedPosition = reply.dataPosition();
        try {
            reply.setDataPosition(0);

            // 读取异常码
            int exceptionCode = reply.readInt();
            if (exceptionCode != 0) {
                XposedBridge.log(TAG + "│ 返回异常码: " + exceptionCode);
                return;
            }

            String result = null;
            String dataType = null;

            switch (descriptor) {
                case "com.android.internal.telephony.IPhoneSubInfo":
                    result = parsePhoneSubInfoReply(code, reply);
                    dataType = getPhoneSubInfoTransactionName(code);
                    break;
                case "com.android.internal.telephony.ITelephony":
                    result = parseTelephonyReply(code, reply);
                    dataType = getTelephonyTransactionName(code);
                    break;
                case "android.bluetooth.IBluetooth":
                case "android.bluetooth.IBluetoothManager":
                    result = parseBluetoothReply(code, reply);
                    dataType = getBluetoothTransactionName(code);
                    break;
                case "android.net.wifi.IWifiManager":
                    result = parseWifiReply(code, reply);
                    dataType = getWifiTransactionName(code);
                    break;
                case "android.content.pm.IPackageManager":
                    result = parsePackageManagerReply(code, reply);
                    dataType = getPackageManagerTransactionName(code);
                    break;
                case "android.provider.ISettings":
                    result = parseSettingsReply(code, reply);
                    dataType = getSettingsTransactionName(code);
                    break;
                case "android.os.IDeviceIdentifiersPolicyService":
                    result = parseDeviceIdPolicyReply(code, reply);
                    dataType = getDeviceIdPolicyTransactionName(code);
                    break;
                default:
                    // 通用解析：尝试读取字符串或打印 hex
                    result = parseGenericReply(reply);
                    dataType = "code=" + code;
                    break;
            }

            if (result != null && !result.isEmpty()) {
                XposedBridge.log(TAG + "│ 返回值: " + (dataType != null ? "[" + dataType + "] " : "") + result);
            }

            // 打印返回数据的 hex 预览（用于调试）
            logReplyHexPreview(reply);

        } catch (Throwable t) {
            XposedBridge.log(TAG + "│ 返回数据解析异常: " + t.getMessage());
        } finally {
            reply.setDataPosition(savedPosition);
        }
    }

    /**
     * 解析 IPhoneSubInfo 服务的返回数据
     * 使用动态方法名匹配适配 Android 15
     */
    private static String parsePhoneSubInfoReply(int code, android.os.Parcel reply) {
        // 优先使用动态方法名判断
        String methodName = getDynamicMethodName("com.android.internal.telephony.IPhoneSubInfo", code);
        if (methodName != null) {
            String lowerMethod = methodName.toLowerCase();
            // 这些方法返回字符串类型
            if (lowerMethod.contains("deviceid") || lowerMethod.contains("imei") ||
                lowerMethod.contains("subscriberid") || lowerMethod.contains("imsi") ||
                lowerMethod.contains("iccserial") || lowerMethod.contains("simserialnumber") ||
                lowerMethod.contains("line1number") || lowerMethod.contains("phonenumber") ||
                lowerMethod.contains("nai") || lowerMethod.contains("groupid")) {
                return readStringFromParcel(reply);
            }
        }

        // 回退到硬编码事务码
        switch (code) {
            case 1:  // getDeviceId (IMEI)
            case 2:  // getDeviceIdForPhone
            case 4:  // getImeiForSubscriber
            case 6:  // getSubscriberId (IMSI)
            case 7:  // getSubscriberIdForSubscriber
            case 10: // getIccSerialNumber (ICCID)
            case 11: // getIccSerialNumberForSubscriber
            case 12: // getLine1Number
            case 13: // getLine1NumberForSubscriber
                return readStringFromParcel(reply);
            default:
                return null;
        }
    }

    /**
     * 解析 ITelephony 服务的返回数据
     * 使用动态方法名匹配适配 Android 15
     */
    private static String parseTelephonyReply(int code, android.os.Parcel reply) {
        // 优先使用动态方法名判断
        String methodName = getDynamicMethodName("com.android.internal.telephony.ITelephony", code);
        if (methodName != null) {
            String lowerMethod = methodName.toLowerCase();
            // 这些方法返回字符串类型
            if (lowerMethod.contains("deviceid") || lowerMethod.contains("imei") ||
                lowerMethod.contains("meid") || lowerMethod.contains("serial")) {
                return readStringFromParcel(reply);
            }
        }

        // 回退到硬编码事务码
        switch (code) {
            case 5:  // getDeviceId (IMEI)
            case 6:  // getDeviceIdForPhone
            case 7:  // getImeiForSlot
            case 8:  // getMeidForSlot
                return readStringFromParcel(reply);
            default:
                return null;
        }
    }

    /**
     * 解析蓝牙服务的返回数据
     * 使用动态方法名匹配适配 Android 15
     */
    private static String parseBluetoothReply(int code, android.os.Parcel reply) {
        // 优先使用动态方法名判断
        String methodName = getDynamicMethodName("android.bluetooth.IBluetooth", code);
        if (methodName != null) {
            String lowerMethod = methodName.toLowerCase();
            // 这些方法返回字符串类型
            if (lowerMethod.contains("address") || lowerMethod.contains("name")) {
                return readStringFromParcel(reply);
            }
        }

        // 回退到硬编码事务码
        switch (code) {
            case 6:  // getAddress (蓝牙 MAC)
            case 7:  // getName
                return readStringFromParcel(reply);
            default:
                return null;
        }
    }

    /**
     * 解析 WiFi 服务的返回数据
     */
    private static String parseWifiReply(int code, android.os.Parcel reply) {
        switch (code) {
            case 10: // getConnectionInfo - 返回 WifiInfo 对象
                return parseWifiInfoFromParcel(reply);
            default:
                return null;
        }
    }

    /**
     * 从 Parcel 中读取字符串
     */
    private static String readStringFromParcel(android.os.Parcel reply) {
        try {
            // 检查是否有数据
            int hasData = reply.readInt();
            if (hasData == 0) {
                return "[null]";
            }
            return reply.readString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 从 Parcel 中解析 WifiInfo 对象的关键字段
     */
    private static String parseWifiInfoFromParcel(android.os.Parcel reply) {
        try {
            // WifiInfo 是 Parcelable，格式较复杂
            // 尝试读取关键字段
            int hasData = reply.readInt();
            if (hasData == 0) {
                return "[null]";
            }

            StringBuilder sb = new StringBuilder();

            // 尝试读取 SSID 和 BSSID
            // 注意：WifiInfo 的 Parcel 格式可能因 Android 版本而异
            int networkId = reply.readInt();
            sb.append("networkId=").append(networkId);

            // 读取 RSSI
            int rssi = reply.readInt();
            sb.append(", rssi=").append(rssi);

            // 读取 linkSpeed
            int linkSpeed = reply.readInt();
            sb.append(", linkSpeed=").append(linkSpeed);

            // 读取 frequency
            int frequency = reply.readInt();
            sb.append(", freq=").append(frequency);

            return sb.toString();
        } catch (Throwable t) {
            return "[解析失败]";
        }
    }

    /**
     * 获取事务方法名称
     * 优先使用动态缓存，回退到静态映射
     */
    private static String getTransactionMethodName(String descriptor, int code) {
        // 优先使用动态缓存（适配所有 Android 版本）
        String dynamicName = getDynamicMethodName(descriptor, code);
        if (dynamicName != null) {
            return dynamicName;
        }

        // 回退到静态映射（兼容旧版本）
        switch (descriptor) {
            case "com.android.internal.telephony.ITelephony":
                return getTelephonyTransactionName(code);
            case "com.android.internal.telephony.IPhoneSubInfo":
                return getPhoneSubInfoTransactionName(code);
            case "android.net.wifi.IWifiManager":
                return getWifiTransactionName(code);
            case "android.bluetooth.IBluetooth":
            case "android.bluetooth.IBluetoothManager":
                return getBluetoothTransactionName(code);
            case "android.content.pm.IPackageManager":
                return getPackageManagerTransactionName(code);
            case "android.os.IDeviceIdentifiersPolicyService":
                return getDeviceIdPolicyTransactionName(code);
            case "android.provider.ISettings":
                return getSettingsTransactionName(code);
            default:
                return null;
        }
    }

    /**
     * 判断是否为高价值事务（需要记录调用堆栈）
     * 使用动态事务码适配不同 Android 版本
     */
    private static boolean isHighValueTransaction(String descriptor, int code) {
        // 优先使用动态方法名判断
        String methodName = getDynamicMethodName(descriptor, code);
        if (methodName != null) {
            return isHighValueMethod(methodName);
        }

        // 回退到静态事务码判断
        switch (descriptor) {
            case "com.android.internal.telephony.IPhoneSubInfo":
                // IMEI, IMSI, ICCID, 手机号等
                return code >= 1 && code <= 13;
            case "com.android.internal.telephony.ITelephony":
                // getDeviceId, getImei 等
                return code >= 5 && code <= 8;
            case "android.bluetooth.IBluetooth":
            case "android.bluetooth.IBluetoothManager":
                // 蓝牙 MAC 地址
                return code == 6;
            case "android.net.wifi.IWifiManager":
                // WiFi 连接信息
                return code == 10;
            case "android.os.IDeviceIdentifiersPolicyService":
                return true;
            default:
                return false;
        }
    }

    /**
     * 根据方法名判断是否为高价值方法
     */
    private static boolean isHighValueMethod(String methodName) {
        if (methodName == null) return false;
        String lowerName = methodName.toLowerCase();
        return lowerName.contains("deviceid") ||
               lowerName.contains("imei") ||
               lowerName.contains("imsi") ||
               lowerName.contains("meid") ||
               lowerName.contains("subscriberid") ||
               lowerName.contains("iccserial") ||
               lowerName.contains("line1number") ||
               lowerName.contains("phonenumber") ||
               lowerName.contains("address") ||
               lowerName.contains("serial") ||
               lowerName.contains("connectioninfo") ||
               lowerName.contains("macaddress");
    }

    /**
     * 记录调用堆栈
     */
    private static void logCallStack() {
        XposedBridge.log(TAG + "│ 【调用堆栈】");
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int count = 0;
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            // 跳过系统类和 Xposed 框架类
            if (className.startsWith("dalvik.") ||
                className.startsWith("java.lang.") ||
                className.startsWith("de.robv.android.xposed") ||
                className.startsWith("android.os.BinderProxy") ||
                className.contains("BinderHook")) {
                continue;
            }
            XposedBridge.log(TAG + "│   " + element.getClassName() + "." +
                    element.getMethodName() + "(" + element.getFileName() + ":" +
                    element.getLineNumber() + ")");
            count++;
            if (count >= 10) {
                XposedBridge.log(TAG + "│   ... (更多堆栈省略)");
                break;
            }
        }
    }

    /**
     * 记录 Parcel 数据内容
     */
    private static void logParcelData(android.os.Parcel data, String label) {
        int savedPosition = data.dataPosition();
        try {
            data.setDataPosition(0);
            int dataSize = data.dataSize();

            if (dataSize > 0) {
                // 读取接口描述符（通常是第一个字段）
                try {
                    // enforceInterface 会验证接口描述符
                    int tokenLength = data.readInt();
                    if (tokenLength > 0 && tokenLength < 256) {
                        String interfaceToken = data.readString();
                        if (interfaceToken != null) {
                            XposedBridge.log(TAG + "│ " + label + "接口: " + interfaceToken);
                        }
                    }
                } catch (Throwable ignored) {}

                // 尝试读取剩余数据的前几个字段
                int remaining = data.dataAvail();
                if (remaining > 0) {
                    StringBuilder hexPreview = new StringBuilder();
                    byte[] bytes = new byte[Math.min(remaining, 64)];
                    data.setDataPosition(data.dataPosition());

                    // 读取原始字节用于预览
                    int bytesToRead = Math.min(remaining, 64);
                    for (int i = 0; i < bytesToRead && data.dataAvail() > 0; i++) {
                        try {
                            bytes[i] = data.readByte();
                            hexPreview.append(String.format("%02X ", bytes[i]));
                        } catch (Throwable ignored) {
                            break;
                        }
                    }

                    if (hexPreview.length() > 0) {
                        XposedBridge.log(TAG + "│ " + label + "数据(hex): " + hexPreview.toString().trim());
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "│ " + label + "数据解析失败: " + t.getMessage());
        } finally {
            data.setDataPosition(savedPosition);
        }
    }

    /**
     * 设备标识策略服务事务名称
     */
    private static String getDeviceIdPolicyTransactionName(int code) {
        switch (code) {
            case 1: return "getDeviceId";
            case 2: return "getSerialNumber";
            default: return null;
        }
    }

    /**
     * Settings 服务事务名称
     */
    private static String getSettingsTransactionName(int code) {
        switch (code) {
            case 1: return "getString (可能获取 Android ID)";
            case 2: return "putString";
            case 3: return "getInt";
            case 4: return "putInt";
            default: return null;
        }
    }

    /**
     * 解析 PackageManager 服务的返回数据
     */
    private static String parsePackageManagerReply(int code, android.os.Parcel reply) {
        switch (code) {
            case 6:  // getPackageInfo
            case 7:  // getPackageUid
                return readStringFromParcel(reply);
            case 42: // getInstalledPackages
            case 43: // getInstalledApplications
                // 返回的是 ParceledListSlice，格式复杂
                try {
                    int count = reply.readInt();
                    return "[列表] 数量: " + count;
                } catch (Throwable t) {
                    return "[列表解析失败]";
                }
            default:
                return null;
        }
    }

    /**
     * 解析 Settings 服务的返回数据
     */
    private static String parseSettingsReply(int code, android.os.Parcel reply) {
        switch (code) {
            case 1:  // getString
            case 2:  // putString
                return readStringFromParcel(reply);
            case 3:  // getInt
            case 4:  // putInt
                try {
                    int value = reply.readInt();
                    return String.valueOf(value);
                } catch (Throwable t) {
                    return null;
                }
            default:
                return null;
        }
    }

    /**
     * 解析设备标识策略服务的返回数据
     */
    private static String parseDeviceIdPolicyReply(int code, android.os.Parcel reply) {
        switch (code) {
            case 1:  // getDeviceId
            case 2:  // getSerialNumber
                return readStringFromParcel(reply);
            default:
                return null;
        }
    }

    /**
     * 通用返回值解析（尝试读取字符串）
     */
    private static String parseGenericReply(android.os.Parcel reply) {
        try {
            // 尝试读取字符串
            String str = readStringFromParcel(reply);
            if (str != null && !str.isEmpty() && !str.equals("[null]")) {
                return str;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 打印返回数据的 hex 预览
     */
    private static void logReplyHexPreview(android.os.Parcel reply) {
        try {
            reply.setDataPosition(4); // 跳过异常码
            int remaining = reply.dataAvail();
            if (remaining > 0 && remaining <= 256) {
                StringBuilder hexPreview = new StringBuilder();
                int bytesToRead = Math.min(remaining, 64);
                for (int i = 0; i < bytesToRead && reply.dataAvail() > 0; i++) {
                    byte b = reply.readByte();
                    hexPreview.append(String.format("%02X ", b));
                }
                if (hexPreview.length() > 0) {
                    XposedBridge.log(TAG + "│ 返回数据(hex): " + hexPreview.toString().trim() +
                            (remaining > 64 ? " ..." : ""));
                }
            }
        } catch (Throwable ignored) {}
    }

    // ============================================================================
    // Binder IPC 层面的设备信息伪造
    // ============================================================================

    /**
     * 伪造 Binder IPC 返回数据
     *
     * @param descriptor 服务接口描述符
     * @param code 事务码
     * @param reply 返回的 Parcel 数据
     * @return 是否成功伪造
     */
    private static boolean fakeReplyParcel(String descriptor, int code, android.os.Parcel reply) {
        try {
            switch (descriptor) {
                case "com.android.internal.telephony.IPhoneSubInfo":
                    return fakePhoneSubInfoReply(code, reply);
                case "com.android.internal.telephony.ITelephony":
                    return fakeTelephonyReply(code, reply);
                case "android.bluetooth.IBluetooth":
                case "android.bluetooth.IBluetoothManager":
                    return fakeBluetoothReply(code, reply);
                case "android.net.wifi.IWifiManager":
                    return fakeWifiReply(code, reply);
                case "android.os.IDeviceIdentifiersPolicyService":
                    return fakeDeviceIdPolicyReply(code, reply);
                case "android.provider.ISettings":
                    return fakeSettingsReply(code, reply);
                default:
                    return false;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "│ 伪造数据异常: " + t.getMessage());
            return false;
        }
    }

    /**
     * 伪造 IPhoneSubInfo 服务返回数据
     * 包括: IMEI, IMSI, ICCID, 手机号等
     * 使用动态事务码适配 Android 15
     */
    private static boolean fakePhoneSubInfoReply(int code, android.os.Parcel reply) {
        String methodName = getDynamicMethodName("com.android.internal.telephony.IPhoneSubInfo", code);
        String fakeValue = null;
        String dataType = null;

        // 优先使用动态方法名匹配
        if (methodName != null) {
            String lowerMethod = methodName.toLowerCase();
            if (lowerMethod.contains("deviceid") || lowerMethod.contains("imei")) {
                fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                dataType = "IMEI (" + methodName + ")";
            } else if (lowerMethod.contains("subscriberid") || lowerMethod.contains("imsi")) {
                fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                dataType = "IMSI (" + methodName + ")";
            } else if (lowerMethod.contains("iccserial") || lowerMethod.contains("simserialnumber")) {
                fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                dataType = "ICCID (" + methodName + ")";
            } else if (lowerMethod.contains("line1number") || lowerMethod.contains("phonenumber")) {
                fakeValue = FakeData.FAKE_PHONE_NUMBER;
                dataType = "手机号 (" + methodName + ")";
            }
        }

        // 回退到硬编码事务码（兼容旧版本）
        if (fakeValue == null) {
            switch (code) {
                case 1:  // getDeviceId (IMEI)
                case 2:  // getDeviceIdForPhone
                case 4:  // getImeiForSubscriber
                    fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                    dataType = "IMEI";
                    break;
                case 6:  // getSubscriberId (IMSI)
                case 7:  // getSubscriberIdForSubscriber
                    fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                    dataType = "IMSI";
                    break;
                case 10: // getIccSerialNumber (ICCID)
                case 11: // getIccSerialNumberForSubscriber
                    fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                    dataType = "ICCID";
                    break;
                case 12: // getLine1Number
                case 13: // getLine1NumberForSubscriber
                    fakeValue = FakeData.FAKE_PHONE_NUMBER;
                    dataType = "手机号";
                    break;
                default:
                    return false;
            }
        }

        if (fakeValue != null || dataType != null) {
            writeStringToParcel(reply, fakeValue);
            XposedBridge.log(TAG + "│ ✅ Android15受限 " + dataType + ": " + fakeValue);
            return true;
        }
        return false;
    }

    /**
     * 伪造 ITelephony 服务返回数据
     * 使用动态事务码适配 Android 15
     */
    private static boolean fakeTelephonyReply(int code, android.os.Parcel reply) {
        String methodName = getDynamicMethodName("com.android.internal.telephony.ITelephony", code);
        String fakeValue = null;
        String dataType = null;

        // 优先使用动态方法名匹配
        if (methodName != null) {
            String lowerMethod = methodName.toLowerCase();
            if (lowerMethod.contains("imei") || lowerMethod.contains("deviceid")) {
                fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                dataType = "IMEI (" + methodName + ")";
            } else if (lowerMethod.contains("meid")) {
                fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                dataType = "MEID (" + methodName + ")";
            }
        }

        // 回退到硬编码事务码
        if (fakeValue == null) {
            switch (code) {
                case 5:  // getDeviceId (IMEI)
                case 6:  // getDeviceIdForPhone
                case 7:  // getImeiForSlot
                    fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                    dataType = "IMEI";
                    break;
                case 8:  // getMeidForSlot
                    fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                    dataType = "MEID";
                    break;
                default:
                    return false;
            }
        }

        if (fakeValue != null || dataType != null) {
            writeStringToParcel(reply, fakeValue);
            XposedBridge.log(TAG + "│ ✅ Android15受限 " + dataType + ": " + fakeValue);
            return true;
        }
        return false;
    }

    /**
     * 伪造蓝牙服务返回数据
     * 使用动态事务码适配 Android 15
     */
    private static boolean fakeBluetoothReply(int code, android.os.Parcel reply) {
        String methodName = getDynamicMethodName("android.bluetooth.IBluetooth", code);

        // 优先使用动态方法名匹配
        boolean isAddressMethod = false;
        if (methodName != null) {
            String lowerMethod = methodName.toLowerCase();
            isAddressMethod = lowerMethod.contains("address") || lowerMethod.equals("getaddress");
        }

        // 回退到硬编码事务码
        if (!isAddressMethod && code == 6) {
            isAddressMethod = true;
        }

        if (isAddressMethod) {
            // 优先使用动态配置的蓝牙地址
            String fakeMac = (HookInit.bluetooth_address != null) ?
                    HookInit.bluetooth_address : FakeData.FAKE_BLUETOOTH_MAC;
            writeStringToParcel(reply, fakeMac);
            XposedBridge.log(TAG + "│ ✅ 伪造蓝牙 MAC" +
                    (methodName != null ? " (" + methodName + ")" : "") + ": " + fakeMac);
            return true;
        }
        return false;
    }

    /**
     * 伪造 WiFi 服务返回数据
     * 注意: WiFi 连接信息返回的是 WifiInfo 对象，格式复杂
     */
    private static boolean fakeWifiReply(int code, android.os.Parcel reply) {
        // WiFi 服务的伪造比较复杂，因为返回的是 Parcelable 对象
        // 这里暂时只记录日志，实际伪造在 SystemInfoFakeHook 的 Java 层完成
        if (code == 10) { // getConnectionInfo
            XposedBridge.log(TAG + "│ WiFi 连接信息 (WifiInfo) 在 Java 层伪造");
            return false; // 返回 false 表示未在 Binder 层伪造
        }
        return false;
    }

    /**
     * 伪造设备标识策略服务返回数据
     */
    private static boolean fakeDeviceIdPolicyReply(int code, android.os.Parcel reply) {
        String fakeValue = null;
        String dataType = null;

        switch (code) {
            case 1: // getDeviceId
                fakeValue = FakeData.RESTRICTED_TELEPHONY_ID;
                dataType = "设备ID";
                break;
            case 2: // getSerialNumber
                fakeValue = FakeData.RESTRICTED_BUILD_SERIAL;
                dataType = "序列号";
                break;
            default:
                return false;
        }

        if (fakeValue != null || dataType != null) {
            writeStringToParcel(reply, fakeValue);
            XposedBridge.log(TAG + "│ Android15受限 " + dataType + ": " + fakeValue);
            return true;
        }
        return false;
    }

    /**
     * 将字符串写入 Parcel（覆盖原有数据）
     *
     * String AIDL 返回值格式：
     * - 4 字节: 异常码 (0 = 无异常)
     * - 字符串数据，null 由 Parcel.writeString 自身编码
     */
    private static void writeStringToParcel(android.os.Parcel reply, String value) {
        reply.setDataPosition(0);
        reply.setDataSize(0);
        reply.writeNoException();  // 写入异常码 0
        reply.writeString(value);
        reply.setDataPosition(0);  // 重置位置供读取
    }

    /**
     * 伪造 Settings 服务返回数据
     * 主要用于伪造 Android ID
     *
     * 注意：ISettings 的 getString 需要检查请求的 key 是否为 android_id
     * 但在 Binder 层难以获取请求参数，因此这里的伪造可能不够精确
     * 建议主要依赖 Java 层的 Settings.Secure.getString Hook
     */
    private static boolean fakeSettingsReply(int code, android.os.Parcel reply) {
        // Settings 服务的伪造比较复杂，因为需要知道请求的 key
        // 这里暂时不在 Binder 层伪造，依赖 Java 层的 Hook
        // 如果需要更精确的伪造，可以在 beforeHookedMethod 中解析请求数据
        if (code == 1) { // getString
            XposedBridge.log(TAG + "│ Settings.getString 在 Java 层伪造 (ContentResolver Hook)");
        }
        return false;
    }
}
