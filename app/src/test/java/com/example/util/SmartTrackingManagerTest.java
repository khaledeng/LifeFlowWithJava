package com.example.util;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.example.data.entity.Activity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class SmartTrackingManagerTest {

    private Context context;
    private SmartTrackingManager smartTrackingManager;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        smartTrackingManager = new SmartTrackingManager(context);
    }

    @Test
    public void testActivityAppBinding() {
        Activity entertainment = new Activity();
        entertainment.setId(101L);
        entertainment.setName("Entertainment");
        entertainment.setColorHex("#A855F7");
        entertainment.setIconName("ic_entertainment");

        Set<String> pkgs = new HashSet<>();
        pkgs.add("com.zhiliaoapp.musically");
        pkgs.add("com.ss.android.ugc.trill");

        smartTrackingManager.setActivityBoundApps(entertainment, pkgs);

        Set<String> retrieved = smartTrackingManager.getActivityBoundApps(entertainment);
        Assert.assertNotNull(retrieved);
        Assert.assertTrue(retrieved.contains("com.zhiliaoapp.musically"));
        Assert.assertTrue(retrieved.contains("com.ss.android.ugc.trill"));
    }

    @Test
    public void testTimeRangeLogic() {
        Activity work = new Activity();
        work.setId(202L);
        work.setName("Work");
        work.setColorHex("#3B82F6");
        work.setIconName("ic_work");

        smartTrackingManager.setActivityTimeRange(work, 16, 0, 20, 0, true);

        Assert.assertTrue(smartTrackingManager.isActivityTimeEnabled(work));
        Assert.assertEquals(16, smartTrackingManager.getActivityStartHour(work));
        Assert.assertEquals(20, smartTrackingManager.getActivityEndHour(work));
        Assert.assertTrue(SmartTrackingManager.isTimeInRange(17, 30, 16, 0, 20, 0));
        Assert.assertFalse(SmartTrackingManager.isTimeInRange(21, 0, 16, 0, 20, 0));
    }

    @Test
    public void testAppLockToggle() {
        Activity entertainment = new Activity();
        entertainment.setId(303L);
        entertainment.setName("Entertainment");

        smartTrackingManager.setActivityAppLockEnabled(entertainment, true);
        Assert.assertTrue(smartTrackingManager.isActivityAppLockEnabled(entertainment));

        smartTrackingManager.setActivityAppLockEnabled(entertainment, false);
        Assert.assertFalse(smartTrackingManager.isActivityAppLockEnabled(entertainment));
    }

    @Test
    public void testDefaultActivityFallback() {
        Activity defaultAct = new Activity();
        defaultAct.setId(404L);
        defaultAct.setName("Free Time");

        smartTrackingManager.setDefaultActivity(defaultAct);
        Assert.assertEquals(404L, smartTrackingManager.getDefaultActivityId());
        Assert.assertEquals("Free Time", smartTrackingManager.getDefaultActivityName());

        List<Activity> list = new ArrayList<>();
        list.add(defaultAct);

        String target = smartTrackingManager.determineTargetActivityName(context, list, null);
        Assert.assertEquals("Free Time", target);
    }
}
