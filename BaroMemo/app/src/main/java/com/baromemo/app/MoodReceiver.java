package com.baromemo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** 위젯의 기분 이모지를 누르면 화면 전환 없이 그 기분을 메모로 남긴다. */
public class MoodReceiver extends BroadcastReceiver {
    public static final String EXTRA_MOOD = "mood";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String mood = intent.getStringExtra(EXTRA_MOOD);
        if (NoteStore.add(ctx, mood)) {
            Toast.makeText(ctx, "기분 기록됨 " + mood, Toast.LENGTH_SHORT).show();
            NotificationHelper.show(ctx, NotificationHelper.savedStatus(ctx, mood));
        }
    }
}
