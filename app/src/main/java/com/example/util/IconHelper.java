package com.example.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.R;

public class IconHelper {

    public static boolean isEmojiIcon(String iconName) {
        if (iconName == null || iconName.isEmpty()) return false;
        return !iconName.startsWith("ic_");
    }

    public static String extractEmoji(String iconName) {
        if (iconName == null || iconName.isEmpty()) return "⚡";
        if (iconName.startsWith("emoji:")) return iconName.substring(6);
        if (!iconName.startsWith("ic_")) return iconName;
        return "⚡";
    }

    public static Drawable getDrawableForIcon(Context context, String iconName) {
        if (isEmojiIcon(iconName)) {
            String emoji = extractEmoji(iconName);
            return createEmojiDrawable(context, emoji);
        }
        int resId = getDrawableResForIcon(iconName);
        return ContextCompat.getDrawable(context, resId);
    }

    public static Drawable createEmojiDrawable(Context context, String emoji) {
        int size = (int) (36 * context.getResources().getDisplayMetrics().density);
        if (size <= 0) size = 72;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size * 0.65f);
        paint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float y = (size / 2f) - ((fontMetrics.descent + fontMetrics.ascent) / 2f);
        canvas.drawText(emoji, size / 2f, y, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static void setIcon(ImageView imageView, String iconName, int colorTint) {
        if (imageView == null) return;
        if (isEmojiIcon(iconName)) {
            String emoji = extractEmoji(iconName);
            Drawable emojiDrawable = createEmojiDrawable(imageView.getContext(), emoji);
            imageView.setImageDrawable(emojiDrawable);
            imageView.setImageTintList(null);
        } else {
            int resId = getDrawableResForIcon(iconName);
            imageView.setImageResource(resId);
            if (colorTint != 0) {
                imageView.setImageTintList(ColorStateList.valueOf(colorTint));
            }
        }
    }

    public static Bitmap createNotificationLargeIcon(Context context, String iconName, int color) {
        try {
            int size = (int) (48 * context.getResources().getDisplayMetrics().density);
            if (size <= 0) size = 96;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color != 0 ? color : Color.parseColor("#39D353"));
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

            if (isEmojiIcon(iconName)) {
                String emoji = extractEmoji(iconName);
                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(size * 0.5f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float y = (size / 2f) - ((fm.descent + fm.ascent) / 2f);
                canvas.drawText(emoji, size / 2f, y, textPaint);
            } else {
                int iconRes = getDrawableResForIcon(iconName);
                Drawable drawable = ContextCompat.getDrawable(context, iconRes);
                if (drawable != null) {
                    Drawable mutate = drawable.mutate();
                    double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
                    int iconTint = luminance > 0.45 ? Color.BLACK : Color.WHITE;
                    DrawableCompat.setTint(mutate, iconTint);
                    int padding = size / 4;
                    mutate.setBounds(padding, padding, size - padding, size - padding);
                    mutate.draw(canvas);
                }
            }
            return bitmap;
        } catch (Throwable t) {
            android.util.Log.e("IconHelper", "Error creating notification icon", t);
            return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        }
    }

    public static int getDrawableResForIcon(String iconName) {
        if (iconName == null) return R.drawable.ic_other;
        switch (iconName) {
            case "ic_study":
                return R.drawable.ic_study;
            case "ic_work":
                return R.drawable.ic_work;
            case "ic_laptop":
                return R.drawable.ic_laptop;
            case "ic_code":
                return R.drawable.ic_code;
            case "ic_exercise":
                return R.drawable.ic_exercise;
            case "ic_fitness":
                return R.drawable.ic_fitness;
            case "ic_run":
                return R.drawable.ic_run;
            case "ic_bike":
                return R.drawable.ic_bike;
            case "ic_entertainment":
                return R.drawable.ic_entertainment;
            case "ic_sleep":
                return R.drawable.ic_sleep;
            case "ic_reading":
                return R.drawable.ic_reading;
            case "ic_meditation":
                return R.drawable.ic_meditation;
            case "ic_coffee":
                return R.drawable.ic_coffee;
            case "ic_restaurant":
                return R.drawable.ic_restaurant;
            case "ic_home_activity":
                return R.drawable.ic_home_activity;
            case "ic_music":
                return R.drawable.ic_music;
            case "ic_camera":
                return R.drawable.ic_camera;
            case "ic_heart":
                return R.drawable.ic_heart;
            case "ic_task":
                return R.drawable.ic_task;
            case "ic_check_circle":
                return R.drawable.ic_check_circle;
            case "ic_lightbulb":
                return R.drawable.ic_lightbulb;
            case "ic_fire":
                return R.drawable.ic_fire;
            case "ic_star":
                return R.drawable.ic_star;
            case "ic_smile":
                return R.drawable.ic_smile;
            case "ic_schedule":
                return R.drawable.ic_schedule;
            case "ic_growth":
                return R.drawable.ic_growth;
            case "ic_quran":
                return R.drawable.ic_quran;
            case "ic_other":
            default:
                return R.drawable.ic_other;
        }
    }

    public static int parseColorOrDefault(String colorHex, int defaultColor) {
        if (colorHex == null || colorHex.isEmpty()) return defaultColor;
        try {
            return Color.parseColor(colorHex);
        } catch (Exception e) {
            return defaultColor;
        }
    }

    public static void setCircleBackgroundColor(View view, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        view.setBackground(shape);
    }

    public static void setRoundedBackgroundColor(View view, int color, float radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radiusDp * view.getResources().getDisplayMetrics().density);
        shape.setColor(color);
        if (strokeWidthDp > 0) {
            shape.setStroke((int) (strokeWidthDp * view.getResources().getDisplayMetrics().density), strokeColor);
        }
        view.setBackground(shape);
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            try {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                view.clearFocus();
            } catch (Exception ignored) {}
        }
    }

    public static String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        String formatted;
        if (hours > 0) {
            if (minutes > 0) {
                formatted = String.format(java.util.Locale.US, "%dh %02dm", hours, minutes);
            } else {
                formatted = String.format(java.util.Locale.US, "%dh", hours);
            }
        } else if (minutes > 0) {
            formatted = String.format(java.util.Locale.US, "%dm", minutes);
        } else {
            formatted = "0m";
        }
        return "\u200E" + formatted;
    }

    public static String formatDurationWithSeconds(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        String formatted;
        if (hours > 0) {
            formatted = String.format(java.util.Locale.US, "%dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            formatted = String.format(java.util.Locale.US, "%dm %02ds", minutes, seconds);
        } else {
            formatted = String.format(java.util.Locale.US, "%ds", seconds);
        }
        return "\u200E" + formatted;
    }

    public static String formatTimer(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return "\u200E" + String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
