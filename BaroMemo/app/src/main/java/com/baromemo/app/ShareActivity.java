package com.baromemo.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** 다른 앱에서 "공유 → 바로메모"를 누르면 글·링크·사진을 화면 전환 없이 바로 저장한다. */
public class ShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean saved = false;
        Intent in = getIntent();
        if (in != null && Intent.ACTION_SEND.equals(in.getAction())) {
            CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            String body = text == null ? "" : text.toString().trim();
            String subject = in.getStringExtra(Intent.EXTRA_SUBJECT);
            if (subject != null && !subject.trim().isEmpty() && !body.contains(subject.trim())) {
                body = body.isEmpty() ? subject.trim() : subject.trim() + " " + body;
            }
            String photo = null;
            String type = in.getType();
            if (type != null && type.startsWith("image/")) {
                Uri uri = in.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) photo = NoteStore.importPhoto(this, uri);
            }
            saved = NoteStore.add(this, body, photo);
        }
        Toast.makeText(this, saved ? "바로메모에 저장됨" : "저장할 내용이 없어요", Toast.LENGTH_SHORT).show();
        if (saved) NotificationHelper.show(this, "저장됨 ✓");
        finish();
    }
}
