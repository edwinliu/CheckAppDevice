package com.deviceveil.guard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Generic coverage for lower-frequency fingerprint APIs not owned by the
 * focused modules.
 */
public class MissingInfoHook {
    private static final String TAG = "[设备信息记录]---[MissingInfoHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        hookStorageManager(lpparam);
        hookUserManager(lpparam);
        hookDisplay(lpparam);
        hookBiometricManager(lpparam);
        hookPackageInstaller(lpparam);
        hookSystemGetenv(lpparam);
        hookSettingsSystemGlobal(lpparam);
        hookInputMethodManager(lpparam);
        hookWallpaperManager(lpparam);
        hookCameraAndCodecLogs(lpparam);
        XposedBridge.log(TAG + "Additional system API hooks initialized");
    }

    private static void hookStorageManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> storageManager = XposedHelpers.findClassIfExists(
                    "android.os.storage.StorageManager", lpparam.classLoader);
            if (storageManager != null) {
                for (Method method : storageManager.getDeclaredMethods()) {
                    String name = method.getName();
                    if (name.equals("getUuidForPath") || name.equals("getPrimaryStorageUuid")) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult(UUID.nameUUIDFromBytes(FakeData.FAKE_STORAGE_UUID.getBytes()));
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "StorageManager hook failed: " + t);
        }

        try {
            Class<?> storageVolume = XposedHelpers.findClassIfExists(
                    "android.os.storage.StorageVolume", lpparam.classLoader);
            if (storageVolume != null) {
                XposedHelpers.findAndHookMethod(storageVolume, "getUuid", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() != null) {
                            param.setResult(FakeData.FAKE_EXTERNAL_STORAGE_UUID);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookUserManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> userManager = XposedHelpers.findClassIfExists("android.os.UserManager", lpparam.classLoader);
            Class<?> userHandle = XposedHelpers.findClassIfExists("android.os.UserHandle", lpparam.classLoader);
            if (userManager != null && userHandle != null) {
                XposedHelpers.findAndHookMethod(userManager, "getSerialNumberForUser",
                        userHandle, XC_MethodReplacement.returnConstant(FakeData.FAKE_USER_SERIAL));
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "UserManager hook failed: " + t);
        }
    }

    private static void hookDisplay(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> display = XposedHelpers.findClassIfExists("android.view.Display", lpparam.classLoader);
            if (display == null) return;
            XposedHelpers.findAndHookMethod(display, "getRefreshRate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult((float) FakeData.FAKE_DISPLAY_REFRESH_RATE_HZ);
                }
            });
            XposedHelpers.findAndHookMethod(display, "getSupportedRefreshRates", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(new float[]{(float) FakeData.FAKE_DISPLAY_REFRESH_RATE_HZ});
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookBiometricManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> biometricManager = XposedHelpers.findClassIfExists(
                    "android.hardware.biometrics.BiometricManager", lpparam.classLoader);
            if (biometricManager != null) {
                for (Method method : biometricManager.getDeclaredMethods()) {
                    if (method.getName().equals("canAuthenticate")) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(12));
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> fingerprintManager = XposedHelpers.findClassIfExists(
                    "android.hardware.fingerprint.FingerprintManager", lpparam.classLoader);
            if (fingerprintManager != null) {
                XposedHelpers.findAndHookMethod(fingerprintManager, "isHardwareDetected",
                        XC_MethodReplacement.returnConstant(false));
                XposedHelpers.findAndHookMethod(fingerprintManager, "hasEnrolledFingerprints",
                        XC_MethodReplacement.returnConstant(false));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookPackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> packageInstaller = XposedHelpers.findClassIfExists(
                    "android.content.pm.PackageInstaller", lpparam.classLoader);
            if (packageInstaller == null) return;
            for (Method method : packageInstaller.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("getAllSessions") || name.equals("getMySessions")
                        || name.equals("getActiveStagedSessions") || name.equals("getStagedSessions")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new ArrayList<>());
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "PackageInstaller hook failed: " + t);
        }
    }

    private static void hookSystemGetenv(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                    "getenv", String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if ("LD_PRELOAD".equals(key) || "LD_LIBRARY_PATH".equals(key)
                                    || "LD_AUDIT".equals(key) || "LD_PROFILE".equals(key)) {
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "System.getenv hook failed: " + t);
        }
    }

    private static void hookSettingsSystemGlobal(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSettingsClass(lpparam, "android.provider.Settings$System");
        hookSettingsClass(lpparam, "android.provider.Settings$Global");
    }

    private static void hookSettingsClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (cls == null) return;
            for (Method method : cls.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("getString") || name.equals("getStringForUser")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = null;
                            for (Object arg : param.args) {
                                if (arg instanceof String) {
                                    key = (String) arg;
                                    break;
                                }
                            }
                            if (key == null) return;
                            String lower = key.toLowerCase();
                            if (lower.equals("android_id") || lower.endsWith(":android_id")) {
                                param.setResult(HookInit.Android_id != null ? HookInit.Android_id : FakeData.FAKE_ANDROID_ID);
                            } else if (lower.equals("adb_enabled") || lower.equals("development_settings_enabled")
                                    || lower.equals("install_non_market_apps")) {
                                param.setResult("0");
                            }
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookInputMethodManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> inputMethodManager = XposedHelpers.findClassIfExists(
                    "android.view.inputmethod.InputMethodManager", lpparam.classLoader);
            if (inputMethodManager == null) return;
            for (Method method : inputMethodManager.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("getEnabledInputMethodList") || name.equals("getInputMethodList")
                        || name.equals("getShortcutInputMethodsAndSubtypes")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new ArrayList<>());
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookWallpaperManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> wallpaperManager = XposedHelpers.findClassIfExists(
                    "android.app.WallpaperManager", lpparam.classLoader);
            if (wallpaperManager == null) return;
            for (Method method : wallpaperManager.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("getWallpaperInfo")) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(null));
                } else if (name.equals("getWallpaperId")) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(0));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookCameraAndCodecLogs(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cameraManager = XposedHelpers.findClassIfExists(
                    "android.hardware.camera2.CameraManager", lpparam.classLoader);
            if (cameraManager != null) {
                XposedHelpers.findAndHookMethod(cameraManager, "getCameraIdList", new XC_MethodHook() {});
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> mediaCodecList = XposedHelpers.findClassIfExists(
                    "android.media.MediaCodecList", lpparam.classLoader);
            if (mediaCodecList != null) {
                XposedHelpers.findAndHookMethod(mediaCodecList, "getCodecInfos", new XC_MethodHook() {});
            }
        } catch (Throwable ignored) {
        }
    }
}
