package com.example.ui.blocker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.R;
import com.example.util.IconHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

public class AppBlockerManager {

    private static final String TAG = "AppBlockerManager";

    private static View currentOverlayView = null;
    private static WindowManager currentWindowManager = null;
    private static String currentBlockedPackage = null;
    private static long lastBlockTimestamp = 0L;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized void blockApp(
            Context context,
            String activityName,
            String colorHex,
            String iconName,
            String blockedPkg,
            long trackedMillis,
            long targetMillis
    ) {
        final Context appContext = context.getApplicationContext();
        long now = System.currentTimeMillis();

        if (blockedPkg != null && blockedPkg.equals(currentBlockedPackage) && currentOverlayView != null) {
            // Already actively blocking this package with overlay visible
            return;
        }

        if (now - lastBlockTimestamp < 300L) {
            return;
        }
        lastBlockTimestamp = now;
        currentBlockedPackage = blockedPkg;

        mainHandler.post(() -> {
            boolean overlayShown = false;
            // 1. Try WindowManager Overlay (Instant, unblockable on Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(appContext)) {
                overlayShown = showWindowManagerOverlay(appContext, activityName, colorHex, iconName, blockedPkg, trackedMillis, targetMillis);
            }

            // 2. If overlay cannot be shown or as fallback, force home redirect & launch full blocker activity
            if (!overlayShown) {
                forceRedirectHome(appContext);
                AppBlockerActivity.launch(appContext, activityName, colorHex, iconName, blockedPkg, trackedMillis, targetMillis);
            }
        });
    }

    private static boolean showWindowManagerOverlay(
            Context context,
            String activityName,
            String colorHex,
            String iconName,
            String blockedPkg,
            long trackedMillis,
            long targetMillis
    ) {
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return false;

            if (currentOverlayView != null && currentWindowManager != null) {
                try {
                    currentWindowManager.removeView(currentOverlayView);
                } catch (Exception ignored) {}
                currentOverlayView = null;
            }

            LayoutInflater inflater = LayoutInflater.from(context);
            View overlayView = inflater.inflate(R.layout.activity_app_blocker, null);

            bindOverlayViews(context, overlayView, activityName, colorHex, iconName, blockedPkg, trackedMillis, targetMillis);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            wm.addView(overlayView, params);
            currentOverlayView = overlayView;
            currentWindowManager = wm;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error displaying window overlay", e);
            return false;
        }
    }

    private static void bindOverlayViews(
            Context context,
            View root,
            String activityName,
            String colorHex,
            String iconName,
            String blockedPkg,
            long trackedMillis,
            long targetMillis
    ) {
        TextView tvSubtitle = root.findViewById(R.id.tv_blocker_subtitle);
        TextView tvActivityName = root.findViewById(R.id.tv_activity_name);
        TextView tvBlockedAppName = root.findViewById(R.id.tv_blocked_app_name);
        TextView tvTimeStats = root.findViewById(R.id.tv_time_stats);
        ImageView ivActivityIcon = root.findViewById(R.id.iv_activity_icon);
        LinearProgressIndicator progressLimit = root.findViewById(R.id.progress_limit);
        MaterialButton btnReturnHome = root.findViewById(R.id.btn_return_home);

        String actName = (activityName != null && !activityName.trim().isEmpty()) ? activityName : "Activity";
        if (tvSubtitle != null) {
            tvSubtitle.setText(context.getString(R.string.app_blocker_consumed_format, actName));
        }
        if (tvActivityName != null) {
            tvActivityName.setText(actName);
        }

        // App details
        String appLabel = blockedPkg != null ? blockedPkg : "Unknown App";
        try {
            if (blockedPkg != null) {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(blockedPkg, 0);
                appLabel = pm.getApplicationLabel(ai).toString();
            }
        } catch (Exception ignored) {}

        if (tvBlockedAppName != null) {
            tvBlockedAppName.setText(appLabel);
        }

        // Color and icon
        int parsedColor = 0xFFEF4444;
        try {
            if (colorHex != null && !colorHex.isEmpty()) {
                parsedColor = Color.parseColor(colorHex);
            }
        } catch (Exception ignored) {}

        if (ivActivityIcon != null) {
            ivActivityIcon.setColorFilter(parsedColor);
            ivActivityIcon.setBackgroundTintList(ColorStateList.valueOf(parsedColor & 0x33FFFFFF));
            int resId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            if (resId != 0) {
                ivActivityIcon.setImageResource(resId);
            } else {
                Drawable d = IconHelper.getDrawableForIcon(context, iconName);
                if (d != null) {
                    ivActivityIcon.setImageDrawable(d);
                }
            }
        }

        // Time Stats
        long tracked = Math.max(0, trackedMillis);
        long target = Math.max(0, targetMillis);
        String trackedStr = formatDuration(tracked);
        String targetStr = formatDuration(target);

        int percent = target > 0 ? (int) Math.min(300, ((float) tracked / target) * 100f) : 100;
        if (tvTimeStats != null) {
            tvTimeStats.setText(context.getString(R.string.app_blocker_tracked_vs_target, trackedStr, targetStr) + " (" + percent + "%)");
        }
        if (progressLimit != null) {
            progressLimit.setProgress(Math.min(100, percent));
        }

        // Return Home button handler
        if (btnReturnHome != null) {
            btnReturnHome.setOnClickListener(v -> {
                dismissOverlay();
                forceRedirectHome(context);
            });
        }
    }

    public static synchronized void dismissOverlay() {
        mainHandler.post(() -> {
            if (currentOverlayView != null && currentWindowManager != null) {
                try {
                    currentWindowManager.removeView(currentOverlayView);
                } catch (Exception ignored) {}
                currentOverlayView = null;
                currentWindowManager = null;
            }
            currentBlockedPackage = null;
        });
    }

    public static void forceRedirectHome(Context context) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(homeIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch home intent", e);
        }
    }

    private static String formatDuration(long millis) {
        long totalSecs = Math.max(0, millis / 1000);
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
