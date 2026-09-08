package com.example.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.example.R;
import com.example.data.entity.ActivityCategory;
import com.example.data.model.AllActivitiesMatrixData;

import java.util.Locale;

/**
 * High-performance, crisp Canvas renderer for the Month Achievement Matrix Widget.
 * Supports interactive day windowing (e.g. Days 1-7, 8-14, 1-14, or Full Month)
 * and multi-month navigation.
 */
public class MatrixBitmapRenderer {

    public static Bitmap renderMatrix(Context context, AllActivitiesMatrixData matrix,
                                       int targetWidth, int targetHeight, boolean is14DayMode) {
        int totalDays = (matrix != null && matrix.daysInMonth > 0) ? matrix.daysInMonth : 30;
        int todayDay = -1;
        if (matrix != null && matrix.dayHeaders != null) {
            for (int i = 0; i < matrix.dayHeaders.size(); i++) {
                if (matrix.dayHeaders.get(i).isToday) {
                    todayDay = matrix.dayHeaders.get(i).dayOfMonth;
                    break;
                }
            }
        }
        if (todayDay <= 0) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            todayDay = cal.get(java.util.Calendar.DAY_OF_MONTH);
        }
        todayDay = Math.max(1, Math.min(todayDay, totalDays));

        int maxAllowedDay = Math.min(totalDays, todayDay + 1);
        int endDay = maxAllowedDay;
        int startDay = is14DayMode ? Math.max(1, endDay - 13) : 1;

