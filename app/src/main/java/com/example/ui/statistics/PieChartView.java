package com.example.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PieChartView extends View {

    public static class Slice {
        public String name;
        public float percentage;
        public int color;

        public Slice(String name, float percentage, int color) {
            this.name = name;
            this.percentage = percentage;
            this.color = color;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerKnockoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final RectF drawRect = new RectF();
    
    private int selectedIndex = -1;
    private android.widget.Toast activeToast = null;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private boolean isDragging = false;

    public PieChartView(Context context) {
        super(context);
        init();
    }

    public PieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PieChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.parseColor("#121214")); // Background color to act as a separator gap
        borderPaint.setStrokeWidth(dp(3f));

        centerKnockoutPaint.setColor(Color.parseColor("#121214")); // Dark theme bg
        centerKnockoutPaint.setStyle(Paint.Style.FILL);

        centerBorderPaint.setColor(Color.parseColor("#26262B"));
        centerBorderPaint.setStyle(Paint.Style.STROKE);
        centerBorderPaint.setStrokeWidth(dp(1.5f));
    }

    public void setSlices(List<Slice> newSlices) {
        this.slices.clear();
        this.selectedIndex = -1;
        if (newSlices != null) {
            this.slices.addAll(newSlices);
        }
        invalidate();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (slices.isEmpty()) return super.onTouchEvent(event);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float size = Math.min(getWidth(), getHeight()) - dp(48f); // leaves space for offset slices
        float radius = size / 2f;

        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        int action = event.getAction();

        if (action == android.view.MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            touchDownY = event.getY();
            isDragging = false;
            return true; // Needed to receive subsequent events
        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            float dxMove = event.getX() - touchDownX;
            float dyMove = event.getY() - touchDownY;
            float absDx = Math.abs(dxMove);
            float absDy = Math.abs(dyMove);
            if (absDx > dp(8f) || absDy > dp(8f)) {
                isDragging = true;
            }
            if (isDragging) {
                getParent().requestDisallowInterceptTouchEvent(false);
                return false; // let parent handle intercepting and scrolling
            }
            return true;
        } else if (action == android.view.MotionEvent.ACTION_UP) {
            if (isDragging) {
                return true;
            }
            int clickedIndex = -1;
            if (dist >= radius * 0.35f && dist <= radius + dp(12f)) {
                double angleRad = Math.atan2(dy, dx);
                double angleDeg = Math.toDegrees(angleRad);
                if (angleDeg < 0) {
                    angleDeg += 360.0;
                }
                
                // Start angle is -90 degrees, normalize so -90 matches 0
                double normalizedAngle = angleDeg - (-90.0);
                if (normalizedAngle < 0) {
                    normalizedAngle += 360.0;
                }
                if (normalizedAngle >= 360.0) {
                    normalizedAngle -= 360.0;
                }

                float total = 0f;
                for (Slice slice : slices) {
                    total += slice.percentage;
                }

                if (total > 0f) {
                    float currentAngle = 0f;
                    for (int i = 0; i < slices.size(); i++) {
                        float sweepAngle = (slices.get(i).percentage / total) * 360f;
                        if (normalizedAngle >= currentAngle && normalizedAngle < currentAngle + sweepAngle) {
                            clickedIndex = i;
                            break;
                        }
                        currentAngle += sweepAngle;
                    }
                }
            }

            if (clickedIndex != -1) {
                if (clickedIndex == selectedIndex) {
                    // Clicked again, deselect!
                    selectedIndex = -1;
                    if (activeToast != null) {
                        activeToast.cancel();
                        activeToast = null;
                    }
                } else {
                    // Select new slice and show toast
                    selectedIndex = clickedIndex;
                    if (activeToast != null) {
                        activeToast.cancel();
                    }
                    Slice slice = slices.get(clickedIndex);
                    activeToast = android.widget.Toast.makeText(getContext(), slice.name + " (" + Math.round(slice.percentage) + "%)", android.widget.Toast.LENGTH_SHORT);
                    activeToast.show();
                }
            } else {
                // Clicked outside, deselect
                selectedIndex = -1;
                if (activeToast != null) {
                    activeToast.cancel();
                    activeToast = null;
                }
            }

            invalidate();
            performClick();
            return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || slices.isEmpty()) return;

        // Make it a perfect circle fitting the smaller dimension with padding
        float size = Math.min(width, height) - dp(48f); // extra padding for popped out slices
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;
        rectF.set(left, top, left + size, top + size);

        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = size / 2f;

        // Compute total percentage to normalize
        float total = 0f;
        for (Slice slice : slices) {
            total += slice.percentage;
        }
        if (total <= 0f) return;

        float currentAngle = -90f; // Start from top
        textPaint.setTextSize(sp(12f));

        for (int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            float sweepAngle = (slice.percentage / total) * 360f;
            if (sweepAngle <= 0f) continue;

            boolean isSelected = (i == selectedIndex);
            float offset = isSelected ? dp(8f) : 0f;
            float middleAngle = currentAngle + sweepAngle / 2f;

            // Offset the slice outward if selected
            float ox = (float) Math.cos(Math.toRadians(middleAngle)) * offset;
            float oy = (float) Math.sin(Math.toRadians(middleAngle)) * offset;

            drawRect.set(rectF);
            drawRect.offset(ox, oy);

            // Draw the slice
            paint.setColor(slice.color);
            canvas.drawArc(drawRect, currentAngle, sweepAngle, true, paint);

            // Draw separators between slices to give a modern, premium look
            canvas.drawArc(drawRect, currentAngle, sweepAngle, true, borderPaint);

            // Draw percentage text inside the slice if it's large enough (> 12 degrees)
            if (sweepAngle > 12f) {
                // Position text at 65% of the radius from the center
                float textRadius = radius * 0.62f;
                float tx = centerX + ox + (float) Math.cos(Math.toRadians(middleAngle)) * textRadius;
                float ty = centerY + oy + (float) Math.sin(Math.toRadians(middleAngle)) * textRadius;

                // Format: "25%"
                String text = String.format(Locale.US, "%.0f%%", slice.percentage);
                
                // Adjust vertical alignment
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float dy = ty - (fm.ascent + fm.descent) / 2f;

                canvas.drawText(text, tx, dy, textPaint);
            }

            currentAngle += sweepAngle;
        }

        // Draw a beautiful central hollow knockout to turn it into an elegant donut chart
        canvas.drawCircle(centerX, centerY, radius * 0.35f, centerKnockoutPaint);

        // Subtle inner glowing border for the center hole
        canvas.drawCircle(centerX, centerY, radius * 0.35f, centerBorderPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
