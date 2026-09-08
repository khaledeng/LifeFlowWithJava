package com.example.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.example.MainActivity;
import com.example.R;
import com.example.data.TrackingRepository;
import com.example.data.model.AllActivitiesMatrixData;

import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * AppWidgetProvider for the Month Achievement Matrix home screen widget.
 * Enforces a spacious, ultra-readable 7-day focus window with in-place month and day navigation
 * and 1-tap direct background "Make Done" execution right from the Home Screen.
 */
public class MonthMatrixWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_WIDGET = "com.example.ACTION_REFRESH_MONTH_WIDGET";
    public static final String ACTION_UPDATE_WIDGET = "com.example.ACTION_UPDATE_MONTH_WIDGET";
    public static final String ACTION_PREV_MONTH = "com.example.ACTION_PREV_MONTH";
    public static final String ACTION_NEXT_MONTH = "com.example.ACTION_NEXT_MONTH";
    public static final String ACTION_PREV_DAYS = "com.example.ACTION_PREV_DAYS";
    public static final String ACTION_NEXT_DAYS = "com.example.ACTION_NEXT_DAYS";
    public static final String ACTION_PREV_ACT = "com.example.ACTION_PREV_ACT";
    public static final String ACTION_NEXT_ACT = "com.example.ACTION_NEXT_ACT";
    public static final String ACTION_SELECT_CELL = "com.example.ACTION_SELECT_CELL";
    public static final String ACTION_TOGGLE_ONCES = "com.example.ACTION_TOGGLE_ONCES";
    public static final String ACTION_WIDGET_MARK_DONE = "com.example.ACTION_WIDGET_MARK_DONE";
    public static final String ACTION_WIDGET_MARK_UNDONE = "com.example.ACTION_WIDGET_MARK_UNDONE";
    public static final String ACTION_WIDGET_MARK_PAUSE = "com.example.ACTION_WIDGET_MARK_PAUSE";

    public static final String EXTRA_ACTION_ACTIVITY_ID = "extra_action_activity_id";
    public static final String EXTRA_ACTION_DAY = "extra_action_day";
    public static final String EXTRA_ACTION_MONTH_OFFSET = "extra_action_month_offset";

    private static final String PREFS_NAME = "widget_matrix_prefs";
    private static final int WINDOW_SIZE = 7; // Always 7 days for maximum clarity and size

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, appWidgetManager, appWidgetId, newOptions);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && intent.getData() != null) {
            try {
                java.util.List<String> segments = intent.getData().getPathSegments();
                if (segments != null && segments.size() >= 1) {
                    appWidgetId = Integer.parseInt(segments.get(0));
                }
            } catch (Exception ignored) {}
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                AppWidgetManager mgr = AppWidgetManager.getInstance(context);
                ComponentName cn = new ComponentName(context, MonthMatrixWidgetProvider.class);
                int[] allIds = mgr.getAppWidgetIds(cn);
                if (allIds != null && allIds.length > 0) {
                    appWidgetId = allIds[0];
                }
            } catch (Exception ignored) {}
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (ACTION_PREV_MONTH.equals(action)) {
            int monthOffset = prefs.getInt("month_offset_" + appWidgetId, 0);
            prefs.edit()
                    .putInt("month_offset_" + appWidgetId, monthOffset - 1)
                    .putInt("window_index_" + appWidgetId, 0)
                    .apply();
            updateAllWidgets(context);
            return;
        }

        if (ACTION_NEXT_MONTH.equals(action)) {
            int monthOffset = prefs.getInt("month_offset_" + appWidgetId, 0);
            if (monthOffset < 0) {
                prefs.edit()
                        .putInt("month_offset_" + appWidgetId, monthOffset + 1)
                        .putInt("window_index_" + appWidgetId, 0)
                        .apply();
                updateAllWidgets(context);
            }
            return;
        }

        if (ACTION_PREV_DAYS.equals(action)) {
            int windowIndex = prefs.getInt("window_index_" + appWidgetId, 0);
            prefs.edit()
                    .putInt("window_index_" + appWidgetId, Math.max(0, windowIndex - 1))
                    .apply();
            updateAllWidgets(context);
            return;
        }

        if (ACTION_NEXT_DAYS.equals(action)) {
            int windowIndex = prefs.getInt("window_index_" + appWidgetId, 0);
            prefs.edit()
                    .putInt("window_index_" + appWidgetId, Math.min(4, windowIndex + 1))
                    .apply();
            updateAllWidgets(context);
            return;
        }

        if (ACTION_TOGGLE_ONCES.equals(action)) {
            boolean showOnces = prefs.getBoolean("show_onces_" + appWidgetId, false);
            prefs.edit()
                    .putBoolean("show_onces_" + appWidgetId, !showOnces)
                    .apply();
            updateAllWidgets(context);
            return;
        }

        if (ACTION_PREV_ACT.equals(action)) {
            final int finalWidgetId = appWidgetId;
            TrackingRepository repo = TrackingRepository.getInstance(context);
            com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                List<com.example.data.entity.Activity> all = repo.getAllActivitiesSync();
                if (all != null && !all.isEmpty()) {
                    long currentActId = prefs.getLong("selected_act_id_" + finalWidgetId, -1);
                    int curIdx = 0;
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).getId() == currentActId) {
                            curIdx = i;
                            break;
                        }
                    }
                    int newIdx = (curIdx - 1 + all.size()) % all.size();
                    long newActId = all.get(newIdx).getId();
                    prefs.edit()
                            .putInt("act_index_" + finalWidgetId, newIdx)
                            .putLong("selected_act_id_" + finalWidgetId, newActId)
                            .apply();
                    updateAllWidgets(context);
                }
            });
            return;
        }

        if (ACTION_NEXT_ACT.equals(action)) {
            final int finalWidgetId = appWidgetId;
            TrackingRepository repo = TrackingRepository.getInstance(context);
            com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                List<com.example.data.entity.Activity> all = repo.getAllActivitiesSync();
                if (all != null && !all.isEmpty()) {
                    long currentActId = prefs.getLong("selected_act_id_" + finalWidgetId, -1);
                    int curIdx = 0;
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).getId() == currentActId) {
                            curIdx = i;
                            break;
                        }
                    }
                    int newIdx = (curIdx + 1) % all.size();
                    long newActId = all.get(newIdx).getId();
                    prefs.edit()
                            .putInt("act_index_" + finalWidgetId, newIdx)
                            .putLong("selected_act_id_" + finalWidgetId, newActId)
                            .apply();
                    updateAllWidgets(context);
                }
            });
            return;
        }

        if (ACTION_SELECT_CELL.equals(action)) {
            long targetActivityId = intent.getLongExtra(EXTRA_ACTION_ACTIVITY_ID, -1);
            int targetDay = intent.getIntExtra(EXTRA_ACTION_DAY, -1);

            if (targetActivityId <= 0 && intent.getData() != null) {
                try {
                    java.util.List<String> segs = intent.getData().getPathSegments();
                    if (segs != null && segs.size() >= 3) {
                        targetActivityId = Long.parseLong(segs.get(2));
                    }
                    if (segs != null && segs.size() >= 4) {
                        targetDay = Integer.parseInt(segs.get(3));
                    }
                } catch (Exception ignored) {}
            }

            SharedPreferences.Editor editor = prefs.edit();
            if (targetActivityId > 0) {
                editor.putLong("selected_act_id_" + appWidgetId, targetActivityId);
            }
            if (targetDay > 0) {
                editor.putInt("selected_day_" + appWidgetId, targetDay);
            }
            editor.apply();
            updateAllWidgets(context);
            return;
        }

        if (ACTION_WIDGET_MARK_DONE.equals(action)) {
            final long targetActivityId = intent.getLongExtra(EXTRA_ACTION_ACTIVITY_ID, -1);
            final int targetMonthOffset = intent.getIntExtra(EXTRA_ACTION_MONTH_OFFSET, 0);
            final int passedDay = intent.getIntExtra(EXTRA_ACTION_DAY, -1);
            final int finalWidgetId = appWidgetId;

            TrackingRepository repo = TrackingRepository.getInstance(context);
            com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                long actIdToUse = targetActivityId;
                if (actIdToUse <= 0) {
                    actIdToUse = prefs.getLong("selected_act_id_" + finalWidgetId, -1);
                }
                if (actIdToUse <= 0) {
                    List<com.example.data.entity.Activity> all = repo.getAllActivitiesSync();
                    if (all != null && !all.isEmpty()) {
                        int actIndex = prefs.getInt("act_index_" + finalWidgetId, 0);
                        if (actIndex < 0 || actIndex >= all.size()) actIndex = 0;
                        actIdToUse = all.get(actIndex).getId();
                    }
                }

                if (actIdToUse > 0) {
                    com.example.data.entity.Activity act = repo.getActivityByIdSync(actIdToUse);
                    if (act != null) {
                        Calendar cal = Calendar.getInstance();
                        int dayToMark = passedDay;
                        if (dayToMark <= 0) {
                            dayToMark = prefs.getInt("selected_day_" + finalWidgetId, cal.get(Calendar.DAY_OF_MONTH));
                        }

                        final int finalDay = dayToMark;
                        repo.markActivityDoneForDay(act.getId(), targetMonthOffset, finalDay, () -> {
                            updateAllWidgets(context);
                            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                            handler.post(() -> {
                                Calendar targetCal = Calendar.getInstance();
                                targetCal.add(Calendar.MONTH, targetMonthOffset);
                                targetCal.set(Calendar.DAY_OF_MONTH, finalDay);
                                SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
                                String dateStr = sdf.format(targetCal.getTime());
                                String toastMsg = context.getString(R.string.matrix_toast_marked_done, act.getName(), dateStr);
                                Toast.makeText(context.getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show();
                            });
                        });
                    }
                }
            });
            return;
        }

        if (ACTION_WIDGET_MARK_PAUSE.equals(action)) {
            final long targetActivityId = intent.getLongExtra(EXTRA_ACTION_ACTIVITY_ID, -1);
            final int targetMonthOffset = intent.getIntExtra(EXTRA_ACTION_MONTH_OFFSET, 0);
            final int passedDay = intent.getIntExtra(EXTRA_ACTION_DAY, -1);
            final int finalWidgetId = appWidgetId;

            TrackingRepository repo = TrackingRepository.getInstance(context);
            com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                long actIdToUse = targetActivityId;
                if (actIdToUse <= 0) {
                    actIdToUse = prefs.getLong("selected_act_id_" + finalWidgetId, -1);
                }
                if (actIdToUse <= 0) {
                    List<com.example.data.entity.Activity> all = repo.getAllActivitiesSync();
                    if (all != null && !all.isEmpty()) {
                        int actIndex = prefs.getInt("act_index_" + finalWidgetId, 0);
                        if (actIndex < 0 || actIndex >= all.size()) actIndex = 0;
                        actIdToUse = all.get(actIndex).getId();
                    }
                }

                if (actIdToUse > 0) {
                    com.example.data.entity.Activity act = repo.getActivityByIdSync(actIdToUse);
                    if (act != null) {
                        Calendar cal = Calendar.getInstance();
                        int dayToMark = passedDay;
                        if (dayToMark <= 0) {
                            dayToMark = prefs.getInt("selected_day_" + finalWidgetId, cal.get(Calendar.DAY_OF_MONTH));
                        }

                        final int finalDay = dayToMark;
                        repo.markActivityPausedForDay(act.getId(), targetMonthOffset, finalDay, () -> {
                            updateAllWidgets(context);
                            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                            handler.post(() -> {
                                Calendar targetCal = Calendar.getInstance();
                                targetCal.add(Calendar.MONTH, targetMonthOffset);
                                targetCal.set(Calendar.DAY_OF_MONTH, finalDay);
                                SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
                                String dateStr = sdf.format(targetCal.getTime());
                                String toastMsg = context.getString(R.string.matrix_toast_marked_paused, act.getName(), dateStr);
                                Toast.makeText(context.getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show();
                            });
                        });
                    }
                }
            });
            return;
        }

        if (ACTION_WIDGET_MARK_UNDONE.equals(action)) {
            final long targetActivityId = intent.getLongExtra(EXTRA_ACTION_ACTIVITY_ID, -1);
            final int targetMonthOffset = intent.getIntExtra(EXTRA_ACTION_MONTH_OFFSET, 0);
            final int passedDay = intent.getIntExtra(EXTRA_ACTION_DAY, -1);
            final int finalWidgetId = appWidgetId;

            TrackingRepository repo = TrackingRepository.getInstance(context);
            com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                long actIdToUse = targetActivityId;
                if (actIdToUse <= 0) {
                    actIdToUse = prefs.getLong("selected_act_id_" + finalWidgetId, -1);
                }
                if (actIdToUse <= 0) {
                    List<com.example.data.entity.Activity> all = repo.getAllActivitiesSync();
                    if (all != null && !all.isEmpty()) {
                        int actIndex = prefs.getInt("act_index_" + finalWidgetId, 0);
                        if (actIndex < 0 || actIndex >= all.size()) actIndex = 0;
                        actIdToUse = all.get(actIndex).getId();
                    }
                }

                if (actIdToUse > 0) {
                    com.example.data.entity.Activity act = repo.getActivityByIdSync(actIdToUse);
                    if (act != null) {
                        Calendar cal = Calendar.getInstance();
                        int dayToMark = passedDay;
                        if (dayToMark <= 0) {
                            dayToMark = prefs.getInt("selected_day_" + finalWidgetId, cal.get(Calendar.DAY_OF_MONTH));
                        }

                        final int finalDay = dayToMark;
                        repo.markActivityUndoneForDay(act.getId(), targetMonthOffset, finalDay, () -> {
                            updateAllWidgets(context);
                            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                            handler.post(() -> {
                                Calendar targetCal = Calendar.getInstance();
                                targetCal.add(Calendar.MONTH, targetMonthOffset);
                                targetCal.set(Calendar.DAY_OF_MONTH, finalDay);
                                SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
                                String dateStr = sdf.format(targetCal.getTime());
                                String toastMsg = context.getString(R.string.matrix_toast_marked_undone, act.getName(), dateStr);
                                Toast.makeText(context.getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show();
                            });
                        });
                    }
                }
            });
            return;
        }

        if (ACTION_REFRESH_WIDGET.equals(action) ||
                ACTION_UPDATE_WIDGET.equals(action) ||
                Intent.ACTION_DATE_CHANGED.equals(action) ||
                Intent.ACTION_TIME_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            updateAllWidgets(context);
        }
    }

    /**
     * Triggers an asynchronous update for all placed Month Matrix widgets.
     */
    public static void updateAllWidgets(Context context) {
        if (context == null) return;
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, MonthMatrixWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            if (appWidgetIds != null && appWidgetIds.length > 0) {
                for (int id : appWidgetIds) {
                    updateWidget(context, appWidgetManager, id);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        updateWidget(context, appWidgetManager, appWidgetId, null);
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle optionsOverride) {
        Bundle options = (optionsOverride != null) ? optionsOverride : appWidgetManager.getAppWidgetOptions(appWidgetId);
        int orientation = context.getResources().getConfiguration().orientation;
        int widthDp = 0;
        int heightDp = 0;

        if (options != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    java.util.ArrayList<android.util.SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                    if (sizes != null && !sizes.isEmpty()) {
                        for (android.util.SizeF size : sizes) {
                            if (size != null && size.getWidth() > 0 && size.getHeight() > 0) {
                                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    if (size.getWidth() >= size.getHeight()) {
                                        widthDp = (int) size.getWidth();
                                        heightDp = (int) size.getHeight();
                                        break;
                                    }
                                } else {
                                    if (size.getHeight() >= size.getWidth() || widthDp == 0) {
                                        widthDp = (int) size.getWidth();
                                        heightDp = (int) size.getHeight();
                                        if (size.getHeight() >= size.getWidth()) break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (widthDp <= 0 || heightDp <= 0) {
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
                    heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                } else {
                    widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                    heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                }
            }

            if (widthDp <= 0) {
                widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            }
            if (widthDp <= 0) {
                widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            }
            if (heightDp <= 0) {
                heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            }
            if (heightDp <= 0) {
                heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            }
        }

        if (widthDp <= 0) widthDp = 300;
        if (heightDp <= 0) heightDp = 180;

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = (dm != null && dm.density > 0) ? dm.density : 2.5f;

        // Account for widget outer padding (8dp * 2 = 16dp) and header row
        int headerHeightDp = (heightDp < 130) ? 28 : 34;
        int canvasWidthDp = Math.max(widthDp - 16, 120);
        int canvasHeightDp = Math.max(heightDp - headerHeightDp - 6, 60);

        // Render scale: 1.5x - 2.0x for crisp Canvas rendering
        float renderScale = Math.min(density, 2.0f);
        int rawWidthPx = (int) (canvasWidthDp * renderScale);
        int rawHeightPx = (int) (canvasHeightDp * renderScale);

        // Cap total pixels to 180,000 pixels (720 KB) to strictly respect Android RemoteViews Binder limit (1MB),
        // while preserving EXACT aspect ratio so resizing doesn't distort or add letterboxing.
        float totalPixels = (float) rawWidthPx * rawHeightPx;
        int targetWidthPx = rawWidthPx;
        int targetHeightPx = rawHeightPx;

        if (totalPixels > 180000f) {
            float aspect = (float) canvasWidthDp / (float) canvasHeightDp;
            targetHeightPx = Math.max(60, (int) Math.sqrt(180000f / aspect));
            targetWidthPx = Math.max(120, (int) (targetHeightPx * aspect));
        }

        final int finalWidthDp = widthDp;
        final int finalHeightDp = heightDp;
        final int finalTargetWidthPx = targetWidthPx;
        final int finalTargetHeightPx = targetHeightPx;

        // Preferences for this widget instance
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int monthOffset = prefs.getInt("month_offset_" + appWidgetId, 0);
        int dayOffset = prefs.getInt("day_offset_" + appWidgetId, 0);
        boolean showOnces = prefs.getBoolean("show_onces_" + appWidgetId, false);

        // Fetch month matrix for chosen monthOffset
        TrackingRepository repository = TrackingRepository.getInstance(context);
        repository.calculateAllActivitiesMatrix(monthOffset, showOnces, matrix -> {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_month_matrix);

            // Month Name only without year as requested: "اخفي اسم السنه اسم الشهر يكفي"
            String displayMonth = "";
            try {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, monthOffset);
                java.text.SimpleDateFormat sdfMonthOnly = new java.text.SimpleDateFormat("MMMM", Locale.getDefault());
                displayMonth = sdfMonthOnly.format(cal.getTime());
            } catch (Exception ignored) {}

            if (displayMonth == null || displayMonth.isEmpty()) {
                if (matrix != null && matrix.monthName != null) {
                    displayMonth = matrix.monthName.replaceAll("\\d{4}", "").trim();
                }
            }
            if (displayMonth != null && !displayMonth.isEmpty()) {
                displayMonth = displayMonth.substring(0, 1).toUpperCase(Locale.getDefault()) + displayMonth.substring(1);
                views.setTextViewText(R.id.tv_widget_title, displayMonth);
            } else {
                views.setTextViewText(R.id.tv_widget_title, context.getString(R.string.widget_month_matrix_title));
            }

            int totalDays = (matrix != null && matrix.daysInMonth > 0) ? matrix.daysInMonth : 30;
            int todayDay = -999;
            boolean isCurrentMonth = (monthOffset == 0);

            if (isCurrentMonth) {
                if (matrix != null && matrix.dayHeaders != null) {
                    for (int i = 0; i < matrix.dayHeaders.size(); i++) {
                        if (matrix.dayHeaders.get(i).isToday) {
                            todayDay = matrix.dayHeaders.get(i).dayOfMonth;
                            break;
                        }
                    }
                }
                if (todayDay <= 0) {
                    todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                }
                todayDay = Math.max(1, Math.min(todayDay, totalDays));
            }

            // Strict 7-day window calculation: "اظهر بال7 ايام بس مش اكتر عشان الشكل ميبقاش صغير اوي كدا"
            int maxWindowIndex = Math.max(0, (totalDays - 1) / 7);
            int defaultWindowIndex = (monthOffset == 0) ? Math.min(maxWindowIndex, Math.max(0, (todayDay - 1) / 7)) : 0;
            int windowIndex = prefs.getInt("window_index_" + appWidgetId, defaultWindowIndex);
            windowIndex = Math.max(0, Math.min(maxWindowIndex, windowIndex));

            int startDay = windowIndex * 7 + 1;
            int endDay = Math.min(totalDays, startDay + 6);
            if (endDay - startDay + 1 < 7 && totalDays >= 7) {
                startDay = Math.max(1, endDay - 6);
            }

            // Days range text (e.g. 1 - 7 or 1-7)
            String rangeText = (finalWidthDp < 240) ? (startDay + "-" + endDay) : (startDay + " - " + endDay);
            views.setTextViewText(R.id.tv_widget_days_range, rangeText);

            // Responsive typography based on widget width
            if (finalWidthDp < 250) {
                views.setTextViewTextSize(R.id.tv_widget_title, TypedValue.COMPLEX_UNIT_SP, 12f);
                views.setTextViewTextSize(R.id.tv_widget_days_range, TypedValue.COMPLEX_UNIT_SP, 10f);
            } else {
                views.setTextViewTextSize(R.id.tv_widget_title, TypedValue.COMPLEX_UNIT_SP, 14f);
                views.setTextViewTextSize(R.id.tv_widget_days_range, TypedValue.COMPLEX_UNIT_SP, 12f);
            }

            // Button alpha visual hints
            if (monthOffset >= 0) {
                views.setInt(R.id.btn_widget_next_month, "setAlpha", 90);
            } else {
                views.setInt(R.id.btn_widget_next_month, "setAlpha", 255);
            }
            views.setInt(R.id.btn_widget_prev_month, "setAlpha", 255);

            if (windowIndex <= 0) {
                views.setInt(R.id.btn_widget_prev_days, "setAlpha", 90);
            } else {
                views.setInt(R.id.btn_widget_prev_days, "setAlpha", 255);
            }
            if (windowIndex >= maxWindowIndex) {
                views.setInt(R.id.btn_widget_next_days, "setAlpha", 90);
            } else {
                views.setInt(R.id.btn_widget_next_days, "setAlpha", 255);
            }

            // Today completion badge
            int completedToday = 0;
            int totalActs = (matrix != null && matrix.rows != null) ? matrix.rows.size() : 0;
            if (isCurrentMonth && matrix != null && matrix.rows != null) {
                for (AllActivitiesMatrixData.ActivityRow row : matrix.rows) {
                    if (row.dayCells != null) {
                        for (AllActivitiesMatrixData.DayCell cell : row.dayCells) {
                            if (cell.isToday && cell.status == 2) {
                                completedToday++;
                                break;
                            }
                        }
                    }
                }
            }

            if (isCurrentMonth && totalActs > 0) {
                views.setViewVisibility(R.id.tv_widget_today_badge, View.VISIBLE);
                views.setTextViewText(R.id.tv_widget_today_badge,
                        String.format(context.getString(R.string.widget_today_badge), completedToday, totalActs));
            } else {
                views.setViewVisibility(R.id.tv_widget_today_badge, View.GONE);
            }

            // Toggle Onces Button state & styling
            if (showOnces) {
                views.setTextViewText(R.id.btn_widget_toggle_onces, "Hide onces");
                views.setTextColor(R.id.btn_widget_toggle_onces, Color.parseColor("#FF9F0A"));
                views.setInt(R.id.btn_widget_toggle_onces, "setBackgroundResource", R.drawable.bg_widget_onces_hide_pill);
            } else {
                views.setTextViewText(R.id.btn_widget_toggle_onces, "Show onces");
                views.setTextColor(R.id.btn_widget_toggle_onces, Color.parseColor("#30D158"));
                views.setInt(R.id.btn_widget_toggle_onces, "setBackgroundResource", R.drawable.bg_widget_onces_show_pill);
            }

            int flag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ?
                    (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) :
                    PendingIntent.FLAG_UPDATE_CURRENT;

            boolean isAr = Locale.getDefault().getLanguage().equals("ar");

            // Build Dynamic Matrix View (Header + Rows with individual clickable day cells)
            views.removeAllViews(R.id.layout_matrix_rows_container);

            if (totalActs == 0 || matrix == null || matrix.rows == null || matrix.rows.isEmpty()) {
                views.setViewVisibility(R.id.tv_widget_empty, View.VISIBLE);
                views.setViewVisibility(R.id.layout_quick_done_bar, View.GONE);
            } else {
                views.setViewVisibility(R.id.tv_widget_empty, View.GONE);

                // Determine selected activity & day
                long storedActId = prefs.getLong("selected_act_id_" + appWidgetId, -1);
                int storedDay = prefs.getInt("selected_day_" + appWidgetId, (todayDay > 0 ? todayDay : startDay));
                int actIndex = prefs.getInt("act_index_" + appWidgetId, 0);

                if (storedDay < startDay || storedDay > endDay) {
                    storedDay = (todayDay >= startDay && todayDay <= endDay) ? todayDay : startDay;
                }

                int foundIndex = -1;
                for (int i = 0; i < matrix.rows.size(); i++) {
                    if (matrix.rows.get(i).activity.getId() == storedActId) {
                        foundIndex = i;
                        break;
                    }
                }
                if (foundIndex < 0) {
                    if (actIndex >= 0 && actIndex < matrix.rows.size()) {
                        foundIndex = actIndex;
                    } else {
                        foundIndex = 0;
                    }
                    storedActId = matrix.rows.get(foundIndex).activity.getId();
                }
                final long selectedActId = storedActId;
                final int selectedDay = storedDay;
                final int selectedActIndex = foundIndex;

                // 1. Add Header Row
                RemoteViews headerRow = new RemoteViews(context.getPackageName(), R.layout.widget_matrix_header_row);
                headerRow.setTextViewText(R.id.tv_header_act_title, context.getString(R.string.widget_act_header));
                int[] headerDayIds = {
                        R.id.tv_header_day_0, R.id.tv_header_day_1, R.id.tv_header_day_2,
                        R.id.tv_header_day_3, R.id.tv_header_day_4, R.id.tv_header_day_5, R.id.tv_header_day_6
                };

                for (int d = 0; d < WINDOW_SIZE; d++) {
                    int dayNum = startDay + d;
                    int col = isAr ? (WINDOW_SIZE - 1 - d) : d;
                    if (col < headerDayIds.length && dayNum <= endDay) {
                        headerRow.setTextViewText(headerDayIds[col], String.valueOf(dayNum));
                        if (dayNum == todayDay && isCurrentMonth) {
                            headerRow.setTextColor(headerDayIds[col], android.graphics.Color.parseColor("#60CDFF")); // Cyan for today
                        } else if (dayNum == selectedDay) {
                            headerRow.setTextColor(headerDayIds[col], android.graphics.Color.parseColor("#FFD60A")); // Gold for selected day
                        } else {
                            headerRow.setTextColor(headerDayIds[col], android.graphics.Color.parseColor("#8E8E98"));
                        }
                    }
                }
                views.addView(R.id.layout_matrix_rows_container, headerRow);

                // 2. Add Activity Rows
                int[] cellIds = {
                        R.id.iv_cell_0, R.id.iv_cell_1, R.id.iv_cell_2,
                        R.id.iv_cell_3, R.id.iv_cell_4, R.id.iv_cell_5, R.id.iv_cell_6
                };

                for (int i = 0; i < matrix.rows.size(); i++) {
                    AllActivitiesMatrixData.ActivityRow row = matrix.rows.get(i);
                    RemoteViews rowView = new RemoteViews(context.getPackageName(), R.layout.widget_matrix_row);
                    long rowActId = row.activity.getId();
                    boolean isRowSelected = (rowActId == selectedActId);

                    // Row Background for selected activity
                    if (isRowSelected) {
                        rowView.setInt(R.id.layout_row_root, "setBackgroundResource", R.drawable.bg_widget_row_selected);
                    } else {
                        rowView.setInt(R.id.layout_row_root, "setBackgroundResource", 0);
                    }

                    // Activity Name (with ⚡ prefix if once activity)
                    String actDisplayName = row.activity.isOnce() ? ("⚡ " + row.activity.getName()) : row.activity.getName();
                    rowView.setTextViewText(R.id.tv_row_act_name, actDisplayName);

                    // Activity Color Dot
                    int actColor = android.graphics.Color.parseColor("#39D353");
                    try {
                        if (row.activity.getColorHex() != null) {
                            actColor = android.graphics.Color.parseColor(row.activity.getColorHex());
                        }
                    } catch (Exception ignored) {}
                    rowView.setImageViewBitmap(R.id.iv_row_dot, MatrixBitmapRenderer.renderDotBitmap(actColor, 24));

                    // Tapping on Activity Name selects this activity for the active day
                    Intent selectActIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                    selectActIntent.setAction(ACTION_SELECT_CELL);
                    selectActIntent.setPackage(context.getPackageName());
                    selectActIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    selectActIntent.putExtra(EXTRA_ACTION_ACTIVITY_ID, rowActId);
                    selectActIntent.putExtra(EXTRA_ACTION_DAY, selectedDay);
                    selectActIntent.putExtra(EXTRA_ACTION_MONTH_OFFSET, monthOffset);
                    selectActIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/select_act/" + rowActId + "/" + selectedDay));
                    int actReqCode = Math.abs(java.util.Objects.hash(appWidgetId, "act", rowActId, selectedDay, monthOffset));
                    rowView.setOnClickPendingIntent(R.id.layout_row_act_info,
                            PendingIntent.getBroadcast(context, actReqCode, selectActIntent, flag));

                    // 7 Day Cells
                    for (int d = 0; d < WINDOW_SIZE; d++) {
                        int dayNum = startDay + d;
                        int col = isAr ? (WINDOW_SIZE - 1 - d) : d;
                        if (col < cellIds.length && dayNum <= endDay) {
                            AllActivitiesMatrixData.DayCell cell = (row.dayCells != null && dayNum <= row.dayCells.size()) ?
                                    row.dayCells.get(dayNum - 1) : null;

                            boolean isCellSelected = isRowSelected && (dayNum == selectedDay);
                            boolean isCellToday = isCurrentMonth && (dayNum == todayDay);

                            Bitmap cellBmp = MatrixBitmapRenderer.renderCellBitmap(context, cell, row.activity.getCategory(),
                                    actColor, isCellSelected, isCellToday, 64);
                            rowView.setImageViewBitmap(cellIds[col], cellBmp);

                            // Tapping on THIS specific circle selects this exact activity and day!
                            Intent selectCellIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                            selectCellIntent.setAction(ACTION_SELECT_CELL);
                            selectCellIntent.setPackage(context.getPackageName());
                            selectCellIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                            selectCellIntent.putExtra(EXTRA_ACTION_ACTIVITY_ID, rowActId);
                            selectCellIntent.putExtra(EXTRA_ACTION_DAY, dayNum);
                            selectCellIntent.putExtra(EXTRA_ACTION_MONTH_OFFSET, monthOffset);
                            selectCellIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/cell/" + rowActId + "/" + dayNum + "/" + monthOffset));

                            int cellReqCode = Math.abs(java.util.Objects.hash(appWidgetId, "cell", rowActId, dayNum, monthOffset));
                            rowView.setOnClickPendingIntent(cellIds[col],
                                    PendingIntent.getBroadcast(context, cellReqCode, selectCellIntent, flag));
                        }
                    }

                    views.addView(R.id.layout_matrix_rows_container, rowView);
                }

                // 3. Quick Action Bar (Done / Pause / Undone for selected Activity & Day)
                AllActivitiesMatrixData.ActivityRow selectedRow = matrix.rows.get(selectedActIndex);
                String actName = selectedRow.activity.getName();

                // Check completion / pause status for the chosen day
                boolean isDoneSelectedDay = false;
                boolean isPausedSelectedDay = false;
                if (selectedRow.dayCells != null && selectedDay <= selectedRow.dayCells.size() && selectedDay >= 1) {
                    AllActivitiesMatrixData.DayCell targetCell = selectedRow.dayCells.get(selectedDay - 1);
                    if (targetCell != null) {
                        if (targetCell.status == 2) {
                            isDoneSelectedDay = true;
                        } else if (targetCell.status == 3 || targetCell.isPaused) {
                            isPausedSelectedDay = true;
                        }
                    }
                }

                Calendar calSelected = Calendar.getInstance();
                calSelected.add(Calendar.MONTH, monthOffset);
                calSelected.set(Calendar.DAY_OF_MONTH, Math.min(selectedDay, calSelected.getActualMaximum(Calendar.DAY_OF_MONTH)));
                SimpleDateFormat sdfDayMonth = new SimpleDateFormat("d MMM", Locale.getDefault());
                String dateTitle = sdfDayMonth.format(calSelected.getTime());

                views.setViewVisibility(R.id.layout_quick_done_bar, View.VISIBLE);
                String statusSuffix = isDoneSelectedDay ? " ✓" : (isPausedSelectedDay ? " ⏸" : "");
                String barDisplay = actName + " • " + dateTitle + statusSuffix;
                views.setTextViewText(R.id.tv_widget_selected_act_name, barDisplay);

                // Prev Act Button
                Intent prevActIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                prevActIntent.setAction(ACTION_PREV_ACT);
                prevActIntent.setPackage(context.getPackageName());
                prevActIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                prevActIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/prev_act"));
                views.setOnClickPendingIntent(R.id.btn_widget_prev_act,
                        PendingIntent.getBroadcast(context, appWidgetId * 100 + 8, prevActIntent, flag));

                // Next Act Button
                Intent nextActIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                nextActIntent.setAction(ACTION_NEXT_ACT);
                nextActIntent.setPackage(context.getPackageName());
                nextActIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                nextActIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/next_act"));
                views.setOnClickPendingIntent(R.id.btn_widget_next_act,
                        PendingIntent.getBroadcast(context, appWidgetId * 100 + 9, nextActIntent, flag));

                // Quick Done Button Intent (Direct Background Execution for selected activity and day)
                Intent quickDoneIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                quickDoneIntent.setAction(ACTION_WIDGET_MARK_DONE);
                quickDoneIntent.setPackage(context.getPackageName());
                quickDoneIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                quickDoneIntent.putExtra(EXTRA_ACTION_ACTIVITY_ID, selectedActId);
                quickDoneIntent.putExtra(EXTRA_ACTION_DAY, selectedDay);
                quickDoneIntent.putExtra(EXTRA_ACTION_MONTH_OFFSET, monthOffset);
                quickDoneIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/quick_done/" + selectedActId + "/" + selectedDay + "/" + monthOffset));
                views.setOnClickPendingIntent(R.id.btn_widget_quick_done,
                        PendingIntent.getBroadcast(context, appWidgetId * 100 + 10, quickDoneIntent, flag));

                // Quick Pause Button Intent (Direct Background Execution for selected activity and day)
                Intent quickPauseIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                quickPauseIntent.setAction(ACTION_WIDGET_MARK_PAUSE);
                quickPauseIntent.setPackage(context.getPackageName());
                quickPauseIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                quickPauseIntent.putExtra(EXTRA_ACTION_ACTIVITY_ID, selectedActId);
                quickPauseIntent.putExtra(EXTRA_ACTION_DAY, selectedDay);
                quickPauseIntent.putExtra(EXTRA_ACTION_MONTH_OFFSET, monthOffset);
                quickPauseIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/quick_pause/" + selectedActId + "/" + selectedDay + "/" + monthOffset));
                views.setOnClickPendingIntent(R.id.btn_widget_quick_pause,
                        PendingIntent.getBroadcast(context, appWidgetId * 100 + 12, quickPauseIntent, flag));

                // Quick Undone Button Intent (Direct Background Execution for selected activity and day)
                Intent quickUndoneIntent = new Intent(context, MonthMatrixWidgetProvider.class);
                quickUndoneIntent.setAction(ACTION_WIDGET_MARK_UNDONE);
                quickUndoneIntent.setPackage(context.getPackageName());
                quickUndoneIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                quickUndoneIntent.putExtra(EXTRA_ACTION_ACTIVITY_ID, selectedActId);
                quickUndoneIntent.putExtra(EXTRA_ACTION_DAY, selectedDay);
                quickUndoneIntent.putExtra(EXTRA_ACTION_MONTH_OFFSET, monthOffset);
                quickUndoneIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/quick_undone/" + selectedActId + "/" + selectedDay + "/" + monthOffset));
                views.setOnClickPendingIntent(R.id.btn_widget_quick_undone,
                        PendingIntent.getBroadcast(context, appWidgetId * 100 + 11, quickUndoneIntent, flag));
            }

            // 1. Month Navigation Intents
            Intent prevMonthIntent = new Intent(context, MonthMatrixWidgetProvider.class);
            prevMonthIntent.setAction(ACTION_PREV_MONTH);
            prevMonthIntent.setPackage(context.getPackageName());
            prevMonthIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            prevMonthIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/prev_month"));
            views.setOnClickPendingIntent(R.id.btn_widget_prev_month,
                    PendingIntent.getBroadcast(context, appWidgetId * 100 + 1, prevMonthIntent, flag));

            Intent nextMonthIntent = new Intent(context, MonthMatrixWidgetProvider.class);
            nextMonthIntent.setAction(ACTION_NEXT_MONTH);
            nextMonthIntent.setPackage(context.getPackageName());
            nextMonthIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            nextMonthIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/next_month"));
            views.setOnClickPendingIntent(R.id.btn_widget_next_month,
                    PendingIntent.getBroadcast(context, appWidgetId * 100 + 2, nextMonthIntent, flag));

            // 2. Day Navigation Intents (Step by 7 days)
            Intent prevDaysIntent = new Intent(context, MonthMatrixWidgetProvider.class);
            prevDaysIntent.setAction(ACTION_PREV_DAYS);
            prevDaysIntent.setPackage(context.getPackageName());
            prevDaysIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            prevDaysIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/prev_days"));
            views.setOnClickPendingIntent(R.id.btn_widget_prev_days,
                    PendingIntent.getBroadcast(context, appWidgetId * 100 + 3, prevDaysIntent, flag));

            Intent nextDaysIntent = new Intent(context, MonthMatrixWidgetProvider.class);
            nextDaysIntent.setAction(ACTION_NEXT_DAYS);
            nextDaysIntent.setPackage(context.getPackageName());
            nextDaysIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            nextDaysIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/next_days"));
            views.setOnClickPendingIntent(R.id.btn_widget_next_days,
                    PendingIntent.getBroadcast(context, appWidgetId * 100 + 4, nextDaysIntent, flag));

            // 3. Refresh Button Intent
            Intent refreshIntent = new Intent(context, MonthMatrixWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_WIDGET);
            refreshIntent.setPackage(context.getPackageName());
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            refreshIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/refresh"));
            views.setOnClickPendingIntent(R.id.btn_widget_refresh,
                    PendingIntent.getBroadcast(context, appWidgetId * 100 + 5, refreshIntent, flag));

            // 4. Toggle Onces Button Intent
            Intent toggleOncesIntent = new Intent(context, MonthMatrixWidgetProvider.class);
            toggleOncesIntent.setAction(ACTION_TOGGLE_ONCES);
            toggleOncesIntent.setPackage(context.getPackageName());
            toggleOncesIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            toggleOncesIntent.setData(android.net.Uri.parse("widget://month_matrix/" + appWidgetId + "/toggle_onces"));
            views.setOnClickPendingIntent(R.id.btn_widget_toggle_onces,
                    PendingIntent.getBroadcast(context, appWidgetId * 100 + 15, toggleOncesIntent, flag));

            // 5. Tap on Title to Open Main App Track Progress
            Intent openAppIntent = new Intent(context, com.example.MainActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            views.setOnClickPendingIntent(R.id.tv_widget_title,
                    PendingIntent.getActivity(context, appWidgetId * 100 + 7, openAppIntent, flag));

            // Apply update
            try {
                appWidgetManager.updateAppWidget(appWidgetId, views);
            } catch (Exception ignored) {}
        } catch (Throwable t) {
            android.util.Log.e("MatrixWidget", "Error updating widget", t);
        }
    });
    }
}
