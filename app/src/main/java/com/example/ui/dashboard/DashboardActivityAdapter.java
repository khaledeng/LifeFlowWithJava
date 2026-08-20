package com.example.ui.dashboard;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.entity.Activity;
import com.example.databinding.ItemDashboardActivityBinding;
import com.example.util.IconHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardActivityAdapter extends RecyclerView.Adapter<DashboardActivityAdapter.ViewHolder> {

    public interface OnActivityActionListener {
        void onStartClicked(Activity activity);
        void onStopClicked(Activity activity);
    }

    private List<Activity> activities = new ArrayList<>();
    private long activeActivityId = -1;
    private long activeTotalTodayMillis = 0;
    private Map<Long, Long> todayDurations = new HashMap<>();
    private final OnActivityActionListener listener;

    public DashboardActivityAdapter(OnActivityActionListener listener) {
        this.listener = listener;
    }

    public void setActivities(List<Activity> activities, long activeActivityId, Map<Long, Long> todayDurations) {
        this.activities = activities != null ? new ArrayList<>(activities) : new ArrayList<>();
        this.activeActivityId = activeActivityId;
        if (todayDurations != null) {
            this.todayDurations = todayDurations;
        }
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

    public void setActiveActivityId(long activeActivityId) {
        if (this.activeActivityId == activeActivityId) return;
        long oldId = this.activeActivityId;
        this.activeActivityId = activeActivityId;
        for (int i = 0; i < activities.size(); i++) {
            long id = activities.get(i).getId();
            if (id == oldId || id == activeActivityId) {
                notifyItemChanged(i);
            }
        }
    }

    public void setTodayDurations(Map<Long, Long> todayDurations) {
        if (todayDurations == null) return;
        this.todayDurations = todayDurations;
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).getId() != activeActivityId) {
                notifyItemChanged(i);
            }
        }
    }

    public void updateActiveTotalToday(long activeActivityId, long totalTodayMillis) {
        this.activeActivityId = activeActivityId;
        this.activeTotalTodayMillis = totalTodayMillis;
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).getId() == activeActivityId) {
                notifyItemChanged(i, "TIMER_UPDATE");
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDashboardActivityBinding binding = ItemDashboardActivityBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("TIMER_UPDATE")) {
            Activity activity = activities.get(position);
            if (activity.getId() == activeActivityId) {
                holder.updateLiveTimer(activeTotalTodayMillis);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Activity activity = activities.get(position);
        boolean isActive = (activity.getId() == activeActivityId);
        long accumulatedToday = todayDurations.containsKey(activity.getId()) ? todayDurations.get(activity.getId()) : 0L;
        long displayMillis = isActive ? activeTotalTodayMillis : accumulatedToday;
        holder.bind(activity, isActive, displayMillis, listener);
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDashboardActivityBinding binding;
        private Activity currentActivity;

        ViewHolder(ItemDashboardActivityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void updateLiveTimer(long totalMillis) {
            binding.tvActivityTimer.setText(IconHelper.formatTimer(totalMillis));
            if (currentActivity != null) {
                int activityColor = IconHelper.parseColorOrDefault(currentActivity.getColorHex(), Color.parseColor("#39D353"));
                updateTargetProgress(currentActivity, totalMillis, activityColor);
            }
        }

        private void updateTargetProgress(Activity activity, long displayMillis, int activityColor) {
            if (activity == null) return;
            float targetHours = activity.getExpectedHoursPerDay();

            if (targetHours <= 0) {
                if (binding.tvTargetInfo.getVisibility() != View.GONE) {
                    binding.tvTargetInfo.setVisibility(View.GONE);
                }
                if (binding.progressBarTarget.getVisibility() != View.GONE) {
                    binding.progressBarTarget.setVisibility(View.GONE);
                }
                if (binding.layoutPercentagePill.getVisibility() != View.GONE) {
                    binding.layoutPercentagePill.setVisibility(View.GONE);
                }
                return;
            }

            if (binding.tvTargetInfo.getVisibility() != View.VISIBLE) {
                binding.tvTargetInfo.setVisibility(View.VISIBLE);
            }
            if (binding.progressBarTarget.getVisibility() != View.VISIBLE) {
                binding.progressBarTarget.setVisibility(View.VISIBLE);
            }
            if (binding.layoutPercentagePill.getVisibility() != View.VISIBLE) {
                binding.layoutPercentagePill.setVisibility(View.VISIBLE);
            }

            long targetMillis = (long) (targetHours * 3600.0 * 1000.0);
            int pct = (int) Math.min(100, Math.max(0, (displayMillis * 100) / targetMillis));

            // Allow percentage over 100% for display
            int displayPct = (int) ((displayMillis * 100) / targetMillis);

            if (binding.progressBarTarget.getProgress() != pct) {
                binding.progressBarTarget.setProgressCompat(pct, false);
            }

            com.example.data.entity.ActivityCategory category = activity.getGoalType();
            int progressColor = activityColor;
            int pillColor = Color.parseColor("#333333");
            int textColor = Color.parseColor("#E0E0E0");

            if (category == com.example.data.entity.ActivityCategory.INCREASE) {
                binding.ivPercentageArrow.setVisibility(View.VISIBLE);
                binding.ivPercentageArrow.setImageResource(com.example.R.drawable.ic_arrow_up);
                int arrowColor = Color.parseColor("#39D353");
                binding.ivPercentageArrow.setColorFilter(arrowColor);

                progressColor = Color.parseColor("#39D353");
                if (displayPct >= 100) {
                    pillColor = Color.parseColor("#1C3A24"); // Dark green badge
                    textColor = Color.parseColor("#39D353");
                } else {
                    pillColor = Color.parseColor("#1B2E20");
                    textColor = Color.parseColor("#E0E0E0");
                }
            } else if (category == com.example.data.entity.ActivityCategory.DECREASE) {
                binding.ivPercentageArrow.setVisibility(View.VISIBLE);
                binding.ivPercentageArrow.setImageResource(com.example.R.drawable.ic_arrow_down);

                if (displayPct >= 100) {
                    int arrowColor = Color.parseColor("#FF5252");
                    binding.ivPercentageArrow.setColorFilter(arrowColor);
                    progressColor = Color.parseColor("#FF5252"); // Red warning
                    pillColor = Color.parseColor("#5A2424"); // Dark red
                    textColor = Color.parseColor("#FF5252");
                } else if (displayPct >= 75) {
                    int arrowColor = Color.parseColor("#FF9800");
                    binding.ivPercentageArrow.setColorFilter(arrowColor);
                    progressColor = Color.parseColor("#FF9800"); // Orange caution
                    pillColor = Color.parseColor("#4A361A"); // Dark orange
                    textColor = Color.parseColor("#FFB74D");
                } else {
                    int arrowColor = Color.parseColor("#FF8C42");
                    binding.ivPercentageArrow.setColorFilter(arrowColor);
                    progressColor = Color.parseColor("#4CAF50"); // Safe green progress
                    pillColor = Color.parseColor("#2D231E");
                    textColor = Color.parseColor("#E0E0E0");
                }
            } else {
                binding.ivPercentageArrow.setVisibility(View.GONE);
                progressColor = activityColor;
                pillColor = Color.parseColor("#2A2A2A");
                textColor = Color.parseColor("#AAAAAA");
            }

            // Set Pill properties
            binding.tvPercentagePill.setText(String.format(java.util.Locale.US, "\u200E%d%%", displayPct));
            binding.tvPercentagePill.setTextColor(textColor);

            android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
            pillBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(50f);
            pillBg.setColor(pillColor);
            binding.layoutPercentagePill.setBackground(pillBg);

            if (binding.progressBarTarget.getIndicatorColor() == null ||
                    binding.progressBarTarget.getIndicatorColor().length == 0 ||
                    binding.progressBarTarget.getIndicatorColor()[0] != progressColor) {
                binding.progressBarTarget.setIndicatorColor(progressColor);
            }

            String targetText = "";
            android.content.Context ctx = binding.getRoot().getContext();
            String formattedTarget = IconHelper.formatDuration(targetMillis);

            if (category == com.example.data.entity.ActivityCategory.INCREASE) {
                if (displayMillis >= targetMillis) {
                    targetText = ctx.getString(com.example.R.string.target_achieved_increase, formattedTarget);
                } else {
                    long remainingMillis = targetMillis - displayMillis;
                    targetText = ctx.getString(com.example.R.string.target_remaining, IconHelper.formatDuration(remainingMillis), formattedTarget);
                }
            } else if (category == com.example.data.entity.ActivityCategory.DECREASE) {
                if (displayMillis >= targetMillis) {
                    long exceededMillis = displayMillis - targetMillis;
                    targetText = ctx.getString(com.example.R.string.target_exceeded_decrease, IconHelper.formatDuration(exceededMillis));
                } else {
                    long remainingMillis = targetMillis - displayMillis;
                    targetText = ctx.getString(com.example.R.string.target_remaining, IconHelper.formatDuration(remainingMillis), formattedTarget);
                }
            } else {
                long remainingMillis = Math.max(0, targetMillis - displayMillis);
                targetText = ctx.getString(com.example.R.string.target_remaining, IconHelper.formatDuration(remainingMillis), formattedTarget);
            }

            if (!targetText.equals(binding.tvTargetInfo.getText().toString())) {
                binding.tvTargetInfo.setText(targetText);
            }
        }

        void bind(Activity activity, boolean isActive, long displayMillis, OnActivityActionListener listener) {
            this.currentActivity = activity;
            String displayName = activity.getName();
            binding.tvActivityName.setText(displayName);

            int activityColor = IconHelper.parseColorOrDefault(activity.getColorHex(), Color.parseColor("#39D353"));
            IconHelper.setIcon(binding.ivActivityIcon, activity.getIconName(), activityColor);

            updateTargetProgress(activity, displayMillis, activityColor);

            float density = binding.getRoot().getContext().getResources().getDisplayMetrics().density;
            int iconBgColor = Color.argb(isActive ? 65 : 32, Color.red(activityColor), Color.green(activityColor), Color.blue(activityColor));
            IconHelper.setRoundedBackgroundColor(binding.activityIconBg, iconBgColor, 14f, activityColor, isActive ? 1 : 0);

            if (isActive) {
                // Active Card: Glow border with Activity's OWN color, deep tinted surface
                binding.cardActivityItem.setStrokeColor(ColorStateList.valueOf(activityColor));
                binding.cardActivityItem.setStrokeWidth((int) (2 * density));

                int r = (int) (Color.red(activityColor) * 0.16f + 0x12 * 0.84f);
                int g = (int) (Color.green(activityColor) * 0.16f + 0x12 * 0.84f);
                int b = (int) (Color.blue(activityColor) * 0.16f + 0x15 * 0.84f);
                binding.cardActivityItem.setCardBackgroundColor(Color.rgb(r, g, b));

                // Timer text: Activity's own color
                binding.tvActivityTimer.setTextColor(activityColor);
                binding.tvActivityTimer.setText(IconHelper.formatTimer(displayMillis));

                // Button: Solid Stop Button matching Activity's color
                binding.btnStop.setVisibility(View.VISIBLE);
                binding.btnStart.setVisibility(View.GONE);

                GradientDrawable stopDrawable = new GradientDrawable();
                stopDrawable.setShape(GradientDrawable.RECTANGLE);
                stopDrawable.setCornerRadius(50 * density);
                stopDrawable.setColor(activityColor);
                binding.btnStop.setBackground(stopDrawable);

                double luminance = (0.299 * Color.red(activityColor) + 0.587 * Color.green(activityColor) + 0.114 * Color.blue(activityColor)) / 255.0;
                int contentColor = luminance > 0.45 ? Color.BLACK : Color.WHITE;
                binding.tvStopLabel.setTextColor(contentColor);

                binding.btnStop.setOnClickListener(v -> {
                    if (listener != null) listener.onStopClicked(activity);
                });
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onStopClicked(activity);
                });
            } else {
                // Inactive Card: Clean dark background, no border
                binding.cardActivityItem.setStrokeWidth(0);
                binding.cardActivityItem.setCardBackgroundColor(Color.parseColor("#1B1B1E"));

                // Timer text: Dark muted monospace grey showing today's accumulated duration
                binding.tvActivityTimer.setTextColor(Color.parseColor("#FFFFFF"));
                binding.tvActivityTimer.setText(IconHelper.formatTimer(displayMillis));

                // Button: Outlined Start Button with Activity's Color
                binding.btnStop.setVisibility(View.GONE);
                binding.btnStart.setVisibility(View.VISIBLE);

                GradientDrawable startDrawable = new GradientDrawable();
                startDrawable.setShape(GradientDrawable.RECTANGLE);
                startDrawable.setCornerRadius(50 * density);
                startDrawable.setStroke((int) (1f * density), Color.parseColor("#4E4E56"));
                startDrawable.setColor(Color.TRANSPARENT);
                binding.btnStart.setBackground(startDrawable);

                binding.tvStartLabel.setTextColor(Color.parseColor("#AAAAAA"));

                binding.btnStart.setOnClickListener(v -> {
                    if (listener != null) listener.onStartClicked(activity);
                });
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onStartClicked(activity);
                });
            }
        }
    }
}
