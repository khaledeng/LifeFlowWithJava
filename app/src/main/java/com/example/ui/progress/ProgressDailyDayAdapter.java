package com.example.ui.progress;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.model.ProgressDayData;
import com.example.util.HapticHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProgressDailyDayAdapter extends RecyclerView.Adapter<ProgressDailyDayAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(ProgressDayData day);
    }

    private final List<ProgressDayData> days = new ArrayList<>();
    private OnDayClickListener listener;
    private int selectedPosition = 0; // default to today (first item)

    public void setDays(List<ProgressDayData> newDays) {
        this.days.clear();
        if (newDays != null) {
            this.days.addAll(newDays);
        }
        notifyDataSetChanged();
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void setSelectedPosition(int pos) {
        int oldPos = this.selectedPosition;
        this.selectedPosition = pos;
        if (oldPos >= 0 && oldPos < days.size()) notifyItemChanged(oldPos);
        if (pos >= 0 && pos < days.size()) notifyItemChanged(pos);
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_daily_card, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        ProgressDayData data = days.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvDayName.setText(data.dayNameShort != null ? data.dayNameShort : "");
        holder.tvDayDate.setText(data.dateLabel != null ? data.dateLabel : (data.dayOfMonth + ""));

        // Format tracked vs target
        String trackedFormatted = formatDuration(data.trackedMillis);
        String targetFormatted = formatDuration(data.targetMillis);
        holder.tvTrackedVsTarget.setText(trackedFormatted + " / " + targetFormatted);

        // Status Indicator Ring & Text
        if (data.status == ProgressDayData.Status.PAUSED || data.isPaused) {
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_orange);
            holder.tvCircleText.setText("⏸");
            holder.tvCircleText.setTextColor(Color.parseColor("#FFD60A"));
            holder.tvCircleText.setTextSize(20f);
        } else if (data.status == ProgressDayData.Status.COMPLETED_100) {
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_green);
            holder.tvCircleText.setText("✓");
            holder.tvCircleText.setTextColor(Color.parseColor("#39D353"));
            holder.tvCircleText.setTextSize(22f);
        } else if (data.status == ProgressDayData.Status.EXCEEDED_LIMIT_100) {
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_red);
            holder.tvCircleText.setText("✕");
            holder.tvCircleText.setTextColor(Color.parseColor("#FF4D4D"));
            holder.tvCircleText.setTextSize(22f);
        } else if (data.status == ProgressDayData.Status.PARTIAL_RED) {
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_red);
            holder.tvCircleText.setText(Math.round(data.percentage) + "%");
            holder.tvCircleText.setTextColor(Color.parseColor("#FF4D4D"));
            holder.tvCircleText.setTextSize(17f);
        } else if (data.status == ProgressDayData.Status.PARTIAL_ORANGE) {
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_orange);
            holder.tvCircleText.setText(Math.round(data.percentage) + "%");
            holder.tvCircleText.setTextColor(Color.parseColor("#FF8C42"));
            holder.tvCircleText.setTextSize(17f);
        } else if (data.status == ProgressDayData.Status.PARTIAL_GREEN) {
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_green);
            holder.tvCircleText.setText(Math.round(data.percentage) + "%");
            holder.tvCircleText.setTextColor(Color.parseColor("#39D353"));
            holder.tvCircleText.setTextSize(17f);
        } else if (data.status == ProgressDayData.Status.ZERO && data.category == com.example.data.entity.ActivityCategory.DECREASE && !data.isFuture) {
            // Decrease 0% is green success
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_green);
            holder.tvCircleText.setText("0%");
            holder.tvCircleText.setTextColor(Color.parseColor("#39D353"));
            holder.tvCircleText.setTextSize(16f);
        } else {
            // ZERO (Increase) or FUTURE
            holder.layoutCircleRing.setBackgroundResource(R.drawable.bg_circle_ring_neutral);
            if (data.isFuture) {
                holder.tvCircleText.setText("-");
            } else {
                holder.tvCircleText.setText("0%");
            }
            holder.tvCircleText.setTextColor(Color.parseColor("#8E8E93"));
            holder.tvCircleText.setTextSize(16f);
        }

        // Highlight selected or today
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(R.drawable.bg_calendar_day_selected);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_rounded_card);
        }

        holder.itemView.setOnClickListener(v -> {
            HapticHelper.performClick(v);
            int prev = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            if (prev != RecyclerView.NO_POSITION) {
                notifyItemChanged(prev);
            }
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(selectedPosition);
            }
            if (listener != null) {
                listener.onDayClick(data);
            }
        });
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    private String formatDuration(long millis) {
        long totalSecs = millis / 1000;
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + mins + "m";
        } else {
            return mins + "m";
        }
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayName, tvDayDate, tvCircleText, tvTrackedVsTarget;
        FrameLayout layoutCircleRing;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tv_day_name);
            tvDayDate = itemView.findViewById(R.id.tv_day_date);
            tvCircleText = itemView.findViewById(R.id.tv_circle_text);
            tvTrackedVsTarget = itemView.findViewById(R.id.tv_tracked_vs_target);
            layoutCircleRing = itemView.findViewById(R.id.layout_circle_ring);
        }
    }
}
