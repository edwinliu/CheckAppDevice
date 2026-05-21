package com.deviceveil.guard;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * VPN 检测绕过模块
 * 拦截应用检测 VPN 连接的常见方法
 */
public class VpnDetectionBypassHook {

    private static final String TAG = "[设备信息记录]---[VpnDetectionBypassHook] ";

    // VPN 网络接口名称前缀
    private static final String[] VPN_INTERFACE_PREFIXES = {
            "tun", "tap", "ppp", "pptp", "l2tp", "ipsec", "vpn"
    };

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化 VPN 检测绕过 Hook");

        hookNetworkInterface(lpparam);
        hookNetworkCapabilities(lpparam);
        hookConnectivityManager(lpparam);

        XposedBridge.log(TAG + "VPN 检测绕过 Hook 初始化完成");
    }

    /**
     * Hook NetworkInterface.getNetworkInterfaces()
     * 过滤掉 VPN 相关的网络接口 (tun/tap/ppp)
     */
    private static void hookNetworkInterface(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.net.NetworkInterface",
                    lpparam.classLoader,
                    "getNetworkInterfaces",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            @SuppressWarnings("unchecked")
                            Enumeration<NetworkInterface> original =
                                    (Enumeration<NetworkInterface>) param.getResult();

                            if (original == null) return;

                            List<NetworkInterface> filtered = new ArrayList<>();
                            List<String> removedInterfaces = new ArrayList<>();

                            while (original.hasMoreElements()) {
                                NetworkInterface ni = original.nextElement();
                                if (!isVpnInterface(ni.getName())) {
                                    filtered.add(ni);
                                } else {
                                    removedInterfaces.add(ni.getName());
                                }
                            }

                            if (!removedInterfaces.isEmpty()) {
                                XposedBridge.log(TAG + "过滤 VPN 接口: " + removedInterfaces);
                            }

                            param.setResult(Collections.enumeration(filtered));
                        }
                    }
            );
            XposedBridge.log(TAG + "NetworkInterface.getNetworkInterfaces() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "NetworkInterface Hook 失败: " + e.getMessage());
        }
    }

    /**
     * Hook NetworkCapabilities 相关方法
     */
    private static void hookNetworkCapabilities(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> networkCapabilitiesClass = XposedHelpers.findClass(
                    "android.net.NetworkCapabilities", lpparam.classLoader);

            // Hook hasTransport() - 当检测 TRANSPORT_VPN (4) 时返回 false
            XposedHelpers.findAndHookMethod(
                    networkCapabilitiesClass,
                    "hasTransport",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int transportType = (int) param.args[0];
                            // TRANSPORT_VPN = 4
                            if (transportType == 4) {
                                boolean orig = (boolean) param.getResult();
                                if (orig) {
                                    XposedBridge.log(TAG + "hasTransport(VPN) 原始: true -> 伪造: false");
                                    param.setResult(false);
                                }
                            }
                        }
                    }
            );

            // Hook hasCapability() - 当检测 NET_CAPABILITY_NOT_VPN (15) 时返回 true
            XposedHelpers.findAndHookMethod(
                    networkCapabilitiesClass,
                    "hasCapability",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int capability = (int) param.args[0];
                            // NET_CAPABILITY_NOT_VPN = 15
                            if (capability == 15) {
                                boolean orig = (boolean) param.getResult();
                                if (!orig) {
                                    XposedBridge.log(TAG + "hasCapability(NOT_VPN) 原始: false -> 伪造: true");
                                    param.setResult(true);
                                }
                            }
                        }
                    }
            );

            XposedBridge.log(TAG + "NetworkCapabilities Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "NetworkCapabilities Hook 失败: " + e.getMessage());
        }
    }

    /**
     * Hook ConnectivityManager 相关方法
     */
    private static void hookConnectivityManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> connectivityManagerClass = XposedHelpers.findClass(
                    "android.net.ConnectivityManager", lpparam.classLoader);

            // Hook getNetworkInfo(int) - 过滤 VPN 类型网络
            XposedHelpers.findAndHookMethod(
                    connectivityManagerClass,
                    "getNetworkInfo",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int networkType = (int) param.args[0];
                            // TYPE_VPN = 17
                            if (networkType == 17) {
                                XposedBridge.log(TAG + "getNetworkInfo(TYPE_VPN) -> 返回 null");
                                param.setResult(null);
                            }
                        }
                    }
            );

            XposedBridge.log(TAG + "ConnectivityManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "ConnectivityManager Hook 失败: " + e.getMessage());
        }
    }

    /**
     * 检查网络接口名称是否为 VPN 接口
     */
    private static boolean isVpnInterface(String interfaceName) {
        if (interfaceName == null) return false;
        String lowerName = interfaceName.toLowerCase();
        for (String prefix : VPN_INTERFACE_PREFIXES) {
            if (lowerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}