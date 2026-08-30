package com.example.ui.settings;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;


import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import com.google.android.material.chip.Chip;
import com.example.util.AppGroupManager;
import com.example.util.AppGroupManager.AppGroup;

import android.os.Bundle;
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
import com.example.data.TrackingRepository;
import com.example.data.entity.Activity;
import com.example.databinding.FragmentSmartTrackingBinding;
import com.example.service.TrackingService;
import com.example.util.SmartTrackingManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartTrackingFragment extends Fragment {

    private FragmentSmartTrackingBinding binding;
    private SmartTrackingManager smartTrackingManager;
    private AppGroupManager appGroupManager;
    private TrackingRepository repository;
    private SmartTrackingAdapter adapter;
    private List<Activity> currentActivitiesList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSmartTrackingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        smartTrackingManager = new SmartTrackingManager(requireContext());
        appGroupManager = new AppGroupManager(requireContext());
        repository = TrackingRepository.getInstance(requireContext());

        preloadApps(requireContext());

        binding.toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToHome();
            }
        });
        
        binding.rvActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SmartTrackingAdapter(requireContext(), currentActivitiesList, smartTrackingManager, new SmartTrackingAdapter.OnItemInteractionListener() {
            @Override
            public void onTimeRangeSelected(Activity activity, int startH, int startM, int endH, int endM) {
                smartTrackingManager.setActivityTimeRange(activity, startH, startM, endH, endM, true);
                triggerServiceUpdate();
            }

            @Override
            public void onTimeToggled(Activity activity, boolean isEnabled) {
                int sh = smartTrackingManager.getActivityStartHour(activity);
                int sm = smartTrackingManager.getActivityStartMinute(activity);
                int eh = smartTrackingManager.getActivityEndHour(activity);
                int em = smartTrackingManager.getActivityEndMinute(activity);
                smartTrackingManager.setActivityTimeRange(activity, sh, sm, eh, em, isEnabled);
                triggerServiceUpdate();
            }

            @Override
            public void onBindAppClicked(Activity activity) {
                showAppPickerDialog(activity);
            }

            @Override
            public void onSetDefaultClicked(Activity activity) {
                boolean isDefault = smartTrackingManager.isDefaultActivity(activity);
                if (isDefault) {
                    smartTrackingManager.clearDefaultActivity();
                    Toast.makeText(requireContext(), getString(com.example.R.string.smart_tracking_default_cleared_toast), Toast.LENGTH_SHORT).show();
                } else {
                    smartTrackingManager.setDefaultActivity(activity.getId(), activity.getName());
                    Toast.makeText(requireContext(), getString(com.example.R.string.smart_tracking_default_set_toast_format, activity.getName()), Toast.LENGTH_SHORT).show();
                }
                adapter.notifyDataSetChanged();
                triggerServiceUpdate();
            }

            @Override
            public void onLockLimitClicked(Activity activity) {
                boolean isLockEnabled = smartTrackingManager.isActivityAppLockEnabled(activity);
                if (isLockEnabled) {
                    smartTrackingManager.setActivityAppLockEnabled(activity, false);
                    Toast.makeText(requireContext(), getString(com.example.R.string.smart_tracking_lock_disabled_toast_format, activity.getName()), Toast.LENGTH_SHORT).show();
                } else {
                    smartTrackingManager.setActivityAppLockEnabled(activity, true);
                    Toast.makeText(requireContext(), getString(com.example.R.string.smart_tracking_lock_enabled_toast_format, activity.getName()), Toast.LENGTH_SHORT).show();
                    if (!SmartTrackingManager.hasOverlayPermission(requireContext())) {
                        openOverlaySettings();
                    }
                }
                adapter.notifyDataSetChanged();
                triggerServiceUpdate();
            }
        });
        binding.rvActivities.setAdapter(adapter);

        repository.getAllActivities().observe(getViewLifecycleOwner(), activities -> {
            if (activities != null) {
                currentActivitiesList.clear();
                currentActivitiesList.addAll(activities);
                adapter.notifyDataSetChanged();
            }
        });

        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSmartTrackingUI();
    }

    private void setupListeners() {
        if (binding.cardManageGroups != null) {
            binding.cardManageGroups.setOnClickListener(v -> showManageGroupsDialog());
        }
        if (binding.switchSmartTracking != null) {
            binding.switchSmartTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
                smartTrackingManager.setEnabled(isChecked);
                updateSmartTrackingUI();

                if (isChecked) {
                    TrackingService.startTracking(requireContext(), "Smart Tracking");
                } else {
                    TrackingService.stopTracking(requireContext());
                }
            });
        }
    }

    private void openOverlaySettings() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + requireContext().getPackageName())
                );
                startActivity(intent);
            }
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(requireContext(), getString(com.example.R.string.smart_tracking_settings_error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateSmartTrackingUI() {
        boolean enabled = smartTrackingManager.isEnabled();
        binding.switchSmartTracking.setChecked(enabled);
        binding.rvActivities.setVisibility(enabled ? View.VISIBLE : View.GONE);
        
        if (enabled && !SmartTrackingManager.hasUsagePermission(requireContext())) {
            binding.tvPermissionWarning.setVisibility(View.VISIBLE);
            binding.tvPermissionWarning.setOnClickListener(v -> SmartTrackingManager.openUsageSettings(requireContext()));
        } else {
            binding.tvPermissionWarning.setVisibility(View.GONE);
        }

        if (enabled && !SmartTrackingManager.hasOverlayPermission(requireContext())) {
            binding.tvOverlayPermissionWarning.setVisibility(View.VISIBLE);
            binding.tvOverlayPermissionWarning.setOnClickListener(v -> SmartTrackingManager.openOverlaySettings(requireContext()));
        } else {
            binding.tvOverlayPermissionWarning.setVisibility(View.GONE);
        }
        
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private interface OnAppsSelectedListener {
        void onSelected(java.util.Set<String> selectedPackages);
    }

    private static List<AppItem> cachedAppItems = null;

    public static void preloadApps(android.content.Context context) {
        if (cachedAppItems != null && !cachedAppItems.isEmpty()) return;
        final android.content.Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                PackageManager pm = appContext.getPackageManager();
                List<AppItem> appItems = new ArrayList<>();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                
                java.util.HashSet<String> addedPackages = new java.util.HashSet<>();
                for (android.content.pm.ResolveInfo resolveInfo : resolveInfos) {
                    android.content.pm.ApplicationInfo packageInfo = resolveInfo.activityInfo.applicationInfo;
                    if (packageInfo.packageName.equals(appContext.getPackageName())) continue;
                    if (addedPackages.contains(packageInfo.packageName)) continue;
                    addedPackages.add(packageInfo.packageName);

                    String appName = resolveInfo.loadLabel(pm).toString();
                    Drawable icon = resolveInfo.loadIcon(pm);
                    appItems.add(new AppItem(appName, packageInfo.packageName, icon));
                }
                Collections.sort(appItems, (a, b) -> a.name.compareToIgnoreCase(b.name));
                cachedAppItems = appItems;
            } catch (Exception e) {
                // ignore
            }
        }).start();
    }

    private void showAppPickerDialog(Activity activity) {
        showAppPicker(getString(com.example.R.string.link_apps_title_format, activity.getName()), 
            new java.util.HashSet<>(smartTrackingManager.getActivityBoundApps(activity)), 
            true, 
            selectedPackages -> {
                smartTrackingManager.setActivityBoundApps(activity, selectedPackages);
                adapter.notifyDataSetChanged();
                triggerServiceUpdate();
            });
    }

    private void showAppPicker(String title, java.util.Set<String> initialSelection, boolean showManageGroups, OnAppsSelectedListener listener) {
        if (cachedAppItems != null && !cachedAppItems.isEmpty()) {
            displayAppPickerDialog(title, cachedAppItems, initialSelection, showManageGroups, listener);
        } else {
            Toast.makeText(requireContext(), getString(com.example.R.string.loading_apps_list), Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                PackageManager pm = requireContext().getPackageManager();
                List<AppItem> appItems = new ArrayList<>();
                
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MAIN, null);
                intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                
                java.util.HashSet<String> addedPackages = new java.util.HashSet<>();
                for (android.content.pm.ResolveInfo resolveInfo : resolveInfos) {
                    android.content.pm.ApplicationInfo packageInfo = resolveInfo.activityInfo.applicationInfo;
                    if (packageInfo.packageName.equals(requireContext().getPackageName())) continue;
                    
                    if (addedPackages.contains(packageInfo.packageName)) continue;
                    addedPackages.add(packageInfo.packageName);

                    String appName = resolveInfo.loadLabel(pm).toString();
                    Drawable icon = resolveInfo.loadIcon(pm);
                    appItems.add(new AppItem(appName, packageInfo.packageName, icon));
                }
                
                Collections.sort(appItems, (a, b) -> a.name.compareToIgnoreCase(b.name));
                cachedAppItems = appItems;

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    displayAppPickerDialog(title, cachedAppItems, initialSelection, showManageGroups, listener);
                });
            }).start();
        }
    }

    private void displayAppPickerDialog(String title, List<AppItem> appItems, java.util.Set<String> initialSelection, boolean showManageGroups, OnAppsSelectedListener listener) {
        java.util.Set<String> selectedPkgs = new java.util.HashSet<>(initialSelection);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(com.example.R.layout.dialog_app_picker, null);
        dialog.setContentView(view);
        
        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal).setPeekHeight(1200);
        }
        
        android.widget.TextView tvTitle = view.findViewById(com.example.R.id.tv_picker_title);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(com.example.R.id.rv_apps);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        android.widget.TextView tvSelectedCount = view.findViewById(com.example.R.id.tv_selected_count);

        List<Chip> chipList = new ArrayList<>();
        List<AppGroup> groupList = new ArrayList<>();

        Runnable refreshUI = () -> {
            if (tvSelectedCount != null) {
                tvSelectedCount.setText(getString(com.example.R.string.selected_count_format, selectedPkgs.size()));
            }
            for (int i = 0; i < chipList.size(); i++) {
                Chip chip = chipList.get(i);
                AppGroup g = groupList.get(i);
                boolean isSelected = !g.packages.isEmpty() && selectedPkgs.containsAll(g.packages);
                if (isSelected) {
                    chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#39D353")));
                    chip.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
                    chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#39D353")));
                } else {
                    chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1B1B1E")));
                    chip.setTextColor(android.graphics.Color.parseColor("#E0E0E0"));
                    chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#333333")));
                    chip.setChipStrokeWidth(1f);
                }
            }
        };

        AppPickerAdapter pickerAdapter = new AppPickerAdapter(appItems, selectedPkgs, refreshUI);
        rv.setAdapter(pickerAdapter);
        
        com.google.android.material.chip.ChipGroup chipGroup = view.findViewById(com.example.R.id.chip_group_quick_select);
        if (chipGroup != null) {
            chipGroup.removeAllViews();
            List<AppGroup> groups = appGroupManager.getAllGroups();
            for (AppGroup g : groups) {
                Chip chip = new Chip(requireContext());
                chip.setText(g.name);
                chip.setCheckable(false);
                chip.setOnClickListener(v -> {
                    boolean isCurrentlySelected = !g.packages.isEmpty() && selectedPkgs.containsAll(g.packages);
                    if (isCurrentlySelected) {
                        selectedPkgs.removeAll(g.packages);
                        Toast.makeText(requireContext(), getString(com.example.R.string.group_removed_toast_format, g.name), Toast.LENGTH_SHORT).show();
                    } else {
                        selectedPkgs.addAll(g.packages);
                        Toast.makeText(requireContext(), getString(com.example.R.string.group_added_toast_format, g.name), Toast.LENGTH_SHORT).show();
                    }
                    refreshUI.run();
                    pickerAdapter.sortAndRefresh();
                });
                chipGroup.addView(chip);
                chipList.add(chip);
                groupList.add(g);
            }
            refreshUI.run();
        }
        
        com.google.android.material.button.MaterialButton btnManage = view.findViewById(com.example.R.id.btn_manage_groups);
        if (btnManage != null) {
            if (showManageGroups) {
                btnManage.setVisibility(View.VISIBLE);
                btnManage.setOnClickListener(v -> {
                    dialog.dismiss();
                    showManageGroupsDialog();
                });
            } else {
                btnManage.setVisibility(View.GONE);
            }
        }

        com.google.android.material.textfield.TextInputEditText etSearch = view.findViewById(com.example.R.id.et_search_app);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    pickerAdapter.filter(s != null ? s.toString() : "");
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
        
        view.findViewById(com.example.R.id.btn_save).setOnClickListener(v -> {
            listener.onSelected(pickerAdapter.getSelectedPackages());
            dialog.dismiss();
            Toast.makeText(requireContext(), getString(com.example.R.string.saved_success), Toast.LENGTH_SHORT).show();
        });
        
        dialog.show();
    }

    private void showManageGroupsDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(com.example.R.layout.dialog_manage_groups, null);
        dialog.setContentView(view);
        
        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal).setPeekHeight(1200);
        }
        
        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(com.example.R.id.rv_groups);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        GroupAdapter adapter = new GroupAdapter(appGroupManager.getAllGroups());
        
        // Edit Apps -> click on item
        adapter.setEditAppsListener(group -> {
            dialog.dismiss();
            showEditGroupAppsDialog(group);
        });
        
        // Edit Name -> click on pencil
        adapter.setEditNameListener(group -> {
            dialog.dismiss();
            showEditGroupNameDialog(group);
        });
        
        // Delete -> click on trash
        adapter.setDeleteListener(group -> {
            new AlertDialog.Builder(requireContext())
                .setTitle(getString(com.example.R.string.delete_group_dialog_title))
                .setMessage(getString(com.example.R.string.delete_group_dialog_message))
                .setPositiveButton(getString(com.example.R.string.delete_button), (d, w) -> {
                    appGroupManager.deleteGroup(group.id);
                    adapter.setGroups(appGroupManager.getAllGroups());
                })
                .setNegativeButton(getString(com.example.R.string.cancel_button), null)
                .show();
        });
        
        rv.setAdapter(adapter);
        
        view.findViewById(com.example.R.id.btn_add_group).setOnClickListener(v -> {
            dialog.dismiss();
            showCreateGroupDialog();
        });
        
        dialog.show();
    }
    
    private void showCreateGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(com.example.R.layout.dialog_edit_group_name, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        com.google.android.material.textfield.TextInputEditText etName = view.findViewById(com.example.R.id.et_group_name);
        
        view.findViewById(com.example.R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(com.example.R.id.btn_save).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            dialog.dismiss();
            
            AppGroup newGroup = new AppGroup(java.util.UUID.randomUUID().toString(), name, new java.util.HashSet<>());
            showEditGroupAppsDialog(newGroup);
        });
        
        dialog.show();
    }
    
    private void showEditGroupNameDialog(AppGroup group) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(com.example.R.layout.dialog_edit_group_name, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        com.google.android.material.textfield.TextInputEditText etName = view.findViewById(com.example.R.id.et_group_name);
        etName.setText(group.name);
        
        view.findViewById(com.example.R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(com.example.R.id.btn_save).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            dialog.dismiss();
            
            group.name = name;
            appGroupManager.saveGroup(group.id, group.name, group.packages);
            showManageGroupsDialog();
        });
        
        dialog.show();
    }
    
    private void showEditGroupAppsDialog(AppGroup group) {
        showAppPicker(getString(com.example.R.string.link_apps_title_format, group.name), group.packages, false, selectedPackages -> {
            appGroupManager.saveGroup(group.id, group.name, selectedPackages);
            showManageGroupsDialog(); // Go back to manage groups
        });
    }

    private class GroupAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {
        private List<AppGroup> groups;
        private java.util.function.Consumer<AppGroup> editAppsListener;
        private java.util.function.Consumer<AppGroup> editNameListener;
        private java.util.function.Consumer<AppGroup> deleteListener;
        
        public GroupAdapter(List<AppGroup> groups) {
            this.groups = groups;
        }
        
        public void setGroups(List<AppGroup> groups) {
            this.groups = groups;
            notifyDataSetChanged();
        }
        
        public void setEditAppsListener(java.util.function.Consumer<AppGroup> editAppsListener) {
            this.editAppsListener = editAppsListener;
        }
        
        public void setEditNameListener(java.util.function.Consumer<AppGroup> editNameListener) {
            this.editNameListener = editNameListener;
        }
        
        public void setDeleteListener(java.util.function.Consumer<AppGroup> deleteListener) {
            this.deleteListener = deleteListener;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(com.example.R.layout.item_group, parent, false);
            return new GroupViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            AppGroup group = groups.get(position);
            holder.tvName.setText(group.name);
            holder.tvCount.setText(String.format(getString(com.example.R.string.apps_count_suffix), group.packages.size()));
            
            holder.itemView.setOnClickListener(v -> {
                if (editAppsListener != null) editAppsListener.accept(group);
            });
            holder.btnEdit.setOnClickListener(v -> {
                if (editNameListener != null) editNameListener.accept(group);
            });
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.accept(group);
            });
        }

        @Override
        public int getItemCount() { return groups.size(); }

        class GroupViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvName, tvCount;
            View btnDelete, btnEdit;
            GroupViewHolder(View v) {
                super(v);
                tvName = v.findViewById(com.example.R.id.tv_group_name);
                tvCount = v.findViewById(com.example.R.id.tv_group_count);
                btnDelete = v.findViewById(com.example.R.id.btn_delete_group);
                btnEdit = v.findViewById(com.example.R.id.btn_edit_group);
            }
        }
    }


    private void triggerServiceUpdate() {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.service.TrackingService.class);
        intent.setAction("UPDATE_SMART_TRACKING");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
    }

    private static class AppItem {
        String name;
        String packageName;
        android.graphics.drawable.Drawable icon;
        AppItem(String name, String packageName, android.graphics.drawable.Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private class AppPickerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<AppPickerAdapter.AppViewHolder> {
        private java.util.List<AppItem> allItems;
        private java.util.List<AppItem> items;
        private java.util.Set<String> selected;
        private Runnable onSelectionChanged;

        private String currentQuery = "";

        AppPickerAdapter(java.util.List<AppItem> items, java.util.Set<String> selected, Runnable onSelectionChanged) {
            this.allItems = new java.util.ArrayList<>(items);
            this.selected = selected;
            this.onSelectionChanged = onSelectionChanged;
            Collections.sort(this.allItems, (a, b) -> {
                boolean aSelected = selected.contains(a.packageName);
                boolean bSelected = selected.contains(b.packageName);
                if (aSelected && !bSelected) return -1;
                if (!aSelected && bSelected) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });
            this.items = new java.util.ArrayList<>(this.allItems);
        }

        public void filter(String query) {
            this.currentQuery = query != null ? query : "";
            items.clear();
            String lowerQuery = currentQuery.toLowerCase().trim();
            for (AppItem item : allItems) {
                if (lowerQuery.isEmpty() || item.name.toLowerCase().contains(lowerQuery)) {
                    items.add(item);
                }
            }
            notifyDataSetChanged();
        }

        public void sortAndRefresh() {
            Collections.sort(allItems, (a, b) -> {
                boolean aSelected = selected.contains(a.packageName);
                boolean bSelected = selected.contains(b.packageName);
                if (aSelected && !bSelected) return -1;
                if (!aSelected && bSelected) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });
            filter(currentQuery);
        }

        @androidx.annotation.NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater().inflate(com.example.R.layout.item_app_selection, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull AppViewHolder holder, int position) {
            AppItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.ivIcon.setImageDrawable(item.icon);
            holder.checkbox.setChecked(selected.contains(item.packageName));
            
            holder.itemView.setOnClickListener(v -> {
                if (selected.contains(item.packageName)) {
                    selected.remove(item.packageName);
                } else {
                    selected.add(item.packageName);
                }
                sortAndRefresh();
                if (onSelectionChanged != null) {
                    onSelectionChanged.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
        
        public java.util.Set<String> getSelectedPackages() {
            return selected;
        }

        class AppViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.ImageView ivIcon;
            android.widget.TextView tvName;
            android.widget.CheckBox checkbox;
            AppViewHolder(android.view.View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(com.example.R.id.iv_app_icon);
                tvName = itemView.findViewById(com.example.R.id.tv_app_name);
                checkbox = itemView.findViewById(com.example.R.id.checkbox_app);
            }
        }
    }
}
