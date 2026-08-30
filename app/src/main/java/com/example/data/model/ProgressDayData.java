package com.example.data.model;

import com.example.data.entity.ActivityCategory;

public class ProgressDayData {
    public int dayOfMonth;
    public int dayOfWeek; // e.g. Calendar.SATURDAY
    public String dayNameShort; // "Sat", "السبت"
    public long startOfDayMillis;
    public long endOfDayMillis;
    public long trackedMillis;
    public long targetMillis;
    public float percentage; // 0 to 100+
    public boolean isToday;
    public boolean isFuture;
    public boolean isCurrentMonth;
    public ActivityCategory category;
    public String dateLabel; // e.g. "20 أغسطس" / "20 Aug"
    public String dayLetter; // e.g. "س", "أ", "ا", "ث", "أ", "خ", "ج" / "S", "S", "M", "T", "W", "T", "F"

    public enum Status {
        COMPLETED_100,      // ✓ Checkmark (Green) - for Increase goals
        EXCEEDED_LIMIT_100, // ✕ Cross (Red) - for Decrease goals when daily limit is broken (>= 100%)
        PARTIAL_GREEN,      // Green (>=50% for Increase, <=50% for Decrease)
        PARTIAL_ORANGE,     // Orange (1%-49% for Increase, 51%-75% for Decrease)
        PARTIAL_RED,        // Red (76%-99% for Decrease)
        ZERO,               // 0% (Neutral for Increase, Green for Decrease)
        FUTURE              // Future day (Disabled)
    }

    public Status status = Status.ZERO;
}
