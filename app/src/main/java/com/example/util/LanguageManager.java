package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.R;

public class LanguageManager {

    private static final String PREF_NAME = "lifeflow_settings";
    private static final String KEY_LANGUAGE = "app_language";

    public static final String LANG_SYSTEM = "sys";
    public static final String LANG_ARABIC = "ar";
    public static final String LANG_ENGLISH = "en";

    public static void setLanguage(Context context, String langCode) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply();

        applyLanguage(langCode, false);
    }

    public static void applySavedLanguage(Context context) {
        // AndroidX automatically persists and restores application locales across app restarts
        // via AppLocalesMetadataHolderService in AndroidManifest.xml.
        // Avoid calling setApplicationLocales during Activity startup to prevent recreation loops.
    }

    private static void applyLanguage(String langCode, boolean isStartupCheck) {
        LocaleListCompat locales;
        if (LANG_ARABIC.equalsIgnoreCase(langCode)) {
            locales = LocaleListCompat.forLanguageTags("ar-u-nu-latn");
        } else if (LANG_ENGLISH.equalsIgnoreCase(langCode)) {
            locales = LocaleListCompat.forLanguageTags("en");
        } else {
            locales = LocaleListCompat.getEmptyLocaleList();
        }

        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        String currentTags = currentLocales.toLanguageTags();
        String targetTags = locales.toLanguageTags();

        // If on startup, be extra conservative to prevent infinite activity recreation loops.
        // If current locales already match the chosen language family, do NOT touch applicationLocales.
        if (isStartupCheck) {
            if (LANG_SYSTEM.equalsIgnoreCase(langCode)) {
                return;
            }
            if (LANG_ARABIC.equalsIgnoreCase(langCode) && currentTags.startsWith("ar")) {
                return;
            }
            if (LANG_ENGLISH.equalsIgnoreCase(langCode) && currentTags.startsWith("en")) {
                return;
            }
        }

        if (!currentTags.equals(targetTags)) {
            AppCompatDelegate.setApplicationLocales(locales);
        }
    }

    public static String getLanguage(Context context) {
        if (context == null) return LANG_SYSTEM;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM);
    }

    public static String getLanguageDisplayName(Context context) {
        if (context == null) return "";
        String lang = getLanguage(context);
        if (LANG_ARABIC.equalsIgnoreCase(lang)) {
            return context.getString(R.string.lang_arabic);
        } else if (LANG_ENGLISH.equalsIgnoreCase(lang)) {
            return context.getString(R.string.lang_english);
        } else {
            return context.getString(R.string.lang_system_default);
        }
    }

    public static boolean isArabic(Context context) {
        if (context == null) return false;
        String lang = getLanguage(context);
        if (LANG_ARABIC.equalsIgnoreCase(lang)) {
            return true;
        } else if (LANG_ENGLISH.equalsIgnoreCase(lang)) {
            return false;
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.os.LocaleList list = context.getResources().getConfiguration().getLocales();
                    if (list != null && !list.isEmpty()) {
                        java.util.Locale currentLocale = list.get(0);
                        return currentLocale != null && "ar".equalsIgnoreCase(currentLocale.getLanguage());
                    }
                } else {
                    java.util.Locale currentLocale = context.getResources().getConfiguration().locale;
                    return currentLocale != null && "ar".equalsIgnoreCase(currentLocale.getLanguage());
                }
            } catch (Exception ignored) {}
            return false;
        }
    }
}
