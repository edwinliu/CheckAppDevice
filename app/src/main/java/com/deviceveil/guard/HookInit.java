package com.deviceveil.guard;

import static com.deviceveil.guard.RandomIdGenerator.generate16CharAndroidId;
import static com.deviceveil.guard.RandomIdGenerator.generateBootId;

import static com.deviceveil.guard.RandomIdGenerator.generateRandomBluetoothAddress;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomTimestampInLast2Days;
import static com.deviceveil.guard.RandomIdGenerator.generateRecentTimestampSeconds;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomDrmId;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiMac;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomGaid;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomOaid;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomFirebaseId;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomFcmToken;
import static com.deviceveil.guard.RandomIdGenerator.bytesToHexString;
import static com.deviceveil.guard.RandomIdGenerator.hexStringToBytes;
import static com.deviceveil.guard.RandomIdGenerator.macBytesToString;
// WiFi 信息生成
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiSsid;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiBssid;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiIpAddress;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiRssi;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiLinkSpeed;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiFrequency;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomWifiNetworkId;
// 电池信息生成
import static com.deviceveil.guard.RandomIdGenerator.generateRandomBatteryCapacity;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomBatteryChargeCounter;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomBatteryCurrentNow;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomBatteryCurrentAverage;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomBatteryEnergyCounter;
// 音频设置生成
import static com.deviceveil.guard.RandomIdGenerator.generateRandomRingerMode;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomStreamVolume;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomStreamMaxVolume;
// 时区与语言生成
import static com.deviceveil.guard.RandomIdGenerator.generateRandomTimezoneId;
import static com.deviceveil.guard.RandomIdGenerator.generateTimezoneRawOffset;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomLocaleLanguage;
import static com.deviceveil.guard.RandomIdGenerator.generateRandomLocaleCountry;

import android.app.Application;
import android.util.Log;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.deviceveil.guard.handler.DeviceInfoHandler;
import com.virjar.sekiro.business.api.SekiroClient;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
// import de.robv.android.xposed.IXposedHookZygoteInit;  // 已由 Native 层和 FakeDeviceModule 覆盖
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.google.gson.reflect.TypeToken;


public class HookInit implements IXposedHookLoadPackage /* , IXposedHookZygoteInit */ {

    /*
     * Zygote 层 DRM Hook - 已由 Native 层和 FakeDeviceModule 覆盖，暂时禁用
     *
    @Override
    public void initZygote(StartupParam startupParam) {
        XposedBridge.log("[设备信息记录]---[HookInit] initZygote called - Zygote 层 DRM Hook");

        // Zygote 层 Hook MediaDrm，确保在应用加载前就生效
        XposedHelpers.findAndHookMethod(
                "android.media.MediaDrm",
                null,
                "getPropertyByteArray",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String name = (String) param.args[0];
                        byte[] origResult = (byte[]) param.getResult();

                        if ("deviceUniqueId".equals(name)) {
                            // 确保伪造设备信息已加载
                            ensureFakeDeviceInfo();

                            // 优先使用动态配置，回退到静态默认值
                            byte[] fakeDrmId = (Drm_id != null) ? Drm_id : FakeData.FAKE_DRM_ID;

                            String origHex = bytesToHex(origResult);
                            String fakeHex = bytesToHex(fakeDrmId);

                            XposedBridge.log("[设备信息记录]---[HookInit] [Zygote] MediaDrm.getPropertyByteArray(deviceUniqueId)");
                            XposedBridge.log("[设备信息记录]---[HookInit]   原始 DRM ID: " + origHex);
                            XposedBridge.log("[设备信息记录]---[HookInit]   伪造 DRM ID: " + fakeHex +
                                    (Drm_id != null ? " (from file)" : " (default)"));

                            param.setResult(fakeDrmId);
                        }
                    }
                }
        );
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    */

    public static final String TAG = "[设备信息记录]---[HookInit] ";

    // 伪造设备信息（线程安全）
    public static volatile String Android_id = null;
    public static volatile String bluetooth_address = null;
    public static volatile String pm = null;
    public static volatile String Boot_id = null;
    public static volatile Long Install_time = null;
    public static volatile Long Update_time = null;  // app 最后更新时间（毫秒）

    public static volatile long FAKE_BOOT_TIME = 1L; // 伪造的开机时间（秒）

    // 新增：DRM ID 和 WiFi MAC（用于 Native 层同步）
    public static volatile byte[] Drm_id = null;
    public static volatile byte[] Wifi_mac = null;

    // 新增：GAID 和 OAID（广告标识符）
    public static volatile String Gaid = null;
    public static volatile String Oaid = null;

    // 新增：App Set ID 和 Firebase ID（Android 15 新 API）
    public static volatile String App_set_id = null;
    public static volatile String Firebase_id = null;
    public static volatile String Fcm_token = null;

