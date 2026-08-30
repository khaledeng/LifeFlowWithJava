package com.example.ui.progress;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.model.ProgressDayData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProgressWeekAdapter extends RecyclerView.Adapter<ProgressWeekAdapter.WeekViewHolder> {

    private final Context context;
    private final List<ProgressDayData> weekDays = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

    public ProgressWeekAdapter(Context context) {
        this.context = context;
    }

    public void setDays(List<ProgressDayData> days) {
        this.weekDays.clear();
        if (days != null) {
            this.weekDays.addAll(days);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WeekViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_week_day, parent, false);
        return new WeekViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WeekViewHolder holder, int position) {
        ProgressDayData data = weekDays.get(position);

        String dateStr = dateFormat.format(new Date(data.startOfDayMillis));
        if (data.isToday) {
            dateStr = context.getString(R.string.today_title) + " (" + dateStr + ")";
        }
        holder.tvDayName.setText(dateStr);

        // Format tracked vs target
        long trackedSecs = data.trackedMillis / 1000;
        long trackedH = trackedSecs / 3600;
        long trackedM = (trackedSecs % 3600) / 60;
        holder.tvTrackedTime.setText(String.format(Locale.getDefault(), "%dh %02dm", trackedH, trackedM));

        long targetSecs = data.targetMillis / 1000;
        long targetH = targetSecs / 3600;
        long targetM = (targetSecs % 3600) / 60;
        holder.tvTargetTime.setText(String.format(Locale.getDefault(), "%dh %02dm", targetH, targetM));

        int progressInt = Math.min(100, Math.round(data.percentage));
        holder.progressBar.setProgress(progressInt);
        holder.tvPercentVal.setText(Math.round(data.percentage) + "%");

        if (data.isFuture) {
            holder.itemView.setAlpha(0.4f);
            holder.tvStatusBadge.setText(R.string.status_upcoming);
            holder.tvStatusBadge.setTextColor(Color.parseColor("#8E8E93"));
            holder.tvPercentVal.setTextColor(Color.parseColor("#8E8E93"));
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#44444F")));
        } else {
            holder.itemView.setAlpha(1.0f);
            if (data.status == ProgressDayData.Status.COMPLETED_100) {
                holder.tvStatusBadge.setText("✓ " + context.getString(R.string.status_achieved));
                holder.tvStatusBadge.setTextColor(Color.parseColor("#39D353"));
                holder.tvPercentVal.setTextColor(Color.parseColor("#39D353"));
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            } else if (data.status == ProgressDayData.Status.PARTIAL_GREEN) {
                holder.tvStatusBadge.setText(Math.round(data.percentage) + "%");
                holder.tvStatusBadge.setTextColor(Color.parseColor("#39D353"));
                holder.tvPercentVal.setTextColor(Color.parseColor("#39D353"));
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            } else if (data.status == ProgressDayData.Status.PARTIAL_ORANGE) {
                holder.tvStatusBadge.setText(Math.round(data.percentage) + "%");
                holder.tvStatusBadge.setTextColor(Color.parseColor("#FF8C42"));
                holder.tvPercentVal.setTextColor(Color.parseColor("#FF8C42"));
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF8C42")));
            } else {
                holder.tvStatusBadge.setText(R.string.status_not_started);
                holder.tvStatusBadge.setTextColor(Color.parseColor("#8E8E93"));
                holder.tvPercentVal.setTextColor(Color.parseColor("#8E8E93"));
                holder.progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#383842")));
            }
        }
    }

    @Override
    public int getItemCount() {
        return weekDays.size();
    }

    static class WeekViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayName;
        TextView tvStatusBadge;
        TextView tvTrackedTime;
        TextView tvTargetTime;
        TextView tvPercentVal;
        ProgressBar progressBar;

        WeekViewHolder(View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tv_week_day_name);
            tvStatusBadge = itemView.findViewById(R.id.tv_week_status_badge);
            tvTrackedTime = itemView.findViewById(R.id.tv_week_tracked_time);
            tvTargetTime = itemView.findViewById(R.id.tv_week_target_time);
            tvPercentVal = itemView.findViewById(R.id.tv_week_percent_val);
            progressBar = itemView.findViewById(R.id.pb_week_progress);
        }
    }
}
