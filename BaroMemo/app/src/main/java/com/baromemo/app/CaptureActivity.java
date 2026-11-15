package com.baromemo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 알림창 버튼에서 바로 쓰는 화면 없는 도우미.
 * "말로 쓰기"는 음성을 글로 바꿔 곧바로 저장하고, "사진·그림"은 찍기/고르기/그리기를 골라 저장한다.
 */
public class CaptureActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_VOICE = "voice";
    public static final String MODE_ATTACH = "attach";
    public static final String MODE_PHOTO = "photo";

    private static final int REQ_SPEECH = 1;
    private static final int REQ_PHOTO = 2;
    private static final int REQ_CAMERA = 3;

    private Uri cameraUri;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyPalette(this);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // 화면이 다시 만들어진 경우: 이미 시작한 작업의 결과만 기다린다
            cameraUri = savedInstanceState.getParcelable("cameraUri");
            return;
        }
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_VOICE.equals(mode)) {
            startVoice();
        } else {
            chooseAttachment(MODE_PHOTO.equals(mode));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("cameraUri", cameraUri);
    }

    // ───── 말로 쓰기 ─────

    private void startVoice() {
        Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "말하면 바로 메모로 저장돼요");
        try {
            startActivityForResult(speech, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "이 폰에서는 받아쓰기를 쓸 수 없어요", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ───── 사진 · 그림 ─────

    /** photoOnly: 사진 버튼에서 왔으면 사진 찍기/갤러리만 보여 준다. */
    private void chooseAttachment(boolean photoOnly) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (photoOnly || Prefs.on(this, Prefs.PHOTO)) {
            labels.add("사진 찍기");
            actions.add(this::takePhoto);
            labels.add("갤러리에서 고르기");
            actions.add(this::pickPhoto);
        }
        if (!photoOnly && Prefs.on(this, Prefs.DRAW)) {
            labels.add("그림 그리기");
            actions.add(this::openDrawing);
        }
        if (actions.isEmpty()) {
            finish();
            return;
        }
        if (actions.size() == 1) {
            actions.get(0).run();
            return;
        }
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void takePhoto() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "baromemo_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraUri == null) throw new IllegalStateException("no uri");
            Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(capture, REQ_CAMERA);
        } catch (Exception e) {
            cameraUri = null;
            Toast.makeText(this, "카메라를 열 수 없어요", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void pickPhoto() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "사진을 고를 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** 그리기 화면은 저장까지 스스로 하므로, 열어 주고 물러난다. */
    private void openDrawing() {
        startActivity(new Intent(this, DrawActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH) {
            ArrayList<String> results = (resultCode == RESULT_OK && data != null)
                    ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
            if (results != null && !results.isEmpty()) {
                saved(NoteStore.add(this, results.get(0)), results.get(0));
            }
            finish();
        } else if (requestCode == REQ_PHOTO) {
            Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            if (uri != null) {
                askCaption(uri);
            } else {
                finish();
            }
        } else if (requestCode == REQ_CAMERA) {
            Uri shot = cameraUri;
            cameraUri = null;
            if (shot != null && resultCode == RESULT_OK) {
                askCaption(shot);
            } else {
                if (shot != null) {
                    try {
                        getContentResolver().delete(shot, null, null); // 취소하면 빈 항목 정리
                    } catch (Exception e) {
                        // 정리 실패는 무시
                    }
                }
                finish();
            }
        } else {
            finish();
        }
    }

    /** 사진을 앱 안에 저장한 뒤, 사진에 붙일 글을 물어보고 메모로 남긴다. */
    private void askCaption(Uri uri) {
        final String name = NoteStore.importPhoto(this, uri);
        if (name == null) {
            Toast.makeText(this, "사진을 불러오지 못했어요", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        final EditText caption = new EditText(this);
        caption.setHint("사진에 붙일 글 (안 써도 돼요)");
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density + 0.5f);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        Bitmap bmp = NoteStore.loadPhoto(this, name, 2);
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight((int) (240 * density + 0.5f));
            box.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        box.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("사진 메모")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    saved(NoteStore.add(this, caption.getText(), name), caption.getText().toString());
                    finish();
                })
                .setNegativeButton("글 없이 저장", (d, w) -> {
                    saved(NoteStore.add(this, "", name), "");
                    finish();
                })
                .setOnCancelListener(d -> {
                    saved(NoteStore.add(this, "", name), "");
                    finish();
                })
                .show();
    }

    private void saved(boolean ok, String text) {
        if (!ok) return;
        Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show();
        NotificationHelper.show(this, NotificationHelper.savedStatus(this, text));
    }
}