    // ==================== WiFi 信息（动态生成）====================
    public static volatile String Wifi_ssid = null;
    public static volatile String Wifi_bssid = null;
    public static volatile Integer Wifi_ip_address = null;
    public static volatile Integer Wifi_rssi = null;
    public static volatile Integer Wifi_link_speed = null;
    public static volatile Integer Wifi_frequency = null;
    public static volatile Integer Wifi_network_id = null;

    // ==================== 电池信息（动态生成）====================
    public static volatile Integer Battery_capacity = null;
    public static volatile Long Battery_charge_counter = null;
    public static volatile Long Battery_current_now = null;
    public static volatile Long Battery_current_average = null;
    public static volatile Long Battery_energy_counter = null;

    // ==================== 音频设置（动态生成）====================
    public static volatile Integer Ringer_mode = null;
    public static volatile Integer Stream_volume = null;
    public static volatile Integer Stream_max_volume = null;

    // ==================== 时区与语言（动态生成）====================
    public static volatile String Timezone_id = null;
    public static volatile Integer Timezone_raw_offset = null;
    public static volatile String Locale_language = null;
    public static volatile String Locale_country = null;


    private static volatile SekiroClient sekiroClient = null;
    private static volatile boolean sekiroInitialized = false;


    // 防止重复初始化
    private static final Object LOCK = new Object();


