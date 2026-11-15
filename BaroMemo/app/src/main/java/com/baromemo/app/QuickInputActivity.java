package com.baromemo.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** 위젯·알림·빠른 설정 버튼을 누르면 키보드와 함께 바로 뜨는 작은 입력창. */
public class QuickInputActivity extends Activity {
    private static final int[] EMOTION_IDS = {
            R.id.emo1, R.id.emo2, R.id.emo3, R.id.emo4, R.id.emo5
    };

    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        getWindow().setGravity(Gravity.TOP);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        setFinishOnTouchOutside(true);

        input = findViewById(R.id.quick_input);
        Button save = findViewById(R.id.quick_save);
        save.setOnClickListener(v -> saveAndClose(null));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndClose(null);
                return true;
            }
            return false;
        });

        // 감정 이모지를 누르면 글이 없어도 그 자리에서 저장
        for (int id : EMOTION_IDS) {
            TextView emo = findViewById(id);
            emo.setOnClickListener(v -> saveAndClose(((TextView) v).getText().toString()));
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

    private void saveAndClose(String emotion) {
        String text = input.getText().toString().trim();
        if (emotion != null) {
            text = text.isEmpty() ? emotion : emotion + " " + text;
        }
        if (NoteStore.add(this, text)) {
            Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show();
            NotificationHelper.show(this, "저장됨 ✓ " + text);
        }
        input.setText("");
        finish();
    }
}
