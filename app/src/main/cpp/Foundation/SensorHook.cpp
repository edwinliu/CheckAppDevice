//
// SensorHook.cpp - Native 层传感器数据伪造实现
//
// 功能：Hook ASensorEventQueue_getEvents 对传感器数据添加噪声
// 防止应用通过 NDK 直接获取传感器数据绕过 Java 层 Hook
//

#include "SensorHook.h"
#include <dlfcn.h>
#include <cstdlib>
#include <ctime>
#include <cmath>
#include <mutex>

// ============================================================================
// 全局配置变量定义
// ============================================================================

bool g_sensorFakeEnabled = true;
float g_sensorNoiseAccelerometer = 0.02f;  // m/s²
float g_sensorNoiseGyroscope = 0.005f;     // rad/s
float g_sensorNoiseMagnetic = 0.2f;        // μT
float g_sensorNoiseLight = 10.0f;          // lux
float g_sensorNoiseProximity = 0.2f;       // cm

// 随机数生成器（基于种子保持一致性）
static unsigned int g_sensorSeed = 0;
static std::mutex g_sensorMutex;
static bool g_sensorHookInitialized = false;

// ============================================================================
// 原始函数指针
// ============================================================================

typedef ssize_t (*ASensorEventQueue_getEvents_t)(
    ASensorEventQueue* queue, ASensorEvent* events, size_t count);
static ASensorEventQueue_getEvents_t orig_ASensorEventQueue_getEvents = nullptr;

// ============================================================================
// 辅助函数
// ============================================================================

// 生成高斯分布随机噪声 (Box-Muller 变换)
// stddev = maxNoise / 3，使 99.7% 的值落在 [-maxNoise, +maxNoise] 范围内
// 比均匀分布更接近真实传感器噪声特征
static float generateNoise(float maxNoise) {
    // Box-Muller 变换：将两个均匀分布随机数转换为高斯分布
    float u1, u2;
    do {
        u1 = (float)rand() / (float)RAND_MAX;
    } while (u1 <= 1e-7f); // 避免 log(0)
    u2 = (float)rand() / (float)RAND_MAX;

    // 标准正态分布 N(0,1)
    float z = sqrtf(-2.0f * logf(u1)) * cosf(2.0f * (float)M_PI * u2);

    // 缩放：stddev = maxNoise / 3，并 clamp 到 [-maxNoise, +maxNoise]
    float stddev = maxNoise / 3.0f;
    float noise = z * stddev;

    // 硬限幅，防止极端值
    if (noise > maxNoise) noise = maxNoise;
    if (noise < -maxNoise) noise = -maxNoise;

    return noise;
}

// 根据传感器类型获取噪声级别
static float getNoiseLevel(int sensorType) {
    switch (sensorType) {
        case 1:  // ASENSOR_TYPE_ACCELEROMETER
            return g_sensorNoiseAccelerometer;
        case 4:  // ASENSOR_TYPE_GYROSCOPE
            return g_sensorNoiseGyroscope;
        case 2:  // ASENSOR_TYPE_MAGNETIC_FIELD
            return g_sensorNoiseMagnetic;
        case 5:  // ASENSOR_TYPE_LIGHT
            return g_sensorNoiseLight;
        case 8:  // ASENSOR_TYPE_PROXIMITY
            return g_sensorNoiseProximity;
        case 9:  // ASENSOR_TYPE_GRAVITY
            return g_sensorNoiseAccelerometer * 0.5f;
        case 10: // ASENSOR_TYPE_LINEAR_ACCELERATION
            return g_sensorNoiseAccelerometer;
        case 11: // ASENSOR_TYPE_ROTATION_VECTOR
            return g_sensorNoiseGyroscope * 0.5f;
        default:
            return 0.01f;
    }
}

// 判断是否需要对该传感器添加噪声
static bool shouldFakeSensor(int sensorType) {
    return sensorType == 1 || sensorType == 2 || sensorType == 4 ||
           sensorType == 5 || sensorType == 8 || sensorType == 9 ||
           sensorType == 10 || sensorType == 11;
}

// 获取传感器数据维度
static int getSensorDataDimension(int sensorType) {
    switch (sensorType) {
        case 1:  // ACCELEROMETER (x, y, z)
        case 2:  // MAGNETIC_FIELD (x, y, z)
        case 4:  // GYROSCOPE (x, y, z)
        case 9:  // GRAVITY (x, y, z)
        case 10: // LINEAR_ACCELERATION (x, y, z)
            return 3;
        case 11: // ROTATION_VECTOR (x, y, z, cos, accuracy)
            return 5;
        case 5:  // LIGHT (lux)
        case 8:  // PROXIMITY (distance)
            return 1;
        default:
            return 3;
    }
}