    private static void ensureFakeDeviceInfo(long profileResetToken) {
        synchronized (LOCK) {
            // 判断是否需要加载或生成
            if (Android_id != null &&
                    bluetooth_address != null &&
                    Boot_id != null &&
                    Install_time != null &&
                    FAKE_BOOT_TIME != 1L &&
                    Drm_id != null &&
                    Wifi_mac != null &&
                    Gaid != null &&
                    Oaid != null &&
                    Wifi_ssid != null &&
                    Wifi_bssid != null &&
                    Wifi_ip_address != null &&
                    Battery_capacity != null &&
                    Ringer_mode != null &&
                    Timezone_id != null) {
                return; // 所有字段均已初始化
            }

            File file = new File(Constants.getFakeDeviceInfoPath());
            Map<String, Object> dataFromJson = null;

            // 尝试从文件读取
            if (file.exists()) {
                try {
                    String content = FileUtils.readFileToString(file, "UTF-8");
                    if (content != null && !content.trim().equals("404")) {
                        Gson gson = new Gson();
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        dataFromJson = gson.fromJson(content, type);
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + "⚠️ 读取 fake_device_info.json 异常: " + e.getMessage());
                }
            }

            if (shouldResetProfile(dataFromJson, profileResetToken)) {
                XposedBridge.log(TAG + "🔄 收到设备画像重置请求，删除旧 fake_device_info.json 后重新生成");
                resetFakeDeviceInfoFields();
                if (file.exists() && !file.delete()) {
                    XposedBridge.log(TAG + "⚠️ 删除 fake_device_info.json 失败，将覆盖写入新画像");
                }
                dataFromJson = null;
            }

            if (dataFromJson != null) {
                try {
                    String loadedAndroidId = (String) dataFromJson.get("android_id");
                    String loadedBootId = (String) dataFromJson.get("boot_id");
                    String loadedBtAddr = (String) dataFromJson.get("bluetooth_address");
                    String loadedDrmIdHex = (String) dataFromJson.get("drm_id");
                    String loadedWifiMacHex = (String) dataFromJson.get("wifi_mac");
                    String loadedGaid = (String) dataFromJson.get("gaid");
                    String loadedOaid = (String) dataFromJson.get("oaid");
                    // Android 15 新 API ID
                    String loadedAppSetId = (String) dataFromJson.get("app_set_id");
                    String loadedFirebaseId = (String) dataFromJson.get("firebase_id");
                    String loadedFcmToken = (String) dataFromJson.get("fcm_token");
                    // WiFi 信息
                    String loadedWifiSsid = (String) dataFromJson.get("wifi_ssid");
                    String loadedWifiBssid = (String) dataFromJson.get("wifi_bssid");
                    Long loadedInstallTime = null;
                    Long loadedUpdateTime = null;
                    Long loadedFakeBootTime = null;
                    Integer loadedWifiIpAddress = null;
                    Integer loadedWifiRssi = null;
                    Integer loadedWifiLinkSpeed = null;
                    Integer loadedWifiFrequency = null;
                    Integer loadedWifiNetworkId = null;
                    // 电池信息
                    Integer loadedBatteryCapacity = null;
                    Long loadedBatteryChargeCounter = null;
                    Long loadedBatteryCurrentNow = null;
                    Long loadedBatteryCurrentAverage = null;
                    Long loadedBatteryEnergyCounter = null;
                    // 音频设置
                    Integer loadedRingerMode = null;
                    Integer loadedStreamVolume = null;
                    Integer loadedStreamMaxVolume = null;

                    // JSON 中数字默认反序列化为 Double，需转换
                    Object installTimeObj = dataFromJson.get("install_time");
                    if (installTimeObj instanceof Number) {
                        loadedInstallTime = ((Number) installTimeObj).longValue();
                    }

                    Object updateTimeObj = dataFromJson.get("update_time");
                    if (updateTimeObj instanceof Number) {
                        loadedUpdateTime = ((Number) updateTimeObj).longValue();
                    }

                    Object fakeBootTimeObj = dataFromJson.get("fake_boot_time");
                    if (fakeBootTimeObj instanceof Number) {
                        loadedFakeBootTime = ((Number) fakeBootTimeObj).longValue();
                    }

                    // 解析 WiFi 信息
                    Object wifiIpObj = dataFromJson.get("wifi_ip_address");
                    if (wifiIpObj instanceof Number) loadedWifiIpAddress = ((Number) wifiIpObj).intValue();
                    Object wifiRssiObj = dataFromJson.get("wifi_rssi");
                    if (wifiRssiObj instanceof Number) loadedWifiRssi = ((Number) wifiRssiObj).intValue();
                    Object wifiLinkSpeedObj = dataFromJson.get("wifi_link_speed");
                    if (wifiLinkSpeedObj instanceof Number) loadedWifiLinkSpeed = ((Number) wifiLinkSpeedObj).intValue();
                    Object wifiFreqObj = dataFromJson.get("wifi_frequency");
                    if (wifiFreqObj instanceof Number) loadedWifiFrequency = ((Number) wifiFreqObj).intValue();
                    Object wifiNetIdObj = dataFromJson.get("wifi_network_id");
                    if (wifiNetIdObj instanceof Number) loadedWifiNetworkId = ((Number) wifiNetIdObj).intValue();

                    // 解析电池信息
                    Object battCapObj = dataFromJson.get("battery_capacity");
                    if (battCapObj instanceof Number) loadedBatteryCapacity = ((Number) battCapObj).intValue();
                    Object battChargeObj = dataFromJson.get("battery_charge_counter");
                    if (battChargeObj instanceof Number) loadedBatteryChargeCounter = ((Number) battChargeObj).longValue();
                    Object battCurrNowObj = dataFromJson.get("battery_current_now");
                    if (battCurrNowObj instanceof Number) loadedBatteryCurrentNow = ((Number) battCurrNowObj).longValue();
                    Object battCurrAvgObj = dataFromJson.get("battery_current_average");
                    if (battCurrAvgObj instanceof Number) loadedBatteryCurrentAverage = ((Number) battCurrAvgObj).longValue();
                    Object battEnergyObj = dataFromJson.get("battery_energy_counter");
                    if (battEnergyObj instanceof Number) loadedBatteryEnergyCounter = ((Number) battEnergyObj).longValue();

                    // 解析音频设置
                    Object ringerObj = dataFromJson.get("ringer_mode");
                    if (ringerObj instanceof Number) loadedRingerMode = ((Number) ringerObj).intValue();
                    Object streamVolObj = dataFromJson.get("stream_volume");
                    if (streamVolObj instanceof Number) loadedStreamVolume = ((Number) streamVolObj).intValue();
                    Object streamMaxObj = dataFromJson.get("stream_max_volume");
                    if (streamMaxObj instanceof Number) loadedStreamMaxVolume = ((Number) streamMaxObj).intValue();

                    // 解析时区与语言
                    String loadedTimezoneId = (String) dataFromJson.get("timezone_id");
                    Integer loadedTimezoneRawOffset = null;
                    String loadedLocaleLanguage = (String) dataFromJson.get("locale_language");
                    String loadedLocaleCountry = (String) dataFromJson.get("locale_country");
                    Object tzOffsetObj = dataFromJson.get("timezone_raw_offset");
                    if (tzOffsetObj instanceof Number) loadedTimezoneRawOffset = ((Number) tzOffsetObj).intValue();

                    // 解析 DRM ID 和 WiFi MAC
                    byte[] loadedDrmId = null;
                    byte[] loadedWifiMac = null;
                    if (loadedDrmIdHex != null && loadedDrmIdHex.length() == 64) {
                        loadedDrmId = hexStringToBytes(loadedDrmIdHex);
                    }
                    if (loadedWifiMacHex != null && loadedWifiMacHex.length() == 12) {
                        loadedWifiMac = hexStringToBytes(loadedWifiMacHex);
                    }

                    boolean validAndroidId = isAndroidIdString(loadedAndroidId);
                    if (!validAndroidId) {
                        XposedBridge.log(TAG + "⚠️ fake_device_info.json 中 android_id 不是 16 位 hex 格式，将重新生成");
                    }

                    // 验证基础字段有效（WiFi/电池/音频为可选，向后兼容）
                    if (loadedAndroidId != null &&
                            validAndroidId &&
                            loadedBootId != null &&
                            loadedBtAddr != null &&
                            loadedInstallTime != null &&
                            loadedFakeBootTime != null &&
                            loadedFakeBootTime != 1L &&
                            loadedDrmId != null && loadedDrmId.length == 32 &&
                            loadedWifiMac != null && loadedWifiMac.length == 6 &&
                            loadedGaid != null &&
                            loadedOaid != null) {

                        Android_id = loadedAndroidId;
                        Boot_id = loadedBootId;
                        bluetooth_address = loadedBtAddr;
                        Install_time = loadedInstallTime;
                        if (loadedUpdateTime != null) Update_time = loadedUpdateTime;
                        FAKE_BOOT_TIME = loadedFakeBootTime;
                        Drm_id = loadedDrmId;
                        Wifi_mac = loadedWifiMac;
                        Gaid = loadedGaid;
                        Oaid = loadedOaid;

                        // 加载 Android 15 新 API ID（如果存在）
                        if (loadedAppSetId != null) App_set_id = loadedAppSetId;
                        if (loadedFirebaseId != null) Firebase_id = loadedFirebaseId;
                        if (loadedFcmToken != null) Fcm_token = loadedFcmToken;

                        // 加载 WiFi 信息（如果存在）
                        if (loadedWifiSsid != null) Wifi_ssid = loadedWifiSsid;
                        if (loadedWifiBssid != null) Wifi_bssid = loadedWifiBssid;
                        if (loadedWifiIpAddress != null) Wifi_ip_address = loadedWifiIpAddress;
                        if (loadedWifiRssi != null) Wifi_rssi = loadedWifiRssi;
                        if (loadedWifiLinkSpeed != null) Wifi_link_speed = loadedWifiLinkSpeed;
                        if (loadedWifiFrequency != null) Wifi_frequency = loadedWifiFrequency;
                        if (loadedWifiNetworkId != null) Wifi_network_id = loadedWifiNetworkId;

                        // 加载电池信息（如果存在）
                        if (loadedBatteryCapacity != null) Battery_capacity = loadedBatteryCapacity;
                        if (loadedBatteryChargeCounter != null) Battery_charge_counter = loadedBatteryChargeCounter;
                        if (loadedBatteryCurrentNow != null) Battery_current_now = loadedBatteryCurrentNow;
                        if (loadedBatteryCurrentAverage != null) Battery_current_average = loadedBatteryCurrentAverage;
                        if (loadedBatteryEnergyCounter != null) Battery_energy_counter = loadedBatteryEnergyCounter;

                        // 加载音频设置（如果存在）
                        if (loadedRingerMode != null) Ringer_mode = loadedRingerMode;
                        if (loadedStreamVolume != null) Stream_volume = loadedStreamVolume;
                        if (loadedStreamMaxVolume != null) Stream_max_volume = loadedStreamMaxVolume;

                        // 加载时区与语言（如果存在）
                        if (loadedTimezoneId != null) Timezone_id = loadedTimezoneId;
                        if (loadedTimezoneRawOffset != null) Timezone_raw_offset = loadedTimezoneRawOffset;
                        if (loadedLocaleLanguage != null) Locale_language = loadedLocaleLanguage;
                        if (loadedLocaleCountry != null) Locale_country = loadedLocaleCountry;

                        XposedBridge.log(TAG + "✅ 从文件成功加载伪造设备信息");
                        XposedBridge.log(TAG + "  gaid=" + Constants.maskValue(Gaid));
                        XposedBridge.log(TAG + "  oaid=" + Constants.maskValue(Oaid));

                        // 如果 WiFi/电池/音频/时区信息缺失，需要生成
                        boolean needGenerate = (Wifi_ssid == null || Battery_capacity == null || Ringer_mode == null || Timezone_id == null);
                        if (!needGenerate) {
                            return;
                        }
                        XposedBridge.log(TAG + "⚠️ WiFi/电池/音频信息缺失，将补充生成");
                    } else {
                        XposedBridge.log(TAG + "⚠️ 文件中设备信息不完整，将重新生成");
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + "⚠️ 解析 fake_device_info.json 出错: " + e.getMessage());
                }
            }

            // 生成新数据（仅生成缺失的字段）
            if (Android_id == null || !isAndroidIdString(Android_id)) Android_id = generate16CharAndroidId();
            if (Install_time == null) Install_time = generateRandomTimestampInLast2Days();
            // Update_time 应该在 Install_time 之后，生成一个在 Install_time 到当前时间之间的随机时间
            if (Update_time == null && Install_time != null) {
                long now = System.currentTimeMillis();
                // 更新时间在安装时间之后，最近时间之前
                Update_time = Install_time + (long) (Math.random() * (now - Install_time));
            }
            if (Boot_id == null) Boot_id = generateBootId(Android_id);
            if (bluetooth_address == null) bluetooth_address = generateRandomBluetoothAddress();
            if (FAKE_BOOT_TIME == 1L) FAKE_BOOT_TIME = generateRecentTimestampSeconds();
            if (Drm_id == null) Drm_id = generateRandomDrmId();
            if (Wifi_mac == null) Wifi_mac = generateRandomWifiMac();
            if (Gaid == null) Gaid = generateRandomGaid();
            if (Oaid == null) Oaid = generateRandomOaid();

            // 生成 App Set ID 和 Firebase ID（Android 15 新 API）
            if (App_set_id == null) App_set_id = java.util.UUID.randomUUID().toString();
            if (Firebase_id == null) Firebase_id = generateRandomFirebaseId();
            if (Fcm_token == null) Fcm_token = generateRandomFcmToken();

            // 生成 WiFi 信息
            if (Wifi_ssid == null) Wifi_ssid = generateRandomWifiSsid();
            if (Wifi_bssid == null) Wifi_bssid = generateRandomWifiBssid();
            if (Wifi_ip_address == null) Wifi_ip_address = generateRandomWifiIpAddress();
            if (Wifi_rssi == null) Wifi_rssi = generateRandomWifiRssi();
            // 频率和链接速度关联生成：先生成频率，再根据频率生成匹配的速度
            if (Wifi_frequency == null) Wifi_frequency = generateRandomWifiFrequency();
            if (Wifi_link_speed == null) Wifi_link_speed = generateRandomWifiLinkSpeed(Wifi_frequency);
            if (Wifi_network_id == null) Wifi_network_id = generateRandomWifiNetworkId();

//             生成电池信息（有依赖关系）
            if (Battery_capacity == null) Battery_capacity = generateRandomBatteryCapacity();
            boolean isCharging = new java.util.Random().nextBoolean();
            if (Battery_charge_counter == null) Battery_charge_counter = generateRandomBatteryChargeCounter(Battery_capacity);
            if (Battery_current_now == null) Battery_current_now = generateRandomBatteryCurrentNow(isCharging);
            if (Battery_current_average == null) Battery_current_average = generateRandomBatteryCurrentAverage(isCharging);
            if (Battery_energy_counter == null) Battery_energy_counter = generateRandomBatteryEnergyCounter(Battery_capacity);

            // 生成音频设置
            if (Stream_max_volume == null) Stream_max_volume = generateRandomStreamMaxVolume();
            if (Ringer_mode == null) Ringer_mode = generateRandomRingerMode();
            if (Stream_volume == null) Stream_volume = generateRandomStreamVolume(Stream_max_volume);

            // 生成时区与语言（有依赖关系：raw_offset 依赖 timezone_id）
            if (Timezone_id == null) Timezone_id = generateRandomTimezoneId();
            if (Timezone_raw_offset == null) Timezone_raw_offset = generateTimezoneRawOffset(Timezone_id);
            if (Locale_language == null) Locale_language = generateRandomLocaleLanguage();
            if (Locale_country == null) Locale_country = generateRandomLocaleCountry();

            XposedBridge.log(TAG + "✅ 生成伪造设备信息:");
            XposedBridge.log(TAG + "  android_id=" + Constants.maskValue(Android_id));
            XposedBridge.log(TAG + "  boot_id=" + Constants.maskValue(Boot_id));
            XposedBridge.log(TAG + "  install_time=" + Install_time);
            XposedBridge.log(TAG + "  update_time=" + Update_time);
            XposedBridge.log(TAG + "  bluetooth_address=" + Constants.maskValue(bluetooth_address));
            XposedBridge.log(TAG + "  fake_boot_time=" + FAKE_BOOT_TIME);
            XposedBridge.log(TAG + "  drm_id=" + Constants.maskValue(bytesToHexString(Drm_id)));
            XposedBridge.log(TAG + "  wifi_mac=" + Constants.maskValue(macBytesToString(Wifi_mac)));
            XposedBridge.log(TAG + "  gaid=" + Constants.maskValue(Gaid));
            XposedBridge.log(TAG + "  oaid=" + Constants.maskValue(Oaid));
            XposedBridge.log(TAG + "  wifi_ssid=" + Constants.maskValue(Wifi_ssid));
            XposedBridge.log(TAG + "  wifi_bssid=" + Constants.maskValue(Wifi_bssid));
            XposedBridge.log(TAG + "  wifi_ip=" + Wifi_ip_address);
            XposedBridge.log(TAG + "  battery_capacity=" + Battery_capacity + "%");
            XposedBridge.log(TAG + "  ringer_mode=" + Ringer_mode);
            XposedBridge.log(TAG + "  timezone=" + Timezone_id + " (offset=" + Timezone_raw_offset + ")");
            XposedBridge.log(TAG + "  locale=" + Locale_language + "_" + Locale_country);

            // 写入文件
            try {
                Map<String, Object> output = new HashMap<>();
                output.put("android_id", Android_id);
                output.put("boot_id", Boot_id);
                output.put("bluetooth_address", bluetooth_address);
                output.put("install_time", Install_time);
                output.put("update_time", Update_time);
                output.put("fake_boot_time", FAKE_BOOT_TIME);
                output.put("drm_id", bytesToHexString(Drm_id));
                output.put("wifi_mac", bytesToHexString(Wifi_mac));
                output.put("gaid", Gaid);
                output.put("oaid", Oaid);
                // Android 15 新 API ID
                output.put("app_set_id", App_set_id);
                output.put("firebase_id", Firebase_id);
                output.put("fcm_token", Fcm_token);
                // WiFi 信息
                output.put("wifi_ssid", Wifi_ssid);
                output.put("wifi_bssid", Wifi_bssid);
                output.put("wifi_ip_address", Wifi_ip_address);
                output.put("wifi_rssi", Wifi_rssi);
                output.put("wifi_link_speed", Wifi_link_speed);
                output.put("wifi_frequency", Wifi_frequency);
                output.put("wifi_network_id", Wifi_network_id);
                // 电池信息
                output.put("battery_capacity", Battery_capacity);
                output.put("battery_charge_counter", Battery_charge_counter);
                output.put("battery_current_now", Battery_current_now);
                output.put("battery_current_average", Battery_current_average);
                output.put("battery_energy_counter", Battery_energy_counter);
                // 音频设置
                output.put("ringer_mode", Ringer_mode);
                output.put("stream_volume", Stream_volume);
                output.put("stream_max_volume", Stream_max_volume);
                // 时区与语言
                output.put("timezone_id", Timezone_id);
                output.put("timezone_raw_offset", Timezone_raw_offset);
                output.put("locale_language", Locale_language);
                output.put("locale_country", Locale_country);
                output.put("profile_reset_token", profileResetToken);

                Gson gson = new Gson();
                String json = gson.toJson(output);
                FileUtils.writeStringToFile(file, json, "UTF-8");
                XposedBridge.log(TAG + "💾 伪造设备信息已保存到 fake_device_info.json");
            } catch (IOException e) {
                XposedBridge.log(TAG + "❌ 保存 fake_device_info.json 失败: " + e.getMessage());
            }
        }
    }

