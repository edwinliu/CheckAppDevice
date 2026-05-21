//
// KeymasterHook.h - Native 层 Key Attestation 拦截头文件
//

#ifndef DEVICEVEIL_KEYMASTERHOOK_H
#define DEVICEVEIL_KEYMASTERHOOK_H

#include "log.h"
#include "HookFunction.h"

// ============================================================================
// 全局配置变量声明
// ============================================================================

extern bool g_keymasterHookEnabled;

// ============================================================================
// KeymasterHook 类
// ============================================================================

class KeymasterHook {
public:
    // 初始化 Hook
    static void init();

    // 启用/禁用拦截
    static void setEnabled(bool enabled);

    // 设置伪造的设备序列号 (32 字符 hex)
    static void setFakeSerial(const char* serial);
};

#endif //DEVICEVEIL_KEYMASTERHOOK_H
