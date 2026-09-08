package com.example;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

public class LifeFlowApplication extends Application {
    private static final String TAG = "LifeFlowApp";
    public static final String CRASH_PREFS = "lifeflow_crash_prefs";
    public static final String KEY_LAST_CRASH = "last_crash_log";
    public static final String KEY_LAST_CRASH_TIME = "last_crash_time";

    @Override
    public void onCreate() {
        super.onCreate();
        
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception in " + thread.getName(), throwable);
            try {
                SharedPreferences prefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE);
                prefs.edit()
                        .putString(KEY_LAST_CRASH, Log.getStackTraceString(throwable))
                        .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
                        .commit();
            } catch (Throwable ignored) {}

            // If it's a database fatal error, proactively delete corrupted db file to unblock subsequent starts
            String msg = throwable != null ? throwable.getMessage() : "";
            if (msg != null && (msg.contains("Room cannot verify") || msg.contains("Migration didn't properly handle") || msg.contains("database disk image is malformed"))) {
                try {
                    deleteDatabase("lifeflow_database");
                } catch (Throwable ignored) {}
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
