package com.example.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.MainActivity;
import com.example.R;
import com.example.data.AppDatabase;
import com.example.data.TrackingRepository;
import com.example.data.dao.ActivityDao;
import com.example.data.dao.SessionDao;
import com.example.data.entity.Activity;
import com.example.data.entity.SessionEntity;
import com.example.util.IconHelper;
import com.example.util.SubscriptionManager;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TrackingService extends Service {

    public static final String ACTION_START = "com.example.service.ACTION_START";
    public static final String ACTION_STOP = "com.example.service.ACTION_STOP";
    public static final String ACTION_PREV_GOAL = "com.example.service.ACTION_PREV_GOAL";
    public static final String ACTION_NEXT_GOAL = "com.example.service.ACTION_NEXT_GOAL";
    public static final String EXTRA_ACTIVITY_NAME = "extra_activity_name";

    private static final String CHANNEL_ID = "lifeflow_active_tracking_v2";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Handler smartCheckHandler = new Handler(Looper.getMainLooper());
    private boolean isTracking = false;
    private TrackingRepository repository;
    private AppDatabase database;

    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTracking) {
                updateNotificationContent();
                tickerHandler.postDelayed(this, 1000);
            }
        }
    };

    private final Runnable smartCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTracking) {
                checkSmartTracking();
                smartCheckHandler.postDelayed(this, 300);
            }
        }
    };

    private static String lastSmartTargetName = null;
    
    private void checkSmartTracking() {
        final com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(this);
        if (!smart.isEnabled()) {
            lastSmartTargetName = null;
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                java.util.List<com.example.data.entity.Activity> allActivities = database.activityDao().getAllActivitiesSync();
                if (allActivities == null || allActivities.isEmpty()) return;

                String targetName = smart.determineTargetActivityName(this, allActivities, database.sessionDao());
                if (targetName == null) return;
                
                com.example.data.entity.SessionEntity active = database.sessionDao().getActiveSessionSync();
                if (active != null && active.isActive() && active.getActivityName() != null && active.getActivityName().trim().equalsIgnoreCase(targetName.trim())) {
                    lastSmartTargetName = targetName;
                    return;
                }
                
                lastSmartTargetName = targetName;
                    
                    long targetId = -1;
                    for (com.example.data.entity.Activity act : allActivities) {
                        if (act.getName() != null && act.getName().trim().equalsIgnoreCase(targetName.trim())) {
                            targetId = act.getId();
                            break;
                        }
                    }

                    if (targetId != -1) {
                        repository.startActivity(targetId, true, () -> {
                            tickerHandler.post(() -> {
                                updateNotificationContent();
                                android.content.Intent broadcast = new android.content.Intent("com.example.ACTION_SMART_TRACKING_UPDATED");
                                sendBroadcast(broadcast);
                            });
                        });
                    }
            } catch (Exception e) { }
        });
    }

    public static void startTracking(Context context, String activityName) {
        Intent intent = new Intent(context, TrackingService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_ACTIVITY_NAME, activityName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopTracking(Context context) {
        Intent intent = new Intent(context, TrackingService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        repository = TrackingRepository.getInstance(this);
        database = AppDatabase.getDatabase(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME);
            if (activityName == null || activityName.isEmpty()) {
                activityName = "Active Tracking";
            }
            isTracking = true;
            Notification initialNotification = buildNotification(activityName, "Today: 00:00:00 - 0.0% of day");
            try {
                startForeground(NOTIFICATION_ID, initialNotification);
            } catch (Exception e) {
                android.util.Log.e("TrackingService", "Failed to start foreground service", e);
            }

            tickerHandler.removeCallbacks(tickerRunnable);
            tickerHandler.post(tickerRunnable);

            smartCheckHandler.removeCallbacks(smartCheckRunnable);
            smartCheckHandler.post(smartCheckRunnable);
            
            checkSmartTracking();
        } else if ("UPDATE_SMART_TRACKING".equals(action)) {
            isTracking = true;
            smartCheckHandler.removeCallbacks(smartCheckRunnable);
            smartCheckHandler.post(smartCheckRunnable);
            checkSmartTracking();
            updateNotificationContent();
        } else if (ACTION_STOP.equals(action)) {
            isTracking = false;
            tickerHandler.removeCallbacks(tickerRunnable);
            smartCheckHandler.removeCallbacks(smartCheckRunnable);
            repository.stopActiveSession(false, () -> {
                // Done stopping
            });
            try {
                stopForeground(true);
            } catch (Exception ignored) {}
            stopSelf();
        } else if (ACTION_PREV_GOAL.equals(action)) {
            isTracking = true;
            repository.switchToPreviousActivity(this::updateNotificationContent);
        } else if (ACTION_NEXT_GOAL.equals(action)) {
            isTracking = true;
            repository.switchToNextActivity(this::updateNotificationContent);
        }

        return START_STICKY;
    }

    private void updateNotificationContent() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!isTracking) return;

            SessionDao sessionDao = database.sessionDao();
            ActivityDao activityDao = database.activityDao();
            SessionEntity activeSession = sessionDao.getActiveSessionSync();

            if (activeSession == null || !activeSession.isActive()) {
                com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(TrackingService.this);
                if (smart.isEnabled()) {
                    checkSmartTracking();
                    android.app.Notification standby = buildNotification("الذكاء نشط (في وضع الانتظار)", "في انتظار التغيير التلقائي للتطبيق...", "ic_other", 0xFFAAAAAA);
                    android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) manager.notify(NOTIFICATION_ID, standby);
                    return;
                }
                isTracking = false;
                tickerHandler.removeCallbacks(tickerRunnable);
                try {
                    stopForeground(true);
                } catch (Exception ignored) {}
                stopSelf();
                return;
            }

            long activityId = activeSession.getActivityId();
            String title = activeSession.getActivityName();
            Activity act = activityDao.getActivityById(activityId);
            String iconName = act != null ? act.getIconName() : "ic_other";
            int activityColor = act != null ? IconHelper.parseColorOrDefault(act.getColorHex(), 0xFF39D353) : 0xFF39D353;

            // Calculate start of day
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            // Calculate closed sessions for this activity today
            List<SessionEntity> todaySessions = sessionDao.getSessionsInRangeSync(startOfDay, now + 86400000L);
            long baseClosedToday = 0;
            if (todaySessions != null) {
                for (SessionEntity s : todaySessions) {
                    if (s.getActivityId() == activityId && s.getEndTime() > 0) {
                        long sStart = Math.max(s.getStartTime(), startOfDay);
                        long sEnd = Math.min(s.getEndTime(), now);
                        if (sEnd > sStart) {
                            baseClosedToday += (sEnd - sStart);
                        }
                    }
                }
            }

            // Current live session duration today
            long activeSessionStartToday = Math.max(startOfDay, activeSession.getStartTime());
            long activeElapsedToday = Math.max(0, now - activeSessionStartToday);
            long totalActivityTodayMillis = baseClosedToday + activeElapsedToday;

            if (act != null) {
                com.example.util.ProgressNotificationManager.checkAndNotifyMilestone(TrackingService.this, act, totalActivityTodayMillis);
            }

            long totalSecs = totalActivityTodayMillis / 1000;
            long hours = totalSecs / 3600;
            long minutes = (totalSecs % 3600) / 60;
            long seconds = totalSecs % 60;
            String timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
            String subtitle;

            float targetMillis = act != null ? act.getExpectedHoursPerDay() * 3600f * 1000f : 0;
            if (targetMillis > 0) {
                float percentOfGoal = ((float) totalActivityTodayMillis / targetMillis) * 100f;
                subtitle = getString(com.example.R.string.notification_subtitle_goal, timeStr, percentOfGoal);
            } else {
                float percentOfDay = ((float) totalActivityTodayMillis / (24f * 3600f * 1000f)) * 100f;
                subtitle = getString(com.example.R.string.notification_subtitle_day, timeStr, percentOfDay);
            }

            Notification notification = buildNotification(title, subtitle, iconName, activityColor);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && isTracking) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LifeFlow Active Tracking",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows active time tracking status at top priority");
            channel.setShowBadge(true);
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String activityName, String contentText) {
        return buildNotification(activityName, contentText, "ic_other", 0xFF39D353);
    }

    private Notification buildNotification(String activityName, String contentText, String iconName, int color) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Previous Goal Action
        Intent prevIntent = new Intent(this, TrackingService.class);
        prevIntent.setAction(ACTION_PREV_GOAL);
        PendingIntent prevPendingIntent = PendingIntent.getService(
                this, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Stop Action
        Intent stopIntent = new Intent(this, TrackingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Next Goal Action
        Intent nextIntent = new Intent(this, TrackingService.class);
        nextIntent.setAction(ACTION_NEXT_GOAL);
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Bitmap largeIcon = IconHelper.createNotificationLargeIcon(this, iconName, color != 0 ? color : 0xFF39D353);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_time)
                .setLargeIcon(largeIcon)
                .setContentTitle(activityName)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(color != 0 ? color : 0xFF39D353)
                .setColorized(false)
                .addAction(R.drawable.ic_prev_goal, "Previous Goal", prevPendingIntent)
                .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
                .addAction(R.drawable.ic_next_goal, "Next Goal", nextPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    @Override
    public void onDestroy() {
        isTracking = false;
        tickerHandler.removeCallbacks(tickerRunnable);
        smartCheckHandler.removeCallbacks(smartCheckRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
