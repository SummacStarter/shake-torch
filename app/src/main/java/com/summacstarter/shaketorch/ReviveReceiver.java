package com.summacstarter.shaketorch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Brings ShakeService back whenever it should be running but is not:
 * after a reboot (BOOT_COMPLETED), on a periodic watchdog alarm, and
 * shortly after the app is swiped away or the service is destroyed.
 * It only starts the service when the user left shake detection switched on.
 */
public class ReviveReceiver extends BroadcastReceiver {

    public static final String ACTION_REVIVE = "com.summacstarter.shaketorch.REVIVE";
    private static final int REQUEST_CODE = 7;
    public static final long WATCHDOG_INTERVAL_MS = 15 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isEnabled(context)) {
            startService(context);
        }
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(ShakeService.PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(ShakeService.KEY_ENABLED, false);
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, ShakeService.class);
        context.startForegroundService(intent);
    }

    /** One-shot alarm that will ask this receiver to revive the service after the delay. */
    public static void scheduleRevive(Context context, long delayMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent intent = new Intent(context, ReviveReceiver.class);
        intent.setAction(ACTION_REVIVE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending);
    }

    public static void scheduleWatchdog(Context context) {
        scheduleRevive(context, WATCHDOG_INTERVAL_MS);
    }

    public static void cancelRevive(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent intent = new Intent(context, ReviveReceiver.class);
        intent.setAction(ACTION_REVIVE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pending);
    }
}
