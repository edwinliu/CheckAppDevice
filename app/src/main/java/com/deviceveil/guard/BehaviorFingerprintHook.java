package com.deviceveil.guard;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ============================================================================
 * BehaviorFingerprintHook.java - 行为指纹防护模块
 *
 * 功能：防止应用通过用户行为模式进行指纹识别
 *
 * 防护范围：
 * 1. 传感器数据伪造 - 加速度计、陀螺仪、磁力计等添加噪声
 * 2. 触摸事件伪造 - 触摸压力、大小、时间戳添加随机偏移
 * 3. 输入行为伪造 - 按键时间戳添加随机偏移
 *
 * 特点：
 * - 基于设备种子的伪随机，保持一致性
 * - 轻度噪声，不影响正常使用
 * - 模块化设计，可单独启用/禁用各项功能
 * ============================================================================
 */
public class BehaviorFingerprintHook {

    private static final String TAG = "[设备信息记录]---[BehaviorFingerprintHook] ";

    // 基于设备种子的随机数生成器（保持一致性）
    private static Random sSeededRandom = null;
    // 完全随机的生成器（用于时间戳等需要变化的值）
    private static final Random sRandom = new Random();

    // 触摸坐标噪声缓存（确保同一事件多次调用返回一致的值）
    private static final Map<Integer, float[]> sCoordinateNoiseCache = new WeakHashMap<>();

    // 光感传感器模拟状态
    private static float sSimulatedLightLevel = 300f; // 初始环境光 (lux)
    private static long sLastLightUpdateTime = 0;

