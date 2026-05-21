package com.deviceveil.guard;


public class FakeData {
    // ==================== Android 15 非 root 隐私策略 ====================
    // 普通应用在 Android 10+ 已无法直接获取 IMEI/IMSI/硬件序列号等持久设备标识。
    // 默认返回系统限制值，避免伪造完整硬件 ID 造成更明显的指纹冲突。
    public static final boolean ANDROID15_NON_ROOT_PRIVACY_MODE = true;
    public static final String RESTRICTED_TELEPHONY_ID = null;
    public static final String RESTRICTED_BUILD_SERIAL = "unknown";
    public static final String RESTRICTED_WIFI_MAC = "02:00:00:00:00:00";
    public static final byte[] RESTRICTED_MAC_BYTES = new byte[]{(byte) 0x02, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final String RESTRICTED_WIFI_SSID = "<unknown ssid>";
    public static final int RESTRICTED_WIFI_NETWORK_ID = -1;

    // ==================== 基础设备标识 ====================
    public static final String FAKE_IMEI = RESTRICTED_TELEPHONY_ID;
    public static final String FAKE_MEID = RESTRICTED_TELEPHONY_ID;
    public static final String FAKE_IMSI = RESTRICTED_TELEPHONY_ID;
    public static final String FAKE_SIM_SERIAL = RESTRICTED_TELEPHONY_ID;
    public static final String FAKE_PHONE_NUMBER = "";  // 空字符串表示无手机号
    public static final String FAKE_ANDROID_ID = "a1b2c3d4e5f67890";
    public static final String FAKE_SERIAL = RESTRICTED_BUILD_SERIAL;
    public static final String FAKE_BLUETOOTH_MAC = "C0:11:22:33:44:55";
    public static final long FAKE_INSTALL_TIME = System.currentTimeMillis() / 1000L;
    public static final String FAKE_WIFI_MAC = RESTRICTED_WIFI_MAC;
    public static final byte[] FAKE_NETWORK_MAC_BYTES = RESTRICTED_MAC_BYTES;
    public static final String FAKE_SD_STATE = "mounted";
    public static final String FAKE_SCRIBE_INSTALLATION_ID = "a1b2c3d4-e5f6-0718-293a-4b5c6d7e8f90";
    public static final String FAKE_MOCA_INSTALLATION_ID = "5f8b4c2a-9d1e-4a6b-8c3d-7e9f0a1b2c3d";
    public static final String FAKE_UUID_INSTALLATION_ID = "5f8b4c2a-9d1e-4a6b-8c3d-7e9f0a1b2c3d";
    public static final String FAKE_DEVICE_UUID_ID = "b2c3d4e5-f6a7-4819-9a0b-1c2d3e4f5a6b";
    public static final String FAKE_MOCA_ODS = "ods-" + FAKE_MOCA_INSTALLATION_ID;
    public static final String FAKE_MOCA_ODS1 = "ods1-" + FAKE_MOCA_INSTALLATION_ID;
    public static final String FAKE_APPSFLYER_UID = "1706054400000-1234567890123456789";
    public static final String FAKE_LEANPLUM_DEVICE_ID = "lp_" + FAKE_ANDROID_ID;
    public static final String FAKE_ICCID = "8986001234567890123";
    public static final String FAKE_STORAGE_UUID = "0123-4567";
    public static final String FAKE_EXTERNAL_STORAGE_UUID = "0123-4567";
    public static final long FAKE_USER_SERIAL = 0L;
    public static final int FAKE_DISPLAY_REFRESH_RATE_HZ = 60;
    // boot_id: UUID 格式，每次系统启动时生成，用于标识本次启动会话
    public static final String FAKE_BOOT_ID = "a1b2c3d4-e5f6-0718-293a-4b5c6d7e8f90";

    // ==================== DRM 设备唯一标识 ====================
    // DRM ID 通常是 32 字节，用于 MediaDrm.PROPERTY_DEVICE_UNIQUE_ID
    public static final byte[] FAKE_DRM_ID = new byte[]{
            (byte) 0xA1, (byte) 0xB2, (byte) 0xC3, (byte) 0xD4,
            (byte) 0xE5, (byte) 0xF6, (byte) 0x07, (byte) 0x18,
            (byte) 0x29, (byte) 0x3A, (byte) 0x4B, (byte) 0x5C,
            (byte) 0x6D, (byte) 0x7E, (byte) 0x8F, (byte) 0x90,
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
            (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0,
            (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
            (byte) 0x55, (byte) 0x66, (byte) 0x77, (byte) 0x88
    };
    // DRM ID 的十六进制字符串表示（用于日志）
    // 与 FAKE_DRM_ID 字节数组保持一致
    public static final String FAKE_DRM_ID_HEX = "A1B2C3D4E5F60718293A4B5C6D7E8F90123456789ABCDEF01122334455667788";

    // ==================== 10. WiFi 信息 ====================
    public static final String WIFI_SSID = RESTRICTED_WIFI_SSID;
    public static final String WIFI_BSSID = RESTRICTED_WIFI_MAC;
    public static final String WIFI_MAC_ADDRESS = RESTRICTED_WIFI_MAC;
    public static final int WIFI_IP_ADDRESS = 0x8F03A8C0; // 192.168.3.143 小端序
    public static final int WIFI_RSSI = -39;
    public static final int WIFI_LINK_SPEED = 156;
    public static final int WIFI_FREQUENCY = 2412;
    public static final int WIFI_NETWORK_ID = RESTRICTED_WIFI_NETWORK_ID;

    // ==================== 11. 电池信息 ====================
    public static final int BATTERY_CAPACITY = 85;
    public static final long BATTERY_CHARGE_COUNTER = 4250000L;
    public static final long BATTERY_CURRENT_NOW = -150000L;
    public static final long BATTERY_CURRENT_AVERAGE = -120000L;
    public static final long BATTERY_ENERGY_COUNTER = 15300000000L;

    // ==================== 12. 系统设置 ====================
    public static final int AIRPLANE_MODE = 0;
    public static final int WIFI_ON = 1;
    public static final int BLUETOOTH_ON = 1;
    public static final int ADB_ENABLED = 0;
    public static final int DEVELOPMENT_SETTINGS_ENABLED = 0;
    public static final int INSTALL_NON_MARKET_APPS = 0;
    public static final int SCREEN_BRIGHTNESS = 125;
    public static final int SCREEN_BRIGHTNESS_MODE = 1;
    public static final int ACCESSIBILITY_ENABLED = 0;
    public static final int LOCATION_MODE = 3;

    // ==================== 13. 音频设置 ====================
    public static final int RINGER_MODE = 2;
    public static final int STREAM_VOLUME = 7;
    public static final int STREAM_MAX_VOLUME = 15;

    // ==================== 14. TelephonyManager (无 SIM 卡) ====================
    public static final int SIM_STATE = 1;
    public static final int PHONE_TYPE = 0;
    public static final int NETWORK_TYPE = 0;
    public static final int DATA_STATE = 0;
    public static final int CALL_STATE = 0;

    // ==================== 15. 网络状态 ====================
    public static final int NETWORK_INFO_TYPE = 1;
    public static final String NETWORK_TYPE_NAME = "WIFI";
    public static final boolean NETWORK_CONNECTED = true;
    public static final boolean NETWORK_AVAILABLE = true;
    public static final boolean NETWORK_ROAMING = false;

    // ==================== 16. 安全状态 ====================
    public static final boolean KEYGUARD_LOCKED = false;
    public static final boolean DEVICE_SECURE = true;
    public static final boolean IS_USER_A_MONKEY = false;

    // ==================== 17. 时区与语言 ====================
    public static final String TIMEZONE_ID = "Asia/Shanghai";
    public static final int TIMEZONE_RAW_OFFSET = 28800000; // UTC+8 (8小时 = 28800000毫秒)
    public static final String LOCALE_LANGUAGE = "zh";
    public static final String LOCALE_COUNTRY = "CN";
    public static final String LOCALE_DISPLAY_NAME = "中文 (中国)";

    // ==================== 18. 文件元信息伪造配置 (stat/lstat/fstat) ====================
    /**
     * 是否启用文件元信息伪造
     * 开启后会伪造非应用目录的文件时间戳、大小、权限
     */
    public static final boolean FAKE_STAT_ENABLED = true;

    /**
     * 伪造文件时间戳的基准时间 (Unix 时间戳，秒)
     * 0 表示使用开机时间作为基准
     * 建议设置为一个合理的过去时间，如设备出厂时间
     */
    public static final long FAKE_FILE_TIME_BASE = 0;  // 0 表示使用开机时间

    /**
     * 伪造文件大小的偏移范围 (字节)
     * 实际偏移将基于路径哈希在 -offset ~ +offset 范围内确定性生成
     * 0 表示不伪造文件大小
     */
    public static final long FAKE_FILE_SIZE_OFFSET = 1024;  // ±1KB

    /**
     * 伪造文件权限掩码 (八进制)
     * 如 0644 表示 rw-r--r--
     * 如 0755 表示 rwxr-xr-x
     * 0 表示不伪造文件权限
     */
    public static final int FAKE_FILE_MODE_MASK = 0;  // 0 表示不伪造权限

    /**
     * 需要伪造 stat 信息的自定义路径前缀列表
     * 这些路径下的文件会被伪造元信息
     * 除此之外，Native 层还有默认路径列表:
     * - /data, /data/user, /data/user/0, /data/data
     * - /storage, /storage/emulated, /storage/emulated/0
     * - /system, /system/build.prop
     * - /vendor, /proc
     */
    public static final String[] FAKE_STAT_PATHS = {
            // 可添加自定义路径，例如:
            // "/sdcard",
            // "/mnt/sdcard",
    };

    // ==================== 19. WebView 指纹伪造 ====================
    /**
     * 是否启用 WebView UA 伪造
     * 注意: 如果设为 true，请确保 FAKE_USER_AGENT 中的设备信息与真实设备一致
     *       否则会与系统层暴露的真实设备信息产生矛盾，更容易被检测
     */
    public static final boolean WEBVIEW_UA_FAKE_ENABLED = false;

    /**
     * 伪造的 User-Agent
     * 注意: 当前禁用 UA 伪造，因为包含 Samsung 设备信息会与 OnePlus 真实设备产生矛盾
     * 如需启用，请修改为与真实设备匹配的 UA
     */
    public static final String FAKE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-G9910 Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36";

    /**
     * 伪造的 WebView 版本
     */
    public static final String FAKE_WEBVIEW_VERSION = "120.0.6099.230";

    // ==================== 20. Canvas 指纹保护 ====================
    /**
     * 是否启用 Canvas 噪声注入
     * 开启后会对 Canvas 绑定的 Bitmap 添加细微噪声
     */
    public static final boolean CANVAS_NOISE_ENABLED = true;

    /**
     * Canvas 噪声强度 (0-255)
     * 值越大噪声越明显，建议 1-5
     */
    public static final int CANVAS_NOISE_LEVEL = 2;

    // ==================== 21. 剪贴板保护 ====================
    /**
     * 是否启用剪贴板保护
     * 开启后会阻止应用读取剪贴板内容
     */
    public static final boolean CLIPBOARD_PROTECTION_ENABLED = true;

    /**
     * 剪贴板保护模式:
     * 0 - 返回空内容
     * 1 - 返回伪造内容
     * 2 - 返回 null (模拟无权限)
     */
    public static final int CLIPBOARD_PROTECTION_MODE = 0;

    /**
     * 伪造的剪贴板内容 (当 mode=1 时使用)
     */
    public static final String FAKE_CLIPBOARD_TEXT = "";

    // ==================== 22. Bootloader/系统状态伪造 ====================
    /**
     * 伪造的 Bootloader 解锁状态
     * false = 已锁定 (正常设备)
     * true = 已解锁 (开发/Root 设备)
     */
    public static final boolean FAKE_BOOTLOADER_UNLOCKED = false;

    /**
     * 伪造的验证启动状态
     * "green" = 正常启动, "yellow" = 自定义 OS, "orange" = 解锁, "red" = 损坏
     */
    public static final String FAKE_VERIFIED_BOOT_STATE = "green";

    /**
     * 伪造的安全补丁级别
     */
    public static final String FAKE_SECURITY_PATCH = "2024-12-01";

    /**
     * 伪造的 Build 指纹
     * 格式: brand/product/device:version/id/incremental:type/tags
     */
    public static final String FAKE_BUILD_FINGERPRINT = "samsung/p3sxxx/p3s:14/UP1A.231005.007/G9910ZCU5FXK1:user/release-keys";

    // ==================== 23. 账户信息伪造 ====================
    /**
     * 是否启用账户信息隐藏
     * 开启后会返回空的账户列表
     */
    public static final boolean HIDE_ACCOUNTS_ENABLED = true;

    /**
     * 是否伪造 Google 账户
     * 如果为 true，会返回伪造的 Google 账户
     */
    public static final boolean FAKE_GOOGLE_ACCOUNT_ENABLED = false;

    /**
     * 伪造的 Google 账户邮箱
     */
    public static final String FAKE_GOOGLE_ACCOUNT_EMAIL = "";

    // ==================== 24. 应用使用统计伪造 ====================
    /**
     * 是否启用应用使用统计伪造
     */
    public static final boolean USAGE_STATS_FAKE_ENABLED = true;

    /**
     * 返回空的使用统计
     */
    public static final boolean USAGE_STATS_RETURN_EMPTY = true;

    // ==================== 25. 音频指纹伪造 ====================
    /**
     * 伪造的音频输出采样率 (Hz)
     */
    public static final int FAKE_AUDIO_OUTPUT_SAMPLE_RATE = 48000;

    /**
     * 伪造的音频输出帧数
     */
    public static final int FAKE_AUDIO_OUTPUT_FRAMES_PER_BUFFER = 192;

    // ==================== 26. GPU 信息伪造 ====================
    /**
     * 是否启用 GPU 信息伪造
     */
    public static final boolean GPU_INFO_FAKE_ENABLED = false;

    /**
     * 伪造的 GPU 厂商 (GL_VENDOR)
     */
    public static final String FAKE_GL_VENDOR = "Qualcomm";

    /**
     * 伪造的 GPU 渲染器 (GL_RENDERER)
     */
    public static final String FAKE_GL_RENDERER = "Adreno (TM) 740";

    /**
     * 伪造的 OpenGL ES 版本 (GL_VERSION)
     */
    public static final String FAKE_GL_VERSION = "OpenGL ES 3.2 V@0675.37 (GIT@c2e2156ca4, I0c82f50779, 1693449096) (Date:08/31/23)";

    /**
     * 伪造的 OpenGL 扩展 (GL_EXTENSIONS) - 简化版本
     * 完整扩展列表太长，这里只提供常见的扩展
     */
    public static final String FAKE_GL_EXTENSIONS = "GL_OES_EGL_image GL_OES_EGL_image_external GL_OES_EGL_sync GL_OES_vertex_half_float GL_OES_framebuffer_object GL_OES_rgb8_rgba8 GL_OES_compressed_ETC1_RGB8_texture GL_AMD_compressed_ATC_texture GL_KHR_texture_compression_astc_ldr GL_OES_texture_compression_astc GL_OES_texture_3D GL_EXT_color_buffer_float GL_EXT_color_buffer_half_float GL_QCOM_tiled_rendering GL_OES_depth_texture";

    // ==================== 27. 字体列表伪造 ====================
    /**
     * 是否启用字体列表伪造
     */
    public static final boolean FONT_LIST_FAKE_ENABLED = true;

    /**
     * 伪造的系统字体列表
     * 返回标准 Android 字体，隐藏自定义字体
     */
    public static final String[] FAKE_FONT_LIST = {
            "sans-serif",
            "sans-serif-light",
            "sans-serif-condensed",
            "sans-serif-black",
            "sans-serif-thin",
            "sans-serif-medium",
            "serif",
            "monospace",
            "serif-monospace",
            "casual",
            "cursive",
            "sans-serif-smallcaps"
    };

    /**
     * 伪造的字体文件列表 (系统字体目录下)
     */
    public static final String[] FAKE_FONT_FILES = {
            "Roboto-Regular.ttf",
            "Roboto-Bold.ttf",
            "Roboto-Italic.ttf",
            "Roboto-BoldItalic.ttf",
            "Roboto-Light.ttf",
            "Roboto-LightItalic.ttf",
            "Roboto-Medium.ttf",
            "Roboto-MediumItalic.ttf",
            "Roboto-Thin.ttf",
            "Roboto-ThinItalic.ttf",
            "Roboto-Black.ttf",
            "Roboto-BlackItalic.ttf",
            "RobotoCondensed-Regular.ttf",
            "RobotoCondensed-Bold.ttf",
            "RobotoCondensed-Italic.ttf",
            "RobotoCondensed-BoldItalic.ttf",
            "NotoSerif-Regular.ttf",
            "NotoSerif-Bold.ttf",
            "NotoSerif-Italic.ttf",
            "NotoSerif-BoldItalic.ttf",
            "DroidSansMono.ttf"
    };

    // ==================== 28. 位置信息伪造 ====================
    /**
     * 是否启用位置信息伪造
     */
    public static final boolean LOCATION_FAKE_ENABLED = true;

    /**
     * 伪造的纬度 (默认: 上海)
     */
    public static final double FAKE_LATITUDE = 31.2304;

    /**
     * 伪造的经度 (默认: 上海)
     */
    public static final double FAKE_LONGITUDE = 121.4737;

    /**
     * 伪造的海拔 (米)
     */
    public static final double FAKE_ALTITUDE = 4.0;

    /**
     * 伪造的精度 (米)
     */
    public static final float FAKE_ACCURACY = 20.0f;

    /**
     * 伪造的速度 (米/秒)
     */
    public static final float FAKE_SPEED = 0.0f;

    /**
     * 伪造的方向 (度)
     */
    public static final float FAKE_BEARING = 0.0f;

    /**
     * 位置提供者
     */
    public static final String FAKE_LOCATION_PROVIDER = "gps";

    /**
     * 是否启用位置随机抖动 (更真实)
     * 开启后会在基准坐标附近随机偏移
     */
    public static final boolean LOCATION_JITTER_ENABLED = true;

    /**
     * 位置抖动范围 (米)
     */
    public static final float LOCATION_JITTER_RADIUS = 50.0f;

    // ==================== 29. 行为指纹防护 ====================
    /**
     * 是否启用传感器数据伪造
     * 对加速度计、陀螺仪、磁力计等传感器数据添加轻微噪声
     */
    public static final boolean SENSOR_DATA_FAKE_ENABLED = true;

    /**
     * 是否启用触摸事件伪造
     * 对触摸压力、大小、时间戳添加随机偏移
     */
    public static final boolean TOUCH_EVENT_FAKE_ENABLED = true;

    /**
     * 是否启用输入行为伪造
     * 对按键时间戳添加随机偏移
     */
    public static final boolean INPUT_EVENT_FAKE_ENABLED = true;

    /**
     * 加速度计噪声幅度 (m/s²)
     * 建议值: 0.01-0.05，轻度噪声不影响正常使用
     */
    public static final float SENSOR_NOISE_ACCELEROMETER = 0.02f;

    /**
     * 陀螺仪噪声幅度 (rad/s)
     * 建议值: 0.001-0.01
     */
    public static final float SENSOR_NOISE_GYROSCOPE = 0.005f;

    /**
     * 磁力计噪声幅度 (μT)
     * 建议值: 0.1-0.5
     */
    public static final float SENSOR_NOISE_MAGNETIC = 0.2f;

    /**
     * 触摸压力噪声幅度 (0-1 范围内的偏移)
     * 建议值: 0.01-0.05
     */
    public static final float TOUCH_PRESSURE_NOISE = 0.02f;

    /**
     * 触摸大小噪声幅度
     * 建议值: 0.005-0.02
     */
    public static final float TOUCH_SIZE_NOISE = 0.01f;

    /**
     * 触摸时间戳噪声幅度 (毫秒)
     * 建议值: 1-5ms
     */
    public static final float TOUCH_TIME_NOISE_MS = 2.0f;

    /**
     * 输入事件时间戳噪声幅度 (毫秒)
     * 建议值: 1-5ms
     */
    public static final float INPUT_TIME_NOISE_MS = 3.0f;

    /**
     * 触摸坐标噪声幅度 (像素)
     * 亚像素级别的微小抖动，模拟真实人手的不稳定性
     * 建议值: 0.5-2.0 像素
     */
    public static final float TOUCH_COORDINATE_NOISE = 1.0f;

    /**
     * 光感传感器噪声幅度 (lux)
     * 模拟环境光的自然波动
     * 建议值: 5-20 lux
     */
    public static final float SENSOR_NOISE_LIGHT = 10.0f;

    /**
     * 接近传感器噪声幅度 (cm)
     * 建议值: 0.1-0.5
     */
    public static final float SENSOR_NOISE_PROXIMITY = 0.2f;

    /**
     * 是否启用 WebView JS 层防护
     * 防止通过 navigator.webdriver 等 JS API 检测自动化工具
     */
    public static final boolean WEBVIEW_JS_PROTECTION_ENABLED = true;
}
