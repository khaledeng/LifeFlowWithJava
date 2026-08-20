package com.example.data.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Entity(tableName = "activity_sessions")
public class ActivitySession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long activityId;
    public long startTimestamp;
    public long endTimestamp; // 0 or null while session is active/running
    public String dateKey; // "yyyy-MM-dd", derived from startTimestamp in local time

    public ActivitySession() {
    }

    @Ignore
    public ActivitySession(long activityId, long startTimestamp, long endTimestamp) {
        this.activityId = activityId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.dateKey = formatDateKey(startTimestamp);
    }

    @Ignore
    public ActivitySession(long activityId, long startTimestamp, long endTimestamp, String dateKey) {
        this.activityId = activityId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.dateKey = dateKey != null ? dateKey : formatDateKey(startTimestamp);
    }

    public static String formatDateKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getActivityId() {
        return activityId;
    }

    public void setActivityId(long activityId) {
        this.activityId = activityId;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
        if (this.dateKey == null || this.dateKey.isEmpty()) {
            this.dateKey = formatDateKey(startTimestamp);
        }
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getDateKey() {
        return dateKey;
    }

    public void setDateKey(String dateKey) {
        this.dateKey = dateKey;
    }
}
