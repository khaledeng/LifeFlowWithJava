package com.example.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.util.IconHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * High-precision Multi-Line Trend Chart matching the exact design:
 * - Left Y-Axis labels (0, 40h, 80h...) with subtle dashed grid lines
 * - Bottom X-Axis labels (Month names / Day names / Hour slots)
 * - Multi-series smooth cubic curves with double-ring glowing points
 * - Interactive touch indicator & tooltip
 */
public class MultiLineStatsChartView extends View {

    public static class Series {
        public long activityId;
        public String name;
        public String iconName;
        public int color;
        public float[] values; // duration in hours for each x-slot

        public Series(long activityId, String name, int color, float[] values) {
            this(activityId, name, "ic_work", color, values);
        }

        public Series(long activityId, String name, String iconName, int color, float[] values) {
            this.activityId = activityId;
            this.name = name;
            this.iconName = iconName;
            this.color = color;
            this.values = values;
        }
    }

    private final List<Series> seriesList = new ArrayList<>();
    private String[] xLabels = new String[0];
    private float maxYValue = 10f; // in hours

    private Long highlightedActivityId = null; // null = show all, or specific ID

    // Paints
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Touch selection & gestures
    public interface OnPeriodSwipeListener {
        void onSwipeToPrevious();
        void onSwipeToNext();
    }

    private OnPeriodSwipeListener periodSwipeListener;
    private int selectedIndex = -1;
    private Series selectedSeries = null;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private boolean isDragging = false;
    private boolean hasTriggeredSwipe = false;

    public void setOnPeriodSwipeListener(OnPeriodSwipeListener listener) {
        this.periodSwipeListener = listener;
    }

    public MultiLineStatsChartView(Context context) {
        super(context);
        init();
    }

