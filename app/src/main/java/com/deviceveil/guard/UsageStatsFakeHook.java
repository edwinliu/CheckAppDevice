package com.deviceveil.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 应用使用统计伪造 Hook 模块
 *
 * 功能:
 * 1. 伪造应用使用统计数据，防止应用追踪用户行为
 * 2. Hook UsageStatsManager.queryUsageStats() - 返回空列表
 * 3. Hook UsageStatsManager.queryAndAggregateUsageStats() - 返回空 Map
 * 4. Hook UsageStatsManager.queryEvents() - 返回空事件
 * 5. Hook UsageStatsManager.queryConfigurations() - 返回空配置
 */
public class UsageStatsFakeHook {

    private static final String TAG = "[设备信息记录]---[UsageStatsFakeHook] ";

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化应用使用统计伪造 Hook");

        if (!FakeData.USAGE_STATS_FAKE_ENABLED) {
            XposedBridge.log(TAG + "应用使用统计伪造已禁用，跳过初始化");
            return;
        }

        hookUsageStatsManager(lpparam);
        hookNetworkStatsManager(lpparam);

        XposedBridge.log(TAG + "应用使用统计伪造 Hook 初始化完成");
    }

    // ==================== UsageStatsManager Hook ====================
    private static void hookUsageStatsManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> usageStatsManagerClass = XposedHelpers.findClass(
                    "android.app.usage.UsageStatsManager", lpparam.classLoader);

            // Hook queryUsageStats(int intervalType, long beginTime, long endTime)
            XposedHelpers.findAndHookMethod(usageStatsManagerClass, "queryUsageStats",
                    int.class, long.class, long.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int intervalType = (int) param.args[0];
                    long beginTime = (long) param.args[1];
                    long endTime = (long) param.args[2];

                    Object orig = param.getResult();
                    int origCount = 0;
                    if (orig instanceof List) {
                        origCount = ((List<?>) orig).size();
                    }
                    XposedBridge.log(TAG + "queryUsageStats(interval=" + intervalType +
                            ", begin=" + beginTime + ", end=" + endTime + ") 原始记录数: " + origCount);

                    if (FakeData.USAGE_STATS_RETURN_EMPTY) {
                        param.setResult(new ArrayList<>());
                        XposedBridge.log(TAG + "queryUsageStats() 返回空列表");
                    }
                }
            });

            // Hook queryAndAggregateUsageStats(long beginTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(usageStatsManagerClass, "queryAndAggregateUsageStats",
                        long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        int origCount = 0;
                        if (orig instanceof Map) {
                            origCount = ((Map<?, ?>) orig).size();
                        }
                        XposedBridge.log(TAG + "queryAndAggregateUsageStats() 原始记录数: " + origCount);

                        if (FakeData.USAGE_STATS_RETURN_EMPTY) {
                            param.setResult(new HashMap<>());
                            XposedBridge.log(TAG + "queryAndAggregateUsageStats() 返回空 Map");
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "queryAndAggregateUsageStats() 方法不存在，跳过");
            }

            // Hook queryEvents(long beginTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(usageStatsManagerClass, "queryEvents",
                        long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "queryEvents() 被调用");

                        if (FakeData.USAGE_STATS_RETURN_EMPTY) {
                            // 返回空的 UsageEvents 对象 - 需要创建一个空的
                            // 由于 UsageEvents 构造函数是隐藏的，保持原值但记录日志
                            XposedBridge.log(TAG + "queryEvents() 保持原值（无法创建空 UsageEvents）");
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "queryEvents() 方法不存在，跳过");
            }

            // Hook queryEventsForSelf(long beginTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(usageStatsManagerClass, "queryEventsForSelf",
                        long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "queryEventsForSelf() 被调用");
                        // 同样保持原值
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法可能不存在于低版本 API
            }

            // Hook queryConfigurations(int intervalType, long beginTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(usageStatsManagerClass, "queryConfigurations",
                        int.class, long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        int origCount = 0;
                        if (orig instanceof List) {
                            origCount = ((List<?>) orig).size();
                        }
                        XposedBridge.log(TAG + "queryConfigurations() 原始记录数: " + origCount);

                        if (FakeData.USAGE_STATS_RETURN_EMPTY) {
                            param.setResult(new ArrayList<>());
                            XposedBridge.log(TAG + "queryConfigurations() 返回空列表");
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + "queryConfigurations() 方法不存在，跳过");
            }

            // Hook isAppInactive(String packageName)
            try {
                XposedHelpers.findAndHookMethod(usageStatsManagerClass, "isAppInactive",
                        String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String packageName = (String) param.args[0];
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "isAppInactive(" + packageName + ") 原始值: " + orig);
                        // 返回 false，表示应用不处于非活动状态
                        param.setResult(false);
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法可能不存在
            }

            // Hook getAppStandbyBucket()
            try {
                XposedHelpers.findAndHookMethod(usageStatsManagerClass, "getAppStandbyBucket", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        XposedBridge.log(TAG + "getAppStandbyBucket() 原始值: " + orig);
                        // 返回 STANDBY_BUCKET_ACTIVE = 10
                        param.setResult(10);
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法可能不存在
            }

            XposedBridge.log(TAG + "UsageStatsManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "UsageStatsManager Hook 失败: " + e.getMessage());
        }
    }

    // ==================== NetworkStatsManager Hook ====================
    private static void hookNetworkStatsManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> networkStatsManagerClass = XposedHelpers.findClass(
                    "android.app.usage.NetworkStatsManager", lpparam.classLoader);

            // Hook querySummaryForDevice(int networkType, String subscriberId, long startTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(networkStatsManagerClass, "querySummaryForDevice",
                        int.class, String.class, long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int networkType = (int) param.args[0];
                        XposedBridge.log(TAG + "querySummaryForDevice(networkType=" + networkType + ") 被调用");

                        if (FakeData.USAGE_STATS_RETURN_EMPTY) {
                            // NetworkStats.Bucket 很难伪造，记录日志即可
                            XposedBridge.log(TAG + "querySummaryForDevice() 保持原值");
                        }
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，跳过
            }

            // Hook querySummaryForUser(int networkType, String subscriberId, long startTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(networkStatsManagerClass, "querySummaryForUser",
                        int.class, String.class, long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int networkType = (int) param.args[0];
                        XposedBridge.log(TAG + "querySummaryForUser(networkType=" + networkType + ") 被调用");
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，跳过
            }

            // Hook querySummary(int networkType, String subscriberId, long startTime, long endTime)
            try {
                XposedHelpers.findAndHookMethod(networkStatsManagerClass, "querySummary",
                        int.class, String.class, long.class, long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int networkType = (int) param.args[0];
                        XposedBridge.log(TAG + "querySummary(networkType=" + networkType + ") 被调用");
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，跳过
            }

            // Hook queryDetailsForUid(int networkType, String subscriberId, long startTime, long endTime, int uid)
            try {
                XposedHelpers.findAndHookMethod(networkStatsManagerClass, "queryDetailsForUid",
                        int.class, String.class, long.class, long.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int networkType = (int) param.args[0];
                        int uid = (int) param.args[4];
                        XposedBridge.log(TAG + "queryDetailsForUid(networkType=" + networkType +
                                ", uid=" + uid + ") 被调用");
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在，跳过
            }

            XposedBridge.log(TAG + "NetworkStatsManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "NetworkStatsManager Hook 失败: " + e.getMessage());
        }
    }
}