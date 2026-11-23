package com.summacstarter.shaketorch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Foreground service that listens to the accelerometer for a Moto-style
 * double-chop gesture and toggles the flashlight. No network, no ads,
 * no analytics — it only reads the accelerometer and proximity sensors.
 */
public class ShakeService extends Service implements SensorEventListener {

    public static final String PREFS = "shake_torch_prefs";
    public static final String KEY_SENSITIVITY = "sensitivity"; // 0..100, default 50
    public static final String KEY_ENABLED = "service_enabled";

    private static final String CHANNEL_ID = "shake_torch_listening";
    private static final int NOTIFICATION_ID = 42;

    // Double-chop timing windows.
    private static final long CHOP_PAIR_WINDOW_MS = 600;
    private static final long CHOP_DEBOUNCE_MS = 150;
    private static final long TOGGLE_COOLDOWN_MS = 1000;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor proximity;
    private boolean sensorsRegistered = false;
    private volatile float thresholdG = thresholdFor(50);
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private boolean proximityNear = false;
    private boolean torchOn = false;

    private long lastChopTime = 0;
    private long lastAboveThresholdTime = 0;
    private long lastToggleTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }
        // Cache the threshold instead of reading preferences on every sample.
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        thresholdG = thresholdFor(prefs.getInt(KEY_SENSITIVITY, 50));
        prefListener = (sharedPreferences, key) -> {
            if (KEY_SENSITIVITY.equals(key)) {
                thresholdG = thresholdFor(sharedPreferences.getInt(KEY_SENSITIVITY, 50));
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        registerSensors();
        // Chain a watchdog alarm: if the system kills the service anyway,
        // the alarm revives it (ReviveReceiver checks the on/off switch first).
        ReviveReceiver.scheduleWatchdog(this);
        // If root protections were applied once, quietly re-assert the cheap
        // ones (Doze whitelist, standby bucket) in case the system reset them.
        // First start of this instance only; the watchdog restarting a live
        // service should cost nothing.
        if (startId == 1) {
            new Thread(() -> RootTools.reapplyQuietly(this)).start();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiped away from Recents: come back in a couple of seconds if still enabled.
        if (ReviveReceiver.isEnabled(this)) {
            ReviveReceiver.scheduleRevive(this, 2000);
        }
        super.onTaskRemoved(rootIntent);
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Shake Torch listening", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown while shake-to-flashlight is switched on");
            nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Shake Torch is listening")
                .setContentText("Chop twice to toggle the flashlight")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void registerSensors() {
        if (sensorManager == null || sensorsRegistered) {
            return;
        }
        if (accelerometer != null) {
            // Energy: sample at 25 Hz (a chop is a broad motion; 50 Hz was
            // wasted resolution) and let the sensor hub batch reports for
            // 250 ms so the processor sleeps between bursts instead of
            // waking for every sample. Chop timing uses each sample's own
            // hardware timestamp, so batching does not distort the gesture.
            sensorManager.registerListener(this, accelerometer,
                    40_000, 250_000);
        }
        if (proximity != null) {
            sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
        sensorsRegistered = true;
    }

    @Override
    public void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        sensorsRegistered = false;
        if (prefListener != null) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        // Killed while still switched on (the switch writes its pref before
        // stopping the service, so an intentional stop leaves enabled=false):
        // ask the watchdog path to bring us back.
        if (ReviveReceiver.isEnabled(this)) {
            ReviveReceiver.scheduleRevive(this, 2000);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Maps the 0..100 slider to a g-force threshold: 100 (most sensitive) = 1.3g, 0 = 3.8g. */
    public static float thresholdFor(int sensitivity) {
        return 3.8f - (sensitivity / 100f) * 2.5f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            float max = event.sensor.getMaximumRange();
            proximityNear = event.values[0] < max;
            return;
        }
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double gForce = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;

        // The sample's hardware timestamp, not delivery time: batched
        // reports arrive in bursts but keep their true spacing.
        long now = event.timestamp / 1_000_000L;
        if (gForce <= thresholdG) {
            return;
        }
        // Debounce: one chop per sharp movement, not per sensor sample.
        if (now - lastAboveThresholdTime < CHOP_DEBOUNCE_MS) {
            return;
        }
        lastAboveThresholdTime = now;

        // Pocket guard: if the proximity sensor is covered, it's a pocket/bag — ignore.
        if (proximityNear) {
            return;
        }

        if (now - lastToggleTime < TOGGLE_COOLDOWN_MS) {
            return;
        }

        if (lastChopTime != 0 && now - lastChopTime <= CHOP_PAIR_WINDOW_MS) {
            // Second chop inside the window: that's the gesture.
            lastChopTime = 0;
            lastToggleTime = now;
            toggleTorch();
        } else {
            lastChopTime = now;
        }
    }

    private void toggleTorch() {
        torchOn = !torchOn;
        boolean ok = TorchHelper.setTorch(this, torchOn);
        if (!ok) {
            torchOn = !torchOn;
            return;
        }
        TorchHelper.buzz(this, torchOn);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed.
    }
}
