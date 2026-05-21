//
// KeymasterHook.cpp - Native 层 Key Attestation 拦截与修改
//
// 功能：
// 1. Hook Binder IPC 拦截 Keystore 调用
// 2. 解析并修改证书链中的设备序列号
// 3. 在 Parcel 数据中替换证书内容
//

#include "../include/KeymasterHook.h"
#include <dlfcn.h>
#include <cstring>
#include <mutex>
#include <cstdint>
#include <vector>
#include <string>

// ============================================================================
// NDK Binder 类型定义
// ============================================================================

struct AIBinder;
struct AParcel;

typedef int32_t binder_status_t;
typedef uint32_t transaction_code_t;
typedef uint32_t binder_flags_t;

// Parcel 操作函数指针类型
typedef const char* (*AIBinder_getInterfaceDescriptor_t)(AIBinder* binder);
typedef int32_t (*AParcel_getDataSize_t)(const AParcel* parcel);
typedef const uint8_t* (*AParcel_getData_t)(const AParcel* parcel);
typedef binder_status_t (*AParcel_setDataPosition_t)(AParcel* parcel, int32_t position);
typedef binder_status_t (*AParcel_writeByteArray_t)(AParcel* parcel, const int8_t* data, int32_t len);

static AIBinder_getInterfaceDescriptor_t fn_getInterfaceDescriptor = nullptr;
static AParcel_getDataSize_t fn_getDataSize = nullptr;

// ============================================================================
// 全局配置
// ============================================================================

bool g_keymasterHookEnabled = true;
static bool g_keymasterHookInitialized = false;
static std::mutex g_keymasterMutex;

// 伪造的设备序列号 (32 字节 hex)
static char g_fakeKeymasterSerial[65] = {0};

// 外部引用 (从 corner_monit.cpp)
extern char g_fakeAndroidId[32];

// ============================================================================
// 证书序列号查找和替换
// ============================================================================

// ASN.1 DER 中 SERIALNUMBER OID: 2.5.4.5 = 55 04 05
static const uint8_t SERIAL_NUMBER_OID[] = {0x55, 0x04, 0x05};

/**
 * 在二进制数据中查找并替换证书序列号
 *
 * X.509 证书中 SERIALNUMBER 的 DER 编码格式:
 * 06 03 55 04 05  (OID: 2.5.4.5)
 * 13 XX [data]    (PrintableString) 或
 * 0C XX [data]    (UTF8String)
 */
static bool findAndReplaceSerialNumber(uint8_t* data, size_t dataLen,
                                        const char* fakeSerial, size_t fakeSerialLen) {
    if (data == nullptr || dataLen < 10 || fakeSerial == nullptr) {
        return false;
    }

    bool modified = false;

    // 搜索 SERIALNUMBER OID
    for (size_t i = 0; i < dataLen - 10; i++) {
        // 查找 OID 标记 (06 03) + SERIALNUMBER OID (55 04 05)
        if (data[i] == 0x06 && data[i+1] == 0x03 &&
            data[i+2] == 0x55 && data[i+3] == 0x04 && data[i+4] == 0x05) {

            // OID 后面是字符串类型 (13=PrintableString, 0C=UTF8String)
            size_t strOffset = i + 5;
            if (strOffset + 2 >= dataLen) continue;

            uint8_t strType = data[strOffset];
            if (strType != 0x13 && strType != 0x0C) continue;

            uint8_t strLen = data[strOffset + 1];
            size_t strDataOffset = strOffset + 2;

            // 检查边界
            if (strDataOffset + strLen > dataLen) continue;

            // 记录原始值
            char origSerial[128] = {0};
            size_t copyLen = (strLen < 127) ? strLen : 127;
            memcpy(origSerial, &data[strDataOffset], copyLen);

            LOGE("[KeymasterHook] 🔍 找到 SERIALNUMBER: %s (长度=%d)", origSerial, strLen);

            // 如果长度匹配，直接替换
            if (strLen == fakeSerialLen) {
                memcpy(&data[strDataOffset], fakeSerial, fakeSerialLen);
                LOGE("[KeymasterHook] ✅ 替换为: %s", fakeSerial);
                modified = true;
            } else {
                LOGE("[KeymasterHook] ⚠️ 长度不匹配 (原=%d, 新=%zu), 跳过", strLen, fakeSerialLen);
            }
        }
    }

    return modified;
}

