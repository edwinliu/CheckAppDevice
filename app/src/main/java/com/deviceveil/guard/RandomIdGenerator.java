package com.deviceveil.guard;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

public class RandomIdGenerator {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Random random = new Random();

    public static String generate16CharAndroidId() {
        byte[] randomBytes = new byte[8]; // 8 bytes = 64 bits → 16 hex chars
        secureRandom.nextBytes(randomBytes);
        return bytesToHex(randomBytes);
    }

    public static String generateUuidAndroidId() {
        return UUID.randomUUID().toString().toLowerCase();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String generateBootId(String seed) {
        try {
            // 使用 SHA-256 生成 256 位哈希
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes("UTF-8"));

            // 取前 16 字节构造 UUID
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xFF);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xFF);
            }

            // 设置 UUID v4 格式位
            // version = 4 (随机 UUID)
            msb = (msb & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
            // variant = 10xx (RFC 4122)
            lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

            return new UUID(msb, lsb).toString().toLowerCase();
        } catch (Exception e) {
            // 降级方案
            return UUID.randomUUID().toString().toLowerCase();
        }
    }


    public static long generateRandomTimestampInLast2Days() {
        long now = System.currentTimeMillis();
        long twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000);
        long randomTimestamp = twoDaysAgo + (long) (Math.random() * (now - twoDaysAgo));
        return randomTimestamp;
    }

    ;


    public static String generateRandomBluetoothAddress() {
        byte[] addr = new byte[6];

        // 第一个字节：确保 bit7=1, bit6=1 → 值范围 0xC0 (192) 到 0xFE (254)
        int firstByte = 0xC0 + random.nextInt(0x3F); // 0xC0 ~ 0xFE (63个值)
        addr[0] = (byte) firstByte;

        // 其余 5 个字节：完全随机
        for (int i = 1; i < 6; i++) {
            addr[i] = (byte) random.nextInt(256);
        }

        // 转换为 XX:XX:XX:XX:XX:XX 格式
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addr.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", addr[i] & 0xFF));
        }

        return sb.toString();
    }

    public static long generateRecentTimestampSeconds() {
        Random random = new Random();

        // 当前时间（秒）
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        // 3小时 = 3 * 60 * 60 = 10800 秒
        // 7小时 = 7 * 60 * 60 = 25200 秒
        long threeHoursAgo = currentTimeSeconds - (3 * 60 * 60);  // 3小时前
        long sevenHoursAgo = currentTimeSeconds - (7 * 60 * 60); // 7小时前

        // 在 [7小时前, 3小时前] 范围内生成随机时间戳
        long range = threeHoursAgo - sevenHoursAgo; // 时间范围（秒）
        long randomOffset = random.nextLong() % (range + 1);
        if (randomOffset < 0) {
            randomOffset = -randomOffset;
        }

        return sevenHoursAgo + randomOffset;
    }

    /**
     * 生成随机的 DRM ID (32 字节)
     * @return 32 字节的随机 DRM ID
     */
    public static byte[] generateRandomDrmId() {
        byte[] drmId = new byte[32];
        secureRandom.nextBytes(drmId);
        return drmId;
    }

    // 常见手机厂商的 OUI (Organizationally Unique Identifier) 前缀
    // 使用真实厂商 OUI 可以避免被检测为随机/伪造 MAC 地址
    private static final byte[][] VENDOR_OUI_LIST = {
            // 小米 (Xiaomi)
            {(byte) 0x28, (byte) 0x6C, (byte) 0x07},
            {(byte) 0x64, (byte) 0xCC, (byte) 0x2E},
            {(byte) 0x9C, (byte) 0x99, (byte) 0xA0},
            {(byte) 0x74, (byte) 0x23, (byte) 0x44},
            // 华为 (Huawei)
            {(byte) 0x48, (byte) 0xDB, (byte) 0x50},
            {(byte) 0x70, (byte) 0x8A, (byte) 0x09},
            {(byte) 0xDC, (byte) 0xD2, (byte) 0xFC},
            {(byte) 0x34, (byte) 0x12, (byte) 0x98},
            // OPPO
            {(byte) 0x18, (byte) 0xF0, (byte) 0xE4},
            {(byte) 0xA4, (byte) 0x50, (byte) 0x46},
            {(byte) 0x2C, (byte) 0x5B, (byte) 0xE1},
            // vivo
            {(byte) 0xBC, (byte) 0x1A, (byte) 0xE4},
            {(byte) 0xD0, (byte) 0xE3, (byte) 0x2C},
            {(byte) 0x98, (byte) 0x13, (byte) 0x62},
            // 三星 (Samsung)
            {(byte) 0x00, (byte) 0x26, (byte) 0x37},
            {(byte) 0x94, (byte) 0x35, (byte) 0x0A},
            {(byte) 0xA8, (byte) 0x7C, (byte) 0x01},
            // OnePlus
            {(byte) 0xC0, (byte) 0xEE, (byte) 0xFB},
            {(byte) 0x94, (byte) 0x65, (byte) 0x2D},
    };

    /**
     * 生成随机的 WiFi MAC 地址 (6 字节)
     * 使用真实厂商 OUI 前缀，避免被检测为随机/伪造地址
     * @return 6 字节的随机 MAC 地址
     */
    public static byte[] generateRandomWifiMac() {
        byte[] mac = new byte[6];
        // 随机选择一个厂商 OUI 作为前 3 字节
        byte[] oui = VENDOR_OUI_LIST[secureRandom.nextInt(VENDOR_OUI_LIST.length)];
        mac[0] = oui[0];
        mac[1] = oui[1];
        mac[2] = oui[2];
        // 后 3 字节随机生成
        byte[] suffix = new byte[3];
        secureRandom.nextBytes(suffix);
        mac[3] = suffix[0];
        mac[4] = suffix[1];
        mac[5] = suffix[2];
        return mac;
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 将十六进制字符串转换为字节数组
     */
    public static byte[] hexStringToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return null;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 将 MAC 字节数组转换为 XX:XX:XX:XX:XX:XX 格式
     */
    public static String macBytesToString(byte[] mac) {
        if (mac == null || mac.length != 6) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", mac[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 生成随机的 GAID (Google Advertising ID)
     * 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (UUID 格式)
     * @return UUID 格式的 GAID
     */
    public static String generateRandomGaid() {
        return UUID.randomUUID().toString().toLowerCase();
    }

    /**
     * 生成随机的 OAID (Open Anonymous Device Identifier)
     * 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (UUID 格式)
     * @return UUID 格式的 OAID
     */
    public static String generateRandomOaid() {
        return UUID.randomUUID().toString().toLowerCase();
    }

    // ==================== WiFi 信息随机生成 ====================

    // 常见的 WiFi SSID 前缀
    private static final String[] WIFI_SSID_PREFIXES = {
            "TP-Link_", "HUAWEI-", "Xiaomi_", "MERCURY_", "FAST_",
            "Tenda_", "ASUS_", "NETGEAR", "D-Link_", "ZTE_",
            "ChinaNet-", "CMCC-", "ChinaUnicom-", "Home_", "WiFi_"
    };

    /**
     * 生成随机的 WiFi SSID
     * 格式: "前缀+4位随机字符" (带引号，符合 Android WifiInfo.getSSID() 返回格式)
     */
    public static String generateRandomWifiSsid() {
        String prefix = WIFI_SSID_PREFIXES[random.nextInt(WIFI_SSID_PREFIXES.length)];
        String suffix = String.format("%04X", random.nextInt(0xFFFF));
        return "\"" + prefix + suffix + "\"";
    }

    /**
     * 生成随机的 WiFi BSSID (路由器 MAC 地址)
     * 使用真实路由器厂商 OUI 前缀，避免被检测为随机地址
     * 格式: xx:xx:xx:xx:xx:xx (小写)
     */
    // 常见路由器厂商 OUI 前缀
    private static final byte[][] ROUTER_OUI_LIST = {
            // TP-Link
            {(byte) 0x50, (byte) 0xC7, (byte) 0xBF},
            {(byte) 0xEC, (byte) 0x08, (byte) 0x6B},
            {(byte) 0x30, (byte) 0xB5, (byte) 0xC2},
            // Huawei
            {(byte) 0x48, (byte) 0xDB, (byte) 0x50},
            {(byte) 0x88, (byte) 0x66, (byte) 0x39},
            // Xiaomi
            {(byte) 0x28, (byte) 0x6C, (byte) 0x07},
            {(byte) 0x64, (byte) 0xCC, (byte) 0x2E},
            // Netgear
            {(byte) 0xB0, (byte) 0x7F, (byte) 0xB9},
            {(byte) 0xA4, (byte) 0x2B, (byte) 0x8C},
            // D-Link
            {(byte) 0xC8, (byte) 0xD3, (byte) 0xA3},
            // ASUS
            {(byte) 0x04, (byte) 0xD4, (byte) 0xC4},
            // ZTE
            {(byte) 0x54, (byte) 0x22, (byte) 0xF8},
            // Mercury (水星)
            {(byte) 0xC8, (byte) 0x3A, (byte) 0x35},
            // Tenda (腾达)
            {(byte) 0xC8, (byte) 0x3A, (byte) 0x35},
    };

    public static String generateRandomWifiBssid() {
        byte[] oui = ROUTER_OUI_LIST[secureRandom.nextInt(ROUTER_OUI_LIST.length)];
        // 设置为单播地址 (bit0 = 0)
        byte first = (byte) (oui[0] & 0xFE);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02x", first & 0xFF));
        sb.append(':').append(String.format("%02x", oui[1] & 0xFF));
        sb.append(':').append(String.format("%02x", oui[2] & 0xFF));
        for (int i = 0; i < 3; i++) {
            sb.append(':').append(String.format("%02x", secureRandom.nextInt(256)));
        }
        return sb.toString();
    }

    /**
     * 生成随机的 WiFi IP 地址 (小端序整数)
     * 生成 192.168.x.x 范围的私有 IP
     */
    public static int generateRandomWifiIpAddress() {
        // 192.168.x.x 格式，小端序: 0xXXXXA8C0
        int thirdOctet = 1 + random.nextInt(254);  // 1-254
        int fourthOctet = 2 + random.nextInt(253); // 2-254 (避免 .1 网关和 .255 广播)
        // 小端序: 192.168.x.y -> 0xYYXXA8C0
        return (fourthOctet << 24) | (thirdOctet << 16) | (168 << 8) | 192;
    }

    /**
     * 生成随机的 WiFi 信号强度 (RSSI)
     * 范围: -30 到 -70 dBm (正常信号范围)
     */
    public static int generateRandomWifiRssi() {
        return -30 - random.nextInt(41); // -30 到 -70
    }

    /**
     * 生成随机的 WiFi 链接速度 (Mbps)
     * 根据频率选择合理的速度值，保持关联性
     * @param frequency 当前 WiFi 频率 (MHz)，传 0 则独立随机
     */
    public static int generateRandomWifiLinkSpeed(int frequency) {
        if (frequency >= 5000) {
            // 5GHz 频段对应较高速度
            int[] speeds = {433, 866, 1200};
            return speeds[random.nextInt(speeds.length)];
        } else {
            // 2.4GHz 频段对应较低速度
            int[] speeds = {72, 150, 300};
            return speeds[random.nextInt(speeds.length)];
        }
    }

    /**
     * 生成随机的 WiFi 链接速度 (Mbps) — 无参版本，向后兼容
     * 常见值: 72, 150, 300, 433, 866, 1200
     */
    public static int generateRandomWifiLinkSpeed() {
        int[] speeds = {72, 150, 300, 433, 866, 1200};
        return speeds[random.nextInt(speeds.length)];
    }

    /**
     * 生成随机的 WiFi 频率 (MHz)
     * 2.4GHz: 2412-2484, 5GHz: 5180-5825
     */
    public static int generateRandomWifiFrequency() {
        if (random.nextBoolean()) {
            // 2.4GHz 频段 (信道 1-13)
            int[] channels24 = {2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462, 2467, 2472};
            return channels24[random.nextInt(channels24.length)];
        } else {
            // 5GHz 频段 (常用信道)
            int[] channels5 = {5180, 5200, 5220, 5240, 5260, 5280, 5300, 5320, 5500, 5520, 5540, 5560, 5580, 5600, 5620, 5640, 5660, 5680, 5700, 5745, 5765, 5785, 5805, 5825};
            return channels5[random.nextInt(channels5.length)];
        }
    }

    /**
     * 生成随机的 WiFi 网络 ID
     * 范围: 0-10 (已保存的网络数量)
     */
    public static int generateRandomWifiNetworkId() {
        return random.nextInt(5); // 0-4
    }

    // ==================== 电池信息随机生成 ====================

    /**
     * 生成随机的电池电量百分比
     * 范围: 25-95 (避免极端值)
     */
    public static int generateRandomBatteryCapacity() {
        return 25 + random.nextInt(71); // 25-95
    }

    /**
     * 生成随机的电池充电计数器 (微安时 μAh)
     * 基于电量百分比计算，假设电池容量 5000mAh
     */
    public static long generateRandomBatteryChargeCounter(int capacityPercent) {
        // 5000mAh = 5000000μAh，根据百分比计算
        long fullCapacity = 5000000L;
        return (fullCapacity * capacityPercent) / 100;
    }

    /**
     * 生成随机的电池当前电流 (微安 μA)
     * 负值表示放电，正值表示充电
     * @param isCharging 是否正在充电
     */
    public static long generateRandomBatteryCurrentNow(boolean isCharging) {
        if (isCharging) {
            // 充电电流: 500mA - 2000mA (正值)
            return 500000L + random.nextInt(1500001);
        } else {
            // 放电电流: -100mA 到 -500mA (负值)
            return -(100000L + random.nextInt(400001));
        }
    }

    /**
     * 生成随机的电池平均电流 (微安 μA)
     */
    public static long generateRandomBatteryCurrentAverage(boolean isCharging) {
        if (isCharging) {
            return 400000L + random.nextInt(1000001);
        } else {
            return -(80000L + random.nextInt(320001));
        }
    }

    /**
     * 生成随机的电池能量计数器 (纳瓦时 nWh)
     * 基于电量百分比计算
     */
    public static long generateRandomBatteryEnergyCounter(int capacityPercent) {
        // 假设电池 5000mAh * 3.8V = 19Wh = 19000000000nWh
        long fullEnergy = 19000000000L;
        return (fullEnergy * capacityPercent) / 100;
    }

    // ==================== 音频设置随机生成 ====================

    /**
     * 生成随机的铃声模式
     * 0: RINGER_MODE_SILENT (静音)
     * 1: RINGER_MODE_VIBRATE (振动)
     * 2: RINGER_MODE_NORMAL (正常)
     */
    public static int generateRandomRingerMode() {
        // 大多数情况下是正常模式
        int[] modes = {2, 2, 2, 1, 0}; // 60% 正常, 20% 振动, 20% 静音
        return modes[random.nextInt(modes.length)];
    }

    /**
     * 生成随机的音量值
     * @param maxVolume 最大音量
     */
    public static int generateRandomStreamVolume(int maxVolume) {
        // 生成 30%-80% 的音量
        int minVol = (int) (maxVolume * 0.3);
        int maxVol = (int) (maxVolume * 0.8);
        return minVol + random.nextInt(maxVol - minVol + 1);
    }

    /**
     * 生成随机的最大音量
     * 常见值: 15 (媒体), 7 (铃声), 5 (通话)
     */
    public static int generateRandomStreamMaxVolume() {
        int[] maxVolumes = {15, 15, 15, 7, 5}; // 大多数是媒体音量
        return maxVolumes[random.nextInt(maxVolumes.length)];
    }

    // ==================== 时区与语言随机生成 ====================

    // 常见的中国用户时区
    private static final String[] CHINA_TIMEZONE_IDS = {
            "Asia/Shanghai",    // 中国标准时间 (最常见)
            "Asia/Chongqing",   // 重庆
            "Asia/Harbin",      // 哈尔滨
            "Asia/Urumqi"       // 乌鲁木齐
    };

    // 常见的国际时区 (用于模拟不同地区用户)
    private static final String[][] INTERNATIONAL_TIMEZONES = {
            {"Asia/Shanghai", "28800000", "zh", "CN"},       // 中国
            {"Asia/Tokyo", "32400000", "ja", "JP"},          // 日本
            {"Asia/Seoul", "32400000", "ko", "KR"},          // 韩国
            {"Asia/Singapore", "28800000", "en", "SG"},      // 新加坡
            {"America/New_York", "-18000000", "en", "US"},   // 美国东部
            {"America/Los_Angeles", "-28800000", "en", "US"},// 美国西部
            {"Europe/London", "0", "en", "GB"},              // 英国
            {"Europe/Paris", "3600000", "fr", "FR"}          // 法国
    };

    /**
     * 生成随机的时区 ID
     * 默认返回中国时区
     */
    public static String generateRandomTimezoneId() {
        // 90% 概率返回 Asia/Shanghai，10% 概率返回其他中国时区
        if (random.nextInt(10) < 9) {
            return "Asia/Shanghai";
        }
        return CHINA_TIMEZONE_IDS[random.nextInt(CHINA_TIMEZONE_IDS.length)];
    }

    /**
     * 生成随机的时区偏移量 (毫秒)
     * @param timezoneId 时区 ID
     * @return UTC 偏移量 (毫秒)
     */
    public static int generateTimezoneRawOffset(String timezoneId) {
        // 根据时区 ID 返回对应的偏移量
        switch (timezoneId) {
            case "Asia/Shanghai":
            case "Asia/Chongqing":
            case "Asia/Harbin":
            case "Asia/Singapore":
                return 28800000;  // UTC+8
            case "Asia/Urumqi":
                return 21600000;  // UTC+6
            case "Asia/Tokyo":
            case "Asia/Seoul":
                return 32400000;  // UTC+9
            case "America/New_York":
                return -18000000; // UTC-5
            case "America/Los_Angeles":
                return -28800000; // UTC-8
            case "Europe/London":
                return 0;         // UTC+0
            case "Europe/Paris":
                return 3600000;   // UTC+1
            default:
                return 28800000;  // 默认 UTC+8
        }
    }

    /**
     * 生成随机的语言代码
     */
    public static String generateRandomLocaleLanguage() {
        // 默认返回中文
        return "zh";
    }

    /**
     * 生成随机的国家代码
     */
    public static String generateRandomLocaleCountry() {
        // 默认返回中国
        return "CN";
    }

    // ==================== Android 15 新 API ID 生成 ====================

    /**
     * 生成随机的 Firebase Installation ID (FID)
     * 格式: 22 字符的 Base64 编码字符串
     */
    public static String generateRandomFirebaseId() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        // 使用 URL-safe Base64 编码，去掉填充
        String base64 = android.util.Base64.encodeToString(randomBytes,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        // FID 通常是 22 字符
        return base64.substring(0, Math.min(22, base64.length()));
    }

    /**
     * 生成随机的 FCM Token (Firebase Cloud Messaging Token)
     * 格式: 约 163 字符的字符串，包含设备信息和项目信息
     */
    public static String generateRandomFcmToken() {
        // FCM Token 格式: {instance_id}:{random_token}
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String base64 = android.util.Base64.encodeToString(randomBytes,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        // 生成类似真实 FCM Token 的格式
        String instanceId = generateRandomFirebaseId();
        return instanceId + ":" + base64;
    }

}
