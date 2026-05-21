package com.deviceveil.guard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

public final class MonitorEventStore {
    public static final String ACTION_MONITOR_EVENT = "com.deviceveil.guard.MONITOR_EVENT";
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_PROCESS = "process";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_DETAIL = "detail";
    public static final String EXTRA_TIME = "time";

    private static final String PREFS = "monitor_events";
    private static final String KEY_RECENT = "recent_events";
    private static final int MAX_RECENT = 120;
    private static final int MAX_TEXT = 240;

    private MonitorEventStore() {
    }

    public static void send(Context context, String target, String process, String type, String name, String detail) {
        if (context == null) return;
        Intent intent = new Intent(ACTION_MONITOR_EVENT);
        intent.setClassName(Constants.MODULE_PACKAGE, Constants.MODULE_PACKAGE + ".MonitorEventReceiver");
        intent.putExtra(EXTRA_TARGET, safe(target));
        intent.putExtra(EXTRA_PROCESS, safe(process));
        intent.putExtra(EXTRA_TYPE, safe(type));
        intent.putExtra(EXTRA_NAME, truncate(name));
        intent.putExtra(EXTRA_DETAIL, truncate(detail));
        intent.putExtra(EXTRA_TIME, System.currentTimeMillis());
        context.sendBroadcast(intent);
    }

    public static synchronized void record(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_MONITOR_EVENT.equals(intent.getAction())) return;
        String target = safe(intent.getStringExtra(EXTRA_TARGET));
        String type = safe(intent.getStringExtra(EXTRA_TYPE));
        String name = truncate(intent.getStringExtra(EXTRA_NAME));
        String detail = truncate(intent.getStringExtra(EXTRA_DETAIL));
        String process = safe(intent.getStringExtra(EXTRA_PROCESS));
        long time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis());
        if (target.isEmpty() || type.isEmpty() || name.isEmpty()) return;
        if (!ModuleConfig.fromContext(context).getTargetPackages().contains(target)) return;

        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(countKey(target, type), prefs.getInt(countKey(target, type), 0) + 1);
        editor.putInt(countKey(target, type, name), prefs.getInt(countKey(target, type, name), 0) + 1);
        editor.putString(KEY_RECENT, appendRecent(prefs.getString(KEY_RECENT, "[]"),
                target, process, type, name, detail, time));
        editor.apply();
    }

    public static synchronized void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static synchronized String buildSummary(Context context, Set<String> targets) {
        SharedPreferences prefs = prefs(context);
        StringBuilder out = new StringBuilder();
        if (targets == null || targets.isEmpty()) {
            out.append("尚未选择目标应用。\n");
        } else {
            for (String target : targets) {
                out.append(target).append('\n');
                out.append("  设备 API: ").append(prefs.getInt(countKey(target, "api"), 0)).append('\n');
                out.append("  文件读取/检测: ").append(prefs.getInt(countKey(target, "file"), 0)).append('\n');
                out.append("  命令执行: ").append(prefs.getInt(countKey(target, "command"), 0)).append('\n');
                out.append("  系统属性: ").append(prefs.getInt(countKey(target, "property"), 0)).append('\n');
            }
        }
        out.append('\n').append("最近事件:\n");
        out.append(formatRecent(prefs.getString(KEY_RECENT, "[]"), targets));
        return out.toString();
    }

    private static String appendRecent(String json, String target, String process, String type,
                                       String name, String detail, long time) {
        JSONArray oldArray = parseArray(json);
        JSONArray newArray = new JSONArray();
        try {
            JSONObject event = new JSONObject();
            event.put(EXTRA_TIME, time);
            event.put(EXTRA_TARGET, target);
            event.put(EXTRA_PROCESS, process);
            event.put(EXTRA_TYPE, type);
            event.put(EXTRA_NAME, name);
            event.put(EXTRA_DETAIL, detail);
            newArray.put(event);
            for (int i = 0; i < oldArray.length() && newArray.length() < MAX_RECENT; i++) {
                newArray.put(oldArray.getJSONObject(i));
            }
        } catch (Exception ignored) {
        }
        return newArray.toString();
    }

    private static String formatRecent(String json, Set<String> targets) {
        JSONArray array = parseArray(json);
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < array.length() && shown < 40; i++) {
            try {
                JSONObject event = array.getJSONObject(i);
                String target = event.optString(EXTRA_TARGET);
                if (targets != null && !targets.isEmpty() && !targets.contains(target)) {
                    continue;
                }
                out.append(formatTime(event.optLong(EXTRA_TIME)))
                        .append(" [").append(event.optString(EXTRA_TYPE)).append("] ")
                        .append(event.optString(EXTRA_NAME));
                String detail = event.optString(EXTRA_DETAIL);
                if (!detail.isEmpty()) {
                    out.append(" -> ").append(detail);
                }
                String process = event.optString(EXTRA_PROCESS);
                if (!process.isEmpty()) {
                    out.append(" (").append(process).append(")");
                }
                out.append('\n');
                shown++;
            } catch (Exception ignored) {
            }
        }
        if (shown == 0) {
            out.append("暂无记录。启动目标应用并触发设备读取/文件检测/命令执行后会显示在这里。\n");
        }
        return out.toString();
    }

    private static JSONArray parseArray(String json) {
        try {
            return new JSONArray(json == null ? "[]" : json);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String countKey(String target, String type) {
        return "count|" + target + "|" + type;
    }

    private static String countKey(String target, String type, String name) {
        return "count|" + target + "|" + type + "|" + name;
    }

    private static String formatTime(long millis) {
        return android.text.format.DateFormat.format("HH:mm:ss", millis).toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT) + "...";
    }
}
