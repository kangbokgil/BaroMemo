package com.baromemo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 재부팅·앱 업데이트·알림 지움 때 알림창 입력칸과 기록 알림 예약을 되살린다. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        NotificationHelper.show(ctx, null);
        NoteWidget.updateAll(ctx);
        Reminder.schedule(ctx);
    }
}
