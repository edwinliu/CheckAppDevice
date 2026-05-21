package com.deviceveil.guard;

import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FakeDeviceModule {

    private static final String TAG = "[设备信息记录]---[FakeDeviceModule] ";

    static void fake_imei(XC_LoadPackage.LoadPackageParam lpparam) {
        hookTelephonyRestrictedString(lpparam, "getDeviceId", "设备 ID");
        hookTelephonyRestrictedString(lpparam, "getImei", "IMEI");
        hookTelephonyRestrictedString(lpparam, "getMeid", "MEID");
        try {
            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    "getDeviceId",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + "getDeviceId(int) 原始值: " + orig + " -> Android15受限值: null");
                            param.setResult(FakeData.RESTRICTED_TELEPHONY_ID);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking getDeviceId(int): " + e);
        }
        hookTelephonyRestrictedStringWithInt(lpparam, "getImei", "IMEI");
        hookTelephonyRestrictedStringWithInt(lpparam, "getMeid", "MEID");
    }

    private static void hookTelephonyRestrictedString(XC_LoadPackage.LoadPackageParam lpparam, String methodName, String label) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + methodName + "() 原始值: " + orig + " -> Android15受限" + label + ": null");
                            param.setResult(FakeData.RESTRICTED_TELEPHONY_ID);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Skip hooking " + methodName + "(): " + e.getClass().getSimpleName());
        }
    }

    private static void hookTelephonyRestrictedStringWithInt(XC_LoadPackage.LoadPackageParam lpparam, String methodName, String label) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    methodName,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + methodName + "(int) 原始值: " + orig + " -> Android15受限" + label + ": null");
                            param.setResult(FakeData.RESTRICTED_TELEPHONY_ID);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Skip hooking " + methodName + "(int): " + e.getClass().getSimpleName());
        }
    }

    static void fake_imsi(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    "getSubscriberId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + "getSubscriberId() 原始值: " + orig + " -> Android15受限值: null");
                            param.setResult(FakeData.RESTRICTED_TELEPHONY_ID);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking getSubscriberId(): " + e);
        }
    }

    static void fake_sim_serial(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    "getSimSerialNumber",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + "getSimSerialNumber() 原始值: " + orig + " -> Android15受限值: null");
                            param.setResult(FakeData.RESTRICTED_TELEPHONY_ID);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking getSimSerialNumber(): " + e);
        }
    }

    static void fake_android_id(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.provider.Settings$Secure",
                    lpparam.classLoader,
                    "getString",
                    android.content.ContentResolver.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[1];
                            if ("android_id".equals(key)) {
                                // 优先使用动态生成的值
                                String fakeId = (HookInit.Android_id != null) ? HookInit.Android_id : FakeData.FAKE_ANDROID_ID;
                                param.setResult(fakeId);
                            }
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking Settings.Secure.getString(): " + e);
        }
    }

    static void fake_build_serial(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.Build",
                    lpparam.classLoader,
                    "getSerial",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + "Build.getSerial() 原始值: " + orig + " -> Android15受限值: " + FakeData.RESTRICTED_BUILD_SERIAL);
                            param.setResult(FakeData.RESTRICTED_BUILD_SERIAL);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking Build.getSerial(): " + e);
        }
    }

    static void fake_install_time(XC_LoadPackage.LoadPackageParam lpparam) {
        hookReferrerLongMethod(lpparam,
                "getInstallBeginTimestampSeconds",
                fakeInstallSeconds(),
                "InstallReferrer 安装开始时间");
        hookReferrerLongMethod(lpparam,
                "getInstallBeginTimestampServerSeconds",
                fakeInstallSeconds(),
                "InstallReferrer 服务端安装开始时间");
        hookReferrerLongMethod(lpparam,
                "getReferrerClickTimestampSeconds",
                fakeReferrerClickSeconds(),
                "InstallReferrer 点击时间");
        hookReferrerLongMethod(lpparam,
                "getReferrerClickTimestampServerSeconds",
                fakeReferrerClickSeconds(),
                "InstallReferrer 服务端点击时间");
    }

    private static void hookReferrerLongMethod(XC_LoadPackage.LoadPackageParam lpparam,
                                               String methodName,
                                               long fakeValue,
                                               String label) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.installreferrer.api.ReferrerDetails",
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + label + " " + methodName + "() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                            param.setResult(fakeValue);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Skip hooking " + methodName + "(): " + e.getClass().getSimpleName());
        }
    }

    private static long fakeInstallSeconds() {
        return HookInit.Install_time != null ? HookInit.Install_time / 1000L : FakeData.FAKE_INSTALL_TIME;
    }

    private static long fakeReferrerClickSeconds() {
        long installSeconds = fakeInstallSeconds();
        return Math.max(0L, installSeconds - Constants.THREE_HOURS_SECONDS);
    }

    static void fake_wifi_mac_address(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.net.wifi.WifiInfo",
                    lpparam.classLoader,
                    "getMacAddress",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) return;
                            Object orig = param.getResult();
                            String fakeMac = FakeData.RESTRICTED_WIFI_MAC;
                            XposedBridge.log(TAG + "WifiInfo.getMacAddress() 原始值: " + orig + " -> 伪造值: " + fakeMac);
                            param.setResult(fakeMac);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking getMacAddress(): " + e);
        }
    }

    static void fake_networkinterface_mac(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.net.NetworkInterface",
                    lpparam.classLoader,
                    "getHardwareAddress",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) return;
                            byte[] orig = (byte[]) param.getResult();
                            String origStr = (orig != null) ? bytesToMac(orig) : "null";
                            String interfaceName = getNetworkInterfaceName(param.thisObject);
                            if (isWifiLikeInterface(interfaceName)) {
                                XposedBridge.log(TAG + "NetworkInterface.getHardwareAddress(" + interfaceName + ") 原始值: " + origStr + " -> Android15受限值: null");
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking getHardwareAddress(): " + e);
        }
    }

    static void fake_sd_state(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.Environment",
                    lpparam.classLoader,
                    "getExternalStorageState",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object orig = param.getResult();
                            XposedBridge.log(TAG + "Environment.getExternalStorageState() 原始值: " + orig + " -> 伪造值: " + FakeData.FAKE_SD_STATE);
                            param.setResult(FakeData.FAKE_SD_STATE);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log(TAG + "Error hooking getExternalStorageState(): " + e);
        }
    }

    /**
     * 将字节数组转换为 MAC 地址格式字符串
     */
    private static String bytesToMac(byte[] bytes) {
        if (bytes == null || bytes.length != 6) {
            return "invalid";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    private static String getNetworkInterfaceName(Object networkInterface) {
        try {
            Object name = XposedHelpers.callMethod(networkInterface, "getName");
            return name != null ? String.valueOf(name) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isWifiLikeInterface(String name) {
        if (name == null) return false;
        return name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("p2p");
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Hook MediaDrm.getPropertyByteArray() 方法
     * 用于伪造 DRM 设备唯一标识 (PROPERTY_DEVICE_UNIQUE_ID)
     */
    static void fake_drm_id(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.media.MediaDrm",
                    lpparam.classLoader,
                    "getPropertyByteArray",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String propertyName = (String) param.args[0];
                            byte[] origResult = (byte[]) param.getResult();

                            XposedBridge.log(TAG + "MediaDrm.getPropertyByteArray() 被调用");
                            XposedBridge.log(TAG + "  propertyName: " + propertyName);

                            // PROPERTY_DEVICE_UNIQUE_ID = "deviceUniqueId"
                            if ("deviceUniqueId".equals(propertyName)) {
                                // 优先使用动态生成的值
                                byte[] fakeDrmId = (HookInit.Drm_id != null) ? HookInit.Drm_id : FakeData.FAKE_DRM_ID;
                                String origHex = bytesToHex(origResult);
                                String fakeHex = bytesToHex(fakeDrmId);

                                XposedBridge.log(TAG + "  原始 DRM ID: " + origHex);
                                XposedBridge.log(TAG + "  伪造 DRM ID: " + fakeHex);
                                XposedBridge.log(TAG + "  原始长度: " + (origResult != null ? origResult.length : 0) + " bytes");
                                XposedBridge.log(TAG + "  伪造长度: " + fakeDrmId.length + " bytes");

                                param.setResult(fakeDrmId);
                            } else {
                                XposedBridge.log(TAG + "  原始值: " + bytesToHex(origResult));
                            }
                        }
                    });
            XposedBridge.log(TAG + "✅ Hook MediaDrm.getPropertyByteArray() 成功");
        } catch (Throwable e) {
            XposedBridge.log(TAG + "❌ Hook MediaDrm.getPropertyByteArray() 失败: " + e.getMessage());
        }

        // 同时 Hook getPropertyString 方法，记录其他属性获取
        try {
            XposedHelpers.findAndHookMethod(
                    "android.media.MediaDrm",
                    lpparam.classLoader,
                    "getPropertyString",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String propertyName = (String) param.args[0];
                            String result = (String) param.getResult();
                            XposedBridge.log(TAG + "MediaDrm.getPropertyString() 被调用");
                            XposedBridge.log(TAG + "  propertyName: " + propertyName);
                            XposedBridge.log(TAG + "  返回值: " + result);
                        }
                    });
            XposedBridge.log(TAG + "✅ Hook MediaDrm.getPropertyString() 成功");
        } catch (Throwable e) {
            XposedBridge.log(TAG + "❌ Hook MediaDrm.getPropertyString() 失败: " + e.getMessage());
        }
    }
}
