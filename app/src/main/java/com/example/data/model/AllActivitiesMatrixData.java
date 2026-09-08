package com.example.data.model;

import com.example.data.entity.Activity;
import java.util.ArrayList;
import java.util.List;

public class AllActivitiesMatrixData {
    public int monthOffset;
    public String monthName;
    public int daysInMonth;
    public List<DayHeader> dayHeaders = new ArrayList<>();
    public List<ActivityRow> rows = new ArrayList<>();

    public static class DayHeader {
        public int dayOfMonth;
        public String dayName;
        public boolean isToday;
    }

    public static class ActivityRow {
        public Activity activity;
        public int trophyCount;
        public int currentStreak;
        public List<DayCell> dayCells = new ArrayList<>();
    }

    public static class DayCell {
        public int dayOfMonth;
        public long trackedMillis;
        public long targetMillis;
        public float percent;
        public int status; // 3 = PAUSED/REST DAY, 2 = COMPLETED (100%+), 1 = PARTIAL (>0), 0 = ZERO, -1 = FUTURE
        public boolean isToday;
        public boolean isPaused;
    }
}
