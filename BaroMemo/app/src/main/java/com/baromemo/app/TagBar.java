package com.baromemo.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/** 입력칸 아래 태그 버튼 줄: 누르면 입력칸에 #태그가 바로 붙는다. */
public final class TagBar {
    private TagBar() {}

    public static void fill(Activity a, LinearLayout row, final EditText target, List<Note> notes, int chipBackground) {
        row.removeAllViews();
        addChip(a, row, "#", chipBackground, v -> append(target, "#", false));
        List<Map.Entry<String, Integer>> tags = Tags.counts(notes);
        for (int i = 0; i < tags.size() && i < 10; i++) {
            final String tag = tags.get(i).getKey();
            addChip(a, row, tag, chipBackground, v -> append(target, tag, true));
        }
    }

    private static void addChip(Activity a, LinearLayout row, String label, int background, View.OnClickListener onClick) {
        float density = a.getResources().getDisplayMetrics().density;
        int padH = (int) (12 * density + 0.5f);
        TextView chip = new TextView(a);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(padH, 0, padH, 0);
        chip.setMinWidth((int) (34 * density + 0.5f));
        chip.setBackgroundResource(background);
        chip.setTextColor(Ui.tagColor(a));
        chip.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (32 * density + 0.5f));
        lp.setMarginEnd((int) (6 * density + 0.5f));
        row.addView(chip, lp);
    }

    private static void append(EditText et, String s, boolean spaceAfter) {
        String cur = et.getText().toString();
        String before = (cur.isEmpty() || cur.endsWith(" ")) ? "" : " ";
        et.append(before + s + (spaceAfter ? " " : ""));
        et.requestFocus();
        et.setSelection(et.length());
    }
}
