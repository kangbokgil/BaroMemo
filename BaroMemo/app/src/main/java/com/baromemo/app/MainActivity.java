package com.baromemo.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
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
import java.util.Map;
import java.util.Set;

/** 앱 화면: 위에 입력칸, 아래에 목록 / 달력 / 요약 탭. 설정(⚙)에서 쓸 기능과 테마를 고른다. */
public class MainActivity extends Activity {
    /** 위젯의 메모 목록이나 알림 본문을 눌러 "보러" 들어왔을 때: 키보드를 띄우지 않는다. */
    public static final String EXTRA_VIEW_ONLY = "view_only";
    private static final int MAX_PHOTOS_AT_ONCE = 10;
    private boolean viewOnly;

    private static final int REQ_NOTIFICATION = 1;
    private static final int REQ_PHOTO = 2;
    private static final int REQ_CAMERA = 3;
    private static final int REQ_SPEECH = 4;
    private static final int REQ_BACKUP = 5;
    private static final int REQ_RESTORE = 6;
    private static final int REQ_DRAW = 7;

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
    private View micButton;
    private View attachButton;
    private View tabRow;
    private TextView tabList;
    private TextView tabCalendar;
    private TextView tabSummary;
    private View calendarPane;
    private TextView calTitle;
    private View calWeekdays;
    private LinearLayout calGrid;
    private TextView dayTitle;
    private ListView list;
    private View summaryPane;
    private TextView summaryText;
    private View selectBar;
    private TextView selCount;
    private View tagScroll;
    private LinearLayout tagRow;
    private View inputTagScroll;
    private LinearLayout inputTagRow;

    private final List<Note> shown = new ArrayList<>();
    private final Set<Long> selected = new HashSet<>(); // 선택 모드에서 고른 메모(시간 값)
    private final NoteAdapter adapter = new NoteAdapter();
    private int mode = MODE_LIST;
    private Calendar month;        // 보고 있는 달의 1일 0시
    private long selectedDay;      // 달력에서 고른 날의 0시
    private boolean calCollapsed;  // 달력을 접어 하루 화면으로 볼지
    private Uri cameraUri;         // 사진 찍기 결과가 저장될 곳

    private List<String[]> customRules = new ArrayList<>(); // 내가 정한 "글자 → 이모지" 규칙

