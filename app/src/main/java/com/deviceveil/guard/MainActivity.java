package com.deviceveil.guard;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private EditText processRulesInput;
    private EditText searchInput;
    private EditText targetPackageInput;
    private LinearLayout selectedAppsContainer;
    private LinearLayout appsContainer;
    private LinearLayout modulesContainer;
    private LinearLayout diagnosticsContainer;
    private SwitchMaterial stableModeSwitch;
    private final Set<String> selectedPackages = new LinkedHashSet<>();
    private final List<AppEntry> installedApps = new ArrayList<>();
    private final List<SwitchMaterial> moduleSwitches = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ModuleConfig.PREFS_NAME, MODE_PRIVATE);
        seedDefaults();
        loadInstalledApps();
        loadSelectedPackages();
        setContentView(buildContent());
        renderSelectedApps();
        renderApps("");
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = text("DeviceVeil", 26, true);
        root.addView(title);
        root.addView(text("通用设备指纹保护与 Hook 模块配置", 13, false));

        root.addView(section("作用目标"));
        root.addView(switchRow("启用模块", ModuleConfig.KEY_GLOBAL_ENABLED, true));

        root.addView(text("只能从已安装应用中选择目标，保存时写入应用包名。", 13, false));
        selectedAppsContainer = new LinearLayout(this);
        selectedAppsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(selectedAppsContainer);

        targetPackageInput = input("手动输入目标包名，例如 com.example.app");
        root.addView(targetPackageInput);
        Button addTargetButton = button("添加包名");
        addTargetButton.setOnClickListener(v -> addManualTargetPackage());
        root.addView(addTargetButton);

        processRulesInput = input("进程规则：*、:remote、!:push");
        processRulesInput.setSingleLine(false);
        processRulesInput.setMinLines(2);
        processRulesInput.setText(prefs.getString(ModuleConfig.KEY_PROCESS_RULES, "*"));
        root.addView(processRulesInput);

        root.addView(section("运行自检"));
        diagnosticsContainer = new LinearLayout(this);
        diagnosticsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(diagnosticsContainer);
        renderDiagnostics();

        root.addView(section("已安装应用"));
        searchInput = input("搜索包名或应用名称");
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleRenderApps(String.valueOf(s));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(searchInput);
        Button searchButton = button("搜索");
        searchButton.setOnClickListener(v -> renderApps(searchInput.getText().toString()));
        root.addView(searchButton);
        appsContainer = new LinearLayout(this);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsContainer);

        root.addView(section("Hook 覆盖"));
        root.addView(text("稳定模式是 UI 预设和状态标记；实际启用哪些 Hook 由下方每个模块开关决定。", 13, false));
        stableModeSwitch = (SwitchMaterial) switchRow("稳定模式", ModuleConfig.KEY_STABLE_MODE, true);
        root.addView(stableModeSwitch);
        root.addView(switchRow("记录敏感值到日志", ModuleConfig.KEY_LOG_SENSITIVE, false));
        root.addView(buttonRow("应用稳定预设", v -> setSafeModeModules(),
                "全部关闭 Hook", v -> setAllModulesEnabled(false)));
        modulesContainer = new LinearLayout(this);
        modulesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(modulesContainer);
        renderModuleGroups();

        Button save = button("保存配置");
        save.setOnClickListener(v -> saveConfig());
        root.addView(save);

        return scroll;
    }

    private void seedDefaults() {
        SharedPreferences.Editor editor = null;
        boolean changed = false;
        if (!prefs.contains(ModuleConfig.KEY_TARGET_PACKAGES)) {
            editor = prefs.edit();
            editor.putBoolean(ModuleConfig.KEY_GLOBAL_ENABLED, true);
            editor.putString(ModuleConfig.KEY_TARGET_PACKAGES, Constants.DEFAULT_TARGET_PACKAGE);
            editor.putString(ModuleConfig.KEY_PROCESS_RULES, "*");
            editor.putBoolean(ModuleConfig.KEY_LOG_SENSITIVE, false);
            editor.putBoolean(ModuleConfig.KEY_STABLE_MODE, true);
            for (String key : ModuleConfig.moduleLabels().keySet()) {
                editor.putBoolean(key, ModuleConfig.getDefaultModuleEnabled(key));
            }
            changed = true;
        }
        if (!prefs.getBoolean(ModuleConfig.KEY_HEAVY_MODULES_DISABLED_V1, false)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(ModuleConfig.MODULE_BINDER, false);
            editor.putBoolean(ModuleConfig.MODULE_INTEGRITY, false);
            editor.putBoolean(ModuleConfig.MODULE_BEHAVIOR, false);
            editor.putBoolean(ModuleConfig.MODULE_SEKIRO, false);
            editor.putBoolean(ModuleConfig.KEY_HEAVY_MODULES_DISABLED_V1, true);
            changed = true;
        }
        if (!prefs.getBoolean(ModuleConfig.KEY_NATIVE_DISABLED_V1, false)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(ModuleConfig.MODULE_NATIVE, false);
            editor.putBoolean(ModuleConfig.KEY_NATIVE_DISABLED_V1, true);
            changed = true;
        }
        if (!prefs.getBoolean(ModuleConfig.KEY_SAFE_MODE_V2, false)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            for (String key : ModuleConfig.moduleLabels().keySet()) {
                editor.putBoolean(key, ModuleConfig.getDefaultModuleEnabled(key));
            }
            editor.putBoolean(ModuleConfig.KEY_LOG_SENSITIVE, false);
            editor.putBoolean(ModuleConfig.KEY_STABLE_MODE, true);
            editor.putBoolean(ModuleConfig.KEY_SAFE_MODE_V2, true);
            changed = true;
        }
        if (changed && editor != null) {
            editor.commit();
            makePreferencesReadable();
        }
    }

    private void saveConfig() {
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, "请至少选择一个已安装应用。", Toast.LENGTH_LONG).show();
            return;
        }
        persistConfig(true);
    }

    private void persistConfig(boolean showToast) {
        String targets = String.join("\n", selectedPackages);
        String processRules = processRulesInput != null
                ? processRulesInput.getText().toString().trim()
                : prefs.getString(ModuleConfig.KEY_PROCESS_RULES, "*");
        prefs.edit()
                .putString(ModuleConfig.KEY_TARGET_PACKAGES, targets)
                .putString(ModuleConfig.KEY_PROCESS_RULES, processRules)
                .commit();
        ModuleConfig.invalidateCache();
        makePreferencesReadable();
        if (showToast) {
            Toast.makeText(this, "已保存。请强制停止目标应用后重新打开，使 Hook 配置生效。", Toast.LENGTH_LONG).show();
        }
        refreshStatusViews();
    }

    private void makePreferencesReadable() {
        File dataDir = new File(getApplicationInfo().dataDir);
        dataDir.setExecutable(true, false);
        chmod(dataDir, 0711);
        File sharedPrefsDir = new File(dataDir, "shared_prefs");
        if (sharedPrefsDir.exists()) {
            sharedPrefsDir.setExecutable(true, false);
            sharedPrefsDir.setReadable(true, false);
            chmod(sharedPrefsDir, 0755);
        }
        File prefsFile = getPrefsFile();
        if (prefsFile.exists()) {
            prefsFile.setReadable(true, false);
            prefsFile.setWritable(true, true);
            chmod(prefsFile, 0644);
            File parent = prefsFile.getParentFile();
            if (parent != null) {
                parent.setExecutable(true, false);
                parent.setReadable(true, false);
                chmod(parent, 0755);
            }
        }
    }

    private void chmod(File file, int mode) {
        if (file == null) return;
        try {
            Os.chmod(file.getAbsolutePath(), mode);
        } catch (Throwable ignored) {
        }
    }

    private void renderApps(String query) {
        if (appsContainer == null) return;
        appsContainer.removeAllViews();
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int shown = 0;
        for (AppEntry app : installedApps) {
            if (!needle.isEmpty() && !app.searchText.contains(needle)) {
                continue;
            }
            appsContainer.addView(appRow(app.label, app.packageName));
            if (++shown >= 60) {
                appsContainer.addView(text("仅显示前 60 个结果，请输入更精确的关键词继续筛选。", 12, false));
                break;
            }
        }
        if (shown == 0) {
            appsContainer.addView(text("没有找到匹配的已安装应用。请确认应用已安装，或尝试输入完整/部分包名。", 13, false));
        }
    }

    private void scheduleRenderApps(String query) {
        if (pendingSearch != null) {
            mainHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> renderApps(query);
        mainHandler.postDelayed(pendingSearch, 180);
    }

    private View appRow(String label, String packageName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView text = text(label + "\n" + packageName, 13, false);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        boolean selected = selectedPackages.contains(packageName);
        Button add = button(selected ? "移除" : "添加");
        add.setOnClickListener(v -> toggleTargetPackage(packageName));
        row.addView(add);
        return row;
    }

    private void toggleTargetPackage(String packageName) {
        if (selectedPackages.contains(packageName)) {
            selectedPackages.remove(packageName);
        } else if (isInstalledPackage(packageName)) {
            selectedPackages.add(packageName);
        }
        persistConfig(false);
        renderSelectedApps();
        renderApps(searchInput != null ? searchInput.getText().toString() : "");
    }

    private void addManualTargetPackage() {
        if (targetPackageInput == null) return;
        String packageName = targetPackageInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!isValidPackageName(packageName)) {
            Toast.makeText(this, "请输入有效包名。", Toast.LENGTH_LONG).show();
            return;
        }
        selectedPackages.add(packageName);
        targetPackageInput.setText("");
        persistConfig(false);
        renderSelectedApps();
        renderApps(searchInput != null ? searchInput.getText().toString() : "");
        Toast.makeText(this, "已添加目标包名，请强制停止目标应用后重新打开。", Toast.LENGTH_LONG).show();
    }

    private void loadSelectedPackages() {
        selectedPackages.clear();
        Set<String> installed = getInstalledPackageNames();
        Set<String> saved = ModuleConfig.fromContext(this).getTargetPackages();
        for (String packageName : saved) {
            if (installed.contains(packageName) || isValidPackageName(packageName)) {
                selectedPackages.add(packageName);
            }
        }
    }

    private void renderSelectedApps() {
        if (selectedAppsContainer == null) return;
        selectedAppsContainer.removeAllViews();
        if (selectedPackages.isEmpty()) {
            selectedAppsContainer.addView(text("尚未选择目标应用。请在下方已安装应用中搜索并添加。", 13, false));
            return;
        }

        PackageManager pm = getPackageManager();
        for (String packageName : new ArrayList<>(selectedPackages)) {
            String label = getAppLabel(pm, packageName);
            selectedAppsContainer.addView(selectedAppRow(label, packageName));
        }
    }

    private View selectedAppRow(String label, String packageName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView text = text(label + "\n" + packageName, 13, false);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button remove = button("移除");
        remove.setOnClickListener(v -> toggleTargetPackage(packageName));
        row.addView(remove);
        return row;
    }

    private Set<String> getInstalledPackageNames() {
        Set<String> out = new LinkedHashSet<>();
        for (AppEntry app : installedApps) {
            out.add(app.packageName);
        }
        return out;
    }

    private boolean isInstalledPackage(String packageName) {
        try {
            getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean isValidPackageName(String packageName) {
        return packageName != null
                && packageName.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
    }

    private String getAppLabel(PackageManager pm, String packageName) {
        try {
            ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
            return getAppLabel(pm, app);
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知应用";
        }
    }

    private String getAppLabel(PackageManager pm, ApplicationInfo app) {
        CharSequence label = app.loadLabel(pm);
        if (label == null || label.length() == 0) {
            label = app.nonLocalizedLabel;
        }
        if (label == null || label.length() == 0) {
            int labelRes = app.labelRes;
            if (labelRes != 0) {
                try {
                    label = pm.getText(app.packageName, labelRes, app);
                } catch (Throwable ignored) {
                }
            }
        }
        return label != null && label.length() > 0 ? String.valueOf(label) : app.packageName;
    }

    private void loadInstalledApps() {
        installedApps.clear();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            String label = getAppLabel(pm, app);
            installedApps.add(new AppEntry(label, app.packageName));
        }
        Collections.sort(installedApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final String searchText;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
            this.searchText = (label + " " + packageName).toLowerCase(Locale.ROOT);
        }
    }

    private View switchRow(String label, String key, boolean defaultValue) {
        SwitchMaterial sw = new SwitchMaterial(this);
        sw.setTag(key);
        sw.setText(label);
        sw.setTextSize(15);
        sw.setPadding(0, dp(6), 0, dp(6));
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).commit();
            ModuleConfig.invalidateCache();
            makePreferencesReadable();
            refreshStatusViews();
        });
        return sw;
    }

    private void renderModuleGroups() {
        moduleSwitches.clear();
        modulesContainer.removeAllViews();
        addModuleGroup("推荐保护（Java 修改 + SO/Native 基础修改）", ModuleConfig.recommendedModules());
        addModuleGroup("高级保护（指纹、隐私、反检测）", ModuleConfig.advancedModules());
        addModuleGroup("监控与调试（日志较多）", ModuleConfig.monitorModules());
    }

    private void addModuleGroup(String title, Map<String, String> modules) {
        modulesContainer.addView(section(title));
        for (Map.Entry<String, String> entry : modules.entrySet()) {
            SwitchMaterial sw = (SwitchMaterial) switchRow(entry.getValue(), entry.getKey(),
                    ModuleConfig.getDefaultModuleEnabled(entry.getKey()));
            moduleSwitches.add(sw);
            modulesContainer.addView(sw);
        }
    }

    private void setAllModulesEnabled(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : ModuleConfig.moduleLabels().keySet()) {
            editor.putBoolean(key, enabled);
        }
        editor.putBoolean(ModuleConfig.KEY_STABLE_MODE, false);
        editor.commit();
        for (SwitchMaterial sw : moduleSwitches) {
            sw.setChecked(enabled);
        }
        if (stableModeSwitch != null) {
            stableModeSwitch.setChecked(false);
        }
        makePreferencesReadable();
        refreshStatusViews();
    }

    private void setSafeModeModules() {
        Set<String> enabledModules = ModuleConfig.safeDefaultEnabledModules();
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : ModuleConfig.moduleLabels().keySet()) {
            editor.putBoolean(key, enabledModules.contains(key));
        }
        editor.putBoolean(ModuleConfig.KEY_LOG_SENSITIVE, false);
        editor.putBoolean(ModuleConfig.KEY_STABLE_MODE, true);
        editor.putBoolean(ModuleConfig.KEY_SAFE_MODE_V2, true);
        editor.commit();
        for (SwitchMaterial sw : moduleSwitches) {
            Object tag = sw.getTag();
            if (tag instanceof String) {
                sw.setChecked(enabledModules.contains((String) tag));
            }
        }
        if (stableModeSwitch != null) {
            stableModeSwitch.setChecked(true);
        }
        makePreferencesReadable();
        ModuleConfig.invalidateCache();
        refreshStatusViews();
        Toast.makeText(this, "已切换稳定模式。请强制停止目标应用后重新打开。", Toast.LENGTH_LONG).show();
    }

    private void refreshStatusViews() {
        renderDiagnostics();
    }

    private void renderDiagnostics() {
        if (diagnosticsContainer == null) return;
        diagnosticsContainer.removeAllViews();
        File prefsFile = getPrefsFile();
        diagnosticsContainer.addView(text("全局启用: " + yesNo(prefs.getBoolean(ModuleConfig.KEY_GLOBAL_ENABLED, true)), 13, false));
        diagnosticsContainer.addView(text("目标应用: " + (selectedPackages.isEmpty() ? "未选择" : String.join(", ", selectedPackages)), 13, false));
        diagnosticsContainer.addView(text("进程规则: " + prefs.getString(ModuleConfig.KEY_PROCESS_RULES, "*"), 13, false));
        diagnosticsContainer.addView(text("稳定模式: " + yesNo(prefs.getBoolean(ModuleConfig.KEY_STABLE_MODE, true)), 13, false));
        diagnosticsContainer.addView(text("配置文件: " + (prefsFile.exists() ? prefsFile.getAbsolutePath() : "未生成"), 13, false));
        diagnosticsContainer.addView(text("配置已设为全局可读: " + yesNo(prefsFile.exists() && prefsFile.canRead()), 13, false));
        diagnosticsContainer.addView(text("已启用 Hook: " + enabledModuleCount() + " / " + ModuleConfig.moduleLabels().size(), 13, false));
        diagnosticsContainer.addView(text("Native Hook: " + (prefs.getBoolean(ModuleConfig.MODULE_NATIVE,
                ModuleConfig.getDefaultModuleEnabled(ModuleConfig.MODULE_NATIVE)) ? "目标进程启动时加载" : "已关闭"), 13, false));
    }

    private int enabledModuleCount() {
        int count = 0;
        for (String key : ModuleConfig.moduleLabels().keySet()) {
            if (prefs.getBoolean(key, ModuleConfig.getDefaultModuleEnabled(key))) {
                count++;
            }
        }
        return count;
    }

    private File getPrefsFile() {
        return new File(getApplicationInfo().dataDir,
                "shared_prefs/" + ModuleConfig.PREFS_NAME + ".xml");
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private View buttonRow(String leftText, View.OnClickListener leftClick,
                           String rightText, View.OnClickListener rightClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        Button left = button(leftText);
        Button right = button(rightText);
        left.setOnClickListener(leftClick);
        right.setOnClickListener(rightClick);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private TextView section(String text) {
        TextView view = text(text, 18, true);
        view.setPadding(0, dp(20), 0, dp(8));
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setPadding(0, dp(8), 0, dp(8));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
