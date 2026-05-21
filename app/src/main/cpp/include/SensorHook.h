//
// SensorHook.h - Native 层传感器数据伪造
//
// 功能：Hook ASensorEventQueue_getEvents 对传感器数据添加噪声
// 防止应用通过 NDK 直接获取传感器数据绕过 Java 层 Hook
//

#ifndef DEVICEVEIL_SENSORHOOK_H
#define DEVICEVEIL_SENSORHOOK_H

#include "log.h"
#include "HookFunction.h"
#include <sys/types.h>

// ============================================================================
// ASensor 类型定义 (来自 android/sensor.h)
// ============================================================================

// 前向声明 (避免直接包含 android/sensor.h)
struct ASensorEventQueue;

// ASensorEvent 结构体定义
typedef struct ASensorVector {
    float x;
    float y;
    float z;
} ASensorVector;

typedef struct ASensorEvent {
    int32_t version;   // sizeof(ASensorEvent)
    int32_t sensor;    // 传感器标识符
    int32_t type;      // 传感器类型
    int32_t reserved0;
    int64_t timestamp; // 时间戳 (纳秒)
    union {
        float data[16];
        ASensorVector vector;
        ASensorVector acceleration;
        ASensorVector magnetic;
        ASensorVector gyro;
        float temperature;
        float distance;
        float light;
        float pressure;
    };
    uint32_t flags;
    int32_t reserved1[3];
} ASensorEvent;

// ============================================================================
// 传感器噪声配置（与 Java 层 FakeData 保持一致）
// ============================================================================

// 是否启用传感器数据伪造
extern bool g_sensorFakeEnabled;

// 加速度计噪声幅度 (m/s²)
extern float g_sensorNoiseAccelerometer;

// 陀螺仪噪声幅度 (rad/s)
extern float g_sensorNoiseGyroscope;

// 磁力计噪声幅度 (μT)
extern float g_sensorNoiseMagnetic;

// 光感传感器噪声幅度 (lux)
extern float g_sensorNoiseLight;

// 接近传感器噪声幅度 (cm)
extern float g_sensorNoiseProximity;

// ============================================================================
// 传感器 Hook 接口
// ============================================================================

class SensorHook {
public:
    /**
     * 初始化传感器 Hook
     * Hook ASensorEventQueue_getEvents 函数
     */
    static void init();

    /**
     * 设置传感器伪造开关
     */
    static void setEnabled(bool enabled);

    /**
     * 设置各传感器噪声级别
     */
    static void setNoiseLevel(int sensorType, float noiseLevel);
};

#endif //DEVICEVEIL_SENSORHOOK_H