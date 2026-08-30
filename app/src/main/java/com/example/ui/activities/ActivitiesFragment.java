package com.example.ui.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.TrackingRepository;
import com.example.data.entity.Activity;
import com.example.databinding.DialogActivityEditBinding;
import com.example.databinding.FragmentActivitiesBinding;
import com.example.util.IconHelper;
import com.example.util.SubscriptionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * ActivitiesFragment enables creating, editing, and managing activity categories.
 */
public class ActivitiesFragment extends Fragment implements ActivityManageAdapter.OnActivityManageListener {

    private FragmentActivitiesBinding binding;
    private TrackingRepository repository;
    private SubscriptionManager subscriptionManager;
    private ActivityManageAdapter adapter;
    private List<Activity> activityList = new ArrayList<>();

    private static final String[] COLOR_PALETTE = {
            "#39D353", "#6750A4", "#4ECDC4", "#FF6B6B", "#FFD166",
            "#1B9AAA", "#A06CD5", "#FF8C42", "#E63946", "#2EC4B6",
            "#F72585", "#7209B7", "#4361EE", "#4CC9F0", "#06D6A0",
            "#F8961E", "#8338EC", "#00F5D4", "#70E000", "#FF007F",
            "#00E5FF", "#AA00FF", "#FFAB00", "#D50000"
    };

    private static final String[] ICON_CHOICES = {
            "ic_work", "ic_laptop", "ic_code", "ic_study", "ic_reading",
            "ic_exercise", "ic_fitness", "ic_run", "ic_bike",
            "ic_entertainment", "ic_music", "ic_camera", "ic_sleep", "ic_meditation",
            "ic_coffee", "ic_restaurant", "ic_home_activity",
            "ic_task", "ic_check_circle", "ic_lightbulb", "ic_fire",
            "ic_star", "ic_growth", "ic_schedule", "ic_smile", "ic_quran",
            "ic_heart", "ic_other"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentActivitiesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = TrackingRepository.getInstance(requireContext());
        subscriptionManager = new SubscriptionManager(requireContext());

        setupRecyclerView();
        setupObservers();
        setupListeners();
    }

