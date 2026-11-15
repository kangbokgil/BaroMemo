package com.baromemo.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

/** 매일 정해진 시간에 "지금 어때요?" 알림을 띄우는 예약. */
public final class Reminder {
    private static final String PREFS = "reminder";

    private Reminder() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context ctx) {
        return prefs(ctx).getBoolean("on", false);
    }

    public static int hour(Context ctx) {
        return prefs(ctx).getInt("hour", 21);
    }

    public static int minute(Context ctx) {
        return prefs(ctx).getInt("minute", 0);
    }

    public static String label(Context ctx) {
        if (!isOn(ctx)) return "기록 알림: 꺼짐";
        return String.format(Locale.KOREA, "기록 알림: 매일 %02d:%02d", hour(ctx), minute(ctx));
    }

    public static void set(Context ctx, boolean on, int hour, int minute) {
        prefs(ctx).edit()
                .putBoolean("on", on)
                .putInt("hour", hour)
                .putInt("minute", minute)
                .apply();
        schedule(ctx);
    }

    public static void turnOff(Context ctx) {
        prefs(ctx).edit().putBoolean("on", false).apply();
        schedule(ctx);
    }

    /** 다음 알림 시각을 다시 예약한다. 꺼져 있으면 취소만 한다. */
    public static void schedule(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 9,
                new Intent(ctx, ReminderReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!isOn(ctx)) return;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour(ctx));
        c.set(Calendar.MINUTE, minute(ctx));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
    }
}
