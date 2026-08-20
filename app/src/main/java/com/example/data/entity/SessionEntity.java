package com.example.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * SessionEntity records a tracked time interval for an activity.
 * If endTime == 0, the session is actively being tracked.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = @ForeignKey(
        entity = Activity.class,
        parentColumns = "id",
        childColumns = "activityId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("activityId"), @Index("startTime")}
)
public class SessionEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long activityId;
    private String activityName;
    private String activityColorHex;
    private String activityIconName;
    private long startTime;
    private long endTime; // 0 if active
    private long durationMillis;

    public SessionEntity(long activityId, String activityName, String activityColorHex, String activityIconName, long startTime, long endTime, long durationMillis) {
        this.activityId = activityId;
        this.activityName = activityName;
        this.activityColorHex = activityColorHex;
        this.activityIconName = activityIconName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMillis = durationMillis;
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

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getActivityColorHex() {
        return activityColorHex;
    }

    public void setActivityColorHex(String activityColorHex) {
        this.activityColorHex = activityColorHex;
    }

    public String getActivityIconName() {
        return activityIconName;
    }

    public void setActivityIconName(String activityIconName) {
        this.activityIconName = activityIconName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isActive() {
        return endTime == 0;
    }
}
