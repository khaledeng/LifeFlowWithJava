package com.example.ui.progress;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.MainActivity;
import com.example.R;
import com.example.data.TrackingRepository;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityCategory;
import com.example.data.model.ProgressDayData;
import com.example.data.model.ProgressSummary;
import com.example.data.model.AllActivitiesMatrixData;
import com.example.util.HapticHelper;
import android.widget.HorizontalScrollView;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.view.Gravity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackProgressFragment extends Fragment {

    private TrackingRepository repository;

    // View switcher tabs: 0 = Monthly, 1 = Weekly, 2 = Daily
    private int currentTab = 0;

    private int monthOffset = 0;
    private int weekOffset = 0;
    private int dayOffset = 0;

    private long selectedActivityId = -1;
    private int selectedDayOfMonth = -1;

    private ProgressActivityAdapter activityAdapter;
    private CalendarMonthAdapter monthAdapter;
    private ProgressWeekCardsAdapter weekAdapter;
    private ProgressDailyDayAdapter dailyDayAdapter;

    private ProgressSummary currentSummary;

    // Views
    private TextView tvHeaderStreak;
    private RecyclerView rvActivities;
    private TextView tvCurrentStreakVal, tvLongestStreakVal, tvTargetGoalVal, tvGoalCardLabel, tvCompletionRateVal, tvMonthlyGoalSubtext;

    private TextView tabMonthly, tabWeekly, tabDaily;
    private LinearLayout layoutMonthlyView, layoutWeeklyView, layoutDailyView;

    // Monthly View elements
    private TextView tvCurrentMonthName;
    private RecyclerView rvCalendarGrid;
    private LinearLayout cardSelectedDayDetail;
    private LinearLayout layoutMonthLegendIncrease, layoutMonthLegendDecrease;
    private TextView tvDetailDayTitle, tvDetailDayStatusBadge, tvDetailTrackedTime, tvDetailTargetTime, tvDetailPercentText, tvDetailMotivationalNote;
    private ProgressBar pbDetailProgress;

    // Weekly View elements
    private TextView tvCurrentWeekTitle, tvWeeklyLegend;
    private RecyclerView rvWeekCards;

    // Daily View elements
    private RecyclerView rvDailyHistoryDays;
    private TextView tvDailyLegend;
    private TextView tvDailyDetailTitle, tvDailyDetailBadge, tvDailyDetailTracked, tvDailyDetailTarget, tvDailyDetailPercent, tvDailyDetailNote;
    private ProgressBar pbDailyDetailBar;

    // Monthly Habit Matrix elements
    private LinearLayout cardAllActivitiesMatrix;
    private TextView tvMatrixMonthBadge;
    private LinearLayout layoutMatrixLeftColumn;
    private LinearLayout layoutMatrixRightGrid;
    private HorizontalScrollView scrollMatrixRightGrid;

    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat dayFullFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_track_progress, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = TrackingRepository.getInstance(requireContext());

        initViews(view);
        setupRecyclerViews();
        setupTabs();
        setupNavigationButtons(view);

        // Load activities and observe changes
        repository.getAllActivities().observe(getViewLifecycleOwner(), activities -> {
            if (activities != null && !activities.isEmpty()) {
                if (selectedActivityId == -1) {
                    selectedActivityId = activities.get(0).getId();
                }
                activityAdapter.setActivities(activities, selectedActivityId);
                loadProgressData();
            }
        });

        // Observe session database changes for live real-time progress calculations
        repository.getAllSessions().observe(getViewLifecycleOwner(), sessions -> {
            loadProgressData();
        });
        repository.getActiveSession().observe(getViewLifecycleOwner(), activeSession -> {
            loadProgressData();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProgressData();
    }

    private void initViews(View view) {
        ImageView btnBurger = view.findViewById(R.id.btn_burger_menu);
        btnBurger.setOnClickListener(v -> {
            HapticHelper.performTabSwitch(v);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showBurgerMenu();
            }
        });

        tvHeaderStreak = view.findViewById(R.id.tv_header_streak_count);
        rvActivities = view.findViewById(R.id.rv_activity_selector);

        tvCurrentStreakVal = view.findViewById(R.id.tv_current_streak_val);
        tvLongestStreakVal = view.findViewById(R.id.tv_longest_streak_val);
        tvTargetGoalVal = view.findViewById(R.id.tv_target_goal_val);
        tvGoalCardLabel = view.findViewById(R.id.tv_goal_card_label);
        tvCompletionRateVal = view.findViewById(R.id.tv_completion_rate_val);
        tvMonthlyGoalSubtext = view.findViewById(R.id.tv_monthly_goal_subtext);

        tabMonthly = view.findViewById(R.id.tab_monthly);
        tabWeekly = view.findViewById(R.id.tab_weekly);
        tabDaily = view.findViewById(R.id.tab_daily);

        layoutMonthlyView = view.findViewById(R.id.layout_monthly_view);
        layoutWeeklyView = view.findViewById(R.id.layout_weekly_view);
        layoutDailyView = view.findViewById(R.id.layout_daily_view);

        // Monthly
        tvCurrentMonthName = view.findViewById(R.id.tv_current_month_name);
        rvCalendarGrid = view.findViewById(R.id.rv_calendar_grid);
        cardSelectedDayDetail = view.findViewById(R.id.card_selected_day_detail);
        layoutMonthLegendIncrease = view.findViewById(R.id.layout_month_legend_increase);
        layoutMonthLegendDecrease = view.findViewById(R.id.layout_month_legend_decrease);
        tvDetailDayTitle = view.findViewById(R.id.tv_detail_day_title);
        tvDetailDayStatusBadge = view.findViewById(R.id.tv_detail_day_status_badge);
        tvDetailTrackedTime = view.findViewById(R.id.tv_detail_tracked_time);
        tvDetailTargetTime = view.findViewById(R.id.tv_detail_target_time);
        tvDetailPercentText = view.findViewById(R.id.tv_detail_percent_text);
        tvDetailMotivationalNote = view.findViewById(R.id.tv_detail_motivational_note);
        pbDetailProgress = view.findViewById(R.id.pb_detail_progress);

        // Weekly
        tvCurrentWeekTitle = view.findViewById(R.id.tv_current_week_title);
        tvWeeklyLegend = view.findViewById(R.id.tv_weekly_legend);
        rvWeekCards = view.findViewById(R.id.rv_week_cards);

        // Daily
        rvDailyHistoryDays = view.findViewById(R.id.rv_daily_history_days);
        tvDailyLegend = view.findViewById(R.id.tv_daily_legend);
        tvDailyDetailTitle = view.findViewById(R.id.tv_daily_detail_title);
        tvDailyDetailBadge = view.findViewById(R.id.tv_daily_detail_badge);
        tvDailyDetailTracked = view.findViewById(R.id.tv_daily_detail_tracked);
        tvDailyDetailTarget = view.findViewById(R.id.tv_daily_detail_target);
        tvDailyDetailPercent = view.findViewById(R.id.tv_daily_detail_percent);
        tvDailyDetailNote = view.findViewById(R.id.tv_daily_detail_note);
        pbDailyDetailBar = view.findViewById(R.id.pb_daily_detail_bar);

        // All Activities Matrix
        cardAllActivitiesMatrix = view.findViewById(R.id.card_all_activities_matrix);
        tvMatrixMonthBadge = view.findViewById(R.id.tv_matrix_month_badge);
        layoutMatrixLeftColumn = view.findViewById(R.id.layout_matrix_left_column);
        layoutMatrixRightGrid = view.findViewById(R.id.layout_matrix_right_grid);
        scrollMatrixRightGrid = view.findViewById(R.id.scroll_matrix_right_grid);
    }

    private void setupRecyclerViews() {
        // Activities Horizontal Chips
        activityAdapter = new ProgressActivityAdapter(requireContext(), activity -> {
            selectedActivityId = activity.getId();
            loadProgressData();
        });
        rvActivities.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvActivities.setAdapter(activityAdapter);

        // Monthly Calendar Grid (7 columns)
        monthAdapter = new CalendarMonthAdapter(requireContext(), dayData -> {
            selectedDayOfMonth = dayData.dayOfMonth;
            updateSelectedDayDetail(dayData);
        });
        rvCalendarGrid.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        rvCalendarGrid.setAdapter(monthAdapter);

        // Weekly List (Cards per week)
        weekAdapter = new ProgressWeekCardsAdapter(requireContext());
        rvWeekCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvWeekCards.setAdapter(weekAdapter);

        // Daily Horizontal Days Carousel
        dailyDayAdapter = new ProgressDailyDayAdapter();
        dailyDayAdapter.setOnDayClickListener(dayData -> {
            updateDailySelectedDetail(dayData);
        });
        rvDailyHistoryDays.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvDailyHistoryDays.setAdapter(dailyDayAdapter);
    }

    private void setupTabs() {
        tabMonthly.setOnClickListener(v -> switchTab(0));
        tabWeekly.setOnClickListener(v -> switchTab(1));
        tabDaily.setOnClickListener(v -> switchTab(2));
    }

    private void switchTab(int tabIndex) {
        if (currentTab == tabIndex) return;
        currentTab = tabIndex;
        HapticHelper.performTabSwitch(getView());

        tabMonthly.setBackgroundResource(tabIndex == 0 ? R.drawable.bg_tab_indicator_pill : 0);
        tabMonthly.setTextColor(tabIndex == 0 ? Color.WHITE : Color.parseColor("#8E8E93"));

        tabWeekly.setBackgroundResource(tabIndex == 1 ? R.drawable.bg_tab_indicator_pill : 0);
        tabWeekly.setTextColor(tabIndex == 1 ? Color.WHITE : Color.parseColor("#8E8E93"));

        tabDaily.setBackgroundResource(tabIndex == 2 ? R.drawable.bg_tab_indicator_pill : 0);
        tabDaily.setTextColor(tabIndex == 2 ? Color.WHITE : Color.parseColor("#8E8E93"));

        layoutMonthlyView.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutWeeklyView.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutDailyView.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        if (currentSummary != null) {
            renderSummaryUI(currentSummary);
        }
    }

    private void setupNavigationButtons(View root) {
        // Month Navigation
        root.findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            HapticHelper.performTabSwitch(v);
            monthOffset--;
            selectedDayOfMonth = -1;
            loadProgressData();
        });
        root.findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            HapticHelper.performTabSwitch(v);
            monthOffset++;
            selectedDayOfMonth = -1;
            loadProgressData();
        });
        View btnTodayMonth = root.findViewById(R.id.btn_today_month_jump);
        if (btnTodayMonth != null) {
            btnTodayMonth.setOnClickListener(v -> {
                HapticHelper.performTabSwitch(v);
                monthOffset = 0;
                loadProgressData();
            });
        }

        // Week Navigation
        View btnPrevWeek = root.findViewById(R.id.btn_prev_week);
        if (btnPrevWeek != null) {
            btnPrevWeek.setOnClickListener(v -> {
                HapticHelper.performTabSwitch(v);
                weekOffset--;
                loadProgressData();
            });
        }
        View btnNextWeek = root.findViewById(R.id.btn_next_week);
        if (btnNextWeek != null) {
            btnNextWeek.setOnClickListener(v -> {
                HapticHelper.performTabSwitch(v);
                weekOffset++;
                loadProgressData();
            });
        }
        View btnTodayWeek = root.findViewById(R.id.btn_today_week_jump);
        if (btnTodayWeek != null) {
            btnTodayWeek.setOnClickListener(v -> {
                HapticHelper.performTabSwitch(v);
                weekOffset = 0;
                loadProgressData();
            });
        }
    }

    private void loadProgressData() {
        if (selectedActivityId <= 0 && activityAdapter.getItemCount() > 0) {
            Activity first = activityAdapter.getSelectedActivity();
            if (first != null) selectedActivityId = first.getId();
        }

        repository.calculateProgressSummary(selectedActivityId, monthOffset, weekOffset, summary -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                currentSummary = summary;
                renderSummaryUI(summary);
            });
        });

        repository.calculateAllActivitiesMatrix(monthOffset, matrix -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                renderAllActivitiesMatrix(matrix);
            });
        });
    }

    private void renderSummaryUI(ProgressSummary summary) {
        if (summary == null) return;

        // Top Streak Header Pill
        tvHeaderStreak.setText(summary.currentStreak + "d");

        // 4 Key Metrics Cards
        tvCurrentStreakVal.setText(summary.currentStreak + " " + getString(R.string.unit_days));
        tvLongestStreakVal.setText(summary.longestStreak + " " + getString(R.string.unit_days));

        if (summary.dailyTargetHours > 0) {
            if (summary.dailyTargetHours == (int) summary.dailyTargetHours) {
                tvTargetGoalVal.setText(((int) summary.dailyTargetHours) + "h / " + getString(R.string.unit_day));
            } else {
                tvTargetGoalVal.setText(String.format(Locale.US, "%.1fh / %s", summary.dailyTargetHours, getString(R.string.unit_day)));
            }
        } else {
            tvTargetGoalVal.setText(R.string.no_target_set);
        }

        // Dynamic Legend for Monthly, Weekly, and Daily Views
        boolean isDecrease = (summary.selectedActivity != null && summary.selectedActivity.getCategory() == com.example.data.entity.ActivityCategory.DECREASE);

        if (currentTab == 1) { // Weekly Goal
            if (tvGoalCardLabel != null) {
                tvGoalCardLabel.setText(R.string.weekly_goal_label);
            }
            if (summary.weeklyTargetHours > 0) {
                float pct = summary.weeklyGoalPercentage;
                tvCompletionRateVal.setText(Math.round(pct) + "%");
                tvCompletionRateVal.setTextColor(getGoalPercentageColor(pct, isDecrease));
                String trackedStr = formatHoursNicely(summary.weeklyTrackedHours);
                String targetStr = formatHoursNicely(summary.weeklyTargetHours);
                tvMonthlyGoalSubtext.setText(trackedStr + " / " + targetStr);
                tvMonthlyGoalSubtext.setVisibility(View.VISIBLE);
            } else {
                String trackedStr = formatHoursNicely(summary.weeklyTrackedHours);
                tvCompletionRateVal.setText(trackedStr);
                tvCompletionRateVal.setTextColor(Color.parseColor("#8E8E93"));
                tvMonthlyGoalSubtext.setText(R.string.no_target_set);
                tvMonthlyGoalSubtext.setVisibility(View.VISIBLE);
            }
        } else if (currentTab == 2) { // Daily Goal
            if (tvGoalCardLabel != null) {
                tvGoalCardLabel.setText(R.string.daily_goal_label);
            }
            float dailyTracked = (summary.todayData != null) ? (summary.todayData.trackedMillis / 3600000f) : 0f;
            float dailyTarget = summary.dailyTargetHours;
            if (dailyTarget > 0) {
                float pct = (summary.todayData != null) ? summary.todayData.percentage : 0f;
                tvCompletionRateVal.setText(Math.round(pct) + "%");
                tvCompletionRateVal.setTextColor(getGoalPercentageColor(pct, isDecrease));
                String trackedStr = formatHoursNicely(dailyTracked);
                String targetStr = formatHoursNicely(dailyTarget);
                tvMonthlyGoalSubtext.setText(trackedStr + " / " + targetStr);
                tvMonthlyGoalSubtext.setVisibility(View.VISIBLE);
            } else {
                String trackedStr = formatHoursNicely(dailyTracked);
                tvCompletionRateVal.setText(trackedStr);
                tvCompletionRateVal.setTextColor(Color.parseColor("#8E8E93"));
                tvMonthlyGoalSubtext.setText(R.string.no_target_set);
                tvMonthlyGoalSubtext.setVisibility(View.VISIBLE);
            }
        } else { // Monthly Goal
            if (tvGoalCardLabel != null) {
                tvGoalCardLabel.setText(R.string.monthly_goal_label);
            }
            if (summary.monthlyTargetHours > 0) {
                float pct = summary.monthlyGoalPercentage;
                tvCompletionRateVal.setText(Math.round(pct) + "%");
                tvCompletionRateVal.setTextColor(getGoalPercentageColor(pct, isDecrease));
                String trackedStr = formatHoursNicely(summary.monthlyTrackedHours);
                String targetStr = formatHoursNicely(summary.monthlyTargetHours);
                tvMonthlyGoalSubtext.setText(trackedStr + " / " + targetStr);
                tvMonthlyGoalSubtext.setVisibility(View.VISIBLE);
            } else {
                String trackedStr = formatHoursNicely(summary.monthlyTrackedHours);
                tvCompletionRateVal.setText(trackedStr);
                tvCompletionRateVal.setTextColor(Color.parseColor("#8E8E93"));
                tvMonthlyGoalSubtext.setText(R.string.no_target_set);
                tvMonthlyGoalSubtext.setVisibility(View.VISIBLE);
            }
        }

        if (layoutMonthLegendIncrease != null) {
            layoutMonthLegendIncrease.setVisibility(isDecrease ? View.GONE : View.VISIBLE);
        }
        if (layoutMonthLegendDecrease != null) {
            layoutMonthLegendDecrease.setVisibility(isDecrease ? View.VISIBLE : View.GONE);
        }
        if (tvWeeklyLegend != null) {
            tvWeeklyLegend.setText(isDecrease ? R.string.legend_decrease_summary : R.string.legend_increase_summary);
        }
        if (tvDailyLegend != null) {
            tvDailyLegend.setText(isDecrease ? R.string.legend_decrease_summary : R.string.legend_increase_summary);
        }

        // Update Month Header Name
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, monthOffset);
        tvCurrentMonthName.setText(monthYearFormat.format(cal.getTime()));

        // Default selected day in month
        if (selectedDayOfMonth == -1) {
            if (summary.todayData != null && monthOffset == 0) {
                selectedDayOfMonth = summary.todayData.dayOfMonth;
            } else if (!summary.monthDays.isEmpty()) {
                selectedDayOfMonth = summary.monthDays.get(0).dayOfMonth;
            }
        }

        // Render Monthly Grid
        monthAdapter.setData(summary.monthDays, summary.firstDayOfWeekOffset, selectedDayOfMonth);

        // Render Selected Day detail
        ProgressDayData selectedDay = null;
        for (ProgressDayData d : summary.monthDays) {
            if (d.dayOfMonth == selectedDayOfMonth) {
                selectedDay = d;
                break;
            }
        }
        if (selectedDay == null && !summary.monthDays.isEmpty()) {
            selectedDay = summary.monthDays.get(0);
        }
        if (selectedDay != null) {
            updateSelectedDayDetail(selectedDay);
        }

        // Render Weekly List
        if (tvCurrentWeekTitle != null) {
            if (summary.weekDays != null && !summary.weekDays.isEmpty()) {
                SimpleDateFormat sdfWeekLabel = new SimpleDateFormat("MMM d", Locale.getDefault());
                String weekStartStr = sdfWeekLabel.format(new Date(summary.weekDays.get(0).startOfDayMillis));
                String weekEndStr = sdfWeekLabel.format(new Date(summary.weekDays.get(summary.weekDays.size() - 1).startOfDayMillis));

                String weekName;
                if (weekOffset == 0) {
                    weekName = getString(R.string.this_week_title);
                } else if (weekOffset == -1) {
                    weekName = getString(R.string.last_week_title);
                } else if (weekOffset == -2) {
                    weekName = getString(R.string.two_weeks_ago_title);
                } else if (weekOffset < -2) {
                    weekName = String.format(Locale.getDefault(), getString(R.string.weeks_ago_format), Math.abs(weekOffset));
                } else if (weekOffset == 1) {
                    weekName = getString(R.string.next_week_title);
                } else {
                    weekName = String.format(Locale.getDefault(), getString(R.string.weeks_later_format), weekOffset);
                }
                tvCurrentWeekTitle.setText(weekName + " (" + weekStartStr + " - " + weekEndStr + ")");
            } else {
                tvCurrentWeekTitle.setText(getString(R.string.this_week_title));
            }
        }
        weekAdapter.setWeeks(summary.weeksHistory);

        // Render Daily Carousel & Details
        dailyDayAdapter.setDays(summary.dailyHistoryDays);
        if (!summary.dailyHistoryDays.isEmpty()) {
            updateDailySelectedDetail(summary.dailyHistoryDays.get(0));
        }
    }

    private void updateDailySelectedDetail(ProgressDayData day) {
        if (day == null) return;

        String dayStr = dayFullFormat.format(new Date(day.startOfDayMillis));
        if (day.isToday) {
            dayStr = getString(R.string.today_title) + " (" + dayStr + ")";
        }
        tvDailyDetailTitle.setText(dayStr);

        long trackedSecs = day.trackedMillis / 1000;
        long trackedH = trackedSecs / 3600;
        long trackedM = (trackedSecs % 3600) / 60;
        tvDailyDetailTracked.setText(String.format(Locale.getDefault(), "%dh %02dm", trackedH, trackedM));

        long targetSecs = day.targetMillis / 1000;
        long targetH = targetSecs / 3600;
        long targetM = (targetSecs % 3600) / 60;
        tvDailyDetailTarget.setText(String.format(Locale.getDefault(), "%dh %02dm %s", targetH, targetM, getString(R.string.target_label_suffix)));

        int pctInt = Math.round(day.percentage);
        tvDailyDetailPercent.setText(pctInt + "%");
        pbDailyDetailBar.setProgress(Math.min(100, pctInt));

        if (day.isFuture) {
            tvDailyDetailBadge.setText(R.string.status_upcoming);
            tvDailyDetailBadge.setTextColor(Color.parseColor("#8E8E93"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#393945")));
            tvDailyDetailNote.setText(R.string.note_future_day);
        } else if (day.status == ProgressDayData.Status.EXCEEDED_LIMIT_100) {
            tvDailyDetailBadge.setText("✕ " + getString(R.string.status_limit_exceeded));
            tvDailyDetailBadge.setTextColor(Color.parseColor("#FF4D4D"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF4D4D")));
            tvDailyDetailNote.setText(getString(R.string.note_limit_exceeded));
        } else if (day.status == ProgressDayData.Status.PARTIAL_RED) {
            tvDailyDetailBadge.setText(pctInt + "% " + getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.status_danger_limit : R.string.status_in_progress));
            tvDailyDetailBadge.setTextColor(Color.parseColor("#FF4D4D"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF4D4D")));
            tvDailyDetailNote.setText(getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.note_decrease_warning_near_limit : (day.isToday ? R.string.note_needs_focus : R.string.note_needs_focus_past)));
        } else if (day.status == ProgressDayData.Status.COMPLETED_100) {
            tvDailyDetailBadge.setText("✓ " + getString(R.string.status_achieved));
            tvDailyDetailBadge.setTextColor(Color.parseColor("#39D353"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            tvDailyDetailNote.setText(getString(R.string.note_streak_maintained));
        } else if (day.status == ProgressDayData.Status.PARTIAL_GREEN) {
            tvDailyDetailBadge.setText(pctInt + "% " + getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.legend_decrease_safe : R.string.status_on_track));
            tvDailyDetailBadge.setTextColor(Color.parseColor("#39D353"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            tvDailyDetailNote.setText(getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.note_decrease_under_limit : R.string.note_good_progress));
        } else if (day.status == ProgressDayData.Status.PARTIAL_ORANGE) {
            tvDailyDetailBadge.setText(pctInt + "% " + getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.legend_decrease_warning : R.string.status_in_progress));
            tvDailyDetailBadge.setTextColor(Color.parseColor("#FF8C42"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF8C42")));
            tvDailyDetailNote.setText(getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.note_decrease_warning_near_limit : (day.isToday ? R.string.note_needs_focus : R.string.note_needs_focus_past)));
        } else if (day.category == com.example.data.entity.ActivityCategory.DECREASE) {
            tvDailyDetailBadge.setText("0% " + getString(R.string.status_achieved));
            tvDailyDetailBadge.setTextColor(Color.parseColor("#39D353"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            tvDailyDetailNote.setText(R.string.note_decrease_zero_success);
        } else {
            tvDailyDetailBadge.setText(R.string.status_not_tracked);
            tvDailyDetailBadge.setTextColor(Color.parseColor("#8E8E93"));
            pbDailyDetailBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#33333D")));
            tvDailyDetailNote.setText(R.string.note_no_activity_logged);
        }
    }

    private void updateSelectedDayDetail(ProgressDayData day) {
        if (day == null) return;

        String dayStr = dayFullFormat.format(new Date(day.startOfDayMillis));
        if (day.isToday) {
            dayStr = dayStr + " (" + getString(R.string.today_title) + ")";
        }
        tvDetailDayTitle.setText(dayStr);

        long trackedSecs = day.trackedMillis / 1000;
        long trackedH = trackedSecs / 3600;
        long trackedM = (trackedSecs % 3600) / 60;
        tvDetailTrackedTime.setText(String.format(Locale.getDefault(), "%dh %02dm", trackedH, trackedM));

        long targetSecs = day.targetMillis / 1000;
        long targetH = targetSecs / 3600;
        long targetM = (targetSecs % 3600) / 60;
        tvDetailTargetTime.setText(String.format(Locale.getDefault(), "%dh %02dm %s", targetH, targetM, getString(R.string.target_label_suffix)));

        int pctInt = Math.round(day.percentage);
        tvDetailPercentText.setText(pctInt + "%");
        pbDetailProgress.setProgress(Math.min(100, pctInt));

        if (day.isFuture) {
            tvDetailDayStatusBadge.setText(R.string.status_upcoming);
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#8E8E93"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#393945")));
            tvDetailMotivationalNote.setText(R.string.note_future_day);
        } else if (day.status == ProgressDayData.Status.EXCEEDED_LIMIT_100) {
            tvDetailDayStatusBadge.setText("✕ " + getString(R.string.status_limit_exceeded));
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#FF4D4D"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF4D4D")));
            tvDetailMotivationalNote.setText(getString(R.string.note_limit_exceeded));
        } else if (day.status == ProgressDayData.Status.PARTIAL_RED) {
            tvDetailDayStatusBadge.setText(pctInt + "% " + getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.status_danger_limit : R.string.status_in_progress));
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#FF4D4D"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF4D4D")));
            tvDetailMotivationalNote.setText(getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.note_decrease_warning_near_limit : (day.isToday ? R.string.note_needs_focus : R.string.note_needs_focus_past)));
        } else if (day.status == ProgressDayData.Status.COMPLETED_100) {
            tvDetailDayStatusBadge.setText("✓ " + getString(R.string.status_achieved));
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#39D353"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            tvDetailMotivationalNote.setText(getString(R.string.note_streak_maintained));
        } else if (day.status == ProgressDayData.Status.PARTIAL_GREEN) {
            tvDetailDayStatusBadge.setText(pctInt + "% " + getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.legend_decrease_safe : R.string.status_on_track));
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#39D353"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            tvDetailMotivationalNote.setText(getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.note_decrease_under_limit : R.string.note_good_progress));
        } else if (day.status == ProgressDayData.Status.PARTIAL_ORANGE) {
            tvDetailDayStatusBadge.setText(pctInt + "% " + getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.legend_decrease_warning : R.string.status_in_progress));
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#FF8C42"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF8C42")));
            tvDetailMotivationalNote.setText(getString(day.category == com.example.data.entity.ActivityCategory.DECREASE ? R.string.note_decrease_warning_near_limit : (day.isToday ? R.string.note_needs_focus : R.string.note_needs_focus_past)));
        } else if (day.category == com.example.data.entity.ActivityCategory.DECREASE) {
            tvDetailDayStatusBadge.setText("0% " + getString(R.string.status_achieved));
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#39D353"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#39D353")));
            tvDetailMotivationalNote.setText(R.string.note_decrease_zero_success);
        } else {
            tvDetailDayStatusBadge.setText(R.string.status_not_tracked);
            tvDetailDayStatusBadge.setTextColor(Color.parseColor("#8E8E93"));
            pbDetailProgress.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#33333D")));
            tvDetailMotivationalNote.setText(R.string.note_no_activity_logged);
        }
    }

    private String formatHoursNicely(float hours) {
        if (hours == (int) hours) {
            return ((int) hours) + "h";
        } else if (hours < 10) {
            return String.format(Locale.US, "%.1fh", hours);
        } else {
            return Math.round(hours) + "h";
        }
    }

    private int getGoalPercentageColor(float pct, boolean isDecrease) {
        if (isDecrease) {
            if (pct >= 100f) {
                return Color.parseColor("#FF4D4D"); // Red (Limit broken / Danger)
            } else if (pct > 90f) {
                return Color.parseColor("#FF8C42"); // Orange (Near limit / Warning)
            } else {
                return Color.parseColor("#39D353"); // Green (Safe / Under limit)
            }
        } else {
            if (pct >= 100f) {
                return Color.parseColor("#39D353"); // Green (Achieved 100%+)
            } else if (pct >= 50f) {
                return Color.parseColor("#39D353"); // Green (Good progress >= 50%)
            } else if (pct > 0f) {
                return Color.parseColor("#FF8C42"); // Orange (In progress < 50%)
            } else {
                return Color.parseColor("#FF4D4D"); // Red (0% completed)
            }
        }
    }

    private void renderAllActivitiesMatrix(AllActivitiesMatrixData matrix) {
        if (getContext() == null || cardAllActivitiesMatrix == null) return;

        layoutMatrixLeftColumn.removeAllViews();
        layoutMatrixRightGrid.removeAllViews();

        if (matrix == null || matrix.rows == null || matrix.rows.isEmpty()) {
            cardAllActivitiesMatrix.setVisibility(View.GONE);
            return;
        }

        cardAllActivitiesMatrix.setVisibility(View.VISIBLE);
        tvMatrixMonthBadge.setText(matrix.monthName);

        boolean isAr = Locale.getDefault().getLanguage().equals("ar");
        int rowHeight = dpToPx(48);
        int cellWidth = dpToPx(42);

        // 1. Create Header Row
        // Left column header view
        LinearLayout leftHeader = new LinearLayout(getContext());
        leftHeader.setOrientation(LinearLayout.HORIZONTAL);
        leftHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams leftHeaderLp = new LinearLayout.LayoutParams(dpToPx(80), rowHeight);
        leftHeader.setLayoutParams(leftHeaderLp);

        TextView tvLeftHeader = new TextView(getContext());
        tvLeftHeader.setText(isAr ? "الأنشطة" : "Activities");
        tvLeftHeader.setTextColor(Color.parseColor("#8E8E93"));
        tvLeftHeader.setTextSize(11);
        tvLeftHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLeftHeader.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        leftHeader.addView(tvLeftHeader);
        layoutMatrixLeftColumn.addView(leftHeader);

        // Right column header view
        LinearLayout rightHeaderRow = new LinearLayout(getContext());
        rightHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        rightHeaderRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight));

        for (AllActivitiesMatrixData.DayHeader dh : matrix.dayHeaders) {
            LinearLayout cellHeader = new LinearLayout(getContext());
            cellHeader.setOrientation(LinearLayout.VERTICAL);
            cellHeader.setGravity(Gravity.CENTER);
            cellHeader.setPadding(0, dpToPx(4), 0, dpToPx(4));
            cellHeader.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight));

            TextView tvDayName = new TextView(getContext());
            tvDayName.setText(dh.dayName);
            tvDayName.setTextColor(Color.parseColor("#8E8E93"));
            tvDayName.setTextSize(9);
            tvDayName.setGravity(Gravity.CENTER);

            TextView tvDayNum = new TextView(getContext());
            tvDayNum.setText(String.valueOf(dh.dayOfMonth));
            tvDayNum.setTextColor(dh.isToday ? Color.parseColor("#60CDFF") : Color.WHITE);
            tvDayNum.setTextSize(11);
            tvDayNum.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDayNum.setGravity(Gravity.CENTER);

            cellHeader.addView(tvDayName);
            cellHeader.addView(tvDayNum);
            rightHeaderRow.addView(cellHeader);
        }
        layoutMatrixRightGrid.addView(rightHeaderRow);

        // 2. Create rows for each activity
        for (AllActivitiesMatrixData.ActivityRow row : matrix.rows) {
            // Left Column row (Activity Info)
            LinearLayout leftRow = new LinearLayout(getContext());
            leftRow.setOrientation(LinearLayout.HORIZONTAL);
            leftRow.setGravity(Gravity.CENTER_VERTICAL);
            leftRow.setPadding(dpToPx(4), 0, dpToPx(4), 0);
            leftRow.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(80), rowHeight));

            // Activity Name
            TextView tvName = new TextView(getContext());
            tvName.setText(row.activity.getName());
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tvName.setLayoutParams(nameLp);
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvName.setTextColor(Color.parseColor(row.activity.getColorHex()));
            tvName.setTextSize(11);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            leftRow.addView(tvName);

            layoutMatrixLeftColumn.addView(leftRow);

            // Right Column row (Days cells)
            LinearLayout rightRow = new LinearLayout(getContext());
            rightRow.setOrientation(LinearLayout.HORIZONTAL);
            rightRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight));

            int actColor = Color.parseColor(row.activity.getColorHex());

            for (AllActivitiesMatrixData.DayCell cell : row.dayCells) {
                FrameLayout fl = new FrameLayout(getContext());
                fl.setLayoutParams(new LinearLayout.LayoutParams(cellWidth, rowHeight));

                // 1. Double Ring if it's TODAY
                if (cell.isToday) {
                    View ring = new View(getContext());
                    FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(dpToPx(34), dpToPx(34));
                    ringLp.gravity = Gravity.CENTER;
                    ring.setLayoutParams(ringLp);

                    GradientDrawable gdRing = new GradientDrawable();
                    gdRing.setShape(GradientDrawable.OVAL);
                    gdRing.setColor(Color.TRANSPARENT);
                    gdRing.setStroke(dpToPx(1.5f), Color.parseColor("#60CDFF"));
                    ring.setBackground(gdRing);
                    fl.addView(ring);
                }

                // 2. Day dot
                View dotCell = new View(getContext());
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dpToPx(26), dpToPx(26));
                dotLp.gravity = Gravity.CENTER;
                dotCell.setLayoutParams(dotLp);

                GradientDrawable gdDotCell = new GradientDrawable();
                gdDotCell.setShape(GradientDrawable.OVAL);

                // Determine display properties based on status and category
                String displayText = "";
                int bgCircleColor = Color.TRANSPARENT;
                int borderStrokeWidth = 0;
                int borderStrokeColor = Color.TRANSPARENT;
                int textCellColor = Color.WHITE;

                if (cell.status == -1) {
                    // Future day: empty dotted/thin grey ring
                    bgCircleColor = Color.TRANSPARENT;
                    borderStrokeWidth = dpToPx(1.5f);
                    borderStrokeColor = Color.parseColor("#22222A");
                    displayText = "";
                } else if (cell.status == 0) {
                    // Zero tracked
                    if (row.activity.getCategory() == ActivityCategory.DECREASE) {
                        // For DECREASE, 0 tracked is a complete success!
                        bgCircleColor = actColor;
                        displayText = "✓";
                        textCellColor = Color.WHITE;
                    } else {
                        // For INCREASE, 0 is zero progress
                        bgCircleColor = Color.TRANSPARENT;
                        borderStrokeWidth = dpToPx(1.5f);
                        borderStrokeColor = Color.parseColor("#2C2C35");
                        displayText = "-";
                        textCellColor = Color.parseColor("#8E8E93");
                    }
                } else if (cell.status == 2) {
                    // Completed / Met Goal
                    bgCircleColor = actColor;
                    displayText = "✓";
                    textCellColor = Color.WHITE;
                } else if (cell.status == 1) {
                    // Incomplete / Partial / Failed
                    if (row.activity.getCategory() == ActivityCategory.DECREASE) {
                        // For DECREASE, status = 1 means they EXCEEDED the limit (failure)!
                        bgCircleColor = Color.parseColor("#FF453A"); // System red
                        displayText = "✗";
                        textCellColor = Color.WHITE;
                    } else {
                        // For INCREASE, status = 1 means partial progress
                        // Semi-transparent color
                        bgCircleColor = Color.argb(89, Color.red(actColor), Color.green(actColor), Color.blue(actColor));
                        int pct = Math.round(cell.percent * 100);
                        displayText = pct + "%";
                        textCellColor = Color.WHITE;
                    }
                }

                gdDotCell.setColor(bgCircleColor);
                if (borderStrokeWidth > 0) {
                    gdDotCell.setStroke(borderStrokeWidth, borderStrokeColor);
                }
                dotCell.setBackground(gdDotCell);
                fl.addView(dotCell);

                // 3. Add text (✓, ✗, percent, or -) inside the circle
                if (!displayText.isEmpty()) {
                    TextView tvInside = new TextView(getContext());
                    FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(dpToPx(26), dpToPx(26));
                    tvLp.gravity = Gravity.CENTER;
                    tvInside.setLayoutParams(tvLp);
                    tvInside.setGravity(Gravity.CENTER);
                    tvInside.setText(displayText);
                    tvInside.setTextColor(textCellColor);

                    if (displayText.equals("✓") || displayText.equals("✗")) {
                        tvInside.setTextSize(11);
                        tvInside.setTypeface(null, android.graphics.Typeface.BOLD);
                    } else if (displayText.equals("-")) {
                        tvInside.setTextSize(10);
                        tvInside.setTypeface(null, android.graphics.Typeface.NORMAL);
                    } else {
                        // For percentage: e.g. "45%"
                        tvInside.setTextSize(7.5f);
                        tvInside.setTypeface(null, android.graphics.Typeface.BOLD);
                    }
                    fl.addView(tvInside);
                }

                rightRow.addView(fl);
            }
            layoutMatrixRightGrid.addView(rightRow);
        }

        // 3. Scroll to today's day of the month automatically so it centers it
        if (scrollMatrixRightGrid != null && matrix.dayHeaders != null) {
            for (int i = 0; i < matrix.dayHeaders.size(); i++) {
                if (matrix.dayHeaders.get(i).isToday) {
                    final int todayIndex = i;
                    scrollMatrixRightGrid.post(() -> {
                        if (getContext() == null || scrollMatrixRightGrid == null) return;
                        int viewWidth = scrollMatrixRightGrid.getWidth();
                        if (rightHeaderRow.getChildCount() > todayIndex) {
                            View todayHeaderView = rightHeaderRow.getChildAt(todayIndex);
                            if (todayHeaderView != null) {
                                int childLeft = todayHeaderView.getLeft();
                                int childWidth = todayHeaderView.getWidth();
                                int scrollX = childLeft - (viewWidth / 2) + (childWidth / 2);
                                scrollMatrixRightGrid.scrollTo(scrollX, 0);
                            }
                        }
                    });
                    break;
                }
            }
        }
    }

    private int dpToPx(float dp) {
        if (getContext() == null) return Math.round(dp);
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
