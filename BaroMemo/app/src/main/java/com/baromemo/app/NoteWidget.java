package com.baromemo.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/** 홈 화면 위젯: 입력바를 누르면 작은 입력창, 목록 아이콘을 누르면 전체 목록. */
public class NoteWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        updateAll(ctx);
    }

    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, NoteWidget.class));
        if (ids == null || ids.length == 0) return;

        List<Note> notes = NoteStore.load(ctx);
        String last = notes.isEmpty() ? "아직 메모가 없어요" : "최근: " + notes.get(0).text;

        Intent quick = new Intent(ctx, QuickInputActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent quickPi = PendingIntent.getActivity(ctx, 3, quick,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent list = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent listPi = PendingIntent.getActivity(ctx, 4, list,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_note);
            v.setTextViewText(R.id.widget_last, last);
            v.setOnClickPendingIntent(R.id.widget_input, quickPi);
            v.setOnClickPendingIntent(R.id.widget_list, listPi);
            mgr.updateAppWidget(id, v);
        }
    }
}
