package com.deviceveil.guard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ConfigProvider extends ContentProvider {
    public static final Uri CONFIG_URI = Uri.parse("content://"
            + Constants.CONFIG_PROVIDER_AUTHORITY + "/config");
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_VALUE = "value";

    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_INT = "int";
    private static final String TYPE_STRING = "string";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (!isConfigUri(uri) || !isCallerAllowed()) {
            return null;
        }

        SharedPreferences prefs = getContext().getSharedPreferences(
                ModuleConfig.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        MatrixCursor cursor = new MatrixCursor(new String[]{
                COLUMN_KEY,
                COLUMN_TYPE,
                COLUMN_VALUE
        });
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                cursor.addRow(new Object[]{entry.getKey(), TYPE_BOOLEAN, String.valueOf(value)});
            } else if (value instanceof Long) {
                cursor.addRow(new Object[]{entry.getKey(), TYPE_LONG, String.valueOf(value)});
            } else if (value instanceof Integer) {
                cursor.addRow(new Object[]{entry.getKey(), TYPE_INT, String.valueOf(value)});
            } else if (value != null) {
                cursor.addRow(new Object[]{entry.getKey(), TYPE_STRING, String.valueOf(value)});
            }
        }
        return cursor;
    }

    private boolean isConfigUri(Uri uri) {
        return uri != null
                && Constants.CONFIG_PROVIDER_AUTHORITY.equals(uri.getAuthority())
                && "/config".equals(uri.getPath());
    }

    private boolean isCallerAllowed() {
        if (getContext() == null) {
            return false;
        }
        int uid = android.os.Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) {
            return true;
        }
        PackageManager pm = getContext().getPackageManager();
        String[] callerPackages = pm.getPackagesForUid(uid);
        if (callerPackages == null || callerPackages.length == 0) {
            return false;
        }

        SharedPreferences prefs = getContext().getSharedPreferences(
                ModuleConfig.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String targetText = prefs.getString(ModuleConfig.KEY_TARGET_PACKAGES,
                Constants.DEFAULT_TARGET_PACKAGE);
        Set<String> targets = parseTargets(targetText);
        for (String packageName : callerPackages) {
            if (Constants.MODULE_PACKAGE.equals(packageName) || targets.contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseTargets(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String item : Arrays.asList(text.split("[,\\n\\r\\t ]+"))) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.deviceveil.config";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
