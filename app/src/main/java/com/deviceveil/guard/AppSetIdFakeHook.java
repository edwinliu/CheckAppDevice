package com.deviceveil.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Android 15 新 API Hook 模块
 *
 * 功能：
 * 1. 伪造 App Set ID (Google Play Services 新标识符)
 * 2. 伪造 Firebase Installation ID (FID)
 * 3. 伪造 Firebase Cloud Messaging Token (FCM Token)
 * 4. 伪造 SubscriptionManager 订阅信息
 *
 * 这些是 Android 15 中推荐使用的新设备标识 API
 */
public class AppSetIdFakeHook {

    private static final String TAG = "[设备信息记录]---[AppSetIdFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "Android 15 新 API Hook 模块初始化");
        XposedBridge.log(TAG + "========================================");

        // Hook App Set ID
        hookAppSetId(lpparam);

        // Hook Firebase Installation ID
        hookFirebaseInstallationId(lpparam);

        // Hook Firebase Messaging Token
        hookFirebaseMessagingToken(lpparam);

        // Hook SubscriptionManager
        hookSubscriptionManager(lpparam);

        XposedBridge.log(TAG + "✅ Android 15 新 API Hook 模块初始化完成");
    }

    // ==================== App Set ID Hook ====================

    /**
     * Hook Google Play Services App Set ID
     * API: com.google.android.gms.appset.AppSetIdClient
     */
    private static void hookAppSetId(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook AppSetIdInfo.getId()
        try {
            Class<?> appSetIdInfoClass = XposedHelpers.findClass(
                    "com.google.android.gms.appset.AppSetIdInfo",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    appSetIdInfoClass,
                    "getId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String originalId = (String) param.getResult();
                            String fakeId = HookInit.App_set_id;

                            param.setResult(fakeId);
                            XposedBridge.log(TAG + "【App Set ID 伪造】原值: " + originalId +
                                    " → 伪造值: " + fakeId);
                        }
                    });

            XposedBridge.log(TAG + "Hook App Set ID (AppSetIdInfo.getId) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook App Set ID 失败 (可能未集成 Play Services): " + t.getMessage());
        }

        // Hook AppSetIdInfo.getScope()
        try {
            Class<?> appSetIdInfoClass = XposedHelpers.findClass(
                    "com.google.android.gms.appset.AppSetIdInfo",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    appSetIdInfoClass,
                    "getScope",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Integer scope = (Integer) param.getResult();
                            XposedBridge.log(TAG + "【App Set ID】getScope() = " + scope +
                                    " (1=APP, 2=DEVELOPER)");
                        }
                    });
        } catch (Throwable t) {
            // 忽略
        }
    }

    // ==================== Firebase Installation ID Hook ====================

    /**
     * Hook Firebase Installation ID (FID)
     * API: com.google.firebase.installations.FirebaseInstallations
     */
    private static void hookFirebaseInstallationId(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook FirebaseInstallations.getId() 返回的 Task
        try {
            Class<?> firebaseInstallationsClass = XposedHelpers.findClass(
                    "com.google.firebase.installations.FirebaseInstallations",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    firebaseInstallationsClass,
                    "getId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "【Firebase ID】getId() 被调用，尝试 Hook Task 结果");
                            // Task 结果需要在回调中处理
                            hookTaskResult(param.getResult(), "Firebase Installation ID");
                        }
                    });

            XposedBridge.log(TAG + "Hook Firebase Installation ID (FirebaseInstallations.getId) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Firebase Installation ID 失败 (可能未集成 Firebase): " + t.getMessage());
        }

        // 直接 Hook InstallationIdResult
        try {
            Class<?> resultClass = XposedHelpers.findClass(
                    "com.google.firebase.installations.InstallationIdResult",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    resultClass,
                    "getId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String originalId = (String) param.getResult();
                            String fakeId = HookInit.Firebase_id;

                            param.setResult(fakeId);
                            XposedBridge.log(TAG + "【Firebase ID 伪造】原值: " + originalId +
                                    " → 伪造值: " + fakeId);
                        }
                    });

            XposedBridge.log(TAG + "Hook Firebase InstallationIdResult.getId 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook InstallationIdResult 失败: " + t.getMessage());
        }
    }

    // ==================== Firebase Messaging Token Hook ====================

    /**
     * Hook Firebase Cloud Messaging Token
     * API: com.google.firebase.messaging.FirebaseMessaging
     */
    private static void hookFirebaseMessagingToken(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook FirebaseMessaging.getToken()
        try {
            Class<?> firebaseMessagingClass = XposedHelpers.findClass(
                    "com.google.firebase.messaging.FirebaseMessaging",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    firebaseMessagingClass,
                    "getToken",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "【FCM Token】getToken() 被调用");
                            hookTaskResult(param.getResult(), "FCM Token");
                        }
                    });

            XposedBridge.log(TAG + "Hook FCM Token (FirebaseMessaging.getToken) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook FCM Token 失败 (可能未集成 Firebase): " + t.getMessage());
        }

        // Hook 旧版 getInstanceId (兼容旧版本)
        try {
            Class<?> firebaseInstanceIdClass = XposedHelpers.findClass(
                    "com.google.firebase.iid.FirebaseInstanceId",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    firebaseInstanceIdClass,
                    "getToken",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String originalToken = (String) param.getResult();
                            String fakeToken = HookInit.Fcm_token;

                            param.setResult(fakeToken);
                            XposedBridge.log(TAG + "【FCM Token 伪造 (旧版)】原值: " +
                                    (originalToken != null ? originalToken.substring(0, Math.min(20, originalToken.length())) + "..." : "null") +
                                    " → 伪造值: " + fakeToken.substring(0, 20) + "...");
                        }
                    });

            XposedBridge.log(TAG + "Hook FCM Token (FirebaseInstanceId.getToken) 成功");
        } catch (Throwable t) {
            // 旧版 API 可能不存在，忽略
        }
    }

    // ==================== SubscriptionManager Hook ====================

    /**
     * Hook SubscriptionManager (SIM 卡订阅信息)
     * API: android.telephony.SubscriptionManager
     */
    private static void hookSubscriptionManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> subscriptionInfoClass = XposedHelpers.findClass(
                    "android.telephony.SubscriptionInfo",
                    lpparam.classLoader);

            // Hook getSubscriptionId()
            XposedHelpers.findAndHookMethod(
                    subscriptionInfoClass,
                    "getSubscriptionId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Integer originalId = (Integer) param.getResult();
                            // 返回固定的订阅 ID
                            int fakeId = 1;
                            param.setResult(fakeId);
                            XposedBridge.log(TAG + "【Subscription ID 伪造】原值: " + originalId +
                                    " → 伪造值: " + fakeId);
                        }
                    });

            // Hook getIccId() - ICC ID 在 Android 10+ 受限
            try {
                XposedHelpers.findAndHookMethod(
                        subscriptionInfoClass,
                        "getIccId",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String originalIccId = (String) param.getResult();
                                param.setResult(FakeData.FAKE_ICCID);
                                XposedBridge.log(TAG + "【ICC ID 伪造】原值: " + originalIccId +
                                        " → 伪造值: " + FakeData.FAKE_ICCID);
                            }
                        });
            } catch (Throwable t) {
                // getIccId 可能不存在
            }

            XposedBridge.log(TAG + "Hook SubscriptionManager 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook SubscriptionManager 失败: " + t.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * Hook Google Play Services Task 的结果
     * Task 是异步的，需要 Hook 结果回调
     */
    private static void hookTaskResult(Object task, String apiName) {
        if (task == null) return;

        try {
            // Hook Task.addOnSuccessListener 的回调
            Class<?> taskClass = task.getClass();

            // 尝试获取结果（如果已完成）
            try {
                Object result = XposedHelpers.callMethod(task, "getResult");
                if (result != null) {
                    XposedBridge.log(TAG + "【" + apiName + "】Task 已完成，结果类型: " + result.getClass().getName());
                }
            } catch (Throwable t) {
                // Task 可能未完成
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Task 结果失败: " + t.getMessage());
        }
    }
}
