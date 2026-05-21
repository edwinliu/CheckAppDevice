package com.deviceveil.guard;

import android.opengl.GLES10;
import android.opengl.GLES20;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * GPU 信息伪造 Hook 模块
 *
 * 功能:
 * 1. 伪造 OpenGL ES GPU 信息，防止 GPU 指纹追踪
 * 2. Hook GLES10.glGetString() - 旧版 OpenGL ES 1.0 API
 * 3. Hook GLES20.glGetString() - OpenGL ES 2.0 API
 * 4. Hook GLES30.glGetString() - OpenGL ES 3.0 API (如存在)
 * 5. Hook EGL14.eglQueryString() - EGL 信息查询
 *
 * 伪造的属性:
 * - GL_VENDOR: GPU 厂商
 * - GL_RENDERER: GPU 渲染器型号
 * - GL_VERSION: OpenGL ES 版本
 * - GL_EXTENSIONS: 支持的扩展列表
 */
public class GPUFakeHook {

    private static final String TAG = "[设备信息记录]---[GPUFakeHook] ";

    // OpenGL 常量
    private static final int GL_VENDOR = 0x1F00;
    private static final int GL_RENDERER = 0x1F01;
    private static final int GL_VERSION = 0x1F02;
    private static final int GL_EXTENSIONS = 0x1F03;
    private static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化 GPU 信息伪造 Hook");

        if (!FakeData.GPU_INFO_FAKE_ENABLED) {
            XposedBridge.log(TAG + "GPU 信息伪造已禁用，跳过初始化");
            return;
        }

        hookGLES10(lpparam);
        hookGLES20(lpparam);
        hookGLES30(lpparam);
        hookEGL14(lpparam);
        hookGLSurfaceView(lpparam);

