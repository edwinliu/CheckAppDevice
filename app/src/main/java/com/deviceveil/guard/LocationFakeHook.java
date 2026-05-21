package com.deviceveil.guard;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.Random;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 位置信息伪造 Hook 模块
 *
 * 功能:
 * 1. 伪造 GPS/网络位置信息，防止位置追踪
 * 2. Hook LocationManager.getLastKnownLocation() - 获取最后已知位置
 * 3. Hook LocationManager.requestLocationUpdates() - 位置更新回调
 * 4. Hook Location 对象的各种 getter 方法
 * 5. Hook FusedLocationProviderClient (Google Play Services)
 *
 * 位置信息:
 * - 纬度/经度: 伪造的坐标
 * - 海拔: 伪造的海拔高度
 * - 精度: 伪造的定位精度
 * - 速度/方向: 伪造的移动信息
 */
public class LocationFakeHook {

    private static final String TAG = "[设备信息记录]---[LocationFakeHook] ";
    private static final Random random = new Random();

    // 缓存伪造的 Location 对象（避免每次生成不一致）
    private static Location cachedFakeLocation = null;
    private static long lastLocationUpdateTime = 0;
    private static final long LOCATION_CACHE_DURATION = 5000; // 5秒缓存

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "开始初始化位置信息伪造 Hook");

        if (!FakeData.LOCATION_FAKE_ENABLED) {
            XposedBridge.log(TAG + "位置信息伪造已禁用，跳过初始化");
            return;
        }

        hookLocationManager(lpparam);
        hookLocation(lpparam);
        hookFusedLocationProvider(lpparam);
        hookGeocoder(lpparam);

        XposedBridge.log(TAG + "位置信息伪造 Hook 初始化完成");
    }

    // ==================== LocationManager Hook ====================
    private static void hookLocationManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> locationManagerClass = XposedHelpers.findClass(
                    "android.location.LocationManager", lpparam.classLoader);

            // Hook getLastKnownLocation(String provider)
            XposedHelpers.findAndHookMethod(locationManagerClass, "getLastKnownLocation",
                    String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String provider = (String) param.args[0];
                    Location origLocation = (Location) param.getResult();

                    XposedBridge.log(TAG + "getLastKnownLocation(\"" + provider + "\") 被调用");
                    if (origLocation != null) {
                        XposedBridge.log(TAG + "  原始位置: " + origLocation.getLatitude() + ", " + origLocation.getLongitude());
                    }

                    Location fakeLocation = createFakeLocation(provider);
                    param.setResult(fakeLocation);
                    XposedBridge.log(TAG + "  伪造位置: " + fakeLocation.getLatitude() + ", " + fakeLocation.getLongitude());
                }
            });

            // Hook requestLocationUpdates (多个重载)
            hookRequestLocationUpdates(locationManagerClass);

            // Hook requestSingleUpdate
            hookRequestSingleUpdate(locationManagerClass);

            // Hook getProviders()
            try {
                XposedHelpers.findAndHookMethod(locationManagerClass, "getProviders", boolean.class,
                        new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "getProviders() 被调用");
                    }
                });
            } catch (NoSuchMethodError e) {
                // 忽略
            }

            // Hook isProviderEnabled()
            try {
                XposedHelpers.findAndHookMethod(locationManagerClass, "isProviderEnabled",
                        String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String provider = (String) param.args[0];
                        XposedBridge.log(TAG + "isProviderEnabled(\"" + provider + "\") = " + param.getResult());
                        // 保持原值，仅记录
                    }
                });
            } catch (NoSuchMethodError e) {
                // 忽略
            }

            XposedBridge.log(TAG + "LocationManager Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "LocationManager Hook 失败: " + e.getMessage());
        }
    }

    private static void hookRequestLocationUpdates(Class<?> locationManagerClass) {
        // Hook requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener)
        try {
            XposedHelpers.findAndHookMethod(locationManagerClass, "requestLocationUpdates",
                    String.class, long.class, float.class, LocationListener.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String provider = (String) param.args[0];
                    LocationListener origListener = (LocationListener) param.args[3];

                    XposedBridge.log(TAG + "requestLocationUpdates(\"" + provider + "\", ...) 被调用");

                    // 包装 LocationListener，在回调时返回伪造位置
                    LocationListener wrappedListener = createWrappedListener(origListener, provider);
                    param.args[3] = wrappedListener;
                }
            });
        } catch (NoSuchMethodError e) {
            XposedBridge.log(TAG + "requestLocationUpdates(4参数) 方法不存在");
        }

        // Hook requestLocationUpdates with Looper
        try {
            XposedHelpers.findAndHookMethod(locationManagerClass, "requestLocationUpdates",
                    String.class, long.class, float.class, LocationListener.class, android.os.Looper.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String provider = (String) param.args[0];
                    LocationListener origListener = (LocationListener) param.args[3];

                    XposedBridge.log(TAG + "requestLocationUpdates(\"" + provider + "\", ..., Looper) 被调用");

                    LocationListener wrappedListener = createWrappedListener(origListener, provider);
                    param.args[3] = wrappedListener;
                }
            });
        } catch (NoSuchMethodError e) {
            // 忽略
        }
    }

    private static void hookRequestSingleUpdate(Class<?> locationManagerClass) {
        try {
            XposedHelpers.findAndHookMethod(locationManagerClass, "requestSingleUpdate",
                    String.class, LocationListener.class, android.os.Looper.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String provider = (String) param.args[0];
                    LocationListener origListener = (LocationListener) param.args[1];

                    XposedBridge.log(TAG + "requestSingleUpdate(\"" + provider + "\", ...) 被调用");

                    LocationListener wrappedListener = createWrappedListener(origListener, provider);
                    param.args[1] = wrappedListener;
                }
            });
        } catch (NoSuchMethodError e) {
            XposedBridge.log(TAG + "requestSingleUpdate 方法不存在");
        }
    }

    // ==================== Location 对象 Hook ====================
    private static void hookLocation(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> locationClass = XposedHelpers.findClass("android.location.Location", lpparam.classLoader);

            // Hook getLatitude()
            XposedHelpers.findAndHookMethod(locationClass, "getLatitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    double origLat = (double) param.getResult();
                    double fakeLat = getFakeLatitude();

                    // 只有当原始值不是伪造值时才修改（避免循环）
                    if (Math.abs(origLat - fakeLat) > 0.0001) {
                        XposedBridge.log(TAG + "Location.getLatitude() 原始: " + origLat + " -> 伪造: " + fakeLat);
                        param.setResult(fakeLat);
                    }
                }
            });

            // Hook getLongitude()
            XposedHelpers.findAndHookMethod(locationClass, "getLongitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    double origLng = (double) param.getResult();
                    double fakeLng = getFakeLongitude();

                    if (Math.abs(origLng - fakeLng) > 0.0001) {
                        XposedBridge.log(TAG + "Location.getLongitude() 原始: " + origLng + " -> 伪造: " + fakeLng);
                        param.setResult(fakeLng);
                    }
                }
            });

            // Hook getAltitude()
            XposedHelpers.findAndHookMethod(locationClass, "getAltitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(FakeData.FAKE_ALTITUDE);
                }
            });

            // Hook getAccuracy()
            XposedHelpers.findAndHookMethod(locationClass, "getAccuracy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(FakeData.FAKE_ACCURACY);
                }
            });

            // Hook getSpeed()
            XposedHelpers.findAndHookMethod(locationClass, "getSpeed", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(FakeData.FAKE_SPEED);
                }
            });

            // Hook getBearing()
            XposedHelpers.findAndHookMethod(locationClass, "getBearing", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(FakeData.FAKE_BEARING);
                }
            });

            XposedBridge.log(TAG + "Location Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Location Hook 失败: " + e.getMessage());
        }
    }

    // ==================== FusedLocationProviderClient Hook ====================
    private static void hookFusedLocationProvider(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Google Play Services FusedLocationProviderClient
        try {
            Class<?> fusedClientClass = XposedHelpers.findClass(
                    "com.google.android.gms.location.FusedLocationProviderClient", lpparam.classLoader);

            // Hook getLastLocation()
            try {
                XposedHelpers.findAndHookMethod(fusedClientClass, "getLastLocation", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "FusedLocationProviderClient.getLastLocation() 被调用");
                        // Task<Location> 的结果需要在回调中处理
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法签名可能不同
            }

            XposedBridge.log(TAG + "FusedLocationProviderClient Hook 成功");
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log(TAG + "FusedLocationProviderClient 类不存在（无 Google Play Services）");
        } catch (Exception e) {
            XposedBridge.log(TAG + "FusedLocationProviderClient Hook 失败: " + e.getMessage());
        }
    }

    // ==================== Geocoder Hook ====================
    private static void hookGeocoder(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> geocoderClass = XposedHelpers.findClass(
                    "android.location.Geocoder", lpparam.classLoader);

            // Hook getFromLocation() - 反向地理编码
            try {
                XposedHelpers.findAndHookMethod(geocoderClass, "getFromLocation",
                        double.class, double.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        double lat = (double) param.args[0];
                        double lng = (double) param.args[1];

                        XposedBridge.log(TAG + "Geocoder.getFromLocation(" + lat + ", " + lng + ") 被调用");

                        // 替换为伪造坐标
                        param.args[0] = getFakeLatitude();
                        param.args[1] = getFakeLongitude();
                    }
                });
            } catch (NoSuchMethodError e) {
                // 方法不存在
            }

            XposedBridge.log(TAG + "Geocoder Hook 成功");
        } catch (Exception e) {
            XposedBridge.log(TAG + "Geocoder Hook 失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建伪造的 Location 对象
     */
    private static Location createFakeLocation(String provider) {
        // 检查缓存
        long now = System.currentTimeMillis();
        if (cachedFakeLocation != null && (now - lastLocationUpdateTime) < LOCATION_CACHE_DURATION) {
            // 更新时间戳
            cachedFakeLocation.setTime(now);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                cachedFakeLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }
            return cachedFakeLocation;
        }

        Location location = new Location(provider != null ? provider : FakeData.FAKE_LOCATION_PROVIDER);
        location.setLatitude(getFakeLatitude());
        location.setLongitude(getFakeLongitude());
        location.setAltitude(FakeData.FAKE_ALTITUDE);
        location.setAccuracy(FakeData.FAKE_ACCURACY);
        location.setSpeed(FakeData.FAKE_SPEED);
        location.setBearing(FakeData.FAKE_BEARING);
        location.setTime(now);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        // 缓存
        cachedFakeLocation = location;
        lastLocationUpdateTime = now;

        return location;
    }

    /**
     * 获取伪造的纬度（带可选抖动）
     */
    private static double getFakeLatitude() {
        double lat = FakeData.FAKE_LATITUDE;
        if (FakeData.LOCATION_JITTER_ENABLED) {
            // 将米转换为度（大约 1 度 = 111km）
            double jitterDegrees = FakeData.LOCATION_JITTER_RADIUS / 111000.0;
            lat += (random.nextDouble() * 2 - 1) * jitterDegrees;
        }
        return lat;
    }

    /**
     * 获取伪造的经度（带可选抖动）
     */
    private static double getFakeLongitude() {
        double lng = FakeData.FAKE_LONGITUDE;
        if (FakeData.LOCATION_JITTER_ENABLED) {
            // 经度在不同纬度下 1 度对应的距离不同，需要根据纬度调整
            double latRad = Math.toRadians(FakeData.FAKE_LATITUDE);
            double metersPerDegree = 111000.0 * Math.cos(latRad);
            double jitterDegrees = FakeData.LOCATION_JITTER_RADIUS / metersPerDegree;
            lng += (random.nextDouble() * 2 - 1) * jitterDegrees;
        }
        return lng;
    }

    /**
     * 创建包装的 LocationListener
     */
    private static LocationListener createWrappedListener(final LocationListener origListener, final String provider) {
        return new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                XposedBridge.log(TAG + "onLocationChanged 原始: " +
                        (location != null ? location.getLatitude() + ", " + location.getLongitude() : "null"));

                Location fakeLocation = createFakeLocation(provider);
                XposedBridge.log(TAG + "onLocationChanged 伪造: " +
                        fakeLocation.getLatitude() + ", " + fakeLocation.getLongitude());

                origListener.onLocationChanged(fakeLocation);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onStatusChanged(String provider, int status, Bundle extras) {
                origListener.onStatusChanged(provider, status, extras);
            }

            @Override
            public void onProviderEnabled(String provider) {
                origListener.onProviderEnabled(provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                origListener.onProviderDisabled(provider);
            }
        };
    }
}