// ============================================================================
// Binder Hook
// ============================================================================

typedef binder_status_t (*AIBinder_transact_t)(
    AIBinder* binder,
    transaction_code_t code,
    AParcel** in,
    AParcel** out,
    binder_flags_t flags
);
static AIBinder_transact_t orig_AIBinder_transact = nullptr;

// Keystore 相关事务码
#define SECURITY_LEVEL_GENERATE_KEY 2

static binder_status_t new_AIBinder_transact(
    AIBinder* binder,
    transaction_code_t code,
    AParcel** in,
    AParcel** out,
    binder_flags_t flags
) {
    // 先调用原始函数
    binder_status_t result = orig_AIBinder_transact(binder, code, in, out, flags);

    if (!g_keymasterHookEnabled || binder == nullptr || result != 0) {
        return result;
    }

    // 获取接口描述符
    const char* descriptor = nullptr;
    if (fn_getInterfaceDescriptor != nullptr) {
        descriptor = fn_getInterfaceDescriptor(binder);
    }

    if (descriptor == nullptr) {
        return result;
    }

    // 检查是否为 Keystore 相关接口
    bool isKeystoreCall = (strstr(descriptor, "IKeystoreSecurityLevel") != nullptr ||
                           strstr(descriptor, "IKeystoreService") != nullptr);

    if (!isKeystoreCall) {
        return result;
    }

    LOGE("[KeymasterHook] 📡 Keystore 调用: %s, code=%d", descriptor, code);

    // 尝试修改返回数据中的证书序列号
    // 注意: AParcel 的内部数据访问需要特殊处理
    // 这里我们通过 Hook 更底层的函数来实现

    return result;
}

// ============================================================================
// libbinder.so Hook (传统 Binder)
// ============================================================================

// IPCThreadState::transact 函数签名
typedef int32_t (*IPCThreadState_transact_t)(
    void* thisPtr,
    int32_t handle,
    uint32_t code,
    void* data,      // Parcel&
    void* reply,     // Parcel*
    uint32_t flags
);
static IPCThreadState_transact_t orig_IPCThreadState_transact = nullptr;

// Parcel 类的内部结构 (简化版)
struct ParcelData {
    uint8_t* mData;
    size_t mDataSize;
    size_t mDataCapacity;
    size_t mDataPos;
    // ... 其他字段
};

static int32_t new_IPCThreadState_transact(
    void* thisPtr,
    int32_t handle,
    uint32_t code,
    void* data,
    void* reply,
    uint32_t flags
) {
    // 先调用原始函数
    int32_t result = orig_IPCThreadState_transact(thisPtr, handle, code, data, reply, flags);

    if (!g_keymasterHookEnabled || result != 0 || reply == nullptr) {
        return result;
    }

    // 尝试访问 Parcel 数据
    // 注意: 这依赖于 Parcel 的内部布局，可能因 Android 版本而异
    try {
        ParcelData* parcel = (ParcelData*)reply;
        if (parcel->mData != nullptr && parcel->mDataSize > 100) {
            // 检查是否包含证书数据 (查找 X.509 证书标记)
            // X.509 证书以 SEQUENCE (0x30) 开头
            bool hasCertData = false;
            for (size_t i = 0; i < parcel->mDataSize - 5; i++) {
                if (parcel->mData[i] == 0x30 &&
                    parcel->mData[i+2] == 0x30) {
                    hasCertData = true;
                    break;
                }
            }

            if (hasCertData && strlen(g_fakeKeymasterSerial) == 32) {
                LOGE("[KeymasterHook] 🔐 检测到可能的证书数据，尝试修改...");

                bool modified = findAndReplaceSerialNumber(
                    parcel->mData,
                    parcel->mDataSize,
                    g_fakeKeymasterSerial,
                    32
                );

                if (modified) {
                    LOGE("[KeymasterHook] ✅ SO 层证书序列号修改成功");
                }
            }
        }
    } catch (...) {
        LOGE("[KeymasterHook] ❌ 访问 Parcel 数据异常");
    }

    return result;
}

