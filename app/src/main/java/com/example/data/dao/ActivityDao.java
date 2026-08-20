package com.example.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.data.entity.Activity;

import java.util.List;

@Dao
public interface ActivityDao {

    @Query("SELECT * FROM activities ORDER BY createdAt ASC")
    LiveData<List<Activity>> getAllActivitiesLive();

    @Query("SELECT * FROM activities ORDER BY createdAt ASC")
    List<Activity> getAllActivitiesSync();

    @Query("SELECT * FROM activities WHERE id = :id LIMIT 1")
    Activity getActivityById(long id);

    @Query("SELECT COUNT(*) FROM activities")
    int getActivityCountSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertActivity(Activity activity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Activity> activities);

    @Update
    void updateActivity(Activity activity);

    @Delete
    void deleteActivity(Activity activity);

    @Query("DELETE FROM activities")
    void deleteAllActivities();
}
