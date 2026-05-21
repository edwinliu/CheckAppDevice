package com.deviceveil.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WebView 指纹伪造 Hook 模块
 *
 * 功能:
 * 1. Hook WebSettings.getUserAgentString() - 伪造 User-Agent
 * 2. Hook WebSettings.setUserAgentString() - 记录 UA 设置
 * 3. Hook WebView.getSettings() - 监控 WebView 使用
 * 4. Hook WebSettings.getDefaultUserAgent() - 伪造默认 UA
 */
public class WebViewFakeHook {

    private static final String TAG = "[设备信息记录]---[WebViewFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化 WebView 指纹伪造 Hook");

        hookWebSettings(lpparam);
        hookWebView(lpparam);

        XposedBridge.log(TAG + "WebView 指纹伪造 Hook 初始化完成");
    }

    // ==================== WebSettings Hook ====================
    private static void hookWebSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webSettingsClass = XposedHelpers.findClass("android.webkit.WebSettings", lpparam.classLoader);

            // Hook getUserAgentString() - 返回伪造的 UA
            XposedHelpers.findAndHookMethod(webSettingsClass, "getUserAgentString", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    if (FakeData.WEBVIEW_UA_FAKE_ENABLED) {
                        String fakeUA = FakeData.FAKE_USER_AGENT;
                        XposedBridge.log(TAG + "getUserAgentString() 原始值: " + orig);
                        XposedBridge.log(TAG + "getUserAgentString() 伪造值: " + fakeUA);
                        param.setResult(fakeUA);
                    } else {
                        XposedBridge.log(TAG + "getUserAgentString() (不伪造): " + orig);
                    }
                }
            });

            // Hook setUserAgentString() - 记录但不阻止
            XposedHelpers.findAndHookMethod(webSettingsClass, "setUserAgentString", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String ua = (String) param.args[0];
                    if (FakeData.WEBVIEW_UA_FAKE_ENABLED) {
                        XposedBridge.log(TAG + "setUserAgentString() 应用尝试设置 UA: " + ua);
                        // 强制使用我们的 UA
                        param.args[0] = FakeData.FAKE_USER_AGENT;
                        XposedBridge.log(TAG + "setUserAgentString() 已替换为伪造 UA");
                    } else {
                        XposedBridge.log(TAG + "setUserAgentString() (不伪造): " + ua);
                    }
                }
            });

            // Hook getDefaultUserAgent() - 静态方法
            try {
                XposedHelpers.findAndHookMethod(webSettingsClass, "getDefaultUserAgent",
                        android.content.Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        if (FakeData.WEBVIEW_UA_FAKE_ENABLED) {
                            String fakeUA = FakeData.FAKE_USER_AGENT;
                            XposedBridge.log(TAG + "getDefaultUserAgent() 原始值: " + orig);
                            XposedBridge.log(TAG + "getDefaultUserAgent() 伪造值: " + fakeUA);
                            param.setResult(fakeUA);
                        } else {
                            XposedBridge.log(TAG + "getDefaultUserAgent() (不伪造): " + orig);
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "getDefaultUserAgent() 方法不存在，跳过");
            }

            XposedBridge.log(TAG + "WebSettings Hook 成功 (UA伪造: " + (FakeData.WEBVIEW_UA_FAKE_ENABLED ? "启用" : "禁用") + ")");
        } catch (Exception e) {
            XposedBridge.log(TAG + "WebSettings Hook 失败: " + e.getMessage());
        }
    }

    // ==================== WebView Hook ====================
    private static void hookWebView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);

            // Hook getSettings() - 监控 WebView 使用
            XposedHelpers.findAndHookMethod(webViewClass, "getSettings", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "WebView.getSettings() 被调用");
                }
            });

            // Hook loadUrl() - 监控加载的 URL
            XposedHelpers.findAndHookMethod(webViewClass, "loadUrl", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String url = (String) param.args[0];
                    XposedBridge.log(TAG + "WebView.loadUrl(): " + url);
                }
            });

            // Hook evaluateJavascript() - 监控 JS 执行
            try {
                XposedHelpers.findAndHookMethod(webViewClass, "evaluateJavascript",
                        String.class, android.webkit.ValueCallback.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String script = (String) param.args[0];
                        if (script != null && script.length() > 200) {
                            script = script.substring(0, 200) + "...";
                        }
                        XposedBridge.log(TAG + "WebView.evaluateJavascript(): " + script);
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "evaluateJavascript() 方法不存在，跳过");
            }

            XposedBridge.log(TAG + "WebView Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "WebView Hook 失败: " + e.getMessage());
        }

        // Hook System.getProperty for user.agent
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                    "getProperty", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    if ("http.agent".equals(key)) {
                        Object orig = param.getResult();
                        if (FakeData.WEBVIEW_UA_FAKE_ENABLED) {
                            XposedBridge.log(TAG + "System.getProperty(http.agent) 原始值: " + orig);
                            param.setResult(FakeData.FAKE_USER_AGENT);
                            XposedBridge.log(TAG + "System.getProperty(http.agent) 伪造值: " + FakeData.FAKE_USER_AGENT);
                        } else {
                            XposedBridge.log(TAG + "System.getProperty(http.agent) (不伪造): " + orig);
                        }
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log(TAG + "System.getProperty Hook 失败: " + e.getMessage());
        }
    }
}