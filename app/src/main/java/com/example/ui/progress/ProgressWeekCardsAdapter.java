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
import com.example.data.model.ProgressWeekCardData;

import java.util.ArrayList;
import java.util.List;

public class ProgressWeekCardsAdapter extends RecyclerView.Adapter<ProgressWeekCardsAdapter.WeekCardViewHolder> {

    private final Context context;
    private final List<ProgressWeekCardData> weekCards = new ArrayList<>();

    public ProgressWeekCardsAdapter(Context context) {
        this.context = context;
    }

    public void setWeeks(List<ProgressWeekCardData> weeks) {
        this.weekCards.clear();
        if (weeks != null) {
            this.weekCards.addAll(weeks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WeekCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_week_card, parent, false);
        return new WeekCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WeekCardViewHolder holder, int position) {
        ProgressWeekCardData cardData = weekCards.get(position);

        holder.tvTitle.setText(cardData.title);

        int pctInt = Math.round(cardData.weekPercentage);
        holder.tvPctBadge.setText(pctInt + "%");
        if (cardData.weekPercentage >= 50f) {
            holder.tvPctBadge.setBackgroundResource(R.drawable.bg_active_pill);
            holder.tvPctBadge.setTextColor(Color.parseColor("#39D353"));
        } else {
            holder.tvPctBadge.setBackgroundResource(R.drawable.bg_pill_orange);
            holder.tvPctBadge.setTextColor(Color.parseColor("#FF8C42"));
        }

        // Bind 7 day columns
        for (int i = 0; i < 7; i++) {
            if (i < cardData.days.size()) {
                ProgressDayData day = cardData.days.get(i);
                holder.tvDayLetters[i].setText(day.dayLetter != null ? day.dayLetter : "");

                if (day.isFuture) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_dotted);
                    holder.tvDayVals[i].setText("…");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#8E8E93"));
                } else if (day.status == ProgressDayData.Status.PAUSED || day.isPaused) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_orange);
                    holder.tvDayVals[i].setText("⏸");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#FFD60A"));
                } else if (day.status == ProgressDayData.Status.COMPLETED_100) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_green);
                    holder.tvDayVals[i].setText("✓");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#39D353"));
                } else if (day.status == ProgressDayData.Status.EXCEEDED_LIMIT_100) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_red);
                    holder.tvDayVals[i].setText("✕");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#FF4D4D"));
                } else if (day.status == ProgressDayData.Status.PARTIAL_RED) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_red);
                    holder.tvDayVals[i].setText(Math.round(day.percentage) + "%");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#FF4D4D"));
                } else if (day.status == ProgressDayData.Status.PARTIAL_ORANGE) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_orange);
                    holder.tvDayVals[i].setText(Math.round(day.percentage) + "%");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#FF8C42"));
                } else if (day.status == ProgressDayData.Status.PARTIAL_GREEN) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_green);
                    holder.tvDayVals[i].setText(Math.round(day.percentage) + "%");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#39D353"));
                } else if (day.status == ProgressDayData.Status.ZERO && day.category == com.example.data.entity.ActivityCategory.DECREASE) {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_green);
                    holder.tvDayVals[i].setText("0%");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#39D353"));
                } else {
                    holder.flDayRings[i].setBackgroundResource(R.drawable.bg_circle_ring_neutral);
                    holder.tvDayVals[i].setText("0%");
                    holder.tvDayVals[i].setTextColor(Color.parseColor("#8E8E93"));
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return weekCards.size();
    }

    static class WeekCardViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvPctBadge;
        TextView[] tvDayLetters = new TextView[7];
        FrameLayout[] flDayRings = new FrameLayout[7];
        TextView[] tvDayVals = new TextView[7];

        WeekCardViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_week_card_title);
            tvPctBadge = itemView.findViewById(R.id.tv_week_card_pct);

            tvDayLetters[0] = itemView.findViewById(R.id.tv_day_letter_0);
            tvDayLetters[1] = itemView.findViewById(R.id.tv_day_letter_1);
            tvDayLetters[2] = itemView.findViewById(R.id.tv_day_letter_2);
            tvDayLetters[3] = itemView.findViewById(R.id.tv_day_letter_3);
            tvDayLetters[4] = itemView.findViewById(R.id.tv_day_letter_4);
            tvDayLetters[5] = itemView.findViewById(R.id.tv_day_letter_5);
            tvDayLetters[6] = itemView.findViewById(R.id.tv_day_letter_6);

            flDayRings[0] = itemView.findViewById(R.id.fl_day_ring_0);
            flDayRings[1] = itemView.findViewById(R.id.fl_day_ring_1);
            flDayRings[2] = itemView.findViewById(R.id.fl_day_ring_2);
            flDayRings[3] = itemView.findViewById(R.id.fl_day_ring_3);
            flDayRings[4] = itemView.findViewById(R.id.fl_day_ring_4);
            flDayRings[5] = itemView.findViewById(R.id.fl_day_ring_5);
            flDayRings[6] = itemView.findViewById(R.id.fl_day_ring_6);

            tvDayVals[0] = itemView.findViewById(R.id.tv_day_val_0);
            tvDayVals[1] = itemView.findViewById(R.id.tv_day_val_1);
            tvDayVals[2] = itemView.findViewById(R.id.tv_day_val_2);
            tvDayVals[3] = itemView.findViewById(R.id.tv_day_val_3);
            tvDayVals[4] = itemView.findViewById(R.id.tv_day_val_4);
            tvDayVals[5] = itemView.findViewById(R.id.tv_day_val_5);
            tvDayVals[6] = itemView.findViewById(R.id.tv_day_val_6);
        }
    }
}
