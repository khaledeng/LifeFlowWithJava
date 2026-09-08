package com.example.util;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class AppGroupManagerTest {

    private Context context;
    private AppGroupManager appGroupManager;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        appGroupManager = new AppGroupManager(context);
    }

    @Test
    public void testDefaultGroupsInitialization() {
        List<AppGroupManager.AppGroup> groups = appGroupManager.getAllGroups();
        Assert.assertNotNull(groups);
        Assert.assertTrue(groups.size() >= 2);

        AppGroupManager.AppGroup social = appGroupManager.getGroupById(AppGroupManager.DEFAULT_GROUP_SOCIAL_ID);
        Assert.assertNotNull(social);

        AppGroupManager.AppGroup games = appGroupManager.getGroupById(AppGroupManager.DEFAULT_GROUP_GAMES_ID);
        Assert.assertNotNull(games);
    }

    @Test
    public void testCustomGroupCreationAndDeletion() {
        String groupId = "custom_group_productivity";
        Set<String> pkgs = new HashSet<>();
        pkgs.add("com.adobe.reader");
        pkgs.add("com.google.android.apps.docs");

        appGroupManager.saveGroup(groupId, "Productivity Apps", pkgs);

        // Verify retrieval
        AppGroupManager.AppGroup retrieved = appGroupManager.getGroupById(groupId);
        Assert.assertNotNull(retrieved);
        Assert.assertEquals("Productivity Apps", retrieved.name);
        Assert.assertEquals(2, retrieved.packages.size());
        Assert.assertTrue(retrieved.packages.contains("com.adobe.reader"));

        // Update group
        pkgs.add("com.notion.id");
        appGroupManager.saveGroup(groupId, "Work & Docs", pkgs);
        AppGroupManager.AppGroup updated = appGroupManager.getGroupById(groupId);
        Assert.assertEquals("Work & Docs", updated.name);
        Assert.assertEquals(3, updated.packages.size());

        // Delete group
        appGroupManager.deleteGroup(groupId);
        AppGroupManager.AppGroup deleted = appGroupManager.getGroupById(groupId);
        Assert.assertTrue(deleted.packages.isEmpty());
    }
}
