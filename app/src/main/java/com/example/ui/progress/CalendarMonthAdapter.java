package com.example.ui.progress;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.model.ProgressDayData;
import com.example.util.HapticHelper;

import java.util.ArrayList;
import java.util.List;

public class CalendarMonthAdapter extends RecyclerView.Adapter<CalendarMonthAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(ProgressDayData dayData);
    }

    private static final int COLOR_SELECTED_BG = Color.parseColor("#21382A");
    private static final int COLOR_SELECTED_STROKE = Color.parseColor("#39D353");
    private static final int COLOR_TODAY_BG = Color.parseColor("#1C241E");
    private static final int COLOR_TODAY_STROKE = Color.parseColor("#39D353");
    private static final int COLOR_NORMAL_BG = Color.parseColor("#18181D");
    private static final int COLOR_NORMAL_STROKE = Color.parseColor("#23232A");

    private static final int COLOR_RED = Color.parseColor("#FF4D4D");
    private static final int COLOR_ORANGE = Color.parseColor("#FF8C42");
    private static final int COLOR_GREEN = Color.parseColor("#39D353");
    private static final int COLOR_MUTED_TEXT = Color.parseColor("#60606A");

    private final Context context;
    private final List<ProgressDayData> days = new ArrayList<>();
    private int firstDayOffset = 0;
    private int selectedDayOfMonth = -1;
    private final OnDayClickListener listener;

    public CalendarMonthAdapter(Context context, OnDayClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<ProgressDayData> dayList, int offset, int selectedDay) {
        this.days.clear();
        if (dayList != null) {
            this.days.addAll(dayList);
        }
        this.firstDayOffset = Math.max(0, offset);
        this.selectedDayOfMonth = selectedDay;
        notifyDataSetChanged();
    }

    public void setSelectedDay(int dayOfMonth) {
        if (this.selectedDayOfMonth == dayOfMonth) return;
        int prevSelected = this.selectedDayOfMonth;
        this.selectedDayOfMonth = dayOfMonth;

        notifyAffectedDays(prevSelected, dayOfMonth);
    }

    private void notifyAffectedDays(int dayA, int dayB) {
        for (int i = 0; i < getItemCount(); i++) {
            int dayIndex = i - firstDayOffset;
            if (dayIndex >= 0 && dayIndex < days.size()) {
                ProgressDayData d = days.get(dayIndex);
                if (d.dayOfMonth == dayA || d.dayOfMonth == dayB) {
                    notifyItemChanged(i);
                }
            }
        }
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        if (position < firstDayOffset) {
            holder.itemView.setVisibility(View.INVISIBLE);
            holder.itemView.setEnabled(false);
            holder.itemView.setOnClickListener(null);
            return;
        }

        int dayIndex = position - firstDayOffset;
        if (dayIndex >= days.size()) {
            holder.itemView.setVisibility(View.INVISIBLE);
            holder.itemView.setEnabled(false);
            holder.itemView.setOnClickListener(null);
            return;
        }

        holder.itemView.setVisibility(View.VISIBLE);
        holder.itemView.setEnabled(true);
        ProgressDayData data = days.get(dayIndex);

        holder.tvDayNumber.setText(String.valueOf(data.dayOfMonth));

        boolean isSelected = (data.dayOfMonth == selectedDayOfMonth);

        // Cell Background styling
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(14));

        if (isSelected) {
            bg.setColor(COLOR_SELECTED_BG);
            bg.setStroke(dpToPx(2.5f), COLOR_SELECTED_STROKE);
        } else if (data.isToday) {
            bg.setColor(COLOR_TODAY_BG);
            bg.setStroke(dpToPx(1.5f), COLOR_TODAY_STROKE);
        } else {
            bg.setColor(COLOR_NORMAL_BG);
            bg.setStroke(dpToPx(1), COLOR_NORMAL_STROKE);
        }
        holder.cellContainer.setBackground(bg);

        // Future day handling
        if (data.isFuture) {
            holder.cellContainer.setAlpha(0.35f);
            holder.tvDayNumber.setTextColor(COLOR_MUTED_TEXT);
            holder.ivCheckBadge.setVisibility(View.GONE);
            holder.ivCrossBadge.setVisibility(View.GONE);
            holder.tvPercentage.setVisibility(View.GONE);
            holder.tvNeutralDash.setVisibility(View.GONE);
        } else {
            holder.cellContainer.setAlpha(1.0f);
            holder.tvDayNumber.setTextColor(Color.WHITE);

            holder.ivCheckBadge.setVisibility(View.GONE);
            holder.ivCrossBadge.setVisibility(View.GONE);
            holder.tvPercentage.setVisibility(View.GONE);
            holder.tvNeutralDash.setVisibility(View.GONE);

            // Progress status display
            switch (data.status) {
                case PAUSED:
                    holder.tvPercentage.setVisibility(View.VISIBLE);
                    holder.tvPercentage.setText("⏸");
                    holder.tvPercentage.setTextColor(COLOR_ORANGE);
                    break;

                case COMPLETED_100:
                    holder.ivCheckBadge.setVisibility(View.VISIBLE);
                    break;

                case EXCEEDED_LIMIT_100:
                    holder.ivCrossBadge.setVisibility(View.VISIBLE);
                    break;

                case PARTIAL_RED:
                    holder.tvPercentage.setVisibility(View.VISIBLE);
                    holder.tvPercentage.setText(Math.round(data.percentage) + "%");
                    holder.tvPercentage.setTextColor(COLOR_RED);
                    break;

                case PARTIAL_ORANGE:
                    holder.tvPercentage.setVisibility(View.VISIBLE);
                    holder.tvPercentage.setText(Math.round(data.percentage) + "%");
                    holder.tvPercentage.setTextColor(COLOR_ORANGE);
                    break;

                case PARTIAL_GREEN:
                    holder.tvPercentage.setVisibility(View.VISIBLE);
                    holder.tvPercentage.setText(Math.round(data.percentage) + "%");
                    holder.tvPercentage.setTextColor(COLOR_GREEN);
                    break;

                case ZERO:
                default:
                    if (data.category == com.example.data.entity.ActivityCategory.DECREASE) {
                        holder.tvPercentage.setVisibility(View.VISIBLE);
                        holder.tvPercentage.setText("0%");
                        holder.tvPercentage.setTextColor(COLOR_GREEN);
                    } else {
                        holder.tvNeutralDash.setVisibility(View.VISIBLE);
                    }
                    break;
            }
        }

        View.OnClickListener clickListener = v -> {
            HapticHelper.performClick(v);
            int prevSelected = selectedDayOfMonth;
            selectedDayOfMonth = data.dayOfMonth;
            notifyAffectedDays(prevSelected, data.dayOfMonth);

            if (listener != null) {
                listener.onDayClick(data);
            }
        };

        holder.itemView.setOnClickListener(clickListener);
        if (holder.cellContainer != null) {
            holder.cellContainer.setOnClickListener(clickListener);
        }
    }

    @Override
    public int getItemCount() {
        return firstDayOffset + days.size();
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        LinearLayout cellContainer;
        TextView tvDayNumber;
        ImageView ivCheckBadge;
        ImageView ivCrossBadge;
        TextView tvPercentage;
        TextView tvNeutralDash;

        DayViewHolder(View itemView) {
            super(itemView);
            cellContainer = itemView.findViewById(R.id.cell_container);
            tvDayNumber = itemView.findViewById(R.id.tv_day_number);
            ivCheckBadge = itemView.findViewById(R.id.iv_check_badge);
            ivCrossBadge = itemView.findViewById(R.id.iv_cross_badge);
            tvPercentage = itemView.findViewById(R.id.tv_percentage_text);
            tvNeutralDash = itemView.findViewById(R.id.tv_neutral_dash);
        }
    }
}
