package com.example.ui.statistics;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.TrackingRepository;
import com.example.databinding.ItemStatRowBinding;
import com.example.util.IconHelper;

import java.util.ArrayList;
import java.util.List;

public class StatsBreakdownAdapter extends RecyclerView.Adapter<StatsBreakdownAdapter.ViewHolder> {

    public interface OnItemEditClickListener {
        void onEditClick(TrackingRepository.ActivityStat stat);
    }

    private List<TrackingRepository.ActivityStat> stats = new ArrayList<>();
    private long activeActivityId = -1;
    private long activeLiveDurationMillis = 0;
    private OnItemEditClickListener editClickListener;

    public void setStats(List<TrackingRepository.ActivityStat> newStats, long activeId) {
        this.activeActivityId = activeId;
        this.stats = new ArrayList<>();
        if (newStats != null) {
            // Prioritize the active activity to be the FIRST item in the activity breakdown list
            TrackingRepository.ActivityStat activeItem = null;
            List<TrackingRepository.ActivityStat> otherItems = new ArrayList<>();
            for (TrackingRepository.ActivityStat s : newStats) {
                if (activeId != -1 && s.activityId == activeId) {
                    activeItem = s;
                } else {
                    otherItems.add(s);
                }
            }
            if (activeItem != null) {
                this.stats.add(activeItem);
            }
            this.stats.addAll(otherItems);
        }
        notifyDataSetChanged();
    }

    public void updateActiveDuration(long activeId, long liveDurationMillis) {
        this.activeActivityId = activeId;
        this.activeLiveDurationMillis = liveDurationMillis;
        if (!stats.isEmpty() && stats.get(0).activityId == activeId) {
            stats.get(0).durationMillis = liveDurationMillis;
            notifyItemChanged(0, "TIMER_UPDATE");
        } else {
            for (int i = 0; i < stats.size(); i++) {
                if (stats.get(i).activityId == activeId) {
                    stats.get(i).durationMillis = liveDurationMillis;
                    notifyItemChanged(i, "TIMER_UPDATE");
                    break;
                }
            }
        }
    }

    public void setOnItemEditClickListener(OnItemEditClickListener listener) {
        this.editClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStatRowBinding binding = ItemStatRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("TIMER_UPDATE")) {
            TrackingRepository.ActivityStat stat = stats.get(position);
            if (stat.activityId == activeActivityId) {
                holder.updateLiveDuration(activeLiveDurationMillis > 0 ? activeLiveDurationMillis : stat.durationMillis);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackingRepository.ActivityStat stat = stats.get(position);
        boolean isActive = (activeActivityId != -1 && stat.activityId == activeActivityId);
        long displayDuration = (isActive && activeLiveDurationMillis > 0) ? activeLiveDurationMillis : stat.durationMillis;
        holder.bind(stat, isActive, displayDuration, editClickListener);
    }

    @Override
    public int getItemCount() {
        return stats.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStatRowBinding binding;

        ViewHolder(ItemStatRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void updateLiveDuration(long liveDurationMillis) {
            binding.tvStatDuration.setText(IconHelper.formatDurationWithSeconds(liveDurationMillis));
        }

        void bind(TrackingRepository.ActivityStat stat, boolean isActive, long displayDuration, OnItemEditClickListener listener) {
            binding.tvStatName.setText(stat.name);
            binding.tvStatDuration.setText(IconHelper.formatDurationWithSeconds(displayDuration));

            int color = IconHelper.parseColorOrDefault(stat.colorHex, Color.parseColor("#39D353"));
            binding.tvStatPercentage.setTextColor(color);
            if (stat.percentage > 0f) {
                binding.tvStatPercentage.setText(String.format(java.util.Locale.US, "%.1f%%", stat.percentage));
            } else {
                binding.tvStatPercentage.setText("0.0%");
            }
            binding.tvStatPercentage.setVisibility(View.VISIBLE);

            IconHelper.setIcon(binding.ivStatIcon, stat.iconName, color);

            int iconBgColor = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color));
            IconHelper.setRoundedBackgroundColor(binding.statIconBg, iconBgColor, 10f, color, 0);

            float density = binding.getRoot().getResources().getDisplayMetrics().density;
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(14f * density);

            if (isActive) {
                // Subtle glowing border with subtle translucent tint of the active activity's color
                int bgTint = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));
                cardBg.setColor(bgTint);
                cardBg.setStroke((int) (1.8f * density), color);
                binding.getRoot().setBackground(cardBg);

                binding.tvStatDuration.setTextColor(color);
                binding.tvStatDuration.setTypeface(null, android.graphics.Typeface.BOLD);
                if (binding.viewActivePulse != null) {
                    binding.viewActivePulse.setVisibility(View.VISIBLE);
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(color);
                    binding.viewActivePulse.setBackground(dot);
                }
            } else {
                cardBg.setColor(Color.parseColor("#1B1B1E"));
                cardBg.setStroke(0, Color.TRANSPARENT);
                binding.getRoot().setBackground(cardBg);

                binding.tvStatDuration.setTextColor(Color.parseColor("#C0C0C5"));
                binding.tvStatDuration.setTypeface(null, android.graphics.Typeface.NORMAL);
                if (binding.viewActivePulse != null) {
                    binding.viewActivePulse.setVisibility(View.GONE);
                }
            }

            View.OnClickListener clickListener = v -> {
                if (listener != null) {
                    listener.onEditClick(stat);
                }
            };
            binding.getRoot().setOnClickListener(clickListener);
        }
    }
}
