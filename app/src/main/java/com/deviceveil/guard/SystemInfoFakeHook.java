package com.deviceveil.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemInfoFakeHook {

    private static final String TAG = "[设备信息记录]---[SystemInfoFakeHook] ";

    private static void verbose(String message) {
        if (Constants.VERBOSE_HOOK_LOGS) {
            XposedBridge.log(TAG + message);
        }
    }

    // 递归保护标志，防止 Hook 回调中调用被 Hook 的方法导致无限递归
    private static final ThreadLocal<Boolean> sInTimezoneHook = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> sInLocaleHook = ThreadLocal.withInitial(() -> false);

    // 预缓存的伪造对象，避免在 Hook 回调中创建新对象触发递归
    private static volatile java.util.TimeZone sCachedFakeTimeZone = null;
    private static volatile java.util.Locale sCachedFakeLocale = null;

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化系统信息 Hook");

        // 预初始化缓存对象（在 Hook 安装前完成，避免递归）
        initCachedObjects();

        if (!FakeData.ANDROID15_NON_ROOT_PRIVACY_MODE) {
            hookWifiInfo(lpparam);
            hookWifiScanResults(lpparam);
        } else {
            XposedBridge.log(TAG + "Android15 非 root 模式: 跳过 WiFiInfo/ScanResults 完整伪造，保留系统权限模型");
        }
        hookBatteryManager(lpparam);
        hookSystemSettings(lpparam);
        hookAudioManager(lpparam);
        if (!FakeData.ANDROID15_NON_ROOT_PRIVACY_MODE) {
            hookTelephonyManager(lpparam);
        } else {
            XposedBridge.log(TAG + "Android15 非 root 模式: 跳过 Telephony 状态伪造，仅限制敏感标识");
        }
        hookNetworkInfo(lpparam);
        hookSecurityStatus(lpparam);
        hookTimezone(lpparam);

        XposedBridge.log(TAG + "系统信息 Hook 初始化完成");
    }

    /**
     * 预初始化缓存的伪造对象
     * 必须在 Hook 安装前调用，避免在 Hook 回调中创建对象触发递归
     */
    private static void initCachedObjects() {
        // 预缓存 TimeZone
        String fakeTimezoneId = (HookInit.Timezone_id != null) ? HookInit.Timezone_id : FakeData.TIMEZONE_ID;
        sCachedFakeTimeZone = java.util.TimeZone.getTimeZone(fakeTimezoneId);

        // 预缓存 Locale
        String fakeLanguage = (HookInit.Locale_language != null) ? HookInit.Locale_language : FakeData.LOCALE_LANGUAGE;
        String fakeCountry = (HookInit.Locale_country != null) ? HookInit.Locale_country : FakeData.LOCALE_COUNTRY;
        sCachedFakeLocale = new java.util.Locale(fakeLanguage, fakeCountry);

        verbose("缓存对象已初始化: timezone=" + fakeTimezoneId + ", locale=" + fakeLanguage + "_" + fakeCountry);
    }

    // ==================== WiFi 信息 Hook ====================
    private static void hookWifiInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> wifiInfoClass = XposedHelpers.findClass("android.net.wifi.WifiInfo", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getSSID", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    String fakeValue = (HookInit.Wifi_ssid != null) ? HookInit.Wifi_ssid : FakeData.WIFI_SSID;
                    verbose("getSSID() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getBSSID", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    String fakeValue = (HookInit.Wifi_bssid != null) ? HookInit.Wifi_bssid : FakeData.WIFI_BSSID;
                    verbose("getBSSID() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getIpAddress", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Wifi_ip_address != null) ? HookInit.Wifi_ip_address : FakeData.WIFI_IP_ADDRESS;
                    verbose("getIpAddress() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getRssi", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Wifi_rssi != null) ? HookInit.Wifi_rssi : FakeData.WIFI_RSSI;
                    verbose("getRssi() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getLinkSpeed", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Wifi_link_speed != null) ? HookInit.Wifi_link_speed : FakeData.WIFI_LINK_SPEED;
                    verbose("getLinkSpeed() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getFrequency", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Wifi_frequency != null) ? HookInit.Wifi_frequency : FakeData.WIFI_FREQUENCY;
                    verbose("getFrequency() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(wifiInfoClass, "getNetworkId", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Wifi_network_id != null) ? HookInit.Wifi_network_id : FakeData.WIFI_NETWORK_ID;
                    verbose("getNetworkId() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            verbose("WiFi 信息 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "WiFi 信息 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== WiFi 扫描结果 Hook ====================
    private static void hookWifiScanResults(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> wifiManagerClass = XposedHelpers.findClass("android.net.wifi.WifiManager", lpparam.classLoader);

            // Hook getScanResults() - 返回伪造的周围 WiFi 列表
            XposedHelpers.findAndHookMethod(wifiManagerClass, "getScanResults", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> origList = (java.util.List<Object>) param.getResult();
                        int origCount = (origList != null) ? origList.size() : 0;

                        // 生成伪造的扫描结果列表
                        java.util.List<Object> fakeList = generateFakeScanResults(lpparam.classLoader);

                        verbose("getScanResults() 原始数量: " + origCount + " -> 伪造数量: " + fakeList.size());
                        param.setResult(fakeList);
                    } catch (Exception e) {
                        XposedBridge.log(TAG + "getScanResults() 伪造失败: " + e.getMessage());
                    }
                }
            });

            // Hook startScan() - 返回 true 表示扫描成功
            XposedHelpers.findAndHookMethod(wifiManagerClass, "startScan", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("startScan() 原始值: " + orig + " -> 伪造值: true");
                    param.setResult(true);
                }
            });

            verbose("WiFi 扫描结果 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "WiFi 扫描结果 Hook 失败: " + e.getMessage());
        }
    }

    /**
     * 生成伪造的 WiFi 扫描结果列表
     * 第一个条目为当前连接的 WiFi（与 WifiInfo 一致），其余为随机生成的周围网络
     */
    private static java.util.List<Object> generateFakeScanResults(ClassLoader classLoader) {
        java.util.List<Object> fakeResults = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random();

        // 常见的 WiFi SSID 前缀
        String[] ssidPrefixes = {"TP-Link_", "HUAWEI-", "Xiaomi_", "MERCURY_", "FAST_",
                "Tenda_", "ASUS_", "NETGEAR", "D-Link_", "ZTE_",
                "ChinaNet-", "CMCC-", "Home_", "WiFi_"};

        try {
            Class<?> scanResultClass = XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader);

            // === 第一个条目：当前连接的 WiFi（与 WifiInfo 保持一致）===
            try {
                Object currentAp = scanResultClass.getDeclaredConstructor().newInstance();

                // 使用当前伪造的 WiFi 信息
                String currentSsid = (HookInit.Wifi_ssid != null) ? HookInit.Wifi_ssid : FakeData.WIFI_SSID;
                // 去掉 SSID 两端的引号（ScanResult.SSID 不带引号）
                if (currentSsid.startsWith("\"") && currentSsid.endsWith("\"")) {
                    currentSsid = currentSsid.substring(1, currentSsid.length() - 1);
                }
                String currentBssid = (HookInit.Wifi_bssid != null) ? HookInit.Wifi_bssid : FakeData.WIFI_BSSID;
                int currentRssi = (HookInit.Wifi_rssi != null) ? HookInit.Wifi_rssi : FakeData.WIFI_RSSI;
                int currentFreq = (HookInit.Wifi_frequency != null) ? HookInit.Wifi_frequency : FakeData.WIFI_FREQUENCY;

                XposedHelpers.setObjectField(currentAp, "SSID", currentSsid);
                XposedHelpers.setObjectField(currentAp, "BSSID", currentBssid);
                XposedHelpers.setIntField(currentAp, "level", currentRssi);
                XposedHelpers.setIntField(currentAp, "frequency", currentFreq);
                XposedHelpers.setObjectField(currentAp, "capabilities", "[WPA2-PSK-CCMP][ESS]");
                XposedHelpers.setLongField(currentAp, "timestamp", System.currentTimeMillis() * 1000 - random.nextInt(5000000));

                fakeResults.add(currentAp);
                verbose("生成当前连接 WiFi: SSID=" + currentSsid + ", BSSID=" + currentBssid +
                        ", level=" + currentRssi + "dBm, freq=" + currentFreq + "MHz");
            } catch (Exception e) {
                XposedBridge.log(TAG + "创建当前连接 ScanResult 失败: " + e.getMessage());
            }

            // === 其余条目：随机生成的周围 WiFi ===
            int count = 2 + random.nextInt(3); // 额外 2-4 个

            for (int i = 0; i < count; i++) {
                try {
                    Object scanResult = scanResultClass.getDeclaredConstructor().newInstance();

                    String prefix = ssidPrefixes[random.nextInt(ssidPrefixes.length)];
                    String ssid = prefix + String.format("%04X", random.nextInt(0xFFFF));

                    // BSSID 使用真实厂商 OUI 前缀
                    String bssid = generateFakeBssidWithOui(random);

                    // 信号强度比当前连接的弱 (-50 到 -85 dBm)
                    int level = -50 - random.nextInt(36);

                    // 频率
                    int frequency;
                    if (random.nextBoolean()) {
                        int[] channels24 = {2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462};
                        frequency = channels24[random.nextInt(channels24.length)];
                    } else {
                        int[] channels5 = {5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805};
                        frequency = channels5[random.nextInt(channels5.length)];
                    }

                    String[] capabilities = {"[WPA2-PSK-CCMP][ESS]", "[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][ESS]",
                            "[WPA2-PSK-CCMP][WPS][ESS]", "[ESS]"};
                    String capability = capabilities[random.nextInt(capabilities.length)];

                    XposedHelpers.setObjectField(scanResult, "SSID", ssid);
                    XposedHelpers.setObjectField(scanResult, "BSSID", bssid);
                    XposedHelpers.setIntField(scanResult, "level", level);
                    XposedHelpers.setIntField(scanResult, "frequency", frequency);
                    XposedHelpers.setObjectField(scanResult, "capabilities", capability);

                    long timestamp = System.currentTimeMillis() * 1000 - random.nextInt(60000000);
                    XposedHelpers.setLongField(scanResult, "timestamp", timestamp);

                    fakeResults.add(scanResult);

                    verbose("生成伪造 WiFi: SSID=" + ssid + ", BSSID=" + bssid +
                            ", level=" + level + "dBm, freq=" + frequency + "MHz");

                } catch (Exception e) {
                    XposedBridge.log(TAG + "创建 ScanResult 失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + "获取 ScanResult 类失败: " + e.getMessage());
        }

        return fakeResults;
    }

    /**
     * 使用真实厂商 OUI 前缀生成伪造的 BSSID
     */
    private static final byte[][] ROUTER_OUI_LIST = {
            // TP-Link
            {(byte) 0x50, (byte) 0xC7, (byte) 0xBF},
            {(byte) 0xEC, (byte) 0x08, (byte) 0x6B},
            // Huawei
            {(byte) 0x48, (byte) 0xDB, (byte) 0x50},
            {(byte) 0x88, (byte) 0x66, (byte) 0x39},
            // Xiaomi
            {(byte) 0x28, (byte) 0x6C, (byte) 0x07},
            {(byte) 0x64, (byte) 0xCC, (byte) 0x2E},
            // Netgear
            {(byte) 0xB0, (byte) 0x7F, (byte) 0xB9},
            // D-Link
            {(byte) 0xC8, (byte) 0xD3, (byte) 0xA3},
            // ASUS
            {(byte) 0x04, (byte) 0xD4, (byte) 0xC4},
            // ZTE
            {(byte) 0x54, (byte) 0x22, (byte) 0xF8},
    };

    private static String generateFakeBssidWithOui(java.util.Random random) {
        byte[] oui = ROUTER_OUI_LIST[random.nextInt(ROUTER_OUI_LIST.length)];
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                oui[0] & 0xFF, oui[1] & 0xFF, oui[2] & 0xFF,
                random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    // ==================== 电池信息 Hook ====================
    private static void hookBatteryManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> batteryManagerClass = XposedHelpers.findClass("android.os.BatteryManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(batteryManagerClass, "getIntProperty", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int property = (int) param.args[0];
                    Object orig = param.getResult();
                    switch (property) {
                        case 4: // BATTERY_PROPERTY_CAPACITY
                            int fakeCap = (HookInit.Battery_capacity != null) ? HookInit.Battery_capacity : FakeData.BATTERY_CAPACITY;
                            verbose("getIntProperty(CAPACITY) 原始值: " + orig + " -> 伪造值: " + fakeCap);
                            param.setResult(fakeCap);
                            break;
                        case 1: // BATTERY_PROPERTY_CHARGE_COUNTER — Android 15 可能通过 getIntProperty 调用
                            long chargeVal = (HookInit.Battery_charge_counter != null) ? HookInit.Battery_charge_counter : FakeData.BATTERY_CHARGE_COUNTER;
                            verbose("getIntProperty(CHARGE_COUNTER) 原始值: " + orig + " -> 伪造值: " + (int) chargeVal);
                            param.setResult((int) chargeVal);
                            break;
                        case 2: // BATTERY_PROPERTY_CURRENT_NOW
                            long currNowVal = (HookInit.Battery_current_now != null) ? HookInit.Battery_current_now : FakeData.BATTERY_CURRENT_NOW;
                            verbose("getIntProperty(CURRENT_NOW) 原始值: " + orig + " -> 伪造值: " + (int) currNowVal);
                            param.setResult((int) currNowVal);
                            break;
                        case 3: // BATTERY_PROPERTY_CURRENT_AVERAGE
                            long currAvgVal = (HookInit.Battery_current_average != null) ? HookInit.Battery_current_average : FakeData.BATTERY_CURRENT_AVERAGE;
                            verbose("getIntProperty(CURRENT_AVERAGE) 原始值: " + orig + " -> 伪造值: " + (int) currAvgVal);
                            param.setResult((int) currAvgVal);
                            break;
                        case 5: // BATTERY_PROPERTY_ENERGY_COUNTER
                            long energyVal = (HookInit.Battery_energy_counter != null) ? HookInit.Battery_energy_counter : FakeData.BATTERY_ENERGY_COUNTER;
                            verbose("getIntProperty(ENERGY_COUNTER) 原始值: " + orig + " -> 伪造值: " + (int) energyVal);
                            param.setResult((int) energyVal);
                            break;
                    }
                }
            });

            XposedHelpers.findAndHookMethod(batteryManagerClass, "getLongProperty", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int property = (int) param.args[0];
                    Object orig = param.getResult();
                    switch (property) {
                        case 1: // BATTERY_PROPERTY_CHARGE_COUNTER
                            long chargeCounter = (HookInit.Battery_charge_counter != null) ? HookInit.Battery_charge_counter : FakeData.BATTERY_CHARGE_COUNTER;
                            verbose("getLongProperty(CHARGE_COUNTER) 原始值: " + orig + " -> 伪造值: " + chargeCounter);
                            param.setResult(chargeCounter);
                            break;
                        case 2: // BATTERY_PROPERTY_CURRENT_NOW
                            long currentNow = (HookInit.Battery_current_now != null) ? HookInit.Battery_current_now : FakeData.BATTERY_CURRENT_NOW;
                            verbose("getLongProperty(CURRENT_NOW) 原始值: " + orig + " -> 伪造值: " + currentNow);
                            param.setResult(currentNow);
                            break;
                        case 3: // BATTERY_PROPERTY_CURRENT_AVERAGE
                            long currentAvg = (HookInit.Battery_current_average != null) ? HookInit.Battery_current_average : FakeData.BATTERY_CURRENT_AVERAGE;
                            verbose("getLongProperty(CURRENT_AVERAGE) 原始值: " + orig + " -> 伪造值: " + currentAvg);
                            param.setResult(currentAvg);
                            break;
                        case 5: // BATTERY_PROPERTY_ENERGY_COUNTER
                            long energyCounter = (HookInit.Battery_energy_counter != null) ? HookInit.Battery_energy_counter : FakeData.BATTERY_ENERGY_COUNTER;
                            verbose("getLongProperty(ENERGY_COUNTER) 原始值: " + orig + " -> 伪造值: " + energyCounter);
                            param.setResult(energyCounter);
                            break;
                    }
                }
            });

            verbose("电池信息 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "电池信息 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 系统设置 Hook ====================
    private static void hookSystemSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Settings.Global.getInt
            XposedHelpers.findAndHookMethod("android.provider.Settings$Global", lpparam.classLoader,
                    "getInt", android.content.ContentResolver.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[1];
                            Object orig = param.getResult();
                            switch (name) {
                                case "airplane_mode_on":
                                    verbose("Global.getInt(airplane_mode_on) 原始值: " + orig + " -> 伪造值: " + FakeData.AIRPLANE_MODE);
                                    param.setResult(FakeData.AIRPLANE_MODE);
                                    break;
                                case "wifi_on":
                                    verbose("Global.getInt(wifi_on) 原始值: " + orig + " -> 伪造值: " + FakeData.WIFI_ON);
                                    param.setResult(FakeData.WIFI_ON);
                                    break;
                                case "bluetooth_on":
                                    verbose("Global.getInt(bluetooth_on) 原始值: " + orig + " -> 伪造值: " + FakeData.BLUETOOTH_ON);
                                    param.setResult(FakeData.BLUETOOTH_ON);
                                    break;
                                case "adb_enabled":
                                    verbose("Global.getInt(adb_enabled) 原始值: " + orig + " -> 伪造值: " + FakeData.ADB_ENABLED);
                                    param.setResult(FakeData.ADB_ENABLED);
                                    break;
                                case "development_settings_enabled":
                                    verbose("Global.getInt(development_settings_enabled) 原始值: " + orig + " -> 伪造值: " + FakeData.DEVELOPMENT_SETTINGS_ENABLED);
                                    param.setResult(FakeData.DEVELOPMENT_SETTINGS_ENABLED);
                                    break;
                                case "install_non_market_apps":
                                    verbose("Global.getInt(install_non_market_apps) 原始值: " + orig + " -> 伪造值: " + FakeData.INSTALL_NON_MARKET_APPS);
                                    param.setResult(FakeData.INSTALL_NON_MARKET_APPS);
                                    break;
                            }
                        }
                    });

            // Hook Settings.System.getInt
            XposedHelpers.findAndHookMethod("android.provider.Settings$System", lpparam.classLoader,
                    "getInt", android.content.ContentResolver.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[1];
                            Object orig = param.getResult();
                            switch (name) {
                                case "screen_brightness":
                                    verbose("System.getInt(screen_brightness) 原始值: " + orig + " -> 伪造值: " + FakeData.SCREEN_BRIGHTNESS);
                                    param.setResult(FakeData.SCREEN_BRIGHTNESS);
                                    break;
                                case "screen_brightness_mode":
                                    verbose("System.getInt(screen_brightness_mode) 原始值: " + orig + " -> 伪造值: " + FakeData.SCREEN_BRIGHTNESS_MODE);
                                    param.setResult(FakeData.SCREEN_BRIGHTNESS_MODE);
                                    break;
                            }
                        }
                    });

            // Hook Settings.Secure.getInt
            XposedHelpers.findAndHookMethod("android.provider.Settings$Secure", lpparam.classLoader,
                    "getInt", android.content.ContentResolver.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[1];
                            Object orig = param.getResult();
                            switch (name) {
                                case "accessibility_enabled":
                                    verbose("Secure.getInt(accessibility_enabled) 原始值: " + orig + " -> 伪造值: " + FakeData.ACCESSIBILITY_ENABLED);
                                    param.setResult(FakeData.ACCESSIBILITY_ENABLED);
                                    break;
                                case "location_mode":
                                    verbose("Secure.getInt(location_mode) 原始值: " + orig + " -> 伪造值: " + FakeData.LOCATION_MODE);
                                    param.setResult(FakeData.LOCATION_MODE);
                                    break;
                            }
                        }
                    });

            verbose("系统设置 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "系统设置 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 音频设置 Hook ====================
    private static void hookAudioManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> audioManagerClass = XposedHelpers.findClass("android.media.AudioManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(audioManagerClass, "getRingerMode", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Ringer_mode != null) ? HookInit.Ringer_mode : FakeData.RINGER_MODE;
                    verbose("getRingerMode() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(audioManagerClass, "getStreamVolume", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int streamType = (int) param.args[0];
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Stream_volume != null) ? HookInit.Stream_volume : FakeData.STREAM_VOLUME;
                    verbose("getStreamVolume(streamType=" + streamType + ") 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            XposedHelpers.findAndHookMethod(audioManagerClass, "getStreamMaxVolume", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int streamType = (int) param.args[0];
                    Object orig = param.getResult();
                    int fakeValue = (HookInit.Stream_max_volume != null) ? HookInit.Stream_max_volume : FakeData.STREAM_MAX_VOLUME;
                    verbose("getStreamMaxVolume(streamType=" + streamType + ") 原始值: " + orig + " -> 伪造值: " + fakeValue);
                    param.setResult(fakeValue);
                }
            });

            verbose("音频设置 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "音频设置 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== TelephonyManager Hook ====================
    private static void hookTelephonyManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> telephonyClass = XposedHelpers.findClass("android.telephony.TelephonyManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(telephonyClass, "getSimState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getSimState() 原始值: " + orig + " -> 伪造值: " + FakeData.SIM_STATE);
                    param.setResult(FakeData.SIM_STATE);
                }
            });

            XposedHelpers.findAndHookMethod(telephonyClass, "getPhoneType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getPhoneType() 原始值: " + orig + " -> 伪造值: " + FakeData.PHONE_TYPE);
                    param.setResult(FakeData.PHONE_TYPE);
                }
            });

            XposedHelpers.findAndHookMethod(telephonyClass, "getNetworkType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getNetworkType() 原始值: " + orig + " -> 伪造值: " + FakeData.NETWORK_TYPE);
                    param.setResult(FakeData.NETWORK_TYPE);
                }
            });

            XposedHelpers.findAndHookMethod(telephonyClass, "getDataState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getDataState() 原始值: " + orig + " -> 伪造值: " + FakeData.DATA_STATE);
                    param.setResult(FakeData.DATA_STATE);
                }
            });

            XposedHelpers.findAndHookMethod(telephonyClass, "getCallState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getCallState() 原始值: " + orig + " -> 伪造值: " + FakeData.CALL_STATE);
                    param.setResult(FakeData.CALL_STATE);
                }
            });

            verbose("TelephonyManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "TelephonyManager Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 网络状态 Hook ====================
    private static void hookNetworkInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> networkInfoClass = XposedHelpers.findClass("android.net.NetworkInfo", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(networkInfoClass, "getType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getType() 原始值: " + orig + " -> 伪造值: " + FakeData.NETWORK_INFO_TYPE);
                    param.setResult(FakeData.NETWORK_INFO_TYPE);
                }
            });

            XposedHelpers.findAndHookMethod(networkInfoClass, "getTypeName", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("getTypeName() 原始值: " + orig + " -> 伪造值: " + FakeData.NETWORK_TYPE_NAME);
                    param.setResult(FakeData.NETWORK_TYPE_NAME);
                }
            });

            XposedHelpers.findAndHookMethod(networkInfoClass, "isConnected", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("isConnected() 原始值: " + orig + " -> 伪造值: " + FakeData.NETWORK_CONNECTED);
                    param.setResult(FakeData.NETWORK_CONNECTED);
                }
            });

            XposedHelpers.findAndHookMethod(networkInfoClass, "isAvailable", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("isAvailable() 原始值: " + orig + " -> 伪造值: " + FakeData.NETWORK_AVAILABLE);
                    param.setResult(FakeData.NETWORK_AVAILABLE);
                }
            });

            XposedHelpers.findAndHookMethod(networkInfoClass, "isRoaming", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("isRoaming() 原始值: " + orig + " -> 伪造值: " + FakeData.NETWORK_ROAMING);
                    param.setResult(FakeData.NETWORK_ROAMING);
                }
            });

            verbose("网络状态 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "网络状态 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 安全状态 Hook ====================
    private static void hookSecurityStatus(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook KeyguardManager
            Class<?> keyguardClass = XposedHelpers.findClass("android.app.KeyguardManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(keyguardClass, "isKeyguardLocked", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("isKeyguardLocked() 原始值: " + orig + " -> 伪造值: " + FakeData.KEYGUARD_LOCKED);
                    param.setResult(FakeData.KEYGUARD_LOCKED);
                }
            });

            XposedHelpers.findAndHookMethod(keyguardClass, "isDeviceSecure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    verbose("isDeviceSecure() 原始值: " + orig + " -> 伪造值: " + FakeData.DEVICE_SECURE);
                    param.setResult(FakeData.DEVICE_SECURE);
                }
            });

            // Hook ActivityManager.isUserAMonkey
            XposedHelpers.findAndHookMethod("android.app.ActivityManager", lpparam.classLoader,
                    "isUserAMonkey", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object orig = param.getResult();
                            verbose("isUserAMonkey() 原始值: " + orig + " -> 伪造值: " + FakeData.IS_USER_A_MONKEY);
                            param.setResult(FakeData.IS_USER_A_MONKEY);
                        }
                    });

            verbose("安全状态 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "安全状态 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 时区与语言 Hook ====================
    private static void hookTimezone(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook TimeZone.getDefault() — 带递归保护
            XposedHelpers.findAndHookMethod("java.util.TimeZone", lpparam.classLoader,
                    "getDefault", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sInTimezoneHook.get()) return; // 递归保护
                            sInTimezoneHook.set(true);
                            try {
                                Object orig = param.getResult();
                                String origId = (orig != null) ? ((java.util.TimeZone) orig).getID() : "null";
                                java.util.TimeZone fakeTimeZone = sCachedFakeTimeZone;
                                verbose("TimeZone.getDefault() 原始值: " + origId + " -> 伪造值: " + fakeTimeZone.getID());
                                param.setResult(fakeTimeZone);
                            } finally {
                                sInTimezoneHook.set(false);
                            }
                        }
                    });

            // Hook TimeZone.getID() — 带递归保护
            XposedHelpers.findAndHookMethod("java.util.TimeZone", lpparam.classLoader,
                    "getID", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sInTimezoneHook.get()) return;
                            sInTimezoneHook.set(true);
                            try {
                                Object orig = param.getResult();
                                String fakeValue = (HookInit.Timezone_id != null) ? HookInit.Timezone_id : FakeData.TIMEZONE_ID;
                                verbose("TimeZone.getID() 原始值: " + orig + " -> 伪造值: " + fakeValue);
                                param.setResult(fakeValue);
                            } finally {
                                sInTimezoneHook.set(false);
                            }
                        }
                    });

            // Hook TimeZone.getRawOffset() — Hook 具体实现类而非抽象方法
            // java.util.SimpleTimeZone 是 TimeZone 的具体实现
            try {
                XposedHelpers.findAndHookMethod("java.util.SimpleTimeZone", lpparam.classLoader,
                        "getRawOffset", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (sInTimezoneHook.get()) return;
                                sInTimezoneHook.set(true);
                                try {
                                    Object orig = param.getResult();
                                    int fakeValue = (HookInit.Timezone_raw_offset != null) ? HookInit.Timezone_raw_offset : FakeData.TIMEZONE_RAW_OFFSET;
                                    param.setResult(fakeValue);
                                } finally {
                                    sInTimezoneHook.set(false);
                                }
                            }
                        });
                verbose("Hook SimpleTimeZone.getRawOffset() 成功");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Hook SimpleTimeZone.getRawOffset() 失败: " + t.getMessage());
            }

            // Android 内部使用 libcore.util.ZoneInfo 作为 TimeZone 的实现
            try {
                Class<?> zoneInfoClass = XposedHelpers.findClassIfExists("libcore.util.ZoneInfo", lpparam.classLoader);
                if (zoneInfoClass != null) {
                    XposedHelpers.findAndHookMethod(zoneInfoClass, "getRawOffset", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sInTimezoneHook.get()) return;
                            sInTimezoneHook.set(true);
                            try {
                                Object orig = param.getResult();
                                int fakeValue = (HookInit.Timezone_raw_offset != null) ? HookInit.Timezone_raw_offset : FakeData.TIMEZONE_RAW_OFFSET;
                                param.setResult(fakeValue);
                            } finally {
                                sInTimezoneHook.set(false);
                            }
                        }
                    });
                    verbose("Hook ZoneInfo.getRawOffset() 成功");
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Hook ZoneInfo.getRawOffset() 失败: " + t.getMessage());
            }

            // Hook Locale.getDefault() — 带递归保护
            XposedHelpers.findAndHookMethod("java.util.Locale", lpparam.classLoader,
                    "getDefault", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sInLocaleHook.get()) return;
                            sInLocaleHook.set(true);
                            try {
                                Object orig = param.getResult();
                                java.util.Locale fakeLocale = sCachedFakeLocale;
                                param.setResult(fakeLocale);
                            } finally {
                                sInLocaleHook.set(false);
                            }
                        }
                    });

            // Hook Locale.getLanguage() — 带递归保护
            XposedHelpers.findAndHookMethod("java.util.Locale", lpparam.classLoader,
                    "getLanguage", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sInLocaleHook.get()) return;
                            sInLocaleHook.set(true);
                            try {
                                String fakeValue = (HookInit.Locale_language != null) ? HookInit.Locale_language : FakeData.LOCALE_LANGUAGE;
                                param.setResult(fakeValue);
                            } finally {
                                sInLocaleHook.set(false);
                            }
                        }
                    });

            // Hook Locale.getCountry() — 带递归保护
            XposedHelpers.findAndHookMethod("java.util.Locale", lpparam.classLoader,
                    "getCountry", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sInLocaleHook.get()) return;
                            sInLocaleHook.set(true);
                            try {
                                String fakeValue = (HookInit.Locale_country != null) ? HookInit.Locale_country : FakeData.LOCALE_COUNTRY;
                                param.setResult(fakeValue);
                            } finally {
                                sInLocaleHook.set(false);
                            }
                        }
                    });

            verbose("时区与语言 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "时区与语言 Hook 失败: " + e.getMessage());
        }
    }
}
