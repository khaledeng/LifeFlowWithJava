package com.example.util;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

public class ActivationDialogHelper {

    public static void showActivationCodeDialog(Context context, Runnable onSuccess) {
        SubscriptionManager subscriptionManager = new SubscriptionManager(context);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_activate_code, null);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextInputEditText etCode = dialogView.findViewById(R.id.et_activation_code);
        TextView tvError = dialogView.findViewById(R.id.tv_activation_error);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btn_submit_code);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_activation);

        btnSubmit.setOnClickListener(v -> {
            HapticHelper.performClick(v);
            tvError.setVisibility(View.GONE);

            String code = etCode.getText() != null ? etCode.getText().toString() : "";
            SubscriptionManager.ActivationResult result = subscriptionManager.validateAndApplyCode(code);

            switch (result) {
                case SUCCESS_MONTHLY:
                    Toast.makeText(context, R.string.msg_activation_success_monthly, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    if (onSuccess != null) onSuccess.run();
                    break;
                case SUCCESS_YEARLY:
                    Toast.makeText(context, R.string.msg_activation_success_yearly, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    if (onSuccess != null) onSuccess.run();
                    break;
                case SUCCESS_LIFETIME:
                    Toast.makeText(context, R.string.msg_activation_success_lifetime, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    if (onSuccess != null) onSuccess.run();
                    break;
                case ALREADY_USED:
                    tvError.setText(R.string.msg_code_already_used);
                    tvError.setVisibility(View.VISIBLE);
                    break;
                case INVALID_CODE:
                default:
                    tvError.setText(R.string.msg_invalid_code);
                    tvError.setVisibility(View.VISIBLE);
                    break;
            }
        });

        btnCancel.setOnClickListener(v -> {
            HapticHelper.performClick(v);
            dialog.dismiss();
        });

        dialog.show();
    }
}
