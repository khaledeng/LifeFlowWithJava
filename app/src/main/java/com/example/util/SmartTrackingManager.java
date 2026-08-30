package com.example.util;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.data.AppDatabase;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityCategory;

import java.util.Calendar;
import java.util.List;

public class SmartTrackingManager {

    private static final String PREFS_NAME = "smart_tracking_prefs";
    private static final String KEY_ENABLED = "smart_tracking_enabled";
    private static final String KEY_MANUAL_BASE_ID = "manual_base_activity_id";
    private static final String KEY_MANUAL_BASE_NAME = "manual_base_activity_name";
    private static final String KEY_MANUAL_BASE_TIMESTAMP = "manual_base_timestamp";
    private static final String KEY_DEFAULT_ACTIVITY_ID = "default_activity_id";
    private static final String KEY_DEFAULT_ACTIVITY_NAME = "default_activity_name";
    
    private final SharedPreferences prefs;

    public SmartTrackingManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setDefaultActivity(long activityId, String activityName) {
        prefs.edit()
                .putLong(KEY_DEFAULT_ACTIVITY_ID, activityId)
                .putString(KEY_DEFAULT_ACTIVITY_NAME, activityName)
                .apply();
    }

    public void setDefaultActivity(Activity activity) {
        if (activity == null) {
            clearDefaultActivity();
        } else {
            setDefaultActivity(activity.getId(), activity.getName());
        }
    }

    public void clearDefaultActivity() {
        prefs.edit()
                .remove(KEY_DEFAULT_ACTIVITY_ID)
                .remove(KEY_DEFAULT_ACTIVITY_NAME)
                .apply();
    }

    public long getDefaultActivityId() {
        return prefs.getLong(KEY_DEFAULT_ACTIVITY_ID, -1L);
    }

    public String getDefaultActivityName() {
        return prefs.getString(KEY_DEFAULT_ACTIVITY_NAME, null);
    }

    public boolean isDefaultActivity(long activityId) {
        return getDefaultActivityId() == activityId;
    }

    public boolean isDefaultActivity(Activity activity) {
        if (activity == null) return false;
        if (activity.getId() == getDefaultActivityId()) return true;
        String defName = getDefaultActivityName();
        return defName != null && activity.getName() != null && activity.getName().trim().equalsIgnoreCase(defName.trim());
    }

