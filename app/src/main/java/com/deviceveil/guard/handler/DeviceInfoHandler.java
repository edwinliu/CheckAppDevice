package com.deviceveil.guard.handler;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.deviceveil.guard.Constants;
import com.deviceveil.guard.HookInit;
import com.deviceveil.guard.PackageManagerHook;
import com.virjar.sekiro.business.api.interfaze.ActionHandler;
import com.virjar.sekiro.business.api.interfaze.SekiroRequest;
import com.virjar.sekiro.business.api.interfaze.SekiroResponse;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

import static com.deviceveil.guard.RandomIdGenerator.bytesToHexString;
import static com.deviceveil.guard.RandomIdGenerator.macBytesToString;

/**
 * 设备信息 Handler
 * 返回 fake_device_info.json 中的所有伪造设备信息
 *
 * 使用示例：/business-demo/invoke?action=DeviceInfo
 */
public class DeviceInfoHandler implements ActionHandler {

    private static final String TAG = "[设备信息记录]---[DeviceInfoHandler] ";

    @Override
    public String action() {
        return "DeviceInfo";
    }

    @Override
    public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
        try {
            Map<String, Object> result = new HashMap<>();

            // 优先从文件读取
            File file = new File(Constants.getFakeDeviceInfoPath());
            if (file.exists()) {
                try {
                    String content = FileUtils.readFileToString(file, "UTF-8");
                    if (content != null && !content.trim().isEmpty() && !content.trim().equals("404")) {
                        Gson gson = new Gson();
                        Type type = new TypeToken<Map<String, Object>>() {}.getType();
                        Map<String, Object> fileData = gson.fromJson(content, type);
                        if (fileData != null) {
                            result.putAll(fileData);
                            result.put("source", "file");
                            XposedBridge.log(TAG + "从文件读取设备信息成功");
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + "读取文件异常: " + e.getMessage());
                }
            }

            // 如果文件读取失败或为空，从内存中获取
            if (result.isEmpty() || !result.containsKey("android_id")) {
                result.put("android_id", HookInit.Android_id);
                result.put("boot_id", HookInit.Boot_id);
                result.put("bluetooth_address", HookInit.bluetooth_address);
                result.put("install_time", HookInit.Install_time);
                result.put("fake_boot_time", HookInit.FAKE_BOOT_TIME);

                if (HookInit.Drm_id != null) {
                    result.put("drm_id", bytesToHexString(HookInit.Drm_id));
                }
                if (HookInit.Wifi_mac != null) {
                    result.put("wifi_mac", bytesToHexString(HookInit.Wifi_mac));
                    result.put("wifi_mac_formatted", macBytesToString(HookInit.Wifi_mac));
                }
                if (HookInit.Gaid != null) {
                    result.put("gaid", HookInit.Gaid);
                }
                if (HookInit.Oaid != null) {
                    result.put("oaid", HookInit.Oaid);
                }

                // WiFi 信息
                if (HookInit.Wifi_ssid != null) {
                    result.put("wifi_ssid", HookInit.Wifi_ssid);
                }
                if (HookInit.Wifi_bssid != null) {
                    result.put("wifi_bssid", HookInit.Wifi_bssid);
                }
                if (HookInit.Wifi_ip_address != null) {
                    result.put("wifi_ip_address", HookInit.Wifi_ip_address);
                }
                if (HookInit.Wifi_rssi != null) {
                    result.put("wifi_rssi", HookInit.Wifi_rssi);
                }
                if (HookInit.Wifi_link_speed != null) {
                    result.put("wifi_link_speed", HookInit.Wifi_link_speed);
                }
                if (HookInit.Wifi_frequency != null) {
                    result.put("wifi_frequency", HookInit.Wifi_frequency);
                }
                if (HookInit.Wifi_network_id != null) {
                    result.put("wifi_network_id", HookInit.Wifi_network_id);
                }

                // 电池信息
                if (HookInit.Battery_capacity != null) {
                    result.put("battery_capacity", HookInit.Battery_capacity);
                }
                if (HookInit.Battery_charge_counter != null) {
                    result.put("battery_charge_counter", HookInit.Battery_charge_counter);
                }
                if (HookInit.Battery_current_now != null) {
                    result.put("battery_current_now", HookInit.Battery_current_now);
                }
                if (HookInit.Battery_current_average != null) {
                    result.put("battery_current_average", HookInit.Battery_current_average);
                }
                if (HookInit.Battery_energy_counter != null) {
                    result.put("battery_energy_counter", HookInit.Battery_energy_counter);
                }

                // 音频设置
                if (HookInit.Ringer_mode != null) {
                    result.put("ringer_mode", HookInit.Ringer_mode);
                }
                if (HookInit.Stream_volume != null) {
                    result.put("stream_volume", HookInit.Stream_volume);
                }
                if (HookInit.Stream_max_volume != null) {
                    result.put("stream_max_volume", HookInit.Stream_max_volume);
                }

                result.put("source", "memory");
                XposedBridge.log(TAG + "从内存读取设备信息");
            }

            // 添加额外的格式化信息
            if (HookInit.Wifi_mac != null && !result.containsKey("wifi_mac_formatted")) {
                result.put("wifi_mac_formatted", macBytesToString(HookInit.Wifi_mac));
            }

            // 添加文件路径信息
            result.put("config_path", Constants.getFakeDeviceInfoPath());
            result.put("file_exists", file.exists());

            // ============================================================================
            // 添加 PackageManagerHook 伪造的安装时间和更新时间
            // ============================================================================
            Long fakePkgInstallTime = PackageManagerHook.getFakeInstallTime();
            Long fakePkgUpdateTime = PackageManagerHook.getFakeUpdateTime();
            if (fakePkgInstallTime != null) {
                result.put("fake_pkg_install_time", fakePkgInstallTime);
            }
            if (fakePkgUpdateTime != null) {
                result.put("fake_pkg_update_time", fakePkgUpdateTime);
            }

            sekiroResponse.success(result);
            XposedBridge.log(TAG + "返回设备信息成功");

        } catch (Throwable t) {
            XposedBridge.log(TAG + "处理请求异常: " + t.getMessage());
            sekiroResponse.failed(-1, "获取设备信息失败: " + t.getMessage());
        }
    }
}
