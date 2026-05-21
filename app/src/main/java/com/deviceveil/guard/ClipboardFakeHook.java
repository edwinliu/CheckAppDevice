package com.deviceveil.guard;

import android.content.ClipData;
import android.content.ClipDescription;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 剪贴板保护 Hook 模块
 *
 * 功能:
 * 1. 阻止应用读取剪贴板内容，防止隐私泄露
 * 2. Hook ClipboardManager.getPrimaryClip() - 返回空/伪造内容
 * 3. Hook ClipboardManager.getText() - 返回空/伪造内容
 * 4. Hook ClipboardManager.hasPrimaryClip() - 返回 false
 * 5. Hook ClipboardManager.getPrimaryClipDescription() - 返回 null
 *
 * 保护模式:
 * 0 - 返回空内容
 * 1 - 返回伪造内容
 * 2 - 返回 null (模拟无权限)
 */
public class ClipboardFakeHook {

    private static final String TAG = "[设备信息记录]---[ClipboardFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化剪贴板保护 Hook");

        if (!FakeData.CLIPBOARD_PROTECTION_ENABLED) {
            XposedBridge.log(TAG + "剪贴板保护已禁用，跳过初始化");
            return;
        }

        hookClipboardManager(lpparam);

        XposedBridge.log(TAG + "剪贴板保护 Hook 初始化完成");
    }

    // ==================== ClipboardManager Hook ====================
    private static void hookClipboardManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clipboardManagerClass = XposedHelpers.findClass(
                    "android.content.ClipboardManager", lpparam.classLoader);

            // Hook getPrimaryClip() - 获取剪贴板内容
            XposedHelpers.findAndHookMethod(clipboardManagerClass, "getPrimaryClip", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object origClip = param.getResult();
                    String origText = null;

                    // 记录原始内容（用于日志）
                    if (origClip != null) {
                        try {
                            ClipData clipData = (ClipData) origClip;
                            if (clipData.getItemCount() > 0) {
                                CharSequence text = clipData.getItemAt(0).getText();
                                if (text != null) {
                                    origText = text.toString();
                                    if (origText.length() > 50) {
                                        origText = origText.substring(0, 50) + "...";
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 忽略
                        }
                    }

                    XposedBridge.log(TAG + "getPrimaryClip() 原始内容: " + (origText != null ? origText : "(null)"));

                    // 根据保护模式处理
                    switch (FakeData.CLIPBOARD_PROTECTION_MODE) {
                        case 0: // 返回空内容
                            ClipData emptyClip = ClipData.newPlainText("", "");
                            param.setResult(emptyClip);
                            XposedBridge.log(TAG + "getPrimaryClip() 返回空内容");
                            break;
                        case 1: // 返回伪造内容
                            ClipData fakeClip = ClipData.newPlainText("text", FakeData.FAKE_CLIPBOARD_TEXT);
                            param.setResult(fakeClip);
                            XposedBridge.log(TAG + "getPrimaryClip() 返回伪造内容");
                            break;
                        case 2: // 返回 null
                            param.setResult(null);
                            XposedBridge.log(TAG + "getPrimaryClip() 返回 null (模拟无权限)");
                            break;
                    }
                }
            });

            // Hook getText() - 已废弃但部分应用仍在使用
            try {
                XposedHelpers.findAndHookMethod(clipboardManagerClass, "getText", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "getText() 原始内容: " + (orig != null ? orig : "(null)"));

                        switch (FakeData.CLIPBOARD_PROTECTION_MODE) {
                            case 0:
                                param.setResult("");
                                break;
                            case 1:
                                param.setResult(FakeData.FAKE_CLIPBOARD_TEXT);
                                break;
                            case 2:
                                param.setResult(null);
                                break;
                        }
                        XposedBridge.log(TAG + "getText() 已拦截");
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "getText() 方法不存在（已废弃），跳过");
            }

            // Hook hasPrimaryClip() - 检查是否有剪贴板内容
            XposedHelpers.findAndHookMethod(clipboardManagerClass, "hasPrimaryClip", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    XposedBridge.log(TAG + "hasPrimaryClip() 原始值: " + orig);

                    if (FakeData.CLIPBOARD_PROTECTION_MODE == 2) {
                        // 模式 2: 返回 false，假装没有内容
                        param.setResult(false);
                        XposedBridge.log(TAG + "hasPrimaryClip() 返回 false (模拟无权限)");
                    }
                    // 模式 0 和 1: 返回 true，但内容是空/伪造的
                }
            });

            // Hook getPrimaryClipDescription() - 获取剪贴板描述
            XposedHelpers.findAndHookMethod(clipboardManagerClass, "getPrimaryClipDescription", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    XposedBridge.log(TAG + "getPrimaryClipDescription() 原始值: " + (orig != null ? orig : "(null)"));

                    if (FakeData.CLIPBOARD_PROTECTION_MODE == 2) {
                        param.setResult(null);
                        XposedBridge.log(TAG + "getPrimaryClipDescription() 返回 null (模拟无权限)");
                    } else {
                        // 返回一个基本的描述
                        ClipDescription desc = new ClipDescription("text", new String[]{"text/plain"});
                        param.setResult(desc);
                    }
                }
            });

            // Hook addPrimaryClipChangedListener() - 监控剪贴板变化监听
            try {
                XposedHelpers.findAndHookMethod(clipboardManagerClass, "addPrimaryClipChangedListener",
                        "android.content.ClipboardManager$OnPrimaryClipChangedListener", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "应用尝试添加剪贴板变化监听器");
                        // 可以选择阻止添加监听器
                        // param.setResult(null);
                    }
                });
            } catch (Exception e) {
                XposedBridge.log(TAG + "addPrimaryClipChangedListener() Hook 失败: " + e.getMessage());
            }

            XposedBridge.log(TAG + "ClipboardManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "ClipboardManager Hook 失败: " + e.getMessage());
        }
    }
}