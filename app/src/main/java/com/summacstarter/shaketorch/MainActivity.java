package com.summacstarter.shaketorch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0 system, 1 light, 2 dark
    private static final String KEY_ACCENT = "accent_index";
    private static final int[] ACCENTS = {
            0xFF7C4DFF, // purple
            0xFF2196F3, // blue
            0xFFEC407A, // pink
            0xFF009688, // teal
            0xFFFF7043  // orange
    };

    private SharedPreferences prefs;
    private ScrollView scrollView;
    private Switch serviceSwitch;
    private TextView sensitivityValue;
    private TextView sensitivityLabel;
    private SeekBar sensitivityBar;
    private TextView heroStatus;
    private Button heroButton;
    private boolean manualTorchOn = false;

    private android.graphics.Typeface fontLight;
    private android.graphics.Typeface fontRegular;
    private android.graphics.Typeface fontMedium;
    private android.graphics.Typeface fontSemiBold;

    private boolean dark;
    private int accent;
    private int accentDark;
    private int colBg;
    private int colCard;
    private int colText;
    private int colMuted;
    private int colSurface;
    private int colStroke;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ShakeService.PREFS, MODE_PRIVATE);
        fontLight = interFont(340);
        fontRegular = interFont(400);
        fontMedium = interFont(500);
        fontSemiBold = interFont(600);
        resolveTheme();
        requestNotificationPermissionIfNeeded();
        buildUi();
    }

    private void resolveTheme() {
        int mode = prefs.getInt(KEY_THEME_MODE, 0);
        boolean systemDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = mode == 2 || (mode == 0 && systemDark);
        int idx = prefs.getInt(KEY_ACCENT, 0);
        if (idx < 0 || idx >= ACCENTS.length) {
            idx = 0;
        }
        accent = ACCENTS[idx];
        accentDark = shade(accent, 0.68f);
        colBg = dark ? 0xFF0B0B0F : 0xFFF6F6F8;
        colCard = dark ? 0xFF17171D : 0xFFFFFFFF;
        colText = dark ? 0xFFF2F2F5 : 0xFF131318;
        colMuted = dark ? 0xFF9A9AA5 : 0xFF6B6B76;
        colSurface = dark ? 0xFF23232B : 0xFFE9E9EE;
        // Reel rule (alpha borders): strokes are a 12% alpha tint, white on
        // dark and black on light, so they blend into whatever sits behind
        // them instead of reading as a solid grey line.
        colStroke = dark ? 0x1FFFFFFF : 0x1F000000;
    }

    // Reel rule (gradients in OKLCH): interpolate the gradient midpoint in
    // OKLab space so the middle of the gradient keeps its saturation
    // instead of washing out the way an sRGB midpoint does.
    private static int oklabMidpoint(int c1, int c2) {
        double[] lab1 = toOklab(c1);
        double[] lab2 = toOklab(c2);
        return fromOklab(
                (lab1[0] + lab2[0]) / 2.0,
                (lab1[1] + lab2[1]) / 2.0,
                (lab1[2] + lab2[2]) / 2.0);
    }

    private static double[] toOklab(int color) {
        double r = srgbToLinear(((color >> 16) & 0xFF) / 255.0);
        double g = srgbToLinear(((color >> 8) & 0xFF) / 255.0);
        double b = srgbToLinear((color & 0xFF) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    private static int fromOklab(double L, double a, double b) {
        double l = L + 0.3963377774 * a + 0.2158037573 * b;
        double m = L - 0.1055613458 * a - 0.0638541728 * b;
        double s = L - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        return 0xFF000000 | (linearToSrgb(r) << 16) | (linearToSrgb(g) << 8) | linearToSrgb(bl);
    }

    private static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int linearToSrgb(double c) {
        c = Math.max(0.0, Math.min(1.0, c));
        double v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
        return Math.round((float) (v * 255.0));
    }

    private static int shade(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    // Inter variable font bundled in assets; weight picked per role.
    private android.graphics.Typeface interFont(int weight) {
        try {
            android.graphics.Typeface tf = new android.graphics.Typeface.Builder(
                    getAssets(), "fonts/inter.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (tf != null) {
                return tf;
            }
        } catch (Exception ignored) {
        }
        return android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL);
    }

    // Platform Buttons carry default min sizes, padding and a raise-on-press
    // animator that fight custom shapes; strip them so text sits centred in
    // exactly the shape it inhabits.
    private void flatten(Button b) {
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setIncludeFontPadding(false);
        b.setGravity(Gravity.CENTER);
    }

    // ---------- small view factories ----------

    private float px(float dpValue) {
        return dpValue * getResources().getDisplayMetrics().density;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radiusDp));
        return d;
    }

    private GradientDrawable cardDrawable() {
        GradientDrawable d = rounded(colCard, 28);
        d.setStroke(dp(1), colStroke);
        return d;
    }

    private GradientDrawable accentGradient(float radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{accent, oklabMidpoint(accent, accentDark), accentDark});
        d.setCornerRadius(px(radiusDp));
        return d;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(cardDrawable());
        int p = dp(18);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase(java.util.Locale.US));
        tv.setTextSize(11);
        tv.setTypeface(fontMedium);
        tv.setLetterSpacing(0.16f);
        tv.setTextColor(colMuted);
        tv.setPadding(0, 0, 0, dp(12));
        return tv;
    }

    private TextView body(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTypeface(fontRegular);
        tv.setTextColor(colMuted);
        tv.setLineSpacing(0, 1.35f);
        return tv;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        flatten(b);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(fontSemiBold);
        b.setAllCaps(false);
        // Reel rule (concentric radius): a full-width button inside a card
        // takes the card radius minus the card padding: 28 - 18 = 10.
        b.setBackground(accentGradient(10));
        b.setTextColor(0xFFFFFFFF);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        flatten(b);
        b.setText(text);
        b.setTextSize(14);
        b.setTypeface(fontMedium);
        b.setAllCaps(false);
        b.setBackground(rounded(colSurface, 10));
        b.setTextColor(colText);
        return b;
    }

    private LinearLayout.LayoutParams fullRow(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    // ---------- UI ----------

    private void buildUi() {
        getWindow().setStatusBarColor(colBg);
        if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        scroll.setBackground(glowBackground(false));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, dp(36), pad, dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Shake Torch");
        title.setTextSize(32);
        title.setTypeface(fontLight);
        title.setLetterSpacing(-0.01f);
        title.setTextColor(colText);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView subtitle = body("Chop twice, like the old Motorola chop-chop, "
                + "to turn your flashlight on or off.");
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(dp(16), dp(8), dp(16), dp(26));
        root.addView(subtitle);

        // ---------- Hero: the torch itself ----------
        LinearLayout heroCard = card();
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroButton = new Button(this);
        flatten(heroButton);
        heroButton.setText("");
        heroButton.setOnClickListener(v -> toggleManualTorch());
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(dp(132), dp(132));
        heroLp.gravity = Gravity.CENTER_HORIZONTAL;
        heroCard.addView(heroButton, heroLp);
        heroStatus = new TextView(this);
        heroStatus.setTextSize(14);
        heroStatus.setTypeface(fontRegular);
        heroStatus.setTextColor(colMuted);
        heroStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        heroStatus.setPadding(dp(8), dp(16), dp(8), 0);
        heroCard.addView(heroStatus);
        refreshHero();
        root.addView(heroCard);

        // ---------- Shake detection ----------
        LinearLayout detectCard = card();
        detectCard.addView(sectionLabel("Shake detection"));
        serviceSwitch = new Switch(this);
        serviceSwitch.setText("Listen for the chop");
        serviceSwitch.setTextSize(15);
        serviceSwitch.setTypeface(fontRegular);
        serviceSwitch.setTextColor(colText);
        // Off must read as off: grey thumb, grey track, muted label. On is accent.
        int[][] switchStates = new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}};
        serviceSwitch.setThumbTintList(new ColorStateList(switchStates,
                new int[]{accent, dark ? 0xFF6E6E78 : 0xFFFFFFFF}));
        serviceSwitch.setTrackTintList(new ColorStateList(switchStates,
                new int[]{(140 << 24) | (accent & 0x00FFFFFF),
                        dark ? 0xFF3A3A44 : 0xFFDCDCE2}));
        serviceSwitch.setTextColor(new ColorStateList(switchStates,
                new int[]{colText, colMuted}));
        serviceSwitch.setPadding(0, dp(2), 0, dp(14));
        serviceSwitch.setChecked(prefs.getBoolean(KEY_SERVICE_ENABLED, false));
        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, isChecked).apply();
            updateSensitivityEnabled(isChecked);
            if (isChecked) {
                startShakeService();
            } else {
                ReviveReceiver.cancelRevive(this);
                stopService(new Intent(this, ShakeService.class));
            }
        });
        detectCard.addView(serviceSwitch);

        sensitivityLabel = new TextView(this);
        sensitivityLabel.setText("Shake intensity");
        sensitivityLabel.setTextSize(15);
        sensitivityLabel.setTypeface(fontMedium);
        sensitivityLabel.setTextColor(colText);
        detectCard.addView(sensitivityLabel);

        sensitivityValue = new TextView(this);
        sensitivityValue.setTextSize(13);
        sensitivityValue.setTypeface(fontRegular);
        sensitivityValue.setTextColor(colMuted);
        sensitivityValue.setPadding(0, dp(2), 0, dp(4));
        detectCard.addView(sensitivityValue);

        sensitivityBar = new SeekBar(this);
        sensitivityBar.setMax(100);
        sensitivityBar.setProgress(prefs.getInt(ShakeService.KEY_SENSITIVITY, 50));
        sensitivityBar.setProgressTintList(ColorStateList.valueOf(accent));
        sensitivityBar.setThumbTintList(ColorStateList.valueOf(accent));
        updateSensitivityLabel(sensitivityBar.getProgress());
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt(ShakeService.KEY_SENSITIVITY, progress).apply();
                updateSensitivityLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        detectCard.addView(sensitivityBar);
        updateSensitivityEnabled(serviceSwitch.isChecked());
        root.addView(detectCard);

        // ---------- Appearance ----------
        LinearLayout appearanceCard = card();
        appearanceCard.addView(sectionLabel("Appearance"));
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] modeNames = {"System", "Light", "Dark"};
        int currentMode = prefs.getInt(KEY_THEME_MODE, 0);
        for (int i = 0; i < modeNames.length; i++) {
            final int modeValue = i;
            Button modeButton = new Button(this);
            flatten(modeButton);
            modeButton.setText(modeNames[i]);
            modeButton.setAllCaps(false);
            modeButton.setTextSize(14);
            modeButton.setTypeface(fontMedium);
            if (i == currentMode) {
                modeButton.setBackground(accentGradient(999));
                modeButton.setTextColor(0xFFFFFFFF);
            } else {
                modeButton.setBackground(rounded(colSurface, 999));
                modeButton.setTextColor(colText);
            }
            modeButton.setOnClickListener(v -> {
                prefs.edit().putInt(KEY_THEME_MODE, modeValue).apply();
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i < modeNames.length - 1) {
                lp.rightMargin = dp(8);
            }
            modeRow.addView(modeButton, lp);
        }
        appearanceCard.addView(modeRow);

        LinearLayout accentRow = new LinearLayout(this);
        accentRow.setOrientation(LinearLayout.HORIZONTAL);
        accentRow.setGravity(Gravity.CENTER_HORIZONTAL);
        accentRow.setPadding(0, dp(18), 0, 0);
        int currentAccent = prefs.getInt(KEY_ACCENT, 0);
        for (int i = 0; i < ACCENTS.length; i++) {
            final int accentIndex = i;
            Button swatch = new Button(this);
            flatten(swatch);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(ACCENTS[i]);
            if (i == currentAccent) {
                circle.setStroke(dp(3), colText);
            }
            swatch.setBackground(circle);
            swatch.setText(i == currentAccent ? "✓" : "");
            swatch.setTextColor(0xFFFFFFFF);
            swatch.setTextSize(20);
            swatch.setTypeface(fontSemiBold);
            swatch.setOnClickListener(v -> {
                prefs.edit().putInt(KEY_ACCENT, accentIndex).apply();
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
            if (i < ACCENTS.length - 1) {
                lp.rightMargin = dp(12);
            }
            accentRow.addView(swatch, lp);
        }
        appearanceCard.addView(accentRow);
        root.addView(appearanceCard);

        // ---------- Staying alive ----------
        LinearLayout aliveCard = card();
        aliveCard.addView(sectionLabel("Staying alive"));
        aliveCard.addView(body("It starts by itself after a reboot, revives itself if the "
                + "system kills it, and checks in every 30 minutes. On OnePlus it also needs "
                + "Battery usage set to Unrestricted and Auto launch turned on."));
        Button batteryButton = secondaryButton("Battery + auto-launch settings");
        batteryButton.setOnClickListener(v -> openBatterySettings());
        aliveCard.addView(batteryButton, fullRow(14));
        root.addView(aliveCard);

        // ---------- Root protections ----------
        LinearLayout rootCard = card();
        rootCard.addView(sectionLabel("Root protections"));
        rootCard.addView(body("With root, this app can whitelist itself against Doze, pin its "
                + "standby bucket to active, allow background runs, and disable the Android "
                + "phantom process killer (a global, reversible setting). Once applied, the "
                + "whitelist and bucket are re-applied quietly whenever the service starts."));
        TextView rootStatus = new TextView(this);
        rootStatus.setTextSize(13);
        rootStatus.setTypeface(fontRegular);
        rootStatus.setTextColor(colMuted);
        rootStatus.setPadding(0, dp(8), 0, 0);
        rootCard.addView(rootStatus);
        Button rootButton = primaryButton("Apply root protections");
        rootButton.setOnClickListener(v -> {
            rootStatus.setText("Working… (approve the root prompt if one appears)");
            new Thread(() -> {
                RootTools.forgetCachedRoot();
                java.util.List<String> report = RootTools.applyProtections(this);
                StringBuilder sb = new StringBuilder();
                for (String line : report) {
                    sb.append(line).append('\n');
                }
                runOnUiThread(() -> rootStatus.setText(sb.toString().trim()));
            }).start();
        });
        rootCard.addView(rootButton, fullRow(14));
        root.addView(rootCard);

        TextView footer = new TextView(this);
        footer.setText("No ads · No internet · Nothing leaves your phone");
        footer.setTextSize(11);
        footer.setTypeface(fontMedium);
        footer.setLetterSpacing(0.05f);
        footer.setTextColor(colMuted);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(4), 0, 0);
        root.addView(footer);

        setContentView(scroll);

        // If the user had it on before (e.g. after a reboot of the activity), keep it running.
        if (serviceSwitch.isChecked()) {
            startShakeService();
        }
    }

    private void toggleManualTorch() {
        manualTorchOn = !manualTorchOn;
        boolean ok = TorchHelper.setTorch(this, manualTorchOn);
        if (!ok) {
            manualTorchOn = !manualTorchOn;
            Toast.makeText(this, "Could not reach the flashlight", Toast.LENGTH_SHORT).show();
            return;
        }
        TorchHelper.buzz(this, manualTorchOn);
        // Reel rule (icon swaps): soften the swap — the old state fades
        // down while the new one fades in, instead of a hard cut.
        heroButton.animate().alpha(0.25f).setDuration(90).withEndAction(() -> {
            refreshHero();
            heroButton.animate().alpha(1f).setDuration(140).start();
        }).start();
    }

    // Pure decoration: two soft accent glows breathing in the corners of
    // the background. They brighten when the torch is on, because why not.
    private android.graphics.drawable.Drawable glowBackground(boolean lit) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(colBg);
        GradientDrawable topGlow = new GradientDrawable();
        topGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        topGlow.setGradientRadius(px(330));
        topGlow.setGradientCenter(0.9f, 0.0f);
        topGlow.setColors(new int[]{
                ((lit ? 115 : 52) << 24) | (accent & 0x00FFFFFF), 0x00000000});
        GradientDrawable bottomGlow = new GradientDrawable();
        bottomGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bottomGlow.setGradientRadius(px(430));
        bottomGlow.setGradientCenter(0.0f, 1.0f);
        bottomGlow.setColors(new int[]{
                ((lit ? 85 : 34) << 24) | (accentDark & 0x00FFFFFF), 0x00000000});
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, topGlow, bottomGlow});
    }

    // The power glyph is drawn, not typed: the ⏻ character (U+23FB) is
    // missing from many OEM fonts and rendered invisibly on OnePlus.
    private android.graphics.drawable.Drawable powerIcon(int color, int sizePx) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        paint.setStrokeWidth(sizePx * 0.075f);
        paint.setColor(color);
        float inset = sizePx * 0.24f;
        android.graphics.RectF oval = new android.graphics.RectF(
                inset, inset, sizePx - inset, sizePx - inset);
        canvas.drawArc(oval, -60f, 300f, false, paint);
        canvas.drawLine(sizePx / 2f, sizePx * 0.16f, sizePx / 2f, sizePx * 0.46f, paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private void refreshHero() {
        if (heroButton == null) {
            return;
        }
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        int glyphColor;
        if (manualTorchOn) {
            oval = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{accent, oklabMidpoint(accent, accentDark), accentDark});
            oval.setShape(GradientDrawable.OVAL);
            glyphColor = 0xFFFFFFFF;
            heroStatus.setTextColor(colText);
            heroStatus.setText("Flashlight is on. Tap or chop twice to turn it off.");
        } else {
            oval.setColor(colSurface);
            oval.setStroke(dp(1), colStroke);
            glyphColor = accent;
            heroStatus.setTextColor(colMuted);
            heroStatus.setText("Flashlight is off. Tap or chop twice to turn it on.");
        }
        // A thin halo ring floats around the circle, pure ornament.
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor(0x00000000);
        halo.setStroke(dp(1), ((manualTorchOn ? 170 : 70) << 24) | (accent & 0x00FFFFFF));
        int iconSize = dp(56);
        android.graphics.drawable.Drawable icon = powerIcon(glyphColor, iconSize);
        android.graphics.drawable.LayerDrawable layers =
                new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{halo, oval, icon});
        layers.setLayerInset(1, dp(9), dp(9), dp(9), dp(9));
        layers.setLayerGravity(2, Gravity.CENTER);
        layers.setLayerSize(2, iconSize, iconSize);
        heroButton.setBackground(layers);
        if (scrollView != null) {
            scrollView.setBackground(glowBackground(manualTorchOn));
        }
    }

    // The intensity controls belong to the listener: when the chop is off
    // they dim and stop responding, so the card never shows live-looking
    // controls for a dead feature.
    private void updateSensitivityEnabled(boolean enabled) {
        if (sensitivityBar == null) {
            return;
        }
        sensitivityBar.setEnabled(enabled);
        sensitivityBar.setAlpha(enabled ? 1f : 0.4f);
        sensitivityLabel.setAlpha(enabled ? 1f : 0.45f);
        sensitivityValue.setAlpha(enabled ? 1f : 0.45f);
    }

    private void updateSensitivityLabel(int progress) {
        String word;
        if (progress < 34) {
            word = "Low — needs a hard chop";
        } else if (progress < 67) {
            word = "Medium";
        } else {
            word = "High — a light chop is enough";
        }
        sensitivityValue.setText(word + "  (" + progress + ", fires at ~"
                + String.format(java.util.Locale.US, "%.1f", ShakeService.thresholdFor(progress))
                + "g)");
    }

    private void startShakeService() {
        Intent intent = new Intent(this, ShakeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void openBatterySettings() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Battery is already Unrestricted for Shake Torch",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Set Battery usage to Unrestricted, and Auto launch on",
                    Toast.LENGTH_LONG).show();
        }
        // OxygenOS ignores the stock battery-optimization intents, so go straight
        // to this app's info page, where Battery usage and Auto launch live.
        // Fall back through the stock screens if even that is unavailable.
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            return;
        } catch (Exception ignored) {
        }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