    private static boolean shouldResetProfile(Map<String, Object> dataFromJson, long profileResetToken) {
        if (profileResetToken <= 0) {
            return false;
        }
        if (dataFromJson == null) {
            return true;
        }
        Object storedTokenObj = dataFromJson.get("profile_reset_token");
        long storedToken = 0L;
        if (storedTokenObj instanceof Number) {
            storedToken = ((Number) storedTokenObj).longValue();
        }
        return profileResetToken > storedToken;
    }

    private static boolean isAndroidIdString(String value) {
        return value != null && value.matches("^[0-9a-fA-F]{16}$");
    }

    private static void resetFakeDeviceInfoFields() {
        Android_id = null;
        bluetooth_address = null;
        pm = null;
        Boot_id = null;
        Install_time = null;
        Update_time = null;
        FAKE_BOOT_TIME = 1L;
        Drm_id = null;
        Wifi_mac = null;
        Gaid = null;
        Oaid = null;
        App_set_id = null;
        Firebase_id = null;
        Fcm_token = null;
        Wifi_ssid = null;
        Wifi_bssid = null;
        Wifi_ip_address = null;
        Wifi_rssi = null;
        Wifi_link_speed = null;
        Wifi_frequency = null;
        Wifi_network_id = null;
        Battery_capacity = null;
        Battery_charge_counter = null;
        Battery_current_now = null;
        Battery_current_average = null;
        Battery_energy_counter = null;
        Ringer_mode = null;
        Stream_volume = null;
        Stream_max_volume = null;
        Timezone_id = null;
        Timezone_raw_offset = null;
        Locale_language = null;
        Locale_country = null;
    }

