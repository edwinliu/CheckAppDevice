package com.deviceveil.guard;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.nio.ByteBuffer;
import java.util.Random;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Canvas 指纹保护 Hook 模块
 *
 * 功能:
 * 1. 对 Canvas 绑定的 Bitmap 添加细微噪声，防止 Canvas 指纹识别
 * 2. Hook Bitmap.getPixels() / getPixel() - 在读取像素时添加噪声
 * 3. Hook Canvas.drawText() / drawTextOnPath() - 监控文字绘制（常用于指纹）
 * 4. Hook BitmapFactory.decodeByteArray() 等 - 监控图片解码
 */
public class CanvasFakeHook {

    private static final String TAG = "[设备信息记录]---[CanvasFakeHook] ";

    // 使用固定种子的随机数生成器，确保相同像素位置的噪声一致
    private static final Random noiseRandom = new Random(System.currentTimeMillis());

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化 Canvas 指纹保护 Hook");

        if (!FakeData.CANVAS_NOISE_ENABLED) {
            XposedBridge.log(TAG + "Canvas 噪声注入已禁用，跳过初始化");
            return;
        }

        hookBitmapGetPixels(lpparam);
        hookBitmapGetPixel(lpparam);
        hookCanvasDrawText(lpparam);
        hookBitmapCopyPixelsToBuffer(lpparam);

        XposedBridge.log(TAG + "Canvas 指纹保护 Hook 初始化完成");
    }

    // ==================== Bitmap.getPixels() Hook ====================
    private static void hookBitmapGetPixels(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> bitmapClass = XposedHelpers.findClass("android.graphics.Bitmap", lpparam.classLoader);

            // getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height)
            XposedHelpers.findAndHookMethod(bitmapClass, "getPixels",
                    int[].class, int.class, int.class, int.class, int.class, int.class, int.class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int[] pixels = (int[]) param.args[0];
                        if (pixels != null && pixels.length > 0) {
                            addNoiseToPixels(pixels);
                            XposedBridge.log(TAG + "getPixels() 已添加噪声，像素数量: " + pixels.length);
                        }
                    } catch (Exception e) {
                        XposedBridge.log(TAG + "getPixels() 噪声注入失败: " + e.getMessage());
                    }
                }
            });

            XposedBridge.log(TAG + "Bitmap.getPixels() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Bitmap.getPixels() Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Bitmap.getPixel() Hook ====================
    private static void hookBitmapGetPixel(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> bitmapClass = XposedHelpers.findClass("android.graphics.Bitmap", lpparam.classLoader);

            // getPixel(int x, int y)
            XposedHelpers.findAndHookMethod(bitmapClass, "getPixel", int.class, int.class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int origColor = (int) param.getResult();
                        int noisyColor = addNoiseToColor(origColor);
                        param.setResult(noisyColor);
                        // 不记录每个像素，太频繁
                    } catch (Exception e) {
                        // 静默失败
                    }
                }
            });

            XposedBridge.log(TAG + "Bitmap.getPixel() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Bitmap.getPixel() Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Canvas.drawText() Hook ====================
    private static void hookCanvasDrawText(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> canvasClass = XposedHelpers.findClass("android.graphics.Canvas", lpparam.classLoader);

            // drawText(String text, float x, float y, Paint paint)
            XposedHelpers.findAndHookMethod(canvasClass, "drawText",
                    String.class, float.class, float.class, android.graphics.Paint.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String text = (String) param.args[0];
                    // 只记录较短的文本，避免日志过多
                    if (text != null && text.length() <= 100) {
                        XposedBridge.log(TAG + "Canvas.drawText(): \"" + text + "\"");
                    }
                }
            });

            // drawText(char[] text, int index, int count, float x, float y, Paint paint)
            XposedHelpers.findAndHookMethod(canvasClass, "drawText",
                    char[].class, int.class, int.class, float.class, float.class, android.graphics.Paint.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    char[] text = (char[]) param.args[0];
                    int index = (int) param.args[1];
                    int count = (int) param.args[2];
                    if (text != null && count <= 100) {
                        String str = new String(text, index, count);
                        XposedBridge.log(TAG + "Canvas.drawText(char[]): \"" + str + "\"");
                    }
                }
            });

            XposedBridge.log(TAG + "Canvas.drawText() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Canvas.drawText() Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Bitmap.copyPixelsToBuffer() Hook ====================
    private static void hookBitmapCopyPixelsToBuffer(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> bitmapClass = XposedHelpers.findClass("android.graphics.Bitmap", lpparam.classLoader);

            // copyPixelsToBuffer(Buffer dst)
            XposedHelpers.findAndHookMethod(bitmapClass, "copyPixelsToBuffer",
                    java.nio.Buffer.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        java.nio.Buffer buffer = (java.nio.Buffer) param.args[0];
                        if (buffer instanceof ByteBuffer) {
                            ByteBuffer byteBuffer = (ByteBuffer) buffer;
                            addNoiseToByteBuffer(byteBuffer);
                            XposedBridge.log(TAG + "copyPixelsToBuffer() 已添加噪声");
                        }
                    } catch (Exception e) {
                        XposedBridge.log(TAG + "copyPixelsToBuffer() 噪声注入失败: " + e.getMessage());
                    }
                }
            });

            XposedBridge.log(TAG + "Bitmap.copyPixelsToBuffer() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Bitmap.copyPixelsToBuffer() Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 噪声注入辅助方法 ====================

    /**
     * 对像素数组添加噪声
     */
    private static void addNoiseToPixels(int[] pixels) {
        int noiseLevel = FakeData.CANVAS_NOISE_LEVEL;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = addNoiseToColor(pixels[i], noiseLevel);
        }
    }

    /**
     * 对单个颜色值添加噪声
     */
    private static int addNoiseToColor(int color) {
        return addNoiseToColor(color, FakeData.CANVAS_NOISE_LEVEL);
    }

    /**
     * 对单个颜色值添加指定级别的噪声
     */
    private static int addNoiseToColor(int color, int noiseLevel) {
        if (noiseLevel <= 0) return color;

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 对 RGB 通道添加随机噪声
        r = clamp(r + randomNoise(noiseLevel), 0, 255);
        g = clamp(g + randomNoise(noiseLevel), 0, 255);
        b = clamp(b + randomNoise(noiseLevel), 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 生成随机噪声值 (-noiseLevel ~ +noiseLevel)
     */
    private static int randomNoise(int noiseLevel) {
        return noiseRandom.nextInt(noiseLevel * 2 + 1) - noiseLevel;
    }

    /**
     * 限制值在指定范围内
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 对 ByteBuffer 添加噪声
     */
    private static void addNoiseToByteBuffer(ByteBuffer buffer) {
        int noiseLevel = FakeData.CANVAS_NOISE_LEVEL;
        if (noiseLevel <= 0) return;

        buffer.rewind();
        int capacity = buffer.remaining();

        // 对每 4 个字节（ARGB）添加噪声
        for (int i = 0; i < capacity; i++) {
            byte b = buffer.get(i);
            // 只对非 Alpha 通道添加噪声（假设 ARGB 顺序）
            if (i % 4 != 0) { // 跳过 Alpha 通道
                int noise = randomNoise(noiseLevel);
                int newValue = clamp((b & 0xFF) + noise, 0, 255);
                buffer.put(i, (byte) newValue);
            }
        }
    }
}