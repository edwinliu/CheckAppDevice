package com.deviceveil.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 广告 ID 伪造模块
 *
 * 功能：
 * 1. 伪造 GAID (Google Advertising ID)
 * 2. 伪造 OAID (Open Anonymous Device Identifier)
 *
 * Hook 点：
 * - GAID: com.google.android.gms.ads.identifier.AdvertisingIdClient$Info.getId()
 * - OAID: MSA SDK 和各厂商 SDK 的 OAID 获取接口
 */
public class AdIdFakeHook {

    private static final String TAG = "[设备信息记录]---[AdIdFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "广告 ID 伪造模块初始化");
        XposedBridge.log(TAG + "========================================");

        // Hook GAID
        hookGoogleAdvertisingId(lpparam);

        // Hook OAID (MSA SDK)
        hookOaidMsaSdk(lpparam);

        // Hook OAID (各厂商 SDK)
        hookOaidVendorSdk(lpparam);

        XposedBridge.log(TAG + "✅ 广告 ID 伪造模块初始化完成");
    }

    /**
     * Hook Google Advertising ID (GAID)
     */
    private static void hookGoogleAdvertisingId(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook AdvertisingIdClient$Info.getId()
        try {
            Class<?> infoClass = XposedHelpers.findClass(
                    "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    infoClass,
                    "getId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String originalGaid = (String) param.getResult();
                            String fakeGaid = HookInit.Gaid;

                            param.setResult(fakeGaid);
                            XposedBridge.log(TAG + "【GAID 伪造】原值: " + originalGaid +
                                    " → 伪造值: " + fakeGaid);
                        }
                    });

            XposedBridge.log(TAG + "Hook GAID (AdvertisingIdClient$Info.getId) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook GAID 失败 (可能未安装 Google Play Services): " + t.getMessage());
        }

        // Hook AdvertisingIdClient.getAdvertisingIdInfo() 的返回对象
        try {
            Class<?> clientClass = XposedHelpers.findClass(
                    "com.google.android.gms.ads.identifier.AdvertisingIdClient",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    clientClass,
                    "getAdvertisingIdInfo",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "【GAID】getAdvertisingIdInfo() 被调用");
                        }
                    });

            XposedBridge.log(TAG + "Hook GAID (AdvertisingIdClient.getAdvertisingIdInfo) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook getAdvertisingIdInfo 失败: " + t.getMessage());
        }
    }

    /**
     * Hook OAID - MSA SDK (移动安全联盟)
     */
    private static void hookOaidMsaSdk(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook MdidSdkHelper 回调
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                    "com.bun.miitmdid.interfaces.IIdentifierListener",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(listenerClass, "OnSupport",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // IdSupplier 是第二个参数
                            if (param.args.length >= 2 && param.args[1] != null) {
                                Object idSupplier = param.args[1];
                                hookIdSupplier(idSupplier);
                            }
                        }
                    });

            XposedBridge.log(TAG + "Hook OAID (MSA IIdentifierListener.OnSupport) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MSA SDK 失败 (可能未集成): " + t.getMessage());
        }

        // Hook IdSupplier.getOAID()
        try {
            Class<?> supplierClass = XposedHelpers.findClass(
                    "com.bun.miitmdid.interfaces.IdSupplier",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    supplierClass,
                    "getOAID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String originalOaid = (String) param.getResult();
                            String fakeOaid = HookInit.Oaid;

                            param.setResult(fakeOaid);
                            XposedBridge.log(TAG + "【OAID 伪造 (MSA)】原值: " + originalOaid +
                                    " → 伪造值: " + fakeOaid);
                        }
                    });

            XposedBridge.log(TAG + "Hook OAID (MSA IdSupplier.getOAID) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook IdSupplier.getOAID 失败: " + t.getMessage());
        }
    }

    /**
     * Hook IdSupplier 对象的 getOAID 方法
     */
    private static void hookIdSupplier(Object idSupplier) {
        try {
            XposedHelpers.findAndHookMethod(
                    idSupplier.getClass(),
                    "getOAID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String originalOaid = (String) param.getResult();
                            String fakeOaid = HookInit.Oaid;

                            param.setResult(fakeOaid);
                            XposedBridge.log(TAG + "【OAID 伪造 (IdSupplier)】原值: " +
                                    originalOaid + " → 伪造值: " + fakeOaid);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook IdSupplier 实例失败: " + t.getMessage());
        }
    }

    /**
     * Hook OAID - 各厂商 SDK
     */
    private static void hookOaidVendorSdk(XC_LoadPackage.LoadPackageParam lpparam) {
        // 华为 HMS
        hookHuaweiOaid(lpparam);

        // 小米
        hookXiaomiOaid(lpparam);

        // OPPO
        hookOppoOaid(lpparam);

        // vivo
        hookVivoOaid(lpparam);
    }

    /**
     * Hook 华为 OAID
     */
    private static void hookHuaweiOaid(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> huaweiAdIdClass = XposedHelpers.findClass(
                    "com.huawei.hms.ads.identifier.AdvertisingIdClient$Info",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    huaweiAdIdClass,
                    "getId",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String original = (String) param.getResult();
                            String fake = HookInit.Oaid;

                            param.setResult(fake);
                            XposedBridge.log(TAG + "【OAID 伪造 (华为)】原值: " + original +
                                    " → 伪造值: " + fake);
                        }
                    });

            XposedBridge.log(TAG + "Hook OAID (华为 HMS) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook 华为 OAID 失败 (可能未集成 HMS): " + t.getMessage());
        }
    }

    /**
     * Hook 小米 OAID
     */
    private static void hookXiaomiOaid(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> xiaomiIdClass = XposedHelpers.findClass(
                    "com.miui.deviceid.IdentifierManager",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    xiaomiIdClass,
                    "getOAID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String original = (String) param.getResult();
                            String fake = HookInit.Oaid;

                            param.setResult(fake);
                            XposedBridge.log(TAG + "【OAID 伪造 (小米)】原值: " + original +
                                    " → 伪造值: " + fake);
                        }
                    });

            XposedBridge.log(TAG + "Hook OAID (小米) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook 小米 OAID 失败: " + t.getMessage());
        }
    }

    /**
     * Hook OPPO OAID
     */
    private static void hookOppoOaid(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> oppoIdClass = XposedHelpers.findClass(
                    "com.heytap.openid.OpenIdManager",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    oppoIdClass,
                    "getOAID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String original = (String) param.getResult();
                            String fake = HookInit.Oaid;

                            param.setResult(fake);
                            XposedBridge.log(TAG + "【OAID 伪造 (OPPO)】原值: " + original +
                                    " → 伪造值: " + fake);
                        }
                    });

            XposedBridge.log(TAG + "Hook OAID (OPPO) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook OPPO OAID 失败: " + t.getMessage());
        }
    }

    /**
     * Hook vivo OAID
     */
    private static void hookVivoOaid(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> vivoIdClass = XposedHelpers.findClass(
                    "com.vivo.identifier.IdentifierIdClient",
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    vivoIdClass,
                    "getOAID",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String original = (String) param.getResult();
                            String fake = HookInit.Oaid;

                            param.setResult(fake);
                            XposedBridge.log(TAG + "【OAID 伪造 (vivo)】原值: " + original +
                                    " → 伪造值: " + fake);
                        }
                    });

            XposedBridge.log(TAG + "Hook OAID (vivo) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook vivo OAID 失败: " + t.getMessage());
        }
    }
}
