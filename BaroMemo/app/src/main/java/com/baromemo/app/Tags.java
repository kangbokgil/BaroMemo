package com.baromemo.app;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 메모 안의 #해시태그를 찾고, 색을 입히고, 많이 쓴 순으로 센다. */
public final class Tags {
    private static final Pattern TAG = Pattern.compile("#[\\p{L}\\p{N}_]+");

    private Tags() {}

    public static List<String> extract(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            String tag = m.group();
            if (!out.contains(tag)) out.add(tag);
        }
        return out;
    }

    /** 해시태그 부분만 색을 바꾼 글. */
    public static CharSequence highlight(String text, int color) {
        SpannableString s = new SpannableString(text);
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            s.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** 해시태그별 사용 횟수, 많이 쓴 순. */
    public static List<Map.Entry<String, Integer>> counts(List<Note> notes) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Note n : notes) {
            for (String tag : extract(n.text)) {
                Integer old = map.get(tag);
                map.put(tag, old == null ? 1 : old + 1);
            }
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        return list;
    }
}