    private void setupRecyclerView() {
        adapter = new ActivityManageAdapter(this);
        binding.rvActivitiesManage.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActivitiesManage.setAdapter(adapter);

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
                // No swipe action
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
                List<Activity> reordered = adapter.getActivities();
                if (reordered != null && !reordered.isEmpty()) {
                    repository.reorderActivities(new ArrayList<>(reordered), null);
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(binding.rvActivitiesManage);
    }

    private void setupObservers() {
        repository.getAllActivities().observe(getViewLifecycleOwner(), activities -> {
            activityList = activities != null ? activities : new ArrayList<>();
            adapter.setActivities(activityList);
        });
    }

    private void setupListeners() {
        binding.btnMenu.setOnClickListener(v -> {
            com.example.util.HapticHelper.performClick(v);
            if (getActivity() instanceof com.example.MainActivity) {
                ((com.example.MainActivity) getActivity()).showBurgerMenu();
            }
        });

        binding.fabAddActivity.setOnClickListener(v -> {
            com.example.util.HapticHelper.performClick(v);
            if (!subscriptionManager.isPro() && activityList.size() >= SubscriptionManager.FREE_TIER_MAX_ACTIVITIES) {
                showProLimitDialog();
            } else {
                showActivityEditDialog(null);
            }
        });
    }

    private void showProLimitDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.free_tier_limit_reached)
                .setMessage(R.string.free_tier_limit_message)
                .setPositiveButton(R.string.btn_enter_activation_code, (dialog, which) -> {
                    com.example.util.ActivationDialogHelper.showActivationCodeDialog(requireContext(), () -> {
                        if (subscriptionManager.isPro()) {
                            showActivityEditDialog(null);
                        }
                    });
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void showActivityEditDialog(@Nullable Activity activityToEdit) {
        DialogActivityEditBinding dialogBinding = DialogActivityEditBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogBinding.getRoot())
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        final String[] selectedColor = {activityToEdit != null ? activityToEdit.getColorHex() : COLOR_PALETTE[0]};
        final String[] selectedIcon = {activityToEdit != null ? activityToEdit.getIconName() : ICON_CHOICES[0]};
        final com.example.data.entity.ActivityCategory[] selectedCategory = {
                activityToEdit != null ? activityToEdit.getCategory() : com.example.data.entity.ActivityCategory.NEUTRAL
        };

        dialogBinding.btnDialogClose.setOnClickListener(v -> dialog.dismiss());

        // Dynamic Preview Helper
        Runnable updatePreview = () -> {
            String currentName = dialogBinding.etActivityName.getText() != null ? dialogBinding.etActivityName.getText().toString().trim() : "";
            if (currentName.isEmpty()) {
                dialogBinding.tvPreviewTitle.setText(getString(R.string.activity_name_preview_default));
                dialogBinding.tvPreviewTitle.setTextColor(Color.parseColor("#80808A"));
            } else {
                dialogBinding.tvPreviewTitle.setText(currentName);
                dialogBinding.tvPreviewTitle.setTextColor(Color.WHITE);
            }

            int color = IconHelper.parseColorOrDefault(selectedColor[0], Color.parseColor("#39D353"));
            IconHelper.setCircleBackgroundColor(dialogBinding.vPreviewIconBg, color);
            IconHelper.setIcon(dialogBinding.ivPreviewIcon, selectedIcon[0], Color.WHITE);
        };

        // Goal Category Selection Helper
        Runnable updateCategoryUI = () -> {
            boolean isIncrease = selectedCategory[0] == com.example.data.entity.ActivityCategory.INCREASE;
            boolean isNormal = selectedCategory[0] == com.example.data.entity.ActivityCategory.NEUTRAL;
            boolean isDecrease = selectedCategory[0] == com.example.data.entity.ActivityCategory.DECREASE;

            dialogBinding.cardGoalIncrease.setBackgroundResource(isIncrease ? R.drawable.bg_goal_card_selected : R.drawable.bg_goal_card_unselected);
            dialogBinding.ivGoalIncreaseIcon.setColorFilter(isIncrease ? Color.parseColor("#3B82F6") : Color.parseColor("#A0A0AA"));
            dialogBinding.tvGoalIncreaseLabel.setTextColor(isIncrease ? Color.WHITE : Color.parseColor("#A0A0AA"));

            dialogBinding.cardGoalNormal.setBackgroundResource(isNormal ? R.drawable.bg_goal_card_selected : R.drawable.bg_goal_card_unselected);
            dialogBinding.ivGoalNormalIcon.setColorFilter(isNormal ? Color.parseColor("#3B82F6") : Color.parseColor("#A0A0AA"));
            dialogBinding.tvGoalNormalLabel.setTextColor(isNormal ? Color.WHITE : Color.parseColor("#A0A0AA"));

            dialogBinding.cardGoalDecrease.setBackgroundResource(isDecrease ? R.drawable.bg_goal_card_selected : R.drawable.bg_goal_card_unselected);
            dialogBinding.ivGoalDecreaseIcon.setColorFilter(isDecrease ? Color.parseColor("#3B82F6") : Color.parseColor("#A0A0AA"));
            dialogBinding.tvGoalDecreaseLabel.setTextColor(isDecrease ? Color.WHITE : Color.parseColor("#A0A0AA"));

            if (isIncrease) {
                dialogBinding.tvGoalTypeDescription.setText(R.string.goal_desc_increase);
                dialogBinding.tvExpectedHoursLabel.setText(R.string.target_hours_label_increase);
            } else if (isDecrease) {
                dialogBinding.tvGoalTypeDescription.setText(R.string.goal_desc_decrease);
                dialogBinding.tvExpectedHoursLabel.setText(R.string.target_hours_label_decrease);
            } else {
                dialogBinding.tvGoalTypeDescription.setText(R.string.goal_desc_normal);
                dialogBinding.tvExpectedHoursLabel.setText(R.string.target_hours_label_normal);
            }
        };

        dialogBinding.cardGoalIncrease.setOnClickListener(v -> {
            selectedCategory[0] = com.example.data.entity.ActivityCategory.INCREASE;
            updateCategoryUI.run();
        });
        dialogBinding.cardGoalNormal.setOnClickListener(v -> {
            selectedCategory[0] = com.example.data.entity.ActivityCategory.NEUTRAL;
            updateCategoryUI.run();
        });
        dialogBinding.cardGoalDecrease.setOnClickListener(v -> {
            selectedCategory[0] = com.example.data.entity.ActivityCategory.DECREASE;
            updateCategoryUI.run();
        });

        if (activityToEdit != null) {
            dialogBinding.tvDialogTitle.setText(R.string.edit_activity);
            dialogBinding.etActivityName.setText(activityToEdit.getName());

            if (activityToEdit.getExpectedHoursPerDay() > 0) {
                dialogBinding.etExpectedHours.setText(String.valueOf(activityToEdit.getExpectedHoursPerDay()));
            }
        } else {
            dialogBinding.tvDialogTitle.setText(R.string.add_activity);
        }

        updateCategoryUI.run();

        dialogBinding.etActivityName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    dialogBinding.tilActivityName.setError(null);
                }
                updatePreview.run();
            }
        });

        // Setup Color Picker
        List<View> colorViews = new ArrayList<>();
        dialogBinding.layoutColorPicker.removeAllViews();
        for (String colorHex : COLOR_PALETTE) {
            int color = Color.parseColor(colorHex);
            FrameLayout dot = new FrameLayout(requireContext());
            int size = (int) (36 * getResources().getDisplayMetrics().density);
            int margin = (int) (5 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);

            boolean isSelected = colorHex.equalsIgnoreCase(selectedColor[0]);
            IconHelper.setCircleBackgroundColor(dot, color);
            if (isSelected) {
                dot.setScaleX(1.15f);
                dot.setScaleY(1.15f);
            }

            dot.setOnClickListener(v -> {
                selectedColor[0] = colorHex;
                for (View other : colorViews) {
                    other.setScaleX(1.0f);
                    other.setScaleY(1.0f);
                }
                dot.setScaleX(1.15f);
                dot.setScaleY(1.15f);
                updatePreview.run();
            });

            colorViews.add(dot);
            dialogBinding.layoutColorPicker.addView(dot);
        }

        // Plus button for custom color
        FrameLayout addColorBtn = new FrameLayout(requireContext());
        int dotSize = (int) (36 * getResources().getDisplayMetrics().density);
        int margin = (int) (5 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        addLp.setMargins(margin, 0, margin, 0);
        addColorBtn.setLayoutParams(addLp);
        IconHelper.setRoundedBackgroundColor(addColorBtn, Color.TRANSPARENT, 18f, Color.parseColor("#3A3A42"), 1);

        TextView plusTv = new TextView(requireContext());
        plusTv.setText("+");
        plusTv.setTextColor(Color.parseColor("#A0A0AA"));
        plusTv.setTextSize(18);
        plusTv.setGravity(android.view.Gravity.CENTER);
        addColorBtn.addView(plusTv);

        addColorBtn.setOnClickListener(v -> {
            boolean visible = dialogBinding.layoutCustomColorRow.getVisibility() == View.VISIBLE;
            dialogBinding.layoutCustomColorRow.setVisibility(visible ? View.GONE : View.VISIBLE);
        });
        dialogBinding.layoutColorPicker.addView(addColorBtn);

        // Setup Icon Picker
        List<FrameLayout> iconBoxes = new ArrayList<>();
        List<ImageView> iconViews = new ArrayList<>();
        dialogBinding.layoutIconPicker.removeAllViews();

        for (String iconName : ICON_CHOICES) {
            FrameLayout iconBox = new FrameLayout(requireContext());
            int size = (int) (40 * getResources().getDisplayMetrics().density);
            int iconMargin = (int) (4 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(iconMargin, 0, iconMargin, 0);
            iconBox.setLayoutParams(lp);

            ImageView iv = new ImageView(requireContext());
            int iconSize = (int) (22 * getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize);
            ivLp.gravity = android.view.Gravity.CENTER;
            iv.setLayoutParams(ivLp);

            boolean isSelected = iconName.equalsIgnoreCase(selectedIcon[0]);
            int activeCol = IconHelper.parseColorOrDefault(selectedColor[0], Color.parseColor("#39D353"));
            int selBg = Color.argb(60, Color.red(activeCol), Color.green(activeCol), Color.blue(activeCol));

            IconHelper.setIcon(iv, iconName, isSelected ? activeCol : Color.parseColor("#A0A0AA"));
            iconBox.addView(iv);

            if (isSelected) {
                IconHelper.setRoundedBackgroundColor(iconBox, selBg, 10f, activeCol, 1);
            } else {
                IconHelper.setRoundedBackgroundColor(iconBox, Color.parseColor("#242428"), 10f, Color.parseColor("#2C2C32"), 0);
            }

            iconBox.setOnClickListener(v -> {
                selectedIcon[0] = iconName;
                if (dialogBinding.etCustomEmoji.getText() != null) {
                    dialogBinding.etCustomEmoji.setText("");
                }
                int currentCol = IconHelper.parseColorOrDefault(selectedColor[0], Color.parseColor("#39D353"));
                int currentSelBg = Color.argb(60, Color.red(currentCol), Color.green(currentCol), Color.blue(currentCol));

                for (int i = 0; i < iconBoxes.size(); i++) {
                    boolean sel = ICON_CHOICES[i].equalsIgnoreCase(selectedIcon[0]);
                    if (sel) {
                        IconHelper.setRoundedBackgroundColor(iconBoxes.get(i), currentSelBg, 10f, currentCol, 1);
                        IconHelper.setIcon(iconViews.get(i), ICON_CHOICES[i], currentCol);
                    } else {
                        IconHelper.setRoundedBackgroundColor(iconBoxes.get(i), Color.parseColor("#242428"), 10f, Color.parseColor("#2C2C32"), 0);
                        IconHelper.setIcon(iconViews.get(i), ICON_CHOICES[i], Color.parseColor("#A0A0AA"));
                    }
                }
                updatePreview.run();
            });

            iconBoxes.add(iconBox);
            iconViews.add(iv);
            dialogBinding.layoutIconPicker.addView(iconBox);
        }

        // Plus button for custom emoji
        FrameLayout addIconBtn = new FrameLayout(requireContext());
        int boxSize = (int) (40 * getResources().getDisplayMetrics().density);
        int iconMargin = (int) (4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams addIconLp = new LinearLayout.LayoutParams(boxSize, boxSize);
        addIconLp.setMargins(iconMargin, 0, iconMargin, 0);
        addIconBtn.setLayoutParams(addIconLp);
        IconHelper.setRoundedBackgroundColor(addIconBtn, Color.TRANSPARENT, 10f, Color.parseColor("#3A3A42"), 1);

        TextView plusIconTv = new TextView(requireContext());
        plusIconTv.setText("+");
        plusIconTv.setTextColor(Color.parseColor("#A0A0AA"));
        plusIconTv.setTextSize(18);
        plusIconTv.setGravity(android.view.Gravity.CENTER);
        addIconBtn.addView(plusIconTv);

        addIconBtn.setOnClickListener(v -> {
            boolean visible = dialogBinding.tilCustomEmoji.getVisibility() == View.VISIBLE;
            dialogBinding.tilCustomEmoji.setVisibility(visible ? View.GONE : View.VISIBLE);
        });
        dialogBinding.layoutIconPicker.addView(addIconBtn);

        // Setup Custom Color Listener & Preview
        int initCol = IconHelper.parseColorOrDefault(selectedColor[0], Color.parseColor("#39D353"));
        IconHelper.setRoundedBackgroundColor(dialogBinding.vColorPreview, initCol, 8f, 0, 0);
        dialogBinding.etCustomColor.setText(selectedColor[0]);

        dialogBinding.etCustomColor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String hex = s != null ? s.toString().trim() : "";
                if (!hex.isEmpty()) {
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    try {
                        int parsed = Color.parseColor(hex);
                        selectedColor[0] = hex;
                        IconHelper.setRoundedBackgroundColor(dialogBinding.vColorPreview, parsed, 8f, 0, 0);
                        for (View other : colorViews) {
                            other.setScaleX(1.0f);
                            other.setScaleY(1.0f);
                        }
                        updatePreview.run();
                    } catch (Exception ignored) {}
                }
            }
        });

        // Setup Custom Emoji Listener
        if (IconHelper.isEmojiIcon(selectedIcon[0])) {
            dialogBinding.etCustomEmoji.setText(IconHelper.extractEmoji(selectedIcon[0]));
        }

        dialogBinding.etCustomEmoji.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String emojiText = s != null ? s.toString().trim() : "";
                if (!emojiText.isEmpty()) {
                    selectedIcon[0] = emojiText;
                    for (int i = 0; i < iconBoxes.size(); i++) {
                        IconHelper.setRoundedBackgroundColor(iconBoxes.get(i), Color.parseColor("#242428"), 10f, Color.parseColor("#2C2C32"), 0);
                    }
                    updatePreview.run();
                }
            }
        });

        updatePreview.run();

        dialog.setOnDismissListener(d -> {
            IconHelper.hideKeyboard(dialogBinding.etActivityName);
        });

        dialogBinding.btnDialogCancel.setOnClickListener(v -> {
            IconHelper.hideKeyboard(dialogBinding.etActivityName);
            dialog.dismiss();
        });

        dialogBinding.btnDialogSave.setOnClickListener(v -> {
            IconHelper.hideKeyboard(dialogBinding.etActivityName);
            dialogBinding.tilActivityName.setError(null);
            dialogBinding.tilExpectedHours.setError(null);

            String name = dialogBinding.etActivityName.getText() != null ? dialogBinding.etActivityName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                dialogBinding.tilActivityName.setError(getString(R.string.activity_name_empty_error));
                return;
            }

            float expectedHours = 0f;
            String hoursStr = dialogBinding.etExpectedHours.getText() != null ? dialogBinding.etExpectedHours.getText().toString().trim() : "";
            if (!hoursStr.isEmpty()) {
                try {
                    expectedHours = Float.parseFloat(hoursStr);
                } catch (NumberFormatException ignored) {}
            }

            // Calculate current total daily goals
            float currentTotalGoals = 0f;
            for (Activity act : activityList) {
                if (activityToEdit == null || act.getId() != activityToEdit.getId()) {
                    currentTotalGoals += act.getExpectedHoursPerDay();
                }
            }

            if (currentTotalGoals + expectedHours > 24f) {
                dialogBinding.tilExpectedHours.setError(getString(R.string.goal_exceeds_24h_error));
                Toast.makeText(requireContext(), getString(R.string.goal_exceeds_24h_error), Toast.LENGTH_LONG).show();
                return;
            }

            if (activityToEdit != null) {
                activityToEdit.setName(name);
                activityToEdit.setCategory(selectedCategory[0]);
                activityToEdit.setExpectedHoursPerDay(expectedHours);
                activityToEdit.setColorHex(selectedColor[0]);
                activityToEdit.setIconName(selectedIcon[0]);
                com.example.util.HapticHelper.vibrateSuccess(getContext());
                repository.updateActivity(activityToEdit, () -> {
                    requireActivity().runOnUiThread(dialog::dismiss);
                });
            } else {
                Activity newActivity = new Activity(
                        name,
                        selectedCategory[0],
                        expectedHours,
                        selectedColor[0],
                        selectedIcon[0],
                        false,
                        System.currentTimeMillis()
                );
                com.example.util.HapticHelper.vibrateSuccess(getContext());
                repository.insertActivity(newActivity, () -> {
                    requireActivity().runOnUiThread(dialog::dismiss);
                });
            }
        });

        dialog.show();
    }

    @Override
    public void onEditActivity(Activity activity) {
        showActivityEditDialog(activity);
    }

    @Override
    public void onDeleteActivity(Activity activity) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_activity)
                .setMessage(getString(R.string.delete_activity_confirm, activity.getName()))
                .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                    com.example.util.HapticHelper.vibrateStop(getContext());
                    repository.deleteActivitySafely(activity, null);
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