    /**
     * 初始化所有行为指纹防护 Hook
     */
    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "行为指纹防护模块初始化");
        XposedBridge.log(TAG + "========================================");

        // 初始化基于设备种子的随机数生成器
        initSeededRandom();

        // 1. 传感器数据伪造
        if (FakeData.SENSOR_DATA_FAKE_ENABLED) {
            hookSensorEventListener(lpparam);
            XposedBridge.log(TAG + "传感器数据伪造: 已启用");
        }

        // 2. 触摸事件伪造
        if (FakeData.TOUCH_EVENT_FAKE_ENABLED) {
            hookMotionEvent(lpparam);
            XposedBridge.log(TAG + "触摸事件伪造: 已启用");
        }

        // 3. 输入行为伪造
        if (FakeData.INPUT_EVENT_FAKE_ENABLED) {
            hookInputConnection(lpparam);
            XposedBridge.log(TAG + "输入行为伪造: 已启用");
        }

        // 4. WebView JS 防护
        initWebViewHooks(lpparam);

        XposedBridge.log(TAG + "========================================");
        XposedBridge.log(TAG + "行为指纹防护模块初始化完成");
        XposedBridge.log(TAG + "========================================");
    }

    /**
     * 初始化基于设备种子的随机数生成器
     */
    private static void initSeededRandom() {
        String seed = HookInit.Android_id != null ? HookInit.Android_id : FakeData.FAKE_ANDROID_ID;
        sSeededRandom = new Random(seed.hashCode());
        XposedBridge.log(TAG + "随机种子已初始化 (基于 Android ID)");
    }

    /**
     * 获取基于种子的稳定随机偏移
     */
    private static float getSeededNoise(float maxNoise) {
        return (sSeededRandom.nextFloat() - 0.5f) * 2 * maxNoise;
    }

    /**
     * 获取实时随机偏移（用于时间戳等）
     */
    private static float getRandomNoise(float maxNoise) {
        return (sRandom.nextFloat() - 0.5f) * 2 * maxNoise;
    }

    // ========================================================================
    // 1. 传感器数据伪造
    // ========================================================================

    private static void hookSensorEventListener(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook SensorManager.registerListener 来拦截 SensorEventListener
        try {
            // 方法签名: registerListener(SensorEventListener, Sensor, int)
            XposedHelpers.findAndHookMethod(
                    "android.hardware.SensorManager",
                    lpparam.classLoader,
                    "registerListener",
                    "android.hardware.SensorEventListener",
                    Sensor.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object listener = param.args[0];
                            Sensor sensor = (Sensor) param.args[1];
                            if (listener != null && sensor != null) {
                                wrapSensorEventListener(param, listener, sensor);
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook SensorManager.registerListener(3参数) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook SensorManager.registerListener(3参数) 失败: " + t.getMessage());
        }

        // 方法签名: registerListener(SensorEventListener, Sensor, int, Handler)
        try {
            XposedHelpers.findAndHookMethod(
                    "android.hardware.SensorManager",
                    lpparam.classLoader,
                    "registerListener",
                    "android.hardware.SensorEventListener",
                    Sensor.class,
                    int.class,
                    "android.os.Handler",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object listener = param.args[0];
                            Sensor sensor = (Sensor) param.args[1];
                            if (listener != null && sensor != null) {
                                wrapSensorEventListener(param, listener, sensor);
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook SensorManager.registerListener(4参数) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook SensorManager.registerListener(4参数) 失败: " + t.getMessage());
        }
    }

    /**
     * 包装 SensorEventListener，在数据传递前添加噪声
     */
    private static void wrapSensorEventListener(XC_MethodHook.MethodHookParam param,
                                                 Object originalListener, Sensor sensor) {
        int sensorType = sensor.getType();

        // 只对特定传感器添加噪声
        if (!shouldFakeSensor(sensorType)) {
            return;
        }

        try {
            // 创建代理 Listener
            Object proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                    originalListener.getClass().getClassLoader(),
                    new Class<?>[]{android.hardware.SensorEventListener.class},
                    (proxy, method, args) -> {
                        if ("onSensorChanged".equals(method.getName()) && args != null && args.length > 0) {
                            SensorEvent event = (SensorEvent) args[0];
                            if (event != null) {
                                addNoiseToSensorEvent(event);
                            }
                        }
                        return method.invoke(originalListener, args);
                    });

            param.args[0] = proxyListener;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "创建传感器代理失败: " + t.getMessage());
        }
    }

    /**
     * 判断是否需要对该传感器添加噪声
     */
    private static boolean shouldFakeSensor(int sensorType) {
        // Sensor.TYPE_ACCELEROMETER = 1
        // Sensor.TYPE_MAGNETIC_FIELD = 2
        // Sensor.TYPE_GYROSCOPE = 4
        // Sensor.TYPE_LIGHT = 5
        // Sensor.TYPE_PROXIMITY = 8
        // Sensor.TYPE_GRAVITY = 9
        // Sensor.TYPE_LINEAR_ACCELERATION = 10
        // Sensor.TYPE_ROTATION_VECTOR = 11
        return sensorType == 1 || sensorType == 2 || sensorType == 4 ||
               sensorType == 5 || sensorType == 8 || sensorType == 9 ||
               sensorType == 10 || sensorType == 11;
    }

    /**
     * 向传感器事件数据添加噪声
     */
    private static void addNoiseToSensorEvent(SensorEvent event) {
        if (event.values == null || event.values.length == 0) {
            return;
        }

        try {
            // 通过反射修改 values 数组（SensorEvent.values 是 final 的）
            Field valuesField = SensorEvent.class.getField("values");
            float[] values = (float[]) valuesField.get(event);

            if (values == null) return;

            int sensorType = event.sensor.getType();
            float noiseLevel = getSensorNoiseLevel(sensorType);

            for (int i = 0; i < values.length; i++) {
                // 添加轻微噪声，基于种子保持一致性模式，但每次有微小变化
                float baseNoise = getSeededNoise(noiseLevel);
                float dynamicNoise = getRandomNoise(noiseLevel * 0.1f);
                values[i] += baseNoise + dynamicNoise;
            }
        } catch (Throwable t) {
            // 静默失败，不影响正常功能
        }
    }

    /**
     * 获取不同传感器的噪声级别
     */
    private static float getSensorNoiseLevel(int sensorType) {
        switch (sensorType) {
            case 1:  // TYPE_ACCELEROMETER
                return FakeData.SENSOR_NOISE_ACCELEROMETER;
            case 4:  // TYPE_GYROSCOPE
                return FakeData.SENSOR_NOISE_GYROSCOPE;
            case 2:  // TYPE_MAGNETIC_FIELD
                return FakeData.SENSOR_NOISE_MAGNETIC;
            case 5:  // TYPE_LIGHT
                return FakeData.SENSOR_NOISE_LIGHT;
            case 8:  // TYPE_PROXIMITY
                return FakeData.SENSOR_NOISE_PROXIMITY;
            case 10: // TYPE_LINEAR_ACCELERATION
                return FakeData.SENSOR_NOISE_ACCELEROMETER;
            case 9:  // TYPE_GRAVITY
                return FakeData.SENSOR_NOISE_ACCELEROMETER * 0.5f;
            case 11: // TYPE_ROTATION_VECTOR
                return FakeData.SENSOR_NOISE_GYROSCOPE * 0.5f;
            default:
                return 0.01f;
        }
    }

    // ========================================================================
    // 2. 触摸事件伪造
    // ========================================================================

    private static void hookMotionEvent(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook MotionEvent.getPressure()
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getPressure",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            float original = (float) param.getResult();
                            float noise = getRandomNoise(FakeData.TOUCH_PRESSURE_NOISE);
                            float faked = Math.max(0.0f, Math.min(1.0f, original + noise));
                            param.setResult(faked);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getPressure(int) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getPressure(int) 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getPressure() 无参版本
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getPressure",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            float original = (float) param.getResult();
                            float noise = getRandomNoise(FakeData.TOUCH_PRESSURE_NOISE);
                            float faked = Math.max(0.0f, Math.min(1.0f, original + noise));
                            param.setResult(faked);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getPressure() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getPressure() 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getSize()
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getSize",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            float original = (float) param.getResult();
                            float noise = getRandomNoise(FakeData.TOUCH_SIZE_NOISE);
                            float faked = Math.max(0.0f, original + noise);
                            param.setResult(faked);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getSize(int) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getSize(int) 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getSize() 无参版本
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getSize",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            float original = (float) param.getResult();
                            float noise = getRandomNoise(FakeData.TOUCH_SIZE_NOISE);
                            float faked = Math.max(0.0f, original + noise);
                            param.setResult(faked);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getSize() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getSize() 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getToolMajor() - 触摸工具主轴大小
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getToolMajor",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            float original = (float) param.getResult();
                            float noise = getRandomNoise(FakeData.TOUCH_SIZE_NOISE * 2);
                            float faked = Math.max(0.0f, original + noise);
                            param.setResult(faked);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getToolMajor(int) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getToolMajor(int) 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getToolMinor() - 触摸工具次轴大小
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getToolMinor",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            float original = (float) param.getResult();
                            float noise = getRandomNoise(FakeData.TOUCH_SIZE_NOISE * 2);
                            float faked = Math.max(0.0f, original + noise);
                            param.setResult(faked);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getToolMinor(int) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getToolMinor(int) 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getEventTime() - 事件时间戳
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.MotionEvent",
                    lpparam.classLoader,
                    "getEventTime",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            long original = (long) param.getResult();
                            int noise = (int) getRandomNoise(FakeData.TOUCH_TIME_NOISE_MS);
                            param.setResult(original + noise);
                        }
                    });
            XposedBridge.log(TAG + "Hook MotionEvent.getEventTime() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook MotionEvent.getEventTime() 失败: " + t.getMessage());
        }

        // Hook MotionEvent.getX(int) - 触摸坐标 X 添加亚像素抖动
        hookCoordinateMethod(lpparam, "getX", int.class);
        // Hook MotionEvent.getX() - 无参版本
        hookCoordinateMethod(lpparam, "getX", null);
        // Hook MotionEvent.getY(int) - 触摸坐标 Y 添加亚像素抖动
        hookCoordinateMethod(lpparam, "getY", int.class);
        // Hook MotionEvent.getY() - 无参版本
        hookCoordinateMethod(lpparam, "getY", null);
        // Hook MotionEvent.getRawX() - 原始坐标 X
        hookCoordinateMethod(lpparam, "getRawX", null);
        // Hook MotionEvent.getRawY() - 原始坐标 Y
        hookCoordinateMethod(lpparam, "getRawY", null);
    }

    /**
     * Hook 坐标方法 (getX/getY/getRawX/getRawY) 添加亚像素抖动
     */
    private static void hookCoordinateMethod(XC_LoadPackage.LoadPackageParam lpparam,
                                              String methodName, Class<?> paramType) {
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    float original = (float) param.getResult();
                    MotionEvent event = (MotionEvent) param.thisObject;

                    // 使用事件的 hashCode 作为缓存 key
                    int eventKey = System.identityHashCode(event);
                    float[] noiseCache;

                    synchronized (sCoordinateNoiseCache) {
                        noiseCache = sCoordinateNoiseCache.get(eventKey);
                        if (noiseCache == null) {
                            // 生成新的噪声值 [noiseX, noiseY]
                            noiseCache = new float[2];
                            noiseCache[0] = getRandomNoise(FakeData.TOUCH_COORDINATE_NOISE);
                            noiseCache[1] = getRandomNoise(FakeData.TOUCH_COORDINATE_NOISE);
                            sCoordinateNoiseCache.put(eventKey, noiseCache);
                        }
                    }

                    // 根据方法名选择 X 或 Y 的噪声
                    boolean isX = methodName.contains("X");
                    float noise = isX ? noiseCache[0] : noiseCache[1];
                    param.setResult(original + noise);
                }
            };

            if (paramType != null) {
                XposedHelpers.findAndHookMethod("android.view.MotionEvent",
                        lpparam.classLoader, methodName, paramType, hook);
            } else {
                XposedHelpers.findAndHookMethod("android.view.MotionEvent",
                        lpparam.classLoader, methodName, hook);
            }
            String suffix = paramType != null ? "(" + paramType.getSimpleName() + ")" : "()";
            XposedBridge.log(TAG + "Hook MotionEvent." + methodName + suffix + " 成功");
        } catch (Throwable t) {
            String suffix = paramType != null ? "(" + paramType.getSimpleName() + ")" : "()";
            XposedBridge.log(TAG + "Hook MotionEvent." + methodName + suffix + " 失败: " + t.getMessage());
        }
    }

    // ========================================================================
    // 3. 输入行为伪造
    // ========================================================================

    private static void hookInputConnection(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook BaseInputConnection.commitText - 文本输入时间戳
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.inputmethod.BaseInputConnection",
                    lpparam.classLoader,
                    "commitText",
                    CharSequence.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 添加微小延迟来模拟自然输入节奏变化
                            int delay = (int) Math.abs(getRandomNoise(FakeData.INPUT_TIME_NOISE_MS));
                            if (delay > 0 && delay < 10) {
                                try {
                                    Thread.sleep(delay);
                                } catch (InterruptedException ignored) {}
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook BaseInputConnection.commitText() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook BaseInputConnection.commitText() 失败: " + t.getMessage());
        }

        // Hook InputConnection.sendKeyEvent - 按键事件时间戳
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.inputmethod.BaseInputConnection",
                    lpparam.classLoader,
                    "sendKeyEvent",
                    "android.view.KeyEvent",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object keyEvent = param.args[0];
                            if (keyEvent != null) {
                                try {
                                    // 获取原始时间戳并添加噪声
                                    long eventTime = (long) XposedHelpers.callMethod(
                                            keyEvent, "getEventTime");
                                    long downTime = (long) XposedHelpers.callMethod(
                                            keyEvent, "getDownTime");
                                    int noise = (int) getRandomNoise(FakeData.INPUT_TIME_NOISE_MS);

                                    // 创建新的 KeyEvent 带修改后的时间戳
                                    int action = (int) XposedHelpers.callMethod(keyEvent, "getAction");
                                    int code = (int) XposedHelpers.callMethod(keyEvent, "getKeyCode");
                                    int repeat = (int) XposedHelpers.callMethod(keyEvent, "getRepeatCount");
                                    int metaState = (int) XposedHelpers.callMethod(keyEvent, "getMetaState");
                                    int deviceId = (int) XposedHelpers.callMethod(keyEvent, "getDeviceId");
                                    int scanCode = (int) XposedHelpers.callMethod(keyEvent, "getScanCode");
                                    int flags = (int) XposedHelpers.callMethod(keyEvent, "getFlags");
                                    int source = (int) XposedHelpers.callMethod(keyEvent, "getSource");

                                    Object newKeyEvent = XposedHelpers.newInstance(
                                            XposedHelpers.findClass("android.view.KeyEvent", lpparam.classLoader),
                                            downTime + noise, eventTime + noise, action, code,
                                            repeat, metaState, deviceId, scanCode, flags, source);

                                    param.args[0] = newKeyEvent;
                                } catch (Throwable ignored) {}
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook BaseInputConnection.sendKeyEvent() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook BaseInputConnection.sendKeyEvent() 失败: " + t.getMessage());
        }
    }

    // ========================================================================
    // 4. WebView JS 层防护
    // ========================================================================

    /**
     * 初始化 WebView JS 防护 Hook
     * 防止通过 navigator.webdriver 等 JS API 检测自动化工具
     */
    public static void initWebViewHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!FakeData.WEBVIEW_JS_PROTECTION_ENABLED) {
            return;
        }

        XposedBridge.log(TAG + "WebView JS 防护: 初始化中...");

        // Hook WebView.loadUrl() 注入防护脚本
        hookWebViewLoadUrl(lpparam);
        // Hook WebViewClient.onPageFinished() 注入防护脚本
        hookWebViewClientOnPageFinished(lpparam);

        XposedBridge.log(TAG + "WebView JS 防护: 已启用");
    }

    private static void hookWebViewLoadUrl(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebView",
                    lpparam.classLoader,
                    "loadUrl",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            injectAntiDetectionScript(param.thisObject, lpparam);
                        }
                    });
            XposedBridge.log(TAG + "Hook WebView.loadUrl(String) 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook WebView.loadUrl(String) 失败: " + t.getMessage());
        }
    }

    private static void hookWebViewClientOnPageFinished(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.webkit.WebViewClient",
                    lpparam.classLoader,
                    "onPageFinished",
                    "android.webkit.WebView",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object webView = param.args[0];
                            if (webView != null) {
                                injectAntiDetectionScript(webView, lpparam);
                            }
                        }
                    });
            XposedBridge.log(TAG + "Hook WebViewClient.onPageFinished() 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook WebViewClient.onPageFinished() 失败: " + t.getMessage());
        }
    }

    /**
     * 注入反检测 JS 脚本
     */
    private static void injectAntiDetectionScript(Object webView, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String script = getAntiDetectionScript();
            XposedHelpers.callMethod(webView, "evaluateJavascript", script, null);
        } catch (Throwable t) {
            // 静默失败
        }
    }

    /**
     * 获取反检测 JS 脚本
     */
    private static String getAntiDetectionScript() {
        return "(function() {" +
                // 隐藏 webdriver 属性
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                // 隐藏自动化相关属性
                "delete navigator.__proto__.webdriver;" +
                // 伪造 plugins 数组（非空）
                "Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});" +
                // 伪造 languages
                "Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN','zh','en']});" +
                // 隐藏 Selenium/Puppeteer 痕迹
                "window.chrome = {runtime: {}};" +
                "window.navigator.chrome = {runtime: {}};" +
                // 隐藏 callPhantom/phantom
                "delete window.callPhantom;" +
                "delete window._phantom;" +
                "delete window.phantom;" +
                "})();";
    }

    // ========================================================================
    // 5. 高斯分布随机数生成
    // ========================================================================

    /**
     * 获取高斯分布的随机噪声
     * 比均匀分布更接近自然人类行为
     * @param mean 均值
     * @param stdDev 标准差
     */
    private static float getGaussianNoise(float mean, float stdDev) {
        return (float) (mean + sRandom.nextGaussian() * stdDev);
    }

    /**
     * 获取高斯分布的输入延迟（毫秒）
     * 模拟自然的人类输入节奏
     */
    private static int getGaussianInputDelay() {
        // 均值 50ms，标准差 20ms，模拟自然打字节奏
        float delay = getGaussianNoise(50f, 20f);
        // 限制在合理范围内 [10, 200] ms
        return Math.max(10, Math.min(200, (int) delay));
    }
}