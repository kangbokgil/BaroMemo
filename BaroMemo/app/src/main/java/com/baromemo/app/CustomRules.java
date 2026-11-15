package com.baromemo.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 직접 정한 "글자 → 이모지" 규칙.
 * 메모에 그 글자가 들어 있으면 글은 그대로 두고, 정해 둔 이모지를 스티커로 붙인다.
 */
public final class CustomRules {
    private CustomRules() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** 규칙 목록. 각 항목은 {글자, 이모지}. */
    public static List<String[]> load(Context ctx) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString("custom_rules", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                out.add(new String[]{pair.getString(0), pair.getString(1)});
            }
        } catch (JSONException e) {
            // 읽지 못하면 빈 목록
        }
        return out;
    }

    private static void save(Context ctx, List<String[]> rules) {
        JSONArray arr = new JSONArray();
        for (String[] r : rules) {
            JSONArray pair = new JSONArray();
            pair.put(r[0]);
            pair.put(r[1]);
            arr.put(pair);
        }
        prefs(ctx).edit().putString("custom_rules", arr.toString()).apply();
    }

    /** 규칙을 더한다. 같은 글자가 이미 있으면 이모지만 바꾼다. */
    public static void add(Context ctx, String trigger, String emoji) {
        List<String[]> rules = load(ctx);
        for (String[] r : rules) {
            if (r[0].equals(trigger)) {
                r[1] = emoji;
                save(ctx, rules);
                return;
            }
        }
        rules.add(new String[]{trigger, emoji});
        save(ctx, rules);
    }

    public static void remove(Context ctx, int index) {
        List<String[]> rules = load(ctx);
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            save(ctx, rules);
        }
    }

    /** 글에 맞는 규칙의 이모지. 없으면 null. */
    public static String pick(List<String[]> rules, String text) {
        if (text == null || text.isEmpty()) return null;
        for (String[] r : rules) {
            if (text.contains(r[0])) return r[1];
        }
        return null;
    }
}
