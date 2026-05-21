package com.deviceveil.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Bootloader/系统状态伪造 Hook 模块
 *
 * 功能:
 * 1. 伪造 Bootloader 解锁状态 - 隐藏解锁状态
 * 2. 伪造验证启动状态 (Verified Boot) - 返回 "green" (正常)
 * 3. 伪造安全补丁级别 - 返回指定的安全补丁日期
 * 4. 伪造 Build 指纹 - 返回正常设备的指纹
 * 5. Hook SystemProperties - 拦截敏感属性查询
 */
public class BootloaderFakeHook {

    private static final String TAG = "[设备信息记录]---[BootloaderFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化 Bootloader/系统状态伪造 Hook");

        hookBuildFields(lpparam);
        hookSystemProperties(lpparam);
        hookDevicePolicyManager(lpparam);
        hookKeyguardManager(lpparam);

        XposedBridge.log(TAG + "Bootloader/系统状态伪造 Hook 初始化完成");
    }

    // ==================== Build 字段 Hook ====================
    // 注意: Build.FINGERPRINT, Build.TAGS, Build.TYPE 不再伪造
    // 原因: 伪造成不同品牌(如 Samsung)会与系统层暴露的真实设备信息(如 Oplus 服务)产生矛盾
    //       这种不一致反而更容易被检测
    private static void hookBuildFields(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> buildClass = XposedHelpers.findClass("android.os.Build", lpparam.classLoader);

            // 仅记录原始值，不再修改
            try {
                Field fingerprintField = buildClass.getDeclaredField("FINGERPRINT");
                fingerprintField.setAccessible(true);
                String origFingerprint = (String) fingerprintField.get(null);
                XposedBridge.log(TAG + "Build.FINGERPRINT (保持原值): " + origFingerprint);
            } catch (Exception e) {
                XposedBridge.log(TAG + "Build.FINGERPRINT 读取失败: " + e.getMessage());
            }

            try {
                Field tagsField = buildClass.getDeclaredField("TAGS");
                tagsField.setAccessible(true);
                String origTags = (String) tagsField.get(null);
                XposedBridge.log(TAG + "Build.TAGS (保持原值): " + origTags);
            } catch (Exception e) {
                XposedBridge.log(TAG + "Build.TAGS 读取失败: " + e.getMessage());
            }

            try {
                Field typeField = buildClass.getDeclaredField("TYPE");
                typeField.setAccessible(true);
                String origType = (String) typeField.get(null);
                XposedBridge.log(TAG + "Build.TYPE (保持原值): " + origType);
            } catch (Exception e) {
                XposedBridge.log(TAG + "Build.TYPE 读取失败: " + e.getMessage());
            }

            XposedBridge.log(TAG + "Build 字段检查完成 (不伪造)");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Build 字段检查失败: " + e.getMessage());
        }
    }

    // ==================== SystemProperties Hook ====================
    private static void hookSystemProperties(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> systemPropertiesClass = XposedHelpers.findClass(
                    "android.os.SystemProperties", lpparam.classLoader);

            // Hook get(String key)
            XposedHelpers.findAndHookMethod(systemPropertiesClass, "get", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    Object result = param.getResult();

                    String fakeValue = getFakeSystemProperty(key);
                    if (fakeValue != null) {
                        XposedBridge.log(TAG + "SystemProperties.get(" + key + ") 原始值: " + result);
                        XposedBridge.log(TAG + "SystemProperties.get(" + key + ") 伪造值: " + fakeValue);
                        param.setResult(fakeValue);
                    }
                }
            });

            // Hook get(String key, String def)
            XposedHelpers.findAndHookMethod(systemPropertiesClass, "get", String.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    Object result = param.getResult();

                    String fakeValue = getFakeSystemProperty(key);
                    if (fakeValue != null) {
                        XposedBridge.log(TAG + "SystemProperties.get(" + key + ", def) 原始值: " + result);
                        XposedBridge.log(TAG + "SystemProperties.get(" + key + ", def) 伪造值: " + fakeValue);
                        param.setResult(fakeValue);
                    }
                }
            });

            // Hook getBoolean(String key, boolean def)
            try {
                XposedHelpers.findAndHookMethod(systemPropertiesClass, "getBoolean",
                        String.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        Object result = param.getResult();

                        Boolean fakeValue = getFakeBooleanProperty(key);
                        if (fakeValue != null) {
                            XposedBridge.log(TAG + "SystemProperties.getBoolean(" + key + ") 原始值: " + result);
                            XposedBridge.log(TAG + "SystemProperties.getBoolean(" + key + ") 伪造值: " + fakeValue);
                            param.setResult(fakeValue);
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "SystemProperties.getBoolean() 方法不存在，跳过");
            }

            XposedBridge.log(TAG + "SystemProperties Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "SystemProperties Hook 失败: " + e.getMessage());
        }
    }

    /**
     * 根据属性 key 返回伪造值
     *
     * 注意: ro.build.fingerprint, ro.build.tags, ro.build.type 不再伪造
     *       保持设备真实的 Build 信息，避免与系统层暴露的信息产生矛盾
     */
    private static String getFakeSystemProperty(String key) {
        if (key == null) return null;

        switch (key) {
            // Bootloader 相关
            case "ro.boot.verifiedbootstate":
                return FakeData.FAKE_VERIFIED_BOOT_STATE;
            case "ro.boot.flash.locked":
                return FakeData.FAKE_BOOTLOADER_UNLOCKED ? "0" : "1";
            case "ro.boot.vbmeta.device_state":
                return FakeData.FAKE_BOOTLOADER_UNLOCKED ? "unlocked" : "locked";
            case "ro.bootmode":
                return "unknown";
            case "ro.boot.bootdevice":
                return "";

            // 安全相关 - 仍然伪造，不涉及设备型号
            case "ro.build.version.security_patch":
                return FakeData.FAKE_SECURITY_PATCH;
            case "ro.vendor.build.security_patch":
                return FakeData.FAKE_SECURITY_PATCH;

            // Build 相关 - 不再伪造，保持原值
            // case "ro.build.fingerprint":
            // case "ro.build.tags":
            // case "ro.build.type":

            // 安全/调试相关 - 仍然伪造
            case "ro.debuggable":
                return "0";
            case "ro.secure":
                return "1";

            // SELinux 相关
            case "ro.boot.selinux":
                return "enforcing";
            case "selinux.reload_policy":
                return "1";

            // ADB 相关
            case "init.svc.adbd":
                return "stopped";
            case "sys.usb.state":
                return "mtp";
            case "persist.sys.usb.config":
                return "mtp";

            // Root 检测相关
            case "ro.build.selinux":
                return "1";
            case "service.bootanim.exit":
                return "1";

            default:
                return null;
        }
    }

    /**
     * 根据属性 key 返回伪造的布尔值
     */
    private static Boolean getFakeBooleanProperty(String key) {
        if (key == null) return null;

        switch (key) {
            case "ro.debuggable":
                return false;
            case "ro.secure":
                return true;
            default:
                return null;
        }
    }

    // ==================== DevicePolicyManager Hook ====================
    private static void hookDevicePolicyManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dpmClass = XposedHelpers.findClass(
                    "android.app.admin.DevicePolicyManager", lpparam.classLoader);

            // Hook isDeviceOwnerApp() - 设备所有者检测
            try {
                XposedHelpers.findAndHookMethod(dpmClass, "isDeviceOwnerApp", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "isDeviceOwnerApp() 原始值: " + orig);
                        // 保持原值，仅记录
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，忽略
            }

            // Hook getStorageEncryptionStatus() - 存储加密状态
            try {
                XposedHelpers.findAndHookMethod(dpmClass, "getStorageEncryptionStatus", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "getStorageEncryptionStatus() 原始值: " + orig);
                        // 返回加密状态: ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY = 4
                        param.setResult(4);
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，忽略
            }

            XposedBridge.log(TAG + "DevicePolicyManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "DevicePolicyManager Hook 失败: " + e.getMessage());
        }
    }

    // ==================== KeyguardManager Hook ====================
    private static void hookKeyguardManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> keyguardClass = XposedHelpers.findClass(
                    "android.app.KeyguardManager", lpparam.classLoader);

            // Hook isDeviceSecure() - 设备安全状态
            try {
                XposedHelpers.findAndHookMethod(keyguardClass, "isDeviceSecure", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "isDeviceSecure() 原始值: " + orig);
                        param.setResult(FakeData.DEVICE_SECURE);
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，忽略
            }

            // Hook isDeviceLocked() - 设备锁定状态
            try {
                XposedHelpers.findAndHookMethod(keyguardClass, "isDeviceLocked", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "isDeviceLocked() 原始值: " + orig);
                        param.setResult(FakeData.KEYGUARD_LOCKED);
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，忽略
            }

            XposedBridge.log(TAG + "KeyguardManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "KeyguardManager Hook 失败: " + e.getMessage());
        }
    }
}