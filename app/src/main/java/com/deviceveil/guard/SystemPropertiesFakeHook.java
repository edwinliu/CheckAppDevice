package com.deviceveil.guard;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Central Java-layer android.os.SystemProperties hook.
 *
 * This intentionally avoids broad device model/fingerprint replacement. Those
 * values must be profile-consistent across Java, native, WebView, GPU and vendor
 * services, so this module only normalizes security/root/emulator-sensitive
 * properties that are safe for a generic framework.
 */
public class SystemPropertiesFakeHook {
    private static final String TAG = "[设备信息记录]---[SystemPropertiesFakeHook] ";

    private static final Map<String, String> PROPS = new HashMap<>();

    static {
        PROPS.put("ro.serialno", FakeData.RESTRICTED_BUILD_SERIAL);
        PROPS.put("ro.boot.serialno", FakeData.RESTRICTED_BUILD_SERIAL);
        PROPS.put("ro.secure", "1");
        PROPS.put("ro.debuggable", "0");
        PROPS.put("ro.build.tags", "release-keys");
        PROPS.put("ro.boot.verifiedbootstate", FakeData.FAKE_VERIFIED_BOOT_STATE);
        PROPS.put("ro.boot.flash.locked", FakeData.FAKE_BOOTLOADER_UNLOCKED ? "0" : "1");
        PROPS.put("ro.boot.vbmeta.device_state", FakeData.FAKE_BOOTLOADER_UNLOCKED ? "unlocked" : "locked");
        PROPS.put("ro.boot.veritymode", "enforcing");
        PROPS.put("ro.boot.warranty_bit", "0");
        PROPS.put("ro.warranty_bit", "0");
        PROPS.put("ro.crypto.state", "encrypted");
        PROPS.put("ro.crypto.type", "file");
        PROPS.put("ro.kernel.qemu", "");
        PROPS.put("ro.kernel.qemu.gles", "");
        PROPS.put("qemu.hw.mainkeys", "");
        PROPS.put("ro.boot.qemu", "");
        PROPS.put("ro.dalvik.vm.native.bridge", "0");
        PROPS.put("persist.sys.nativebridge", "");
        PROPS.put("ro.enable.native.bridge.exec", "0");
        PROPS.put("vzw.os.rooted", "");
        PROPS.put("ro.allow.mock.location", "0");
        PROPS.put("init.svc.adbd", "stopped");
        PROPS.put("sys.usb.state", "mtp");
        PROPS.put("persist.sys.usb.config", "mtp");
        PROPS.put("ro.boot.selinux", "enforcing");
        PROPS.put("selinux.reload_policy", "1");
        PROPS.put("ro.build.selinux", "1");
    }

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cls = XposedHelpers.findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "android.os.SystemProperties not found");
            return;
        }

        int hooked = 0;
        for (Method method : cls.getDeclaredMethods()) {
            String name = method.getName();
            if (!"get".equals(name) && !"getInt".equals(name)
                    && !"getLong".equals(name) && !"getBoolean".equals(name)) {
                continue;
            }

            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length == 0) return;
                        Object keyObj = param.args[0];
                        if (!(keyObj instanceof String)) return;
                        String fake = PROPS.get((String) keyObj);
                        if (fake == null) return;

                        Class<?> returnType = ((Method) param.method).getReturnType();
                        try {
                            if (returnType == String.class) {
                                param.setResult(fake);
                            } else if (returnType == int.class || returnType == Integer.class) {
                                param.setResult(fake.isEmpty() ? 0 : Integer.parseInt(fake));
                            } else if (returnType == long.class || returnType == Long.class) {
                                param.setResult(fake.isEmpty() ? 0L : Long.parseLong(fake));
                            } else if (returnType == boolean.class || returnType == Boolean.class) {
                                param.setResult("1".equals(fake) || "true".equalsIgnoreCase(fake));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
                hooked++;
            } catch (Throwable ignored) {
            }
        }

        XposedBridge.log(TAG + "SystemProperties hooks initialized, methods=" + hooked);
    }
}
