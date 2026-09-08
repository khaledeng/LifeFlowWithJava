package com.example.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import com.example.R;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityCategory;
import com.example.data.model.AllActivitiesMatrixData;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class MonthMatrixWidgetTest {

    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testRemoteViewsInflation() {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_month_matrix);
        View inflated = views.apply(context, null);
        Assert.assertNotNull(inflated);
        Assert.assertNotNull(inflated.findViewById(R.id.widget_container));
        Assert.assertNotNull(inflated.findViewById(R.id.btn_widget_prev_month));
        Assert.assertNotNull(inflated.findViewById(R.id.btn_widget_next_month));
        Assert.assertNotNull(inflated.findViewById(R.id.tv_widget_title));
        Assert.assertNotNull(inflated.findViewById(R.id.btn_widget_prev_days));
        Assert.assertNotNull(inflated.findViewById(R.id.btn_widget_next_days));
        Assert.assertNotNull(inflated.findViewById(R.id.tv_widget_days_range));
        Assert.assertNotNull(inflated.findViewById(R.id.layout_matrix_rows_container));
        Assert.assertNotNull(inflated.findViewById(R.id.layout_quick_done_bar));
        Assert.assertNotNull(inflated.findViewById(R.id.btn_widget_quick_done));
        Assert.assertNotNull(inflated.findViewById(R.id.btn_widget_quick_undone));
    }

    @Test
    public void testHeaderAndRowInflation() {
        RemoteViews headerViews = new RemoteViews(context.getPackageName(), R.layout.widget_matrix_header_row);
        View headerInflated = headerViews.apply(context, null);
        Assert.assertNotNull(headerInflated);
        Assert.assertNotNull(headerInflated.findViewById(R.id.tv_header_act_title));
        Assert.assertNotNull(headerInflated.findViewById(R.id.tv_header_day_0));

        RemoteViews rowViews = new RemoteViews(context.getPackageName(), R.layout.widget_matrix_row);
        View rowInflated = rowViews.apply(context, null);
        Assert.assertNotNull(rowInflated);
        Assert.assertNotNull(rowInflated.findViewById(R.id.tv_row_act_name));
        Assert.assertNotNull(rowInflated.findViewById(R.id.iv_cell_0));
        Assert.assertNotNull(rowInflated.findViewById(R.id.iv_cell_6));
    }

    @Test
    public void testCellAndDotBitmapRendering() {
        AllActivitiesMatrixData.DayCell cell = new AllActivitiesMatrixData.DayCell();
        cell.dayOfMonth = 6;
        cell.isToday = true;
        cell.status = 2; // completed
        cell.percent = 1.0f;

        Bitmap cellBmp = MatrixBitmapRenderer.renderCellBitmap(context, cell,
                ActivityCategory.INCREASE, android.graphics.Color.GREEN, true, true, 64);
        Assert.assertNotNull(cellBmp);
        Assert.assertEquals(64, cellBmp.getWidth());
        Assert.assertEquals(64, cellBmp.getHeight());

        Bitmap dotBmp = MatrixBitmapRenderer.renderDotBitmap(android.graphics.Color.BLUE, 24);
        Assert.assertNotNull(dotBmp);
        Assert.assertEquals(24, dotBmp.getWidth());
        Assert.assertEquals(24, dotBmp.getHeight());
    }

    @Test
    public void testMatrixBitmapRendering() {
        AllActivitiesMatrixData data = new AllActivitiesMatrixData();
        data.monthName = "سبتمبر 2026";
        data.daysInMonth = 30;
        data.dayHeaders = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            AllActivitiesMatrixData.DayHeader dh = new AllActivitiesMatrixData.DayHeader();
            dh.dayOfMonth = i;
            dh.dayName = "سبت";
            dh.isToday = (i == 6);
            data.dayHeaders.add(dh);
        }

        data.rows = new ArrayList<>();
        Activity act = new Activity("قراءة", ActivityCategory.INCREASE, 1.0f);
        act.setId(1);
        act.setColorHex("#39D353");
        AllActivitiesMatrixData.ActivityRow row = new AllActivitiesMatrixData.ActivityRow();
        row.activity = act;
        row.dayCells = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            AllActivitiesMatrixData.DayCell cell = new AllActivitiesMatrixData.DayCell();
            cell.dayOfMonth = i;
            cell.isToday = (i == 6);
            cell.status = (i < 6) ? 2 : ((i == 6) ? 1 : -1);
            cell.percent = (i < 6) ? 1.0f : ((i == 6) ? 0.5f : 0f);
            row.dayCells.add(cell);
        }
        data.rows.add(row);

        Bitmap bitmap = MatrixBitmapRenderer.renderMatrix(context, data, 720, 420, 1, 7, true);
        Assert.assertNotNull(bitmap);
        Assert.assertTrue(bitmap.getWidth() > 0);
        Assert.assertTrue(bitmap.getHeight() > 0);

        // Responsive Small Widget Size (e.g. 2x2 or compact)
        Bitmap smallBitmap = MatrixBitmapRenderer.renderMatrix(context, data, 200, 100, 1, 7, true);
        Assert.assertNotNull(smallBitmap);
        Assert.assertEquals(200, smallBitmap.getWidth());
        Assert.assertEquals(100, smallBitmap.getHeight());

        // Responsive Tall / Square Size (e.g. 4x4 or 3x3)
        Bitmap squareBitmap = MatrixBitmapRenderer.renderMatrix(context, data, 400, 400, 1, 7, true);
        Assert.assertNotNull(squareBitmap);
        Assert.assertEquals(400, squareBitmap.getWidth());
        Assert.assertEquals(400, squareBitmap.getHeight());
    }
}
