package de.appplant.cordova.plugin.background;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
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
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

import static android.R.string.cancel;
import static android.R.string.ok;
import static android.R.style.Theme_DeviceDefault_Light_Dialog;
import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.POWER_SERVICE;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
import static android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

public class BackgroundModeExt extends CordovaPlugin {

     private PowerManager.WakeLock wakeLock;

     @Override
     public boolean execute(String action, JSONArray args, CallbackContext callback) {
          switch (action) {
               case "battery":
                    disableBatteryOptimizations();
                    break;
               case "autoBatteryOpt":
                    autoBatteryOpt();
                    break;
               case "webview":
                    disableWebViewOptimizations();
                    break;
               case "appstart":
                    openAppStart(args.opt(0));
                    break;
               case "background":
                    moveToBackground();
                    break;
               case "foreground":
                    moveToForeground();
                    break;
               case "tasklist":
                    excludeFromTaskList();
                    break;
               case "dimmed":
                    isDimmed(callback);
                    break;
               case "wakeup":
                    wakeup();
                    break;
               case "unlock":
                    wakeup();
                    unlock();
                    break;
               default:
                    callback.error("Invalid action: " + action);
                    return false;
          }
          callback.success();
          return true;
     }

     private void moveToBackground() {
          Intent intent = new Intent(Intent.ACTION_MAIN);
          intent.addCategory(Intent.CATEGORY_HOME);
          getApp().startActivity(intent);
     }

     private void moveToForeground() {
          Activity app = getApp();
          Intent intent = getLaunchIntent();
          intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
          clearScreenAndKeyguardFlags();
          app.startActivity(intent);
     }

