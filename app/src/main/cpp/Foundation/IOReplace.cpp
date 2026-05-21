#include "IOReplace.h"
#include "SandboxFs.h"
#include <net/if.h>  // for struct ifreq (ioctl MAC address)
#include <ifaddrs.h>
#include <linux/if_packet.h>
#include <sys/ptrace.h>
#include <set>
#include <map>
#include <mutex>
#include <cstring>
#include <cstdlib>

// ============================================================================
// 设备信息文件伪造 (WiFi MAC, Bluetooth MAC, CPU Info 等)
// ============================================================================
// 敏感文件类型枚举
enum class SensitiveFileType {
    NONE = 0,
    WIFI_MAC,           // /sys/class/net/*/address
    BLUETOOTH_MAC,      // /sys/class/bluetooth/*/address
    CPU_INFO,           // /proc/cpuinfo
    NET_ARP,            // /proc/net/arp
    BOARD_SERIAL,       // /sys/class/dmi/id/board_serial
    PRODUCT_SERIAL,     // /sys/class/dmi/id/product_serial
    BOOT_ID,            // /proc/sys/kernel/random/boot_id (已实现伪造)
    VPN_ROUTE,          // /proc/net/route - VPN 路由表检测
    VPN_IPV6_ROUTE,     // /proc/net/ipv6_route - VPN IPv6 路由表检测
    VPN_IF_INET6,       // /proc/net/if_inet6 - VPN IPv6 接口检测
    PROC_MAPS,          // /proc/self/maps/smaps - Hook 框架检测
    PROC_MOUNTINFO,     // /proc/self/mountinfo - Magisk 挂载检测
    PROC_ATTR,          // /proc/self/attr/current - SELinux 上下文检测
    CPU_POSSIBLE,       // /sys/devices/system/cpu/possible
    CPU_FREQ,           // /sys/devices/system/cpu/*/cpufreq/*
    CPU_IDLE,           // /sys/devices/system/cpu/*/cpuidle/*
    UFS_EMMC_SERIAL,    // /sys/block/*/device/serial/cid
    USB_ISERIAL,        // /sys/class/android_usb/android0/iSerial
    BATTERY_SERIAL,     // /sys/class/power_supply/* serial/type
    BOOTCONFIG,         // /proc/bootconfig
    KERNEL_HOSTNAME,    // /proc/sys/kernel/hostname/domainname
    SELINUX_ENFORCE     // /sys/fs/selinux/enforce
};

// fd 到敏感文件类型的映射
static std::map<int, SensitiveFileType> g_sensitiveFileFdMap;
static std::mutex g_sensitiveFileMutex;

// fd/path 与 mmap 区域跟踪，用于定位 native CRC/hash/memory scan。
static std::map<int, std::string> g_fdPathMap;
static std::mutex g_fdPathMutex;

struct MmapRegionInfo {
    void *addr;
    size_t length;
    int fd;
    off64_t offset;
    std::string path;
};

static std::map<void *, MmapRegionInfo> g_mmapRegionMap;
static std::mutex g_mmapRegionMutex;

// 外部引用 corner_monit.cpp 中的伪造值
extern char g_fakeAndroidId[32];
extern uint8_t g_fakeWifiMac[6];
extern char g_fakeBluetoothMac[32];
extern char g_fakeSerialNo[64];
extern char g_fakeBootId[64];

// 外部引用命令监控开关
extern bool g_cmdMonitorEnabled;

static bool containsIgnoreCase(const char *text, const char *keyword) {
    if (text == nullptr || keyword == nullptr) return false;
    size_t textLen = strlen(text);
    size_t keywordLen = strlen(keyword);
    if (keywordLen == 0 || textLen < keywordLen) return false;

    for (size_t i = 0; i <= textLen - keywordLen; i++) {
        size_t j = 0;
        for (; j < keywordLen; j++) {
            if (tolower(text[i + j]) != tolower(keyword[j])) break;
        }
        if (j == keywordLen) return true;
    }
    return false;
}

static bool isSensitiveModulePath(const char *path) {
    if (path == nullptr || path[0] == '\0') return false;

    static const char* moduleKeywords[] = {
        "frida",
        "gadget",
        "xposed",
        "edxposed",
        "lsposed",
        "zygisk",
        "riru",
        "magisk",
        "substrate",
        "deviceveil",
        "corner_monit",
        "libdobby",
        nullptr
    };

    for (int i = 0; moduleKeywords[i] != nullptr; i++) {
        if (containsIgnoreCase(path, moduleKeywords[i])) {
            return true;
        }
    }
    return false;
}

static bool isIntegrityPath(const char *path) {
    if (path == nullptr || path[0] == '\0') return false;
    return containsIgnoreCase(path, "base.apk") ||
           containsIgnoreCase(path, "split_config") ||
           containsIgnoreCase(path, "classes.dex") ||
           containsIgnoreCase(path, ".dex") ||
           containsIgnoreCase(path, ".apk") ||
           containsIgnoreCase(path, ".jar") ||
           containsIgnoreCase(path, ".so");
}

static void addFdPath(int fd, const char *path) {
    if (fd < 0 || path == nullptr) return;
    std::lock_guard<std::mutex> lock(g_fdPathMutex);
    g_fdPathMap[fd] = path;
}

static void removeFdPath(int fd) {
    std::lock_guard<std::mutex> lock(g_fdPathMutex);
    g_fdPathMap.erase(fd);
}

static std::string getFdPath(int fd) {
    std::lock_guard<std::mutex> lock(g_fdPathMutex);
    auto it = g_fdPathMap.find(fd);
    if (it != g_fdPathMap.end()) return it->second;
    return "";
}

static void addMmapRegion(void *addr, size_t length, int fd, off64_t offset, const std::string &path) {
    if (addr == MAP_FAILED || addr == nullptr || length == 0 || path.empty()) return;
    std::lock_guard<std::mutex> lock(g_mmapRegionMutex);
    g_mmapRegionMap[addr] = {addr, length, fd, offset, path};
}

static void removeMmapRegion(void *addr, size_t length) {
    std::lock_guard<std::mutex> lock(g_mmapRegionMutex);
    for (auto it = g_mmapRegionMap.begin(); it != g_mmapRegionMap.end();) {
        uintptr_t regionStart = reinterpret_cast<uintptr_t>(it->second.addr);
        uintptr_t regionEnd = regionStart + it->second.length;
        uintptr_t unmapStart = reinterpret_cast<uintptr_t>(addr);
        uintptr_t unmapEnd = unmapStart + length;
        if (unmapStart < regionEnd && unmapEnd > regionStart) {
            it = g_mmapRegionMap.erase(it);
        } else {
            ++it;
        }
    }
}

static std::string findMmapRegionPath(const void *addr, size_t length) {
    uintptr_t start = reinterpret_cast<uintptr_t>(addr);
    uintptr_t end = start + length;
    std::lock_guard<std::mutex> lock(g_mmapRegionMutex);
    for (const auto &entry : g_mmapRegionMap) {
        uintptr_t regionStart = reinterpret_cast<uintptr_t>(entry.second.addr);
        uintptr_t regionEnd = regionStart + entry.second.length;
        if (start < regionEnd && end > regionStart) {
            return entry.second.path;
        }
    }
    return "";
}

// ============================================================================
// 文件元信息伪造辅助函数 (stat/lstat/fstat)
// ============================================================================

// 检查路径是否需要伪造 stat 信息
static bool shouldFakeStatForPath(const char *pathname) {
    if (!g_fakeStatEnabled || pathname == nullptr) {
        return false;
    }

    // 检查是否匹配任何配置的路径前缀
    for (int i = 0; i < g_fakeStatPathCount; i++) {
        if (strncmp(pathname, g_fakeStatPaths[i], strlen(g_fakeStatPaths[i])) == 0) {
            return true;
        }
    }

    // 默认需要伪造的非应用路径
    static const char* defaultFakePaths[] = {
        "/data",
        "/data/user",
        "/data/user/0",
        "/data/data",
        "/storage",
        "/storage/emulated",
        "/storage/emulated/0",
        "/system",
        "/system/build.prop",
        "/vendor",
        "/proc",
        nullptr
    };

    for (int i = 0; defaultFakePaths[i] != nullptr; i++) {
        // 精确匹配或前缀匹配（排除应用自身目录）
        if (strcmp(pathname, defaultFakePaths[i]) == 0) {
            return true;
        }
    }

    return false;
}

// 伪造 stat 缓冲区中的元信息
static void fakeStatBuffer(struct stat *statbuf, const char *pathname) {
    if (statbuf == nullptr) return;

    // 伪造时间戳
    if (g_fakeFileTimeBase > 0) {
        // 使用路径哈希生成确定性但随机化的偏移
        unsigned int pathHash = 0;
        if (pathname != nullptr) {
            for (const char *p = pathname; *p; p++) {
                pathHash = pathHash * 31 + (unsigned char)*p;
            }
        }

        // 基于路径哈希生成时间偏移 (0-30天范围内)
        int64_t timeOffset = (pathHash % (30 * 24 * 3600));

        time_t fakeTime = (time_t)(g_fakeFileTimeBase + timeOffset);

        // 保存原始时间用于日志
        time_t origMtime = statbuf->st_mtime;
        time_t origAtime = statbuf->st_atime;
        time_t origCtime = statbuf->st_ctime;

        // 伪造所有时间戳
        statbuf->st_mtime = fakeTime;
        statbuf->st_atime = fakeTime + (pathHash % 3600);  // 访问时间稍晚
        statbuf->st_ctime = fakeTime;  // 状态改变时间

        LOGE("[Stat伪造] 时间戳已伪造: %s", pathname ? pathname : "(null)");
        LOGE("[Stat伪造]   mtime: %ld -> %ld", (long)origMtime, (long)statbuf->st_mtime);
        LOGE("[Stat伪造]   atime: %ld -> %ld", (long)origAtime, (long)statbuf->st_atime);
        LOGE("[Stat伪造]   ctime: %ld -> %ld", (long)origCtime, (long)statbuf->st_ctime);
    }

    // 伪造文件大小 (仅对普通文件)
    if (g_fakeFileSizeOffset != 0 && S_ISREG(statbuf->st_mode)) {
        off_t origSize = statbuf->st_size;

        // 基于路径哈希生成大小偏移
        unsigned int pathHash = 0;
        if (pathname != nullptr) {
            for (const char *p = pathname; *p; p++) {
                pathHash = pathHash * 31 + (unsigned char)*p;
            }
        }

        // 计算偏移 (-offset ~ +offset 范围)
        int64_t sizeOffset = (pathHash % (g_fakeFileSizeOffset * 2)) - g_fakeFileSizeOffset;

        // 确保大小不为负
        if (statbuf->st_size + sizeOffset > 0) {
            statbuf->st_size += sizeOffset;
        }

        LOGE("[Stat伪造] 文件大小已伪造: %s", pathname ? pathname : "(null)");
        LOGE("[Stat伪造]   size: %lld -> %lld", (long long)origSize, (long long)statbuf->st_size);
    }

    // 伪造文件权限
    if (g_fakeFileModeMask != 0) {
        mode_t origMode = statbuf->st_mode;

        // 保留文件类型位，替换权限位
        statbuf->st_mode = (statbuf->st_mode & S_IFMT) | (g_fakeFileModeMask & ~S_IFMT);

        LOGE("[Stat伪造] 文件权限已伪造: %s", pathname ? pathname : "(null)");
        LOGE("[Stat伪造]   mode: %o -> %o", origMode, statbuf->st_mode);
    }
}

