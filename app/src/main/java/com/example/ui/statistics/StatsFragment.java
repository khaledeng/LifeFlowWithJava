package com.example.ui.statistics;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.MainActivity;
import com.example.R;
import com.example.data.TrackingRepository;
import com.example.databinding.DialogEditStatTimeBinding;
import com.example.databinding.FragmentStatsBinding;
import com.example.util.IconHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.util.Calendar;
import java.util.List;

/**
 * StatsFragment presents high-precision multi-line trend analytics and activity breakdown
 * across Day, Week, Month, and Year periods with real-time overview badges and time adjustment.
 */
public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private TrackingRepository repository;
    private StatsBreakdownAdapter adapter;
    private int currentPeriodTab = 0; // Default to Day (0 = Day, 1 = Week, 2 = Month, 3 = Year)
    private int periodOffset = 0; // 0 = current period, -1 = previous period, -2 = 2 periods ago...

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = TrackingRepository.getInstance(requireContext());

        setupRecyclerView();
        setupTabs();
        setupPeriodNavigation();
        setupObservers();

        binding.btnMenu.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showBurgerMenu();
            }
        });
    }

    private void setupPeriodNavigation() {
        binding.btnPrevPeriod.setOnClickListener(v -> {
            periodOffset--;
            refreshAllStats();
        });

        binding.btnNextPeriod.setOnClickListener(v -> {
            if (periodOffset < 0) {
                periodOffset++;
                refreshAllStats();
            }
        });

        binding.chartMultiLineStats.setOnPeriodSwipeListener(new MultiLineStatsChartView.OnPeriodSwipeListener() {
            @Override
            public void onSwipeToPrevious() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        periodOffset--;
                        refreshAllStats();
                    });
                }
            }

            @Override
            public void onSwipeToNext() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (periodOffset < 0) {
                            periodOffset++;
                            refreshAllStats();
                        }
                    });
                }
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new StatsBreakdownAdapter();
        adapter.setOnItemEditClickListener(this::showEditTimeDialog);
        binding.rvStatsBreakdown.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvStatsBreakdown.setAdapter(adapter);
    }

    private void showEditTimeDialog(TrackingRepository.ActivityStat stat) {
        if (getContext() == null) return;

        DialogEditStatTimeBinding dialogBinding = DialogEditStatTimeBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogBinding.getRoot())
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Set activity name & dot color
        dialogBinding.tvDialogActivityName.setText(stat.name);
        int color = IconHelper.parseColorOrDefault(stat.colorHex, Color.parseColor("#39D353"));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dialogBinding.viewDialogDot.setBackground(dot);

        // Period tag label & max limits
        String periodTag;
        final long maxMinutes;
        final String maxLimitFormatted;
        switch (currentPeriodTab) {
            case 0: // Day: Strictly 24 hours
                periodTag = periodOffset == 0 ? getString(R.string.period_tag_today) : (periodOffset == -1 ? getString(R.string.period_tag_yesterday) : getString(R.string.period_tag_selected_day));
                maxMinutes = 24L * 60L;
                maxLimitFormatted = "24" + getString(R.string.hours_short) + " 00" + getString(R.string.minutes_short);
                break;
            case 1: // Week: 7 days = 168 hours
                periodTag = periodOffset == 0 ? getString(R.string.period_tag_this_week) : (periodOffset == -1 ? getString(R.string.period_tag_last_week) : getString(R.string.period_tag_selected_week));
                maxMinutes = 7L * 24L * 60L;
                maxLimitFormatted = "168" + getString(R.string.hours_short) + " 00" + getString(R.string.minutes_short);
                break;
            case 2: // Month: ~31 days = 744 hours
                periodTag = periodOffset == 0 ? getString(R.string.period_tag_this_month) : (periodOffset == -1 ? getString(R.string.period_tag_last_month) : getString(R.string.period_tag_selected_month));
                maxMinutes = 31L * 24L * 60L;
                maxLimitFormatted = "744" + getString(R.string.hours_short) + " 00" + getString(R.string.minutes_short);
                break;
            case 3: // Year: 366 days = 8784 hours
            default:
                periodTag = periodOffset == 0 ? getString(R.string.period_tag_this_year) : (periodOffset == -1 ? getString(R.string.period_tag_last_year) : getString(R.string.period_tag_selected_year));
                maxMinutes = 366L * 24L * 60L;
                maxLimitFormatted = "8784" + getString(R.string.hours_short) + " 00" + getString(R.string.minutes_short);
                break;
        }
        dialogBinding.tvDialogPeriodTag.setText(periodTag);
        dialogBinding.tvDialogLimitInfo.setText(getString(R.string.max_limit_info, maxLimitFormatted));
        dialogBinding.tvDialogCurrentDuration.setText(getString(R.string.current_time_prefix, IconHelper.formatDuration(stat.durationMillis)));

        // Pre-fill existing duration in hours and minutes (capped at max limit)
        long totalSecs = stat.durationMillis / 1000;
        final long[] totalMinutesHolder = { Math.min(maxMinutes, totalSecs / 60) };

        // Handle auto-clearing '0' on tap/focus so user can type immediately
        View.OnFocusChangeListener zeroClearFocusListener = (v, hasFocus) -> {
            android.widget.EditText et = (android.widget.EditText) v;
            if (hasFocus) {
                et.setHint("");
                if ("0".equals(et.getText().toString().trim())) {
                    et.setText("");
                } else {
                    et.selectAll();
                }
            } else {
                et.setHint("0");
            }
        };
        dialogBinding.etHours.setOnFocusChangeListener(zeroClearFocusListener);
        dialogBinding.etMinutes.setOnFocusChangeListener(zeroClearFocusListener);

        View.OnClickListener zeroClearClickListener = v -> {
            android.widget.EditText et = (android.widget.EditText) v;
            et.setHint("");
            if ("0".equals(et.getText().toString().trim())) {
                et.setText("");
            }
        };
        dialogBinding.etHours.setOnClickListener(zeroClearClickListener);
        dialogBinding.etMinutes.setOnClickListener(zeroClearClickListener);

        updateDialogInputFields(dialogBinding, totalMinutesHolder[0]);

        // Quick adjustment buttons (respecting 24h / period max boundaries)
        dialogBinding.btnAdjustMinus1h.setOnClickListener(v -> {
            dialogBinding.tilHours.setError(null);
            long current = getCurrentInputMinutes(dialogBinding, totalMinutesHolder[0]);
            totalMinutesHolder[0] = Math.max(0, current - 60);
            updateDialogInputFields(dialogBinding, totalMinutesHolder[0]);
        });

        dialogBinding.btnAdjustMinus15m.setOnClickListener(v -> {
            dialogBinding.tilHours.setError(null);
            long current = getCurrentInputMinutes(dialogBinding, totalMinutesHolder[0]);
            totalMinutesHolder[0] = Math.max(0, current - 15);
            updateDialogInputFields(dialogBinding, totalMinutesHolder[0]);
        });

        dialogBinding.btnAdjustPlus15m.setOnClickListener(v -> {
            dialogBinding.tilHours.setError(null);
            long current = getCurrentInputMinutes(dialogBinding, totalMinutesHolder[0]);
            totalMinutesHolder[0] = Math.min(maxMinutes, current + 15);
            updateDialogInputFields(dialogBinding, totalMinutesHolder[0]);
        });

        dialogBinding.btnAdjustPlus1h.setOnClickListener(v -> {
            dialogBinding.tilHours.setError(null);
            long current = getCurrentInputMinutes(dialogBinding, totalMinutesHolder[0]);
            totalMinutesHolder[0] = Math.min(maxMinutes, current + 60);
            updateDialogInputFields(dialogBinding, totalMinutesHolder[0]);
        });

        dialog.setOnDismissListener(d -> {
            IconHelper.hideKeyboard(dialogBinding.etHours);
            IconHelper.hideKeyboard(dialogBinding.etMinutes);
        });

        dialogBinding.btnCancelEditTime.setOnClickListener(v -> {
            IconHelper.hideKeyboard(dialogBinding.etHours);
            IconHelper.hideKeyboard(dialogBinding.etMinutes);
            dialog.dismiss();
        });

        dialogBinding.btnSaveEditTime.setOnClickListener(v -> {
            IconHelper.hideKeyboard(dialogBinding.etHours);
            IconHelper.hideKeyboard(dialogBinding.etMinutes);
            dialogBinding.tilHours.setError(null);
            dialogBinding.tilMinutes.setError(null);

            String hoursStr = dialogBinding.etHours.getText() != null ? dialogBinding.etHours.getText().toString().trim() : "";
            String minsStr = dialogBinding.etMinutes.getText() != null ? dialogBinding.etMinutes.getText().toString().trim() : "";

            long inputHours = 0;
            long inputMins = 0;
            try {
                if (!hoursStr.isEmpty()) inputHours = Long.parseLong(hoursStr);
            } catch (NumberFormatException ignored) {}

            try {
                if (!minsStr.isEmpty()) inputMins = Long.parseLong(minsStr);
            } catch (NumberFormatException ignored) {}

            long totalInputMinutes = (inputHours * 60L) + inputMins;

            // Strict Validation: A day cannot exceed 24 hours
            if (totalInputMinutes > maxMinutes) {
                if (currentPeriodTab == 0) {
                    dialogBinding.tilHours.setError(getString(R.string.max_day_limit_exceeded));
                    Toast.makeText(requireContext(), R.string.max_day_limit_exceeded, Toast.LENGTH_SHORT).show();
                } else {
                    dialogBinding.tilHours.setError(getString(R.string.max_period_limit_exceeded, maxLimitFormatted));
                    Toast.makeText(requireContext(), getString(R.string.max_period_limit_exceeded, maxLimitFormatted), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            long newTotalMillis = totalInputMinutes * 60L * 1000L;

            long[] range = getActivePeriodRange();
            repository.adjustActivityTime(
                    stat.activityId,
                    stat.name,
                    range[0],
                    range[1],
                    newTotalMillis,
                    () -> {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                refreshAllStats();
                                Toast.makeText(requireContext(), R.string.time_adjusted_toast, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
            );

            dialog.dismiss();
        });

        dialog.show();
    }

    private long getCurrentInputMinutes(DialogEditStatTimeBinding db, long fallback) {
        String hoursStr = db.etHours.getText() != null ? db.etHours.getText().toString().trim() : "";
        String minsStr = db.etMinutes.getText() != null ? db.etMinutes.getText().toString().trim() : "";
        if (hoursStr.isEmpty() && minsStr.isEmpty()) {
            return fallback;
        }
        long h = 0;
        long m = 0;
        try {
            if (!hoursStr.isEmpty()) h = Long.parseLong(hoursStr);
        } catch (NumberFormatException ignored) {}
        try {
            if (!minsStr.isEmpty()) m = Long.parseLong(minsStr);
        } catch (NumberFormatException ignored) {}
        return (h * 60L) + m;
    }

    private void updateDialogInputFields(DialogEditStatTimeBinding db, long totalMinutes) {
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        db.etHours.setText(h == 0 ? "" : String.valueOf(h));
        db.etMinutes.setText(m == 0 ? "" : String.valueOf(m));
    }

    private long[] getActivePeriodRange() {
        Calendar cal = Calendar.getInstance();
        long startMillis;
        long endMillis;

        switch (currentPeriodTab) {
            case 0: // Day
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DAY_OF_YEAR, periodOffset);
                startMillis = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 1);
                endMillis = cal.getTimeInMillis();
                break;
            case 1: // Week
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                cal.add(Calendar.WEEK_OF_YEAR, periodOffset);
                startMillis = cal.getTimeInMillis();
                cal.add(Calendar.WEEK_OF_YEAR, 1);
                endMillis = cal.getTimeInMillis();
                break;
            case 2: // Month
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, periodOffset);
                startMillis = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                endMillis = cal.getTimeInMillis();
                break;
            case 3: // Year
            default:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.add(Calendar.YEAR, periodOffset);
                startMillis = cal.getTimeInMillis();
                cal.add(Calendar.YEAR, 1);
                endMillis = cal.getTimeInMillis();
                break;
        }
        return new long[]{startMillis, endMillis};
    }

    private void setupTabs() {
        TabLayout.Tab defaultTab = binding.tabLayoutPeriod.getTabAt(currentPeriodTab);
        if (defaultTab != null) {
            defaultTab.select();
        }

        binding.tabLayoutPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentPeriodTab = tab.getPosition();
                periodOffset = 0; // Reset to current period when switching tabs
                refreshAllStats();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupObservers() {
        repository.getAllSessions().observe(getViewLifecycleOwner(), sessions -> {
            refreshAllStats();
        });
    }

    private void refreshAllStats() {
        updatePeriodNavigator();
        loadPeriodStats();
        loadChartTrends();
    }

    private void updatePeriodNavigator() {
        if (binding == null) return;

        Calendar cal = Calendar.getInstance();
        String title;
        String subtitle;

        java.text.SimpleDateFormat sdfDay = new java.text.SimpleDateFormat("EEE, MMM d, yyyy", java.util.Locale.getDefault());
        java.text.SimpleDateFormat sdfMonth = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
        java.text.SimpleDateFormat sdfShortDate = new java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault());

        switch (currentPeriodTab) {
            case 0: // Day
                cal.add(Calendar.DAY_OF_YEAR, periodOffset);
                if (periodOffset == 0) {
                    title = getString(R.string.today_title) + " (" + sdfDay.format(cal.getTime()) + ")";
                    subtitle = getString(R.string.period_subtitle_today);
                } else if (periodOffset == -1) {
                    title = getString(R.string.tab_yesterday) + " (" + sdfDay.format(cal.getTime()) + ")";
                    subtitle = getString(R.string.period_subtitle_yesterday);
                } else {
                    title = sdfDay.format(cal.getTime());
                    subtitle = getResources().getQuantityString(R.plurals.days_ago, Math.abs(periodOffset), Math.abs(periodOffset));
                }
                break;

            case 1: // Week
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                cal.add(Calendar.WEEK_OF_YEAR, periodOffset);
                long startW = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 6);
                long endW = cal.getTimeInMillis();
                String rangeStr = sdfShortDate.format(new java.util.Date(startW)) + " – " + sdfShortDate.format(new java.util.Date(endW));

                if (periodOffset == 0) {
                    title = (java.util.Locale.getDefault().getLanguage().equals("ar") ? "هذا الأسبوع" : "This Week") + " (" + rangeStr + ")";
                    subtitle = getString(R.string.period_subtitle_this_week);
                } else if (periodOffset == -1) {
                    title = (java.util.Locale.getDefault().getLanguage().equals("ar") ? "الأسبوع الماضي" : "Last Week") + " (" + rangeStr + ")";
                    subtitle = getString(R.string.period_subtitle_last_week);
                } else {
                    title = getString(R.string.period_navigation_week, rangeStr);
                    subtitle = getResources().getQuantityString(R.plurals.weeks_ago, Math.abs(periodOffset), Math.abs(periodOffset));
                }
                break;

            case 2: // Month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, periodOffset);
                if (periodOffset == 0) {
                    title = (java.util.Locale.getDefault().getLanguage().equals("ar") ? "هذا الشهر" : "This Month") + " (" + sdfMonth.format(cal.getTime()) + ")";
                    subtitle = getString(R.string.period_subtitle_this_month);
                } else if (periodOffset == -1) {
                    title = (java.util.Locale.getDefault().getLanguage().equals("ar") ? "الشهر الماضي" : "Last Month") + " (" + sdfMonth.format(cal.getTime()) + ")";
                    subtitle = getString(R.string.period_subtitle_last_month);
                } else {
                    title = sdfMonth.format(cal.getTime());
                    subtitle = getResources().getQuantityString(R.plurals.months_ago, Math.abs(periodOffset), Math.abs(periodOffset));
                }
                break;

            case 3: // Year
            default:
                cal.add(Calendar.YEAR, periodOffset);
                int year = cal.get(Calendar.YEAR);
                if (periodOffset == 0) {
                    title = (java.util.Locale.getDefault().getLanguage().equals("ar") ? "هذه السنة" : "This Year") + " (" + year + ")";
                    subtitle = getString(R.string.period_subtitle_this_year);
                } else if (periodOffset == -1) {
                    title = (java.util.Locale.getDefault().getLanguage().equals("ar") ? "السنة الماضية" : "Last Year") + " (" + year + ")";
                    subtitle = getString(R.string.period_subtitle_last_year);
                } else {
                    title = getString(R.string.period_navigation_year, year);
                    subtitle = getResources().getQuantityString(R.plurals.years_ago, Math.abs(periodOffset), Math.abs(periodOffset));
                }
                break;
        }

        binding.tvPeriodNavTitle.setText(title);
        binding.tvPeriodNavSubtitle.setText(subtitle);

        // Next button state: disabled when viewing current period (offset == 0)
        boolean canGoNext = periodOffset < 0;
        binding.btnNextPeriod.setEnabled(canGoNext);
        binding.btnNextPeriod.setAlpha(canGoNext ? 1.0f : 0.25f);
    }

    private void loadChartTrends() {
        repository.calculateTrends(currentPeriodTab, periodOffset, trendData -> {
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;

                // Setup interactive legend chips
                binding.chipGroupLegend.removeAllViews();
                float density = getResources().getDisplayMetrics().density;
                for (MultiLineStatsChartView.Series s : trendData.seriesList) {
                    Chip chip = new Chip(requireContext());
                    chip.setText(s.name);
                    chip.setCheckable(true);

                    if (IconHelper.isEmojiIcon(s.iconName)) {
                        chip.setChipIcon(IconHelper.getDrawableForIcon(requireContext(), s.iconName));
                        chip.setChipIconVisible(true);
                        chip.setChipIconTint(null);
                    } else {
                        int iconRes = IconHelper.getDrawableResForIcon(s.iconName);
                        chip.setChipIconResource(iconRes);
                        chip.setChipIconVisible(true);
                        chip.setChipIconTint(ColorStateList.valueOf(s.color));
                    }
                    chip.setChipIconSize(18f * density);

                    chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1B1B1E")));
                    chip.setTextColor(ColorStateList.valueOf(Color.WHITE));
                    chip.setChipStrokeColor(ColorStateList.valueOf(s.color));
                    chip.setChipStrokeWidth(1.2f * density);
                    chip.setTextSize(12.5f);

                    chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            binding.chartMultiLineStats.setHighlightedActivityId(s.activityId);
                            int highlightBg = Color.argb(45, Color.red(s.color), Color.green(s.color), Color.blue(s.color));
                            chip.setChipBackgroundColor(ColorStateList.valueOf(highlightBg));
                            chip.setChipStrokeWidth(2f * density);
                        } else {
                            binding.chartMultiLineStats.setHighlightedActivityId(null);
                            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1B1B1E")));
                            chip.setChipStrokeWidth(1.2f * density);
                        }
                    });

                    binding.chipGroupLegend.addView(chip);
                }

                binding.chartMultiLineStats.setData(trendData.seriesList, trendData.xLabels);
            });
        });
    }

    private void loadPeriodStats() {
        long[] range = getActivePeriodRange();
        long startMillis = range[0];
        long endMillis = range[1];

        repository.calculateStats(startMillis, endMillis, (totalTrackedMillis, totalWindowMillis, stats) -> {
            if (binding == null) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;

                if (stats.isEmpty()) {
                    binding.tvEmptyStats.setVisibility(View.VISIBLE);
                    binding.rvStatsBreakdown.setVisibility(View.GONE);
                    binding.layoutDistributionHeader.setVisibility(View.GONE);
                    binding.chartPieStats.setVisibility(View.GONE);
                } else {
                    binding.tvEmptyStats.setVisibility(View.GONE);
                    binding.rvStatsBreakdown.setVisibility(View.VISIBLE);
                    binding.layoutDistributionHeader.setVisibility(View.VISIBLE);
                    binding.chartPieStats.setVisibility(View.VISIBLE);
                    adapter.setStats(stats);

                    // Build Pie Chart slices representing the relative occupancy of tracked time
                    java.util.List<PieChartView.Slice> pieSlices = new java.util.ArrayList<>();
                    long trackedTotal = 0;
                    for (com.example.data.TrackingRepository.ActivityStat stat : stats) {
                        trackedTotal += stat.durationMillis;
                    }

                    if (trackedTotal > 0) {
                        for (com.example.data.TrackingRepository.ActivityStat stat : stats) {
                            float pct = ((float) stat.durationMillis / trackedTotal) * 100f;
                            int color = com.example.util.IconHelper.parseColorOrDefault(stat.colorHex, Color.parseColor("#39D353"));
                            pieSlices.add(new PieChartView.Slice(stat.name, pct, color));
                        }
                    }

                    binding.chartPieStats.setSlices(pieSlices);
                }
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllStats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
