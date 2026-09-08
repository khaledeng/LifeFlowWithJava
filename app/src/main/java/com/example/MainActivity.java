package com.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.data.TrackingRepository;
import com.example.databinding.ActivityMainBinding;
import com.example.service.TrackingService;
import com.example.ui.activities.ActivitiesFragment;
import com.example.ui.dashboard.DashboardFragment;
import com.example.ui.progress.TrackProgressFragment;
import com.example.ui.settings.SettingsFragment;
import com.example.ui.statistics.StatsFragment;
import com.google.android.material.navigation.NavigationBarView;

/**
 * MainActivity manages the primary bottom navigation and fragment backstack for LifeFlow.
 */
public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private static final int PERMISSION_REQUEST_NOTIFICATION = 101;
    private ActivityMainBinding binding;
    private static final String KEY_CURRENT_NAV_ID = "key_current_nav_id";
    private int currentNavId = R.id.nav_home;
    private TrackingRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            com.example.util.LanguageManager.applySavedLanguage(this);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error applying language", e);
        }
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            repository = TrackingRepository.getInstance(this);
        } catch (Throwable t) {
            android.util.Log.e("MainActivity", "Error initializing repository", t);
        }

        try {
            com.example.util.AutoBackupManager.updateSchedule(this);
        } catch (Throwable t) {
            android.util.Log.e("MainActivity", "Error scheduling backup", t);
        }

        checkPreviousCrashAndNotify();

        binding.bottomNavigation.setOnItemSelectedListener(this);

        // Check and request notification permission for Android 13+
        requestNotificationPermissionIfNeeded();

        // Observe active session to sync with foreground service
        try {
            if (repository != null) {
                final boolean[] isTrackingActive = new boolean[]{false};
                repository.getActiveSession().observe(this, activeSession -> {
                    try {
                        com.example.util.SmartTrackingManager smart = new com.example.util.SmartTrackingManager(this);
                        if (activeSession != null && activeSession.isActive()) {
                            isTrackingActive[0] = true;
                            TrackingService.startTracking(this, activeSession.getActivityName());
                        } else {
                            if (smart.isEnabled()) {
                                isTrackingActive[0] = true;
                                TrackingService.startTracking(this, "Smart Tracking");
                            } else if (isTrackingActive[0]) {
                                isTrackingActive[0] = false;
                                TrackingService.stopTracking(this);
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error in activeSession observer", e);
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error observing active session", e);
        }

        if (savedInstanceState != null) {
            currentNavId = savedInstanceState.getInt(KEY_CURRENT_NAV_ID, R.id.nav_home);
        } else {
            if (getIntent() != null && "PROGRESS".equals(getIntent().getStringExtra("NAVIGATE_TO"))) {
                navigateToProgress();
            } else {
                showFragment(new DashboardFragment(), "FRAGMENT_DASHBOARD");
            }
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "PROGRESS".equals(intent.getStringExtra("NAVIGATE_TO"))) {
            navigateToProgress();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_NOTIFICATION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Continue normally regardless of user grant/deny
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_NAV_ID, currentNavId);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == currentNavId) {
            return true;
        }
        currentNavId = itemId;
        com.example.util.HapticHelper.performTabSwitch(binding.bottomNavigation);

        if (itemId == R.id.nav_home) {
            showFragment(new DashboardFragment(), "FRAGMENT_DASHBOARD");
            return true;
        } else if (itemId == R.id.nav_activities) {
            showFragment(new ActivitiesFragment(), "FRAGMENT_ACTIVITIES");
            return true;
        } else if (itemId == R.id.nav_stats) {
            showFragment(new StatsFragment(), "FRAGMENT_STATS");
            return true;
        } else if (itemId == R.id.nav_progress) {
            showFragment(new TrackProgressFragment(), "FRAGMENT_PROGRESS");
            return true;
        }
        return false;
    }

    public void navigateToStats() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_stats);
    }

    public void navigateToActivities() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_activities);
    }

    public void navigateToProgress() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_progress);
    }

    public void navigateToSettings() {
        currentNavId = -1;
        showFragment(new SettingsFragment(), "FRAGMENT_SETTINGS");
    }

    public void navigateToSmartTracking() {
        currentNavId = -1;
        showFragment(new com.example.ui.settings.SmartTrackingFragment(), "FRAGMENT_SMART_TRACKING");
    }

    public void navigateToHome() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    public void showBurgerMenu() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.ThemeOverlay_LifeFlow_BottomSheetDialog);
        android.view.View sheetView = getLayoutInflater().inflate(R.layout.dialog_burger_menu, null);
        dialog.setContentView(sheetView);

        sheetView.findViewById(R.id.menu_item_tracker).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToHome();
        });

        sheetView.findViewById(R.id.menu_item_stats).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToStats();
        });

        sheetView.findViewById(R.id.menu_item_activities).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToActivities();
        });

        android.view.View menuProgress = sheetView.findViewById(R.id.menu_item_progress);
        if (menuProgress != null) {
            menuProgress.setOnClickListener(v -> {
                dialog.dismiss();
                navigateToProgress();
            });
        }

        android.view.View menuSmartTracking = sheetView.findViewById(R.id.menu_item_smart_tracking);
        if (menuSmartTracking != null) {
            menuSmartTracking.setOnClickListener(v -> {
                dialog.dismiss();
                navigateToSmartTracking();
            });
        }

        sheetView.findViewById(R.id.menu_item_settings).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToSettings();
        });

        android.widget.TextView tvLanguageLabel = sheetView.findViewById(R.id.tv_menu_language_label);
        if (tvLanguageLabel != null) {
            boolean isArabic = com.example.util.LanguageManager.isArabic(this);
            tvLanguageLabel.setText(isArabic ? "En" : "Ar");
        }

        sheetView.findViewById(R.id.menu_item_language).setOnClickListener(v -> {
            dialog.dismiss();
            boolean isArabic = com.example.util.LanguageManager.isArabic(this);
            String newLang = isArabic ? com.example.util.LanguageManager.LANG_ENGLISH : com.example.util.LanguageManager.LANG_ARABIC;
            com.example.util.LanguageManager.setLanguage(this, newLang);
        });

        dialog.show();
    }

    private void checkPreviousCrashAndNotify() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(
                    LifeFlowApplication.CRASH_PREFS, MODE_PRIVATE);
            String lastCrash = prefs.getString(LifeFlowApplication.KEY_LAST_CRASH, null);
            long crashTime = prefs.getLong(LifeFlowApplication.KEY_LAST_CRASH_TIME, 0);

            if (lastCrash != null && !lastCrash.isEmpty()) {
                prefs.edit().remove(LifeFlowApplication.KEY_LAST_CRASH).apply();

                if (System.currentTimeMillis() - crashTime < 300000) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("تم استعادة استقرار التطبيق")
                            .setMessage("تم الكشف عن إغلاق غير متوقع سابقاً وتم تطبيق المعالجة التلقائية لقواعد البيانات والذاكرة بنجاح.")
                            .setPositiveButton("حسناً", (d, w) -> d.dismiss())
                            .show();
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("MainActivity", "Error checking crash log", t);
        }
    }

    private void showFragment(Fragment fragment, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }
}
