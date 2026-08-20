package com.example.data.entity;

import androidx.room.Ignore;

/**
 * ActivityEntity extends Activity for backwards compatibility across the project.
 */
public class ActivityEntity extends Activity {

    public ActivityEntity() {
        super();
    }

    @Ignore
    public ActivityEntity(String name, String colorHex, String iconName, boolean isDefault, long createdAt) {
        super(name, colorHex, iconName, isDefault, createdAt);
    }

    @Ignore
    public ActivityEntity(String name, ActivityCategory category, float expectedHoursPerDay) {
        super(name, category, expectedHoursPerDay);
    }

    @Ignore
    public ActivityEntity(String name, ActivityCategory category, float expectedHoursPerDay, String colorHex, String iconName, boolean isDefault, long createdAt) {
        super(name, category, expectedHoursPerDay, colorHex, iconName, isDefault, createdAt);
    }
}
