package com.example.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.data.entity.DailyProgress;

import java.util.List;

@Dao
public interface DailyProgressDao {

    @Query("SELECT * FROM daily_progress WHERE compositeKey = :compositeKey LIMIT 1")
    DailyProgress getProgressSync(String compositeKey);

    @Query("SELECT * FROM daily_progress WHERE activityId = :activityId AND dateKey = :dateKey LIMIT 1")
    DailyProgress getProgressForActivityAndDateSync(long activityId, String dateKey);

    @Query("SELECT * FROM daily_progress WHERE isPaused = 1")
    List<DailyProgress> getAllPausedProgressSync();

    @Query("SELECT * FROM daily_progress WHERE activityId = :activityId AND isPaused = 1")
    List<DailyProgress> getPausedProgressForActivitySync(long activityId);

    @Query("DELETE FROM daily_progress WHERE compositeKey = :compositeKey")
    void deleteByCompositeKey(String compositeKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DailyProgress progress);

    @Update
    void update(DailyProgress progress);

    @Query("DELETE FROM daily_progress")
    void deleteAll();
}
