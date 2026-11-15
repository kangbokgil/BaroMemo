package com.baromemo.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 앱 화면: 위에 입력칸, 아래에 목록 / 달력 / 요약 탭. */
public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATION = 1;
    private static final int REQ_PHOTO = 2;
    private static final int REQ_CAMERA = 3;
    private static final int REQ_SPEECH = 4;
    private static final int MODE_LIST = 0;
    private static final int MODE_CALENDAR = 1;
    private static final int MODE_SUMMARY = 2;
    private static final int[] PAPERS = {
            R.color.paper1, R.color.paper2, R.color.paper3, R.color.paper4, R.color.paper5
    };
    private static final String[] WEEKDAYS = {"일", "월", "화", "수", "목", "금", "토"};

    private EditText input;
    private EditText search;
    private TextView empty;
    private TextView reminder;
    private TextView tabList;
    private TextView tabCalendar;
    private TextView tabSummary;
    private View calendarPane;
    private TextView calTitle;
    private LinearLayout calGrid;
    private TextView dayTitle;
    private ListView list;
    private View summaryPane;
    private TextView summaryText;

    private View bottomBar;
    private View selectBar;
    private TextView selCount;

    private final List<Note> shown = new ArrayList<>();
    private final Set<Long> selected = new HashSet<>(); // 선택 모드에서 고른 메모(시간 값)
    private Uri cameraUri;                               // 사진 찍기 결과가 저장될 곳
    private final NoteAdapter adapter = new NoteAdapter();
    private int mode = MODE_LIST;
    private Calendar month;      // 보고 있는 달의 1일 0시
    private long selectedDay;    // 달력에서 고른 날의 0시

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.input);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        reminder = findViewById(R.id.reminder);
        tabList = findViewById(R.id.tab_list);
        tabCalendar = findViewById(R.id.tab_calendar);
        tabSummary = findViewById(R.id.tab_summary);
        calendarPane = findViewById(R.id.calendar_pane);
        calTitle = findViewById(R.id.cal_title);
        calGrid = findViewById(R.id.cal_grid);
        dayTitle = findViewById(R.id.day_title);
        list = findViewById(R.id.list);
        summaryPane = findViewById(R.id.summary_pane);
        summaryText = findViewById(R.id.summary_text);
        bottomBar = findViewById(R.id.bottom_bar);
        selectBar = findViewById(R.id.select_bar);
        selCount = findViewById(R.id.sel_count);
        if (savedInstanceState != null) {
            cameraUri = savedInstanceState.getParcelable("cameraUri");
        }

        selectedDay = startOfDay(System.currentTimeMillis());
        month = Calendar.getInstance();
        month.setTimeInMillis(selectedDay);
        month.set(Calendar.DAY_OF_MONTH, 1);

        list.setAdapter(adapter);

        findViewById(R.id.save).setOnClickListener(v -> saveInput());
        findViewById(R.id.photo).setOnClickListener(v -> choosePhotoSource());
        findViewById(R.id.mic).setOnClickListener(v -> startDictation());

        findViewById(R.id.sel_copy).setOnClickListener(v -> {
            copyText(selectedText());
            clearSelection();
        });
        findViewById(R.id.sel_share).setOnClickListener(v -> {
            shareText(selectedText());
            clearSelection();
        });
        findViewById(R.id.sel_delete).setOnClickListener(v -> deleteSelected());
        findViewById(R.id.sel_cancel).setOnClickListener(v -> clearSelection());
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

        tabList.setOnClickListener(v -> setMode(MODE_LIST));
        tabCalendar.setOnClickListener(v -> setMode(MODE_CALENDAR));
        tabSummary.setOnClickListener(v -> setMode(MODE_SUMMARY));

        findViewById(R.id.cal_prev).setOnClickListener(v -> {
            month.add(Calendar.MONTH, -1);
            refresh();
        });
        findViewById(R.id.cal_next).setOnClickListener(v -> {
            month.add(Calendar.MONTH, 1);
            refresh();
        });

        // 요일 줄
        LinearLayout weekdays = findViewById(R.id.cal_weekdays);
        for (String w : WEEKDAYS) {
            TextView t = new TextView(this);
            t.setText(w);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(11);
            t.setTextColor(getColor(R.color.sub));
            weekdays.addView(t, new LinearLayout.LayoutParams(0, dp(24), 1f));
        }

        // 누르면 메뉴(수정·복사·공유·삭제), 길게 누르면 여러 개 선택
        list.setOnItemClickListener((parent, view, position, id) -> {
            Note n = shown.get(position);
            if (selected.isEmpty()) {
                showActions(n);
            } else {
                toggleSelected(n);
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleSelected(shown.get(position));
            return true;
        });

        findViewById(R.id.share).setOnClickListener(v -> {
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
        setMode(MODE_LIST);
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
        if (mode == MODE_LIST) input.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("cameraUri", cameraUri);
    }

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            clearSelection();
        } else {
            super.onBackPressed();
        }
    }

    // ───── 탭 ─────

    private void setMode(int newMode) {
        mode = newMode;
        selected.clear();
        updateSelectionBar();
        styleTab(tabList, mode == MODE_LIST);
        styleTab(tabCalendar, mode == MODE_CALENDAR);
        styleTab(tabSummary, mode == MODE_SUMMARY);
        search.setVisibility(mode == MODE_LIST ? View.VISIBLE : View.GONE);
        calendarPane.setVisibility(mode == MODE_CALENDAR ? View.VISIBLE : View.GONE);
        dayTitle.setVisibility(mode == MODE_CALENDAR ? View.VISIBLE : View.GONE);
        list.setVisibility(mode == MODE_SUMMARY ? View.GONE : View.VISIBLE);
        summaryPane.setVisibility(mode == MODE_SUMMARY ? View.VISIBLE : View.GONE);
        if (mode != MODE_LIST) hideKeyboard();
        refresh();
    }

    private void styleTab(TextView tab, boolean on) {
        tab.setBackgroundResource(on ? R.drawable.bg_tab_selected : 0);
        tab.setTextColor(getColor(on ? R.color.accent : R.color.sub));
        tab.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    // ───── 화면 갱신 ─────

    private void refresh() {
        List<Note> all = NoteStore.load(this); // 최신이 앞
        shown.clear();
        String emptyMessage = null;

        if (mode == MODE_LIST) {
            String q = search.getText().toString().trim().toLowerCase(Locale.KOREA);
            for (Note n : all) {
                if (q.isEmpty() || n.text.toLowerCase(Locale.KOREA).contains(q)) shown.add(n);
            }
            if (all.isEmpty()) {
                emptyMessage = "아직 메모가 없어요.\n위젯이나 알림창에서 바로 적어 보세요.";
            } else if (shown.isEmpty()) {
                emptyMessage = "검색 결과가 없어요.";
            }
        } else if (mode == MODE_CALENDAR) {
            buildCalendar(all);
            long end = selectedDay + 24L * 60 * 60 * 1000;
            for (Note n : all) {
                if (n.time >= selectedDay && n.time < end) shown.add(n);
            }
            Collections.reverse(shown); // 하루 안에서는 시간순
            String day = new SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(new Date(selectedDay));
            dayTitle.setText(day + " · 기록 " + shown.size() + "개");
            if (shown.isEmpty()) emptyMessage = "이 날은 기록이 없어요.";
        } else {
            summaryText.setText(Summary.build(this));
        }

        adapter.notifyDataSetChanged();
        if (emptyMessage != null) {
            empty.setText(emptyMessage);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    // ───── 달력 ─────

    private void buildCalendar(List<Note> all) {
        int year = month.get(Calendar.YEAR);
        int monthIndex = month.get(Calendar.MONTH);
        calTitle.setText(String.format(Locale.KOREA, "%d년 %d월", year, monthIndex + 1));

        int days = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDow = month.get(Calendar.DAY_OF_WEEK) - 1; // 일요일 = 0

        // 날짜별 기록 수와 기분 점수
        int[] count = new int[days + 1];
        int[] sum = new int[days + 1];
        int[] moods = new int[days + 1];
        long monthStart = month.getTimeInMillis();
        Calendar next = (Calendar) month.clone();
        next.add(Calendar.MONTH, 1);
        long monthEnd = next.getTimeInMillis();
        Calendar tmp = Calendar.getInstance();
        for (Note n : all) {
            if (n.time < monthStart || n.time >= monthEnd) continue;
            tmp.setTimeInMillis(n.time);
            int d = tmp.get(Calendar.DAY_OF_MONTH);
            count[d]++;
            int s = Summary.score(n.text);
            if (s > 0) {
                sum[d] += s;
                moods[d]++;
            }
        }

        long today = startOfDay(System.currentTimeMillis());
        int rows = (firstDow + days + 6) / 7;
        int day = 1 - firstDow;
        calGrid.removeAllViews();
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++, day++) {
                TextView cell = new TextView(this);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(12);
                if (day >= 1 && day <= days) {
                    Calendar dc = (Calendar) month.clone();
                    dc.set(Calendar.DAY_OF_MONTH, day);
                    final long dayStart = dc.getTimeInMillis();

                    String mark;
                    if (moods[day] > 0) {
                        mark = Summary.face((double) sum[day] / moods[day]);
                    } else if (count[day] > 0) {
                        mark = "•";
                    } else {
                        mark = " ";
                    }
                    cell.setText(day + "\n" + mark);

                    boolean isToday = dayStart == today;
                    cell.setTextColor(getColor(isToday ? R.color.accent : R.color.text));
                    cell.setTypeface(null, isToday ? Typeface.BOLD : Typeface.NORMAL);
                    if (dayStart == selectedDay) {
                        cell.setBackgroundResource(R.drawable.bg_cell_selected);
                    }
                    cell.setOnClickListener(v -> {
                        selectedDay = dayStart;
                        refresh();
                    });
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(46), 1f));
            }
            calGrid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static long startOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ───── 메모 저장·수정·삭제 ─────

    private void saveInput() {
        if (NoteStore.add(this, input.getText())) {
            input.setText("");
            refresh();
        }
    }

    // ───── 사진 ─────

    private void choosePhotoSource() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"사진 찍기", "갤러리에서 고르기"}, (d, which) -> {
                    if (which == 0) takePhoto(); else pickPhoto();
                })
                .show();
    }

    /** 카메라를 열어 찍은 사진을 입력칸의 글과 함께 메모로 저장한다. 찍은 사진은 갤러리에도 남는다. */
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
        }
    }

    /** 갤러리에서 사진을 골라, 입력칸의 글과 함께 메모로 저장한다. */
    private void pickPhoto() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
        try {
            startActivityForResult(pick, REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "사진을 고를 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePhotoNote(Uri uri) {
        String name = NoteStore.importPhoto(this, uri);
        if (name == null) {
            Toast.makeText(this, "사진을 불러오지 못했어요", Toast.LENGTH_SHORT).show();
            return;
        }
        NoteStore.add(this, input.getText(), name);
        input.setText("");
        refresh();
    }

    // ───── 받아쓰기 ─────

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
        if (requestCode == REQ_PHOTO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                savePhotoNote(data.getData());
            }
        } else if (requestCode == REQ_CAMERA) {
            Uri uri = cameraUri;
            cameraUri = null;
            if (uri == null) return;
            if (resultCode == RESULT_OK) {
                savePhotoNote(uri);
            } else {
                try {
                    getContentResolver().delete(uri, null, null); // 취소하면 빈 항목 정리
                } catch (Exception e) {
                    // 정리 실패는 무시
                }
            }
        } else if (requestCode == REQ_SPEECH) {
            if (resultCode != RESULT_OK || data == null) return;
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results == null || results.isEmpty()) return;
            String before = input.getText().toString();
            String joined = before.isEmpty() ? results.get(0) : before + " " + results.get(0);
            input.setText(joined);
            input.setSelection(input.length());
        }
    }

    // ───── 복사·공유 ─────

    private void copyText(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("바로메모", text));
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "복사했어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "메모 내보내기"));
    }

    private static String noteLine(Note n) {
        return (n.photo != null ? "📷 " : "") + n.text;
    }

    // ───── 여러 개 선택 ─────

    private void toggleSelected(Note n) {
        if (!selected.remove(n.time)) selected.add(n.time);
        updateSelectionBar();
        adapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        selected.clear();
        updateSelectionBar();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionBar() {
        boolean on = !selected.isEmpty();
        selectBar.setVisibility(on ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        selCount.setText(selected.size() + "개 선택");
    }

    /** 선택한 메모를 시간순으로, 날짜·시간과 함께 글로 만든다. */
    private String selectedText() {
        List<Note> all = NoteStore.load(this); // 최신이 앞
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        for (int i = all.size() - 1; i >= 0; i--) {
            Note n = all.get(i);
            if (!selected.contains(n.time)) continue;
            sb.append(fmt.format(new Date(n.time))).append("  ").append(noteLine(n)).append('\n');
        }
        return sb.toString().trim();
    }

    private void deleteSelected() {
        final List<Long> targets = new ArrayList<>(selected);
        new AlertDialog.Builder(this)
                .setMessage("선택한 메모 " + targets.size() + "개를 삭제할까요?")
                .setPositiveButton("삭제", (d, w) -> {
                    for (long t : targets) NoteStore.delete(this, t);
                    clearSelection();
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───── 메모 한 개 메뉴·수정·삭제 ─────

    private void showActions(final Note target) {
        String title = noteLine(target);
        if (title.length() > 30) title = title.substring(0, 30) + "…";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(new String[]{"수정", "복사", "공유", "삭제"}, (d, which) -> {
                    if (which == 0) editNote(target);
                    else if (which == 1) copyText(noteLine(target));
                    else if (which == 2) shareText(noteLine(target));
                    else confirmDelete(target);
                })
                .show();
    }

    private void editNote(final Note target) {
        final EditText et = new EditText(this);
        et.setText(target.text);
        et.setSelection(et.length());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        if (target.photo != null) {
            Bitmap bmp = NoteStore.loadPhoto(this, target.photo, 1);
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                iv.setMaxHeight(dp(320));
                box.addView(iv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        box.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(target.photo != null ? "메모 보기·수정" : "메모 수정")
                .setView(box)
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
                .setMessage("이 메모를 삭제할까요?\n\n" + (target.photo != null ? "📷 " : "") + target.text)
                .setPositiveButton("삭제", (d, w) -> {
                    NoteStore.delete(this, target.time);
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───── 기록 알림 ─────

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

    // ───── 목록 한 줄 ─────

    private class NoteAdapter extends BaseAdapter {
        private final SimpleDateFormat timeOnly = new SimpleDateFormat("HH:mm", Locale.KOREA);
        private final LruCache<String, Bitmap> thumbs = new LruCache<>(12);

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return shown.get(position).time; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.row_note, parent, false);
            }
            Note n = shown.get(position);
            TextView time = convertView.findViewById(R.id.row_time);
            TextView text = convertView.findViewById(R.id.row_text);
            boolean picked = selected.contains(n.time);
            String when = mode == MODE_CALENDAR
                    ? timeOnly.format(new Date(n.time))
                    : NoteStore.shortTime(n.time);
            time.setText(picked ? "✓ " + when : when);
            time.setTextColor(getColor(picked ? R.color.accent : R.color.sub));
            time.setTypeface(null, picked ? Typeface.BOLD : Typeface.NORMAL);
            text.setText(n.text);
            text.setVisibility(n.text.isEmpty() ? View.GONE : View.VISIBLE);

            // 다이어리 느낌: 메모마다 종이 색을 달리하고, 내용에 맞는 스티커를 붙인다
            int paper = PAPERS[(int) ((n.time / 1000) % PAPERS.length)];
            convertView.setBackgroundTintList(ColorStateList.valueOf(getColor(paper)));
            TextView sticker = convertView.findViewById(R.id.row_sticker);
            String emoji = Sticker.pick(n.text);
            if (emoji != null) {
                sticker.setText(emoji);
                sticker.setVisibility(View.VISIBLE);
            } else {
                sticker.setVisibility(View.GONE);
            }

            ImageView photo = convertView.findViewById(R.id.row_photo);
            Bitmap bmp = null;
            if (n.photo != null) {
                bmp = thumbs.get(n.photo);
                if (bmp == null) {
                    bmp = NoteStore.loadPhoto(MainActivity.this, n.photo, 2);
                    if (bmp != null) thumbs.put(n.photo, bmp);
                }
            }
            if (bmp != null) {
                photo.setImageBitmap(bmp);
                photo.setVisibility(View.VISIBLE);
            } else {
                photo.setImageDrawable(null);
                photo.setVisibility(View.GONE);
            }
            return convertView;
        }
    }
}
