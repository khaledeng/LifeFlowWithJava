package com.example.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.data.entity.SessionEntity;

import java.util.List;

@Dao
public interface SessionDao {

    @Query("SELECT * FROM sessions WHERE endTime = 0 LIMIT 1")
    SessionEntity getActiveSessionSync();

    @Query("SELECT * FROM sessions WHERE endTime = 0 LIMIT 1")
    LiveData<SessionEntity> getActiveSessionLive();

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    LiveData<List<SessionEntity>> getAllSessionsLive();

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    List<SessionEntity> getAllSessionsSync();

    @Query("SELECT * FROM sessions WHERE (startTime >= :startMillis AND startTime < :endMillis) OR (endTime > :startMillis AND endTime <= :endMillis) OR (startTime < :startMillis AND (endTime = 0 OR endTime > :endMillis)) ORDER BY startTime ASC")
    List<SessionEntity> getSessionsInRangeSync(long startMillis, long endMillis);

    @Query("SELECT * FROM sessions WHERE activityId = :activityId AND endTime = 0")
    List<SessionEntity> getActiveSessionsForActivity(long activityId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSession(SessionEntity session);

    @Update
    void updateSession(SessionEntity session);

    @Delete
    void deleteSession(SessionEntity session);

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    void deleteSessionById(long sessionId);

    @Query("DELETE FROM sessions")
    void deleteAllSessions();
}
