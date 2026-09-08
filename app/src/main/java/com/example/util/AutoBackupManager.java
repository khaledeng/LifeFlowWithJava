package com.example.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.data.TrackingRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles automatic daily backups at 12:00 AM (midnight) to persistent device storage.
 */
public class AutoBackupManager {

    private static final String TAG = "AutoBackupManager";
    public static final String WORK_NAME = "LifeFlowDailyAutoBackup";
    private static final String BACKUP_DIR_NAME = "auto_backups";
    private static final int MAX_KEEP_BACKUPS = 14; // Keep up to 14 daily backups

    public static class DailyBackupWorker extends Worker {

        public DailyBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();
            SubscriptionManager subManager = new SubscriptionManager(context);
            if (!subManager.isAutoBackupEnabled()) {
                Log.d(TAG, "Auto-backup disabled by user. Skipping.");
                return Result.success();
            }

            Log.d(TAG, "Executing scheduled daily auto-backup...");
            boolean success = performBackupSync(context);
            if (success) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                subManager.setLastAutoBackupTimestamp(System.currentTimeMillis());
                Log.d(TAG, "Daily auto-backup completed successfully.");
                return Result.success();
            } else {
                Log.e(TAG, "Daily auto-backup failed.");
                return Result.retry();
            }
        }
    }

    /**
     * Schedules or cancels the WorkManager daily 12:00 AM backup based on preferences.
     */
    public static void updateSchedule(Context context) {
        try {
            SubscriptionManager subManager = new SubscriptionManager(context);
            WorkManager workManager = WorkManager.getInstance(context);

            if (!subManager.isAutoBackupEnabled()) {
                workManager.cancelUniqueWork(WORK_NAME);
                Log.d(TAG, "Cancelled auto backup work.");
                return;
            }

            long initialDelayMillis = calculateInitialDelayToMidnight();

            PeriodicWorkRequest backupRequest = new PeriodicWorkRequest.Builder(
                    DailyBackupWorker.class,
                    24,
                    TimeUnit.HOURS
            )
                    .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                    .setConstraints(new Constraints.Builder().build())
                    .build();

            workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    backupRequest
            );

            Log.d(TAG, "Scheduled auto backup at midnight. Initial delay: " + (initialDelayMillis / 1000 / 60) + " mins");
        } catch (Exception e) {
            Log.e(TAG, "Error updating WorkManager schedule", e);
        }
    }

    /**
     * Calculates delay in milliseconds until next 12:00 AM.
     */
    private static long calculateInitialDelayToMidnight() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 0);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        // If today's midnight has already passed, schedule for tomorrow's midnight
        if (target.before(now) || target.equals(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * Synchronously exports JSON and saves it into the device's persistent app files directory.
     */
    public static boolean performBackupSync(Context context) {
        TrackingRepository repository = TrackingRepository.getInstance(context);
        CountDownLatch latch = new CountDownLatch(1);
        final String[] resultJson = new String[1];
        final Exception[] resultError = new Exception[1];

        repository.exportDataToJson((json, error) -> {
            resultJson[0] = json;
            resultError[0] = error;
            latch.countDown();
        });

        try {
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed || resultJson[0] == null) {
                return false;
            }

            return saveBackupFile(context, resultJson[0]);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public static File getBackupDirectory(Context context) {
        File dir = new File(context.getFilesDir(), BACKUP_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static boolean saveBackupFile(Context context, String json) {
        try {
            File dir = getBackupDirectory(context);

            // Master cumulative file (overwritten with latest full data export containing all history)
            File masterFile = new File(dir, "lifeflow_cumulative_auto_backup.json");
            try (FileOutputStream fos = new FileOutputStream(masterFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            // Dated snapshot file
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            String fileName = "lifeflow_auto_backup_" + sdf.format(new Date()) + ".json";
            File backupFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(backupFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving backup file", e);
            return false;
        }
    }

    public static File[] getAvailableAutoBackups(Context context) {
        File dir = getBackupDirectory(context);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())); // Newest first
            return files;
        }
        return new File[0];
    }
}