     // FIXED: This was completely broken before
     private void disableWebViewOptimizations() {
          new Thread(() -> {
               try {
                    Thread.sleep(1000);
                    getApp().runOnUiThread(() -> {
                         View view = webView.getEngine().getView();
                         try {
                              // Crosswalk (very old) support
                              Class.forName("org.crosswalk.engine.XWalkCordovaView")
                                        .getMethod("onShow")
                                        .invoke(view);
                         } catch (Exception e) {
                              // Standard Cordova WebView fallback
                              view.dispatchWindowVisibilityChanged(View.VISIBLE);
                         }
                    });
               } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
               }
          }).start();
     }

     // Backward compatibility
     private void disableBatteryOptimizations() {
          autoBatteryOpt();
     }

     @SuppressLint("BatteryLife")
     private void autoBatteryOpt() {
          Activity activity = cordova.getActivity();
          String pkgName = activity.getPackageName();
          PowerManager pm = (PowerManager) getService(POWER_SERVICE);

          if (SDK_INT < M)
               return;

          if (pm.isIgnoringBatteryOptimizations(pkgName)) {
               openAppStartSilently();
               return;
          }

          Intent intent = new Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
          intent.setData(Uri.parse("package:" + pkgName));
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          activity.startActivity(intent);

          try {
               JSONObject config = new JSONObject();
               config.put("text", "Please enable Auto-start & Background running");
               openAppStart(config);
          } catch (Exception ignored) {
               openAppStart(null);
          }
     }

     private void openAppStart(Object arg) {
          if (arg instanceof Boolean && (Boolean) arg) {
               openAppStartSilently();
               return;
          }

          Activity activity = cordova.getActivity();
          PackageManager pm = activity.getPackageManager();
          JSONObject spec = (arg instanceof JSONObject) ? (JSONObject) arg : null;

          for (Intent intent : getAppStartIntents()) {
               if (pm.resolveActivity(intent, MATCH_DEFAULT_ONLY) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    AlertDialog.Builder dialog = new AlertDialog.Builder(activity, Theme_DeviceDefault_Light_Dialog);
                    dialog.setPositiveButton(ok, (d, w) -> activity.startActivity(intent));
                    dialog.setNegativeButton(cancel, (d, w) -> {
                    });
                    dialog.setCancelable(true);

                    if (spec != null && spec.has("title")) {
                         dialog.setTitle(spec.optString("title"));
                    }
                    if (spec != null && spec.has("text")) {
                         dialog.setMessage(spec.optString("text"));
                    } else {
                         dialog.setMessage("Allow this app to run in background?");
                    }

                    activity.runOnUiThread(dialog::show);
                    return;
               }
          }
     }

     private void openAppStartSilently() {
          Activity activity = cordova.getActivity();
          PackageManager pm = activity.getPackageManager();

          for (Intent intent : getAppStartIntents()) {
               if (pm.resolveActivity(intent, MATCH_DEFAULT_ONLY) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                    break;
               }
          }
     }

     @TargetApi(Build.VERSION_CODES.LOLLIPOP)
     private void excludeFromTaskList() {
          ActivityManager am = (ActivityManager) getService(ACTIVITY_SERVICE);
          if (am == null || SDK_INT < 21)
               return;

          List<ActivityManager.AppTask> tasks = am.getAppTasks();
          if (tasks == null || tasks.isEmpty())
               return;

          tasks.get(0).setExcludeFromRecents(true);
     }

     @SuppressWarnings("deprecation")
     private void isDimmed(CallbackContext callback) {
          PowerManager pm = (PowerManager) getService(POWER_SERVICE);
          boolean dimmed = (SDK_INT < 20) ? !pm.isScreenOn() : !pm.isInteractive();
          callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, dimmed));
     }

     private void wakeup() {
          acquireWakeLock();
     }

     private void unlock() {
          addSreenAndKeyguardFlags();
          getApp().startActivity(getLaunchIntent());
     }

     @SuppressWarnings("deprecation")
     private void acquireWakeLock() {
          PowerManager pm = (PowerManager) getService(POWER_SERVICE);
          releaseWakeLock();

          boolean screenOn = (SDK_INT < 20) ? pm.isScreenOn() : pm.isInteractive();
          if (screenOn)
               return;

          wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "backgroundmode:wakelock");

          wakeLock.acquire(1000); // 1-second pulse
     }

     private void releaseWakeLock() {
          if (wakeLock != null && wakeLock.isHeld()) {
               wakeLock.release();
               wakeLock = null;
          }
     }

     private void addSreenAndKeyguardFlags() {
          getApp().runOnUiThread(() -> getApp().getWindow().addFlags(
                    FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                              FLAG_SHOW_WHEN_LOCKED |
                              FLAG_TURN_SCREEN_ON |
                              FLAG_DISMISS_KEYGUARD));
     }

     private void clearScreenAndKeyguardFlags() {
          getApp().runOnUiThread(() -> getApp().getWindow().clearFlags(
                    FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                              FLAG_SHOW_WHEN_LOCKED |
                              FLAG_TURN_SCREEN_ON |
                              FLAG_DISMISS_KEYGUARD));
     }

     public static void clearKeyguardFlags(Activity app) {
          app.runOnUiThread(() -> app.getWindow().clearFlags(FLAG_DISMISS_KEYGUARD));
     }

     private Activity getApp() {
          return cordova.getActivity();
     }

     private Intent getLaunchIntent() {
          Context ctx = getApp().getApplicationContext();
          return ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
     }

     private Object getService(String name) {
          return getApp().getSystemService(name);
     }

     private List<Intent> getAppStartIntents() {
          return Arrays.asList(
                    new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                              "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                              "com.miui.powerkeeper.ui.PowerUsageModelActivity")),
                    new Intent().setComponent(new ComponentName("com.samsung.android.sm",
                              "com.samsung.android.sm.ui.battery.BatteryActivity")),
                    new Intent().setComponent(new ComponentName("com.samsung.android.sm_cn",
                              "com.samsung.android.sm.ui.ram.AutoRunActivity")),
                    new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                              "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                              "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                    new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                              "com.coloros.safe.permission.startup.StartupAppListActivity")),
                    new Intent().setComponent(new ComponentName("com.oppo.safe",
                              "com.oppo.safe.permission.startup.StartupAppListActivity")),
                    new Intent().setComponent(
                              new ComponentName("com.vivo.abe", "com.vivo.abe.power.AbePowerManagerActivity")),
                    new Intent().setComponent(new ComponentName("com.iqoo.secure",
                              "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                    new Intent().setComponent(new ComponentName("com.asus.mobilemanager",
                              "com.asus.mobilemanager.autostart.AutoStartActivity")),
                    new Intent().setComponent(
                              new ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")),
                    new Intent().setAction("com.letv.android.permissionautoboot"));
     }
}