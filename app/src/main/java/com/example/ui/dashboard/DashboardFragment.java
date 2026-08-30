package com.example.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.MainActivity;
import com.example.R;
import com.example.data.TrackingRepository;
import com.example.data.entity.Activity;
import com.example.data.entity.SessionEntity;
import com.example.databinding.FragmentDashboardBinding;
import com.example.util.IconHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DashboardFragment matches the OLED dark Tracker screen:
 * Tracks and displays total accumulated time today per activity, seamlessly continuing
 * timers from previous sessions without resetting to zero.
 */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private TrackingRepository repository;
    private DashboardActivityAdapter adapter;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private SessionEntity currentActiveSession = null;
    private List<Activity> currentActivities = new ArrayList<>();
    private Map<Long, Long> currentTodayClosedDurations = new HashMap<>();
    private long totalClosedTodayMillis = 0;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding != null && currentActiveSession != null && currentActiveSession.isActive()) {
                long startOfDay = getStartOfDayMillis();
                long activeSessionStartToday = Math.max(startOfDay, currentActiveSession.getStartTime());
                long activeElapsedToday = Math.max(0, System.currentTimeMillis() - activeSessionStartToday);

                long activeBaseToday = currentTodayClosedDurations.containsKey(currentActiveSession.getActivityId())
                        ? currentTodayClosedDurations.get(currentActiveSession.getActivityId()) : 0L;
                long activeTotalToday = activeBaseToday + activeElapsedToday;

                adapter.updateActiveTotalToday(currentActiveSession.getActivityId(), activeTotalToday);

                if (currentActivities != null && getContext() != null) {
                    for (Activity act : currentActivities) {
                        if (act.getId() == currentActiveSession.getActivityId()) {
                            com.example.util.ProgressNotificationManager.checkAndNotifyMilestone(requireContext(), act, activeTotalToday);
                            break;
                        }
                    }
                }

                long liveTotalTrackedToday = totalClosedTodayMillis + activeElapsedToday;
                binding.tvTotalTrackedToday.setText(IconHelper.formatTimer(liveTotalTrackedToday));

                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private long getStartOfDayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = TrackingRepository.getInstance(requireContext());

        setupHeaderDate();
        setupRecyclerView();
        setupObservers();
        setupListeners();
    }

    private void setupHeaderDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        binding.tvTodayDate.setText(dateFormat.format(new Date()));
    }

    private void setupRecyclerView() {
        adapter = new DashboardActivityAdapter(new DashboardActivityAdapter.OnActivityActionListener() {
            @Override
            public void onStartClicked(Activity activity) {
                com.example.util.HapticHelper.vibrateStart(getContext());
                repository.startActivity(activity.getId(), null);
            }

            @Override
            public void onStopClicked(Activity activity) {
                com.example.util.HapticHelper.vibrateStop(getContext());
                repository.stopActiveSession(null);
            }
        });

        binding.rvDashboardActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDashboardActivities.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                com.example.util.HapticHelper.performClick(viewHolder.itemView);
                return adapter.onItemMove(fromPos, toPos);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe action needed
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    com.example.util.HapticHelper.performClick(viewHolder.itemView);
                    viewHolder.itemView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                // Persist new order into database
                List<Activity> reordered = adapter.getActivities();
                if (reordered != null && !reordered.isEmpty()) {
                    repository.reorderActivities(new ArrayList<>(reordered), null);
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(binding.rvDashboardActivities);
    }

    private void setupObservers() {
        // Observe all activities
        repository.getAllActivities().observe(getViewLifecycleOwner(), activities -> {
            currentActivities = (activities != null) ? activities : new ArrayList<>();
            long activeId = (currentActiveSession != null && currentActiveSession.isActive()) ? currentActiveSession.getActivityId() : -1;
            adapter.setActivities(currentActivities, activeId, currentTodayClosedDurations);
            updateTimerDisplay();
        });

        // Observe active session
        repository.getActiveSession().observe(getViewLifecycleOwner(), activeSession -> {
            currentActiveSession = activeSession;
            long activeId = (activeSession != null && activeSession.isActive()) ? activeSession.getActivityId() : -1;
            adapter.setActiveActivityId(activeId);

            timerHandler.removeCallbacks(timerRunnable);
            if (activeSession != null && activeSession.isActive()) {
                updateTimerDisplay();
                timerHandler.post(timerRunnable);
            } else {
                updateTimerDisplay();
            }
            refreshTodayDurations();
        });

        // Observe all sessions to keep durations up to date
        repository.getAllSessions().observe(getViewLifecycleOwner(), sessions -> {
            refreshTodayDurations();
        });
    }

    private void updateTimerDisplay() {
        if (binding == null) return;
        if (currentActiveSession != null && currentActiveSession.isActive()) {
            long startOfDay = getStartOfDayMillis();
            long activeSessionStartToday = Math.max(startOfDay, currentActiveSession.getStartTime());
            long activeElapsedToday = Math.max(0, System.currentTimeMillis() - activeSessionStartToday);

            long activeBaseToday = currentTodayClosedDurations.containsKey(currentActiveSession.getActivityId())
                    ? currentTodayClosedDurations.get(currentActiveSession.getActivityId()) : 0L;
            long activeTotalToday = activeBaseToday + activeElapsedToday;

            adapter.updateActiveTotalToday(currentActiveSession.getActivityId(), activeTotalToday);

            long liveTotalTrackedToday = totalClosedTodayMillis + activeElapsedToday;
            binding.tvTotalTrackedToday.setText(IconHelper.formatTimer(liveTotalTrackedToday));
        } else {
            binding.tvTotalTrackedToday.setText(IconHelper.formatTimer(totalClosedTodayMillis));
        }
    }

    private void setupListeners() {
        binding.btnMenu.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showBurgerMenu();
            }
        });

        binding.bannerUsagePermission.setOnClickListener(v -> {
            com.example.util.SmartTrackingManager.openUsageSettings(requireContext());
        });

        binding.bannerOverlayPermission.setOnClickListener(v -> {
            com.example.util.SmartTrackingManager.openOverlaySettings(requireContext());
        });

        binding.bannerNotifPermission.setOnClickListener(v -> {
            com.example.util.SmartTrackingManager.openNotificationSettings(requireContext());
        });
    }

    private void checkAndDisplayPermissionWarnings() {
        if (binding == null || getContext() == null) return;
        boolean hasUsage = com.example.util.SmartTrackingManager.hasUsagePermission(requireContext());
        boolean hasOverlay = com.example.util.SmartTrackingManager.hasOverlayPermission(requireContext());
        boolean hasNotif = com.example.util.SmartTrackingManager.hasNotificationPermission(requireContext());

        boolean anyMissing = (!hasUsage || !hasOverlay || !hasNotif);
        binding.layoutPermissionBanners.setVisibility(anyMissing ? View.VISIBLE : View.GONE);

        binding.bannerUsagePermission.setVisibility(!hasUsage ? View.VISIBLE : View.GONE);
        binding.bannerOverlayPermission.setVisibility(!hasOverlay ? View.VISIBLE : View.GONE);
        binding.bannerNotifPermission.setVisibility(!hasNotif ? View.VISIBLE : View.GONE);
    }

    private void refreshTodayDurations() {
        repository.calculateTodayDurations((closedTrackedMillis, closedDurationsMap) -> {
            if (binding == null) return;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    totalClosedTodayMillis = closedTrackedMillis;
                    currentTodayClosedDurations = closedDurationsMap;
                    adapter.setTodayDurations(closedDurationsMap);
                    updateTimerDisplay();
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        setupHeaderDate();
        checkAndDisplayPermissionWarnings();
        if (currentActiveSession != null && currentActiveSession.isActive()) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        }
        refreshTodayDurations();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacks(timerRunnable);
        binding = null;
    }
}
