package com.deviceveil.guard;

public class Constants {
    public static final String MODULE_PACKAGE = "com.deviceveil.guard";
    public static final String CONFIG_PROVIDER_AUTHORITY = MODULE_PACKAGE + ".config";
    public static final String DEFAULT_TARGET_PACKAGE = "";
    public static volatile String TARGET_PACKAGE = DEFAULT_TARGET_PACKAGE;
    private static final String DEFAULT_DATA_DIR = "/data/data";
    private static volatile String targetDataDir = DEFAULT_DATA_DIR;

    // Android version constants
    public static final int ANDROID_15_SDK = 35;

    // Time constants (in milliseconds)
    public static final long TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000;
    public static final long THREE_HOURS_SECONDS = 3L * 60 * 60;
    public static final long SEVEN_HOURS_SECONDS = 7L * 60 * 60;

    // Timeout constants
    public static final long PROCESS_TIMEOUT_SECONDS = 2L;
    public static final long STDERR_JOIN_TIMEOUT_MS = 1000L;
    public static final int MIN_UPTIME_SECONDS = 60;

    // Log limits
    public static final boolean LOG_SENSITIVE_VALUES = false;
    public static final boolean VERBOSE_HOOK_LOGS = false;
    public static final int MAX_LOG_LINE_LENGTH = 200;
    public static final int MAX_CERT_LOG_LENGTH = 500;
    public static final int MAX_BASE64_PREVIEW_LENGTH = 100;
    public static final int MAX_BYTES_PREVIEW = 32;
    public static final int MAX_LIST_PREVIEW_ITEMS = 3;
    public static final int MAX_STACK_TRACE_DEPTH = 15;

    // File paths
    @Deprecated
    public static final String FAKE_DEVICE_INFO_PATH = DEFAULT_DATA_DIR + "/fake_device_info.json";
    @Deprecated
    public static final String SEKIRO_CONFIG_PATH = DEFAULT_DATA_DIR + "/sekiro_config.json";
    public static final String PROC_STAT_PATH = "/proc/stat";
    public static final String PROC_UPTIME_PATH = "/proc/uptime";
    public static final String BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id";

    public static void initTargetDataDir(String dataDir) {
        if (dataDir != null && !dataDir.isEmpty()) {
            targetDataDir = dataDir;
        }
    }

    public static void setRuntimeTargetPackage(String packageName) {
        if (packageName != null && !packageName.isEmpty()) {
            TARGET_PACKAGE = packageName;
        }
    }

    public static String getFakeDeviceInfoPath() {
        return targetDataDir + "/fake_device_info.json";
    }

    public static String getSekiroConfigPath() {
        return targetDataDir + "/sekiro_config.json";
    }

    public static String getTargetDataDir() {
        return targetDataDir;
    }

