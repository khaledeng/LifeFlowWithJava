import sys

with open("app/src/main/java/com/example/ui/statistics/MultiLineStatsChartView.java", "r") as f:
    content = f.read()

target = """            float[] ptsX = new float[numPoints];
            float[] ptsY = new float[numPoints];

            for (int i = 0; i < numPoints; i++) {
                ptsX[i] = paddingLeft + ((float) i / Math.max(1, numPoints - 1)) * chartWidth;
                float val = (i < s.values.length) ? s.values[i] : 0f;
                float clampedVal = Math.max(0, Math.min(val, maxYValue));
                ptsY[i] = paddingTop + chartHeight - ((clampedVal / maxYValue) * chartHeight);
            }

            // Construct smooth Bezier curve path
            Path linePath = new Path();
            linePath.moveTo(ptsX[0], ptsY[0]);

            for (int i = 0; i < numPoints - 1; i++) {
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
                for (int i = 0; i < numPoints; i++) {
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
            }"""

replacement = """            int lastValidIndex = -1;
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
            }"""

new_content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/statistics/MultiLineStatsChartView.java", "w") as f:
    f.write(new_content)

print("Replaced:", content != new_content)
