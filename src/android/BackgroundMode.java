package io.globules.cordova.plugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

import static android.content.Context.POWER_SERVICE;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
import static android.R.string.cancel;
import static android.R.string.ok;
import static android.R.style.Theme_DeviceDefault_Light_Dialog;

public class BackgroundMode extends CordovaPlugin {

    private static final String TAG = "BGDEBUG";  // For logcat
    private static final String JS_NAMESPACE = "cordova.plugins.backgroundMode";
    private static final String ACTION_UPDATE = "io.globules.cordova.plugin.backgroundMode.UPDATE_NOTIFICATION";

    private boolean isEnabled = false;
    private boolean isActive = false;
    private static JSONObject settings = new JSONObject();

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) {
        Log.d(TAG, "JS execute: " + action);
        switch (action) {
            case "enable":
                enable();
                callback.success();
                return true;
            case "disable":
                disable();
                callback.success();
                return true;
            case "configure":
                configure(args.optJSONObject(0), args.optBoolean(1, true));
                callback.success();
                return true;
            default:
                Log.e(TAG, "Unknown action: " + action);
                return false;
        }
    }

    // ————————————————————————————————————————
    // LIFECYCLE: These are the ONLY places we fire events
    // ————————————————————————————————————————
    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "onPause called — app backgrounding, isEnabled=" + isEnabled);
        if (isEnabled && !isActive) {
            try {
                startService();
                isActive = true;
                fireEvent("activate");
                Log.d(TAG, "onPause: Service started, activate fired");
            } catch (Exception e) {
                Log.e(TAG, "onPause crash: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        Log.d(TAG, "onResume called — app foregrounding, isActive=" + isActive);
        if (isEnabled && isActive) {
            try {
                stopService();
                isActive = false;
                fireEvent("deactivate");
                Log.d(TAG, "onResume: Service stopped, deactivate fired");
            } catch (Exception e) {
                Log.e(TAG, "onResume crash: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called — cleaning up");
        disable();
    }

    // ————————————————————————————————————————
    // PUBLIC API
    // ————————————————————————————————————————
    private void enable() {
        Log.d(TAG, "enable() called from JS");
        if (isEnabled) {
            Log.d(TAG, "enable(): Already enabled");
            return;
        }
        isEnabled = true;
        Log.d(TAG, "enable(): Enabled — running auto-survival");

        // Auto-survival actions — NO event firing here
        try {
            autoBatteryOpt();
            disableWebViewOptimizations();
            Log.d(TAG, "enable(): Auto-survival complete");
        } catch (Exception e) {
            Log.e(TAG, "enable() crash: " + e.getMessage(), e);
        }
        // activate event will fire in onPause()
    }

    private void disable() {
        Log.d(TAG, "disable() called");
        if (!isEnabled) {
            Log.d(TAG, "disable(): Not enabled");
            return;
        }
        isEnabled = false;
        try {
            stopService();
            isActive = false;
        } catch (Exception e) {
            Log.e(TAG, "disable() crash: " + e.getMessage(), e);
        }
    }

    private void configure(JSONObject newSettings, boolean updateNotification) {
        Log.d(TAG, "configure() called");
        try {
            if (settings.length() == 0) settings = new JSONObject();
            JSONArray keys = newSettings.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    settings.put(key, newSettings.get(key));
                }
            }
            Log.d(TAG, "configure(): Settings updated: " + settings.toString());
        } catch (Exception e) {
            Log.e(TAG, "configure() crash: " + e.getMessage(), e);
        }

        if (updateNotification && isActive) {
            try {
                Intent intent = new Intent(ACTION_UPDATE);
                intent.setPackage(cordova.getActivity().getPackageName());
                intent.putExtra("settings", settings.toString());
                cordova.getActivity().sendBroadcast(intent);
                Log.d(TAG, "configure(): Broadcast sent for update");
            } catch (Exception e) {
                Log.e(TAG, "configure() broadcast crash: " + e.getMessage(), e);
            }
        }
    }

    // ————————————————————————————————————————
    // AUTO-SURVIVAL
    // ————————————————————————————————————————
    private void autoBatteryOpt() {
        Log.d(TAG, "autoBatteryOpt() called");
        if (SDK_INT < M) {
            Log.d(TAG, "autoBatteryOpt(): API too low");
            return;
        }

        Activity a = cordova.getActivity();
        String pkg = a.getPackageName();
        PowerManager pm = (PowerManager) a.getSystemService(POWER_SERVICE);

        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            Log.d(TAG, "autoBatteryOpt(): Already ignored — opening OEM silently");
            openOemSettingsSilently();
            return;
        }

        Log.d(TAG, "autoBatteryOpt(): Requesting ignore");
        Intent i = new Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        i.setData(Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(i);
        openOemSettingsWithDialog();
    }

    private void disableWebViewOptimizations() {
        Log.d(TAG, "disableWebViewOptimizations() called");
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            cordova.getActivity().runOnUiThread(() -> {
                View view = webView.getEngine().getView();
                try {
                    Class.forName("org.crosswalk.engine.XWalkCordovaView")
                         .getMethod("onShow").invoke(view);
                    Log.d(TAG, "disableWebViewOptimizations(): Crosswalk fixed");
                } catch (Exception e) {
                    view.dispatchWindowVisibilityChanged(View.VISIBLE);
                    Log.d(TAG, "disableWebViewOptimizations(): Standard WebView fixed");
                }
            });
        }).start();
    }

    private void openOemSettingsWithDialog() {
        Log.d(TAG, "openOemSettingsWithDialog() called");
        Activity a = cordova.getActivity();
        PackageManager pm = a.getPackageManager();
        for (Intent i : getOemIntents()) {
            if (pm.resolveActivity(i, MATCH_DEFAULT_ONLY) != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                new AlertDialog.Builder(a, Theme_DeviceDefault_Light_Dialog)
                    .setMessage("Please enable Auto-start / Background running")
                    .setPositiveButton(ok, (d, w) -> a.startActivity(i))
                    .setNegativeButton(cancel, null)
                    .setCancelable(true)
                    .show();
                Log.d(TAG, "openOemSettingsWithDialog(): Dialog shown for " + i.getComponent().getClassName());
                return;
            }
        }
        Log.d(TAG, "openOemSettingsWithDialog(): No matching OEM intent");
    }

    private void openOemSettingsSilently() {
        Log.d(TAG, "openOemSettingsSilently() called");
        Activity a = cordova.getActivity();
        PackageManager pm = a.getPackageManager();
        for (Intent i : getOemIntents()) {
            if (pm.resolveActivity(i, MATCH_DEFAULT_ONLY) != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
                Log.d(TAG, "openOemSettingsSilently(): Opened " + i.getComponent().getClassName());
                break;
            }
        }
        Log.d(TAG, "openOemSettingsSilently(): No matching OEM intent");
    }

    private List<Intent> getOemIntents() {
        return Arrays.asList(
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.powerkeeper.ui.PowerUsageModelActivity")),
            new Intent().setComponent(new ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            new Intent().setComponent(new ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safe.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
        );
    }

    // ————————————————————————————————————————
    // SERVICE CONTROL (no events here)
    // ————————————————————————————————————————
    private void startService() {
        Log.d(TAG, "startService() called");
        if (isActive) {
            Log.d(TAG, "startService(): Already active");
            return;
        }
        try {
            Intent i = new Intent(cordova.getActivity(), ForegroundService.class);
            i.putExtra("settings", settings.toString());
            cordova.getContext().startService(i);
            isActive = true;
            Log.d(TAG, "startService(): Success — foreground started");
        } catch (Exception e) {
            Log.e(TAG, "startService() crash: " + e.getMessage(), e);
        }
    }

    private void stopService() {
        Log.d(TAG, "stopService() called");
        if (!isActive) {
            Log.d(TAG, "stopService(): Not active");
            return;
        }
        try {
            cordova.getContext().stopService(new Intent(cordova.getActivity(), ForegroundService.class));
            isActive = false;
            Log.d(TAG, "stopService(): Success — foreground stopped");
        } catch (Exception e) {
            Log.e(TAG, "stopService() crash: " + e.getMessage(), e);
        }
    }

    // ————————————————————————————————————————
    // EVENTS
    // ————————————————————————————————————————
    private void fireEvent(String event) {
        Log.d(TAG, "fireEvent: " + event);
        String js = JS_NAMESPACE + "._isActive=" + ("activate".equals(event) ? "true" : "false") +
                    "; " + JS_NAMESPACE + ".fireEvent('" + event + "');";
        webView.loadUrl("javascript:" + js);
    }

    public static JSONObject getSettings() {
        return settings.length() > 0 ? settings : new JSONObject();
    }
}