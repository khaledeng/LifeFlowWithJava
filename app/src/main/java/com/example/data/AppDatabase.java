package com.example.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.data.converter.ActivityCategoryConverter;
import com.example.data.dao.ActivityDao;
import com.example.data.dao.ActivitySessionDao;
import com.example.data.dao.DailyProgressDao;
import com.example.data.dao.SessionDao;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityEntity;
import com.example.data.entity.ActivitySession;
import com.example.data.entity.DailyProgress;
import com.example.data.entity.SessionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Activity.class, SessionEntity.class, ActivitySession.class, DailyProgress.class}, version = 2, exportSchema = false)
@TypeConverters({ActivityCategoryConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract ActivityDao activityDao();
    public abstract SessionDao sessionDao();
    public abstract ActivitySessionDao activitySessionDao();
    public abstract DailyProgressDao dailyProgressDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "lifeflow_database")
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                if (INSTANCE != null) {
                    ActivityDao dao = INSTANCE.activityDao();
                    if (dao.getActivityCountSync() == 0) {
                        List<Activity> defaults = new ArrayList<>();
                        long now = System.currentTimeMillis();
                        defaults.add(new Activity("Work", com.example.data.entity.ActivityCategory.INCREASE, 8f, "#39D353", "ic_work", true, now));
                        defaults.add(new Activity("Sleep", com.example.data.entity.ActivityCategory.NEUTRAL, 8f, "#8A80E6", "ic_sleep", true, now + 1));
                        defaults.add(new Activity("Entertainment", com.example.data.entity.ActivityCategory.DECREASE, 8f, "#FF8C42", "ic_entertainment", true, now + 2));
                        dao.insertAll(defaults);
                    }
                }
            });
        }
    };
}
