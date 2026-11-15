package com.baromemo.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 홈 화면 위젯. 다섯 가지 모양을 제공하고, 사용자가 위젯 목록에서 원하는 것을 골라 놓는다.
 *  - NoteWidget (A): 알약형 입력바
 *  - WidgetB: 버튼 네 개 (글 / 말 / 사진 / 그림)
 *  - WidgetC: 입력바 + 최근 메모 두 줄
 *  - WidgetD: 입력바 + 기분 다섯 개 (누르면 바로 기록)
 *  - WidgetE: 작은 동그라미 버튼
 */
public class NoteWidget extends AppWidgetProvider {
    private static final String[] MOODS = {"😊", "🙂", "😐", "😔", "😡"};
    private static final int[] MOOD_IDS = {R.id.w_e1, R.id.w_e2, R.id.w_e3, R.id.w_e4, R.id.w_e5};
    private static final int[] LINE_IDS = {R.id.w_m1, R.id.w_m2, R.id.w_m3, R.id.w_m4, R.id.w_m5,
            R.id.w_m6, R.id.w_m7, R.id.w_m8, R.id.w_m9, R.id.w_m10};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        updateAll(ctx);
    }

    /** 위젯 크기를 바꾸면 "최근 메모" 위젯에 보이는 줄 수를 다시 맞춘다. */
    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle options) {
        updateAll(ctx);
    }

    /** 놓여 있는 모든 종류의 위젯을 새로 그린다. */
    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        List<Note> notes = NoteStore.load(ctx);
        update(ctx, mgr, NoteWidget.class, R.layout.widget_a, notes);
        update(ctx, mgr, WidgetB.class, R.layout.widget_b, notes);
        update(ctx, mgr, WidgetC.class, R.layout.widget_c, notes);
        update(ctx, mgr, WidgetD.class, R.layout.widget_d, notes);
        update(ctx, mgr, WidgetE.class, R.layout.widget_e, notes);
    }

    private static void update(Context ctx, AppWidgetManager mgr, Class<?> type, int layout, List<Note> notes) {
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, type));
        if (ids == null || ids.length == 0) return;
        // 크기가 위젯마다 다를 수 있어서 하나씩 그린다
        for (int id : ids) {
            mgr.updateAppWidget(id, build(ctx, mgr, id, layout, notes));
        }
    }

    private static RemoteViews build(Context ctx, AppWidgetManager mgr, int id, int layout, List<Note> notes) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        // 모양마다 가진 버튼이 다르다. 그 모양에 없는 버튼은 연결해도 그냥 무시된다.
        v.setOnClickPendingIntent(R.id.w_input, activity(ctx, 20, new Intent(ctx, QuickInputActivity.class)));
        v.setOnClickPendingIntent(R.id.w_voice, capture(ctx, 21, CaptureActivity.MODE_VOICE));
        v.setOnClickPendingIntent(R.id.w_attach, capture(ctx, 22, CaptureActivity.MODE_ATTACH));
        v.setOnClickPendingIntent(R.id.w_photo, capture(ctx, 23, CaptureActivity.MODE_PHOTO));
        v.setOnClickPendingIntent(R.id.w_draw, activity(ctx, 24, new Intent(ctx, DrawActivity.class)));
        // 최근 메모를 누르면 목록을 "보러" 들어간다: 키보드를 띄우지 않고, 화면을 겹쳐 쌓지 않는다
        v.setOnClickPendingIntent(R.id.w_list, activity(ctx, 25, new Intent(ctx, MainActivity.class)
                .putExtra(MainActivity.EXTRA_VIEW_ONLY, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));

        // 기분: 누르면 화면 전환 없이 바로 기록
        for (int i = 0; i < MOODS.length; i++) {
            Intent mood = new Intent(ctx, MoodReceiver.class)
                    .setAction("com.baromemo.app.MOOD_" + i)
                    .putExtra(MoodReceiver.EXTRA_MOOD, MOODS[i]);
            v.setOnClickPendingIntent(MOOD_IDS[i], PendingIntent.getBroadcast(ctx, 30 + i, mood,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        // 최근 메모: 위젯 높이에 들어가는 만큼 보여 준다 (입력바와 여백을 뺀 높이를 한 줄 높이로 나눔)
        if (layout == R.layout.widget_c) {
            Bundle options = mgr.getAppWidgetOptions(id);
            boolean landscape = ctx.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            int height = options == null ? 0 : options.getInt(landscape
                    ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            float fontScale = ctx.getResources().getConfiguration().fontScale;
            int fit = height <= 0 ? 2 : (int) ((height - 78) / (19 * fontScale + 4));
            int lines = Math.max(1, Math.min(LINE_IDS.length, fit));
            for (int i = 0; i < LINE_IDS.length; i++) {
                boolean show = i < lines && (i < notes.size() || i == 0);
                v.setViewVisibility(LINE_IDS[i], show ? View.VISIBLE : View.GONE);
                if (show) {
                    v.setTextViewText(LINE_IDS[i], notes.isEmpty() ? "아직 메모가 없어요" : line(notes.get(i)));
                }
            }
        }
        return v;
    }

    private static String line(Note n) {
        String time = new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(n.time));
        String text = n.text.replace('\n', ' ');
        return time + "  " + (n.photo != null ? (text.isEmpty() ? "사진" : "[사진] ") : "") + text;
    }

    private static PendingIntent activity(Context ctx, int requestCode, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent capture(Context ctx, int requestCode, String mode) {
        Intent intent = new Intent(ctx, CaptureActivity.class)
                .setAction("com.baromemo.app.WIDGET_" + mode)
                .putExtra(CaptureActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return activity(ctx, requestCode, intent);
    }
}
