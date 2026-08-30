package com.example.data.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "activities")
public class Activity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public ActivityCategory category = ActivityCategory.NEUTRAL;
    public float expectedHoursPerDay = 0f;

    public String colorHex = "#39D353";
    public String iconName = "ic_work";
    public boolean isDefault = false;
    public long createdAt = System.currentTimeMillis();

    public Activity() {
    }

    @Ignore
    public Activity(String name, ActivityCategory category, float expectedHoursPerDay) {
        this.name = name;
        this.category = category;
        this.expectedHoursPerDay = expectedHoursPerDay;
    }

    @Ignore
    public Activity(String name, String colorHex, String iconName, boolean isDefault, long createdAt) {
        this.name = name;
        this.colorHex = colorHex;
        this.iconName = iconName;
        this.isDefault = isDefault;
        this.category = ActivityCategory.NEUTRAL;
        this.expectedHoursPerDay = 0f;
    }

    @Ignore
    public Activity(String name, ActivityCategory category, float expectedHoursPerDay, String colorHex, String iconName, boolean isDefault, long createdAt) {
        this.name = name;
        this.category = category;
        this.expectedHoursPerDay = expectedHoursPerDay;
        this.colorHex = colorHex;
        this.iconName = iconName;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNameWithArrow() {
        if (name == null) return "";
        ActivityCategory cat = getCategory();
        if (cat == ActivityCategory.INCREASE) {
            return name + " ↑";
        } else if (cat == ActivityCategory.DECREASE) {
            return name + " ↓";
        }
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ActivityCategory getCategory() {
        return category != null ? category : ActivityCategory.NEUTRAL;
    }

    public void setCategory(ActivityCategory category) {
        this.category = category;
    }

    public ActivityCategory getGoalType() {
        return getCategory();
    }

    public void setGoalType(ActivityCategory category) {
        setCategory(category);
    }

    public float getExpectedHoursPerDay() {
        return expectedHoursPerDay;
    }

    public void setExpectedHoursPerDay(float expectedHoursPerDay) {
        this.expectedHoursPerDay = expectedHoursPerDay;
    }

    public String getColorHex() {
        return colorHex != null ? colorHex : "#39D353";
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getIconName() {
        return iconName != null ? iconName : "ic_work";
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
