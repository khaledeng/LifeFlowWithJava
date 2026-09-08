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
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class SmartTrackingManager {

    public static class TimeInterval {
        public int startHour;
        public int startMinute;
        public int endHour;
        public int endMinute;

        public TimeInterval() {
            this(8, 0, 9, 0);
        }

        public TimeInterval(int startHour, int startMinute, int endHour, int endMinute) {
            this.startHour = startHour;
            this.startMinute = startMinute;
            this.endHour = endHour;
            this.endMinute = endMinute;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TimeInterval that = (TimeInterval) o;
            return startHour == that.startHour &&
                    startMinute == that.startMinute &&
                    endHour == that.endHour &&
                    endMinute == that.endMinute;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startHour, startMinute, endHour, endMinute);
        }
    }

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

    public List<TimeInterval> getActivityTimeIntervals(Activity activity) {
        if (activity == null) {
            List<TimeInterval> list = new ArrayList<>();
            list.add(new TimeInterval(8, 0, 9, 0));
            return list;
        }
        return getActivityTimeIntervals(activity.getId(), activity.getName());
    }

    public List<TimeInterval> getActivityTimeIntervals(long activityId) {
        return getActivityTimeIntervals(activityId, null);
    }

    public List<TimeInterval> getActivityTimeIntervals(long activityId, String activityName) {
        List<TimeInterval> result = new ArrayList<>();
        String json = prefs.getString("act_" + activityId + "_intervals_json", null);
        if ((json == null || json.trim().isEmpty()) && activityName != null && !activityName.trim().isEmpty()) {
            String nameKey = "act_name_" + activityName.trim().toLowerCase(java.util.Locale.ROOT);
            json = prefs.getString(nameKey + "_intervals_json", null);
        }

        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        int sh = obj.optInt("startHour", obj.optInt("sh", 8));
                        int sm = obj.optInt("startMinute", obj.optInt("sm", 0));
                        int eh = obj.optInt("endHour", obj.optInt("eh", 9));
                        int em = obj.optInt("endMinute", obj.optInt("em", 0));
                        result.add(new TimeInterval(sh, sm, eh, em));
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Exception ignored) {}
        }

        // Fallback to legacy single interval
        int sh = 8, sm = 0, eh = 9, em = 0;
        if (prefs.contains("act_" + activityId + "_start_h")) {
            sh = prefs.getInt("act_" + activityId + "_start_h", 8);
            sm = prefs.getInt("act_" + activityId + "_start_m", 0);
            eh = prefs.getInt("act_" + activityId + "_end_h", 9);
            em = prefs.getInt("act_" + activityId + "_end_m", 0);
        } else if (activityName != null && !activityName.trim().isEmpty()) {
            String nameKey = "act_name_" + activityName.trim().toLowerCase(java.util.Locale.ROOT);
            if (prefs.contains(nameKey + "_start_h")) {
                sh = prefs.getInt(nameKey + "_start_h", 8);
                sm = prefs.getInt(nameKey + "_start_m", 0);
                eh = prefs.getInt(nameKey + "_end_h", 9);
                em = prefs.getInt(nameKey + "_end_m", 0);
            }
        }

        result.add(new TimeInterval(sh, sm, eh, em));
        return result;
    }

    public void setActivityTimeIntervals(Activity activity, List<TimeInterval> intervals, boolean isTimeEnabled) {
        if (activity == null) return;
        setActivityTimeIntervals(activity.getId(), activity.getName(), intervals, isTimeEnabled);
    }

    public void setActivityTimeIntervals(long activityId, List<TimeInterval> intervals, boolean isTimeEnabled) {
        setActivityTimeIntervals(activityId, null, intervals, isTimeEnabled);
    }

    public void setActivityTimeIntervals(long activityId, String activityName, List<TimeInterval> intervals, boolean isTimeEnabled) {
        List<TimeInterval> safeIntervals = (intervals != null && !intervals.isEmpty()) ? new ArrayList<>(intervals) : new ArrayList<>();
        if (safeIntervals.isEmpty()) {
            safeIntervals.add(new TimeInterval(8, 0, 9, 0));
        }

        JSONArray arr = new JSONArray();
        for (TimeInterval ti : safeIntervals) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("startHour", ti.startHour);
                obj.put("startMinute", ti.startMinute);
                obj.put("endHour", ti.endHour);
                obj.put("endMinute", ti.endMinute);
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        String json = arr.toString();
        SharedPreferences.Editor editor = prefs.edit();

        TimeInterval first = safeIntervals.get(0);

        if (activityName != null && !activityName.trim().isEmpty()) {
            String nameKey = "act_name_" + activityName.trim().toLowerCase(java.util.Locale.ROOT);
            editor.putString(nameKey + "_intervals_json", json)
                  .putInt(nameKey + "_start_h", first.startHour)
                  .putInt(nameKey + "_start_m", first.startMinute)
                  .putInt(nameKey + "_end_h", first.endHour)
                  .putInt(nameKey + "_end_m", first.endMinute)
                  .putBoolean(nameKey + "_time_enabled", isTimeEnabled);
        }

        String idKey = "act_" + activityId;
        editor.putString(idKey + "_intervals_json", json)
              .putInt(idKey + "_start_h", first.startHour)
              .putInt(idKey + "_start_m", first.startMinute)
              .putInt(idKey + "_end_h", first.endHour)
              .putInt(idKey + "_end_m", first.endMinute)
              .putBoolean(idKey + "_time_enabled", isTimeEnabled)
              .apply();
    }

    public void addActivityTimeInterval(Activity activity, int startH, int startM, int endH, int endM) {
        if (activity == null) return;
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        intervals.add(new TimeInterval(startH, startM, endH, endM));
        setActivityTimeIntervals(activity, intervals, true);
    }

    public void updateActivityTimeInterval(Activity activity, int index, int startH, int startM, int endH, int endM) {
        if (activity == null) return;
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        if (index >= 0 && index < intervals.size()) {
            intervals.set(index, new TimeInterval(startH, startM, endH, endM));
            setActivityTimeIntervals(activity, intervals, isActivityTimeEnabled(activity));
        }
    }

    public void removeActivityTimeInterval(Activity activity, int index) {
        if (activity == null) return;
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        if (index >= 0 && index < intervals.size()) {
            intervals.remove(index);
            if (intervals.isEmpty()) {
                intervals.add(new TimeInterval(8, 0, 9, 0));
            }
            setActivityTimeIntervals(activity, intervals, isActivityTimeEnabled(activity));
        }
    }

    public void setActivityTimeRange(Activity activity, int startHour, int startMin, int endHour, int endMin, boolean isTimeEnabled) {
        if (activity == null) return;
        List<TimeInterval> list = new ArrayList<>();
        list.add(new TimeInterval(startHour, startMin, endHour, endMin));
        setActivityTimeIntervals(activity, list, isTimeEnabled);
    }

    public void setActivityTimeRange(long activityId, int startHour, int startMin, int endHour, int endMin, boolean isTimeEnabled) {
        List<TimeInterval> list = new ArrayList<>();
        list.add(new TimeInterval(startHour, startMin, endHour, endMin));
        setActivityTimeIntervals(activityId, list, isTimeEnabled);
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
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        return !intervals.isEmpty() ? intervals.get(0).startHour : 8;
    }
    public int getActivityStartHour(long activityId) {
        List<TimeInterval> intervals = getActivityTimeIntervals(activityId);
        return !intervals.isEmpty() ? intervals.get(0).startHour : 8;
    }

    public int getActivityStartMinute(Activity activity) {
        if (activity == null) return 0;
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        return !intervals.isEmpty() ? intervals.get(0).startMinute : 0;
    }
    public int getActivityStartMinute(long activityId) {
        List<TimeInterval> intervals = getActivityTimeIntervals(activityId);
        return !intervals.isEmpty() ? intervals.get(0).startMinute : 0;
    }

    public int getActivityEndHour(Activity activity) {
        if (activity == null) return 9;
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        return !intervals.isEmpty() ? intervals.get(0).endHour : 9;
    }
    public int getActivityEndHour(long activityId) {
        List<TimeInterval> intervals = getActivityTimeIntervals(activityId);
        return !intervals.isEmpty() ? intervals.get(0).endHour : 9;
    }

    public int getActivityEndMinute(Activity activity) {
        if (activity == null) return 0;
        List<TimeInterval> intervals = getActivityTimeIntervals(activity);
        return !intervals.isEmpty() ? intervals.get(0).endMinute : 0;
    }
    public int getActivityEndMinute(long activityId) {
        List<TimeInterval> intervals = getActivityTimeIntervals(activityId);
        return !intervals.isEmpty() ? intervals.get(0).endMinute : 0;
    }

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
            List<TimeInterval> intervals = smart.getActivityTimeIntervals(activity);
            long totalSchedMillis = 0;
            for (TimeInterval ti : intervals) {
                int durMin = (ti.endHour * 60 + ti.endMinute) - (ti.startHour * 60 + ti.startMinute);
                if (durMin < 0) durMin += 1440;
                totalSchedMillis += durMin * 60L * 1000L;
            }
            if (totalSchedMillis > 0) {
                targetMillis = totalSchedMillis;
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
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return android.provider.Settings.canDrawOverlays(context);
            } catch (Throwable t) {
                android.util.Log.e("SmartTrackingManager", "Error checking overlay permission", t);
                return false;
            }
        }
        return true;
    }

    public static boolean hasUsagePermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                if (appOps == null) return false;
                int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), context.getPackageName());
                return mode == AppOpsManager.MODE_ALLOWED;
            } catch (Throwable t) {
                android.util.Log.e("SmartTrackingManager", "Error checking usage stats permission", t);
                return false;
            }
        }
        return true;
    }

    public static boolean hasNotificationPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                return androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) {
                android.util.Log.e("SmartTrackingManager", "Error checking notification permission", t);
                return false;
            }
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
                        List<TimeInterval> intervals = getActivityTimeIntervals(actId);
                        for (TimeInterval ti : intervals) {
                            if (isTimeInRange(hour, minute, ti.startHour, ti.startMinute, ti.endHour, ti.endMinute)) {
                                prefs.edit().putLong("override_time_" + actId, now).apply();
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } else if (key.startsWith("act_name_") && key.endsWith("_time_enabled")) {
                if (Boolean.TRUE.equals(all.get(key))) {
                    try {
                        String nameKey = key.substring(0, key.length() - "_time_enabled".length());
                        String name = nameKey.substring("act_name_".length());
                        List<TimeInterval> intervals = getActivityTimeIntervals(-1L, name);
                        for (TimeInterval ti : intervals) {
                            if (isTimeInRange(hour, minute, ti.startHour, ti.startMinute, ti.endHour, ti.endMinute)) {
                                prefs.edit().putLong("override_time_" + nameKey, now).apply();
                                break;
                            }
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

        // 2. Check Active Time Schedule (e.g. Work 8 AM - 10 AM, 1 PM - 3 PM or Sleep)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        Activity matchingScheduleActivity = null;
        long matchingScheduleWindowStart = 0L;

        for (Activity act : allActivities) {
            if (isActivityTimeEnabled(act)) {
                List<TimeInterval> intervals = getActivityTimeIntervals(act);
                for (TimeInterval interval : intervals) {
                    int startH = interval.startHour;
                    int startM = interval.startMinute;
                    int endH = interval.endHour;
                    int endM = interval.endMinute;
                    if (isTimeInRange(hour, minute, startH, startM, endH, endM)) {
                        if (!isScheduleOverridden(act, hour, minute, startH, startM, endH, endM)) {
                            matchingScheduleActivity = act;
                            matchingScheduleWindowStart = getScheduleWindowStartTime(hour, minute, startH, startM, endH, endM);
                            break;
                        }
                    }
                }
                if (matchingScheduleActivity != null) {
                    break;
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