    public MultiLineStatsChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MultiLineStatsChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.parseColor("#26262B"));
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(4f)}, 0));

        axisTextPaint.setColor(Color.parseColor("#7A7A85"));
        axisTextPaint.setTextSize(sp(11f));
        axisTextPaint.setTextAlign(Paint.Align.RIGHT);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointOuterPaint.setStyle(Paint.Style.STROKE);
        pointOuterPaint.setStrokeWidth(dp(2.5f));

        pointInnerPaint.setStyle(Paint.Style.FILL);

        tooltipBgPaint.setColor(Color.parseColor("#222228"));
        tooltipBgPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(sp(12f));
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
        tooltipTextPaint.setFakeBoldText(true);
    }

    public void setData(List<Series> newSeries, String[] labels) {
        this.seriesList.clear();
        if (newSeries != null) {
            this.seriesList.addAll(newSeries);
        }
        this.xLabels = labels != null ? labels : new String[0];

        // Recalculate max Y dynamically based on actual recorded data
        float max = 0f;
        for (Series s : this.seriesList) {
            if (s.values != null) {
                for (float v : s.values) {
                    if (v > max) max = v;
                }
            }
        }

        if (max <= 0f) {
            maxYValue = 1.0f; // Default 1 hour when empty
        } else if (max <= 0.1f) { // <= 6 mins -> 6 mins
            maxYValue = 0.1f;
        } else if (max <= 0.25f) { // <= 15 mins -> 15 mins
            maxYValue = 0.25f;
        } else if (max <= 0.5f) { // <= 30 mins -> 30 mins
            maxYValue = 0.5f;
        } else if (max <= 1.0f) { // <= 60 mins -> 60 mins
            maxYValue = 1.0f;
        } else if (max <= 2.0f) { // <= 2h -> 2.0h
            maxYValue = 2.0f;
        } else if (max <= 4.0f) {
            maxYValue = (float) Math.ceil(max);
        } else if (max <= 12.0f) {
            maxYValue = (float) (Math.ceil(max / 2.0) * 2.0);
        } else if (max <= 40.0f) {
            maxYValue = (float) (Math.ceil(max / 5.0) * 5.0);
        } else if (max <= 200.0f) {
            maxYValue = (float) (Math.ceil(max / 20.0) * 20.0);
        } else {
            maxYValue = (float) (Math.ceil(max / 50.0) * 50.0);
        }

        selectedIndex = -1;
        selectedSeries = null;
        invalidate();
    }

    public void setHighlightedActivityId(Long activityId) {
        if (this.highlightedActivityId != null && this.highlightedActivityId.equals(activityId)) {
            this.highlightedActivityId = null; // Toggle back to all
        } else {
            this.highlightedActivityId = activityId;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float paddingLeft = dp(46f);
        float paddingRight = dp(18f);
        float paddingTop = dp(20f);
        float paddingBottom = dp(32f);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        if (chartWidth <= 0 || chartHeight <= 0) return;

        // 1. Draw Y-Axis labels & horizontal grid lines (5 levels: 0, 1/4, 2/4, 3/4, 4/4)
        int steps = 4;
        boolean isArabic = Locale.getDefault().getLanguage().equals("ar");
        for (int i = 0; i <= steps; i++) {
            float ratio = (float) i / steps;
            float y = paddingTop + chartHeight - (ratio * chartHeight);
            float val = ratio * maxYValue;

            String label;
            if (val <= 0.001f) {
                label = "\u200E0";
            } else if (maxYValue <= 1.0f) {
                long mins = Math.round(val * 60f);
                label = String.format(Locale.getDefault(), "\u200E%d%s", mins, isArabic ? "د" : "m");
            } else if (val >= 10) {
                label = String.format(Locale.getDefault(), "\u200E%.0f%s", val, isArabic ? "س" : "h");
            } else {
                if (Math.abs(val - Math.round(val)) < 0.05f) {
                    label = String.format(Locale.getDefault(), "\u200E%.0f%s", val, isArabic ? "س" : "h");
                } else {
                    label = String.format(Locale.getDefault(), "\u200E%.1f%s", val, isArabic ? "س" : "h");
                }
            }

            // Text
            axisTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(label, paddingLeft - dp(6f), y + dp(4f), axisTextPaint);

            // Grid Line
            Path gridPath = new Path();
            gridPath.moveTo(paddingLeft, y);
            gridPath.lineTo(width - paddingRight, y);
            canvas.drawPath(gridPath, gridPaint);
        }

        // 2. Draw X-Axis labels
        int numPoints = xLabels.length;
        if (numPoints == 0) return;

        axisTextPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < numPoints; i++) {
            float x = paddingLeft + ((float) i / Math.max(1, numPoints - 1)) * chartWidth;
            canvas.drawText(xLabels[i], x, height - dp(8f), axisTextPaint);
        }

        // 3. Draw Lines & Points for each series
        for (Series s : seriesList) {
            if (s.values == null || s.values.length == 0) continue;

            boolean isDimmed = (highlightedActivityId != null && highlightedActivityId != s.activityId);
            int color = s.color;
            if (isDimmed) {
                color = Color.argb(40, Color.red(s.color), Color.green(s.color), Color.blue(s.color));
            }

            linePaint.setColor(color);
            linePaint.setStrokeWidth(isDimmed ? dp(1.5f) : dp(3.5f));

            int lastValidIndex = -1;
            for (int i = 0; i < numPoints; i++) {
                float val = (i < s.values.length) ? s.values[i] : 0f;
                if (val < 0) break;
                lastValidIndex = i;
            }

            if (lastValidIndex < 0) continue; // No valid points to draw

            float[] ptsX = new float[lastValidIndex + 1];
            float[] ptsY = new float[lastValidIndex + 1];

            for (int i = 0; i <= lastValidIndex; i++) {
                ptsX[i] = paddingLeft + ((float) i / Math.max(1, numPoints - 1)) * chartWidth;
                float val = s.values[i];
                float clampedVal = Math.max(0, Math.min(val, maxYValue));
                ptsY[i] = paddingTop + chartHeight - ((clampedVal / maxYValue) * chartHeight);
            }

            // Construct smooth Bezier curve path
            Path linePath = new Path();
            linePath.moveTo(ptsX[0], ptsY[0]);

            for (int i = 0; i < lastValidIndex; i++) {
                float x1 = ptsX[i];
                float y1 = ptsY[i];
                float x2 = ptsX[i + 1];
                float y2 = ptsY[i + 1];

                float cx1 = x1 + (x2 - x1) / 2f;
                float cy1 = y1;
                float cx2 = x1 + (x2 - x1) / 2f;
                float cy2 = y2;

                linePath.cubicTo(cx1, cy1, cx2, cy2, x2, y2);
            }

            canvas.drawPath(linePath, linePaint);

            // Draw circular point rings with glowing outer circles
            if (!isDimmed) {
                for (int i = 0; i <= lastValidIndex; i++) {
                    float px = ptsX[i];
                    float py = ptsY[i];

                    // Outer glowing ring
                    pointOuterPaint.setColor(s.color);
                    pointOuterPaint.setAlpha(255);
                    canvas.drawCircle(px, py, dp(6f), pointOuterPaint);

                    // Inner background knockout / dot
                    pointInnerPaint.setColor(Color.parseColor("#121214")); // Dark theme bg
                    canvas.drawCircle(px, py, dp(4f), pointInnerPaint);

                    // Center solid colored dot
                    pointInnerPaint.setColor(s.color);
                    canvas.drawCircle(px, py, dp(2.5f), pointInnerPaint);
                }
            }
        }

        // 4. Draw Touch Tooltip if selected
        if (selectedIndex >= 0 && selectedIndex < numPoints) {
            float tx = paddingLeft + ((float) selectedIndex / Math.max(1, numPoints - 1)) * chartWidth;

            // Draw vertical dashed cursor line
            Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            cursorPaint.setColor(Color.WHITE);
            cursorPaint.setAlpha(120);
            cursorPaint.setStrokeWidth(dp(1.5f));
            cursorPaint.setPathEffect(new DashPathEffect(new float[]{dp(3f), dp(3f)}, 0));
            canvas.drawLine(tx, paddingTop, tx, paddingTop + chartHeight, cursorPaint);

            // Tooltip box
            String tooltipSlot = xLabels[selectedIndex];
            String tooltipVal = "";
            int tipColor = Color.WHITE;

            if (selectedSeries != null && selectedIndex < selectedSeries.values.length) {
                float v = selectedSeries.values[selectedIndex]; if (v < 0) v = 0f;
                tooltipVal = selectedSeries.name + ": \u200E" + formatDuration(v, isArabic);
                tipColor = selectedSeries.color;
            } else if (!seriesList.isEmpty()) {
                Series topSeries = seriesList.get(0);
                float v = (selectedIndex < topSeries.values.length) ? topSeries.values[selectedIndex] : 0f; if (v < 0) v = 0f;
                tooltipVal = topSeries.name + ": \u200E" + formatDuration(v, isArabic);
                tipColor = topSeries.color;
            }

            String fullText = tooltipSlot + " • " + tooltipVal;
            float textWidth = tooltipTextPaint.measureText(fullText);
            float boxWidth = textWidth + dp(24f);
            float boxHeight = dp(28f);

            float boxLeft = Math.max(dp(8f), Math.min(width - boxWidth - dp(8f), tx - (boxWidth / 2f)));
            float boxTop = paddingTop - dp(12f);

            RectF tipRect = new RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight);
            canvas.drawRoundRect(tipRect, dp(8f), dp(8f), tooltipBgPaint);

            // Border for tooltip in active color
            Paint tipStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            tipStroke.setStyle(Paint.Style.STROKE);
            tipStroke.setColor(tipColor);
            tipStroke.setStrokeWidth(dp(1.5f));
            canvas.drawRoundRect(tipRect, dp(8f), dp(8f), tipStroke);

            canvas.drawText(fullText, tipRect.centerX(), tipRect.centerY() + dp(4f), tooltipTextPaint);
        }
    }

    private String formatDuration(float hoursVal, boolean isArabic) {
        if (hoursVal <= 0.001f) return isArabic ? "0 د" : "0m";
        long totalMinutes = Math.round(hoursVal * 60f);
        if (totalMinutes == 0 && hoursVal > 0) totalMinutes = 1;
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        if (h == 0) {
            return m + (isArabic ? " د" : "m");
        } else if (m == 0) {
            return h + (isArabic ? " س" : "h");
        } else {
            return h + (isArabic ? " س " : "h ") + m + (isArabic ? " د" : "m");
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (xLabels.length == 0) return super.onTouchEvent(event);

        float paddingLeft = dp(46f);
        float paddingRight = dp(18f);
        float chartWidth = getWidth() - paddingLeft - paddingRight;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                isDragging = false;
                hasTriggeredSwipe = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float totalDx = event.getX() - touchDownX;
                float totalDy = event.getY() - touchDownY;
                float absDx = Math.abs(totalDx);
                float absDy = Math.abs(totalDy);

                if (absDx > dp(8f) || absDy > dp(8f)) {
                    isDragging = true;
                }

                // If vertical drag is more than horizontal, allow parent ScrollView to intercept
                if (isDragging && absDy > absDx && absDy > dp(8f)) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }

                // Disallow parent to intercept when clearly doing horizontal swipe or drag within chart
                if (isDragging) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                // If user is clearly swiping horizontally beyond threshold
                if (absDx > dp(60f) && absDx > absDy * 1.2f && !hasTriggeredSwipe) {
                    if (periodSwipeListener != null) {
                        hasTriggeredSwipe = true;
                        if (totalDx > 0) {
                            periodSwipeListener.onSwipeToPrevious();
                        } else {
                            periodSwipeListener.onSwipeToNext();
                        }
                    }
                } else if (!hasTriggeredSwipe) {
                    float touchX = event.getX() - paddingLeft;
                    float fraction = Math.max(0f, Math.min(1f, touchX / chartWidth));
                    int index = Math.round(fraction * (xLabels.length - 1));
                    if (index != selectedIndex) {
                        selectIndex(index);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (!isDragging && !hasTriggeredSwipe) {
                    // Tap / Click action: toggle selection if clicked in same slot
                    float touchX = event.getX() - paddingLeft;
                    float fraction = Math.max(0f, Math.min(1f, touchX / chartWidth));
                    int index = Math.round(fraction * (xLabels.length - 1));

                    if (selectedIndex == index) {
                        // Tapped on the currently selected point/column -> Hide tooltip & line!
                        selectedIndex = -1;
                        selectedSeries = null;
                        invalidate();
                    } else {
                        // Show tooltip & line for the tapped slot
                        selectIndex(index);
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void selectIndex(int index) {
        selectedIndex = index;
        if (highlightedActivityId != null) {
            for (Series s : seriesList) {
                if (s.activityId == highlightedActivityId) {
                    selectedSeries = s;
                    break;
                }
            }
        } else if (!seriesList.isEmpty()) {
            Series best = seriesList.get(0);
            float maxV = (selectedIndex < best.values.length) ? best.values[selectedIndex] : 0f;
            for (Series s : seriesList) {
                if (selectedIndex < s.values.length && s.values[selectedIndex] > maxV) {
                    maxV = s.values[selectedIndex];
                    best = s;
                }
            }
            selectedSeries = best;
        }
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