    // 켜 둔 기능 (설정에서 바뀌면 다시 읽는다)
    private boolean featSticker;
    private boolean featPaper;
    private int listStyle; // 0 카드, 1 줄 목록, 2 말풍선
    private boolean featTags;
    private boolean featLinks;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyPalette(this);
        super.onCreate(savedInstanceState);
        viewOnly = savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_VIEW_ONLY, false);
        if (viewOnly) {
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.input);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        micButton = findViewById(R.id.mic);
        attachButton = findViewById(R.id.photo);
        tabRow = findViewById(R.id.tab_row);
        tabList = findViewById(R.id.tab_list);
        tabCalendar = findViewById(R.id.tab_calendar);
        tabSummary = findViewById(R.id.tab_summary);
        calendarPane = findViewById(R.id.calendar_pane);
        calTitle = findViewById(R.id.cal_title);
        calWeekdays = findViewById(R.id.cal_weekdays);
        calGrid = findViewById(R.id.cal_grid);
        dayTitle = findViewById(R.id.day_title);
        list = findViewById(R.id.list);
        summaryPane = findViewById(R.id.summary_pane);
        summaryText = findViewById(R.id.summary_text);
        selectBar = findViewById(R.id.select_bar);
        selCount = findViewById(R.id.sel_count);
        tagScroll = findViewById(R.id.tag_scroll);
        tagRow = findViewById(R.id.tag_row);
        inputTagScroll = findViewById(R.id.input_tag_scroll);
        inputTagRow = findViewById(R.id.input_tag_row);

        if (savedInstanceState != null) {
            cameraUri = savedInstanceState.getParcelable("cameraUri");
        }

        selectedDay = startOfDay(System.currentTimeMillis());
        month = Calendar.getInstance();
        syncMonthToSelectedDay();

        // 목록 모양에 따라 메모 사이 간격이 다르다 (줄 목록은 줄이 간격을 대신한다)
        listStyle = Prefs.listStyle(this);
        list.setDividerHeight(listStyle == 1 ? 0 : dp(listStyle == 2 ? 6 : 8));
        list.setAdapter(adapter);

        findViewById(R.id.save).setOnClickListener(v -> saveInput());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveInput();
                return true;
            }
            return false;
        });
        attachButton.setOnClickListener(v -> chooseAttachment());
        micButton.setOnClickListener(v -> startDictation());
        findViewById(R.id.settings).setOnClickListener(v -> showSettings());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });

        tabList.setOnClickListener(v -> setMode(MODE_LIST));
        tabCalendar.setOnClickListener(v -> setMode(MODE_CALENDAR));
        tabSummary.setOnClickListener(v -> setMode(MODE_SUMMARY));

        // 달력: 펼친 상태에서는 달, 접은 상태에서는 하루씩 이동. 제목을 누르면 접거나 편다.
        findViewById(R.id.cal_prev).setOnClickListener(v -> moveCalendar(-1));
        findViewById(R.id.cal_next).setOnClickListener(v -> moveCalendar(1));
        calTitle.setOnClickListener(v -> {
            calCollapsed = !calCollapsed;
            syncMonthToSelectedDay();
            refresh();
        });
        LinearLayout weekdays = findViewById(R.id.cal_weekdays);
        for (String w : WEEKDAYS) {
            TextView t = new TextView(this);
            t.setText(w);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(11);
            t.setTextColor(getColor(R.color.sub));
            weekdays.addView(t, new LinearLayout.LayoutParams(0, dp(24), 1f));
        }

        // 메모: 누르면 메뉴(수정·복사·공유·삭제), 길게 누르면 여러 개 선택
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

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        } else {
            NotificationHelper.show(this, null);
        }
        Reminder.schedule(this);

        applyFeatureToggles();
        setMode(MODE_LIST);

        // 처음 실행했을 때 한 번만 사용법을 보여 준다
        if (!Prefs.guideShown(this)) {
            Prefs.setGuideShown(this);
            showGuide();
        }
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
        if (mode == MODE_LIST && !viewOnly) input.requestFocus();
    }

    /** 이미 열려 있는 화면으로 다시 들어온 경우 (화면을 새로 쌓지 않는다). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_VIEW_ONLY, false)) {
            viewOnly = true;
            hideKeyboard();
        }
    }

    /** "보러 들어온" 상태는 그 한 번만. 다음에 앱 아이콘으로 열면 평소처럼 바로 입력할 수 있다. */
    @Override
    protected void onStop() {
        super.onStop();
        if (viewOnly) {
            viewOnly = false;
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
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

    // ───── 설정: 쓸 기능 고르기 · 테마 · 알림 · 백업 ─────

    /** 설정에서 켜고 끈 기능을 화면에 반영한다. */
    private void applyFeatureToggles() {
        featSticker = Prefs.on(this, Prefs.STICKER);
        featPaper = Prefs.on(this, Prefs.PAPER);
        featTags = Prefs.on(this, Prefs.TAGS);
        featLinks = Prefs.on(this, Prefs.LINKS);

        micButton.setVisibility(Prefs.on(this, Prefs.VOICE) ? View.VISIBLE : View.GONE);
        boolean attach = Prefs.on(this, Prefs.PHOTO) || Prefs.on(this, Prefs.DRAW);
        attachButton.setVisibility(attach ? View.VISIBLE : View.GONE);

        boolean cal = Prefs.on(this, Prefs.CALENDAR);
        boolean sum = Prefs.on(this, Prefs.SUMMARY);
        tabCalendar.setVisibility(cal ? View.VISIBLE : View.GONE);
        tabSummary.setVisibility(sum ? View.VISIBLE : View.GONE);
        tabRow.setVisibility(cal || sum ? View.VISIBLE : View.GONE);
        if ((mode == MODE_CALENDAR && !cal) || (mode == MODE_SUMMARY && !sum)) mode = MODE_LIST;

        // 알림창 버튼도 켠 기능에 맞춰 다시 그린다
        NotificationHelper.show(this, null);
    }

    private void showSettings() {
        String[] items = {"쓸 기능 고르기", "테마 색: " + Prefs.THEME_NAMES[Prefs.theme(this)],
                "화면 모드: " + Prefs.NIGHT_NAMES[Prefs.night(this)],
                "목록 모양: " + Prefs.LIST_NAMES[Prefs.listStyle(this)],
                "내 이모지 규칙", Reminder.label(this), "내보내기 · 백업", "사용법 보기", "의견 보내기"};
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(items, (d, which) -> {
                    if (which == 0) showFeatureDialog();
                    else if (which == 1) showThemeDialog();
                    else if (which == 2) showNightDialog();
                    else if (which == 3) showListStyleDialog();
                    else if (which == 4) showRulesDialog();
                    else if (which == 5) showReminderDialog();
                    else if (which == 6) showExportMenu();
                    else if (which == 7) showGuide();
                    else sendFeedback();
                })
                .show();
    }

    /** 숨은 조작을 알려 주는 사용법 안내. 처음 실행 때 한 번, 그 뒤에는 설정에서 다시 볼 수 있다. */
    private void showGuide() {
        String guide =
                "① 앱을 열지 않고 쓰기\n"
                + "화면 위를 내려 바로메모 알림을 펼치면 ‘메모 입력’, ‘말로 쓰기’, ‘사진·그림’ 버튼이 있어요. 알림 본문을 누르면 앱이 열려요.\n\n"
                + "② 위젯 꺼내기\n"
                + "홈 화면 빈 곳을 길게 누르기 → 위젯 → 바로메모를 홈 화면에 놓으세요.\n\n"
                + "③ 메모 다루기\n"
                + "누르면 수정·복사·공유·삭제, 길게 누르면 여러 개를 골라 한 번에 처리해요.\n\n"
                + "④ 달력\n"
                + "날짜를 누르면 그날 화면으로 넘어가요. 맨 위 날짜 줄을 누르면 달력이 다시 펴져요.\n\n"
                + "⑤ 내 입맛대로\n"
                + "오른쪽 위 ⚙에서 쓸 기능만 켜고, 테마 색·기록 알림·백업을 정할 수 있어요.\n\n"
                + "⑥ 메모 지키기\n"
                + "메모는 이 폰에만 저장돼요. 가끔 ⚙ → 내보내기 · 백업 → 백업 파일 만들기를 해 두세요.";
        new AlertDialog.Builder(this)
                .setTitle("바로메모 사용법")
                .setMessage(guide)
                .setPositiveButton("시작하기", null)
                .show();
    }

    /** 써 본 사람이 만든 사람에게 의견을 보내기 쉽게, 틀이 잡힌 글을 공유 창으로 넘긴다. */
    private void sendFeedback() {
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            // 버전을 못 읽으면 물음표로 둔다
        }
        String template =
                "[바로메모 의견]\n"
                + "· 좋았던 점: \n"
                + "· 불편했던 점: \n"
                + "· 있었으면 하는 것: \n"
                + "· 최근 3일 동안 쓴 횟수: \n\n"
                + "(앱 " + version + " / " + Build.MODEL + " / 안드로이드 " + Build.VERSION.RELEASE + ")";
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, template);
        startActivity(Intent.createChooser(send, "만든 사람에게 의견 보내기"));
    }

    private void showFeatureDialog() {
        boolean[] checked = new boolean[Prefs.KEYS.length];
        for (int i = 0; i < checked.length; i++) checked[i] = Prefs.on(this, Prefs.KEYS[i]);
        new AlertDialog.Builder(this)
                .setTitle("쓸 기능 고르기")
                .setMultiChoiceItems(Prefs.LABELS, checked,
                        (d, which, isChecked) -> Prefs.set(this, Prefs.KEYS[which], isChecked))
                .setPositiveButton("확인", null)
                .setOnDismissListener(d -> {
                    applyFeatureToggles();
                    setMode(mode);
                })
                .show();
    }

    private void showThemeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("테마 색")
                .setSingleChoiceItems(Prefs.THEME_NAMES, Prefs.theme(this), (d, which) -> {
                    Prefs.setTheme(this, which);
                    d.dismiss();
                    recreate(); // 새 색으로 화면을 다시 그린다
                })
                .show();
    }

    /** 내 이모지 규칙: 메모에 정해 둔 글자가 있으면, 글은 그대로 두고 이모지를 스티커로 붙인다. */
    private void showRulesDialog() {
        final List<String[]> rules = CustomRules.load(this);
        final int n = rules.size();
        String[] items = new String[n + 1];
        for (int i = 0; i < n; i++) items[i] = rules.get(i)[0] + "  →  " + rules.get(i)[1] + "   (누르면 삭제)";
        items[n] = "＋ 규칙 추가";
        new AlertDialog.Builder(this)
                .setTitle("내 이모지 규칙")
                .setItems(items, (d, which) -> {
                    if (which < n) {
                        CustomRules.remove(this, which);
                        refresh();
                        showRulesDialog();
                    } else {
                        showAddRuleDialog();
                    }
                })
                .setPositiveButton("닫기", null)
                .show();
    }

    private void showAddRuleDialog() {
        final EditText trigger = new EditText(this);
        trigger.setHint("메모에 쓸 글자 (예: 강복길<)");
        trigger.setSingleLine(true);
        final EditText emoji = new EditText(this);
        emoji.setHint("붙일 이모지 (예: 🐶)");
        emoji.setSingleLine(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(trigger, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(emoji, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("규칙 추가")
                .setMessage("메모에 이 글자가 들어 있으면 글은 그대로 두고, 카드 오른쪽에 이모지를 붙여요.")
                .setView(box)
                .setPositiveButton("추가", (d, w) -> {
                    String t = trigger.getText().toString().trim();
                    String e = emoji.getText().toString().trim();
                    if (t.isEmpty() || e.isEmpty()) {
                        Toast.makeText(this, "글자와 이모지를 모두 적어 주세요", Toast.LENGTH_SHORT).show();
                    } else {
                        CustomRules.add(this, t, e);
                        refresh();
                    }
                    showRulesDialog();
                })
                .setNegativeButton("취소", (d, w) -> showRulesDialog())
                .show();
    }

    /** 폰이 다크 모드여도 앱만 밝게(또는 반대로) 쓸 수 있게 한다. */
    private void showNightDialog() {
        new AlertDialog.Builder(this)
                .setTitle("화면 모드")
                .setSingleChoiceItems(Prefs.NIGHT_NAMES, Prefs.night(this), (d, which) -> {
                    Prefs.setNight(this, which);
                    d.dismiss();
                    recreate();
                })
                .show();
    }

    /** 목록 모양: 카드 / 줄 목록 / 말풍선 중에서 고른다. */
    private void showListStyleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("목록 모양")
                .setSingleChoiceItems(Prefs.LIST_NAMES, Prefs.listStyle(this), (d, which) -> {
                    Prefs.setListStyle(this, which);
                    d.dismiss();
                    recreate();
                })
                .show();
    }

    /** 기록 알림: 원하는 시간을 원하는 만큼. 하나도 없으면 알림이 오지 않는다. */
    private void showReminderDialog() {
        final List<Integer> times = Reminder.times(this);
        final int n = times.size();
        String[] items = new String[n + (n == 0 ? 1 : 2)];
        for (int i = 0; i < n; i++) items[i] = Reminder.format(times.get(i)) + "   (누르면 삭제)";
        items[n] = "＋ 알림 시간 추가";
        if (n > 0) items[n + 1] = "모두 끄기";
        new AlertDialog.Builder(this)
                .setTitle(n == 0 ? "기록 알림 (지금은 꺼짐)" : "기록 알림 · 하루 " + n + "번")
                .setItems(items, (d, which) -> {
                    if (which < n) {
                        Reminder.remove(this, times.get(which));
                        showReminderDialog();
                    } else if (which == n) {
                        new TimePickerDialog(this, (view, h, m) -> {
                            Reminder.add(this, h, m);
                            showReminderDialog();
                        }, 21, 0, true).show();
                    } else {
                        Reminder.clear(this);
                        Toast.makeText(this, "기록 알림을 모두 껐어요", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("닫기", null)
                .show();
    }

    private void showExportMenu() {
        String[] items = {"글로 내보내기 (공유)", "백업 파일 만들기 (사진 포함)", "백업 파일에서 복원"};
        new AlertDialog.Builder(this)
                .setTitle("내보내기 · 백업")
                .setItems(items, (d, which) -> {
                    try {
                        if (which == 0) {
                            shareText(NoteStore.exportText(this));
                        } else if (which == 1) {
                            String day = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
                            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("application/zip")
                                    .putExtra(Intent.EXTRA_TITLE, "바로메모_백업_" + day + ".zip"), REQ_BACKUP);
                        } else {
                            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("*/*"), REQ_RESTORE);
                        }
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, "파일을 고를 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
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
        tab.setTextColor(on ? Ui.accent(this) : getColor(R.color.sub));
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
        customRules = CustomRules.load(this);
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
            dayTitle.setText(day + "\n" + Summary.day(shown));
            if (shown.isEmpty()) emptyMessage = "이 날은 기록이 없어요.";
        } else {
            summaryText.setText(Summary.build(this));
        }

        buildTagBars(all);
        adapter.notifyDataSetChanged();
        if (emptyMessage != null) {
            empty.setText(emptyMessage);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    // ───── 해시태그 ─────

    /** 입력칸 아래 "태그 붙이기" 줄과, 목록 탭의 "태그로 모아 보기" 줄을 만든다. */
    private void buildTagBars(List<Note> all) {
        // 입력칸에 붙이는 태그 버튼
        if (featTags) {
            TagBar.fill(this, inputTagRow, input, all, R.drawable.bg_card);
            inputTagScroll.setVisibility(View.VISIBLE);
        } else {
            inputTagScroll.setVisibility(View.GONE);
        }

        // 태그로 모아 보기
        tagRow.removeAllViews();
        List<Map.Entry<String, Integer>> tags = Tags.counts(all);
        if (!featTags || mode != MODE_LIST || tags.isEmpty()) {
            tagScroll.setVisibility(View.GONE);
            return;
        }
        tagScroll.setVisibility(View.VISIBLE);
        String current = search.getText().toString().trim();
        for (int i = 0; i < tags.size() && i < 15; i++) {
            final String tag = tags.get(i).getKey();
            boolean on = tag.equals(current);
            TextView chip = new TextView(this);
            chip.setText(tag + " " + tags.get(i).getValue());
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackgroundResource(on ? R.drawable.bg_tab_selected : R.drawable.bg_card);
            chip.setTextColor(on ? Ui.accent(this) : getColor(R.color.sub));
            chip.setOnClickListener(v -> search.setText(tag.equals(search.getText().toString().trim()) ? "" : tag));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            lp.setMarginEnd(dp(8));
            tagRow.addView(chip, lp);
        }
    }

    // ───── 달력 ─────

    private void moveCalendar(int step) {
        if (calCollapsed) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selectedDay);
            c.add(Calendar.DAY_OF_MONTH, step);
            selectedDay = c.getTimeInMillis();
            syncMonthToSelectedDay();
        } else {
            month.add(Calendar.MONTH, step);
        }
        refresh();
    }

    private void syncMonthToSelectedDay() {
        month.setTimeInMillis(selectedDay);
        month.set(Calendar.DAY_OF_MONTH, 1);
    }

    private void buildCalendar(List<Note> all) {
        calWeekdays.setVisibility(calCollapsed ? View.GONE : View.VISIBLE);
        calGrid.setVisibility(calCollapsed ? View.GONE : View.VISIBLE);
        if (calCollapsed) {
            // 접은 상태: 고른 날짜만 한 줄로
            String day = new SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(new Date(selectedDay));
            calTitle.setText(day + "  ▾");
            return;
        }
        int year = month.get(Calendar.YEAR);
        int monthIndex = month.get(Calendar.MONTH);
        calTitle.setText(String.format(Locale.KOREA, "%d년 %d월  ▴", year, monthIndex + 1));

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
        int accent = Ui.accent(this);
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
                    cell.setTextColor(isToday ? accent : getColor(R.color.text));
                    cell.setTypeface(null, isToday ? Typeface.BOLD : Typeface.NORMAL);
                    if (dayStart == selectedDay) {
                        cell.setBackgroundResource(R.drawable.bg_cell_selected);
                    }
                    // 날짜를 누르면 달력을 접고 그날 화면으로 넘어간다
                    cell.setOnClickListener(v -> {
                        selectedDay = dayStart;
                        calCollapsed = true;
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

    // ───── 메모 저장 ─────

    private void saveInput() {
        if (NoteStore.add(this, input.getText())) {
            input.setText("");
            refresh();
        }
    }

    // ───── 사진 · 그림 붙이기 ─────

    private void chooseAttachment() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (Prefs.on(this, Prefs.PHOTO)) {
            labels.add("사진 찍기");
            actions.add(this::takePhoto);
            labels.add("갤러리에서 고르기");
            actions.add(this::pickPhoto);
        }
        if (Prefs.on(this, Prefs.DRAW)) {
            labels.add("그림 그리기");
            actions.add(this::openDrawing);
        }
        if (actions.size() == 1) {
            actions.get(0).run();
            return;
        }
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    /** 카메라를 열어 찍는다. 찍은 사진은 갤러리에도 남는다. */
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

    private void pickPhoto() {
        // 여러 장을 한 번에 고를 수 있다. 여러 장이면 사진마다 메모가 하나씩 생긴다
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(pick, REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "사진을 고를 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    /** 그리기 화면을 연다. 입력칸에 적어 둔 글은 그림과 함께 저장된다. */
    private void openDrawing() {
        Intent draw = new Intent(this, DrawActivity.class)
                .putExtra(DrawActivity.EXTRA_TEXT, input.getText().toString());
        startActivityForResult(draw, REQ_DRAW);
    }

    /** 고르거나 찍은 사진을 저장한다. 입력칸이 비어 있으면 사진에 붙일 글을 물어본다. */
    /** 사진을 여러 장 골랐을 때: 사진마다 메모를 하나씩 만든다. 입력칸에 쓴 글은 첫 사진에 붙는다. */
    private void savePhotoNotes(ClipData photos) {
        int total = Math.min(photos.getItemCount(), MAX_PHOTOS_AT_ONCE);
        String typed = input.getText().toString().trim();
        int saved = 0;
        // 고른 순서대로 쌓이도록 뒤에서부터 저장한다 (목록은 최신이 위)
        for (int i = total - 1; i >= 0; i--) {
            Uri one = photos.getItemAt(i).getUri();
            String name = one == null ? null : NoteStore.importPhoto(this, one);
            if (name == null) continue;
            NoteStore.add(this, i == 0 ? typed : "", name);
            saved++;
            android.os.SystemClock.sleep(3); // 메모와 사진 파일 이름이 시각이라 겹치지 않게 한다
        }
        if (saved > 0 && !typed.isEmpty()) input.setText("");
        refresh();
        String message = saved == 0 ? "사진을 불러오지 못했어요" : "사진 메모 " + saved + "개를 만들었어요";
        if (photos.getItemCount() > MAX_PHOTOS_AT_ONCE) {
            message += " (한 번에 " + MAX_PHOTOS_AT_ONCE + "장까지)";
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void savePhotoNote(Uri uri) {
        final String name = NoteStore.importPhoto(this, uri);
        if (name == null) {
            Toast.makeText(this, "사진을 불러오지 못했어요", Toast.LENGTH_SHORT).show();
            return;
        }
        String typed = input.getText().toString().trim();
        if (!typed.isEmpty()) {
            NoteStore.add(this, typed, name);
            input.setText("");
            refresh();
            return;
        }

        final EditText caption = new EditText(this);
        caption.setHint("사진에 붙일 글 (안 써도 돼요)");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        Bitmap bmp = NoteStore.loadPhoto(this, name, 2);
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(dp(240));
            box.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        box.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("사진 메모")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    NoteStore.add(this, caption.getText(), name);
                    refresh();
                })
                .setNegativeButton("글 없이 저장", (d, w) -> {
                    NoteStore.add(this, "", name);
                    refresh();
                })
                .setOnCancelListener(d -> {
                    NoteStore.add(this, "", name);
                    refresh();
                })
                .show();
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
        Uri uri = data == null ? null : data.getData();
        if (requestCode == REQ_PHOTO) {
            ClipData many = data == null ? null : data.getClipData();
            if (resultCode == RESULT_OK && many != null && many.getItemCount() > 1) {
                savePhotoNotes(many);
            } else if (resultCode == RESULT_OK && many != null && many.getItemCount() == 1) {
                savePhotoNote(many.getItemAt(0).getUri());
            } else if (resultCode == RESULT_OK && uri != null) {
                savePhotoNote(uri);
            }
        } else if (requestCode == REQ_CAMERA) {
            Uri shot = cameraUri;
            cameraUri = null;
            if (shot == null) return;
            if (resultCode == RESULT_OK) {
                savePhotoNote(shot);
            } else {
                try {
                    getContentResolver().delete(shot, null, null); // 취소하면 빈 항목 정리
                } catch (Exception e) {
                    // 정리 실패는 무시
                }
            }
        } else if (requestCode == REQ_DRAW) {
            if (resultCode == RESULT_OK) {
                input.setText("");
                refresh();
            }
        } else if (requestCode == REQ_BACKUP) {
            if (resultCode == RESULT_OK && uri != null) {
                boolean ok = Backup.export(this, uri);
                Toast.makeText(this, ok ? "백업 파일을 만들었어요" : "백업에 실패했어요", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_RESTORE) {
            if (resultCode == RESULT_OK && uri != null) {
                int added = Backup.restore(this, uri);
                Toast.makeText(this, added < 0 ? "바로메모 백업 파일이 아니에요"
                        : "메모 " + added + "개를 복원했어요", Toast.LENGTH_SHORT).show();
                refresh();
            }
        } else if (requestCode == REQ_SPEECH) {
            if (resultCode != RESULT_OK || data == null) return;
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results == null || results.isEmpty()) return;
            String before = input.getText().toString();
            input.setText(before.isEmpty() ? results.get(0) : before + " " + results.get(0));
            input.setSelection(input.length());
        }
    }

    // ───── 복사 · 공유 ─────

    private void copyText(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("바로메모", text));
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "복사했어요", Toast.LENGTH_SHORT).show();
        }
    }

    /** 메모에 붙은 사진을 클립보드에 넣는다. 사진 붙여넣기를 받는 앱(메신저 등)에서 붙여넣을 수 있다. */
    private void copyPhoto(String photoName) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null) return;
        try {
            Uri uri = PhotoProvider.uriFor(photoName);
            cm.setPrimaryClip(ClipData.newUri(getContentResolver(), "바로메모 사진", uri));
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(this, "사진을 복사했어요", Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, "사진을 복사하지 못했어요", Toast.LENGTH_SHORT).show();
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
        selectBar.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
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

    // ───── 메모 한 개: 메뉴 · 수정 · 삭제 ─────

    private void showActions(final Note target) {
        String title = noteLine(target);
        if (title.length() > 30) title = title.substring(0, 30) + "…";
        final List<String> urls = featLinks ? Tags.urls(target.text) : new ArrayList<String>();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (!urls.isEmpty()) {
            labels.add(urls.size() == 1 ? "링크 열기" : "링크 열기 (" + urls.size() + "개)");
            actions.add(() -> openLinks(urls));
        }
        labels.add("수정");
        actions.add(() -> editNote(target));
        labels.add("복사");
        actions.add(() -> copyText(noteLine(target)));
        if (target.photo != null) {
            labels.add("사진 복사");
            actions.add(() -> copyPhoto(target.photo));
        }
        labels.add("공유");
        actions.add(() -> shareText(noteLine(target)));
        labels.add("삭제");
        actions.add(() -> confirmDelete(target));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    /** 메모 안의 링크를 연다. 여러 개면 어느 것을 열지 고른다. */
    private void openLinks(final List<String> urls) {
        if (urls.size() == 1) {
            openUrl(urls.get(0));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("어느 링크를 열까요?")
                .setItems(urls.toArray(new String[0]), (d, which) -> openUrl(urls.get(which)))
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
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
                .setMessage("이 메모를 삭제할까요?\n\n" + noteLine(target))
                .setPositiveButton("삭제", (d, w) -> {
                    NoteStore.delete(this, target.time);
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
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
                int layout = listStyle == 1 ? R.layout.row_note_line
                        : listStyle == 2 ? R.layout.row_note_chat : R.layout.row_note;
                convertView = getLayoutInflater().inflate(layout, parent, false);
                ImageView img = convertView.findViewById(R.id.row_photo);
                img.setClipToOutline(true); // 사진 모서리를 둥글게
            }
            Note n = shown.get(position);
            int accent = Ui.accent(MainActivity.this);
            TextView time = convertView.findViewById(R.id.row_time);
            TextView text = convertView.findViewById(R.id.row_text);

            boolean picked = selected.contains(n.time);
            String when = mode == MODE_CALENDAR
                    ? timeOnly.format(new Date(n.time))
                    : NoteStore.shortTime(n.time);
            time.setText(picked ? "✓ " + when : when);
            time.setTextColor(picked ? accent : getColor(R.color.sub));
            time.setTypeface(null, picked ? Typeface.BOLD : Typeface.NORMAL);

            text.setText(Tags.highlight(n.text, Ui.tagColor(MainActivity.this), featLinks));
            text.setVisibility(n.text.isEmpty() ? View.GONE : View.VISIBLE);

            // 종이 색(켰을 때만). 카드나 말풍선의 바탕에 입힌다. 줄 목록은 바탕이 없어 해당 없음
            View body = convertView.findViewById(R.id.row_body);
            if (featPaper) {
                int paper = PAPERS[(int) ((n.time / 1000) % PAPERS.length)];
                body.setBackgroundTintList(ColorStateList.valueOf(getColor(paper)));
            } else {
                body.setBackgroundTintList(null);
            }

            // 내용에 맞는 스티커(켰을 때만)
            TextView sticker = convertView.findViewById(R.id.row_sticker);
            // 내가 정한 규칙이 먼저, 없으면 자동 스티커(켰을 때만)
            String emoji = CustomRules.pick(customRules, n.text);
            if (emoji == null && featSticker) emoji = Sticker.pick(n.text);
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
