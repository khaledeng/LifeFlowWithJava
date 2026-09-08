package com.example.util;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LanguageAndIconTest {

    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testLanguageManagerPreferences() {
        LanguageManager.setLanguage(context, LanguageManager.LANG_ARABIC);
        Assert.assertEquals(LanguageManager.LANG_ARABIC, LanguageManager.getLanguage(context));
        Assert.assertTrue(LanguageManager.isArabic(context));

        LanguageManager.setLanguage(context, LanguageManager.LANG_ENGLISH);
        Assert.assertEquals(LanguageManager.LANG_ENGLISH, LanguageManager.getLanguage(context));
        Assert.assertFalse(LanguageManager.isArabic(context));
    }

    @Test
    public void testEmojiDetectionAndExtraction() {
        Assert.assertTrue(IconHelper.isEmojiIcon("emoji:🔥"));
        Assert.assertEquals("🔥", IconHelper.extractEmoji("emoji:🔥"));

        Assert.assertTrue(IconHelper.isEmojiIcon("🎯"));
        Assert.assertEquals("🎯", IconHelper.extractEmoji("🎯"));

        Assert.assertFalse(IconHelper.isEmojiIcon("ic_work"));
        Assert.assertFalse(IconHelper.isEmojiIcon("ic_sleep"));
    }
}
