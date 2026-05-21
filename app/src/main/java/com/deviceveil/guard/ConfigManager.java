package com.deviceveil.guard;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

public class ConfigManager {
    private static final String TAG = "[设备信息记录]---[ConfigManager] ";

    // Default configuration values
    private static final String DEFAULT_SEKIRO_GROUP = "deviceguard";
    private static final String DEFAULT_SEKIRO_CLIENT_ID = "950";
    private static final String DEFAULT_SEKIRO_IP = "192.168.31.58";
    private static final int DEFAULT_SEKIRO_PORT = 5612;

    private String sekiroGroup;
    private String sekiroClientId;
    private String sekiroIp;
    private int sekiroPort;

    private ConfigManager() {
        loadConfig();
    }

    private static class Holder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    private void loadConfig() {
        File configFile = new File(Constants.getSekiroConfigPath());

        if (configFile.exists()) {
            try {
                String content = FileUtils.readFileToString(configFile, "UTF-8");
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> config = gson.fromJson(content, type);

                sekiroGroup = getStringValue(config, "sekiro_group", DEFAULT_SEKIRO_GROUP);
                sekiroClientId = getStringValue(config, "sekiro_client_id", DEFAULT_SEKIRO_CLIENT_ID);
                sekiroIp = getStringValue(config, "sekiro_ip", DEFAULT_SEKIRO_IP);
                sekiroPort = getIntValue(config, "sekiro_port", DEFAULT_SEKIRO_PORT);

                XposedBridge.log(TAG + "✅ 配置已从文件加载: " + Constants.getSekiroConfigPath());
                XposedBridge.log(TAG + "  Sekiro IP: " + sekiroIp + ":" + sekiroPort);
            } catch (Exception e) {
                XposedBridge.log(TAG + "⚠️ 读取配置文件失败，使用默认配置: " + e.getMessage());
                useDefaultConfig();
            }
        } else {
            XposedBridge.log(TAG + "⚠️ 配置文件不存在，使用默认配置并创建示例文件");
            useDefaultConfig();
            createDefaultConfigFile(configFile);
        }
    }

    private void useDefaultConfig() {
        sekiroGroup = DEFAULT_SEKIRO_GROUP;
        sekiroClientId = DEFAULT_SEKIRO_CLIENT_ID;
        sekiroIp = DEFAULT_SEKIRO_IP;
        sekiroPort = DEFAULT_SEKIRO_PORT;
    }

    private void createDefaultConfigFile(File configFile) {
        try {
            Map<String, Object> defaultConfig = new HashMap<>();
            defaultConfig.put("sekiro_group", DEFAULT_SEKIRO_GROUP);
            defaultConfig.put("sekiro_client_id", DEFAULT_SEKIRO_CLIENT_ID);
            defaultConfig.put("sekiro_ip", DEFAULT_SEKIRO_IP);
            defaultConfig.put("sekiro_port", DEFAULT_SEKIRO_PORT);
            defaultConfig.put("_comment", "修改此文件后需要重启目标应用生效");

            Gson gson = new Gson();
            String json = gson.toJson(defaultConfig);
            FileUtils.writeStringToFile(configFile, json, "UTF-8");
            XposedBridge.log(TAG + "💾 已创建默认配置文件: " + Constants.getSekiroConfigPath());
        } catch (Exception e) {
            XposedBridge.log(TAG + "❌ 创建配置文件失败: " + e.getMessage());
        }
    }

    private String getStringValue(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return (value instanceof String) ? (String) value : defaultValue;
    }

    private int getIntValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    // Getters
    public String getSekiroGroup() {
        return sekiroGroup;
    }

    public String getSekiroClientId() {
        return sekiroClientId;
    }

    public String getSekiroIp() {
        return sekiroIp;
    }

    public int getSekiroPort() {
        return sekiroPort;
    }
}
