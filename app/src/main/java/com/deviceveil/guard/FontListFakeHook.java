package com.deviceveil.guard;

import android.graphics.Typeface;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 字体列表伪造 Hook 模块
 *
 * 功能:
 * 1. 伪造系统字体列表，防止字体指纹追踪
 * 2. Hook Typeface.create() - 字体创建
 * 3. Hook Typeface 内部字体映射
 * 4. Hook File.list() 对 /system/fonts 目录的枚举
 * 5. Hook File.exists() 对字体文件的存在性检查
 *
 * 字体指纹原理:
 * - 不同设备安装的字体不同
 * - 通过枚举字体或测量特定字体渲染结果可以生成设备指纹
 */
public class FontListFakeHook {

    private static final String TAG = "[设备信息记录]---[FontListFakeHook] ";
    private static final String FONT_DIR = "/system/fonts";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化字体列表伪造 Hook");

        if (!FakeData.FONT_LIST_FAKE_ENABLED) {
            XposedBridge.log(TAG + "字体列表伪造已禁用，跳过初始化");
            return;
        }

        hookFileList(lpparam);
        hookFileExists(lpparam);
        hookTypefaceCreate(lpparam);
        hookTypefaceNative(lpparam);

        XposedBridge.log(TAG + "字体列表伪造 Hook 初始化完成");
    }

    // ==================== File.list() Hook ====================
    private static void hookFileList(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(File.class, "list", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    File file = (File) param.thisObject;
                    String path = file.getAbsolutePath();

                    // 只处理字体目录
                    if (FONT_DIR.equals(path) || path.startsWith(FONT_DIR + "/")) {
                        String[] origList = (String[]) param.getResult();
                        int origCount = origList != null ? origList.length : 0;

                        XposedBridge.log(TAG + "File.list(" + path + ") 原始文件数: " + origCount);

                        // 返回伪造的字体文件列表
                        param.setResult(FakeData.FAKE_FONT_FILES);
                        XposedBridge.log(TAG + "File.list(" + path + ") 伪造文件数: " + FakeData.FAKE_FONT_FILES.length);
                    }
                }
            });

            // Hook listFiles() - 返回 File 数组
            XposedHelpers.findAndHookMethod(File.class, "listFiles", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    File file = (File) param.thisObject;
                    String path = file.getAbsolutePath();

                    if (FONT_DIR.equals(path)) {
                        File[] origFiles = (File[]) param.getResult();
                        int origCount = origFiles != null ? origFiles.length : 0;

                        XposedBridge.log(TAG + "File.listFiles(" + path + ") 原始文件数: " + origCount);

                        // 返回伪造的字体文件列表
                        File[] fakeFiles = new File[FakeData.FAKE_FONT_FILES.length];
                        for (int i = 0; i < FakeData.FAKE_FONT_FILES.length; i++) {
                            fakeFiles[i] = new File(FONT_DIR, FakeData.FAKE_FONT_FILES[i]);
                        }
                        param.setResult(fakeFiles);
                        XposedBridge.log(TAG + "File.listFiles(" + path + ") 伪造文件数: " + fakeFiles.length);
                    }
                }
            });

            XposedBridge.log(TAG + "File.list/listFiles Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "File.list/listFiles Hook 失败: " + e.getMessage());
        }
    }

    // ==================== File.exists() Hook ====================
    private static void hookFileExists(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    File file = (File) param.thisObject;
                    String path = file.getAbsolutePath();

                    // 只处理字体文件
                    if (path.startsWith(FONT_DIR + "/")) {
                        String fileName = file.getName();
                        boolean origExists = (boolean) param.getResult();

                        // 检查是否在伪造列表中
                        boolean fakeExists = false;
                        for (String fakeFont : FakeData.FAKE_FONT_FILES) {
                            if (fakeFont.equals(fileName)) {
                                fakeExists = true;
                                break;
                            }
                        }

                        if (origExists != fakeExists) {
                            XposedBridge.log(TAG + "File.exists(" + path + ") 原始: " + origExists + " -> 伪造: " + fakeExists);
                            param.setResult(fakeExists);
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "File.exists Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "File.exists Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Typeface.create() Hook ====================
    private static void hookTypefaceCreate(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Typeface.create(String familyName, int style)
            XposedHelpers.findAndHookMethod(Typeface.class, "create",
                    String.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String familyName = (String) param.args[0];
                    int style = (int) param.args[1];

                    XposedBridge.log(TAG + "Typeface.create(\"" + familyName + "\", " + style + ") 被调用");

                    // 如果请求的字体不在伪造列表中，替换为默认字体
                    if (familyName != null && !isFakeFont(familyName)) {
                        XposedBridge.log(TAG + "字体 \"" + familyName + "\" 不在伪造列表，替换为 sans-serif");
                        param.args[0] = "sans-serif";
                    }
                }
            });

            // Hook Typeface.create(Typeface family, int style)
            XposedHelpers.findAndHookMethod(Typeface.class, "create",
                    Typeface.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "Typeface.create(Typeface, style) 被调用");
                }
            });

            XposedBridge.log(TAG + "Typeface.create Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Typeface.create Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Typeface Native 方法 Hook ====================
    private static void hookTypefaceNative(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Typeface.createFromFile(String path)
            XposedHelpers.findAndHookMethod(Typeface.class, "createFromFile",
                    String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String path = (String) param.args[0];
                    XposedBridge.log(TAG + "Typeface.createFromFile(\"" + path + "\") 被调用");

                    // 检查字体文件是否在允许列表
                    if (path != null && path.startsWith(FONT_DIR)) {
                        String fileName = new File(path).getName();
                        if (!isFakeFontFile(fileName)) {
                            XposedBridge.log(TAG + "字体文件 \"" + fileName + "\" 不在伪造列表，替换为 Roboto-Regular.ttf");
                            param.args[0] = FONT_DIR + "/Roboto-Regular.ttf";
                        }
                    }
                }
            });

            // Hook Typeface.createFromFile(File path)
            XposedHelpers.findAndHookMethod(Typeface.class, "createFromFile",
                    File.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    File file = (File) param.args[0];
                    if (file != null) {
                        String path = file.getAbsolutePath();
                        XposedBridge.log(TAG + "Typeface.createFromFile(File: \"" + path + "\") 被调用");

                        if (path.startsWith(FONT_DIR)) {
                            String fileName = file.getName();
                            if (!isFakeFontFile(fileName)) {
                                XposedBridge.log(TAG + "字体文件 \"" + fileName + "\" 不在伪造列表，替换");
                                param.args[0] = new File(FONT_DIR, "Roboto-Regular.ttf");
                            }
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "Typeface Native Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Typeface Native Hook 失败: " + e.getMessage());
        }
    }

    /**
     * 检查字体名称是否在伪造列表中
     */
    private static boolean isFakeFont(String familyName) {
        if (familyName == null) return false;
        String lowerName = familyName.toLowerCase();
        for (String fakeFont : FakeData.FAKE_FONT_LIST) {
            if (fakeFont.toLowerCase().equals(lowerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查字体文件是否在伪造列表中
     */
    private static boolean isFakeFontFile(String fileName) {
        if (fileName == null) return false;
        for (String fakeFile : FakeData.FAKE_FONT_FILES) {
            if (fakeFile.equals(fileName)) {
                return true;
            }
        }
        return false;
    }
}