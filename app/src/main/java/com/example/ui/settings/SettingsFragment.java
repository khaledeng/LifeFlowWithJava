package com.example.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.R;
import com.example.data.TrackingRepository;
import com.example.databinding.FragmentSettingsBinding;
import com.example.service.TrackingService;
import com.example.util.SubscriptionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SubscriptionManager subscriptionManager;
    private TrackingRepository repository;
    private String pendingExportJson = null;

    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null && pendingExportJson != null) {
                    writeJsonToUri(uri, pendingExportJson);
                }
            });

    private final ActivityResultLauncher<String> importFileLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    readJsonFromUri(uri);
                }
            });

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    readJsonFromUri(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        subscriptionManager = new SubscriptionManager(requireContext());
        repository = TrackingRepository.getInstance(requireContext());

        updateMembershipUI();
        updateNotificationSwitchUI();
        updateLanguageUI();
        setupListeners();
    }

    private void updateLanguageUI() {
        if (binding.tvCurrentLanguage != null) {
            boolean isArabic = com.example.util.LanguageManager.isArabic(requireContext());
            binding.tvCurrentLanguage.setText(isArabic ? "En" : "Ar");
        }
    }

    private void toggleLanguage() {
        boolean isArabic = com.example.util.LanguageManager.isArabic(requireContext());
        String newLang = isArabic ? com.example.util.LanguageManager.LANG_ENGLISH : com.example.util.LanguageManager.LANG_ARABIC;
        com.example.util.LanguageManager.setLanguage(requireContext(), newLang);
    }

    private void updateMembershipUI() {
        binding.tvMembershipStatus.setText(subscriptionManager.getProExpiryDetailsFormatted(requireContext()));
        binding.btnTogglePro.setVisibility(View.GONE);
    }

    private void updateNotificationSwitchUI() {
        binding.switchNotifications.setChecked(subscriptionManager.isNotificationsEnabled());
        binding.switchMotivationalNotifications.setChecked(subscriptionManager.isMotivationalNotificationsEnabled());
        binding.switchAutoBackup.setChecked(subscriptionManager.isAutoBackupEnabled());
    }

    private void setupListeners() {
        binding.btnMenu.setOnClickListener(v -> {
            if (getActivity() instanceof com.example.MainActivity) {
                ((com.example.MainActivity) getActivity()).showBurgerMenu();
            }
        });

        binding.btnTogglePro.setOnClickListener(v -> {
            com.example.util.HapticHelper.performClick(v);
            com.example.util.ActivationDialogHelper.showActivationCodeDialog(requireContext(), this::updateMembershipUI);
        });

        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            subscriptionManager.setNotificationsEnabled(isChecked);
            if (!isChecked) {
                TrackingService.stopTracking(requireContext());
            }
        });

        binding.switchMotivationalNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            subscriptionManager.setMotivationalNotificationsEnabled(isChecked);
        });

        binding.switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            subscriptionManager.setAutoBackupEnabled(isChecked);
            if (getContext() != null) {
                com.example.util.AutoBackupManager.updateSchedule(requireContext());
            }
            if (isChecked) {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), R.string.auto_backup_enabled_toast, Toast.LENGTH_SHORT).show();
                    Context appContext = requireContext().getApplicationContext();
                    new Thread(() -> {
                        com.example.util.AutoBackupManager.performBackupSync(appContext);
                    }).start();
                }
            } else {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), R.string.auto_backup_disabled_toast, Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnRestoreAutoBackup.setOnClickListener(v -> showAutoBackupsDialog());

        binding.btnBackupNow.setOnClickListener(v -> {
            com.example.util.HapticHelper.performClick(v);
            if (getContext() == null) return;
            Context appContext = requireContext().getApplicationContext();
            new Thread(() -> {
                boolean success = com.example.util.AutoBackupManager.performBackupSync(appContext);
                if (getActivity() != null && !getActivity().isFinishing()) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() != null) {
                            if (success) {
                                Toast.makeText(getContext(), R.string.auto_backup_now_success, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), R.string.export_failed_toast, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }).start();
        });

        binding.cardLanguage.setOnClickListener(v -> toggleLanguage());

        binding.btnExportJson.setOnClickListener(v -> handleExportJson());

        binding.btnImportJson.setOnClickListener(v -> {
            try {
                openDocumentLauncher.launch(new String[]{"application/json", "application/octet-stream", "*/*"});
            } catch (Exception e1) {
                try {
                    importFileLauncher.launch("application/json");
                } catch (Exception e2) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "No file picker app found", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void handleExportJson() {
        repository.exportDataToJson((json, error) -> {
            if (getActivity() == null || getActivity().isFinishing() || getContext() == null) return;
            getActivity().runOnUiThread(() -> {
                if (getContext() == null) return;
                if (json == null) {
                    Toast.makeText(getContext(), R.string.export_failed_toast, Toast.LENGTH_SHORT).show();
                    return;
                }
                pendingExportJson = json;

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US);
                String fileName = "LifeFlowBackup_" + sdf.format(new Date()) + ".json";

                try {
                    createDocumentLauncher.launch(fileName);
                } catch (Exception e) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/json");
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "LifeFlow Backup JSON");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, json);
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.save_or_share_backup)));
                }
            });
        });
    }

    private void writeJsonToUri(Uri uri, String json) {
        if (getContext() == null) return;
        try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.export_success_toast, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.export_failed_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void readJsonFromUri(Uri uri) {
        if (getContext() == null) return;
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            parseAndImportJson(stringBuilder.toString());
        } catch (Exception e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.import_failed_toast, "Error reading file"), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void parseAndImportJson(String jsonString) {
        if (getContext() == null || !isAdded()) return;
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONObject metadata = root.optJSONObject("metadata");
            String dateInfo = metadata != null ? metadata.optString("exported_at", "Unknown") : "Unknown";

            JSONArray acts = root.optJSONArray("activities");
            JSONArray sess = root.optJSONArray("sessions");

            int actCount = acts != null ? acts.length() : 0;
            int sessCount = sess != null ? sess.length() : 0;

            String actStr = getResources().getQuantityString(R.plurals.plural_activities, actCount, actCount);
            String sessStr = getResources().getQuantityString(R.plurals.plural_sessions, sessCount, sessCount);
            String message = getString(R.string.backup_meta_prompt, dateInfo, actStr, sessStr);

            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.import_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.import_merge_btn, (dialog, which) -> {
                        executeImport(jsonString, false);
                    })
                    .setNeutralButton(R.string.import_replace_btn, (dialog, which) -> {
                        if (getContext() == null) return;
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.confirm_full_replacement_title)
                                .setMessage(R.string.confirm_full_replacement_msg)
                                .setPositiveButton(R.string.proceed_replace_button, (d2, w2) -> executeImport(jsonString, true))
                                .setNegativeButton(R.string.cancel_button, null)
                                .show();
                    })
                    .setNegativeButton(R.string.cancel_button, null)
                    .show();
        } catch (Exception e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.import_failed_toast, "Invalid JSON structure: " + e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showAutoBackupsDialog() {
        if (getContext() == null) return;
        java.io.File[] backups = com.example.util.AutoBackupManager.getAvailableAutoBackups(requireContext());
        if (backups == null || backups.length == 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.auto_backup_history_dialog_title)
                    .setMessage(R.string.auto_backup_no_backups)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        String[] itemLabels = new String[backups.length];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault());
        for (int i = 0; i < backups.length; i++) {
            long lastMod = backups[i].lastModified();
            long sizeKb = backups[i].length() / 1024;
            String name = backups[i].getName();
            if (name.equals("lifeflow_cumulative_auto_backup.json")) {
                itemLabels[i] = "★ النسخة التراكمية الشاملة - " + sdf.format(new Date(lastMod));
            } else {
                itemLabels[i] = sdf.format(new Date(lastMod)) + " (" + (sizeKb > 0 ? sizeKb + " KB" : backups[i].length() + " B") + ")";
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auto_backup_history_dialog_title)
                .setItems(itemLabels, (dialog, which) -> {
                    java.io.File selectedFile = backups[which];
                    try {
                        java.io.FileInputStream fis = new java.io.FileInputStream(selectedFile);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        fis.close();
                        parseAndImportJson(sb.toString());
                    } catch (Exception e) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), getString(R.string.import_failed_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void executeImport(String jsonString, boolean replaceExisting) {
        repository.importDataFromJson(jsonString, replaceExisting, (actCount, sessCount, error) -> {
            if (getActivity() == null || getActivity().isFinishing() || getContext() == null) return;
            getActivity().runOnUiThread(() -> {
                if (getContext() == null || !isAdded()) return;
                if (error != null) {
                    Toast.makeText(getContext(), getString(R.string.import_failed_toast, error), Toast.LENGTH_LONG).show();
                } else {
                    String actStr = getResources().getQuantityString(R.plurals.plural_activities, actCount, actCount);
                    String sessStr = getResources().getQuantityString(R.plurals.plural_sessions, sessCount, sessCount);
                    Toast.makeText(getContext(), getString(R.string.import_success_template, actStr, sessStr), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