// 检查路径是否为敏感设备信息文件
static SensitiveFileType getSensitiveFileType(const char *path) {
    if (path == nullptr) return SensitiveFileType::NONE;

    // WiFi MAC: /sys/class/net/wlan0/address, /sys/class/net/eth0/address
    if (strstr(path, "/sys/class/net/") != nullptr &&
        strstr(path, "/address") != nullptr) {
        return SensitiveFileType::WIFI_MAC;
    }

    // Bluetooth MAC: /sys/class/bluetooth/hci0/address
    if (strstr(path, "/sys/class/bluetooth/") != nullptr &&
        strstr(path, "/address") != nullptr) {
        return SensitiveFileType::BLUETOOTH_MAC;
    }

    // CPU Info: /proc/cpuinfo
    if (strcmp(path, "/proc/cpuinfo") == 0) {
        return SensitiveFileType::CPU_INFO;
    }

    // ARP 表: /proc/net/arp
    if (strcmp(path, "/proc/net/arp") == 0) {
        return SensitiveFileType::NET_ARP;
    }

    // 主板序列号
    if (strstr(path, "/sys/class/dmi/id/board_serial") != nullptr ||
        strstr(path, "/sys/class/dmi/id/product_serial") != nullptr) {
        return SensitiveFileType::BOARD_SERIAL;
    }

    // boot_id: /proc/sys/kernel/random/boot_id (仅监测)
    if (strcmp(path, "/proc/sys/kernel/random/boot_id") == 0) {
        return SensitiveFileType::BOOT_ID;
    }

    // VPN 路由表检测: /proc/net/route
    if (strcmp(path, "/proc/net/route") == 0) {
        return SensitiveFileType::VPN_ROUTE;
    }

    // VPN IPv6 路由表检测: /proc/net/ipv6_route
    if (strcmp(path, "/proc/net/ipv6_route") == 0) {
        return SensitiveFileType::VPN_IPV6_ROUTE;
    }

    // VPN IPv6 接口检测: /proc/net/if_inet6
    if (strcmp(path, "/proc/net/if_inet6") == 0) {
        return SensitiveFileType::VPN_IF_INET6;
    }

    // /proc/self/maps/smaps 或 /proc/<pid>/maps/smaps - Hook 框架检测
    if (strcmp(path, "/proc/self/maps") == 0 ||
        strcmp(path, "/proc/self/smaps") == 0 ||
        strcmp(path, "/proc/self/smaps_rollup") == 0) {
        return SensitiveFileType::PROC_MAPS;
    }
    char selfMapsPath[64];
    snprintf(selfMapsPath, sizeof(selfMapsPath), "/proc/%d/maps", getpid());
    if (strcmp(path, selfMapsPath) == 0) {
        return SensitiveFileType::PROC_MAPS;
    }
    snprintf(selfMapsPath, sizeof(selfMapsPath), "/proc/%d/smaps", getpid());
    if (strcmp(path, selfMapsPath) == 0) {
        return SensitiveFileType::PROC_MAPS;
    }
    snprintf(selfMapsPath, sizeof(selfMapsPath), "/proc/%d/smaps_rollup", getpid());
    if (strcmp(path, selfMapsPath) == 0) {
        return SensitiveFileType::PROC_MAPS;
    }

    // /proc/self/mountinfo 或 /proc/<pid>/mountinfo - Magisk 挂载检测
    if (strcmp(path, "/proc/self/mountinfo") == 0) {
        return SensitiveFileType::PROC_MOUNTINFO;
    }
    char selfMountinfoPath[64];
    snprintf(selfMountinfoPath, sizeof(selfMountinfoPath), "/proc/%d/mountinfo", getpid());
    if (strcmp(path, selfMountinfoPath) == 0) {
        return SensitiveFileType::PROC_MOUNTINFO;
    }
    // 也检测 /proc/self/mounts
    if (strcmp(path, "/proc/self/mounts") == 0 || strcmp(path, "/proc/mounts") == 0) {
        return SensitiveFileType::PROC_MOUNTINFO;
    }

    // /proc/self/attr/current - SELinux 上下文检测
    if (strcmp(path, "/proc/self/attr/current") == 0) {
        return SensitiveFileType::PROC_ATTR;
    }
    char selfAttrPath[64];
    snprintf(selfAttrPath, sizeof(selfAttrPath), "/proc/%d/attr/current", getpid());
    if (strcmp(path, selfAttrPath) == 0) {
        return SensitiveFileType::PROC_ATTR;
    }

    // CPU possible: /sys/devices/system/cpu/possible - 暂时禁用
    // if (strcmp(path, "/sys/devices/system/cpu/possible") == 0) {
    //     return SensitiveFileType::CPU_POSSIBLE;
    // }

    // CPU 频率相关: /sys/devices/system/cpu/*/cpufreq/* - 暂时禁用
    // if (strstr(path, "/sys/devices/system/cpu/") != nullptr &&
    //     strstr(path, "/cpufreq/") != nullptr) {
    //     return SensitiveFileType::CPU_FREQ;
    // }

    // CPU 空闲时间: /sys/devices/system/cpu/*/cpuidle/* - 暂时禁用
    // if (strstr(path, "/sys/devices/system/cpu/") != nullptr &&
    //     strstr(path, "/cpuidle/") != nullptr) {
    //     return SensitiveFileType::CPU_IDLE;
    // }

    // UFS/eMMC serial and CID files expose stable hardware identifiers.
    if (strstr(path, "/sys/block/") != nullptr &&
        (strstr(path, "/device/serial") != nullptr ||
         strstr(path, "/device/cid") != nullptr ||
         strstr(path, "/device/manfid") != nullptr ||
         strstr(path, "/device/oemid") != nullptr)) {
        return SensitiveFileType::UFS_EMMC_SERIAL;
    }

    if (strstr(path, "/sys/class/android_usb/") != nullptr &&
        strstr(path, "iSerial") != nullptr) {
        return SensitiveFileType::USB_ISERIAL;
    }

    if (strstr(path, "/sys/class/power_supply/") != nullptr &&
        (strstr(path, "serial_number") != nullptr ||
         strstr(path, "battery_type") != nullptr)) {
        return SensitiveFileType::BATTERY_SERIAL;
    }

    if (strcmp(path, "/proc/bootconfig") == 0) {
        return SensitiveFileType::BOOTCONFIG;
    }

    if (strcmp(path, "/proc/sys/kernel/hostname") == 0 ||
        strcmp(path, "/proc/sys/kernel/domainname") == 0) {
        return SensitiveFileType::KERNEL_HOSTNAME;
    }

    if (strcmp(path, "/sys/fs/selinux/enforce") == 0) {
        return SensitiveFileType::SELINUX_ENFORCE;
    }

    return SensitiveFileType::NONE;
}

// VPN 接口名称前缀列表
static const char* g_vpnInterfacePrefixes[] = {
    "tun", "tap", "ppp", "pptp", "l2tp", "ipsec", "vpn", nullptr
};

// 检查字符串是否包含 VPN 接口名称
static bool containsVpnInterface(const char *line) {
    if (line == nullptr) return false;
    for (int i = 0; g_vpnInterfacePrefixes[i] != nullptr; i++) {
        if (strstr(line, g_vpnInterfacePrefixes[i]) != nullptr) {
            return true;
        }
    }
    return false;
}

// 记录敏感文件的 fd
static void addSensitiveFileFd(int fd, SensitiveFileType type) {
    if (fd >= 0 && type != SensitiveFileType::NONE) {
        std::lock_guard<std::mutex> lock(g_sensitiveFileMutex);
        g_sensitiveFileFdMap[fd] = type;
        LOGE("[文件伪造] 记录敏感文件 fd=%d, type=%d", fd, (int)type);
    }
}

// 移除敏感文件的 fd
static void removeSensitiveFileFd(int fd) {
    std::lock_guard<std::mutex> lock(g_sensitiveFileMutex);
    g_sensitiveFileFdMap.erase(fd);
}

// 获取 fd 对应的敏感文件类型
static SensitiveFileType getSensitiveFileFdType(int fd) {
    std::lock_guard<std::mutex> lock(g_sensitiveFileMutex);
    auto it = g_sensitiveFileFdMap.find(fd);
    if (it != g_sensitiveFileFdMap.end()) {
        return it->second;
    }
    return SensitiveFileType::NONE;
}

// 伪造敏感文件内容，返回伪造后的长度，0 表示不需要伪造
static ssize_t fakeSensitiveFileContent(SensitiveFileType type, char *buf, size_t count) {
    if (buf == nullptr || count == 0) return 0;

    char fakeContent[256] = {0};
    ssize_t fakeLen = 0;

    switch (type) {
        case SensitiveFileType::WIFI_MAC:
            // 格式: xx:xx:xx:xx:xx:xx\n
            snprintf(fakeContent, sizeof(fakeContent),
                     "%02x:%02x:%02x:%02x:%02x:%02x\n",
                     g_fakeWifiMac[0], g_fakeWifiMac[1], g_fakeWifiMac[2],
                     g_fakeWifiMac[3], g_fakeWifiMac[4], g_fakeWifiMac[5]);
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ WiFi MAC 伪造为: %s", fakeContent);
            break;

        case SensitiveFileType::BLUETOOTH_MAC:
            snprintf(fakeContent, sizeof(fakeContent), "%s\n", g_fakeBluetoothMac);
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ Bluetooth MAC 伪造为: %s", fakeContent);
            break;

        case SensitiveFileType::BOARD_SERIAL:
        case SensitiveFileType::PRODUCT_SERIAL:
        case SensitiveFileType::UFS_EMMC_SERIAL:
        case SensitiveFileType::USB_ISERIAL:
            snprintf(fakeContent, sizeof(fakeContent), "unknown\n");
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ 硬件序列号按 Android15 非 root 模式隐藏");
            break;

        case SensitiveFileType::BATTERY_SERIAL:
            snprintf(fakeContent, sizeof(fakeContent), "unknown\n");
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ 电池硬件标识已隐藏");
            break;

        case SensitiveFileType::CPU_INFO:
            // CPU info 太长，只伪造 Serial 行
            // 这里不直接替换，而是在原内容中修改
            return 0;

        case SensitiveFileType::NET_ARP:
            // ARP 表包含 MAC 地址，返回空表
            snprintf(fakeContent, sizeof(fakeContent),
                     "IP address       HW type     Flags       HW address            Mask     Device\n");
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ ARP 表已清空");
            break;

        case SensitiveFileType::VPN_ROUTE:
            // /proc/net/route - 返回只包含表头和 lo/wlan0 的路由表，过滤 VPN 接口
            snprintf(fakeContent, sizeof(fakeContent),
                     "Iface\tDestination\tGateway\t\tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n"
                     "wlan0\t00000000\t0100A8C0\t0003\t0\t0\t0\t00000000\t0\t0\t0\n");
            fakeLen = strlen(fakeContent);
            LOGE("[VPN绕过] ✅ /proc/net/route 已过滤 VPN 接口");
            break;

        case SensitiveFileType::VPN_IPV6_ROUTE:
            // /proc/net/ipv6_route - 返回空的 IPv6 路由表（过滤 VPN 接口）
            snprintf(fakeContent, sizeof(fakeContent), "");
            fakeLen = 0;
            LOGE("[VPN绕过] ✅ /proc/net/ipv6_route 已过滤 VPN 接口");
            break;

        case SensitiveFileType::VPN_IF_INET6:
            // /proc/net/if_inet6 - 返回只包含 lo 的 IPv6 接口列表
            snprintf(fakeContent, sizeof(fakeContent),
                     "00000000000000000000000000000001 01 80 10 80       lo\n");
            fakeLen = strlen(fakeContent);
            LOGE("[VPN绕过] ✅ /proc/net/if_inet6 已过滤 VPN 接口");
            break;

        case SensitiveFileType::BOOT_ID:
            // boot_id 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\n (37字符)
            snprintf(fakeContent, sizeof(fakeContent), "%s\n", g_fakeBootId);
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ boot_id 伪造为: %s", g_fakeBootId);
            break;

        case SensitiveFileType::BOOTCONFIG:
            snprintf(fakeContent, sizeof(fakeContent), "\n");
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ /proc/bootconfig 已隐藏");
            break;

        case SensitiveFileType::KERNEL_HOSTNAME:
            snprintf(fakeContent, sizeof(fakeContent), "localhost\n");
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ kernel hostname/domainname 已归一化");
            break;

        case SensitiveFileType::SELINUX_ENFORCE:
            snprintf(fakeContent, sizeof(fakeContent), "1\n");
            fakeLen = strlen(fakeContent);
            LOGE("[文件伪造] ✅ SELinux enforce 已归一化");
            break;

        // CPU possible - 暂时禁用
        // case SensitiveFileType::CPU_POSSIBLE:
        //     // CPU possible 格式: 0-7 (表示 8 核)
        //     snprintf(fakeContent, sizeof(fakeContent), "0-7\n");
        //     fakeLen = strlen(fakeContent);
        //     LOGE("[文件伪造] ✅ CPU possible 伪造为: 0-7");
        //     break;

        // CPU 频率 - 暂时禁用
        // case SensitiveFileType::CPU_FREQ:
        //     // CPU 频率信息 - 返回通用值
        //     // 不同文件返回不同内容，这里返回一个通用的频率值
        //     snprintf(fakeContent, sizeof(fakeContent), "1800000\n");
        //     fakeLen = strlen(fakeContent);
        //     break;

        // CPU 空闲时间 - 暂时禁用
        // case SensitiveFileType::CPU_IDLE:
        //     // CPU 空闲时间 - 返回随机化的值
        //     snprintf(fakeContent, sizeof(fakeContent), "%lu\n", (unsigned long)(rand() % 1000000000));
        //     fakeLen = strlen(fakeContent);
        //     break;

        case SensitiveFileType::PROC_MAPS:
            // /proc/self/maps 需要特殊处理，在 read 中过滤
            // 这里返回 0 表示不直接替换，而是在 read 后过滤
            return 0;

        default:
            return 0;
    }

    if (fakeLen > 0 && (size_t)fakeLen <= count) {
        memcpy(buf, fakeContent, fakeLen);
        return fakeLen;
    }

    return 0;
}

// ============================================================================
// 反调试绕过: TracerPid 伪造
// ============================================================================
// 用于跟踪打开的 /proc/*/status 文件的 fd
static std::set<int> g_statusFdSet;
static std::mutex g_statusFdMutex;

// 用于跟踪打开的 /proc/self/maps/smaps 文件的 fd
static std::set<int> g_mapsFdSet;
static std::mutex g_mapsFdMutex;
static std::map<int, std::string> g_mapsTailMap;

// /proc/self/maps 敏感关键词列表 - 需要过滤的行
static const char* g_mapsFilterKeywords[] = {
    // Frida 相关
    "frida",
    "gadget",
    "linjector",
    "frida-agent",

    // Xposed 相关
    "xposed",
    "edxposed",
    "lsposed",
    "exposed",

    // Magisk/Root 相关
    "magisk",
    "supersu",
    "superuser",

    // Substrate 相关
    "substrate",
    "cydia",

    // Riru/Zygisk 相关
    "riru",
    "zygisk",

    // 本模块相关
    "deviceveil",
    "fake_device",
    "corner_monit",
    "libdobby",

    // 可疑路径
    "/data/local/tmp",

    nullptr
};

