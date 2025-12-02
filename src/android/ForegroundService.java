package de.appplant.cordova.plugin.background;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

public class ForegroundService extends Service {

    private static final int NOTIF_ID = 98234157;
    private static final String CHANNEL_ID = "backgroundmode_channel";
    private static final String ACTION_UPDATE = "de.appplant.cordova.plugin.background.UPDATE_NOTIFICATION";

    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver updateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        registerUpdateReceiver();
        keepAwake();
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("settings")) {
            try {
                JSONObject s = new JSONObject(intent.getStringExtra("settings"));
                getNotificationManager().notify(NOTIF_ID, buildNotification(s));
            } catch (Exception ignored) {}
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (updateReceiver != null) unregisterReceiver(updateReceiver);
        stopForeground(true);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void registerUpdateReceiver() {
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String json = intent.getStringExtra("settings");
                try {
                    JSONObject s = json != null ? new JSONObject(json) : new JSONObject();
                    getNotificationManager().notify(NOTIF_ID, buildNotification(s));
                } catch (Exception ignored) {}
            }
        };
        IntentFilter f = new IntentFilter(ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, f);
        }
    }

    @SuppressLint("WakelockTimeout")
    private void keepAwake() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "backgroundmode:wakelock");
        wakeLock.acquire();
    }

    private Notification buildNotification() {
        return buildNotification(BackgroundMode.getSettings());
    }

    private Notification buildNotification(JSONObject s) {
        String title = s.optString("title", "App is running");
        String text  = s.optString("text", "Background service active");
        boolean silent = s.optBoolean("silent", false);
        String icon = s.optString("icon", "icon");

        if (silent) {
            stopForeground(true);
            return null;
        }

        Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pi = i != null
            ? PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
            : null;

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        b.setContentTitle(title)
         .setContentText(text)
         .setSmallIcon(getIconId(icon))
         .setOngoing(true)
         .setContentIntent(pi)
         .setPriority(Notification.PRIORITY_LOW);

        return b.build();
    }

    private int getIconId(String name) {
        Resources r = getResources();
        int id = r.getIdentifier(name, "drawable", getPackageName());
        if (id == 0) id = r.getIdentifier("icon", "drawable", getPackageName());
        if (id == 0) id = android.R.drawable.ic_dialog_info;
        return id;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Background Service", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps app alive in background");
            getNotificationManager().createNotificationChannel(c);
        }
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
}