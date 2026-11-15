package com.baromemo.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;

/** 알림창에 항상 떠 있는 "바로 입력" 알림과, 정해진 시간에 뜨는 기록 알림. */
public final class NotificationHelper {
    public static final String KEY_TEXT = "memo_text";
    public static final String ACTION_REMINDER_REPLY = "com.baromemo.app.REMINDER_REPLY";
    public static final String ACTION_REPOST = "com.baromemo.app.REPOST";
    public static final int REMINDER_ID = 2;

    private static final String CHANNEL_ID = "quick_memo";
    private static final String REMINDER_CHANNEL_ID = "reminder";
    private static final int NOTIFICATION_ID = 1;

    private NotificationHelper() {}

    /** 저장 직후 알림에 띄울 문구. 잠금화면에서도 보이므로 메모 내용은 설정에서 켰을 때만 넣는다. */
    public static String savedStatus(Context ctx, String text) {
        return Prefs.on(ctx, Prefs.PREVIEW) ? "저장됨 ✓ " + text : "저장됨 ✓";
    }

    private static boolean canPost(Context ctx) {
        return Build.VERSION.SDK_INT < 33
                || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static Notification.Action replyAction(Context ctx, int requestCode, String action, String label) {
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT)
                .setLabel("떠오른 걸 바로 적으세요")
                .build();
        Intent replyIntent = new Intent(ctx, NoteReplyReceiver.class);
        if (action != null) replyIntent.setAction(action);
        PendingIntent replyPi = PendingIntent.getBroadcast(ctx, requestCode, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_note), label, replyPi)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build();
    }

    private static PendingIntent openQuickInput(Context ctx, int requestCode) {
        Intent openIntent = new Intent(ctx, QuickInputActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, requestCode, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 알림 버튼: 화면 없이 말로 쓰기 / 사진·그림 붙이기를 시작한다. */
    private static Notification.Action captureAction(Context ctx, int requestCode, String mode, String label) {
        Intent intent = new Intent(ctx, CaptureActivity.class)
                .setAction("com.baromemo.app.CAPTURE_" + mode)
                .putExtra(CaptureActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_note), label, pi).build();
    }

    /** 알림창에 상주하는 입력 알림. 버튼은 최대 3개: 메모 입력 / 말로 쓰기 / 사진·그림. */
    public static void show(Context ctx, String status) {
        if (!canPost(ctx)) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "빠른 메모 입력", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // 잠금화면에서도 보이게
        nm.createNotificationChannel(ch);

        // 알림을 밀어서 지워도 다시 띄운다
        Intent repost = new Intent(ctx, BootReceiver.class).setAction(ACTION_REPOST);
        PendingIntent repostPi = PendingIntent.getBroadcast(ctx, 6, repost,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 본문을 누르면 앱(목록 화면)이 열린다
        Intent appIntent = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent appPi = PendingIntent.getActivity(ctx, 10, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean voice = Prefs.on(ctx, Prefs.VOICE);
        boolean attach = Prefs.on(ctx, Prefs.PHOTO) || Prefs.on(ctx, Prefs.DRAW);

        Notification.Builder builder = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("바로메모")
                .setContentText(status == null ? "아래 버튼으로 바로 적기 · 누르면 앱 열기" : status)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(appPi)
                .setDeleteIntent(repostPi)
                .addAction(replyAction(ctx, 1, null, "메모 입력"));
        if (voice) builder.addAction(captureAction(ctx, 11, CaptureActivity.MODE_VOICE, "말로 쓰기"));
        if (attach) builder.addAction(captureAction(ctx, 12, CaptureActivity.MODE_ATTACH, "사진·그림"));
        if (!voice && !attach) {
            // 둘 다 꺼 두었으면 작은 입력창을 여는 버튼을 둔다
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_note), "입력창 열기", openQuickInput(ctx, 2)).build());
        }
        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /** 정해진 시간에 뜨는 "지금 어때요?" 알림. */
    public static void showReminder(Context ctx) {
        if (!canPost(ctx)) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                REMINDER_CHANNEL_ID, "기록 알림", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        Notification n = new Notification.Builder(ctx, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("지금 어때요?")
                .setContentText("한 줄만 남겨 보세요")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openQuickInput(ctx, 8))
                .addAction(replyAction(ctx, 7, ACTION_REMINDER_REPLY, "바로 적기"))
                .build();
        nm.notify(REMINDER_ID, n);
    }
}