// 检查是否是 /proc/self/maps/smaps 路径
static bool isProcMapsPath(const char *path) {
    if (path == nullptr) return false;
    if (strcmp(path, "/proc/self/maps") == 0) return true;
    if (strcmp(path, "/proc/self/smaps") == 0) return true;
    if (strcmp(path, "/proc/self/smaps_rollup") == 0) return true;
    char selfMapsPath[64];
    snprintf(selfMapsPath, sizeof(selfMapsPath), "/proc/%d/maps", getpid());
    if (strcmp(path, selfMapsPath) == 0) return true;
    snprintf(selfMapsPath, sizeof(selfMapsPath), "/proc/%d/smaps", getpid());
    if (strcmp(path, selfMapsPath) == 0) return true;
    snprintf(selfMapsPath, sizeof(selfMapsPath), "/proc/%d/smaps_rollup", getpid());
    if (strcmp(path, selfMapsPath) == 0) return true;
    return false;
}

// 记录 maps 文件的 fd
static void addMapsFd(int fd) {
    if (fd >= 0) {
        std::lock_guard<std::mutex> lock(g_mapsFdMutex);
        g_mapsFdSet.insert(fd);
        g_mapsTailMap.erase(fd);
    }
}

// 移除 maps 文件的 fd
static void removeMapsFd(int fd) {
    std::lock_guard<std::mutex> lock(g_mapsFdMutex);
    g_mapsFdSet.erase(fd);
    g_mapsTailMap.erase(fd);
}

// 检查 fd 是否为 maps 文件
static bool isMapsFd(int fd) {
    std::lock_guard<std::mutex> lock(g_mapsFdMutex);
    return g_mapsFdSet.find(fd) != g_mapsFdSet.end();
}

static void updateMapsTailLocked(int fd, const char *buf, ssize_t len) {
    if (buf == nullptr || len <= 0) {
        g_mapsTailMap.erase(fd);
        return;
    }

    const char *lastNewline = (const char *)memrchr(buf, '\n', (size_t)len);
    const char *tailStart = (lastNewline != nullptr) ? lastNewline + 1 : buf;
    size_t tailLen = (buf + len) - tailStart;
    if (tailLen == 0) {
        g_mapsTailMap.erase(fd);
        return;
    }

    const size_t maxTail = 256;
    if (tailLen > maxTail) {
        tailStart += tailLen - maxTail;
        tailLen = maxTail;
    }
    g_mapsTailMap[fd] = std::string(tailStart, tailLen);
}

static bool containsKeywordWithTailLocked(int fd, const char *lineStart, size_t lineLen,
                                          const char **keywords, const char **matchedKeyword) {
    std::string combined;
    auto tail = g_mapsTailMap.find(fd);
    if (tail != g_mapsTailMap.end()) {
        combined.append(tail->second);
    }
    combined.append(lineStart, lineLen);

    for (char &c : combined) {
        c = (char)tolower(c);
    }

    for (int k = 0; keywords[k] != nullptr; k++) {
        if (combined.find(keywords[k]) != std::string::npos) {
            if (matchedKeyword != nullptr) {
                *matchedKeyword = keywords[k];
            }
            return true;
        }
    }
    return false;
}

// 过滤 /proc/self/maps 内容，移除包含敏感关键词的行
static ssize_t filterMapsContent(int fd, char *buf, ssize_t len) {
    if (buf == nullptr || len <= 0) return len;

    // 创建临时缓冲区
    char *filteredBuf = (char *)malloc(len + 1);
    if (filteredBuf == nullptr) return len;

    char *src = buf;
    char *dst = filteredBuf;
    char *lineStart = src;
    ssize_t filteredLen = 0;
    int filteredCount = 0;

    // 逐行处理
    for (ssize_t i = 0; i <= len; i++) {
        // 找到行尾或缓冲区结束
        if (i == len || src[i] == '\n') {
            size_t lineLen = (i == len) ? (src + i - lineStart) : (src + i - lineStart + 1);

            // 检查该行是否包含敏感关键词
            bool shouldFilter = false;

            // 临时终止字符串以便使用 strstr
            char savedChar = src[i];
            if (i < len) src[i] = '\0';

            // 转换为小写进行比较
            char lowerLine[512];
            size_t copyLen = (lineLen < sizeof(lowerLine) - 1) ? lineLen : sizeof(lowerLine) - 1;
            for (size_t j = 0; j < copyLen; j++) {
                lowerLine[j] = tolower(lineStart[j]);
            }
            lowerLine[copyLen] = '\0';

            const char *matchedKeyword = nullptr;
            {
                std::lock_guard<std::mutex> lock(g_mapsFdMutex);
                shouldFilter = containsKeywordWithTailLocked(fd, lineStart, lineLen,
                                                             g_mapsFilterKeywords, &matchedKeyword);
                if (shouldFilter) {
                    filteredCount++;
                    LOGE("[Maps过滤] 过滤敏感行: %s (匹配: %s)", lowerLine,
                         matchedKeyword ? matchedKeyword : "unknown");
                }
            }

            // 恢复字符
            if (i < len) src[i] = savedChar;

            // 如果不需要过滤，复制该行
            if (!shouldFilter) {
                memcpy(dst, lineStart, lineLen);
                dst += lineLen;
                filteredLen += lineLen;
            }

            // 移动到下一行
            lineStart = src + i + 1;
        }
    }

    {
        std::lock_guard<std::mutex> lock(g_mapsFdMutex);
        updateMapsTailLocked(fd, buf, len);
    }

    // 复制过滤后的内容回原缓冲区
    if (filteredLen > 0) {
        memcpy(buf, filteredBuf, filteredLen);
    } else if (filteredCount > 0 && len > 0) {
        // 避免调用方把“本次全部被过滤”误判为 EOF，保留一个空行继续驱动后续读取。
        buf[0] = '\n';
        filteredLen = 1;
    }

    free(filteredBuf);

    if (filteredCount > 0) {
        LOGE("[Maps过滤] ✅ 共过滤 %d 行敏感内容", filteredCount);
    }

    return filteredLen;
}

// ============================================================================
// /proc/self/mountinfo 过滤: 隐藏 Magisk/Xposed 相关挂载
// ============================================================================
static std::set<int> g_mountinfoFdSet;
static std::mutex g_mountinfoFdMutex;

// mountinfo 敏感关键词列表
static const char* g_mountinfoFilterKeywords[] = {
    "magisk",
    "zygisk",
    "riru",
    "xposed",
    "lsposed",
    "edxposed",
    "/sbin/.magisk",
    "/data/adb/modules",
    "/data/adb/magisk",
    "overlay",  // overlayfs 可能暴露 Magisk 模块
    "tmpfs /system",  // Magisk systemless 挂载
    "tmpfs /vendor",
    "deviceveil",
    nullptr
};

static bool isProcMountinfoPath(const char *path) {
    if (path == nullptr) return false;
    if (strcmp(path, "/proc/self/mountinfo") == 0) return true;
    if (strcmp(path, "/proc/self/mounts") == 0) return true;
    if (strcmp(path, "/proc/mounts") == 0) return true;
    char selfPath[64];
    snprintf(selfPath, sizeof(selfPath), "/proc/%d/mountinfo", getpid());
    if (strcmp(path, selfPath) == 0) return true;
    return false;
}

static void addMountinfoFd(int fd) {
    if (fd >= 0) {
        std::lock_guard<std::mutex> lock(g_mountinfoFdMutex);
        g_mountinfoFdSet.insert(fd);
    }
}

static void removeMountinfoFd(int fd) {
    std::lock_guard<std::mutex> lock(g_mountinfoFdMutex);
    g_mountinfoFdSet.erase(fd);
}

static bool isMountinfoFd(int fd) {
    std::lock_guard<std::mutex> lock(g_mountinfoFdMutex);
    return g_mountinfoFdSet.find(fd) != g_mountinfoFdSet.end();
}

// 过滤 mountinfo 内容，移除包含敏感关键词的行
static ssize_t filterMountinfoContent(char *buf, ssize_t len) {
    if (buf == nullptr || len <= 0) return len;

    char *filteredBuf = (char *)malloc(len + 1);
    if (filteredBuf == nullptr) return len;

    char *src = buf;
    char *dst = filteredBuf;
    char *lineStart = src;
    ssize_t filteredLen = 0;
    int filteredCount = 0;

    for (ssize_t i = 0; i <= len; i++) {
        if (i == len || src[i] == '\n') {
            size_t lineLen = (i == len) ? (src + i - lineStart) : (src + i - lineStart + 1);

            bool shouldFilter = false;
            char savedChar = src[i];
            if (i < len) src[i] = '\0';

            char lowerLine[512];
            size_t copyLen = (lineLen < sizeof(lowerLine) - 1) ? lineLen : sizeof(lowerLine) - 1;
            for (size_t j = 0; j < copyLen; j++) {
                lowerLine[j] = tolower(lineStart[j]);
            }
            lowerLine[copyLen] = '\0';

            for (int k = 0; g_mountinfoFilterKeywords[k] != nullptr; k++) {
                if (strstr(lowerLine, g_mountinfoFilterKeywords[k]) != nullptr) {
                    shouldFilter = true;
                    filteredCount++;
                    LOGE("[Mountinfo过滤] 过滤敏感行: %s (匹配: %s)", lowerLine, g_mountinfoFilterKeywords[k]);
                    break;
                }
            }

            if (i < len) src[i] = savedChar;

            if (!shouldFilter) {
                memcpy(dst, lineStart, lineLen);
                dst += lineLen;
                filteredLen += lineLen;
            }

            lineStart = src + i + 1;
        }
    }

    if (filteredLen > 0) {
        memcpy(buf, filteredBuf, filteredLen);
    }
    free(filteredBuf);

    if (filteredCount > 0) {
        LOGE("[Mountinfo过滤] ✅ 共过滤 %d 行敏感内容", filteredCount);
    }
    return filteredLen;
}

// ============================================================================
// /proc/self/attr/current 过滤: 伪造 SELinux 上下文
// ============================================================================
static std::set<int> g_attrFdSet;
static std::mutex g_attrFdMutex;

static bool isProcAttrPath(const char *path) {
    if (path == nullptr) return false;
    if (strcmp(path, "/proc/self/attr/current") == 0) return true;
    char selfPath[64];
    snprintf(selfPath, sizeof(selfPath), "/proc/%d/attr/current", getpid());
    if (strcmp(path, selfPath) == 0) return true;
    return false;
}

static void addAttrFd(int fd) {
    if (fd >= 0) {
        std::lock_guard<std::mutex> lock(g_attrFdMutex);
        g_attrFdSet.insert(fd);
    }
}

static void removeAttrFd(int fd) {
    std::lock_guard<std::mutex> lock(g_attrFdMutex);
    g_attrFdSet.erase(fd);
}

static bool isAttrFd(int fd) {
    std::lock_guard<std::mutex> lock(g_attrFdMutex);
    return g_attrFdSet.find(fd) != g_attrFdSet.end();
}

// 伪造 SELinux 上下文，替换可疑的域为正常的 untrusted_app
static ssize_t fakeAttrContent(char *buf, ssize_t len) {
    if (buf == nullptr || len <= 0) return len;

    // 检查是否包含可疑的 SELinux 上下文
    char lowerBuf[256];
    size_t copyLen = (len < (ssize_t)sizeof(lowerBuf) - 1) ? len : sizeof(lowerBuf) - 1;
    for (size_t i = 0; i < copyLen; i++) {
        lowerBuf[i] = tolower(buf[i]);
    }
    lowerBuf[copyLen] = '\0';

    // 如果包含 magisk/zygisk/riru 等可疑域，替换为正常的 untrusted_app 上下文
    if (strstr(lowerBuf, "magisk") != nullptr ||
        strstr(lowerBuf, "zygisk") != nullptr ||
        strstr(lowerBuf, "riru") != nullptr ||
        strstr(lowerBuf, "zygote") != nullptr) {
        // 标准的 untrusted_app SELinux 上下文
        const char *fakeContext = "u:r:untrusted_app:s0:c512,c768";
        size_t fakeLen = strlen(fakeContext);
        if (fakeLen < (size_t)len) {
            memcpy(buf, fakeContext, fakeLen);
            buf[fakeLen] = '\0';
            LOGE("[Attr伪造] SELinux 上下文已伪造: %s -> %s", lowerBuf, fakeContext);
            return (ssize_t)fakeLen;
        }
    }

    return len;
}
static bool isProcStatusPath(const char *path) {
    if (path == nullptr) return false;
    // 匹配 /proc/self/status
    if (strcmp(path, "/proc/self/status") == 0) return true;
    // 匹配 /proc/self/task/*/status
    if (strstr(path, "/proc/self/task/") != nullptr && strstr(path, "/status") != nullptr) {
        return true;
    }
    // 匹配 /proc/<pid>/status (当前进程)
    char selfStatus[64];
    snprintf(selfStatus, sizeof(selfStatus), "/proc/%d/status", getpid());
    if (strcmp(path, selfStatus) == 0) return true;
    // 匹配 /proc/<pid>/task/*/status
    char taskPrefix[64];
    snprintf(taskPrefix, sizeof(taskPrefix), "/proc/%d/task/", getpid());
    if (strstr(path, taskPrefix) != nullptr && strstr(path, "/status") != nullptr) {
        return true;
    }
    return false;
}

// 记录 status 文件的 fd
static void addStatusFd(int fd) {
    if (fd >= 0) {
        std::lock_guard<std::mutex> lock(g_statusFdMutex);
        g_statusFdSet.insert(fd);
    }
}

// 移除 status 文件的 fd
static void removeStatusFd(int fd) {
    std::lock_guard<std::mutex> lock(g_statusFdMutex);
    g_statusFdSet.erase(fd);
}

// 检查 fd 是否为 status 文件
static bool isStatusFd(int fd) {
    std::lock_guard<std::mutex> lock(g_statusFdMutex);
    return g_statusFdSet.find(fd) != g_statusFdSet.end();
}

