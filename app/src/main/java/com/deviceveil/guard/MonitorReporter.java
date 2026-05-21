package com.deviceveil.guard;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MonitorReporter {
    private static volatile Context appContext;
    private static volatile String targetPackage;
    private static volatile String processName;

    private MonitorReporter() {
    }

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        targetPackage = lpparam.packageName;
        processName = lpparam.processName;
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = (Context) param.args[0];
                    if (context != null) {
                        appContext = context.getApplicationContext();
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void api(String name, String detail) {
        report("api", name, detail);
    }

    public static void file(String name, String detail) {
        report("file", name, detail);
    }

    public static void command(String name, String detail) {
        report("command", name, detail);
    }

    public static void property(String name, String detail) {
        report("property", name, detail);
    }

    private static void report(String type, String name, String detail) {
        Context context = appContext;
        if (context == null) return;
        MonitorEventStore.send(context, targetPackage, processName, type, name, detail);
    }
}
