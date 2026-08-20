package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SubscriptionManager {

    private static final String PREFS_NAME = "lifeflow_prefs";
    private static final String KEY_IS_PRO = "key_is_pro_member";
    private static final String KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled";
    private static final String KEY_MOTIVATIONAL_NOTIFICATIONS_ENABLED = "key_motivational_notifications_enabled";
    public static final int FREE_TIER_MAX_ACTIVITIES = 3;

    private final SharedPreferences prefs;

    public SubscriptionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isPro() {
        return prefs.getBoolean(KEY_IS_PRO, false);
    }

    public void setPro(boolean isPro) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply();
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean isMotivationalNotificationsEnabled() {
        return prefs.getBoolean(KEY_MOTIVATIONAL_NOTIFICATIONS_ENABLED, true);
    }

    public void setMotivationalNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOTIVATIONAL_NOTIFICATIONS_ENABLED, enabled).apply();
    }
}