// 伪造 TracerPid: 将 "TracerPid:\t<任意数字>" 替换为 "TracerPid:\t0"
static void fakeTracerPid(char *buf, ssize_t len) {
    if (buf == nullptr || len <= 0) return;

    // 查找 "TracerPid:" 字符串
    char *tracerPid = strstr(buf, "TracerPid:");
    if (tracerPid == nullptr) return;

    // 跳过 "TracerPid:" 和可能的空白字符
    char *valueStart = tracerPid + strlen("TracerPid:");
    while (*valueStart == '\t' || *valueStart == ' ') {
        valueStart++;
    }

    // 找到数字的结束位置（换行符或字符串结束）
    char *valueEnd = valueStart;
    while (*valueEnd >= '0' && *valueEnd <= '9') {
        valueEnd++;
    }

    // 计算原始数字的长度
    int origLen = valueEnd - valueStart;
    if (origLen <= 0) return;

    // 用 '0' 替换第一个数字，其余用空格填充（保持长度不变）
    *valueStart = '0';
    for (char *p = valueStart + 1; p < valueEnd; p++) {
        *p = ' ';
    }

    LOGE("[反调试绕过] ✅ TracerPid 已伪造为 0");
}

// ============================================================================
// 反检测绕过: /proc/self/fd/ 遍历检测防护
// ============================================================================
// 敏感关键词列表 - 用于检测 Hook/调试框架
static const char* g_sensitiveKeywords[] = {
    // Frida 相关
    "frida",
    "gadget",
    "linjector",
    "@frida",
    "frida-agent",
    "re.frida.server",

    // Xposed 相关
    "xposed",
    "edxposed",
    "lsposed",
    "exposed",

    // Magisk/Root 相关
    "magisk",
    "supersu",
    "superuser",
    "busybox",

    // Substrate 相关
    "substrate",
    "cydia",

    // Riru/Zygisk 相关
    "riru",
    "zygisk",

    // 其他注入框架
    "inject",
    "hook",

    // 可疑临时目录
    "/data/local/tmp",

    // 本模块相关 (防止被检测)
    "deviceveil",
    "fake_device",
    "corner_monit",
    "libdobby",

    nullptr  // 结束标记
};

// 检查路径是否包含敏感关键词
static bool isSensitiveFdPath(const char *path) {
    if (path == nullptr) return false;

    // 转换为小写进行比较（不区分大小写）
    char lowerPath[PATH_MAX];
    size_t len = strlen(path);
    if (len >= PATH_MAX) len = PATH_MAX - 1;

    for (size_t i = 0; i < len; i++) {
        lowerPath[i] = tolower(path[i]);
    }
    lowerPath[len] = '\0';

    // 检查是否包含敏感关键词
    for (int i = 0; g_sensitiveKeywords[i] != nullptr; i++) {
        if (strstr(lowerPath, g_sensitiveKeywords[i]) != nullptr) {
            LOGE("[反检测绕过] ⚠️ 检测到敏感 fd 路径: %s (匹配: %s)", path, g_sensitiveKeywords[i]);
            return true;
        }
    }

    return false;
}

// 检查是否是 /proc/self/fd/ 路径查询
static bool isProcSelfFdPath(const char *pathname) {
    if (pathname == nullptr) return false;

    // 匹配 /proc/self/fd/
    if (strstr(pathname, "/proc/self/fd/") != nullptr) return true;

    // 匹配 /proc/<pid>/fd/ (当前进程)
    char selfFdPrefix[64];
    snprintf(selfFdPrefix, sizeof(selfFdPrefix), "/proc/%d/fd/", getpid());
    if (strstr(pathname, selfFdPrefix) != nullptr) return true;

    return false;
}

#define HOOK_SYMBOL(handle, func) \
hook_function(handle, #func, (void*) new_##func, (void**) &orig_##func)

static inline void hook_function(void *handle, const char *symbol, void *new_func, void **orig_func) {
    void *addr = dlsym(handle, symbol);  //给他一个so的句柄，在给一个符号名字，返回这个符号在so中的地址
    if (addr == NULL) {
        LOGE("常规hook 找不到符号地址 : %s", symbol);
        return;
    }
//    LOGE("V++ 64 libc hook 符号 : %s  地址: %p", symbol,addr);
    //下面开始 hook 执行hook代码

    if (HookFunction::Hooker(addr, new_func, orig_func)) {
        LOGE("常规hook成功 符号 : %s  地址: %p", symbol, addr);
    } else {
        LOGE("常规hook失败 符号 : %s  地址: %p", symbol, addr);
    }

}


static char **relocate_envp(const char *pathname, char *const envp[]) {
    if (strstr(pathname, "libweexjsb.so")) {
        return const_cast<char **>(envp);
    }
    char *soPath = getenv("V_SO_PATH");
    char *soPath64 = getenv("V_SO_PATH_64");

    char *env_so_path = NULL;
    FILE *fd = fopen(pathname, "r");
    if (!fd) {
        return const_cast<char **>(envp);
    }
    for (int i = 0; i < 4; ++i) {
        fgetc(fd);
    }
    int type = fgetc(fd);
    if (type == ELFCLASS32) {
        env_so_path = soPath;
    } else if (type == ELFCLASS64) {
        env_so_path = soPath64;
    }
    fclose(fd);
    if (env_so_path == NULL) {
        return const_cast<char **>(envp);
    }
    int len = 0;
    int ld_preload_index = -1;
    int self_so_index = -1;
    while (envp[len]) {
        /* find LD_PRELOAD element */
        if (ld_preload_index == -1 && !strncmp(envp[len], "LD_PRELOAD=", 11)) {
            ld_preload_index = len;
        }
        if (self_so_index == -1 && !strncmp(envp[len], "V_SO_PATH=", 10)) {
            self_so_index = len;
        }
        ++len;
    }
    /* append LD_PRELOAD element */
    if (ld_preload_index == -1) {
        ++len;
    }
    /* append V_env element */
    if (self_so_index == -1) {
        // V_SO_PATH
        // V_API_LEVEL
        // V_PREVIEW_API_LEVEL
        // V_NATIVE_PATH
        len += 4;
        if (soPath64) {
            // V_SO_PATH_64
            len++;
        }
        len += get_keep_item_count();
        len += get_forbidden_item_count();
        len += get_replace_item_count() * 2;
    }

    /* append NULL element */
    ++len;

    char **relocated_envp = (char **) malloc(len * sizeof(char *));
    memset(relocated_envp, 0, len * sizeof(char *));
    for (int i = 0; envp[i]; ++i) {
        if (i != ld_preload_index) {
            relocated_envp[i] = strdup(envp[i]);
        }
    }
    char LD_PRELOAD_VARIABLE[PATH_MAX];
    if (ld_preload_index == -1) {
        ld_preload_index = len - 2;
        sprintf(LD_PRELOAD_VARIABLE, "LD_PRELOAD=%s", env_so_path);
    } else {
        const char *orig_ld_preload = envp[ld_preload_index] + 11;
        sprintf(LD_PRELOAD_VARIABLE, "LD_PRELOAD=%s:%s", env_so_path, orig_ld_preload);
    }
    relocated_envp[ld_preload_index] = strdup(LD_PRELOAD_VARIABLE);
    int index = 0;
    while (relocated_envp[index]) index++;
    if (self_so_index == -1) {
        char element[PATH_MAX] = {0};
        sprintf(element, "V_SO_PATH=%s", soPath);
        relocated_envp[index++] = strdup(element);
        if (soPath64) {
            sprintf(element, "V_SO_PATH_64=%s", soPath64);
            relocated_envp[index++] = strdup(element);
        }
        sprintf(element, "V_API_LEVEL=%s", getenv("V_API_LEVEL"));
        relocated_envp[index++] = strdup(element);
        sprintf(element, "V_PREVIEW_API_LEVEL=%s", getenv("V_PREVIEW_API_LEVEL"));
        relocated_envp[index++] = strdup(element);
        sprintf(element, "V_NATIVE_PATH=%s", getenv("V_NATIVE_PATH"));
        relocated_envp[index++] = strdup(element);

        for (int i = 0; i < get_keep_item_count(); ++i) {
            PathItem &item = get_keep_items()[i];
            char env[PATH_MAX] = {0};
            sprintf(env, "V_KEEP_ITEM_%d=%s", i, item.path);
            relocated_envp[index++] = strdup(env);
        }

        for (int i = 0; i < get_forbidden_item_count(); ++i) {
            PathItem &item = get_forbidden_items()[i];
            char env[PATH_MAX] = {0};
            sprintf(env, "V_FORBID_ITEM_%d=%s", i, item.path);
            relocated_envp[index++] = strdup(env);
        }

        for (int i = 0; i < get_replace_item_count(); ++i) {
            ReplaceItem &item = get_replace_items()[i];
            char src[PATH_MAX] = {0};
            char dst[PATH_MAX] = {0};
            sprintf(src, "V_REPLACE_ITEM_SRC_%d=%s", i, item.orig_path);
            sprintf(dst, "V_REPLACE_ITEM_DST_%d=%s", i, item.new_path);
            relocated_envp[index++] = strdup(src);
            relocated_envp[index++] = strdup(dst);
        }
    }
    return relocated_envp;
}
// ============================================================================
// 反调试检测监控辅助函数
// ============================================================================

// 检查路径是否为反调试检测相关路径
// 应用常通过读取这些文件来检测是否被调试
static bool isAntiDebugPath(const char *pathname) {
    if (pathname == nullptr) return false;

    // /proc/self/status - 包含 TracerPid 字段
    if (strcmp(pathname, "/proc/self/status") == 0) {
        return true;
    }

    // /proc/<pid>/status - 同上，使用具体 PID
    // /proc/self/task/<tid>/status - 线程级别的状态检测
    // /proc/<pid>/task/<tid>/status - 同上
    if (strstr(pathname, "/proc/") != nullptr && strstr(pathname, "/status") != nullptr) {
        // 检查是否包含 task 目录（线程级别检测）
        if (strstr(pathname, "/task/") != nullptr) {
            return true;
        }
        // 检查是否是进程级别的 status 文件
        // 格式: /proc/<数字>/status 或 /proc/self/status
        const char* afterProc = pathname + 6;  // 跳过 "/proc/"
        if (strncmp(afterProc, "self/status", 11) == 0) {
            return true;
        }
        // 检查 /proc/<pid>/status 格式
        while (*afterProc >= '0' && *afterProc <= '9') {
            afterProc++;
        }
        if (strncmp(afterProc, "/status", 7) == 0) {
            return true;
        }
    }

    // /proc/self/wchan - 等待通道，可用于检测 ptrace
    if (strstr(pathname, "/proc/") != nullptr && strstr(pathname, "/wchan") != nullptr) {
        return true;
    }

    // /proc/self/stat - 进程状态，包含调试相关信息
    if (strstr(pathname, "/proc/") != nullptr) {
        const char* filename = strrchr(pathname, '/');
        if (filename != nullptr && strcmp(filename, "/stat") == 0) {
            // 排除 /proc/stat (系统级别)
            if (strstr(pathname, "/self/") != nullptr ||
                strstr(pathname, "/task/") != nullptr) {
                return true;
            }
            // 检查 /proc/<pid>/stat 格式
            const char* afterProc = pathname + 6;
            while (*afterProc >= '0' && *afterProc <= '9') {
                afterProc++;
            }
            if (*afterProc == '/') {
                return true;
            }
        }
    }

    return false;
}

HOOK_DEF(int, openat, int fd, const char *pathname, int flags, int mode) {
    char temp[PATH_MAX];

    // 反调试检测监控
    if (isAntiDebugPath(pathname)) {
        LOGE("[反调试检测] openat 检测到可疑路径: %s", pathname);
        if (strstr(pathname, "/status") != nullptr) {
            LOGE("[反调试检测] ⚠️ 应用可能正在读取 TracerPid 检测调试器!");
        }
        if (strstr(pathname, "/task/") != nullptr) {
            LOGE("[反调试检测] ⚠️ 应用正在检测线程级别的调试状态!");
        }
    }

    //路径规则的替换   relocated_path最终需要 io 的路径
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int result = (int) syscall(__NR_openat, fd, relocated_path, flags, mode);
        if (result >= 0) {
            addFdPath(result, pathname);
            if (isIntegrityPath(pathname)) {
                LOGE("[完整性监控] openat 敏感文件 fd=%d, path=%s", result, pathname);
            }
        }
        // 记录 /proc/*/status 文件的 fd，用于后续 read 时伪造 TracerPid
        if (result >= 0 && isProcStatusPath(pathname)) {
            addStatusFd(result);
            LOGE("[反调试绕过] 记录 status fd=%d, path=%s", result, pathname);
        }
        // 记录 /proc/self/maps 文件的 fd，用于后续 read 时过滤敏感内容
        if (result >= 0 && isProcMapsPath(pathname)) {
            addMapsFd(result);
            LOGE("[Maps过滤] 记录 maps fd=%d, path=%s", result, pathname);
        }
        // 记录 /proc/self/mountinfo 文件的 fd，用于后续 read 时过滤 Magisk 挂载
        if (result >= 0 && isProcMountinfoPath(pathname)) {
            addMountinfoFd(result);
            LOGE("[Mountinfo过滤] 记录 mountinfo fd=%d, path=%s", result, pathname);
        }
        // 记录 /proc/self/attr/current 文件的 fd，用于后续 read 时伪造 SELinux 上下文
        if (result >= 0 && isProcAttrPath(pathname)) {
            addAttrFd(result);
            LOGE("[Attr伪造] 记录 attr fd=%d, path=%s", result, pathname);
        }
        // 记录敏感设备信息文件的 fd，用于后续 read 时伪造内容
        if (result >= 0) {
            SensitiveFileType fileType = getSensitiveFileType(pathname);
            if (fileType != SensitiveFileType::NONE) {
                addSensitiveFileFd(result, fileType);
                LOGE("[文件伪造] 记录敏感文件 fd=%d, path=%s", result, pathname);
            }
        }
        return result;
    }
    errno = EACCES;
    return -1;
}

