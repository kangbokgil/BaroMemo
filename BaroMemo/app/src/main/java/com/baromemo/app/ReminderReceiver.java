package com.baromemo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Reminder.isOn(ctx)) NotificationHelper.showReminder(ctx);
        Reminder.schedule(ctx);
    }
}
