package com.baromemo.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

/** 테마 색 도우미. */
public final class Ui {
    private Ui() {}

    /** 앱에서 고른 밝게/어둡게를 폰 설정보다 우선하게 한다. 각 화면의 attachBaseContext에서 부른다. */
    public static Context wrap(Context base) {
        int mode = Prefs.night(base);
        if (mode == 0) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        int night = mode == 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(c);
    }

    /** 고른 테마 색을 화면에 덧씌운다. setContentView 전에 불러야 한다. */
    public static void applyPalette(Activity a) {
        a.getTheme().applyStyle(Prefs.themeStyle(a), true);
    }

    public static int accent(Context ctx) {
        return attrColor(ctx, R.attr.bmAccent);
    }

    /** 해시태그 글자색. 심플 테마에서는 본문과 구분되도록 파란색, 그 밖에는 테마 강조색. */
    public static int tagColor(Context ctx) {
        return Prefs.theme(ctx) == 0 ? ctx.getColor(R.color.tag_simple) : accent(ctx);
    }

    public static int onAccent(Context ctx) {
        return attrColor(ctx, R.attr.bmOnAccent);
    }

    private static int attrColor(Context ctx, int attr) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId != 0 ? ctx.getColor(tv.resourceId) : tv.data;
    }
}