    public void setManualBaseActivity(long activityId, String activityName) {
        prefs.edit()
                .putLong(KEY_MANUAL_BASE_ID, activityId)
                .putString(KEY_MANUAL_BASE_NAME, activityName)
                .putLong(KEY_MANUAL_BASE_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    public void clearManualBaseActivity() {
        prefs.edit()
                .remove(KEY_MANUAL_BASE_ID)
                .remove(KEY_MANUAL_BASE_NAME)
                .remove(KEY_MANUAL_BASE_TIMESTAMP)
                .apply();
    }

    public long getManualBaseActivityId() {
        return prefs.getLong(KEY_MANUAL_BASE_ID, -1L);
    }

    public String getManualBaseActivityName() {
        return prefs.getString(KEY_MANUAL_BASE_NAME, null);
    }

    public long getManualBaseTimestamp() {
        return prefs.getLong(KEY_MANUAL_BASE_TIMESTAMP, 0L);
    }

    public boolean hasManualBaseActivity() {
        return getManualBaseActivityId() != -1L || getManualBaseActivityName() != null;
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public void setActivityTimeRange(Activity activity, int startHour, int startMin, int endHour, int endMin, boolean isTimeEnabled) {
        if (activity == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            editor.putInt(nameKey + "_start_h", startHour)
                  .putInt(nameKey + "_start_m", startMin)
                  .putInt(nameKey + "_end_h", endHour)
                  .putInt(nameKey + "_end_m", endMin)
                  .putBoolean(nameKey + "_time_enabled", isTimeEnabled);
        }
        String idKey = "act_" + activity.getId();
        editor.putInt(idKey + "_start_h", startHour)
              .putInt(idKey + "_start_m", startMin)
              .putInt(idKey + "_end_h", endHour)
              .putInt(idKey + "_end_m", endMin)
              .putBoolean(idKey + "_time_enabled", isTimeEnabled)
              .apply();
    }

    public void setActivityTimeRange(long activityId, int startHour, int startMin, int endHour, int endMin, boolean isTimeEnabled) {
        prefs.edit()
                .putInt("act_" + activityId + "_start_h", startHour)
                .putInt("act_" + activityId + "_start_m", startMin)
                .putInt("act_" + activityId + "_end_h", endHour)
                .putInt("act_" + activityId + "_end_m", endMin)
                .putBoolean("act_" + activityId + "_time_enabled", isTimeEnabled)
                .apply();
    }

    public boolean isActivityTimeEnabled(Activity activity) {
        if (activity == null) return false;
        if (prefs.getBoolean("act_" + activity.getId() + "_time_enabled", false)) return true;
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.getBoolean(nameKey + "_time_enabled", false)) return true;
        }
        return false;
    }

    public boolean isActivityTimeEnabled(long activityId) {
        return prefs.getBoolean("act_" + activityId + "_time_enabled", false);
    }
    
    public int getActivityStartHour(Activity activity) {
        if (activity == null) return 8;
        if (prefs.contains("act_" + activity.getId() + "_start_h")) {
            return prefs.getInt("act_" + activity.getId() + "_start_h", 8);
        }
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.contains(nameKey + "_start_h")) {
                return prefs.getInt(nameKey + "_start_h", 8);
            }
        }
        return 8;
    }
    public int getActivityStartHour(long activityId) { return prefs.getInt("act_" + activityId + "_start_h", 8); }

    public int getActivityStartMinute(Activity activity) {
        if (activity == null) return 0;
        if (prefs.contains("act_" + activity.getId() + "_start_m")) {
            return prefs.getInt("act_" + activity.getId() + "_start_m", 0);
        }
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.contains(nameKey + "_start_m")) {
                return prefs.getInt(nameKey + "_start_m", 0);
            }
        }
        return 0;
    }
    public int getActivityStartMinute(long activityId) { return prefs.getInt("act_" + activityId + "_start_m", 0); }

    public int getActivityEndHour(Activity activity) {
        if (activity == null) return 9;
        if (prefs.contains("act_" + activity.getId() + "_end_h")) {
            return prefs.getInt("act_" + activity.getId() + "_end_h", 9);
        }
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.contains(nameKey + "_end_h")) {
                return prefs.getInt(nameKey + "_end_h", 9);
            }
        }
        return 9;
    }
    public int getActivityEndHour(long activityId) { return prefs.getInt("act_" + activityId + "_end_h", 9); }

    public int getActivityEndMinute(Activity activity) {
        if (activity == null) return 0;
        if (prefs.contains("act_" + activity.getId() + "_end_m")) {
            return prefs.getInt("act_" + activity.getId() + "_end_m", 0);
        }
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.contains(nameKey + "_end_m")) {
                return prefs.getInt(nameKey + "_end_m", 0);
            }
        }
        return 0;
    }
    public int getActivityEndMinute(long activityId) { return prefs.getInt("act_" + activityId + "_end_m", 0); }

    public void setActivityBoundApps(Activity activity, java.util.Set<String> packageNames) {
        if (activity == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        java.util.Set<String> safeSet = (packageNames != null) ? new java.util.HashSet<>(packageNames) : new java.util.HashSet<>();
        
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT) + "_apps";
            if (safeSet.isEmpty()) {
                editor.remove(nameKey);
            } else {
                editor.putStringSet(nameKey, safeSet);
            }
        }
        String idKey = "act_" + activity.getId() + "_apps";
        if (safeSet.isEmpty()) {
            editor.remove(idKey);
        } else {
            editor.putStringSet(idKey, safeSet);
        }
        editor.apply();
    }

    public void setActivityBoundApps(long activityId, java.util.Set<String> packageNames) {
        java.util.Set<String> safeSet = (packageNames != null) ? new java.util.HashSet<>(packageNames) : new java.util.HashSet<>();
        if (safeSet.isEmpty()) {
            prefs.edit().remove("act_" + activityId + "_apps").apply();
        } else {
            prefs.edit().putStringSet("act_" + activityId + "_apps", safeSet).apply();
        }
    }

    public java.util.Set<String> getActivityBoundApps(Activity activity) {
        java.util.Set<String> result = new java.util.HashSet<>();
        if (activity == null) return result;
        
        java.util.Set<String> fromId = prefs.getStringSet("act_" + activity.getId() + "_apps", null);
        if (fromId != null && !fromId.isEmpty()) {
            result.addAll(fromId);
        }
        
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT) + "_apps";
            java.util.Set<String> fromName = prefs.getStringSet(nameKey, null);
            if (fromName != null && !fromName.isEmpty()) {
                result.addAll(fromName);
            }
        }
        return result;
    }

    public java.util.Set<String> getActivityBoundApps(long activityId) {
        return prefs.getStringSet("act_" + activityId + "_apps", new java.util.HashSet<>());
    }

    public void setActivityAppLockEnabled(Activity activity, boolean enabled) {
        if (activity == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            editor.putBoolean(nameKey + "_app_lock", enabled);
        }
        editor.putBoolean("act_" + activity.getId() + "_app_lock", enabled);
        editor.apply();
    }

    public void setActivityAppLockEnabled(long activityId, boolean enabled) {
        prefs.edit().putBoolean("act_" + activityId + "_app_lock", enabled).apply();
    }

    public boolean isActivityAppLockEnabled(Activity activity) {
        if (activity == null) return false;
        if (prefs.getBoolean("act_" + activity.getId() + "_app_lock", false)) return true;
        if (activity.getName() != null && !activity.getName().trim().isEmpty()) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.getBoolean(nameKey + "_app_lock", false)) return true;
        }
        return false;
    }

    public boolean isActivityAppLockEnabled(long activityId) {
        return prefs.getBoolean("act_" + activityId + "_app_lock", false);
    }

    public static class ActivityGoalStatus {
        public final boolean hasGoal;
        public final boolean isExceeded;
        public final long trackedTodayMillis;
        public final long targetGoalMillis;
        public final float percent;

        public ActivityGoalStatus(boolean hasGoal, boolean isExceeded, long trackedTodayMillis, long targetGoalMillis, float percent) {
            this.hasGoal = hasGoal;
            this.isExceeded = isExceeded;
            this.trackedTodayMillis = trackedTodayMillis;
            this.targetGoalMillis = targetGoalMillis;
            this.percent = percent;
        }
    }

    public static ActivityGoalStatus getActivityDailyGoalStatus(Context context, Activity activity, com.example.data.dao.SessionDao sessionDao) {
        if (activity == null || sessionDao == null) {
            return new ActivityGoalStatus(false, false, 0L, 0L, 0f);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        long now = System.currentTimeMillis();

        List<com.example.data.entity.SessionEntity> todaySessions = sessionDao.getSessionsInRangeSync(startOfDay, now + 86400000L);
        long baseClosedToday = 0;
        if (todaySessions != null) {
            for (com.example.data.entity.SessionEntity s : todaySessions) {
                boolean matches = (s.getActivityId() == activity.getId())
                        || (activity.getName() != null && s.getActivityName() != null && s.getActivityName().trim().equalsIgnoreCase(activity.getName().trim()));
                if (matches && s.getEndTime() > 0) {
                    long sStart = Math.max(s.getStartTime(), startOfDay);
                    long sEnd = Math.min(s.getEndTime(), now);
                    if (sEnd > sStart) {
                        baseClosedToday += (sEnd - sStart);
                    }
                }
            }
        }

        com.example.data.entity.SessionEntity active = sessionDao.getActiveSessionSync();
        long activeElapsedToday = 0;
        if (active != null && active.isActive()) {
            boolean matches = (active.getActivityId() == activity.getId())
                    || (activity.getName() != null && active.getActivityName() != null && active.getActivityName().trim().equalsIgnoreCase(activity.getName().trim()));
            if (matches) {
                long activeSessionStartToday = Math.max(startOfDay, active.getStartTime());
                activeElapsedToday = Math.max(0, now - activeSessionStartToday);
            }
        }

        long totalToday = baseClosedToday + activeElapsedToday;

        long targetMillis = 0;
        if (activity.getExpectedHoursPerDay() > 0) {
            targetMillis = (long) (activity.getExpectedHoursPerDay() * 3600f * 1000f);
        }

        SmartTrackingManager smart = new SmartTrackingManager(context);
        if (targetMillis <= 0 && smart.isActivityTimeEnabled(activity)) {
            int sh = smart.getActivityStartHour(activity);
            int sm = smart.getActivityStartMinute(activity);
            int eh = smart.getActivityEndHour(activity);
            int em = smart.getActivityEndMinute(activity);
            int durMin = (eh * 60 + em) - (sh * 60 + sm);
            if (durMin < 0) durMin += 1440;
            if (durMin > 0) {
                targetMillis = durMin * 60L * 1000L;
            }
        }

        if (targetMillis <= 0) {
            boolean isLockOn = smart.isActivityAppLockEnabled(activity);
            return new ActivityGoalStatus(isLockOn, isLockOn, totalToday, 0L, isLockOn ? 100f : 0f);
        }

        float percent = ((float) totalToday / targetMillis) * 100f;
        boolean isExceeded = totalToday >= targetMillis;

        return new ActivityGoalStatus(true, isExceeded, totalToday, targetMillis, percent);
    }

    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return android.provider.Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static boolean hasUsagePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        }
        return true;
    }

    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void openUsageSettings(Context context) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    public static void openOverlaySettings(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + context.getPackageName())
                );
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    public static void openNotificationSettings(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception ignored) {}
    }

    public static boolean isTimeInRange(int currentHour, int currentMin, int startHour, int startMin, int endHour, int endMin) {
        int cur = currentHour * 60 + currentMin;
        int start = startHour * 60 + startMin;
        int end = endHour * 60 + endMin;
        if (start <= end) {
            return cur >= start && cur <= end;
        } else {
            return cur >= start || cur <= end;
        }
    }

    public static String getRealForegroundPackage(Context context) {
        if (!hasUsagePermission(context)) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long time = System.currentTimeMillis();
            String lastPackage = null;
            long maxTime = 0;

            // 1. Primary: query UsageEvents in short recent window (60s then fallback 10m)
            try {
                UsageEvents events = usm.queryEvents(time - 60 * 1000L, time + 1000L);
                if (events != null && events.hasNextEvent()) {
                    UsageEvents.Event event = new UsageEvents.Event();
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event);
                        int eventType = event.getEventType();
                        if (eventType == UsageEvents.Event.ACTIVITY_RESUMED || eventType == 1) {
                            String pkg = event.getPackageName();
                            if (pkg != null && event.getTimeStamp() >= maxTime) {
                                maxTime = event.getTimeStamp();
                                lastPackage = pkg;
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }

            if (lastPackage == null) {
                try {
                    UsageEvents events = usm.queryEvents(time - 10 * 60 * 1000L, time);
                    if (events != null) {
                        UsageEvents.Event event = new UsageEvents.Event();
                        while (events.hasNextEvent()) {
                            events.getNextEvent(event);
                            int eventType = event.getEventType();
                            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED || eventType == 1) {
                                String pkg = event.getPackageName();
                                if (pkg != null && event.getTimeStamp() >= maxTime) {
                                    maxTime = event.getTimeStamp();
                                    lastPackage = pkg;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }
            
            // 2. Fallback / comparison with UsageStats
            try {
                List<android.app.usage.UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, time - 60 * 1000L, time);
                if (stats != null && !stats.isEmpty()) {
                    for (android.app.usage.UsageStats usageStats : stats) {
                        if (usageStats.getLastTimeUsed() > maxTime) {
                            String pkg = usageStats.getPackageName();
                            if (pkg != null) {
                                maxTime = usageStats.getLastTimeUsed();
                                lastPackage = pkg;
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }
            
            // 3. Fallback to ActivityManager running tasks
            if (lastPackage == null) {
                try {
                    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                        if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                            String pkg = tasks.get(0).topActivity.getPackageName();
                            if (pkg != null) {
                                lastPackage = pkg;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            return lastPackage;
        }
        return null;
    }

    public void overrideActiveTimeSchedules(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        java.util.Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("act_") && key.endsWith("_time_enabled")) {
                if (Boolean.TRUE.equals(all.get(key))) {
                    try {
                        String idStr = key.substring("act_".length(), key.length() - "_time_enabled".length());
                        long actId = Long.parseLong(idStr);
                        int startH = getActivityStartHour(actId);
                        int startM = getActivityStartMinute(actId);
                        int endH = getActivityEndHour(actId);
                        int endM = getActivityEndMinute(actId);
                        if (isTimeInRange(hour, minute, startH, startM, endH, endM)) {
                            prefs.edit().putLong("override_time_" + actId, now).apply();
                        }
                    } catch (Exception ignored) {}
                }
            } else if (key.startsWith("act_name_") && key.endsWith("_time_enabled")) {
                if (Boolean.TRUE.equals(all.get(key))) {
                    try {
                        String nameKey = key.substring(0, key.length() - "_time_enabled".length());
                        int startH = prefs.getInt(nameKey + "_start_h", 8);
                        int startM = prefs.getInt(nameKey + "_start_m", 0);
                        int endH = prefs.getInt(nameKey + "_end_h", 9);
                        int endM = prefs.getInt(nameKey + "_end_m", 0);
                        if (isTimeInRange(hour, minute, startH, startM, endH, endM)) {
                            prefs.edit().putLong("override_time_" + nameKey, now).apply();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public long getScheduleWindowStartTime(int currentH, int currentM, int startH, int startM, int endH, int endM) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, startH);
        cal.set(Calendar.MINUTE, startM);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If span midnight (e.g. 23:00 to 07:00) and current hour is past midnight (e.g. 03:00 < 23:00)
        if (startH > endH && currentH < startH) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        return cal.getTimeInMillis();
    }

    public boolean isScheduleOverridden(Activity activity, int currentH, int currentM, int startH, int startM, int endH, int endM) {
        if (activity == null) return false;
        long overrideTime = prefs.getLong("override_time_" + activity.getId(), 0L);
        if (overrideTime == 0L && activity.getName() != null) {
            String nameKey = "act_name_" + activity.getName().trim().toLowerCase(java.util.Locale.ROOT);
            overrideTime = prefs.getLong("override_time_" + nameKey, 0L);
        }
        if (overrideTime == 0L) return false;

        long windowStart = getScheduleWindowStartTime(currentH, currentM, startH, startM, endH, endM);
        return overrideTime >= windowStart;
    }

    public boolean isScheduleOverridden(long actId, int currentH, int currentM, int startH, int startM, int endH, int endM) {
        long overrideTime = prefs.getLong("override_time_" + actId, 0L);
        if (overrideTime == 0L) return false;

        long windowStart = getScheduleWindowStartTime(currentH, currentM, startH, startM, endH, endM);
        return overrideTime >= windowStart;
    }

    public String determineTargetActivityName(Context context, List<Activity> allActivities) {
        return determineTargetActivityName(context, allActivities, null);
    }

    public String determineTargetActivityName(Context context, List<Activity> allActivities, com.example.data.dao.SessionDao sessionDao) {
        if (allActivities == null || allActivities.isEmpty()) return null;

        String realPkg = getRealForegroundPackage(context);

        // 1. Check App Binding (Highest priority: when user opens a bound app like TikTok -> Entertainment)
        if (realPkg != null && !realPkg.equals(context.getPackageName())) {
            String lowerRealPkg = realPkg.trim().toLowerCase(java.util.Locale.ROOT);
            for (Activity act : allActivities) {
                java.util.Set<String> boundApps = getActivityBoundApps(act);
                if (boundApps != null && !boundApps.isEmpty()) {
                    boolean matches = false;
                    for (String bp : boundApps) {
                        if (bp != null && bp.trim().toLowerCase(java.util.Locale.ROOT).equals(lowerRealPkg)) {
                            matches = true;
                            break;
                        }
                    }
                    if (matches) {
                        if (sessionDao != null && isActivityAppLockEnabled(act)) {
                            ActivityGoalStatus status = getActivityDailyGoalStatus(context, act, sessionDao);
                            if (status.isExceeded) {
                                com.example.ui.blocker.AppBlockerManager.blockApp(
                                        context,
                                        act.getName(),
                                        act.getColorHex(),
                                        act.getIconName(),
                                        realPkg,
                                        status.trackedTodayMillis,
                                        status.targetGoalMillis
                                );
                                break; // Skip switching to this locked activity, fall through to schedule/manual/default
                            } else {
                                com.example.ui.blocker.AppBlockerManager.dismissOverlay();
                            }
                        } else {
                            com.example.ui.blocker.AppBlockerManager.dismissOverlay();
                        }
                        return act.getName();
                    }
                }
            }
        }

        // 2. Check Active Time Schedule (e.g. Work 4 PM - 8 PM or Sleep)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        Activity matchingScheduleActivity = null;
        long matchingScheduleWindowStart = 0L;

        for (Activity act : allActivities) {
            if (isActivityTimeEnabled(act)) {
                int startH = getActivityStartHour(act);
                int startM = getActivityStartMinute(act);
                int endH = getActivityEndHour(act);
                int endM = getActivityEndMinute(act);
                if (isTimeInRange(hour, minute, startH, startM, endH, endM)) {
                    if (!isScheduleOverridden(act, hour, minute, startH, startM, endH, endM)) {
                        matchingScheduleActivity = act;
                        matchingScheduleWindowStart = getScheduleWindowStartTime(hour, minute, startH, startM, endH, endM);
                        break;
                    }
                }
            }
        }

        long manualTimestamp = getManualBaseTimestamp();
        long manualId = getManualBaseActivityId();
        String manualName = getManualBaseActivityName();

        // If user manually switched to another activity *after* the schedule began, manual activity takes priority
        if (manualTimestamp > 0 && matchingScheduleActivity != null && manualTimestamp >= matchingScheduleWindowStart) {
            for (Activity act : allActivities) {
                if (act.getId() == manualId || (manualName != null && act.getName().equalsIgnoreCase(manualName))) {
                    return act.getName();
                }
            }
        }

        // Otherwise, the active time schedule takes priority
        if (matchingScheduleActivity != null) {
            return matchingScheduleActivity.getName();
        }

        // 3. If no active time schedule, return manual base activity if set
        if (manualId != -1L || manualName != null) {
            for (Activity act : allActivities) {
                if (act.getId() == manualId || (manualName != null && act.getName().equalsIgnoreCase(manualName))) {
                    return act.getName();
                }
            }
        }

        // 4. Default Activity Fallback (When no time schedule matches & no manual activity is active)
        long defaultId = getDefaultActivityId();
        String defaultName = getDefaultActivityName();
        if (defaultId != -1L || defaultName != null) {
            for (Activity act : allActivities) {
                if (act.getId() == defaultId || (defaultName != null && act.getName().equalsIgnoreCase(defaultName))) {
                    return act.getName();
                }
            }
        }

        // 5. Fallback if nothing matches
        return null;
    }
}