        XposedBridge.log(TAG + "GPU 信息伪造 Hook 初始化完成");
    }

    // ==================== GLES10 Hook ====================
    private static void hookGLES10(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> gles10Class = XposedHelpers.findClass("android.opengl.GLES10", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(gles10Class, "glGetString", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int name = (int) param.args[0];
                    String orig = (String) param.getResult();
                    String fake = getFakeGLString(name);

                    if (fake != null) {
                        XposedBridge.log(TAG + "GLES10.glGetString(" + getGLConstantName(name) + ") 原始值: " + truncateString(orig));
                        XposedBridge.log(TAG + "GLES10.glGetString(" + getGLConstantName(name) + ") 伪造值: " + truncateString(fake));
                        param.setResult(fake);
                    }
                }
            });

            XposedBridge.log(TAG + "GLES10 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "GLES10 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== GLES20 Hook ====================
    private static void hookGLES20(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> gles20Class = XposedHelpers.findClass("android.opengl.GLES20", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(gles20Class, "glGetString", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int name = (int) param.args[0];
                    String orig = (String) param.getResult();
                    String fake = getFakeGLString(name);

                    if (fake != null) {
                        XposedBridge.log(TAG + "GLES20.glGetString(" + getGLConstantName(name) + ") 原始值: " + truncateString(orig));
                        XposedBridge.log(TAG + "GLES20.glGetString(" + getGLConstantName(name) + ") 伪造值: " + truncateString(fake));
                        param.setResult(fake);
                    }
                }
            });

            XposedBridge.log(TAG + "GLES20 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "GLES20 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== GLES30 Hook ====================
    private static void hookGLES30(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> gles30Class = XposedHelpers.findClass("android.opengl.GLES30", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(gles30Class, "glGetString", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int name = (int) param.args[0];
                    String orig = (String) param.getResult();
                    String fake = getFakeGLString(name);

                    if (fake != null) {
                        XposedBridge.log(TAG + "GLES30.glGetString(" + getGLConstantName(name) + ") 原始值: " + truncateString(orig));
                        XposedBridge.log(TAG + "GLES30.glGetString(" + getGLConstantName(name) + ") 伪造值: " + truncateString(fake));
                        param.setResult(fake);
                    }
                }
            });

            XposedBridge.log(TAG + "GLES30 Hook 成功");
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + "GLES30 类不存在，跳过");
        } catch (Exception e) {
            XposedBridge.log(TAG + "GLES30 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== EGL14 Hook ====================
    private static void hookEGL14(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> egl14Class = XposedHelpers.findClass("android.opengl.EGL14", lpparam.classLoader);

            // Hook eglQueryString(EGLDisplay dpy, int name)
            XposedHelpers.findAndHookMethod(egl14Class, "eglQueryString",
                    "android.opengl.EGLDisplay", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int name = (int) param.args[1];
                    String orig = (String) param.getResult();

                    // EGL_VENDOR = 0x3053, EGL_VERSION = 0x3054, EGL_EXTENSIONS = 0x3055
                    if (name == 0x3053) { // EGL_VENDOR
                        XposedBridge.log(TAG + "EGL14.eglQueryString(EGL_VENDOR) 原始值: " + orig);
                        param.setResult(FakeData.FAKE_GL_VENDOR);
                        XposedBridge.log(TAG + "EGL14.eglQueryString(EGL_VENDOR) 伪造值: " + FakeData.FAKE_GL_VENDOR);
                    }
                }
            });

            XposedBridge.log(TAG + "EGL14 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "EGL14 Hook 失败: " + e.getMessage());
        }
    }

    // ==================== GLSurfaceView.Renderer Hook ====================
    private static void hookGLSurfaceView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook ActivityManager.getDeviceConfigurationInfo() 获取 GPU 信息
            Class<?> activityManagerClass = XposedHelpers.findClass(
                    "android.app.ActivityManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(activityManagerClass, "getDeviceConfigurationInfo",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object configInfo = param.getResult();
                    if (configInfo != null) {
                        try {
                            // 修改 ConfigurationInfo.reqGlEsVersion
                            // 0x00030002 = OpenGL ES 3.2
                            XposedHelpers.setIntField(configInfo, "reqGlEsVersion", 0x00030002);
                            XposedBridge.log(TAG + "ConfigurationInfo.reqGlEsVersion 已伪造为 3.2");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + "ConfigurationInfo 修改失败: " + e.getMessage());
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "ActivityManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "ActivityManager Hook 失败: " + e.getMessage());
        }
    }

    /**
     * 根据 GL 常量返回伪造值
     */
    private static String getFakeGLString(int name) {
        switch (name) {
            case GL_VENDOR:
                return FakeData.FAKE_GL_VENDOR;
            case GL_RENDERER:
                return FakeData.FAKE_GL_RENDERER;
            case GL_VERSION:
                return FakeData.FAKE_GL_VERSION;
            case GL_EXTENSIONS:
                return FakeData.FAKE_GL_EXTENSIONS;
            case GL_SHADING_LANGUAGE_VERSION:
                return "OpenGL ES GLSL ES 3.20";
            default:
                return null;
        }
    }

    /**
     * 获取 GL 常量名称（用于日志）
     */
    private static String getGLConstantName(int name) {
        switch (name) {
            case GL_VENDOR:
                return "GL_VENDOR";
            case GL_RENDERER:
                return "GL_RENDERER";
            case GL_VERSION:
                return "GL_VERSION";
            case GL_EXTENSIONS:
                return "GL_EXTENSIONS";
            case GL_SHADING_LANGUAGE_VERSION:
                return "GL_SHADING_LANGUAGE_VERSION";
            default:
                return "0x" + Integer.toHexString(name);
        }
    }

    /**
     * 截断过长字符串（用于日志）
     */
    private static String truncateString(String s) {
        if (s == null) return "(null)";
        if (s.length() <= 100) return s;
        return s.substring(0, 100) + "... (长度: " + s.length() + ")";
    }
}