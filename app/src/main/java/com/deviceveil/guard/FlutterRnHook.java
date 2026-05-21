package com.deviceveil.guard;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Identifier coverage for common Flutter/RN device-info bridges.
 */
public class FlutterRnHook {
    private static final String TAG = "[设备信息记录]---[FlutterRnHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        hookFlutterPluginHandlers(lpparam);
        hookReactNativeDeviceInfo(lpparam);
        XposedBridge.log(TAG + "Flutter/RN hooks initialized");
    }

    private static void hookFlutterPluginHandlers(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
                "io.flutter.plugins.deviceinfo.MethodCallHandlerImpl",
                "dev.fluttercommunity.plus.device_info.MethodCallHandlerImpl",
                "io.flutter.plugins.packageinfo.MethodCallHandlerImpl",
                "dev.fluttercommunity.plus.packageinfo.MethodCallHandlerImpl"
        };

        for (String className : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;
                for (Method method : cls.getDeclaredMethods()) {
                    if (method.getName().equals("onMethodCall")) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                // Result is normally an interface instance owned by the plugin.
                                // Keep this as low-risk observability only.
                            }
                        });
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void hookReactNativeDeviceInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> deviceInfo = XposedHelpers.findClassIfExists(
                    "com.learnium.RNDeviceInfo.RNDeviceModule", lpparam.classLoader);
            if (deviceInfo == null) return;

            for (Method method : deviceInfo.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("getUniqueIdSync") || name.equals("getDeviceIdSync")
                        || name.equals("getAndroidIdSync") || name.equals("getSerialNumberSync")
                        || name.equals("getUniqueId") || name.equals("getDeviceId")
                        || name.equals("getAndroidId") || name.equals("getSerialNumber")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.method instanceof Method)) return;
                            Method reflectedMethod = (Method) param.method;
                            if (reflectedMethod.getReturnType() != String.class) return;
                            String methodName = reflectedMethod.getName().toLowerCase();
                            if (methodName.contains("serial")) {
                                param.setResult(FakeData.RESTRICTED_BUILD_SERIAL);
                            } else {
                                param.setResult(HookInit.Android_id != null ? HookInit.Android_id : FakeData.FAKE_ANDROID_ID);
                            }
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RN DeviceInfo hook failed: " + t);
        }
    }
}
