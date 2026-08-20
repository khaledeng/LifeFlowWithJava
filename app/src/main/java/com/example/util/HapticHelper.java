package com.example.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Utility for refined, tactile haptic feedback across the application.
 */
public final class HapticHelper {

    private HapticHelper() {}

    private static Vibrator getVibrator(Context context) {
        if (context == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    return vibratorManager.getDefaultVibrator();
                }
            }
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Subtle tap feedback for buttons and interactive items.
     */
    public static void performClick(View view) {
        if (view != null) {
            try {
                view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                );
            } catch (Exception ignored) {}
        }
    }

    /**
     * Tactile feedback for switching bottom navigation tabs or segments.
     */
    public static void performTabSwitch(View view) {
        if (view != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(
                            HapticFeedbackConstants.GESTURE_START,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    );
                } else {
                    view.performHapticFeedback(
                            HapticFeedbackConstants.CLOCK_TICK,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    );
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Satisfying, crisp vibration when starting or resuming an activity tracking.
     */
    public static void vibrateStart(Context context) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(25);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Firm, distinctive vibration when stopping an activity or finishing a session.
     */
    public static void vibrateStop(Context context) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(45);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Cheerful double-tap vibration for saving, adding a new activity, or reaching a milestone.
     */
    public static void vibrateSuccess(Context context) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] timings = {0, 20, 50, 30};
                int[] amplitudes = {0, 180, 0, 255};
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                vibrator.vibrate(new long[]{0, 20, 50, 30}, -1);
            }
        } catch (Exception ignored) {}
    }
}
