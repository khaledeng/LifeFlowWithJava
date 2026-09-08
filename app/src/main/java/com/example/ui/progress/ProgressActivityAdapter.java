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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.entity.Activity;
import com.example.util.HapticHelper;
import com.example.util.IconHelper;

import java.util.ArrayList;
import java.util.List;

public class ProgressActivityAdapter extends RecyclerView.Adapter<ProgressActivityAdapter.ViewHolder> {

    public interface OnActivitySelectedListener {
        void onActivitySelected(Activity activity);
    }

    private final Context context;
    private final List<Activity> activities = new ArrayList<>();
    private long selectedActivityId = -1;
    private final OnActivitySelectedListener listener;

    public ProgressActivityAdapter(Context context, OnActivitySelectedListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setActivities(List<Activity> list, long selectedId) {
        this.activities.clear();
        if (list != null) {
            this.activities.addAll(list);
        }
        this.selectedActivityId = selectedId;
        if (this.selectedActivityId == -1 && !this.activities.isEmpty()) {
            this.selectedActivityId = this.activities.get(0).getId();
        }
        notifyDataSetChanged();
    }

    public void setSelectedActivityId(long id) {
        this.selectedActivityId = id;
        notifyDataSetChanged();
    }

    public Activity getSelectedActivity() {
        for (Activity a : activities) {
            if (a.getId() == selectedActivityId) {
                return a;
            }
        }
        return activities.isEmpty() ? null : activities.get(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_activity_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Activity act = activities.get(position);
        boolean isSelected = (act.getId() == selectedActivityId);

        if (act.isOnce()) {
            holder.tvName.setText("⚡ " + act.getNameWithArrow());
        } else {
            holder.tvName.setText(act.getNameWithArrow());
        }

        int color = IconHelper.parseColorOrDefault(act.getColorHex(), 0xFF39D353);

        // Icon or emoji
        String iconName = act.getIconName();
        if (IconHelper.isEmojiIcon(iconName)) {
            holder.ivIcon.setVisibility(View.GONE);
            holder.tvEmoji.setVisibility(View.VISIBLE);
            holder.tvEmoji.setText(IconHelper.extractEmoji(iconName));
        } else {
            holder.tvEmoji.setVisibility(View.GONE);
            holder.ivIcon.setVisibility(View.VISIBLE);
            IconHelper.setIcon(holder.ivIcon, iconName, color);
        }

        // Daily Target text
        float targetHours = act.getExpectedHoursPerDay();
        if (targetHours > 0) {
            if (targetHours == (int) targetHours) {
                holder.tvTarget.setText(((int) targetHours) + "h");
            } else {
                holder.tvTarget.setText(String.format(java.util.Locale.US, "%.1fh", targetHours));
            }
            holder.tvTarget.setVisibility(View.VISIBLE);
        } else {
            holder.tvTarget.setVisibility(View.GONE);
        }

        // Background styling
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(20));

        if (isSelected) {
            bg.setColor(Color.parseColor("#262632"));
            bg.setStroke(dpToPx(2), Color.parseColor("#60CDFF"));
            holder.tvName.setTextColor(Color.WHITE);
            holder.container.setAlpha(1.0f);
        } else {
            bg.setColor(Color.parseColor("#18181C"));
            bg.setStroke(dpToPx(1), Color.parseColor("#26262E"));
            holder.tvName.setTextColor(Color.parseColor("#C5C5D0"));
            holder.container.setAlpha(0.85f);
        }
        holder.container.setBackground(bg);

        holder.itemView.setOnClickListener(v -> {
            HapticHelper.performTabSwitch(v);
            selectedActivityId = act.getId();
            notifyDataSetChanged();
            if (listener != null) {
                listener.onActivitySelected(act);
            }
        });
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout container;
        ImageView ivIcon;
        TextView tvEmoji;
        TextView tvName;
        TextView tvTarget;

        ViewHolder(View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.chip_container);
            ivIcon = itemView.findViewById(R.id.iv_activity_icon);
            tvEmoji = itemView.findViewById(R.id.tv_activity_emoji);
            tvName = itemView.findViewById(R.id.tv_activity_name);
            tvTarget = itemView.findViewById(R.id.tv_target_badge);
        }
    }
}
