import sys

with open("app/src/main/java/com/example/ui/statistics/MultiLineStatsChartView.java", "r") as f:
    content = f.read()

target = """            if (selectedSeries != null && selectedIndex < selectedSeries.values.length) {
                float v = selectedSeries.values[selectedIndex];
                tooltipVal = String.format(Locale.US, "%s: \u200E%.1fh", selectedSeries.name, v);
                tipColor = selectedSeries.color;
            } else if (!seriesList.isEmpty()) {
                Series topSeries = seriesList.get(0);
                float v = (selectedIndex < topSeries.values.length) ? topSeries.values[selectedIndex] : 0f;
                tooltipVal = String.format(Locale.US, "%s: \u200E%.1fh", topSeries.name, v);
                tipColor = topSeries.color;
            }"""

replacement = """            if (selectedSeries != null && selectedIndex < selectedSeries.values.length) {
                float v = selectedSeries.values[selectedIndex];
                if (v < 0) v = 0f;
                tooltipVal = String.format(Locale.US, "%s: \u200E%.1fh", selectedSeries.name, v);
                tipColor = selectedSeries.color;
            } else if (!seriesList.isEmpty()) {
                Series topSeries = seriesList.get(0);
                float v = (selectedIndex < topSeries.values.length) ? topSeries.values[selectedIndex] : 0f;
                if (v < 0) v = 0f;
                tooltipVal = String.format(Locale.US, "%s: \u200E%.1fh", topSeries.name, v);
                tipColor = topSeries.color;
            }"""

new_content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/statistics/MultiLineStatsChartView.java", "w") as f:
    f.write(new_content)

print("Replaced:", content != new_content)
