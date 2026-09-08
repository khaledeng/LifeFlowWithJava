package com.example.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
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

@Database(entities = {Activity.class, SessionEntity.class, ActivitySession.class, DailyProgress.class}, version = 5, exportSchema = false)
@TypeConverters({ActivityCategoryConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract ActivityDao activityDao();
    public abstract SessionDao sessionDao();
    public abstract ActivitySessionDao activitySessionDao();
    public abstract DailyProgressDao dailyProgressDao();

    private static void safeMigrateAll(@NonNull SupportSQLiteDatabase database) {
        try {
            database.execSQL("ALTER TABLE `activities` ADD COLUMN `category` TEXT DEFAULT 'NEUTRAL'");
        } catch (Exception ignored) {}
        try {
            database.execSQL("ALTER TABLE `activities` ADD COLUMN `expectedHoursPerDay` REAL NOT NULL DEFAULT 0.0");
        } catch (Exception ignored) {}
        try {
            database.execSQL("ALTER TABLE `activities` ADD COLUMN `isOnce` INTEGER NOT NULL DEFAULT 0");
        } catch (Exception ignored) {}
        try {
            database.execSQL("ALTER TABLE `activities` ADD COLUMN `onceDate` TEXT");
        } catch (Exception ignored) {}
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS `activity_sessions` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`activityId` INTEGER NOT NULL, "
                    + "`startTimestamp` INTEGER NOT NULL, "
                    + "`endTimestamp` INTEGER NOT NULL, "
                    + "`dateKey` TEXT)");
        } catch (Exception ignored) {}
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS `daily_progress` ("
                    + "`compositeKey` TEXT PRIMARY KEY NOT NULL, "
                    + "`activityId` INTEGER NOT NULL, "
                    + "`dateKey` TEXT, "
                    + "`lastNotifiedThreshold` INTEGER NOT NULL DEFAULT 0, "
                    + "`isPaused` INTEGER NOT NULL DEFAULT 0)");
        } catch (Exception ignored) {}
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_1_3 = new Migration(1, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_2_4 = new Migration(2, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_1_4 = new Migration(1, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_3_5 = new Migration(3, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_2_5 = new Migration(2, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    public static final Migration MIGRATION_1_5 = new Migration(1, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            safeMigrateAll(database);
        }
    };

    private static AppDatabase buildDatabase(final Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                AppDatabase.class, "lifeflow_database")
                .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3,
                        MIGRATION_3_4, MIGRATION_2_4, MIGRATION_1_4,
                        MIGRATION_4_5, MIGRATION_3_5, MIGRATION_2_5, MIGRATION_1_5
                )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(sRoomDatabaseCallback)
                .build();
    }

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = buildDatabase(context);
                        // Eagerly verify SQLite connectivity to catch and heal any corrupt schema early
                        INSTANCE.getOpenHelper().getWritableDatabase();
                    } catch (Throwable t) {
                        android.util.Log.e("AppDatabase", "Failed to open or migrate database. Rebuilding fresh database.", t);
                        try {
                            context.getApplicationContext().deleteDatabase("lifeflow_database");
                        } catch (Throwable ignored) {}
                        INSTANCE = buildDatabase(context);
                    }
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
