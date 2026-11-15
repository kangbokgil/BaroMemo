package com.baromemo.app;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** 알림창 입력칸에서 보낸 글을 저장하고 알림을 원래대로 되돌린다. */
public class NoteReplyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        CharSequence text = results == null ? null : results.getCharSequence(NotificationHelper.KEY_TEXT);
        boolean saved = NoteStore.add(ctx, text);

        if (NotificationHelper.ACTION_REMINDER_REPLY.equals(intent.getAction())) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.cancel(NotificationHelper.REMINDER_ID);
        }
        NotificationHelper.show(ctx, saved ? NotificationHelper.savedStatus(ctx, text.toString().trim()) : null);
    }
}