// ============================================================================
// 公共接口
// ============================================================================

void KeymasterHook::init() {
    std::lock_guard<std::mutex> lock(g_keymasterMutex);

    if (g_keymasterHookInitialized) {
        LOGE("[KeymasterHook] 已初始化，跳过");
        return;
    }

    LOGE("[KeymasterHook] ========================================");
    LOGE("[KeymasterHook] 初始化 Key Attestation SO 层拦截");
    LOGE("[KeymasterHook] ========================================");

    // 初始化伪造序列号 (基于 Android ID)
    if (strlen(g_fakeAndroidId) >= 16) {
        snprintf(g_fakeKeymasterSerial, sizeof(g_fakeKeymasterSerial),
                 "%s%s", g_fakeAndroidId, g_fakeAndroidId);
    } else {
        strcpy(g_fakeKeymasterSerial, "a1b2c3d4e5f67890a1b2c3d4e5f67890");
    }
    LOGE("[KeymasterHook] 伪造序列号: %s", g_fakeKeymasterSerial);

    // Hook libbinder_ndk.so (NDK Binder)
    void* binderNdkHandle = dlopen("libbinder_ndk.so", RTLD_NOW);
    if (binderNdkHandle != nullptr) {
        fn_getInterfaceDescriptor = (AIBinder_getInterfaceDescriptor_t)
            dlsym(binderNdkHandle, "AIBinder_getInterfaceDescriptor");

        void* transactAddr = dlsym(binderNdkHandle, "AIBinder_transact");
        if (transactAddr != nullptr) {
            if (HookFunction::Hooker(transactAddr, (void*)new_AIBinder_transact,
                                     (void**)&orig_AIBinder_transact)) {
                LOGE("[KeymasterHook] ✅ Hook AIBinder_transact 成功");
            }
        }
    }

    // Hook libbinder.so (传统 Binder) - 更底层
    void* binderHandle = dlopen("libbinder.so", RTLD_NOW);
    if (binderHandle != nullptr) {
        // IPCThreadState::transact 是 C++ 方法，需要 mangled name
        // _ZN7android14IPCThreadState8transactEijRKNS_6ParcelEPS1_j
        void* transactAddr = dlsym(binderHandle,
            "_ZN7android14IPCThreadState8transactEijRKNS_6ParcelEPS1_j");
        if (transactAddr != nullptr) {
            if (HookFunction::Hooker(transactAddr, (void*)new_IPCThreadState_transact,
                                     (void**)&orig_IPCThreadState_transact)) {
                LOGE("[KeymasterHook] ✅ Hook IPCThreadState::transact 成功");
            }
        } else {
            LOGE("[KeymasterHook] ⚠️ 找不到 IPCThreadState::transact");
        }
    }

    g_keymasterHookInitialized = true;
    LOGE("[KeymasterHook] ========================================");
}

void KeymasterHook::setEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(g_keymasterMutex);
    g_keymasterHookEnabled = enabled;
    LOGE("[KeymasterHook] SO 层 Key Attestation 拦截 %s", enabled ? "已启用" : "已禁用");
}

void KeymasterHook::setFakeSerial(const char* serial) {
    if (serial != nullptr && strlen(serial) == 32) {
        std::lock_guard<std::mutex> lock(g_keymasterMutex);
        strncpy(g_fakeKeymasterSerial, serial, 64);
        g_fakeKeymasterSerial[64] = '\0';
        LOGE("[KeymasterHook] 伪造序列号已更新: %s", g_fakeKeymasterSerial);
    }
}