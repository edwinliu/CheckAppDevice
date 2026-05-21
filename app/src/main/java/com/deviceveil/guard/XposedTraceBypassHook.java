package com.deviceveil.guard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Low-risk Java-layer hiding for common Xposed/LSPosed traces.
 */
public class XposedTraceBypassHook {
    private static final String TAG = "[设备信息记录]---[XposedTraceBypassHook] ";

    private static final String[] TRACE_KEYWORDS = {
            "_xposed_",
            "xposed",
            "xc_methodhook",
            "lsposed",
            "edxposed",
            "epicframework",
            "de.robv.android",
            "io.github.lsposed",
            "io.github.libxposed",
            "org.lsposed"
    };

    private static final String[] TRACE_THREAD_PREFIXES = {
            "LSPosed",
            "Xposed",
            "EdXposed",
            "Riru",
            "Zygisk",
            "Magisk"
    };

    private static final String[] SYSTEM_CALLER_PREFIXES = {
            "java.",
            "javax.",
            "android.",
            "androidx.",
            "com.android.",
            "dalvik.",
            "libcore.",
            "sun."
    };

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        hookMethodToString();
        hookFieldToString();
        hookThreadGetName();
        hookClassLoaderToString();
        XposedBridge.log(TAG + "Xposed trace hiding initialized");
    }

    private static boolean isCallerSystem() {
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 4; i < Math.min(stack.length, 12); i++) {
                String className = stack[i].getClassName();
                for (String prefix : SYSTEM_CALLER_PREFIXES) {
                    if (className.startsWith(prefix)) return true;
                }
                return false;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean containsTrace(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        for (String keyword : TRACE_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static String scrub(String value) {
        if (value == null) return null;
        String out = value;
        for (String keyword : TRACE_KEYWORDS) {
            out = out.replace(keyword, "");
            out = out.replace(keyword.toUpperCase(), "");
        }
        return out;
    }

    private static void hookMethodToString() {
        try {
            XposedHelpers.findAndHookMethod(Method.class, "toString", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (isCallerSystem()) return;
                    String value = (String) param.getResult();
                    if (containsTrace(value)) {
                        param.setResult(scrub(value));
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Method.toString failed: " + t);
        }
    }

    private static void hookFieldToString() {
        try {
            XposedHelpers.findAndHookMethod(Field.class, "toString", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (isCallerSystem()) return;
                    String value = (String) param.getResult();
                    if (containsTrace(value)) {
                        param.setResult(scrub(value));
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Field.toString failed: " + t);
        }
    }

    private static void hookThreadGetName() {
        try {
            XposedHelpers.findAndHookMethod(Thread.class, "getName", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String name = (String) param.getResult();
                    if (name == null) return;
                    for (String prefix : TRACE_THREAD_PREFIXES) {
                        if (name.startsWith(prefix)) {
                            param.setResult("worker-" + Math.abs(name.hashCode() % 1000));
                            return;
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Thread.getName failed: " + t);
        }
    }

    private static void hookClassLoaderToString() {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "toString", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String value = (String) param.getResult();
                    if (containsTrace(value)) {
                        param.setResult(scrub(value));
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook ClassLoader.toString failed: " + t);
        }

        try {
            Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            XposedHelpers.findAndHookMethod(baseDexClassLoader, "toString", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String value = (String) param.getResult();
                    if (containsTrace(value)) {
                        param.setResult(scrub(value));
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
