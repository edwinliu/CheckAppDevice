/**
 * ============================================================================
 * 文件名：CmdAndFileLogger.java
 * 功能：命令执行和文件访问监控模块
 *
 * 主要功能：
 * 1. 监控 Runtime.exec() 命令执行
 * 2. 监控 ProcessBuilder 进程创建
 * 3. 监控文件存在性检查（File.exists()）
 * 4. 监控目录遍历（File.listFiles()）
 * 5. 监控系统属性查询（SystemProperties.get()）
 * 6. 监控包管理器查询（PackageManager.getInstalledPackages()）
 * 7. 监控 Play Integrity API 调用
 * 8. 过滤和脱敏敏感信息输出
 *
 * 安全特性：
 * - 自动过滤敏感包名（Magisk、Xposed 等）
 * - 脱敏命令输出中的敏感关键词
 * - 防止日志泄露 Root 和框架信息
 *
 * 作者：DeviceVeil Team
 * 版本：4.10
 * 日期：2024-12-06
 * ============================================================================
 */
package com.deviceveil.guard;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 命令和文件访问监控器
 *
 * 该类负责监控应用的命令执行和文件系统访问行为，
 * 帮助分析应用的安全检测机制和隐私收集行为。
 */
public class CmdAndFileLogger {
    // ============================================================================
    // 常量定义
    // ============================================================================

    /** 日志标签，用于在 Xposed 日志中标识本模块的输出 */
    private static final String TAG = "[设备信息记录]---[CmdAndFileLogger] ";

    // ============================================================================
    // 主初始化方法
    // ============================================================================