    private static void initSekiroIfNeeded(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!ModuleRuntime.isModuleEnabled(ModuleConfig.MODULE_SEKIRO)) return;
        if (sekiroInitialized) return;

        synchronized (LOCK) {
            if (sekiroInitialized) return;

            try {
                // 从配置文件加载 Sekiro 连接参数
                ConfigManager config = ConfigManager.getInstance();
                sekiroClient = new SekiroClient(
                        config.getSekiroGroup(),
                        config.getSekiroClientId(),
                        config.getSekiroIp(),
                        config.getSekiroPort()
                );
                sekiroClient.setupSekiroRequestInitializer((sekiroRequest, handlerRegistry) -> {
                    handlerRegistry.registerSekiroHandler(new DeviceInfoHandler());
                }).start();

                sekiroInitialized = true;
                XposedBridge.log(TAG + "✅ SekiroClient 已启动");
            } catch (Throwable e) {
                XposedBridge.log(TAG + "❌ SekiroClient 启动失败: " + Log.getStackTraceString(e));
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            handleLoadPackageSafely(lpparam);
        } catch (Throwable t) {
            logAlways("handleLoadPackage 顶层异常: package=" + safePackage(lpparam)
                    + ", process=" + safeProcess(lpparam)
                    + ", error=" + Log.getStackTraceString(t));
            throw t;
        }
    }

