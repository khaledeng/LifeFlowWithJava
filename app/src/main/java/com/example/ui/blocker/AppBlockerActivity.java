package com.example.ui.blocker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.util.IconHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

public class AppBlockerActivity extends AppCompatActivity {

    public static final String EXTRA_ACTIVITY_NAME = "extra_activity_name";
    public static final String EXTRA_ACTIVITY_COLOR = "extra_activity_color";
    public static final String EXTRA_ACTIVITY_ICON = "extra_activity_icon";
    public static final String EXTRA_BLOCKED_APP_PKG = "extra_blocked_app_pkg";
    public static final String EXTRA_TRACKED_MILLIS = "extra_tracked_millis";
    public static final String EXTRA_TARGET_MILLIS = "extra_target_millis";

    private static long lastLaunchTimestamp = 0L;
    private static volatile boolean isActivityResumed = false;
    private static final int BLOCKER_NOTIFICATION_ID = 88991;
    private static final String BLOCKER_CHANNEL_ID = "lifeflow_app_blocker_channel";

    public static synchronized void launch(Context context, String activityName, String colorHex, String iconName, String blockedPkg, long trackedMillis, long targetMillis) {
        if (isActivityResumed) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLaunchTimestamp < 500L) {
            return;
        }
        lastLaunchTimestamp = now;

        Intent intent = new Intent(context, AppBlockerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(EXTRA_ACTIVITY_NAME, activityName);
        intent.putExtra(EXTRA_ACTIVITY_COLOR, colorHex);
        intent.putExtra(EXTRA_ACTIVITY_ICON, iconName);
        intent.putExtra(EXTRA_BLOCKED_APP_PKG, blockedPkg);
        intent.putExtra(EXTRA_TRACKED_MILLIS, trackedMillis);
        intent.putExtra(EXTRA_TARGET_MILLIS, targetMillis);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("AppBlockerActivity", "Direct startActivity failed", e);
        }

        // Secondary guarantee: trigger high-priority FullScreenIntent notification
        showFullScreenBlockerNotification(context, intent, activityName);
    }

    private static void showFullScreenBlockerNotification(Context context, Intent blockerIntent, String activityName) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        BLOCKER_CHANNEL_ID,
                        "App Lock Alerts",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Immediate full-screen blocker when locked apps are opened");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 200, 100, 200});
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                channel.setBypassDnd(true);
                nm.createNotificationChannel(channel);
            }

            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }

            android.app.PendingIntent fullScreenPendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    BLOCKER_NOTIFICATION_ID,
                    blockerIntent,
                    flags
            );

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, BLOCKER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_time)
                    .setContentTitle(context.getString(R.string.app_lock_notification_title, activityName != null ? activityName : "Activity"))
                    .setContentText(context.getString(R.string.app_lock_notification_text))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setAutoCancel(true);

            nm.notify(BLOCKER_NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            android.util.Log.e("AppBlockerActivity", "Notification trigger error", e);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_app_blocker);
        renderData(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderData(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityResumed = true;
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(BLOCKER_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityResumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityResumed = false;
    }

    private void renderData(Intent intent) {
        if (intent == null) return;

        String activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME);
        if (activityName == null) activityName = "Activity";

        String colorHex = intent.getStringExtra(EXTRA_ACTIVITY_COLOR);
        if (colorHex == null || colorHex.isEmpty()) colorHex = "#EF4444";

        String iconName = intent.getStringExtra(EXTRA_ACTIVITY_ICON);
        if (iconName == null || iconName.isEmpty()) iconName = "ic_work";

        String blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_APP_PKG);
        long trackedMillis = intent.getLongExtra(EXTRA_TRACKED_MILLIS, 0L);
        long targetMillis = intent.getLongExtra(EXTRA_TARGET_MILLIS, 0L);

        // Views
        TextView tvSubtitle = findViewById(R.id.tv_blocker_subtitle);
        ImageView ivActivityIcon = findViewById(R.id.iv_activity_icon);
        TextView tvActivityName = findViewById(R.id.tv_activity_name);
        TextView tvBlockedAppName = findViewById(R.id.tv_blocked_app_name);
        TextView tvTimeStats = findViewById(R.id.tv_time_stats);
        LinearProgressIndicator progressLimit = findViewById(R.id.progress_limit);
        MaterialButton btnReturnHome = findViewById(R.id.btn_return_home);

        // Activity details
        tvSubtitle.setText(getString(R.string.app_blocker_consumed_format, activityName));
        tvActivityName.setText(activityName);

        try {
            int parsedColor = Color.parseColor(colorHex);
            ivActivityIcon.setColorFilter(parsedColor);
            ivActivityIcon.setBackgroundTintList(ColorStateList.valueOf(parsedColor & 0x33FFFFFF));
        } catch (Exception ignored) { }

        int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
        if (resId != 0) {
            ivActivityIcon.setImageResource(resId);
        } else {
            ivActivityIcon.setImageDrawable(IconHelper.getDrawableForIcon(this, iconName));
        }

        // Blocked app name
        if (blockedPkg != null) {
            String appDisplayName = blockedPkg;
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo info = pm.getApplicationInfo(blockedPkg, 0);
                appDisplayName = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) { }
            tvBlockedAppName.setText(appDisplayName);
        }

        // Time stats
        String trackedStr = formatDuration(trackedMillis);
        String targetStr = formatDuration(targetMillis);
        int percent = targetMillis > 0 ? (int) Math.min(300, ((float) trackedMillis / targetMillis) * 100f) : 100;

        tvTimeStats.setText(getString(R.string.app_blocker_tracked_vs_target, trackedStr, targetStr) + " (" + percent + "%)");
        progressLimit.setProgress(Math.min(100, percent));

        btnReturnHome.setOnClickListener(v -> exitToHomeScreen());
    }

    private void exitToHomeScreen() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        exitToHomeScreen();
    }

    private String formatDuration(long millis) {
        long totalSecs = Math.max(0, millis / 1000);
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
