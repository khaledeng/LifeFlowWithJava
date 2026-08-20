package com.example.data.converter;

import androidx.room.TypeConverter;
import com.example.data.entity.ActivityCategory;

public class ActivityCategoryConverter {

    @TypeConverter
    public static ActivityCategory toCategory(String category) {
        if (category == null) return ActivityCategory.NEUTRAL;
        try {
            return ActivityCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            return ActivityCategory.NEUTRAL;
        }
    }

    @TypeConverter
    public static String fromCategory(ActivityCategory category) {
        return category == null ? ActivityCategory.NEUTRAL.name() : category.name();
    }
}