// ============================================================================
// Hook 实现
// ============================================================================

// Hook 后的 ASensorEventQueue_getEvents 函数
static ssize_t new_ASensorEventQueue_getEvents(
    ASensorEventQueue* queue, ASensorEvent* events, size_t count) {

    // 调用原始函数
    ssize_t result = orig_ASensorEventQueue_getEvents(queue, events, count);

    // 如果未启用伪造或没有获取到事件，直接返回
    if (!g_sensorFakeEnabled || result <= 0 || events == nullptr) {
        return result;
    }

    // 遍历所有事件，添加噪声
    for (ssize_t i = 0; i < result; i++) {
        ASensorEvent* event = &events[i];
        int sensorType = event->type;

        // 检查是否需要伪造该传感器
        if (!shouldFakeSensor(sensorType)) {
            continue;
        }

        // 获取噪声级别和数据维度
        float noiseLevel = getNoiseLevel(sensorType);
        int dimension = getSensorDataDimension(sensorType);

        // 对传感器数据添加噪声
        for (int j = 0; j < dimension && j < 16; j++) {
            event->data[j] += generateNoise(noiseLevel);
        }
    }

    return result;
}

// ============================================================================
// 公共接口实现
// ============================================================================

void SensorHook::init() {
    std::lock_guard<std::mutex> lock(g_sensorMutex);

    if (g_sensorHookInitialized) {
        LOGE("[SensorHook] 已初始化，跳过");
        return;
    }

    LOGE("[SensorHook] ========================================");
    LOGE("[SensorHook] 初始化 Native 传感器 Hook");
    LOGE("[SensorHook] ========================================");

    // 初始化随机数种子
    g_sensorSeed = (unsigned int)time(nullptr);
    srand(g_sensorSeed);

    // 获取 libandroid.so 句柄
    void* handle = dlopen("libandroid.so", RTLD_NOW);
    if (handle == nullptr) {
        LOGE("[SensorHook] ❌ 无法打开 libandroid.so: %s", dlerror());
        return;
    }

    // 获取 ASensorEventQueue_getEvents 函数地址
    void* funcAddr = dlsym(handle, "ASensorEventQueue_getEvents");
    if (funcAddr == nullptr) {
        LOGE("[SensorHook] ❌ 无法找到 ASensorEventQueue_getEvents: %s", dlerror());
        dlclose(handle);
        return;
    }

    LOGE("[SensorHook] ASensorEventQueue_getEvents 地址: %p", funcAddr);

    // 使用 Dobby Hook
    bool hookResult = HookFunction::Hooker(
        funcAddr,
        (void*)new_ASensorEventQueue_getEvents,
        (void**)&orig_ASensorEventQueue_getEvents
    );

    if (hookResult && orig_ASensorEventQueue_getEvents != nullptr) {
        LOGE("[SensorHook] ✅ Hook ASensorEventQueue_getEvents 成功");
        g_sensorHookInitialized = true;
    } else {
        LOGE("[SensorHook] ❌ Hook ASensorEventQueue_getEvents 失败");
    }

    LOGE("[SensorHook] ========================================");
    LOGE("[SensorHook] 传感器噪声配置:");
    LOGE("[SensorHook]   加速度计: %.4f m/s²", g_sensorNoiseAccelerometer);
    LOGE("[SensorHook]   陀螺仪: %.4f rad/s", g_sensorNoiseGyroscope);
    LOGE("[SensorHook]   磁力计: %.4f μT", g_sensorNoiseMagnetic);
    LOGE("[SensorHook]   光感: %.2f lux", g_sensorNoiseLight);
    LOGE("[SensorHook]   接近: %.2f cm", g_sensorNoiseProximity);
    LOGE("[SensorHook] ========================================");
}

void SensorHook::setEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(g_sensorMutex);
    g_sensorFakeEnabled = enabled;
    LOGE("[SensorHook] 传感器伪造 %s", enabled ? "已启用" : "已禁用");
}

void SensorHook::setNoiseLevel(int sensorType, float noiseLevel) {
    std::lock_guard<std::mutex> lock(g_sensorMutex);
    switch (sensorType) {
        case 1:
            g_sensorNoiseAccelerometer = noiseLevel;
            break;
        case 4:
            g_sensorNoiseGyroscope = noiseLevel;
            break;
        case 2:
            g_sensorNoiseMagnetic = noiseLevel;
            break;
        case 5:
            g_sensorNoiseLight = noiseLevel;
            break;
        case 8:
            g_sensorNoiseProximity = noiseLevel;
            break;
    }
    LOGE("[SensorHook] 传感器类型 %d 噪声级别设置为 %.4f", sensorType, noiseLevel);
}