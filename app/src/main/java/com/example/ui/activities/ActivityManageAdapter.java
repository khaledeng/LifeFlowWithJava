package com.example.ui.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.entity.Activity;
import com.example.databinding.ItemActivityManageBinding;
import com.example.util.IconHelper;

import java.util.ArrayList;
import java.util.List;

public class ActivityManageAdapter extends RecyclerView.Adapter<ActivityManageAdapter.ViewHolder> {

    public interface OnActivityManageListener {
        void onEditActivity(Activity activity);
        void onDeleteActivity(Activity activity);
    }

    private List<Activity> activities = new ArrayList<>();
    private final OnActivityManageListener listener;

    public ActivityManageAdapter(OnActivityManageListener listener) {
        this.listener = listener;
    }

    public void setActivities(List<Activity> activities) {
        this.activities = activities != null ? new ArrayList<>(activities) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public boolean onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= activities.size() ||
            toPosition < 0 || toPosition >= activities.size()) {
            return false;
        }
        java.util.Collections.swap(activities, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemActivityManageBinding binding = ItemActivityManageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(activities.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemActivityManageBinding binding;

        ViewHolder(ItemActivityManageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Activity activity, OnActivityManageListener listener) {
            binding.tvManageName.setText(activity.getName());

            android.content.Context context = binding.getRoot().getContext();
            String categoryStr = "⚖️ " + context.getString(com.example.R.string.goal_normal);
            if (activity.getCategory() == com.example.data.entity.ActivityCategory.INCREASE) {
                categoryStr = "📈 " + context.getString(com.example.R.string.goal_increase);
            } else if (activity.getCategory() == com.example.data.entity.ActivityCategory.DECREASE) {
                categoryStr = "📉 " + context.getString(com.example.R.string.goal_decrease);
            }

            if (activity.getExpectedHoursPerDay() > 0) {
                String targetLabel = context.getString(com.example.R.string.target_label_short);
                String hoursPerDay = context.getString(com.example.R.string.hours_per_day_short);
                binding.tvManageDetails.setText(String.format(java.util.Locale.US, "%s • %s: \u200E%.1f %s", categoryStr, targetLabel, activity.getExpectedHoursPerDay(), hoursPerDay));
            } else {
                binding.tvManageDetails.setText(categoryStr);
            }

            int color = IconHelper.parseColorOrDefault(activity.getColorHex(), Color.parseColor("#386B40"));
            IconHelper.setIcon(binding.ivManageIcon, activity.getIconName(), color);

            int alphaColor = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color));
            IconHelper.setRoundedBackgroundColor(binding.manageIconBg, alphaColor, 12f, color, 0);

            binding.btnEditActivity.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditActivity(activity);
                }
            });

            binding.btnDeleteActivity.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteActivity(activity);
                }
            });
        }
    }
}
