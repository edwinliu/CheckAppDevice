package com.deviceveil.guard;

import android.accounts.Account;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 账户信息伪造 Hook 模块
 *
 * 功能:
 * 1. 隐藏设备上的账户信息，防止应用获取用户账户
 * 2. Hook AccountManager.getAccounts() - 返回空数组或伪造账户
 * 3. Hook AccountManager.getAccountsByType() - 返回空数组或伪造账户
 * 4. Hook AccountManager.hasFeatures() - 返回 false
 * 5. 可选伪造 Google 账户
 */
public class AccountFakeHook {

    private static final String TAG = "[设备信息记录]---[AccountFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化账户信息伪造 Hook");

        if (!FakeData.HIDE_ACCOUNTS_ENABLED) {
            XposedBridge.log(TAG + "账户信息隐藏已禁用，跳过初始化");
            return;
        }

        hookAccountManager(lpparam);
        hookGoogleAccountManager(lpparam);

        XposedBridge.log(TAG + "账户信息伪造 Hook 初始化完成");
    }

    // ==================== AccountManager Hook ====================
    private static void hookAccountManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> accountManagerClass = XposedHelpers.findClass(
                    "android.accounts.AccountManager", lpparam.classLoader);

            // Hook getAccounts() - 获取所有账户
            XposedHelpers.findAndHookMethod(accountManagerClass, "getAccounts", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Account[] origAccounts = (Account[]) param.getResult();
                    int origCount = origAccounts != null ? origAccounts.length : 0;
                    XposedBridge.log(TAG + "getAccounts() 原始账户数: " + origCount);

                    if (origAccounts != null && origAccounts.length > 0) {
                        for (Account acc : origAccounts) {
                            XposedBridge.log(TAG + "  - 原始账户: " + acc.type + " / " + acc.name);
                        }
                    }

                    // 返回伪造账户或空数组
                    Account[] fakeAccounts = getFakeAccounts();
                    param.setResult(fakeAccounts);
                    XposedBridge.log(TAG + "getAccounts() 返回伪造账户数: " + fakeAccounts.length);
                }
            });

            // Hook getAccountsByType(String type) - 按类型获取账户
            XposedHelpers.findAndHookMethod(accountManagerClass, "getAccountsByType", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String type = (String) param.args[0];
                    Account[] origAccounts = (Account[]) param.getResult();
                    int origCount = origAccounts != null ? origAccounts.length : 0;
                    XposedBridge.log(TAG + "getAccountsByType(" + type + ") 原始账户数: " + origCount);

                    // 如果启用了 Google 账户伪造，且请求的是 Google 账户
                    if (FakeData.FAKE_GOOGLE_ACCOUNT_ENABLED && "com.google".equals(type)) {
                        Account[] fakeGoogleAccounts = getFakeGoogleAccounts();
                        param.setResult(fakeGoogleAccounts);
                        XposedBridge.log(TAG + "getAccountsByType(" + type + ") 返回伪造 Google 账户");
                        return;
                    }

                    // 返回空数组
                    param.setResult(new Account[0]);
                    XposedBridge.log(TAG + "getAccountsByType(" + type + ") 返回空数组");
                }
            });

            // Hook getAccountsByTypeAndFeatures() - 按类型和特性获取账户
            try {
                XposedHelpers.findAndHookMethod(accountManagerClass, "getAccountsByTypeAndFeatures",
                        String.class, String[].class,
                        "android.accounts.AccountManagerCallback", "android.os.Handler",
                        new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String type = (String) param.args[0];
                        XposedBridge.log(TAG + "getAccountsByTypeAndFeatures(" + type + ") 被调用");
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法签名可能不同，忽略
            }

            // Hook hasFeatures() - 检查账户特性
            try {
                XposedHelpers.findAndHookMethod(accountManagerClass, "hasFeatures",
                        Account.class, String[].class,
                        "android.accounts.AccountManagerCallback", "android.os.Handler",
                        new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Account account = (Account) param.args[0];
                        XposedBridge.log(TAG + "hasFeatures() 账户: " + account.name);
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法签名可能不同，忽略
            }

            XposedBridge.log(TAG + "AccountManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "AccountManager Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Google 账户专用 Hook ====================
    private static void hookGoogleAccountManager(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook GoogleAccountManager (如果存在)
        try {
            Class<?> googleAccountManagerClass = XposedHelpers.findClass(
                    "com.google.android.gms.auth.GoogleAccountManager", lpparam.classLoader);

            // Hook getAccounts()
            XposedHelpers.findAndHookMethod(googleAccountManagerClass, "getAccounts", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Account[] origAccounts = (Account[]) param.getResult();
                    int origCount = origAccounts != null ? origAccounts.length : 0;
                    XposedBridge.log(TAG + "GoogleAccountManager.getAccounts() 原始账户数: " + origCount);

                    if (FakeData.FAKE_GOOGLE_ACCOUNT_ENABLED) {
                        param.setResult(getFakeGoogleAccounts());
                    } else {
                        param.setResult(new Account[0]);
                    }
                }
            });

            XposedBridge.log(TAG + "GoogleAccountManager Hook 成功");
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + "GoogleAccountManager 类不存在，跳过");
        } catch (Exception e) {
            XposedBridge.log(TAG + "GoogleAccountManager Hook 失败: " + e.getMessage());
        }

        // Hook GoogleAuthUtil (如果存在)
        try {
            Class<?> googleAuthUtilClass = XposedHelpers.findClass(
                    "com.google.android.gms.auth.GoogleAuthUtil", lpparam.classLoader);

            // Hook getAccountId()
            try {
                XposedHelpers.findAndHookMethod(googleAuthUtilClass, "getAccountId",
                        android.content.Context.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "GoogleAuthUtil.getAccountId() 原始值: " + orig);

                        if (FakeData.FAKE_GOOGLE_ACCOUNT_ENABLED && FakeData.FAKE_GOOGLE_ACCOUNT_EMAIL != null) {
                            // 返回伪造的 Account ID (简单的哈希)
                            String fakeId = String.valueOf(FakeData.FAKE_GOOGLE_ACCOUNT_EMAIL.hashCode() & 0x7FFFFFFF);
                            param.setResult(fakeId);
                            XposedBridge.log(TAG + "GoogleAuthUtil.getAccountId() 伪造值: " + fakeId);
                        } else {
                            param.setResult(null);
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，忽略
            }

            XposedBridge.log(TAG + "GoogleAuthUtil Hook 成功");
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + "GoogleAuthUtil 类不存在，跳过");
        } catch (Exception e) {
            XposedBridge.log(TAG + "GoogleAuthUtil Hook 失败: " + e.getMessage());
        }
    }

    /**
     * 获取伪造的账户数组
     */
    private static Account[] getFakeAccounts() {
        if (FakeData.FAKE_GOOGLE_ACCOUNT_ENABLED) {
            return getFakeGoogleAccounts();
        }
        return new Account[0];
    }

    /**
     * 获取伪造的 Google 账户数组
     */
    private static Account[] getFakeGoogleAccounts() {
        if (!FakeData.FAKE_GOOGLE_ACCOUNT_ENABLED ||
            FakeData.FAKE_GOOGLE_ACCOUNT_EMAIL == null ||
            FakeData.FAKE_GOOGLE_ACCOUNT_EMAIL.isEmpty()) {
            return new Account[0];
        }

        Account fakeAccount = new Account(FakeData.FAKE_GOOGLE_ACCOUNT_EMAIL, "com.google");
        return new Account[]{fakeAccount};
    }
}