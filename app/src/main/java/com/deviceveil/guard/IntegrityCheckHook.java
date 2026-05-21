package com.deviceveil.guard;

import java.io.File;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Integrity/CRC monitor.
 *
 * This module intentionally defaults to monitoring instead of rewriting values.
 * Blindly changing CRC or digest results can break normal request signing,
 * resource loading, and anti-corruption checks. Once logs identify an exact
 * APK/DEX/SO check path, the rewrite switches below can be enabled narrowly.
 */
public class IntegrityCheckHook {
    private static final String TAG = "[设备信息记录]---[IntegrityCheckHook] ";

    private static final boolean ENABLE_CRC_REWRITE = false;
    private static final boolean ENABLE_DIGEST_REWRITE = false;
    private static final int STACK_DEPTH = 12;

    private static final Map<ZipFile, String> ZIP_PATHS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<WeakReference<ZipEntry>> LAST_ZIP_ENTRY =
            new ThreadLocal<>();

    public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        hookZipFile(lpparam);
        hookZipEntry(lpparam);
        hookCRC32(lpparam);
        hookMessageDigest(lpparam);
        XposedBridge.log(TAG + "Integrity/CRC hooks initialized");
    }

    private static void hookZipFile(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookConstructor(ZipFile.class, File.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ZIP_PATHS.put((ZipFile) param.thisObject, ((File) param.args[0]).getAbsolutePath());
                    logZipOpen((ZipFile) param.thisObject);
                }
            });

            XposedHelpers.findAndHookConstructor(ZipFile.class, File.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ZIP_PATHS.put((ZipFile) param.thisObject, ((File) param.args[0]).getAbsolutePath());
                    logZipOpen((ZipFile) param.thisObject);
                }
            });

            XposedHelpers.findAndHookConstructor(ZipFile.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ZIP_PATHS.put((ZipFile) param.thisObject, (String) param.args[0]);
                    logZipOpen((ZipFile) param.thisObject);
                }
            });

            XposedHelpers.findAndHookMethod(ZipFile.class, "getEntry", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ZipEntry entry = (ZipEntry) param.getResult();
                    if (entry != null) {
                        LAST_ZIP_ENTRY.set(new WeakReference<>(entry));
                        String zipPath = ZIP_PATHS.get((ZipFile) param.thisObject);
                        if (isInterestingZipPath(zipPath) || isInterestingEntry(entry.getName())) {
                            XposedBridge.log(TAG + "ZipFile.getEntry path=" + zipPath + ", entry=" + entry.getName());
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(ZipFile.class, "getInputStream", ZipEntry.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ZipEntry entry = (ZipEntry) param.args[0];
                    if (entry != null) {
                        LAST_ZIP_ENTRY.set(new WeakReference<>(entry));
                        String zipPath = ZIP_PATHS.get((ZipFile) param.thisObject);
                        if (isInterestingZipPath(zipPath) || isInterestingEntry(entry.getName())) {
                            XposedBridge.log(TAG + "ZipFile.getInputStream path=" + zipPath +
                                    ", entry=" + entry.getName());
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ZipFile hook failed: " + t);
        }
    }

    private static void hookZipEntry(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(ZipEntry.class, "getCrc", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ZipEntry entry = (ZipEntry) param.thisObject;
                    long crc = (Long) param.getResult();
                    if (isInterestingEntry(entry.getName())) {
                        XposedBridge.log(TAG + "ZipEntry.getCrc entry=" + entry.getName() +
                                ", crc=" + crc + "\n" + shortStack());
                        if (ENABLE_CRC_REWRITE) {
                            param.setResult(fakeCrcForName(entry.getName(), crc));
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(ZipEntry.class, "getSize", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ZipEntry entry = (ZipEntry) param.thisObject;
                    if (isInterestingEntry(entry.getName())) {
                        XposedBridge.log(TAG + "ZipEntry.getSize entry=" + entry.getName() +
                                ", size=" + param.getResult());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ZipEntry hook failed: " + t);
        }
    }

    private static void hookCRC32(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(CRC32.class, "getValue", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    long crc = (Long) param.getResult();
                    ZipEntry entry = null;
                    WeakReference<ZipEntry> ref = LAST_ZIP_ENTRY.get();
                    if (ref != null) {
                        entry = ref.get();
                    }
                    String entryName = entry != null ? entry.getName() : null;
                    if (entryName != null && isInterestingEntry(entryName)) {
                        XposedBridge.log(TAG + "CRC32.getValue entry=" + entryName +
                                ", crc=" + crc + "\n" + shortStack());
                        if (ENABLE_CRC_REWRITE) {
                            param.setResult(fakeCrcForName(entryName, crc));
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "CRC32 hook failed: " + t);
        }
    }

    private static void hookMessageDigest(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(MessageDigest.class, "digest", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    byte[] digest = (byte[]) param.getResult();
                    logDigest((MessageDigest) param.thisObject, digest);
                    if (ENABLE_DIGEST_REWRITE && isIntegrityStack()) {
                        param.setResult(stableFakeDigest(digest));
                    }
                }
            });

            XposedHelpers.findAndHookMethod(MessageDigest.class, "digest", byte[].class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    byte[] digest = (byte[]) param.getResult();
                    logDigest((MessageDigest) param.thisObject, digest);
                    if (ENABLE_DIGEST_REWRITE && isIntegrityStack()) {
                        param.setResult(stableFakeDigest(digest));
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "MessageDigest hook failed: " + t);
        }
    }

    private static void logZipOpen(ZipFile zipFile) {
        String path = ZIP_PATHS.get(zipFile);
        if (isInterestingZipPath(path)) {
            XposedBridge.log(TAG + "ZipFile opened: " + path + "\n" + shortStack());
        }
    }

    private static void logDigest(MessageDigest md, byte[] digest) {
        if (isKnownIdentifierSdkStack()) return;
        if (!isIntegrityStack()) return;
        XposedBridge.log(TAG + "MessageDigest.digest algorithm=" + md.getAlgorithm() +
                ", digest=" + hexPreview(digest, 32) + "\n" + shortStack());
    }

    private static boolean isInterestingZipPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".jar") ||
                lower.contains(Constants.TARGET_PACKAGE) || lower.contains("base.apk");
    }

    private static boolean isInterestingEntry(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".dex") || lower.endsWith(".so") || lower.startsWith("lib/") ||
                lower.startsWith("classes") || lower.startsWith("assets/") ||
                lower.equals("androidmanifest.xml");
    }

    private static boolean isIntegrityStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            String method = e.getMethodName();
            String lower = (cls + "." + method).toLowerCase();
            if (lower.contains("crc") || lower.contains("digest") || lower.contains("hash") ||
                    lower.contains("check") || lower.contains("verify") ||
                    lower.contains("integrity") || lower.contains("signature")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownIdentifierSdkStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (cls.startsWith("com.heytap.openid") ||
                    cls.startsWith("com.bun.miitmdid") ||
                    cls.startsWith("com.huawei.hms.ads.identifier") ||
                    cls.startsWith("com.miui.deviceid") ||
                    cls.startsWith("com.vivo.identifier")) {
                return true;
            }
        }
        return false;
    }

    private static long fakeCrcForName(String name, long original) {
        long value = 0x811C9DC5L;
        for (int i = 0; name != null && i < name.length(); i++) {
            value ^= name.charAt(i);
            value *= 0x01000193L;
            value &= 0xFFFFFFFFL;
        }
        return value == 0 ? original : value;
    }

    private static byte[] stableFakeDigest(byte[] original) {
        if (original == null) return null;
        byte[] out = original.clone();
        byte[] seed = HookInit.Android_id != null ? HookInit.Android_id.getBytes() : FakeData.FAKE_ANDROID_ID.getBytes();
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (out[i] ^ seed[i % seed.length] ^ 0x5A);
        }
        return out;
    }

    private static String hexPreview(byte[] bytes, int maxBytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        int count = Math.min(bytes.length, maxBytes);
        for (int i = 0; i < count; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        if (bytes.length > count) sb.append("...");
        return sb.toString();
    }

    private static String shortStack() {
        StringBuilder sb = new StringBuilder("stack:");
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int count = 0;
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (cls.startsWith("java.lang.Thread") ||
                    cls.startsWith("de.robv.android.xposed") ||
                    cls.startsWith("com.deviceveil.guard.IntegrityCheckHook")) {
                continue;
            }
            sb.append("\n  at ").append(cls).append(".").append(e.getMethodName())
                    .append("(").append(e.getFileName()).append(":").append(e.getLineNumber()).append(")");
            if (++count >= STACK_DEPTH) break;
        }
        return sb.toString();
    }
}
