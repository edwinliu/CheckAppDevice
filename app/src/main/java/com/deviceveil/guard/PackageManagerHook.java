package com.deviceveil.guard;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PackageManagerHook {
    private static final String TAG = "[设备信息记录]---[PackageManagerHook] ";
    private static final Set<String> HIDDEN_PACKAGES_SET = new HashSet<>(Arrays.asList(Constants.HIDDEN_PACKAGES));

    private static void verbose(String message) {
        if (Constants.VERBOSE_HOOK_LOGS) {
            XposedBridge.log(TAG + message);
        }
    }

    // 提供 getter 方法供 DeviceInfoHandler 使用（直接返回 HookInit 中的值）
    public static Long getFakeInstallTime() {
        return HookInit.Install_time;
    }

    public static Long getFakeUpdateTime() {
        return HookInit.Update_time;
    }

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        hookGetInstalledPackages(lpparam);
        hookGetInstalledApplications(lpparam);
        hookGetPackageInfo(lpparam);
        hookGetApplicationInfo(lpparam);
        hookQueryIntentActivities(lpparam);
        hookResolveActivity(lpparam);
        hookGetPackagesForUid(lpparam);
        hookGetPackageInfoWithFlags(lpparam);
        hookAndroid13FlagOverloads(lpparam);
        // 新增：防止通过其他途径获取安装时间
        hookReflectionAccess(lpparam);
        hookFileLastModified(lpparam);
        hookSettingsSecure(lpparam);
        // Android 15: 伪造安装来源信息
        hookGetInstallSourceInfo(lpparam);
        hookGetInstallerPackageName(lpparam);
        XposedBridge.log(TAG + "PackageManager hooks initialized, using HookInit time: " +
                "install=" + HookInit.Install_time + ", update=" + HookInit.Update_time);
    }

    /**
     * 伪造 PackageInfo 的安装时间和更新时间
     * 直接使用 HookInit 中已加载的时间，确保全局一致
     */
    private static void fakePackageInfoTime(PackageInfo pkg) {
        long origInstall = pkg.firstInstallTime;
        long origUpdate = pkg.lastUpdateTime;

        Long fakeInstall = HookInit.Install_time;
        Long fakeUpdate = HookInit.Update_time;

        if (fakeInstall != null) {
            pkg.firstInstallTime = fakeInstall;
        }
        if (fakeUpdate != null) {
            pkg.lastUpdateTime = fakeUpdate;
        }

        verbose("伪造时间: 安装 " + origInstall + " -> " + pkg.firstInstallTime +
                ", 更新 " + origUpdate + " -> " + pkg.lastUpdateTime);
    }

    private static boolean shouldHidePackage(String packageName) {
        return packageName != null && HIDDEN_PACKAGES_SET.contains(packageName);
    }

    private static void setNameNotFound(XC_MethodHook.MethodHookParam param, String packageName) {
        XposedBridge.log(TAG + "  已拦截敏感包查询: " + packageName);
        param.setThrowable(new android.content.pm.PackageManager.NameNotFoundException(packageName));
    }

    private static List<PackageInfo> filterPackageInfoList(List<PackageInfo> packages) {
        if (packages == null) return null;
        List<PackageInfo> filtered = new ArrayList<>();
        List<String> hiddenList = new ArrayList<>();
        for (PackageInfo pkg : packages) {
            if (pkg != null && shouldHidePackage(pkg.packageName)) {
                hiddenList.add(pkg.packageName);
                continue;
            }
            if (pkg != null && Constants.TARGET_PACKAGE.equals(pkg.packageName)) {
                fakePackageInfoTime(pkg);
            }
            filtered.add(pkg);
        }
        if (!hiddenList.isEmpty()) {
            XposedBridge.log(TAG + "PackageInfoFlags 已隐藏: " + hiddenList);
        }
        return filtered;
    }

    private static List<ApplicationInfo> filterApplicationInfoList(List<ApplicationInfo> apps) {
        if (apps == null) return null;
        List<ApplicationInfo> filtered = new ArrayList<>();
        List<String> hiddenList = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (app != null && shouldHidePackage(app.packageName)) {
                hiddenList.add(app.packageName);
            } else {
                filtered.add(app);
            }
        }
        if (!hiddenList.isEmpty()) {
            XposedBridge.log(TAG + "ApplicationInfoFlags 已隐藏: " + hiddenList);
        }
        return filtered;
    }

    private static List<ResolveInfo> filterResolveInfoList(List<ResolveInfo> results) {
        if (results == null) return null;
        List<ResolveInfo> filtered = new ArrayList<>();
        List<String> hiddenList = new ArrayList<>();
        for (ResolveInfo info : results) {
            String pkgName = info != null && info.activityInfo != null ? info.activityInfo.packageName : null;
            if (shouldHidePackage(pkgName)) {
                hiddenList.add(pkgName);
            } else {
                filtered.add(info);
            }
        }
        if (!hiddenList.isEmpty()) {
            XposedBridge.log(TAG + "ResolveInfoFlags 已隐藏: " + hiddenList);
        }
        return filtered;
    }

    /**
     * Hook getInstalledPackages - 过滤已安装包列表
     */
    private static void hookGetInstalledPackages(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getInstalledPackages",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int flags = (int) param.args[0];
                            @SuppressWarnings("unchecked")
                            List<PackageInfo> packages = (List<PackageInfo>) param.getResult();
                            if (packages == null) return;

                            int originalCount = packages.size();
                            List<PackageInfo> filtered = new ArrayList<>();
                            List<String> hiddenList = new ArrayList<>();
                            for (PackageInfo pkg : packages) {
                                if (HIDDEN_PACKAGES_SET.contains(pkg.packageName)) {
                                    hiddenList.add(pkg.packageName);
                                } else {
                                    // 修改目标包的安装时间
                                    if (Constants.TARGET_PACKAGE.equals(pkg.packageName)) {
                                        fakePackageInfoTime(pkg);
                                    }
                                    filtered.add(pkg);
                                }
                            }
                            param.setResult(filtered);

                            XposedBridge.log(TAG + "getInstalledPackages(flags=" + flags + ")");
                            XposedBridge.log(TAG + "  原始数量: " + originalCount + ", 过滤后: " + filtered.size());
                            if (!hiddenList.isEmpty()) {
                                XposedBridge.log(TAG + "  已隐藏: " + hiddenList);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getInstalledPackages(int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getInstalledPackages failed: " + t.getMessage());
        }
    }

    /**
     * Hook getInstalledApplications - 过滤已安装应用列表
     */
    private static void hookGetInstalledApplications(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getInstalledApplications",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int flags = (int) param.args[0];
                            @SuppressWarnings("unchecked")
                            List<ApplicationInfo> apps = (List<ApplicationInfo>) param.getResult();
                            if (apps == null) return;

                            int originalCount = apps.size();
                            List<ApplicationInfo> filtered = new ArrayList<>();
                            List<String> hiddenList = new ArrayList<>();
                            for (ApplicationInfo app : apps) {
                                if (HIDDEN_PACKAGES_SET.contains(app.packageName)) {
                                    hiddenList.add(app.packageName);
                                } else {
                                    filtered.add(app);
                                }
                            }
                            param.setResult(filtered);

                            XposedBridge.log(TAG + "getInstalledApplications(flags=" + flags + ")");
                            XposedBridge.log(TAG + "  原始数量: " + originalCount + ", 过滤后: " + filtered.size());
                            if (!hiddenList.isEmpty()) {
                                XposedBridge.log(TAG + "  已隐藏: " + hiddenList);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getInstalledApplications(int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getInstalledApplications failed: " + t.getMessage());
        }
    }

    /**
     * Hook getPackageInfo - 对敏感包抛出 NameNotFoundException
     */
    private static void hookGetPackageInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getPackageInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            int flags = (int) param.args[1];
                            verbose("getPackageInfo(pkg=" + packageName + ", flags=" + flags + ")");
                            if (HIDDEN_PACKAGES_SET.contains(packageName)) {
                                XposedBridge.log(TAG + "  已拦截敏感包查询，抛出 NameNotFoundException");
                                throw new android.content.pm.PackageManager.NameNotFoundException(packageName);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            PackageInfo result = (PackageInfo) param.getResult();
                            if (result != null) {
                                // 修改目标包的安装时间和更新时间
                                if (Constants.TARGET_PACKAGE.equals(result.packageName)) {
                                    fakePackageInfoTime(result);
                                }
                                verbose("  返回: " + result.packageName +
                                        " (versionName=" + result.versionName + ")");
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getPackageInfo(String, int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getPackageInfo failed: " + t.getMessage());
        }
    }

    /**
     * Hook getApplicationInfo - 对敏感包抛出 NameNotFoundException
     */
    private static void hookGetApplicationInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getApplicationInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            int flags = (int) param.args[1];
                            verbose("getApplicationInfo(pkg=" + packageName + ", flags=" + flags + ")");
                            if (HIDDEN_PACKAGES_SET.contains(packageName)) {
                                XposedBridge.log(TAG + "  已拦截敏感包查询，抛出 NameNotFoundException");
                                throw new android.content.pm.PackageManager.NameNotFoundException(packageName);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ApplicationInfo result = (ApplicationInfo) param.getResult();
                            if (result != null) {
                                verbose("  返回: " + result.packageName +
                                        " (sourceDir=" + result.sourceDir + ")");
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getApplicationInfo(String, int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getApplicationInfo failed: " + t.getMessage());
        }
    }

    /**
     * Hook queryIntentActivities - 过滤 Intent 查询结果中的敏感包
     */
    private static void hookQueryIntentActivities(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "queryIntentActivities",
                    Intent.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            @SuppressWarnings("unchecked")
                            List<ResolveInfo> results = (List<ResolveInfo>) param.getResult();
                            if (results == null || results.isEmpty()) return;

                            int originalCount = results.size();
                            List<ResolveInfo> filtered = new ArrayList<>();
                            List<String> hiddenList = new ArrayList<>();
                            for (ResolveInfo info : results) {
                                String pkgName = info.activityInfo != null ?
                                        info.activityInfo.packageName : null;
                                if (pkgName != null && HIDDEN_PACKAGES_SET.contains(pkgName)) {
                                    hiddenList.add(pkgName);
                                } else {
                                    filtered.add(info);
                                }
                            }
                            param.setResult(filtered);

                            if (!hiddenList.isEmpty()) {
                                XposedBridge.log(TAG + "queryIntentActivities 已隐藏: " + hiddenList);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked queryIntentActivities(Intent, int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook queryIntentActivities failed: " + t.getMessage());
        }
    }

    /**
     * Hook resolveActivity - 如果解析到敏感包则返回 null
     */
    private static void hookResolveActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "resolveActivity",
                    Intent.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ResolveInfo result = (ResolveInfo) param.getResult();
                            if (result == null) return;

                            String pkgName = result.activityInfo != null ?
                                    result.activityInfo.packageName : null;
                            if (pkgName != null && HIDDEN_PACKAGES_SET.contains(pkgName)) {
                                XposedBridge.log(TAG + "resolveActivity 已拦截敏感包: " + pkgName);
                                param.setResult(null);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked resolveActivity(Intent, int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook resolveActivity failed: " + t.getMessage());
        }
    }

    /**
     * Hook getPackagesForUid - 过滤 UID 对应的敏感包名
     */
    private static void hookGetPackagesForUid(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getPackagesForUid",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String[] packages = (String[]) param.getResult();
                            if (packages == null || packages.length == 0) return;

                            List<String> filtered = new ArrayList<>();
                            List<String> hiddenList = new ArrayList<>();
                            for (String pkg : packages) {
                                if (HIDDEN_PACKAGES_SET.contains(pkg)) {
                                    hiddenList.add(pkg);
                                } else {
                                    filtered.add(pkg);
                                }
                            }

                            if (!hiddenList.isEmpty()) {
                                XposedBridge.log(TAG + "getPackagesForUid 已隐藏: " + hiddenList);
                                param.setResult(filtered.isEmpty() ? null :
                                        filtered.toArray(new String[0]));
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getPackagesForUid(int)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getPackagesForUid failed: " + t.getMessage());
        }
    }

    /**
     * Hook getPackageInfo with PackageInfoFlags (Android 13+)
     */
    private static void hookGetPackageInfoWithFlags(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Android 13+ 使用 PackageManager.PackageInfoFlags 类型
            Class<?> flagsClass = XposedHelpers.findClassIfExists(
                    "android.content.pm.PackageManager$PackageInfoFlags",
                    lpparam.classLoader);
            if (flagsClass == null) {
                XposedBridge.log(TAG + "PackageInfoFlags not found (Android < 13), skipping");
                return;
            }

            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getPackageInfo",
                    String.class,
                    flagsClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            verbose("getPackageInfo(pkg=" + packageName +
                                    ", PackageInfoFlags)");
                            if (HIDDEN_PACKAGES_SET.contains(packageName)) {
                                XposedBridge.log(TAG + "  已拦截敏感包查询");
                                throw new android.content.pm.PackageManager
                                        .NameNotFoundException(packageName);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            PackageInfo result = (PackageInfo) param.getResult();
                            if (result != null &&
                                    Constants.TARGET_PACKAGE.equals(result.packageName)) {
                                fakePackageInfoTime(result);
                                verbose("  已伪造安装时间");
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getPackageInfo(String, PackageInfoFlags)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getPackageInfo(PackageInfoFlags) failed: " + t.getMessage());
        }
    }

    /**
     * Android 13+ PackageManager flag wrapper overloads.
     */
    private static void hookAndroid13FlagOverloads(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> packageInfoFlagsClass = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager$PackageInfoFlags", lpparam.classLoader);
        Class<?> applicationInfoFlagsClass = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager$ApplicationInfoFlags", lpparam.classLoader);
        Class<?> resolveInfoFlagsClass = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager$ResolveInfoFlags", lpparam.classLoader);

        if (packageInfoFlagsClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        lpparam.classLoader,
                        "getInstalledPackages",
                        packageInfoFlagsClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                @SuppressWarnings("unchecked")
                                List<PackageInfo> packages = (List<PackageInfo>) param.getResult();
                                param.setResult(filterPackageInfoList(packages));
                            }
                        });
                XposedBridge.log(TAG + "hooked getInstalledPackages(PackageInfoFlags)");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook getInstalledPackages(PackageInfoFlags) failed: " + t.getMessage());
            }
        }

        if (applicationInfoFlagsClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        lpparam.classLoader,
                        "getApplicationInfo",
                        String.class,
                        applicationInfoFlagsClass,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String packageName = (String) param.args[0];
                                if (shouldHidePackage(packageName)) {
                                    setNameNotFound(param, packageName);
                                }
                            }
                        });
                XposedBridge.log(TAG + "hooked getApplicationInfo(String, ApplicationInfoFlags)");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook getApplicationInfo(ApplicationInfoFlags) failed: " + t.getMessage());
            }

            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        lpparam.classLoader,
                        "getInstalledApplications",
                        applicationInfoFlagsClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                @SuppressWarnings("unchecked")
                                List<ApplicationInfo> apps = (List<ApplicationInfo>) param.getResult();
                                param.setResult(filterApplicationInfoList(apps));
                            }
                        });
                XposedBridge.log(TAG + "hooked getInstalledApplications(ApplicationInfoFlags)");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook getInstalledApplications(ApplicationInfoFlags) failed: " + t.getMessage());
            }
        }

        if (resolveInfoFlagsClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        lpparam.classLoader,
                        "queryIntentActivities",
                        Intent.class,
                        resolveInfoFlagsClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                @SuppressWarnings("unchecked")
                                List<ResolveInfo> results = (List<ResolveInfo>) param.getResult();
                                param.setResult(filterResolveInfoList(results));
                            }
                        });
                XposedBridge.log(TAG + "hooked queryIntentActivities(Intent, ResolveInfoFlags)");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook queryIntentActivities(ResolveInfoFlags) failed: " + t.getMessage());
            }

            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        lpparam.classLoader,
                        "resolveActivity",
                        Intent.class,
                        resolveInfoFlagsClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                ResolveInfo result = (ResolveInfo) param.getResult();
                                String pkgName = result != null && result.activityInfo != null
                                        ? result.activityInfo.packageName : null;
                                if (shouldHidePackage(pkgName)) {
                                    XposedBridge.log(TAG + "resolveActivity(ResolveInfoFlags) 已拦截敏感包: " + pkgName);
                                    param.setResult(null);
                                }
                            }
                        });
                XposedBridge.log(TAG + "hooked resolveActivity(Intent, ResolveInfoFlags)");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook resolveActivity(ResolveInfoFlags) failed: " + t.getMessage());
            }
        }
    }

    /**
     * Hook 反射访问 PackageInfo 的 firstInstallTime 和 lastUpdateTime 字段
     * 防止通过 Field.getLong() 绕过 PackageManager API 获取真实时间
     */
    private static void hookReflectionAccess(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    Field.class,
                    "getLong",
                    Object.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Field field = (Field) param.thisObject;
                            Object obj = param.args[0];
                            if (obj instanceof PackageInfo) {
                                PackageInfo pkg = (PackageInfo) obj;
                                if (Constants.TARGET_PACKAGE.equals(pkg.packageName)) {
                                    String fieldName = field.getName();
                                    if ("firstInstallTime".equals(fieldName) && HookInit.Install_time != null) {
                                        param.setResult(HookInit.Install_time);
                                        XposedBridge.log(TAG + "反射拦截 firstInstallTime -> " + HookInit.Install_time);
                                    } else if ("lastUpdateTime".equals(fieldName) && HookInit.Update_time != null) {
                                        param.setResult(HookInit.Update_time);
                                        XposedBridge.log(TAG + "反射拦截 lastUpdateTime -> " + HookInit.Update_time);
                                    }
                                }
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked Field.getLong() for reflection access");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Field.getLong failed: " + t.getMessage());
        }
    }

    /**
     * Hook File.lastModified() - 伪造 APK 文件的修改时间
     * 防止通过 new File(sourceDir).lastModified() 获取真实安装时间
     */
    private static void hookFileLastModified(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    File.class,
                    "lastModified",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            File file = (File) param.thisObject;
                            String path = file.getAbsolutePath();
                            // 检查是否是目标应用的 APK 或数据目录
                            if (Constants.TARGET_PACKAGE != null &&
                                    !Constants.TARGET_PACKAGE.isEmpty() &&
                                    path.contains(Constants.TARGET_PACKAGE)) {
                                Long fakeTime = HookInit.Update_time;
                                if (fakeTime != null) {
                                    param.setResult(fakeTime);
                                    verbose("File.lastModified 伪造: " + path + " -> " + fakeTime);
                                }
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked File.lastModified()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook File.lastModified failed: " + t.getMessage());
        }
    }

    /**
     * Hook Settings.Secure.getString/getLong - 拦截可能存储安装时间的系统设置查询
     */
    private static void hookSettingsSecure(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Settings.Secure.getString
            XposedHelpers.findAndHookMethod(
                    "android.provider.Settings$Secure",
                    lpparam.classLoader,
                    "getString",
                    android.content.ContentResolver.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String name = (String) param.args[1];
                            // 拦截可能包含安装时间的设置项
                            if (name != null && (name.contains("install") || name.contains("first_boot"))) {
                                XposedBridge.log(TAG + "Settings.Secure.getString 查询: " + name);
                            }
                        }
                    }
            );

            // Hook Settings.Secure.getLong
            XposedHelpers.findAndHookMethod(
                    "android.provider.Settings$Secure",
                    lpparam.classLoader,
                    "getLong",
                    android.content.ContentResolver.class,
                    String.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String name = (String) param.args[1];
                            if (name != null && (name.contains("install") || name.contains("first_boot"))) {
                                XposedBridge.log(TAG + "Settings.Secure.getLong 查询: " + name +
                                        " = " + param.getResult());
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked Settings.Secure");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Settings.Secure failed: " + t.getMessage());
        }
    }

    /**
     * Hook PackageManager.getInstallSourceInfo() (Android 11+, API 30+)
     * 伪造安装来源为 Google Play，防止风控检测到非正规渠道安装
     */
    private static void hookGetInstallSourceInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> installSourceInfoClass = XposedHelpers.findClassIfExists(
                    "android.content.pm.InstallSourceInfo", lpparam.classLoader);
            if (installSourceInfoClass == null) {
                XposedBridge.log(TAG + "InstallSourceInfo not found (Android < 11), skipping");
                return;
            }

            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getInstallSourceInfo",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            Object result = param.getResult();
                            if (result == null) return;

                            if (Constants.TARGET_PACKAGE.equals(packageName)) {
                                try {
                                    // 伪造 initiatingPackageName 和 installingPackageName 为 Google Play
                                    XposedHelpers.setObjectField(result, "mInitiatingPackageName", "com.android.vending");
                                    XposedHelpers.setObjectField(result, "mInstallingPackageName", "com.android.vending");
                                    XposedBridge.log(TAG + "getInstallSourceInfo(" + packageName + ") 已伪造为 com.android.vending");
                                } catch (Throwable t) {
                                    // 字段名可能不同，尝试通过方法 Hook
                                    XposedBridge.log(TAG + "getInstallSourceInfo 字段设置失败，尝试 Hook 方法: " + t.getMessage());
                                }
                            }
                        }
                    }
            );

            // Hook InstallSourceInfo 的 getter 方法
            try {
                XposedHelpers.findAndHookMethod(installSourceInfoClass, "getInstallingPackageName",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult("com.android.vending");
                            }
                        });
                XposedHelpers.findAndHookMethod(installSourceInfoClass, "getInitiatingPackageName",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult("com.android.vending");
                            }
                        });
                XposedBridge.log(TAG + "hooked InstallSourceInfo getter methods");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook InstallSourceInfo getters failed: " + t.getMessage());
            }

            XposedBridge.log(TAG + "hooked getInstallSourceInfo(String)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getInstallSourceInfo failed: " + t.getMessage());
        }
    }

    /**
     * Hook PackageManager.getInstallerPackageName() (已废弃但仍被使用)
     * 伪造安装来源为 Google Play
     */
    private static void hookGetInstallerPackageName(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    lpparam.classLoader,
                    "getInstallerPackageName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            if (Constants.TARGET_PACKAGE.equals(packageName)) {
                                String orig = (String) param.getResult();
                                param.setResult("com.android.vending");
                                XposedBridge.log(TAG + "getInstallerPackageName(" + packageName +
                                        ") 原始值: " + orig + " -> 伪造值: com.android.vending");
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + "hooked getInstallerPackageName(String)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook getInstallerPackageName failed: " + t.getMessage());
        }
    }
}
