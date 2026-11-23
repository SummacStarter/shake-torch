package com.summacstarter.shaketorch;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Small helper for finding the flash-equipped back camera and toggling its torch. */
public final class TorchHelper {

    private static String cachedCameraId;

    private TorchHelper() {
    }

    public static String getFlashCameraId(Context context) {
        if (cachedCameraId != null) {
            return cachedCameraId;
        }
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) {
            return null;
        }
        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics chars = cm.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (hasFlash != null && hasFlash
                        && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cachedCameraId = id;
                    return id;
                }
            }
            // Fallback: any camera with a flash unit.
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics chars = cm.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    cachedCameraId = id;
                    return id;
                }
            }
        } catch (CameraAccessException ignored) {
        }
        return null;
    }

    public static boolean setTorch(Context context, boolean on) {
        String id = getFlashCameraId(context);
        if (id == null) {
            return false;
        }
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) {
            return false;
        }
        try {
            cm.setTorchMode(id, on);
            return true;
        } catch (CameraAccessException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Haptic confirmation: double pulse when the torch turns on, one firm buzz when it turns off. */
    public static void buzz(Context context, boolean on) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (on) {
            long[] timings = {0, 70, 60, 70};
            int[] amplitudes = {0, 255, 0, 255};
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(140, 255));
        }
    }
}
