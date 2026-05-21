//
// Created by vb2345qw on 2023/9/12.
//

#ifndef DEVICEVEIL_IOREPLACE_H
#define DEVICEVEIL_IOREPLACE_H
#include <string>
#include <vector>
#include <unistd.h>
#include <iostream>
#include <cstdlib>
#include <ctime>
#include "log.h"
#include <sstream>
#include <dlfcn.h>
#include "HookFunction.h"
#include "Symbol.h"
#include <elf.h>
#include <fcntl.h>
#include <sys/utsname.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/system_properties.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <link.h>
using namespace std;

// ============================================================================
// 全局配置变量声明（定义在 corner_monit.cpp）
// ============================================================================

// 伪造的 DRM ID (32 字节) - 可通过 JNI 动态更新
extern uint8_t g_fakeDrmId[32];

// 伪造的 Android ID (16 字符) - 可通过 JNI 动态更新
extern char g_fakeAndroidId[32];

// 伪造的 WiFi MAC 地址 (6 字节) - 可通过 JNI 动态更新
extern uint8_t g_fakeWifiMac[6];

// 伪造的开机时间 (Unix 时间戳，秒) - 可通过 JNI 动态更新
extern int64_t g_fakeBootTime;

// ============================================================================
// 文件元信息伪造配置 (stat/lstat/fstat)
// ============================================================================

// 伪造的文件时间戳基准 (Unix 时间戳，秒)
extern int64_t g_fakeFileTimeBase;

// 伪造的文件大小偏移
extern int64_t g_fakeFileSizeOffset;

// 是否启用文件元信息伪造
extern bool g_fakeStatEnabled;

// 伪造的文件权限掩码
extern mode_t g_fakeFileModeMask;

// 需要伪造 stat 的路径前缀列表
#define MAX_FAKE_STAT_PATHS 32
extern char g_fakeStatPaths[MAX_FAKE_STAT_PATHS][256];
extern int g_fakeStatPathCount;




// hook 回调
#define HOOK_DEF(ret, func, ...) \
  ret (*orig_##func)(__VA_ARGS__)=nullptr; \
  ret new_##func(__VA_ARGS__)
//

//常规 hook   HOOK_SYMBOL()的调用，又调用了 hook_function()
//new_##func=new_read    #func=read     orig_##func=orig_read
//#define HOOK_SYMBOL(handle, func) \
//hook_function(handle, #func, (void*) new_##func, (void**) &orig_##func)



//case __NR_fchmodat:   //检测程序  svc 指令自实现系统调用
//  MSHookFunction(func, (void *) new_fchmodat, (void **) &orig_fchmodat);
//  pass++;
//break;

//宏封装实现 HookFunction::Hooker(addr, new_func, old_func)  __NR_read:
//HookFunction::Hooker(func,(void *) new_##name, (void **) &orig_##name);      \

#define HOOK_SYSCALL(name,func) \
case __NR_##name:          \
hook_syscalls(#name,func,(void *) new_##name, (void **) &orig_##name);      \
pass++; \
break; \

//hook_syscalls(func,(void *) new_##name, (void **) &orig_##name);           \


//HookFunction::Hooker(func,(void *) new___##name, (void **) &orig___##name);      \

#define HOOK__SYSCALL(name,func) \
case __NR_##name:           \
hook_syscalls(#name,func,(void *) new___##name, (void **) &orig___##name);      \
pass++; \
break; \










class IOReplace{
public:
    static void substituteCharacter(std::vector<std::string> rootFile);
    static void initForbidPath(std::vector<std::string> forbidFile);
    static void startUniformer(const char *so_path, const char *so_path_64, const char *native_path,int api_level,int preview_api_level);

};


#endif //DEVICEVEIL_IOREPLACE_H