        return renderMatrix(context, matrix, targetWidth, targetHeight, startDay, endDay, true);
    }

    public static Bitmap renderMatrix(Context context, AllActivitiesMatrixData matrix,
                                       int targetWidth, int targetHeight,
                                       int startDay, int endDay, boolean isCurrentMonth) {
        return renderMatrix(context, matrix, targetWidth, targetHeight, startDay, endDay, isCurrentMonth, -1);
    }

    public static Bitmap renderMatrix(Context context, AllActivitiesMatrixData matrix,
                                       int targetWidth, int targetHeight,
                                       int startDay, int endDay, boolean isCurrentMonth,
                                       int selectedRowIndex) {
        int width = Math.max(targetWidth, 120);
        int height = Math.max(targetHeight, 60);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        boolean isAr = Locale.getDefault().getLanguage().equals("ar");

        // Solid clean dark background matching card
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#14141E"));
        canvas.drawRect(0, 0, width, height, bgPaint);

        if (matrix == null || matrix.rows == null || matrix.rows.isEmpty()) {
            drawEmptyState(context, canvas, width, height);
            return bitmap;
        }

        // 1. Determine Day Bounds
        int totalDays = (matrix.daysInMonth > 0) ? matrix.daysInMonth : 30;
        int todayDay = -999;
        if (isCurrentMonth) {
            if (matrix.dayHeaders != null) {
                for (int i = 0; i < matrix.dayHeaders.size(); i++) {
                    if (matrix.dayHeaders.get(i).isToday) {
                        todayDay = matrix.dayHeaders.get(i).dayOfMonth;
                        break;
                    }
                }
            }
            if (todayDay <= 0) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                todayDay = cal.get(java.util.Calendar.DAY_OF_MONTH);
            }
            todayDay = Math.max(1, Math.min(todayDay, totalDays));
        }

        startDay = Math.max(1, Math.min(startDay, totalDays));
        endDay = Math.max(startDay, Math.min(endDay, totalDays));
        int displayDaysCount = Math.max(1, endDay - startDay + 1);
        boolean isFocusView = (displayDaysCount <= 8);

        // 2. Responsive Metrics Calculation
        float paddingLeft = Math.max(4f, Math.min(10f, width * 0.025f));
        float paddingRight = paddingLeft;
        float paddingTop = Math.max(2f, Math.min(6f, height * 0.02f));
        float paddingBottom = Math.max(2f, Math.min(6f, height * 0.02f));

        int numRows = matrix.rows.size();

        // Responsive header height based on canvas height
        float headerHeight = Math.max(18f, Math.min(34f, height * 0.16f));

        // Legend: only show if height is comfortable and rows <= 5
        boolean showLegend = (height >= 260 && numRows <= 5 && width >= 300);
        float legendHeight = showLegend ? Math.min(20f, height * 0.07f) : 0f;

        float availableHeight = height - paddingTop - paddingBottom - headerHeight - legendHeight;
        float rowHeight = availableHeight / Math.max(1, numRows);

        // Responsive column widths
        float nameRatio = (width < 280f) ? 0.24f : 0.28f;
        float nameColWidth = Math.min(width * nameRatio, 160f);
        nameColWidth = Math.max(38f, nameColWidth);

        float gridWidth = width - paddingLeft - paddingRight - nameColWidth - 8f;
        float gridStartX = isAr ? paddingLeft : (paddingLeft + nameColWidth + 8f);
        float cellWidth = gridWidth / displayDaysCount;

        // Shared Paints
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect bounds = new Rect();

        // 3. Draw Day Headers
        drawDayHeaders(canvas, matrix, startDay, endDay, todayDay, gridStartX, cellWidth,
                paddingTop, headerHeight, isAr, isFocusView, textPaint, shapePaint, bounds);

        // 4. Draw Activity Rows
        float currentY = paddingTop + headerHeight;
        for (int r = 0; r < numRows; r++) {
            AllActivitiesMatrixData.ActivityRow row = matrix.rows.get(r);
            boolean isSelected = (r == selectedRowIndex);
            drawActivityRow(canvas, row, currentY, rowHeight, width, paddingLeft, paddingRight,
                    nameColWidth, gridStartX, cellWidth, startDay, endDay, todayDay,
                    isAr, isFocusView, isSelected, textPaint, shapePaint, bounds);
            currentY += rowHeight;
        }

        // 5. Draw Legend (if enough space)
        if (showLegend) {
            float legendY = height - paddingBottom - (legendHeight / 2f);
            drawLegend(context, canvas, width, legendY, isCurrentMonth, textPaint, shapePaint, bounds);
        }

        return bitmap;
    }

    private static void drawDayHeaders(Canvas canvas, AllActivitiesMatrixData matrix,
                                       int startDay, int endDay, int todayDay,
                                       float gridStartX, float cellWidth,
                                       float top, float height, boolean isAr, boolean isFocusView,
                                       TextPaint textPaint, Paint shapePaint, Rect bounds) {
        boolean showDayName = (height >= 24f && cellWidth >= 22f);
        float dayNameSize = Math.max(7.5f, Math.min(12f, height * 0.35f));
        float dayNumberSize = Math.max(9f, Math.min(15f, showDayName ? height * 0.42f : height * 0.65f));

        for (int day = startDay; day <= endDay; day++) {
            int colIndex = isAr ? (endDay - day) : (day - startDay);
            float cx = gridStartX + colIndex * cellWidth + cellWidth / 2f;
            boolean isToday = (day == todayDay);

            AllActivitiesMatrixData.DayHeader header = null;
            if (matrix.dayHeaders != null && day <= matrix.dayHeaders.size()) {
                header = matrix.dayHeaders.get(day - 1);
            }

            // Clean Arabic day name (strip "الـ" prefix if present)
            String dayName = "";
            if (header != null && header.dayName != null) {
                dayName = header.dayName.trim();
                if (dayName.startsWith("ال")) {
                    dayName = dayName.substring(2);
                }
            }

            if (isToday) {
                // Today: bright cyan filled badge
                float badgeRadius = Math.min(cellWidth * 0.38f, height * (showDayName ? 0.36f : 0.44f));
                badgeRadius = Math.max(6f, Math.min(14f, badgeRadius));
                float badgeCenterY = showDayName ? (top + height - badgeRadius - 2f) : (top + height / 2f);

                shapePaint.setStyle(Paint.Style.FILL);
                shapePaint.setColor(Color.parseColor("#60CDFF"));
                canvas.drawCircle(cx, badgeCenterY, badgeRadius, shapePaint);

                // Day number inside badge in dark color
                textPaint.setColor(Color.parseColor("#0A0A14"));
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                textPaint.setTextSize(Math.min(dayNumberSize, badgeRadius * 1.35f));
                textPaint.setTextAlign(Paint.Align.CENTER);
                String dayStr = String.valueOf(day);
                textPaint.getTextBounds(dayStr, 0, dayStr.length(), bounds);
                canvas.drawText(dayStr, cx, badgeCenterY + bounds.height() / 2f, textPaint);

                // Day name above badge in cyan
                if (showDayName && !dayName.isEmpty()) {
                    textPaint.setColor(Color.parseColor("#60CDFF"));
                    textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    textPaint.setTextSize(dayNameSize);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(dayName, cx, top + dayNameSize + 1f, textPaint);
                }
            } else {
                if (showDayName && !dayName.isEmpty()) {
                    textPaint.setColor(Color.parseColor("#8E8E9F"));
                    textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                    textPaint.setTextSize(dayNameSize);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(dayName, cx, top + dayNameSize + 1f, textPaint);
                }

                textPaint.setColor(Color.parseColor("#E0E0EC"));
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                textPaint.setTextSize(dayNumberSize);
                textPaint.setTextAlign(Paint.Align.CENTER);
                String dayStr = String.valueOf(day);
                textPaint.getTextBounds(dayStr, 0, dayStr.length(), bounds);
                float numberY = showDayName ? (top + height - 3f) : (top + height / 2f + bounds.height() / 2f);
                canvas.drawText(dayStr, cx, numberY, textPaint);
            }
        }
    }

    private static void drawActivityRow(Canvas canvas, AllActivitiesMatrixData.ActivityRow row,
                                        float top, float rowHeight, float totalWidth,
                                        float paddingLeft, float paddingRight, float nameColWidth,
                                        float gridStartX, float cellWidth,
                                        int startDay, int endDay, int todayDay,
                                        boolean isAr, boolean isFocusView, boolean isSelected,
                                        TextPaint textPaint, Paint shapePaint, Rect bounds) {
        float centerY = top + rowHeight / 2f;
        int actColor;
        try {
            actColor = Color.parseColor(row.activity.getColorHex());
        } catch (Exception e) {
            actColor = Color.parseColor("#39D353");
        }

        // 0. Selected Row Highlight (Glowing outline and subtle tinted fill)
        if (isSelected) {
            float bgPaddingX = Math.max(2f, paddingLeft * 0.4f);
            android.graphics.RectF rowBg = new android.graphics.RectF(
                    bgPaddingX,
                    top + 1f,
                    totalWidth - bgPaddingX,
                    top + rowHeight - 1f
            );
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(Color.argb(45, Color.red(actColor), Color.green(actColor), Color.blue(actColor)));
            canvas.drawRoundRect(rowBg, 8f, 8f, shapePaint);

            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setColor(actColor);
            shapePaint.setStrokeWidth(1.8f);
            canvas.drawRoundRect(rowBg, 8f, 8f, shapePaint);
        }

        // 1. Activity Name & Color Dot
        float dotRadius = Math.max(2f, Math.min(6f, rowHeight * 0.15f));
        float fontSize = Math.max(8.5f, Math.min(17f, rowHeight * 0.36f));

        if (isAr) {
            float dotX = totalWidth - paddingRight - dotRadius;
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(actColor);
            canvas.drawCircle(dotX, centerY, dotRadius, shapePaint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(fontSize);
            textPaint.setTextAlign(Paint.Align.RIGHT);

            float maxTextWidth = Math.max(16f, nameColWidth - (dotRadius * 2 + 8f));
            String name = TextUtils.ellipsize(row.activity.getName(), textPaint, maxTextWidth, TextUtils.TruncateAt.END).toString();
            textPaint.getTextBounds(name, 0, name.length(), bounds);
            canvas.drawText(name, dotX - dotRadius - 4f, centerY + bounds.height() / 2f, textPaint);
        } else {
            float dotX = paddingLeft + dotRadius;
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(actColor);
            canvas.drawCircle(dotX, centerY, dotRadius, shapePaint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(fontSize);
            textPaint.setTextAlign(Paint.Align.LEFT);

            float maxTextWidth = Math.max(16f, nameColWidth - (dotRadius * 2 + 8f));
            String name = TextUtils.ellipsize(row.activity.getName(), textPaint, maxTextWidth, TextUtils.TruncateAt.END).toString();
            textPaint.getTextBounds(name, 0, name.length(), bounds);
            canvas.drawText(name, dotX + dotRadius + 4f, centerY + bounds.height() / 2f, textPaint);
        }

        // 2. Day Cells
        float cellRadius = Math.min(cellWidth * 0.38f, rowHeight * 0.38f);
        cellRadius = Math.max(4.5f, Math.min(22f, cellRadius));

        for (int day = startDay; day <= endDay; day++) {
            int colIndex = isAr ? (endDay - day) : (day - startDay);
            float cx = gridStartX + colIndex * cellWidth + cellWidth / 2f;
            boolean isToday = (day == todayDay);

            AllActivitiesMatrixData.DayCell cell = (row.dayCells != null && day <= row.dayCells.size()) ?
                    row.dayCells.get(day - 1) : null;

            if (cell == null) continue;

            // Highlight ring for Today / Selected state
            if (isToday) {
                if (isSelected) {
                    shapePaint.setStyle(Paint.Style.STROKE);
                    shapePaint.setColor(Color.parseColor("#FFD60A")); // Bright gold selection ring around today
                    shapePaint.setStrokeWidth(Math.max(2.0f, Math.min(3.2f, cellRadius * 0.22f)));
                    canvas.drawCircle(cx, centerY, cellRadius + Math.max(3.2f, cellRadius * 0.28f), shapePaint);
                } else {
                    shapePaint.setStyle(Paint.Style.STROKE);
                    shapePaint.setColor(Color.parseColor("#60CDFF"));
                    shapePaint.setStrokeWidth(Math.max(1.5f, Math.min(2.8f, cellRadius * 0.18f)));
                    canvas.drawCircle(cx, centerY, cellRadius + Math.max(1.8f, cellRadius * 0.18f), shapePaint);
                }
            }

            if (cell.status == 3 || cell.isPaused) {
                // Paused / Rest Day (Filled with activity color, white pause symbol)
                shapePaint.setStyle(Paint.Style.FILL);
                shapePaint.setColor(actColor);
                canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                if (cellRadius >= 6.5f) {
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    textPaint.setTextSize(cellRadius * 1.05f);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.getTextBounds("⏸", 0, 1, bounds);
                    canvas.drawText("⏸", cx, centerY + bounds.height() / 2f, textPaint);
                }
            } else if (cell.status == -1) {
                // Future day (only 1 day ahead allowed): sleek clean outline ring
                shapePaint.setStyle(Paint.Style.STROKE);
                shapePaint.setColor(Color.parseColor("#3C3C54"));
                shapePaint.setStrokeWidth(Math.max(1.0f, Math.min(2.0f, cellRadius * 0.15f)));
                canvas.drawCircle(cx, centerY, cellRadius, shapePaint);
            } else if (cell.status == 2) {
                // Completed (Met goal)
                shapePaint.setStyle(Paint.Style.FILL);
                shapePaint.setColor(actColor);
                canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                // White checkmark
                if (cellRadius >= 6.5f) {
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    textPaint.setTextSize(cellRadius * 1.25f);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.getTextBounds("✓", 0, 1, bounds);
                    canvas.drawText("✓", cx, centerY + bounds.height() / 2f, textPaint);
                }
            } else if (cell.status == 1) {
                // Partial or Exceeded
                if (row.activity.getCategory() == ActivityCategory.DECREASE) {
                    // Exceeded limit (Red ✗)
                    shapePaint.setStyle(Paint.Style.FILL);
                    shapePaint.setColor(Color.parseColor("#FF453A"));
                    canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                    if (cellRadius >= 6.5f) {
                        textPaint.setColor(Color.WHITE);
                        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        textPaint.setTextSize(cellRadius * 1.2f);
                        textPaint.setTextAlign(Paint.Align.CENTER);
                        textPaint.getTextBounds("✗", 0, 1, bounds);
                        canvas.drawText("✗", cx, centerY + bounds.height() / 2f, textPaint);
                    }
                } else {
                    // Partial progress (Alpha actColor fill + solid stroke)
                    shapePaint.setStyle(Paint.Style.FILL);
                    int rColor = Color.red(actColor);
                    int gColor = Color.green(actColor);
                    int bColor = Color.blue(actColor);
                    shapePaint.setColor(Color.argb(130, rColor, gColor, bColor));
                    canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                    shapePaint.setStyle(Paint.Style.STROKE);
                    shapePaint.setColor(actColor);
                    shapePaint.setStrokeWidth(Math.max(1.2f, Math.min(2.2f, cellRadius * 0.16f)));
                    canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                    if (cellRadius >= 13f) {
                        int pct = Math.round(cell.percent * 100);
                        String pctStr = pct + "%";
                        textPaint.setColor(Color.WHITE);
                        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        textPaint.setTextSize(cellRadius * 0.72f);
                        textPaint.setTextAlign(Paint.Align.CENTER);
                        textPaint.getTextBounds(pctStr, 0, pctStr.length(), bounds);
                        canvas.drawText(pctStr, cx, centerY + bounds.height() / 2f, textPaint);
                    }
                }
            } else { // cell.status == 0 (Zero tracked)
                if (row.activity.getCategory() == ActivityCategory.DECREASE) {
                    // 0 tracked for decrease is success!
                    shapePaint.setStyle(Paint.Style.FILL);
                    shapePaint.setColor(actColor);
                    canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                    if (cellRadius >= 6.5f) {
                        textPaint.setColor(Color.WHITE);
                        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        textPaint.setTextSize(cellRadius * 1.25f);
                        textPaint.setTextAlign(Paint.Align.CENTER);
                        textPaint.getTextBounds("✓", 0, 1, bounds);
                        canvas.drawText("✓", cx, centerY + bounds.height() / 2f, textPaint);
                    }
                } else {
                    // Zero progress: clean dark circle with dash
                    shapePaint.setStyle(Paint.Style.FILL);
                    shapePaint.setColor(Color.parseColor("#1B1B26"));
                    canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                    shapePaint.setStyle(Paint.Style.STROKE);
                    shapePaint.setColor(Color.parseColor("#2A2A38"));
                    shapePaint.setStrokeWidth(Math.max(1.2f, Math.min(1.8f, cellRadius * 0.15f)));
                    canvas.drawCircle(cx, centerY, cellRadius, shapePaint);

                    if (cellRadius >= 6.5f) {
                        textPaint.setColor(Color.parseColor("#7A7A8A"));
                        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                        textPaint.setTextSize(cellRadius * 1.1f);
                        textPaint.setTextAlign(Paint.Align.CENTER);
                        textPaint.getTextBounds("-", 0, 1, bounds);
                        canvas.drawText("-", cx, centerY + bounds.height() / 2f, textPaint);
                    }
                }
            }
        }
    }

    private static void drawLegend(Context context, Canvas canvas, float totalWidth, float legendY,
                                   boolean isCurrentMonth, TextPaint textPaint, Paint shapePaint, Rect bounds) {
        String sCompleted = context.getString(R.string.widget_legend_completed);
        String sPartial = context.getString(R.string.widget_legend_partial);
        String sExceeded = context.getString(R.string.widget_legend_exceeded);
        String sToday = context.getString(R.string.widget_legend_today);

        String[] labels = isCurrentMonth ?
                new String[]{sCompleted, sPartial, sExceeded, sToday} :
                new String[]{sCompleted, sPartial, sExceeded};

        int[] colors = isCurrentMonth ?
                new int[]{
                        Color.parseColor("#39D353"), // Completed
                        Color.parseColor("#FFA726"), // Partial
                        Color.parseColor("#FF453A"), // Exceeded
                        Color.parseColor("#60CDFF")  // Today
                } :
                new int[]{
                        Color.parseColor("#39D353"), // Completed
                        Color.parseColor("#FFA726"), // Partial
                        Color.parseColor("#FF453A")  // Exceeded
                };

        textPaint.setTextSize(13f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        float dotRadius = 4.5f;
        float itemSpacing = 24f;
        float totalLegendWidth = 0f;
        float[] itemWidths = new float[labels.length];

        for (int i = 0; i < labels.length; i++) {
            float textWidth = textPaint.measureText(labels[i]);
            itemWidths[i] = (dotRadius * 2) + 8f + textWidth;
            totalLegendWidth += itemWidths[i];
            if (i > 0) totalLegendWidth += itemSpacing;
        }

        float startX = (totalWidth - totalLegendWidth) / 2f;
        float currentX = startX;

        for (int i = 0; i < labels.length; i++) {
            shapePaint.setColor(colors[i]);
            if (isCurrentMonth && i == 3) {
                shapePaint.setStyle(Paint.Style.STROKE);
                shapePaint.setStrokeWidth(2f);
                canvas.drawCircle(currentX + dotRadius, legendY, dotRadius, shapePaint);
            } else {
                shapePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(currentX + dotRadius, legendY, dotRadius, shapePaint);
            }

            textPaint.setColor(Color.parseColor("#8E8E98"));
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.getTextBounds(labels[i], 0, labels[i].length(), bounds);
            canvas.drawText(labels[i], currentX + (dotRadius * 2) + 8f, legendY + bounds.height() / 2f, textPaint);

            currentX += itemWidths[i] + itemSpacing;
        }
    }

    private static void drawEmptyState(Context context, Canvas canvas, int width, int height) {
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Rect bounds = new Rect();

        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(22f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        String title = context.getString(R.string.widget_month_matrix_title);
        textPaint.getTextBounds(title, 0, title.length(), bounds);
        canvas.drawText(title, width / 2f, height / 2f - 20f, textPaint);

        textPaint.setColor(Color.parseColor("#8E8E98"));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setTextSize(16f);
        String sub = context.getString(R.string.widget_no_activities);
        textPaint.getTextBounds(sub, 0, sub.length(), bounds);
        canvas.drawText(sub, width / 2f, height / 2f + 20f, textPaint);
    }

    public static Bitmap renderCellBitmap(Context context, AllActivitiesMatrixData.DayCell cell,
                                          com.example.data.entity.ActivityCategory category,
                                          int actColor, boolean isSelected, boolean isToday, int sizePx) {
        int sz = Math.max(sizePx, 48);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Rect bounds = new Rect();

        float cx = sz / 2f;
        float cy = sz / 2f;
        float cellRadius = sz * 0.35f;

        // 1. Selection Highlight (Glowing gold ring + tinted background)
        if (isSelected) {
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(Color.argb(60, Color.red(actColor), Color.green(actColor), Color.blue(actColor)));
            canvas.drawCircle(cx, cy, sz * 0.46f, shapePaint);

            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setColor(Color.parseColor("#FFD60A")); // Bright gold outline
            shapePaint.setStrokeWidth(sz * 0.08f);
            canvas.drawCircle(cx, cy, cellRadius + sz * 0.07f, shapePaint);
        } else if (isToday) {
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setColor(Color.parseColor("#60CDFF")); // Cyan ring for today
            shapePaint.setStrokeWidth(sz * 0.055f);
            canvas.drawCircle(cx, cy, cellRadius + sz * 0.055f, shapePaint);
        }

        if (cell == null || cell.status == -1) {
            // Future
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setColor(Color.parseColor("#3C3C54"));
            shapePaint.setStrokeWidth(sz * 0.045f);
            canvas.drawCircle(cx, cy, cellRadius, shapePaint);
        } else if (cell.status == 3 || cell.isPaused) {
            // Paused / Rest Day
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(actColor);
            canvas.drawCircle(cx, cy, cellRadius, shapePaint);

            // White pause symbol
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(cellRadius * 1.05f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.getTextBounds("⏸", 0, 1, bounds);
            canvas.drawText("⏸", cx, cy + bounds.height() / 2f, textPaint);
        } else if (cell.status == 2) {
            // Completed
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(actColor);
            canvas.drawCircle(cx, cy, cellRadius, shapePaint);

            // White Checkmark
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(cellRadius * 1.25f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.getTextBounds("✓", 0, 1, bounds);
            canvas.drawText("✓", cx, cy + bounds.height() / 2f, textPaint);
        } else if (cell.status == 1) {
            // Partial / Exceeded
            if (category == com.example.data.entity.ActivityCategory.DECREASE) {
                shapePaint.setStyle(Paint.Style.FILL);
                shapePaint.setColor(Color.parseColor("#FF453A"));
                canvas.drawCircle(cx, cy, cellRadius, shapePaint);

                textPaint.setColor(Color.WHITE);
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                textPaint.setTextSize(cellRadius * 1.2f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.getTextBounds("✗", 0, 1, bounds);
                canvas.drawText("✗", cx, cy + bounds.height() / 2f, textPaint);
            } else {
                shapePaint.setStyle(Paint.Style.FILL);
                int rColor = Color.red(actColor);
                int gColor = Color.green(actColor);
                int bColor = Color.blue(actColor);
                shapePaint.setColor(Color.argb(130, rColor, gColor, bColor));
                canvas.drawCircle(cx, cy, cellRadius, shapePaint);

                shapePaint.setStyle(Paint.Style.STROKE);
                shapePaint.setColor(actColor);
                shapePaint.setStrokeWidth(sz * 0.05f);
                canvas.drawCircle(cx, cy, cellRadius, shapePaint);

                int pct = Math.round(cell.percent * 100);
                if (pct > 0) {
                    String pctStr = pct + "%";
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    textPaint.setTextSize(cellRadius * 0.72f);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.getTextBounds(pctStr, 0, pctStr.length(), bounds);
                    canvas.drawText(pctStr, cx, cy + bounds.height() / 2f, textPaint);
                }
            }
        } else {
            // 0 tracked
            if (category == com.example.data.entity.ActivityCategory.DECREASE) {
                shapePaint.setStyle(Paint.Style.FILL);
                shapePaint.setColor(actColor);
                canvas.drawCircle(cx, cy, cellRadius, shapePaint);

                textPaint.setColor(Color.WHITE);
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                textPaint.setTextSize(cellRadius * 1.25f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.getTextBounds("✓", 0, 1, bounds);
                canvas.drawText("✓", cx, cy + bounds.height() / 2f, textPaint);
            } else {
                shapePaint.setStyle(Paint.Style.FILL);
                shapePaint.setColor(Color.parseColor("#1B1B26"));
                canvas.drawCircle(cx, cy, cellRadius, shapePaint);

                shapePaint.setStyle(Paint.Style.STROKE);
                shapePaint.setColor(Color.parseColor("#2A2A38"));
                shapePaint.setStrokeWidth(sz * 0.045f);
                canvas.drawCircle(cx, cy, cellRadius, shapePaint);

                textPaint.setColor(Color.parseColor("#7A7A8A"));
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                textPaint.setTextSize(cellRadius * 1.1f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.getTextBounds("-", 0, 1, bounds);
                canvas.drawText("-", cx, cy + bounds.height() / 2f, textPaint);
            }
        }

        return bmp;
    }

    public static Bitmap renderDotBitmap(int color, int sizePx) {
        int sz = Math.max(sizePx, 16);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(sz / 2f, sz / 2f, sz * 0.42f, paint);
        return bmp;
    }
}