    /**
     * 初始化所有命令和文件访问监控 Hook
     *
     * 该方法是命令和文件监控模块的入口点，负责：
     * 1. 检测 Android 版本
     * 2. 初始化命令执行监控（Runtime.exec、ProcessBuilder）
     * 3. 初始化文件访问监控（File.exists、File.listFiles）
     * 4. 初始化系统属性和包管理器监控
     * 5. 初始化 Play Integrity API 监控
     *
     * @param lpparam Xposed 加载包参数，包含目标应用的 ClassLoader
     */
    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        // 检测 Android 版本
        boolean isAndroid15OrHigher = Build.VERSION.SDK_INT >= Constants.ANDROID_15_SDK;
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "命令和文件监控模块初始化");
        XposedBridge.log(TAG + "Android 版本: " + Build.VERSION.SDK_INT +
                         " (Android 15+: " + isAndroid15OrHigher + ")");
        XposedBridge.log(TAG + "目标应用: " + lpparam.packageName);
        XposedBridge.log(TAG + "========================================");

        // 步骤 1: 初始化命令执行监控
        XposedBridge.log(TAG + "[初始化] 命令执行监控模块...");
        hookRuntime_exec(lpparam);          // Runtime.exec() 监控
        hookProcessBuilder_start(lpparam);  // ProcessBuilder.start() 监控

        // 步骤 2: 初始化文件访问监控
        XposedBridge.log(TAG + "[初始化] 文件访问监控模块...");
        hookFile_exists(lpparam);           // File.exists() 监控
        hookFile_listFiles(lpparam);        // File.listFiles() 监控

        // 步骤 3: 初始化系统属性监控
        XposedBridge.log(TAG + "[初始化] 系统属性监控模块...");
        hookSystemProperties(lpparam);      // SystemProperties.get() 监控

        // 步骤 4: 初始化包管理器监控
        XposedBridge.log(TAG + "[初始化] 包管理器监控模块...");
        hookPackageManager(lpparam);        // PackageManager.getInstalledPackages() 监控

        // 步骤 5: 初始化 Play Integrity 监控
        XposedBridge.log(TAG + "[初始化] Play Integrity 监控模块...");
        hookPlayIntegrity(lpparam);         // Play Integrity API 监控

        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "✅ 命令和文件监控模块初始化完成");
        XposedBridge.log(TAG + "========================================");
    }

    // ============================================================================
    // 命令输出处理方法
    // ============================================================================

    /**
     * 记录进程输出
     *
     * 读取进程的标准输出和标准错误，等待进程完成，
     * 并记录命令执行结果。对敏感命令（如 pm list）进行特殊处理。
     *
     * @param process 要监控的进程对象
     * @param cmdDesc 命令描述（用于日志）
     */

    private static void logProcessOutput(Process process, String cmdDesc) {
        try {
            ProcessOutput output = readProcessStreams(process);
            int exitCode = waitForProcessCompletion(process, cmdDesc);

            if (handleSpecialCommands(cmdDesc, output.stdout, exitCode)) {
                return;
            }

            logCommandResult(cmdDesc, output.stdout, output.stderr, exitCode);
        } catch (Exception e) {
            XposedBridge.log(TAG + "命令输出读取失败 (" + cmdDesc + "): " + e.toString());
            try {
                process.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
    }

    private static ProcessOutput readProcessStreams(Process process) throws IOException, InterruptedException {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        // 异步读 stderr 防阻塞
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stderr.append(sanitizeLogLine(line)).append("\n");
                }
            } catch (IOException ignored) {
            }
        }, "StderrReader");
        stderrThread.start();

        // 同步读 stdout
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                stdout.append(sanitizeLogLine(line)).append("\n");
            }
        }

        stderrThread.join(Constants.STDERR_JOIN_TIMEOUT_MS);

        return new ProcessOutput(stdout.toString(), stderr.toString());
    }

    private static int waitForProcessCompletion(Process process, String cmdDesc) throws InterruptedException {
        boolean finished = process.waitFor(Constants.PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            XposedBridge.log(TAG + "【警告】命令超时强制终止: " + cmdDesc);
            return -1;
        }
        return process.exitValue();
    }

    private static boolean handleSpecialCommands(String cmdDesc, String stdout, int exitCode) {
        // 🔒 特殊处理 pm list：绝不打印包名！
        if (cmdDesc.contains("pm list package") && exitCode == 0) {
            String filtered = filterSensitivePackages(stdout);
            int pkgCount = countLines(filtered);
            XposedBridge.log(TAG + "【pm list】检测到 " + pkgCount + " 个应用（已脱敏）");
            return true;
        }
        return false;
    }

    private static void logCommandResult(String cmdDesc, String stdout, String stderr, int exitCode) {
        String logMsg = String.format(
                "{\"cmd\":\"%s\", \"stdout_len\":%d, \"stderr_len\":%d, \"exitCode\":%d}",
                cmdDesc.replace("\"", "\\\""),
                stdout.length(),
                stderr.length(),
                exitCode
        );
        XposedBridge.log(TAG + "【命令返回】" + logMsg);
    }

    private static class ProcessOutput {
        final String stdout;
        final String stderr;

        ProcessOutput(String stdout, String stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    // 辅助方法


    private static String filterSensitivePackages(String output) {
        for (String kw : Constants.HIDDEN_PACKAGES) {
            output = output.replaceAll("(?m)^package:" + java.util.regex.Pattern.quote(kw) + ".*$\n?", "");
        }
        return output;
    }

    private static int countLines(String str) {
        if (str == null || str.isEmpty()) return 0;
        return str.split("\n", -1).length;
    }

    // === 敏感内容脱敏函数 ===
    private static String sanitizeLogLine(String line) {
        if (line == null) return "";

        // 转为小写用于匹配，但返回原内容脱敏（保留格式）
        String lower = line.toLowerCase();


        // 如果包含敏感词，直接丢弃
        for (String keyword : Constants.SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                // 返回空行 或 替换为 ***（建议返回空，避免残留线索）
                return ""; // 完全丢弃该行
                // 或者：return "*** [SENSITIVE LINE REMOVED] ***";
            }
        }

        // 可选：进一步泛化脱敏（如移除所有 .so 路径？谨慎！）
        // 但一般只需过滤关键词即可

        return line;
    }

    // ============================================================================
    // getprop 命令伪造配置（同型号同系统设备的不同值）
    // ============================================================================

    /** 需要伪造的 getprop 属性及其伪造值 */
    private static final java.util.Map<String, String> FAKE_PROPS = new java.util.HashMap<String, String>() {{
        // 序列号相关（每台设备不同）
        put("ro.serialno", FakeData.RESTRICTED_BUILD_SERIAL);
        put("ro.boot.serialno", FakeData.RESTRICTED_BUILD_SERIAL);
        put("persist.sys.device_id", "");
        // 安全状态（隐藏 Root）
        put("ro.secure", "1");
        put("ro.debuggable", "0");
        put("ro.build.tags", "release-keys");
        put("ro.boot.verifiedbootstate", "green");
        put("ro.boot.flash.locked", "1");
        put("ro.boot.vbmeta.device_state", "locked");
    }};

    private static void hookRuntime_exec(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // exec(String) - 拦截 getprop 命令
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    lpparam.classLoader,
                    "exec",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String cmd = (String) param.args[0];
                            XposedBridge.log(TAG + "【命令执行】exec(String): " + cmd);
                            MonitorReporter.command("Runtime.exec(String)", cmd);
                            logCallStack("exec(String)");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String cmd = (String) param.args[0];
                            if (cmd != null && cmd.startsWith("getprop ")) {
                                String propName = cmd.substring(8).trim();
                                fakeGetpropResult(param, propName);
                            }
                        }
                    });

            // exec(String[]) - 拦截 getprop 命令
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    lpparam.classLoader,
                    "exec",
                    String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String[] cmds = (String[]) param.args[0];
                            XposedBridge.log(TAG + "【命令执行】exec(String[]): " + Arrays.toString(cmds));
                            MonitorReporter.command("Runtime.exec(String[])", Arrays.toString(cmds));
                            logCallStack("exec(String[])");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String[] cmds = (String[]) param.args[0];
                            if (cmds != null && cmds.length >= 2 && "getprop".equals(cmds[0])) {
                                fakeGetpropResult(param, cmds[1]);
                            }
                        }
                    });

            // exec(String, String[]) - 带环境变量
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    lpparam.classLoader,
                    "exec",
                    String.class, String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String cmd = (String) param.args[0];
                            String[] envp = (String[]) param.args[1];
                            XposedBridge.log(TAG + "【命令执行】exec(String, envp): " + cmd);
                            MonitorReporter.command("Runtime.exec(String, envp)", cmd);
                            if (envp != null) {
                                XposedBridge.log(TAG + "  环境变量: " + Arrays.toString(envp));
                            }
                            logCallStack("exec(String, envp)");
                        }
                    });

            // exec(String[], String[]) - 带环境变量
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    lpparam.classLoader,
                    "exec",
                    String[].class, String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String[] cmds = (String[]) param.args[0];
                            String[] envp = (String[]) param.args[1];
                            XposedBridge.log(TAG + "【命令执行】exec(String[], envp): " + Arrays.toString(cmds));
                            MonitorReporter.command("Runtime.exec(String[], envp)", Arrays.toString(cmds));
                            if (envp != null) {
                                XposedBridge.log(TAG + "  环境变量: " + Arrays.toString(envp));
                            }
                            logCallStack("exec(String[], envp)");
                        }
                    });

            // exec(String, String[], File) - 带环境变量和工作目录
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    lpparam.classLoader,
                    "exec",
                    String.class, String[].class, File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String cmd = (String) param.args[0];
                            String[] envp = (String[]) param.args[1];
                            File dir = (File) param.args[2];
                            XposedBridge.log(TAG + "【命令执行】exec(String, envp, dir): " + cmd);
                            MonitorReporter.command("Runtime.exec(String, envp, dir)", cmd + formatWorkingDir(dir));
                            if (envp != null) {
                                XposedBridge.log(TAG + "  环境变量: " + Arrays.toString(envp));
                            }
                            if (dir != null) {
                                XposedBridge.log(TAG + "  工作目录: " + dir.getAbsolutePath());
                            }
                            logCallStack("exec(String, envp, dir)");
                        }
                    });

            // exec(String[], String[], File) - 带环境变量和工作目录
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    lpparam.classLoader,
                    "exec",
                    String[].class, String[].class, File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String[] cmds = (String[]) param.args[0];
                            String[] envp = (String[]) param.args[1];
                            File dir = (File) param.args[2];
                            XposedBridge.log(TAG + "【命令执行】exec(String[], envp, dir): " + Arrays.toString(cmds));
                            MonitorReporter.command("Runtime.exec(String[], envp, dir)", Arrays.toString(cmds) + formatWorkingDir(dir));
                            if (envp != null) {
                                XposedBridge.log(TAG + "  环境变量: " + Arrays.toString(envp));
                            }
                            if (dir != null) {
                                XposedBridge.log(TAG + "  工作目录: " + dir.getAbsolutePath());
                            }
                            logCallStack("exec(String[], envp, dir)");
                        }
                    });

            XposedBridge.log(TAG + "✅ Hook Runtime.exec 所有重载方法成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Runtime.exec 失败: " + t.toString());
        }
    }

    private static void hookProcessBuilder_start(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.ProcessBuilder",
                    lpparam.classLoader,
                    "start",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ProcessBuilder pb = (ProcessBuilder) param.thisObject;
                            XposedBridge.log(TAG + "【命令执行】ProcessBuilder.start()");
                            XposedBridge.log(TAG + "  命令: " + pb.command());
                            File dir = pb.directory();
                            MonitorReporter.command("ProcessBuilder.start", String.valueOf(pb.command()) + formatWorkingDir(dir));
                            if (dir != null) {
                                XposedBridge.log(TAG + "  工作目录: " + dir.getAbsolutePath());
                            }
                            XposedBridge.log(TAG + "  重定向错误流: " + pb.redirectErrorStream());
                            logCallStack("ProcessBuilder.start()");
                        }
                    });
            XposedBridge.log(TAG + "✅ Hook ProcessBuilder.start 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook ProcessBuilder.start 失败: " + t.toString());
        }
    }

    // ============================================================================
    // getprop 命令伪造实现
    // ============================================================================

    /**
     * 伪造 getprop 命令的返回结果
     * 通过替换 Process 对象的输入流来返回伪造值
     */
    private static void fakeGetpropResult(XC_MethodHook.MethodHookParam param, String propName) {
        if (propName == null) return;

        // 检查是否需要伪造
        String fakeValue = FAKE_PROPS.get(propName);
        if (fakeValue == null) {
            // 动态获取伪造值（从 HookInit 中获取）
            if ("ro.serialno".equals(propName) || "ro.boot.serialno".equals(propName)) {
                fakeValue = FakeData.RESTRICTED_BUILD_SERIAL;
            }
        }

        if (fakeValue != null) {
            try {
                Process originalProcess = (Process) param.getResult();
                Process fakeProcess = new FakeProcess(originalProcess, fakeValue + "\n");
                param.setResult(fakeProcess);
                XposedBridge.log(TAG + "【getprop 伪造】" + propName + " → " + fakeValue);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "【getprop 伪造失败】" + propName + ": " + t.getMessage());
            }
        }
    }

    /**
     * 伪造的 Process 类，用于返回伪造的命令输出
     */
    private static class FakeProcess extends Process {
        private final Process original;
        private final java.io.InputStream fakeInputStream;

        FakeProcess(Process original, String fakeOutput) {
            this.original = original;
            this.fakeInputStream = new java.io.ByteArrayInputStream(fakeOutput.getBytes());
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return original.getOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return fakeInputStream;  // 返回伪造的输出
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return original.getErrorStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            return 0;  // 立即返回成功
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            original.destroy();
        }
    }

    /**
     * 记录调用栈信息（仅记录关键帧，避免日志过长）
     */
    private static void logCallStack(String methodName) {
        if (!ModuleRuntime.isSensitiveLoggingEnabled()) {
            return;
        }
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append("【调用栈】").append(methodName).append(":\n");
            int count = 0;
            for (int i = 0; i < stackTrace.length && count < 10; i++) {
                StackTraceElement element = stackTrace[i];
                String className = element.getClassName();
                // 跳过系统类和 Xposed 框架类
                if (className.startsWith("dalvik.") ||
                    className.startsWith("java.lang.Thread") ||
                    className.startsWith("de.robv.android.xposed") ||
                    className.startsWith("com.deviceveil.guard.CmdAndFileLogger")) {
                    continue;
                }
                sb.append("  at ").append(className)
                  .append(".").append(element.getMethodName())
                  .append("(").append(element.getFileName())
                  .append(":").append(element.getLineNumber()).append(")\n");
                count++;
            }
            XposedBridge.log(TAG + sb.toString());
        } catch (Throwable t) {
            // 忽略调用栈记录失败
        }
    }

    // ========== 文件相关 Hook ==========
    private static void hookFile_exists(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.io.File",
                    lpparam.classLoader,
                    "exists",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            File file = (File) param.thisObject;
                            boolean exists = (boolean) param.getResult();
                            if (exists && shouldLogPath(file.getPath())) {
                                XposedBridge.log(TAG + "【文件检测】exists: " + file.getPath() + " (true)");
                                MonitorReporter.file("File.exists", file.getPath());
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook File.exists 失败: " + t.toString());
        }
    }

    private static void hookFile_listFiles(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.io.File",
                    lpparam.classLoader,
                    "listFiles",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            File dir = (File) param.thisObject;
                            if (shouldLogPath(dir.getPath())) {
                                XposedBridge.log(TAG + "【目录遍历】listFiles: " + dir.getPath());
                                MonitorReporter.file("File.listFiles", dir.getPath());
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook File.listFiles 失败: " + t.toString());
        }
    }

    // 优化：shouldLogPath（排除 CA cert 洪水）
    private static boolean shouldLogPath(String path) {
        if (Constants.isTargetOwnPath(path)) {
            return false;
        }
        if (path != null && path.contains("/apex/com.android.conscrypt/cacerts/")) {
            return false;  // 忽略 CA 证书加载
        }
        String[] sensitivePaths = {
                "/system/bin/su", "/sbin/su", "/data/local/tmp", "/proc/", "/dev/",
                "build.prop", "getprop", "/apex/", "/vendor/bin/magisk", "ro.boot.verifiedbootstate"
        };
        for (String s : sensitivePaths) {
            if (path != null && path.contains(s)) {
                return true;
            }
        }
        return false;
    }

    // ========== 系统属性 Hook（增强 ROM 键记录） ==========
    private static void hookSystemProperties(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.SystemProperties",
                    lpparam.classLoader,
                    "get",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            String[] sensitiveKeys = {"ro.secure", "ro.debuggable", "ro.boot.verifiedbootstate", "ro.build.tags"};
                            for (String s : sensitiveKeys) {
                                if (key != null && key.contains(s)) {
                                    XposedBridge.log(TAG + "【属性查询】get(\"" + key + "\") - 敏感键");
                                    MonitorReporter.property("SystemProperties.get", key);
                                    break;
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            String value = (String) param.getResult();
                            if (key != null && (key.contains("ro.secure") || key.contains("ro.debuggable"))) {
                                XposedBridge.log(TAG + "【属性返回】" + key + " = " + value);
                            }
                            // 新增：ROM 键记录
                            if (key != null && (key.contains("ro.") || key.contains("sys.lge.") || key.contains("ro.build."))) {
                                XposedBridge.log(TAG + "【属性返回】" + key + " = " + (value != null ? value : "[empty]") + " (ROM 检查)");
                                MonitorReporter.property("SystemProperties.get", key + "=" + (value != null ? value : ""));
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook SystemProperties.get 失败: " + t.toString());
        }
    }

    // ========== 包管理器 Hook ==========
    private static void hookPackageManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getInstalledPackages",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int flags = (int) param.args[0];
                            XposedBridge.log(TAG + "【包扫描】getInstalledPackages(flags=" + flags + ") - 可能扫描 Root app (A15 兼容)");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            List<?> packages = (List<?>) param.getResult();
                            if (packages != null && packages.size() > 0) {
                                XposedBridge.log(TAG + "【包返回】getInstalledPackages 返回 " + packages.size() + " 个包");
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook PackageManager.getInstalledPackages 成功 (使用 ApplicationPackageManager)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook PackageManager.getInstalledPackages 失败: " + t.toString() + " (A15: 尝试 Zygisk 模式)");
        }
    }

    private static void hookPlayIntegrity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 检查类是否存在
            try {
                Class<?> integrityManagerClass = XposedHelpers.findClass("com.google.android.play.core.integrity.IntegrityManager", lpparam.classLoader);
                Class<?> integrityTokenRequestClass = XposedHelpers.findClass("com.google.android.play.core.integrity.IntegrityTokenRequest", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(
                        integrityManagerClass,
                        "requestIntegrityToken",
                        integrityTokenRequestClass,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                Object request = param.args[0];
                                try {
                                    String nonce = (String) XposedHelpers.callMethod(request, "getNonce");
                                    XposedBridge.log(TAG + "【完整性检查】requestIntegrityToken(nonce=" + nonce + ") - Play Integrity 调用 (A15 兼容)");
                                } catch (Throwable e) {
                                    XposedBridge.log(TAG + "【完整性检查】requestIntegrityToken - nonce 提取失败: " + e.toString());
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object task = param.getResult();
                                if (task != null) {
                                    XposedBridge.log(TAG + "【完整性返回】requestIntegrityToken 返回 Task - 检查 Basic/Strong verdict");
                                }
                            }
                        });
                XposedBridge.log(TAG + "Hook Play Integrity.requestIntegrityToken 成功");
            } catch (Throwable classError) {
                XposedBridge.log(TAG + "跳过 Play Integrity Hook: IntegrityManager 类未加载 (常见于非 Google App；A15: 确保 Play 服务更新)");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook Play Integrity.requestIntegrityToken 失败: " + t.toString() + " (A15: 延迟加载或用 Play Integrity Fix 模块)");
        }
    }

    private static String formatWorkingDir(File dir) {
        return dir == null ? "" : " cwd=" + dir.getAbsolutePath();
    }
}
