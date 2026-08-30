package com.example.data.model;

import com.example.data.entity.Activity;
import java.util.ArrayList;
import java.util.List;

public class ProgressSummary {
    public Activity selectedActivity;
    public int currentStreak;
    public int longestStreak;
    public int completedDaysInMonth;
    public int totalPastDaysInMonth;
    public float monthlyCompletionRate; // e.g. 85.5%
    public long totalTrackedMillisInMonth;
    public float dailyTargetHours;
    public int daysInMonth;
    public float monthlyTargetHours;
    public float monthlyTrackedHours;
    public float monthlyGoalPercentage;
    public float weeklyTargetHours;
    public float weeklyTrackedHours;
    public float weeklyGoalPercentage;
    public List<ProgressDayData> monthDays = new ArrayList<>();
    public List<ProgressDayData> weekDays = new ArrayList<>();
    public List<ProgressDayData> dailyHistoryDays = new ArrayList<>();
    public List<ProgressWeekCardData> weeksHistory = new ArrayList<>();
    public ProgressDayData todayData;
    public int firstDayOfWeekOffset; // offset for calendar grid leading empty slots
}
