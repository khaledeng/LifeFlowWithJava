package com.example.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.MainActivity;
import com.example.R;
import com.example.data.AppDatabase;
import com.example.data.dao.DailyProgressDao;
import com.example.data.entity.Activity;
import com.example.data.entity.ActivityCategory;
import com.example.data.entity.DailyProgress;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class ProgressNotificationManager {

    private static final String CHANNEL_ID = "lifeflow_goal_milestones_v1";
    private static final Random random = new Random();

    public static void checkAndNotifyMilestone(Context context, Activity activity, long totalTodayMillis) {
        if (context == null || activity == null) return;
        SubscriptionManager subManager = new SubscriptionManager(context);
        if (!subManager.isNotificationsEnabled() || !subManager.isMotivationalNotificationsEnabled()) {
            return;
        }
        if (activity.getCategory() == ActivityCategory.NEUTRAL || activity.getExpectedHoursPerDay() <= 0) {
            return;
        }

        long targetMillis = (long) (activity.getExpectedHoursPerDay() * 3600.0 * 1000.0);
        if (targetMillis <= 0) return;

        double percentage = ((double) totalTodayMillis / (double) targetMillis) * 100.0;
        int pctInt = (int) percentage;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String dateKey = sdf.format(new Date());

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
                DailyProgressDao progressDao = db.dailyProgressDao();

                DailyProgress dp = progressDao.getProgressForActivityAndDateSync(activity.getId(), dateKey);
                int lastNotified = (dp != null) ? dp.getLastNotifiedThreshold() : 0;

                int milestoneToNotify = 0;
                if (activity.getCategory() == ActivityCategory.INCREASE) {
                    if (pctInt >= 200 && lastNotified < 200) {
                        milestoneToNotify = 200;
                    } else if (pctInt >= 175 && lastNotified < 175) {
                        milestoneToNotify = 175;
                    } else if (pctInt >= 150 && lastNotified < 150) {
                        milestoneToNotify = 150;
                    } else if (pctInt >= 125 && lastNotified < 125) {
                        milestoneToNotify = 125;
                    } else if (pctInt >= 100 && lastNotified < 100) {
                        milestoneToNotify = 100;
                    } else if (pctInt >= 75 && lastNotified < 75) {
                        milestoneToNotify = 75;
                    } else if (pctInt >= 50 && lastNotified < 50) {
                        milestoneToNotify = 50;
                    } else if (pctInt >= 25 && lastNotified < 25) {
                        milestoneToNotify = 25;
                    }
                } else if (activity.getCategory() == ActivityCategory.DECREASE) {
                    if (pctInt >= 100 && lastNotified < 100) {
                        milestoneToNotify = 100;
                    } else if (pctInt >= 75 && lastNotified < 75) {
                        milestoneToNotify = 75;
                    } else if (pctInt >= 50 && lastNotified < 50) {
                        milestoneToNotify = 50;
                    } else if (pctInt >= 25 && lastNotified < 25) {
                        milestoneToNotify = 25;
                    }
                }

                if (milestoneToNotify > 0) {
                    if (dp == null) {
                        dp = new DailyProgress(activity.getId(), dateKey, milestoneToNotify);
                    } else {
                        dp.setLastNotifiedThreshold(milestoneToNotify);
                    }
                    progressDao.insertOrUpdate(dp);

                    sendNotification(context, activity, milestoneToNotify);
                }
            } catch (Exception ignored) {}
        });
    }

    private static void sendNotification(Context context, Activity activity, int milestone) {
        Context appContext = context.getApplicationContext();
        NotificationManager nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createNotificationChannel(appContext, nm);

        String actName = activity.getName();
        boolean isArabic = LanguageManager.isArabic(appContext);

        String[][] options = getNotificationOptions(activity.getCategory(), milestone, isArabic);
        if (options == null || options.length == 0) return;

        String[] selectedOption = options[random.nextInt(options.length)];
        String title = selectedOption[0];
        String body = selectedOption[1].replace("[Activity]", actName).replace("[activity]", actName);

        Intent intent = new Intent(appContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                appContext,
                (int) activity.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int color = IconHelper.parseColorOrDefault(activity.getColorHex(), 0xFF39D353);
        Bitmap largeIcon = IconHelper.createNotificationLargeIcon(appContext, activity.getIconName(), color);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_time)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(color)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        int notificationId = 20000 + (int) activity.getId();
        nm.notify(notificationId, builder.build());
    }

    private static String[][] getNotificationOptions(ActivityCategory category, int milestone, boolean isArabic) {
        if (category == ActivityCategory.INCREASE) {
            if (isArabic) {
                switch (milestone) {
                    case 25:
                        return new String[][]{
                                {"🔥 بداية جامدة!", "أول 25% خلصوا! إنت بدأت صح في [Activity]، يلا نكمل ونجيب الهدف كله."},
                                {"💪 كمل يا بطل!", "وصلت لأول 25% في [Activity]، وأهم حاجة إنك بدأت. متوقفش دلوقتي!"},
                                {"🚀 الـ Flow بدأ!", "ربع الهدف خلص في [Activity]! خد الزخم ده وكمل عليه."},
                                {"👊 أول خطوة اتعملت!", "25% في الجيب في [Activity]. دلوقتي نكمل واحدة واحدة لحد الـ 100%."},
                                {"🔥 أنت قدها!", "بدأت كويس جدًا في [Activity]. كمل، والهدف هيقرب منك دقيقة بعد دقيقة."},
                                {"🏃 يلا بينا!", "أول جزء خلص في [Activity]. متفكرش في الباقي، ركز في الخطوة اللي جاية بس."}
                        };
                    case 50:
                        return new String[][]{
                                {"🔥 نص الطريق!", "وصلت 50% في [Activity]! أنت قطعت نص المشوار، متسيبش تعبك يضيع. كمل!"},
                                {"💪 عاش عليك!", "نص الهدف اتحقق في [Activity]! فاضل النص التاني، وإنت أثبت إنك تقدر."},
                                {"🚀 كمل بنفس القوة!", "وصلت للنص يا بطل في [Activity]. خليك على نفس الرتم وهتجيب الـ 100%."},
                                {"👊 متوقفش هنا!", "50% خلصوا في [Activity]. أنت دخلت في الجد، يلا نكمل للآخر!"},
                                {"🔥 أنت ماشي صح!", "نص هدفك في [Activity] في جيبك. فاضل شوية ونقفلها النهارده."},
                                {"🏆 قربت!", "نصف الهدف اتحقق في [Activity]. كمل دلوقتي عشان لما تخلص تقول \"أنا عملتها\"."}
                        };
                    case 75:
                        return new String[][]{
                                {"🔥 قربت جدًا!", "فاضلك 25% بس في [Activity]! آخر دفعة وتحقق هدفك. متقفش دلوقتي!"},
                                {"🏁 آخر لفة يا بطل!", "وصلت 75% في [Activity]! كل اللي فاضل شوية تركيز وتقول Done."},
                                {"💪 متستسلمش دلوقتي!", "قطعت 75% من الطريق في [Activity]. إنت أقرب للهدف من أي وقت فات."},
                                {"🚀 دوس كمان شوية!", "فاضلك ربع الهدف بس في [Activity]. كمل بنفس القوة وهتخلصه!"},
                                {"🔥 النهاية قدامك!", "75% خلصوا في [Activity]! دلوقتي وقت آخر Push. يلا نجيب الـ 100%."},
                                {"👊 خلصها!", "أنت وصلت لمرحلة ناس كتير بتقف عندها في [Activity]. كمل للآخر وخد الـ 100%."}
                        };
                    case 100:
                        return new String[][]{
                                {"🏆 إنت عملتها!", "100% من هدفك في [Activity] النهارده! عاش يا بطل. لو لسه عندك طاقة، ورينا الـ Extra 🔥"},
                                {"🎉 الهدف اتقفل!", "حققت هدفك بالكامل في [Activity]. كل دقيقة اشتغلتها اتحسبت. كمل لو عايز تكسر هدفك!"},
                                {"🔥 GOAL CRUSHED!", "وصلت للـ 100% في [Activity]! ده إنجاز النهارده. تحب تزود وتعمل Extra Time؟"},
                                {"👑 النهارده بتاعك!", "خلصت هدفك يا بطل في [Activity]. مفيش أحسن من كده! ولو تقدر تكمل، زود كمان."},
                                {"🚀 100% يا معلم!", "الهدف اتحقق في [Activity]! بس لو الـ Flow لسه شغال، مفيش مانع نزود شوية 😉"},
                                {"💪 أثبت لنفسك إنك تقدر!", "وصلت لهدفك النهارده في [Activity]. افتكر الإحساس ده، وخلينا نعمل أكتر بكرة."}
                        };
                    case 125:
                        return new String[][]{
                                {"🔥 لسه مكمل؟ جامد!", "عديت هدفك في [Activity] بـ25%. أنت مش بس حققت الهدف، أنت بتزود عليه."}
                        };
                    case 150:
                        return new String[][]{
                                {"🏆 إيه القوة دي؟", "وصلت لـ150% في [Activity]! الهدف خلص من بدري وإنت لسه مكمل."}
                        };
                    case 175:
                        return new String[][]{
                                {"🚀 إنت داخل على Level تاني!", "وصلت لـ175% من هدفك في [Activity]. واضح إنك داخل تعمل يوم مختلف."}
                        };
                    case 200:
                        return new String[][]{
                                {"👑 ضاعفت هدفك!", "200% يا بطل في [Activity]! ده مش مجرد تحقيق هدف، ده كسر للهدف."}
                        };
                }
            } else {
                // English INCREASE
                switch (milestone) {
                    case 25:
                        return new String[][]{
                                {"🔥 Strong start.", "You're 25% in [Activity]. Keep that momentum going."},
                                {"💪 You're moving.", "First quarter done in [Activity]. Don't lose the rhythm."},
                                {"🚀 Here we go.", "25% down in [Activity]. Keep building."},
                                {"👊 Nice start.", "You showed up for [Activity]. Now keep going."}
                        };
                    case 50:
                        return new String[][]{
                                {"🔥 Halfway there.", "You've done half the work in [Activity]. Keep pushing."},
                                {"💪 You're on a roll.", "50% done in [Activity]. Don't let the momentum die now."},
                                {"🚀 Keep the pressure on.", "You're halfway to today's goal in [Activity]."},
                                {"👀 Look at you go.", "Half the goal in [Activity] is already behind you."}
                        };
                    case 75:
                        return new String[][]{
                                {"🔥 Final stretch.", "You're 75% in [Activity]. One last push."},
                                {"🏁 Almost there.", "You've come this far in [Activity]. Finish what you started."},
                                {"💪 Don't stop now.", "The goal for [Activity] is right in front of you."},
                                {"🚀 One more push.", "Just 25% left in [Activity]. You've got this."}
                        };
                    case 100:
                        return new String[][]{
                                {"🏆 You did it.", "Today's goal for [Activity] is officially yours. Keep going if you've got more in the tank."},
                                {"🔥 Goal crushed.", "100% done in [Activity]! Anything from here is extra."},
                                {"👑 That's a win.", "You set the goal for [Activity]. You hit the goal. Simple as that."},
                                {"🚀 Mission complete.", "You made it to 100% in [Activity]! Want to see how far you can take it?"},
                                {"💪 Proud of this one.", "Today's target for [Activity] is done. Keep the momentum if you want to go further."}
                        };
                    case 125:
                        return new String[][]{
                                {"🔥 Still going? Epic!", "You passed your goal in [Activity] by 25%. You're pushing past limits!"}
                        };
                    case 150:
                        return new String[][]{
                                {"🏆 What a streak!", "Reached 150% in [Activity]! The goal was finished long ago and you're still crushing it."}
                        };
                    case 175:
                        return new String[][]{
                                {"🚀 Next level performance!", "Reached 175% of your goal in [Activity]. Today is turning into something legendary."}
                        };
                    case 200:
                        return new String[][]{
                                {"👑 Doubled your goal!", "200% in [Activity]! That's not just hitting a target, that's shattering it."}
                        };
                }
            }
        } else if (category == ActivityCategory.DECREASE) {
            if (isArabic) {
                switch (milestone) {
                    case 25:
                        return new String[][]{
                                {"👀 لسه تمام!", "استخدمت 25% بس من وقتك في [Activity]. استمتع، بس خليك أنت المتحكم."},
                                {"💪 تحت السيطرة!", "ربع الوقت خلص في [Activity] ولسه الدنيا تمام. متخليش الوقت يسحبك."},
                                {"🎯 أنت اللي بتتحكم!", "استخدمت جزء صغير من وقتك في [Activity]. لما تخلص، ارجع لحاجة مهمة."}
                        };
                    case 50:
                        return new String[][]{
                                {"💪 خليك مسيطر!", "نص وقتك راح في [Activity]. إيه رأيك نقفل هنا ونكسب شوية وقت لحاجة أهم؟"},
                                {"👀 خد بالك!", "وصلت للنص في [Activity]. لسه تقدر تختار إنك تحول وقتك لحاجة هتفيدك."},
                                {"🔥 القرار في إيدك!", "50% خلصوا في [Activity]. تقدر تكمل، أو تاخد القرار اللي هيخلي يومك أحسن."},
                                {"🚀 وقت التحويل!", "نص الوقت خلص في [Activity]. إيه رأيك ندي الـ Work أو Study فرصة؟"}
                        };
                    case 75:
                        return new String[][]{
                                {"⚠️ قربنا!", "استخدمت 75% من وقتك في [Activity]. فاضلك شوية، وبعدها خلينا نرجع لحاجات أهم."},
                                {"🔥 آخر فرصة!", "فاضلك 25% بس من الوقت المسموح لـ [Activity]. متخليش ساعتك تكسبك."},
                                {"💪 أنت تقدر توقف!", "وصلت 75% في [Activity]. لو وقفت دلوقتي، أنت اللي كسبت الوقت."},
                                {"👊 خد القرار!", "قربت من الـ Limit في [Activity]. اقفلها دلوقتي وخد وقتك لحاجة هتفرحك آخر اليوم."}
                        };
                    case 100:
                        return new String[][]{
                                {"🛑 وقتك خلص!", "وصلت للـ Limit اللي حددته لنفسك في [Activity]. عاش إنك حاولت تلتزم—دلوقتي نكسب الوقت ده في حاجة تانية."},
                                {"💪 أنت قلت ساعتين!", "وصلت للوقت اللي أنت بنفسك حددته لـ [Activity]. خليك قد قرارك وروح اعمل حاجة تانية."},
                                {"🔥 وقف هنا واكسب!", "الـ Limit خلص في [Activity]. إقفال النشاط دلوقتي هو المكسب الحقيقي."},
                                {"👑 القرار ليك!", "وصلت للحد. أنت اللي بتحكم في وقتك، مش [Activity]."},
                                {"🚀 وقت نغيّر!", "خلص وقت [Activity] النهارده. يلا نستثمر باقي اليوم في حاجة أهم."}
                        };
                }
            } else {
                // English DECREASE
                switch (milestone) {
                    case 25:
                        return new String[][]{
                                {"👀 Still in control.", "You've used 25% of your limit for [Activity]. Enjoy it, just stay aware."},
                                {"💪 You're good.", "25% used in [Activity]. Keep yourself in the driver's seat."}
                        };
                    case 50:
                        return new String[][]{
                                {"⏱️ Halfway through limit.", "Halfway through your limit for [Activity]. Maybe it's a good time to switch gears."},
                                {"👀 Half gone.", "You've got time left in [Activity], but don't let it disappear."},
                                {"💪 Your call.", "You're halfway through [Activity]. You can keep going or take your time somewhere else."}
                        };
                    case 75:
                        return new String[][]{
                                {"⚠️ Getting close.", "75% of your limit for [Activity] is gone. Time to think about what's next."},
                                {"🔥 Last stretch.", "You've got 25% left for [Activity]. Make it count."},
                                {"👊 Take back the wheel.", "You're close to your limit in [Activity]. You decide when this ends."}
                        };
                    case 100:
                        return new String[][]{
                                {"🛑 You set the limit.", "You just reached your limit for [Activity]. Now stick to the plan."},
                                {"👑 You said you'd stop here.", "And here we are in [Activity]. Keep the promise to yourself."},
                                {"🔥 That's enough for today.", "Your limit for [Activity] is done. Time to spend the rest somewhere that matters."},
                                {"💪 You control the time.", "Today's limit for [Activity] is reached. What's next?"}
                        };
                }
            }
        }
        return null;
    }

    private static void createNotificationChannel(Context context, NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Goal & Habit Milestones",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for 25%, 50%, 75%, 100%, and Extra Time activity goals");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }
    }
}
