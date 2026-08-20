package com.example.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_progress")
public class DailyProgress {

    @NonNull
    @PrimaryKey
    public String compositeKey; // activityId + "_" + dateKey

    public long activityId;

    public String dateKey;

    public int lastNotifiedThreshold; // 0, 25, 50, 75, 100, 125, 150... highest threshold already notified for that day

    public DailyProgress() {
        this.compositeKey = "";
        this.dateKey = "";
    }

    @Ignore
    public DailyProgress(long activityId, @NonNull String dateKey, int lastNotifiedThreshold) {
        this.activityId = activityId;
        this.dateKey = dateKey;
        this.compositeKey = activityId + "_" + dateKey;
        this.lastNotifiedThreshold = lastNotifiedThreshold;
    }

    @NonNull
    public String getCompositeKey() {
        return compositeKey;
    }

    public void setCompositeKey(@NonNull String compositeKey) {
        this.compositeKey = compositeKey;
    }

    public long getActivityId() {
        return activityId;
    }

    public void setActivityId(long activityId) {
        this.activityId = activityId;
        if (this.dateKey != null) {
            this.compositeKey = activityId + "_" + this.dateKey;
        }
    }

    public String getDateKey() {
        return dateKey;
    }

    public void setDateKey(String dateKey) {
        this.dateKey = dateKey;
        if (dateKey != null) {
            this.compositeKey = this.activityId + "_" + dateKey;
        }
    }

    public int getLastNotifiedThreshold() {
        return lastNotifiedThreshold;
    }

    public void setLastNotifiedThreshold(int lastNotifiedThreshold) {
        this.lastNotifiedThreshold = lastNotifiedThreshold;
    }
}
