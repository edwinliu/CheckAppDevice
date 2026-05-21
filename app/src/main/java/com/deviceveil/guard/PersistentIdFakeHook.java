package com.deviceveil.guard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces SDK-level persistent identifiers read from SharedPreferences and
 * common attribution/crash SDK APIs.
 */
public class PersistentIdFakeHook {
    private static final String TAG = "[设备信息记录]---[PersistentIdFakeHook] ";

    private static final Map<String, String> FAKE_SP_STRINGS = new HashMap<>();

    static {
        FAKE_SP_STRINGS.put("installation_id", FakeData.FAKE_SCRIBE_INSTALLATION_ID);
        FAKE_SP_STRINGS.put("key_header_installation_id", FakeData.FAKE_MOCA_INSTALLATION_ID);
        FAKE_SP_STRINGS.put("key_ods", FakeData.FAKE_MOCA_ODS);
        FAKE_SP_STRINGS.put("key_ods1", FakeData.FAKE_MOCA_ODS1);
        FAKE_SP_STRINGS.put("AppsFlyerKey", FakeData.FAKE_APPSFLYER_UID);
        FAKE_SP_STRINGS.put("afUID", FakeData.FAKE_APPSFLYER_UID);
        FAKE_SP_STRINGS.put("appsFlyerCount", "1");
        FAKE_SP_STRINGS.put("Fid", FakeData.FAKE_UUID_INSTALLATION_ID);
        FAKE_SP_STRINGS.put("firebase.installations.installationId", FakeData.FAKE_UUID_INSTALLATION_ID);
        FAKE_SP_STRINGS.put("fire-installations-id-token", "fake-fis-token-placeholder");
        FAKE_SP_STRINGS.put("__leanplum_device_id", FakeData.FAKE_LEANPLUM_DEVICE_ID);
        FAKE_SP_STRINGS.put("existing_instance_identifier", FakeData.FAKE_UUID_INSTALLATION_ID);
        FAKE_SP_STRINGS.put("crashlytics.installation.id", FakeData.FAKE_UUID_INSTALLATION_ID);
        FAKE_SP_STRINGS.put("crashlytics.advertising.id", "00000000-0000-0000-0000-000000000000");
        FAKE_SP_STRINGS.put("com.crashlytics.CrashlyticsAdvertisingId", "00000000-0000-0000-0000-000000000000");
        FAKE_SP_STRINGS.put("fcm_token", "fake_fcm_token_placeholder");
        FAKE_SP_STRINGS.put("gcm.topics", "");
        FAKE_SP_STRINGS.put("deviceid", FakeData.FAKE_DEVICE_UUID_ID);
        FAKE_SP_STRINGS.put("deviceId", FakeData.FAKE_DEVICE_UUID_ID);
        FAKE_SP_STRINGS.put("device_id", FakeData.FAKE_DEVICE_UUID_ID);
        FAKE_SP_STRINGS.put("unique_device_id", FakeData.FAKE_DEVICE_UUID_ID);
        FAKE_SP_STRINGS.put("google_ad_id", "00000000-0000-0000-0000-000000000000");
    }

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSharedPreferencesGetString(lpparam);
        hookFirebaseInstallations(lpparam);
        hookAppsFlyerDeviceId(lpparam);
        hookLeanplumDeviceId(lpparam);
        hookCrashlyticsAndFcm(lpparam);
        XposedBridge.log(TAG + "Persistent ID hooks initialized");
    }

    private static void hookSharedPreferencesGetString(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spImplClass = XposedHelpers.findClass(
                    "android.app.SharedPreferencesImpl", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(spImplClass, "getString",
                    String.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if (key == null) return;
                            String fake = FAKE_SP_STRINGS.get(key);
                            if (fake != null) {
                                param.setResult(fake);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook SharedPreferencesImpl.getString failed: " + t);
        }
    }

    private static void hookFirebaseInstallations(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
                "com.google.firebase.installations.FirebaseInstallations",
                "com.google.firebase.installations.b"
        };
        for (String className : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;
                XposedBridge.hookAllMethods(cls, "getId", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Class<?> tasksClass = XposedHelpers.findClass(
                                "com.google.android.gms.tasks.Tasks", lpparam.classLoader);
                        String fake = getUuidInstallId();
                        return XposedHelpers.callStaticMethod(tasksClass, "forResult", fake);
                    }
                });
                break;
            } catch (Throwable ignored) {
            }
        }

        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.google.firebase.iid.FirebaseInstanceId", lpparam.classLoader);
            if (cls != null) {
                XposedBridge.hookAllMethods(cls, "getId", XC_MethodReplacement.returnConstant(getUuidInstallId()));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookAppsFlyerDeviceId(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
                "com.appsflyer.internal.AFb1rSDK",
                "com.appsflyer.AppsFlyerLibCore"
        };
        for (String className : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (cls == null) continue;
                XposedBridge.hookAllMethods(cls, "getAppsFlyerUID",
                        XC_MethodReplacement.returnConstant(FakeData.FAKE_APPSFLYER_UID));
                break;
            } catch (Throwable ignored) {
            }
        }
    }

    private static void hookLeanplumDeviceId(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("com.leanplum.Leanplum", lpparam.classLoader);
            if (cls != null) {
                XposedBridge.hookAllMethods(cls, "getDeviceId",
                        XC_MethodReplacement.returnConstant(FakeData.FAKE_LEANPLUM_DEVICE_ID));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookCrashlyticsAndFcm(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.google.firebase.crashlytics.internal.common.IdManager", lpparam.classLoader);
            if (cls != null) {
                XposedBridge.hookAllMethods(cls, "getCrashlyticsInstallId",
                        XC_MethodReplacement.returnConstant(getUuidInstallId()));
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.google.firebase.messaging.FirebaseMessaging", lpparam.classLoader);
            if (cls != null) {
                XposedBridge.hookAllMethods(cls, "getToken", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Class<?> tasksClass = XposedHelpers.findClass(
                                "com.google.android.gms.tasks.Tasks", lpparam.classLoader);
                        String fake = HookInit.Fcm_token != null ? HookInit.Fcm_token : "fake_fcm_token_placeholder";
                        return XposedHelpers.callStaticMethod(tasksClass, "forResult", fake);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static String getUuidInstallId() {
        String candidate = HookInit.Firebase_id;
        if (isUuid(candidate)) {
            return candidate;
        }
        return FakeData.FAKE_UUID_INSTALLATION_ID;
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
