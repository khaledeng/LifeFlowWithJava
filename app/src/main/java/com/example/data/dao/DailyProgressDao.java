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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DailyProgress progress);

    @Update
    void update(DailyProgress progress);

    @Query("DELETE FROM daily_progress")
    void deleteAll();
}
