package de.appplant.cordova.plugin.background;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

public class ForegroundService extends Service {

     public static final int NOTIFICATION_ID = -574543954;

     private static final String NOTIFICATION_TITLE = "App is running in background";
     private static final String NOTIFICATION_TEXT = "Doing heavy tasks.";
     private static final String NOTIFICATION_ICON = "icon";

     private final IBinder binder = new ForegroundBinder();
     private PowerManager.WakeLock wakeLock;

     @Override
     public IBinder onBind(Intent intent) {
          return binder;
     }

     class ForegroundBinder extends Binder {
          ForegroundService getService() {
               return ForegroundService.this;
          }
     }

     @Override
     public void onCreate() {
          super.onCreate();
          keepAwake();
     }

     @Override
     public void onDestroy() {
          super.onDestroy();
          sleepWell();
     }

     @Override
     public int onStartCommand(Intent intent, int flags, int startId) {
          return START_STICKY;
     }

     @SuppressLint("WakelockTimeout")
     private void keepAwake() {
          JSONObject settings = BackgroundMode.getSettings();
          boolean isSilent = settings.optBoolean("silent", false);

          if (!isSilent) {
               Notification notification = makeNotification(settings);

               int foregroundType = 0;
               if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                    foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
               } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // CONNECTED_DEVICE is the least-restricted fallback on Chinese ROMs
                    foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
               }

               if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, foregroundType);
               } else {
                    startForeground(NOTIFICATION_ID, notification);
               }
          }

          // PARTIAL_WAKE_LOCK without timeout = survives Doze + OEM killers
          PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
          wakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, "backgroundmode:wakelock");
          wakeLock.acquire(); // ← NO TIMEOUT. This is the key.
     }

     private void sleepWell() {
          stopForeground(true);
          getNotificationManager().cancel(NOTIFICATION_ID);

          if (wakeLock != null && wakeLock.isHeld()) {
               wakeLock.release();
               wakeLock = null;
          }
          stopSelf();
     }

     private Notification makeNotification() {
          return makeNotification(BackgroundMode.getSettings());
     }

     private Notification makeNotification(JSONObject settings) {
          final String CHANNEL_ID = "cordova-plugin-background-mode-id";

          // Create channel (once)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
               NotificationChannel channel = new NotificationChannel(
                         CHANNEL_ID,
                         settings.optString("channelName", "Background Service"),
                         NotificationManager.IMPORTANCE_LOW);
               channel.setDescription(settings.optString("text", "Running in background"));
               channel.setShowBadge(false);
               channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
               getNotificationManager().createNotificationChannel(channel);
          }

          String title = settings.optString("title", NOTIFICATION_TITLE);
          String text = settings.optString("text", NOTIFICATION_TEXT);
          boolean bigText = settings.optBoolean("bigText", false);
          boolean hidden = settings.optBoolean("hidden", false); // false = visible notification

          Context context = getApplicationContext();
          Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());

          Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

          builder.setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setSmallIcon(getIconResId(settings))
                    .setPriority(hidden ? Notification.PRIORITY_MIN : Notification.PRIORITY_LOW)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);

          if (bigText || text.contains("\n")) {
               builder.setStyle(new Notification.BigTextStyle().bigText(text));
          }

          setColor(builder, settings);

          if (intent != null && settings.optBoolean("resume", true)) {
               intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
               int flags = PendingIntent.FLAG_UPDATE_CURRENT;
               if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
               }
               PendingIntent pi = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags);
               builder.setContentIntent(pi);
          }

          return builder.build();
     }

     protected void updateNotification(JSONObject settings) {
          boolean isSilent = settings.optBoolean("silent", false);
          if (isSilent) {
               stopForeground(true);
               return;
          }
          Notification n = makeNotification(settings);
          getNotificationManager().notify(NOTIFICATION_ID, n);
     }

     private int getIconResId(JSONObject settings) {
          String icon = settings.optString("icon", NOTIFICATION_ICON);
          int resId = getIconResId(icon, "mipmap");
          if (resId == 0)
               resId = getIconResId(icon, "drawable");
          if (resId == 0)
               resId = android.R.drawable.ic_dialog_info;
          return resId;
     }

     private int getIconResId(String icon, String type) {
          Resources res = getResources();
          String pkgName = getPackageName();
          int resId = res.getIdentifier(icon, type, pkgName);
          if (resId == 0) {
               resId = res.getIdentifier("icon", type, pkgName);
          }
          return resId;
     }

     @TargetApi(Build.VERSION_CODES.LOLLIPOP)
     private void setColor(Notification.Builder builder, JSONObject settings) {
          String hex = settings.optString("color", null);
          if (Build.VERSION.SDK_INT < 21 || hex == null)
               return;
          try {
               int argb = Integer.parseInt(hex, 16) + 0xFF000000;
               builder.setColor(argb);
          } catch (Exception ignored) {
          }
     }

     private NotificationManager getNotificationManager() {
          return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
     }
}