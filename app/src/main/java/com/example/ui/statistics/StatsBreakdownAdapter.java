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
import java.util.Locale;

public class StatsBreakdownAdapter extends RecyclerView.Adapter<StatsBreakdownAdapter.ViewHolder> {

    public interface OnItemEditClickListener {
        void onEditClick(TrackingRepository.ActivityStat stat);
    }

    private List<TrackingRepository.ActivityStat> stats = new ArrayList<>();
    private OnItemEditClickListener editClickListener;

    public void setStats(List<TrackingRepository.ActivityStat> stats) {
        this.stats = stats != null ? stats : new ArrayList<>();
        notifyDataSetChanged();
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(stats.get(position), editClickListener);
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

        void bind(TrackingRepository.ActivityStat stat, OnItemEditClickListener listener) {
            binding.tvStatName.setText(stat.name);
            binding.tvStatDuration.setText(IconHelper.formatDuration(stat.durationMillis));
            binding.tvStatPercentage.setVisibility(View.GONE);

            int color = IconHelper.parseColorOrDefault(stat.colorHex, Color.parseColor("#39D353"));
            IconHelper.setIcon(binding.ivStatIcon, stat.iconName, color);

            int iconBgColor = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color));
            IconHelper.setRoundedBackgroundColor(binding.statIconBg, iconBgColor, 10f, color, 0);

            View.OnClickListener clickListener = v -> {
                if (listener != null) {
                    listener.onEditClick(stat);
                }
            };
            binding.getRoot().setOnClickListener(clickListener);
        }
    }
}
