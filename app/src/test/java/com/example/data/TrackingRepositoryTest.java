package com.example.data;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.example.data.dao.ActivityDao;
import com.example.data.dao.SessionDao;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityCategory;
import com.example.data.entity.SessionEntity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class TrackingRepositoryTest {

    private AppDatabase inMemoryDb;
    private ActivityDao activityDao;
    private SessionDao sessionDao;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        activityDao = inMemoryDb.activityDao();
        sessionDao = inMemoryDb.sessionDao();
    }

    @After
    public void tearDown() {
        inMemoryDb.close();
    }

    @Test
    public void testInsertAndRetrieveActivity() {
        Activity study = new Activity("Study", ActivityCategory.INCREASE, 4.0f, "#3B82F6", "ic_study", true, System.currentTimeMillis());
        long id = activityDao.insertActivity(study);
        study.setId(id);

        Activity retrieved = activityDao.getActivityById(id);
        Assert.assertNotNull(retrieved);
        Assert.assertEquals("Study", retrieved.getName());
        Assert.assertEquals(ActivityCategory.INCREASE, retrieved.getCategory());
        Assert.assertEquals(4.0f, retrieved.getExpectedHoursPerDay(), 0.01f);
        Assert.assertEquals("#3B82F6", retrieved.getColorHex());
    }

    @Test
    public void testUpdateAndDeleteActivity() {
        Activity reading = new Activity("Reading", ActivityCategory.INCREASE, 1.5f, "#10B981", "ic_reading", true, System.currentTimeMillis());
        long id = activityDao.insertActivity(reading);
        reading.setId(id);

        reading.setName("Deep Reading");
        reading.setExpectedHoursPerDay(2.5f);
        activityDao.updateActivity(reading);

        Activity updated = activityDao.getActivityById(id);
        Assert.assertEquals("Deep Reading", updated.getName());
        Assert.assertEquals(2.5f, updated.getExpectedHoursPerDay(), 0.01f);

        activityDao.deleteActivity(updated);
        Assert.assertNull(activityDao.getActivityById(id));
    }

    @Test
    public void testActiveSessionLifecycle() {
        Activity code = new Activity("Coding", ActivityCategory.INCREASE, 6.0f, "#6366F1", "ic_code", true, System.currentTimeMillis());
        long actId = activityDao.insertActivity(code);

        long startTime = System.currentTimeMillis() - 3600000; // 1 hour ago
        SessionEntity activeSession = new SessionEntity(actId, "Coding", "#6366F1", "ic_code", startTime, 0L, 0L);
        long sessId = sessionDao.insertSession(activeSession);
        activeSession.setId(sessId);

        SessionEntity retrievedActive = sessionDao.getActiveSessionSync();
        Assert.assertNotNull(retrievedActive);
        Assert.assertEquals(actId, retrievedActive.getActivityId());
        Assert.assertEquals(0L, retrievedActive.getEndTime());

        // End session
        long endTime = System.currentTimeMillis();
        retrievedActive.setEndTime(endTime);
        retrievedActive.setDurationMillis(endTime - startTime);
        sessionDao.updateSession(retrievedActive);

        SessionEntity noActive = sessionDao.getActiveSessionSync();
        Assert.assertNull(noActive);

        List<SessionEntity> all = sessionDao.getAllSessionsSync();
        Assert.assertEquals(1, all.size());
        Assert.assertEquals(endTime, all.get(0).getEndTime());
    }

    @Test
    public void testSessionsInRange() {
        Activity sport = new Activity("Sport", ActivityCategory.INCREASE, 2.0f, "#EF4444", "ic_exercise", true, System.currentTimeMillis());
        long actId = activityDao.insertActivity(sport);

        long baseTime = 1700000000000L;
        // Session 1: inside range [baseTime, baseTime + 10000]
        SessionEntity s1 = new SessionEntity(actId, "Sport", "#EF4444", "ic_exercise", baseTime + 1000, baseTime + 3000, 2000L);
        sessionDao.insertSession(s1);

        // Session 2: outside range
        SessionEntity s2 = new SessionEntity(actId, "Sport", "#EF4444", "ic_exercise", baseTime + 20000, baseTime + 25000, 5000L);
        sessionDao.insertSession(s2);

        List<SessionEntity> inRange = sessionDao.getSessionsInRangeSync(baseTime, baseTime + 10000);
        Assert.assertEquals(1, inRange.size());
        Assert.assertEquals(baseTime + 1000, inRange.get(0).getStartTime());
    }
}
