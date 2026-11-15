package com.baromemo.app;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 오늘과 최근 7일의 기분·기록 요약을 글로 만든다. 기분은 메모 맨 앞 이모지로 센다. */
public final class Summary {
    // 순서 = 점수 1~5
    private static final String[] EMO = {"😡", "😔", "😐", "🙂", "😊"};

    private Summary() {}

    /** 메모가 기분 이모지로 시작하면 1~5점, 아니면 0. */
    static int score(String text) {
        for (int i = 0; i < EMO.length; i++) {
            if (text.startsWith(EMO[i])) return i + 1;
        }
        return 0;
    }

    private static long startOfDay(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, -daysAgo);
        return c.getTimeInMillis();
    }

    static String face(double avg) {
        int i = (int) Math.round(avg);
        if (i < 1) i = 1;
        if (i > 5) i = 5;
        return EMO[i - 1];
    }

    public static String build(Context ctx) {
        List<Note> notes = NoteStore.load(ctx); // 최신이 앞
        SimpleDateFormat dayFmt = new SimpleDateFormat("M/d (E)", Locale.KOREA);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.KOREA);
        StringBuilder sb = new StringBuilder();

        // 오늘
        long today = startOfDay(0);
        List<Note> todays = new ArrayList<>();
        for (Note n : notes) {
            if (n.time >= today) todays.add(n);
        }
        Collections.reverse(todays); // 시간순

        sb.append("■ 오늘 ").append(dayFmt.format(new Date())).append('\n');
        if (todays.isEmpty()) {
            sb.append("아직 기록이 없어요.\n");
        } else {
            int[] count = new int[EMO.length];
            int sum = 0;
            int moods = 0;
            for (Note n : todays) {
                int s = score(n.text);
                if (s > 0) {
                    count[s - 1]++;
                    sum += s;
                    moods++;
                }
            }
            sb.append(String.format(Locale.KOREA, "기록 %d개 · 기분 기록 %d번\n", todays.size(), moods));
            if (moods > 0) {
                double avg = (double) sum / moods;
                sb.append(String.format(Locale.KOREA, "평균 기분 %s (%.1f / 5)\n", face(avg), avg));
                for (int i = EMO.length - 1; i >= 0; i--) {
                    if (count[i] > 0) sb.append(EMO[i]).append(' ').append(count[i]).append("번  ");
                }
                sb.append('\n');
            } else {
                sb.append("기분 이모지를 누른 기록이 없어요.\n");
            }

            sb.append("\n■ 오늘의 흐름\n");
            for (Note n : todays) {
                sb.append(timeFmt.format(new Date(n.time))).append("  ")
                        .append(n.photo != null ? "📷 " : "").append(n.text).append('\n');
            }
        }

        // 최근 7일
        sb.append("\n■ 최근 7일\n");
        for (int d = 6; d >= 0; d--) {
            long start = startOfDay(d);
            long end = startOfDay(d - 1);
            int total = 0;
            int sum = 0;
            int moods = 0;
            for (Note n : notes) {
                if (n.time < start || n.time >= end) continue;
                total++;
                int s = score(n.text);
                if (s > 0) {
                    sum += s;
                    moods++;
                }
            }
            sb.append(dayFmt.format(new Date(start))).append("  ");
            if (moods > 0) {
                double avg = (double) sum / moods;
                sb.append(String.format(Locale.KOREA, "%s %.1f", face(avg), avg));
            } else {
                sb.append("–");
            }
            sb.append("  · 기록 ").append(total).append("개\n");
        }

        // 해시태그
        List<Map.Entry<String, Integer>> tags = Tags.counts(notes);
        if (!tags.isEmpty()) {
            sb.append("\n■ 자주 쓴 해시태그\n");
            for (int i = 0; i < tags.size() && i < 10; i++) {
                sb.append(tags.get(i).getKey()).append("  ").append(tags.get(i).getValue()).append("번\n");
            }
        }
        return sb.toString();
    }

    /** 하루치 메모의 짧은 요약: 기록 수, 평균 기분, 기분별 횟수, 그날 쓴 해시태그. */
    public static String day(List<Note> dayNotes) {
        if (dayNotes.isEmpty()) return "기록 없음";
        int[] count = new int[EMO.length];
        int sum = 0;
        int moods = 0;
        Set<String> tags = new LinkedHashSet<>();
        for (Note n : dayNotes) {
            int s = score(n.text);
            if (s > 0) {
                count[s - 1]++;
                sum += s;
                moods++;
            }
            tags.addAll(Tags.extract(n.text));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("기록 ").append(dayNotes.size()).append("개");
        if (moods > 0) {
            double avg = (double) sum / moods;
            sb.append(String.format(Locale.KOREA, " · 평균 기분 %s %.1f", face(avg), avg));
            sb.append('\n');
            for (int i = EMO.length - 1; i >= 0; i--) {
                if (count[i] > 0) sb.append(EMO[i]).append(' ').append(count[i]).append("번  ");
            }
        }
        if (!tags.isEmpty()) {
            sb.append('\n');
            for (String t : tags) sb.append(t).append(' ');
        }
        return sb.toString().trim();
    }
}
