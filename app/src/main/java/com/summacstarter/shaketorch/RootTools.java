package com.summacstarter.shaketorch;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Root-powered protections for the shake service. Everything here runs
 * through `su` at runtime; the app itself holds no special permission.
 * If root is not available, every call simply reports that and does nothing.
 */
public final class RootTools {

    public static final String KEY_ROOT_PROTECT = "root_protect";

    private static Boolean cachedRoot;

    private RootTools() {
    }

    /** Result of one shell command. */
    private static final class Result {
        final int exit;
        final String out;

        Result(int exit, String out) {
            this.exit = exit;
            this.out = out;
        }
    }

    private static Result runSu(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int exit = process.waitFor();
            return new Result(exit, sb.toString().trim());
        } catch (Exception e) {
            return new Result(-1, String.valueOf(e.getMessage()));
        }
    }

    /** True when `su` answers with uid=0. Cached after the first check. */
    public static boolean hasRoot() {
        if (cachedRoot != null) {
            return cachedRoot;
        }
        Result r = runSu("id");
        cachedRoot = r.exit == 0 && r.out.contains("uid=0");
        return cachedRoot;
    }

    public static void forgetCachedRoot() {
        cachedRoot = null;
    }

    /**
     * Applies the full protection set and returns one human-readable line
     * per step. Must be called off the main thread (su can block on a
     * grant prompt the first time).
     */
    public static List<String> applyProtections(Context context) {
        List<String> report = new ArrayList<>();
        String pkg = context.getPackageName();
        if (!hasRoot()) {
            report.add("Root: not available (su did not grant)");
            return report;
        }
        report.add("Root: granted");

        Result r1 = runSu("cmd deviceidle whitelist +" + pkg);
        report.add("Doze whitelist: " + (r1.exit == 0 ? "added" : "failed (" + r1.out + ")"));

        Result r2 = runSu("am set-standby-bucket " + pkg + " active");
        report.add("Standby bucket = active: " + (r2.exit == 0 ? "done" : "failed (" + r2.out + ")"));

        Result r3 = runSu("cmd appops set " + pkg + " RUN_IN_BACKGROUND allow;"
                + " cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
        report.add("Background-run appops: " + (r3.exit == 0 ? "allowed" : "failed (" + r3.out + ")"));

        // Global setting: stops Android 12+ from killing processes that spawn
        // many children (the "phantom process killer"). Reversible with the
        // same command ending in "true", or by deleting the setting.
        Result r4 = runSu("settings put global settings_enable_monitor_phantom_procs false");
        report.add("Phantom process killer: " + (r4.exit == 0 ? "disabled (global)" : "failed (" + r4.out + ")"));

        SharedPreferences prefs =
                context.getSharedPreferences(ShakeService.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ROOT_PROTECT, true).apply();
        return report;
    }

    /** Quiet re-apply of the cheap, idempotent pieces. Background thread only. */
    public static void reapplyQuietly(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(ShakeService.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ROOT_PROTECT, false)) {
            return;
        }
        if (!hasRoot()) {
            return;
        }
        String pkg = context.getPackageName();
        runSu("cmd deviceidle whitelist +" + pkg);
        runSu("am set-standby-bucket " + pkg + " active");
    }
}
