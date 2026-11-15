package com.baromemo.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 사용자가 고른 설정: 어떤 기능을 켤지, 어떤 테마 색을 쓸지. */
public final class Prefs {
    public static final String MOOD = "mood";
    public static final String STICKER = "sticker";
    public static final String PHOTO = "photo";
    public static final String VOICE = "voice";
    public static final String DRAW = "draw";
    public static final String TAGS = "tags";
    public static final String CALENDAR = "calendar";
    public static final String SUMMARY = "summary";
    public static final String PAPER = "paper";

    /** 설정 화면에 나오는 순서. */
    public static final String[] KEYS = {MOOD, TAGS, PHOTO, DRAW, VOICE, STICKER, CALENDAR, SUMMARY, PAPER};
    public static final String[] LABELS = {
            "기분 이모지 버튼",
            "해시태그 버튼",
            "사진 첨부",
            "간단한 그림 그리기",
            "받아쓰기 (음성 입력)",
            "내용에 맞는 자동 스티커",
            "달력 탭",
            "요약 탭",
            "메모마다 종이 색 다르게",
    };

    public static final String[] THEME_NAMES = {"심플", "코랄", "민트", "라벤더", "블루", "핑크"};
    private static final int[] THEME_STYLES = {
            R.style.Palette_Simple, R.style.Palette_Coral, R.style.Palette_Mint,
            R.style.Palette_Lavender, R.style.Palette_Blue, R.style.Palette_Pink,
    };

    private Prefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** 기능이 켜져 있는지. 종이 색만 기본이 꺼짐이고 나머지는 기본이 켜짐. */
    public static boolean on(Context ctx, String key) {
        return prefs(ctx).getBoolean(key, !PAPER.equals(key));
    }

    public static void set(Context ctx, String key, boolean value) {
        prefs(ctx).edit().putBoolean(key, value).apply();
    }

    public static int theme(Context ctx) {
        int t = prefs(ctx).getInt("theme", 0);
        return (t < 0 || t >= THEME_STYLES.length) ? 0 : t;
    }

    public static void setTheme(Context ctx, int index) {
        prefs(ctx).edit().putInt("theme", index).apply();
    }

    public static int themeStyle(Context ctx) {
        return THEME_STYLES[theme(ctx)];
    }
}
