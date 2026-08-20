package com.example.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.data.dao.ActivityDao;
import com.example.data.dao.SessionDao;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityEntity;
import com.example.data.entity.SessionEntity;

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
            if (activityDao.getActivityCountSync() == 0) {
                List<Activity> defaults = new ArrayList<>();
                long now = System.currentTimeMillis();
                defaults.add(new Activity("Work 💸", com.example.data.entity.ActivityCategory.INCREASE, 8f, "#39D353", "ic_work", true, now));
                defaults.add(new Activity("Sleep 😴", com.example.data.entity.ActivityCategory.NEUTRAL, 8f, "#8A80E6", "ic_sleep", true, now + 1));
                defaults.add(new Activity("Entertainment 🥳", com.example.data.entity.ActivityCategory.DECREASE, 8f, "#FF8C42", "ic_entertainment", true, now + 2));
                activityDao.insertAll(defaults);
            }
            if (!hasRepaired) {
                hasRepaired = true;
                repairOrphanedSessionsInternal();
            }
        });
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
                    nameMap.put(ae.getName().trim().toLowerCase(Locale.ROOT), ae);
                }
            }

            for (SessionEntity s : sessions) {
                Activity match = idMap.get(s.getActivityId());
                if (match == null && s.getActivityName() != null) {
                    match = nameMap.get(s.getActivityName().trim().toLowerCase(Locale.ROOT));
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
                }
            });

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void stopActiveSession(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                SessionEntity currentActive = sessionDao.getActiveSessionSync();
                if (currentActive != null) {
                    currentActive.setEndTime(now);
                    long duration = Math.max(0, now - currentActive.getStartTime());
                    currentActive.setDurationMillis(duration);
                    sessionDao.updateSession(currentActive);
                }
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
            defaults.add(new Activity("Work 💸", com.example.data.entity.ActivityCategory.INCREASE, 8f, "#39D353", "ic_work", true, now));
            defaults.add(new Activity("Sleep 😴", com.example.data.entity.ActivityCategory.NEUTRAL, 8f, "#8A80E6", "ic_sleep", true, now + 1));
            defaults.add(new Activity("Entertainment 🥳", com.example.data.entity.ActivityCategory.DECREASE, 8f, "#FF8C42", "ic_entertainment", true, now + 2));
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

        public ActivityStat(long activityId, String name, String colorHex, String iconName, long durationMillis, float percentage) {
            this.activityId = activityId;
            this.name = name;
            this.colorHex = colorHex;
            this.iconName = iconName;
            this.durationMillis = durationMillis;
            this.percentage = percentage;
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
                    statList.add(new ActivityStat(act.getId(), act.getName(), act.getColorHex(), act.getIconName(), dur, pct));
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
                data.seriesList.add(new MultiLineStatsChartView.Series(act.getId(), act.getName(), act.getIconName(), parsedColor, hours));
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
                        aObj.put("colorHex", a.getColorHex());
                        aObj.put("iconName", a.getIconName());
                        aObj.put("isDefault", a.isDefault());
                        aObj.put("createdAt", a.getCreatedAt());
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
        String colorHex = "";
        String iconName = "";
        boolean isDefault = false;
        long createdAt = System.currentTimeMillis();
        List<ParsedSessionItem> nestedSessions = new ArrayList<>();
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
                            }
                        }
                    }

                    int paletteCounter = allKnownActivities.size();

                    // 1. Insert or map all explicitly defined timers/activities
                    for (ParsedTimer pt : timersList) {
                        String lowerName = pt.name.trim().toLowerCase(Locale.ROOT);
                        Activity targetEntity;

                        if (nameLookup.containsKey(lowerName)) {
                            targetEntity = nameLookup.get(lowerName);
                        } else {
                            String color = (pt.colorHex != null && !pt.colorHex.isEmpty()) ? pt.colorHex : IMPORT_COLOR_PALETTE[(paletteCounter++) % IMPORT_COLOR_PALETTE.length];
                            String icon = (pt.iconName != null && !pt.iconName.isEmpty()) ? pt.iconName : inferIconFromName(pt.name);
                            Activity newAct = new Activity(pt.name, color, icon, pt.isDefault, pt.createdAt);
                            long targetId = activityDao.insertActivity(newAct);
                            newAct.setId(targetId);
                            nameLookup.put(lowerName, newAct);
                            idLookup.put(String.valueOf(targetId), newAct);
                            allKnownActivities.add(newAct);
                            targetEntity = newAct;
                            counts[0]++;
                        }

                        if (targetEntity != null) {
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
                        }

                        // Match 3: By rawActivityId parsed as a name
                        if (matchedAct == null && s.rawActivityId != null && !s.rawActivityId.trim().isEmpty()) {
                            matchedAct = nameLookup.get(s.rawActivityId.trim().toLowerCase(Locale.ROOT));
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
}


