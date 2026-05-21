package com.deviceveil.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ModuleConfig {
    public static final String PREFS_NAME = "module_config";

    public static final String KEY_GLOBAL_ENABLED = "global_enabled";
    public static final String KEY_TARGET_PACKAGES = "target_packages";
    public static final String KEY_PROCESS_RULES = "process_rules";
    public static final String KEY_LOG_SENSITIVE = "log_sensitive_values";
    public static final String KEY_PROFILE_RESET_TOKEN = "profile_reset_token";
    public static final String KEY_HEAVY_MODULES_DISABLED_V1 = "heavy_modules_disabled_v1";
    public static final String KEY_NATIVE_DISABLED_V1 = "native_disabled_v1";
    public static final String KEY_SAFE_MODE_V2 = "safe_mode_v2";
    public static final String KEY_STABLE_MODE = "stable_mode_v1";

    public static final String MODULE_BOOT_TIME = "module_boot_time";
    public static final String MODULE_DEVICE_LOGGER = "module_device_logger";
    public static final String MODULE_COMMAND_FILE = "module_command_file";
    public static final String MODULE_BINDER = "module_binder";
    public static final String MODULE_INTEGRITY = "module_integrity";
    public static final String MODULE_SYSTEM_INFO = "module_system_info";
    public static final String MODULE_DEVICE_IDS = "module_device_ids";
    public static final String MODULE_AD_IDS = "module_ad_ids";
    public static final String MODULE_APP_SET = "module_app_set";
    public static final String MODULE_WEBVIEW = "module_webview";
    public static final String MODULE_CANVAS = "module_canvas";
    public static final String MODULE_CLIPBOARD = "module_clipboard";
    public static final String MODULE_BOOTLOADER = "module_bootloader";
    public static final String MODULE_ACCOUNT = "module_account";
    public static final String MODULE_USAGE_STATS = "module_usage_stats";
    public static final String MODULE_GPU = "module_gpu";
    public static final String MODULE_FONTS = "module_fonts";
    public static final String MODULE_LOCATION = "module_location";
    public static final String MODULE_VPN = "module_vpn";
    public static final String MODULE_BEHAVIOR = "module_behavior";
    public static final String MODULE_PACKAGE_MANAGER = "module_package_manager";
    public static final String MODULE_XPOSED_TRACE = "module_xposed_trace";
    public static final String MODULE_PERSISTENT_IDS = "module_persistent_ids";
    public static final String MODULE_MISSING_INFO = "module_missing_info";
    public static final String MODULE_FLUTTER_RN = "module_flutter_rn";
    public static final String MODULE_SYSTEM_PROPERTIES = "module_system_properties";
    public static final String MODULE_NATIVE = "module_native";
    public static final String MODULE_SEKIRO = "module_sekiro";
    private static final Map<String, String> MODULE_LABELS = new LinkedHashMap<>();

    static {
        MODULE_LABELS.put(MODULE_BOOT_TIME, "开机时间伪装");
        MODULE_LABELS.put(MODULE_DEVICE_LOGGER, "设备读取监控");
        MODULE_LABELS.put(MODULE_COMMAND_FILE, "命令与文件监控");
        MODULE_LABELS.put(MODULE_BINDER, "Binder 调用监控");
        MODULE_LABELS.put(MODULE_INTEGRITY, "完整性检测监控");
        MODULE_LABELS.put(MODULE_SYSTEM_INFO, "系统 / WiFi / 电池信息");
        MODULE_LABELS.put(MODULE_DEVICE_IDS, "设备标识保护");
        MODULE_LABELS.put(MODULE_AD_IDS, "广告标识 GAID / OAID");
        MODULE_LABELS.put(MODULE_APP_SET, "AppSet / Firebase / FCM");
        MODULE_LABELS.put(MODULE_WEBVIEW, "WebView 指纹保护");
        MODULE_LABELS.put(MODULE_CANVAS, "Canvas 指纹保护");
        MODULE_LABELS.put(MODULE_CLIPBOARD, "剪贴板保护");
        MODULE_LABELS.put(MODULE_BOOTLOADER, "Bootloader 与安全属性");
        MODULE_LABELS.put(MODULE_ACCOUNT, "账户信息保护");
        MODULE_LABELS.put(MODULE_USAGE_STATS, "应用使用统计保护");
        MODULE_LABELS.put(MODULE_GPU, "GPU 信息保护");
        MODULE_LABELS.put(MODULE_FONTS, "字体列表保护");
        MODULE_LABELS.put(MODULE_LOCATION, "位置伪装");
        MODULE_LABELS.put(MODULE_VPN, "VPN / 代理检测绕过");
        MODULE_LABELS.put(MODULE_BEHAVIOR, "行为指纹保护");
        MODULE_LABELS.put(MODULE_PACKAGE_MANAGER, "应用列表保护");
        MODULE_LABELS.put(MODULE_XPOSED_TRACE, "Xposed 痕迹隐藏");
        MODULE_LABELS.put(MODULE_PERSISTENT_IDS, "三方 SDK 持久标识");
        MODULE_LABELS.put(MODULE_MISSING_INFO, "补充系统 API 保护");
        MODULE_LABELS.put(MODULE_FLUTTER_RN, "Flutter / RN 标识保护");
        MODULE_LABELS.put(MODULE_SYSTEM_PROPERTIES, "系统属性保护");
        MODULE_LABELS.put(MODULE_NATIVE, "Native 层 Hook");
        MODULE_LABELS.put(MODULE_SEKIRO, "Sekiro 调试桥");
    }

    private final SharedPreferences prefs;
    private final XSharedPreferences xPrefs;
    private final Map<String, Object> filePrefs;
    private static volatile ModuleConfig cachedXposedConfig;
    private static volatile long cachedPrefsModified = Long.MIN_VALUE;
    private static volatile long cachedPrefsSize = Long.MIN_VALUE;

    private ModuleConfig(SharedPreferences prefs, XSharedPreferences xPrefs, Map<String, Object> filePrefs) {
        this.prefs = prefs;
        this.xPrefs = xPrefs;
        this.filePrefs = filePrefs;
    }

    public static ModuleConfig fromContext(Context context) {
        return new ModuleConfig(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), null, null);
    }

    public static ModuleConfig fromXposed() {
        return fromXposed(false);
    }

    public static synchronized ModuleConfig fromXposed(boolean forceReload) {
        File prefsFile = getPrefsFile();
        long modified = prefsFile.exists() ? prefsFile.lastModified() : -1L;
        long size = prefsFile.exists() ? prefsFile.length() : -1L;
        if (!forceReload
                && cachedXposedConfig != null
                && cachedPrefsModified == modified
                && cachedPrefsSize == size) {
            return cachedXposedConfig;
        }

        XSharedPreferences prefs = new XSharedPreferences(Constants.MODULE_PACKAGE, PREFS_NAME);
        prefs.makeWorldReadable();
        prefs.reload();
        ModuleConfig config = new ModuleConfig(null, prefs, loadPrefsFile(prefsFile));
        cachedXposedConfig = config;
        cachedPrefsModified = modified;
        cachedPrefsSize = size;
        return config;
    }

    public static synchronized void invalidateCache() {
        cachedXposedConfig = null;
        cachedPrefsModified = Long.MIN_VALUE;
        cachedPrefsSize = Long.MIN_VALUE;
    }

    public String getSourceDescription() {
        if (prefs != null) {
            return "context";
        }
        return "xshared" + (filePrefs != null && !filePrefs.isEmpty() ? "+xml" : "");
    }

    public static Map<String, String> moduleLabels() {
        return MODULE_LABELS;
    }

    public static Map<String, String> recommendedModules() {
        LinkedHashMap<String, String> modules = new LinkedHashMap<>();
        putAll(modules,
                MODULE_BOOT_TIME,
                MODULE_SYSTEM_INFO,
                MODULE_DEVICE_IDS,
                MODULE_AD_IDS,
                MODULE_APP_SET,
                MODULE_PACKAGE_MANAGER,
                MODULE_PERSISTENT_IDS,
                MODULE_MISSING_INFO,
                MODULE_SYSTEM_PROPERTIES);
        return modules;
    }

    public static Map<String, String> advancedModules() {
        LinkedHashMap<String, String> modules = new LinkedHashMap<>();
        putAll(modules,
                MODULE_WEBVIEW,
                MODULE_CLIPBOARD,
                MODULE_BOOTLOADER,
                MODULE_ACCOUNT,
                MODULE_USAGE_STATS,
                MODULE_FONTS,
                MODULE_LOCATION,
                MODULE_VPN,
                MODULE_BEHAVIOR,
                MODULE_XPOSED_TRACE,
                MODULE_FLUTTER_RN,
                MODULE_NATIVE);
        return modules;
    }

    public static Map<String, String> monitorModules() {
        LinkedHashMap<String, String> modules = new LinkedHashMap<>();
        putAll(modules,
                MODULE_DEVICE_LOGGER,
                MODULE_COMMAND_FILE,
                MODULE_BINDER,
                MODULE_INTEGRITY,
                MODULE_SEKIRO);
        return modules;
    }

    private static void putAll(Map<String, String> out, String... keys) {
        for (String key : keys) {
            String label = MODULE_LABELS.get(key);
            if (label != null) {
                out.put(key, label);
            }
        }
    }

    public boolean isGlobalEnabled() {
        return getBoolean(KEY_GLOBAL_ENABLED, true);
    }

    public boolean logSensitiveValues() {
        return getBoolean(KEY_LOG_SENSITIVE, false);
    }

    public long getProfileResetToken() {
        return getLong(KEY_PROFILE_RESET_TOKEN, 0L);
    }

    public String getTargetPackagesText() {
        return getString(KEY_TARGET_PACKAGES, Constants.DEFAULT_TARGET_PACKAGE);
    }

    public String getProcessRulesText() {
        return getString(KEY_PROCESS_RULES, "*");
    }

    public Set<String> getTargetPackages() {
        return parseList(getTargetPackagesText());
    }

    public boolean isTargetPackage(String packageName) {
        return isGlobalEnabled() && packageName != null && getTargetPackages().contains(packageName);
    }

    public boolean isTargetPackageOrProcess(String packageName, String processName) {
        return getMatchedTargetPackage(packageName, processName) != null;
    }

    public String getMatchedTargetPackage(String packageName, String processName) {
        if (!isGlobalEnabled()) {
            return null;
        }
        Set<String> targets = getTargetPackages();
        if (targets.isEmpty()) {
            return null;
        }
        for (String target : targets) {
            if (target.equals(packageName)
                    || target.equals(processName)
                    || (processName != null && processName.startsWith(target + ":"))) {
                return target;
            }
        }
        return null;
    }

    public boolean isProcessAllowed(String packageName, String processName) {
        Set<String> rules = parseList(getProcessRulesText());
        if (rules.isEmpty() || rules.contains("*")) {
            return true;
        }

        boolean hasInclude = false;
        boolean included = false;
        for (String rule : rules) {
            boolean exclude = rule.startsWith("!");
            String pattern = exclude ? rule.substring(1) : rule;
            if (pattern.isEmpty()) {
                continue;
            }
            boolean matches = matchesProcessRule(pattern, packageName, processName);
            if (exclude && matches) {
                return false;
            }
            if (!exclude) {
                hasInclude = true;
                included |= matches;
            }
        }
        return !hasInclude || included;
    }

    public boolean isModuleEnabled(String moduleKey) {
        return getBoolean(moduleKey, getDefaultModuleEnabled(moduleKey));
    }

    public boolean shouldUseStableMode() {
        return getBoolean(KEY_STABLE_MODE, true);
    }

    public static boolean getDefaultModuleEnabled(String moduleKey) {
        return MODULE_BOOT_TIME.equals(moduleKey)
                || MODULE_SYSTEM_INFO.equals(moduleKey)
                || MODULE_DEVICE_IDS.equals(moduleKey)
                || MODULE_AD_IDS.equals(moduleKey)
                || MODULE_APP_SET.equals(moduleKey)
                || MODULE_PACKAGE_MANAGER.equals(moduleKey)
                || MODULE_PERSISTENT_IDS.equals(moduleKey)
                || MODULE_MISSING_INFO.equals(moduleKey)
                || MODULE_SYSTEM_PROPERTIES.equals(moduleKey);
    }

    public static Set<String> safeDefaultEnabledModules() {
        LinkedHashSet<String> enabled = new LinkedHashSet<>();
        for (String key : MODULE_LABELS.keySet()) {
            if (getDefaultModuleEnabled(key)) {
                enabled.add(key);
            }
        }
        return enabled;
    }

    private boolean matchesProcessRule(String pattern, String packageName, String processName) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.startsWith(":")) {
            return processName != null && (processName.equals(packageName + pattern) || processName.endsWith(pattern));
        }
        if (pattern.indexOf('*') >= 0) {
            String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
            return processName != null && processName.matches(regex);
        }
        return pattern.equals(processName) || pattern.equals(packageName);
    }

    private Set<String> parseList(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String item : Arrays.asList(text.split("[,\\n\\r\\t ]+"))) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        if (filePrefs != null && filePrefs.containsKey(key)) {
            Object value = filePrefs.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return prefs != null ? prefs.getBoolean(key, defaultValue) : xPrefs.getBoolean(key, defaultValue);
    }

    private String getString(String key, String defaultValue) {
        if (filePrefs != null && filePrefs.containsKey(key)) {
            Object value = filePrefs.get(key);
            return value != null ? String.valueOf(value) : defaultValue;
        }
        return prefs != null ? prefs.getString(key, defaultValue) : xPrefs.getString(key, defaultValue);
    }

    private long getLong(String key, long defaultValue) {
        if (filePrefs != null && filePrefs.containsKey(key)) {
            Object value = filePrefs.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return prefs != null ? prefs.getLong(key, defaultValue) : xPrefs.getLong(key, defaultValue);
    }

    private static Map<String, Object> loadPrefsFile() {
        return loadPrefsFile(getPrefsFile());
    }

    private static Map<String, Object> loadPrefsFile(File file) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!file.exists()) {
            return out;
        }

        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "utf-8");
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                String tag = parser.getName();
                String name = parser.getAttributeValue(null, "name");
                if (name == null) {
                    continue;
                }
                if ("string".equals(tag)) {
                    out.put(name, parser.nextText());
                } else if ("boolean".equals(tag)) {
                    out.put(name, Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                } else if ("long".equals(tag) || "int".equals(tag)) {
                    String value = parser.getAttributeValue(null, "value");
                    if (value != null) {
                        out.put(name, Long.parseLong(value));
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[设备信息记录]---[ModuleConfig] 直接读取配置 XML 失败: " + t);
        }
        return out;
    }

    private static File getPrefsFile() {
        return new File("/data/data/" + Constants.MODULE_PACKAGE
                + "/shared_prefs/" + PREFS_NAME + ".xml");
    }

    public static String normalizePackageInput(String input) {
        Set<String> packages = new LinkedHashSet<>();
        if (input != null) {
            for (String item : input.split("[,\\n\\r\\t ]+")) {
                String pkg = item.trim().toLowerCase(Locale.ROOT);
                if (!pkg.isEmpty()) {
                    packages.add(pkg);
                }
            }
        }
        return String.join("\n", packages);
    }
}
