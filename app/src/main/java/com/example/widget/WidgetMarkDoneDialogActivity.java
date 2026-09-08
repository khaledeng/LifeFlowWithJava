package com.example.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.data.TrackingRepository;
import com.example.data.entity.Activity;
import com.example.util.HapticHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Transparent Dialog Activity launched when clicking on a widget cell or when
 * the user wants to mark an activity as DONE directly from the Home Screen widget.
 */
public class WidgetMarkDoneDialogActivity extends AppCompatActivity {

    public static final String EXTRA_ACTIVITY_ID = "extra_activity_id";
    public static final String EXTRA_MONTH_OFFSET = "extra_month_offset";
    public static final String EXTRA_DAY_OF_MONTH = "extra_day_of_month";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        long activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1);
        int monthOffset = intent.getIntExtra(EXTRA_MONTH_OFFSET, 0);
        int dayOfMonth = intent.getIntExtra(EXTRA_DAY_OF_MONTH, -1);

        TrackingRepository repository = TrackingRepository.getInstance(this);

        if (activityId > 0 && dayOfMonth > 0) {
            showDirectDialog(repository, activityId, monthOffset, dayOfMonth);
        } else {
            // If clicked generally on the widget canvas, let user pick which activity to mark done for today
            showActivityPickerDialog(repository, monthOffset, dayOfMonth > 0 ? dayOfMonth : Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        }
    }

    private void showDirectDialog(TrackingRepository repository, long activityId, int monthOffset, int dayOfMonth) {
        AppDatabaseExecution(repository, activityId, (activity) -> {
            if (activity == null || isFinishing() || isDestroyed()) {
                finish();
                return;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MONTH, monthOffset);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            SimpleDateFormat sdfDate = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
            String formattedDate = sdfDate.format(cal.getTime());

            float targetHours = activity.getExpectedHoursPerDay();
            String targetFormatted = (targetHours > 0) ?
                    String.format(Locale.getDefault(), "%.1fh", targetHours) : "1h";

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_mark_activity_done, null);
            AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_LifeFlow_Dialog)
                    .setView(dialogView)
                    .setCancelable(true)
                    .setOnCancelListener(d -> finish())
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            View viewDot = dialogView.findViewById(R.id.view_activity_dot);
            TextView tvName = dialogView.findViewById(R.id.tv_activity_name);
            TextView tvDetails = dialogView.findViewById(R.id.tv_activity_details);
            View btnNo = dialogView.findViewById(R.id.btn_dialog_no);
            View btnDone = dialogView.findViewById(R.id.btn_dialog_done);
            View btnPause = dialogView.findViewById(R.id.btn_dialog_pause);
            View btnUndone = dialogView.findViewById(R.id.btn_dialog_undone);

            int actColor;
            try {
                actColor = Color.parseColor(activity.getColorHex());
            } catch (Exception e) {
                actColor = Color.parseColor("#30D158");
            }

            if (viewDot != null) {
                GradientDrawable dotDrawable = new GradientDrawable();
                dotDrawable.setShape(GradientDrawable.OVAL);
                dotDrawable.setColor(actColor);
                viewDot.setBackground(dotDrawable);
            }

            if (tvName != null) {
                tvName.setText(activity.getName());
                tvName.setTextColor(actColor);
            }

            if (tvDetails != null) {
                tvDetails.setText(getString(R.string.daily_target_label) + ": " + targetFormatted + " • " + formattedDate);
            }

            if (btnNo != null) {
                btnNo.setOnClickListener(v -> {
                    dialog.dismiss();
                    finish();
                });
            }

            if (btnPause != null) {
                btnPause.setOnClickListener(v -> {
                    dialog.dismiss();
                    HapticHelper.vibrateSuccess(this);
                    repository.markActivityPausedForDay(activity.getId(), monthOffset, dayOfMonth, () -> {
                        runOnUiThread(() -> {
                            String toastMsg = getString(R.string.matrix_toast_marked_paused, activity.getName(), formattedDate);
                            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
                            MonthMatrixWidgetProvider.updateAllWidgets(this);
                            finish();
                        });
                    });
                });
            }

            if (btnDone != null) {
                btnDone.setOnClickListener(v -> {
                    dialog.dismiss();
                    HapticHelper.vibrateSuccess(this);
                    repository.markActivityDoneForDay(activity.getId(), monthOffset, dayOfMonth, () -> {
                        runOnUiThread(() -> {
                            String toastMsg = getString(R.string.matrix_toast_marked_done, activity.getName(), formattedDate);
                            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
                            MonthMatrixWidgetProvider.updateAllWidgets(this);
                            finish();
                        });
                    });
                });
            }

            if (btnUndone != null) {
                btnUndone.setOnClickListener(v -> {
                    dialog.dismiss();
                    HapticHelper.performClick(v);
                    repository.markActivityUndoneForDay(activity.getId(), monthOffset, dayOfMonth, () -> {
                        runOnUiThread(() -> {
                            String toastMsg = getString(R.string.matrix_toast_marked_undone, activity.getName(), formattedDate);
                            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
                            MonthMatrixWidgetProvider.updateAllWidgets(this);
                            finish();
                        });
                    });
                });
            }

            dialog.show();
        });
    }

    private void showActivityPickerDialog(TrackingRepository repository, int monthOffset, int dayOfMonth) {
        com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Activity> activities = repository.getAllActivitiesSync();
            runOnUiThread(() -> {
                if (activities == null || activities.isEmpty() || isFinishing() || isDestroyed()) {
                    finish();
                    return;
                }

                String[] names = new String[activities.size()];
                for (int i = 0; i < activities.size(); i++) {
                    names[i] = activities.get(i).getName();
                }

                new AlertDialog.Builder(this, R.style.Theme_LifeFlow_Dialog)
                        .setTitle(R.string.select_activity_label)
                        .setItems(names, (dialog, which) -> {
                            Activity selected = activities.get(which);
                            showDirectDialog(repository, selected.getId(), monthOffset, dayOfMonth);
                        })
                        .setOnCancelListener(d -> finish())
                        .show();
            });
        });
    }

    private void AppDatabaseExecution(TrackingRepository repository, long activityId, ActivityLoadedCallback callback) {
        com.example.data.AppDatabase.databaseWriteExecutor.execute(() -> {
            Activity act = repository.getActivityByIdSync(activityId);
            runOnUiThread(() -> callback.onLoaded(act));
        });
    }

    private interface ActivityLoadedCallback {
        void onLoaded(Activity activity);
    }
}
