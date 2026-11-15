package com.baromemo.app;

import android.text.SpannableString;
import android.text.Spanned;
import android.graphics.Typeface;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Patterns;

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

    /** 글 안의 웹 주소들. 주소 앞에 http가 없으면 붙여서 돌려준다. */
    public static List<String> urls(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            String url = m.group();
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
            if (!out.contains(url)) out.add(url);
        }
        return out;
    }

    /** 해시태그는 색과 굵기로, 웹 주소는 색과 밑줄로 표시한 글. */
    public static CharSequence highlight(String text, int color) {
        SpannableString s = new SpannableString(text);
        Matcher link = Patterns.WEB_URL.matcher(text);
        while (link.find()) {
            s.setSpan(new ForegroundColorSpan(color), link.start(), link.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new UnderlineSpan(), link.start(), link.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            s.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
