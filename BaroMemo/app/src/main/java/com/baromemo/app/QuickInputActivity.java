package com.baromemo.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** 위젯·알림·빠른 설정 버튼을 누르면 키보드와 함께 바로 뜨는 작은 입력창. */
public class QuickInputActivity extends Activity {
    private static final int REQ_SPEECH = 1;
    private static final int[] EMOTION_IDS = {
            R.id.emo1, R.id.emo2, R.id.emo3, R.id.emo4, R.id.emo5
    };

    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyPalette(this);
        super.onCreate(savedInstanceState);
        // 잠금화면 위에서도 입력창이 뜨게 한다 (쓰기만 가능, 기존 메모는 보이지 않음)
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        setContentView(R.layout.activity_quick);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        getWindow().setGravity(Gravity.TOP);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        setFinishOnTouchOutside(true);

        input = findViewById(R.id.quick_input);
        TextView save = findViewById(R.id.quick_save);
        save.setOnClickListener(v -> saveAndClose(null));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndClose(null);
                return true;
            }
            return false;
        });

        // 켜 둔 기능만 보여 준다
        View mic = findViewById(R.id.quick_mic);
        mic.setVisibility(Prefs.on(this, Prefs.VOICE) ? View.VISIBLE : View.GONE);
        mic.setOnClickListener(v -> startDictation());

        View emoRow = findViewById(R.id.emo_row);
        emoRow.setVisibility(Prefs.on(this, Prefs.MOOD) ? View.VISIBLE : View.GONE);
        // 기분 이모지를 누르면 글이 없어도 그 자리에서 저장
        for (int id : EMOTION_IDS) {
            TextView emo = findViewById(id);
            emo.setOnClickListener(v -> saveAndClose(((TextView) v).getText().toString()));
        }

        View tagScroll = findViewById(R.id.quick_tag_scroll);
        if (Prefs.on(this, Prefs.TAGS)) {
            LinearLayout tagRow = findViewById(R.id.quick_tag_row);
            TagBar.fill(this, tagRow, input, NoteStore.load(this), R.drawable.bg_field);
            tagScroll.setVisibility(View.VISIBLE);
        } else {
            tagScroll.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void startDictation() {
        Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하세요");
        try {
            startActivityForResult(speech, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "이 폰에서는 받아쓰기를 쓸 수 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String before = input.getText().toString();
        input.setText(before.isEmpty() ? results.get(0) : before + " " + results.get(0));
        input.setSelection(input.length());
    }

    private void saveAndClose(String emotion) {
        String text = input.getText().toString().trim();
        if (emotion != null) {
            text = text.isEmpty() ? emotion : emotion + " " + text;
        }
        if (NoteStore.add(this, text)) {
            Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show();
            NotificationHelper.show(this, NotificationHelper.savedStatus(this, text));
        }
        input.setText("");
        finish();
    }
}
