package com.deviceveil.guard;


import static com.deviceveil.guard.HookInit.FAKE_BOOT_TIME;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BootTimeFakeHook {

    private static final String TAG = "[设备信息记录]---[BootTimeFakeHook] ";


    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {

        XposedBridge.log(TAG + "开始 Hook，只替换 /proc/stat 的 btime 行");

        // 方案1：直接 Hook FileReader
        hookProcStatWithFileReader(lpparam);

        // 方案2：Hook BufferedReader.readLine（备用）
        hookProcStatWithBufferedReader(lpparam);

        // Hook /proc/uptime
        hookProcUptime(lpparam);

        // Hook SystemClock 相关方法
        hookSystemClock();
    }

    private static void hookProcStatWithFileReader(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookConstructor("java.io.FileReader",
                    lpparam.classLoader,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String filePath = (String) param.args[0];
                            if ("/proc/stat".equals(filePath)) {
                                String realContent = readRealProcStat();
                                String fakeContent = replaceBtimeLine(realContent);
                                param.setResult(new StringReader(fakeContent));
                            }
                        }
                    });
        } catch (Exception e) {
            XposedBridge.log(TAG + "FileReader Hook 失败: " + e.getMessage());
        }
    }

    private static void hookProcStatWithBufferedReader(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.io.BufferedReader",
                    lpparam.classLoader,
                    "readLine",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String result = (String) param.getResult();
                            if (result != null && result.startsWith("btime ")) {
                                param.setResult("btime " + FAKE_BOOT_TIME);
                            }
                        }
                    });
        } catch (Exception e) {
            XposedBridge.log(TAG + "BufferedReader Hook 失败: " + e.getMessage());
        }
    }

    private static void hookProcUptime(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookConstructor("java.io.FileReader",
                    lpparam.classLoader,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if ("/proc/uptime".equals(param.args[0])) {
                                param.setResult(new StringReader(getFakeUptime()));
                            }
                        }
                    });
        } catch (Exception e) {
            XposedBridge.log(TAG + "Uptime Hook 失败: " + e.getMessage());
        }
    }

    private static String readRealProcStat() {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            XposedBridge.log(TAG + "读取真实 /proc/stat 失败: " + e.getMessage());
        }
        return content.toString();
    }

    private static String replaceBtimeLine(String realContent) {
        String[] lines = realContent.split("\n", -1);
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("btime ")) {
                result.append("btime ").append(FAKE_BOOT_TIME).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    private static String getFakeUptime() {
        long currentTime = System.currentTimeMillis() / 1000;
        long uptimeSeconds = currentTime - FAKE_BOOT_TIME;
        uptimeSeconds = Math.max(60, uptimeSeconds);

        return String.format("%.2f %.2f\n",
                (double) uptimeSeconds,
                (double) (uptimeSeconds * 100.0));
    }

    /**
     * Hook SystemClock 相关方法，伪造系统运行时间
     */
    private static void hookSystemClock() {
        // 计算时间偏移量（毫秒）
        // 偏移量 = 真实开机时间戳 - 伪造开机时间戳
        final long realBootTimeMs = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        final long fakeBootTimeMs = FAKE_BOOT_TIME * 1000L;
        final long offsetMs = realBootTimeMs - fakeBootTimeMs;

        XposedBridge.log(TAG + "SystemClock Hook 初始化，时间偏移量: " + offsetMs + "ms");

        // Hook elapsedRealtime() - 系统启动后经过的毫秒数（含深度睡眠）
        try {
            XposedHelpers.findAndHookMethod(
                    android.os.SystemClock.class,
                    "elapsedRealtime",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long realValue = (long) param.getResult();
                            long fakeValue = realValue + offsetMs;
                            fakeValue = Math.max(60000L, fakeValue); // 至少 60 秒
                            param.setResult(fakeValue);
                        }
                    });
            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() Hook 失败: " + e.getMessage());
        }

        // Hook uptimeMillis() - 系统启动后经过的毫秒数（不含深度睡眠）
        try {
            XposedHelpers.findAndHookMethod(
                    android.os.SystemClock.class,
                    "uptimeMillis",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long realValue = (long) param.getResult();
                            long fakeValue = realValue + offsetMs;
                            fakeValue = Math.max(60000L, fakeValue);
                            param.setResult(fakeValue);
                        }
                    });
            XposedBridge.log(TAG + "SystemClock.uptimeMillis() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "SystemClock.uptimeMillis() Hook 失败: " + e.getMessage());
        }

        // Hook elapsedRealtimeNanos() - 纳秒级精度
        try {
            XposedHelpers.findAndHookMethod(
                    android.os.SystemClock.class,
                    "elapsedRealtimeNanos",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long realValue = (long) param.getResult();
                            long fakeValue = realValue + (offsetMs * 1000000L); // 转换为纳秒
                            fakeValue = Math.max(60000000000L, fakeValue); // 至少 60 秒
                            param.setResult(fakeValue);
                        }
                    });
            XposedBridge.log(TAG + "SystemClock.elapsedRealtimeNanos() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "SystemClock.elapsedRealtimeNanos() Hook 失败: " + e.getMessage());
        }
    }
}