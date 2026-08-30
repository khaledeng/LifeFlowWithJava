package com.example.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.data.dao.ActivityDao;
import com.example.data.dao.SessionDao;
import com.example.data.entity.Activity;
import com.example.R;
import com.example.data.entity.ActivityCategory;
import com.example.data.entity.ActivityEntity;
import com.example.data.entity.SessionEntity;
import com.example.data.model.AllActivitiesMatrixData;
import com.example.data.model.ProgressDayData;
import com.example.data.model.ProgressSummary;
import com.example.data.model.ProgressWeekCardData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.example.util.IconHelper;
import com.example.ui.statistics.MultiLineStatsChartView;

public class TrackingRepository {

    private static volatile TrackingRepository INSTANCE;
    private static volatile boolean hasRepaired = false;

    private final Context appContext;
    private final AppDatabase database;
    private final ActivityDao activityDao;
    private final SessionDao sessionDao;
    private final com.example.data.dao.ActivitySessionDao activitySessionDao;
    private final com.example.data.dao.DailyProgressDao dailyProgressDao;
    private final LiveData<List<Activity>> allActivities;
    private final LiveData<List<SessionEntity>> allSessions;
    private final LiveData<SessionEntity> activeSession;

    public static TrackingRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TrackingRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TrackingRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public TrackingRepository(Context context) {
        this.appContext = context.getApplicationContext();
        database = AppDatabase.getDatabase(context.getApplicationContext());
        activityDao = database.activityDao();
        sessionDao = database.sessionDao();
        activitySessionDao = database.activitySessionDao();
        dailyProgressDao = database.dailyProgressDao();
        allActivities = activityDao.getAllActivitiesLive();
        allSessions = sessionDao.getAllSessionsLive();
        activeSession = sessionDao.getActiveSessionLive();

        // Ensure default items exist on initial run if DB was created previously without defaults
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!hasRepaired) {
                hasRepaired = true;
                deduplicateActivitiesInternal();
                repairOrphanedSessionsInternal();
            }
            if (activityDao.getActivityCountSync() == 0) {
                List<Activity> defaults = new ArrayList<>();
                long now = System.currentTimeMillis();
                defaults.add(new Activity("Work", com.example.data.entity.ActivityCategory.INCREASE, 8f, "#39D353", "ic_work", true, now));
                defaults.add(new Activity("Sleep", com.example.data.entity.ActivityCategory.NEUTRAL, 8f, "#8A80E6", "ic_sleep", true, now + 1));
                defaults.add(new Activity("Entertainment", com.example.data.entity.ActivityCategory.DECREASE, 8f, "#FF8C42", "ic_entertainment", true, now + 2));
                activityDao.insertAll(defaults);
            }
        });
    }

    private static String normalizeActivityName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("[^\\p{L}\\p{Nd}]", "").toLowerCase(Locale.ROOT);
        return cleaned.isEmpty() ? name.trim().toLowerCase(Locale.ROOT) : cleaned;
    }

    private void deduplicateActivitiesInternal() {
        try {
            List<Activity> acts = activityDao.getAllActivitiesSync();
            if (acts == null || acts.size() <= 1) return;

            Map<String, Activity> primaryMap = new HashMap<>();
            List<Activity> duplicatesToDelete = new ArrayList<>();

            for (Activity act : acts) {
                if (act == null || act.getName() == null) continue;
                String key = normalizeActivityName(act.getName());
                if (!primaryMap.containsKey(key)) {
                    primaryMap.put(key, act);
                } else {
                    Activity primary = primaryMap.get(key);
                    if (primary != null) {
                        List<SessionEntity> sessions = sessionDao.getAllSessionsSync();
                        if (sessions != null) {
                            for (SessionEntity s : sessions) {
                                if (s.getActivityId() == act.getId()) {
                                    s.setActivityId(primary.getId());
                                    s.setActivityName(primary.getName());
                                    s.setActivityColorHex(primary.getColorHex());
                                    s.setActivityIconName(primary.getIconName());
                                    sessionDao.updateSession(s);
                                }
                            }
                        }
                    }
                    duplicatesToDelete.add(act);
                }
            }

            for (Activity dup : duplicatesToDelete) {
                activityDao.deleteActivity(dup);
            }
        } catch (Exception ignored) {
        }
    }

    private void repairOrphanedSessionsInternal() {
        try {
            List<Activity> acts = activityDao.getAllActivitiesSync();
            List<SessionEntity> sessions = sessionDao.getAllSessionsSync();
            if (acts == null || acts.isEmpty() || sessions == null || sessions.isEmpty()) return;

            Map<String, Activity> nameMap = new HashMap<>();
            Map<Long, Activity> idMap = new HashMap<>();
            for (Activity ae : acts) {
                idMap.put(ae.getId(), ae);
                if (ae.getName() != null) {
                    nameMap.put(normalizeActivityName(ae.getName()), ae);
                    nameMap.put(ae.getName().trim().toLowerCase(Locale.ROOT), ae);
                }
            }

            for (SessionEntity s : sessions) {
                Activity match = idMap.get(s.getActivityId());
                if (match == null && s.getActivityName() != null) {
                    match = nameMap.get(normalizeActivityName(s.getActivityName()));
                    if (match == null) {
                        match = nameMap.get(s.getActivityName().trim().toLowerCase(Locale.ROOT));
                    }
                }

                if (match != null && (s.getActivityId() != match.getId() || !match.getName().equals(s.getActivityName()))) {
                    s.setActivityId(match.getId());
                    s.setActivityName(match.getName());
                    s.setActivityColorHex(match.getColorHex());
                    s.setActivityIconName(match.getIconName());
                    sessionDao.updateSession(s);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public LiveData<List<Activity>> getAllActivities() {
        return allActivities;
    }

    public LiveData<List<SessionEntity>> getAllSessions() {
        return allSessions;
    }

    public LiveData<SessionEntity> getActiveSession() {
        return activeSession;
    }

    public void startActivity(long activityId, Runnable onComplete) {
        startActivity(activityId, false, onComplete);
    }

    public void startActivity(long activityId, boolean isAutomatic, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                // 1. Stop any currently active session
                SessionEntity currentActive = sessionDao.getActiveSessionSync();
                if (currentActive != null) {
                    if (currentActive.getActivityId() == activityId) {
                        // Already tracking this activity
                        return;
                    }
                    currentActive.setEndTime(now);
                    long duration = Math.max(0, now - currentActive.getStartTime());
                    currentActive.setDurationMillis(duration);
                    sessionDao.updateSession(currentActive);
                }

                // 2. Fetch the target activity
                Activity activity = activityDao.getActivityById(activityId);
                if (activity != null) {
                    SessionEntity newSession = new SessionEntity(
                            activity.getId(),
                            activity.getName(),
                            activity.getColorHex(),
                            activity.getIconName(),
                            now,
                            0, // 0 indicates active session
                            0
                    );
                    sessionDao.insertSession(newSession);

                    // If NOT automatic (meaning it's manual), save this as the manual base activity
                    if (!isAutomatic) {
                        com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(appContext);
                        smart.setManualBaseActivity(activity.getId(), activity.getName());
                        smart.overrideActiveTimeSchedules(now);
                    }
                }
            });

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void stopActiveSession(Runnable onComplete) {
        stopActiveSession(true, onComplete);
    }

    public void stopActiveSession(boolean fallbackToDefault, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                SessionEntity currentActive = sessionDao.getActiveSessionSync();
                long stoppedActivityId = -1;
                if (currentActive != null) {
                    stoppedActivityId = currentActive.getActivityId();
                    currentActive.setEndTime(now);
                    long duration = Math.max(0, now - currentActive.getStartTime());
                    currentActive.setDurationMillis(duration);
                    sessionDao.updateSession(currentActive);
                }
                
                com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(appContext);
                smart.overrideActiveTimeSchedules(now);

                if (fallbackToDefault && smart.isEnabled()) {
                    long defaultId = smart.getDefaultActivityId();
                    if (defaultId != -1 && defaultId != stoppedActivityId) {
                        Activity defaultAct = activityDao.getActivityById(defaultId);
                        if (defaultAct != null) {
                            SessionEntity newSession = new SessionEntity(
                                    defaultAct.getId(),
                                    defaultAct.getName(),
                                    defaultAct.getColorHex(),
                                    defaultAct.getIconName(),
                                    now,
                                    0,
                                    0
                            );
                            sessionDao.insertSession(newSession);
                            smart.setManualBaseActivity(defaultAct.getId(), defaultAct.getName());
                            return;
                        }
                    }
                }
                
                // Clear manual base activity if stopping default itself or fallback disabled
                smart.clearManualBaseActivity();
            });
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void switchToNextActivity(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                List<Activity> activities = activityDao.getAllActivitiesSync();
                if (activities == null || activities.isEmpty()) {
                    return;
                }
                SessionEntity active = sessionDao.getActiveSessionSync();
                int nextIndex = 0;
                if (active != null) {
                    for (int i = 0; i < activities.size(); i++) {
                        if (activities.get(i).getId() == active.getActivityId()) {
                            nextIndex = (i + 1) % activities.size();
                            break;
                        }
                    }
                }
                Activity target = activities.get(nextIndex);
                long now = System.currentTimeMillis();
                if (active != null) {
                    active.setEndTime(now);
                    long duration = Math.max(0, now - active.getStartTime());
                    active.setDurationMillis(duration);
                    sessionDao.updateSession(active);
                }

                SessionEntity newSession = new SessionEntity(
                        target.getId(),
                        target.getName(),
                        target.getColorHex(),
                        target.getIconName(),
                        now,
                        0,
                        0
                );
                sessionDao.insertSession(newSession);

                com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(appContext);
                smart.setManualBaseActivity(target.getId(), target.getName());
            });

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void switchToPreviousActivity(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                List<Activity> activities = activityDao.getAllActivitiesSync();
                if (activities == null || activities.isEmpty()) {
                    return;
                }
                SessionEntity active = sessionDao.getActiveSessionSync();
                int prevIndex = activities.size() - 1;
                if (active != null) {
                    for (int i = 0; i < activities.size(); i++) {
                        if (activities.get(i).getId() == active.getActivityId()) {
                            prevIndex = (i - 1 + activities.size()) % activities.size();
                            break;
                        }
                    }
                }
                Activity target = activities.get(prevIndex);
                long now = System.currentTimeMillis();
                if (active != null) {
                    active.setEndTime(now);
                    long duration = Math.max(0, now - active.getStartTime());
                    active.setDurationMillis(duration);
                    sessionDao.updateSession(active);
                }

                SessionEntity newSession = new SessionEntity(
                        target.getId(),
                        target.getName(),
                        target.getColorHex(),
                        target.getIconName(),
                        now,
                        0,
                        0
                );
                sessionDao.insertSession(newSession);

                com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(appContext);
                smart.setManualBaseActivity(target.getId(), target.getName());
            });

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void insertActivity(Activity activity, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            activityDao.insertActivity(activity);
            if (onComplete != null) onComplete.run();
        });
    }

    public void updateActivity(Activity activity, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            activityDao.updateActivity(activity);
            if (onComplete != null) onComplete.run();
        });
    }

    public void reorderActivities(List<Activity> reorderedList, Runnable onComplete) {
        if (reorderedList == null || reorderedList.isEmpty()) return;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long baseTime = System.currentTimeMillis() - (reorderedList.size() * 1000L);
            for (int i = 0; i < reorderedList.size(); i++) {
                Activity act = reorderedList.get(i);
                act.setCreatedAt(baseTime + (i * 1000L));
                activityDao.updateActivity(act);
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteActivitySafely(Activity activity, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            // Check if this activity is currently active
            SessionEntity active = sessionDao.getActiveSessionSync();
            if (active != null && active.getActivityId() == activity.getId()) {
                active.setEndTime(now);
                active.setDurationMillis(Math.max(0, now - active.getStartTime()));
                sessionDao.updateSession(active);
            }
            activityDao.deleteActivity(activity);
            if (onComplete != null) onComplete.run();
        });
    }

    public void deleteSession(long sessionId, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            sessionDao.deleteSessionById(sessionId);
            if (onComplete != null) onComplete.run();
        });
    }

    public void resetAllData(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            sessionDao.deleteAllSessions();
            activityDao.deleteAllActivities();

            List<Activity> defaults = new ArrayList<>();
            long now = System.currentTimeMillis();
            defaults.add(new Activity("Work", com.example.data.entity.ActivityCategory.INCREASE, 8f, "#39D353", "ic_work", true, now));
            defaults.add(new Activity("Sleep", com.example.data.entity.ActivityCategory.NEUTRAL, 8f, "#8A80E6", "ic_sleep", true, now + 1));
            defaults.add(new Activity("Entertainment", com.example.data.entity.ActivityCategory.DECREASE, 8f, "#FF8C42", "ic_entertainment", true, now + 2));
            activityDao.insertAll(defaults);

            if (onComplete != null) onComplete.run();
        });
    }

    public interface TodayDurationsCallback {
        void onDurationsCalculated(long totalClosedMillis, java.util.Map<Long, Long> closedDurationMap);
    }

    public void calculateTodayDurations(TodayDurationsCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            long endOfDay = cal.getTimeInMillis();

            List<SessionEntity> sessions = sessionDao.getSessionsInRangeSync(startOfDay, endOfDay);
            java.util.Map<Long, Long> durationMap = new java.util.HashMap<>();
            long totalClosedTracked = 0;

            for (SessionEntity s : sessions) {
                if (s.getEndTime() > 0) {
                    long sStart = s.getStartTime();
                    long sEnd = s.getEndTime();

                    long overlapStart = Math.max(sStart, startOfDay);
                    long overlapEnd = Math.min(sEnd, endOfDay);

                    if (overlapEnd > overlapStart) {
                        long duration = overlapEnd - overlapStart;
                        totalClosedTracked += duration;
                        long prev = durationMap.containsKey(s.getActivityId()) ? durationMap.get(s.getActivityId()) : 0L;
                        durationMap.put(s.getActivityId(), prev + duration);
                    }
                }
            }

            if (callback != null) {
                callback.onDurationsCalculated(totalClosedTracked, durationMap);
            }
        });
    }

    public interface StatsCallback {
        void onStatsCalculated(long totalTrackedMillis, long totalWindowMillis, List<ActivityStat> stats);
    }

    public static class ActivityStat {
        public long activityId;
        public String name;
        public String colorHex;
        public String iconName;
        public long durationMillis;
        public float percentage;

        public ActivityCategory category = ActivityCategory.NEUTRAL;

        public ActivityStat(long activityId, String name, String colorHex, String iconName, long durationMillis, float percentage) {
            this(activityId, name, colorHex, iconName, durationMillis, percentage, ActivityCategory.NEUTRAL);
        }

        public ActivityStat(long activityId, String name, String colorHex, String iconName, long durationMillis, float percentage, ActivityCategory category) {
            this.activityId = activityId;
            this.name = name;
            this.colorHex = colorHex;
            this.iconName = iconName;
            this.durationMillis = durationMillis;
            this.percentage = percentage;
            this.category = category;
        }

        public String getNameWithArrow() {
            if (name == null) return "";
            if (category == ActivityCategory.INCREASE) {
                return name + " ↑";
            } else if (category == ActivityCategory.DECREASE) {
                return name + " ↓";
            }
            return name;
        }
    }

    /**
     * Calculates stats within [startWindowMillis, endWindowMillis], correctly splitting sessions across boundaries (including midnight).
     */
    public void calculateStats(long startWindowMillis, long endWindowMillis, StatsCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            List<SessionEntity> sessions = sessionDao.getSessionsInRangeSync(startWindowMillis, endWindowMillis);
            List<Activity> allActs = activityDao.getAllActivitiesSync();

            java.util.Map<Long, Long> durationPerActivity = new java.util.HashMap<>();
            long totalTracked = 0;

            for (SessionEntity s : sessions) {
                long sStart = s.getStartTime();
                long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();

                long resolvedActId = s.getActivityId();
                boolean actExists = false;
                if (allActs != null) {
                    for (Activity ae : allActs) {
                        if (ae.getId() == resolvedActId) {
                            actExists = true;
                            break;
                        }
                    }
                    if (!actExists && s.getActivityName() != null) {
                        for (Activity ae : allActs) {
                            if (s.getActivityName().trim().equalsIgnoreCase(ae.getName().trim())) {
                                resolvedActId = ae.getId();
                                break;
                            }
                        }
                    }
                }

                // Compute overlap with the window [startWindowMillis, endWindowMillis]
                long overlapStart = Math.max(sStart, startWindowMillis);
                long overlapEnd = Math.min(sEnd, endWindowMillis);

                if (overlapEnd > overlapStart) {
                    long overlapDuration = overlapEnd - overlapStart;
                    totalTracked += overlapDuration;
                    long prev = durationPerActivity.containsKey(resolvedActId) ? durationPerActivity.get(resolvedActId) : 0L;
                    durationPerActivity.put(resolvedActId, prev + overlapDuration);
                }
            }

            List<ActivityStat> statList = new ArrayList<>();
            for (Activity act : allActs) {
                long dur = durationPerActivity.containsKey(act.getId()) ? durationPerActivity.get(act.getId()) : 0L;
                if (dur > 0) {
                    float pct = totalTracked > 0 ? ((float) dur / totalTracked) * 100f : 0f;
                    statList.add(new ActivityStat(act.getId(), act.getNameWithArrow(), act.getColorHex(), act.getIconName(), dur, pct, act.getCategory()));
                }
            }

            // Sort by duration descending
            statList.sort((a, b) -> Long.compare(b.durationMillis, a.durationMillis));

            long totalWindowMillis = Math.max(1, endWindowMillis - startWindowMillis);
            if (callback != null) {
                callback.onStatsCalculated(totalTracked, totalWindowMillis, statList);
            }
        });
    }

    public static class OverviewTotals {
        public long todayMillis;
        public long weekMillis;
        public String weekLabel;
        public int weekNum;
        public long monthMillis;
        public String monthLabel;
        public long totalAllTimeMillis;
    }

    public interface OverviewTotalsCallback {
        void onTotalsCalculated(OverviewTotals totals);
    }

    public void calculateOverviewTotals(OverviewTotalsCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            OverviewTotals totals = new OverviewTotals();
            long now = System.currentTimeMillis();

            // Today
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startToday = cal.getTimeInMillis();

            // Week (Starting from Saturday)
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int daysSinceSaturday = (dayOfWeek - Calendar.SATURDAY + 7) % 7;
            cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday);
            long startWeek = cal.getTimeInMillis();
            int weekNum = cal.get(Calendar.WEEK_OF_YEAR);
            totals.weekNum = weekNum > 0 ? weekNum : 1;
            totals.weekLabel = "Week " + totals.weekNum;

            // Month
            cal.set(Calendar.DAY_OF_MONTH, 1);
            long startMonth = cal.getTimeInMillis();
            totals.monthLabel = new java.text.SimpleDateFormat("MMM", Locale.getDefault()).format(cal.getTime());

            List<SessionEntity> allSessions = sessionDao.getAllSessionsSync();
            long todaySum = 0;
            long weekSum = 0;
            long monthSum = 0;
            long totalSum = 0;

            if (allSessions != null) {
                for (SessionEntity s : allSessions) {
                    long sStart = s.getStartTime();
                    long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                    long dur = Math.max(0, sEnd - sStart);
                    totalSum += dur;

                    // overlap today
                    long oTodayStart = Math.max(sStart, startToday);
                    long oTodayEnd = Math.min(sEnd, now);
                    if (oTodayEnd > oTodayStart) {
                        todaySum += (oTodayEnd - oTodayStart);
                    }

                    // overlap week
                    long oWeekStart = Math.max(sStart, startWeek);
                    long oWeekEnd = Math.min(sEnd, now);
                    if (oWeekEnd > oWeekStart) {
                        weekSum += (oWeekEnd - oWeekStart);
                    }

                    // overlap month
                    long oMonthStart = Math.max(sStart, startMonth);
                    long oMonthEnd = Math.min(sEnd, now);
                    if (oMonthEnd > oMonthStart) {
                        monthSum += (oMonthEnd - oMonthStart);
                    }
                }
            }

            totals.todayMillis = Math.min(86400000L, todaySum); // Capped strictly at 24 hours per day
            totals.weekMillis = Math.min(7L * 86400000L, weekSum);
            totals.monthMillis = Math.min(31L * 86400000L, monthSum);
            totals.totalAllTimeMillis = totalSum;

            if (callback != null) {
                callback.onTotalsCalculated(totals);
            }
        });
    }

    public static class TrendData {
        public String[] xLabels;
        public List<MultiLineStatsChartView.Series> seriesList;
    }

    public interface TrendsCallback {
        void onTrendsCalculated(TrendData data);
    }

    public void calculateTrends(int periodTab, TrendsCallback callback) {
        calculateTrends(periodTab, 0, callback);
    }

    public void calculateTrends(int periodTab, int periodOffset, TrendsCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            List<Activity> allActivities = activityDao.getAllActivitiesSync();
            List<SessionEntity> allSessions = sessionDao.getAllSessionsSync();

            TrendData data = new TrendData();
            int numSlots;
            String[] labels;
            long[] slotStarts;
            long[] slotEnds;

            Calendar cal = Calendar.getInstance();
            boolean isArabic = Locale.getDefault().getLanguage().equals("ar");

            if (periodTab == 0) { // Day
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DAY_OF_YEAR, periodOffset);
                long dayStart = cal.getTimeInMillis();

                boolean isToday = (periodOffset == 0);
                if (isToday) {
                    Calendar nowCal = Calendar.getInstance();
                    int curHour = nowCal.get(Calendar.HOUR_OF_DAY);
                    if (curHour < 4) {
                        // First hours of today: 1-hour slots from 00:00 to 04:00
                        numSlots = Math.max(4, curHour + 2);
                        labels = new String[numSlots];
                        slotStarts = new long[numSlots];
                        slotEnds = new long[numSlots];
                        for (int i = 0; i < numSlots; i++) {
                            slotStarts[i] = dayStart + (i * 3600000L);
                            slotEnds[i] = slotStarts[i] + 3600000L;
                            labels[i] = formatHourLabel(i, isArabic);
                        }
                    } else if (curHour < 12) {
                        // Morning / early afternoon: 1-hour slots up to current hour + 1
                        numSlots = curHour + 2;
                        labels = new String[numSlots];
                        slotStarts = new long[numSlots];
                        slotEnds = new long[numSlots];
                        for (int i = 0; i < numSlots; i++) {
                            slotStarts[i] = dayStart + (i * 3600000L);
                            slotEnds[i] = slotStarts[i] + 3600000L;
                            labels[i] = formatHourLabel(i, isArabic);
                        }
                    } else {
                        // Full day or past noon: 12 intervals of 2 hours
                        numSlots = 12;
                        labels = new String[numSlots];
                        slotStarts = new long[numSlots];
                        slotEnds = new long[numSlots];
                        for (int i = 0; i < numSlots; i++) {
                            slotStarts[i] = dayStart + (i * 2L * 3600000L);
                            slotEnds[i] = slotStarts[i] + (2L * 3600000L);
                            labels[i] = formatHourLabel(i * 2, isArabic);
                        }
                    }
                } else {
                    // Past days: standard 12 intervals of 2 hours
                    numSlots = 12;
                    labels = new String[numSlots];
                    slotStarts = new long[numSlots];
                    slotEnds = new long[numSlots];
                    for (int i = 0; i < numSlots; i++) {
                        slotStarts[i] = dayStart + (i * 2L * 3600000L);
                        slotEnds[i] = slotStarts[i] + (2L * 3600000L);
                        labels[i] = formatHourLabel(i * 2, isArabic);
                    }
                }
            } else if (periodTab == 1) { // Week: 7 days Sat..Fri (Max 24h per slot)
                numSlots = 7;
                labels = new String[7];
                slotStarts = new long[numSlots];
                slotEnds = new long[numSlots];

                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                int daysSinceSaturday = (dayOfWeek - Calendar.SATURDAY + 7) % 7;
                cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday);
                cal.add(Calendar.DAY_OF_YEAR, periodOffset * 7);
                long satStart = cal.getTimeInMillis();

                java.text.SimpleDateFormat sdfDay = new java.text.SimpleDateFormat("EEE", Locale.getDefault());
                for (int i = 0; i < 7; i++) {
                    slotStarts[i] = satStart + (i * 86400000L);
                    slotEnds[i] = slotStarts[i] + 86400000L;
                    labels[i] = sdfDay.format(new java.util.Date(slotStarts[i]));
                }
            } else if (periodTab == 2) { // Month: 4 weeks
                numSlots = 4;
                labels = new String[]{"W1", "W2", "W3", "W4"};
                slotStarts = new long[numSlots];
                slotEnds = new long[numSlots];

                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, periodOffset);
                long monthStart = cal.getTimeInMillis();
                int maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

                for (int i = 0; i < 4; i++) {
                    slotStarts[i] = monthStart + (i * 7L * 86400000L);
                    slotEnds[i] = (i == 3) ? monthStart + (maxDaysInMonth * 86400000L) : slotStarts[i] + (7L * 86400000L);
                }
            } else { // Year: 12 months for target year (Jan .. Dec)
                numSlots = 12;
                labels = new String[12];
                slotStarts = new long[12];
                slotEnds = new long[12];

                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.add(Calendar.YEAR, periodOffset);

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM", Locale.getDefault());
                for (int i = 0; i < 12; i++) {
                    labels[i] = sdf.format(cal.getTime());
                    slotStarts[i] = cal.getTimeInMillis();
                    cal.add(Calendar.MONTH, 1);
                    slotEnds[i] = cal.getTimeInMillis();
                }
            }

            data.xLabels = labels;
            data.seriesList = new ArrayList<>();

            for (Activity act : allActivities) {
                float[] hours = new float[numSlots]; for (int i=0; i<numSlots; i++) if (slotStarts[i] > now) hours[i] = -1f; 
                boolean hasAnyData = false;

                if (allSessions != null) {
                    for (SessionEntity s : allSessions) {
                        boolean isMatch = (s.getActivityId() == act.getId()) ||
                                (s.getActivityName() != null && !s.getActivityName().trim().isEmpty() && s.getActivityName().trim().equalsIgnoreCase(act.getName().trim()));
                        if (isMatch) {
                            long sStart = s.getStartTime();
                            long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();

                            for (int i = 0; i < numSlots; i++) {
                                if (hours[i] < 0) continue; long oStart = Math.max(sStart, slotStarts[i]);
                                long oEnd = Math.min(sEnd, slotEnds[i]);
                                if (oEnd > oStart) {
                                    float h = (float) (oEnd - oStart) / 3600000f;
                                    float slotMax = (float) (slotEnds[i] - slotStarts[i]) / 3600000f;
                                    hours[i] = Math.min(slotMax, hours[i] + h);
                                    hasAnyData = true;
                                }
                            }
                        }
                    }
                }

                int parsedColor = IconHelper.parseColorOrDefault(act.getColorHex(), 0xFF39D353);
                data.seriesList.add(new MultiLineStatsChartView.Series(act.getId(), act.getNameWithArrow(), act.getIconName(), parsedColor, hours));
            }

            if (callback != null) {
                callback.onTrendsCalculated(data);
            }
        });
    }

    public void adjustActivityTime(long activityId, String activityName, long startRange, long endRange, long targetDurationMillis, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            long maxAllowed = Math.max(0, endRange - startRange);
            long safeTargetDuration = Math.max(0, Math.min(maxAllowed, targetDurationMillis));

            List<SessionEntity> rangeSessions = sessionDao.getSessionsInRangeSync(startRange, endRange);
            List<SessionEntity> actSessions = new ArrayList<>();
            long currentTotalInRange = 0;

            if (rangeSessions != null) {
                for (SessionEntity s : rangeSessions) {
                    if (s.getActivityId() == activityId) {
                        actSessions.add(s);
                        long sStart = Math.max(s.getStartTime(), startRange);
                        long sEnd = (s.getEndTime() == 0) ? now : Math.min(s.getEndTime(), endRange);
                        if (sEnd > sStart) {
                            currentTotalInRange += (sEnd - sStart);
                        }
                    }
                }
            }

            long delta = safeTargetDuration - currentTotalInRange;

            if (delta > 0) {
                // User wants to add more time
                Activity act = activityDao.getActivityById(activityId);
                String colorHex = (act != null && act.getColorHex() != null) ? act.getColorHex() : "#39D353";
                String iconName = (act != null && act.getIconName() != null) ? act.getIconName() : "ic_briefcase";

                long sessionEnd = Math.min(now, endRange);
                long sessionStart = Math.max(startRange, sessionEnd - delta);
                SessionEntity newSession = new SessionEntity(
                        activityId,
                        activityName,
                        colorHex,
                        iconName,
                        sessionStart,
                        sessionEnd,
                        delta
                );
                sessionDao.insertSession(newSession);
            } else if (delta < 0) {
                // User wants to reduce time
                long toRemove = Math.abs(delta);
                for (int i = actSessions.size() - 1; i >= 0 && toRemove > 0; i--) {
                    SessionEntity s = actSessions.get(i);
                    long sStart = Math.max(s.getStartTime(), startRange);
                    long sEnd = (s.getEndTime() == 0) ? now : Math.min(s.getEndTime(), endRange);
                    long dur = Math.max(0, sEnd - sStart);

                    if (dur <= toRemove) {
                        toRemove -= dur;
                        sessionDao.deleteSession(s);
                    } else {
                        long newDur = Math.max(0, s.getDurationMillis() - toRemove);
                        s.setDurationMillis(newDur);
                        s.setEndTime(s.getStartTime() + newDur);
                        sessionDao.updateSession(s);
                        toRemove = 0;
                    }
                }
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    // --- PROGRESS TRACKING & HABIT STREAKS ---

    public interface ProgressSummaryCallback {
        void onProgressCalculated(ProgressSummary summary);
    }

    public void calculateProgressSummary(long activityId, int monthOffset, int weekOffset, ProgressSummaryCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            List<Activity> allActs = activityDao.getAllActivitiesSync();
            List<SessionEntity> allSessions = sessionDao.getAllSessionsSync();

            ProgressSummary summary = new ProgressSummary();
            if (allActs == null || allActs.isEmpty()) {
                if (callback != null) callback.onProgressCalculated(summary);
                return;
            }

            Activity targetActivity = null;
            if (activityId > 0) {
                for (Activity a : allActs) {
                    if (a.getId() == activityId) {
                        targetActivity = a;
                        break;
                    }
                }
            }
            if (targetActivity == null) {
                targetActivity = allActs.get(0);
            }
            summary.selectedActivity = targetActivity;
            summary.dailyTargetHours = targetActivity.getExpectedHoursPerDay();

            long targetMillis = (long) (summary.dailyTargetHours * 3600000L);
            if (targetMillis <= 0) {
                targetMillis = 3600000L; // default 1 hour if unspecified
            }

            List<SessionEntity> targetSessions = new ArrayList<>();
            if (allSessions != null) {
                for (SessionEntity s : allSessions) {
                    boolean isMatch = (s.getActivityId() == targetActivity.getId()) ||
                            (s.getActivityName() != null && targetActivity.getName() != null &&
                             s.getActivityName().trim().equalsIgnoreCase(targetActivity.getName().trim()));
                    if (isMatch) {
                        targetSessions.add(s);
                    }
                }
            }

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            // Set to 1st of target month
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MONTH, monthOffset);
            int maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sunday, 7 = Saturday
            // Offset for grid starting with Saturday:
            // Saturday (7) -> 0, Sunday (1) -> 1, Monday (2) -> 2 ... Friday (6) -> 6
            int gridOffset = (firstDayOfWeek - Calendar.SATURDAY + 7) % 7;
            summary.firstDayOfWeekOffset = gridOffset;

            java.text.SimpleDateFormat sdfDayName = new java.text.SimpleDateFormat("EEE", Locale.getDefault());

            int completedInMonth = 0;
            int pastDaysInMonth = 0;
            long totalTrackedInMonth = 0;

            for (int day = 1; day <= maxDaysInMonth; day++) {
                cal.set(Calendar.DAY_OF_MONTH, day);
                long dayStart = cal.getTimeInMillis();
                long dayEnd = dayStart + 86400000L;

                long trackedThisDay = 0;
                if (targetSessions != null) {
                    for (SessionEntity s : targetSessions) {
                        long sStart = s.getStartTime();
                        long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                        long oStart = Math.max(sStart, dayStart);
                        long oEnd = Math.min(sEnd, dayEnd);
                        if (oEnd > oStart) {
                            trackedThisDay += (oEnd - oStart);
                        }
                    }
                }

                ProgressDayData pdd = new ProgressDayData();
                pdd.dayOfMonth = day;
                pdd.dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                pdd.dayNameShort = sdfDayName.format(cal.getTime());
                pdd.startOfDayMillis = dayStart;
                pdd.endOfDayMillis = dayEnd;
                pdd.trackedMillis = trackedThisDay;
                pdd.targetMillis = targetMillis;
                pdd.category = targetActivity.getCategory();
                pdd.isToday = (dayStart == todayStart);
                pdd.isFuture = (dayStart > now);
                pdd.isCurrentMonth = true;

                if (targetActivity.getCategory() == ActivityCategory.DECREASE) {
                    if (pdd.isFuture) {
                        pdd.status = ProgressDayData.Status.FUTURE;
                        pdd.percentage = 0f;
                    } else {
                        float spentPct = (targetMillis > 0) ? ((float) trackedThisDay / targetMillis) * 100f : (trackedThisDay > 0 ? 100f : 0f);
                        pdd.percentage = spentPct;
                        if (spentPct >= 100f) {
                            pdd.status = ProgressDayData.Status.EXCEEDED_LIMIT_100;
                        } else if (spentPct > 90f) {
                            pdd.status = ProgressDayData.Status.PARTIAL_ORANGE;
                        } else if (spentPct > 0f) {
                            pdd.status = ProgressDayData.Status.PARTIAL_GREEN;
                        } else {
                            pdd.status = ProgressDayData.Status.ZERO;
                        }
                    }
                } else {
                    if (pdd.isFuture) {
                        pdd.status = ProgressDayData.Status.FUTURE;
                        pdd.percentage = 0f;
                    } else {
                        float pct = (targetMillis > 0) ? ((float) trackedThisDay / targetMillis) * 100f : (trackedThisDay > 0 ? 100f : 0f);
                        pdd.percentage = pct;
                        if (pct >= 100f) {
                            pdd.status = ProgressDayData.Status.COMPLETED_100;
                        } else if (pct >= 50f) {
                            pdd.status = ProgressDayData.Status.PARTIAL_GREEN;
                        } else if (pct > 0f) {
                            pdd.status = ProgressDayData.Status.PARTIAL_ORANGE;
                        } else {
                            pdd.status = ProgressDayData.Status.ZERO;
                        }
                    }
                }

                if (!pdd.isFuture) {
                    pastDaysInMonth++;
                    totalTrackedInMonth += trackedThisDay;
                    boolean isSuccessfulDay = (targetActivity.getCategory() == ActivityCategory.DECREASE)
                            ? (pdd.status != ProgressDayData.Status.EXCEEDED_LIMIT_100)
                            : (pdd.status == ProgressDayData.Status.COMPLETED_100);
                    if (isSuccessfulDay) {
                        completedInMonth++;
                    }
                }

                if (pdd.isToday) {
                    summary.todayData = pdd;
                }

                summary.monthDays.add(pdd);
            }

            summary.completedDaysInMonth = completedInMonth;
            summary.totalPastDaysInMonth = pastDaysInMonth;
            summary.totalTrackedMillisInMonth = totalTrackedInMonth;
            summary.monthlyCompletionRate = (pastDaysInMonth > 0) ? ((float) completedInMonth / pastDaysInMonth) * 100f : 0f;
            summary.daysInMonth = maxDaysInMonth;
            summary.monthlyTargetHours = summary.dailyTargetHours * maxDaysInMonth;
            summary.monthlyTrackedHours = totalTrackedInMonth / 3600000f;
            if (summary.monthlyTargetHours > 0) {
                summary.monthlyGoalPercentage = (summary.monthlyTrackedHours / summary.monthlyTargetHours) * 100f;
            } else {
                summary.monthlyGoalPercentage = 0f;
            }

            // --- WEEKS HISTORY CARDS (Based on weekOffset) ---
            boolean isArabic = Locale.getDefault().getLanguage().equals("ar");
            for (int k = 0; k < 4; k++) {
                int relativeWeek = weekOffset - k;

                Calendar wkCardCal = Calendar.getInstance();
                wkCardCal.set(Calendar.HOUR_OF_DAY, 0);
                wkCardCal.set(Calendar.MINUTE, 0);
                wkCardCal.set(Calendar.SECOND, 0);
                wkCardCal.set(Calendar.MILLISECOND, 0);
                int cDayOfWeek = wkCardCal.get(Calendar.DAY_OF_WEEK);
                int dSinceSat = (cDayOfWeek - Calendar.SATURDAY + 7) % 7;
                wkCardCal.add(Calendar.DAY_OF_YEAR, -dSinceSat + (relativeWeek * 7));

                ProgressWeekCardData weekCard = new ProgressWeekCardData();
                if (relativeWeek == 0) {
                    weekCard.title = (appContext != null) ? appContext.getString(R.string.this_week_title) : "This Week";
                } else if (relativeWeek == -1) {
                    weekCard.title = (appContext != null) ? appContext.getString(R.string.last_week_title) : "Last Week";
                } else if (relativeWeek == -2) {
                    weekCard.title = (appContext != null) ? appContext.getString(R.string.two_weeks_ago_title) : "2 Weeks Ago";
                } else if (relativeWeek < -2) {
                    weekCard.title = (appContext != null) ? String.format(Locale.getDefault(), appContext.getString(R.string.weeks_ago_format), Math.abs(relativeWeek)) : (Math.abs(relativeWeek) + " Weeks Ago");
                } else if (relativeWeek == 1) {
                    weekCard.title = (appContext != null) ? appContext.getString(R.string.next_week_title) : "Next Week";
                } else {
                    weekCard.title = (appContext != null) ? String.format(Locale.getDefault(), appContext.getString(R.string.weeks_later_format), relativeWeek) : ("In " + relativeWeek + " Weeks");
                }

                int nonFutureDays = 0;
                float sumPct = 0;

                for (int w = 0; w < 7; w++) {
                    long wStart = wkCardCal.getTimeInMillis();
                    long wEnd = wStart + 86400000L;

                    long trackedThisWeekDay = 0;
                    if (targetSessions != null) {
                        for (SessionEntity s : targetSessions) {
                            long sStart = s.getStartTime();
                            long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                            long oStart = Math.max(sStart, wStart);
                            long oEnd = Math.min(sEnd, wEnd);
                            if (oEnd > oStart) {
                                trackedThisWeekDay += (oEnd - oStart);
                            }
                        }
                    }

                    ProgressDayData wpdd = new ProgressDayData();
                    wpdd.dayOfMonth = wkCardCal.get(Calendar.DAY_OF_MONTH);
                    wpdd.dayOfWeek = wkCardCal.get(Calendar.DAY_OF_WEEK);
                    wpdd.dayNameShort = sdfDayName.format(wkCardCal.getTime());
                    wpdd.startOfDayMillis = wStart;
                    wpdd.endOfDayMillis = wEnd;
                    wpdd.trackedMillis = trackedThisWeekDay;
                    wpdd.targetMillis = targetMillis;
                    wpdd.category = targetActivity.getCategory();
                    wpdd.isToday = (wStart == todayStart);
                    wpdd.isFuture = (wStart > now);
                    wpdd.isCurrentMonth = true;

                    // Day single letter
                    switch (wpdd.dayOfWeek) {
                        case Calendar.SATURDAY:
                            wpdd.dayLetter = isArabic ? "س" : "S";
                            break;
                        case Calendar.SUNDAY:
                            wpdd.dayLetter = isArabic ? "أ" : "S";
                            break;
                        case Calendar.MONDAY:
                            wpdd.dayLetter = isArabic ? "ا" : "M";
                            break;
                        case Calendar.TUESDAY:
                            wpdd.dayLetter = isArabic ? "ث" : "T";
                            break;
                        case Calendar.WEDNESDAY:
                            wpdd.dayLetter = isArabic ? "أ" : "W";
                            break;
                        case Calendar.THURSDAY:
                            wpdd.dayLetter = isArabic ? "خ" : "T";
                            break;
                        case Calendar.FRIDAY:
                        default:
                            wpdd.dayLetter = isArabic ? "ج" : "F";
                            break;
                    }

                    if (targetActivity.getCategory() == ActivityCategory.DECREASE) {
                        if (wpdd.isFuture) {
                            wpdd.status = ProgressDayData.Status.FUTURE;
                            wpdd.percentage = 0f;
                        } else {
                            nonFutureDays++;
                            float spentPct = (targetMillis > 0) ? ((float) trackedThisWeekDay / targetMillis) * 100f : (trackedThisWeekDay > 0 ? 100f : 0f);
                            wpdd.percentage = spentPct;
                            if (spentPct >= 100f) {
                                wpdd.status = ProgressDayData.Status.EXCEEDED_LIMIT_100;
                            } else if (spentPct > 90f) {
                                wpdd.status = ProgressDayData.Status.PARTIAL_ORANGE;
                            } else if (spentPct > 0f) {
                                wpdd.status = ProgressDayData.Status.PARTIAL_GREEN;
                            } else {
                                wpdd.status = ProgressDayData.Status.ZERO;
                            }
                            float adherence = (spentPct >= 100f) ? 0f : (spentPct > 90f ? 50f : 100f);
                            sumPct += adherence;
                        }
                    } else {
                        if (wpdd.isFuture) {
                            wpdd.status = ProgressDayData.Status.FUTURE;
                            wpdd.percentage = 0f;
                        } else {
                            nonFutureDays++;
                            float pct = (targetMillis > 0) ? ((float) trackedThisWeekDay / targetMillis) * 100f : (trackedThisWeekDay > 0 ? 100f : 0f);
                            wpdd.percentage = pct;
                            if (pct >= 100f) {
                                wpdd.status = ProgressDayData.Status.COMPLETED_100;
                            } else if (pct >= 50f) {
                                wpdd.status = ProgressDayData.Status.PARTIAL_GREEN;
                            } else if (pct > 0f) {
                                wpdd.status = ProgressDayData.Status.PARTIAL_ORANGE;
                            } else {
                                wpdd.status = ProgressDayData.Status.ZERO;
                            }
                            sumPct += Math.min(100f, pct);
                        }
                    }

                    weekCard.days.add(wpdd);
                    if (k == 0) {
                        summary.weekDays.add(wpdd);
                    }
                    wkCardCal.add(Calendar.DAY_OF_YEAR, 1);
                }

                weekCard.weekPercentage = (nonFutureDays > 0) ? (sumPct / nonFutureDays) : 0f;
                summary.weeksHistory.add(weekCard);
            }

            long totalTrackedInWeek = 0;
            if (summary.weekDays != null) {
                for (ProgressDayData wDay : summary.weekDays) {
                    totalTrackedInWeek += wDay.trackedMillis;
                }
            }
            summary.weeklyTrackedHours = totalTrackedInWeek / 3600000f;
            summary.weeklyTargetHours = summary.dailyTargetHours * 7f;
            if (summary.weeklyTargetHours > 0) {
                summary.weeklyGoalPercentage = (summary.weeklyTrackedHours / summary.weeklyTargetHours) * 100f;
            } else {
                summary.weeklyGoalPercentage = 0f;
            }

            // --- DAILY HISTORY DAYS (Past 30 days from Today backwards) ---
            java.text.SimpleDateFormat sdfDayFull = new java.text.SimpleDateFormat("EEEE", Locale.getDefault());
            java.text.SimpleDateFormat sdfDayMonth = new java.text.SimpleDateFormat("d MMMM", Locale.getDefault());

            Calendar dailyHistoryCal = Calendar.getInstance();
            dailyHistoryCal.set(Calendar.HOUR_OF_DAY, 0);
            dailyHistoryCal.set(Calendar.MINUTE, 0);
            dailyHistoryCal.set(Calendar.SECOND, 0);
            dailyHistoryCal.set(Calendar.MILLISECOND, 0);

            for (int d = 0; d < 30; d++) {
                long dhStart = dailyHistoryCal.getTimeInMillis();
                long dhEnd = dhStart + 86400000L;

                long trackedThisDay = 0;
                if (targetSessions != null) {
                    for (SessionEntity s : targetSessions) {
                        long sStart = s.getStartTime();
                        long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                        long oStart = Math.max(sStart, dhStart);
                        long oEnd = Math.min(sEnd, dhEnd);
                        if (oEnd > oStart) {
                            trackedThisDay += (oEnd - oStart);
                        }
                    }
                }

                ProgressDayData dpdd = new ProgressDayData();
                dpdd.dayOfMonth = dailyHistoryCal.get(Calendar.DAY_OF_MONTH);
                dpdd.dayOfWeek = dailyHistoryCal.get(Calendar.DAY_OF_WEEK);
                dpdd.dayNameShort = sdfDayFull.format(dailyHistoryCal.getTime());
                dpdd.dateLabel = sdfDayMonth.format(dailyHistoryCal.getTime());
                dpdd.startOfDayMillis = dhStart;
                dpdd.endOfDayMillis = dhEnd;
                dpdd.trackedMillis = trackedThisDay;
                dpdd.targetMillis = targetMillis;
                dpdd.category = targetActivity.getCategory();
                dpdd.isToday = (dhStart == todayStart);
                dpdd.isFuture = (dhStart > now);
                dpdd.isCurrentMonth = true;

                if (targetActivity.getCategory() == ActivityCategory.DECREASE) {
                    if (dpdd.isFuture) {
                        dpdd.status = ProgressDayData.Status.FUTURE;
                        dpdd.percentage = 0f;
                    } else {
                        float spentPct = (targetMillis > 0) ? ((float) trackedThisDay / targetMillis) * 100f : (trackedThisDay > 0 ? 100f : 0f);
                        dpdd.percentage = spentPct;
                        if (spentPct >= 100f) {
                            dpdd.status = ProgressDayData.Status.EXCEEDED_LIMIT_100;
                        } else if (spentPct > 90f) {
                            dpdd.status = ProgressDayData.Status.PARTIAL_ORANGE;
                        } else if (spentPct > 0f) {
                            dpdd.status = ProgressDayData.Status.PARTIAL_GREEN;
                        } else {
                            dpdd.status = ProgressDayData.Status.ZERO;
                        }
                    }
                } else {
                    if (dpdd.isFuture) {
                        dpdd.status = ProgressDayData.Status.FUTURE;
                        dpdd.percentage = 0f;
                    } else {
                        float pct = (targetMillis > 0) ? ((float) trackedThisDay / targetMillis) * 100f : (trackedThisDay > 0 ? 100f : 0f);
                        dpdd.percentage = pct;
                        if (pct >= 100f) {
                            dpdd.status = ProgressDayData.Status.COMPLETED_100;
                        } else if (pct >= 50f) {
                            dpdd.status = ProgressDayData.Status.PARTIAL_GREEN;
                        } else if (pct > 0f) {
                            dpdd.status = ProgressDayData.Status.PARTIAL_ORANGE;
                        } else {
                            dpdd.status = ProgressDayData.Status.ZERO;
                        }
                    }
                }

                summary.dailyHistoryDays.add(dpdd);
                dailyHistoryCal.add(Calendar.DAY_OF_YEAR, -1);
            }

            // --- STREAK CALCULATION ---
            int currentStreak = 0;
            int longestStreak = 0;
            int tempStreak = 0;

            long activityCreatedTime = targetActivity.getCreatedAt();
            if (activityCreatedTime <= 0) {
                activityCreatedTime = now;
            }
            Calendar actCreatedCal = Calendar.getInstance();
            actCreatedCal.setTimeInMillis(activityCreatedTime);
            actCreatedCal.set(Calendar.HOUR_OF_DAY, 0);
            actCreatedCal.set(Calendar.MINUTE, 0);
            actCreatedCal.set(Calendar.SECOND, 0);
            actCreatedCal.set(Calendar.MILLISECOND, 0);
            long activityCreatedDayStart = actCreatedCal.getTimeInMillis();

            long earliestSessionDayStart = todayStart;
            boolean hasAnySession = false;
            if (allSessions != null) {
                for (SessionEntity s : allSessions) {
                    boolean isMatch = (s.getActivityId() == targetActivity.getId()) ||
                            (s.getActivityName() != null && targetActivity.getName() != null &&
                             s.getActivityName().trim().equalsIgnoreCase(targetActivity.getName().trim()));
                    if (isMatch && s.getStartTime() > 0) {
                        hasAnySession = true;
                        Calendar sCal = Calendar.getInstance();
                        sCal.setTimeInMillis(s.getStartTime());
                        sCal.set(Calendar.HOUR_OF_DAY, 0);
                        sCal.set(Calendar.MINUTE, 0);
                        sCal.set(Calendar.SECOND, 0);
                        sCal.set(Calendar.MILLISECOND, 0);
                        long sDayStart = sCal.getTimeInMillis();
                        if (sDayStart < earliestSessionDayStart) {
                            earliestSessionDayStart = sDayStart;
                        }
                    }
                }
            }
            long effectiveCreationDayStart = Math.min(activityCreatedDayStart, earliestSessionDayStart);

            Calendar streakCal = Calendar.getInstance();
            streakCal.setTimeInMillis(todayStart);

            // Compute streaks day by day backwards
            boolean isCurrentStreakActive = true;
            for (int i = 0; i < 365; i++) {
                long dStart = streakCal.getTimeInMillis();
                long dEnd = dStart + 86400000L;

                if (dStart < effectiveCreationDayStart) {
                    break;
                }

                long tracked = 0;
                if (targetSessions != null) {
                    for (SessionEntity s : targetSessions) {
                        long sStart = s.getStartTime();
                        long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                        long oStart = Math.max(sStart, dStart);
                        long oEnd = Math.min(sEnd, dEnd);
                        if (oEnd > oStart) {
                            tracked += (oEnd - oStart);
                        }
                    }
                }

                boolean metGoal;
                if (targetActivity.getCategory() == ActivityCategory.DECREASE) {
                    metGoal = (tracked <= targetMillis);
                } else {
                    metGoal = (tracked >= targetMillis * 0.95f) || (targetMillis == 0 && tracked > 0);
                }

                if (i == 0) {
                    if (metGoal) {
                        currentStreak++;
                    }
                } else {
                    if (metGoal && isCurrentStreakActive) {
                        currentStreak++;
                    } else if (i > 0) {
                        isCurrentStreakActive = false;
                    }
                }

                streakCal.add(Calendar.DAY_OF_YEAR, -1);
            }

            // Longest streak calculation starting from effectiveCreationDayStart
            long longestStreakStart = Math.max(todayStart - 365L * 86400000L, effectiveCreationDayStart);
            streakCal.setTimeInMillis(longestStreakStart);
            while (streakCal.getTimeInMillis() <= todayStart) {
                long dStart = streakCal.getTimeInMillis();
                long dEnd = dStart + 86400000L;

                long tracked = 0;
                if (targetSessions != null) {
                    for (SessionEntity s : targetSessions) {
                        long sStart = s.getStartTime();
                        long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                        long oStart = Math.max(sStart, dStart);
                        long oEnd = Math.min(sEnd, dEnd);
                        if (oEnd > oStart) {
                            tracked += (oEnd - oStart);
                        }
                    }
                }

                boolean metGoal;
                if (targetActivity.getCategory() == ActivityCategory.DECREASE) {
                    metGoal = (tracked <= targetMillis);
                } else {
                    metGoal = (tracked >= targetMillis * 0.95f) || (targetMillis == 0 && tracked > 0);
                }

                if (metGoal) {
                    tempStreak++;
                    if (tempStreak > longestStreak) {
                        longestStreak = tempStreak;
                    }
                } else {
                    tempStreak = 0;
                }
                streakCal.add(Calendar.DAY_OF_YEAR, 1);
            }

            summary.currentStreak = currentStreak;
            summary.longestStreak = Math.max(currentStreak, longestStreak);

            if (callback != null) {
                callback.onProgressCalculated(summary);
            }
        });
    }

    // --- EXPORT & IMPORT JSON ---

    public interface ExportCallback {
        void onExportComplete(String json, Exception error);
    }

    public interface ImportCallback {
        void onImportComplete(int activitiesImported, int sessionsImported, String error);
    }

    public void exportDataToJson(ExportCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Activity> activities = activityDao.getAllActivitiesSync();
                List<SessionEntity> sessions = sessionDao.getAllSessionsSync();

                JSONObject root = new JSONObject();
                root.put("app", "LifeFlow");
                root.put("version", 1);
                long now = System.currentTimeMillis();
                root.put("exportedAt", now);

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                root.put("exportedDate", sdf.format(new java.util.Date(now)));

                JSONArray activitiesArray = new JSONArray();
                if (activities != null) {
                    for (Activity a : activities) {
                        JSONObject aObj = new JSONObject();
                        aObj.put("id", a.getId());
                        aObj.put("name", a.getName());
                        aObj.put("category", a.getCategory() != null ? a.getCategory().name() : ActivityCategory.NEUTRAL.name());
                        aObj.put("goalType", a.getCategory() != null ? a.getCategory().name() : ActivityCategory.NEUTRAL.name());
                        aObj.put("expectedHoursPerDay", a.getExpectedHoursPerDay());
                        aObj.put("targetHours", a.getExpectedHoursPerDay());
                        aObj.put("colorHex", a.getColorHex());
                        aObj.put("iconName", a.getIconName());
                        aObj.put("isDefault", a.isDefault());
                        aObj.put("createdAt", a.getCreatedAt());
                        com.example.util.SmartTrackingManager smartManager = new com.example.util.SmartTrackingManager(appContext);
                        if (smartManager.isActivityTimeEnabled(a)) {
                            JSONObject timeObj = new JSONObject();
                            timeObj.put("enabled", true);
                            timeObj.put("startHour", smartManager.getActivityStartHour(a));
                            timeObj.put("startMinute", smartManager.getActivityStartMinute(a));
                            timeObj.put("endHour", smartManager.getActivityEndHour(a));
                            timeObj.put("endMinute", smartManager.getActivityEndMinute(a));
                            aObj.put("smartTimeRange", timeObj);
                        }
                        java.util.Set<String> boundApps = smartManager.getActivityBoundApps(a);
                        if (boundApps != null && !boundApps.isEmpty()) {
                            JSONArray appsArr = new JSONArray(boundApps);
                            aObj.put("boundApps", appsArr);
                        }
                        activitiesArray.put(aObj);
                    }
                }
                root.put("activities", activitiesArray);

                JSONArray sessionsArray = new JSONArray();
                if (sessions != null) {
                    for (SessionEntity s : sessions) {
                        JSONObject sObj = new JSONObject();
                        sObj.put("id", s.getId());
                        sObj.put("activityId", s.getActivityId());
                        sObj.put("activityName", s.getActivityName());
                        sObj.put("activityColorHex", s.getActivityColorHex());
                        sObj.put("activityIconName", s.getActivityIconName());
                        sObj.put("startTime", s.getStartTime());
                        sObj.put("endTime", s.getEndTime());
                        sObj.put("durationMillis", s.getDurationMillis());
                        sessionsArray.put(sObj);
                    }
                }
                root.put("sessions", sessionsArray);

                String formattedJson = root.toString(2);
                if (callback != null) {
                    callback.onExportComplete(formattedJson, null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onExportComplete(null, e);
                }
            }
        });
    }

    private static final String[] IMPORT_COLOR_PALETTE = new String[]{
            "#39D353", "#2196F3", "#FF9800", "#E91E63", "#9C27B0",
            "#00BCD4", "#FFC107", "#4CAF50", "#3F51B5", "#F44336",
            "#009688", "#00ACC1", "#8BC34A", "#673AB7", "#FF5722"
    };

    private static String optStringField(JSONObject obj, String... keys) {
        if (obj == null) return null;
        for (String k : keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                String val = obj.optString(k, "").trim();
                if (!val.isEmpty() && !val.equalsIgnoreCase("null")) {
                    return val;
                }
            }
        }
        return null;
    }

    private static long parseTimestampSafe(Object val, long fallback) {
        if (val == null) return fallback;
        if (val instanceof Number) {
            long num = ((Number) val).longValue();
            if (num <= 0) return fallback;
            if (num < 10000000000L) { // Epoch seconds
                return num * 1000L;
            }
            return num;
        }
        String s = val.toString().trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) return fallback;
        try {
            long num = Long.parseLong(s);
            if (num <= 0) return fallback;
            if (num < 10000000000L) return num * 1000L;
            return num;
        } catch (NumberFormatException ignored) {}

        String[] formats = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };
        for (String f : formats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(f, Locale.US);
                java.util.Date d = sdf.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static String inferIconFromName(String name) {
        if (name == null) return "ic_other";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("work") || lower.contains("code") || lower.contains("dev") || lower.contains("program") ||
                lower.contains("office") || lower.contains("job") || lower.contains("business") || lower.contains("شغل") ||
                lower.contains("عمل") || lower.contains("وظيفة") || lower.contains("برمجة") || lower.contains("كود")) {
            return "ic_work";
        }
        if (lower.contains("study") || lower.contains("learn") || lower.contains("read") || lower.contains("book") ||
                lower.contains("school") || lower.contains("exam") || lower.contains("course") || lower.contains("دراسة") ||
                lower.contains("مذاكرة") || lower.contains("قراءة") || lower.contains("كتاب") || lower.contains("حفظ")) {
            return "ic_study";
        }
        if (lower.contains("exercise") || lower.contains("gym") || lower.contains("sport") || lower.contains("workout") ||
                lower.contains("fitness") || lower.contains("run") || lower.contains("walk") || lower.contains("swim") ||
                lower.contains("رياضة") || lower.contains("جيم") || lower.contains("تمرين") || lower.contains("مشي") ||
                lower.contains("ركض") || lower.contains("جري")) {
            return "ic_exercise";
        }
        if (lower.contains("sleep") || lower.contains("rest") || lower.contains("nap") || lower.contains("bed") ||
                lower.contains("نوم") || lower.contains("راحة") || lower.contains("استرخاء")) {
            return "ic_sleep";
        }
        if (lower.contains("meditation") || lower.contains("pray") || lower.contains("prayer") || lower.contains("yoga") ||
                lower.contains("mind") || lower.contains("breathe") || lower.contains("صلاة") || lower.contains("تأمل") ||
                lower.contains("ذكر") || lower.contains("عبادة") || lower.contains("قرآن")) {
            return "ic_meditation";
        }
        if (lower.contains("entertainment") || lower.contains("game") || lower.contains("movie") || lower.contains("play") ||
                lower.contains("fun") || lower.contains("music") || lower.contains("tv") || lower.contains("video") ||
                lower.contains("ترفيه") || lower.contains("العاب") || lower.contains("لعبة") || lower.contains("افلام") ||
                lower.contains("موسيقى")) {
            return "ic_entertainment";
        }
        return "ic_other";
    }

    private static class ParsedTimer {
        List<String> aliasIds = new ArrayList<>();
        String name = "";
        ActivityCategory category = ActivityCategory.NEUTRAL;
        float expectedHoursPerDay = 0f;
        String colorHex = "";
        String iconName = "";
        boolean isDefault = false;
        long createdAt = System.currentTimeMillis();
        List<ParsedSessionItem> nestedSessions = new ArrayList<>();
        boolean timeEnabled = false;
        int startHour = 8;
        int startMin = 0;
        int endHour = 9;
        int endMin = 0;
        List<String> boundApps = new ArrayList<>();
    }

    private static class ParsedSessionItem {
        String rawActivityId = "";
        String activityName = "";
        String activityColorHex = "";
        String activityIconName = "";
        long startTime = 0;
        long endTime = 0;
        long durationMillis = 0;
    }

    public void importDataFromJson(String jsonContent, boolean replaceExisting, ImportCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (jsonContent == null || jsonContent.trim().isEmpty()) {
                    if (callback != null) callback.onImportComplete(0, 0, "JSON content is empty");
                    return;
                }

                String trimmed = jsonContent.trim();
                List<ParsedTimer> timersList = new ArrayList<>();
                List<ParsedSessionItem> flatSessionsList = new ArrayList<>();

                int colorIndex = 0;

                if (trimmed.startsWith("[")) {
                    // Root is an array
                    JSONArray rootArray = new JSONArray(trimmed);
                    for (int i = 0; i < rootArray.length(); i++) {
                        JSONObject itemObj = rootArray.optJSONObject(i);
                        if (itemObj == null) continue;

                        parseItemOrSession(itemObj, timersList, flatSessionsList, colorIndex++);
                    }
                } else {
                    // Root is an object
                    JSONObject rootObj = new JSONObject(trimmed);

                    // 1. Check for activity/timer collection keys
                    String[] timerArrayKeys = new String[]{
                            "activities", "timers", "tasks", "projects", "categories", "goals", "tags", "habits", "items"
                    };
                    for (String key : timerArrayKeys) {
                        JSONArray arr = rootObj.optJSONArray(key);
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject tObj = arr.optJSONObject(i);
                                if (tObj == null) continue;
                                parseTimerObject(tObj, timersList, colorIndex++);
                            }
                        }
                    }

                    // 2. Check for session/history collection keys
                    String[] sessionArrayKeys = new String[]{
                            "sessions", "history", "records", "logs", "entries", "time_entries", "timeEntries", "intervals", "events", "data"
                    };
                    for (String key : sessionArrayKeys) {
                        JSONArray arr = rootObj.optJSONArray(key);
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject sObj = arr.optJSONObject(i);
                                if (sObj == null) continue;
                                parseSessionObject(sObj, flatSessionsList);
                            }
                        }
                    }

                    // 2b. Check for active/current session object
                    String[] activeSessionKeys = new String[]{"activeSession", "active_session", "currentSession", "current_session", "active"};
                    for (String ak : activeSessionKeys) {
                        JSONObject activeObj = rootObj.optJSONObject(ak);
                        if (activeObj != null) {
                            parseSessionObject(activeObj, flatSessionsList);
                        }
                    }

                    // 3. If no standard arrays found, check if root is a map of timer names: { "Work": [sessions], "Gym": [sessions] }
                    if (timersList.isEmpty() && flatSessionsList.isEmpty()) {
                        java.util.Iterator<String> keys = rootObj.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (key.equalsIgnoreCase("app") || key.equalsIgnoreCase("version") || key.equalsIgnoreCase("exportedAt") || key.equalsIgnoreCase("exportedDate")) {
                                continue;
                            }
                            JSONArray arr = rootObj.optJSONArray(key);
                            if (arr != null) {
                                ParsedTimer pt = new ParsedTimer();
                                pt.name = key;
                                pt.aliasIds.add(key);
                                pt.colorHex = IMPORT_COLOR_PALETTE[(colorIndex++) % IMPORT_COLOR_PALETTE.length];
                                pt.iconName = inferIconFromName(key);
                                for (int j = 0; j < arr.length(); j++) {
                                    JSONObject sObj = arr.optJSONObject(j);
                                    if (sObj != null) {
                                        ParsedSessionItem psi = extractSessionFromObject(sObj, pt.name, pt.colorHex, pt.iconName);
                                        if (psi != null) {
                                            pt.nestedSessions.add(psi);
                                        }
                                    }
                                }
                                timersList.add(pt);
                            } else {
                                JSONObject subObj = rootObj.optJSONObject(key);
                                if (subObj != null) {
                                    parseTimerObject(subObj, timersList, colorIndex++);
                                }
                            }
                        }
                    }
                }

                if (timersList.isEmpty() && flatSessionsList.isEmpty()) {
                    if (callback != null) callback.onImportComplete(0, 0, "No activities or timer sessions found in JSON");
                    return;
                }

                final int[] counts = new int[2]; // [activitiesCount, sessionsCount]

                database.runInTransaction(() -> {
                    if (replaceExisting) {
                        sessionDao.deleteAllSessions();
                        activityDao.deleteAllActivities();
                    }

                    Map<String, Activity> idLookup = new HashMap<>();
                    Map<String, Activity> nameLookup = new HashMap<>();
                    List<Activity> allKnownActivities = new ArrayList<>();

                    List<Activity> currentActs = activityDao.getAllActivitiesSync();
                    if (currentActs != null) {
                        for (Activity ca : currentActs) {
                            allKnownActivities.add(ca);
                            idLookup.put(String.valueOf(ca.getId()), ca);
                            if (ca.getName() != null) {
                                nameLookup.put(ca.getName().trim().toLowerCase(Locale.ROOT), ca);
                                nameLookup.put(normalizeActivityName(ca.getName()), ca);
                            }
                        }
                    }

                    int paletteCounter = allKnownActivities.size();

                    // 1. Insert or map all explicitly defined timers/activities
                    for (ParsedTimer pt : timersList) {
                        String lowerName = pt.name.trim().toLowerCase(Locale.ROOT);
                        String normName = normalizeActivityName(pt.name);
                        Activity targetEntity = nameLookup.get(normName);
                        if (targetEntity == null) {
                            targetEntity = nameLookup.get(lowerName);
                        }

                        if (targetEntity != null) {
                            boolean needUpdate = false;
                            if (pt.category != null && pt.category != ActivityCategory.NEUTRAL) {
                                targetEntity.setCategory(pt.category);
                                needUpdate = true;
                            }
                            if (pt.expectedHoursPerDay > 0) {
                                targetEntity.setExpectedHoursPerDay(pt.expectedHoursPerDay);
                                needUpdate = true;
                            }
                            if (needUpdate) {
                                activityDao.updateActivity(targetEntity);
                            }
                        } else {
                            String color = (pt.colorHex != null && !pt.colorHex.isEmpty()) ? pt.colorHex : IMPORT_COLOR_PALETTE[(paletteCounter++) % IMPORT_COLOR_PALETTE.length];
                            String icon = (pt.iconName != null && !pt.iconName.isEmpty()) ? pt.iconName : inferIconFromName(pt.name);
                            Activity newAct = new Activity(pt.name, pt.category, pt.expectedHoursPerDay, color, icon, pt.isDefault, pt.createdAt);
                            long targetId = activityDao.insertActivity(newAct);
                            newAct.setId(targetId);
                            nameLookup.put(lowerName, newAct);
                            nameLookup.put(normName, newAct);
                            idLookup.put(String.valueOf(targetId), newAct);
                            allKnownActivities.add(newAct);
                            targetEntity = newAct;
                            counts[0]++;
                        }

                        if (targetEntity != null) {
                            com.example.util.SmartTrackingManager smartManager = new com.example.util.SmartTrackingManager(appContext);
                            if (pt.timeEnabled) {
                                smartManager.setActivityTimeRange(targetEntity, pt.startHour, pt.startMin, pt.endHour, pt.endMin, true);
                            }
                            if (!pt.boundApps.isEmpty()) {
                                smartManager.setActivityBoundApps(targetEntity, new java.util.HashSet<>(pt.boundApps));
                            }

                            for (String alias : pt.aliasIds) {
                                if (alias != null && !alias.trim().isEmpty()) {
                                    idLookup.put(alias.trim(), targetEntity);
                                }
                            }

                            // Also process any nested sessions inside this timer
                            for (ParsedSessionItem ns : pt.nestedSessions) {
                                if (ns.startTime <= 0) continue;
                                long duration = ns.durationMillis > 0 ? ns.durationMillis : (ns.endTime > ns.startTime ? (ns.endTime - ns.startTime) : 0);
                                SessionEntity sessionEntity = new SessionEntity(
                                        targetEntity.getId(),
                                        targetEntity.getName(),
                                        targetEntity.getColorHex(),
                                        targetEntity.getIconName(),
                                        ns.startTime,
                                        ns.endTime,
                                        duration
                                );
                                sessionDao.insertSession(sessionEntity);
                                counts[1]++;
                            }
                        }
                    }

                    // 2. Process all flat session items
                    List<SessionEntity> existingSessions = replaceExisting ? new ArrayList<>() : sessionDao.getAllSessionsSync();

                    for (ParsedSessionItem s : flatSessionsList) {
                        if (s.startTime <= 0) continue;

                        long duration = s.durationMillis > 0 ? s.durationMillis : (s.endTime > s.startTime ? (s.endTime - s.startTime) : 0);

                        // Deduplication check on merge
                        if (!replaceExisting && existingSessions != null) {
                            boolean isDup = false;
                            for (SessionEntity es : existingSessions) {
                                if (es.getStartTime() == s.startTime && es.getEndTime() == s.endTime &&
                                        s.activityName.equalsIgnoreCase(es.getActivityName())) {
                                    isDup = true;
                                    break;
                                }
                            }
                            if (isDup) continue;
                        }

                        Activity matchedAct = null;

                        // Match 1: By rawActivityId in idLookup
                        if (s.rawActivityId != null && !s.rawActivityId.trim().isEmpty()) {
                            matchedAct = idLookup.get(s.rawActivityId.trim());
                        }

                        // Match 2: By activityName in nameLookup
                        if (matchedAct == null && s.activityName != null && !s.activityName.trim().isEmpty()) {
                            String lowName = s.activityName.trim().toLowerCase(Locale.ROOT);
                            matchedAct = nameLookup.get(lowName);
                            if (matchedAct == null) {
                                matchedAct = nameLookup.get(normalizeActivityName(s.activityName));
                            }
                        }

                        // Match 3: By rawActivityId parsed as a name
                        if (matchedAct == null && s.rawActivityId != null && !s.rawActivityId.trim().isEmpty()) {
                            matchedAct = nameLookup.get(s.rawActivityId.trim().toLowerCase(Locale.ROOT));
                            if (matchedAct == null) {
                                matchedAct = nameLookup.get(normalizeActivityName(s.rawActivityId));
                            }
                        }

                        // Match 4: Auto-create activity if it has a real name
                        if (matchedAct == null) {
                            String candidateName = (s.activityName != null && !s.activityName.trim().isEmpty() && !s.activityName.equalsIgnoreCase("General"))
                                    ? s.activityName.trim()
                                    : null;

                            if (candidateName != null) {
                                String color = (s.activityColorHex != null && !s.activityColorHex.isEmpty()) ? s.activityColorHex : IMPORT_COLOR_PALETTE[(paletteCounter++) % IMPORT_COLOR_PALETTE.length];
                                String icon = (s.activityIconName != null && !s.activityIconName.isEmpty()) ? s.activityIconName : inferIconFromName(candidateName);

                                Activity autoAct = new Activity(candidateName, color, icon, false, System.currentTimeMillis());
                                long autoId = activityDao.insertActivity(autoAct);
                                autoAct.setId(autoId);

                                nameLookup.put(candidateName.toLowerCase(Locale.ROOT), autoAct);
                                idLookup.put(String.valueOf(autoId), autoAct);
                                if (s.rawActivityId != null && !s.rawActivityId.trim().isEmpty()) {
                                    idLookup.put(s.rawActivityId.trim(), autoAct);
                                }
                                allKnownActivities.add(autoAct);
                                matchedAct = autoAct;
                                counts[0]++;
                            } else if (!allKnownActivities.isEmpty()) {
                                // If there are existing activities and no name provided, attach to the first known activity
                                matchedAct = allKnownActivities.get(0);
                            } else {
                                // Create default activity
                                String fallbackName = "General";
                                Activity defaultAct = new Activity(fallbackName, "#39D353", "ic_work", false, System.currentTimeMillis());
                                long defId = activityDao.insertActivity(defaultAct);
                                defaultAct.setId(defId);
                                nameLookup.put("general", defaultAct);
                                idLookup.put(String.valueOf(defId), defaultAct);
                                allKnownActivities.add(defaultAct);
                                matchedAct = defaultAct;
                                counts[0]++;
                            }
                        }

                        if (matchedAct != null) {
                            SessionEntity newSession = new SessionEntity(
                                    matchedAct.getId(),
                                    matchedAct.getName(),
                                    matchedAct.getColorHex(),
                                    matchedAct.getIconName(),
                                    s.startTime,
                                    s.endTime,
                                    duration
                            );
                            sessionDao.insertSession(newSession);
                            counts[1]++;
                        }
                    }
                });

                if (callback != null) {
                    callback.onImportComplete(counts[0], counts[1], null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onImportComplete(0, 0, e.getMessage());
                }
            }
        });
    }

    private static void parseItemOrSession(JSONObject obj, List<ParsedTimer> timersList, List<ParsedSessionItem> flatSessionsList, int index) {
        boolean hasStartTime = obj.has("startTime") || obj.has("start_time") || obj.has("startedAt") || obj.has("started_at") || obj.has("from") || obj.has("start") || obj.has("begin") || obj.has("timestamp") || obj.has("date");
        boolean hasNestedSessions = obj.has("sessions") || obj.has("records") || obj.has("history") || obj.has("entries") || obj.has("logs") || obj.has("intervals");

        if (hasNestedSessions || !hasStartTime) {
            parseTimerObject(obj, timersList, index);
        } else {
            parseSessionObject(obj, flatSessionsList);
        }
    }

    private static void parseTimerObject(JSONObject tObj, List<ParsedTimer> timersList, int index) {
        ParsedTimer pt = new ParsedTimer();
        
        // Extract all possible IDs
        String[] idKeys = new String[]{"id", "_id", "goalId", "goal_id", "timerId", "timer_id", "activityId", "activity_id", "taskId", "task_id", "projectId", "project_id", "categoryId", "category_id", "habitId", "habit_id", "key", "uuid", "uid", "aid", "tid", "pid", "cid", "gid"};
        for (String k : idKeys) {
            if (tObj.has(k) && !tObj.isNull(k)) {
                String idVal = tObj.optString(k, "").trim();
                if (!idVal.isEmpty() && !idVal.equalsIgnoreCase("null")) {
                    pt.aliasIds.add(idVal);
                }
            }
        }

        pt.name = optStringField(tObj,
                "name", "title", "timer", "timerName", "timer_name",
                "goal", "goalName", "goal_name", "goalTitle", "goal_title",
                "activity", "activityName", "activity_name",
                "task", "taskName", "task_name",
                "project", "projectName", "project_name",
                "category", "categoryName", "category_name",
                "habit", "habitName", "habit_name",
                "label", "tag", "description", "subject"
        );
        if (pt.name == null || pt.name.isEmpty()) {
            pt.name = "Timer " + (index + 1);
        }

        pt.colorHex = optStringField(tObj, "colorHex", "color_hex", "color", "hex", "colour", "colorCode", "color_code", "theme", "accent");
        if (pt.colorHex == null || !pt.colorHex.startsWith("#")) {
            pt.colorHex = IMPORT_COLOR_PALETTE[index % IMPORT_COLOR_PALETTE.length];
        }

        pt.iconName = optStringField(tObj, "iconName", "icon_name", "icon", "symbol", "emoji");
        if (pt.iconName == null || pt.iconName.isEmpty()) {
            pt.iconName = inferIconFromName(pt.name);
        }

        pt.isDefault = tObj.optBoolean("isDefault", tObj.optBoolean("is_default", false));
        pt.createdAt = parseTimestampSafe(tObj.opt("createdAt") != null ? tObj.opt("createdAt") : tObj.opt("created_at"), System.currentTimeMillis());

        // Parse category / goal type
        String catStr = optStringField(tObj, "category", "goalType", "goal_type", "type", "kind", "actionType", "action_type");
        if (catStr != null && !catStr.isEmpty()) {
            String upper = catStr.toUpperCase(Locale.ROOT);
            if (upper.contains("INCREASE") || upper.contains("زيادة") || upper.contains("زادة") || upper.contains("مستهدف") || upper.contains("TARGET")) {
                pt.category = ActivityCategory.INCREASE;
            } else if (upper.contains("DECREASE") || upper.contains("نقص") || upper.contains("تقليل") || upper.contains("REDUCE")) {
                pt.category = ActivityCategory.DECREASE;
            } else if (upper.contains("NEUTRAL") || upper.contains("عادي") || upper.contains("NORMAL")) {
                pt.category = ActivityCategory.NEUTRAL;
            } else {
                try {
                    pt.category = ActivityCategory.valueOf(upper);
                } catch (Exception ignored) {
                    pt.category = ActivityCategory.NEUTRAL;
                }
            }
        }

        // Parse expected hours / target
        double hoursVal = tObj.optDouble("expectedHoursPerDay",
                tObj.optDouble("expected_hours_per_day",
                tObj.optDouble("expectedHours",
                tObj.optDouble("targetHours",
                tObj.optDouble("target_hours",
                tObj.optDouble("goalHours",
                tObj.optDouble("goal_hours",
                tObj.optDouble("target",
                tObj.optDouble("goal", 0.0)))))))));
        if (hoursVal > 0) {
            pt.expectedHoursPerDay = (float) hoursVal;
        }

        // Parse smart time range
        JSONObject timeObj = tObj.optJSONObject("smartTimeRange");
        if (timeObj == null) timeObj = tObj.optJSONObject("smart_time_range");
        if (timeObj == null) timeObj = tObj.optJSONObject("timeRange");
        if (timeObj != null) {
            pt.timeEnabled = timeObj.optBoolean("enabled", timeObj.optBoolean("is_enabled", true));
            pt.startHour = timeObj.optInt("startHour", timeObj.optInt("start_hour", 8));
            pt.startMin = timeObj.optInt("startMinute", timeObj.optInt("start_minute", 0));
            pt.endHour = timeObj.optInt("endHour", timeObj.optInt("end_hour", 9));
            pt.endMin = timeObj.optInt("endMinute", timeObj.optInt("end_minute", 0));
        }

        // Parse bound apps
        JSONArray appsArr = tObj.optJSONArray("boundApps");
        if (appsArr == null) appsArr = tObj.optJSONArray("bound_apps");
        if (appsArr != null) {
            for (int j = 0; j < appsArr.length(); j++) {
                String pkg = appsArr.optString(j, "").trim();
                if (!pkg.isEmpty()) {
                    pt.boundApps.add(pkg);
                }
            }
        }

        // Check for nested sessions
        String[] nestedKeys = new String[]{"sessions", "records", "history", "entries", "logs", "intervals", "time_entries", "timeEntries"};
        for (String nk : nestedKeys) {
            JSONArray arr = tObj.optJSONArray(nk);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject sObj = arr.optJSONObject(i);
                    if (sObj != null) {
                        ParsedSessionItem psi = extractSessionFromObject(sObj, pt.name, pt.colorHex, pt.iconName);
                        if (psi != null) {
                            pt.nestedSessions.add(psi);
                        }
                    }
                }
            }
        }

        timersList.add(pt);
    }

    private static void parseSessionObject(JSONObject sObj, List<ParsedSessionItem> flatSessionsList) {
        ParsedSessionItem psi = extractSessionFromObject(sObj, null, null, null);
        if (psi != null) {
            flatSessionsList.add(psi);
        }
    }

    private static ParsedSessionItem extractSessionFromObject(JSONObject sObj, String fallbackName, String fallbackColor, String fallbackIcon) {
        ParsedSessionItem psi = new ParsedSessionItem();

        // 1. Extract rawActivityId
        psi.rawActivityId = optStringField(sObj,
                "goalId", "goal_id", "goalID", "gid",
                "activityId", "activity_id", "activityID", "act_id", "actId", "aid",
                "timerId", "timer_id", "timerID", "tid",
                "taskId", "task_id", "taskID",
                "projectId", "project_id", "projectID", "pid",
                "categoryId", "category_id", "categoryID", "cid",
                "habitId", "habit_id", "hid",
                "itemId", "item_id", "iid",
                "tagId", "tag_id"
        );

        // Check if "goal", "activity", "timer", "task", "project", "category", "habit" holds an ID or numeric reference
        String[] ambiguousKeys = new String[]{"goal", "activity", "timer", "task", "project", "category", "habit", "item", "tag"};
        for (String ak : ambiguousKeys) {
            if (sObj.has(ak) && !sObj.isNull(ak)) {
                Object val = sObj.opt(ak);
                if (val instanceof Number || (val instanceof String && ((String) val).matches("^\\d+$"))) {
                    if (psi.rawActivityId == null || psi.rawActivityId.isEmpty()) {
                        psi.rawActivityId = String.valueOf(val).trim();
                    }
                }
            }
        }

        if (psi.rawActivityId == null) psi.rawActivityId = "";

        // 2. Extract activityName
        psi.activityName = optStringField(sObj,
                "goalName", "goal_name", "goalTitle", "goal_title",
                "activityName", "activity_name",
                "timerName", "timer_name",
                "taskName", "task_name",
                "projectName", "project_name",
                "categoryName", "category_name",
                "habitName", "habit_name",
                "label", "tag", "title", "name", "description", "subject", "summary"
        );

        // If activityName is still empty, check ambiguous keys if they contain string names
        if (psi.activityName == null || psi.activityName.isEmpty()) {
            for (String ak : ambiguousKeys) {
                if (sObj.has(ak) && !sObj.isNull(ak)) {
                    String strVal = sObj.optString(ak, "").trim();
                    if (!strVal.isEmpty() && !strVal.matches("^\\d+$") && !strVal.equalsIgnoreCase("null")) {
                        psi.activityName = strVal;
                        break;
                    }
                }
            }
        }

        if (psi.activityName == null || psi.activityName.isEmpty()) {
            psi.activityName = fallbackName != null ? fallbackName : "";
        }

        psi.activityColorHex = optStringField(sObj, "activityColorHex", "colorHex", "color_hex", "color", "hex", "colour");
        if (psi.activityColorHex == null || !psi.activityColorHex.startsWith("#")) {
            psi.activityColorHex = fallbackColor != null ? fallbackColor : "";
        }

        psi.activityIconName = optStringField(sObj, "activityIconName", "iconName", "icon_name", "icon");
        if (psi.activityIconName == null || psi.activityIconName.isEmpty()) {
            psi.activityIconName = fallbackIcon != null ? fallbackIcon : "";
        }

        psi.startTime = parseTimestampSafe(
                sObj.opt("startTime") != null ? sObj.opt("startTime") :
                        (sObj.opt("start_time") != null ? sObj.opt("start_time") :
                                (sObj.opt("start") != null ? sObj.opt("start") :
                                        (sObj.opt("startedAt") != null ? sObj.opt("startedAt") :
                                                (sObj.opt("started_at") != null ? sObj.opt("started_at") :
                                                        (sObj.opt("timestamp") != null ? sObj.opt("timestamp") :
                                                                (sObj.opt("from") != null ? sObj.opt("from") :
                                                                        (sObj.opt("begin") != null ? sObj.opt("begin") : sObj.opt("date")))))))),
                0
        );

        psi.endTime = parseTimestampSafe(
                sObj.opt("endTime") != null ? sObj.opt("endTime") :
                        (sObj.opt("end_time") != null ? sObj.opt("end_time") :
                                (sObj.opt("end") != null ? sObj.opt("end") :
                                        (sObj.opt("stoppedAt") != null ? sObj.opt("stoppedAt") :
                                                (sObj.opt("stopped_at") != null ? sObj.opt("stopped_at") :
                                                        (sObj.opt("to") != null ? sObj.opt("to") :
                                                                (sObj.opt("stop") != null ? sObj.opt("stop") : sObj.opt("finishedAt"))))))),
                0
        );

        // Duration parsing
        if (sObj.has("durationMillis")) {
            psi.durationMillis = sObj.optLong("durationMillis");
        } else if (sObj.has("duration_millis")) {
            psi.durationMillis = sObj.optLong("duration_millis");
        } else if (sObj.has("durationSeconds")) {
            psi.durationMillis = sObj.optLong("durationSeconds") * 1000L;
        } else if (sObj.has("duration_seconds")) {
            psi.durationMillis = sObj.optLong("duration_seconds") * 1000L;
        } else if (sObj.has("durationMinutes")) {
            psi.durationMillis = sObj.optLong("durationMinutes") * 60000L;
        } else if (sObj.has("duration_minutes")) {
            psi.durationMillis = sObj.optLong("duration_minutes") * 60000L;
        } else if (sObj.has("duration")) {
            long d = sObj.optLong("duration");
            if (d > 0 && psi.endTime > psi.startTime && (psi.endTime - psi.startTime) == d * 1000L) {
                psi.durationMillis = d * 1000L;
            } else if (d > 0 && psi.endTime > psi.startTime && (psi.endTime - psi.startTime) == d) {
                psi.durationMillis = d;
            } else if (d < 100000L && d > 0) {
                psi.durationMillis = d * 1000L;
            } else {
                psi.durationMillis = d;
            }
        }

        if (psi.durationMillis <= 0 && psi.endTime > psi.startTime) {
            psi.durationMillis = psi.endTime - psi.startTime;
        }

        if (psi.startTime <= 0 && psi.durationMillis > 0) {
            // Estimate start and end if only duration was provided
            psi.endTime = System.currentTimeMillis();
            psi.startTime = psi.endTime - psi.durationMillis;
        }

        return psi;
    }

    private static String formatHourLabel(int hour24, boolean isArabic) {
        int h = hour24 % 24;
        int hour12 = h % 12;
        if (hour12 == 0) hour12 = 12;
        String suffix = (h < 12 ? "a" : "p");
        return hour12 + suffix;
    }

    public interface AllActivitiesMatrixCallback {
        void onMatrixCalculated(AllActivitiesMatrixData data);
    }

    public void calculateAllActivitiesMatrix(int monthOffset, AllActivitiesMatrixCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AllActivitiesMatrixData matrix = new AllActivitiesMatrixData();
            matrix.monthOffset = monthOffset;

            List<Activity> allActs = activityDao.getAllActivitiesSync();
            List<SessionEntity> allSessions = sessionDao.getAllSessionsSync();

            if (allActs == null || allActs.isEmpty()) {
                if (callback != null) callback.onMatrixCalculated(matrix);
                return;
            }

            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            int currentYear = cal.get(Calendar.YEAR);
            int currentMonth = cal.get(Calendar.MONTH);
            int currentDay = cal.get(Calendar.DAY_OF_MONTH);

            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MONTH, monthOffset);

            int targetYear = cal.get(Calendar.YEAR);
            int targetMonth = cal.get(Calendar.MONTH);
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            matrix.daysInMonth = daysInMonth;

            java.text.SimpleDateFormat sdfMonthYear = new java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            matrix.monthName = sdfMonthYear.format(cal.getTime());

            java.text.SimpleDateFormat sdfDayName = new java.text.SimpleDateFormat("EEE", Locale.getDefault());

            for (int day = 1; day <= daysInMonth; day++) {
                cal.set(Calendar.DAY_OF_MONTH, day);
                AllActivitiesMatrixData.DayHeader dh = new AllActivitiesMatrixData.DayHeader();
                dh.dayOfMonth = day;
                dh.dayName = sdfDayName.format(cal.getTime());
                dh.isToday = (targetYear == currentYear && targetMonth == currentMonth && day == currentDay);
                matrix.dayHeaders.add(dh);
            }

            for (Activity act : allActs) {
                AllActivitiesMatrixData.ActivityRow row = new AllActivitiesMatrixData.ActivityRow();
                row.activity = act;

                float targetHours = act.getExpectedHoursPerDay();
                long targetMillis = (long) (targetHours * 3600000L);

                int trophyCount = 0;

                List<SessionEntity> actSessions = new ArrayList<>();
                if (allSessions != null) {
                    for (SessionEntity s : allSessions) {
                        boolean isMatch = (s.getActivityId() == act.getId()) ||
                                (s.getActivityName() != null && act.getName() != null &&
                                 s.getActivityName().trim().equalsIgnoreCase(act.getName().trim()));
                        if (isMatch) {
                            actSessions.add(s);
                        }
                    }
                }

                for (int day = 1; day <= daysInMonth; day++) {
                    cal.set(Calendar.DAY_OF_MONTH, day);
                    long dayStart = cal.getTimeInMillis();
                    long dayEnd = dayStart + 86400000L;

                    long tracked = 0;
                    if (actSessions != null) {
                        for (SessionEntity s : actSessions) {
                            long sStart = s.getStartTime();
                            long sEnd = (s.getEndTime() == 0) ? now : s.getEndTime();
                            long oStart = Math.max(sStart, dayStart);
                            long oEnd = Math.min(sEnd, dayEnd);
                            if (oEnd > oStart) {
                                tracked += (oEnd - oStart);
                            }
                        }
                    }

                    AllActivitiesMatrixData.DayCell cell = new AllActivitiesMatrixData.DayCell();
                    cell.dayOfMonth = day;
                    cell.trackedMillis = tracked;
                    cell.targetMillis = targetMillis;
                    cell.isToday = (targetYear == currentYear && targetMonth == currentMonth && day == currentDay);

                    boolean isFuture = dayStart > todayStart;

                    if (isFuture) {
                        cell.status = -1;
                        cell.percent = 0f;
                    } else if (tracked == 0) {
                        cell.status = 0;
                        cell.percent = 0f;
                    } else {
                        if (targetMillis > 0) {
                            cell.percent = (float) tracked / targetMillis;
                        } else {
                            cell.percent = 1.0f;
                        }

                        boolean metGoal;
                        if (act.getCategory() == ActivityCategory.DECREASE) {
                            metGoal = (tracked <= targetMillis);
                        } else {
                            metGoal = (cell.percent >= 0.95f) || (targetMillis == 0 && tracked > 0);
                        }

                        if (metGoal) {
                            cell.status = 2;
                            trophyCount++;
                        } else {
                            cell.status = 1;
                        }
                    }

                    row.dayCells.add(cell);
                }

                row.trophyCount = trophyCount;
                row.currentStreak = 0; // Streaks are removed from UI as requested
                matrix.rows.add(row);
            }

            if (callback != null) {
                callback.onMatrixCalculated(matrix);
            }
        });
    }
}


