package com.baromemo.app;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;

/** 테마 색 도우미. */
public final class Ui {
    private Ui() {}

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
