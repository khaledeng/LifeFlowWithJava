package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class SubscriptionManager {

    private static final String PREFS_NAME = "lifeflow_prefs";
    private static final String KEY_IS_PRO = "key_is_pro_member";
    private static final String KEY_PRO_EXPIRY_TIMESTAMP = "key_pro_expiry_timestamp";
    private static final String KEY_PRO_PLAN_TYPE = "key_pro_plan_type";
    private static final String KEY_USED_CODES = "key_used_codes";
    private static final String KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled";
    private static final String KEY_MOTIVATIONAL_NOTIFICATIONS_ENABLED = "key_motivational_notifications_enabled";

    public static final int FREE_TIER_MAX_ACTIVITIES = 3;
    private static final String SECRET_KEY = "LIFEFLOW_ADMIN_SECRET_KEY_2026";

    public enum CodeType {
        MONTHLY, // 30 Days
        YEARLY,  // 365 Days
        LIFETIME // Permanent
    }

    public enum ActivationResult {
        SUCCESS_MONTHLY,
        SUCCESS_YEARLY,
        SUCCESS_LIFETIME,
        INVALID_CODE,
        ALREADY_USED
    }

    private final SharedPreferences prefs;

    public SubscriptionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isPro() {
        return true;
    }

    public void setPro(boolean isPro) {
        if (isPro) {
            // Default 30 days if manual toggle
            long expiry = System.currentTimeMillis() + (30L * 24 * 3600 * 1000);
            prefs.edit()
                    .putBoolean(KEY_IS_PRO, true)
                    .putLong(KEY_PRO_EXPIRY_TIMESTAMP, expiry)
                    .putString(KEY_PRO_PLAN_TYPE, "MONTHLY")
                    .apply();
        } else {
            prefs.edit()
                    .putBoolean(KEY_IS_PRO, false)
                    .putLong(KEY_PRO_EXPIRY_TIMESTAMP, 0L)
                    .putString(KEY_PRO_PLAN_TYPE, "FREE")
                    .apply();
        }
    }

    public long getProExpiryTimestamp() {
        return prefs.getLong(KEY_PRO_EXPIRY_TIMESTAMP, 0L);
    }

    public String getProPlanType() {
        if (!isPro()) return "FREE";
        return prefs.getString(KEY_PRO_PLAN_TYPE, "MONTHLY");
    }

    public int getDaysRemaining() {
        if (!isPro()) return 0;
        long expiry = getProExpiryTimestamp();
        if (expiry == 0L) return -1; // Unlimited/Lifetime

        long diff = expiry - System.currentTimeMillis();
        if (diff <= 0) return 0;

        return (int) Math.ceil((double) diff / (1000.0 * 3600 * 24));
    }

    public String getProExpiryDetailsFormatted(Context context) {
        return "النسخة كاملة مجاناً 🎉 (جميع الميزات مفرودة للجميع)";
    }

    /**
     * Validates and applies an activation code.
     */
    public ActivationResult validateAndApplyCode(String rawCode) {
        if (rawCode == null) return ActivationResult.INVALID_CODE;
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) return ActivationResult.INVALID_CODE;

        Set<String> usedCodes = prefs.getStringSet(KEY_USED_CODES, new HashSet<>());
        if (usedCodes.contains(code)) {
            return ActivationResult.ALREADY_USED;
        }

        // Check for Master Promo Codes
        CodeType activatedType = null;
        if ("PRO-MONTHLY-30".equals(code) || "LIFEFLOW-30D".equals(code)) {
            activatedType = CodeType.MONTHLY;
        } else if ("PRO-YEARLY-365".equals(code) || "LIFEFLOW-365D".equals(code)) {
            activatedType = CodeType.YEARLY;
        } else if ("PRO-LIFETIME-VIP".equals(code) || "LIFEFLOW-VIP".equals(code)) {
            activatedType = CodeType.LIFETIME;
        } else {
            // Check algorithmic signature format: PRO30-XXXXXX-CCCC or PRO365-XXXXXX-CCCC or PROLIFE-XXXXXX-CCCC
            String[] parts = code.split("-");
            if (parts.length == 3) {
                String prefix = parts[0];
                String randomPart = parts[1];
                String hashPart = parts[2];

                String base = prefix + "-" + randomPart;
                String expectedHash = calculateChecksum(base + SECRET_KEY);

                if (hashPart.equalsIgnoreCase(expectedHash)) {
                    if ("PRO30".equals(prefix)) {
                        activatedType = CodeType.MONTHLY;
                    } else if ("PRO365".equals(prefix)) {
                        activatedType = CodeType.YEARLY;
                    } else if ("PROLIFE".equals(prefix)) {
                        activatedType = CodeType.LIFETIME;
                    }
                }
            }
        }

        if (activatedType == null) {
            return ActivationResult.INVALID_CODE;
        }

        // Apply Activation
        long now = System.currentTimeMillis();
        long currentExpiry = isPro() ? getProExpiryTimestamp() : 0L;
        long baseTime = (currentExpiry > now) ? currentExpiry : now;

        long newExpiry = 0L;
        String planType = "MONTHLY";

        if (activatedType == CodeType.MONTHLY) {
            newExpiry = baseTime + (30L * 24 * 3600 * 1000);
            planType = "MONTHLY";
        } else if (activatedType == CodeType.YEARLY) {
            newExpiry = baseTime + (365L * 24 * 3600 * 1000);
            planType = "YEARLY";
        } else if (activatedType == CodeType.LIFETIME) {
            newExpiry = 0L;
            planType = "LIFETIME";
        }

        Set<String> newUsedCodes = new HashSet<>(usedCodes);
        newUsedCodes.add(code);

        prefs.edit()
                .putBoolean(KEY_IS_PRO, true)
                .putLong(KEY_PRO_EXPIRY_TIMESTAMP, newExpiry)
                .putString(KEY_PRO_PLAN_TYPE, planType)
                .putStringSet(KEY_USED_CODES, newUsedCodes)
                .apply();

        if (activatedType == CodeType.MONTHLY) {
            return ActivationResult.SUCCESS_MONTHLY;
        } else if (activatedType == CodeType.YEARLY) {
            return ActivationResult.SUCCESS_YEARLY;
        } else {
            return ActivationResult.SUCCESS_LIFETIME;
        }
    }

    /**
     * Generates a valid activation code for admins to send to customers.
     */
    public static String generateActivationCode(CodeType type) {
        String prefix = "PRO30";
        if (type == CodeType.YEARLY) prefix = "PRO365";
        else if (type == CodeType.LIFETIME) prefix = "PROLIFE";

        String randomPart = getRandomAlphaNumeric(6);
        String base = prefix + "-" + randomPart;
        String hash = calculateChecksum(base + SECRET_KEY);

        return base + "-" + hash;
    }

    private static String getRandomAlphaNumeric(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Omit ambiguous chars like O, 0, I, 1
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String calculateChecksum(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.substring(0, 4); // First 4 chars of MD5
        } catch (NoSuchAlgorithmException e) {
            return "9999";
        }
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
