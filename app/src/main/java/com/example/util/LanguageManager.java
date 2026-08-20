package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

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
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply();

        applyLanguage(langCode);
    }

    public static void applySavedLanguage(Context context) {
        String langCode = getLanguage(context);
        applyLanguage(langCode);
    }

    private static void applyLanguage(String langCode) {
        LocaleListCompat locales;
        if (LANG_ARABIC.equalsIgnoreCase(langCode)) {
            locales = LocaleListCompat.forLanguageTags("ar-u-nu-latn");
        } else if (LANG_ENGLISH.equalsIgnoreCase(langCode)) {
            locales = LocaleListCompat.forLanguageTags("en");
        } else {
            locales = LocaleListCompat.getEmptyLocaleList();
        }
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM);
    }

    public static String getLanguageDisplayName(Context context) {
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
        String lang = getLanguage(context);
        if (LANG_ARABIC.equalsIgnoreCase(lang)) {
            return true;
        } else if (LANG_ENGLISH.equalsIgnoreCase(lang)) {
            return false;
        } else {
            java.util.Locale currentLocale = context.getResources().getConfiguration().getLocales().get(0);
            return currentLocale != null && "ar".equalsIgnoreCase(currentLocale.getLanguage());
        }
    }
}
