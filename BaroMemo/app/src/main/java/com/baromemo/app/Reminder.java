package com.baromemo.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 기록 알림: 원하는 시간을 원하는 만큼 정해 두면 매일 그 시간에 "지금 어때요?" 알림이 뜬다. 없으면 안 뜬다. */
public final class Reminder {
    private static final String PREFS = "reminder";
    private static final String KEY_TIMES = "times"; // "분,분,분" (하루 중 몇 분째인지)

    private Reminder() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 정해 둔 알림 시간들 (하루 중 분 단위, 이른 순). */
    public static List<Integer> times(Context ctx) {
        SharedPreferences p = prefs(ctx);
        List<Integer> out = new ArrayList<>();
        String raw = p.getString(KEY_TIMES, null);
        if (raw == null) {
            // 예전 버전(하루 한 번) 설정 이어받기
            if (p.getBoolean("on", false)) {
                out.add(p.getInt("hour", 21) * 60 + p.getInt("minute", 0));
            }
            return out;
        }
        for (String s : raw.split(",")) {
            if (s.isEmpty()) continue;
            try {
                int v = Integer.parseInt(s);
                if (v >= 0 && v < 24 * 60 && !out.contains(v)) out.add(v);
            } catch (NumberFormatException e) {
                // 잘못된 값은 건너뛴다
            }
        }
        Collections.sort(out);
        return out;
    }

    private static void save(Context ctx, List<Integer> times) {
        StringBuilder sb = new StringBuilder();
        for (int t : times) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        prefs(ctx).edit().putString(KEY_TIMES, sb.toString()).putBoolean("on", false).apply();
        schedule(ctx);
    }

    public static void add(Context ctx, int hour, int minute) {
        List<Integer> t = times(ctx);
        int v = hour * 60 + minute;
        if (!t.contains(v)) t.add(v);
        Collections.sort(t);
        save(ctx, t);
    }

    public static void remove(Context ctx, int minutes) {
        List<Integer> t = times(ctx);
        t.remove(Integer.valueOf(minutes));
        save(ctx, t);
    }

    public static void clear(Context ctx) {
        save(ctx, new ArrayList<Integer>());
    }

    public static boolean isOn(Context ctx) {
        return !times(ctx).isEmpty();
    }

    public static String format(int minutes) {
        return String.format(Locale.KOREA, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public static String label(Context ctx) {
        List<Integer> t = times(ctx);
        if (t.isEmpty()) return "기록 알림: 꺼짐";
        if (t.size() <= 2) {
            StringBuilder sb = new StringBuilder("기록 알림: ");
            for (int i = 0; i < t.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(format(t.get(i)));
            }
            return sb.toString();
        }
        return "기록 알림: 하루 " + t.size() + "번";
    }

    /** 가장 가까운 다음 알림 시각을 예약한다. 정해 둔 시간이 없으면 취소만 한다. */
    public static void schedule(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 9,
                new Intent(ctx, ReminderReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        List<Integer> t = times(ctx);
        if (t.isEmpty()) return;

        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (int minutes : t) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, minutes / 60);
            c.set(Calendar.MINUTE, minutes % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_MONTH, 1);
            if (c.getTimeInMillis() < next) next = c.getTimeInMillis();
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
    }
}
