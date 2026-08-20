package com.example.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.data.entity.ActivitySession;

import java.util.List;

@Dao
public interface ActivitySessionDao {

    @Query("SELECT * FROM activity_sessions ORDER BY startTimestamp DESC")
    LiveData<List<ActivitySession>> getAllSessionsLive();

    @Query("SELECT * FROM activity_sessions WHERE endTimestamp = 0 LIMIT 1")
    ActivitySession getActiveSessionSync();

    @Query("SELECT * FROM activity_sessions WHERE activityId = :activityId AND dateKey = :dateKey")
    List<ActivitySession> getSessionsForActivityAndDate(long activityId, String dateKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSession(ActivitySession session);

    @Update
    void updateSession(ActivitySession session);

    @Delete
    void deleteSession(ActivitySession session);

    @Query("DELETE FROM activity_sessions")
    void deleteAllSessions();
}
