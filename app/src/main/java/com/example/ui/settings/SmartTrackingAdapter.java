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

        int sh = smartManager.getActivityStartHour(activity);
        int sm = smartManager.getActivityStartMinute(activity);
        int eh = smartManager.getActivityEndHour(activity);
        int em = smartManager.getActivityEndMinute(activity);

        holder.tvTimeStart.setText(context.getString(R.string.smart_tracking_time_from, formatTime(sh, sm)));
        holder.tvTimeEnd.setText(context.getString(R.string.smart_tracking_time_to, formatTime(eh, em)));

        holder.tvTimeStart.setOnClickListener(v -> {
            new TimePickerDialog(context, (view, hour, minute) -> {
                if (listener != null) listener.onTimeRangeSelected(activity, hour, minute, eh, em);
                notifyItemChanged(position);
            }, sh, sm, false).show();
        });

        holder.tvTimeEnd.setOnClickListener(v -> {
            new TimePickerDialog(context, (view, hour, minute) -> {
                if (listener != null) listener.onTimeRangeSelected(activity, sh, sm, hour, minute);
                notifyItemChanged(position);
            }, eh, em, false).show();
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
        TextView tvTimeStart;
        TextView tvTimeEnd;
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
            tvTimeStart = itemView.findViewById(R.id.tv_time_start);
            tvTimeEnd = itemView.findViewById(R.id.tv_time_end);
            layoutBindApp = itemView.findViewById(R.id.layout_bind_app);
            tvBoundApp = itemView.findViewById(R.id.tv_bound_app);
        }
    }
}