    public static boolean isTargetOwnPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String dataDir = targetDataDir;
        if (dataDir != null && !dataDir.isEmpty() && !DEFAULT_DATA_DIR.equals(dataDir)) {
            if (path.equals(dataDir) || path.startsWith(dataDir + "/")) {
                return true;
            }
        }
        String packageName = TARGET_PACKAGE;
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        return path.contains("/Android/data/" + packageName + "/")
                || path.contains("/Android/obb/" + packageName + "/")
                || (path.contains("/data/app/") && path.contains(packageName))
                || (path.contains("/mnt/expand/") && path.contains("/app/") && path.contains(packageName));
    }

    public static String maskValue(Object value) {
        if (value == null) return "null";
        String text = String.valueOf(value);
        if (ModuleRuntime.isSensitiveLoggingEnabled() || text.length() <= 4) return text;
        return "***" + text.substring(text.length() - 4);
    }

    // Sensitive packages to hide (ROOT / Magisk / Xposed / Debug tools)
    public static final String[] HIDDEN_PACKAGES = {
            // === ROOT / Magisk ===
            "com.topjohnwu.magisk",
            "bin.mt.plus",

            // === Xposed / EdXposed / LSPosed ===
            MODULE_PACKAGE,
            "org.meowcat.edxposed.manager",
            "io.github.lsposed.manager",
            "de.robv.android.xposed.installer",

            // === 抓包 / 代理 / 调试工具 ===
            "com.reqable.android",
            "com.tunnelworkshop.postern",
            "com.github.kr328.clash",
            "com.debug.loggerui",
            "com.example.devicescheck",

            // === 高度可疑 / 随机命名 / 非常规应用 ===
            "com.dd.b43boo",
            "com.zx.Justmeplush",
            "com.zx.encryptstack",
            "com.swdd.strangeapp",
            "com.zhenxi.jnitrace",
            "com.zhenxi.hunter",
            "com.sukisu.ultra",
            "andes.oplus.documentsreader",

            "com.android.shell",
            "com.android.traceur",
            "com.oplus.engineermode",
            "com.mediatek.atci.service"
    };

    // Sensitive keywords for log sanitization
    public static final String[] SENSITIVE_KEYWORDS = {
            "sekiro",
            "xposed",
            "riru",
            "edxposed",
            "lsposed",
            "hooker",
            "justtrustme",
            "sslunpinning",
            "/data/misc/riru",
            "/system/lib/libriru",
            "/system/lib64/libriru",
            "org.lsposed",
            "de.robv.android.xposed",
            MODULE_PACKAGE,
            "xpatch",
            "taichi",
            "sandhook",
            "epic",
            "yukikaze",
            "com.topjohnwu.magisk",
            "bin.mt.plus",
            "org.meowcat.edxposed.manager",
            "io.github.lsposed.manager",
            "de.robv.android.xposed.installer",
            "com.reqable.android",
            "com.tunnelworkshop.postern",
            "com.github.kr328.clash",
            "com.debug.loggerui",
            "com.example.devicescheck",
            "com.dd.b43boo",
            "com.zx.Justmeplush",
            "com.zx.encryptstack",
            "com.swdd.strangeapp",
            "com.zhenxi.jnitrace",
            "com.zhenxi.hunter",
            "com.sukisu.ultra",
            "andes.oplus.documentsreader",
            "com.android.shell",
            "com.android.traceur",
            "com.oplus.engineermode",
            "com.mediatek.atci.service"
    };

    // Sensitive file paths for monitoring (Java + Native layer)
    public static final String[] SENSITIVE_PATHS = {
            // SU binaries
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/tmp",
            "/data/local/bin/su",
            "/system/app/Superuser.apk",

            // Magisk
            "/system/bin/magisk",
            "/system/xbin/magisk",
            "/data/adb/magisk",
            "/vendor/bin/magisk",

            // Proc filesystem
            "/proc/self/maps",
            "/proc/self/status",
            "/proc/self/cmdline",
            "/proc/",

            // Other sensitive paths
            "/dev/",
            "build.prop",
            "getprop",
            "/apex/",
            "ro.boot.verifiedbootstate",

            // Hook frameworks
            "xposed",
            "magisk",
            "riru",
            "lsposed"
    };

    // Sensitive commands for native monitoring
    public static final String[] SENSITIVE_COMMANDS = {
            "su",
            "pm list",
            "getprop",
            "which su",
            "ps",
            "cat /proc",
            "mount",
            "magisk",
            "xposed"
    };

    // Emulator indicators
    public static final String[] EMULATOR_INDICATORS = {
            "google_sdk",
            "emulator",
            "sdk_gphone",
            "vbox",
            "qemu",
            "genymotion",
            "generic",
            "unknown"
    };

    // System property keys to monitor
    public static final String[] SENSITIVE_SYSTEM_PROPERTIES = {
            "ro.secure",
            "ro.debuggable",
            "ro.boot.verifiedbootstate",
            "ro.build.tags"
    };

    // VPN detection bypass - interface prefixes to filter
    public static final String[] VPN_INTERFACE_PREFIXES = {
            "tun", "tap", "ppp", "pptp", "l2tp", "ipsec", "vpn"
    };

    // VPN detection bypass - proc files to intercept
    public static final String[] VPN_PROC_FILES = {
            "/proc/net/route",
            "/proc/net/ipv6_route",
            "/proc/net/if_inet6",
            "/sys/class/net/tun0",
            "/sys/class/net/ppp0"
    };
}
