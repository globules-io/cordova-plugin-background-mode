package de.appplant.cordova.plugin.background;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
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

    private static final String JS_NAMESPACE = "cordova.plugins.backgroundMode";
    private static final String ACTION_UPDATE = "de.appplant.cordova.plugin.background.UPDATE_NOTIFICATION";

    private boolean isEnabled = false;
    private boolean isActive = false;
    private static JSONObject settings = new JSONObject();

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) {
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
                return false;
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        if (isEnabled) startService();
    }

    @Override
    public void onResume(boolean multitasking) {
        if (isEnabled) stopService();
    }

    @Override
    public void onDestroy() {
        stopService();
    }

    // ——————————————————————————————————————————————————————
    //  PUBLIC API
    // ——————————————————————————————————————————————————————

    private void enable() {
        if (isEnabled) return;
        isEnabled = true;

        autoBatteryOpt();                    // ← opens Xiaomi/Samsung settings
        disableWebViewOptimizations();       // ← keeps WebView alive
        startService();                      // ← starts foreground service
        fireEvent("activate");
    }

    private void disable() {
        if (!isEnabled) return;
        isEnabled = false;
        stopService();
        fireEvent("deactivate");
    }

    private void configure(JSONObject newSettings, boolean updateNotification) {
        try {
            if (settings.length() == 0) settings = new JSONObject();
            JSONArray keys = newSettings.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    settings.put(key, newSettings.get(key));
                }
            }
        } catch (Exception ignored) {}

        if (updateNotification && isActive) {
            Intent intent = new Intent(ACTION_UPDATE);
            intent.setPackage(cordova.getActivity().getPackageName());
            intent.putExtra("settings", settings.toString());
            cordova.getActivity().sendBroadcast(intent);
        }
    }

    // ——————————————————————————————————————————————————————
    //  AUTO-SURVIVAL (everything happens automatically)
    // ——————————————————————————————————————————————————————

    private void autoBatteryOpt() {
        if (SDK_INT < M) return;

        Activity activity = cordova.getActivity();
        String pkg = activity.getPackageName();
        PowerManager pm = (PowerManager) activity.getSystemService(POWER_SERVICE);

        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            openOemSettingsSilently();
            return;
        }

        Intent i = new Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        i.setData(Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);

        openOemSettingsWithDialog();
    }

    private void disableWebViewOptimizations() {
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            cordova.getActivity().runOnUiThread(() -> {
                View view = webView.getEngine().getView();
                try {
                    Class.forName("org.crosswalk.engine.XWalkCordovaView")
                         .getMethod("onShow").invoke(view);
                } catch (Exception ignored) {
                    view.dispatchWindowVisibilityChanged(View.VISIBLE);
                }
            });
        }).start();
    }

    private void openOemSettingsWithDialog() {
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
                return;
            }
        }
    }

    private void openOemSettingsSilently() {
        Activity a = cordova.getActivity();
        PackageManager pm = a.getPackageManager();
        for (Intent i : getOemIntents()) {
            if (pm.resolveActivity(i, MATCH_DEFAULT_ONLY) != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
                break;
            }
        }
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

    // ——————————————————————————————————————————————————————
    //  Service control
    // ——————————————————————————————————————————————————————

    private void startService() {
        if (isActive) return;
        Intent i = new Intent(cordova.getActivity(), ForegroundService.class);
        i.putExtra("settings", settings.toString());
        cordova.getContext().startService(i);
        isActive = true;
    }

    private void stopService() {
        if (!isActive) return;
        cordova.getContext().stopService(new Intent(cordova.getActivity(), ForegroundService.class));
        isActive = false;
    }

    private void fireEvent(String event) {
        String js = JS_NAMESPACE + "._isActive=" + ("activate".equals(event) ? "true" : "false") +
                    "; " + JS_NAMESPACE + ".fireEvent('" + event + "');";
        webView.loadUrl("javascript:" + js);
    }

    public static JSONObject getSettings() {
        return settings.length() > 0 ? settings : new JSONObject();
    }
}