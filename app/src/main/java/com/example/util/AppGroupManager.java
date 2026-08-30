package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.example.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AppGroupManager {

    private static final String PREFS_NAME = "app_groups_prefs";
    private static final String KEY_GROUP_IDS = "group_ids";
    private static final String KEY_INITIALIZED_V2 = "groups_initialized_smart_v2";

    public static final String DEFAULT_GROUP_SOCIAL_ID = "default_group_social";
    public static final String DEFAULT_GROUP_GAMES_ID = "default_group_games";

    private final SharedPreferences prefs;
    private final Context context;

    public static class AppGroup {
        public String id;
        public String name;
        public Set<String> packages;

        public AppGroup(String id, String name, Set<String> packages) {
            this.id = id;
            this.name = name;
            this.packages = packages;
        }
    }

    public AppGroupManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initDefaultGroupsIfNeeded();
    }

    public void initDefaultGroupsIfNeeded() {
        boolean initialized = prefs.getBoolean(KEY_INITIALIZED_V2, false);
        if (!initialized) {
            createOrUpdateDefaultGroups();
            prefs.edit().putBoolean(KEY_INITIALIZED_V2, true).apply();
        }
    }

    public void createOrUpdateDefaultGroups() {
        Set<String> socialPkgs = detectSocialMediaApps();
        Set<String> gamePkgs = detectGameApps();

        // Save Social Media group
        saveGroupInternal(DEFAULT_GROUP_SOCIAL_ID, context.getString(R.string.default_group_social), socialPkgs);

        // Save Games group
        saveGroupInternal(DEFAULT_GROUP_GAMES_ID, context.getString(R.string.default_group_games), gamePkgs);
    }

    public Set<String> detectGameApps() {
        Set<String> games = new LinkedHashSet<>();
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        Set<String> knownGamePackages = new HashSet<>(Arrays.asList(
                "com.tencent.ig", "com.pubg.krmobile", "com.pubg.imobile", "com.vng.pubgmobile", "com.rekoo.pubgm",
                "com.dts.freefireth", "com.dts.freefiremax",
                "com.activision.callofduty.shooter",
                "com.king.candycrushsaga", "com.king.candycrushsodasaga", "com.king.candycrushjellysaga",
                "com.supercell.clashofclans", "com.supercell.clashroyale", "com.supercell.brawlstars", "com.supercell.hayday",
                "com.roblox.client", "com.mojang.minecraftpe",
                "com.kiloo.subwaysurf", "com.sybogames.chCapture",
                "com.innersloth.spacemafia", "com.mobile.legends", "com.miniclip.eightballpool",
                "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea", "com.HoYoverse.Nap",
                "com.fingersoft.hillclimb", "com.fingersoft.hcr2",
                "com.ludo.king", "com.chess", "com.ea.gp.fifamobile", "com.ea.gp.apexmobile"
        ));

        for (ApplicationInfo packageInfo : packages) {
            // Must have a launch intent (user-facing app)
            if (pm.getLaunchIntentForPackage(packageInfo.packageName) == null) {
                continue;
            }

            String pkgLower = packageInfo.packageName.toLowerCase(Locale.ROOT);

            // 1. Android category check
            boolean isGame = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isGame = (packageInfo.category == ApplicationInfo.CATEGORY_GAME);
            }
            if (!isGame) {
                isGame = (packageInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            }

            // 2. Known game packages check
            if (knownGamePackages.contains(packageInfo.packageName)) {
                isGame = true;
            }

            // 3. Publisher / package name keywords
            if (!isGame) {
                if (pkgLower.contains("supercell.") || pkgLower.contains("gameloft.") || pkgLower.contains("playrix.") ||
                    pkgLower.contains("rovio.") || pkgLower.contains("miniclip.") || pkgLower.contains("epicgames.") ||
                    pkgLower.contains("ea.gp.") || pkgLower.contains(".game") || pkgLower.contains(".games") ||
                    pkgLower.contains("pubg") || pkgLower.contains("freefire") || pkgLower.contains("subway") ||
                    pkgLower.contains("candycrush") || pkgLower.contains("roblox") || pkgLower.contains("minecraft") ||
                    pkgLower.contains("chess") || pkgLower.contains("ludo")) {
                    isGame = true;
                }
            }

            // 4. Label keywords
            if (!isGame) {
                try {
                    String label = pm.getApplicationLabel(packageInfo).toString().toLowerCase(Locale.ROOT);
                    if (label.contains("game") || label.contains("puzzle") || label.contains("racing") ||
                        label.contains("clash") || label.contains("ludo") || label.contains("chess") ||
                        label.contains("runner") || label.contains("quest")) {
                        isGame = true;
                    }
                } catch (Exception ignored) {}
            }

            if (isGame) {
                games.add(packageInfo.packageName);
            }
        }

        return games;
    }

    public Set<String> detectSocialMediaApps() {
        Set<String> socialApps = new LinkedHashSet<>();
        PackageManager pm = context.getPackageManager();

        Set<String> knownSocialPackages = new HashSet<>(Arrays.asList(
                // Facebook & Messenger
                "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
                // Instagram & Threads
                "com.instagram.android", "com.instagram.lite", "com.instagram.threadsapp",
                // WhatsApp
                "com.whatsapp", "com.whatsapp.w4b", "com.gbwhatsapp", "com.whatsapp.plus", "com.yowhatsapp", "com.fmwhatsapp",
                // Telegram
                "org.telegram.messenger", "org.telegram.plus", "org.telegram.messenger.web", "org.thunderdog.challegram", "nekox.messenger",
                // Twitter / X
                "com.twitter.android", "com.x.android", "com.twitter.android.lite",
                // TikTok
                "com.zhiliaoapp.musically", "com.ss.android.ugc.aweme", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill",
                // Snapchat
                "com.snapchat.android",
                // YouTube
                "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.google.android.apps.youtube.kids",
                // Reddit & Pinterest
                "com.reddit.frontpage", "com.andrewshu.android.reddit", "com.pinterest",
                // Professional & Chat
                "com.linkedin.android", "com.linkedin.android.lite", "com.discord", "tv.twitch.android.app",
                "org.thoughtcrime.securesms", "com.viber.voip", "com.tencent.mm", "jp.naver.line.android",
                "com.imo.android.imoim", "com.tumblr", "video.like", "com.kwai.video", "com.bereal.ft",
                "com.clubhouse.app", "xyz.blueskyweb.app", "org.joinmastodon.android", "com.vkontakte.android"
        ));

        // First add installed from known list
        for (String pkg : knownSocialPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                socialApps.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        // Second, scan all installed launchable applications for social category & keywords
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            if (pm.getLaunchIntentForPackage(packageInfo.packageName) == null) {
                continue;
            }

            boolean isSocial = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageInfo.category == ApplicationInfo.CATEGORY_SOCIAL) {
                    isSocial = true;
                }
            }

            if (!isSocial) {
                String pkgLower = packageInfo.packageName.toLowerCase(Locale.ROOT);
                if (pkgLower.contains("social") || pkgLower.contains("messenger") || pkgLower.contains("chat") ||
                    pkgLower.contains("whatsapp") || pkgLower.contains("telegram") || pkgLower.contains("instagram") ||
                    pkgLower.contains("tiktok") || pkgLower.contains("twitter") || pkgLower.contains("discord") ||
                    pkgLower.contains("facebook") || pkgLower.contains("snapchat") || pkgLower.contains("reddit") ||
                    pkgLower.contains("youtube") || pkgLower.contains("viber") || pkgLower.contains("signal")) {
                    isSocial = true;
                }
            }

            if (isSocial) {
                socialApps.add(packageInfo.packageName);
            }
        }

        return socialApps;
    }

    public List<AppGroup> getAllGroups() {
        Set<String> ids = prefs.getStringSet(KEY_GROUP_IDS, new LinkedHashSet<>());
        List<AppGroup> groups = new ArrayList<>();
        
        // Ensure default groups are always present at the top if they exist
        if (ids.contains(DEFAULT_GROUP_SOCIAL_ID)) {
            groups.add(getGroupById(DEFAULT_GROUP_SOCIAL_ID));
        }
        if (ids.contains(DEFAULT_GROUP_GAMES_ID)) {
            groups.add(getGroupById(DEFAULT_GROUP_GAMES_ID));
        }

        for (String id : ids) {
            if (DEFAULT_GROUP_SOCIAL_ID.equals(id) || DEFAULT_GROUP_GAMES_ID.equals(id)) {
                continue;
            }
            groups.add(getGroupById(id));
        }
        return groups;
    }

    public AppGroup getGroupById(String id) {
        String name = prefs.getString("group_" + id + "_name", null);
        
        // If it's a default group, resolve localized name dynamically
        if (DEFAULT_GROUP_SOCIAL_ID.equals(id)) {
            if (name == null || name.equals("Social Media") || name.equals("السوشيال ميديا") || name.equals("وسائل التواصل")) {
                name = context.getString(R.string.default_group_social);
            }
        } else if (DEFAULT_GROUP_GAMES_ID.equals(id)) {
            if (name == null || name.equals("Games") || name.equals("الألعاب")) {
                name = context.getString(R.string.default_group_games);
            }
        } else if (name == null) {
            name = "Group";
        }

        Set<String> pkgs = prefs.getStringSet("group_" + id + "_pkgs", new HashSet<>());
        return new AppGroup(id, name, pkgs);
    }

    private void saveGroupInternal(String id, String name, Set<String> packages) {
        Set<String> ids = new LinkedHashSet<>(prefs.getStringSet(KEY_GROUP_IDS, new LinkedHashSet<>()));
        ids.add(id);

        prefs.edit()
             .putStringSet(KEY_GROUP_IDS, ids)
             .putString("group_" + id + "_name", name)
             .putStringSet("group_" + id + "_pkgs", packages)
             .apply();
    }

    public void saveGroup(String id, String name, Set<String> packages) {
        saveGroupInternal(id, name, packages);
    }

    public void deleteGroup(String id) {
        Set<String> ids = new LinkedHashSet<>(prefs.getStringSet(KEY_GROUP_IDS, new LinkedHashSet<>()));
        ids.remove(id);

        prefs.edit()
             .putStringSet(KEY_GROUP_IDS, ids)
             .remove("group_" + id + "_name")
             .remove("group_" + id + "_pkgs")
             .apply();
    }
}