HOOK_DEF(int, open, const char *pathname, int flags, mode_t mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int result = (int) syscall(__NR_openat, AT_FDCWD, relocated_path, flags, mode);
        if (result >= 0) {
            addFdPath(result, pathname);
            if (isIntegrityPath(pathname)) {
                LOGE("[完整性监控] open 敏感文件 fd=%d, path=%s", result, pathname);
            }
        }
        return result;
    }
    errno = EACCES;
    return -1;
}

HOOK_DEF(int, open64, const char *pathname, int flags, mode_t mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int result = (int) syscall(__NR_openat, AT_FDCWD, relocated_path, flags, mode);
        if (result >= 0) {
            addFdPath(result, pathname);
            if (isIntegrityPath(pathname)) {
                LOGE("[完整性监控] open64 敏感文件 fd=%d, path=%s", result, pathname);
            }
        }
        return result;
    }
    errno = EACCES;
    return -1;
}


HOOK_DEF(void *, strstr, char *a1, char *a2) {
//    __android_log_print(6, "abcd", "strstr hook  %s  %s", a1, a2);
    void * result = orig_strstr(a1, a2);
//    LOGE("strstr result  %s", (char *)result);
    return result;
}


HOOK_DEF(ssize_t, read, int fd, char *buf, size_t count) {
    // 检查是否是敏感设备信息文件，如果是则直接返回伪造内容
    SensitiveFileType fileType = getSensitiveFileFdType(fd);
    if (fileType != SensitiveFileType::NONE) {
        ssize_t fakeLen = fakeSensitiveFileContent(fileType, buf, count);
        if (fakeLen > 0) {
            return fakeLen;
        }
    }

    ssize_t ret = syscall(__NR_read, fd, buf, count);  //继续执行原来的方法  svc指令

    // 反调试绕过: 如果是 /proc/*/status 文件，伪造 TracerPid
    if (ret > 0 && isStatusFd(fd)) {
        fakeTracerPid(buf, ret);
    }

    // Maps/Smaps 过滤: 如果是 /proc/self/maps/smaps 文件，过滤敏感内容
    if (ret > 0 && isMapsFd(fd)) {
        ret = filterMapsContent(fd, buf, ret);
    }

    // Mountinfo 过滤: 如果是 /proc/self/mountinfo 文件，过滤 Magisk 挂载
    if (ret > 0 && isMountinfoFd(fd)) {
        ret = filterMountinfoContent(buf, ret);
    }

    // Attr 伪造: 如果是 /proc/self/attr/current 文件，伪造 SELinux 上下文
    if (ret > 0 && isAttrFd(fd)) {
        ret = fakeAttrContent(buf, ret);
    }

    return ret;
}

// Hook pread() - 反调试绕过: 某些应用使用 pread 而不是 read 读取 /proc/*/status
HOOK_DEF(ssize_t, pread, int fd, char *buf, size_t count, off_t offset) {
    ssize_t ret = syscall(__NR_pread64, fd, buf, count, offset);

    // 反调试绕过: 如果是 /proc/*/status 文件，伪造 TracerPid
    if (ret > 0 && isStatusFd(fd)) {
        fakeTracerPid(buf, ret);
    }

    if (ret > 0 && isMapsFd(fd)) {
        ret = filterMapsContent(fd, buf, ret);
    }

    return ret;
}

// Hook pread64() - 反调试绕过: 64位版本的 pread
HOOK_DEF(ssize_t, pread64, int fd, char *buf, size_t count, off64_t offset) {
    ssize_t ret = syscall(__NR_pread64, fd, buf, count, offset);

    // 反调试绕过: 如果是 /proc/*/status 文件，伪造 TracerPid
    if (ret > 0 && isStatusFd(fd)) {
        fakeTracerPid(buf, ret);
    }

    if (ret > 0 && isMapsFd(fd)) {
        ret = filterMapsContent(fd, buf, ret);
    }

    return ret;
}

HOOK_DEF(ssize_t, write, int fd, const char *buf, size_t count) {
    ssize_t ret = 0;
    ret = syscall(__NR_write, fd, buf, count);  //继续执行原来的方法
    if (ret >0){
//        LOGE("write方法的buf : %s", buf);
    }
    return ret;
}

// Hook close() - 清理 status fd 记录，防止 fd 复用时误判
HOOK_DEF(int, close, int fd) {
    // 先从 status fd 集合中移除（如果存在）
    removeStatusFd(fd);
    // 从 maps fd 集合中移除（如果存在）
    removeMapsFd(fd);
    // 从 mountinfo fd 集合中移除（如果存在）
    removeMountinfoFd(fd);
    // 从 attr fd 集合中移除（如果存在）
    removeAttrFd(fd);
    // 从敏感文件 fd 映射中移除（如果存在）
    removeSensitiveFileFd(fd);
    removeFdPath(fd);
    // 调用原始 close
    return orig_close(fd);
}



/**
 *
 * @param pathname 需要执行的应用程序路径
 * @param argv 执行的程序的参数
 * @param envp 环境变量
 * @return
 */
//int execve(const char *__file, char * const *__argv, char * const *__envp)
HOOK_DEF(int, execve, const char *pathname, char * const *argv[], char *const envp[]) {
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] ========== execve() 调用 ==========");
        LOGE("[命令监控] 程序路径: %s", pathname ? pathname : "(null)");

        // 记录所有参数
        stringstream ss;
        int i = 0;
        while (argv[i] != nullptr) {
            ss << "argv[" << i << "]=" << argv[i] << " ";
            ++i;
        }
        LOGE("[命令监控] 参数列表: %s", ss.str().c_str());
        LOGE("[命令监控] 参数数量: %d", i);

        // 检测敏感程序
        if (pathname != nullptr) {
            if (strstr(pathname, "/su") != nullptr ||
                strstr(pathname, "magisk") != nullptr ||
                strstr(pathname, "/sh") != nullptr ||
                strstr(pathname, "/bash") != nullptr) {
                LOGE("[命令监控] ⚠️ 检测到敏感程序执行: %s", pathname);
            }
        }
    }

    //挂载 fock() 出的子进程
    char **relocated_envp = relocate_envp(pathname, envp);
    int ret = syscall(__NR_execve, pathname, argv, envp);
    if (relocated_envp != envp) {
        int j = 0;
        while (relocated_envp[j] != nullptr) {
            free(relocated_envp[j]);
            ++j;
        }
        free(relocated_envp);
    }

    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] execve() 返回值: %d (通常不返回，除非失败)", ret);
        LOGE("[命令监控] ==========================================");
    }
    return ret;
}

// Hook system() - 最常用的命令执行函数
HOOK_DEF(int, system, const char *command) {
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] ========== system() 调用 ==========");
        LOGE("[命令监控] 命令: %s", command ? command : "(null)");

        // 检测敏感命令
        if (command != nullptr) {
            if (strstr(command, "su") != nullptr ||
                strstr(command, "magisk") != nullptr ||
                strstr(command, "which") != nullptr ||
                strstr(command, "pm list") != nullptr ||
                strstr(command, "getprop") != nullptr) {
                LOGE("[命令监控] ⚠️ 检测到敏感命令: %s", command);
            }
        }
    }

    // 调用原始 system 函数
    int ret = orig_system(command);

    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] system() 返回值: %d", ret);
        LOGE("[命令监控] ==========================================");
    }
    return ret;
}

// Hook popen() - 管道方式执行命令
HOOK_DEF(FILE*, popen, const char *command, const char *type) {
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] ========== popen() 调用 ==========");
        LOGE("[命令监控] 命令: %s", command ? command : "(null)");
        LOGE("[命令监控] 模式: %s", type ? type : "(null)");

        // 检测敏感命令
        if (command != nullptr) {
            if (strstr(command, "su") != nullptr ||
                strstr(command, "magisk") != nullptr ||
                strstr(command, "which") != nullptr ||
                strstr(command, "pm list") != nullptr ||
                strstr(command, "getprop") != nullptr ||
                strstr(command, "cat /proc") != nullptr ||
                strstr(command, "ls /") != nullptr) {
                LOGE("[命令监控] ⚠️ 检测到敏感命令: %s", command);
            }
        }
    }

    // 调用原始 popen 函数
    FILE* ret = orig_popen(command, type);

    if (g_cmdMonitorEnabled) {
        if (ret != nullptr) {
            LOGE("[命令监控] popen() 成功, FILE*=%p", ret);
        } else {
            LOGE("[命令监控] popen() 失败, errno=%d", errno);
        }
        LOGE("[命令监控] ==========================================");
    }
    return ret;
}

// Hook pclose() - 关闭 popen 打开的管道
HOOK_DEF(int, pclose, FILE *stream) {
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] pclose() 调用, FILE*=%p", stream);
    }
    int ret = orig_pclose(stream);
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] pclose() 返回值: %d", ret);
    }
    return ret;
}

HOOK_DEF(long, ptrace, int request, pid_t pid, void *addr, void *data) {
    if (request == PTRACE_TRACEME) {
        LOGE("[反调试] ptrace(PTRACE_TRACEME) intercepted -> 0");
        return 0;
    }
    long ret = orig_ptrace(request, pid, addr, data);
    LOGE("[反调试] ptrace(request=%d, pid=%d) = %ld", request, pid, ret);
    return ret;
}

HOOK_DEF(char*, getenv, const char *name) {
    if (name != nullptr &&
        (strcmp(name, "LD_PRELOAD") == 0 ||
         strcmp(name, "LD_LIBRARY_PATH") == 0 ||
         strcmp(name, "LD_AUDIT") == 0 ||
         strcmp(name, "LD_PROFILE") == 0)) {
        LOGE("[环境变量] getenv(%s) hidden", name);
        return nullptr;
    }
    return orig_getenv(name);
}

HOOK_DEF(int, getifaddrs, struct ifaddrs **ifap) {
    int ret = orig_getifaddrs(ifap);
    if (ret != 0 || ifap == nullptr || *ifap == nullptr) {
        return ret;
    }

    for (struct ifaddrs *cur = *ifap; cur != nullptr; cur = cur->ifa_next) {
        if (cur->ifa_name == nullptr) continue;
        if (containsVpnInterface(cur->ifa_name)) {
            LOGE("[网络接口] getifaddrs VPN interface observed: %s", cur->ifa_name);
        }
        if (cur->ifa_addr != nullptr && cur->ifa_addr->sa_family == AF_PACKET) {
            struct sockaddr_ll *sll = (struct sockaddr_ll *)cur->ifa_addr;
            if (sll->sll_halen == 6 && strstr(cur->ifa_name, "wlan") != nullptr) {
                memcpy(sll->sll_addr, g_fakeWifiMac, 6);
                LOGE("[网络接口] getifaddrs MAC masked for %s", cur->ifa_name);
            }
        }
    }
    return ret;
}

// Hook fork() - 进程创建
HOOK_DEF(pid_t, fork, void) {
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] fork() 调用");
    }
    pid_t ret = orig_fork();
    if (g_cmdMonitorEnabled) {
        if (ret == 0) {
            LOGE("[命令监控] fork() 子进程创建成功, PID: %d", getpid());
        } else if (ret > 0) {
            LOGE("[命令监控] fork() 父进程继续, 子进程 PID: %d", ret);
        } else {
            LOGE("[命令监控] fork() 失败, errno=%d", errno);
        }
    }
    return ret;
}

// Hook vfork() - 轻量级进程创建
HOOK_DEF(pid_t, vfork, void) {
    if (g_cmdMonitorEnabled) {
        LOGE("[命令监控] vfork() 调用");
    }
    pid_t ret = orig_vfork();
    if (g_cmdMonitorEnabled) {
        if (ret == 0) {
            LOGE("[命令监控] vfork() 子进程创建成功");
        } else if (ret > 0) {
            LOGE("[命令监控] vfork() 父进程继续, 子进程 PID: %d", ret);
        } else {
            LOGE("[命令监控] vfork() 失败, errno=%d", errno);
        }
    }
    return ret;
}

// int __openat(int fd, const char *pathname, int flags, int mode);
HOOK_DEF(int, __openat, int fd, const char *pathname, int flags, int mode) {
    char temp[PATH_MAX];

    // 反调试检测监控 (32位兼容)
    if (isAntiDebugPath(pathname)) {
        LOGE("[反调试检测] __openat 检测到可疑路径: %s", pathname);
        if (strstr(pathname, "/status") != nullptr) {
            LOGE("[反调试检测] ⚠️ 应用可能正在读取 TracerPid 检测调试器!");
        }
        if (strstr(pathname, "/task/") != nullptr) {
            LOGE("[反调试检测] ⚠️ 应用正在检测线程级别的调试状态!");
        }
    }

    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int result = (int) syscall(__NR_openat, fd, relocated_path, flags, mode);
        if (result >= 0) {
            addFdPath(result, pathname);
            if (isIntegrityPath(pathname)) {
                LOGE("[完整性监控] __openat 敏感文件 fd=%d, path=%s", result, pathname);
            }
        }
        return result;
    }
    errno = EACCES;
    return -1;
}




