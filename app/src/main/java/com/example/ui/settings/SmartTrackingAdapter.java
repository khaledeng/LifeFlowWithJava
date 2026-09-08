package com.example.ui.settings;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.entity.Activity;
import com.example.util.SmartTrackingManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class SmartTrackingAdapter extends RecyclerView.Adapter<SmartTrackingAdapter.ViewHolder> {

    private final Context context;
    private final List<Activity> activities;
    private final SmartTrackingManager smartManager;
    private final OnItemInteractionListener listener;
    private final PackageManager packageManager;

    public interface OnItemInteractionListener {
        void onTimeRangeSelected(Activity activity, int startH, int startM, int endH, int endM);
        default void onTimeIntervalsChanged(Activity activity, List<SmartTrackingManager.TimeInterval> intervals) {}
        void onTimeToggled(Activity activity, boolean isEnabled);
        void onBindAppClicked(Activity activity);
        void onSetDefaultClicked(Activity activity);
        void onLockLimitClicked(Activity activity);
    }

    public SmartTrackingAdapter(Context context, List<Activity> activities, SmartTrackingManager smartManager, OnItemInteractionListener listener) {
        this.context = context;
        this.activities = activities;
        this.smartManager = smartManager;
        this.listener = listener;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_smart_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Activity activity = activities.get(position);

        holder.tvName.setText(activity.getNameWithArrow());
        try {
            holder.ivIcon.setColorFilter(Color.parseColor(activity.getColorHex()));
            holder.ivIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(activity.getColorHex()) & 0x40FFFFFF));
        } catch (Exception ignored) { }
        
        int resId = context.getResources().getIdentifier(activity.getIconName(), "drawable", context.getPackageName());
        if (resId != 0) holder.ivIcon.setImageResource(resId);

        // App Lock On Limit Logic
        boolean isLockEnabled = smartManager.isActivityAppLockEnabled(activity);
        if (isLockEnabled) {
            holder.btnLockLimit.setText(context.getString(R.string.smart_tracking_lock_active));
            holder.btnLockLimit.setTextColor(Color.parseColor("#39D353"));
            holder.btnLockLimit.setIconTint(ColorStateList.valueOf(Color.parseColor("#39D353")));
            holder.btnLockLimit.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#39D353")));
            holder.btnLockLimit.setBackgroundColor(Color.parseColor("#2639D353"));
        } else {
            holder.btnLockLimit.setText(context.getString(R.string.smart_tracking_lock_limit));
            holder.btnLockLimit.setTextColor(context.getResources().getColor(R.color.text_secondary));
            holder.btnLockLimit.setIconTint(ColorStateList.valueOf(context.getResources().getColor(R.color.text_secondary)));
            holder.btnLockLimit.setStrokeColor(ColorStateList.valueOf(context.getResources().getColor(R.color.card_stroke)));
            holder.btnLockLimit.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.btnLockLimit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLockLimitClicked(activity);
            }
        });

        // Default Activity Logic
        boolean isDefault = smartManager.isDefaultActivity(activity);
        if (isDefault) {
            holder.btnDefault.setText(context.getString(R.string.smart_tracking_is_default));
            holder.btnDefault.setTextColor(Color.parseColor("#39D353"));
            holder.btnDefault.setIconResource(R.drawable.ic_check_circle);
            holder.btnDefault.setIconTint(ColorStateList.valueOf(Color.parseColor("#39D353")));
            holder.btnDefault.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#39D353")));
            holder.btnDefault.setBackgroundColor(Color.parseColor("#2639D353"));
        } else {
            holder.btnDefault.setText(context.getString(R.string.smart_tracking_set_default));
            holder.btnDefault.setTextColor(context.getResources().getColor(R.color.text_secondary));
            holder.btnDefault.setIconResource(R.drawable.ic_star);
            holder.btnDefault.setIconTint(ColorStateList.valueOf(context.getResources().getColor(R.color.text_secondary)));
            holder.btnDefault.setStrokeColor(ColorStateList.valueOf(context.getResources().getColor(R.color.card_stroke)));
            holder.btnDefault.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.btnDefault.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSetDefaultClicked(activity);
            }
        });

        // Time logic
        boolean isTimeEnabled = smartManager.isActivityTimeEnabled(activity);
        holder.switchTime.setOnCheckedChangeListener(null);
        holder.switchTime.setChecked(isTimeEnabled);
        holder.layoutTimeSettings.setVisibility(isTimeEnabled ? View.VISIBLE : View.GONE);

        holder.switchTime.setOnCheckedChangeListener((buttonView, isChecked) -> {
            holder.layoutTimeSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (listener != null) listener.onTimeToggled(activity, isChecked);
        });

        // Inflate all time intervals
        List<SmartTrackingManager.TimeInterval> intervals = smartManager.getActivityTimeIntervals(activity);
        holder.llTimeIntervals.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < intervals.size(); i++) {
            final int index = i;
            final SmartTrackingManager.TimeInterval ti = intervals.get(i);
            View intervalView = inflater.inflate(R.layout.item_smart_time_interval, holder.llTimeIntervals, false);

            TextView tvStart = intervalView.findViewById(R.id.tv_interval_start);
            TextView tvEnd = intervalView.findViewById(R.id.tv_interval_end);
            ImageView btnDelete = intervalView.findViewById(R.id.btn_delete_interval);

            tvStart.setText(context.getString(R.string.smart_tracking_time_from, formatTime(ti.startHour, ti.startMinute)));
            tvEnd.setText(context.getString(R.string.smart_tracking_time_to, formatTime(ti.endHour, ti.endMinute)));

            tvStart.setOnClickListener(v -> {
                new TimePickerDialog(context, (view, hour, minute) -> {
                    ti.startHour = hour;
                    ti.startMinute = minute;
                    smartManager.setActivityTimeIntervals(activity, intervals, isTimeEnabled);
                    if (listener != null) {
                        listener.onTimeIntervalsChanged(activity, intervals);
                        listener.onTimeRangeSelected(activity, ti.startHour, ti.startMinute, ti.endHour, ti.endMinute);
                    }
                    notifyItemChanged(position);
                }, ti.startHour, ti.startMinute, false).show();
            });

            tvEnd.setOnClickListener(v -> {
                new TimePickerDialog(context, (view, hour, minute) -> {
                    ti.endHour = hour;
                    ti.endMinute = minute;
                    smartManager.setActivityTimeIntervals(activity, intervals, isTimeEnabled);
                    if (listener != null) {
                        listener.onTimeIntervalsChanged(activity, intervals);
                        listener.onTimeRangeSelected(activity, ti.startHour, ti.startMinute, ti.endHour, ti.endMinute);
                    }
                    notifyItemChanged(position);
                }, ti.endHour, ti.endMinute, false).show();
            });

            btnDelete.setVisibility(intervals.size() > 1 ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> {
                intervals.remove(index);
                if (intervals.isEmpty()) {
                    intervals.add(new SmartTrackingManager.TimeInterval(8, 0, 9, 0));
                }
                smartManager.setActivityTimeIntervals(activity, intervals, isTimeEnabled);
                if (listener != null) {
                    listener.onTimeIntervalsChanged(activity, intervals);
                }
                notifyItemChanged(position);
            });

            holder.llTimeIntervals.addView(intervalView);
        }

        holder.btnAddTimeInterval.setOnClickListener(v -> {
            int newStartH = 13;
            int newStartM = 0;
            int newEndH = 15;
            int newEndM = 0;

            if (!intervals.isEmpty()) {
                SmartTrackingManager.TimeInterval last = intervals.get(intervals.size() - 1);
                newStartH = (last.endHour + 1) % 24;
                newStartM = last.endMinute;
                newEndH = (newStartH + 2) % 24;
                newEndM = newStartM;
            }

            intervals.add(new SmartTrackingManager.TimeInterval(newStartH, newStartM, newEndH, newEndM));
            smartManager.setActivityTimeIntervals(activity, intervals, true);
            if (!isTimeEnabled) {
                holder.switchTime.setChecked(true);
            }
            if (listener != null) {
                listener.onTimeIntervalsChanged(activity, intervals);
            }
            notifyItemChanged(position);
        });

        // App Bound Logic
        java.util.Set<String> boundPkgs = smartManager.getActivityBoundApps(activity);
        if (boundPkgs == null || boundPkgs.isEmpty()) {
            holder.tvBoundApp.setText(context.getString(R.string.smart_tracking_no_app_selected));
            holder.tvBoundApp.setTextColor(Color.GRAY);
        } else {
            if (boundPkgs.size() == 1) {
                String pkg = boundPkgs.iterator().next();
                String appName = pkg;
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(pkg, 0);
                    appName = packageManager.getApplicationLabel(appInfo).toString();
                } catch (PackageManager.NameNotFoundException ignored) { }
                holder.tvBoundApp.setText(context.getString(R.string.smart_tracking_bound_to_format, appName));
            } else {
                holder.tvBoundApp.setText(context.getString(R.string.smart_tracking_bound_to_count_format, boundPkgs.size()));
            }
            holder.tvBoundApp.setTextColor(Color.parseColor("#39D353"));
        }

        holder.layoutBindApp.setOnClickListener(v -> {
            if (listener != null) listener.onBindAppClicked(activity);
        });
    }

    @Override
    public int getItemCount() {
        return activities != null ? activities.size() : 0;
    }

    private String formatTime(int hour, int minute) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, minute);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
        return sdf.format(cal.getTime());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        MaterialButton btnLockLimit;
        MaterialButton btnDefault;
        SwitchMaterial switchTime;
        LinearLayout layoutTimeSettings;
        LinearLayout llTimeIntervals;
        MaterialButton btnAddTimeInterval;
        LinearLayout layoutBindApp;
        TextView tvBoundApp;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            btnLockLimit = itemView.findViewById(R.id.btn_lock_limit);
            btnDefault = itemView.findViewById(R.id.btn_default);
            switchTime = itemView.findViewById(R.id.switch_time);
            layoutTimeSettings = itemView.findViewById(R.id.layout_time_settings);
            llTimeIntervals = itemView.findViewById(R.id.ll_time_intervals);
            btnAddTimeInterval = itemView.findViewById(R.id.btn_add_time_interval);
            layoutBindApp = itemView.findViewById(R.id.layout_bind_app);
            tvBoundApp = itemView.findViewById(R.id.tv_bound_app);
        }
    }
}
