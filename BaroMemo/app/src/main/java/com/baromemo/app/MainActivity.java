package com.baromemo.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 앱 아이콘으로 열면: 위에 입력칸, 아래에 지금까지 메모 목록. */
public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATION = 1;

    private EditText input;
    private EditText search;
    private TextView empty;
    private Button reminder;
    private ArrayAdapter<String> adapter;
    private final List<Note> shown = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.input);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        reminder = findViewById(R.id.reminder);
        Button save = findViewById(R.id.save);
        Button share = findViewById(R.id.share);
        ListView list = findViewById(R.id.list);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);

        save.setOnClickListener(v -> saveInput());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveInput();
                return true;
            }
            return false;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });

        // 누르면 수정, 길게 누르면 삭제
        list.setOnItemClickListener((parent, view, position, id) -> editNote(shown.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(shown.get(position));
            return true;
        });

        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, NoteStore.exportText(this));
            startActivity(Intent.createChooser(send, "메모 전체 내보내기"));
        });

        reminder.setOnClickListener(v -> onReminderClick());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        } else {
            NotificationHelper.show(this, null);
        }
        Reminder.schedule(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION) NotificationHelper.show(this, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        reminder.setText(Reminder.label(this));
        input.requestFocus();
    }

    private void saveInput() {
        if (NoteStore.add(this, input.getText())) {
            input.setText("");
            refresh();
        }
    }

    private void editNote(final Note target) {
        final EditText et = new EditText(this);
        et.setText(target.text);
        et.setSelection(et.length());
        new AlertDialog.Builder(this)
                .setTitle("메모 수정")
                .setView(et)
                .setPositiveButton("저장", (d, w) -> {
                    NoteStore.update(this, target.time, et.getText());
                    refresh();
                })
                .setNeutralButton("삭제", (d, w) -> confirmDelete(target))
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmDelete(final Note target) {
        new AlertDialog.Builder(this)
                .setMessage("이 메모를 삭제할까요?\n\n" + target.text)
                .setPositiveButton("삭제", (d, w) -> {
                    NoteStore.delete(this, target.time);
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void onReminderClick() {
        if (!Reminder.isOn(this)) {
            pickReminderTime();
            return;
        }
        new AlertDialog.Builder(this)
                .setItems(new String[]{"시간 바꾸기", "알림 끄기"}, (d, which) -> {
                    if (which == 0) {
                        pickReminderTime();
                    } else {
                        Reminder.turnOff(this);
                        reminder.setText(Reminder.label(this));
                    }
                })
                .show();
    }

    private void pickReminderTime() {
        new TimePickerDialog(this, (view, h, m) -> {
            Reminder.set(this, true, h, m);
            reminder.setText(Reminder.label(this));
        }, Reminder.hour(this), Reminder.minute(this), true).show();
    }

    private void refresh() {
        String q = search.getText().toString().trim().toLowerCase(Locale.KOREA);
        List<Note> all = NoteStore.load(this);
        shown.clear();
        List<String> rows = new ArrayList<>();
        for (Note n : all) {
            if (!q.isEmpty() && !n.text.toLowerCase(Locale.KOREA).contains(q)) continue;
            shown.add(n);
            rows.add(NoteStore.shortTime(n.time) + "\n" + n.text);
        }
        adapter.clear();
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
        if (all.isEmpty()) {
            empty.setText("아직 메모가 없어요.\n위젯이나 알림창에서 바로 적어 보세요.");
            empty.setVisibility(TextView.VISIBLE);
        } else if (shown.isEmpty()) {
            empty.setText("검색 결과가 없어요.");
            empty.setVisibility(TextView.VISIBLE);
        } else {
            empty.setVisibility(TextView.GONE);
        }
    }
}