// int readlinkat(int dirfd, const char *pathname, char *buf, size_t bufsiz);
HOOK_DEF(int, readlinkat, int dirfd, const char *pathname, char *buf, size_t bufsiz) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        long ret = syscall(__NR_readlinkat, dirfd, relocated_path, buf, bufsiz);
        if (ret < 0) {
            return (int) ret;
        } else {
            // 反检测绕过: 检查 /proc/self/fd/ 遍历
            if (isProcSelfFdPath(pathname)) {
                // 确保 buf 以 null 结尾用于字符串操作
                char linkTarget[PATH_MAX];
                size_t copyLen = (ret < PATH_MAX - 1) ? ret : PATH_MAX - 1;
                memcpy(linkTarget, buf, copyLen);
                linkTarget[copyLen] = '\0';

                // 检查链接目标是否包含敏感关键词
                if (isSensitiveFdPath(linkTarget)) {
                    LOGE("[反检测绕过] ✅ 隐藏敏感 fd: %s -> %s", pathname, linkTarget);
                    errno = ENOENT;
                    return -1;
                }
            }

            // relocate link content
            if (reverse_relocate_path_inplace(buf, bufsiz) != -1) {
                return (int) ret;
            }
        }
    }
    errno = EACCES;
    return -1;
}


// int faccessat(int dirfd, const char *pathname, int mode, int flags);
HOOK_DEF(int, faccessat, int dirfd, const char *pathname, int mode, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !(mode & W_OK && isReadOnly(relocated_path))) {
        return static_cast<int>(syscall(__NR_faccessat, dirfd, relocated_path, mode, flags));
    }
    errno = EACCES;
    return -1;
}
// int faccessat2(int dirfd, const char *pathname, int mode, int flags);
HOOK_DEF(int, faccessat2, int dirfd, const char *pathname, int mode, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !(mode & W_OK && isReadOnly(relocated_path))) {
        return static_cast<int>(syscall(__NR_faccessat2, dirfd, relocated_path, mode, flags));
    }
    errno = EACCES;
    return -1;
}

// int access(const char *pathname, int mode);
HOOK_DEF(int, access, const char *pathname, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !(mode & W_OK && isReadOnly(relocated_path))) {
        return syscall(__NR_faccessat, -100, relocated_path, mode);  // -1
    }
    errno = EACCES;
    return -1;
}


// int __statfs (__const char *__file, struct statfs *__buf);
HOOK_DEF(int, __statfs, __const char *__file, struct statfs *__buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(__file, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return static_cast<int>(syscall(__NR_statfs, relocated_path, __buf));
    }
    errno = EACCES;
    return -1;
}
// ssize_t readlink(const char *pathname, char *buf, size_t bufsiz);  //从一个数据流中读取一行数据
HOOK_DEF(ssize_t, readlink,const char *pathname, char *buf, size_t bufsiz) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
//        long ret = syscall(__NR_readlinkat, -100 , relocated_path, buf, bufsiz);
        long ret = orig_readlink(relocated_path,buf,bufsiz);
        if (ret < 0) {
            return ret;
        } else {
            // 反检测绕过: 检查 /proc/self/fd/ 遍历
            if (isProcSelfFdPath(pathname)) {
                // 确保 buf 以 null 结尾用于字符串操作
                char linkTarget[PATH_MAX];
                size_t copyLen = (ret < PATH_MAX - 1) ? ret : PATH_MAX - 1;
                memcpy(linkTarget, buf, copyLen);
                linkTarget[copyLen] = '\0';

                // 检查链接目标是否包含敏感关键词
                if (isSensitiveFdPath(linkTarget)) {
                    LOGE("[反检测绕过] ✅ 隐藏敏感 fd (readlink): %s -> %s", pathname, linkTarget);
                    errno = ENOENT;
                    return -1;
                }
            }

            // relocate link content
            if (reverse_relocate_path_inplace(buf, bufsiz) != -1) {
                return ret;
            }
        }
    }
    errno = EACCES;
    return -1;
}

// int stat(const char *pathname, struct stat *statbuf);
HOOK_DEF(int, stat,const char *pathname, struct stat *statbuf){
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int ret = orig_stat(relocated_path, statbuf);

        // 文件元信息伪造
        if (ret == 0 && shouldFakeStatForPath(pathname)) {
            fakeStatBuffer(statbuf, pathname);
        }

        return ret;
    }
    errno = EACCES;
    return -1;
}
// int fstat(int fd, struct stat *buf);  文件的大小、文件的创建时间、最后访问时间、文件类型等
HOOK_DEF(int, fstat, int fd, struct stat *buf){
    int ret = orig_fstat(fd, buf);

    // 文件元信息伪造 (通过 /proc/self/fd/xxx 获取路径)
    if (ret == 0 && g_fakeStatEnabled && buf != nullptr) {
        char fdPath[64];
        char realPath[PATH_MAX];
        snprintf(fdPath, sizeof(fdPath), "/proc/self/fd/%d", fd);
        ssize_t len = readlink(fdPath, realPath, sizeof(realPath) - 1);
        if (len > 0) {
            realPath[len] = '\0';
            if (shouldFakeStatForPath(realPath)) {
                fakeStatBuffer(buf, realPath);
            }
        }
    }

    return ret;
}

//int fstatat64(int __dir_fd, const char *__path, stat *__buf, int __flags)
HOOK_DEF(int, newfstatat, int __dir_fd, const char *__path, struct stat *__buf, int __flags){
    LOGE("目标应用 fstatat64  pathname: %s ",__path);
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(__path, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int ret = orig_newfstatat(__dir_fd, relocated_path, __buf, __flags);

        // 文件元信息伪造
        if (ret == 0 && shouldFakeStatForPath(__path)) {
            fakeStatBuffer(__buf, __path);
        }

        return ret;
    }
    errno = EACCES;
    return -1;
}
HOOK_DEF(int, statfs, __const char *__file, struct statfs *__buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(__file, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return static_cast<int>(syscall(__NR_statfs, relocated_path, __buf));
    }
    errno = EACCES;
    return -1;
}
// int lstat(const char *path, struct stat *buf);
HOOK_DEF(int, lstat, const char *pathname, struct stat *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        int ret = orig_lstat(relocated_path, buf);

        // 文件元信息伪造
        if (ret == 0 && shouldFakeStatForPath(pathname)) {
            fakeStatBuffer(buf, pathname);
        }

        return ret;
    }
    errno = EACCES;
    return -1;
}
// int statfs64(const char *__path, struct statfs64 *__buf) __INTRODUCED_IN(21);
HOOK_DEF(int, statfs64, const char *filename, struct statfs64 *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(filename, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return static_cast<int>(syscall(__NR_statfs, relocated_path, buf));
    }
    errno = EACCES;
    return -1;
}
// int __statfs64(const char *path, size_t size, struct statfs *stat);
HOOK_DEF(int, __statfs64, const char *pathname, size_t size, struct statfs *stat) {
    LOGE("目标应用 __statfs64  pathname: %s ",pathname);
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return static_cast<int>(syscall(__NR_statfs, relocated_path, size, stat));
    }
    errno = EACCES;
    return -1;
}

// ============================================================================
// 新增 Hook: 系统属性获取 (Android 15 设备指纹核心)
// ============================================================================

// 检查是否为关键设备指纹属性
static bool isKeyProperty(const char *name) {
    if (name == nullptr) return false;

    // 设备指纹关键属性前缀
    const char *keyPrefixes[] = {
        // ===== 基础设备信息 =====
        "ro.build.",           // Build 信息 (fingerprint, id, display, version等)
        "ro.product.",         // 产品信息 (model, brand, name, device, manufacturer)
        "ro.hardware",         // 硬件信息
        "ro.board.",           // 主板信息 (platform)
        "ro.serialno",         // 序列号
        "ro.boot.serialno",    // 启动序列号
        "ro.bootloader",       // Bootloader 版本
        "ro.baseband",         // 基带版本

        // ===== 启动与系统镜像 =====
        "ro.bootimage.",       // 启动镜像
        "ro.boot.hardware",    // 启动硬件信息
        "ro.boot.verifiedbootstate",  // 验证启动状态 (green/yellow/orange)
        "ro.boot.vbmeta.",     // VBMeta 验证信息
        "ro.boot.flash.",      // Flash 信息
        "ro.kernel.",          // 内核信息
        "ro.zygote",           // Zygote 模式 (zygote32/zygote64)

        // ===== 厂商与系统分区 =====
        "ro.odm.",             // ODM 信息
        "ro.vendor.",          // 厂商信息
        "ro.system.",          // 系统信息
        "ro.oem.",             // OEM 信息
        "ro.config.",          // 配置信息

        // ===== 电话与SIM卡 =====
        "gsm.",                // SIM 卡信息 (imei, imsi, operator等)
        "ril.",                // RIL 无线接口层信息
        "ro.telephony.",       // 电话相关配置

        // ===== 网络信息 =====
        "net.hostname",        // 网络主机名
        "wifi.",               // WiFi 信息
        "bluetooth.",          // 蓝牙信息

        // ===== 安全与调试状态 =====
        "ro.crypto.",          // 加密信息
        "ro.secure",           // 安全状态
        "ro.debuggable",       // 调试状态
        "ro.adb.",             // ADB 状态
        "sys.oem_unlock_allowed",  // OEM 解锁状态

        // ===== 系统特性 =====
        "ro.treble_enabled",   // Treble 支持
        "ro.vndk.",            // VNDK 版本
        "dalvik.vm.",          // Dalvik VM 配置
        "ro.sf.lcd_density",   // 屏幕密度

        // ===== 持久化与区域设置 =====
        "persist.sys.device",  // 设备持久化信息
        "persist.sys.timezone",    // 时区
        "persist.sys.language",    // 语言
        "persist.sys.country",     // 国家
        "persist.sys.localevar",   // 区域变量

        // ===== Google 服务 =====
        "ro.com.google.",      // Google 服务相关
        "ro.setupwizard.",     // 设置向导信息
        "ro.gms.",             // GMS 信息

        // ===== 厂商特有属性 (ROM 检测) =====
        // 小米 MIUI
        "ro.miui.",            // MIUI 系统属性
        "ro.mi.",              // 小米设备属性

        // 华为 EMUI/HarmonyOS
        "ro.build.version.emui",   // EMUI 版本
        "ro.huawei.",          // 华为设备属性

        // OPPO ColorOS
        "ro.build.version.opporom",  // OPPO ROM 版本
        "ro.build.version.oplusrom", // OPLUS ROM 版本
        "ro.oppo.",            // OPPO 设备属性
        "ro.oplus.",           // OPLUS 设备属性

        // vivo OriginOS/FuntouchOS
        "ro.vivo.",            // vivo 设备属性

        // 三星 OneUI
        "ro.samsung.",         // 三星设备属性

        // 锤子 Smartisan OS
        "ro.smartisan.",       // 锤子设备属性

        // 魅族 Flyme
        "ro.meizu.",           // 魅族设备属性
        "ro.flyme.",           // Flyme 系统属性

        // 一加 OxygenOS/ColorOS
        "ro.oneplus.",         // 一加设备属性

        // 荣耀 MagicOS
        "ro.honor.",           // 荣耀设备属性
        "ro.build.version.magic",  // Magic OS 版本

        // 联想/摩托罗拉
        "ro.lenovo.",          // 联想设备属性
        "ro.mot.",             // 摩托罗拉设备属性

        // 其他厂商
        "ro.prize_customer",   // Prize 定制
        "ro.preinstall.",      // 预装信息
        "ro.channel.",         // 渠道信息
    };

    for (const char *prefix : keyPrefixes) {
        if (strncmp(name, prefix, strlen(prefix)) == 0) {
            return true;
        }
    }
    return false;
}

// ============================================================================
// 系统属性伪造配置 (从 corner_monit.cpp 引用)
// ============================================================================
// 注意: g_fakeAndroidId, g_fakeWifiMac, g_fakeBluetoothMac, g_fakeSerialNo
// 已在文件开头通过 extern 声明引用 corner_monit.cpp 中的全局变量
extern int64_t g_fakeBootTime;

// 伪造的 IMEI (本地使用)
static char g_fakeImei[32] = "123456789012345";

// 检查并伪造系统属性
static bool fakeSystemProperty(const char *name, char *value) {
    if (name == nullptr || value == nullptr) return false;

    // 序列号伪造
    if (strcmp(name, "ro.serialno") == 0 ||
        strcmp(name, "ro.boot.serialno") == 0 ||
        strcmp(name, "sys.serialno") == 0 ||
        strcmp(name, "ril.serialnumber") == 0) {
        strcpy(value, g_fakeSerialNo);
        LOGE("[属性伪造] ✅ %s: 伪造为 %s", name, value);
        return true;
    }

    // IMEI 伪造
    if (strcmp(name, "gsm.imei") == 0 ||
        strcmp(name, "ril.imei") == 0 ||
        strcmp(name, "gsm.device.imei") == 0) {
        strcpy(value, g_fakeImei);
        LOGE("[属性伪造] ✅ %s: 伪造为 %s", name, value);
        return true;
    }

    // 蓝牙 MAC 伪造
    if (strcmp(name, "bluetooth.address") == 0 ||
        strcmp(name, "ro.bt.bdaddr_path") == 0 ||
        strcmp(name, "persist.service.bdroid.bdaddr") == 0) {
        snprintf(value, 18, "%02X:%02X:%02X:%02X:%02X:%02X",
                 g_fakeWifiMac[0], g_fakeWifiMac[1], g_fakeWifiMac[2],
                 g_fakeWifiMac[3], g_fakeWifiMac[4], g_fakeWifiMac[5]);
        LOGE("[属性伪造] ✅ %s: 伪造为 %s", name, value);
        return true;
    }

    // WiFi MAC 伪造
    if (strcmp(name, "wifi.interface") == 0) {
        // 不伪造接口名，但记录
        return false;
    }

    // Android ID 相关 (通常不通过属性获取，但某些 ROM 可能有)
    if (strstr(name, "android_id") != nullptr) {
        strcpy(value, g_fakeAndroidId);
        LOGE("[属性伪造] ✅ %s: 伪造为 %s", name, value);
        return true;
    }

    return false;
}