    private void handleLoadPackageSafely(XC_LoadPackage.LoadPackageParam lpparam) {
        logAlways("入口调用: package=" + safePackage(lpparam)
                + ", process=" + safeProcess(lpparam)
                + ", classLoader=" + (lpparam != null ? lpparam.classLoader : null));

        ModuleConfig moduleConfig;
        try {
            moduleConfig = ModuleConfig.fromXposed();
            ModuleRuntime.setConfig(moduleConfig);
        } catch (Throwable t) {
            logAlways("读取模块配置失败，Hook 中止: package=" + safePackage(lpparam)
                    + ", process=" + safeProcess(lpparam)
                    + ", prefs=/data/data/" + Constants.MODULE_PACKAGE + "/shared_prefs/"
                    + ModuleConfig.PREFS_NAME + ".xml"
                    + ", error=" + Log.getStackTraceString(t));
            return;
        }

        boolean hasConfiguredTargets = !moduleConfig.getTargetPackages().isEmpty();
        String matchedTarget = getMatchedTargetPackage(moduleConfig, lpparam);
        boolean usingLspScopeFallback = matchedTarget == null
                && !hasConfiguredTargets
                && shouldTrustLspScope(moduleConfig, lpparam);
        boolean isTargetPackage = matchedTarget != null || usingLspScopeFallback;
        boolean isProcessAllowed = moduleConfig.isProcessAllowed(lpparam.packageName, lpparam.processName);
        logAlways("配置检查: package=" + lpparam.packageName
                + ", process=" + lpparam.processName
                + ", source=" + moduleConfig.getSourceDescription()
                + ", global=" + moduleConfig.isGlobalEnabled()
                + ", targets=" + moduleConfig.getTargetPackagesText()
                + ", matchedTarget=" + (matchedTarget != null ? matchedTarget : "")
                + ", rules=" + moduleConfig.getProcessRulesText()
                + ", isTarget=" + isTargetPackage
                + ", processAllowed=" + isProcessAllowed
                + ", configuredTargets=" + hasConfiguredTargets
                + ", lspScopeFallback=" + usingLspScopeFallback);
        if (!isTargetPackage || !isProcessAllowed) {
            logAlways("跳过 Hook: package=" + lpparam.packageName
                    + ", process=" + lpparam.processName
                    + ", reason=" + (!isTargetPackage ? "目标应用未匹配" : "进程规则未匹配"));
            return;
        }

        Constants.setRuntimeTargetPackage(lpparam.packageName);
        ModuleRuntime.setTargetPackageName(lpparam.packageName);
        if (lpparam.appInfo != null) {
            Constants.initTargetDataDir(lpparam.appInfo.dataDir);
        }
        MonitorReporter.init(lpparam);

        ensureFakeDeviceInfo(moduleConfig.getProfileResetToken());


        initSekiroIfNeeded(lpparam);


        runIfEnabled(ModuleConfig.MODULE_BOOT_TIME, () -> BootTimeFakeHook.initHooks(lpparam), "BootTimeFakeHook");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_LOGGER, () -> DeviceLogger.initHooks(lpparam), "DeviceLogger");
        runIfEnabled(ModuleConfig.MODULE_COMMAND_FILE, () -> CmdAndFileLogger.initHooks(lpparam), "CmdAndFileLogger");
        runIfEnabled(ModuleConfig.MODULE_BINDER, () -> BinderHook.initHooks(lpparam), "BinderHook");
        runIfEnabled(ModuleConfig.MODULE_INTEGRITY, () -> IntegrityCheckHook.initHooks(lpparam), "IntegrityCheckHook");
        runIfEnabled(ModuleConfig.MODULE_SYSTEM_INFO, () -> SystemInfoFakeHook.initHooks(lpparam), "SystemInfoFakeHook");
        // 启用 FakeDeviceModule 中的设备标识伪造（同型号同系统设备的不同值）
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_android_id(lpparam), "FakeDeviceModule.fake_android_id");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_imei(lpparam), "FakeDeviceModule.fake_imei");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_imsi(lpparam), "FakeDeviceModule.fake_imsi");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_sim_serial(lpparam), "FakeDeviceModule.fake_sim_serial");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_build_serial(lpparam), "FakeDeviceModule.fake_build_serial");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_wifi_mac_address(lpparam), "FakeDeviceModule.fake_wifi_mac_address");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_networkinterface_mac(lpparam), "FakeDeviceModule.fake_networkinterface_mac");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_drm_id(lpparam), "FakeDeviceModule.fake_drm_id");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_install_time(lpparam), "FakeDeviceModule.fake_install_time");
        runIfEnabled(ModuleConfig.MODULE_DEVICE_IDS, () -> FakeDeviceModule.fake_sd_state(lpparam), "FakeDeviceModule.fake_sd_state");
        runIfEnabled(ModuleConfig.MODULE_AD_IDS, () -> AdIdFakeHook.initHooks(lpparam), "AdIdFakeHook");

        // Android 15 新 API Hook
        runIfEnabled(ModuleConfig.MODULE_APP_SET, () -> AppSetIdFakeHook.initHooks(lpparam), "AppSetIdFakeHook");

        // 新增 Hook 模块（指纹保护）
        runIfEnabled(ModuleConfig.MODULE_WEBVIEW, () -> WebViewFakeHook.initHooks(lpparam), "WebViewFakeHook");
        runIfEnabled(ModuleConfig.MODULE_CANVAS, () -> CanvasFakeHook.initHooks(lpparam), "CanvasFakeHook");
        runIfEnabled(ModuleConfig.MODULE_CLIPBOARD, () -> ClipboardFakeHook.initHooks(lpparam), "ClipboardFakeHook");
        runIfEnabled(ModuleConfig.MODULE_BOOTLOADER, () -> BootloaderFakeHook.initHooks(lpparam), "BootloaderFakeHook");
        runIfEnabled(ModuleConfig.MODULE_ACCOUNT, () -> AccountFakeHook.initHooks(lpparam), "AccountFakeHook");
        runIfEnabled(ModuleConfig.MODULE_USAGE_STATS, () -> UsageStatsFakeHook.initHooks(lpparam), "UsageStatsFakeHook");
        runIfEnabled(ModuleConfig.MODULE_GPU, () -> GPUFakeHook.initHooks(lpparam), "GPUFakeHook");
        runIfEnabled(ModuleConfig.MODULE_FONTS, () -> FontListFakeHook.initHooks(lpparam), "FontListFakeHook");
        runIfEnabled(ModuleConfig.MODULE_LOCATION, () -> LocationFakeHook.initHooks(lpparam), "LocationFakeHook");
        runIfEnabled(ModuleConfig.MODULE_VPN, () -> VpnDetectionBypassHook.initHooks(lpparam), "VpnDetectionBypassHook");
        runIfEnabled(ModuleConfig.MODULE_BEHAVIOR, () -> BehaviorFingerprintHook.initHooks(lpparam), "BehaviorFingerprintHook");
        runIfEnabled(ModuleConfig.MODULE_PACKAGE_MANAGER, () -> PackageManagerHook.initHooks(lpparam), "PackageManagerHook");
        runIfEnabled(ModuleConfig.MODULE_XPOSED_TRACE, () -> XposedTraceBypassHook.initHooks(lpparam), "XposedTraceBypassHook");
        runIfEnabled(ModuleConfig.MODULE_PERSISTENT_IDS, () -> PersistentIdFakeHook.initHooks(lpparam), "PersistentIdFakeHook");
        runIfEnabled(ModuleConfig.MODULE_MISSING_INFO, () -> MissingInfoHook.initHooks(lpparam), "MissingInfoHook");
        runIfEnabled(ModuleConfig.MODULE_FLUTTER_RN, () -> FlutterRnHook.initHooks(lpparam), "FlutterRnHook");
        runIfEnabled(ModuleConfig.MODULE_SYSTEM_PROPERTIES, () -> SystemPropertiesFakeHook.initHooks(lpparam), "SystemPropertiesFakeHook");
        runIfEnabled(ModuleConfig.MODULE_NATIVE, () -> NativeHooks.init(), "NativeHooks");

        XposedBridge.log(TAG + "✅ 已启用 Hook 初始化完成");
    }

    private static void logAlways(String message) {
        String line = TAG + message;
        XposedBridge.log(line);
        Log.i("DeviceVeil", line);
    }

    private static String safePackage(XC_LoadPackage.LoadPackageParam lpparam) {
        return lpparam == null ? "null" : String.valueOf(lpparam.packageName);
    }

    private static String safeProcess(XC_LoadPackage.LoadPackageParam lpparam) {
        return lpparam == null ? "null" : String.valueOf(lpparam.processName);
    }

    private static String getMatchedTargetPackage(ModuleConfig moduleConfig,
                                                  XC_LoadPackage.LoadPackageParam lpparam) {
        if (moduleConfig == null || lpparam == null || lpparam.packageName == null) {
            return null;
        }
        if (Constants.MODULE_PACKAGE.equals(lpparam.packageName)) {
            return null;
        }
        return moduleConfig.getMatchedTargetPackage(lpparam.packageName, lpparam.processName);
    }

    private static boolean shouldTrustLspScope(ModuleConfig moduleConfig,
                                               XC_LoadPackage.LoadPackageParam lpparam) {
        return moduleConfig != null
                && moduleConfig.isGlobalEnabled()
                && lpparam != null
                && lpparam.packageName != null
                && !Constants.MODULE_PACKAGE.equals(lpparam.packageName);
    }

    private void runIfEnabled(String moduleKey, Runnable action, String moduleName) {
        if (ModuleRuntime.isModuleEnabled(moduleKey)) {
            runSafely(action, moduleName);
        } else if (Constants.VERBOSE_HOOK_LOGS) {
            XposedBridge.log(TAG + "⏭ " + moduleName + " 已关闭");
        }
    }


    private void runSafely(Runnable action, String moduleName) {
        try {
            XposedBridge.log(TAG + "▶ 初始化 " + moduleName);
            action.run();
            XposedBridge.log(TAG + "✓ " + moduleName + " 初始化完成");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "❌ " + moduleName + " 初始化失败: " + Log.getStackTraceString(t));
        }
    }

}
