package com.deviceveil.guard;

public class ModuleRuntime {
    private static volatile ModuleConfig config;
    private static volatile boolean sensitiveLoggingEnabled;
    private static volatile String targetPackageName;

    public static void setConfig(ModuleConfig moduleConfig) {
        config = moduleConfig;
        sensitiveLoggingEnabled = moduleConfig != null && moduleConfig.logSensitiveValues();
        targetPackageName = null;
    }

    public static ModuleConfig getConfig() {
        ModuleConfig local = config;
        if (local == null) {
            local = ModuleConfig.fromXposed();
            setConfig(local);
        }
        return local;
    }

    public static boolean isSensitiveLoggingEnabled() {
        return sensitiveLoggingEnabled;
    }

    public static boolean isStableModeEnabled() {
        return getConfig().shouldUseStableMode();
    }

    public static boolean isModuleEnabled(String moduleKey) {
        return getConfig().isModuleEnabled(moduleKey);
    }

    public static void setTargetPackageName(String packageName) {
        targetPackageName = packageName;
    }

    public static String getTargetPackageName() {
        return targetPackageName;
    }

    public static void refreshConfig() {
        ModuleConfig.invalidateCache();
        setConfig(ModuleConfig.fromXposed(true));
    }
}