// int __system_property_get(const char *name, char *value);
HOOK_DEF(int, __system_property_get, const char *name, char *value) {
    int ret = orig___system_property_get(name, value);

    // 尝试伪造属性值
    if (ret > 0 && fakeSystemProperty(name, value)) {
        return strlen(value);
    }

    // 只记录关键属性，避免海量日志
    if (isKeyProperty(name)) {
        LOGE("[属性监测] __system_property_get: name='%s', value='%s'",
             name ? name : "(null)",
             value ? value : "(null)");
    }

    return ret;
}

// ============================================================================
// 新增 Hook: 网络数据接收 (Netlink MAC 地址获取)
// ============================================================================

// ssize_t recvfrom(int sockfd, void *buf, size_t len, int flags,
//                  struct sockaddr *src_addr, socklen_t *addrlen);
HOOK_DEF(ssize_t, recvfrom, int sockfd, void *buf, size_t len, int flags,
         struct sockaddr *src_addr, socklen_t *addrlen) {
    return orig_recvfrom(sockfd, buf, len, flags, src_addr, addrlen);
}

// ssize_t recvmsg(int sockfd, struct msghdr *msg, int flags);
HOOK_DEF(ssize_t, recvmsg, int sockfd, struct msghdr *msg, int flags) {
    return orig_recvmsg(sockfd, msg, flags);
}

// ssize_t recv(int sockfd, void *buf, size_t len, int flags);
HOOK_DEF(ssize_t, recv, int sockfd, void *buf, size_t len, int flags) {
    return orig_recv(sockfd, buf, len, flags);
}

// ============================================================================
// 新增 Hook: 设备控制 (硬件信息获取)
// ============================================================================

// ioctl 命令定义 (来自 Linux 内核头文件)
// 网络相关 ioctl 命令 (sockios.h)
#define SIOCGIFNAME     0x8910  // 获取接口名称
#define SIOCGIFCONF     0x8912  // 获取接口配置列表
#define SIOCGIFFLAGS    0x8913  // 获取接口标志
#define SIOCSIFFLAGS    0x8914  // 设置接口标志
#define SIOCGIFADDR     0x8915  // 获取接口 IP 地址
#define SIOCSIFADDR     0x8916  // 设置接口 IP 地址
#define SIOCGIFDSTADDR  0x8917  // 获取点对点地址
#define SIOCGIFBRDADDR  0x8919  // 获取广播地址
#define SIOCGIFNETMASK  0x891b  // 获取子网掩码
#define SIOCGIFHWADDR   0x8927  // 获取硬件地址 (MAC)
#define SIOCSIFHWADDR   0x8924  // 设置硬件地址 (MAC)
#define SIOCGIFINDEX    0x8933  // 获取接口索引
#define SIOCGIFMTU      0x8921  // 获取 MTU

// Binder 相关 ioctl 命令
#define BINDER_WRITE_READ           0xc0306201
#define BINDER_SET_MAX_THREADS      0x40046205
#define BINDER_SET_CONTEXT_MGR      0x40046207
#define BINDER_THREAD_EXIT          0x40046208
#define BINDER_VERSION              0xc0046209
#define BINDER_GET_NODE_DEBUG_INFO  0xc028620b

// 终端相关 ioctl 命令 (termios.h)
#define TCGETS          0x5401  // 获取终端属性
#define TCSETS          0x5402  // 设置终端属性
#define TIOCGWINSZ      0x5413  // 获取窗口大小
#define TIOCSWINSZ      0x5414  // 设置窗口大小

// 获取 ioctl 命令名称
static const char* getIoctlCmdName(unsigned long request) {
    switch (request) {
        // 网络相关
        case SIOCGIFNAME:    return "SIOCGIFNAME (获取接口名称)";
        case SIOCGIFCONF:    return "SIOCGIFCONF (获取接口配置列表)";
        case SIOCGIFFLAGS:   return "SIOCGIFFLAGS (获取接口标志)";
        case SIOCSIFFLAGS:   return "SIOCSIFFLAGS (设置接口标志)";
        case SIOCGIFADDR:    return "SIOCGIFADDR (获取IP地址)";
        case SIOCSIFADDR:    return "SIOCSIFADDR (设置IP地址)";
        case SIOCGIFDSTADDR: return "SIOCGIFDSTADDR (获取点对点地址)";
        case SIOCGIFBRDADDR: return "SIOCGIFBRDADDR (获取广播地址)";
        case SIOCGIFNETMASK: return "SIOCGIFNETMASK (获取子网掩码)";
        case SIOCGIFHWADDR:  return "SIOCGIFHWADDR (获取MAC地址)";
        case SIOCSIFHWADDR:  return "SIOCSIFHWADDR (设置MAC地址)";
        case SIOCGIFINDEX:   return "SIOCGIFINDEX (获取接口索引)";
        case SIOCGIFMTU:     return "SIOCGIFMTU (获取MTU)";
        // Binder 相关
        case BINDER_WRITE_READ:          return "BINDER_WRITE_READ";
        case BINDER_SET_MAX_THREADS:     return "BINDER_SET_MAX_THREADS";
        case BINDER_SET_CONTEXT_MGR:     return "BINDER_SET_CONTEXT_MGR";
        case BINDER_THREAD_EXIT:         return "BINDER_THREAD_EXIT";
        case BINDER_VERSION:             return "BINDER_VERSION";
        case BINDER_GET_NODE_DEBUG_INFO: return "BINDER_GET_NODE_DEBUG_INFO";
        // 终端相关
        case TCGETS:      return "TCGETS (获取终端属性)";
        case TCSETS:      return "TCSETS (设置终端属性)";
        case TIOCGWINSZ:  return "TIOCGWINSZ (获取窗口大小)";
        case TIOCSWINSZ:  return "TIOCSWINSZ (设置窗口大小)";
        default:          return nullptr;
    }
}

// int ioctl(int fd, unsigned long request, ...);
HOOK_DEF(int, ioctl, int fd, unsigned long request, void *arg) {
    int ret = orig_ioctl(fd, request, arg);

    // ioctl 是 Binder、显示、音视频、网络的高频入口，禁止全量日志，避免目标 app 卡死。
    if (request == SIOCGIFHWADDR && ret == 0 && arg != nullptr) {
        struct ifreq* ifr = (struct ifreq*)arg;
        unsigned char* mac = (unsigned char*)ifr->ifr_hwaddr.sa_data;
        memcpy(mac, g_fakeWifiMac, 6);
        LOGE("[设备控制] ioctl SIOCGIFHWADDR 已伪造: 接口='%s'", ifr->ifr_name);
    }

    return ret;
}

// ============================================================================
// 完整性检测监控: mmap / process_vm_readv
// ============================================================================

HOOK_DEF(void*, mmap, void *addr, size_t length, int prot, int flags, int fd, off_t offset) {
    void *ret = orig_mmap(addr, length, prot, flags, fd, offset);
    if (ret != MAP_FAILED && fd >= 0) {
        std::string path = getFdPath(fd);
        if (!path.empty() && isIntegrityPath(path.c_str())) {
            addMmapRegion(ret, length, fd, offset, path);
            LOGE("[完整性监控] mmap 敏感映射 addr=%p, len=%zu, fd=%d, off=%lld, path=%s",
                 ret, length, fd, (long long)offset, path.c_str());
        }
    }
    return ret;
}

HOOK_DEF(void*, mmap64, void *addr, size_t length, int prot, int flags, int fd, off64_t offset) {
    void *ret = orig_mmap64(addr, length, prot, flags, fd, offset);
    if (ret != MAP_FAILED && fd >= 0) {
        std::string path = getFdPath(fd);
        if (!path.empty() && isIntegrityPath(path.c_str())) {
            addMmapRegion(ret, length, fd, offset, path);
            LOGE("[完整性监控] mmap64 敏感映射 addr=%p, len=%zu, fd=%d, off=%lld, path=%s",
                 ret, length, fd, (long long)offset, path.c_str());
        }
    }
    return ret;
}

HOOK_DEF(int, munmap, void *addr, size_t length) {
    removeMmapRegion(addr, length);
    return orig_munmap(addr, length);
}

HOOK_DEF(ssize_t, process_vm_readv, pid_t pid,
         const struct iovec *local_iov, unsigned long liovcnt,
         const struct iovec *remote_iov, unsigned long riovcnt,
         unsigned long flags) {
    ssize_t ret = orig_process_vm_readv(pid, local_iov, liovcnt, remote_iov, riovcnt, flags);
    if (ret > 0 && pid == getpid() && remote_iov != nullptr) {
        for (unsigned long i = 0; i < riovcnt; i++) {
            std::string path = findMmapRegionPath(remote_iov[i].iov_base, remote_iov[i].iov_len);
            if (!path.empty()) {
                LOGE("[完整性监控] process_vm_readv 读取敏感映射 len=%zu, path=%s",
                     remote_iov[i].iov_len, path.c_str());
                break;
            }
        }
    }
    return ret;
}

// ============================================================================
// 新增 Hook: 内核信息获取 (Android 15 替代 /proc/version)
// ============================================================================

// int uname(struct utsname *buf);
HOOK_DEF(int, uname, struct utsname *buf) {
    int ret = orig_uname(buf);
    if (ret == 0 && buf != nullptr) {
        LOGE("[内核信息] uname: sysname='%s', nodename='%s', release='%s', "
             "version='%s', machine='%s'",
             buf->sysname, buf->nodename, buf->release,
             buf->version, buf->machine);
    }
    return ret;
}

// ============================================================================
// 新增 Hook: AMediaDrm (DRM 设备 ID - Android 15 重要指纹)
// ============================================================================

// AMediaDrm 相关类型定义 (避免依赖 NDK 头文件)
typedef struct AMediaDrm AMediaDrm;
typedef int32_t media_status_t;
typedef struct {
    const uint8_t *ptr;
    size_t length;
} AMediaDrmByteArray;

// 注意: 伪造的 DRM ID 现在使用全局变量 g_fakeDrmId (定义在 corner_monit.cpp)
// 可通过 JNI 动态更新

// 静态缓冲区用于存储伪造的 DRM ID
// 注意: AMediaDrmByteArray.ptr 是 const uint8_t*，指向只读内存
// 不能直接 memcpy 写入，否则会触发 SIGSEGV 崩溃
// 解决方案: 使用静态缓冲区，修改指针指向我们的缓冲区
static uint8_t g_fakeDrmIdBuffer[32];
static bool g_fakeDrmIdBufferInitialized = false;

// 原始函数指针
static media_status_t (*orig_AMediaDrm_getPropertyByteArray)(
    AMediaDrm *drm, const char *propertyName, AMediaDrmByteArray *propertyValue) = nullptr;

// Hook 函数
static media_status_t new_AMediaDrm_getPropertyByteArray(
    AMediaDrm *drm, const char *propertyName, AMediaDrmByteArray *propertyValue) {
    media_status_t ret = orig_AMediaDrm_getPropertyByteArray(drm, propertyName, propertyValue);

    if (ret == 0 && propertyValue != nullptr && propertyValue->ptr != nullptr) {
        // 将字节数组转换为十六进制字符串用于日志
        char hexStr[256] = {0};
        size_t hexLen = 0;
        size_t maxBytes = (propertyValue->length > 32) ? 32 : propertyValue->length;

        for (size_t i = 0; i < maxBytes && hexLen < sizeof(hexStr) - 3; i++) {
            snprintf(hexStr + hexLen, sizeof(hexStr) - hexLen, "%02X", propertyValue->ptr[i]);
            hexLen += 2;
        }
        if (propertyValue->length > 32) {
            snprintf(hexStr + hexLen, sizeof(hexStr) - hexLen, "...");
        }

        LOGE("[DRM属性] AMediaDrm_getPropertyByteArray: property='%s', length=%zu, 原始值=%s",
             propertyName ? propertyName : "(null)",
             propertyValue->length,
             hexStr);

        // 检查是否是 deviceUniqueId，如果是则伪造返回值
        if (propertyName != nullptr && strcmp(propertyName, "deviceUniqueId") == 0) {
            // 每次都从全局配置复制最新的 DRM ID（支持 JNI 动态更新）
            memcpy(g_fakeDrmIdBuffer, g_fakeDrmId, 32);
            g_fakeDrmIdBufferInitialized = true;

            // 伪造 DRM ID：修改结构体指针指向我们的静态缓冲区
            // 注意: propertyValue 结构体本身是可写的，只是 ptr 指向的内存是只读的
            // 我们不修改 ptr 指向的内存，而是修改 ptr 本身
            const uint8_t* origPtr = propertyValue->ptr;
            propertyValue->ptr = g_fakeDrmIdBuffer;
            propertyValue->length = 32;

            // 生成伪造值的十六进制字符串用于日志
            char fakeHexStr[128] = {0};
            for (size_t i = 0; i < 32; i++) {
                snprintf(fakeHexStr + i * 2, sizeof(fakeHexStr) - i * 2, "%02X", g_fakeDrmId[i]);
            }

            LOGE("[DRM属性] ✅ deviceUniqueId 已伪造");
            LOGE("[DRM属性]   原始值: %s (长度=%zu)", hexStr, maxBytes);
            LOGE("[DRM属性]   伪造值: %s (长度=32)", fakeHexStr);
        }
    } else {
        LOGE("[DRM属性] AMediaDrm_getPropertyByteArray: property='%s', ret=%d",
             propertyName ? propertyName : "(null)", ret);
    }

    return ret;
}

// Hook AMediaDrm 函数 (需要单独调用，因为在 libmediandk.so 中)
static void hookAMediaDrm() {
    void *mediaNdkHandle = dlopen("libmediandk.so", RTLD_NOW);
    if (mediaNdkHandle == nullptr) {
        LOGE("[DRM Hook] 无法加载 libmediandk.so: %s", dlerror());
        return;
    }

    void *funcAddr = dlsym(mediaNdkHandle, "AMediaDrm_getPropertyByteArray");
    if (funcAddr != nullptr) {
        HookFunction::Hooker(funcAddr,
                            (void *)new_AMediaDrm_getPropertyByteArray,
                            (void **)&orig_AMediaDrm_getPropertyByteArray);
        LOGE("[DRM Hook] AMediaDrm_getPropertyByteArray Hook 成功");
    } else {
        LOGE("[DRM Hook] 未找到 AMediaDrm_getPropertyByteArray: %s", dlerror());
    }

    // 不要 dlclose，保持库加载状态
}

// ============================================================================
// 模块枚举隐藏: dl_iterate_phdr
// ============================================================================

struct DlIterateContext {
    int (*callback)(struct dl_phdr_info *info, size_t size, void *data);
    void *data;
};

static int filteredDlIterateCallback(struct dl_phdr_info *info, size_t size, void *data) {
    DlIterateContext *ctx = reinterpret_cast<DlIterateContext *>(data);
    const char *name = (info != nullptr) ? info->dlpi_name : nullptr;
    if (isSensitiveModulePath(name)) {
        LOGE("[模块隐藏] dl_iterate_phdr 隐藏: %s", name);
        return 0;
    }
    return ctx->callback(info, size, ctx->data);
}

HOOK_DEF(int, dl_iterate_phdr,
         int (*callback)(struct dl_phdr_info *info, size_t size, void *data),
         void *data) {
    if (callback == nullptr) {
        return orig_dl_iterate_phdr(callback, data);
    }
    DlIterateContext ctx{callback, data};
    return orig_dl_iterate_phdr(filteredDlIterateCallback, &ctx);
}

string generateRandomString(int length) {
    string charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    string randomString;
    srand(time(0));
    for (int i = 0; i < length; i++) {
        int index = rand() % charset.length(); // 随机获取字符集中的字符
        randomString += charset[index];
    }
    return randomString;
}

const char *getLastChar(const char *str) {
    int last_slash_index = -1;
    int len = strlen(str);
    for (int i = 0; i < len; ++i) {
        if (str[i] == '/') {
            last_slash_index = i;
        }
    }
    const char *output_str = "";
    if (last_slash_index != -1 && last_slash_index < len - 1) {
        output_str = str + last_slash_index + 1;
    }
    return output_str;
}

int getLastnum(const char *str) {
    int last_slash_index = -1;
    int len = strlen(str);
    for (int i = 0; i < len; ++i) {
        if (str[i] == '/') {
            last_slash_index = i;
        }
    }
    return last_slash_index;
}

//svc hook 的调用
static inline void hook_syscalls(const char* name,void *symbol, void *new_func, void **orig_func){
    if (symbol == nullptr){
        LOGE("svc 找不到符号地址 : %p  name: %s", symbol,name);
        return;
    }
    if (HookFunction::Hooker(symbol, new_func, orig_func)){
        LOGE("svc hook成功 符号 : %s  地址: %p", name,symbol);
    }else{
        LOGE("svc hook失败 符号 : %s  地址: %p", name,symbol);
    }
}

/**
 * hook svc 指令，拿到指定应用程序发生了哪些系统调用
 * @param path  so的路径
 * @param num   系统调用号
 * @param func  被hook的方法的地址
 * @return
 */
#if defined(__aarch64__)
// 64 位系统调用 Hook 回调 (仅在 aarch64 架构下编译)
bool on_found_syscall_aarch64(const char *path, int num, void *func) {
//    LOGE("路径: %s  符号地址:%p  系统调用号:%d", path,func,num);
    static int pass = 0;
    switch (num) {
        HOOK_SYSCALL(read, func);        //__NR_read
        HOOK_SYSCALL(openat, func);      //__NR_openat
        HOOK_SYSCALL(write, func);       //__NR_write
        HOOK_SYSCALL(execve, func);      //__NR_execve
        HOOK_SYSCALL(faccessat, func);   //__NR_faccessat
        HOOK_SYSCALL(faccessat2, func);  //__NR_faccessat2
        HOOK__SYSCALL(statfs, func);          //__NR3264_statfs
        HOOK_SYSCALL(fstat, func);          //__NR_fstat
        HOOK_SYSCALL(newfstatat, func);          //__NR3264_fstatat64
    }
    return CONTINUE_FIND_SYSCALL;
}
#endif


//系统so hook 核心入口
void startIOHook(int api_level) {
    const char *mode = getenv("DEVICEVEIL_NATIVE_MODE");
    bool fullNativeHook = mode != nullptr && strcmp(mode, "full") == 0;
    if (!fullNativeHook) {
        LOGE("[Native Hook] 保守模式: 跳过 libc inline hook，避免触发目标 App 内存完整性检测");
        return;
    }

    //通过符号查找进行 hook
    void *handle = dlopen("libc.so", RTLD_NOW);
    if (handle == nullptr) {
        LOGE("libc handle error");
        return;
    }
#if defined(__aarch64__)  //64位   void *  8字节

    // ============================================================================
    // ⚠️ 【不建议恢复】SVC Hook - 崩溃根源
    // ============================================================================
    // 问题: 扫描 libc.so 中所有 SVC 指令并进行 inline hook，过于激进
    // 现象: 导致 Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR) 崩溃
    // 原因: SVC 指令是系统调用入口，大量 Hook 会破坏内存布局和指令对齐
    // 结论: 此功能已确认为崩溃根源，禁止启用
    LOGE("⚠️ [Native Hook] SVC Hook 已禁用 - 这是导致 SIGSEGV 崩溃的根源，请勿启用");

    // ============================================================================
    // ✅ 【已恢复】常规 Hook - libc.so 函数监控
    // ============================================================================
    HOOK_SYMBOL(handle, strstr);            // 字符串搜索
    HOOK_SYMBOL(handle, read);              // 文件读取 ✅ 已恢复
    HOOK_SYMBOL(handle, pread);             // 文件定位读取 - 反调试绕过
    HOOK_SYMBOL(handle, pread64);           // 文件定位读取64位 - 反调试绕过
    HOOK_SYMBOL(handle, open);              // 文件打开
    HOOK_SYMBOL(handle, open64);            // 文件打开64
    HOOK_SYMBOL(handle, openat);            // 文件打开
    HOOK_SYMBOL(handle, close);             // 文件关闭 - 反调试绕过 fd 清理
    HOOK_SYMBOL(handle, execve);            // 程序执行
    HOOK_SYMBOL(handle, readlinkat);        // 符号链接读取（新版）
    HOOK_SYMBOL(handle, readlink);          // 符号链接读取（32位兼容）
    HOOK_SYMBOL(handle, faccessat);         // 文件访问权限检查（新版）
    HOOK_SYMBOL(handle, access);            // 文件访问权限检查
    HOOK_SYMBOL(handle, stat);              // 文件状态查询
    HOOK_SYMBOL(handle, fstat);             // 文件描述符状态查询 ✅ 已恢复
    HOOK_SYMBOL(handle, statfs);            // 文件系统状态查询
    HOOK_SYMBOL(handle, lstat);             // 符号链接状态查询
    HOOK_SYMBOL(handle, mmap);              // 文件映射 - 完整性检测监控
    HOOK_SYMBOL(handle, mmap64);            // 文件映射64 - 完整性检测监控
    HOOK_SYMBOL(handle, munmap);            // 取消映射 - 清理映射记录
    HOOK_SYMBOL(handle, process_vm_readv);  // 进程内存读取 - text 段校验监控
    HOOK_SYMBOL(handle, dl_iterate_phdr);   // 已加载模块枚举隐藏

    // ============================================================================
    // ✅ 【已恢复】命令执行监控
    // ============================================================================
    HOOK_SYMBOL(handle, system);            // system() - Shell 命令执行
    HOOK_SYMBOL(handle, popen);             // popen() - 管道方式执行命令
    HOOK_SYMBOL(handle, pclose);            // pclose() - 关闭管道
    HOOK_SYMBOL(handle, ptrace);            // ptrace(PTRACE_TRACEME) - 反调试
    HOOK_SYMBOL(handle, getenv);            // LD_* 环境变量隐藏
    HOOK_SYMBOL(handle, getifaddrs);         // 网络接口枚举 MAC/VPN 监控
    // ⚠️ fork/vfork Hook 已禁用 - 这些函数被系统频繁调用，Hook 会导致 SIGSEGV 崩溃
    // HOOK_SYMBOL(handle, fork);           // fork() - 进程创建 (已禁用)
    // HOOK_SYMBOL(handle, vfork);          // vfork() - 轻量级进程创建 (已禁用)

    // ============================================================================
    // ✅ 【已恢复】系统属性获取 (已添加关键属性过滤)
    // ============================================================================
    // 说明: Android 15 中 Java 层 SystemProperties 已被内联/native 化
    // 必须在 Native 层 Hook __system_property_get 才能捕获属性获取
    // 已添加 isKeyProperty() 过滤，只记录设备指纹相关的关键属性
    HOOK_SYMBOL(handle, __system_property_get);
    LOGE("✅ [Native Hook] __system_property_get 已启用 (仅监测关键属性)");

    // ============================================================================
    // ✅ 【已恢复】Android 15 设备指纹相关 Hook
    // ============================================================================

    // 网络收包是高频路径，默认不 Hook，避免 logcat 和函数跳板拖慢目标应用。
    // HOOK_SYMBOL(handle, recvfrom);
    // HOOK_SYMBOL(handle, recvmsg);
    // HOOK_SYMBOL(handle, recv);

    // 设备控制 - 硬件信息获取
    HOOK_SYMBOL(handle, ioctl);             // 设备控制接口

    // 内核信息获取 - Android 15 替代 /proc/version
    HOOK_SYMBOL(handle, uname);             // 内核版本信息

    // DRM 设备 ID - Android 15 重要指纹
    hookAMediaDrm();                        // MediaDrm 设备唯一 ID

//    HOOK_SYMBOL(handle, __statfs);          //和statfs() 相似 区别是不指向符号链接的文件或目录的信息
//    HOOK_SYMBOL(handle, __statfs64);        //同上
//    HOOK_SYMBOL(handle, fstatat64);





#else  //32位    4
    HOOK_SYMBOL(handle, __openat);
    HOOK_SYMBOL(handle, execve);
    HOOK_SYMBOL(handle, readlinkat);
    HOOK_SYMBOL(handle, access);

#endif
}

//替换原始路径中的指定长度字符
void IOReplace::substituteCharacter(vector<string> rootFile) {
    if (rootFile.empty()) {
        return;
    }
    for (int i = 0; i < rootFile.size(); ++i) {
        auto path = rootFile.at(i);                                  //原始路径
        string oldPath = path;
        auto ch2 = getLastChar(path.c_str());                   //获取最后一个 / 后面的所有字符   "/sbin"
        int len = strlen(ch2);
        int num = getLastnum(path.c_str());                                 //取出最后一个 '/' 所在的位置
        string str = generateRandomString(len);                         //生成 len 个随机字符
        string newPath = path.replace(num + 1, len, str);
        LOGE("原始路径: %s  --> 新的路径: %s", oldPath.c_str(), newPath.c_str());
        add_replace_item(oldPath.c_str(), newPath.c_str());
    }
    int count = get_replace_item_count();
    LOGE("-------->>io  路径初始化完毕 数量为: %d", count);


}

// 初始化需要隐藏的 路径列表
void IOReplace::initForbidPath(vector<string> forbidFile) {
    if (forbidFile.empty()) {
        return;
    }
    for (int i = 0; i < forbidFile.size(); ++i) {
        if (access(forbidFile.at(i).c_str(), F_OK) != -1) {
            add_forbidden_item(forbidFile.at(i).c_str());
        }
    }
}

//启动 io 重定向的入口 测试时先用64 位
void IOReplace::startUniformer(const char *so_path, const char *so_path_64, const char *native_path,
                               int api_level, int preview_api_level) {  // 参数信息补齐 挂载子进程时候需要使用

//    bool ret = ff_Recognizer::getFFR().init(getMagicPath());
//    LOGE("FFR path %s init %s", getMagicPath(), ret ? "success" : "fail");
    char api_level_chars[56];
    char pre_api_level_chars[56];
    setenv("V_SO_PATH", so_path, 1);
    setenv("V_SO_PATH_64", so_path_64, 1);
    sprintf(api_level_chars, "%i", api_level);
    setenv("V_API_LEVEL", api_level_chars, 1);
    sprintf(pre_api_level_chars, "%i", preview_api_level);
    setenv("V_PREVIEW_API_LEVEL", pre_api_level_chars, 1);
    setenv("V_API_LEVEL", api_level_chars, 1);
    setenv("V_NATIVE_PATH", native_path, 1);
    startIOHook(api_level);
}
